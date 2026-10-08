package com.lowdragmc.photon.client.gameobject.particle.renderer;

import com.lowdragmc.lowdraglib2.utils.Vector3fHelper;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.PhotonMesh;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.IModelSource;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.IDynamicMesh;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.AnimatedGltfModelSource;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.skin.VertexAnimationBake;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleConfig;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleRendererSetting;
import com.lowdragmc.photon.client.gameobject.emitter.particle.FacingMode;
import com.lowdragmc.photon.client.gameobject.emitter.particle.FacingOrientationHelper;
import com.lowdragmc.photon.client.fx.IWholeEffectTransformer;
import com.lowdragmc.photon.client.fx.WholeEffectRenderSpace;
import com.lowdragmc.photon.client.gameobject.particle.IParticle;
import com.lowdragmc.photon.client.gameobject.particle.TileParticle;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collection;

/**
 * Renders {@link TileParticle}s: the single entry point for both the CPU vertex path
 * ({@link #renderQueue}) and the GPU-instanced path ({@link #uploadInstances}/{@link #drawInstanced},
 * backed by {@link ParticleInstanceRenderer} for GL resources). Both paths share the same
 * billboard/stretched/model orientation math, so they stay visually identical by construction.
 * The particle itself only holds data and simulation.
 */
@OnlyIn(Dist.CLIENT)
@ParametersAreNonnullByDefault
public class TileParticleRenderer {
    /** Float stride of the particle/sprite instance payload, including packed light. */
    public static final int INSTANCE_FLOATS = 21;
    /** Float stride of the model instance payload, including packed light. */
    public static final int MODEL_INSTANCE_FLOATS = 15;

    /**
     * Converts Unity-style Euler angles using the same rotation order as the shared render paths.
     * Kept here as a public compatibility entry point from Photon 26.2.
     */
    public static Quaternionf eulerRotation(Vector3f rotation) {
        return ParticleRotationMath.eulerRotation(rotation);
    }

    private static final int GL_MAX_TEXTURE_BUFFER_SIZE = 0x8C2B;
    private final ParticleConfig config;
    /** The renderer runtime this pass draws with (slot-or-config per field): the config's default runtime
     *  for the shared pass, or a per-emitter overriding runtime for an override pass. Custom GPU data
     *  still comes from the config. */
    private final ParticleRendererSetting.Runtime renderer;
    private final ParticleInstanceRenderer instanceBackend;
    private boolean instancedVatActive;

    public static boolean vatFitsGpuBuffer(AnimatedGltfModelSource.BakedVertexAnimation vat) {
        RenderSystem.assertOnRenderThread();
        int maxTexels = org.lwjgl.opengl.GL11.glGetInteger(GL_MAX_TEXTURE_BUFFER_SIZE);
        return maxTexels > 0 && (long) vat.vertexCount() * vat.frames() <= maxTexels;
    }

    public TileParticleRenderer(ParticleConfig config, ParticleRendererSetting.Runtime renderer) {
        this.config = config;
        this.renderer = renderer;
        this.instanceBackend = new ParticleInstanceRenderer(config, renderer);
    }

    // ---------------------------------------------------------------------
    // CPU path
    // ---------------------------------------------------------------------

    public void renderQueue(VertexConsumer buffer, Collection<IParticle> particles, Camera camera, float partialTicks) {
        var renderMode = renderer.getRenderMode();
        if (renderMode == ParticleRendererSetting.Mode.None) {
            return;
        }
        ModelPass model = renderMode == ParticleRendererSetting.Mode.Model ? modelPass() : null;
        boolean renderedModel = false;
        for (var particle : particles) {
            if (particle instanceof TileParticle tileParticle && tileParticle.getDelay() <= 0) {
                renderParticle(buffer, tileParticle, camera, partialTicks, model);
                renderedModel |= model != null;
            }
        }
        if (renderedModel && model.dynamicSource() != null) {
            model.dynamicSource().onDrawn();
        }
    }

    private record ModelPass(PhotonMesh mesh, boolean remapUV, boolean shade, Vector3f pivot,
                             @Nullable IDynamicMesh dynamicSource,
                             @Nullable AnimatedGltfModelSource.BakedVertexAnimation vat) {
    }

    private ModelPass modelPass() {
        IModelSource source = renderer.getModelSource();
        var vat = source.vertexAnimation();
        PhotonMesh mesh = vat == null ? source.getMesh() : source.topology();
        return new ModelPass(mesh, source.hasAtlasUV() && !renderer.isUseBlockUV(), renderer.isShade(),
                renderer.getModelPivot(), vat == null ? source.asDynamic() : null, vat);
    }

    private void renderParticle(@Nonnull VertexConsumer buffer, TileParticle particle, Camera camera, float partialTicks,
                                @Nullable ModelPass model) {
        var vec3 = camera.getPosition();

        var localPos = particle.getSimPos(partialTicks).mulPosition(particle.getSpaceTransform());
        localPos = applyWholeEffectPosition(particle, localPos);
        var x = (float) (localPos.x - vec3.x);
        var y = (float) (localPos.y - vec3.y);
        var z = (float) (localPos.z - vec3.z);

        var color = particle.getRealColor(partialTicks);
        var r = color.x();
        var g = color.y();
        var b = color.z();
        var a = color.w();

        var light = particle.getRealLight(partialTicks);
        // A particle can be rendered before its first light-cache refresh (notably during an
        // editor seek or immediately after an emitter is attached).  -1 is not a valid packed
        // light coordinate: the shader would sample far outside the light texture and multiply
        // the whole material to transparent black, leaving only the editor wireframe visible.
        if (light < 0) {
            light = LightTexture.FULL_BRIGHT;
        }
        var rotation = particle.getRealRotation(partialTicks);
        var renderMode = renderer.getRenderMode();

        var size = particle.getRealSize(partialTicks);

        if (model != null) {
            // mesh positions are already in centered model space (PhotonMesh convention)
            var transform = new Matrix4f().translate(x, y, z)
                    .rotate(computeModelQuaternion(particle, rotation, camera, partialTicks))
                    .scale(size.mul(particle.getSpaceScale()));
            // draw 3d model
            var mesh = model.mesh();
            var remapUV = model.remapUV();
            var shade = model.shade();
                var pivot = model.pivot();
                var vatPose = model.vat() == null ? null : vatPose(model.vat(), particle, partialTicks);
                var normalMat = transform.normal(new Matrix3f());
                for (int quad = 0; quad < mesh.quadCount(); quad++) {
                    putMeshQuad(transform, normalMat, pivot, buffer, mesh, quad, model.vat(), vatPose,
                            shade, r, g, b, a, light, remapUV);
            }
        } else {
            Quaternionf quaternion;
            float finalSizeX = size.x;
            float finalSizeY = size.y;
            float finalSizeZ = size.z;
            var spaceScale = particle.getSpaceScale();

            if (renderMode == ParticleRendererSetting.Mode.StretchedBillboard) {
                var frame = computeStretchedFrame(particle, localPos, vec3.x, vec3.y, vec3.z, size, spaceScale);
                quaternion = frame.rotation();
                finalSizeX = frame.stretchedSizeX();
                x -= frame.offsetX();
                y -= frame.offsetY();
                z -= frame.offsetZ();
            } else {
                quaternion = computeBillboardQuaternion(particle, renderMode, camera, partialTicks, rotation);
            }

            var rawVertexes = new Vector3f[]{
                    new Vector3f(-1.0F, -1.0F, 0.0F),
                    new Vector3f(-1.0F, 1.0F, 0.0F),
                    new Vector3f(1.0F, 1.0F, 0.0F),
                    new Vector3f(1.0F, -1.0F, 0.0F),
            };
            // 1.20.1's camera-facing particle convention uses a clockwise quad when viewed
            // from +Z. Its visible face and normal therefore point towards -Z.
            var normal = new Vector3f(0, 0, -1);

            for (var i = 0; i < 4; ++i) {
                var vertex = rawVertexes[i];
                vertex.mul(finalSizeX, finalSizeY, finalSizeZ);
                vertex = quaternion.transform(vertex);
                vertex.mul(spaceScale);
                vertex.add(x, y, z);
            }

            normal = quaternion.transform(normal);

            var uvs = particle.getRealUVs(partialTicks);
            var u0 = uvs.x();
            var v0 = uvs.y();
            var u1 = uvs.z();
            var v1 = uvs.w();
            // Camera.rotation() in 1.20.1 maps local +X to screen-left. Match vanilla
            // SingleQuadParticle's U order, without changing authored/custom facing modes.
            if (isDefaultCameraFacing(particle, renderMode)) {
                var swap = u0;
                u0 = u1;
                u1 = swap;
            }

            buffer.vertex(rawVertexes[0].x(), rawVertexes[0].y(), rawVertexes[0].z())
                    .color(r, g, b, a).uv(u0, v1).uv2(light).normal(normal.x, normal.y, normal.z).endVertex();
            buffer.vertex(rawVertexes[1].x(), rawVertexes[1].y(), rawVertexes[1].z())
                    .color(r, g, b, a).uv(u0, v0).uv2(light).normal(normal.x, normal.y, normal.z).endVertex();
            buffer.vertex(rawVertexes[2].x(), rawVertexes[2].y(), rawVertexes[2].z())
                    .color(r, g, b, a).uv(u1, v0).uv2(light).normal(normal.x, normal.y, normal.z).endVertex();
            buffer.vertex(rawVertexes[3].x(), rawVertexes[3].y(), rawVertexes[3].z())
                    .color(r, g, b, a).uv(u1, v1).uv2(light).normal(normal.x, normal.y, normal.z).endVertex();
        }
    }

    private void putMeshQuad(Matrix4f transform, Matrix3f normalMat, Vector3f pivotPoint,
                             VertexConsumer buffer, PhotonMesh mesh, int quad,
                             @Nullable AnimatedGltfModelSource.BakedVertexAnimation vat, @Nullable VertexAnimationBake.PlaybackFrame vatPose,
                             boolean shade, float red, float green, float blue, float alpha, int light,
                             boolean remapUV) {
        var vertices = mesh.vertices();

        float u0 = 0, v0 = 0, uw = 1, vh = 1;
        if (remapUV) {
            var bounds = mesh.quadSpriteBounds();
            u0 = bounds[quad * 4];
            v0 = bounds[quad * 4 + 1];
            uw = bounds[quad * 4 + 2] - u0;
            vh = bounds[quad * 4 + 3] - v0;
        }
        float[] decodedA = vat == null ? null : new float[3];
        float[] decodedB = vat == null || !vat.interpolate() ? null : new float[3];

        for (int corner = 0; corner < 4; corner++) {
            int off = PhotonMesh.vertexOffset(quad, corner);
            var x = vertices[off] + pivotPoint.x;
            var y = vertices[off + 1] + pivotPoint.y;
            var z = vertices[off + 2] + pivotPoint.z;
            float nx = vertices[off + 5], ny = vertices[off + 6], nz = vertices[off + 7];
            if (vat != null && vatPose != null) {
                int vertex = quad * 4 + corner;
                int first = (vatPose.frame() * vat.vertexCount() + vertex) * VertexAnimationBake.FLOATS_PER_VERTEX;
                int second = (vatPose.nextFrame() * vat.vertexCount() + vertex) * VertexAnimationBake.FLOATS_PER_VERTEX;
                float[] table = vat.table();
                float blend = vatPose.blend();
                x = table[first] + (table[second] - table[first]) * blend + pivotPoint.x;
                y = table[first + 1] + (table[second + 1] - table[first + 1]) * blend + pivotPoint.y;
                z = table[first + 2] + (table[second + 2] - table[first + 2]) * blend + pivotPoint.z;
                if (vat.interpolate()) {
                    VertexAnimationBake.unpackNormal(table[first + 3], decodedA);
                    VertexAnimationBake.unpackNormal(table[second + 3], decodedB);
                    nx = decodedA[0] + (decodedB[0] - decodedA[0]) * blend;
                    ny = decodedA[1] + (decodedB[1] - decodedA[1]) * blend;
                    nz = decodedA[2] + (decodedB[2] - decodedA[2]) * blend;
                } else {
                    VertexAnimationBake.unpackNormal(table[first + 3], decodedA);
                    nx = decodedA[0]; ny = decodedA[1]; nz = decodedA[2];
                }
            }
            var u = vertices[off + 3];
            var v = vertices[off + 4];
            if (remapUV) {
                u = (u - u0) / uw;
                v = (v - v0) / vh;
            }

            var pos = transform.transform(new Vector4f(x, y, z, 1.0F));
            var normal = new Vector3f(nx, ny, nz).mul(normalMat).normalize();
            float brightness = shade ? mesh.shadeBrightness(quad, corner) : 1f;

            buffer.vertex(pos.x, pos.y, pos.z)
                    .color(red * brightness, green * brightness, blue * brightness, alpha)
                    .uv(u, v).uv2(light).normal(normal.x, normal.y, normal.z).endVertex();
        }
    }

    private static VertexAnimationBake.PlaybackFrame vatPose(AnimatedGltfModelSource.BakedVertexAnimation vat, IParticle particle, float partialTicks) {
        float phase = vat.phaseSource() == AnimatedGltfModelSource.PhaseSource.Lifetime
                ? particle.getT(partialTicks)
                : vat.phase() + particle.getMemRandom("instance_random");
        var playback = VertexAnimationBake.playbackFrame(phase, vat.frames(), vat.loop(), vat.interpolate());
        return playback;
    }

    // ---------------------------------------------------------------------
    // instanced path
    // ---------------------------------------------------------------------

    /** Set by the render pass each frame from the emitter's Tangent renderer setting; see {@link #uploadInstances}. */
    public void setWantsTangent(boolean wantsTangent) {
        instanceBackend.setWantsTangent(wantsTangent);
    }

    /**
     * Fill and upload the per-instance data for this pass's particles. Returns true if any
     * instance was uploaded (the VAO is left bound for {@link #drawInstanced}).
     */
    public boolean uploadInstances(Collection<IParticle> particles, Camera camera, float partialTicks) {
        var renderMode = renderer.getRenderMode();
        // rebuild the static geometry when the model mesh was hot-reloaded (identity compare), when a
        // runtime renderMode override crossed the Model/non-Model boundary (different instance layout),
        // or when the pass started/stopped wanting tangents (different mesh vertex layout)
        if (instanceBackend.isInitialized()
                && (instanceBackend.wasBuiltForModel() != (renderMode == ParticleRendererSetting.Mode.Model)
                    || (renderMode == ParticleRendererSetting.Mode.Model
                        && (instanceBackend.getBuiltMesh() != instancedMesh()
                            || instanceBackend.wasBuiltWithTangent() != instanceBackend.wantsTangent())))) {
            instanceBackend.dispose();
        }
        var buffer = instanceBackend.beginUpload(particles.size());
        if (buffer == null) return false;
        var vat = renderMode == ParticleRendererSetting.Mode.Model
                ? renderer.getModelSource().vertexAnimation() : null;
        instancedVatActive = vat != null;
        instanceBackend.setVertexAnimation(vat == null ? null : vat.table(),
                vat == null ? 0 : vat.vertexCount(), vat == null ? 0 : vat.frames(),
                vat == null ? 0 : vat.phase(),
                vat != null && vat.phaseSource() == AnimatedGltfModelSource.PhaseSource.Lifetime,
                vat != null && vat.interpolate(), vat != null && vat.loop(), renderer.getModelPivot());
        var setting = config.additionalGPUDataSetting;
        var dataBuffer = setting.hasDataRecord() ? instanceBackend.beginDataUpload(particles.size()) : null;
        var customBuffer = setting.hasCustomRecord() ? instanceBackend.beginCustomUpload(particles.size()) : null;

        var instanceCount = 0;
        var vec3 = camera.getPosition();
        for (var p : particles) {
            if (!(p instanceof TileParticle particle) || particle.getDelay() > 0) continue;
            instanceCount++;
            var localPos = particle.getSimPos(partialTicks).mulPosition(particle.getSpaceTransform());
            localPos = applyWholeEffectPosition(particle, localPos);
            var x = (float) (localPos.x - vec3.x);
            var y = (float) (localPos.y - vec3.y);
            var z = (float) (localPos.z - vec3.z);

            var color = particle.getRealColor(partialTicks);
            var rotation = particle.getRealRotation(partialTicks);
            var size = particle.getRealSize(partialTicks);
            var scale = particle.getSpaceScale();
            var light = particle.getRealLight(partialTicks);
            // The light cache is initialized lazily.  A freshly spawned particle can therefore
            // still report -1 on the instanced path; packed light coordinates are consumed by the
            // vertex shader as a texture lookup, where -1 turns into an out-of-range black sample.
            if (light < 0) {
                light = LightTexture.FULL_BRIGHT;
            }

            if (renderMode == ParticleRendererSetting.Mode.Model) {
                var quaternion = computeModelQuaternion(particle, rotation, camera, partialTicks);
                // pos vec3
                buffer.put(x).put(y).put(z);
                // scale vec3
                buffer.put(scale.x * size.x).put(scale.y * size.y).put(scale.z * size.z);
                // rot quat (vec4)
                buffer.put(quaternion.x).put(quaternion.y).put(quaternion.z).put(quaternion.w);
                // color vec4
                buffer.put(color.x).put(color.y).put(color.z).put(color.w);
                // light int
                buffer.put(Float.intBitsToFloat(light));
            } else {
                var uvs = particle.getRealUVs(partialTicks);

                Quaternionf quaternion;
                float finalSizeX = size.x;
                float finalSizeY = size.y;
                if (renderMode == ParticleRendererSetting.Mode.StretchedBillboard) {
                    var frame = computeStretchedFrame(particle, localPos, vec3.x, vec3.y, vec3.z, size, scale);
                    quaternion = frame.rotation();
                    finalSizeX = frame.stretchedSizeX();
                    x -= frame.offsetX();
                    y -= frame.offsetY();
                    z -= frame.offsetZ();
                } else {
                    quaternion = computeBillboardQuaternion(particle, renderMode, camera, partialTicks, rotation);
                }

                // pos vec3
                buffer.put(x).put(y).put(z);
                // size vec2
                buffer.put(finalSizeX).put(finalSizeY);
                // scale vec3
                buffer.put(scale.x).put(scale.y).put(scale.z);
                // rot quat (vec4)
                buffer.put(quaternion.x).put(quaternion.y).put(quaternion.z).put(quaternion.w);
                // color vec4
                buffer.put(color.x).put(color.y).put(color.z).put(color.w);
                // Same U order as the CPU quad; V and the atlas frame bounds are unchanged.
                boolean flipU = isDefaultCameraFacing(particle, renderMode);
                buffer.put(flipU ? uvs.z : uvs.x).put(uvs.w)
                        .put(flipU ? uvs.x : uvs.z).put(uvs.y);
                // light int
                buffer.put(Float.intBitsToFloat(light));
            }

            // legacy per-channel attributes (custom shaders), + packed record for the data TBO (shadergraph)
            if (setting.hasAttribs()) {
                setting.uploadAttribs(particle, buffer, partialTicks);
            }
            if (dataBuffer != null) {
                setting.uploadDataRecord(particle, dataBuffer, partialTicks);
            }
            if (customBuffer != null) {
                setting.uploadCustomRecord(particle, customBuffer, partialTicks);
            }
        }

        if (dataBuffer != null) {
            instanceBackend.endDataUpload(dataBuffer);
        }
        if (customBuffer != null) {
            instanceBackend.endCustomUpload(customBuffer);
        }
        instanceBackend.endUpload(buffer, instanceCount);
        return instanceCount > 0;
    }

    public void drawInstanced(ShaderInstance shader) {
        instanceBackend.drawWithAppliedShader(shader);
        var dynamic = renderer.getModelSource().asDynamic();
        if (renderer.getRenderMode() == ParticleRendererSetting.Mode.Model
                && !instancedVatActive
                && dynamic != null) {
            dynamic.onDrawn();
        }
    }

    private PhotonMesh instancedMesh() {
        IModelSource source = renderer.getModelSource();
        return source.vertexAnimation() == null ? source.getMesh() : source.topology();
    }

    /**
     * Full GL teardown of the instanced resources. Call when the render mode / model / instance
     * layout changes (not for capacity growth).
     */
    public void dispose() {
        instanceBackend.dispose();
    }

    // ---------------------------------------------------------------------
    // shared orientation math (used by BOTH the CPU and instanced paths)
    // ---------------------------------------------------------------------

    private StretchedBillboardMath.Frame computeStretchedFrame(TileParticle particle, Vector3f worldPos,
                                                 double camX, double camY, double camZ,
                                                 Vector3f size, Vector3f spaceScale) {
        var effect = particle.getEmitter().getEffectExecutor();
        var velocity = WholeEffectRenderSpace.referenceDirection(particle, particle.getRealVelocity());
        // worldPos already includes the root pose from spawning/simulation, in both render paths.
        var referencePos = effect instanceof IWholeEffectTransformer whole
                ? whole.removeWholeEffectPosition(new Vector3f(worldPos)) : worldPos;
        var frame = StretchedBillboardMath.compute(velocity, referencePos, camX, camY, camZ, size, spaceScale,
                renderer.getLengthScale(), renderer.getVelocityScale());
        if (!(effect instanceof IWholeEffectTransformer whole)) return frame;
        var offset = whole.applyWholeEffectDirection(new Vector3f(frame.offsetX(), frame.offsetY(), frame.offsetZ()));
        return new StretchedBillboardMath.Frame(whole.applyWholeEffectRotation(frame.rotation()),
                frame.stretchedSizeX(), offset.x, offset.y, offset.z);
    }

    private static boolean isDefaultCameraFacing(TileParticle particle, ParticleRendererSetting.Mode renderMode) {
        return renderMode == ParticleRendererSetting.Mode.Billboard
                && particle.getRuntime().renderer.getFacingMode()
                == com.lowdragmc.photon.client.gameobject.emitter.particle.FacingMode.DEFAULT;
    }

    private static Quaternionf computeBillboardQuaternion(TileParticle particle, ParticleRendererSetting.Mode renderMode,
                                                          Camera camera, float partialTicks, Vector3f rotation) {
        var quaternion = renderMode.quaternion.apply(particle, camera, partialTicks);
        if (particle.getEmitter().getEffectExecutor() instanceof IWholeEffectTransformer whole) {
            // Only the default camera-facing billboard opts out of whole-FX orientation.
            // Position still rotates; custom facing modes and Model animation keep their contracts.
            boolean keepCameraFacing = isDefaultCameraFacing(particle, renderMode);
            return whole.applyAnimatedBillboardRotation(quaternion, rotation, keepCameraFacing);
        }
        if (!Vector3fHelper.isZero(rotation)) {
            quaternion = ParticleRotationMath.withParticleRotation(quaternion, rotation);
        }
        return quaternion;
    }

    private static Quaternionf computeModelQuaternion(TileParticle particle, Vector3f rotation,
                                                       Camera camera, float partialTicks) {
        var renderer = particle.getRuntime().renderer;
        var facing = renderer.getFacingMode();
        var orientation = facing == FacingMode.DEFAULT
                ? particle.getSpaceRotation()
                : new Quaternionf(FacingOrientationHelper.compute(
                        facing, renderer.getFacingDirection(), particle, camera, partialTicks));
        var effect = particle.getEmitter().getEffectExecutor();
        if (effect instanceof IWholeEffectTransformer whole) {
            // Only the default orientation comes from the simulation space and can already include
            // the whole-effect rotation for local-space particles. Explicit facing is world-oriented.
            boolean spaceContainsWholeRotation = facing == FacingMode.DEFAULT
                    && particle.getConfig().getSimulationSpace() != ParticleConfig.Space.World;
            return whole.applyAnimatedModelRotation(orientation, rotation, spaceContainsWholeRotation);
        }
        return ParticleRotationMath.withParticleRotation(orientation, rotation);
    }

    private static Vector3f applyWholeEffectPosition(TileParticle particle, Vector3f worldPosition) {
        return WholeEffectRenderSpace.position(particle, worldPosition);
    }
}
