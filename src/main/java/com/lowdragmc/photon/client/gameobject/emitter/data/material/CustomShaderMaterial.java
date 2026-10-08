package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.client.shader.LDShaderInstance;
import com.lowdragmc.lowdraglib2.client.shader.LDShaderHolder;
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
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.AutoCloseCleaner;
import com.lowdragmc.photon.client.PhotonShaders;
import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline;
import com.lowdragmc.photon.client.render.PhotonDepthParams;
import com.lowdragmc.photon.client.render.PhotonCustomUniforms;
import com.lowdragmc.photon.client.render.MaterialPreviewRenderer;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.vfyjxf.taffy.style.AlignItems;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.GsonHelper;
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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.ref.Cleaner;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@OnlyIn(Dist.CLIENT)
@ParametersAreNonnullByDefault
@LDLRegisterClient(name = "custom_shader", registry = "photon:material")
public class CustomShaderMaterial extends ShaderInstanceMaterial {
    public final static int MAX_SAMPLER = 128;
    public final static int MAX_SAMPLING = 128;

    /** Shader JSON key used by Photon 26.2 to identify shaders authored for reverse-Z depth. */
    public static final String DEPTH_CONVENTION_KEY = "depthConvention";
    /** Value used by Photon 26.2 for its native reverse-Z depth convention. */
    public static final String DEPTH_CONVENTION_NATIVE = "reverse_z";

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
    private final Cleaner.Cleanable samplerTexturesCleanable;
    @Getter
    private String compiledErrorMessage = "";
    /** Per-material API overrides. Kept separate from LDShaderHolder's UI values for NBT stability. */
    private final Map<String, float[]> uniformOverrides = new HashMap<>();
    @Nullable
    private PhotonCustomUniforms customUniforms;
    @Nullable
    private JsonObject cachedShaderJson;

    public CustomShaderMaterial() {
        samplerTexturesCleanable = AutoCloseCleaner.registerRenderThread(this,
                new SamplerTextures(curveTexture, gradientTexture));
    }

    public CustomShaderMaterial(ResourceLocation shaderLocation) {
        this();
        this.shaderLocation = shaderLocation;
    }

    /** Holds no reference to the material, so the cleaner can release both owned dynamic textures. */
    private record SamplerTextures(CurveTexture curve, GradientTexture gradient) implements AutoCloseable {
        @Override
        public void close() {
            try {
                curve.close();
            } finally {
                gradient.close();
            }
        }
    }

    public void setShader(ResourceLocation shaderLocation) {
        this.shaderLocation = shaderLocation;
        recompile();
    }

    @Override
    public void setupUniform(MaterialContext context) {
        super.setupUniform(context);
        var shader = getShader(context);
        if (shaderHolder == null && shader != null) {
            // A failed custom program falls back to the stock textured particle shader. Configure
            // every input that shader needs; leaving Sampler0 on a previous UI texture commonly
            // produces a fully transparent fallback even though the particle VBO was submitted.
            RenderSystem.setShaderTexture(0, Photon.id("textures/particle/circle.png"));
            shader.safeGetUniform("DiscardThreshold").set(0.01f);
            shader.safeGetUniform("HDR").set(0f, 0f, 0f, 1f);
            shader.safeGetUniform("HDRMode").set(0);
        }
    }

    @Override
    public IMaterial copy() {
        var copied = new CustomShaderMaterial(shaderLocation);
        var provider = Platform.getFrozenRegistry();
        // These configurable sampler inputs are not part of additional NBT. This override of
        // IMaterial.copy() intentionally avoids round-tripping the whole configurable object,
        // so copy the two editable texture definitions explicitly as well.
        MaterialSamplerTextureCopy.copy(curveTexture, gradientTexture,
                copied.curveTexture, copied.gradientTexture, provider);
        copied.deserializeAdditionalNBT(serializeAdditionalNBT(provider), provider);
        return copied;
    }

    @Override
    public Tag serializeAdditionalNBT(HolderLookup.@NotNull Provider provider) {
        var shaderData = new CompoundTag();
        if (shaderHolder != null) {
            shaderData.put("shaderData", shaderHolder.serializeNBT(provider));
        }
        if (!uniformOverrides.isEmpty()) {
            shaderData.put("uniformOverrides", CustomShaderUniformNbt.write(uniformOverrides));
        }
        return shaderData;
    }

    @Override
    public void deserializeAdditionalNBT(Tag tag, HolderLookup.@NotNull Provider provider) {
        uniformOverrides.clear();
        if (tag instanceof CompoundTag materialData) {
            if (materialData.contains("shaderData", Tag.TAG_COMPOUND)) {
                var legacyShaderData = materialData.getCompound("shaderData");
                if (CustomShaderUniformNbt.isPhoton262LegacyShaderData(legacyShaderData)) {
                    CustomShaderUniformNbt.readLegacyUniformsInto(uniformOverrides,
                            legacyShaderData.get("uniforms"));
                }
            }
            var savedOverrides = new HashMap<String, float[]>();
            CustomShaderUniformNbt.readInto(savedOverrides, materialData.get("uniformOverrides"));
            // The explicit Forge field wins if a save contains both formats.
            uniformOverrides.putAll(savedOverrides);
        } else {
            CustomShaderUniformNbt.readInto(uniformOverrides, tag);
        }
        if (tag instanceof CompoundTag || shaderHolder != null) recompile();
        if (shaderHolder != null && tag instanceof CompoundTag shaderData
                && shaderData.contains("shaderData", Tag.TAG_COMPOUND)) {
            shaderHolder.deserializeNBT(provider, shaderData.getCompound("shaderData"));
            attachDynamicSamplers(shaderHolder);
            attachDynamicUniforms(shaderHolder);
        }
    }

    public boolean isCompiledError() {
        return !compiledErrorMessage.isEmpty();
    }

    public void recompile() {
        compiledErrorMessage = "";
        customUniforms = null;
        cachedShaderJson = null;

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
        var shaderHolder = LDShaderHolder.create(shaderLocation, DefaultVertexFormat.BLOCK);
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
            shaderHolder.addDynamicSampler("SamplerSceneColor", () -> {
                int previewTexture = MaterialPreviewRenderer.previewSceneColorTexture();
                return previewTexture >= 0 ? previewTexture : Optional.ofNullable(RenderPassPipeline.getCurrent())
                        .map(pipeline -> pipeline.getSceneSamplers().colorTexture()).orElse(-1);
            });
        }
        if (samplerNames.contains("SamplerSceneDepth")) {
            shaderHolder.addDynamicSampler("SamplerSceneDepth", () -> {
                int previewTexture = MaterialPreviewRenderer.previewSceneDepthTexture();
                return previewTexture >= 0 ? previewTexture : Optional.ofNullable(RenderPassPipeline.getCurrent())
                        .map(pipeline -> pipeline.getSceneSamplers().depthTexture()).orElse(-1);
            });
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
                        uniform.set((float) pos.x, (float) pos.y, (float) pos.z, 1f);
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
        if (uniformNames.contains("U_ViewPort")) {
            shaderHolder.addDynamicUniform("U_ViewPort", uniform -> {
                uniform.set(new Vector4f(
                        GlStateManager.Viewport.x(), GlStateManager.Viewport.y(),
                        GlStateManager.Viewport.width(), GlStateManager.Viewport.height()
                ));
            });
        }
        // Photon 26.2's engine.glsl exposes the depth-buffer convention together with the depth
        // range. Forge 1.20.1 still uses forward-Z and the OpenGL [-1, 1] projection interval.
        if (uniformNames.contains("U_DepthParams")) {
            shaderHolder.addDynamicUniform("U_DepthParams", uniform -> {
                uniform.set(PhotonDepthParams.fromProjection(RenderSystem.getProjectionMatrix()));
            });
        }
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
            shader = shaderHolder.getShaderInstance(defines);
        }
        applyUniformOverrides(shader);
        return shader;
    }

    /** Set a user-defined shader uniform without recompiling; values are saved with this material. */
    public void setUniformValue(String name, float... components) {
        if (name == null || name.isBlank() || name.startsWith("U_") || components == null
                || components.length == 0 || components.length > 16) return;
        for (float component : components) if (!Float.isFinite(component)) return;
        uniformOverrides.put(name, components.clone());
        if (shaderHolder != null) applyUniformOverrides(shaderHolder.baseInstance);
    }

    /**
     * Returns the JSON-declared custom uniforms. Forge 1.20.1 applies their values directly to the
     * shader's ordinary uniforms instead of creating the 26.2-only GPU uniform buffer.
     */
    public PhotonCustomUniforms customUniforms() {
        if (customUniforms == null) {
            customUniforms = new PhotonCustomUniforms(CustomShaderUniformDefaults.fields(readShaderJson()),
                    this::setUniformValue, this::applyUniformOverridesToBase);
        }
        return customUniforms;
    }

    private void applyUniformOverridesToBase() {
        if (shaderHolder != null) applyUniformOverrides(shaderHolder.baseInstance);
    }

    /** Read the current value, including the shader JSON defaults when the program is not compiled yet. */
    public float[] getUniformValue(String name, int count) {
        if (count <= 0) return new float[0];
        var override = uniformOverrides.get(name);
        float[] result;
        var uniform = shaderHolder == null ? null
                : shaderHolder.baseInstance.getShaderInstanceAccessor().getUniformMap().get(name);
        if (uniform == null) {
            result = readShaderUniformDefaults(name, count);
        } else {
            result = new float[count];
            if (isIntegerUniform(uniform.getType())) {
                var values = uniform.getIntBuffer();
                for (int i = 0; i < Math.min(count, values.capacity()); i++) result[i] = values.get(i);
            } else {
                var values = uniform.getFloatBuffer();
                for (int i = 0; i < Math.min(count, values.capacity()); i++) result[i] = values.get(i);
            }
        }
        if (override != null) System.arraycopy(override, 0, result, 0, Math.min(count, override.length));
        return result;
    }

    private float[] readShaderUniformDefaults(String name, int count) {
        return CustomShaderUniformDefaults.read(readShaderJson(), name, count);
    }

    private JsonObject readShaderJson() {
        if (cachedShaderJson != null) return cachedShaderJson;
        var shaderJson = ResourceLocation.fromNamespaceAndPath(shaderLocation.getNamespace(),
                "shaders/core/" + shaderLocation.getPath() + ".json");
        var resource = Minecraft.getInstance().getResourceManager().getResource(shaderJson).orElse(null);
        if (resource == null) return cachedShaderJson = new JsonObject();
        try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            return cachedShaderJson = GsonHelper.parse(reader);
        } catch (Exception ignored) {
            return cachedShaderJson = new JsonObject();
        }
    }

    private void applyUniformOverrides(LDShaderInstance shader) {
        var uniforms = shader.getShaderInstanceAccessor().getUniformMap();
        uniformOverrides.forEach((name, components) -> {
            var uniform = uniforms.get(name);
            if (uniform == null) return;
            float[] fittedComponents = CustomShaderUniformValues.fitComponents(components, uniform.getCount());
            if (fittedComponents.length == 0) return;
            if (isIntegerUniform(uniform.getType())) {
                int[] integerComponents = CustomShaderUniformValues.toIntegerComponents(fittedComponents);
                switch (integerComponents.length) {
                    case 1 -> uniform.set(integerComponents[0]);
                    case 2 -> uniform.set(integerComponents[0], integerComponents[1]);
                    case 3 -> uniform.set(integerComponents[0], integerComponents[1], integerComponents[2]);
                    case 4 -> uniform.set(integerComponents[0], integerComponents[1],
                            integerComponents[2], integerComponents[3]);
                    default -> Photon.LOGGER.warn("Ignoring unsupported integer shader uniform '{}' length {}",
                            name, integerComponents.length);
                }
            } else {
                uniform.set(fittedComponents);
            }
        });
    }

    private static boolean isIntegerUniform(int type) {
        return type == Uniform.UT_INT1 || type == Uniform.UT_INT2
                || type == Uniform.UT_INT3 || type == Uniform.UT_INT4;
    }

    @Override
    public IGuiTexture preview() {
        return DynamicTexture.of(() -> isCompiledError() ?
                new TextTexture(compiledErrorMessage.isEmpty() ? "error" : compiledErrorMessage, 0xffff0000) :
                MaterialPreviewRenderer.previewOf(this));
    }

    @Override
    public IGuiTexture previewLive() {
        return DynamicTexture.of(() -> isCompiledError() ?
                new TextTexture(compiledErrorMessage.isEmpty() ? "error" : compiledErrorMessage, 0xffff0000) :
                MaterialPreviewRenderer.livePreviewOf(this));
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
                    setShader(ResourceLocation.parse(s));
                    reloadShaderConfigurator(shaderConfigurator);
                    configurator.notifyChanges();
                },
                shaderLocation.toString(),
                true).setResourceLocation(true);

        var reloadButton = new Configurator().addInlineChild(new Button()
                .setOnClick(event -> {
                    CompoundTag previousData = null;
                    if (shaderHolder != null) {
                        previousData = shaderHolder.serializeNBT(Platform.getFrozenRegistry());
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
        }
    }

    @Nullable
    public static ResourceLocation getShaderFromFile(File filePath) {
        String fullPath = filePath.getPath().replace('\\', '/');

        // find the "assets/" directory in the path
        var assetsIndex = fullPath.indexOf("assets/");
        if (assetsIndex == -1) {
            return null;
        }

        var relativePath = fullPath.substring(assetsIndex + "assets/".length());

        // find mod_id
        var slashIndex = relativePath.indexOf('/');
        if (slashIndex == -1) {
            return null;
        }

        var modId = relativePath.substring(0, slashIndex);
        var subPath = relativePath.substring(slashIndex + 1);

        // find shader location
        var shaderIndex = subPath.indexOf("shaders/core/");
        if (shaderIndex == -1) {
            return null;
        }

        var shaderPath = subPath.substring(shaderIndex + "shaders/core/".length());
        if (!shaderPath.endsWith(".json")) {
            return null;
        }

        var location = modId + ":" + shaderPath.substring(0, shaderPath.length() - 5); // remove ".json" suffix

        if (LDLib2.isValidResourceLocation(location)) {
            return ResourceLocation.parse(location);
        }
        return null;
    }
}
