package com.lowdragmc.photon.client;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.ParticleQueueRenderType;
import com.lowdragmc.photon.core.mixins.accessor.ParticleEngineAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.JsonModelSource;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Opt-in CI smoke test for the editor's on-disk .fxproj persistence path.
 * It is dormant during normal play and only runs with -Dphoton.fxprojSmokeTest=true.
 */
public final class PhotonFXProjectSmokeTest {
    private PhotonFXProjectSmokeTest() {}

    public static void runIfEnabled() {
        if (!Boolean.getBoolean("photon.fxprojSmokeTest")) return;

        Path file = null;
        Path objModelJson = null;
        var photon1ProjectFiles = new java.util.ArrayList<Path>();
        var photon1ConvertedFiles = new java.util.ArrayList<Path>();
        try {
            if (!(Minecraft.getInstance().particleEngine instanceof ParticleEngineAccessor)) {
                throw new IllegalStateException("Photon ParticleEngine accessor mixin was not applied");
            }
            ParticleQueueRenderType.OPAQUE_QUEUE.hasQueuedParticles();
            Photon.LOGGER.info("[mixin-smoke] ParticleEngine accessor path is callable");

            var objLocation = new ResourceLocation("photon", "block/photon_obj_smoke");
            var modelFile = net.minecraft.client.resources.model.ModelBakery.MODEL_LISTER.idToFile(objLocation);
            objModelJson = LDLib2.getAssetsDir().toPath()
                    .resolve("photon").resolve("assets").resolve("photon")
                    .resolve(modelFile.getPath());
            Files.createDirectories(objModelJson.getParent());
            Files.writeString(objModelJson,
                    "{\"loader\":\"neoforge:obj\",\"model\":\"photon:models/obj/photon_obj_smoke.obj\"}");
            if (!new JsonModelSource(objLocation).isObjLoaderModel()) {
                throw new IllegalStateException("NeoForge OBJ JSON was not detected as a direct Photon mesh");
            }
            Photon.LOGGER.info("[obj-model-smoke] detected NeoForge OBJ JSON without model bakery registration");

            var project = new FXProject();
            var emitter = new ParticleEmitter();
            var burst = new EmissionSetting.Burst();
            burst.time = 23;
            burst.interval = 3;
            burst.cycles = 4;
            burst.probability = 0.75f;
            emitter.config.emission.getBursts().add(burst);
            project.getFx().getFxData().objects().add(emitter);

            file = Files.createTempFile("photon-fxproj-smoke-", ".fxproj");
            FXProject.TYPE.saveProjectToFile(project, file.toFile());
            FXProject.TYPE.loadProjectFromFile(file.toFile());
            Photon.LOGGER.info("[fxproj-smoke] loaded synthetic .fxproj with burst");

            // Recreate the managed-list envelope written by Photon 26.2 projects. The
            // fixture is generated in memory so user projects never leave the runner.
            var encodedProject = NbtIo.read(file.toFile());
            var fxObjects = encodedProject.getCompound("data").getCompound("fx")
                    .getCompound("fxData").getList("fxObjects", Tag.TAG_COMPOUND);
            if (fxObjects.isEmpty()) {
                throw new IllegalStateException("Synthetic project did not contain its particle emitter");
            }
            var emitterData = fxObjects.getCompound(0).getCompound("data");
            if (!emitterData.contains("config", Tag.TAG_COMPOUND)) {
                throw new IllegalStateException("Synthetic particle emitter did not serialize its config compound");
            }
            var emitterConfig = emitterData.getCompound("config");
            if (!emitterConfig.contains("emission", Tag.TAG_COMPOUND)) {
                throw new IllegalStateException("Synthetic particle emitter did not serialize its emission compound");
            }
            var emission = emitterConfig.getCompound("emission");
            var payload = new ListTag();
            payload.add(burst.serializeNBT(Platform.getFrozenRegistry()));
            var envelope = new CompoundTag();
            envelope.putInt("uid", 0);
            envelope.put("payload", payload);
            emission.put("bursts", envelope);
            NbtIo.write(encodedProject, file.toFile());

            var loaded = FXProject.TYPE.loadProjectFromFile(file.toFile());
            if (!(loaded instanceof FXProject loadedProject)
                    || loadedProject.getFx().getFxData().objects().isEmpty()
                    || !(loadedProject.getFx().getFxData().objects().get(0) instanceof ParticleEmitter loadedEmitter)) {
                throw new IllegalStateException("Legacy .fxproj did not restore its particle emitter");
            }
            var loadedBursts = loadedEmitter.config.emission.getBursts();
            if (loadedBursts.size() != 1 || loadedBursts.get(0).time != 23
                    || loadedBursts.get(0).interval != 3 || loadedBursts.get(0).cycles != 4
                    || Float.compare(loadedBursts.get(0).probability, 0.75f) != 0) {
                throw new IllegalStateException("Legacy .fxproj burst fields were not restored correctly");
            }
            Photon.LOGGER.info("[fxproj-legacy-smoke] loaded Photon 26.2 managed-list .fxproj and preserved burst fields");

            // Photon 1 editor projects used raw (uncompressed) NBT and two root layouts.
            // Opening either one must import to a sibling Photon 2 project and leave the source intact.
            for (int layout = 0; layout < 2; layout++) {
                var legacyDocument = new CompoundTag();
                var oldObjects = new ListTag();
                if (layout == 0) {
                    legacyDocument.put("emitters", oldObjects);
                } else {
                    var oldFx = new CompoundTag();
                    var mainFx = new CompoundTag();
                    mainFx.put("fxObjects", oldObjects);
                    oldFx.put("mainFX", mainFx);
                    oldFx.put("subFXs", new CompoundTag());
                    legacyDocument.put("fx", oldFx);
                }
                legacyDocument.put("resources", new CompoundTag());
                var legacyFile = Files.createTempFile("photon1-raw-project-", ".fxproj");
                photon1ProjectFiles.add(legacyFile);
                NbtIo.write(legacyDocument, legacyFile.toFile());
                var imported = FXProject.TYPE.loadProjectFromFile(legacyFile.toFile());
                if (!(imported instanceof FXProject)) {
                    throw new IllegalStateException("Photon 1 raw .fxproj did not open as a Photon 2 project");
                }
                var stem = legacyFile.getFileName().toString();
                stem = stem.substring(0, stem.length() - FXProject.TYPE.getSuffix().length());
                var convertedFile = legacyFile.resolveSibling(stem + "_photon2" + FXProject.TYPE.getSuffix());
                photon1ConvertedFiles.add(convertedFile);
                if (!Files.isRegularFile(convertedFile)
                        || !NbtIo.read(legacyFile).equals(legacyDocument)
                        || !NbtIo.read(convertedFile).contains("data", Tag.TAG_COMPOUND)) {
                    throw new IllegalStateException("Photon 1 .fxproj migration overwrote its source or did not save Photon 2 data");
                }
            }
            Photon.LOGGER.info("[photon1-editor-project-smoke] opened both raw Photon 1 .fxproj layouts and preserved the originals");
        } catch (Throwable failure) {
            Photon.LOGGER.error("[fxproj-smoke] failed to load synthetic .fxproj", failure);
            throw new IllegalStateException("Photon FX project persistence smoke test failed", failure);
        } finally {
            if (objModelJson != null) {
                try {
                    Files.deleteIfExists(objModelJson);
                } catch (IOException cleanupFailure) {
                    Photon.LOGGER.warn("[obj-model-smoke] could not remove temporary model JSON {}", objModelJson, cleanupFailure);
                }
            }
            for (var converted : photon1ConvertedFiles) {
                try {
                    Files.deleteIfExists(converted);
                } catch (IOException cleanupFailure) {
                    Photon.LOGGER.warn("[photon1-editor-project-smoke] could not remove {}", converted, cleanupFailure);
                }
            }
            for (var legacy : photon1ProjectFiles) {
                try {
                    Files.deleteIfExists(legacy);
                } catch (IOException cleanupFailure) {
                    Photon.LOGGER.warn("[photon1-editor-project-smoke] could not remove {}", legacy, cleanupFailure);
                }
            }
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException cleanupFailure) {
                    Photon.LOGGER.warn("[fxproj-smoke] could not remove temporary project {}", file, cleanupFailure);
                }
            }
        }
    }

}

