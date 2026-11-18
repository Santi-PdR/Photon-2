package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.client.shader.LDProgramDefineManager;
import com.lowdragmc.lowdraglib2.client.shader.LDShaderHolder;
import com.lowdragmc.lowdraglib2.client.shader.LDShaderInstance;
import com.lowdragmc.lowdraglib2.configurator.ConfiguratorParser;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.gui.texture.DynamicTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.UISurface;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.AutoCloseCleaner;
import com.lowdragmc.photon.client.fx.PreloadContext;
import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline;
import com.lowdragmc.photon.client.util.PhotonAssetPath;
import com.lowdragmc.photon.client.util.PhotonPaths;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.shaders.Uniform;
import dev.vfyjxf.taffy.style.AlignItems;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.File;
import java.lang.ref.Cleaner;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
@ParametersAreNonnullByDefault
@LDLRegisterClient(name = "custom_shader", registry = "photon:material")
public class CustomShaderMaterial extends ShaderInstanceMaterial {
    public final static int MAX_SAMPLER = 128;
    public final static int MAX_SAMPLING = 128;

    @Getter
    @Persisted
    private ResourceLocation shaderLocation = Photon.id("circle");
    @Configurable(name = "SamplerCurve", subConfigurable = true)
    public final CurveTexture curveTexture = new CurveTexture(MAX_SAMPLING, MAX_SAMPLER);
    @Configurable(name = "SamplerGradient", subConfigurable = true)
    public final GradientTexture gradientTexture = new GradientTexture(MAX_SAMPLING, MAX_SAMPLER);
    @Nullable
    private LDShaderHolder shaderHolder;
    // Keep authored data even when the external shader is missing or cannot compile.
    @Nullable
    private CompoundTag unavailableShaderData;
    private boolean declaredSceneColor;
    private boolean declaredSceneDepth;
    private Cleaner.Cleanable shaderCleanable;
    @Getter
    private String compiledErrorMessage = "";
    private String previewErrorText = "";
    @Nullable
    private TextTexture previewErrorTexture;
    private final IGuiTexture previewTexture = DynamicTexture.of(this::previewTextureImage);
    /**
     * The shader's json lists {@code PhotonLightData}, as one including {@code photon:lit_particle.glsl} has to: the
     * dynamic lights are bound to it, out of the inspector and the save.
     */
    @Getter
    private boolean litByDynamicLights;
    private final Map<String, float[]> uniformOverrides = new HashMap<>();
    private final Set<String> warnedFallbackVariants = new java.util.HashSet<>();
    private final Set<String> legacyHdrUniforms = new java.util.HashSet<>();
    private final Cleaner.Cleanable samplerTexturesCleanable;

    public CustomShaderMaterial() {
        // Curve/gradient samplers own DynamicTextures independently from the shader holder. Tie their
        // native images and GL handles to this material's lifetime as well.
        samplerTexturesCleanable = AutoCloseCleaner.registerRenderThread(this,
                new DynamicSamplerTextures(curveTexture, gradientTexture));
    }

    public CustomShaderMaterial(@Nullable ResourceLocation shaderLocation) {
        this();
        this.shaderLocation = shaderLocation == null ? Photon.id("circle") : shaderLocation;
    }

    private record DynamicSamplerTextures(CurveTexture curves, GradientTexture gradients) implements AutoCloseable {
        @Override
        public void close() {
            try {
                curves.close();
            } finally {
                gradients.close();
            }
        }
    }

    /** A missing serialized shader id falls back to the built-in particle shader instead of crashing editor/render paths. */
    public ResourceLocation getShaderLocation() {
        if (shaderLocation == null) shaderLocation = Photon.id("circle");
        return shaderLocation;
    }

    public void setShader(@Nullable ResourceLocation shaderLocation) {
        var resolved = shaderLocation == null ? Photon.id("circle") : shaderLocation;
        if (!Objects.equals(getShaderLocation(), resolved)) {
            legacyHdrUniforms.clear();
            unavailableShaderData = null;
        }
        this.shaderLocation = resolved;
        recompile();
    }

    @Override
    public void setupUniform(MaterialContext context) {
        super.setupUniform(context);
    }

    @Override
    public IMaterial copy() {
        var copied = new CustomShaderMaterial(getShaderLocation());
        var data = serializeAdditionalNBT(Platform.getFrozenRegistry());
        copied.deserializeAdditionalNBT(data, Platform.getFrozenRegistry());
        return copied;
    }

    @Override
    public Tag serializeAdditionalNBT(HolderLookup.@NotNull Provider provider) {
        var shaderData = new CompoundTag();
        if (unavailableShaderData != null) {
            shaderData.put("shaderData", unavailableShaderData.copy());
        } else if (shaderHolder != null) {
            shaderData.put("shaderData", authoredShaderData(provider));
        }
        if (!uniformOverrides.isEmpty()) {
            shaderData.put("uniformOverrides", CustomShaderUniformNbt.write(uniformOverrides));
        }
        if (!legacyHdrUniforms.isEmpty()) {
            var names = new ListTag();
            legacyHdrUniforms.stream().sorted().forEach(name -> names.add(StringTag.valueOf(name)));
            shaderData.put("legacyHdrUniforms", names);
        }
        return shaderData;
    }

    private CompoundTag authoredShaderData(HolderLookup.Provider provider) {
        var data = Objects.requireNonNull(shaderHolder).serializeNBT(provider);
        if (litByDynamicLights) LitParticles.UNIFORMS.forEach(data.getCompound("uniforms")::remove);
        return data;
    }

    @Override
    public void deserializeAdditionalNBT(Tag tag, HolderLookup.@NotNull Provider provider) {
        uniformOverrides.clear();
        legacyHdrUniforms.clear();
        unavailableShaderData = tag instanceof CompoundTag material && material.contains("shaderData", Tag.TAG_COMPOUND)
                ? material.getCompound("shaderData").copy() : null;
        if (tag instanceof CompoundTag materialData) {
            var names = materialData.getList("legacyHdrUniforms", Tag.TAG_STRING);
            for (int i = 0; i < names.size(); i++) legacyHdrUniforms.add(names.getString(i));
            if (materialData.contains("shaderData", Tag.TAG_COMPOUND)) {
                var legacyShaderData = materialData.getCompound("shaderData");
                if (CustomShaderUniformNbt.isPhoton262LegacyShaderData(legacyShaderData)) {
                    CustomShaderUniformNbt.readLegacyUniformsInto(uniformOverrides, legacyShaderData.get("uniforms"));
                }
            }
            var savedOverrides = new HashMap<String, float[]>();
            CustomShaderUniformNbt.readInto(savedOverrides, materialData.get("uniformOverrides"));
            uniformOverrides.putAll(savedOverrides);
        } else {
            CustomShaderUniformNbt.readInto(uniformOverrides, tag);
        }
        recompile();
        if (shaderHolder != null && tag instanceof CompoundTag materialData
                && materialData.contains("shaderData", Tag.TAG_COMPOUND)) {
            var shaderData = materialData.getCompound("shaderData");
            shaderHolder.deserializeNBT(provider, shaderData);
            unavailableShaderData = null;
            // Older LDLib material files contain a sampler map, but lack the later HDR metadata.
            // LDShaderHolder can restore uniforms present in the base program itself. A uniform
            // used only by an instanced variant is absent there, so keep its authored value until
            // that variant is compiled. Do not shadow editable uniforms that the holder restored.
            if (shaderData.contains("samplers", Tag.TAG_COMPOUND)
                    && !shaderData.contains("hdrVersion") && !shaderData.contains("hdrUniforms")) {
                var legacyValues = new HashMap<String, float[]>();
                CustomShaderUniformNbt.readLegacyUniformsInto(legacyValues, shaderData.get("uniforms"));
                var baseUniforms = shaderHolder.baseInstance.getShaderInstanceAccessor().getUniformMap();
                legacyValues.forEach((name, values) -> {
                    if (!baseUniforms.containsKey(name)) {
                        uniformOverrides.putIfAbsent(name, values);
                    } else if (values.length == 4 && shaderHolder.isHDRUniform(name)) {
                        legacyHdrUniforms.add(name);
                    }
                });
            }
            attachDynamicSamplers(shaderHolder);
            attachDynamicUniforms(shaderHolder);
            applyLegacyHdrUniforms(shaderHolder.baseInstance);
            applyUniformOverrides(shaderHolder.baseInstance);
        }
    }

    public boolean isCompiledError() {
        return !compiledErrorMessage.isEmpty();
    }

    @Override
    public boolean usesSceneColor() {
        if (shaderHolder == null && !isCompiledError()) recompile();
        return shaderHolder != null && declaredSceneColor;
    }

    public void recompile() {
        var shaderLocation = getShaderLocation();
        compiledErrorMessage = "";
        litByDynamicLights = false;
        warnedFallbackVariants.clear();

        if (shaderCleanable != null) {
            shaderCleanable.clean();
            shaderCleanable = null;
        }
        if (shaderHolder != null) {
            this.shaderHolder = null;
        }

        LDShaderHolder loadedHolder = null;
        try {
            loadedHolder = loadShaderHolder(shaderLocation);
            var cleanable = AutoCloseCleaner.registerRenderThread(this, loadedHolder);
            this.shaderHolder = loadedHolder;
            this.shaderCleanable = cleanable;
        } catch (Throwable e) {
            // If cleaner registration failed after linking, the holder was not yet installed on this
            // material and no owner would release its programs. Dispose the temporary explicitly.
            if (loadedHolder != null && this.shaderHolder != loadedHolder) {
                try {
                    loadedHolder.close();
                } catch (Throwable closeFailure) {
                    e.addSuppressed(closeFailure);
                }
            }
            this.shaderHolder = null;
            // A shader the install does not ship (a project carrying `photon:slash` from an older
            // Photon, or an FX pack whose shader was not included) is a content problem, not a
            // crash: report it once as a warning and let the material fall back to the built-in HDR
            // particle program, so the rest of the effect — geometry, colours, trails — still draws.
            if (e instanceof IllegalStateException && e.getMessage() != null
                    && e.getMessage().startsWith("Failed to find shader ")) {
                Photon.LOGGER.warn("Shader {} is not installed; the material falls back to the built-in particle shader",
                        shaderLocation);
            } else {
                Photon.LOGGER.error("Failed to recompile shader", e);
            }
            // Never blank, and never null: a blank message reads as "no error", and this flag is the
            // only thing stopping getShader() from re-running the whole compile on every frame it
            // draws — which would build (and strand) a GL program per frame per material.
            // Throwable#getMessage() is null for plenty of exceptions, NPE among them.
            this.compiledErrorMessage = describeFailure(e);
            this.shaderCleanable = null;
        }
    }

    private static String describeFailure(Throwable e) {
        var message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private LDShaderHolder loadShaderHolder(ResourceLocation shaderLocation) throws Throwable {
        LDShaderHolder shaderHolder;
        releaseLeakedDefines(shaderLocation, Set.of());
        try {
            shaderHolder = LDShaderHolder.create(
                    com.lowdragmc.photon.client.fx.compat.ForgeShaderCompat.wrap(Minecraft.getInstance().getResourceManager()),
                    shaderLocation, DefaultVertexFormat.BLOCK);
        } finally {
            releaseLeakedDefines(shaderLocation, Set.of());
        }
        if (shaderHolder == null) throw new IllegalStateException("Failed to find shader " + shaderLocation);
        try {
            declaredSceneColor = declaresSampler(shaderLocation, "SamplerSceneColor");
            declaredSceneDepth = declaresSampler(shaderLocation, "SamplerSceneDepth");
            var shader = shaderHolder.baseInstance;
            var samplerNames = shader.getShaderInstanceAccessor().getSamplerNames();
            if (samplerNames.contains("SamplerBlockAtlas")) {
                var texture = Minecraft.getInstance().getTextureManager().getTexture(InventoryMenu.BLOCK_ATLAS);
                shader.setSampler("SamplerBlockAtlas", texture);
            }
            attachDynamicSamplers(shaderHolder);
            attachDynamicUniforms(shaderHolder);
            litByDynamicLights = samplerNames.contains(LitParticles.SAMPLERS.get(0));
            return shaderHolder;
        } catch (Throwable e) {
            // the holder owns a linked GL program by this point, and the caller only ever sees the
            // exception — dropping it here would strand the program for the life of the process
            shaderHolder.close();
            throw e;
        }
    }

    private void attachDynamicSamplers(LDShaderHolder shaderHolder) {
        var shader = shaderHolder.baseInstance;
        var samplerNames = shader.getShaderInstanceAccessor().getSamplerNames();
        if (samplerNames.contains("SamplerCurve") || declaresSampler(shaderLocation, "SamplerCurve")) {
            shaderHolder.addDynamicSampler("SamplerCurve", curveTexture::getCurveTexture);
        }
        if (samplerNames.contains("SamplerGradient") || declaresSampler(shaderLocation, "SamplerGradient")) {
            shaderHolder.addDynamicSampler("SamplerGradient", gradientTexture::getGradientTexture);
        }
        if (declaredSceneColor || samplerNames.contains("SamplerSceneColor")) {
            shaderHolder.addDynamicSampler("SamplerSceneColor", () -> Optional.ofNullable(RenderPassPipeline.getCurrent())
                    .map(pipeline -> pipeline.getSceneSamplers().colorTexture()).orElse(-1));
        }
        if (declaredSceneDepth || samplerNames.contains("SamplerSceneDepth")) {
            shaderHolder.addDynamicSampler("SamplerSceneDepth", () -> Optional.ofNullable(RenderPassPipeline.getCurrent())
                    .map(pipeline -> pipeline.getSceneSamplers().depthTexture()).orElse(-1));
        }
    }

    /** A sampler can be optimized out of the base program but used by an instanced variant. */
    private static boolean declaresSampler(ResourceLocation shader, String name) {
        var json = new ResourceLocation(shader.getNamespace(), "shaders/core/" + shader.getPath() + ".json");
        var resource = Minecraft.getInstance().getResourceManager().getResource(json).orElse(null);
        if (resource == null) return false;
        try (var reader = resource.openAsReader()) {
            var root = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            var samplers = root.getAsJsonArray("samplers");
            if (samplers == null) return false;
            for (var sampler : samplers) {
                var entry = sampler.getAsJsonObject();
                if (entry.has("name") && name.equals(entry.get("name").getAsString())) return true;
            }
        } catch (Exception e) {
            Photon.LOGGER.warn("Could not inspect shader samplers for {}", shader, e);
        }
        return false;
    }

    /** Uniforms may be inactive in the base program but live in an instanced variant. */
    private static Set<String> manifestUniforms(ResourceLocation shader) {
        var json = new ResourceLocation(shader.getNamespace(), "shaders/core/" + shader.getPath() + ".json");
        var resource = Minecraft.getInstance().getResourceManager().getResource(json).orElse(null);
        if (resource == null) return Set.of();
        try (var reader = resource.openAsReader()) {
            var root = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            var uniforms = root.getAsJsonArray("uniforms");
            if (uniforms == null) return Set.of();
            var names = new java.util.HashSet<String>();
            for (var uniform : uniforms) {
                var entry = uniform.getAsJsonObject();
                if (entry.has("name")) names.add(entry.get("name").getAsString());
            }
            return names;
        } catch (Exception e) {
            Photon.LOGGER.warn("Could not inspect shader uniforms for {}", shader, e);
            return Set.of();
        }
    }

    private void attachDynamicUniforms(LDShaderHolder shaderHolder) {
        var shader = shaderHolder.baseInstance;
        var uniformNames = shader.getShaderInstanceAccessor().getUniformMap().keySet();
        var manifestUniforms = manifestUniforms(shaderLocation);
        if (uniformNames.contains("U_CameraPosition") || manifestUniforms.contains("U_CameraPosition")) {
            shaderHolder.addDynamicUniform("U_CameraPosition", uniform -> {
                if (RenderPassPipeline.getCurrent() != null) {
                    var camera = RenderPassPipeline.getCurrent().getCamera();
                    if (camera != null) {
                        var pos = camera.getPosition();
                        uniform.set((float) pos.x, (float) pos.y, (float) pos.z);
                    }
                }
            });
        }
        if (uniformNames.contains("U_InverseProjectionMatrix") || manifestUniforms.contains("U_InverseProjectionMatrix")) {
            shaderHolder.addDynamicUniform("U_InverseProjectionMatrix", uniform -> {
                var inverse = RenderSystem.getProjectionMatrix().invert(new Matrix4f());
                var pipeline = RenderPassPipeline.getCurrent();
                if (pipeline != null) {
                    adjustLegacyScreenProjection(inverse, shaderLocation,
                            pipeline.framebufferWidth(), pipeline.framebufferHeight(),
                            GlStateManager.Viewport.x(), GlStateManager.Viewport.y(),
                            GlStateManager.Viewport.width(), GlStateManager.Viewport.height());
                }
                uniform.set(inverse);
            });
        }
        if (uniformNames.contains("U_InverseViewMatrix") || manifestUniforms.contains("U_InverseViewMatrix")) {
            shaderHolder.addDynamicUniform("U_InverseViewMatrix", uniform -> {
                uniform.set(RenderSystem.getModelViewMatrix().invert(new Matrix4f()));
            });
        }
        if (uniformNames.contains("ScreenSize") || manifestUniforms.contains("ScreenSize")) {
            shaderHolder.addDynamicUniform("ScreenSize", uniform -> {
                // gl_FragCoord is absolute in the framebuffer even for an offset editor viewport.
                // Scene samplers copy the full draw target, so normalization must use its size.
                var pipeline = RenderPassPipeline.getCurrent();
                if (pipeline != null) {
                    // A minimized/resizing target may report zero for a frame; screen-space shader nodes divide by this.
                    uniform.set((float) Math.max(1, pipeline.framebufferWidth()),
                            (float) Math.max(1, pipeline.framebufferHeight()));
                } else {
                    var surface = UISurface.current();
                    if (surface != null && !surface.isMainWindow()) {
                        uniform.set((float) Math.max(1, surface.framebufferWidth()),
                                (float) Math.max(1, surface.framebufferHeight()));
                    } else {
                        var target = Minecraft.getInstance().getMainRenderTarget();
                        uniform.set((float) Math.max(1, target.width), (float) Math.max(1, target.height));
                    }
                }
            });
        }
        if (uniformNames.contains("U_ViewPort") || manifestUniforms.contains("U_ViewPort")) {
            shaderHolder.addDynamicUniform("U_ViewPort", uniform -> {
                uniform.set(new Vector4f(
                        GlStateManager.Viewport.x(), GlStateManager.Viewport.y(),
                        Math.max(1, GlStateManager.Viewport.width()), Math.max(1, GlStateManager.Viewport.height())
                ));
            });
        }
    }

    /** The original scan and shield templates derive NDC from full-frame scene UVs. */
    public static void adjustLegacyScreenProjection(Matrix4f inverse, ResourceLocation shader,
                                                     int frameWidth, int frameHeight,
                                                     int x, int y, int width, int height) {
        if (!(shader.equals(new ResourceLocation("ldlib2", "scan")) ||
                shader.equals(new ResourceLocation("photon", "shield"))) ||
                frameWidth <= 0 || frameHeight <= 0 || width <= 0 || height <= 0) return;
        float sx = (float) frameWidth / width;
        float sy = (float) frameHeight / height;
        float tx = ((float) frameWidth - 2f * x) / width - 1f;
        float ty = ((float) frameHeight - 2f * y) / height - 1f;
        inverse.translate(tx, ty, 0f).scale(sx, sy, 1f);
    }

    @Override
    public void preload(MaterialContext context, PreloadContext preload) {
        // Collection-only diagnostics do not execute render jobs. Report the current authored sampler
        // assignments now; the render-thread job below still compiles and warms the shader.
        if (shaderHolder != null) {
            var samplerCache = ((com.lowdragmc.photon.core.mixins.accessor.LDShaderHolderAccessor) shaderHolder)
                    .photon$getSamplerCache();
            for (var sampler : samplerCache.values()) {
                if (sampler instanceof ResourceLocation texture) preload.texture(texture);
            }
        } else if (unavailableShaderData != null && unavailableShaderData.contains("samplers", Tag.TAG_COMPOUND)) {
            var samplers = unavailableShaderData.getCompound("samplers");
            for (var name : samplers.getAllKeys()) {
                var sampler = samplers.getCompound(name);
                if ("texture".equals(sampler.getString("type"))) {
                    var texture = ResourceLocation.tryParse(sampler.getString("resource"));
                    if (texture != null) preload.texture(texture);
                }
            }
        }
        preload.onRenderThread(() -> {
            getShader(context);
            var holder = shaderHolder;
            if (holder == null) return;
            var samplers = holder.serializeNBT(Platform.getFrozenRegistry()).getCompound("samplers");
            for (var name : samplers.getAllKeys()) {
                if (holder.deserializeSampler(samplers.getCompound(name)) instanceof ResourceLocation texture) {
                    preload.texture(texture);
                }
            }
            var samplerNames = holder.baseInstance.getShaderInstanceAccessor().getSamplerNames();
            if (samplerNames.contains("SamplerCurve") || declaresSampler(shaderLocation, "SamplerCurve")) {
                curveTexture.getCurveTexture();
            }
            if (samplerNames.contains("SamplerGradient") || declaresSampler(shaderLocation, "SamplerGradient")) {
                gradientTexture.getGradientTexture();
            }
        });
    }

    @Override
    public ShaderInstance begin(MaterialContext context) {
        var shader = getShader(context);
        return shaderHolder == null ? IMaterial.MISSING.begin(context) : shader;
    }

    @Override
    public ShaderInstance getShader(MaterialContext context) {
        if (shaderHolder == null) {
            if (isCompiledError()) {
                return IMaterial.MISSING.getShader(context);
            }
            recompile();
        }
        if (shaderHolder == null) {
            return IMaterial.MISSING.getShader(context);
        }
        // LDLib binds saved custom-shader samplers directly through TextureManager. Unlike
        // TextureMaterial that path did not adopt loose Photon 1 PNGs before binding them.
        // Read the live assignments so textures changed in the editor are also resolved.
        var samplerCache = ((com.lowdragmc.photon.core.mixins.accessor.LDShaderHolderAccessor) shaderHolder)
                .photon$getSamplerCache();
        for (var sampler : samplerCache.values()) {
            if (sampler instanceof ResourceLocation texture) {
                com.lowdragmc.photon.client.fx.LooseTextures.resolveForRender(texture);
            }
        }
        var defines = context.getShaderDefines();
        LDShaderInstance shader;
        if (defines.isEmpty()) {
            shader = shaderHolder.getShaderInstance();
        } else {
            releaseLeakedDefines(shaderLocation, defines);
            try {
                shader = shaderHolder.getShaderInstance(defines);
            } finally {
                // A compile can throw before LDLib has a chance to consume its thread-local defines.
                // Do not let an instanced variant turn the next unrelated program into an instanced one.
                releaseLeakedDefines(shaderLocation, defines);
            }
            if (!shader.defines.containsAll(defines) && warnedFallbackVariants.add(context.getVariantKey())) {
                Photon.LOGGER.error("Shader {} could not create variant {}; LDLib returned its base program, so this effect may not render correctly",
                        shaderLocation, defines);
            }
        }
        if (litByDynamicLights) LitParticles.bind(shader, context, 1f);
        applyLegacyHdrUniforms(shader);
        applyUniformOverrides(shader);
        return shader;
    }

    /** Legacy Photon shaders use vec4(rgb, intensity), while LDLib's newer HDR editor uploads rgb*intensity,1. */
    private void applyLegacyHdrUniforms(ShaderInstance shader) {
        if (shaderHolder == null) return;
        for (var name : legacyHdrUniforms) {
            var uniform = shader.getUniform(name);
            if (uniform == null || uniform.getCount() != 4
                    || shaderHolder.baseInstance.getUniform(name) == null) continue;
            var color = shaderHolder.getHDRUniform(name);
            uniform.set(color.getR(), color.getG(), color.getB(), color.getIntensity());
        }
    }

    private void applyUniformOverrides(ShaderInstance shader) {
        if (!(shader instanceof LDShaderInstance ldShader)) return;
        var uniforms = ldShader.getShaderInstanceAccessor().getUniformMap();
        uniformOverrides.forEach((name, components) -> {
            Uniform uniform = uniforms.get(name);
            if (uniform == null) return;
            float[] fitted = CustomShaderUniformValues.fitComponents(components, uniform.getCount());
            if (fitted.length == 0) return;
            int type = uniform.getType();
            if (type >= Uniform.UT_INT1 && type <= Uniform.UT_INT4) {
                int[] values = CustomShaderUniformValues.toIntegerComponents(fitted);
                switch (values.length) {
                    case 1 -> uniform.set(values[0]);
                    case 2 -> uniform.set(values[0], values[1]);
                    case 3 -> uniform.set(values[0], values[1], values[2]);
                    case 4 -> uniform.set(values[0], values[1], values[2], values[3]);
                    default -> Photon.LOGGER.warn("Ignoring unsupported integer shader uniform '{}' length {}", name, values.length);
                }
            } else {
                uniform.set(fitted);
            }
        });
    }

    /**
     * ⚠️ {@code LDShaderInstance.create} only clears the global program defines when the compile succeeds, and
     * {@code LDShaderHolder} swallows the failure (a variant falls back to the base program). Left set, the
     * defines are compiled into whatever shader is built next, e.g. a CPU-path program built as an instanced one.
     * Checked on both sides of a build: a leak from elsewhere must not get into ours either.
     */
    private static void releaseLeakedDefines(ResourceLocation shaderLocation, Set<String> defines) {
        if (!LDProgramDefineManager.hasProgramDefines()) return;
        Photon.LOGGER.warn("A failed shader compile left the defines {} set, cleared them (around {}{})",
                LDProgramDefineManager.getProgramDefines(), shaderLocation, defines.isEmpty() ? "" : " " + defines);
        LDProgramDefineManager.clearProgramDefines();
    }

    private IGuiTexture previewTextureImage() {
        if (!isCompiledError()) return preview;
        var message = compiledErrorMessage.isEmpty() ? "error" : compiledErrorMessage;
        if (previewErrorTexture == null || !message.equals(previewErrorText)) {
            previewErrorText = message;
            previewErrorTexture = new TextTexture(message, 0xffff0000);
        }
        return previewErrorTexture;
    }

    @Override
    public IGuiTexture preview() {
        return previewTexture;
    }

    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        createPreview(father);

        var configurator = new Configurator();
        var shaderConfigurator = new ConfiguratorGroup("photon.shader.settings");
        shaderConfigurator.setCollapse(false);
        shaderConfigurator.setCanCollapse(false);

        var shaderLocationField = new StringConfigurator("photon.shader",
                () -> getShaderLocation().toString(),
                s -> {
                    setShader(new ResourceLocation(s));
                    reloadShaderConfigurator(shaderConfigurator);
                    configurator.notifyChanges();
                },
                getShaderLocation().toString(),
                true).setResourceLocation(true);

        var reloadButton = new Configurator().addInlineChild(new Button()
                .setOnClick(event -> {
                    CompoundTag previousData = null;
                    if (shaderHolder != null) {
                        previousData = authoredShaderData(Platform.getFrozenRegistry());
                    }
                    recompile();
                    if (previousData != null && shaderHolder != null) {
                        shaderHolder.deserializeNBT(Platform.getFrozenRegistry(), previousData);
                        attachDynamicSamplers(shaderHolder);
                        attachDynamicUniforms(shaderHolder);
                    }
                    reloadShaderConfigurator(shaderConfigurator);
                }).setText("photon.reload_shader").layout(layout -> layout.alignSelf(AlignItems.CENTER)));

        configurator.inlineContainer.addChild( // button to select shader
                new Button().setText("photon.select_shader").setOnClick(e -> {
                    var mui = e.currentElement.getModularUI();
                    if (mui == null) return;
                    Dialog.showFileDialog("photon.select_shader", PhotonPaths.getDataDir(), true, Dialog.suffixFilter(".json"), r -> {
                        if (r != null && r.isFile()) {
                            var location = getShaderFromFile(r);
                            if (location == null) return;
                            setShader(location);
                            reloadShaderConfigurator(shaderConfigurator);
                            configurator.notifyChanges();
                        }
                    }).show(mui.ui.rootElement);
                }).layout(layout -> layout.alignSelf(AlignItems.CENTER)));

        reloadShaderConfigurator(shaderConfigurator);

        father.addConfigurators(
                configurator,
                shaderLocationField,
                reloadButton,
                shaderConfigurator
        );
        ConfiguratorParser.createConfigurators(father, this);
    }

    private void reloadShaderConfigurator(ConfiguratorGroup shaderConfigurator) {
        shaderConfigurator.removeAllConfigurators();
        if (shaderHolder != null) {
            shaderHolder.buildConfigurator(shaderConfigurator);
            if (litByDynamicLights) {
                for (var row : List.copyOf(shaderConfigurator.getConfigurators())) {
                    if (LitParticles.declares(row.label.getText().getString())) shaderConfigurator.removeConfigurator(row);
                }
            }
        }
    }

    @Nullable
    public static ResourceLocation getShaderFromFile(File filePath) {
        return PhotonAssetPath.shaderLocationFromFile(PhotonPaths.getAssetsDir().toPath(), filePath.toPath());
    }
}


