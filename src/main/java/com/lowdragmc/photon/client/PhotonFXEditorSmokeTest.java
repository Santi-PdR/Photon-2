package com.lowdragmc.photon.client;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.JsonModelSource;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.ObjModelSource;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.PhotonMesh;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.PhotonMeshCache;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.CustomShaderMaterial;
import com.lowdragmc.photon.client.util.PhotonPaths;
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
import com.lowdragmc.photon.client.gameobject.emitter.trail.TrailEmitter;
import com.lowdragmc.photon.client.gameobject.particle.TrailParticle;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.File;
import java.util.ArrayList;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

/** Exercises the editor load and preview path only in the opt-in remote client smoke run. */
public final class PhotonFXEditorSmokeTest {
    private static boolean started;
    private static boolean finished;
    private static boolean bloomSmokeLogged;
    private static int ticks;
    private static int tornadoFrameWait;
    private static FXEditor editor;
    private static Screen previousScreen;
    private static Path projectFile;
    private static TrailEmitter smokeTrail;
    private static com.lowdragmc.photon.client.gameobject.emitter.aratrail.AraTrailEmitter smokeAraTrail;
    private static TrailEmitter smokeCpuTrail;
    private static TrailEmitter realArrowTrail;
    private static float savedSceneZoom;
    private static org.joml.Vector3f savedSceneCenter;
    private static CustomShaderMaterial smokeScreenShader;
    private static com.lowdragmc.photon.client.fx.FXRuntime removedEntityRuntime;
    private static com.lowdragmc.photon.client.fx.EntityEffectExecutor firstPersonEffect;
    private static boolean exportReloadComplete;
    private static boolean exportServerCommandVerified;
    private static boolean exportRemoveVerified;
    private static Throwable exportReloadFailure;
    private static boolean serverFxPackSyncLogged;
    private static int serverFxPackSyncTicks;

    private PhotonFXEditorSmokeTest() {}

    /** Exercise the real framebuffer copy/composite rather than only checking routing flags. */
    private static void verifyLateSceneSnapshot() {
        int previous = com.mojang.blaze3d.platform.GlStateManager.getBoundFramebuffer();
        int vx = com.mojang.blaze3d.platform.GlStateManager.Viewport.x();
        int vy = com.mojang.blaze3d.platform.GlStateManager.Viewport.y();
        int vw = com.mojang.blaze3d.platform.GlStateManager.Viewport.width();
        int vh = com.mojang.blaze3d.platform.GlStateManager.Viewport.height();
        var scene = com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline.resize(null, 4, 4, true);
        var layer = com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline.resize(null, 4, 4, true);
        var snapshot = com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline.resize(null, 4, 4, true);
        try {
            scene.setClearColor(0, 0, 1, 1);
            scene.clear(Minecraft.ON_OSX);
            layer.setClearColor(0.5f, 0, 0, 0.5f);
            layer.clear(Minecraft.ON_OSX);
            com.mojang.blaze3d.systems.RenderSystem.viewport(0, 0, 4, 4);
            com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline.copyLateSceneColor(scene, layer, snapshot);
            snapshot.bindWrite(false);
            var pixel = org.lwjgl.BufferUtils.createFloatBuffer(4);
            org.lwjgl.opengl.GL11.glReadPixels(2, 2, 1, 1, org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_FLOAT, pixel);
            if (Math.abs(pixel.get(0) - 0.5f) > 0.03f || Math.abs(pixel.get(2) - 0.5f) > 0.03f)
                throw new IllegalStateException("Late scene snapshot lost accumulated FX: red=" + pixel.get(0) + " blue=" + pixel.get(2));
            Photon.LOGGER.info("[late-scene-composite-smoke] scene sampling includes previously drawn FX");
        } finally {
            scene.destroyBuffers();
            layer.destroyBuffers();
            snapshot.destroyBuffers();
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, previous);
            com.mojang.blaze3d.systems.RenderSystem.viewport(vx, vy, vw, vh);
        }
    }

    private static void verifyLegacyBlendSafety() {
        for (var pair : java.util.Map.of("sub", "SUB", "REVERSE_sub", "REVERSE_SUB").entrySet()) {
            var material = new net.minecraft.nbt.CompoundTag();
            var blend = new net.minecraft.nbt.CompoundTag();
            blend.putString("blendFunc", pair.getKey());
            material.put("blendMode", blend);
            var mapped = com.lowdragmc.photon.client.fx.compat.MapperUtils.mapMaterial(material);
            if (!mapped.getCompound("blendMode").getString("blendFunc").equals(pair.getValue())
                    || !material.getCompound("blendMode").getString("blendFunc").equals(pair.getKey())) {
                throw new IllegalStateException("Legacy blend conversion lost or mutated " + pair.getKey());
            }
        }
        var mode = new com.lowdragmc.photon.client.gameobject.emitter.data.material.BlendMode();
        mode.setBlendFunc(null);
        mode.setSrcColorFactor(null);
        mode.setDstColorFactor(null);
        mode.setSrcAlphaFactor(null);
        mode.setDstAlphaFactor(null);
        if (mode.getBlendFunc() != com.lowdragmc.photon.client.gameobject.emitter.data.material.BlendMode.BlendFuc.ADD
                || mode.getSrcColorFactor() == null || mode.getDstColorFactor() == null
                || mode.getSrcAlphaFactor() == null || mode.getDstAlphaFactor() == null) {
            throw new IllegalStateException("Invalid blend fallback failed");
        }
        com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.PremultipliedBlendPlan.isLayerSafe(mode);
        mode.apply();
        Photon.LOGGER.info("[legacy-blend-smoke] legacy equations converted without mutation and invalid blend fields are safe");
    }

    /** Exercise the scene-masked bright pass with a bright sky and a small copy mismatch. */
    private static void verifyBloomSceneMask() {
        int framebuffer = com.mojang.blaze3d.platform.GlStateManager.getBoundFramebuffer();
        int vx = com.mojang.blaze3d.platform.GlStateManager.Viewport.x();
        int vy = com.mojang.blaze3d.platform.GlStateManager.Viewport.y();
        int vw = com.mojang.blaze3d.platform.GlStateManager.Viewport.width();
        int vh = com.mojang.blaze3d.platform.GlStateManager.Viewport.height();
        var scene = com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline.resize(null, 64, 64, false);
        var input = com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline.resize(null, 64, 64, false);
        var output = com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline.resize(null, 32, 32, false);
        try {
            scene.setClearColor(0.6f, 0.6f, 0.6f, 1); scene.clear(Minecraft.ON_OSX);
            input.setClearColor(0.62f, 0.62f, 0.62f, 1); input.clear(Minecraft.ON_OSX);
            input.bindWrite(false);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            org.lwjgl.opengl.GL11.glScissor(24, 24, 16, 16);
            org.lwjgl.opengl.GL11.glClearColor(1f, 0.6f, 0.6f, 1);
            org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            var shader = com.lowdragmc.photon.client.PhotonShaders.getBrightPassShader();
            if (shader == null) throw new IllegalStateException("Missing bloom bright pass shader");
            com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            shader.setSampler("inputSampler", input);
            shader.setSampler("sceneSampler", scene);
            shader.safeGetUniform("Threshold").set(0.5f);
            shader.safeGetUniform("Knee").set(0.1f);
            shader.safeGetUniform("SceneMask").set(1f);
            com.lowdragmc.photon.client.postprocessing.PhotonPostProcessing.blitShader(shader, output, false);
            output.bindWrite(false);
            var pixel = org.lwjgl.BufferUtils.createFloatBuffer(4);
            org.lwjgl.opengl.GL11.glReadPixels(4, 4, 1, 1, org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_FLOAT, pixel);
            if (pixel.get(0) > 0.03f) throw new IllegalStateException("Tiny scene mismatch amplified into a bright halo: " + pixel.get(0));
            org.lwjgl.opengl.GL11.glReadPixels(16, 16, 1, 1, org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_FLOAT, pixel);
            if (pixel.get(0) < 0.15f || pixel.get(1) > 0.03f)
                throw new IllegalStateException("Scene mask lost or recoloured real FX contribution: " + pixel.get(0) + "," + pixel.get(1));
            Photon.LOGGER.info("[bloom-scene-mask-smoke] bright sky mismatch stays dim while FX contribution survives");
        } finally {
            scene.destroyBuffers(); input.destroyBuffers(); output.destroyBuffers();
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, framebuffer);
            com.mojang.blaze3d.systems.RenderSystem.viewport(vx, vy, vw, vh);
        }
    }

    /** Prove that an ordinary red FX pixel reaches the built-in bloom halo at the shipped defaults. */
    private static void verifyBloomDefaults() {
        var expectedBuiltinKilaMaterials = com.lowdragmc.photon.client.gameobject.emitter.data.material.kila.KilaPresets.ALL.size() + 1;
        var actualBuiltinKilaMaterials = com.lowdragmc.photon.gui.editor.resource.MaterialResource.INSTANCE.getResourceInstance()
                .listAllResourceEntries().stream()
                .filter(entry -> entry.path() instanceof com.lowdragmc.lowdraglib2.editor.resource.BuiltinPath)
                .map(com.lowdragmc.lowdraglib2.editor.resource.ResourceInstance.ResourceEntry::getResource)
                .filter(com.lowdragmc.photon.client.gameobject.emitter.data.material.kila.KilaMaterial.class::isInstance)
                .count();
        if (actualBuiltinKilaMaterials < expectedBuiltinKilaMaterials) {
            throw new IllegalStateException("Material picker exposes " + actualBuiltinKilaMaterials
                    + " built-in Kila materials; expected " + expectedBuiltinKilaMaterials);
        }
        Photon.LOGGER.info("[builtin-material-picker-smoke] Kila and all built-in material presets are listed in the material selector");
        com.lowdragmc.photon.client.fx.FXBloom.endFrame();
        var config = com.lowdragmc.photon.PhotonConfig.INSTANCE;
        var oldEnabled = config.enableBloom.get();
        var oldThreshold = config.bloomThreshold.get();
        var oldIntensity = config.bloomIntensity.get();
        int previousFramebuffer = com.mojang.blaze3d.platform.GlStateManager.getBoundFramebuffer();
        int vx = com.mojang.blaze3d.platform.GlStateManager.Viewport.x();
        int vy = com.mojang.blaze3d.platform.GlStateManager.Viewport.y();
        int vw = com.mojang.blaze3d.platform.GlStateManager.Viewport.width();
        int vh = com.mojang.blaze3d.platform.GlStateManager.Viewport.height();
        var source = com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline
                .resize(null, 64, 64, false);
        try {
            config.enableBloom.set(true);
            config.bloomThreshold.set(0.5);
            config.bloomIntensity.set(0.18);
            source.setClearColor(0.3f, 0.3f, 0.3f, 1);
            source.clear(Minecraft.ON_OSX);
            source.bindWrite(false);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            org.lwjgl.opengl.GL11.glScissor(30, 30, 4, 4);
            org.lwjgl.opengl.GL11.glClearColor(1, 0, 0, 1);
            org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);

            com.lowdragmc.photon.client.postprocessing.PhotonPostProcessing.prepareTarget(64, 64);
            var output = com.lowdragmc.photon.client.postprocessing.PhotonPostProcessing.postTarget(source);
            output.bindWrite(false);
            var pixel = org.lwjgl.BufferUtils.createFloatBuffer(4);
            org.lwjgl.opengl.GL11.glReadPixels(38, 32, 1, 1, org.lwjgl.opengl.GL11.GL_RGBA,
                    org.lwjgl.opengl.GL11.GL_FLOAT, pixel);
            if (!(pixel.get(0) > 0.001f)) {
                throw new IllegalStateException("Ordinary red FX did not produce a bloom halo: red=" + pixel.get(0));
            }
            var background = org.lwjgl.BufferUtils.createFloatBuffer(4);
            org.lwjgl.opengl.GL11.glReadPixels(8, 8, 1, 1, org.lwjgl.opengl.GL11.GL_RGBA,
                    org.lwjgl.opengl.GL11.GL_FLOAT, background);
            if (Math.abs(background.get(0) - 0.3f) > 0.04f || Math.abs(background.get(1) - 0.3f) > 0.04f
                    || Math.abs(background.get(2) - 0.3f) > 0.04f) {
                throw new IllegalStateException("Bloom altered non-highlight scene pixels: rgb="
                        + background.get(0) + "," + background.get(1) + "," + background.get(2));
            }
            Photon.LOGGER.info("[bloom-smoke] red FX halo is present while non-highlight scene pixels stay intact (halo red={})", pixel.get(0));
        } finally {
            config.enableBloom.set(oldEnabled);
            config.bloomThreshold.set(oldThreshold);
            config.bloomIntensity.set(oldIntensity);
            source.destroyBuffers();
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER,
                    previousFramebuffer);
            com.mojang.blaze3d.systems.RenderSystem.viewport(vx, vy, vw, vh);
        }
    }

    /**
     * A search box that has a value already chosen must still open on the whole catalogue.
     * <p>
     * This is the reported symptom: "the particle picker and the fx list are still the same" — the dropdown only
     * ever showed the value already selected, and typing made it worse because the first keystroke was appended to
     * that leftover name instead of replacing it. The cause is in the widget itself (LDLib 2.2.39, fixed upstream in
     * 2.2.39.8): it searches on open with the text the field still holds, which is the current value's name.
     * {@code SearchComponentMixin} re-runs the search empty and selects the text.
     * <p>
     * The box is built the way the editor builds one (a {@code SearchComponentConfigurator} over a two-entry
     * catalogue), given a selected value, and opened. Without the fix the dialog holds that one entry; with it, both.
     */
    private static void verifySearchBoxOpensOnTheWholeCatalogue() {
        var queried = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var candidateCount = new java.util.concurrent.atomic.AtomicInteger();
        var searchDone = new java.util.concurrent.CountDownLatch(1);
        var configurator = new com.lowdragmc.lowdraglib2.configurator.ui.SearchComponentConfigurator<>(
                "catalogue", () -> "photon:chosen", value -> { }, "photon:chosen", false,
                (word, handler) -> {
                    queried.add(word == null ? "<null>" : word);
                    int matched = 0;
                    for (var candidate : java.util.List.of("photon:chosen", "photon:other")) {
                        if (Thread.currentThread().isInterrupted()) return;
                        if (candidate.contains(word == null ? "" : word)) {
                            handler.accept(candidate);
                            matched++;
                        }
                    }
                    if ((word == null || word.isEmpty()) && matched == 2) {
                        candidateCount.set(matched);
                        searchDone.countDown();
                    }
                },
                value -> value,
                com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider.text(
                        value -> Component.literal(value == null ? "---" : value)));
        var box = configurator.searchComponent;
        if (!"photon:chosen".equals(box.getValue())) {
            throw new IllegalStateException("The search box did not start on the chosen value");
        }
        // What show() does, minus the dialog (there is no screen to add it to in a headless run): the box runs its
        // search and the candidates are pulled in by the search engine, which is asynchronous.
        box.show();
        try {
            if (!searchDone.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("Opening the box searched " + queried + " but did not collect both effects");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the picker search", e);
        }
        // and the fix asks for the whole catalogue, not for the value already chosen
        if (!queried.contains("")) {
            throw new IllegalStateException("Opening the search box never searched the whole catalogue, only "
                    + queried);
        }
        // what the author then types replaces the leftover name instead of being appended to it
        var field = readField(box, "textField");
        if (!(field instanceof com.lowdragmc.lowdraglib2.gui.ui.elements.TextField text)
                || text.getSelectionStart() != 0 || text.getSelectionEnd() != text.getValue().length()) {
            throw new IllegalStateException("The chosen name was left unselected, so typing would append to it");
        }
        Photon.LOGGER.info("[search-box-smoke] opening a picker with a value already chosen lists the whole "
                + "catalogue ({}) and selects the old name so typing replaces it", candidateCount.get());
    }

    /** Bloom enable + intensity live on the FX, survive a save, and default the old look when missing. */
    private static void verifyFxBloomRoundtrip() {
        var data = new com.lowdragmc.photon.client.fx.FXData();
        if (!data.bloomEnabled() || Math.abs(data.bloomIntensity()
                - com.lowdragmc.photon.client.fx.FXData.DEFAULT_BLOOM_INTENSITY) > 0.0001f) {
            throw new IllegalStateException("A new FX did not default to bloom on at the old intensity");
        }
        data.setBloomEnabled(false);
        data.setBloomIntensity(1.25f);
        var provider = com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry();
        var tag = data.serializeNBT(provider);
        var copy = data.copy(false);
        if (copy.bloomEnabled() || Math.abs(copy.bloomIntensity() - 1.25f) > 0.0001f) {
            throw new IllegalStateException("FXData.copy dropped bloom");
        }
        var loaded = new com.lowdragmc.photon.client.fx.FXData();
        loaded.deserializeNBT(provider, tag);
        if (loaded.bloomEnabled() || Math.abs(loaded.bloomIntensity() - 1.25f) > 0.0001f) {
            throw new IllegalStateException("FX bloom did not survive save: enabled=" + loaded.bloomEnabled()
                    + " intensity=" + loaded.bloomIntensity());
        }
        var legacy = new com.lowdragmc.photon.client.fx.FXData();
        legacy.deserializeNBT(provider, new net.minecraft.nbt.CompoundTag());
        if (!legacy.bloomEnabled() || Math.abs(legacy.bloomIntensity()
                - com.lowdragmc.photon.client.fx.FXData.DEFAULT_BLOOM_INTENSITY) > 0.0001f) {
            throw new IllegalStateException("A file without bloom keys did not keep the old look");
        }
        Photon.LOGGER.info("[fx-bloom-smoke] enable+intensity are saved on the FX and default on at {}",
                com.lowdragmc.photon.client.fx.FXData.DEFAULT_BLOOM_INTENSITY);
    }

    /** Read a private field of a library widget (the test drives the real widget, not a copy of it). */
    private static Object readField(Object owner, String name) {
        try {
            var field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read " + owner.getClass().getSimpleName() + "." + name, e);
        }
    }

    /**
     * Prove the two editor entry points that were reported broken: the fx picker lists effect ids (a list that
     * must be read live, not frozen when the panel was built) and the scene bar's bloom control writes this FX's
     * intensity, not a client-wide config.
     */
    private static void verifyFxPickerAndBloomControl() {
        var fxIds = com.lowdragmc.photon.client.fx.FXHelper.listAllFX();
        var exampleId = new net.minecraft.resources.ResourceLocation("photon", "nested/example");
        var fakeRoot = java.nio.file.Path.of("test-game");
        if (!com.lowdragmc.photon.client.fx.FXSourceReport.knownFile(fakeRoot, "fxpack/legacy-ldlib", exampleId)
                .equals(fakeRoot.resolve("ldlib/assets/photon/fx/nested/example.fx"))
                || !com.lowdragmc.photon.client.fx.FXSourceReport.knownFile(fakeRoot, "fxpack/editor-ldlib2", exampleId)
                .equals(fakeRoot.resolve("ldlib2/assets/photon/fx/nested/example.fx"))
                || !com.lowdragmc.photon.client.fx.FXSourceReport.knownFile(fakeRoot, "photon/workspace", exampleId)
                .equals(fakeRoot.resolve("photon/assets/photon/fx/nested/example.fx"))
                || com.lowdragmc.photon.client.fx.FXSourceReport.knownFile(fakeRoot, "mod_resources", exampleId) != null)
            throw new IllegalStateException("FX source report attributed a pack to the wrong path");
        if (!fxIds.isEmpty()) {
            var id = fxIds.get(0);
            var source = Minecraft.getInstance().getResourceManager().getResource(com.lowdragmc.photon.client.fx.FXHelper.fileLocation(id)).orElseThrow();
            if (com.lowdragmc.photon.client.fx.FXSourceReport.describe(id).stream().noneMatch(line -> line.equals("Selected pack: " + source.sourcePackId())))
                throw new IllegalStateException("FX source report omitted the selected resource pack");
        }
        var found = new ArrayList<String>();
        com.lowdragmc.photon.client.fx.FXHelper.searchFX("", found::add);
        var withClearEntry = found.size() == fxIds.size() + 1 && found.get(0).isEmpty();
        var readLive = new ArrayList<String>();
        com.lowdragmc.photon.client.fx.FXHelper.listAllFX()
                .forEach(fx -> readLive.add(fx.toString()));
        if (!withClearEntry || !found.subList(1, found.size()).equals(readLive)) {
            throw new IllegalStateException("The fx picker's search did not list every loadable effect: "
                    + found.size() + " entries for " + fxIds.size() + " effects");
        }
        if (!fxIds.isEmpty()) {
            // and a query narrows the same list: the first effect's own path must find it
            var filtered = new ArrayList<String>();
            com.lowdragmc.photon.client.fx.FXHelper.searchFX(fxIds.get(0).getPath(), filtered::add, false);
            if (!filtered.contains(fxIds.get(0).toString())) {
                throw new IllegalStateException("The fx picker's search did not match an effect by path: "
                        + fxIds.get(0) + " in " + filtered);
            }
        }
        Photon.LOGGER.info("[fx-picker-smoke] the fx search lists every loadable effect ({} ids) and a fresh search reads the live list",
                fxIds.size());

        verifySearchBoxOpensOnTheWholeCatalogue();
        verifyFxBloomRoundtrip();

        var control = new com.lowdragmc.photon.gui.editor.view.scene.SceneBloomIntensityControl();
        var written = control.current();
        if (Math.abs(written - com.lowdragmc.photon.client.fx.FXData.DEFAULT_BLOOM_INTENSITY) > 0.0001f) {
            throw new IllegalStateException("The bloom control shows " + written + " instead of the FX default");
        }
        if (control.slider.getMin() != com.lowdragmc.photon.gui.editor.view.scene.SceneBloomIntensityControl.MIN
                || control.slider.getMax() != com.lowdragmc.photon.gui.editor.view.scene.SceneBloomIntensityControl.MAX) {
            throw new IllegalStateException("The bloom control's slider does not span 0..2");
        }
        Photon.LOGGER.info("[bloom-control-smoke] the scene bar's bloom control reads this FX's intensity ({}) and spans 0..2",
                written);

        control.slider.setValue(0.7f, true);
        var applied = control.current();
        if (Math.abs(applied - 0.7f) > 0.0001f) {
            throw new IllegalStateException("Dragging the bloom control wrote " + applied + " instead of 0.7");
        }
        Photon.LOGGER.info("[bloom-control-smoke] dragging the scene bar's bloom slider writes this FX's bloomIntensity={}", applied);

        verifyLooseTextureLookup();
        verifySliderDragsContinuously();
        verifyCyclicLifetimes();
        verifySections();
        verifySectionCommand();
        verifyDestroyKeepsRemnants();
        verifyVisibleRemnants();
        verifyInspectorPersistedKeys();
        verifyDisabledMenuLeaves();
        verifyDialogSizeMemory();
        verifyPhoton1EntityFeetAnchor();
        verifyLoadedTextureCatalogue();
        verifyParticleDestReuse();
        verifyPoppedOutScreenSize();
    }

    /**
     * {@code /photon fx section ...} is the only way to steer a playing effect from in game, so it is checked end to
     * end: the command is built by the server command tree, the packet survives encoding (that is what travels to the
     * client), and the decoded packet moves a real clock between its sections. Encoding and decoding are where a
     * network command breaks, and a broken one fails silently: the command answers "done" and nothing moves.
     */
    private static void verifySectionCommand() {
        // the subcommand is part of the tree: /photon fx section entity <targets> jump <name> [location]
        var photon = com.lowdragmc.photon.ServerCommands.createServerCommands().get(0);
        if (!"photon".equals(photon.getLiteral())) {
            throw new IllegalStateException("The server command root is not /photon: " + photon.getLiteral());
        }
        var fx = literal(photon, "fx");
        if (fx == null || literal(fx, "section") == null) {
            throw new IllegalStateException("/photon fx section is not registered");
        }
        var sectionNode = literal(fx, "section");
        // the target is an argument (any entities / a block position), and the actions hang off it
        var entity = sectionNode == null ? null : literal(sectionNode, "entity");
        var targets = entity == null ? null : argument(entity, "entities");
        var jump = targets == null ? null : literal(targets, "jump");
        // the section name is an argument, not a literal
        if (jump == null || argument(jump, "section") == null) {
            throw new IllegalStateException("/photon fx section entity jump <section> is not registered");
        }
        var block = sectionNode == null ? null : literal(sectionNode, "block");
        var pos = block == null ? null : argument(block, "pos");
        var advance = pos == null ? null : literal(pos, "advance");
        var release = pos == null ? null : literal(pos, "release");
        if (advance == null || release == null) {
            throw new IllegalStateException("/photon fx section block advance/release are not registered");
        }
        // the two unnamed actions take an optional trailing location
        if (argument(release, "location") == null || argument(advance, "location") == null) {
            throw new IllegalStateException("/photon fx section block release [location] is not registered");
        }

        // a packet as the server builds it, through the wire and back
        var packetType = com.lowdragmc.photon.command.EffectSectionCommand.Action.JUMP;
        var sent = new com.lowdragmc.photon.command.EffectSectionCommand();
        setCommandField(sent, "action", packetType);
        setCommandField(sent, "section", "loop");
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        sent.encode(buf);
        var received = com.lowdragmc.photon.command.EffectSectionCommand.decodePacket(buf);
        if (readCommandField(received, "action") != packetType
                || !"loop".equals(readCommandField(received, "section"))) {
            throw new IllegalStateException("A section command did not survive being encoded: action="
                    + readCommandField(received, "action") + ", section=" + readCommandField(received, "section"));
        }

        // and it does something: the same clock the timeline uses, with two named stretches
        var clock = new com.lowdragmc.photon.client.fx.timeline.SectionClock(java.util.List.of(
                new com.lowdragmc.photon.client.fx.timeline.TimelineSection("intro", 0),
                new com.lowdragmc.photon.client.fx.timeline.TimelineSection("loop", 10)
                        .repeat(com.lowdragmc.photon.client.fx.timeline.TimelineSection.FOREVER)), 20, null, true);
        clock.begin(null);
        if (!"intro".equals(clock.current())) {
            throw new IllegalStateException("The clock did not start in its first section");
        }
        // jump: from the next tick on, the loop section plays
        if (!received.apply(com.lowdragmc.photon.client.fx.timeline.SectionControl.of(clock))) {
            throw new IllegalStateException("A jump command was refused by a clock that has the section");
        }
        clock.applyPendingJump();
        if (!"loop".equals(clock.current())) {
            throw new IllegalStateException("The jump command did not move the clock into 'loop': "
                    + clock.current());
        }
        // a name the effect does not have changes nothing, and says so
        setCommandField(sent, "section", "does_not_exist");
        if (sent.apply(com.lowdragmc.photon.client.fx.timeline.SectionControl.of(clock))) {
            throw new IllegalStateException("A jump to a section that does not exist was reported as done");
        }
        Photon.LOGGER.info("[section-command-smoke] /photon fx section is registered and a jump command "
                + "moves a playing timeline into the named section");
    }

    /**
     * The child nodes of a command node. A node that has been built (what the tree hands back once a literal is
     * found) exposes {@code getChildren()}; a builder that has not been built yet exposes its arguments.
     */
    private static java.util.List<com.mojang.brigadier.tree.CommandNode<?>> children(Object node) {
        if (node instanceof com.mojang.brigadier.tree.CommandNode<?> built) {
            return java.util.List.copyOf(built.getChildren());
        }
        if (node instanceof com.mojang.brigadier.builder.ArgumentBuilder<?, ?> builder) {
            return java.util.List.copyOf(builder.getArguments());
        }
        return java.util.List.of();
    }

    /** The literal child {@code name} of a command node, or null. */
    private static Object literal(Object node, String name) {
        for (var child : children(node)) {
            if (child instanceof com.mojang.brigadier.tree.LiteralCommandNode<?> literal
                    && name.equals(literal.getLiteral())) {
                return literal;
            }
        }
        return null;
    }

    /** The argument child named {@code name} of a command node, or null (arguments are named, not literal). */
    private static Object argument(Object node, String name) {
        for (var child : children(node)) {
            if (child instanceof com.mojang.brigadier.tree.ArgumentCommandNode<?, ?> argument
                    && name.equals(argument.getName())) {
                return argument;
            }
        }
        return null;
    }

    /** Set a private field of a packet, the way the server command fills it in. */
    private static void setCommandField(Object packet, String name, Object value) {
        try {
            var field = packet.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(packet, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not set EffectSectionCommand." + name, e);
        }
    }

    /** Read a private field of a packet, the way the client handler reads it. */
    private static Object readCommandField(Object packet, String name) {
        try {
            var field = packet.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(packet);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read EffectSectionCommand." + name, e);
        }
    }

    /**
     * Timeline sections, ported from 26.3, checked where they decide something: which tick of the timeline plays next.
     * <p>
     * A section set to repeat 3 must play its own stretch three times and only then move on — not run into the next
     * section the first time it reaches the end. A section set to end must stop the clock instead of continuing, a
     * "goto" must jump. Sections that would be empty (sharing a start, or starting at the content end) are dropped
     * rather than making a zero-length pass. And a timeline without sections has to save exactly as before, because
     * every project made until now is one of those.
     */
    private static void verifySections() {
        // A (0..10) repeats 3 times, then B (10..20) plays: 30 ticks of A before B is reached
        var repeating = new com.lowdragmc.photon.client.fx.timeline.SectionClock(java.util.List.of(
                new com.lowdragmc.photon.client.fx.timeline.TimelineSection("A", 0).repeat(3),
                new com.lowdragmc.photon.client.fx.timeline.TimelineSection("B", 10)), 20, null, true);
        repeating.begin(null);
        for (int i = 0; i < 5; i++) {
            repeating.step(5);
        }
        // 25 ticks in: A has played twice and is on its third pass, still inside its own stretch (0..10)
        if (!"A".equals(repeating.current()) || repeating.pass() != 2
                || repeating.time() < 0 || repeating.time() > 10) {
            throw new IllegalStateException("A section set to repeat 3 did not stay inside its own stretch: now in '"
                    + repeating.current() + "', pass " + repeating.pass() + ", tick " + repeating.time());
        }
        repeating.step(5); // the 30th tick: the third pass ends and B starts exactly at its own start
        if (!"B".equals(repeating.current()) || Math.abs(repeating.time() - 10) > 1e-4) {
            throw new IllegalStateException("After three passes A did not hand over to B at tick 10: got '"
                    + repeating.current() + "' at " + repeating.time());
        }
        Photon.LOGGER.info("[sections-smoke] a section repeating 3 times plays 30 ticks and then starts the next one");

        // "then: end" stops the clock at the seam instead of running into the next section
        var ending = new com.lowdragmc.photon.client.fx.timeline.SectionClock(java.util.List.of(
                new com.lowdragmc.photon.client.fx.timeline.TimelineSection("A", 0)
                        .then(com.lowdragmc.photon.client.fx.timeline.TimelineSection.Then.END)), 20, null, true);
        ending.begin(null);
        ending.step(25);
        if (!ending.endPending() && !ending.ended()) {
            throw new IllegalStateException("\"then: end\" let the clock run past the end of its section to "
                    + ending.time());
        }

        // "goto" jumps to the named section instead of carrying on
        var jumping = new com.lowdragmc.photon.client.fx.timeline.SectionClock(java.util.List.of(
                new com.lowdragmc.photon.client.fx.timeline.TimelineSection("A", 0)
                        .then(com.lowdragmc.photon.client.fx.timeline.TimelineSection.Then.GOTO).target("C"),
                new com.lowdragmc.photon.client.fx.timeline.TimelineSection("B", 10),
                new com.lowdragmc.photon.client.fx.timeline.TimelineSection("C", 20)), 30, null, true);
        jumping.begin(null);
        jumping.step(15);
        if (!"C".equals(jumping.current())) {
            throw new IllegalStateException("\"goto C\" landed in '" + jumping.current() + "' instead");
        }

        // sections that would be empty are left out and reported, not silently played as zero-length passes
        var dropped = new com.lowdragmc.photon.client.fx.timeline.SectionClock(java.util.List.of(
                new com.lowdragmc.photon.client.fx.timeline.TimelineSection("A", 0),
                new com.lowdragmc.photon.client.fx.timeline.TimelineSection("twin", 0),
                new com.lowdragmc.photon.client.fx.timeline.TimelineSection("past-the-end", 40)), 20, null, true);
        if (dropped.names().size() != 1 || dropped.skipped().size() != 2) {
            throw new IllegalStateException("Empty sections were not left out: kept " + dropped.names()
                    + ", skipped " + dropped.skipped());
        }

        // a destroy walks into the release section and plays it once instead of repeating it forever
        var releasing = new com.lowdragmc.photon.client.fx.timeline.SectionClock(java.util.List.of(
                new com.lowdragmc.photon.client.fx.timeline.TimelineSection("A", 0).repeat(
                        com.lowdragmc.photon.client.fx.timeline.TimelineSection.FOREVER),
                new com.lowdragmc.photon.client.fx.timeline.TimelineSection("release", 10).repeat(
                        com.lowdragmc.photon.client.fx.timeline.TimelineSection.FOREVER)), 20, "release", true);
        releasing.begin(null);
        if (!releasing.beginWindDown()) {
            throw new IllegalStateException("Stopping an effect with a release section did nothing");
        }
        releasing.applyPendingJump();
        if (!"release".equals(releasing.current()) || !releasing.isReleased()) {
            throw new IllegalStateException("The release section was not entered on stop");
        }
        // once through it, a forever release section must not repeat: the clock runs past its end, which is how the
        // runtime knows the effect is finished and can let the remnants go
        releasing.step(15);
        if (releasing.time() <= 20) {
            throw new IllegalStateException("A forever release section kept looping while the effect was stopped: "
                    + "the clock is at " + releasing.time() + " instead of past the end");
        }
        Photon.LOGGER.info("[release-section-smoke] stopping enters the release section and it plays once, not forever");

        // saving: a timeline without sections writes neither key, so every existing project reads and writes as before
        var bare = new com.lowdragmc.photon.client.fx.timeline.Timeline();
        var bareTag = bare.serializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry());
        if (bareTag.contains("sections") || bareTag.contains("release")) {
            throw new IllegalStateException("A timeline without sections saved extra keys: " + bareTag.getAllKeys());
        }
        // and one with them round-trips, including the parts that are only written when they differ from the default
        var withSections = new com.lowdragmc.photon.client.fx.timeline.Timeline();
        withSections.sections().add(new com.lowdragmc.photon.client.fx.timeline.TimelineSection("intro", 0)
                .repeat(com.lowdragmc.photon.client.fx.timeline.TimelineSection.FOREVER)
                .then(com.lowdragmc.photon.client.fx.timeline.TimelineSection.Then.GOTO).target("loop"));
        withSections.sections().add(new com.lowdragmc.photon.client.fx.timeline.TimelineSection("loop", 10)
                .repeat(3));
        withSections.releaseSection("loop").releaseNow(false);
        var tag = withSections.serializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry());
        if (!tag.contains("sections") || !tag.contains("release")) {
            throw new IllegalStateException("A timeline with sections did not save them");
        }
        var reloaded = new com.lowdragmc.photon.client.fx.timeline.Timeline();
        reloaded.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(), tag);
        if (reloaded.sections().size() != 2
                || !"intro".equals(reloaded.sections().get(0).name())
                || reloaded.sections().get(0).repeat() != com.lowdragmc.photon.client.fx.timeline.TimelineSection.FOREVER
                || reloaded.sections().get(0).then()
                        != com.lowdragmc.photon.client.fx.timeline.TimelineSection.Then.GOTO
                || !"loop".equals(reloaded.sections().get(0).target())
                || Math.abs(reloaded.sections().get(1).start() - 10) > 1e-4
                || reloaded.sections().get(1).repeat() != 3
                || reloaded.sections().get(1).then()
                        != com.lowdragmc.photon.client.fx.timeline.TimelineSection.Then.LINEAR
                || !"loop".equals(reloaded.releaseSection())
                || reloaded.releaseNow()) {
            throw new IllegalStateException("Sections did not survive saving: " + reloaded.sections().size()
                    + " section(s), release '" + reloaded.releaseSection() + "', now=" + reloaded.releaseNow());
        }
        // The editor must not offer names that the runtime cannot distinguish in jump/release requests.
        if (com.lowdragmc.photon.gui.editor.view.FXTimelineView.validSectionName(reloaded, "intro")
                || com.lowdragmc.photon.gui.editor.view.FXTimelineView.validSectionName(reloaded, " ")
                || com.lowdragmc.photon.gui.editor.view.FXTimelineView.validSectionName(reloaded, " new ")
                || !com.lowdragmc.photon.gui.editor.view.FXTimelineView.validSectionName(reloaded, "outro")) {
            throw new IllegalStateException("The section editor accepted an empty/duplicate name or rejected a new one");
        }
        var lengthTrack = new com.lowdragmc.photon.client.fx.timeline.ControlTrack();
        lengthTrack.clips().add(new com.lowdragmc.photon.client.fx.timeline.Clip(0, 20, 1f));
        reloaded.tracks().add(lengthTrack);
        var moving = reloaded.sections().get(1);
        if (!com.lowdragmc.photon.gui.editor.view.FXTimelineView.validSectionStart(reloaded, moving, 10)
                || !com.lowdragmc.photon.gui.editor.view.FXTimelineView.validSectionStart(reloaded, moving, 12.5)
                || com.lowdragmc.photon.gui.editor.view.FXTimelineView.validSectionStart(reloaded, moving, 0)
                || com.lowdragmc.photon.gui.editor.view.FXTimelineView.validSectionStart(reloaded, moving, 20)
                || com.lowdragmc.photon.gui.editor.view.FXTimelineView.validSectionStart(reloaded, moving, -1)
                || com.lowdragmc.photon.gui.editor.view.FXTimelineView.validSectionStart(reloaded, moving, Double.NaN)) {
            throw new IllegalStateException("The section editor accepted an invalid or duplicate boundary");
        }
        if (!com.lowdragmc.photon.gui.editor.view.FXTimelineView.dragSectionTo(reloaded, moving, 12.6)
                || moving.start() != 13
                || com.lowdragmc.photon.gui.editor.view.FXTimelineView.dragSectionTo(reloaded, moving, 0)
                || com.lowdragmc.photon.gui.editor.view.FXTimelineView.dragSectionTo(reloaded, moving, 20)
                || com.lowdragmc.photon.gui.editor.view.FXTimelineView.dragSectionTo(reloaded, moving, Double.NaN)
                || moving.start() != 13) {
            throw new IllegalStateException("Dragging a section did not snap to ticks or rejected boundaries moved it");
        }
        // a project written before sections existed has no key at all and must load with none
        var legacy = tag.copy();
        legacy.remove("sections");
        legacy.remove("release");
        var old = new com.lowdragmc.photon.client.fx.timeline.Timeline();
        old.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(), legacy);
        if (!old.sections().isEmpty() || old.releaseSection() != null || !old.releaseNow()) {
            throw new IllegalStateException("An older project did not load as one implicit section");
        }
        Photon.LOGGER.info("[sections-save-smoke] sections and the release section survive saving, "
                + "and an older project still loads as a single stretch");
    }

    /**
     * Photon 26.2.2.11: stopping without force must not treat remnants as discarded (that is what made lights go
     * out while particles were still fading) and an FX instance must wind down what it placed, not vanish.
     */
    private static void verifyDestroyKeepsRemnants() {
        var runtime = new com.lowdragmc.photon.client.fx.FX().createRuntime();
        if (runtime.isValid() || runtime.isTracked()) {
            throw new IllegalStateException("A runtime is live before it is emitted");
        }
        runtime.destroy(false);
        if (runtime.isValid()) {
            throw new IllegalStateException("Stopping an effect left it valid");
        }
        if (runtime.isTracked()) {
            throw new IllegalStateException("A runtime that never played is still tracked");
        }
        var instance = new com.lowdragmc.photon.client.gameobject.FXInstanceObject();
        instance.windDown();
        if (!instance.isWindingDown()) {
            throw new IllegalStateException("Winding down an FX instance did nothing");
        }
        if (instance.sections() != null) {
            throw new IllegalStateException("An instance outside a runtime has a section control");
        }
        Photon.LOGGER.info("[destroy-remnant-smoke] destroy(false) invalidates the runtime; an FX instance winds down");
    }

    /**
     * Drain keeps an object ticking while {@code hasVisibleRemnants()} is true. Particle emitters already
     * reported leftover particles; beams, trails and lights did not, so a Drain clip cut them off.
     */
    private static void verifyVisibleRemnants() {
        var beam = new com.lowdragmc.photon.client.gameobject.emitter.beam.BeamEmitter();
        beam.reset();
        if (!beam.hasVisibleRemnants()) {
            throw new IllegalStateException("A live beam reported no remnants; Drain would cut it off");
        }
        var trail = new TrailEmitter();
        trail.reset();
        if (!trail.hasVisibleRemnants()) {
            throw new IllegalStateException("A live trail reported no remnants; Drain would cut it off");
        }
        var ara = new com.lowdragmc.photon.client.gameobject.emitter.aratrail.AraTrailEmitter();
        ara.reset();
        if (!ara.hasVisibleRemnants()) {
            throw new IllegalStateException("A live AraTrail reported no remnants; Drain would cut it off");
        }
        var light = new com.lowdragmc.photon.client.gameobject.light.LightObject();
        if (!light.hasVisibleRemnants()) {
            throw new IllegalStateException("A running light reported no remnants; Drain would cut it off");
        }
        var fog = new com.lowdragmc.photon.client.gameobject.light.FogVolumeObject();
        if (!fog.hasVisibleRemnants()) {
            throw new IllegalStateException("A running fog volume reported no remnants; Drain would cut it off");
        }
        light.remove(true);
        fog.remove(true);
        if (light.hasVisibleRemnants() || fog.hasVisibleRemnants()) {
            throw new IllegalStateException("A force-removed light or fog still reported remnants");
        }
        Photon.LOGGER.info("[visible-remnants-smoke] beam, trail, AraTrail, light and fog report remnants while live");
    }

    /**
     * LDLib2 2.2.46.a persistedKey/isPersistedFlat, used to mark FX instance inspector rows.
     */
    private static void verifyInspectorPersistedKeys() {
        try {
            var name = com.lowdragmc.photon.client.gameobject.FXObject.class.getDeclaredField("name");
            if (com.lowdragmc.photon.gui.editor.PhotonConfigurators.persistedKey(name) == null) {
                throw new IllegalStateException("FXObject.name has no persisted key; inspector marks cannot find it");
            }
            var transform = com.lowdragmc.photon.client.gameobject.FXObject.class.getDeclaredField("transform");
            if (!com.lowdragmc.photon.gui.editor.PhotonConfigurators.isPersistedFlat(transform)
                    && com.lowdragmc.photon.gui.editor.PhotonConfigurators.persistedKey(transform) == null) {
                throw new IllegalStateException("FXObject.transform is neither flat nor keyed");
            }
            var row = new com.lowdragmc.lowdraglib2.configurator.ui.Configurator("row");
            if (!(row instanceof com.lowdragmc.photon.gui.editor.PhotonConfiguratorExt)) {
                throw new IllegalStateException("Configurator mixin did not apply; inspector cannot mark overridden rows");
            }
            com.lowdragmc.photon.gui.editor.PhotonConfigurators.setOverridden(row, true);
            com.lowdragmc.photon.gui.editor.PhotonConfigurators.setOverridden(row, false);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("FXObject fields for inspector marks are missing", e);
        }
        Photon.LOGGER.info("[inspector-marks-smoke] persisted keys and the 2.2.46.a configurator mixin are in place");
    }

    /**
     * LDLib2 2.2.46.a disabledLeaf: an unavailable action stays in the menu, marked so a click does nothing.
     */
    private static void verifyDisabledMenuLeaves() {
        var menu = com.lowdragmc.lowdraglib2.gui.util.TreeBuilder.Menu.start();
        com.lowdragmc.photon.gui.editor.PhotonMenus.disabledLeaf(menu, "photon.fx_instance.open_source");
        menu.leaf("photon.fx_instance.replace", () -> {
        });
        boolean disabled = false;
        boolean enabled = false;
        for (var leaf : menu.build().getChildren()) {
            if (leaf.getContent() == com.lowdragmc.photon.gui.editor.PhotonMenus.DISABLED) {
                disabled = true;
            } else if (leaf.getContent() instanceof Runnable) {
                enabled = true;
            }
        }
        if (!disabled || !enabled) {
            throw new IllegalStateException("disabledLeaf did not mark only the unavailable entry; disabled="
                    + disabled + " enabled=" + enabled);
        }
        Photon.LOGGER.info("[disabled-leaf-smoke] unavailable menu actions stay visible and cannot be picked");
    }

    /**
     * LDLib2 2.2.46.a DialogSizeStore: the FXPack contents window keeps the size it was last resized to.
     */
    private static void verifyDialogSizeMemory() {
        var key = "photon.smoke.dialog_size";
        com.lowdragmc.photon.gui.editor.PhotonDialogSizes.put(key, 420, 310);
        var got = com.lowdragmc.photon.gui.editor.PhotonDialogSizes.get(key);
        if (got == null || Math.abs(got.x - 420) > 0.01f || Math.abs(got.y - 310) > 0.01f) {
            throw new IllegalStateException("dialog size store roundtrip failed: " + got);
        }
        Photon.LOGGER.info("[fxpack-dialog-size-smoke] pack contents dialog size is remembered ({}x{})", got.x, got.y);
    }

    /**
     * Photon 1 attached entity FX at the feet. Converted effects must keep that or an aura sits on
     * the head instead of the torso.
     */
    private static void verifyPhoton1EntityFeetAnchor() {
        var mapped = new net.minecraft.nbt.CompoundTag();
        var emitters = new net.minecraft.nbt.ListTag();
        mapped.put("emitters", emitters);
        var photon2 = com.lowdragmc.photon.client.fx.compat.FXCompat.mapEffect(mapped);
        if (!photon2.getCompound("fxData").getBoolean("entityFollowsFeet")) {
            throw new IllegalStateException("Photon 1 conversion did not stamp entityFollowsFeet");
        }
        var modern = new net.minecraft.nbt.CompoundTag();
        modern.putBoolean("bloomEnabled", true);
        modern.putFloat("bloomIntensity", 0.18f);
        var authored = new com.lowdragmc.photon.client.fx.FXData();
        authored.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(), modern);
        if (authored.entityFollowsFeet()) {
            throw new IllegalStateException("A Photon 2 file followed the feet; it should follow the eyes");
        }
        Photon.LOGGER.info("[photon1-feet-smoke] converted FX follow the feet; Photon 2 FX follow the eyes");
        verifyPhoton1BloomPort();
    }

    /**
     * Photon 1 {@code renderer.bloomEffect} emitters always bloomed. Conversion used to keep only
     * full-bright lighting, so ported FX lost their halo.
     */
    private static void verifyPhoton1BloomPort() {
        var bloomEmitter = new CompoundTag();
        var renderer = new CompoundTag();
        renderer.putBoolean("bloomEffect", true);
        var config = new CompoundTag();
        config.put("renderer", renderer);
        bloomEmitter.put("config", config);
        var withBloom = new CompoundTag();
        var bloomList = new ListTag();
        bloomList.add(bloomEmitter);
        withBloom.put("emitters", bloomList);
        var converted = com.lowdragmc.photon.client.fx.compat.FXCompat.mapEffect(withBloom).getCompound("fxData");
        if (!converted.getBoolean("bloomEnabled")
                || Math.abs(converted.getFloat("bloomIntensity")
                - com.lowdragmc.photon.client.fx.FXData.PHOTON1_BLOOM_INTENSITY) > 0.0001f) {
            throw new IllegalStateException("Photon 1 bloomEffect did not stamp FX bloom: " + converted);
        }
        var plain = new CompoundTag();
        plain.put("emitters", new ListTag());
        var without = com.lowdragmc.photon.client.fx.compat.FXCompat.mapEffect(plain).getCompound("fxData");
        if (without.contains("bloomIntensity") || without.contains("bloomEnabled")) {
            throw new IllegalStateException("A Photon 1 FX without bloomEffect stamped bloom keys");
        }
        var leftover = new CompoundTag();
        var objects = new ListTag();
        var object = new CompoundTag();
        var data = new CompoundTag();
        var leftoverConfig = new CompoundTag();
        var lights = new CompoundTag();
        lights.putBoolean("legacyBloomFullBright", true);
        leftoverConfig.put("lights", lights);
        data.put("config", leftoverConfig);
        object.put("data", data);
        objects.add(object);
        leftover.put("fxObjects", objects);
        var recovered = new com.lowdragmc.photon.client.fx.FXData();
        recovered.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(), leftover);
        if (!recovered.bloomEnabled() || Math.abs(recovered.bloomIntensity()
                - com.lowdragmc.photon.client.fx.FXData.PHOTON1_BLOOM_INTENSITY) > 0.0001f) {
            throw new IllegalStateException("Already-converted Photon 1 bloom did not restore intensity: "
                    + recovered.bloomEnabled() + " " + recovered.bloomIntensity());
        }
        leftover.putBoolean("bloomEnabled", true);
        leftover.putFloat("bloomIntensity", com.lowdragmc.photon.client.fx.FXData.DEFAULT_BLOOM_INTENSITY);
        var savedDefault = new com.lowdragmc.photon.client.fx.FXData();
        savedDefault.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(), leftover);
        if (Math.abs(savedDefault.bloomIntensity()
                - com.lowdragmc.photon.client.fx.FXData.PHOTON1_BLOOM_INTENSITY) > 0.0001f) {
            throw new IllegalStateException("A converted Photon 1 FX saved at the default intensity kept "
                    + savedDefault.bloomIntensity() + " instead of the Photon 1 halo");
        }
        leftover.putFloat("bloomIntensity", 1.25f);
        var authored = new com.lowdragmc.photon.client.fx.FXData();
        authored.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(), leftover);
        if (Math.abs(authored.bloomIntensity() - 1.25f) > 0.0001f) {
            throw new IllegalStateException("An authored bloom intensity was overwritten: " + authored.bloomIntensity());
        }
        Photon.LOGGER.info("[photon1-bloom-smoke] converted FX keep Photon 1 bloom at intensity {}",
                com.lowdragmc.photon.client.fx.FXData.PHOTON1_BLOOM_INTENSITY);
    }

    /**
     * The texture picker lists textures already loaded (Photon, packs, other mods), not only files
     * under the workspace folder.
     */
    private static void verifyLoadedTextureCatalogue() {
        var loaded = com.lowdragmc.photon.client.util.PhotonTextureCatalogue.listLoaded();
        boolean hasParticle = loaded.stream().anyMatch(id -> id.getNamespace().equals("photon")
                && id.getPath().contains("particle"));
        if (!hasParticle) {
            throw new IllegalStateException("Loaded texture catalogue missed Photon's particle textures: " + loaded.size());
        }
        Photon.LOGGER.info("[loaded-texture-catalogue-smoke] editor can pick {} loaded textures including Photon's", loaded.size());
    }

    /**
     * Photon 26.3: pose/color reads write into a caller vector so the particle bake does not allocate per particle.
     * The no-arg getters still return a fresh vector, so anything that stored the old result is unchanged.
     */
    private static void verifyParticleDestReuse() {
        var emitter = new com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter();
        var dest = new org.joml.Vector4f(9, 8, 7, 6);
        var out = emitter.getRGBAColor(dest);
        var fresh = emitter.getRGBAColor();
        if (out != dest || dest.x != fresh.x || dest.y != fresh.y || dest.z != fresh.z || dest.w != fresh.w) {
            throw new IllegalStateException("Emitter color did not write into the caller vector: " + dest + " vs " + fresh);
        }
        if (fresh == dest) {
            throw new IllegalStateException("The no-arg color getter reused the caller vector");
        }
        Photon.LOGGER.info("[particle-dest-smoke] emitter color writes into the caller vector ({})", dest);
    }

    /**
     * Photon 2.2.10: a popped-out editor draws into its own OS window. ScreenSize must follow that
     * surface, not the game window, or scene color/depth sampling reads the wrong texel.
     */
    private static void verifyPoppedOutScreenSize() {
        var surface = com.lowdragmc.lowdraglib2.gui.ui.rendering.UISurface.current();
        if (surface == null) {
            throw new IllegalStateException("UISurface.current() is null; ScreenSize cannot follow a popped-out editor");
        }
        int width = surface.framebufferWidth();
        int height = surface.framebufferHeight();
        if (width <= 0 || height <= 0) {
            throw new IllegalStateException("UISurface framebuffer is " + width + "x" + height);
        }
        Photon.LOGGER.info("[popped-out-screen-size-smoke] UISurface {}x{} mainWindow={}",
                width, height, surface.isMainWindow());
    }

    /**
     * The two lifetime modes ported from 26.3, checked where they actually decide something: {@code T} per tick.
     * <p>
     * {@code Once} must behave exactly as before (0 → 1, then it holds 1) so nothing that already worked changes.
     * {@code Loop} must return to 0 at every lifetime instead of clamping — that is the whole point of the mode.
     * {@code PingPong} must come back down, which is what a ping-pong particle draws differently from a looping one.
     * The clamp is what the old code did, so a regression here would silently make the setting a no-op.
     */
    private static void verifyCyclicLifetimes() {
        var once = com.lowdragmc.photon.client.gameobject.particle.ParticleLifetime.Mode.Once;
        var loop = com.lowdragmc.photon.client.gameobject.particle.ParticleLifetime.Mode.Loop;
        var pingPong = com.lowdragmc.photon.client.gameobject.particle.ParticleLifetime.Mode.PingPong;
        var lifetime = 20;
        // once: monotonic, saturates at 1 and stays there
        if (Math.abs(com.lowdragmc.photon.client.gameobject.particle.ParticleLifetime.t(once, 10, lifetime) - 0.5f) > 1e-4
                || Math.abs(com.lowdragmc.photon.client.gameobject.particle.ParticleLifetime.t(once, 40, lifetime) - 1f) > 1e-4
                || once.cyclic()) {
            throw new IllegalStateException("Once no longer runs 0 to 1 and holds at 1");
        }
        // loop: one full lifetime later it is back at the start, not past the end
        var loopHalf = com.lowdragmc.photon.client.gameobject.particle.ParticleLifetime.t(loop, 10, lifetime);
        var loopWrapped = com.lowdragmc.photon.client.gameobject.particle.ParticleLifetime.t(loop, 10 + lifetime, lifetime);
        if (Math.abs(loopHalf - 0.5f) > 1e-4 || Math.abs(loopWrapped - 0.5f) > 1e-4 || !loop.cyclic()) {
            throw new IllegalStateException("Loop did not wrap back to the same phase: " + loopHalf + " then "
                    + loopWrapped);
        }
        // ping-pong: out during the first cycle, back down during the second, out again on the third
        var out = com.lowdragmc.photon.client.gameobject.particle.ParticleLifetime.t(pingPong, 5, lifetime);
        var back = com.lowdragmc.photon.client.gameobject.particle.ParticleLifetime.t(pingPong, 25, lifetime);
        var returned = com.lowdragmc.photon.client.gameobject.particle.ParticleLifetime.t(pingPong, 45, lifetime);
        if (Math.abs(out - 0.25f) > 1e-4 || Math.abs(back - 0.75f) > 1e-4 || Math.abs(returned - 0.25f) > 1e-4) {
            throw new IllegalStateException("PingPong did not go out and back: " + out + ", " + back + ", " + returned);
        }
        // a cyclic particle only dies because its emitter stopped: it finishes the cycle it is in
        var endAge = com.lowdragmc.photon.client.gameobject.particle.ParticleLifetime.endOfCycle(25, lifetime);
        if (Math.abs(endAge - 40f) > 1e-4
                || Math.abs(com.lowdragmc.photon.client.gameobject.particle.ParticleLifetime.finalT(pingPong, endAge, lifetime)) > 1e-4
                || Math.abs(com.lowdragmc.photon.client.gameobject.particle.ParticleLifetime.t(loop, 41, lifetime, endAge) - 1f) > 1e-4) {
            throw new IllegalStateException("A cyclic particle did not finish and hold its last cycle value");
        }
        Photon.LOGGER.info("[lifetime-mode-smoke] Once holds at 1, Loop wraps to the same phase and PingPong returns "
                + "({} -> {} -> {})", out, back, returned);

        // the exit mode survives a save/load round trip and an old file (no "exit") still reads as Cut
        var track = new com.lowdragmc.photon.client.fx.timeline.ControlTrack();
        track.clips().add(new com.lowdragmc.photon.client.fx.timeline.Clip(0.0, 20.0, 1f)
                .exitMode(com.lowdragmc.photon.client.fx.timeline.Clip.ExitMode.DRAIN));
        var saved = track.writeData(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry());
        var reread = new com.lowdragmc.photon.client.fx.timeline.ControlTrack();
        reread.readData(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(), saved);
        if (reread.clips().size() != 1
                || reread.clips().get(0).exitMode() != com.lowdragmc.photon.client.fx.timeline.Clip.ExitMode.DRAIN) {
            throw new IllegalStateException("A draining clip did not survive saving");
        }
        // and a project written before the setting existed still reads as Cut, which is what it always did
        var legacy = saved.copy();
        for (var entry : legacy.getList("clips", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            if (entry instanceof net.minecraft.nbt.CompoundTag tag) tag.remove("exit");
        }
        var old = new com.lowdragmc.photon.client.fx.timeline.ControlTrack();
        old.readData(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(), legacy);
        if (old.clips().isEmpty()
                || old.clips().get(0).exitMode() != com.lowdragmc.photon.client.fx.timeline.Clip.ExitMode.CUT) {
            throw new IllegalStateException("A project saved before the setting existed did not read as Cut");
        }
        Photon.LOGGER.info("[drain-exit-smoke] a draining clip survives saving and an older project still reads as Cut");
    }

    /**
     * What Alex kept hitting: an effect names {@code cursedfate:textures/...} but no pack serves it, because the folder
     * was dropped somewhere that was never registered, and the particles draw the purple checkerboard. The lookup must
     * find the file in an assets tree on disk and copy it into the workspace so the id resolves from then on.
     */
    private static void verifyLooseTextureLookup() {
        var assets = com.lowdragmc.photon.client.util.PhotonPaths.getAssetsDir().toPath();
        var id = new ResourceLocation("cursedfate_probe", "textures/particle/loose_probe.png");
        // a file outside any mounted pack: "the author's folder", in the same shape the game reads
        var looseRoot = assets.resolve("loose_probe_root");
        var looseFile = looseRoot.resolve("assets").resolve(id.getNamespace()).resolve(id.getPath());
        var adopted = assets.resolve(id.getNamespace()).resolve(id.getPath());
        try {
            var badData = new CompoundTag();
            var badObjects = new ListTag();
            var badObject = new CompoundTag();
            badObject.putString("type", "diagnostic_missing_type");
            badObject.put("data", new CompoundTag());
            badObjects.add(badObject);
            badData.put("fxObjects", badObjects);
            new com.lowdragmc.photon.client.fx.FXData().deserializeNBT(
                    com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(), badData);
            var diagnosticKey = "failure-unknown-object-decode-diagnostic_missing_type-";
            var diagnosticFile = com.lowdragmc.lowdraglib2.Platform.getGamePath().resolve("photon/diagnostics")
                    .resolve("fx-" + Integer.toUnsignedString(diagnosticKey.hashCode(), 16) + ".txt");
            if (!Files.isRegularFile(diagnosticFile) || !Files.readString(diagnosticFile).contains("diagnostic_missing_type")) {
                throw new IllegalStateException("A rejected FX object did not produce a diagnostic dump");
            }
            Files.delete(diagnosticFile);
            // An incomplete workspace namespace must not mask the same namespace in another tree.
            Files.createDirectories(assets.resolve(id.getNamespace()));
            Files.createDirectories(looseFile.getParent());
            Files.write(looseFile, java.util.Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4r2DwHwAFwAJPDlQtOgAAAABJRU5ErkJggg=="));
            com.lowdragmc.photon.client.fx.LooseTextures.invalidate();

            var folder = com.lowdragmc.photon.client.fx.LooseTextures.namespaceFolder(id.getNamespace());
            if (folder == null || !looseFile.equals(com.lowdragmc.photon.client.fx.LooseTextures.find(id))) {
                throw new IllegalStateException("The loose texture lookup did not find " + id + " in " + looseRoot
                        + "; indexed namespace folder was " + folder);
            }
            var resolved = com.lowdragmc.photon.client.fx.LooseTextures.resolve(id);
            if (!resolved.equals(id) || !Files.isRegularFile(adopted)) {
                throw new IllegalStateException("Resolving " + id + " did not adopt the loose file into "
                        + adopted + " (got " + resolved + ")");
            }
            // No resource reload has occurred: rendering must upload the PNG directly rather than cache
            // Minecraft's missing texture for a namespace the pack manager has never seen.
            var manager = Minecraft.getInstance().getTextureManager();
            // Photon 1 custom shader samplers used to bypass TextureMaterial's loose-file path.
            var custom = new CustomShaderMaterial(new ResourceLocation("photon", "ci/tornado_smoke"));
            var shaderData = new CompoundTag();
            var samplers = new CompoundTag();
            var sampler = new CompoundTag();
            sampler.putString("type", "texture");
            sampler.putString("resource", id.toString());
            samplers.put("Sampler0", sampler);
            shaderData.put("samplers", samplers);
            var materialData = new CompoundTag();
            materialData.put("shaderData", shaderData);
            custom.deserializeAdditionalNBT(materialData, com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry());
            custom.getShader(MaterialContext.NORMAL);
            if (custom.isCompiledError()
                    || !(manager.getTexture(id, null) instanceof net.minecraft.client.renderer.texture.DynamicTexture)) {
                throw new IllegalStateException("A custom shader sampler could not use the loose texture: " + id);
            }
            if (!com.lowdragmc.photon.client.fx.LooseTextures.resolveForRender(id).equals(id)
                    || !(manager.getTexture(id, null) instanceof net.minecraft.client.renderer.texture.DynamicTexture)) {
                throw new IllegalStateException("The copied texture was not usable before a resource reload: " + id);
            }
            var uploaded = manager.getTexture(id, null);
            com.lowdragmc.photon.client.fx.LooseTextures.resolveForRender(id);
            if (manager.getTexture(id, null) != uploaded) {
                throw new IllegalStateException("The loose texture was uploaded again on every frame: " + id);
            }
            Photon.LOGGER.info("[loose-texture-smoke] found {} in a folder no pack mounts and copied it to the workspace",
                    id);

            // the other half of Alex's report: the id names one namespace, the artist's files sit under another (or
            // under no assets folder at all). The lookup is path-agnostic, so the name alone has to be enough.
            var strayId = new ResourceLocation("cursedfate_stray", "textures/particle/stray_probe.png");
            var strayFile = looseRoot.resolve("delivered").resolve("stray_probe.png");
            var strayAdopted = assets.resolve(strayId.getNamespace()).resolve(strayId.getPath());
            try {
                Files.createDirectories(strayFile.getParent());
                Files.write(strayFile, new byte[]{(byte) 0x89, 'P', 'N', 'G'});
                com.lowdragmc.photon.client.fx.LooseTextures.invalidate();
                if (!com.lowdragmc.photon.client.fx.LooseTextures.resolve(strayId).equals(strayId)
                        || !Files.isRegularFile(strayAdopted)) {
                    throw new IllegalStateException("A texture whose files sit under another namespace was not found: "
                            + strayId);
                }
                Photon.LOGGER.info("[loose-texture-smoke] found {} outside any assets folder", strayId);
            } finally {
                Files.deleteIfExists(strayAdopted);
                Files.deleteIfExists(strayFile);
                Files.deleteIfExists(strayFile.getParent());
                com.lowdragmc.photon.client.fx.LooseTextures.invalidate();
            }

            // the converter's destination is what the picker reads: a flat assets/photon/fx, never fx_old/ inside it
            var destination = com.lowdragmc.photon.client.fx.compat.FXCompat.destination();
            if (!destination.equals(assets.resolve("photon/fx"))) {
                throw new IllegalStateException("Converted effects do not land in assets/photon/fx: " + destination);
            }
            var source = Files.createTempDirectory("photon1-flat-conversion");
            var nested = source.resolve("fx_old");
            Files.createDirectories(nested);
            var legacy = new CompoundTag();
            legacy.put("emitters", new ListTag());
            try (var output = Files.newOutputStream(nested.resolve("matrix.fx"))) {
                NbtIo.writeCompressed(legacy, output);
            }
            var tempDestination = Files.createTempDirectory("photon2-converted");
            var report = com.lowdragmc.photon.client.fx.compat.FXCompat.convertFX(nested, tempDestination);
            if (report.converted() != 1 || !Files.isRegularFile(tempDestination.resolve("matrix.fx"))
                    || Files.exists(tempDestination.resolve("fx_old"))) {
                throw new IllegalStateException("Conversion kept the legacy folder inside the id: "
                        + report.lines());
            }
            Photon.LOGGER.info("[loose-texture-smoke] conversion flattens fx_old into the effect folder");
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Loose texture lookup fixture failed", failure);
        } finally {
            Minecraft.getInstance().getTextureManager().release(id);
            try {
                Files.deleteIfExists(adopted);
                Files.deleteIfExists(looseFile);
                Files.deleteIfExists(looseFile.getParent());
                Files.deleteIfExists(looseRoot.resolve("assets").resolve(id.getNamespace()));
                Files.deleteIfExists(looseRoot.resolve("assets"));
                Files.deleteIfExists(looseRoot);
                com.lowdragmc.photon.client.fx.LooseTextures.invalidate();
            } catch (java.io.IOException ignored) {
                // leftovers in a temp tree are harmless
            }
        }
    }

    /**
     * The bloom knob's real complaint: it could be clicked but not slid. A press is dispatched on the slider, then
     * a move, then another move further along, then the release — the value must follow every one of them, and
     * the press alone (what a click was) must already have set it. This drives the widget through the same event
     * path the screen uses, so a slider that only starts a drag on its handle cannot pass.
     */
    private static void verifySliderDragsContinuously() {
        IllegalStateException last = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                verifySliderDragsContinuouslyOnce();
                return;
            } catch (IllegalStateException failure) {
                last = failure;
                Photon.LOGGER.warn("[slider-drag-smoke] attempt {} failed: {}", attempt + 1, failure.getMessage());
            }
        }
        throw last;
    }

    private static void verifySliderDragsContinuouslyOnce() {
        var control = new com.lowdragmc.photon.gui.editor.view.scene.SceneBloomIntensityControl();
        var slider = control.slider;
        var min = com.lowdragmc.photon.gui.editor.view.scene.SceneBloomIntensityControl.MIN;
        var max = com.lowdragmc.photon.gui.editor.view.scene.SceneBloomIntensityControl.MAX;
        // a 100px track: the control is never mounted here, so the width it would be laid out with is supplied
        slider.setUnmountedTrackWidth(100f);
        var x0 = slider.getContentX();
        var width = 100f;
        // a press at the left end is an instant set, not a "grab the handle" that needs a second press
        dispatchSlider(slider, com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.MOUSE_DOWN, x0 + 0.001f);
        var atPress = control.current();
        if (Math.abs(atPress - min) > 0.02) {
            throw new IllegalStateException("A press on the track did not set the value to the minimum: fx="
                    + atPress + " widget=" + slider.getValue() + ", dragging=" + slider.isDragging());
        }
        if (!slider.isDragging()) {
            throw new IllegalStateException("A press on the track did not start a drag");
        }
        // then the drag: each move must be written, monotonically, without releasing and pressing again
        var seen = new ArrayList<Double>();
        var widget = new ArrayList<Double>();
        var discrepancies = new ArrayList<String>();
        for (var fraction : new float[]{0.25f, 0.5f, 0.75f, 1f}) {
            dispatchSlider(slider, com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.MOUSE_MOVE, x0 + width * fraction);
            widget.add((double) slider.getValue());
            seen.add((double) control.current());
            if (Math.abs(seen.get(seen.size() - 1) - slider.getValue()) > 0.02) {
                discrepancies.add("widget=" + slider.getValue() + ", fx=" + seen.get(seen.size() - 1));
            }
        }
        for (var i = 1; i < seen.size(); i++) {
            if (!discrepancies.isEmpty() || seen.get(i) <= seen.get(i - 1) || widget.get(i) <= widget.get(i - 1)) {
                throw new IllegalStateException("The slider did not keep up with the drag: fx=" + seen
                        + " widget=" + widget + " (cursor x0=" + x0 + ", track=" + width
                        + ", discrepancies=" + discrepancies + ")");
            }
        }
        if (Math.abs(control.slider.getValue() - max) > 0.02) {
            throw new IllegalStateException("The end of the drag did not reach the maximum: "
                    + control.slider.getValue() + " vs " + max);
        }
        Photon.LOGGER.info("[slider-drag-smoke] pressing and dragging the bloom slider moved it continuously: {}",
                seen);
        dispatchSlider(slider, com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.MOUSE_UP, x0 + width);
        if (slider.isDragging()) {
            throw new IllegalStateException("Releasing the mouse did not end the drag");
        }
    }

    /** Whether the config file is, right now, in the middle of being rewritten or reloaded. */
    private static String describeConfigFile() {
        try {
            var path = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("photon-client.toml");
            if (!java.nio.file.Files.isRegularFile(path)) return "no config file at " + path;
            var text = java.nio.file.Files.readString(path);
            var line = text.lines().filter(l -> l.startsWith("bloom_intensity"))
                    .findFirst().orElse("<bloom_intensity missing>");
            return "file " + path.getFileName() + " (" + text.length() + " chars) says " + line
                    + ", watcher threads=" + Thread.getAllStackTraces().keySet().stream()
                    .map(Thread::getName)
                    .filter(name -> name.toLowerCase(java.util.Locale.ROOT).contains("watch"))
                    .toList();
        } catch (Exception e) {
            return "config file unreadable: " + e;
        }
    }

    /** Sends one pointer event at screen x to this element, the way the UI's own dispatch does. */
    private static void dispatchSlider(com.lowdragmc.lowdraglib2.gui.ui.UIElement element, String type, float x) {
        var event = com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent.create(type);
        event.x = x;
        event.y = element.getContentY() + element.getContentHeight() / 2f;
        event.button = 0;
        event.target = element;
        event.currentElement = element;
        for (var listener : element.getBubbleListeners(type)) {
            event.currentListener = listener;
            listener.handleEvent(event);
        }
    }

    public static void prepareModelFixtureIfEnabled() {
        if (!Boolean.getBoolean("photon.fxprojSmokeTest")) return;
        try {
            Path photon2Asset = LDLib2.getAssetsDir().toPath().resolve("photon2_probe/textures/marker.txt");
            Files.createDirectories(photon2Asset.getParent());
            Files.writeString(photon2Asset, "photon2-editor-assets-mounted");
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
            // The direct resource-pack file deliberately conflicts with the nested asset.
            Files.writeString(ldlibModelDir.resolve("obj").resolve("ci_nested_obj.obj"),
                    "v 7 0 0\nv 8 0 0\nv 7 1 0\nvt 0 0\nvt 1 0\nvt 0 1\nvn 0 0 1\nf 1/1/1 2/2/1 3/3/1\n");
            Path nestedModelDir = LDLib2.getAssetsDir().toPath().resolve("ldlib2/assets/ldlib2/models");
            Files.createDirectories(nestedModelDir.resolve("block"));
            Files.createDirectories(nestedModelDir.resolve("obj"));
            Files.writeString(nestedModelDir.resolve("block/ci_nested_obj.json"),
                    "{\"loader\":\"neoforge:obj\",\"model\":\"ldlib2:models/obj/ci_nested_obj.obj\",\"textures\":{},\"flip_v\":true}");
            Files.writeString(nestedModelDir.resolve("obj/ci_nested_obj.obj"),
                    "v 0 0 0\nv 1 0 0\nv 0 1 0\nvt 0 0\nvt 1 0\nvt 0 1\nvn 0 0 1\nf 1/1/1 2/2/1 3/3/1\n");
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
#ifdef PARTICLE_MODEL_INSTANCE
                    uniform float LegacyVariantOnly;
#endif
                    out float particleT;
                    out vec2 tornadoUv;
                    out vec4 tornadoColor;
                    void main() {
                        ParticleData data = getParticleData();
#ifdef PARTICLE_MODEL_INSTANCE
                        data.UV = aUV;
                        data.Color = vec4(iColor.rgb * aBrightness, iColor.a);
                        data.UV += 0.001 * (aPos.xy + iPos.xy);
#endif
                        particleT = T;
                        tornadoUv = data.UV;
                        tornadoColor = data.Color;
                        gl_Position = ProjMat * ModelViewMat * vec4(data.Position, 1.0);
#ifdef PARTICLE_MODEL_INSTANCE
                        gl_Position.x += (LegacyVariantOnly + float(PhotonInstanceBase)) * 0.01;
#endif
                    }
                    """);
            Files.writeString(shaderDir.resolve("tornado_smoke.fsh"), """
                    #version 330 core
                    #moj_import <photon:particle_utils.glsl>
                    uniform sampler2D SamplerCurve;
                    uniform sampler2D SamplerSceneColor;
                    uniform sampler2D SamplerSceneDepth;
                    uniform vec2 ScreenSize;
                    in float particleT;
                    in vec2 tornadoUv;
                    in vec4 tornadoColor;
                    out vec4 fragColor;
                    void main() {
                        float value = getCurveValue(SamplerCurve, 0, clamp(particleT, 0.0, 1.0));
                        float uvPattern = 0.75 + 0.25 * fract(tornadoUv.x + tornadoUv.y);
                        float viewportAspect = ScreenSize.x / max(ScreenSize.y, 1.0);
                        vec3 curveColor = vec3(0.35 + value * 0.65, 0.1, 1.0 - value * 0.8);
                        vec3 backdrop = texture(SamplerSceneColor, gl_FragCoord.xy / ScreenSize).rgb;
                        float sceneDepth = texture(SamplerSceneDepth, gl_FragCoord.xy / ScreenSize).r;
                        curveColor += backdrop * 0.2;
                        curveColor.b *= 0.8 + 0.2 * sceneDepth;
                        curveColor.r *= clamp(viewportAspect, 0.1, 1.0);
                        fragColor = vec4(curveColor * tornadoColor.rgb * uvPattern, tornadoColor.a);
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
                var sceneSampler = new com.google.gson.JsonObject();
                sceneSampler.addProperty("name", "SamplerSceneColor");
                samplers.add(sceneSampler);
                var depthSampler = new com.google.gson.JsonObject();
                depthSampler.addProperty("name", "SamplerSceneDepth");
                samplers.add(depthSampler);
                json.add("samplers", samplers);
                var uniforms = json.has("uniforms") ? json.getAsJsonArray("uniforms") : new com.google.gson.JsonArray();
                var screenSize = new com.google.gson.JsonObject();
                screenSize.addProperty("name", "ScreenSize");
                screenSize.addProperty("type", "float");
                screenSize.addProperty("count", 2);
                var defaults = new com.google.gson.JsonArray();
                defaults.add(1.0);
                defaults.add(1.0);
                screenSize.add("values", defaults);
                uniforms.add(screenSize);
                var legacyVariant = new com.google.gson.JsonObject();
                legacyVariant.addProperty("name", "LegacyVariantOnly");
                legacyVariant.addProperty("type", "float");
                legacyVariant.addProperty("count", 1);
                var legacyDefaults = new com.google.gson.JsonArray();
                legacyDefaults.add(0.0);
                legacyVariant.add("values", legacyDefaults);
                uniforms.add(legacyVariant);
                json.add("uniforms", uniforms);
                Files.writeString(shaderDir.resolve("tornado_smoke.json"), json.toString());
            }
            var variantSceneFragment = Files.readString(shaderDir.resolve("tornado_smoke.fsh"))
                    .replace("uniform sampler2D SamplerSceneColor;", "#ifdef PARTICLE_MODEL_INSTANCE\nuniform sampler2D SamplerSceneColor;\n#endif")
                    .replace("vec3 backdrop = texture(SamplerSceneColor, gl_FragCoord.xy / ScreenSize).rgb;",
                            "#ifdef PARTICLE_MODEL_INSTANCE\nvec3 backdrop = texture(SamplerSceneColor, gl_FragCoord.xy / ScreenSize).rgb;\n#else\nvec3 backdrop = vec3(0.0);\n#endif");
            Files.writeString(shaderDir.resolve("variant_scene.fsh"), variantSceneFragment);
            var variantSceneManifest = com.google.gson.JsonParser.parseString(Files.readString(shaderDir.resolve("tornado_smoke.json"))).getAsJsonObject();
            variantSceneManifest.addProperty("fragment", "photon:ci/variant_scene");
            Files.writeString(shaderDir.resolve("variant_scene.json"), variantSceneManifest.toString());
            Files.writeString(shaderDir.resolve("trail_smoke.fsh"), """
                    #version 330 core
                    in float vertexDistance;
                    in vec2 texCoord0;
                    in vec4 vertexColor;
                    out vec4 fragColor;
                    void main() {
                        float sweep = 0.8 + 0.2 * sin((texCoord0.x + texCoord0.y) * 6.2831853);
                        fragColor = vec4(vertexColor.rgb * vec3(0.65, 0.9, 1.0) * sweep, vertexColor.a);
                    }
                    """);
            try (var manifest = Photon.class.getResourceAsStream("/assets/photon/shaders/core/circle.json")) {
                var json = com.google.gson.JsonParser.parseString(new String(manifest.readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                json.addProperty("fragment", "photon:ci/trail_smoke");
                Files.writeString(shaderDir.resolve("trail_smoke.json"), json.toString());
            }
            Files.writeString(shaderDir.resolve("trail_cpu_smoke.fsh"), """
                    #version 150
                    uniform sampler2D TailTexture;
                    uniform vec4 colorHDR1;
                    uniform vec4 colorHDR2;
                    in vec2 texCoord0;
                    out vec4 fragColor;
                    void main() {
                        vec3 sampleColor = texture(TailTexture, texCoord0).rgb;
                        vec4 color = mix(colorHDR1, colorHDR2, texCoord0.x);
                        fragColor = vec4(sampleColor * color.rgb * color.a, 1.0);
                    }
                    """);
            // A closed CPU model with different front/back colours exercises the same
            // gl_FrontFacing and transparent sorting contract as the legacy shield.
            Files.writeString(shaderDir.resolve("shield_cpu_smoke.vsh"), """
                    #version 330 core
                    #moj_import <photon:particle.glsl>
                    uniform mat4 ModelViewMat;
                    uniform mat4 ProjMat;
                    void main() {
                        ParticleData data = getParticleData();
                        float offset = (gl_VertexID % 1269 > 255) ? 0.015 : 0.035;
                        gl_Position = ProjMat * ModelViewMat * vec4(data.Position + data.Normal * offset, 1.0);
                    }
                    """);
            Files.writeString(shaderDir.resolve("shield_cpu_smoke.fsh"), """
                    #version 330 core
                    out vec4 fragColor;
                    void main() {
                        fragColor = gl_FrontFacing ? vec4(1.0, 0.0, 0.0, 0.5)
                                                  : vec4(0.0, 1.0, 0.0, 0.5);
                    }
                    """);
            try (var manifest = Photon.class.getResourceAsStream("/assets/photon/shaders/core/circle.json")) {
                var json = com.google.gson.JsonParser.parseString(new String(manifest.readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                json.addProperty("vertex", "photon:ci/shield_cpu_smoke");
                json.addProperty("fragment", "photon:ci/shield_cpu_smoke");
                Files.writeString(shaderDir.resolve("shield_cpu_smoke.json"), json.toString());
            }
            try (var manifest = Photon.class.getResourceAsStream("/assets/photon/shaders/core/circle.json")) {
                var json = com.google.gson.JsonParser.parseString(new String(manifest.readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                json.addProperty("fragment", "photon:ci/trail_cpu_smoke");
                var tailSampler = new com.google.gson.JsonObject();
                tailSampler.addProperty("name", "TailTexture");
                json.getAsJsonArray("samplers").add(tailSampler);
                for (var name : java.util.List.of("colorHDR1", "colorHDR2")) {
                    var colorUniform = new com.google.gson.JsonObject();
                    colorUniform.addProperty("name", name);
                    colorUniform.addProperty("type", "float");
                    colorUniform.addProperty("count", 4);
                    var defaults = new com.google.gson.JsonArray();
                    for (int i = 0; i < 4; i++) defaults.add(1.0);
                    colorUniform.add("values", defaults);
                    json.getAsJsonArray("uniforms").add(colorUniform);
                }
                Files.writeString(shaderDir.resolve("trail_cpu_smoke.json"), json.toString());
            }
            Files.writeString(shaderDir.resolve("tangent_smoke.vsh"), """
                    #version 330 core
                    #moj_import <photon:particle.glsl>
                    uniform mat4 ModelViewMat;
                    uniform mat4 ProjMat;
                    out float vertexDistance;
                    out vec2 texCoord0;
                    out vec4 vertexColor;
                    void main() {
                        ParticleData data = getParticleData();
#if defined(PARTICLE_MODEL_INSTANCE) && defined(PHOTON_TANGENT)
                        data.UV = aUV;
                        data.Normal = aNormal.xyz;
                        data.Color.rgb *= aNormal.w;
                        data.Position += aTangent.xyz * 0.001;
#endif
                        vertexDistance = 0.0;
                        texCoord0 = data.UV;
                        vertexColor = data.Color;
                        gl_Position = ProjMat * ModelViewMat * vec4(data.Position, 1.0);
                    }
                    """);
            try (var manifest = Photon.class.getResourceAsStream("/assets/photon/shaders/core/circle.json")) {
                var json = com.google.gson.JsonParser.parseString(new String(manifest.readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                json.addProperty("vertex", "photon:ci/tangent_smoke");
                Files.writeString(shaderDir.resolve("tangent_smoke.json"), json.toString());
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
            if (Boolean.getBoolean("photon.serverFxPackSyncSmoke") && !serverFxPackSyncLogged) {
                serverFxPackSyncTicks++;
                var marker = new ResourceLocation("photon_sync_probe", "textures/server_sync_marker.txt");
                if (minecraft.getResourceManager().getResource(marker).isPresent()) {
                    Photon.LOGGER.info("[server-fxpack-sync-smoke] server-only FX pack transferred in chunks and mounted after automatic resource reload");
                    serverFxPackSyncLogged = true;
                } else if (serverFxPackSyncTicks >= 600) {
                    throw new IllegalStateException("Server FX pack did not reach the client after login");
                }
            }
            // Server pack sync reloads the resource manager. Let it finish before starting editor particle checks.
            if (Boolean.getBoolean("photon.serverFxPackSyncSmoke") && !serverFxPackSyncLogged) return;
            if (!bloomSmokeLogged) {
                verifyLegacyBlendSafety();
                verifyBloomSceneMask();
                verifyBloomDefaults();
                verifyFxPickerAndBloomControl();
                bloomSmokeLogged = true;
            }
            // A slow software renderer may process multiple client ticks before drawing a frame.
            // Hold each isolated phase until it has actually reached the render pipeline.
            if (Boolean.getBoolean("photon.realTornadoSmoke") && ticks >= 180 && ticks < 208
                    && (ticks - 180) % 7 == 6) {
                String layer = System.getProperty("photon.realTornadoLayer", "");
                if (!com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline.smokeSawTornadoLayerFrame(layer)) {
                    if (++tornadoFrameWait > 10) throw new IllegalStateException("No rendered frame for tornado layer " + layer);
                    return;
                }
                tornadoFrameWait = 0;
            }
            if (started && smokeTrail != null) {
                ticks++;
                if (ticks <= 320) {
                    float x = -2.5f + ticks * 0.12f;
                    float z = (float) Math.sin(ticks * 0.12f) * 0.7f;
                    smokeTrail.transform().localPosition(new org.joml.Vector3f(x, 2, z));
                    smokeCpuTrail.transform().localPosition(new org.joml.Vector3f(2.5f - ticks * 0.12f, 2, z));
                    smokeAraTrail.transform().localPosition(new org.joml.Vector3f(x, 3, z));
                }
            }
            if (realArrowTrail != null) {
                float elapsed = ticks - 260;
                realArrowTrail.transform().localPosition(new org.joml.Vector3f(-2.5f + elapsed * 0.12f, 2,
                        (float) Math.sin(elapsed * 0.12f) * 0.7f));
            }
            if (Boolean.getBoolean("photon.realTornadoSmoke") && ticks >= 180 && ticks < 208) {
                String[] names = {"tornado_body", "tornado_body2", "tornado_cap", "tornado_body3"};
                int phase = (ticks - 180) / 7;
                String selected = names[phase];
                if ((ticks - 180) % 7 == 0) {
                    editor.runtime.objects.values().stream().filter(ParticleEmitter.class::isInstance)
                            .map(ParticleEmitter.class::cast).forEach(e -> e.setSelfVisible(e.getName().equals(selected)));
                    System.setProperty("photon.realTornadoLayer", selected);
                } else if ((ticks - 180) % 7 == 6) {
                    Screenshot.grab(new File("."), minecraft.getMainRenderTarget(),
                            message -> Photon.LOGGER.info("[real-tornado-layer-smoke] {} screenshot: {}", selected, message.getString()));
                }
            } else if (Boolean.getBoolean("photon.realTornadoSmoke") && ticks == 208) {
                editor.runtime.objects.values().stream().filter(ParticleEmitter.class::isInstance)
                        .map(ParticleEmitter.class::cast).forEach(e -> e.setSelfVisible(true));
                System.clearProperty("photon.realTornadoLayer");
                for (String name : new String[]{"tornado_body", "tornado_body2", "tornado_cap", "tornado_body3"})
                    Photon.LOGGER.info("[real-tornado-layer-smoke] {} contributed pixels: {}", name,
                            com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline.smokeSawTornadoLayer(name));
            }
            if (!started) {
                started = true;
                openLegacyProject(minecraft);
            } else if (ticks == 30) {
                int queued = editor.sceneView.particleManager.particlesByRenderType().values().stream()
                        .mapToInt(Collection::size).sum();
                if (queued == 0) throw new IllegalStateException("Editor preview did not queue any particles");
                var trailParticle = trailParticle(smokeTrail);
                if (smokeTrail.getParticleAmount() != 1 || trailParticle.getTails().size() < 3) {
                    throw new IllegalStateException("Moving GPU trail did not accumulate renderable segments: alive="
                            + smokeTrail.getParticleAmount() + ", segments=" + trailParticle.getTails().size());
                }
                var cpuTrailParticle = trailParticle(smokeCpuTrail);
                if (smokeCpuTrail.getParticleAmount() != 1 || cpuTrailParticle.getTails().size() < 3) {
                    throw new IllegalStateException("Moving CPU trail did not accumulate renderable segments");
                }
                Field araParticleField = smokeAraTrail.getClass().getDeclaredField("trailParticle");
                araParticleField.setAccessible(true);
                var araParticle = (com.lowdragmc.photon.client.gameobject.particle.aratrail.AraTrailParticle) araParticleField.get(smokeAraTrail);
                if (araParticle.getPoints().size() < 3)
                    throw new IllegalStateException("Moving AraTrail did not accumulate renderable points");
                Photon.LOGGER.info("[ara-trail-points-smoke] moving AraTrail accumulated {} points", araParticle.getPoints().size());
                int live = editor.runtime.objects.values().stream().filter(ParticleEmitter.class::isInstance)
                        .map(ParticleEmitter.class::cast).mapToInt(ParticleEmitter::getParticleAmount).sum();
                if (live != 5) throw new IllegalStateException("Expected legacy model, two tornado-style GPU model particles, and CPU/GPU billboard particles, got " + live);
                var screenUniform = smokeScreenShader.getShader(MaterialContext.PARTICLE_MODEL_INSTANCE).getUniform("ScreenSize");
                var target = minecraft.getMainRenderTarget();
                if (screenUniform == null || screenUniform.getFloatBuffer().get(0) != target.width ||
                        screenUniform.getFloatBuffer().get(1) != target.height)
                    throw new IllegalStateException("ScreenSize did not match the scene framebuffer");
                var modelShader = smokeScreenShader.getShader(MaterialContext.PARTICLE_MODEL_INSTANCE);
                int pivotLocation = org.lwjgl.opengl.GL20.glGetUniformLocation(modelShader.getId(), "PhotonModelPivot");
                if (pivotLocation < 0) throw new IllegalStateException("Model shader did not expose the pivot uniform");
                float[] actualPivot = new float[3];
                org.lwjgl.opengl.GL20.glGetUniformfv(modelShader.getId(), pivotLocation, actualPivot);
                if (Math.abs(actualPivot[0] - 0.125f) > 0.0001f || Math.abs(actualPivot[1] - 0.25f) > 0.0001f)
                    throw new IllegalStateException("Model shader did not receive the runtime pivot");
                Photon.LOGGER.info("[model-pivot-smoke] custom model shader received nonzero runtime pivot");
                Screenshot.grab(new File("."), minecraft.getMainRenderTarget(),
                        message -> Photon.LOGGER.info("[fxproj-editor-smoke] screenshot: {}", message.getString()));
                Photon.LOGGER.info("[trail-render-smoke] custom TRAIL_INSTANCE shader rendered moving GPU trail with {} live segments", trailParticle.getTails().size());
                Photon.LOGGER.info("[screen-size-smoke] custom GPU model shader rendered using framebuffer-sized ScreenSize");
                Photon.LOGGER.info("[fxproj-editor-smoke] opened v3 managed-list project and previewed legacy model, tornado-style OBJ shader model, CPU-billboard particles, and moving GPU trail ({} live, {} queued objects)", live, queued);
            } else if (ticks == 110) {
                Screenshot.grab(new File("."), minecraft.getMainRenderTarget(),
                        message -> Photon.LOGGER.info("[fxproj-editor-smoke] expired screenshot: {}", message.getString()));
                editor.closeCurrentProject(false, null);
                Files.deleteIfExists(projectFile);
                var realScanFile = new File("./ldlib2/projects/scan.fxproj");
                var realScan = (FXProject) FXProject.TYPE.loadProjectFromFile(realScanFile);
                editor.loadProject(realScan, realScanFile);
                if (editor.getCurrentProject() != realScan || editor.runtime == null)
                    throw new IllegalStateException("Real scan project did not initialize in the editor");
                System.setProperty("photon.realScanSmoke", "true");
                Photon.LOGGER.info("[real-scan-project-smoke] opened original scan project in editor");
            } else if (ticks == 140) {
                var scan = editor.runtime.objects.values().stream().filter(ParticleEmitter.class::isInstance)
                        .map(ParticleEmitter.class::cast).findFirst().orElseThrow();
                var slot = scan.config.renderer.getMaterials().get(0).getMaterial();
                if (!slot.usesSceneColor()) throw new IllegalStateException("Referenced scan material lost its scene-colour dependency");
                var kilaSceneProbe = new com.lowdragmc.photon.client.gameobject.emitter.data.material.kila.KilaMaterial();
                if (kilaSceneProbe.usesSceneColor()) throw new IllegalStateException("Inactive Kila distortion unexpectedly samples scene colour");
                kilaSceneProbe.screenDistortion.setEnable(true);
                if (!kilaSceneProbe.usesSceneColor()) throw new IllegalStateException("Kila distortion did not report its scene-colour dependency");
                if (slot instanceof com.lowdragmc.photon.client.gameobject.emitter.data.material.UIResourceMaterial reference)
                    slot = reference.getRawMaterial();
                if (!(slot instanceof CustomShaderMaterial shader) || shader.isCompiledError()
                        || !shader.getShaderLocation().equals(new ResourceLocation("ldlib2", "scan"))
                        || scan.getParticleAmount() == 0)
                    throw new IllegalStateException("Real scan shader or particle failed to load");
                shader.getShader(MaterialContext.NORMAL);
                if (shader.isCompiledError()) throw new IllegalStateException("Real scan shader failed to compile");
                if (!shader.usesSceneColor()) throw new IllegalStateException("Scan shader did not report its scene-colour dependency");
                var variantScene = new CustomShaderMaterial(new ResourceLocation("photon", "ci/variant_scene"));
                var variantBase = variantScene.getShader(MaterialContext.NORMAL);
                if (((LDShaderInstance) variantBase).getShaderInstanceAccessor().getSamplerNames().contains("SamplerSceneColor")
                        || !variantScene.usesSceneColor())
                    throw new IllegalStateException("Variant-only scene colour sampler was not detected from its shader manifest");
                verifyLateSceneSnapshot();
                Photon.LOGGER.info("[real-scan-shader-smoke] original scan shader compiled and particle is alive");
            } else if (ticks == 160) {
                Screenshot.grab(new File("."), minecraft.getMainRenderTarget(),
                        message -> Photon.LOGGER.info("[real-scan-project-smoke] screenshot: {}", message.getString()));
                editor.closeCurrentProject(false, null);
                System.clearProperty("photon.realScanSmoke");
                var realTornadoFile = new File("./ldlib2/projects/tornado.fxproj");
                var realTornado = (FXProject) FXProject.TYPE.loadProjectFromFile(realTornadoFile);
                editor.loadProject(realTornado, realTornadoFile);
                if (editor.getCurrentProject() != realTornado || editor.runtime == null)
                    throw new IllegalStateException("Real tornado project did not initialize in the editor");
                savedSceneZoom = editor.sceneView.sceneEditor.scene.getZoom();
                savedSceneCenter = new org.joml.Vector3f(editor.sceneView.sceneEditor.scene.getCenter());
                editor.sceneView.sceneEditor.scene.setCenter(new org.joml.Vector3f(0.5f, 5f, 0.5f));
                editor.sceneView.sceneEditor.scene.setZoom(9f);
                System.setProperty("photon.realTornadoSmoke", "true");
            } else if (ticks == 175) {
                Screenshot.grab(new File("."), minecraft.getMainRenderTarget(),
                        message -> Photon.LOGGER.info("[real-tornado-complete-smoke] screenshot near reference animation time: {}", message.getString()));
            } else if (ticks == 190) {
                var tornadoSparks = editor.runtime.objects.values().stream().filter(ParticleEmitter.class::isInstance)
                        .map(ParticleEmitter.class::cast).filter(e -> e.getName().equals("particle_emitter"))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Real tornado spark emitter is missing"));
                if (tornadoSparks.getParticleAmount() == 0)
                    throw new IllegalStateException("Real tornado spark emitter did not emit particles");
                Photon.LOGGER.info("[real-tornado-sparks-smoke] original spark emitter has {} live particles", tornadoSparks.getParticleAmount());
                var modelEmitters = editor.runtime.objects.values().stream().filter(ParticleEmitter.class::isInstance)
                        .map(ParticleEmitter.class::cast).filter(e -> e.config.renderer.getRenderMode() == ParticleRendererSetting.Mode.Model)
                        .toList();
                if (modelEmitters.size() != 4) throw new IllegalStateException("Expected four real tornado model emitters: " + modelEmitters.size());
                long tChannel = PhotonGpuChannels.CHANNELS.stream().filter(channel -> channel.id().equals("addition_gpu_data.t"))
                        .findFirst().orElseThrow().bit();
                for (var emitter : modelEmitters) {
                    var source = emitter.config.renderer.getModelSource();
                    var slot = emitter.config.renderer.getMaterials().get(0).getMaterial();
                    if (slot instanceof com.lowdragmc.photon.client.gameobject.emitter.data.material.UIResourceMaterial reference)
                        slot = reference.getRawMaterial();
                    if (!(source instanceof JsonModelSource model) || model.getMesh() == null || model.getMesh().vertexCount() < 3
                            || !(slot instanceof CustomShaderMaterial shader) || shader.isCompiledError()
                            || !shader.getShaderLocation().equals(new ResourceLocation("ldlib2", "tornado_body"))
                            || shader.curveTexture.getCurveTexture() == null || !emitter.config.renderer.isUseGPUInstance()
                            || !emitter.config.additionalGPUDataSetting.isEnable()
                            || (emitter.config.additionalGPUDataSetting.userMask() & tChannel) == 0
                            || emitter.getParticleAmount() == 0)
                        throw new IllegalStateException("Real tornado model, material, curve, channel or particle failed to load");
                }
                Photon.LOGGER.info("[real-tornado-project-smoke] loaded four live OBJ model emitters and their original shaders");
            } else if (ticks == 210) {
                Screenshot.grab(new File("."), minecraft.getMainRenderTarget(),
                        message -> Photon.LOGGER.info("[real-tornado-project-smoke] screenshot: {}", message.getString()));
                editor.closeCurrentProject(false, null);
                System.clearProperty("photon.realTornadoSmoke");
                editor.sceneView.sceneEditor.scene.setCenter(savedSceneCenter);
                editor.sceneView.sceneEditor.scene.setZoom(savedSceneZoom);
                var realShieldFile = new File("./ldlib2/projects/shield.fxproj");
                var realShield = (FXProject) FXProject.TYPE.loadProjectFromFile(realShieldFile);
                editor.loadProject(realShield, realShieldFile);
                if (editor.getCurrentProject() != realShield || editor.runtime == null)
                    throw new IllegalStateException("Real shield project did not initialize in the editor");
                System.setProperty("photon.realShieldSmoke", "true");
            } else if (ticks == 240) {
                var shield = editor.runtime.objects.values().stream().filter(ParticleEmitter.class::isInstance)
                        .map(ParticleEmitter.class::cast).findFirst().orElseThrow();
                var source = shield.config.renderer.getModelSource();
                var slot = shield.config.renderer.getMaterials().get(0).getMaterial();
                if (slot instanceof com.lowdragmc.photon.client.gameobject.emitter.data.material.UIResourceMaterial reference)
                    slot = reference.getRawMaterial();
                if (!(source instanceof JsonModelSource model) || model.getMesh() == null || model.getMesh().vertexCount() < 3
                        || !(slot instanceof CustomShaderMaterial shader) || shader.isCompiledError()
                        || !shader.getShaderLocation().equals(new ResourceLocation("ldlib2", "shield"))
                        || shield.getParticleAmount() == 0)
                    throw new IllegalStateException("Real shield model, shader or particle failed to load");
                if (shield.shouldCull() || ((com.lowdragmc.photon.client.gameobject.FXObject) editor.runtime.root).shouldCull())
                    throw new IllegalStateException("Forge frustum-culls an effect with culling disabled");
                var cull = shield.runtime().renderer.getCull();
                var rotatedBounds = com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting.Cull.transformedBounds(
                        new net.minecraft.world.phys.AABB(-2, -1, -0.5, 2, 1, 0.5),
                        new org.joml.Vector3f(10, 5, 3),
                        new org.joml.Quaternionf().rotationY((float) (Math.PI / 2)),
                        new org.joml.Vector3f(-1, 1, 2));
                if (Math.abs(rotatedBounds.minX - 9) > 0.001 || Math.abs(rotatedBounds.maxX - 11) > 0.001
                        || Math.abs(rotatedBounds.minZ - 1) > 0.001 || Math.abs(rotatedBounds.maxZ - 5) > 0.001)
                    throw new IllegalStateException("Rotated or negatively scaled emitter bounds are wrong: " + rotatedBounds);
                var forward = new org.joml.Vector3f(0, 0, -1);
                var atEye = com.lowdragmc.photon.client.gameobject.particle.renderer.TileParticleRenderer.adjustOwnerBillboard(
                        new org.joml.Vector3f(), true, forward);
                var other = com.lowdragmc.photon.client.gameobject.particle.renderer.TileParticleRenderer.adjustOwnerBillboard(
                        new org.joml.Vector3f(), false, forward);
                var distant = com.lowdragmc.photon.client.gameobject.particle.renderer.TileParticleRenderer.adjustOwnerBillboard(
                        new org.joml.Vector3f(1, 0, 0), true, forward);
                if (atEye.z > -0.099f || other.lengthSquared() != 0 || distant.x != 1 || distant.z != 0)
                    throw new IllegalStateException("First-person billboard near-plane correction affected the wrong positions");
                cull.setEnable(true);
                try {
                    if (!shield.shouldCull() || !shield.getBoundingBox().equals(shield.getCullBox(1f)))
                        throw new IllegalStateException("Forge is not using the authored emitter cull box");
                } finally { cull.setEnable(false); }
                Photon.LOGGER.info("[forge-cull-smoke] disabled culling stays visible and authored bounds reach Forge");
                var queuedParticle = shield.getParticles().values().stream().flatMap(java.util.Collection::stream)
                        .findFirst().orElseGet(shield.waitToAdded::peek);
                if (queuedParticle == null) throw new IllegalStateException("No shield particle for removal test");
                var removalProbe = new ParticleEmitter();
                removalProbe.waitToAdded.add(queuedParticle);
                removalProbe.remove(true);
                if (!removalProbe.waitToAdded.isEmpty())
                    throw new IllegalStateException("Forced removal left a particle queued for later");
                Photon.LOGGER.info("[queued-removal-smoke] forced removal cleared pending particles");
                var shieldHolderField = CustomShaderMaterial.class.getDeclaredField("shaderHolder");
                shieldHolderField.setAccessible(true);
                var shieldHolder = (com.lowdragmc.lowdraglib2.client.shader.LDShaderHolder) shieldHolderField.get(shader);
                shieldHolder.setHDRUniform("FrontHDR", new com.lowdragmc.lowdraglib2.math.HDRColor(0.15f, 0.75f, 0.25f, 1f, 1f));
                var packDir = Files.createTempDirectory("photon-shield-pack-");
                var pack = packDir.resolve("shield.fxpack");
                try {
                    var project = (FXProject) editor.getCurrentProject();
                    var result = com.lowdragmc.photon.client.fx.fxpack.FXPackExporter.exportInto(
                            pack.toFile(), "smoke", "shield", project.getFx(),
                            com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry());
                    try (var zip = new java.util.zip.ZipFile(pack.toFile())) {
                        for (var path : java.util.List.of("assets/smoke/fx/shield.fx",
                                "assets/ldlib2/models/block/shield.json", "assets/ldlib2/models/obj/shield.obj",
                                "assets/ldlib2/shaders/core/shield.json", "assets/ldlib2/shaders/core/shield.fsh",
                                "assets/photon/shaders/include/particle.glsl")) {
                            if (zip.getEntry(path) == null)
                                throw new IllegalStateException("Shield fxpack missing " + path + "; warnings=" + result.warnings());
                        }
                        var material = zip.stream().filter(entry -> entry.getName().startsWith("assets/photon_fx/material/")
                                && entry.getName().endsWith(".material.nbt")).findFirst().orElseThrow();
                        net.minecraft.nbt.CompoundTag wrapper;
                        try (var input = new java.io.DataInputStream(zip.getInputStream(material))) {
                            wrapper = net.minecraft.nbt.NbtIo.read(input);
                        }
                        var restored = com.lowdragmc.photon.gui.editor.resource.MaterialResource.INSTANCE
                                .deserializeResource(wrapper.get("data"), com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry());
                        if (!(restored instanceof CustomShaderMaterial restoredShader))
                            throw new IllegalStateException("Shield fxpack material did not deserialize");
                        var front = restoredShader.getShader(MaterialContext.NORMAL).getUniform("FrontHDR");
                        if (front == null || Math.abs(front.getFloatBuffer().get(1) - 0.75f) > 0.0001f)
                            throw new IllegalStateException("Shield fxpack lost the edited color");
                        var metadata = new String(zip.getInputStream(zip.getEntry("pack.mcmeta")).readAllBytes(),
                                java.nio.charset.StandardCharsets.UTF_8);
                        if (!metadata.contains("\"pack_format\": 15"))
                            throw new IllegalStateException("Wrong 1.20.1 resource-pack format: " + metadata);
                    }
                    Photon.LOGGER.info("[shield-fxpack-smoke] packed shield material, shader, JSON model and OBJ");
                } finally {
                    Files.deleteIfExists(pack);
                    Files.deleteIfExists(packDir);
                }
                Photon.LOGGER.info("[real-shield-project-smoke] loaded original shield model and shader");
            } else if (ticks == 241 && Boolean.getBoolean("photon.exportUiSmoke")) {
                // Exercise the actual File > Export FX menu callback, including its deferred opening.
                var createMenu = com.lowdragmc.lowdraglib2.editor.ui.menu.MenuTab.class.getDeclaredMethod("createMenu");
                createMenu.setAccessible(true);
                var menu = (com.lowdragmc.lowdraglib2.gui.util.TreeBuilder.Menu) createMenu.invoke(editor.fileMenu);
                var export = menu.build().getChildren().stream()
                        .filter(node -> node.getKey().getB().equals(Component.translatable("ldlib.gui.editor.menu.export")))
                        .findFirst().orElseThrow();
                var action = export.getChildren().stream()
                        .filter(node -> node.getKey().getB().equals(Component.translatable("photon.export_fx")))
                        .findFirst().orElseThrow();
                if (action.getContent() == null) throw new IllegalStateException("Export FX action is disabled");
                action.getContent().run();
            } else if (ticks == 244 && Boolean.getBoolean("photon.exportUiSmoke")) {
                var root = editor.getModularUI().ui.rootElement;
                var dialog = root.getChildren().stream()
                        .filter(com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog.class::isInstance)
                        .map(com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog.class::cast).findFirst().orElseThrow();
                var name = dialog.contentContainer.getChildren().get(0).getChildren().stream()
                        .filter(com.lowdragmc.lowdraglib2.gui.ui.elements.TextField.class::isInstance)
                        .map(com.lowdragmc.lowdraglib2.gui.ui.elements.TextField.class::cast).findFirst().orElseThrow();
                if (!"shield.fx".equals(name.getText()))
                    throw new IllegalStateException("Export FX default filename was " + name.getText());
                Photon.LOGGER.info("[export-menu-smoke] actual menu action opened a save dialog with the project filename");
                dialog.close();
            } else if (ticks == 247 && Boolean.getBoolean("photon.exportUiSmoke")) {
                clickEditorElement(minecraft, editor.menuContainer.getChildren().get(0));
            } else if (ticks == 250 && Boolean.getBoolean("photon.exportUiSmoke")) {
                clickEditorElement(minecraft, findMenuEntry("ldlib.gui.editor.menu.export"));
            } else if (ticks == 253 && Boolean.getBoolean("photon.exportUiSmoke")) {
                clickEditorElement(minecraft, findMenuEntry("photon.export_fx"));
            } else if (ticks == 257 && Boolean.getBoolean("photon.exportUiSmoke")) {
                var root = editor.getModularUI().ui.rootElement;
                var dialog = root.getChildren().stream()
                        .filter(com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog.class::isInstance)
                        .map(com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog.class::cast).findFirst().orElseThrow();
                Photon.LOGGER.info("[export-click-smoke] clicking File, Export and Export FX displayed the dialog");
                dialog.close();
            } else if (ticks == 260) {
                Screenshot.grab(new File("."), minecraft.getMainRenderTarget(),
                        message -> Photon.LOGGER.info("[real-shield-project-smoke] screenshot: {}", message.getString()));
                // Exercise the same file format as File > Export FX, then the entity packet's
                // encode/decode and client handler against the actual player in the test world.
                var exported = new File(PhotonPaths.getAssetsDir(), "photon/fx/ci_export_entity.fx");
                if (!exported.getParentFile().isDirectory() && !exported.getParentFile().mkdirs())
                    throw new IllegalStateException("Could not create FX export directory");
                var definition = ((FXProject) editor.getCurrentProject()).getFx().serializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry());
                definition.putInt("version", FXProject.VERSION);
                net.minecraft.nbt.NbtIo.writeCompressed(definition, exported);
                // Resource packs snapshot their files during reload. Wait for that asynchronous reload
                // before exercising the command against a newly exported workspace effect.
                minecraft.reloadResourcePacks().whenComplete((ignored, failure) -> minecraft.execute(() -> {
                    if (failure != null) {
                        exportReloadFailure = failure;
                        return;
                    }
                    try {
                        com.lowdragmc.photon.client.fx.FXHelper.clearCache();
                        var entityPacket = new com.lowdragmc.photon.command.EntityEffectCommand();
                        entityPacket.setLocation(new ResourceLocation("photon", "ci_export_entity"));
                        entityPacket.setEntities(java.util.List.of(minecraft.player));
                        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                        try {
                            entityPacket.encode(buffer);
                            com.lowdragmc.photon.command.EntityEffectCommand.execute(
                                    com.lowdragmc.photon.command.EntityEffectCommand.decodePacket(buffer));
                        } finally { buffer.release(); }
                        var attached = com.lowdragmc.photon.client.fx.EntityEffectExecutor.CACHE.get(minecraft.player);
                        if (attached == null || attached.isEmpty() || attached.get(attached.size() - 1).getRuntime() == null)
                            throw new IllegalStateException("Exported FX did not start on player through entity packet");
                        Photon.LOGGER.info("[export-entity-smoke] exported definition loaded and started on player through decoded packet");
                        attached.forEach(effect -> effect.getRuntime().destroy(true));
                        com.lowdragmc.photon.client.fx.EntityEffectExecutor.CACHE.remove(minecraft.player);
                        var server = minecraft.getSingleplayerServer();
                        if (server == null) throw new IllegalStateException("Integrated server is unavailable");
                        var playerId = minecraft.player.getUUID();
                        server.execute(() -> {
                            var player = server.getPlayerList().getPlayer(playerId);
                            if (player == null) throw new IllegalStateException("Smoke-test player is unavailable on server");
                            server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4),
                                    "photon fx photon:ci_export_entity entity @s");
                        });
                        exportReloadComplete = true;
                    } catch (Throwable error) {
                        exportReloadFailure = error;
                    }
                }));
                editor.closeCurrentProject(false, null);
                System.clearProperty("photon.realShieldSmoke");
                var realTrailFile = new File("./ldlib2/projects/arrow_trail.fxproj");
                var realTrail = (FXProject) FXProject.TYPE.loadProjectFromFile(realTrailFile);
                editor.loadProject(realTrail, realTrailFile);
                if (editor.getCurrentProject() != realTrail || editor.runtime == null)
                    throw new IllegalStateException("Real arrow trail project did not initialize in the editor");
                realArrowTrail = editor.runtime.objects.values().stream().filter(TrailEmitter.class::isInstance)
                        .map(TrailEmitter.class::cast).findFirst().orElseThrow();
                System.setProperty("photon.realTrailSmoke", "true");
            } else if (ticks >= 310 && !exportServerCommandVerified) {
                if (exportReloadFailure != null)
                    throw new IllegalStateException("Exported workspace FX did not reload", exportReloadFailure);
                if (!exportReloadComplete) {
                    if (ticks >= 400) throw new IllegalStateException("Photon workspace resource reload did not complete");
                    return;
                }
                var attached = com.lowdragmc.photon.client.fx.EntityEffectExecutor.CACHE.get(minecraft.player);
                if (attached == null || attached.isEmpty() || attached.get(attached.size() - 1).getRuntime() == null) {
                    if (ticks >= 400) throw new IllegalStateException("Server command did not deliver the exported effect to the player");
                    return;
                }
                Photon.LOGGER.info("[export-server-command-smoke] server command delivered exported FX over Forge networking");
                exportServerCommandVerified = true;
                removedEntityRuntime = attached.get(attached.size() - 1).getRuntime();
                var server = minecraft.getSingleplayerServer();
                var playerId = minecraft.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(playerId);
                    server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4),
                            "photon fx remove entity @s");
                });
            } else if (ticks == 295) {
                if (realArrowTrail == null || trailParticle(realArrowTrail).getTails().size() < 3)
                    throw new IllegalStateException("Real arrow trail did not accumulate moving segments");
                Photon.LOGGER.info("[real-arrow-trail-project-smoke] original trail accumulated moving segments");
            } else if (ticks >= 325 && exportServerCommandVerified && !exportRemoveVerified) {
                if (removedEntityRuntime == null || !removedEntityRuntime.isFinished()
                        || removedEntityRuntime.objects.values().stream().filter(ParticleEmitter.class::isInstance)
                        .map(ParticleEmitter.class::cast).anyMatch(emitter -> emitter.getParticleAmount() != 0))
                    throw new IllegalStateException("Remove command left the long-lived shield particles visible");
                Photon.LOGGER.info("[remove-server-command-smoke] default remove cleared long-lived entity particles");
                exportRemoveVerified = true;
            } else if (ticks == 335) {
                Screenshot.grab(new File("."), minecraft.getMainRenderTarget(),
                        message -> Photon.LOGGER.info("[real-arrow-trail-project-smoke] screenshot: {}", message.getString()));
                realArrowTrail = null;
                editor.closeCurrentProject(false, null);
                System.clearProperty("photon.realTrailSmoke");
                minecraft.setScreen(previousScreen);
                var realScanFile = new File("./ldlib2/projects/scan.fxproj");
                var firstPersonScan = (FXProject) FXProject.TYPE.loadProjectFromFile(realScanFile);
                minecraft.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                System.setProperty("photon.firstPersonSmoke", "true");
                firstPersonEffect = new com.lowdragmc.photon.client.fx.EntityEffectExecutor(firstPersonScan.getFx(), minecraft.level,
                        minecraft.player, com.lowdragmc.photon.client.fx.EntityEffectExecutor.AutoRotate.NONE);
                firstPersonEffect.start();
            } else if (ticks == 365) {
                if (!Boolean.getBoolean("photon.firstPersonBillboardSeen") || !Boolean.getBoolean("photon.firstPersonScanPixels"))
                    throw new IllegalStateException("CPU first-person scan did not draw a camera-centred owner billboard");
                Photon.LOGGER.info("[first-person-scan-cpu-smoke] owner billboard rendered");
                firstPersonEffect.getRuntime().destroy(true);
                com.lowdragmc.photon.client.fx.EntityEffectExecutor.CACHE.remove(minecraft.player);
                System.clearProperty("photon.firstPersonBillboardSeen");
                System.clearProperty("photon.firstPersonScanPixels");
                System.setProperty("photon.firstPersonGpuSmoke", "true");
                var scanFile = new File("./ldlib2/projects/scan.fxproj");
                var gpuScan = (FXProject) FXProject.TYPE.loadProjectFromFile(scanFile);
                gpuScan.getFx().getFxData().objects().stream().filter(ParticleEmitter.class::isInstance)
                        .map(ParticleEmitter.class::cast).forEach(emitter -> emitter.config.renderer.setUseGPUInstance(true));
                firstPersonEffect = new com.lowdragmc.photon.client.fx.EntityEffectExecutor(gpuScan.getFx(), minecraft.level,
                        minecraft.player, com.lowdragmc.photon.client.fx.EntityEffectExecutor.AutoRotate.NONE);
                firstPersonEffect.start();
            } else if (ticks == 425) {
                if (!Boolean.getBoolean("photon.firstPersonBillboardSeen") || !Boolean.getBoolean("photon.firstPersonScanPixels"))
                    throw new IllegalStateException("GPU first-person scan did not draw a camera-centred owner billboard");
                Photon.LOGGER.info("[first-person-scan-gpu-smoke] owner billboard rendered");
                System.clearProperty("photon.firstPersonSmoke");
                System.clearProperty("photon.firstPersonGpuSmoke");
                finished = true;
                minecraft.stop();
            }
        } catch (Throwable failure) {
            finished = true;
            Photon.LOGGER.error("[fxproj-editor-smoke] failed to open or preview the legacy project", failure);
            throw new IllegalStateException("Photon editor project smoke test failed", failure);
        }
    }

    private static com.lowdragmc.lowdraglib2.gui.ui.UIElement findMenuEntry(String key) {
        for (var element : editor.getModularUI().getAllElements()) {
            if (element instanceof com.lowdragmc.lowdraglib2.gui.ui.elements.Menu<?, ?> menu) {
                for (var entry : menu.getNodeUIs().entrySet()) {
                    if (entry.getKey().getKey() instanceof net.minecraft.util.Tuple<?, ?> tuple
                            && tuple.getB() instanceof Component label
                            && label.equals(Component.translatable(key))) return entry.getValue();
                }
            }
        }
        throw new IllegalStateException("Menu entry not found: " + key);
    }

    private static void clickEditorElement(Minecraft minecraft, com.lowdragmc.lowdraglib2.gui.ui.UIElement element) {
        var pos = element.localToWorld(new org.joml.Vector2f(
                element.getPositionX() + element.getSizeWidth() / 2,
                element.getPositionY() + element.getSizeHeight() / 2));
        minecraft.screen.mouseMoved(pos.x, pos.y);
        minecraft.screen.mouseClicked(pos.x, pos.y, 0);
        minecraft.screen.mouseReleased(pos.x, pos.y, 0);
    }

    private static void openLegacyProject(Minecraft minecraft) throws Exception {
        verifyPhoton1Compatibility();
        com.lowdragmc.photon.client.fx.fxpack.FXPackDependencyRegression.verify();
        Photon.LOGGER.info("[fxpack-dependency-smoke] model, texture metadata and glTF buffers survive pack cleanup");
        com.lowdragmc.photon.client.gameobject.particle.renderer.ModelTriangleOrderRegression.verify();
        var callbacks = new int[1];
        var dynamic = new com.lowdragmc.photon.client.gameobject.emitter.data.model.IDynamicMesh() {
            private final PhotonMesh mesh = new PhotonMesh.Builder().triangle(
                    new float[]{0, 0, 0, 0, 0, 0, 0, 1},
                    new float[]{1, 0, 0, 1, 0, 0, 0, 1},
                    new float[]{0, 1, 0, 0, 1, 0, 0, 1}).build();
            @Override public PhotonMesh topology() { return mesh; }
            @Override public long revision() { return 1; }
            @Override public void onDrawn() { callbacks[0]++; }
        };
        var dynamicConfig = new com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleConfig();
        dynamicConfig.renderer.setRenderMode(ParticleRendererSetting.Mode.Model);
        dynamicConfig.renderer.setModel(new MeshData(new com.lowdragmc.photon.client.gameobject.emitter.data.model.DynamicMeshSource(dynamic)));
        var dynamicRenderer = new com.lowdragmc.photon.client.gameobject.particle.renderer.TileParticleRenderer(
                dynamicConfig, dynamicConfig.renderer.createRuntime());
        dynamicRenderer.renderQueue(null, java.util.List.of(), null, 0);
        if (callbacks[0] != 0) throw new IllegalStateException("Empty model queue notified a dynamic provider as drawn");
        Photon.LOGGER.info("[dynamic-empty-draw-smoke] no provider callback for an empty model queue");
        verifyNestedMeshLoad();
        var provider = com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry();
        var legacyMaterial = new CustomShaderMaterial(new ResourceLocation("photon", "ci/tornado_smoke"));
        var legacyData = new CompoundTag();
        var holderData = new CompoundTag();
        var legacyUniforms = new CompoundTag();
        var variantValue = new ListTag();
        variantValue.add(FloatTag.valueOf(0.37f));
        legacyUniforms.put("LegacyVariantOnly", variantValue);
        holderData.put("uniforms", legacyUniforms);
        holderData.put("samplers", new CompoundTag());
        legacyData.put("shaderData", holderData);
        var missingShader = new CustomShaderMaterial(new ResourceLocation("photon", "ci/intentionally_missing"));
        var missingData = legacyData.copy();
        var sampler = new CompoundTag();
        sampler.putString("type", "texture");
        sampler.putString("resource", "example:textures/particle/spark.png");
        missingData.getCompound("shaderData").getCompound("samplers").put("Sampler0", sampler);
        missingShader.deserializeAdditionalNBT(missingData, provider);
        var savedMissing = (CompoundTag) missingShader.serializeAdditionalNBT(provider);
        if (!missingShader.isCompiledError() || !savedMissing.getCompound("shaderData").equals(missingData.getCompound("shaderData"))) {
            throw new IllegalStateException("Saving a missing shader discarded its uniforms or samplers");
        }
        legacyMaterial.deserializeAdditionalNBT(legacyData, provider);
        if (legacyMaterial.getShader(MaterialContext.NORMAL).getUniform("LegacyVariantOnly") != null) {
            throw new IllegalStateException("Legacy variant-only uniform unexpectedly exists in the base shader");
        }
        var legacyVariant = legacyMaterial.getShader(MaterialContext.PARTICLE_MODEL_INSTANCE);
        var restoredVariantUniform = legacyVariant.getUniform("LegacyVariantOnly");
        if (restoredVariantUniform == null || Math.abs(restoredVariantUniform.getFloatBuffer().get(0) - 0.37f) > 0.0001f) {
            throw new IllegalStateException("Legacy material with samplers lost its variant-only uniform");
        }
        Photon.LOGGER.info("[legacy-variant-uniform-smoke] restored variant-only uniform from legacy shader data with samplers");
        var original = new CustomShaderMaterial();
        var authoredCurve = new com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve(
                0, 1, 0, 1, "", "", new com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.ECBCurves(
                0, 0.15f, 0.1f, 0.15f, 0.9f, 0.85f, 1, 0.85f));
        original.curveTexture.addCurve(authoredCurve);
        var saved = com.lowdragmc.lowdraglib2.utils.PersistedParser.serializeNBT(original, provider);
        if (!saved.contains("curveTexture", Tag.TAG_LIST)) throw new IllegalStateException("Curve sampler did not serialize as a list");
        var savedSegments = saved.getList("curveTexture", Tag.TAG_COMPOUND).getCompound(0).getList("curves", Tag.TAG_LIST);
        if (savedSegments.size() != 1 || !savedSegments.get(0).equals(floatList(0f, 0.15f, 0.1f, 0.15f, 0.9f, 0.85f, 1f, 0.85f)))
            throw new IllegalStateException("Shader curve did not preserve its authored control points: " + savedSegments);
        var restoredMaterial = new CustomShaderMaterial();
        com.lowdragmc.lowdraglib2.utils.PersistedParser.deserializeNBT(saved, restoredMaterial, provider);
        if (restoredMaterial.curveTexture.serializeNBT(provider).size() != 1 || restoredMaterial.curveTexture.getCurveTexture() == null)
            throw new IllegalStateException("Saved shader curve was not restored and uploaded");
        var restoredCurves = restoredMaterial.curveTexture.serializeNBT(provider);
        if (!saved.getList("curveTexture", Tag.TAG_COMPOUND).equals(restoredCurves))
            throw new IllegalStateException("Shader curve control points changed on reload: " + restoredCurves);
        var loadedCurve = new com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve();
        loadedCurve.deserializeNBT(provider, restoredCurves.getCompound(0));
        if (Math.abs(loadedCurve.getCurves().getCurveY(0f) - 0.15f) > 0.0001f ||
                Math.abs(loadedCurve.getCurves().getCurveY(1f) - 0.85f) > 0.0001f)
            throw new IllegalStateException("Shader curve values changed on reload");
        // A legacy shader curve can contain multiple raw Bezier segments, as in the tornado material.
        var legacySegments = new ListTag();
        legacySegments.add(floatList(0f, 0.3f, 0.1f, 0.3f, 0.4f, 0.4f, 0.5f, 0.4f));
        legacySegments.add(floatList(0.5f, 0.4f, 0.6f, 0.4f, 0.9f, 0.9f, 1f, 0.9f));
        var legacyCurveTag = new CompoundTag();
        legacyCurveTag.put("curves", legacySegments);
        var legacyCurve = new com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve();
        legacyCurve.deserializeNBT(provider, legacyCurveTag);
        if (legacyCurve.getCurves().getSegments().size() != 2 ||
                Math.abs(legacyCurve.getCurves().getCurveY(0f) - 0.3f) > 0.0001f ||
                Math.abs(legacyCurve.getCurves().getCurveY(1f) - 0.9f) > 0.0001f)
            throw new IllegalStateException("Legacy multi-segment shader curve was not restored");
        Photon.LOGGER.info("[legacy-multisegment-curve-smoke] restored both legacy shader curve segments");
        if (com.lowdragmc.photon.client.gameobject.particle.renderer.TileParticleRenderer.validParticleLight(-1) != 0xF000F0 ||
                com.lowdragmc.photon.client.gameobject.particle.renderer.TileParticleRenderer.validParticleLight(0) != 0 ||
                com.lowdragmc.photon.client.gameobject.particle.renderer.TileParticleRenderer.validParticleLight(0xA000B0) != 0xA000B0)
            throw new IllegalStateException("Uninitialized particle light did not use the original fullbright fallback");
        Photon.LOGGER.info("[particle-light-fallback-smoke] preserved valid light and restored uninitialized fullbright");
        if (com.lowdragmc.photon.client.gameobject.particle.renderer.TrailInterpolation.partialTime(0, 0, 0) != 0 ||
                com.lowdragmc.photon.client.gameobject.particle.renderer.TrailInterpolation.shouldInterpolate(1, 1, 2, 0.5f) ||
                com.lowdragmc.photon.client.gameobject.particle.renderer.TrailInterpolation.shouldInterpolate(0, 1, Float.NaN, 0.5f) ||
                !com.lowdragmc.photon.client.gameobject.particle.renderer.TrailInterpolation.shouldInterpolate(0, 2, 2, 0.5f) ||
                Math.abs(com.lowdragmc.photon.client.gameobject.particle.renderer.TrailInterpolation.factor(0, 2, 2, 0.5f) - 0.5f) > 0.0001f)
            throw new IllegalStateException("Trail interpolation did not guard invalid or equal lifetimes");
        Photon.LOGGER.info("[trail-interpolation-smoke] guarded invalid durations and equal tail lifetimes");
        if (TrailParticle.movementSampleCount(12f, 0.02f) != 64
                || TrailParticle.movementSampleCount(0.2f, 0.1f) != 2
                || TrailParticle.movementSampleCount(Float.NaN, 0.1f) != 1) {
            throw new IllegalStateException("Fast trail movement was not sub-sampled with a safe per-tick bound");
        }
        Photon.LOGGER.info("[trail-fast-movement-smoke] high-speed trail movement is sub-sampled with a 64-point cap");
        var colourConfig = new com.lowdragmc.photon.client.gameobject.emitter.trail.TrailConfig();
        colourConfig.setMinVertexDistance(0.1f);
        colourConfig.setColorOverTrail(new com.lowdragmc.photon.client.gameobject.emitter.data.number.color.Gradient(
                new com.lowdragmc.lowdraglib2.math.GradientColor(0xFFFF0000, 0xFF0000FF)));
        var colourEmitter = new TrailEmitter(colourConfig);
        var colourTrail = new TrailParticle(colourEmitter, colourConfig);
        boolean oldFastSimulation = com.lowdragmc.photon.client.PhotonParticleManager.isFastSimulation();
        com.lowdragmc.photon.client.PhotonParticleManager.setFastSimulation(true);
        try {
            colourTrail.updateTick(1f);
            colourEmitter.transform().localPosition(new org.joml.Vector3f(1f, 0f, 0f));
            colourTrail.updateTick(1f);
        } finally {
            com.lowdragmc.photon.client.PhotonParticleManager.setFastSimulation(oldFastSimulation);
        }
        var colourTails = colourTrail.getRawTails();
        if (colourTails.size() < 2 || colourTails.getColor(0).x() < 0.9f || colourTails.getColor(colourTails.size() - 1).z() < 0.9f) {
            throw new IllegalStateException("Trail colour gradient did not reach its rendered endpoints");
        }
        Photon.LOGGER.info("[trail-colour-smoke] red-to-blue trail gradient reaches both GPU/CPU point colours");
        var burstTrigger = EmissionSetting.Runtime.class.getDeclaredMethod("burstTriggers",
                EmissionSetting.Burst.class, int.class, net.minecraft.util.RandomSource.class, float.class, float.class);
        burstTrigger.setAccessible(true);
        var invalidBurst = new EmissionSetting.Burst();
        invalidBurst.interval = 0;
        invalidBurst.cycles = 0;
        int triggers = (int) burstTrigger.invoke(null, invalidBurst, 2, net.minecraft.util.RandomSource.create(0L), 0f, 3f);
        if (triggers != 6) throw new IllegalStateException("Zero-interval burst did not advance: " + triggers);
        Photon.LOGGER.info("[burst-interval-smoke] clamped invalid interval and emitted three bursts");
        // The sampled scene depth is addressed in framebuffer coordinates, but
        // the editor projection belongs to its smaller, offset viewport.
        var corrected = new org.joml.Matrix4f();
        CustomShaderMaterial.adjustLegacyScreenProjection(corrected, new ResourceLocation("ldlib2", "scan"),
                1920, 1080, 440, 180, 1080, 720);
        var center = corrected.transform(new org.joml.Vector4f(980f / 1920f * 2f - 1f,
                540f / 1080f * 2f - 1f, 0.4f, 1f));
        if (Math.abs(center.x) > 0.0001f || Math.abs(center.y) > 0.0001f ||
                Math.abs(center.z - 0.4f) > 0.0001f || Math.abs(center.w - 1f) > 0.0001f)
            throw new IllegalStateException("Legacy scan depth reconstruction used framebuffer NDC: " + center);
        var unaffected = new org.joml.Matrix4f();
        CustomShaderMaterial.adjustLegacyScreenProjection(unaffected, new ResourceLocation("example", "custom"),
                1920, 1080, 440, 180, 1080, 720);
        if (!unaffected.equals(new org.joml.Matrix4f()))
            throw new IllegalStateException("Viewport-aware custom shader projection was modified");
        var perspectiveInverse = new org.joml.Matrix4f().perspective(1.1f, 1.5f, 0.1f, 100f).invert();
        var originalInverse = new org.joml.Matrix4f(perspectiveInverse);
        CustomShaderMaterial.adjustLegacyScreenProjection(perspectiveInverse, new ResourceLocation("photon", "shield"),
                1920, 1080, 440, 180, 1080, 720);
        var actualView = perspectiveInverse.transform(new org.joml.Vector4f(710f / 1920f * 2f - 1f,
                720f / 1080f * 2f - 1f, 0.6f, 1f));
        var expectedView = originalInverse.transform(new org.joml.Vector4f(-0.5f, 0.5f, 0.6f, 1f));
        if (actualView.distance(expectedView) > 0.0001f)
            throw new IllegalStateException("Legacy shield depth reconstruction changed perspective: " + actualView);
        Photon.LOGGER.info("[legacy-viewport-projection-smoke] reconstructed viewport center and preserved custom shader");
        var legacyGradientData = new CompoundTag();
        legacyGradientData.put("a", floatList(0f, 1f, 1f, 0f));
        legacyGradientData.put("rgb", floatList(0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f));
        var legacyGradientTag = new CompoundTag();
        legacyGradientTag.put("gradientColor", legacyGradientData);
        var legacyGradient = new com.lowdragmc.photon.client.gameobject.emitter.data.number.color.Gradient();
        legacyGradient.deserializeNBT(provider, legacyGradientTag);
        var stops = legacyGradient.getGradientColor();
        if (stops.getAP().size() != 2 || stops.getRgbP().size() != 2 ||
                Math.abs(stops.getAP().get(1).y) > 0.0001f ||
                Math.abs(stops.getRgbP().get(0).y - 1f) > 0.0001f ||
                Math.abs(stops.getRgbP().get(1).w - 1f) > 0.0001f)
            throw new IllegalStateException("Legacy gradient stops were not restored");
        Photon.LOGGER.info("[legacy-gradient-smoke] restored legacy alpha and color stops");
        // The legacy tornado stores its custom shader's T attribute in this list.
        var gpuDataTag = new CompoundTag();
        gpuDataTag.putBoolean("_enable", true);
        var gpuChannels = new ListTag();
        gpuChannels.add(net.minecraft.nbt.StringTag.valueOf("addition_gpu_data.t"));
        gpuDataTag.put("additionalData", gpuChannels);
        var gpuSetting = new com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleConfig().additionalGPUDataSetting;
        // Toggle groups restore _enable in their deserializeNBT override before
        // delegating their fields to PersistedParser, as the project loader does.
        gpuSetting.deserializeNBT(provider, gpuDataTag);
        if (!gpuSetting.isEnable() || !gpuSetting.hasAttribs() || gpuSetting.attribFloats() != 1)
            throw new IllegalStateException("Legacy tornado particle T attribute did not load from NBT: enabled="
                    + gpuSetting.isEnable() + ", mask=" + gpuSetting.userMask() + ", floats=" + gpuSetting.attribFloats());
        Photon.LOGGER.info("[legacy-gpu-channel-smoke] restored tornado particle T attribute from legacy NBT");
        var legacyRenderState = new CompoundTag();
        legacyRenderState.putBoolean("cull", true);
        legacyRenderState.putBoolean("depthTest", true);
        legacyRenderState.putBoolean("depthMask", true);
        var legacySlot = new MaterialSetting(new com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial());
        com.lowdragmc.lowdraglib2.utils.PersistedParser.deserializeNBT(legacyRenderState, legacySlot, provider);
        if (!legacySlot.isEffectiveCull() || !legacySlot.isEffectiveDepthTest() || !legacySlot.isEffectiveDepthMask())
            throw new IllegalStateException("Legacy tornado material depth or culling state was not restored");
        Photon.LOGGER.info("[legacy-tornado-depth-smoke] restored authored depth and culling state");
        var scanRenderState = new CompoundTag();
        scanRenderState.putBoolean("depthTest", false);
        scanRenderState.putBoolean("depthMask", false);
        var scanSlot = new MaterialSetting(new com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial());
        scanSlot.setDepthTest(true).setDepthMask(true);
        com.lowdragmc.lowdraglib2.utils.PersistedParser.deserializeNBT(scanRenderState, scanSlot, provider);
        if (scanSlot.isEffectiveDepthTest() || scanSlot.isEffectiveDepthMask())
            throw new IllegalStateException("Legacy scan material depth settings were not restored");
        Photon.LOGGER.info("[legacy-scan-depth-smoke] restored disabled scan depth settings");
        var shieldRenderState = new CompoundTag();
        shieldRenderState.putBoolean("cull", false);
        shieldRenderState.putBoolean("depthTest", true);
        shieldRenderState.putBoolean("depthMask", false);
        var shieldSlot = new MaterialSetting(new com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial());
        shieldSlot.setCull(true).setDepthTest(false).setDepthMask(true);
        com.lowdragmc.lowdraglib2.utils.PersistedParser.deserializeNBT(shieldRenderState, shieldSlot, provider);
        if (shieldSlot.isEffectiveCull() || !shieldSlot.isEffectiveDepthTest() || shieldSlot.isEffectiveDepthMask())
            throw new IllegalStateException("Legacy shield material face and depth settings were not restored");
        Photon.LOGGER.info("[legacy-shield-state-smoke] restored two-sided shield depth settings");
        int collisionOrder = com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline
                .compareRenderPassOrder(0, 42, 1, 0, 42, 2);
        int reverseOrder = com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline
                .compareRenderPassOrder(0, 42, 2, 0, 42, 1);
        int extremeLayerOrder = com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline
                .compareRenderPassOrder(Integer.MIN_VALUE, 0, 1, Integer.MAX_VALUE, 0, 2);
        if (collisionOrder >= 0 || reverseOrder <= 0 || extremeLayerOrder >= 0)
            throw new IllegalStateException("Distinct render passes with colliding hashes were merged or misordered");
        class CollisionPass extends com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.PhotonFXRenderPass {
            CollisionPass() { super(null, null, null); }
            @Override public int layerOrder() { return 0; }
            @Override public int hashCode() { return 42; }
            @Override public boolean equals(Object other) { return this == other; }
            @Override protected void renderQueue(com.mojang.blaze3d.vertex.VertexConsumer buffer,
                    Collection<com.lowdragmc.photon.client.gameobject.particle.IParticle> particles,
                    net.minecraft.client.Camera camera, float partialTicks) {}
        }
        var collidingPasses = new java.util.TreeMap<com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.PhotonFXRenderPass, String>(
                com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline.makeRenderPassComparator());
        collidingPasses.put(new CollisionPass(), "first");
        collidingPasses.put(new CollisionPass(), "second");
        if (collidingPasses.size() != 2)
            throw new IllegalStateException("Render-pass tree dropped a pass with a colliding hash");
        Photon.LOGGER.info("[render-pass-order-smoke] preserved colliding render passes and extreme layers");
        String legacyQuad = "v 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nf 1 2 3 4\n";
        var quadMesh = com.lowdragmc.photon.client.gameobject.emitter.data.model.ObjMeshParser.parse(
                new java.io.ByteArrayInputStream(legacyQuad.getBytes(java.nio.charset.StandardCharsets.UTF_8)), false);
        if (!java.util.Arrays.equals(quadMesh.indices(), new int[]{0, 1, 2, 4, 5, 6}) ||
                quadMesh.vertexCount() != 8)
            throw new IllegalStateException("OBJ triangle vertex IDs diverged from the original: " + java.util.Arrays.toString(quadMesh.indices()));
        if (!java.util.Arrays.equals(quadMesh.samplingIds(), new int[]{0, 1, 2, 2, 2, 3, 0, 0}))
            throw new IllegalStateException("OBJ render vertices lost their shared sampling identity");
        float[] quadGeometry = quadMesh.geometry();
        float[] expectedXY = {0, 1, 0, 0, 1, 0, 1, 0, 1, 1, 0, 1};
        for (int i = 0; i < 6; i++) {
            int vertex = quadMesh.indices()[i];
            if (Math.abs(quadGeometry[vertex * 6] - expectedXY[i * 2]) > 0.0001f ||
                    Math.abs(quadGeometry[vertex * 6 + 1] - expectedXY[i * 2 + 1]) > 0.0001f)
                throw new IllegalStateException("OBJ quad diagonal or vertex order diverged at vertex " + i);
        }
        // The shipped shield shader changes displacement at gl_VertexID 255.
        // Exercise that boundary, not only the first pair of triangles.
        var manyFaces = new StringBuilder("v 0 0 0\nv 1 0 0\nv 0 1 0\n");
        for (int i = 0; i < 65; i++) manyFaces.append("f 1 2 3\n");
        var idBoundaryMesh = com.lowdragmc.photon.client.gameobject.emitter.data.model.ObjMeshParser.parse(
                new java.io.ByteArrayInputStream(manyFaces.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)), false);
        if (idBoundaryMesh.vertexCount() != 260 || idBoundaryMesh.indices()[63 * 3 + 2] != 254 ||
                idBoundaryMesh.indices()[64 * 3] != 256)
            throw new IllegalStateException("OBJ GPU vertex IDs crossed the shield shader boundary incorrectly");
        Photon.LOGGER.info("[legacy-obj-quad-smoke] preserved original OBJ quad diagonal");
        if (!com.lowdragmc.photon.client.gameobject.particle.renderer.TileParticleRenderer.flipBillboardU(
                ParticleRendererSetting.Mode.Billboard, com.lowdragmc.photon.client.gameobject.emitter.particle.FacingMode.DEFAULT)
                || com.lowdragmc.photon.client.gameobject.particle.renderer.TileParticleRenderer.flipBillboardU(
                ParticleRendererSetting.Mode.Billboard, com.lowdragmc.photon.client.gameobject.emitter.particle.FacingMode.LOOKAT_Y)
                || com.lowdragmc.photon.client.gameobject.particle.renderer.TileParticleRenderer.flipBillboardU(
                ParticleRendererSetting.Mode.Horizontal, com.lowdragmc.photon.client.gameobject.emitter.particle.FacingMode.DEFAULT))
            throw new IllegalStateException("Legacy billboard UV orientation was not preserved");
        Photon.LOGGER.info("[legacy-billboard-uv-smoke] restored the default camera-facing UV flip");
        var randomCurve = new com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.RandomCurve(
                0, 1, 0, 1, "", "", new com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.ECBCurves(
                0, 0.2f, 0.1f, 0.2f, 0.9f, 0.2f, 1, 0.2f),
                new com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.ECBCurves(
                0, 0.8f, 0.1f, 0.8f, 0.9f, 0.8f, 1, 0.8f));
        var randomSaved = randomCurve.serializeNBT(provider);
        var randomLoaded = new com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.RandomCurve();
        randomLoaded.deserializeNBT(provider, randomSaved);
        if (Math.abs(randomLoaded.get(0.5f, () -> 0f) - 0.2f) > 0.0001f ||
                Math.abs(randomLoaded.get(0.5f, () -> 1f) - 0.8f) > 0.0001f)
            throw new IllegalStateException("Random shader curves changed on reload: " + randomSaved);
        // Legacy tornado materials use normalized multi-segment curves even when their bounds are zero.
        var authoredTornadoCurve = new com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve(
                0, 0, 0, 0, "", "", new com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.ECBCurves(
                0, 0.2f, 0.1f, 0.2f, 0.4f, 0.4f, 0.5f, 0.4f,
                0.5f, 0.4f, 0.6f, 0.4f, 0.9f, 0.9f, 1, 0.9f));
        var savedCurves = new com.lowdragmc.photon.client.gameobject.emitter.data.material.CurveTexture(8, 2);
        savedCurves.addCurve(authoredTornadoCurve);
        var restoredTornadoCurves = new com.lowdragmc.photon.client.gameobject.emitter.data.material.CurveTexture(8, 2);
        restoredTornadoCurves.deserializeNBT(provider, savedCurves.serializeNBT(provider));
        var pixels = restoredTornadoCurves.getCurveTexture().getPixels();
        int first = pixels.getPixelRGBA(0, 0) & 255;
        int last = pixels.getPixelRGBA(7, 0) & 255;
        if (first != 51 || last != 229)
            throw new IllegalStateException("Legacy multi-segment shader curve changed while loading: " + first + ", " + last);
        savedCurves.close();
        restoredTornadoCurves.close();
        Photon.LOGGER.info("[legacy-tornado-curve-smoke] restored and uploaded a multi-segment dissolve curve");
        var firstCurve = new com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve();
        var secondCurve = new com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve();
        secondCurve.getCurves().getSegments().get(0).p0.y = 0.9f;
        var curveRemoval = new com.lowdragmc.photon.client.gameobject.emitter.data.material.CurveTexture(8, 2);
        curveRemoval.addCurve(firstCurve);
        curveRemoval.addCurve(secondCurve);
        int curveBefore = curveRemoval.getCurveTexture().getPixels().getPixelRGBA(0, 0);
        curveRemoval.removeCurve(firstCurve);
        int curveAfter = curveRemoval.getCurveTexture().getPixels().getPixelRGBA(0, 0);
        curveRemoval.close();
        if (curveBefore == curveAfter) throw new IllegalStateException("Removing a curve did not refresh its texture");
        var firstGradient = new com.lowdragmc.lowdraglib2.math.GradientColor(0xffff0000);
        var secondGradient = new com.lowdragmc.lowdraglib2.math.GradientColor(0xff0000ff);
        var gradientRemoval = new com.lowdragmc.photon.client.gameobject.emitter.data.material.GradientTexture(8, 2);
        gradientRemoval.addGradient(firstGradient);
        gradientRemoval.addGradient(secondGradient);
        int gradientBefore = gradientRemoval.getGradientTexture().getPixels().getPixelRGBA(0, 0);
        gradientRemoval.removeGradient(firstGradient);
        int gradientAfter = gradientRemoval.getGradientTexture().getPixels().getPixelRGBA(0, 0);
        gradientRemoval.close();
        if (gradientBefore == gradientAfter) throw new IllegalStateException("Removing a gradient did not refresh its texture");
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
        var tangentMaterial = new CustomShaderMaterial(new ResourceLocation("photon", "ci/tangent_smoke"));
        tangentMaterial.recompile();
        if (tangentMaterial.isCompiledError()
                || !(tangentMaterial.getShader(MaterialContext.PARTICLE_MODEL_INSTANCE_TANGENT) instanceof LDShaderInstance tangentVariant)
                || !tangentVariant.defines.containsAll(Set.of("PARTICLE_MODEL_INSTANCE", "PHOTON_TANGENT"))) {
            throw new IllegalStateException("Photon 26.2 tangent shader inputs are incompatible");
        }
        Photon.LOGGER.info("[tangent-abi-smoke] compiled original vec2 UV and vec4 normal tangent inputs");
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
        var nestedSource = new JsonModelSource(new ResourceLocation("ldlib2", "block/ci_nested_obj"));
        var shadowedResource = minecraft.getResourceManager().getResource(
                new ResourceLocation("ldlib2", "models/obj/ci_nested_obj.obj")).orElseThrow();
        try (var reader = shadowedResource.openAsReader()) {
            if (!reader.readLine().equals("v 7 0 0"))
                throw new IllegalStateException("Conflicting resource-pack OBJ fixture is unavailable");
        }
        var nestedMesh = nestedSource.getMesh();
        if (nestedMesh == null || nestedMesh.vertexCount() < 3)
            throw new IllegalStateException("Nested LDLib2 OBJ did not produce a Photon mesh");
        float[] nestedGeometry = nestedMesh.geometry();
        float[] nestedAttributes = nestedMesh.attributes();
        if (Math.abs(nestedGeometry[0] + 0.5f) > 0.0001f ||
                Math.abs(nestedGeometry[1] + 0.5f) > 0.0001f ||
                Math.abs(nestedGeometry[2] + 0.5f) > 0.0001f ||
                Math.abs(nestedAttributes[1] - 1f) > 0.0001f)
            throw new IllegalStateException("Nested OBJ JSON lost its model-space offset or flip_v setting");
        Photon.LOGGER.info("[legacy-obj-json-smoke] preserved centered model position and flipped V");
        var nestedObj = LDLib2.getAssetsDir().toPath().resolve("ldlib2/assets/ldlib2/models/obj/ci_nested_obj.obj");
        Files.writeString(nestedObj, "v 0 0 0\nv 2 0 0\nv 0 2 0\nv 2 2 0\nf 1 2 3\nf 2 4 3\n");
        Files.setLastModifiedTime(nestedObj, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5000));
        com.lowdragmc.photon.client.gameobject.emitter.data.model.PhotonMeshCache.INSTANCE.pollFileChanges();
        var reloadedNestedMesh = nestedSource.getMesh();
        if (reloadedNestedMesh == nestedMesh || reloadedNestedMesh.vertexCount() <= nestedMesh.vertexCount())
            throw new IllegalStateException("Editing a nested OBJ did not invalidate its JSON model");
        var samplingMesh = new MeshData(nestedSource);
        if (samplingMesh.getVertices().size() != 4 || samplingMesh.getEdges().size() != 5 ||
                samplingMesh.getTriangles().size() != 2 || reloadedNestedMesh.vertexCount() != 8)
            throw new IllegalStateException("OBJ sampling topology did not preserve shared vertices and edges");
        Photon.LOGGER.info("[obj-sampling-topology-smoke] preserved eight render vertices and four sampling vertices");
        Photon.LOGGER.info("[nested-obj-model-smoke] loaded nested LDLib2 OBJ with {} vertices", nestedMesh.vertexCount());
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
        emitter.config.renderer.setUseGPUInstance(false);
        emitter.config.renderer.setVertexSortingMode(com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting.SortMode.DISTANCE);
        emitter.config.renderer.setModelPivot(new org.joml.Vector3f(0, 0.5f, 0));
        emitter.config.renderer.getMaterials().clear();
        var visibleMaterial = new MaterialSetting(new CustomShaderMaterial(new ResourceLocation("photon", "ci/shield_cpu_smoke")));
        visibleMaterial.setCull(false);
        visibleMaterial.setDepthTest(true);
        visibleMaterial.setDepthMask(false);
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
        tornadoModel.config.renderer.setModelPivot(new org.joml.Vector3f(0.125f, 0.25f, 0));
        tornadoModel.config.renderer.setVertexSortingMode(com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting.SortMode.DISTANCE);
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
        tornadoBurst.setCount(NumberFunction.constant(2));
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
        billboardMaterial.setCull(true);
        billboardMaterial.setDepthTest(false);
        billboard.config.renderer.getMaterials().add(billboardMaterial);
        var billboardBurst = new EmissionSetting.Burst();
        billboardBurst.time = 0;
        billboardBurst.cycles = 1;
        billboardBurst.setCount(NumberFunction.constant(1));
        billboardBurst.interval = 4;
        billboard.config.emission.getBursts().add(billboardBurst);
        source.getFx().getFxData().objects().add(billboard);

        var araTrail = new com.lowdragmc.photon.client.gameobject.emitter.aratrail.AraTrailEmitter();
        araTrail.config.setDuration(150);
        araTrail.config.setLooping(false);
        araTrail.config.time = 10f;
        araTrail.config.minDistance = 0.02f;
        araTrail.config.thickness = 0.5f;
        araTrail.config.renderer.getMaterials().get(0).setCull(false).setDepthTest(false);
        araTrail.transform().localPosition(new org.joml.Vector3f(-2.5f, 3, 0));
        source.getFx().getFxData().objects().add(araTrail);

        var trail = new TrailEmitter();
        trail.config.setDuration(150);
        trail.config.setLooping(false);
        trail.config.setTime(150);
        trail.config.setMinVertexDistance(0.02f);
        trail.config.renderer.setUseGPUInstance(true);
        trail.config.renderer.getMaterials().clear();
        var trailShader = new CustomShaderMaterial(new ResourceLocation("photon", "ci/trail_smoke"));
        trailShader.recompile();
        if (trailShader.isCompiledError()) throw new IllegalStateException(trailShader.getCompiledErrorMessage());
        if (!(trailShader.getShader(MaterialContext.TRAIL_INSTANCE) instanceof LDShaderInstance trailVariant)
                || !trailVariant.defines.contains("TRAIL_INSTANCE")) {
            throw new IllegalStateException("Arrow-trail custom shader failed its GPU trail variant");
        }
        var trailMaterial = new MaterialSetting(trailShader);
        trailMaterial.setCull(false);
        trailMaterial.setDepthTest(false);
        trail.config.renderer.getMaterials().add(trailMaterial);
        trail.transform().localPosition(new org.joml.Vector3f(-2.5f, 2, 0));
        source.getFx().getFxData().objects().add(trail);

        // Old arrow_trail projects omit the GPU toggle and restore a texture sampler from shaderData.
        var cpuTrail = new TrailEmitter();
        cpuTrail.config.setDuration(150);
        cpuTrail.config.setLooping(false);
        cpuTrail.config.setTime(150);
        cpuTrail.config.setMinVertexDistance(0.02f);
        cpuTrail.config.setWidthOverTrail(NumberFunction.constant(0.4f));
        cpuTrail.config.renderer.setUseGPUInstance(false);
        cpuTrail.config.renderer.getMaterials().clear();
        var cpuShader = new CustomShaderMaterial(new ResourceLocation("photon", "ci/trail_cpu_smoke"));
        var cpuData = new CompoundTag();
        var cpuShaderData = new CompoundTag();
        var samplers = new CompoundTag();
        var tailTexture = new CompoundTag();
        tailTexture.putString("type", "texture");
        tailTexture.putString("resource", "minecraft:textures/block/stone.png");
        samplers.put("TailTexture", tailTexture);
        cpuShaderData.put("samplers", samplers);
        var cpuUniforms = new CompoundTag();
        cpuUniforms.put("colorHDR1", floatList(0.8f, 0.4f, 0.2f, 3.0f));
        cpuUniforms.put("colorHDR2", floatList(0.2f, 0.6f, 0.9f, 2.0f));
        cpuShaderData.put("uniforms", cpuUniforms);
        cpuData.put("shaderData", cpuShaderData);
        cpuShader.deserializeAdditionalNBT(cpuData, provider);
        if (cpuShader.isCompiledError()) throw new IllegalStateException(cpuShader.getCompiledErrorMessage());
        assertLegacyHdr(cpuShader, 0.8f, 3.0f);
        Field holderField = CustomShaderMaterial.class.getDeclaredField("shaderHolder");
        holderField.setAccessible(true);
        var holder = (com.lowdragmc.lowdraglib2.client.shader.LDShaderHolder) holderField.get(cpuShader);
        holder.setHDRUniform("colorHDR1", new com.lowdragmc.lowdraglib2.math.HDRColor(0.4f, 0.3f, 0.2f, 1.0f, 4.0f));
        assertLegacyHdr(cpuShader, 0.4f, 4.0f);
        var savedCpuData = (CompoundTag) cpuShader.serializeAdditionalNBT(provider);
        if (!savedCpuData.getCompound("shaderData").getCompound("samplers").getCompound("TailTexture")
                .getString("resource").equals("minecraft:textures/block/stone.png")) {
            throw new IllegalStateException("Legacy trail sampler was lost when saving the material");
        }
        cpuShader = new CustomShaderMaterial(new ResourceLocation("photon", "ci/trail_cpu_smoke"));
        cpuShader.deserializeAdditionalNBT(savedCpuData, provider);
        assertLegacyHdr(cpuShader, 0.4f, 4.0f);
        Photon.LOGGER.info("[legacy-hdr-smoke] preserved legacy HDR values through editing and material reload");
        // Match the sample FXP's external LDLib material wrapper and legacy shaderData layout.
        var materialFile = LDLib2.getAssetsDir().toPath().resolve("ldlib2/resources/global/ci_tail.material.nbt");
        Files.createDirectories(materialFile.getParent());
        var oldMaterialData = new CompoundTag();
        oldMaterialData.putString("shaderLocation", "photon:ci/trail_cpu_smoke");
        oldMaterialData.put("_additional", cpuData.copy());
        var oldMaterial = new CompoundTag();
        oldMaterial.putString("type", "custom_shader");
        oldMaterial.put("data", oldMaterialData);
        var resourceFile = new CompoundTag();
        resourceFile.putString("type", "material");
        resourceFile.put("data", oldMaterial);
        NbtIo.write(resourceFile, materialFile.toFile());
        var resourceReference = new com.lowdragmc.photon.client.gameobject.emitter.data.material.UIResourceMaterial(
                new com.lowdragmc.lowdraglib2.editor.resource.FilePath(
                        new File("./ldlib2/assets/ldlib2/resources/global/ci_tail.material.nbt")));
        var cpuMaterial = new MaterialSetting(resourceReference);
        cpuMaterial.setCull(false);
        cpuMaterial.setDepthTest(false);
        cpuTrail.config.renderer.getMaterials().add(cpuMaterial);
        cpuTrail.transform().localPosition(new org.joml.Vector3f(2.5f, 2, 0));
        source.getFx().getFxData().objects().add(cpuTrail);

        var gpuBillboard = new ParticleEmitter();
        gpuBillboard.transform().localPosition(new org.joml.Vector3f(2, 3, 0));
        gpuBillboard.config.setLooping(false);
        gpuBillboard.config.setStartLifetime(NumberFunction.constant(70));
        gpuBillboard.config.setStartSpeed(NumberFunction.constant(0));
        gpuBillboard.config.setStartSize(new com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3(0.8f, 0.8f, 0.8f));
        gpuBillboard.config.emission.setEmissionRate(NumberFunction.constant(0));
        gpuBillboard.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Billboard);
        gpuBillboard.config.renderer.setUseGPUInstance(true);
        gpuBillboard.config.renderer.getMaterials().clear();
        var gpuBillboardMaterial = new MaterialSetting(new com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial());
        gpuBillboardMaterial.setCull(true);
        gpuBillboardMaterial.setDepthTest(false);
        gpuBillboard.config.renderer.getMaterials().add(gpuBillboardMaterial);
        var gpuBillboardBurst = new EmissionSetting.Burst();
        gpuBillboardBurst.time = 0;
        gpuBillboardBurst.cycles = 1;
        gpuBillboardBurst.setCount(NumberFunction.constant(1));
        gpuBillboardBurst.interval = 4;
        gpuBillboard.config.emission.getBursts().add(gpuBillboardBurst);
        source.getFx().getFxData().objects().add(gpuBillboard);

        projectFile = Files.createTempFile("photon-editor-v3-", ".fxproj");
        FXProject.TYPE.saveProjectToFile(source, projectFile.toFile());
        var encoded = NbtIo.read(projectFile.toFile());
        var fixtureConfig = encoded.getCompound("data").getCompound("fx").getCompound("fxData")
                .getList("fxObjects", Tag.TAG_COMPOUND).getCompound(0).getCompound("data").getCompound("config");
        var tornadoFixtureConfig = encoded.getCompound("data").getCompound("fx").getCompound("fxData")
                .getList("fxObjects", Tag.TAG_COMPOUND).getCompound(1).getCompound("data").getCompound("config");
        var trailFixtureConfig = encoded.getCompound("data").getCompound("fx").getCompound("fxData")
                .getList("fxObjects", Tag.TAG_COMPOUND).getCompound(5).getCompound("data").getCompound("config");
        var widthCurve = new CompoundTag();
        var widthData = new CompoundTag();
        var widthSegments = new ListTag();
        widthSegments.add(floatList(0f, 0f, 0.1f, 0f, 0.72f, 1f, 1f, 1f));
        widthData.put("curves", widthSegments);
        widthData.putFloat("min", 0f);
        widthData.putFloat("max", Float.MAX_VALUE);
        widthData.putFloat("lower", 0f);
        widthData.putFloat("upper", 0.2f);
        widthData.putBoolean("lockControlPoint", true);
        widthData.putString("xAxis", "trail position");
        widthData.putString("yAxis", "width");
        widthCurve.putString("type", "curve");
        widthCurve.put("data", widthData);
        trailFixtureConfig.put("widthOverTrail", widthCurve);
        Photon.LOGGER.info("[tornado-render-smoke] saved GPU channel setting before migration: {}",
                tornadoFixtureConfig.getCompound("additionalGPUDataSetting"));
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
        var fixedData = com.lowdragmc.photon.client.gameobject.emitter.data.fixer.PhotonFXProjectDataFixer.INSTANCE
                .applyFixes(3, FXProject.VERSION, encoded.getCompound("data"));
        var fixedTornadoSetting = fixedData.getCompound("fx").getCompound("fxData")
                .getList("fxObjects", Tag.TAG_COMPOUND).getCompound(1).getCompound("data")
                .getCompound("config").getCompound("additionalGPUDataSetting");
        Photon.LOGGER.info("[tornado-render-smoke] setting after v3 datafix: {}", fixedTornadoSetting);

        var loaded = (FXProject) FXProject.TYPE.loadProjectFromFile(projectFile.toFile());
        if (loaded.getFx().getFxData().objects().size() != 7
                || !(loaded.getFx().getFxData().objects().get(0) instanceof ParticleEmitter restored)
                || !(loaded.getFx().getFxData().objects().get(1) instanceof ParticleEmitter restoredTornado)
                || restored.config.renderer.getMaterials().size() != 1
                || restored.config.emission.getBursts().size() != 1
                || restored.config.subEmitters.getEmitters().size() != 1
                || restoredTornado.config.renderer.getMaterials().size() != 1
                || restoredTornado.config.emission.getBursts().size() != 1
                || !(loaded.getFx().getFxData().objects().get(2) instanceof ParticleEmitter)
                || !(loaded.getFx().getFxData().objects().get(3) instanceof com.lowdragmc.photon.client.gameobject.emitter.aratrail.AraTrailEmitter)
                || !(loaded.getFx().getFxData().objects().get(4) instanceof TrailEmitter)
                || !(loaded.getFx().getFxData().objects().get(5) instanceof TrailEmitter)
                || !(loaded.getFx().getFxData().objects().get(6) instanceof ParticleEmitter)
                || restored.config.emission.getBursts().get(0).cycles != 1) {
            throw new IllegalStateException("v3 managed lists were not restored: " + loaded.serializeProject(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry()));
        }
        if (!(restored.config.renderer.getModelSource() instanceof ObjModelSource sphere)
                || !sphere.getModelLocation().equals(new ResourceLocation("photon", "models/sphere.obj"))
                || sphere.getMesh().vertexCount() < 3) {
            throw new IllegalStateException("Legacy photon:block/sphere did not resolve to the bundled sphere OBJ");
        }
        var shieldSlotLoaded = restored.config.renderer.getMaterials().get(0);
        if (restored.config.renderer.isUseGPUInstance() ||
                restored.config.renderer.getVertexSortingMode() != com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting.SortMode.DISTANCE ||
                Math.abs(restored.config.renderer.getModelPivot().y - 0.5f) > 0.0001f ||
                shieldSlotLoaded.isEffectiveCull() || !shieldSlotLoaded.isEffectiveDepthTest() || shieldSlotLoaded.isEffectiveDepthMask())
            throw new IllegalStateException("Shield-style model settings changed while loading the project");
        var restoredCpuTrail = (TrailEmitter) loaded.getFx().getFxData().objects().get(5);
        var restoredWidth = restoredCpuTrail.config.getWidthOverTrail().get(0.8f, () -> 0.5f).floatValue();
        if (!Float.isFinite(restoredWidth) || restoredWidth <= 0.01f) {
            throw new IllegalStateException("Legacy trail width curve did not restore: " + restoredWidth);
        }
        Photon.LOGGER.info("[legacy-trail-curve-smoke] restored a nonzero width from the legacy curve: {}", restoredWidth);
        var externalMaterial = restoredCpuTrail.config.renderer.getMaterials().get(0).getMaterial();
        if (!(externalMaterial instanceof com.lowdragmc.photon.client.gameobject.emitter.data.material.UIResourceMaterial reference)
                || !(reference.getRawMaterial() instanceof CustomShaderMaterial restoredCpuShader)) {
            throw new IllegalStateException("Legacy trail did not resolve its external custom shader material");
        }
        assertLegacyHdr(restoredCpuShader, 0.8f, 3.0f);
        var restoredExternalData = (CompoundTag) restoredCpuShader.serializeAdditionalNBT(provider);
        if (!restoredExternalData.getCompound("shaderData").getCompound("samplers").getCompound("TailTexture")
                .getString("resource").equals("minecraft:textures/block/stone.png")) {
            throw new IllegalStateException("External legacy trail material lost its texture sampler");
        }
        Photon.LOGGER.info("[external-trail-material-smoke] loaded the legacy file-backed trail material");
        Photon.LOGGER.info("[legacy-model-alias-smoke] photon:block/sphere resolved to bundled OBJ with {} vertices",
                sphere.getMesh().vertexCount());
        long tChannel = PhotonGpuChannels.CHANNELS.stream()
                .filter(channel -> channel.id().equals("addition_gpu_data.t"))
                .findFirst().orElseThrow().bit();
        Photon.LOGGER.info("[tornado-render-smoke] loaded enabled channel ids: {}",
                enabledChannelIds(restoredTornado));
        var restoredModel = restoredTornado.config.renderer.getModelSource();
        var restoredTornadoMaterial = restoredTornado.config.renderer.getMaterials().get(0).getMaterial();
        boolean objLoaded = restoredModel instanceof JsonModelSource obj && obj.getMesh() != null && obj.getMesh().vertexCount() >= 3;
        boolean tChannelEnabled = restoredTornado.config.additionalGPUDataSetting.isEnable()
                && (restoredTornado.config.additionalGPUDataSetting.userMask() & tChannel) != 0;
        boolean shaderLoaded = restoredTornadoMaterial instanceof CustomShaderMaterial shader
                && !shader.isCompiledError() && shader.curveTexture.getCurveTexture() != null;
        if (restoredTornadoMaterial instanceof CustomShaderMaterial shader) smokeScreenShader = shader;
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
        Photon.LOGGER.info("[tornado-render-smoke] loaded LDLib2 OBJ with legacy UV/brightness attributes, location-9 T data, GPU shader variant, and SamplerCurve");

        previousScreen = minecraft.screen;
        editor = new FXEditor();
        minecraft.setScreen(new ModularUIScreen(new ModularUI(UI.of(editor)), Component.literal("Photon CI editor")));
        editor.loadProject(loaded, projectFile.toFile());
        editor.sceneView.fxObjectInfoView.setDisplay(false);
        editor.sceneView.fxObjectAnimationView.setDisplay(false);
        if (editor.getCurrentProject() != loaded || editor.runtime == null || editor.runtime.objects.size() < 2) {
            throw new IllegalStateException("Editor did not initialize the loaded project runtime");
        }
        smokeTrail = editor.runtime.objects.values().stream().filter(TrailEmitter.class::isInstance)
                .map(TrailEmitter.class::cast).filter(t -> t.config.renderer.isUseGPUInstance()).findFirst()
                .orElseThrow(() -> new IllegalStateException("Editor runtime did not instantiate the moving trail"));
        smokeCpuTrail = editor.runtime.objects.values().stream().filter(TrailEmitter.class::isInstance)
                .map(TrailEmitter.class::cast).filter(t -> !t.config.renderer.isUseGPUInstance()).findFirst()
                .orElseThrow(() -> new IllegalStateException("Editor runtime did not instantiate the CPU trail"));
        smokeAraTrail = editor.runtime.objects.values().stream()
                .filter(com.lowdragmc.photon.client.gameobject.emitter.aratrail.AraTrailEmitter.class::isInstance)
                .map(com.lowdragmc.photon.client.gameobject.emitter.aratrail.AraTrailEmitter.class::cast).findFirst()
                .orElseThrow(() -> new IllegalStateException("Editor runtime did not instantiate the AraTrail"));
        Photon.LOGGER.info("[fxproj-editor-smoke] initialized v3 project with materials, bursts and sub-emitters");
    }

    /** Photon 1's original FX wrapper and enabled emission modules survive conversion. */
    private static void verifyPhoton1Compatibility() throws Exception {
        var sparseTransform = com.lowdragmc.photon.client.fx.compat.MapperUtils.mapTransformTag(new CompoundTag());
        if (!com.lowdragmc.photon.client.fx.FXRuntime.ROOT_UUID.toString().equals(sparseTransform.getString("_parentId"))
                || sparseTransform.getList("localRotation", Tag.TAG_FLOAT).size() != 4
                || sparseTransform.getList("localRotation", Tag.TAG_FLOAT).getFloat(3) != 1f
                || sparseTransform.getList("localScale", Tag.TAG_FLOAT).size() != 3
                || sparseTransform.getList("localPosition", Tag.TAG_FLOAT).size() != 3) {
            throw new IllegalStateException("Sparse Photon 1 transform fields were not safely defaulted");
        }
        java.util.UUID.fromString(sparseTransform.getString("id"));
        var delayedEmitter = new CompoundTag();
        delayedEmitter.putInt("startDelay", 300);
        if (com.lowdragmc.photon.client.fx.compat.TrailEmitterMapper.mapTrailConfig(delayedEmitter).getInt("startDelay") != 300
                || com.lowdragmc.photon.client.fx.compat.BeamEmitterMapper.mapBeamConfig(delayedEmitter).getInt("startDelay") != 300)
            throw new IllegalStateException("Photon 1 trail or beam delay was lost");
        // Photon 1's pre-smoothing trail reader scales Constant widths, including the default,
        // but does not scale curve output bounds or RandomConstant widths.
        var oldTrail = new CompoundTag();
        var oldWidth = new CompoundTag();
        oldWidth.putString("_type", "Constant"); oldWidth.putInt("number", 2);
        oldTrail.put("widthOverTrail", oldWidth);
        var migratedTrail = com.lowdragmc.photon.client.fx.compat.TrailEmitterMapper.mapTrailConfig(oldTrail);
        var loadedTrailConfig = new com.lowdragmc.photon.client.gameobject.emitter.trail.TrailConfig();
        loadedTrailConfig.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(), migratedTrail);
        if (Math.abs(loadedTrailConfig.getWidthOverTrail().get(0f, () -> 0.5f).floatValue() - 0.2f) > 0.0001f
                || oldWidth.getInt("number") != 2)
            throw new IllegalStateException("Old constant trail width was not migrated without changing its source");
        var reloadedTrailConfig = new com.lowdragmc.photon.client.gameobject.emitter.trail.TrailConfig();
        reloadedTrailConfig.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(),
                loadedTrailConfig.serializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry()));
        if (Math.abs(reloadedTrailConfig.getWidthOverTrail().get(0f, () -> 0.5f).floatValue() - 0.2f) > 0.0001f)
            throw new IllegalStateException("Old trail width was scaled twice after Photon 2 save/reload");
        oldTrail.putBoolean("smoothInterpolation", false);
        var modernWidth = com.lowdragmc.photon.client.fx.compat.TrailEmitterMapper.mapTrailConfig(oldTrail);
        if (modernWidth.getCompound("widthOverTrail").getCompound("data").getInt("number") != 2)
            throw new IllegalStateException("Modern Photon 1 trail width was incorrectly scaled");
        var attachedTrail = new CompoundTag();
        var oldAttachedConfig = new CompoundTag(); oldAttachedConfig.put("widthOverTrail", oldWidth.copy());
        attachedTrail.put("config", oldAttachedConfig);
        var attachedWidth = com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapTrailsTag(attachedTrail)
                .getCompound("config").getCompound("widthOverTrail").getCompound("data").getFloat("number");
        if (Math.abs(attachedWidth - 0.2f) > 0.0001f)
            throw new IllegalStateException("Particle-attached old trail width was not migrated");
        var nestedSparse = new CompoundTag(); nestedSparse.put("config", new CompoundTag());
        var attachedDefault = com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapTrailsTag(nestedSparse)
                .getCompound("config").getCompound("widthOverTrail").getCompound("data").getFloat("number");
        if (Math.abs(attachedDefault - 0.05f) > 0.0001f)
            throw new IllegalStateException("Attached old trail lost its own 0.5 width default");
        // The still older TrailsSetting format stores material and trail values next to ratio,
        // optionally overriding a nested config. Its flat width must not be divided by ten.
        var flatTrails = new CompoundTag(); flatTrails.putBoolean("enable", true);
        var flatMaterial = new CompoundTag(); var flatTexture = new CompoundTag();
        flatTexture.putString("_type", "TextureMaterial");
        flatTexture.putString("texture", "minecraft:textures/block/stone.png");
        flatTexture.putFloat("discardThreshold", 0.01f); flatMaterial.put("material", flatTexture);
        flatMaterial.putBoolean("depthTest", false); flatTrails.put("material", flatMaterial);
        var flatWidth = new CompoundTag(); flatWidth.putString("_type", "Constant"); flatWidth.putFloat("number", 3f);
        flatTrails.put("widthOverTrail", flatWidth);
        var flatColor = new CompoundTag(); flatColor.putString("_type", "Color"); flatColor.putInt("number", 0xff123456);
        flatTrails.put("colorOverTrail", flatColor);
        flatTrails.putString("uvMode", "Tile"); flatTrails.putFloat("minimumVertexDistance", 0.25f);
        flatTrails.put("config", oldAttachedConfig.copy());
        var flatTrailParticleTag = new CompoundTag(); flatTrailParticleTag.putString("_type", "particle"); flatTrailParticleTag.put("trails", flatTrails);
        var flatObject = com.lowdragmc.photon.client.gameobject.IFXObject.deserializeWrapper(
                com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapParticleEmitter(flatTrailParticleTag));
        if (!(flatObject instanceof ParticleEmitter flatTrailEmitter))
            throw new IllegalStateException("Flat Photon 1 trail did not deserialize");
        var trailCfg = flatTrailEmitter.config.trails.config;
        if (!flatTrailEmitter.config.trails.isEnable()
                || Math.abs(trailCfg.getWidthOverTrail().get(0f, () -> 0.5f).floatValue() - 3f) > 0.0001f
                || trailCfg.getColorOverTrail().get(0f, () -> 0.5f).intValue() != 0xff123456
                || !trailCfg.getUvMode().name().equals("Tile") || Math.abs(trailCfg.getMinVertexDistance() - 0.25f) > 0.0001f
                || trailCfg.renderer.getMaterials().size() != 1
                || trailCfg.renderer.getMaterials().get(0).isDepthTest()
                || !(trailCfg.renderer.getMaterials().get(0).getMaterial() instanceof com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial flatTex)
                || !flatTex.getTexture().equals(new ResourceLocation("minecraft:textures/block/stone.png")))
            throw new IllegalStateException("Flat legacy attached trail lost material, width, color, UV or distance");
        var flatReload = com.lowdragmc.photon.client.gameobject.IFXObject.deserializeWrapper(flatTrailEmitter.serializeWrapper());
        if (!(flatReload instanceof ParticleEmitter flatAgain)
                || Math.abs(flatAgain.config.trails.config.getWidthOverTrail().get(0f, () -> 0.5f).floatValue() - 3f) > 0.0001f
                || flatAgain.config.trails.config.renderer.getMaterials().size() != 1)
            throw new IllegalStateException("Flat legacy trail did not survive save and reload");
        Photon.LOGGER.info("[photon1-flat-trail-smoke] restored flat trail material, parameters and nested override");
        var sparseTrail = com.lowdragmc.photon.client.fx.compat.TrailEmitterMapper.mapTrailConfig(new CompoundTag());
        if (Math.abs(sparseTrail.getCompound("widthOverTrail").getCompound("data").getFloat("number") - 0.02f) > 0.0001f)
            throw new IllegalStateException("Old sparse trail lost the Photon 1 migrated default");
        var oldCurveWidth = new CompoundTag(); oldCurveWidth.putString("_type", "Curve");
        oldCurveWidth.putFloat("min", 0); oldCurveWidth.putFloat("max", 10);
        oldCurveWidth.putFloat("lower", 1); oldCurveWidth.putFloat("upper", 2);
        var curveTrail = new CompoundTag(); curveTrail.put("widthOverTrail", oldCurveWidth);
        var curveData = com.lowdragmc.photon.client.fx.compat.TrailEmitterMapper.mapTrailConfig(curveTrail)
                .getCompound("widthOverTrail").getCompound("data");
        if (curveData.getFloat("max") != 1f || curveData.getFloat("lower") != 1f || curveData.getFloat("upper") != 2f)
            throw new IllegalStateException("Old trail curve migration changed its sampled output bounds");
        Photon.LOGGER.info("[photon1-old-trail-smoke] migrated legacy constant widths exactly once and preserved curve output");
        var oldAtlas = new CompoundTag();
        oldAtlas.putString("_type", "BlockTextureSheetMaterial");
        oldAtlas.putString("texture", "minecraft:textures/atlas/blocks.png");
        oldAtlas.putFloat("discardThreshold", 0.01f);
        var mappedAtlas = com.lowdragmc.photon.client.fx.compat.MapperUtils.mapTypedValue(oldAtlas, false);
        if (!mappedAtlas.getString("type").equals("texture")
                || !mappedAtlas.getCompound("data").getString("texture").equals("minecraft:textures/atlas/blocks.png"))
            throw new IllegalStateException("Photon 1 block atlas material was not retained");
        var oldMesh = new CompoundTag();
        var oldVertices = new ListTag();
        for (float f : new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0}) oldVertices.add(net.minecraft.nbt.FloatTag.valueOf(f));
        oldMesh.put("vertices", oldVertices);
        var oldEdges = new ListTag();
        for (float f : new float[]{0, 0, 0, 1, 0, 0}) oldEdges.add(net.minecraft.nbt.FloatTag.valueOf(f));
        oldMesh.put("edges", oldEdges);
        oldMesh.put("triangles", oldVertices.copy());
        var oldShape = new CompoundTag();
        oldShape.putString("_type", "mesh");
        oldShape.put("mesh", oldMesh);
        var mappedShape = com.lowdragmc.photon.client.fx.compat.MapperUtils.mapShape(oldShape);
        var decodedShape = com.lowdragmc.photon.client.gameobject.emitter.data.shape.IShape.deserializeWrapper(mappedShape);
        if (!(decodedShape instanceof com.lowdragmc.photon.client.gameobject.emitter.data.shape.Mesh decodedMesh)
                || decodedMesh.getMeshData().getVertices().size() != 3)
            throw new IllegalStateException("Photon 1 mesh was not restored through the shape registry");
        var mesh = new com.lowdragmc.photon.client.gameobject.emitter.data.shape.MeshData(mappedShape.getCompound("data").getCompound("meshData"));
        var savedMesh = mesh.serializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry());
        var reloadedMesh = new com.lowdragmc.photon.client.gameobject.emitter.data.shape.MeshData(savedMesh);
        if (reloadedMesh.getVertices().size() != 3 || reloadedMesh.getEdges().size() != 1
                || reloadedMesh.getTriangles().size() != 1 || Math.abs(reloadedMesh.getTriangleSumArea() - 0.5) > 0.0001)
            throw new IllegalStateException("Photon 1 mesh emission geometry was not retained");
        var oldRenderer = new CompoundTag();
        var oldModel = new CompoundTag();
        oldModel.putString("modelLocation", "minecraft:block/diamond_block");
        oldRenderer.put("model", oldModel);
        var mappedRenderer = com.lowdragmc.photon.client.fx.compat.MapperUtils.mapRendererTag(oldRenderer, new CompoundTag());
        if (!mappedRenderer.getCompound("model").equals(oldModel))
            throw new IllegalStateException("Photon 1 particle model was not retained");
        var legacy = new CompoundTag();
        var definition = new CompoundTag();
        var main = new CompoundTag();
        var objects = new ListTag();
        var empty = new CompoundTag();
        empty.putString("_type", "empty");
        empty.putString("name", "legacy_anchor");
        var transform = new CompoundTag();
        transform.put("id", net.minecraft.nbt.NbtUtils.createUUID(java.util.UUID.randomUUID()));
        transform.put("_parentId", net.minecraft.nbt.NbtUtils.createUUID(java.util.UUID.randomUUID()));
        for (var key : new String[]{"localPosition", "localRotation", "localScale"}) {
            var vector = new CompoundTag();
            vector.putFloat("x", 0); vector.putFloat("y", 0); vector.putFloat("z", 0);
            if (key.equals("localRotation")) vector.putFloat("w", 1);
            transform.put(key, vector);
        }
        empty.put("transform", transform);
        objects.add(empty);
        main.put("fxObjects", objects);
        definition.put("mainFX", main);
        var legacySubEffects = new CompoundTag();
        legacySubEffects.put("spark", main.copy());
        definition.put("subFXs", legacySubEffects);
        legacy.put("fx", definition);
        // Real legacy exports can omit rotation. Exercise the decoder, not only the converted NBT's shape:
        // a three-float default cannot be decoded as LDLib2's quaternion and makes the entire effect fail.
        var missingRotation = legacy.copy();
        missingRotation.getCompound("fx").getCompound("mainFX").getList("fxObjects", Tag.TAG_COMPOUND)
                .getCompound(0).getCompound("transform").remove("localRotation");
        var sparseFx = new com.lowdragmc.photon.client.fx.FX();
        sparseFx.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(),
                com.lowdragmc.photon.client.fx.FXHelper.applyVersionFixes(missingRotation));
        if (sparseFx.getFxData().objects().size() != 1) {
            throw new IllegalStateException("A Photon 1 effect without localRotation did not load");
        }
        // Parent references and explicit sibling order must survive a complete conversion and runtime copy.
        var parentId = java.util.UUID.randomUUID();
        var firstId = java.util.UUID.randomUUID();
        var secondId = java.util.UUID.randomUUID();
        var hierarchy = legacy.copy();
        var hierarchyObjects = hierarchy.getCompound("fx").getCompound("mainFX").getList("fxObjects", Tag.TAG_COMPOUND);
        var parentTransform = hierarchyObjects.getCompound(0).getCompound("transform");
        parentTransform.put("id", net.minecraft.nbt.NbtUtils.createUUID(parentId));
        parentTransform.remove("_parentId");
        var childOrder = new ListTag();
        childOrder.add(net.minecraft.nbt.NbtUtils.createUUID(secondId));
        childOrder.add(net.minecraft.nbt.NbtUtils.createUUID(firstId));
        parentTransform.put("_childrenId", childOrder);
        for (var id : new java.util.UUID[]{firstId, secondId}) {
            var child = empty.copy();
            child.getCompound("transform").put("id", net.minecraft.nbt.NbtUtils.createUUID(id));
            child.getCompound("transform").put("_parentId", net.minecraft.nbt.NbtUtils.createUUID(parentId));
            hierarchyObjects.add(child);
        }
        var hierarchyFx = new com.lowdragmc.photon.client.fx.FX();
        hierarchyFx.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(),
                com.lowdragmc.photon.client.fx.FXHelper.applyVersionFixes(hierarchy));
        var hierarchyRuntime = hierarchyFx.createRuntime();
        var parentObject = hierarchyRuntime.objects.get(parentId);
        if (parentObject == null || !parentObject.transform().children().stream().map(t -> t.id()).toList()
                .equals(java.util.List.of(secondId, firstId))) {
            throw new IllegalStateException("Photon 1 parent hierarchy or explicit child order was not retained");
        }
        var mapped = com.lowdragmc.photon.client.fx.FXHelper.applyVersionFixes(legacy);
        if (mapped == legacy || !legacy.contains("fx")
                || !mapped.getCompound("fxData").getList("fxObjects", Tag.TAG_COMPOUND).getCompound(0).getString("type").equals("empty"))
            throw new IllegalStateException("Photon 1 effect wrapper was not converted without modifying the source");
        // An older converter already wrote these files as current-version FX, with [0,0,0] rotations.
        // Loading must repair root and nested objects without modifying the saved source tag.
        var alreadyConverted = mapped.copy();
        var oldRotation = new ListTag();
        oldRotation.add(net.minecraft.nbt.FloatTag.ZERO);
        oldRotation.add(net.minecraft.nbt.FloatTag.ZERO);
        oldRotation.add(net.minecraft.nbt.FloatTag.ZERO);
        alreadyConverted.getCompound("fxData").getList("fxObjects", Tag.TAG_COMPOUND).getCompound(0)
                .getCompound("data").getCompound("transform").put("localRotation", oldRotation.copy());
        alreadyConverted.getCompound("legacySubFXs").getCompound("spark").getCompound("fxData")
                .getList("fxObjects", Tag.TAG_COMPOUND).getCompound(0).getCompound("data")
                .getCompound("transform").put("localRotation", oldRotation.copy());
        var oldSnapshot = alreadyConverted.copy();
        var restoredConverted = new com.lowdragmc.photon.client.fx.FX();
        restoredConverted.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(),
                com.lowdragmc.photon.client.fx.FXHelper.applyVersionFixes(alreadyConverted));
        if (restoredConverted.getFxData().objects().size() != 1
                || restoredConverted.getLegacySubFXs().get("spark").getFxData().objects().size() != 1
                || !alreadyConverted.equals(oldSnapshot)) {
            throw new IllegalStateException("An already-converted legacy FX or nested effect did not load safely");
        }
        var projectData = new CompoundTag();
        projectData.put("fx", alreadyConverted.copy());
        var restoredProject = new com.lowdragmc.photon.gui.editor.FXProject();
        restoredProject.deserializeProject(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(), projectData);
        if (restoredProject.getFx().getFxData().objects().size() != 1) {
            throw new IllegalStateException("An already-converted Photon 1 project did not load");
        }
        var marker = Minecraft.getInstance().getResourceManager().getResource(
                new ResourceLocation("photon_legacy_probe", "textures/marker.txt")).orElseThrow(
                () -> new IllegalStateException("Photon 1 editor assets were not mounted"));
        try (var stream = marker.open()) {
            if (!new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).equals("legacy-pack-mounted"))
                throw new IllegalStateException("Photon 1 editor asset contents changed");
        }
        var photon2Marker = Minecraft.getInstance().getResourceManager().getResource(
                new ResourceLocation("photon2_probe", "textures/marker.txt")).orElseThrow(
                () -> new IllegalStateException("Photon 2 editor assets were not mounted"));
        try (var stream = photon2Marker.open()) {
            if (!new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .equals("photon2-editor-assets-mounted")) {
                throw new IllegalStateException("Photon 2 editor asset contents changed");
            }
        }
        var workspaceMarker = Minecraft.getInstance().getResourceManager().getResource(
                new ResourceLocation("photon_workspace_probe", "textures/marker.txt")).orElseThrow(
                () -> new IllegalStateException("Photon workspace assets were not mounted"));
        try (var stream = workspaceMarker.open()) {
            if (!new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .equals("photon-workspace-assets-mounted")) {
                throw new IllegalStateException("Photon workspace asset contents changed");
            }
        }
        Photon.LOGGER.info("[editor-assets-smoke] loaded Photon workspace assets and Photon 1/2 legacy assets");
        var legacyFxPath = java.nio.file.Path.of("ldlib/assets/photon_legacy_probe/fx/imported.fx");
        Files.createDirectories(legacyFxPath.getParent());
        try (var stream = Files.newOutputStream(legacyFxPath)) { NbtIo.writeCompressed(legacy, stream); }
        com.lowdragmc.photon.client.fx.FXHelper.clearCache();
        var loadedLegacy = com.lowdragmc.photon.client.fx.FXHelper.getFX(new ResourceLocation("photon_legacy_probe", "imported"));
        if (loadedLegacy == null || loadedLegacy.getFxData().objects().size() != 1 || !loadedLegacy.getLegacySubFXs().containsKey("spark"))
            throw new IllegalStateException("Original-format FX could not be loaded directly from the Photon 1 asset folder");
        var converted = new com.lowdragmc.photon.client.fx.FX();
        converted.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(), mapped);
        var subFx = converted.getLegacySubFXs().get("spark");
        if (subFx == null || subFx.getFxData().objects().size() != 1)
            throw new IllegalStateException("Photon 1 embedded subeffect was lost during conversion");
        var reloaded = new com.lowdragmc.photon.client.fx.FX();
        reloaded.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(),
                converted.serializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry()));
        var player = Minecraft.getInstance().player;
        var executor = new com.lowdragmc.photon.client.fx.EntityEffectExecutor(reloaded, player.level(), player,
                com.lowdragmc.photon.client.fx.EntityEffectExecutor.AutoRotate.NONE);
        if (executor.legacySubEffect("spark") == null)
            throw new IllegalStateException("Photon 1 embedded subeffect did not survive saving or resolve from its owner");
        var temp = Files.createTempDirectory("photon1-conversion-smoke");
        var source = temp.resolve("old"); var dest = temp.resolve("new");
        Files.createDirectories(source.resolve("nested"));
        try (var output = Files.newOutputStream(source.resolve("nested/test.fx"))) {
            NbtIo.writeCompressed(legacy, output);
        }
        // flattened on purpose: a nested target would become photon:nested/test, an id the picker never asks for
        var conversion = com.lowdragmc.photon.client.fx.compat.FXCompat.convertFX(source, dest);
        if (conversion.converted() != 1 || !Files.isRegularFile(dest.resolve("test.fx")))
            throw new IllegalStateException("Photon 1 converter did not flatten the effect into assets/photon/fx");
        var firstExport = Files.readAllBytes(dest.resolve("test.fx"));
        try (var outputs = Files.list(dest)) {
            if (outputs.anyMatch(path -> path.getFileName().toString().startsWith(".photon-convert-")))
                throw new IllegalStateException("Completed Photon 1 conversion left a temporary file behind");
        }
        var second = com.lowdragmc.photon.client.fx.compat.FXCompat.convertFX(source, dest);
        if (second.converted() != 0 || second.skipped().isEmpty()
                || !java.util.Arrays.equals(firstExport, Files.readAllBytes(dest.resolve("test.fx"))))
            throw new IllegalStateException("Photon 1 converter overwrote an existing effect or hid the skip");
        NbtIo.write(legacy, source.resolve("raw.fx").toFile());
        var rawConversion = com.lowdragmc.photon.client.fx.compat.FXCompat.convertFX(source, dest);
        if (rawConversion.converted() != 1 || !Files.isRegularFile(dest.resolve("raw.fx"))) {
            throw new IllegalStateException("The converter rejected an uncompressed Photon 1 effect");
        }
        // Upstream exposes construction from an existing config for integrations.
        var suppliedConfig = new com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleConfig();
        suppliedConfig.setDuration(37);
        var configuredEmitter = new ParticleEmitter(suppliedConfig);
        if (configuredEmitter.config != suppliedConfig || configuredEmitter.getLifetime() != 37)
            throw new IllegalStateException("ParticleEmitter did not initialize from its supplied config");
        // Delayed beam/trail emitters must not spend their short visible duration waiting.
        var freshBeam = new com.lowdragmc.photon.client.gameobject.emitter.beam.BeamEmitter();
        var freshTrail = new TrailEmitter();
        if (freshBeam.getParticleAmount() != 0 || freshTrail.getParticleAmount() != 0)
            throw new IllegalStateException("Uninitialized beam/trail reports a particle");
        freshBeam.remove(true); freshTrail.remove(true); // safe before reset/emission
        var beamConfig = new com.lowdragmc.photon.client.gameobject.emitter.beam.BeamConfig();
        beamConfig.setStartDelay(2); beamConfig.setDuration(1); beamConfig.setLooping(false);
        var delayedBeam = new com.lowdragmc.photon.client.gameobject.emitter.beam.BeamEmitter(beamConfig);
        var trailConfig = new com.lowdragmc.photon.client.gameobject.emitter.trail.TrailConfig();
        trailConfig.setStartDelay(2); trailConfig.setDuration(1); trailConfig.setLooping(false);
        var delayedTrail = new TrailEmitter(trailConfig);
        delayedBeam.setLevel(Minecraft.getInstance().level); delayedTrail.setLevel(Minecraft.getInstance().level);
        delayedBeam.reset(); delayedTrail.reset();
        for (int i = 0; i < 4; i++) {
            delayedBeam.updateTick(0.5f); delayedTrail.updateTick(0.5f);
            if (!delayedBeam.isAlive() || !delayedTrail.isAlive() || delayedBeam.getAgeF() != 0 || delayedTrail.getAgeF() != 0)
                throw new IllegalStateException("A delayed beam or trail consumed its duration before starting");
        }
        delayedBeam.updateTick(0.5f); delayedTrail.updateTick(0.5f);
        if (delayedBeam.getAgeF() != 0.5f || delayedTrail.getAgeF() != 0.5f)
            throw new IllegalStateException("Beam or trail did not advance after its fractional start delay");
        delayedBeam.remove(true); delayedTrail.remove(true);
        var delayedLegacyTag = new CompoundTag();
        delayedLegacyTag.putInt("startDelay", 2); delayedLegacyTag.putInt("duration", 1);
        delayedLegacyTag.putBoolean("looping", false);
        delayedLegacyTag.putString("_type", "beam");
        var decodedBeam = com.lowdragmc.photon.client.gameobject.IFXObject.deserializeWrapper(
                com.lowdragmc.photon.client.fx.compat.BeamEmitterMapper.mapBeamEmitter(delayedLegacyTag));
        delayedLegacyTag.putString("_type", "trail");
        var decodedTrail = com.lowdragmc.photon.client.gameobject.IFXObject.deserializeWrapper(
                com.lowdragmc.photon.client.fx.compat.TrailEmitterMapper.mapTrailEmitter(delayedLegacyTag));
        if (!(decodedBeam instanceof com.lowdragmc.photon.client.gameobject.emitter.beam.BeamEmitter importedBeam)
                || !(decodedTrail instanceof TrailEmitter importedTrail))
            throw new IllegalStateException("Delayed legacy beam/trail did not deserialize");
        if (!importedBeam.getConfig().getEnd().equals(new org.joml.Vector3f(3, 0, 0)))
            throw new IllegalStateException("A sparse Photon 1 beam lost its +X default endpoint");
        var explicitBeamEnd = new CompoundTag(); explicitBeamEnd.putFloat("x", -2); explicitBeamEnd.putFloat("y", 4); explicitBeamEnd.putFloat("z", 8);
        var explicitBeam = delayedLegacyTag.copy(); explicitBeam.putString("_type", "beam"); explicitBeam.put("end", explicitBeamEnd);
        var explicitObject = com.lowdragmc.photon.client.gameobject.IFXObject.deserializeWrapper(
                com.lowdragmc.photon.client.fx.compat.BeamEmitterMapper.mapBeamEmitter(explicitBeam));
        if (!(explicitObject instanceof com.lowdragmc.photon.client.gameobject.emitter.beam.BeamEmitter explicitEmitter)
                || !explicitEmitter.getConfig().getEnd().equals(new org.joml.Vector3f(-2, 4, 8)))
            throw new IllegalStateException("An authored Photon 1 beam endpoint was replaced by the default");
        for (var imported : java.util.List.of(importedBeam, importedTrail)) {
            imported.setLevel(Minecraft.getInstance().level);
            imported.reset();
            for (int i = 0; i < 2; i++) imported.updateTick(1f);
            if (!imported.isAlive() || imported.getAgeF() != 0)
                throw new IllegalStateException("Imported beam or trail expired during start delay");
            imported.updateTick(0.5f);
            if (imported.getAgeF() != 0.5f) throw new IllegalStateException("Imported beam/trail did not start after delay");
            imported.remove(true);
        }
        // The Photon 1 v0 emitter decoder treated the entire object as its config; v1+ nested it.
        var flat = new CompoundTag();
        flat.putString("_type", "particle");
        flat.putInt("duration", 42);
        flat.putInt("maxParticles", 137);
        flat.putInt("time", 19);
        flat.putInt("startDelay", 8);
        flat.put("end", new CompoundTag());
        var flatParticle = com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapParticleEmitter(flat).getCompound("data").getCompound("config");
        var flatTrail = com.lowdragmc.photon.client.fx.compat.TrailEmitterMapper.mapTrailEmitter(flat).getCompound("data").getCompound("config");
        var flatBeam = com.lowdragmc.photon.client.fx.compat.BeamEmitterMapper.mapBeamEmitter(flat).getCompound("data").getCompound("config");
        if (flatParticle.getInt("duration") != 42 || flatParticle.getInt("maxParticles") != 137
                || flatTrail.getInt("duration") != 42 || flatTrail.getInt("time") != 19
                || flatBeam.getInt("duration") != 42 || flatBeam.getInt("startDelay") != 8) {
            throw new IllegalStateException("Photon 1 flat v0 emitter configuration was lost");
        }
        // Actually decode a sparse v0 particle emitter: omitted fields must retain the real defaults.
        var sparseFlat = flat.copy();
        sparseFlat.remove("startDelay"); // particle delay was a number function, unlike beam/trail
        var decodedFlat = com.lowdragmc.photon.client.gameobject.IFXObject.deserializeWrapper(
                com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapParticleEmitter(sparseFlat));
        if (!(decodedFlat instanceof ParticleEmitter sparseParticle)
                || sparseParticle.config.getDuration() != 42 || sparseParticle.config.getMaxParticles() != 137
                || sparseParticle.config.getStartLifetime().get(0f, () -> 0f).intValue() != 100) {
            throw new IllegalStateException("Sparse Photon 1 particle emitter did not load with its defaults");
        }
        // Before NumberFunction3, shape vectors were compounds of raw doubles. The Photon 1
        // reader upgrades all three vectors when position.x is a double.
        var oldShapeEmitter = sparseFlat.copy();
        var rawShape = new CompoundTag();
        var oldShapePos = new CompoundTag(); oldShapePos.putDouble("x", 2.5); oldShapePos.putDouble("y", -3); oldShapePos.putDouble("z", 4);
        var oldShapeRot = new CompoundTag(); oldShapeRot.putDouble("x", 10); oldShapeRot.putDouble("y", 20); oldShapeRot.putDouble("z", 30);
        var oldShapeScale = new CompoundTag(); oldShapeScale.putDouble("x", 1); oldShapeScale.putDouble("y", 2); oldShapeScale.putDouble("z", 3);
        rawShape.put("position", oldShapePos); rawShape.put("rotation", oldShapeRot); rawShape.put("scale", oldShapeScale);
        oldShapeEmitter.put("shape", rawShape);
        var migratedShape = com.lowdragmc.photon.client.gameobject.IFXObject.deserializeWrapper(
                com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapParticleEmitter(oldShapeEmitter));
        if (!(migratedShape instanceof ParticleEmitter shapeEmitter)
                || !shapeEmitter.config.shape.getPosition().get(0f, () -> 0f).equals(new org.joml.Vector3f(2.5f, -3, 4))
                || !shapeEmitter.config.shape.getRotation().get(0f, () -> 0f).equals(new org.joml.Vector3f(10, 20, 30))
                || !shapeEmitter.config.shape.getScale().get(0f, () -> 0f).equals(new org.joml.Vector3f(1, 2, 3))
                || oldShapePos.getDouble("x") != 2.5) {
            throw new IllegalStateException("Photon 1 raw-double shape vectors were not imported intact");
        }
        var shapeReload = com.lowdragmc.photon.client.gameobject.IFXObject.deserializeWrapper(shapeEmitter.serializeWrapper());
        if (!(shapeReload instanceof ParticleEmitter shapeAgain)
                || !shapeAgain.config.shape.getPosition().get(0f, () -> 0f).equals(new org.joml.Vector3f(2.5f, -3, 4))
                || !shapeAgain.config.shape.getScale().get(0f, () -> 0f).equals(new org.joml.Vector3f(1, 2, 3)))
            throw new IllegalStateException("Imported old shape vectors did not survive a Photon 2 roundtrip");
        var missingVectors = new CompoundTag(); missingVectors.put("position", oldShapePos.copy());
        var mappedMissing = com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapShapeTag(missingVectors);
        if (mappedMissing.getList("scale", Tag.TAG_COMPOUND).getCompound(0).getCompound("data").getDouble("number") != 0)
            throw new IllegalStateException("Old sparse shape vector did not follow Photon 1's zero default");
        Photon.LOGGER.info("[photon1-raw-shape-smoke] restored pre-NumberFunction3 shape transforms through save and reload");
        // Photon 1's older format stored scalar startSize and sizeOverLifetime.scale.
        // Decode them as real runtime config, not just converted NBT.
        var scalarLegacy = sparseFlat.copy();
        var scalarSize = new CompoundTag(); scalarSize.putString("_type", "Constant"); scalarSize.putFloat("number", 3.5f);
        scalarLegacy.put("startSize", scalarSize);
        var scale = new CompoundTag(); scale.putString("_type", "Constant"); scale.putFloat("number", 0.4f);
        var scalarOverLife = new CompoundTag(); scalarOverLife.putBoolean("enable", true); scalarOverLife.put("scale", scale);
        scalarLegacy.put("sizeOverLifetime", scalarOverLife);
        var scalarDecoded = com.lowdragmc.photon.client.gameobject.IFXObject.deserializeWrapper(
                com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapParticleEmitter(scalarLegacy));
        if (!(scalarDecoded instanceof ParticleEmitter scalarEmitter)
                || !scalarEmitter.config.getSimulationSpace().name().equals("World")
                || !scalarEmitter.config.getStartSize().get(0f, () -> 0f).equals(new org.joml.Vector3f(3.5f))
                || !scalarEmitter.config.sizeOverLifetime.getSize().get(0f, () -> 0f).equals(new org.joml.Vector3f(0.4f))) {
            throw new IllegalStateException("Old scalar size or world simulation space was lost during Photon 1 conversion");
        }
        var scalarReloaded = com.lowdragmc.photon.client.gameobject.IFXObject.deserializeWrapper(scalarEmitter.serializeWrapper());
        if (!(scalarReloaded instanceof ParticleEmitter reloadedScalar)
                || !reloadedScalar.config.getStartSize().get(0f, () -> 0f).equals(new org.joml.Vector3f(3.5f)))
            throw new IllegalStateException("Converted scalar start size did not survive saving and reloading");
        var sizeWarningEmitter = new ParticleEmitter();
        sizeWarningEmitter.config.setStartSize(new com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3(0, 0, 0));
        if (!com.lowdragmc.photon.client.fx.FXDiagnostics.hasConstantZeroSize(sizeWarningEmitter))
            throw new IllegalStateException("All-zero constant particle size was not diagnosed");
        sizeWarningEmitter.config.setStartSize(new com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3(0, 1, 0));
        if (com.lowdragmc.photon.client.fx.FXDiagnostics.hasConstantZeroSize(sizeWarningEmitter))
            throw new IllegalStateException("Nonzero particle size was incorrectly diagnosed as all-zero");
        // Already-converted files can still carry Photon 1 codec names. Unrecognized types
        // decode as constant 0, which makes the whole effect invisible. Repair a copy at load.
        var leftoverConstant = new CompoundTag();
        leftoverConstant.putString("type", "Constant");
        leftoverConstant.putFloat("number", 2.25f);
        var leftoverFunction = com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction.deserializeWrapper(leftoverConstant);
        if (leftoverFunction.get(0f, () -> 0f).floatValue() != 2.25f)
            throw new IllegalStateException("Photon 1 Constant number function decoded as zero");
        var leftoverMaterial = new CompoundTag();
        leftoverMaterial.putString("type", "TextureMaterial");
        leftoverMaterial.putString("texture", "minecraft:textures/particle/glow.png");
        leftoverMaterial.putFloat("discardThreshold", 0.01f);
        if (!(com.lowdragmc.photon.client.gameobject.emitter.data.material.IMaterial.deserializeWrapper(leftoverMaterial)
                instanceof com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial leftoverTexture)
                || !leftoverTexture.getTexture().toString().equals("minecraft:textures/particle/glow.png")) {
            throw new IllegalStateException("Photon 1 TextureMaterial did not load");
        }
        var leftoverGradient = new CompoundTag();
        leftoverGradient.putString("type", "Gradient");
        leftoverGradient.put("a", floatList(0f, 1f, 1f, 1f));
        leftoverGradient.put("r", floatList(0f, 1f, 1f, 1f));
        leftoverGradient.put("g", floatList(0f, 0f, 1f, 0f));
        leftoverGradient.put("b", floatList(0f, 0f, 1f, 0f));
        var leftoverGradientFn = com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction.deserializeWrapper(leftoverGradient);
        if (!(leftoverGradientFn instanceof com.lowdragmc.photon.client.gameobject.emitter.data.number.color.Gradient)
                || leftoverGradient.contains("data")
                || ((leftoverGradientFn.get(0f, () -> 0f).intValue() >> 16) & 0xff) < 200) {
            throw new IllegalStateException("Photon 1 Gradient colour channels did not load");
        }
        var leftoverRandomGradient = new CompoundTag();
        leftoverRandomGradient.putString("_type", "RandomGradient");
        leftoverRandomGradient.put("a", leftoverGradient.copy());
        leftoverRandomGradient.put("b", leftoverGradient.copy());
        leftoverRandomGradient.remove("type");
        leftoverRandomGradient.getCompound("a").remove("type");
        leftoverRandomGradient.getCompound("b").remove("type");
        var leftoverRandomFn = com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction.deserializeWrapper(leftoverRandomGradient);
        if (!(leftoverRandomFn instanceof com.lowdragmc.photon.client.gameobject.emitter.data.number.color.RandomGradient)
                || leftoverRandomGradient.contains("data")
                || leftoverRandomFn.get(0f, () -> 0f).intValue() == 0) {
            throw new IllegalStateException("Photon 1 RandomGradient colour channels did not load");
        }
        var leftoverRange = new CompoundTag();
        leftoverRange.putString("type", "RandomConstant");
        leftoverRange.putInt("a", 0);
        leftoverRange.putInt("b", 1);
        leftoverRange.putBoolean("isDecimals", false);
        var leftoverRangeFn = com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction.deserializeWrapper(leftoverRange);
        if (leftoverRangeFn.get(0f, () -> 0.99f).intValue() != 1)
            throw new IllegalStateException("Photon 1 integer RandomConstant range no longer includes the upper endpoint");
        var leftoverToggle = new CompoundTag();
        leftoverToggle.putBoolean("enable", true);
        var repairedToggle = com.lowdragmc.photon.client.fx.compat.MapperUtils.repairLegacyCodecs(leftoverToggle);
        if (!(repairedToggle instanceof CompoundTag enabledToggle) || !enabledToggle.getBoolean("_enable")
                || leftoverToggle.contains("_enable")) {
            throw new IllegalStateException("Photon 1 enable flag was not read as a Photon 2 toggle");
        }
        var leftoverShader = new CompoundTag();
        leftoverShader.putString("type", "CustomShaderMaterial");
        leftoverShader.putString("shader", "photon:circle");
        var leftoverUniforms = new CompoundTag();
        leftoverUniforms.put("uniforms", new CompoundTag());
        leftoverShader.put("uniform", leftoverUniforms);
        if (!(com.lowdragmc.photon.client.gameobject.emitter.data.material.IMaterial.deserializeWrapper(leftoverShader)
                instanceof com.lowdragmc.photon.client.gameobject.emitter.data.material.CustomShaderMaterial leftoverCustom)
                || !leftoverCustom.getShaderLocation().toString().equals("photon:circle")) {
            throw new IllegalStateException("Photon 1 CustomShaderMaterial did not load");
        }
        var leftoverUv = new CompoundTag();
        leftoverUv.putBoolean("enable", true);
        var leftoverTiles = new CompoundTag();
        leftoverTiles.putInt("a", 4);
        leftoverTiles.putInt("b", 2);
        leftoverUv.put("tiles", leftoverTiles);
        var repairedUv = com.lowdragmc.photon.client.fx.compat.MapperUtils.repairLegacyCodecs(leftoverUv);
        if (!(repairedUv instanceof CompoundTag uvTag) || leftoverUv.getCompound("tiles").getInt("a") != 4
                || !java.util.Arrays.equals(uvTag.getIntArray("tiles"), new int[]{4, 2})) {
            throw new IllegalStateException("Photon 1 UV tiles were not read as a Photon 2 sheet size");
        }
        var leftoverShapeSetting = new CompoundTag();
        leftoverShapeSetting.put("shape", new CompoundTag());
        var leftoverPos = new CompoundTag();
        leftoverPos.putDouble("x", 2.5);
        leftoverPos.putDouble("y", -3);
        leftoverPos.putDouble("z", 4);
        leftoverShapeSetting.put("position", leftoverPos);
        leftoverShapeSetting.put("rotation", leftoverPos.copy());
        leftoverShapeSetting.put("scale", leftoverPos.copy());
        var repairedShape = com.lowdragmc.photon.client.fx.compat.MapperUtils.repairLegacyCodecs(leftoverShapeSetting);
        if (!(repairedShape instanceof CompoundTag shapeTag)
                || leftoverShapeSetting.getCompound("position").getDouble("x") != 2.5
                || shapeTag.getList("position", Tag.TAG_COMPOUND).getCompound(0).getCompound("data").getDouble("number") != 2.5) {
            throw new IllegalStateException("Photon 1 raw shape vectors were not read as NumberFunction3");
        }
        var leftoverSol = new CompoundTag();
        leftoverSol.putBoolean("enable", true);
        leftoverSol.put("scale", leftoverConstant.copy());
        var repairedSol = com.lowdragmc.photon.client.fx.compat.MapperUtils.repairLegacyCodecs(leftoverSol);
        if (!(repairedSol instanceof CompoundTag solTag) || leftoverSol.contains("size")
                || solTag.getList("size", Tag.TAG_COMPOUND).size() != 3
                || solTag.getList("size", Tag.TAG_COMPOUND).getCompound(1).getCompound("data").getFloat("number") != 2.25f) {
            throw new IllegalStateException("Photon 1 size-over-lifetime scale did not expand");
        }
        var leftoverSub = new CompoundTag();
        leftoverSub.putString("event", "Birth");
        leftoverSub.putString("emitter", "spark");
        var repairedSub = com.lowdragmc.photon.client.fx.compat.MapperUtils.repairLegacyCodecs(leftoverSub);
        if (!(repairedSub instanceof CompoundTag subTag) || !"spark".equals(leftoverSub.getString("emitter"))
                || !subTag.getString("legacySubFXName").equals("spark") || !subTag.getBoolean("legacyTickGate")) {
            throw new IllegalStateException("Photon 1 sub-emitter name was not kept");
        }
        var leftoverBursts = new CompoundTag();
        leftoverBursts.put("emissionRate", leftoverConstant.copy());
        var leftoverBurstList = new ListTag();
        var leftoverBurst = new CompoundTag();
        leftoverBurst.putInt("time", 5);
        leftoverBurstList.add(leftoverBurst);
        leftoverBursts.put("bursts", leftoverBurstList);
        var repairedBursts = com.lowdragmc.photon.client.fx.compat.MapperUtils.repairLegacyCodecs(leftoverBursts);
        if (!(repairedBursts instanceof CompoundTag leftoverBurstTag) || leftoverBursts.getList("bursts", Tag.TAG_COMPOUND).size() != 1
                || leftoverBurstTag.getCompound("bursts").getList("payload", Tag.TAG_COMPOUND).getCompound(0).getInt("time") != 5) {
            throw new IllegalStateException("Photon 1 burst list was not wrapped for Photon 2");
        }
        var leftoverParticle = new CompoundTag();
        leftoverParticle.put("emission", new CompoundTag());
        leftoverParticle.put("startLifetime", leftoverConstant.copy());
        leftoverParticle.put("startSize", leftoverConstant.copy());
        var repairedParticle = com.lowdragmc.photon.client.fx.compat.MapperUtils.repairLegacyCodecs(leftoverParticle);
        if (!(repairedParticle instanceof CompoundTag particleTag) || leftoverParticle.contains("simulationSpace")
                || !"World".equals(particleTag.getString("simulationSpace"))
                || particleTag.getList("startSize", Tag.TAG_COMPOUND).size() != 3) {
            throw new IllegalStateException("Photon 1 particle without simulationSpace did not default to World");
        }
        var leftoverTrails = new CompoundTag();
        leftoverTrails.putFloat("ratio", 1f);
        leftoverTrails.put("material", leftoverMaterial.copy());
        leftoverTrails.put("widthOverTrail", leftoverConstant.copy());
        leftoverTrails.putFloat("minimumVertexDistance", 0.1f);
        var repairedTrails = com.lowdragmc.photon.client.fx.compat.MapperUtils.repairLegacyCodecs(leftoverTrails);
        if (!(repairedTrails instanceof CompoundTag trailsTag) || leftoverTrails.contains("config")
                || trailsTag.getCompound("config").getFloat("minVertexDistance") != 0.1f
                || !trailsTag.getCompound("config").contains("widthOverTrail")
                || trailsTag.getCompound("config").getCompound("renderer").getCompound("materials")
                .getList("payload", Tag.TAG_COMPOUND).isEmpty()) {
            throw new IllegalStateException("Photon 1 flat trail material was not nested into TrailConfig");
        }
        var leftoverSlot = new CompoundTag();
        leftoverSlot.put("blendMode", new CompoundTag());
        leftoverSlot.putByte("cull", (byte) 1);
        leftoverSlot.putByte("depthTest", (byte) 1);
        leftoverSlot.put("material", leftoverMaterial.copy());
        var leftoverConfigMaterial = new CompoundTag();
        leftoverConfigMaterial.put("material", leftoverSlot);
        leftoverConfigMaterial.put("renderer", new CompoundTag());
        var repairedConfigMaterial = com.lowdragmc.photon.client.fx.compat.MapperUtils.repairLegacyCodecs(leftoverConfigMaterial);
        if (!(repairedConfigMaterial instanceof CompoundTag configMaterialTag)
                || leftoverConfigMaterial.getCompound("renderer").contains("materials")
                || !"CUSTOM".equals(configMaterialTag.getCompound("renderer").getCompound("materials")
                .getList("payload", Tag.TAG_COMPOUND).getCompound(0).getString("renderStateSource"))
                || !configMaterialTag.getCompound("renderer").getCompound("materials")
                .getList("payload", Tag.TAG_COMPOUND).getCompound(0).getCompound("material")
                .getCompound("data").getString("texture").equals("minecraft:textures/particle/glow.png")) {
            throw new IllegalStateException("Photon 1 config material was not nested into renderer.materials");
        }
        var leftoverFrom = new CompoundTag(); leftoverFrom.putFloat("x", -8); leftoverFrom.putFloat("y", -4); leftoverFrom.putFloat("z", -2);
        var leftoverTo = new CompoundTag(); leftoverTo.putFloat("x", 12); leftoverTo.putFloat("y", 6); leftoverTo.putFloat("z", 3);
        var leftoverCull = new CompoundTag(); leftoverCull.putBoolean("enable", true); leftoverCull.put("from", leftoverFrom); leftoverCull.put("to", leftoverTo);
        var repairedCull = com.lowdragmc.photon.client.fx.compat.MapperUtils.repairLegacyCodecs(leftoverCull);
        if (!(repairedCull instanceof CompoundTag cullTag) || leftoverCull.contains("cullBox")
                || cullTag.getCompound("cullBox").getList("min", Tag.TAG_DOUBLE).getDouble(0) != -8
                || cullTag.getCompound("cullBox").getList("max", Tag.TAG_DOUBLE).getDouble(0) != 12) {
            throw new IllegalStateException("Photon 1 cull from/to was not read as cullBox");
        }
        var leftoverBloom = new CompoundTag();
        var leftoverBloomRenderer = new CompoundTag(); leftoverBloomRenderer.putBoolean("bloomEffect", true);
        leftoverBloom.put("renderer", leftoverBloomRenderer);
        var repairedBloom = com.lowdragmc.photon.client.fx.compat.MapperUtils.repairLegacyCodecs(leftoverBloom);
        if (!(repairedBloom instanceof CompoundTag bloomTag) || leftoverBloom.getCompound("lights").getBoolean("legacyBloomFullBright")
                || !bloomTag.getCompound("lights").getBoolean("legacyBloomFullBright")) {
            throw new IllegalStateException("Photon 1 bloomEffect did not keep full-bright lighting");
        }
        var leftoverBlend = new CompoundTag(); leftoverBlend.putString("blendFunc", "sub");
        var repairedBlend = com.lowdragmc.photon.client.fx.compat.MapperUtils.repairLegacyCodecs(leftoverBlend);
        if (!(repairedBlend instanceof CompoundTag blendTag) || !"sub".equals(leftoverBlend.getString("blendFunc"))
                || !"SUB".equals(blendTag.getString("blendFunc"))) {
            throw new IllegalStateException("Photon 1 blendFunc sub was not renamed");
        }
        var leftoverEmission = new CompoundTag();
        leftoverEmission.putString("emissionMode", "Exacting");
        leftoverEmission.put("emissionRate", leftoverConstant.copy());
        var repairedEmission = com.lowdragmc.photon.client.fx.compat.MapperUtils.repairLegacyCodecs(leftoverEmission);
        if (!(repairedEmission instanceof CompoundTag emissionTag) || leftoverEmission.contains("legacyTickEmission")
                || !emissionTag.getBoolean("legacyTickEmission")) {
            throw new IllegalStateException("Photon 1 emission did not keep integer-age sampling");
        }
        var leftoverInherit = new CompoundTag();
        leftoverInherit.putString("mode", "INITIAL");
        leftoverInherit.put("multiply", leftoverConstant.copy());
        leftoverInherit.putBoolean("enable", true);
        var repairedInherit = com.lowdragmc.photon.client.fx.compat.MapperUtils.repairLegacyCodecs(leftoverInherit);
        if (!(repairedInherit instanceof CompoundTag inheritTag) || leftoverInherit.contains("legacyUnscaledVelocity")
                || !inheritTag.getBoolean("legacyUnscaledVelocity")) {
            throw new IllegalStateException("Photon 1 inherit-velocity did not keep raw emitter speed");
        }
        var leftoverSubList = new CompoundTag();
        leftoverSubList.putBoolean("enable", true);
        var leftoverSubEntries = new ListTag();
        leftoverSubEntries.add(leftoverSub.copy());
        leftoverSubList.put("emitters", leftoverSubEntries);
        var repairedSubList = com.lowdragmc.photon.client.fx.compat.MapperUtils.repairLegacyCodecs(leftoverSubList);
        if (!(repairedSubList instanceof CompoundTag subListTag) || leftoverSubList.getList("emitters", Tag.TAG_COMPOUND).size() != 1
                || !"spark".equals(subListTag.getCompound("emitters").getList("payload", Tag.TAG_COMPOUND)
                .getCompound(0).getString("legacySubFXName"))) {
            throw new IllegalStateException("Photon 1 sub-emitter list was not wrapped for Photon 2");
        }
        var leftoverTransform = new CompoundTag();
        var leftoverTransformPos = new CompoundTag(); leftoverTransformPos.putFloat("x", 1); leftoverTransformPos.putFloat("y", 2); leftoverTransformPos.putFloat("z", 3);
        leftoverTransform.put("localPosition", leftoverTransformPos);
        leftoverTransform.put("localScale", leftoverTransformPos.copy());
        var leftoverRot = leftoverTransformPos.copy(); leftoverRot.putFloat("w", 1);
        leftoverTransform.put("localRotation", leftoverRot);
        var repairedTransform = com.lowdragmc.photon.client.fx.compat.MapperUtils.repairLegacyCodecs(leftoverTransform);
        if (!(repairedTransform instanceof CompoundTag transformTag) || leftoverTransform.getCompound("localPosition").getFloat("x") != 1
                || transformTag.getList("localPosition", Tag.TAG_FLOAT).getFloat(0) != 1f
                || transformTag.getList("localRotation", Tag.TAG_FLOAT).size() != 4
                || transformTag.getList("localRotation", Tag.TAG_FLOAT).getFloat(3) != 1f) {
            throw new IllegalStateException("Photon 1 transform vectors were not read as float lists");
        }
        var leftoverUuid = java.util.UUID.fromString("12345678-1234-1234-1234-123456789abc");
        var leftoverTransformUuid = leftoverTransform.copy();
        leftoverTransformUuid.put("id", net.minecraft.nbt.NbtUtils.createUUID(leftoverUuid));
        leftoverTransformUuid.put("_parentId", net.minecraft.nbt.NbtUtils.createUUID(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")));
        var leftoverChildren = new ListTag();
        leftoverChildren.add(net.minecraft.nbt.NbtUtils.createUUID(java.util.UUID.fromString("00000000-0000-0000-0000-000000000002")));
        leftoverTransformUuid.put("_childrenId", leftoverChildren);
        var repairedTransformUuid = com.lowdragmc.photon.client.fx.compat.MapperUtils.repairLegacyCodecs(leftoverTransformUuid);
        if (!(repairedTransformUuid instanceof CompoundTag uuidTag)
                || !(leftoverTransformUuid.get("id") instanceof net.minecraft.nbt.IntArrayTag)
                || !leftoverUuid.toString().equals(uuidTag.getString("id"))
                || !"00000000-0000-0000-0000-000000000001".equals(uuidTag.getString("_parentId"))
                || !"00000000-0000-0000-0000-000000000002".equals(uuidTag.getList("_childrenId", Tag.TAG_STRING).getString(0))) {
            throw new IllegalStateException("Photon 1 transform UUID int arrays were not read as strings");
        }
        var leftoverTransformUuidLists = new CompoundTag();
        leftoverTransformUuidLists.put("localPosition", transformTag.get("localPosition").copy());
        leftoverTransformUuidLists.put("id", net.minecraft.nbt.NbtUtils.createUUID(leftoverUuid));
        var repairedUuidLists = com.lowdragmc.photon.client.fx.compat.MapperUtils.repairLegacyCodecs(leftoverTransformUuidLists);
        if (!(repairedUuidLists instanceof CompoundTag uuidListTag)
                || !(leftoverTransformUuidLists.get("id") instanceof net.minecraft.nbt.IntArrayTag)
                || !leftoverUuid.toString().equals(uuidListTag.getString("id"))) {
            throw new IllegalStateException("Photon 1 transform UUID leftover was not repaired on list coords");
        }
        var leftoverNf3Floats = new CompoundTag();
        leftoverNf3Floats.putFloat("x", 2.25f);
        leftoverNf3Floats.putFloat("y", 3.5f);
        leftoverNf3Floats.putFloat("z", 4.75f);
        var leftoverNf3 = new CompoundTag();
        leftoverNf3.put("startSize", leftoverNf3Floats);
        leftoverNf3.put("force", leftoverNf3Floats.copy());
        var repairedNf3 = com.lowdragmc.photon.client.fx.compat.MapperUtils.repairLegacyCodecs(leftoverNf3);
        if (!(repairedNf3 instanceof CompoundTag nf3Tag) || leftoverNf3.getCompound("startSize").getFloat("x") != 2.25f
                || nf3Tag.getList("startSize", Tag.TAG_COMPOUND).size() != 3
                || nf3Tag.getList("startSize", Tag.TAG_COMPOUND).getCompound(0).getCompound("data").getDouble("number") != 2.25
                || nf3Tag.getList("force", Tag.TAG_COMPOUND).getCompound(2).getCompound("data").getDouble("number") != 4.75) {
            throw new IllegalStateException("Photon 1 NumberFunction3 float x/y/z was not read as constants");
        }
        var leftoverBeamEnd = new CompoundTag(); leftoverBeamEnd.putFloat("x", 1); leftoverBeamEnd.putFloat("y", 2); leftoverBeamEnd.putFloat("z", 3);
        var leftoverBeam = new CompoundTag();
        leftoverBeam.put("emitRate", leftoverConstant.copy());
        leftoverBeam.put("width", leftoverConstant.copy());
        leftoverBeam.put("color", leftoverConstant.copy());
        leftoverBeam.put("end", leftoverBeamEnd);
        var repairedBeam = com.lowdragmc.photon.client.fx.compat.MapperUtils.repairLegacyCodecs(leftoverBeam);
        if (!(repairedBeam instanceof CompoundTag beamTag) || leftoverBeam.getCompound("end").getFloat("x") != 1
                || beamTag.getList("end", Tag.TAG_FLOAT).getFloat(0) != 1f
                || beamTag.getList("end", Tag.TAG_FLOAT).getFloat(2) != 3f) {
            throw new IllegalStateException("Photon 1 beam end was not read as a float list");
        }
        var leftoverBeamSparse = new CompoundTag();
        leftoverBeamSparse.put("emitRate", leftoverConstant.copy());
        leftoverBeamSparse.put("width", leftoverConstant.copy());
        leftoverBeamSparse.put("color", leftoverConstant.copy());
        var repairedBeamSparse = com.lowdragmc.photon.client.fx.compat.MapperUtils.repairLegacyCodecs(leftoverBeamSparse);
        if (!(repairedBeamSparse instanceof CompoundTag sparseBeamTag) || leftoverBeamSparse.contains("end")
                || sparseBeamTag.getList("end", Tag.TAG_FLOAT).getFloat(0) != 3f
                || sparseBeamTag.getList("end", Tag.TAG_FLOAT).getFloat(2) != 0f) {
            throw new IllegalStateException("Photon 1 beam without end did not default to +X");
        }
        var leftoverFlatEmitter = new CompoundTag();
        leftoverFlatEmitter.put("transform", leftoverTransform.copy());
        leftoverFlatEmitter.put("startLifetime", leftoverConstant.copy());
        leftoverFlatEmitter.put("emission", leftoverEmission.copy());
        leftoverFlatEmitter.putString("name", "spark");
        var repairedFlatEmitter = com.lowdragmc.photon.client.fx.compat.MapperUtils.repairLegacyCodecs(leftoverFlatEmitter);
        if (!(repairedFlatEmitter instanceof CompoundTag flatEmitterTag) || leftoverFlatEmitter.contains("config")
                || !"spark".equals(flatEmitterTag.getString("name"))
                || !flatEmitterTag.contains("transform")
                || !flatEmitterTag.getCompound("config").contains("startLifetime")
                || !flatEmitterTag.getCompound("config").contains("emission")
                || !flatEmitterTag.getCompound("config").getCompound("emission").getBoolean("legacyTickEmission")) {
            throw new IllegalStateException("Photon 1 version-0 emitter config was not nested");
        }
        var leftoverShape = new CompoundTag();
        leftoverShape.putString("type", "Circle");
        leftoverShape.putFloat("radius", 1.5f);
        if (!(com.lowdragmc.photon.client.gameobject.emitter.data.shape.IShape.deserializeWrapper(leftoverShape)
                instanceof com.lowdragmc.photon.client.gameobject.emitter.data.shape.Circle leftoverCircle)
                || leftoverCircle.getRadius() != 1.5f) {
            throw new IllegalStateException("Photon 1 Circle shape did not load");
        }
        var leftover = com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapParticleEmitter(sparseFlat).copy();
        leftover.putString("type", "particle");
        var leftoverAxes = new CompoundTag();
        leftoverAxes.put("x", leftoverConstant.copy());
        leftoverAxes.put("y", leftoverConstant.copy());
        leftoverAxes.put("z", leftoverConstant.copy());
        leftover.getCompound("data").getCompound("config").put("startSize", leftoverAxes);
        var leftoverDecoded = com.lowdragmc.photon.client.gameobject.IFXObject.deserializeWrapper(leftover);
        if (!(leftoverDecoded instanceof ParticleEmitter leftoverEmitter))
            throw new IllegalStateException("Photon 1 particle type did not load as a particle emitter");
        if (!leftover.getString("type").equals("particle")
                || !leftover.getCompound("data").getCompound("config").getCompound("startSize").contains("x"))
            throw new IllegalStateException("Leftover Photon 1 codec repair mutated the author's tag");
        if (!leftoverEmitter.config.getStartSize().get(0f, () -> 0f).equals(new org.joml.Vector3f(2.25f)))
            throw new IllegalStateException("Photon 1 x/y/z start size did not load");
        var leftoverScalar = leftover.copy();
        leftoverScalar.getCompound("data").getCompound("config").put("startSize", leftoverConstant.copy());
        var leftoverScalarDecoded = com.lowdragmc.photon.client.gameobject.IFXObject.deserializeWrapper(leftoverScalar);
        if (!(leftoverScalarDecoded instanceof ParticleEmitter leftoverScalarEmitter)
                || !leftoverScalarEmitter.config.getStartSize().get(0f, () -> 0f).equals(new org.joml.Vector3f(2.25f))) {
            throw new IllegalStateException("Photon 1 scalar start size did not load");
        }
        Photon.LOGGER.info("[photon1-codec-smoke] loaded Photon 1 type names without reconverting");
        // Sparse enabled modules must not turn missing 1-valued defaults into zero.
        var sparseModules = sparseFlat.copy();
        var enabledModule = new CompoundTag(); enabledModule.putBoolean("enable", true);
        for (var name : java.util.List.of("velocityOverLifetime", "sizeBySpeed", "noise", "inheritVelocity"))
            sparseModules.put(name, enabledModule.copy());
        var sparsePhysics = enabledModule.copy(); sparsePhysics.putBoolean("removedWhenCollided", true);
        sparseModules.put("physics", sparsePhysics);
        sparseModules.put("shape", new CompoundTag());
        var modulesObject = com.lowdragmc.photon.client.gameobject.IFXObject.deserializeWrapper(
                com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapParticleEmitter(sparseModules));
        if (!(modulesObject instanceof ParticleEmitter modules)
                || modules.config.velocityOverLifetime.getSpeedModifier().get(0f, () -> 0f).floatValue() != 1f
                || modules.config.velocityOverLifetime.getOrbitalMode() == null
                || !modules.config.sizeBySpeed.getSize().get(0f, () -> 0f).equals(new org.joml.Vector3f(1f))
                || !modules.config.shape.getScale().get(0f, () -> 0f).equals(new org.joml.Vector3f(1f))
                || Math.abs(modules.config.noise.getPosition().x.get(0f, () -> 0f).floatValue() - 0.1f) > 0.0001f
                || modules.config.noise.getFrequency() != 1f
                || modules.config.inheritVelocity.getMultiply().get(0f, () -> 0f).floatValue() != 1f
                || !modules.config.physics.isRemovedWhenCollided()) {
            throw new IllegalStateException("Sparse legacy modules lost defaults or collision-removal flag");
        }
        // Photon 1 persisted the inheritance multiplier but did not use it in either particle
        // velocity path. Test converted settings through particle setup, runtime, and reload.
        for (var inheritanceMode : com.lowdragmc.photon.client.gameobject.emitter.data.InheritVelocitySetting.Mode.values()) {
            var oldInherit = new CompoundTag(); oldInherit.putBoolean("enable", true);
            oldInherit.putString("mode", inheritanceMode.name());
            var multiplier = new CompoundTag(); multiplier.putString("_type", "Constant"); multiplier.putFloat("number", 0.25f);
            oldInherit.put("multiply", multiplier);
            var mover = new ParticleEmitter() {
                @Override public org.joml.Vector3f getVelocity() { return new org.joml.Vector3f(2, 0, 0); }
            };
            mover.setLevel(Minecraft.getInstance().level);
            mover.config.shape.setShape(new com.lowdragmc.photon.client.gameobject.emitter.data.shape.Dot());
            mover.config.inheritVelocity.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(),
                    com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapInheritVelocity(oldInherit));
            var savedInheritance = mover.config.inheritVelocity.serializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry());
            mover.config.inheritVelocity.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(), savedInheritance);
            var inherited = new com.lowdragmc.photon.client.gameobject.particle.TileParticle(mover, mover.config);
            if (Math.abs(inherited.getRealVelocity().x - 2f) > 0.0001f
                    || mover.config.inheritVelocity.getMultiply().get(0f, () -> 0f).floatValue() != 0.25f)
                throw new IllegalStateException("Photon 1 " + inheritanceMode + " velocity or multiplier was not retained");
            mover.config.inheritVelocity.setLegacyUnscaledVelocity(false);
            var nativeParticle = new com.lowdragmc.photon.client.gameobject.particle.TileParticle(mover, mover.config);
            if (Math.abs(nativeParticle.getRealVelocity().x - 0.5f) > 0.0001f)
                throw new IllegalStateException("Native " + inheritanceMode + " velocity no longer applies its multiplier");
        }
        var oldFrom = new CompoundTag(); oldFrom.putFloat("x", -8); oldFrom.putFloat("y", -4); oldFrom.putFloat("z", -2);
        var oldTo = new CompoundTag(); oldTo.putFloat("x", 12); oldTo.putFloat("y", 6); oldTo.putFloat("z", 3);
        var oldCull = new CompoundTag(); oldCull.putBoolean("enable", true); oldCull.put("from", oldFrom); oldCull.put("to", oldTo);
        var legacyCullRenderer = new CompoundTag(); legacyCullRenderer.put("cull", oldCull);
        var renderLegacy = sparseFlat.copy(); renderLegacy.put("renderer", legacyCullRenderer);
        renderLegacy.put("material", new CompoundTag());
        renderLegacy.put("uvAnimation", enabledModule.copy());
        renderLegacy.put("lights", enabledModule.copy());
        var renderObject = com.lowdragmc.photon.client.gameobject.IFXObject.deserializeWrapper(
                com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapParticleEmitter(renderLegacy));
        if (!(renderObject instanceof ParticleEmitter renderEmitter)
                || !renderEmitter.config.renderer.getCull().getCullBox().equals(new net.minecraft.world.phys.AABB(-8, -4, -2, 12, 6, 3))
                || renderEmitter.config.uvAnimation.getCycle() != 1f
                || renderEmitter.config.uvAnimation.getTiles().x != 1 || renderEmitter.config.uvAnimation.getTiles().y != 1
                || renderEmitter.config.renderer.getMaterials().isEmpty()
                || !renderEmitter.config.renderer.getMaterials().get(0).isDepthTest()
                || !renderEmitter.config.renderer.getMaterials().get(0).isCull()) {
            throw new IllegalStateException("Legacy cull bounds or sparse renderer defaults were lost");
        }
        // Legacy bloom forces full-bright regardless of the separate authored light group.
        // Preserve the authored group too; this does not claim to recreate the bloom halo.
        var bloomSource = new CompoundTag();
        var bloomRenderer = new CompoundTag(); bloomRenderer.putBoolean("bloomEffect", true);
        bloomSource.put("renderer", bloomRenderer);
        var oldLights = new CompoundTag(); oldLights.putBoolean("enable", false);
        var oldLowLight = new CompoundTag(); oldLowLight.putString("_type", "Constant"); oldLowLight.putInt("number", 3);
        oldLights.put("blockLight", oldLowLight); bloomSource.put("lights", oldLights);
        for (var mappedBloom : java.util.List.of(
                com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapConfigTag(bloomSource),
                com.lowdragmc.photon.client.fx.compat.BeamEmitterMapper.mapBeamConfig(bloomSource),
                com.lowdragmc.photon.client.fx.compat.TrailEmitterMapper.mapTrailConfig(bloomSource))) {
            var lights = new com.lowdragmc.photon.client.gameobject.emitter.data.LightOverLifetimeSetting();
            lights.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(), mappedBloom.getCompound("lights"));
            var lightCopy = new com.lowdragmc.photon.client.gameobject.emitter.data.LightOverLifetimeSetting();
            lightCopy.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(),
                    lights.serializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry()));
            if (lightCopy.isEnable() || lightCopy.getBlockLight().get(0f, () -> 0f).intValue() != 3
                    || !lightCopy.createRuntime().isEnable()
                    || lightCopy.createRuntime().getLight(null, 0f) != net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                throw new IllegalStateException("Photon 1 bloom full-bright or authored light settings were lost");
        }
        bloomRenderer.putBoolean("bloomEffect", false);
        if (com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapConfigTag(bloomSource)
                .getCompound("lights").getBoolean("legacyBloomFullBright"))
            throw new IllegalStateException("Non-bloom Photon 1 effect was incorrectly made full-bright");
        var sparseCull = enabledModule.copy();
        com.lowdragmc.photon.client.fx.compat.MapperUtils.mapCullTag(sparseCull); // omitted bounds must not throw
        var sheet = com.lowdragmc.photon.client.gameobject.emitter.data.UVAnimationSetting.Animation.WholeSheet;
        var rowMode = com.lowdragmc.photon.client.gameobject.emitter.data.UVAnimationSetting.Animation.SingleRow;
        var uvZero = com.lowdragmc.photon.client.gameobject.emitter.data.UVAnimationSetting.calculateUVs(new org.joml.Vector2i(0, 0), sheet, 3, 0);
        var uvNormal = com.lowdragmc.photon.client.gameobject.emitter.data.UVAnimationSetting.calculateUVs(new org.joml.Vector2i(4, 2), sheet, 5, 0);
        var uvNegative = com.lowdragmc.photon.client.gameobject.emitter.data.UVAnimationSetting.calculateUVs(new org.joml.Vector2i(4, 2), rowMode, -1, 0);
        var uvLarge = com.lowdragmc.photon.client.gameobject.emitter.data.UVAnimationSetting.calculateUVs(new org.joml.Vector2i(65536, 65536), sheet, 7, 0);
        if (!uvZero.equals(new org.joml.Vector4f(0, 0, 1, 1)) || !uvNormal.equals(new org.joml.Vector4f(0.25f, 0.5f, 0.5f, 1f))
                || !uvNegative.equals(new org.joml.Vector4f(0.75f, 0, 1, 0.5f)) || !Float.isFinite(uvLarge.x))
            throw new IllegalStateException("UV sheet coordinates failed for malformed, large or wrapped tile counts");
        var nested = new CompoundTag();
        nested.putString("_type", "particle");
        nested.putInt("_version", 2);
        nested.put("config", flat.copy());
        if (!com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapParticleEmitter(nested)
                .getCompound("data").getCompound("config").equals(flatParticle)) {
            throw new IllegalStateException("Photon 1 flat and nested emitter configurations differ");
        }
        var oldBurst = new CompoundTag();
        oldBurst.putInt("time", 4); oldBurst.putInt("cycles", 3); oldBurst.putInt("interval", 2);
        oldBurst.putFloat("probability", 0.75f);
        var count = new CompoundTag(); count.putString("_type", "Constant"); count.putFloat("number", 7);
        oldBurst.put("count", count);
        var oldBursts = new ListTag(); oldBursts.add(oldBurst);
        var emission = new CompoundTag(); emission.put("bursts", oldBursts);
        var mappedBursts = com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapEmissionTag(emission).getCompound("bursts");
        var burst = mappedBursts.getList("payload", Tag.TAG_COMPOUND).getCompound(0);
        if (mappedBursts.getInt("uid") != 1 || burst.getInt("time") != 4 || burst.getInt("cycles") != 3
                || burst.getCompound("count").getCompound("data").getFloat("number") != 7)
            throw new IllegalStateException("Photon 1 burst was lost during conversion");
        // Decode the managed list and execute it, rather than checking only the converted tag shape.
        var immediate = oldBurst.copy();
        immediate.putInt("time", 0);
        immediate.putInt("cycles", 1);
        immediate.putFloat("probability", 1f);
        var immediateBursts = new ListTag(); immediateBursts.add(immediate);
        var oldRate = new CompoundTag(); oldRate.putString("_type", "Constant"); oldRate.putFloat("number", 0f);
        var oldEmission = new CompoundTag();
        oldEmission.put("bursts", immediateBursts);
        oldEmission.put("emissionRate", oldRate);
        oldEmission.putString("emissionMode", "Exacting");
        var emissionConfig = new com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting();
        emissionConfig.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(),
                com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapEmissionTag(oldEmission));
        var emissionEmitter = new ParticleEmitter();
        if (emissionConfig.getBursts().size() != 1 || emissionConfig.createRuntime()
                .getEmissionCount(emissionEmitter, emissionEmitter.getRandomSource()) != 7) {
            throw new IllegalStateException("A converted Photon 1 burst did not emit its particles");
        }
        // Omitted count uses Photon 1's default of 50, rather than an invalid empty function.
        var sparseBursts = new ListTag(); sparseBursts.add(new CompoundTag());
        var sparseEmissionTag = new CompoundTag(); sparseEmissionTag.put("bursts", sparseBursts);
        var sparseEmission = new com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting();
        sparseEmission.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(),
                com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapEmissionTag(sparseEmissionTag));
        if (sparseEmission.createRuntime().getEmissionCount(emissionEmitter, emissionEmitter.getRandomSource()) != 51)
            throw new IllegalStateException("Sparse legacy burst lost its default count or exact emission rate");
        var hugeBurst = immediate.copy(); hugeBurst.putInt("cycles", 0); hugeBurst.putInt("interval", 1);
        var singleCount = count.copy(); singleCount.putFloat("number", 1); hugeBurst.put("count", singleCount);
        var hugeBursts = new ListTag(); hugeBursts.add(hugeBurst);
        var hugeTag = oldEmission.copy(); hugeTag.put("bursts", hugeBursts);
        var zeroRate = oldRate.copy(); zeroRate.putFloat("number", 0); hugeTag.put("emissionRate", zeroRate);
        var hugeEmission = new com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting();
        hugeEmission.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(),
                com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapEmissionTag(hugeTag));
        emissionEmitter.setAge(Integer.MAX_VALUE);
        if (hugeEmission.createRuntime().getEmissionCount(emissionEmitter, emissionEmitter.getRandomSource(), 256f) != 256)
            throw new IllegalStateException("Large-age infinite bursts overflowed or fired the wrong number of times");
        emissionEmitter.setAge(0);
        var enabled = new CompoundTag(); enabled.putByte("enable", (byte) 1);
        enabled.put("force", new CompoundTag()); enabled.putFloat("frequency", 2f); enabled.put("gravity", oldRate.copy());
        if (!com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapForceOTTag(enabled).contains("force", Tag.TAG_LIST)
                || !com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapNoiseTag(enabled).contains("frequency")
                || !com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapPhysicsTag(enabled).contains("gravity"))
            throw new IllegalStateException("Photon 1 enabled module settings were lost during conversion");
        var oldSub = new CompoundTag(); oldSub.putInt("tickInterval", 512); oldSub.putString("emitter", "spark");
        var subList = new ListTag(); subList.add(oldSub);
        var sub = new CompoundTag(); sub.putByte("enable", (byte) 1); sub.put("emitters", subList);
        var mappedSub = com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapSubEmittersTag(sub).getCompound("emitters");
        if (mappedSub.getInt("uid") != 1 || mappedSub.getList("payload", Tag.TAG_COMPOUND).getCompound(0).getInt("tickInterval") != 512
                || !mappedSub.getList("payload", Tag.TAG_COMPOUND).getCompound(0).getString("legacySubFXName").equals("spark"))
            throw new IllegalStateException("Photon 1 sub-emitter list or interval was lost during conversion");
        var decodedSub = new SubEmittersSetting();
        decodedSub.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(),
                com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapSubEmittersTag(sub));
        if (decodedSub.getEmitters().size() != 1 || decodedSub.getEmitters().get(0).getTickInterval() != 512
                || decodedSub.getEmitters().get(0).getEvent() != SubEmittersSetting.Event.Birth
                || !decodedSub.getEmitters().get(0).isLegacyTickGate())
            throw new IllegalStateException("Sparse Photon 1 sub-emitter defaults or event gating were not retained");
        // Disabled Photon 1 modules still serialize authored values: preserve those values for
        // later editor activation and timeline overrides, including nested modules and lists.
        var disabledLegacy = sparseFlat.copy();
        var disabledPhysics = new CompoundTag(); disabledPhysics.putBoolean("enable", false);
        var gravity = count.copy(); gravity.putFloat("number", 2.5f); disabledPhysics.put("gravity", gravity);
        disabledLegacy.put("physics", disabledPhysics);
        var disabledSize = scalarOverLife.copy(); disabledSize.putBoolean("enable", false);
        disabledLegacy.put("sizeOverLifetime", disabledSize);
        var disabledUV = new CompoundTag(); disabledUV.putBoolean("enable", false); disabledUV.putFloat("cycle", 3f);
        var oldTiles = new CompoundTag(); oldTiles.putInt("a", 4); oldTiles.putInt("b", 2); disabledUV.put("tiles", oldTiles);
        disabledLegacy.put("uvAnimation", disabledUV);
        var disabledCull = oldCull.copy(); disabledCull.putBoolean("enable", false);
        var disabledRenderer = new CompoundTag(); disabledRenderer.put("cull", disabledCull);
        disabledLegacy.put("renderer", disabledRenderer);
        var disabledSub = sub.copy(); disabledSub.putBoolean("enable", false); disabledLegacy.put("subEmitters", disabledSub);
        var importedDisabled = com.lowdragmc.photon.client.gameobject.IFXObject.deserializeWrapper(
                com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapParticleEmitter(disabledLegacy));
        if (!(importedDisabled instanceof ParticleEmitter disabledEmitter))
            throw new IllegalStateException("Disabled-module legacy emitter did not deserialize");
        var disabledReload = com.lowdragmc.photon.client.gameobject.IFXObject.deserializeWrapper(disabledEmitter.serializeWrapper());
        if (!(disabledReload instanceof ParticleEmitter retained)) throw new IllegalStateException("Disabled-module emitter did not reload");
        if (retained.config.physics.isEnable() || retained.config.physics.getGravity().get(0f, () -> 0f).floatValue() != 2.5f)
            throw new IllegalStateException("Disabled legacy physics did not survive reload: " + retained.serializeWrapper().getCompound("data").getCompound("config").get("physics"));
        if (retained.config.sizeOverLifetime.isEnable() || !retained.config.sizeOverLifetime.getSize().get(0f, () -> 0f).equals(new org.joml.Vector3f(0.4f)))
            throw new IllegalStateException("Disabled legacy size did not survive reload: " + retained.serializeWrapper().getCompound("data").getCompound("config").get("sizeOverLifetime"));
        if (retained.config.uvAnimation.isEnable() || retained.config.uvAnimation.getCycle() != 3f || !retained.config.uvAnimation.getTiles().equals(new org.joml.Vector2i(4, 2)))
            throw new IllegalStateException("Disabled legacy UV did not survive reload: " + retained.serializeWrapper().getCompound("data").getCompound("config").get("uvAnimation"));
        if (retained.config.renderer.getCull().isEnable() || !retained.config.renderer.getCull().getCullBox().equals(new net.minecraft.world.phys.AABB(-8, -4, -2, 12, 6, 3)))
            throw new IllegalStateException("Disabled legacy cull bounds did not survive reload: " + retained.serializeWrapper().getCompound("data").getCompound("config").get("renderer"));
        if (retained.config.subEmitters.isEnable() || retained.config.subEmitters.getEmitters().size() != 1 || retained.config.subEmitters.getEmitters().get(0).getTickInterval() != 512)
            throw new IllegalStateException("Disabled legacy sub-emitters did not survive reload: " + retained.serializeWrapper().getCompound("data").getCompound("config").get("subEmitters"));
        var nestedRemap = new CompoundTag(); nestedRemap.putBoolean("enable", false); nestedRemap.put("remapCurve", count.copy());
        var disabledNoise = new CompoundTag(); disabledNoise.putBoolean("enable", false); disabledNoise.put("remap", nestedRemap);
        if (!com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapNoiseTag(disabledNoise)
                .getCompound("remap").contains("remapCurve"))
            throw new IllegalStateException("Disabled nested noise remap lost its curve");
        var parentEmitter = new ParticleEmitter();
        parentEmitter.setLevel(Minecraft.getInstance().level);
        var parentParticle = new com.lowdragmc.photon.client.gameobject.particle.TileParticle(parentEmitter, parentEmitter.config);
        parentParticle.setAge(4f);
        parentParticle.setLifetime(12);
        var spawned = new com.lowdragmc.photon.client.fx.FX();
        spawned.getFxData().objects().add(new ParticleEmitter());
        spawned.getFxData().objects().add(new TrailEmitter());
        spawned.getFxData().objects().add(new com.lowdragmc.photon.client.gameobject.emitter.beam.BeamEmitter());
        var spawnedRuntime = spawned.createRuntime();
        com.lowdragmc.photon.client.fx.IEffectExecutor testExecutor = () -> Minecraft.getInstance().level;
        spawnedRuntime.emit(testExecutor);
        var inherit = decodedSub.getEmitters().get(0);
        inherit.setInheritLifetime(true);
        inherit.setInheritDuration(true);
        inherit.applyInheritance(spawnedRuntime, parentParticle);
        for (var value : spawnedRuntime.objects.values()) {
            if (value instanceof com.lowdragmc.photon.client.gameobject.emitter.IParticleEmitter child
                    && (child.getAge() != 4 || child.self().getLifetime() != 12
                    || Math.abs(child.getT() - 4f / 12f) > 0.0001f
                    || Math.abs(child.getT(0.5f) - 4.5f / 12f) > 0.0001f)) {
                throw new IllegalStateException("Sub-emitter inherited age or duration did not reach its runtime: " + child);
            }
        }
        // A per-instance override must not edit the shared definition.
        if (((ParticleEmitter) spawned.getFxData().objects().get(0)).config.getDuration() == 12)
            throw new IllegalStateException("Sub-emitter inheritance mutated its shared FX definition");
        spawnedRuntime.destroy(true);
        var oldShader = new CompoundTag(); oldShader.putString("_type", "CustomShaderMaterial");
        oldShader.putString("shader", "photon:ci/tornado_smoke");
        var oldUniform = new CompoundTag();
        var oldShaderSamplers = new CompoundTag(); oldShaderSamplers.putString("3", "minecraft:textures/particle/flame.png");
        oldUniform.put("samplers", oldShaderSamplers);
        var oldValues = new CompoundTag(); var color = new ListTag(); color.add(FloatTag.valueOf(0.37f));
        oldValues.put("LegacyVariantOnly", color); oldUniform.put("uniforms", oldValues); oldShader.put("uniform", oldUniform);
        var mappedShader = com.lowdragmc.photon.client.fx.compat.MapperUtils.mapTypedValue(oldShader, false);
        var shaderBody = mappedShader.getCompound("data");
        if (!mappedShader.getString("type").equals("custom_shader")
                || !shaderBody.getString("shaderLocation").equals("photon:ci/tornado_smoke")
                || !shaderBody.getCompound("_additional").getCompound("shaderData").getCompound("samplers").getCompound("Sampler3")
                .getString("resource").equals("minecraft:textures/particle/flame.png")
                || shaderBody.getCompound("_additional").getCompound("shaderData").getCompound("uniforms").getList("LegacyVariantOnly", Tag.TAG_FLOAT).getFloat(0) != 0.37f)
            throw new IllegalStateException("Photon 1 custom shader, sampler or uniform was lost during conversion");
        var restoredLegacyShader = com.lowdragmc.photon.client.gameobject.emitter.data.material.IMaterial.deserializeWrapper(mappedShader);
        if (!(restoredLegacyShader instanceof CustomShaderMaterial restored) || restored.isCompiledError()
                || !restored.getShaderLocation().equals(new ResourceLocation("photon", "ci/tornado_smoke")))
            throw new IllegalStateException("Converted Photon 1 shader could not be loaded as a material");
        var restoredData = (CompoundTag) restored.serializeAdditionalNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry());
        var restoredUniform = restored.getShader(MaterialContext.PARTICLE_MODEL_INSTANCE).getUniform("LegacyVariantOnly");
        if (!restoredData.getCompound("shaderData").getCompound("samplers").getCompound("Sampler3")
                .getString("resource").equals("minecraft:textures/particle/flame.png")
                || restoredUniform == null || Math.abs(restoredUniform.getFloatBuffer().get(0) - 0.37f) > 0.0001f)
            throw new IllegalStateException("Converted Photon 1 shader sampler or variant uniform did not survive deserialization");
        // Photon 1's integer RandomConstant draws inclusively: [0,1] must sometimes emit 1,
        // rather than a decimal in [0,1) that is always truncated to zero by the burst.
        var oldRandom = new CompoundTag();
        oldRandom.putString("_type", "RandomConstant");
        oldRandom.putInt("a", 0); oldRandom.putInt("b", 1);
        oldRandom.putBoolean("isDecimals", false);
        var random = NumberFunction.deserializeWrapper(com.lowdragmc.photon.client.fx.compat.MapperUtils.mapTypedValue(oldRandom, false));
        if (random.get(0f, () -> 0.25f).intValue() != 0 || random.get(0f, () -> 0.75f).intValue() != 1
                || random.copy().get(0f, () -> 0.75f).intValue() != 1
                || NumberFunction.deserializeWrapper(random.serializeWrapper()).get(0f, () -> 0.75f).intValue() != 1) {
            throw new IllegalStateException("Photon 1 inclusive integer random range was lost during conversion, copy or save");
        }
        // Photon 1 sampled at ages 0,3,6 for an exact rate of 0.3. This matters for
        // short effects that end before a fractional accumulator reaches one particle.
        // A burst exactly on Photon 1's duration tick must run before the emitter is removed.
        // Decode an actual legacy object, copy it as a runtime would, and advance real particles.
        var boundaryBurst = immediate.copy(); boundaryBurst.putInt("time", 1);
        var boundaryBursts = new ListTag(); boundaryBursts.add(boundaryBurst);
        var boundaryEmission = oldEmission.copy(); boundaryEmission.put("bursts", boundaryBursts);
        var boundaryLegacy = new CompoundTag();
        boundaryLegacy.putString("_type", "particle");
        boundaryLegacy.putInt("duration", 1);
        boundaryLegacy.putBoolean("looping", false);
        var boundaryLife = new CompoundTag(); boundaryLife.putString("_type", "Constant"); boundaryLife.putInt("number", 2);
        boundaryLegacy.put("startLifetime", boundaryLife);
        boundaryLegacy.put("emission", boundaryEmission);
        var boundaryObject = com.lowdragmc.photon.client.gameobject.IFXObject.deserializeWrapper(
                com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapParticleEmitter(boundaryLegacy));
        if (!(boundaryObject instanceof ParticleEmitter convertedBoundary))
            throw new IllegalStateException("Legacy boundary emitter did not decode");
        var boundary = (ParticleEmitter) convertedBoundary.copy(false);
        boundary.setLevel(Minecraft.getInstance().level);
        boundary.update(1f);
        if (boundary.getParticleAmount() != 0 || boundary.getAge() != 1 || boundary.getT() != 0f)
            throw new IllegalStateException("Boundary emitter emitted, aged or sampled progress incorrectly before its burst");
        boundary.update(1f);
        if (boundary.getParticleAmount() != 7)
            throw new IllegalStateException("Photon 1 burst on the final duration tick did not emit: " + boundary.getParticleAmount());
        for (int i = 0; i < 4; i++) boundary.update(1f);
        if (boundary.getParticleAmount() != 0)
            throw new IllegalStateException("Legacy boundary particles did not drain after their lifetime");
        boundary.remove(true);
        var lifetimeOwner = new ParticleEmitter();
        lifetimeOwner.setLevel(Minecraft.getInstance().level);
        lifetimeOwner.config.emission.setLegacyTickEmission(true);
        var oldLife = new com.lowdragmc.photon.client.gameobject.particle.TileParticle(lifetimeOwner, lifetimeOwner.config);
        oldLife.setLifetime(1);
        oldLife.updateTick(1f);
        if (oldLife.isRemoved() || oldLife.getAge() != 1 || oldLife.getT(0) != 1f)
            throw new IllegalStateException("Legacy particle died before its final Photon 1 tick");
        oldLife.updateTick(1f);
        if (!oldLife.isRemoved() || oldLife.getAge() != 2 || oldLife.getT(0) != 2f)
            throw new IllegalStateException("Legacy particle did not complete its Photon 1 final tick");
        var nativeOwner = new ParticleEmitter(); nativeOwner.setLevel(Minecraft.getInstance().level);
        var nativeLife = new com.lowdragmc.photon.client.gameobject.particle.TileParticle(nativeOwner, nativeOwner.config);
        nativeLife.setLifetime(1);
        nativeLife.updateTick(1f); nativeLife.updateTick(1f);
        if (!nativeLife.isRemoved() || nativeLife.getT(0) != 1f)
            throw new IllegalStateException("Native Photon 2 particle lifetime changed with legacy compatibility");
        var nativeBoundary = new ParticleEmitter();
        nativeBoundary.setLevel(Minecraft.getInstance().level);
        nativeBoundary.config.setDuration(1);
        nativeBoundary.config.setLooping(false);
        nativeBoundary.config.emission.setEmissionRate(NumberFunction.constant(0));
        nativeBoundary.update(1f);
        if (nativeBoundary.isAlive())
            throw new IllegalStateException("Photon 2 emitter duration changed with legacy compatibility");
        var cadenceInput = oldEmission.copy();
        var slowRate = oldRate.copy(); slowRate.putFloat("number", 0.3f);
        cadenceInput.put("emissionRate", slowRate);
        cadenceInput.put("bursts", new ListTag());
        var cadence = new com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting();
        cadence.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(),
                com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapEmissionTag(cadenceInput));
        var cadenceRuntime = cadence.createRuntime();
        for (int age = 0; age < 7; age++) {
            emissionEmitter.setAge(age);
            int actual = cadenceRuntime.getEmissionCount(emissionEmitter, emissionEmitter.getRandomSource());
            if (actual != (age % 3 == 0 ? 1 : 0))
                throw new IllegalStateException("Photon 1 exact emission cadence changed at age " + age + ": " + actual);
        }
        var nativeEmission = new com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting();
        nativeEmission.setEmissionRate(NumberFunction.constant(0.3f));
        emissionEmitter.setAge(0);
        if (nativeEmission.createRuntime().getEmissionCount(emissionEmitter, emissionEmitter.getRandomSource()) != 0)
            throw new IllegalStateException("Native Photon 2 emission cadence was changed by legacy compatibility");
        var cadenceReloaded = new com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting();
        cadenceReloaded.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(),
                cadence.serializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry()));
        emissionEmitter.setAge(0);
        if (cadenceReloaded.createRuntime().getEmissionCount(emissionEmitter, emissionEmitter.getRandomSource()) != 1)
            throw new IllegalStateException("Photon 1 emission cadence was lost on save and reload");
        var onceBurst = new com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting();
        onceBurst.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(),
                com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapEmissionTag(oldEmission));
        emissionEmitter.config.setDuration(2);
        emissionEmitter.config.setLooping(true);
        emissionEmitter.setAge(0);
        var onceRuntime = onceBurst.createRuntime();
        int firstBurst = onceRuntime.getEmissionCount(emissionEmitter, emissionEmitter.getRandomSource());
        emissionEmitter.setAge(2);
        int secondBurst = onceRuntime.getEmissionCount(emissionEmitter, emissionEmitter.getRandomSource());
        if (firstBurst != 7 || secondBurst != 0)
            throw new IllegalStateException("A one-shot Photon 1 burst incorrectly repeated after the emitter loop");
        emissionEmitter.setAge(0);
        var randomBurst = immediate.copy(); randomBurst.put("count", oldRandom);
        var randomBursts = new ListTag(); randomBursts.add(randomBurst);
        var randomEmission = oldEmission.copy(); randomEmission.put("bursts", randomBursts);
        var convertedEmission = new com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting();
        convertedEmission.deserializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(),
                com.lowdragmc.photon.client.fx.compat.ParticleEmitterMapper.mapEmissionTag(randomEmission));
        var randomSource = net.minecraft.util.RandomSource.create(9821L);
        var emissionRuntime = convertedEmission.createRuntime();
        int emitted = 0;
        for (int i = 0; i < 128; i++) emitted += emissionRuntime.getEmissionCount(emissionEmitter, randomSource);
        if (emitted == 0 || emitted == 128) throw new IllegalStateException("Converted [0,1] burst did not produce both outcomes: " + emitted);
        oldRandom.putBoolean("isDecimals", true);
        var decimalRandom = NumberFunction.deserializeWrapper(com.lowdragmc.photon.client.fx.compat.MapperUtils.mapTypedValue(oldRandom, false));
        if (Math.abs(decimalRandom.get(0f, () -> 0.75f).floatValue() - 0.75f) > 0.0001f)
            throw new IllegalStateException("Photon 1 decimal random range was changed to integer");
        var oldCurve = new CompoundTag(); oldCurve.putString("_type", "RandomCurve");
        oldCurve.putByte("lockControlPoint", (byte) 0); oldCurve.putFloat("lower", 0.3f); oldCurve.putFloat("upper", 0.8f);
        var mappedCurve = com.lowdragmc.photon.client.fx.compat.MapperUtils.mapTypedValue(oldCurve, false);
        if (!mappedCurve.getString("type").equals("random_curve") || mappedCurve.getCompound("data").getBoolean("lockControlPoint")
                || mappedCurve.getCompound("data").getFloat("upper") != 0.8f)
            throw new IllegalStateException("Photon 1 random curve was not preserved");
        var oldGradient = new CompoundTag(); oldGradient.putString("_type", "RandomGradient");
        var gradientA = new CompoundTag(); var gradientB = new CompoundTag();
        for (var key : new String[]{"a", "r", "g", "b"}) {
            gradientA.put(key, floatList(0f, 0.25f, 1f, 0.75f));
            gradientB.put(key, floatList(0f, 0.75f, 1f, 0.25f));
        }
        oldGradient.put("a", gradientA); oldGradient.put("b", gradientB);
        var mappedGradient = com.lowdragmc.photon.client.fx.compat.MapperUtils.mapTypedValue(oldGradient, false);
        if (!mappedGradient.getString("type").equals("random_gradient")
                || mappedGradient.getCompound("data").getCompound("gradientColor0").getList("rgb", Tag.TAG_FLOAT).size() != 8
                || mappedGradient.getCompound("data").getCompound("gradientColor1").getList("rgb", Tag.TAG_FLOAT).size() != 8)
            throw new IllegalStateException("Photon 1 random gradients were not preserved");
        // a skipped file is reported by name, not swallowed: "converted everything and nothing appears" needs an answer
        Photon.LOGGER.info("[photon1-conversion-report-smoke] a second pass reported {} skip(s)", second.skipped().size());
        Photon.LOGGER.info("[photon1-conversion-smoke] legacy effects, bursts and enabled modules converted");
    }

    private static TrailParticle trailParticle(TrailEmitter emitter) throws ReflectiveOperationException {
        Field field = TrailEmitter.class.getDeclaredField("trailParticle");
        field.setAccessible(true);
        return (TrailParticle) field.get(emitter);
    }

    private static ListTag floatList(float... values) {
        var list = new ListTag();
        for (float value : values) list.add(FloatTag.valueOf(value));
        return list;
    }

    private static void assertLegacyHdr(CustomShaderMaterial material, float red, float intensity) {
        var uniform = material.getShader(MaterialContext.NORMAL).getUniform("colorHDR1");
        if (uniform == null || Math.abs(uniform.getFloatBuffer().get(0) - red) > 0.0001f
                || Math.abs(uniform.getFloatBuffer().get(3) - intensity) > 0.0001f) {
            throw new IllegalStateException("Legacy HDR uniform was changed to premultiplied form");
        }
    }

    @SuppressWarnings("unchecked")
    private static void enableLegacyTChannel(ParticleEmitter emitter) throws ReflectiveOperationException {
        var setting = emitter.config.additionalGPUDataSetting;
        setting.setEnable(true);
        Field field = setting.getClass().getDeclaredField("additionalData");
        field.setAccessible(true);
        ((Set<String>) field.get(setting)).add("addition_gpu_data.t");
    }

    @SuppressWarnings("unchecked")
    private static Object enabledChannelIds(ParticleEmitter emitter) throws ReflectiveOperationException {
        Field field = emitter.config.additionalGPUDataSetting.getClass().getDeclaredField("additionalData");
        field.setAccessible(true);
        return field.get(emitter.config.additionalGPUDataSetting);
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




