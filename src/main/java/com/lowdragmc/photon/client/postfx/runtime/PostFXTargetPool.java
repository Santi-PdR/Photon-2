package com.lowdragmc.photon.client.postfx.runtime;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.client.shader.HDRTarget;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.postfx.graph.TargetFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL46;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The transient render-target pool for post-processing passes (RGBA16F, linear filter, no depth).
 * {@link #release} returns a target to its size bucket <b>immediately</b>, so a later pass in the same
 * frame reuses it — that same-frame reuse IS the aliasing mechanism (GL draws are ordered on one
 * context, no sync needed); a 6-level bloom pyramid peaks at ~3 live targets. Free targets persist
 * across frames (LIFO — the hottest target first) and are destroyed after {@link #EVICT_AFTER_FRAMES}
 * frames unused ({@link #endFrame}) or on window resize ({@link #invalidateAll}). Render thread only.
 */
@OnlyIn(Dist.CLIENT)
public final class PostFXTargetPool {

    private static final class Pooled {
        final HDRTarget target;
        long lastUsedFrame;

        Pooled(HDRTarget target, long lastUsedFrame) {
            this.target = target;
            this.lastUsedFrame = lastUsedFrame;
        }
    }

    /** (width, height, format) -> free targets of that shape, most recently used last. */
    private static final Map<Long, ArrayDeque<Pooled>> FREE = new HashMap<>();
    /** Allocation failures are remembered per shape to avoid retrying/logging the same failure each frame. */
    private static final Set<Long> FAILED = new HashSet<>();
    private static long FRAME_ID;
    /** Bytes in FREE, maintained as targets enter and leave instead of rescanning the pool each frame. */
    private static long freeBytes;
    /** ~2 s at 60 fps: absorbs editor-vs-game size flips without hoarding stale sizes. */
    private static final int EVICT_AFTER_FRAMES = 120;

    private PostFXTargetPool() {}

    /** The current frame index — the effect stack's once-per-frame consumption guard keys on it. */
    public static long currentFrame() {
        return FRAME_ID;
    }

    @Nullable
    public static HDRTarget acquire(int width, int height) {
        return acquire(width, height, TargetFormat.RGBA16F);
    }

    /** Pop a free target of exactly {@code width}×{@code height}×{@code format} or allocate one.
     *  Caller owns it until {@link #release}. */
    @Nullable
    public static HDRTarget acquire(int width, int height, TargetFormat format) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        var targetKey = key(width, height, format);
        var bucket = FREE.get(targetKey);
        if (bucket != null) {
            var pooled = bucket.pollLast();
            if (pooled != null) {
                freeBytes -= byteSize(pooled.target);
                if (bucket.isEmpty()) FREE.remove(targetKey);
                return pooled.target;
            }
        }
        if (FAILED.contains(targetKey)) return null;
        try {
            if (width > RenderSystem.maxSupportedTextureSize() || height > RenderSystem.maxSupportedTextureSize()) {
                throw new IllegalArgumentException("target exceeds the maximum supported texture size");
            }
            var target = format == TargetFormat.RGBA16F
                    ? new HDRTarget(width, height, GL11.GL_LINEAR, false)
                    : new FormatTarget(width, height, GL11.GL_LINEAR, format);
            target.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            labelTarget(target, width, height, format);
            return target;
        } catch (RuntimeException e) {
            FAILED.add(targetKey);
            Photon.LOGGER.error("Could not allocate {}x{} {} post-effect target; skipping the effect chain for this frame",
                    width, height, format, e);
            return null;
        }
    }

    /** Return {@code target} to the pool — legal (and intended) mid-frame, enabling aliasing. */
    public static void release(@Nullable HDRTarget target) {
        if (target == null) return;
        var format = target instanceof FormatTarget formatTarget ? formatTarget.getFormat() : TargetFormat.RGBA16F;
        var pooled = new Pooled(target, FRAME_ID);
        FREE.computeIfAbsent(key(target.width, target.height, format), k -> new ArrayDeque<>()).addLast(pooled);
        freeBytes += byteSize(target);
    }

    /** Advance the frame clock, destroy free targets untouched for {@link #EVICT_AFTER_FRAMES},
     *  then enforce the configured VRAM budget over the remaining free targets (oldest-first). */
    public static void endFrame() {
        FRAME_ID++;
        var buckets = FREE.entrySet().iterator();
        while (buckets.hasNext()) {
            var bucket = buckets.next().getValue();
            // Buckets are oldest-first, so stale targets can be evicted from the head.
            while (!bucket.isEmpty() && FRAME_ID - bucket.peekFirst().lastUsedFrame > EVICT_AFTER_FRAMES) {
                destroy(bucket.pollFirst());
            }
            if (bucket.isEmpty()) buckets.remove();
        }
        enforceBudget();
    }

    private static void destroy(Pooled pooled) {
        freeBytes -= byteSize(pooled.target);
        pooled.target.destroyBuffers();
    }

    /** Evict oldest free targets while the pool exceeds the configured budget (in-use targets are
     *  frame-transient and never counted — the cap bounds what persists across frames). */
    private static void enforceBudget() {
        long budgetBytes = com.lowdragmc.photon.PhotonConfig.INSTANCE.postFxPoolBudgetMB.get() * 1024L * 1024L;
        while (freeBytes > budgetBytes) {
            ArrayDeque<Pooled> oldestBucket = null;
            for (var bucket : FREE.values()) {
                var oldest = bucket.peekFirst();
                if (oldest != null && (oldestBucket == null
                        || oldest.lastUsedFrame < oldestBucket.peekFirst().lastUsedFrame)) {
                    oldestBucket = bucket;
                }
            }
            if (oldestBucket == null) return;
            destroy(oldestBucket.pollFirst());
        }
        FREE.values().removeIf(ArrayDeque::isEmpty);
    }

    private static long byteSize(HDRTarget target) {
        var format = target instanceof FormatTarget formatTarget ? formatTarget.getFormat() : TargetFormat.RGBA16F;
        long bytesPerPixel = switch (format) {
            case RGBA16F -> 8;
            case RGBA8, RG16F -> 4;
            case R16F -> 2;
            case R8 -> 1;
        };
        return (long) target.width * target.height * bytesPerPixel;
    }

    /** Destroy every free target (window resize — screen-relative sizes all change at once). */
    public static void invalidateAll() {
        FREE.values().forEach(bucket -> bucket.forEach(pooled -> pooled.target.destroyBuffers()));
        FREE.clear();
        freeBytes = 0;
        FAILED.clear();
    }

    private static long key(int width, int height, TargetFormat format) {
        return ((long) width << 36) | ((long) height << 8) | format.ordinal();
    }

    /** RenderDoc-friendly names on the pooled FBO + color texture (dev only, same guard as bloom). */
    private static void labelTarget(HDRTarget target, int width, int height, TargetFormat format) {
        if (!Platform.isDevEnv() || !GL.getCapabilities().GL_KHR_debug) return;
        var label = "photonfx_pool %dx%d %s".formatted(width, height, format.name());
        GL46.glObjectLabel(GL30.GL_FRAMEBUFFER, target.frameBufferId, label);
        GL46.glObjectLabel(GL11.GL_TEXTURE, target.getColorTextureId(), label);
    }
}
