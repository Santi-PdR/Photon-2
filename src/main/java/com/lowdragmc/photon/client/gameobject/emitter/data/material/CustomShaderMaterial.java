package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.client.shader.LDProgramDefineManager;
import com.lowdragmc.lowdraglib2.client.shader.LDShaderHolder;
import com.lowdragmc.lowdraglib2.client.shader.LDShaderInstance;
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
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.AutoCloseCleaner;
import com.lowdragmc.photon.client.PhotonShaders;
import com.lowdragmc.photon.client.fx.PreloadContext;
import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline;
import com.lowdragmc.photon.client.util.PhotonAssetPath;
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
    private Cleaner.Cleanable shaderCleanable;
    @Getter
    private String compiledErrorMessage = "";
    /**
     * The shader's json lists {@code PhotonLightData}, as one including {@code photon:lit_particle.glsl} has to: the
     * dynamic lights are bound to it, out of the inspector and the save.
     */
    @Getter
    private boolean litByDynamicLights;
    private final Map<String, float[]> uniformOverrides = new HashMap<>();

    public CustomShaderMaterial() {
    }

    public CustomShaderMaterial(ResourceLocation shaderLocation) {
        this.shaderLocation = shaderLocation;
    }

    public void setShader(ResourceLocation shaderLocation) {
        this.shaderLocation = shaderLocation;
        recompile();
    }

    @Override
    public void setupUniform(MaterialContext context) {
        super.setupUniform(context);
    }

    @Override
    public IMaterial copy() {
        var copied = new CustomShaderMaterial(shaderLocation);
        var data = serializeAdditionalNBT(Platform.getFrozenRegistry());
        copied.deserializeAdditionalNBT(data, Platform.getFrozenRegistry());
        return copied;
    }

    @Override
    public Tag serializeAdditionalNBT(HolderLookup.@NotNull Provider provider) {
        var shaderData = new CompoundTag();
        if (shaderHolder != null) {
            shaderData.put("shaderData", authoredShaderData(provider));
        }
        if (!uniformOverrides.isEmpty()) {
            shaderData.put("uniformOverrides", CustomShaderUniformNbt.write(uniformOverrides));
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
        if (tag instanceof CompoundTag materialData) {
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
            shaderHolder.deserializeNBT(provider, materialData.getCompound("shaderData"));
            attachDynamicSamplers(shaderHolder);
            attachDynamicUniforms(shaderHolder);
            applyUniformOverrides(shaderHolder.baseInstance);
        }
    }

    public boolean isCompiledError() {
        return !compiledErrorMessage.isEmpty();
    }

    public void recompile() {
        compiledErrorMessage = "";
        litByDynamicLights = false;

        if (shaderCleanable != null) {
            shaderCleanable.clean();
            shaderCleanable = null;
        }
        if (shaderHolder != null) {
            this.shaderHolder = null;
        }

        try {
            this.shaderHolder = loadShaderHolder(shaderLocation);
            this.shaderCleanable = AutoCloseCleaner.registerRenderThread(this, this.shaderHolder);
        } catch (Throwable e) {
            Photon.LOGGER.error("Failed to recompile shader", e);
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
        if (samplerNames.contains("SamplerCurve")) {
            shaderHolder.addDynamicSampler("SamplerCurve", curveTexture::getCurveTexture);
        }
        if (samplerNames.contains("SamplerGradient")) {
            shaderHolder.addDynamicSampler("SamplerGradient", gradientTexture::getGradientTexture);
        }
        if (samplerNames.contains("SamplerSceneColor")) {
            shaderHolder.addDynamicSampler("SamplerSceneColor", () -> Optional.ofNullable(RenderPassPipeline.getCurrent())
                    .map(pipeline -> pipeline.getSceneSamplers().colorTexture()).orElse(-1));
        }
        if (samplerNames.contains("SamplerSceneDepth")) {
            shaderHolder.addDynamicSampler("SamplerSceneDepth", () -> Optional.ofNullable(RenderPassPipeline.getCurrent())
                    .map(pipeline -> pipeline.getSceneSamplers().depthTexture()).orElse(-1));
        }
    }

    private void attachDynamicUniforms(LDShaderHolder shaderHolder) {
        var shader = shaderHolder.baseInstance;
        var uniformNames = shader.getShaderInstanceAccessor().getUniformMap().keySet();
        if (uniformNames.contains("U_CameraPosition")) {
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
        if (uniformNames.contains("U_InverseProjectionMatrix")) {
            shaderHolder.addDynamicUniform("U_InverseProjectionMatrix", uniform -> {
                uniform.set(RenderSystem.getProjectionMatrix().invert(new Matrix4f()));
            });
        }
        if (uniformNames.contains("U_InverseViewMatrix")) {
            shaderHolder.addDynamicUniform("U_InverseViewMatrix", uniform -> {
                uniform.set(RenderSystem.getModelViewMatrix().invert(new Matrix4f()));
            });
        }
        if (uniformNames.contains("ScreenSize")) {
            shaderHolder.addDynamicUniform("ScreenSize", uniform -> {
                // Custom shader materials bypass Minecraft's normal shader setup. Keep screen-space
                // effects (for example Photon shield shaders sampling scene depth) in sync with the
                // active render target instead of leaving the manifest's [1, 1] default in place.
                uniform.set((float) GlStateManager.Viewport.width(),
                        (float) GlStateManager.Viewport.height());
            });
        }
        if (uniformNames.contains("U_ViewPort")) {
            shaderHolder.addDynamicUniform("U_ViewPort", uniform -> {
                uniform.set(new Vector4f(
                        GlStateManager.Viewport.x(), GlStateManager.Viewport.y(),
                        GlStateManager.Viewport.width(), GlStateManager.Viewport.height()
                ));
            });
        }
    }

    @Override
    public void preload(MaterialContext context, PreloadContext preload) {
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
            if (samplerNames.contains("SamplerCurve")) curveTexture.getCurveTexture();
            if (samplerNames.contains("SamplerGradient")) gradientTexture.getGradientTexture();
        });
    }

    @Override
    public ShaderInstance getShader(MaterialContext context) {
        if (shaderHolder == null) {
            if (isCompiledError()) {
                return PhotonShaders.getHDRParticleShader();
            }
            recompile();
        }
        if (shaderHolder == null) {
            return PhotonShaders.getHDRParticleShader();
        }
        var defines = context.getShaderDefines();
        LDShaderInstance shader;
        if (defines.isEmpty()) {
            shader = shaderHolder.getShaderInstance();
        } else {
            releaseLeakedDefines(shaderLocation, defines);
            shader = shaderHolder.getShaderInstance(defines);
            releaseLeakedDefines(shaderLocation, defines);
        }
        if (litByDynamicLights) LitParticles.bind(shader, context, 1f);
        applyUniformOverrides(shader);
        return shader;
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

    @Override
    public IGuiTexture preview() {
        return DynamicTexture.of(() -> isCompiledError() ?
                new TextTexture(compiledErrorMessage.isEmpty() ? "error" : compiledErrorMessage, 0xffff0000) :
                preview);
    }

    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        createPreview(father);

        var configurator = new Configurator();
        var shaderConfigurator = new ConfiguratorGroup("photon.shader.settings");
        shaderConfigurator.setCollapse(false);
        shaderConfigurator.setCanCollapse(false);

        var shaderLocationField = new StringConfigurator("photon.shader",
                () -> shaderLocation.toString(),
                s -> {
                    setShader(new ResourceLocation(s));
                    reloadShaderConfigurator(shaderConfigurator);
                    configurator.notifyChanges();
                },
                shaderLocation.toString(),
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
                    Dialog.showFileDialog("photon.select_shader", LDLib2.getAssetsDir(), true, Dialog.suffixFilter(".json"), r -> {
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
        return PhotonAssetPath.shaderLocationFromFile(LDLib2.getAssetsDir().toPath(), filePath.toPath());
    }
}


