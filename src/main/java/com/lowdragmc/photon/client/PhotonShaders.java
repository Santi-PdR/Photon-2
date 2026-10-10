package com.lowdragmc.photon.client;

import com.lowdragmc.lowdraglib2.client.shader.LDLibShaders;
import com.lowdragmc.lowdraglib2.client.shader.LDProgramDefineManager;
import com.lowdragmc.lowdraglib2.client.shader.LDShaderInstance;
import com.lowdragmc.lowdraglib2.client.shader.management.Shader;
import com.lowdragmc.lowdraglib2.client.shader.management.ShaderProgram;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.compat.iris.IrisCompat;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.MaterialContext;
import com.lowdragmc.photon.client.postfx.shadergraph.runtime.FullscreenGraphRuntime;
import com.lowdragmc.photon.client.shadergraph.runtime.ShaderGraphRuntime;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.shaders.Program;
import lombok.Getter;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterShadersEvent;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public class PhotonShaders {
    private static final Map<String, ShaderInstance> HDR_PARTICLE_VARIANTS = new ConcurrentHashMap<>();
    private static final Map<String, ShaderInstance> PIXEL_HDR_PARTICLE_VARIANTS = new ConcurrentHashMap<>();
    private static Shader CATMULL_ROM;
    private static ShaderProgram CATMULL_ROM_PROGRAM;
    @Getter
    private static ShaderInstance HDRParticleShader;
    @Getter
    private static ShaderInstance spriteHDRParticleShader;
    @Getter
    private static ShaderInstance pixelHDRParticleShader;
    @Getter
    private static ShaderInstance brightPassShader;
    @Getter
    private static ShaderInstance downSamplingShader;
    @Getter
    private static ShaderInstance upSamplingShader;
//    @Getter
//    private static ShaderInstance separableBlurShader;
//    @Getter
//    private static ShaderInstance bloomAddPassShader;
//    @Getter
//    private static ShaderInstance bloomScatterPassShader;
    @Getter
    private static ShaderInstance bloomFinalScatterPassShader;
    @Getter
    private static ShaderInstance weightMixShader;
    @Getter
    private static ShaderInstance weightMaskMixShader;
    @Getter
    private static ShaderInstance maskUnionShader;

    /**
     * Return an HDR particle shader whose vertex inputs match the active render path. Shader Graph
     * and custom-shader materials use this as an error fallback; returning the registered base
     * shader for an instanced draw would interpret model-instance attributes as ordinary vertices.
     */
    public static ShaderInstance getHDRParticleShader(MaterialContext context) {
        return getHDRParticleShader(context, false);
    }

    public static ShaderInstance getHDRParticleShader(MaterialContext context, boolean pixelArt) {
        var base = pixelArt ? pixelHDRParticleShader : HDRParticleShader;
        var defines = context.getShaderDefines();
        if (defines.isEmpty()) return base;

        var cache = pixelArt ? PIXEL_HDR_PARTICLE_VARIANTS : HDR_PARTICLE_VARIANTS;
        var variantKey = context.getVariantKey();
        return cache.computeIfAbsent(variantKey, key -> {
            var baseShader = base;
            if (baseShader != null) {
                Program.Type.FRAGMENT.getPrograms().remove(baseShader.getFragmentProgram().getName());
                Program.Type.VERTEX.getPrograms().remove(baseShader.getVertexProgram().getName());
            }
            defines.forEach(LDProgramDefineManager::addProgramDefine);
            try {
                return LDShaderInstance.create(Photon.id(pixelArt ? "pixel_hdr_particle" : "hdr_particle"),
                        DefaultVertexFormat.BLOCK);
            } catch (Throwable e) {
                Photon.LOGGER.error("Failed to create {} HDR particle fallback for render variant {}",
                        pixelArt ? "pixel-art" : "", variantKey, e);
                return baseShader;
            } finally {
                defines.forEach(LDProgramDefineManager::removeProgramDefine);
            }
        });
    }
    @Getter
    private static ShaderInstance irisCompositeShader;

    public static void init() {
        if (LDLibShaders.supportComputeShader()) {
            CATMULL_ROM = LDLibShaders.load(Shader.ShaderType.COMPUTE, Photon.id("catmull_rom"));
        }
    }

    public static ShaderProgram getCatmullRomProgram() {
        if (CATMULL_ROM_PROGRAM == null) {
            CATMULL_ROM_PROGRAM = new ShaderProgram();
            CATMULL_ROM_PROGRAM.attach(CATMULL_ROM);
        }
        return CATMULL_ROM_PROGRAM;
    }

    public static void registerShaders(RegisterShadersEvent registerShadersEvent) {
        // fires on every resource reload — drop lazily-loaded custom pass shaders so they re-resolve,
        // and compact the mask-group id table (ids are per-frame-resolved, safe to reassign)
        com.lowdragmc.photon.client.postfx.runtime.CustomShaderPass.clearAll();
        com.lowdragmc.photon.client.postfx.runtime.MaskGroups.clearAll();
        // compiled effects embed custom-shader port bindings — recompile against the fresh files
        com.lowdragmc.photon.client.postfx.runtime.RenderGraphRuntime.invalidateAll();
        // graph shader programs own GL resources too; close even cache entries whose resources were
        // removed from the pack and therefore will never be resolved again by their old path.
        ShaderGraphRuntime.invalidateAll();
        FullscreenGraphRuntime.invalidateAll();
        // a resource reload can follow a shader-pack reload, which recreates every Iris render
        // target — drop the resolved layout and the composite framebuffer with it
        IrisCompat.invalidate();
        var resourceProvider = registerShadersEvent.getResourceProvider();
        try {
            registerShadersEvent.registerShader(new ShaderInstance(resourceProvider,
                            Photon.id("hdr_particle"), DefaultVertexFormat.BLOCK),
                    shaderInstance -> HDRParticleShader = shaderInstance);
            registerShadersEvent.registerShader(new ShaderInstance(resourceProvider,
                            Photon.id("sprite_hdr_particle"), DefaultVertexFormat.BLOCK),
                    shaderInstance -> spriteHDRParticleShader = shaderInstance);
            registerShadersEvent.registerShader(new ShaderInstance(resourceProvider,
                            Photon.id("pixel_hdr_particle"), DefaultVertexFormat.BLOCK),
                    shaderInstance -> pixelHDRParticleShader = shaderInstance);
            registerShadersEvent.registerShader(new ShaderInstance(resourceProvider,
                            Photon.id("bright_pass"), DefaultVertexFormat.POSITION),
                    shaderInstance -> brightPassShader = shaderInstance);
            registerShadersEvent.registerShader(new ShaderInstance(resourceProvider,
                            Photon.id("down_sampling"), DefaultVertexFormat.POSITION),
                    shaderInstance -> downSamplingShader = shaderInstance);
            registerShadersEvent.registerShader(new ShaderInstance(resourceProvider,
                            Photon.id("up_sampling"), DefaultVertexFormat.POSITION),
                    shaderInstance -> upSamplingShader = shaderInstance);
//            registerShadersEvent.registerShader(new ShaderInstance(resourceProvider,
//                            Photon.id("separable_blur"), DefaultVertexFormat.POSITION),
//                    shaderInstance -> separableBlurShader = shaderInstance);
//            registerShadersEvent.registerShader(new ShaderInstance(resourceProvider,
//                            Photon.id("bloom_add_pass"), DefaultVertexFormat.POSITION),
//                    shaderInstance -> bloomAddPassShader = shaderInstance);
//            registerShadersEvent.registerShader(new ShaderInstance(resourceProvider,
//                            Photon.id("bloom_scatter_pass"), DefaultVertexFormat.POSITION),
//                    shaderInstance -> bloomScatterPassShader = shaderInstance);
            registerShadersEvent.registerShader(new ShaderInstance(resourceProvider,
                            Photon.id("bloom_final_scatter_pass"), DefaultVertexFormat.POSITION),
                    shaderInstance -> bloomFinalScatterPassShader = shaderInstance);
            registerShadersEvent.registerShader(new ShaderInstance(resourceProvider,
                            Photon.id("weight_mix"), DefaultVertexFormat.POSITION),
                    shaderInstance -> weightMixShader = shaderInstance);
            registerShadersEvent.registerShader(new ShaderInstance(resourceProvider,
                            Photon.id("weight_mask_mix"), DefaultVertexFormat.POSITION),
                    shaderInstance -> weightMaskMixShader = shaderInstance);
            registerShadersEvent.registerShader(new ShaderInstance(resourceProvider,
                            Photon.id("mask_union"), DefaultVertexFormat.POSITION),
                    shaderInstance -> maskUnionShader = shaderInstance);
            registerShadersEvent.registerShader(new ShaderInstance(resourceProvider,
                            Photon.id("iris_composite"), DefaultVertexFormat.POSITION),
                    shaderInstance -> irisCompositeShader = shaderInstance);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
