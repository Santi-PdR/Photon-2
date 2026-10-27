package com.lowdragmc.photon.client;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.JsonModelSource;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.ObjModelSource;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.PhotonMesh;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.PhotonMeshCache;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.CustomShaderMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.MaterialContext;
import com.lowdragmc.lowdraglib2.client.shader.LDShaderInstance;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.resources.ResourceLocation;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.MaterialSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.SubEmittersSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.PhotonGpuChannels;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.MeshData;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleRendererSetting;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

/** Exercises the editor load and preview path only in the opt-in remote client smoke run. */
public final class PhotonFXEditorSmokeTest {
    private static boolean started;
    private static boolean finished;
    private static int ticks;
    private static FXEditor editor;
    private static Screen previousScreen;
    private static Path projectFile;

    private PhotonFXEditorSmokeTest() {}

    public static void prepareModelFixtureIfEnabled() {
        if (!Boolean.getBoolean("photon.fxprojSmokeTest")) return;
        try {
            Path modelDir = LDLib2.getAssetsDir().toPath().resolve("photon").resolve("models").resolve("ci");
            Files.createDirectories(modelDir);
            Files.writeString(modelDir.resolve("obj_smoke.json"),
                    "{\"loader\":\"neoforge:obj\",\"model\":\"photon:models/ci/obj_smoke.obj\",\"textures\":{\"particle\":\"minecraft:block/stone\"}}");
            Files.writeString(modelDir.resolve("obj_smoke.obj"),
                    "mtllib obj_smoke.mtl\no smoke\nv 0 0 0\nv 1 0 0\nv 0 1 0\nvt 0 0\nvt 1 0\nvt 0 1\nvn 0 0 1\nusemtl stone\nf 1/1/1 2/2/1 3/3/1\n");
            Files.writeString(modelDir.resolve("obj_smoke.mtl"), "newmtl stone\nKd 1 1 1\nmap_Kd minecraft:block/stone\n");
            Path ldlibModelDir = LDLib2.getAssetsDir().toPath().resolve("ldlib2").resolve("models");
            Files.createDirectories(ldlibModelDir.resolve("block"));
            Files.createDirectories(ldlibModelDir.resolve("obj"));
            Files.writeString(ldlibModelDir.resolve("block").resolve("ci_obj_smoke.json"),
                    "{\"loader\":\"neoforge:obj\",\"model\":\"ldlib2:models/obj/ci_obj_smoke.obj\",\"textures\":{}}");
            Files.writeString(ldlibModelDir.resolve("obj").resolve("ci_obj_smoke.obj"),
                    "o smoke\nv -0.5 -0.5 0\nv 0.5 -0.5 0\nv 0 0.5 0\nvt 0 0\nvt 1 0\nvt 0.5 1\nvn 0 0 1\nf 1/1/1 2/2/1 3/3/1\n");
            Path shaderDir = LDLib2.getAssetsDir().toPath().resolve("photon").resolve("shaders").resolve("core").resolve("ci");
            Files.createDirectories(shaderDir);
            try (var vertex = Photon.class.getResourceAsStream("/assets/photon/shaders/core/particle.vsh");
                 var manifest = Photon.class.getResourceAsStream("/assets/photon/shaders/core/circle.json")) {
                String source = new String(vertex.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                        .replace("fog_distance(ModelViewMat, data.Position, FogShape)", "fog_distance(data.Position, FogShape)");
                var json = com.google.gson.JsonParser.parseString(new String(manifest.readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                json.addProperty("vertex", "photon:ci/fog_smoke");
                Files.writeString(shaderDir.resolve("fog_smoke.vsh"), source);
                Files.writeString(shaderDir.resolve("fog_smoke.json"), json.toString());
            }
            Files.writeString(shaderDir.resolve("tornado_smoke.vsh"), """
                    #version 330 core
                    #moj_import <photon:particle.glsl>
                    layout(location = 9) in float T;
                    uniform mat4 ModelViewMat;
                    uniform mat4 ProjMat;
                    out float particleT;
                    void main() {
                        ParticleData data = getParticleData();
                        particleT = T;
                        gl_Position = ProjMat * ModelViewMat * vec4(data.Position, 1.0);
                    }
                    """);
            Files.writeString(shaderDir.resolve("tornado_smoke.fsh"), """
                    #version 330 core
                    #moj_import <photon:particle_utils.glsl>
                    uniform sampler2D SamplerCurve;
                    in float particleT;
                    out vec4 fragColor;
                    void main() {
                        float value = getCurveValue(SamplerCurve, 0, clamp(particleT, 0.0, 1.0));
                        fragColor = vec4(0.35 + value * 0.65, 0.1, 1.0 - value * 0.8, 1.0);
                    }
                    """);
            try (var manifest = Photon.class.getResourceAsStream("/assets/photon/shaders/core/circle.json")) {
                var json = com.google.gson.JsonParser.parseString(new String(manifest.readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                json.addProperty("vertex", "photon:ci/tornado_smoke");
                json.addProperty("fragment", "photon:ci/tornado_smoke");
                var samplers = new com.google.gson.JsonArray();
                var curveSampler = new com.google.gson.JsonObject();
                curveSampler.addProperty("name", "SamplerCurve");
                samplers.add(curveSampler);
                json.add("samplers", samplers);
                Files.writeString(shaderDir.resolve("tornado_smoke.json"), json.toString());
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Could not prepare OBJ model and tornado-shader smoke fixtures", failure);
        }
    }

    public static void registerIfEnabled() {
        if (Boolean.getBoolean("photon.fxprojSmokeTest")) {
            MinecraftForge.EVENT_BUS.addListener(PhotonFXEditorSmokeTest::onClientTick);
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.getOverlay() != null || minecraft.level == null || minecraft.player == null) return;
        try {
            if (!started) {
                started = true;
                openLegacyProject(minecraft);
            } else if (++ticks == 30) {
                int queued = editor.sceneView.particleManager.particlesByRenderType().values().stream()
                        .mapToInt(Collection::size).sum();
                if (queued == 0) throw new IllegalStateException("Editor preview did not queue any particles");
                int live = editor.runtime.objects.values().stream().filter(ParticleEmitter.class::isInstance)
                        .map(ParticleEmitter.class::cast).mapToInt(ParticleEmitter::getParticleAmount).sum();
                if (live != 3) throw new IllegalStateException("Expected legacy model, tornado-style OBJ shader model, and CPU-billboard particles, got " + live);
                Screenshot.grab(new File("."), minecraft.getMainRenderTarget(),
                        message -> Photon.LOGGER.info("[fxproj-editor-smoke] screenshot: {}", message.getString()));
                Photon.LOGGER.info("[fxproj-editor-smoke] opened v3 managed-list project and previewed legacy model, tornado-style OBJ shader model, and CPU-billboard particles ({} live, {} queued objects)", live, queued);
            } else if (ticks == 110) {
                Screenshot.grab(new File("."), minecraft.getMainRenderTarget(),
                        message -> {
                            Photon.LOGGER.info("[fxproj-editor-smoke] expired screenshot: {}", message.getString());
                            minecraft.execute(minecraft::stop);
                        });
                finished = true;
                editor.closeCurrentProject(false, null);
                minecraft.setScreen(previousScreen);
                Files.deleteIfExists(projectFile);
            }
        } catch (Throwable failure) {
            finished = true;
            Photon.LOGGER.error("[fxproj-editor-smoke] failed to open or preview the legacy project", failure);
            throw new IllegalStateException("Photon editor project smoke test failed", failure);
        }
    }

    private static void openLegacyProject(Minecraft minecraft) throws Exception {
        verifyNestedMeshLoad();
        var provider = com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry();
        var original = new CustomShaderMaterial();
        original.curveTexture.addCurve(new com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve());
        var saved = com.lowdragmc.lowdraglib2.utils.PersistedParser.serializeNBT(original, provider);
        if (!saved.contains("curveTexture", Tag.TAG_LIST)) throw new IllegalStateException("Curve sampler did not serialize as a list");
        var restoredMaterial = new CustomShaderMaterial();
        com.lowdragmc.lowdraglib2.utils.PersistedParser.deserializeNBT(saved, restoredMaterial, provider);
        if (restoredMaterial.curveTexture.serializeNBT(provider).size() != 1 || restoredMaterial.curveTexture.getCurveTexture() == null)
            throw new IllegalStateException("Saved shader curve was not restored and uploaded");
        restoredMaterial.curveTexture.close();
        Photon.LOGGER.info("[shader-curve-smoke] restored and uploaded a saved shader curve");
        var fogMaterial = new CustomShaderMaterial(new ResourceLocation("photon", "ci/fog_smoke"));
        fogMaterial.recompile();
        if (fogMaterial.isCompiledError()) throw new IllegalStateException(fogMaterial.getCompiledErrorMessage());
        if (!(fogMaterial.getShader(MaterialContext.PARTICLE_MODEL_INSTANCE) instanceof LDShaderInstance variant)
                || !variant.defines.contains("PARTICLE_MODEL_INSTANCE")) {
            throw new IllegalStateException("Two-argument fog shader failed its instanced variant");
        }
        Photon.LOGGER.info("[shader-compat-smoke] compiled two-argument fog shader and instanced variant");
        var location = new ResourceLocation("photon", "ci/obj_smoke");
        var resource = minecraft.getResourceManager().getResource(new ResourceLocation("photon", "models/ci/obj_smoke.json")).orElseThrow();
        try (var reader = resource.openAsReader()) {
            // This invokes Forge's ExtendedBlockModelDeserializer, not just Photon's loader detection.
            BlockModel.fromStream(reader);
        }
        var mesh = new JsonModelSource(location).getMesh();
        if (mesh == null || mesh.vertexCount() < 3) {
            throw new IllegalStateException("NeoForge OBJ resource did not produce a Photon mesh");
        }
        Photon.LOGGER.info("[obj-model-smoke] parsed neoforge:obj through Forge and built {} vertices", mesh.vertexCount());
        var externalLocation = new ResourceLocation("ldlib2", "block/ci_obj_smoke");
        var externalResource = minecraft.getResourceManager()
                .getResource(new ResourceLocation("ldlib2", "models/block/ci_obj_smoke.json")).orElseThrow();
        try (var reader = externalResource.openAsReader()) {
            BlockModel.fromStream(reader);
        }
        var externalMesh = new JsonModelSource(externalLocation).getMesh();
        if (externalMesh == null || externalMesh.vertexCount() < 3) {
            throw new IllegalStateException("LDLib2-namespace NeoForge OBJ did not produce a Photon mesh");
        }
        Photon.LOGGER.info("[external-obj-model-smoke] loaded ldlib2:block/ci_obj_smoke with {} vertices", externalMesh.vertexCount());
        var source = new FXProject();
        var emitter = new ParticleEmitter();
        emitter.transform().localPosition(new org.joml.Vector3f(0, 2, 0));
        emitter.config.setLooping(false);
        emitter.config.setStartLifetime(NumberFunction.constant(70));
        emitter.config.setStartSpeed(NumberFunction.constant(0));
        emitter.config.setStartSize(new com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3(4, 4, 4));
        emitter.config.emission.setEmissionRate(NumberFunction.constant(0));
        emitter.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Model);
        emitter.config.renderer.setModel(new MeshData(new JsonModelSource(location)));
        emitter.config.renderer.setUseGPUInstance(true);
        emitter.config.renderer.getMaterials().clear();
        var visibleMaterial = new MaterialSetting(new com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial());
        visibleMaterial.setCull(false);
        visibleMaterial.setDepthTest(false);
        emitter.config.renderer.getMaterials().add(visibleMaterial);
        var burst = new EmissionSetting.Burst();
        burst.time = 0;
        burst.cycles = 1;
        burst.setCount(NumberFunction.constant(1));
        burst.interval = 4;
        emitter.config.emission.getBursts().add(burst);
        emitter.config.subEmitters.setEnable(true);
        emitter.config.subEmitters.getEmitters().add(new SubEmittersSetting.Emitter());
        source.getFx().getFxData().objects().add(emitter);

        var tornadoModel = new ParticleEmitter();
        tornadoModel.transform().localPosition(new org.joml.Vector3f(2.5f, 2, 0));
        tornadoModel.config.setLooping(false);
        tornadoModel.config.setStartLifetime(NumberFunction.constant(70));
        tornadoModel.config.setStartSpeed(NumberFunction.constant(0));
        tornadoModel.config.setStartSize(new com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3(4, 4, 4));
        tornadoModel.config.emission.setEmissionRate(NumberFunction.constant(0));
        tornadoModel.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Model);
        tornadoModel.config.renderer.setModel(new MeshData(new JsonModelSource(externalLocation)));
        tornadoModel.config.renderer.setUseGPUInstance(true);
        tornadoModel.config.renderer.getMaterials().clear();
        var tornadoShader = new CustomShaderMaterial(new ResourceLocation("photon", "ci/tornado_smoke"));
        tornadoShader.curveTexture.addCurve(new com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve());
        tornadoShader.recompile();
        if (tornadoShader.isCompiledError()) throw new IllegalStateException(tornadoShader.getCompiledErrorMessage());
        if (!(tornadoShader.getShader(MaterialContext.PARTICLE_MODEL_INSTANCE) instanceof LDShaderInstance tornadoVariant)
                || !tornadoVariant.defines.contains("PARTICLE_MODEL_INSTANCE")) {
            throw new IllegalStateException("Tornado-style custom shader failed its GPU model variant");
        }
        if (tornadoShader.curveTexture.getCurveTexture() == null) {
            throw new IllegalStateException("Tornado-style shader curve sampler did not upload");
        }
        var tornadoMaterial = new MaterialSetting(tornadoShader);
        tornadoMaterial.setCull(false);
        tornadoMaterial.setDepthTest(false);
        tornadoModel.config.renderer.getMaterials().add(tornadoMaterial);
        enableLegacyTChannel(tornadoModel);
        var tornadoBurst = new EmissionSetting.Burst();
        tornadoBurst.time = 0;
        tornadoBurst.cycles = 1;
        tornadoBurst.setCount(NumberFunction.constant(1));
        tornadoBurst.interval = 4;
        tornadoModel.config.emission.getBursts().add(tornadoBurst);
        source.getFx().getFxData().objects().add(tornadoModel);

        var billboard = new ParticleEmitter();
        billboard.transform().localPosition(new org.joml.Vector3f(-2, 2, 0));
        billboard.config.setLooping(false);
        billboard.config.setStartLifetime(NumberFunction.constant(70));
        billboard.config.setStartSpeed(NumberFunction.constant(0));
        billboard.config.setStartSize(new com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3(0.8f, 0.8f, 0.8f));
        billboard.config.emission.setEmissionRate(NumberFunction.constant(0));
        billboard.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Billboard);
        billboard.config.renderer.setUseGPUInstance(false);
        billboard.config.renderer.getMaterials().clear();
        var billboardMaterial = new MaterialSetting(new com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial());
        billboardMaterial.setCull(false);
        billboardMaterial.setDepthTest(false);
        billboard.config.renderer.getMaterials().add(billboardMaterial);
        var billboardBurst = new EmissionSetting.Burst();
        billboardBurst.time = 0;
        billboardBurst.cycles = 1;
        billboardBurst.setCount(NumberFunction.constant(1));
        billboardBurst.interval = 4;
        billboard.config.emission.getBursts().add(billboardBurst);
        source.getFx().getFxData().objects().add(billboard);

        projectFile = Files.createTempFile("photon-editor-v3-", ".fxproj");
        FXProject.TYPE.saveProjectToFile(source, projectFile.toFile());
        var encoded = NbtIo.read(projectFile.toFile());
        var fixtureConfig = encoded.getCompound("data").getCompound("fx").getCompound("fxData")
                .getList("fxObjects", Tag.TAG_COMPOUND).getCompound(0).getCompound("data").getCompound("config");
        if (fixtureConfig.getCompound("subEmitters").getCompound("emitters")
                .getList("payload", Tag.TAG_COMPOUND).size() != 1) {
            throw new IllegalStateException("The enabled sub-emitter fixture was not written: " + fixtureConfig.getCompound("subEmitters"));
        }
        var legacyModel = new CompoundTag();
        legacyModel.putString("modelLocation", "photon:block/sphere");
        fixtureConfig.getCompound("renderer").put("model", legacyModel);
        encoded.getCompound("meta").putInt("version_num", 3);
        encoded.getCompound("meta").putString("version", "3.0");
        replaceManagedListUids(encoded);
        NbtIo.write(encoded, projectFile.toFile());

        var loaded = (FXProject) FXProject.TYPE.loadProjectFromFile(projectFile.toFile());
        if (loaded.getFx().getFxData().objects().size() != 3
                || !(loaded.getFx().getFxData().objects().get(0) instanceof ParticleEmitter restored)
                || !(loaded.getFx().getFxData().objects().get(1) instanceof ParticleEmitter restoredTornado)
                || restored.config.renderer.getMaterials().size() != 1
                || restored.config.emission.getBursts().size() != 1
                || restored.config.subEmitters.getEmitters().size() != 1
                || restoredTornado.config.renderer.getMaterials().size() != 1
                || restoredTornado.config.emission.getBursts().size() != 1
                || !(loaded.getFx().getFxData().objects().get(2) instanceof ParticleEmitter)
                || restored.config.emission.getBursts().get(0).cycles != 1) {
            throw new IllegalStateException("v3 managed lists were not restored: " + loaded.serializeProject(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry()));
        }
        if (!(restored.config.renderer.getModelSource() instanceof ObjModelSource sphere)
                || !sphere.getModelLocation().equals(new ResourceLocation("photon", "models/sphere.obj"))
                || sphere.getMesh().vertexCount() < 3) {
            throw new IllegalStateException("Legacy photon:block/sphere did not resolve to the bundled sphere OBJ");
        }
        Photon.LOGGER.info("[legacy-model-alias-smoke] photon:block/sphere resolved to bundled OBJ with {} vertices",
                sphere.getMesh().vertexCount());
        long tChannel = PhotonGpuChannels.CHANNELS.stream()
                .filter(channel -> channel.id().equals("addition_gpu_data.t"))
                .findFirst().orElseThrow().bit();
        var restoredModel = restoredTornado.config.renderer.getModelSource();
        var restoredTornadoMaterial = restoredTornado.config.renderer.getMaterials().get(0).getMaterial();
        boolean objLoaded = restoredModel instanceof JsonModelSource obj && obj.getMesh() != null && obj.getMesh().vertexCount() >= 3;
        boolean tChannelEnabled = restoredTornado.config.additionalGPUDataSetting.isEnable()
                && (restoredTornado.config.additionalGPUDataSetting.userMask() & tChannel) != 0;
        boolean shaderLoaded = restoredTornadoMaterial instanceof CustomShaderMaterial shader
                && !shader.isCompiledError() && shader.curveTexture.getCurveTexture() != null;
        if (!objLoaded || !tChannelEnabled || !shaderLoaded) {
            Photon.LOGGER.error("[tornado-render-smoke] reload diagnostics: model={} objLoaded={} tEnabled={} tMask={} tBit={} material={} shaderLoaded={}",
                    restoredModel, objLoaded, restoredTornado.config.additionalGPUDataSetting.isEnable(),
                    restoredTornado.config.additionalGPUDataSetting.userMask(), tChannel,
                    restoredTornadoMaterial.getClass().getName(), shaderLoaded);
            if (restoredTornadoMaterial instanceof CustomShaderMaterial shader) {
                Photon.LOGGER.error("[tornado-render-smoke] shader diagnostics: error={} curveTexture={}",
                        shader.getCompiledErrorMessage(), shader.curveTexture.getCurveTexture());
            }
            throw new IllegalStateException("Tornado-style OBJ, T channel, custom shader, or curve sampler did not survive v3 project loading");
        }
        Photon.LOGGER.info("[tornado-render-smoke] loaded LDLib2 OBJ with location-9 T data, GPU shader variant, and SamplerCurve");

        previousScreen = minecraft.screen;
        editor = new FXEditor();
        minecraft.setScreen(new ModularUIScreen(new ModularUI(UI.of(editor)), Component.literal("Photon CI editor")));
        editor.loadProject(loaded, projectFile.toFile());
        editor.sceneView.fxObjectInfoView.setDisplay(false);
        editor.sceneView.fxObjectAnimationView.setDisplay(false);
        if (editor.getCurrentProject() != loaded || editor.runtime == null || editor.runtime.objects.size() < 2) {
            throw new IllegalStateException("Editor did not initialize the loaded project runtime");
        }
        Photon.LOGGER.info("[fxproj-editor-smoke] initialized v3 project with materials, bursts and sub-emitters");
    }

    @SuppressWarnings("unchecked")
    private static void enableLegacyTChannel(ParticleEmitter emitter) throws ReflectiveOperationException {
        var setting = emitter.config.additionalGPUDataSetting;
        setting.setEnable(true);
        Field field = setting.getClass().getDeclaredField("additionalData");
        field.setAccessible(true);
        ((Set<String>) field.get(setting)).add("addition_gpu_data.t");
    }

    private static void replaceManagedListUids(Tag value) {
        if (value instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                Tag child = compound.get(key);
                if ((key.equals("materials") || key.equals("bursts") || key.equals("emitters"))
                        && child instanceof CompoundTag managed
                        && managed.contains("uid") && managed.get("payload") instanceof ListTag payload) {
                    managed.put("uid", IntTag.valueOf(payload.size()));
                }
                replaceManagedListUids(child);
            }
        } else if (value instanceof ListTag list) {
            for (Tag child : list) replaceManagedListUids(child);
        }
    }

    private record CollidingKey(String name) {
        @Override public int hashCode() { return 0; }
    }

    private static void verifyNestedMeshLoad() {
        var cache = PhotonMeshCache.INSTANCE;
        var parent = new CollidingKey("ci-parent");
        var child = new CollidingKey("ci-child");
        try {
            var loaded = cache.get(parent, ignored -> {
                cache.invalidate(child);
                return cache.get(child, alsoIgnored -> PhotonMesh.EMPTY);
            });
            if (loaded != PhotonMesh.EMPTY) throw new IllegalStateException("Nested mesh load changed its result");
            Photon.LOGGER.info("[mesh-cache-smoke] nested load and invalidation with colliding keys succeeded");
        } finally {
            cache.invalidate(parent);
            cache.invalidate(child);
        }
    }
}



