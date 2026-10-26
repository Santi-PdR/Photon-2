package com.lowdragmc.photon.client;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.JsonModelSource;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.resources.ResourceLocation;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.MaterialSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.SubEmittersSetting;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

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
        } catch (Exception failure) {
            throw new IllegalStateException("Could not prepare OBJ model smoke fixture", failure);
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
        if (minecraft.getOverlay() != null || minecraft.screen == null) return;
        try {
            if (!started) {
                started = true;
                openLegacyProject(minecraft);
            } else if (++ticks == 30) {
                int queued = editor.sceneView.particleManager.particlesByRenderType().values().stream()
                        .mapToInt(Collection::size).sum();
                if (queued == 0) throw new IllegalStateException("Editor preview did not queue any particles");
                Screenshot.grab(new File("."), minecraft.getMainRenderTarget(),
                        message -> Photon.LOGGER.info("[fxproj-editor-smoke] screenshot: {}", message.getString()));
                Photon.LOGGER.info("[fxproj-editor-smoke] opened v3 managed-list project and previewed {} queued particles", queued);
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
        var source = new FXProject();
        var emitter = new ParticleEmitter();
        emitter.config.renderer.getMaterials().add(new MaterialSetting());
        var burst = new EmissionSetting.Burst();
        burst.time = 0;
        burst.cycles = 2;
        burst.interval = 4;
        emitter.config.emission.getBursts().add(burst);
        emitter.config.subEmitters.setEnable(true);
        emitter.config.subEmitters.getEmitters().add(new SubEmittersSetting.Emitter());
        source.getFx().getFxData().objects().add(emitter);

        projectFile = Files.createTempFile("photon-editor-v3-", ".fxproj");
        FXProject.TYPE.saveProjectToFile(source, projectFile.toFile());
        var encoded = NbtIo.read(projectFile.toFile());
        var fixtureConfig = encoded.getCompound("data").getCompound("fx").getCompound("fxData")
                .getList("fxObjects", Tag.TAG_COMPOUND).getCompound(0).getCompound("data").getCompound("config");
        if (fixtureConfig.getCompound("subEmitters").getCompound("emitters")
                .getList("payload", Tag.TAG_COMPOUND).size() != 1) {
            throw new IllegalStateException("The enabled sub-emitter fixture was not written: " + fixtureConfig.getCompound("subEmitters"));
        }
        encoded.getCompound("meta").putInt("version_num", 3);
        encoded.getCompound("meta").putString("version", "3.0");
        replaceManagedListUids(encoded);
        NbtIo.write(encoded, projectFile.toFile());

        var loaded = (FXProject) FXProject.TYPE.loadProjectFromFile(projectFile.toFile());
        if (loaded.getFx().getFxData().objects().size() != 1
                || !(loaded.getFx().getFxData().objects().get(0) instanceof ParticleEmitter restored)
                || restored.config.renderer.getMaterials().size() != 1
                || restored.config.emission.getBursts().size() != 1
                || restored.config.subEmitters.getEmitters().size() != 1
                || restored.config.emission.getBursts().get(0).cycles != 2) {
            throw new IllegalStateException("v3 managed lists were not restored: " + loaded.serializeProject(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry()));
        }

        previousScreen = minecraft.screen;
        editor = new FXEditor();
        minecraft.setScreen(new ModularUIScreen(new ModularUI(UI.of(editor)), Component.literal("Photon CI editor")));
        editor.loadProject(loaded, projectFile.toFile());
        if (editor.getCurrentProject() != loaded || editor.runtime == null || editor.runtime.objects.size() < 2) {
            throw new IllegalStateException("Editor did not initialize the loaded project runtime");
        }
        Photon.LOGGER.info("[fxproj-editor-smoke] initialized v3 project with materials, bursts and sub-emitters");
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
}

