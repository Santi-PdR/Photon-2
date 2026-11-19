package com.lowdragmc.photon.client.gameobject.emitter.data.material.kila;

import com.lowdragmc.photon.client.gameobject.emitter.data.material.BlendMode;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Runtime-free regression for Kila blend selection and safe handling of nullable persisted enums. */
@OnlyIn(Dist.CLIENT)
public final class KilaBlendRegression {
    private KilaBlendRegression() {
    }

    public static void verify() {
        com.lowdragmc.photon.client.gameobject.emitter.data.material.ShaderGraphMaterialRegression.verify();
        com.lowdragmc.photon.client.gameobject.emitter.data.material.kila.export.KilaGraphExportRegression.verify();
        KilaLayoutRegression.verify();
        var nullEnums = new KilaMaterial();
        nullEnums.blend = null;
        nullEnums.vertexColor = null;
        nullEnums.debugView = null;
        nullEnums.previewBackground = null;
        nullEnums.color = null;
        nullEnums.backColor = null;
        nullEnums.emission = null;
        if (nullEnums.effectiveColor() == null || nullEnums.effectiveBackColor() == null
                || nullEnums.effectiveEmission() == null
                || nullEnums.effectiveBlend() != BlendMode.Preset.ALPHA
                || nullEnums.effectiveVertexColor() != KilaMaterial.VertexColor.MULTIPLY
                || nullEnums.effectiveDebugView() != KilaMaterial.DebugView.OFF
                || nullEnums.effectivePreviewBackground() != KilaMaterial.PreviewBackground.CHECKER) {
            throw new IllegalStateException("Kila did not apply safe defaults for null material enums");
        }

        if (KilaMaterial.safeScreenDimension(0) != 1 || KilaMaterial.safeScreenDimension(-10) != 1
                || KilaMaterial.safeScreenDimension(1920) != 1920
                || KilaMaterial.finite(Float.NaN) != 0f || KilaMaterial.finite(Float.NEGATIVE_INFINITY) != 0f
                || KilaMaterial.finite(2_000_000f) != 1_000_000f || KilaMaterial.finite(-2_000_000f) != -1_000_000f
                || KilaModule.finite(Float.NaN, 1f, 0f, 4f) != 1f
                || KilaModule.finite(Float.POSITIVE_INFINITY, 1f, 0f, 4f) != 1f
                || KilaModule.finite(-2f, 1f, 0f, 4f) != 0f
                || KilaModule.finite(8f, 1f, 0f, 4f) != 4f) {
            throw new IllegalStateException("Kila material scalar settings did not recover from invalid or out-of-range values");
        }

        var nullTextureEnums = new KilaTexture();
        nullTextureEnums.uvSource = null;
        nullTextureEnums.noise = null;
        nullTextureEnums.colorMode = null;
        nullTextureEnums.channel = null;
        nullTextureEnums.wrapU = null;
        nullTextureEnums.wrapV = null;
        nullTextureEnums.tiling = null;
        nullTextureEnums.offset = null;
        nullTextureEnums.scroll = null;
        nullTextureEnums.polarCenter = null;
        if (nullTextureEnums.effectiveUvSource() != KilaTexture.UvSource.MESH
                || nullTextureEnums.effectiveNoise() != KilaTexture.Noise.TEXTURE
                || nullTextureEnums.effectiveColorMode() != KilaTexture.ColorMode.RGBA
                || nullTextureEnums.effectiveChannel() != KilaTexture.Channel.R
                || nullTextureEnums.effectiveWrapU() != KilaTexture.Wrap.REPEAT
                || nullTextureEnums.effectiveWrapV() != KilaTexture.Wrap.REPEAT
                || nullTextureEnums.effectiveTiling().x != 1 || nullTextureEnums.effectiveTiling().y != 1
                || nullTextureEnums.effectiveOffset().lengthSquared() != 0
                || nullTextureEnums.effectiveScroll().lengthSquared() != 0
                || nullTextureEnums.effectivePolarCenter().x != 0.5f
                || nullTextureEnums.effectivePolarCenter().y != 0.5f
                || nullTextureEnums.isProcedural() || nullTextureEnums.usesWorld()) {
            throw new IllegalStateException("Kila texture did not apply safe defaults for null enums");
        }
        nullTextureEnums.flags();
        nullTextureEnums.cellWrap();
        nullTextureEnums.structureHash();
        nullTextureEnums.glSampler();

        nullTextureEnums.tiling = new org.joml.Vector2f(Float.NaN, 200_000);
        nullTextureEnums.offset = new org.joml.Vector2f(-200_000, Float.NaN);
        nullTextureEnums.scroll = new org.joml.Vector2f(Float.POSITIVE_INFINITY, -200_000);
        nullTextureEnums.polarCenter = new org.joml.Vector2f(Float.NaN, -200_000);
        nullTextureEnums.rotation = Float.NaN;
        nullTextureEnums.rotationSpeed = Float.POSITIVE_INFINITY;
        nullTextureEnums.polarRadial = Float.NaN;
        nullTextureEnums.polarAngular = Float.POSITIVE_INFINITY;
        nullTextureEnums.noiseScale = Float.NaN;
        nullTextureEnums.noiseMotion = Float.POSITIVE_INFINITY;
        if (nullTextureEnums.effectiveTiling().x != 1 || nullTextureEnums.effectiveTiling().y != 100_000
                || nullTextureEnums.effectiveOffset().x != -100_000 || nullTextureEnums.effectiveOffset().y != 0
                || nullTextureEnums.effectiveScroll().x != 0 || nullTextureEnums.effectiveScroll().y != -100_000
                || nullTextureEnums.effectivePolarCenter().x != 0.5f
                || nullTextureEnums.effectivePolarCenter().y != -100_000
                || nullTextureEnums.effectiveRotation() != 0 || nullTextureEnums.effectiveRotationSpeed() != 0
                || nullTextureEnums.effectivePolarRadial() != 1 || nullTextureEnums.effectivePolarAngular() != 1
                || nullTextureEnums.effectiveNoiseScale() != 8 || nullTextureEnums.effectiveNoiseMotion() != 1) {
            throw new IllegalStateException("Kila texture export values did not recover from non-finite persisted vectors");
        }

        var directionTexture = new KilaDissolve().directionTexture;
        directionTexture.tiling = null;
        directionTexture.offset = null;
        directionTexture.channel = null;
        if (directionTexture.effectiveTiling().x != 1 || directionTexture.effectiveTiling().y != 1
                || directionTexture.effectiveOffset().lengthSquared() != 0
                || directionTexture.effectiveChannel() != KilaTexture.Channel.R) {
            throw new IllegalStateException("Dissolve direction texture did not apply safe vector/channel defaults");
        }

        var layer = new KilaLayer();
        var secondLayer = new KilaLayer(2);
        layer.blend = null;
        secondLayer.blend = null;
        var ramp = new KilaRamp();
        ramp.input = null;
        ramp.mode = null;
        var matcap = new KilaMatcap();
        matcap.blend = null;
        var fresnel = new KilaFresnel();
        fresnel.mode = null;
        fresnel.color = null;
        fresnel.offset = null;
        var shading = new KilaShading();
        var zeroLightDirection = KilaShading.safeLightDirection(new org.joml.Vector3f());
        var corruptLightDirection = KilaShading.safeLightDirection(new org.joml.Vector3f(Float.NaN, 0f, Float.POSITIVE_INFINITY));
        if (!Float.isFinite(zeroLightDirection.x) || !Float.isFinite(zeroLightDirection.y)
                || !Float.isFinite(zeroLightDirection.z) || Math.abs(zeroLightDirection.lengthSquared() - 1f) > 1e-5f
                || !Float.isFinite(corruptLightDirection.x) || !Float.isFinite(corruptLightDirection.y)
                || !Float.isFinite(corruptLightDirection.z)
                || Math.abs(corruptLightDirection.lengthSquared() - 1f) > 1e-5f)
            throw new IllegalStateException("Kila shading did not recover a zero or non-finite key-light direction");
        shading.normal = null;
        shading.lightColor = null;
        shading.lightDirection = null;
        shading.bump = null;
        shading.model = null;
        var projection = new KilaProjection();
        projection.orientation = null;
        var distortion = new KilaDistortion();
        distortion.mask = null;
        distortion.mode = null;
        distortion.amount = null;
        var dissolve = new KilaDissolve();
        dissolve.direction = null;
        dissolve.edge = null;
        dissolve.edgeColor = null;
        var depth = new KilaDepth();
        depth.contactColor = null;
        depth.contactBreakup = Float.POSITIVE_INFINITY;
        var invalidContactTextures = new java.util.ArrayList<KilaTexture>();
        depth.textures(invalidContactTextures::add);
        depth.measure = null;
        var contactProbe = new KilaDepth();
        contactProbe.contactGradient = true;
        contactProbe.contactBreakup = Float.POSITIVE_INFINITY;
        var idleContactLayout = KilaLayout.surface().build(java.util.List.of(contactProbe));
        contactProbe.contactWidth.value = 1f;
        contactProbe.contactStrength.value = 0f;
        var zeroStrengthLayout = KilaLayout.surface().build(java.util.List.of(contactProbe));
        contactProbe.contactStrength.value = 1f;
        var activeContactLayout = KilaLayout.surface().build(java.util.List.of(contactProbe));
        if (idleContactLayout.defines.contains("KILA_CONTACT_RAMP") || !idleContactLayout.textures.isEmpty()
                || zeroStrengthLayout.defines.contains("KILA_CONTACT_RAMP") || !zeroStrengthLayout.textures.isEmpty()
                || !activeContactLayout.defines.contains("KILA_CONTACT_RAMP") || activeContactLayout.textures.size() != 1) {
            throw new IllegalStateException("Disabled contact glow allocated gradient resources or failed to enable them");
        }
        var edgeProbe = new KilaDissolve();
        edgeProbe.edgeGradient = true;
        edgeProbe.edgeWidth.value = 0f;
        var idleEdgeLayout = KilaLayout.surface().build(java.util.List.of(edgeProbe));
        edgeProbe.edgeWidth.value = 0.1f;
        var activeEdgeLayout = KilaLayout.surface().build(java.util.List.of(edgeProbe));
        if (idleEdgeLayout.defines.contains("KILA_DISSOLVE_RAMP")
                || !activeEdgeLayout.defines.contains("KILA_DISSOLVE_RAMP")
                || activeEdgeLayout.textures.size() != idleEdgeLayout.textures.size() + 1) {
            throw new IllegalStateException("Dormant dissolve edge gradient consumed or failed to acquire its ramp sampler");
        }
        depth.contactMode = null;
        var vertexOffset = new KilaVertexOffset();
        vertexOffset.direction = null;
        vertexOffset.axis = new org.joml.Vector3f(Float.NaN, Float.POSITIVE_INFINITY, 0f);
        var distortionAmount = new KilaDistortion();
        distortionAmount.amount = new org.joml.Vector2f(Float.NaN, Float.POSITIVE_INFINITY);
        var dormantFlipbook = new KilaMainTexture();
        long noFlipbookStructure = dormantFlipbook.structure();
        dormantFlipbook.frameBlend = true;
        if (dormantFlipbook.structure() != noFlipbookStructure) {
            throw new IllegalStateException("A dormant flipbook blend option triggered a needless shader variant");
        }
        var mainTexture = new KilaMainTexture();
        mainTexture.frameSource = null;
        mainTexture.dispersionDirection = null;
        var screenDistortion = new KilaScreenDistortion();
        screenDistortion.source = null;
        screenDistortion.chromatic = Float.NaN;
        var uvEffects = new KilaUvEffects();
        uvEffects.waveAxis = null;
        uvEffects.twirlCenter = null;
        uvEffects.shear = null;
        var screenTextures = new java.util.ArrayList<KilaTexture>();
        screenDistortion.textures(screenTextures::add);
        var dissolveTextures = new java.util.ArrayList<KilaTexture>();
        dissolve.textures(dissolveTextures::add);
        if (layer.effectiveBlend() != KilaLayer.Blend.MULTIPLY
                || secondLayer.effectiveBlend() != KilaLayer.Blend.ADD
                || ramp.effectiveInput() != KilaRamp.Input.LUMINANCE
                || ramp.effectiveMode() != KilaRamp.Mode.REPLACE
                || matcap.effectiveBlend() != KilaMatcap.Blend.ADD
                || fresnel.effectiveMode() != KilaFresnel.Mode.RIM || fresnel.effectiveColor() == null
                || fresnel.effectiveOffset().lengthSquared() != 0
                || shading.effectiveLightColor() == null
                || shading.effectiveLightDirection().x != 0.5f || shading.effectiveLightDirection().y != 1
                || shading.effectiveLightDirection().z != 0.35f
                || shading.effectiveNormal() != KilaShading.Normal.SPHERE
                || shading.effectiveBump() != KilaShading.Bump.NONE
                || shading.effectiveModel() != KilaShading.Model.LAMBERT
                || projection.effectiveOrientation() != KilaProjection.Orientation.GROUND
                || distortion.effectiveMask() != KilaDistortion.Mask.NONE
                || distortion.effectiveMode() != KilaDistortion.Mode.OFFSET
                || distortion.effectiveAmount().x != 1 || distortion.effectiveAmount().y != 1
                || dissolve.effectiveDirection() != KilaDissolve.Direction.NONE
                || dissolve.effectiveEdge() != KilaDissolve.Edge.ADD || dissolve.effectiveEdgeColor() == null
                || depth.effectiveContactColor() == null || depth.effectiveMeasure() != KilaDepth.Measure.VIEW
                || depth.effectiveContactMode() != KilaDepth.ContactMode.ADD
                || vertexOffset.effectiveDirection() != KilaVertexOffset.Direction.NORMAL
                || vertexOffset.effectiveAxis().x != 0 || vertexOffset.effectiveAxis().y != 1
                || vertexOffset.effectiveAxis().z != 0
                || !Float.isFinite(distortionAmount.effectiveAmount().x)
                || !Float.isFinite(distortionAmount.effectiveAmount().y)
                || mainTexture.effectiveFrameSource() != KilaMainTexture.FrameSource.TIME
                || mainTexture.effectiveDispersionDirection().x != 1
                || mainTexture.effectiveDispersionDirection().y != 0
                || screenDistortion.effectiveSource() != KilaScreenDistortion.Source.TEXTURE
                || screenDistortion.effectiveChromatic() != 0f || (screenDistortion.structure() & 2) != 0
                || screenTextures.size() != 1 || uvEffects.effectiveWaveAxis() != KilaUvEffects.WaveAxis.U
                || uvEffects.effectiveTwirlCenter().x != 0.5f || uvEffects.effectiveTwirlCenter().y != 0.5f
                || uvEffects.effectiveShear().lengthSquared() != 0
                || dissolve.structure() != 0 || dissolveTextures.size() != 1
                || depth.structure() != 0 || !invalidContactTextures.isEmpty()
                || shading.structure() != 0 || distortion.structure() != 0) {
            throw new IllegalStateException("A Kila module did not apply its safe enum default");
        }

        var nullState = new com.lowdragmc.photon.client.gameobject.emitter.data.material.MaterialRenderState(
                null, true, true, false);
        var nullGraphState = new com.lowdragmc.photon.client.gameobject.emitter.data.material.ShaderGraphMaterial.RenderState(
                null, false, false, true, false);
        var nullMaterialSlot = new com.lowdragmc.photon.client.gameobject.emitter.data.MaterialSetting(null);
        nullMaterialSlot.setRenderStateSource(null);
        if (nullMaterialSlot.effectiveRenderStateSource()
                != com.lowdragmc.photon.client.gameobject.emitter.data.MaterialSetting.RenderStateSource.MATERIAL
                || BlendMode.Preset.of(nullState.blendMode()) != BlendMode.Preset.ALPHA
                || nullGraphState.effectiveBlend() != BlendMode.Preset.ALPHA
                || BlendMode.Preset.of(nullGraphState.create().blendMode()) != BlendMode.Preset.ALPHA
                || nullMaterialSlot.getMaterial() != com.lowdragmc.photon.client.gameobject.emitter.data.material.IMaterial.MISSING
                || nullMaterialSlot.getFollowedRenderState() != null || nullMaterialSlot.getEffectiveBlendMode() == null) {
            throw new IllegalStateException("A material render-state or slot did not recover from a null reference");
        }

        for (var preset : BlendMode.Preset.values()) {
            var resolved = KilaMaterial.resolveShaderBlend(true, preset, false, BlendMode.Preset.MULTIPLY);
            if (resolved != preset) {
                throw new IllegalStateException("Kila ignored the slot-owned " + preset + " blend preset: " + resolved);
            }
        }
        if (KilaMaterial.resolveShaderBlend(true, null, false, BlendMode.Preset.MULTIPLY)
                != BlendMode.Preset.ALPHA) {
            throw new IllegalStateException("Kila custom slot blend did not use the documented alpha shader fallback");
        }
        if (KilaMaterial.resolveShaderBlend(false, BlendMode.Preset.OPAQUE, true, BlendMode.Preset.MULTIPLY)
                != BlendMode.Preset.ALPHA) {
            throw new IllegalStateException("Kila material-owned render state did not preserve its alpha shader contract");
        }
        if (KilaMaterial.resolveShaderBlend(false, null, false, BlendMode.Preset.MULTIPLY)
                != BlendMode.Preset.MULTIPLY) {
            throw new IllegalStateException("Kila stopped using its material blend when no slot state owns the draw");
        }
    }
}
