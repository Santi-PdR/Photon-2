package com.lowdragmc.photon.client.gameobject.forcefield;

import com.lowdragmc.lowdraglib2.client.utils.RenderBufferUtils;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigValueType;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.gameobject.FXObjectType;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.RuntimeBinding;
import com.lowdragmc.photon.client.gameobject.particle.IParticle;
import com.lowdragmc.photon.gui.editor.view.scene.SceneView;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

/**
 * A Unity-style particle force field: affects the particles of every sibling emitter (same FX scene)
 * whose External Forces module is enabled. Supports a shaped influence volume with range falloff,
 * directional force, gravity toward a focus point, vortex rotation, and drag.
 */
@OnlyIn(Dist.CLIENT)
@ParametersAreNonnullByDefault
public class ForceFieldObject extends FXObject {
    public static final IGuiTexture ICON = Icons.icon(Photon.MOD_ID, "force_field");
    @LDLRegisterClient(name = "force_field", registry = "photon:fx_object")
    public static final FXObjectType TYPE = new FXObjectType() {
        @Override
        public IFXObject create() {
            return new ForceFieldObject();
        }

        @Override
        public IGuiTexture icon() {
            return ICON;
        }

        @Override
        public List<RuntimeBinding> runtimeBindings() {
            return RUNTIME_BINDINGS;
        }
    };

    public static final List<RuntimeBinding> RUNTIME_BINDINGS = List.of(
            new RuntimeBinding("startRange", "ForceFieldConfig.startRange", ConfigValueType.FLOAT,
                    o -> ((ForceFieldObject) o).runtime().startRange),
            new RuntimeBinding("endRange", "ForceFieldConfig.endRange", ConfigValueType.FLOAT,
                    o -> ((ForceFieldObject) o).runtime().endRange),
            new RuntimeBinding("directionX", "ForceFieldConfig.directionX", ConfigValueType.NUMBER_FUNCTION,
                    o -> ((ForceFieldObject) o).runtime().directionX),
            new RuntimeBinding("directionY", "ForceFieldConfig.directionY", ConfigValueType.NUMBER_FUNCTION,
                    o -> ((ForceFieldObject) o).runtime().directionY),
            new RuntimeBinding("directionZ", "ForceFieldConfig.directionZ", ConfigValueType.NUMBER_FUNCTION,
                    o -> ((ForceFieldObject) o).runtime().directionZ),
            new RuntimeBinding("gravity", "ForceFieldConfig.gravity", ConfigValueType.NUMBER_FUNCTION,
                    o -> ((ForceFieldObject) o).runtime().gravity),
            new RuntimeBinding("gravityFocus", "ForceFieldConfig.gravityFocus", ConfigValueType.FLOAT,
                    o -> ((ForceFieldObject) o).runtime().gravityFocus),
            new RuntimeBinding("rotationSpeed", "ForceFieldConfig.rotationSpeed", ConfigValueType.NUMBER_FUNCTION,
                    o -> ((ForceFieldObject) o).runtime().rotationSpeed),
            new RuntimeBinding("rotationAttraction", "ForceFieldConfig.rotationAttraction", ConfigValueType.FLOAT,
                    o -> ((ForceFieldObject) o).runtime().rotationAttraction),
            new RuntimeBinding("drag", "ForceFieldConfig.drag", ConfigValueType.NUMBER_FUNCTION,
                    o -> ((ForceFieldObject) o).runtime().drag));

    @Persisted(subPersisted = true)
    public final ForceFieldConfig config = new ForceFieldConfig();

    /** Per-instance runtime layer: named override slots (timeline-driven) over the immutable config. */
    private ForceFieldConfig.Runtime runtime;

    // stable per-field keys for the per-particle vortex-axis randomness (memoized on the particle)
    private final Object rotationRandomKeyX = new Object();
    private final Object rotationRandomKeyZ = new Object();

    public ForceFieldConfig.Runtime runtime() {
        if (runtime == null) {
            runtime = new ForceFieldConfig.Runtime(config);
        }
        return runtime;
    }

    @Override
    public FXObjectType getFXObjectType() {
        return TYPE;
    }

    /** Whether this field has been removed (leaf objects can't use the child-based {@code isAlive}). */
    public boolean isRemoved() {
        return this.removed;
    }

    @Override
    public IGuiTexture getIcon() {
        return ICON;
    }

    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        super.buildConfigurator(father);
        config.buildConfigurator(father);
    }

    @Override
    public void reset() {
        super.reset();
        if (runtime != null) {
            runtime.clear();
        }
    }

    @Override
    protected void onTickBegin() {
        // Transform's lazy matrix caches are not synchronized; warm them on the game thread
        // before particles read them via parallelStream.
        transform().localToWorldMatrix();
        transform().worldToLocalMatrix();
        transform().rotation();
    }

    /**
     * Evaluate this field for one particle and fold the result into {@code worldVelocity} (mutated in
     * place: direction/gravity/vortex integrate as force * dt, drag damps). Thread-safe: read-only on
     * the field's state; per-particle randoms are memoized on the particle.
     *
     * @param worldPos      the particle's world position
     * @param worldVelocity the particle's stored velocity in world space (in/out)
     * @param particleSize  the particle's current size (max component)
     * @param dt            step in ticks
     * @param particle      the affected particle (lifetime t + memoized randoms)
     * @param multiplier    the emitter's external-forces multiplier for this particle
     */
    public void apply(Vector3f worldPos, Vector3f worldVelocity, float particleSize, float dt, IParticle particle, float multiplier) {
        var rt = runtime();
        // shape membership + falloff are tested in field-local space, so the field's transform
        // (including scale) shapes the influence volume
        var local = new Vector3f(worldPos).mulPosition(transform().worldToLocalMatrix());
        var endRange = rt.getEndRange();
        float strength = ForceFieldPhysics.influence(rt.getShape(), local, rt.getStartRange(), endRange) * multiplier;
        if (strength == 0) {
            return;
        }

        var t = particle.getT();
        var localToWorld = transform().localToWorldMatrix();
        float directionX = rt.getDirectionX(particle, t);
        float directionY = rt.getDirectionY(particle, t);
        float directionZ = rt.getDirectionZ(particle, t);
        float gravity = rt.getGravity(particle, t);
        float gravityFocus = rt.getGravityFocus();
        var rotationSpeed = rt.getRotationSpeed(particle, t);
        float rotationAnchorX = 0;
        float rotationAnchorZ = 0;
        if (rotationSpeed != 0) {
            rotationAnchorX = config.getRotationRandomnessX()
                    * particle.getMemRandom(rotationRandomKeyX, rand -> rand.nextFloat() * 2 - 1);
            rotationAnchorZ = config.getRotationRandomnessZ()
                    * particle.getMemRandom(rotationRandomKeyZ, rand -> rand.nextFloat() * 2 - 1);
        }
        var drag = rt.getDrag(particle, t);
        ForceFieldPhysics.apply(local, localToWorld, worldVelocity, particleSize, dt, strength,
                directionX, directionY, directionZ, gravity, gravityFocus,
                rotationSpeed, rt.getRotationAttraction(), rotationAnchorX, rotationAnchorZ,
                drag, config.isMultiplyDragByParticleSize(), config.isMultiplyDragByParticleVelocity(), endRange);
    }

    @Override
    public void drawEditorAfterWorld(SceneView.ParticleSceneEditor scene, MultiBufferSource bufferSource, float partialTicks) {
        if (!scene.sceneView().isShapeVisible()) {
            return;
        }
        var poseStack = new PoseStack();
        poseStack.mulPoseMatrix(transform().localToWorldMatrix());
        var rt = runtime();
        var endRange = rt.getEndRange();
        var startRange = Math.min(rt.getStartRange(), endRange);
        drawRange(poseStack, ForceFieldGizmos.getGuideLines(config.getShape(), endRange), ColorPattern.YELLOW.color);
        if (startRange > 0) {
            drawRange(poseStack, ForceFieldGizmos.getGuideLines(config.getShape(), startRange), ColorPattern.GRAY.color);
        }
    }

    private static void drawRange(PoseStack poseStack, List<oshi.util.tuples.Pair<Vector3f, Vector3f>> edges, int color) {
        if (edges.isEmpty()) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        var buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        RenderSystem.lineWidth(5);

        RenderBufferUtils.drawEdges(poseStack, buffer, edges, color);

        var meshData = buffer.endOrDiscardIfEmpty();
        if (meshData != null) {
            BufferUploader.drawWithShader(meshData);
        }
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }
}
