package com.lowdragmc.photon.client.render;

import com.lowdragmc.lowdraglib2.client.UIRenderStateScope;
import com.lowdragmc.lowdraglib2.client.shader.HDRTarget;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.gameobject.emitter.data.MaterialSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.IMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.MaterialContext;
import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline;
import com.lowdragmc.photon.client.postprocessing.ResourceDisposal;
import com.lowdragmc.photon.client.render.PhotonShaderDefaults;
import com.lowdragmc.photon.client.util.FramebufferState;
import com.lowdragmc.photon.core.mixins.accessor.BlendModeAccessor;
import com.lowdragmc.photon.core.mixins.accessor.ShaderInstanceAccessor;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Off-screen custom-material previews for the Forge 1.20.1 render backend.
 * GUI callbacks only enqueue requests and draw an existing texture; shader draws and target
 * allocation run at {@link net.minecraftforge.client.event.RenderGuiEvent.Pre}.
 */
@OnlyIn(Dist.CLIENT)
public final class MaterialPreviewRenderer {
    private static final int TILE_SIZE = 64;
    private static final int MIN_LIVE_SIZE = 32;
    private static final int MAX_LIVE_SIZE = 512;
    private static final int MAX_ENTRIES = 64;
    private static final int MAX_TILE_RENDERS_PER_FRAME = 8;
    private static final int MAX_LIVE_RENDERS_PER_FRAME = 8;
    private static final long REFRESH_MS = 100;
    private static final long RETRY_MS = 3000;
    private static final long LIVE_STALE_MS = 1000;
    private static final long IDLE_RELEASE_MS = 5000;
    private static final BlendMode PREVIEW_BLEND = new BlendMode(
            GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
            GL11.GL_ONE, GL11.GL_ZERO, GL14.GL_FUNC_ADD);
    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private static final Map<IMaterial, Entry> TILES = new IdentityHashMap<>();
    private static final Set<IMaterial> TILE_REQUESTS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<IMaterial, Entry> LIVE = new IdentityHashMap<>();
    private static final Map<IMaterial, Integer> LIVE_REQUESTS = new IdentityHashMap<>();
    private static final Map<IMaterial, Long> FAILED_UNTIL = new IdentityHashMap<>();
    private static final List<Entry> RELEASE_NEXT_FRAME = new ArrayList<>();
    private static final ThreadLocal<Boolean> PREVIEW_RENDER_ACTIVE = ThreadLocal.withInitial(() -> false);
    @Nullable
    private static HDRTarget PREVIEW_SCENE_TARGET;
    private static long lastRequestMs;

    private MaterialPreviewRenderer() {}

    private static final class Entry {
        final ResourceLocation id;
        final HDRTarget target;
        final int size;
        long lastUsed;
        long lastRendered;

        Entry(int size) {
            this.size = size;
            this.id = Photon.id("material_preview/" + NEXT_ID.getAndIncrement());
            this.target = new HDRTarget(size, size, GL11.GL_LINEAR, true);
            this.target.setClearColor(0, 0, 0, 0);
        }

        void close() {
            target.destroyBuffers();
        }
    }

    public static IGuiTexture previewOf(IMaterial material) {
        return new PreviewTexture(material, false);
    }

    public static IGuiTexture livePreviewOf(IMaterial material) {
        return new PreviewTexture(material, true);
    }

    /** Called before GUI draw, after any previous GUI batch has been flushed. */
    public static void processPending() {
        if (!RenderSystem.isOnRenderThread()) return;
        RELEASE_NEXT_FRAME.forEach(Entry::close);
        RELEASE_NEXT_FRAME.clear();
        long now = System.currentTimeMillis();
        if (TILE_REQUESTS.isEmpty() && LIVE_REQUESTS.isEmpty()) {
            if ((!TILES.isEmpty() || !LIVE.isEmpty() || !FAILED_UNTIL.isEmpty())
                    && now - lastRequestMs > IDLE_RELEASE_MS) releaseAll();
            return;
        }
        processLive(now);
        processTiles(now);
        evictOverflow();
    }

    public static void releaseAll() {
        TILES.values().forEach(Entry::close);
        LIVE.values().forEach(Entry::close);
        RELEASE_NEXT_FRAME.forEach(Entry::close);
        TILES.clear();
        LIVE.clear();
        RELEASE_NEXT_FRAME.clear();
        TILE_REQUESTS.clear();
        LIVE_REQUESTS.clear();
        FAILED_UNTIL.clear();
        if (PREVIEW_SCENE_TARGET != null) {
            PREVIEW_SCENE_TARGET.destroyBuffers();
            PREVIEW_SCENE_TARGET = null;
        }
    }

    /** Scene-sampler stand-ins for preview shaders; the preview has no particle render pipeline. */
    public static int previewSceneColorTexture() {
        if (!PREVIEW_RENDER_ACTIVE.get()) return -1;
        var captured = RenderPassPipeline.lastCapturedSceneSamplersForPreview();
        if (captured != null) return captured.colorTexture();
        return PREVIEW_SCENE_TARGET != null ? PREVIEW_SCENE_TARGET.getColorTextureId() : -1;
    }

    /** Scene-sampler stand-ins for preview shaders; depth is cleared to the far plane. */
    public static int previewSceneDepthTexture() {
        if (!PREVIEW_RENDER_ACTIVE.get()) return -1;
        var captured = RenderPassPipeline.lastCapturedSceneSamplersForPreview();
        if (captured != null) return captured.depthTexture();
        return PREVIEW_SCENE_TARGET != null ? PREVIEW_SCENE_TARGET.getDepthTextureId() : -1;
    }

    private static void ensurePreviewSceneTarget() {
        if (PREVIEW_SCENE_TARGET != null) return;
        var framebuffers = FramebufferState.capture();
        int viewportX = com.mojang.blaze3d.platform.GlStateManager.Viewport.x();
        int viewportY = com.mojang.blaze3d.platform.GlStateManager.Viewport.y();
        int viewportWidth = com.mojang.blaze3d.platform.GlStateManager.Viewport.width();
        int viewportHeight = com.mojang.blaze3d.platform.GlStateManager.Viewport.height();
        HDRTarget target = null;
        try (var state = UIRenderStateScope.capture()) {
            target = new HDRTarget(1, 1, GL11.GL_NEAREST, true);
            target.setClearColor(0, 0, 0, 0);
            target.bindWrite(true);
            target.clear(Minecraft.ON_OSX);
            PREVIEW_SCENE_TARGET = target;
        } catch (RuntimeException error) {
            if (target != null) target.destroyBuffers();
            throw error;
        } finally {
            framebuffers.restore();
            RenderSystem.viewport(viewportX, viewportY, viewportWidth, viewportHeight);
        }
    }

    private static void processLive(long now) {
        var requests = new ArrayList<>(LIVE_REQUESTS.entrySet());
        LIVE_REQUESTS.clear();
        int budget = MAX_LIVE_RENDERS_PER_FRAME;
        for (var request : requests) {
            if (budget <= 0) break;
            var material = request.getKey();
            if (isCoolingDown(material, now)) continue;
            int size = request.getValue();
            var entry = LIVE.get(material);
            if (entry != null && entry.size != size) {
                entry.close();
                LIVE.remove(material);
                entry = null;
            }
            budget--;
            var rendered = render(material, entry, size, now);
            if (rendered == null) {
                LIVE.remove(material);
                fail(material, now);
            } else {
                LIVE.put(material, rendered);
                FAILED_UNTIL.remove(material);
            }
        }
        LIVE.entrySet().removeIf(e -> {
            if (now - e.getValue().lastUsed <= LIVE_STALE_MS) return false;
            e.getValue().close();
            return true;
        });
    }

    private static void processTiles(long now) {
        if (TILE_REQUESTS.isEmpty()) return;
        var requested = new ArrayList<>(TILE_REQUESTS);
        TILE_REQUESTS.clear();
        int budget = MAX_TILE_RENDERS_PER_FRAME;
        var refresh = new ArrayList<Map.Entry<IMaterial, Entry>>();
        for (var material : requested) {
            if (isCoolingDown(material, now)) continue;
            var entry = TILES.get(material);
            if (entry != null) {
                entry.lastUsed = now;
                if (now - entry.lastRendered >= REFRESH_MS) refresh.add(Map.entry(material, entry));
                continue;
            }
            if (budget <= 0) {
                TILE_REQUESTS.add(material);
                continue;
            }
            budget--;
            var rendered = render(material, null, TILE_SIZE, now);
            if (rendered == null) fail(material, now);
            else {
                TILES.put(material, rendered);
                FAILED_UNTIL.remove(material);
            }
        }
        refresh.sort(Comparator.comparingLong(e -> e.getValue().lastRendered));
        for (var item : refresh) {
            if (budget <= 0) break;
            budget--;
            var rendered = render(item.getKey(), item.getValue(), TILE_SIZE, now);
            if (rendered == null) {
                TILES.remove(item.getKey());
                fail(item.getKey(), now);
            } else {
                TILES.put(item.getKey(), rendered);
                FAILED_UNTIL.remove(item.getKey());
            }
        }
    }

    @Nullable
    private static Entry render(IMaterial material, @Nullable Entry entry, int size, long now) {
        try {
            if (entry == null) entry = new Entry(size);
            renderInto(material, entry);
            entry.lastUsed = now;
            entry.lastRendered = now;
            return entry;
        } catch (RuntimeException error) {
            Photon.LOGGER.warn("Material preview render failed: {}", material.getClass().getSimpleName(), error);
            if (entry != null) RELEASE_NEXT_FRAME.add(entry);
            return null;
        }
    }

    private static void renderInto(IMaterial material, Entry entry) {
        var mc = Minecraft.getInstance();
        ensurePreviewSceneTarget();
        var target = entry.target;
        var previewSetting = new MaterialSetting(material);
        var framebuffers = FramebufferState.capture();
        int viewportX = com.mojang.blaze3d.platform.GlStateManager.Viewport.x();
        int viewportY = com.mojang.blaze3d.platform.GlStateManager.Viewport.y();
        int viewportWidth = com.mojang.blaze3d.platform.GlStateManager.Viewport.width();
        int viewportHeight = com.mojang.blaze3d.platform.GlStateManager.Viewport.height();
        ShaderInstance shader = null;
        boolean began = false;
        boolean projectionBackedUp = false;
        boolean modelViewPushed = false;
        boolean materialSettingApplied = false;
        boolean previousPreviewRenderActive = PREVIEW_RENDER_ACTIVE.get();
            var lightTexture = mc.gameRenderer.lightTexture();
        BlendMode materialBlend = null;
        Throwable operationFailure = null;
        try (var state = UIRenderStateScope.capture()) {
            PREVIEW_RENDER_ACTIVE.set(true);
            target.bindWrite(true);
            target.clear(Minecraft.ON_OSX);
            RenderSystem.viewport(0, 0, entry.size, entry.size);
            RenderSystem.backupProjectionMatrix();
            projectionBackedUp = true;
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, entry.size, entry.size, 0,
                    -1, 1), VertexSorting.ORTHOGRAPHIC_Z);
            var modelView = RenderSystem.getModelViewStack();
            modelView.pushPose();
            modelViewPushed = true;
            modelView.setIdentity();
            RenderSystem.applyModelViewMatrix();
            shader = material.begin(MaterialContext.PREVIEW);
            began = true;
            if (shader == null) throw new IllegalStateException("material returned no preview shader");
            var shaderAccessor = (ShaderInstanceAccessor) shader;
            materialBlend = shaderAccessor.photon$getBlend();
            shaderAccessor.photon$setBlend(PREVIEW_BLEND);
            lightTexture.turnOnLightLayer();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            PhotonShaderDefaults.setup(shader);
            var previewShader = shader;
            RenderSystem.setShader(() -> previewShader);
            previewSetting.pre();
            materialSettingApplied = true;
            BlendModeAccessor.photon$setLastApplied(null);
            var buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            var pose = new Matrix4f();
            // Counter-clockwise in projected coordinates, so MaterialSetting's default back-face
            // culling retains the quad while matching the real particle draw state.
            buffer.vertex(pose, 0, 0, 0).color(-1).uv(0, 0).uv2(LightTexture.FULL_BRIGHT).normal(0, 0, 1).endVertex();
            buffer.vertex(pose, 0, entry.size, 0).color(-1).uv(0, 1).uv2(LightTexture.FULL_BRIGHT).normal(0, 0, 1).endVertex();
            buffer.vertex(pose, entry.size, entry.size, 0).color(-1).uv(1, 1).uv2(LightTexture.FULL_BRIGHT).normal(0, 0, 1).endVertex();
            buffer.vertex(pose, entry.size, 0, 0).color(-1).uv(1, 0).uv2(LightTexture.FULL_BRIGHT).normal(0, 0, 1).endVertex();
            BufferUploader.drawWithShader(buffer.end());
        } catch (Throwable error) {
            operationFailure = error;
            throw error;
        } finally {
            var finalShader = shader;
            var finalBlend = materialBlend;
            boolean finalBegan = began;
            boolean finalModelViewPushed = modelViewPushed;
            boolean finalMaterialSettingApplied = materialSettingApplied;
            boolean finalPreviewRenderActive = previousPreviewRenderActive;
            boolean finalProjectionBackedUp = projectionBackedUp;
            Runnable restoreBlend = finalShader != null && finalBlend != null
                    ? () -> ((ShaderInstanceAccessor) finalShader).photon$setBlend(finalBlend)
                    : () -> {};
            ResourceDisposal.runAllPreservingFailure(operationFailure,
                    restoreBlend,
                    () -> { if (finalBegan) material.end(MaterialContext.PREVIEW); },
                    () -> { if (finalMaterialSettingApplied) previewSetting.post(); },
                    lightTexture::turnOffLightLayer,
                    () -> {
                        var modelView = RenderSystem.getModelViewStack();
                        if (finalModelViewPushed) {
                            modelView.popPose();
                            RenderSystem.applyModelViewMatrix();
                        }
                    },
                    () -> { if (finalProjectionBackedUp) RenderSystem.restoreProjectionMatrix(); },
                    framebuffers::restore,
                    () -> RenderSystem.viewport(viewportX, viewportY, viewportWidth, viewportHeight),
                    () -> PREVIEW_RENDER_ACTIVE.set(finalPreviewRenderActive));
        }
    }

    private static void fail(IMaterial material, long now) {
        FAILED_UNTIL.putIfAbsent(material, now + RETRY_MS);
    }

    private static boolean isCoolingDown(IMaterial material, long now) {
        var retryAt = FAILED_UNTIL.get(material);
        return retryAt != null && now < retryAt;
    }

    private static void evictOverflow() {
        while (TILES.size() > MAX_ENTRIES) {
            var oldest = TILES.entrySet().stream().min(Comparator.comparingLong(e -> e.getValue().lastUsed)).orElse(null);
            if (oldest == null) return;
            oldest.getValue().close();
            TILES.remove(oldest.getKey());
        }
    }

    static int targetPixels(float width, float height, double scale) {
        int pixels = (int) Math.ceil(Math.max(width, height) * scale);
        pixels = Math.max(MIN_LIVE_SIZE, Math.min(MAX_LIVE_SIZE, pixels));
        return (pixels + 15) / 16 * 16;
    }

    private static final class PreviewTexture implements IGuiTexture {
        private final IMaterial material;
        private final boolean live;

        private PreviewTexture(IMaterial material, boolean live) {
            this.material = material;
            this.live = live;
        }

        @Override
        public void draw(GuiGraphics graphics, float mouseX, float mouseY,
                         float x, float y, float width, float height, float partialTicks) {
            graphics.flush();
            long now = System.currentTimeMillis();
            lastRequestMs = now;
            var entry = live ? LIVE.get(material) : TILES.get(material);
            if (live) LIVE_REQUESTS.put(material,
                    targetPixels(width, height, Minecraft.getInstance().getWindow().getGuiScale()));
            else {
                TILE_REQUESTS.add(material);
                if (entry != null) entry.lastUsed = now;
            }
            try (var state = UIRenderStateScope.capture()) {
                if (entry == null) {
                    IGuiTexture.MISSING_TEXTURE.draw(graphics, mouseX, mouseY, x, y, width, height, partialTicks);
                    return;
                }
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                RenderSystem.setShaderTexture(0, entry.target.getColorTextureId());
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                var mat = graphics.pose().last().pose();
                var buffer = Tesselator.getInstance().getBuilder();
                buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                // FBO textures have a bottom-left origin; flip V so the preview reads top-to-bottom in GUI space.
                buffer.vertex(mat, x, y + height, 0).uv(0, 0).color(-1).endVertex();
                buffer.vertex(mat, x + width, y + height, 0).uv(1, 0).color(-1).endVertex();
                buffer.vertex(mat, x + width, y, 0).uv(1, 1).color(-1).endVertex();
                buffer.vertex(mat, x, y, 0).uv(0, 1).color(-1).endVertex();
                BufferUploader.drawWithShader(buffer.end());
            }
        }
    }
}
