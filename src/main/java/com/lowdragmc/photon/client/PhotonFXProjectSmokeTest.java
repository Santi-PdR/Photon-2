package com.lowdragmc.photon.client;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.ParticleQueueRenderType;
import com.lowdragmc.photon.core.mixins.accessor.ParticleEngineAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.JsonModelSource;
import com.lowdragmc.photon.gui.editor.FXProject;

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
            emitter.config.emission.getBursts().add(new EmissionSetting.Burst());
            project.getFx().getFxData().objects().add(emitter);

            file = Files.createTempFile("photon-fxproj-smoke-", ".fxproj");
            FXProject.TYPE.saveProjectToFile(project, file.toFile());
            FXProject.TYPE.loadProjectFromFile(file.toFile());
            Photon.LOGGER.info("[fxproj-smoke] loaded synthetic .fxproj with burst");
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
