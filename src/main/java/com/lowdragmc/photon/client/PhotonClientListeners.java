package com.lowdragmc.photon.client;

import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.compat.iris.IrisOverlay;
import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.OpaqueDepthCapture;
import com.lowdragmc.photon.client.render.MaterialPreviewRenderer;
import com.lowdragmc.photon.client.postfx.PhotonPostFX;
import com.lowdragmc.photon.client.postfx.runtime.PostFXCamera;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.List;

@EventBusSubscriber(modid = Photon.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.FORGE)
public class PhotonClientListeners {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        List<LiteralArgumentBuilder<CommandSourceStack>> commands = ClientCommands.createClientCommands();
        commands.forEach(dispatcher::register);
    }

    /**
     * Two seams in the level render:
     *
     * <ul>
     *   <li><b>AFTER_BLOCK_ENTITIES</b> — the last stage before {@code RenderType.translucent()} goes
     *       down. Snapshot the opaque-only depth {@code FXCompositeMode.LATE} depth-tests against, so
     *       a water surface cannot slice an effect in half.</li>
     *   <li><b>AFTER_PARTICLES</b> — standalone post-effect consumption for frames without Photon
     *       particles (the particle pipeline seam never runs then).</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            // Particle materials and the render-pass pipeline consume this before vanilla particles draw.
            capturePostFXCamera(event);
            OpaqueDepthCapture.capture();
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            // Standalone post-FX consumption and the deferred composite run from this later seam.
            capturePostFXCamera(event);
            PhotonPostFX.onLevelStageAfterParticles();
        }
    }

    private static void capturePostFXCamera(RenderLevelStageEvent event) {
        PostFXCamera.capture(event.getPoseStack().last().pose(), event.getProjectionMatrix(),
                event.getCamera().getPosition());
    }

    /** Execute queued material previews before the GUI begins drawing. */
    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        event.getGuiGraphics().flush();
        MaterialPreviewRenderer.processPending();
    }

    /** Opt-in shader-pack layout readout (/photon_iris overlay). */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        IrisOverlay.render(event.getGuiGraphics());
    }
}
