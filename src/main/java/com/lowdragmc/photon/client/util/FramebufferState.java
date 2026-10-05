package com.lowdragmc.photon.client.util;

import com.lowdragmc.lowdraglib2.gui.ui.rendering.UISurface;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL30;

/** Captures GL framebuffer bindings without losing Iris' logical main-target state. Render thread only. */
@OnlyIn(Dist.CLIENT)
public final class FramebufferState {
    private final int readFramebuffer;
    private final int drawFramebuffer;
    @Nullable
    private final RenderTarget logicalDrawTarget;

    private FramebufferState(int readFramebuffer, int drawFramebuffer,
                             @Nullable RenderTarget logicalDrawTarget) {
        this.readFramebuffer = readFramebuffer;
        this.drawFramebuffer = drawFramebuffer;
        this.logicalDrawTarget = logicalDrawTarget;
    }

    public static FramebufferState capture() {
        int read = GlStateManager._getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        var surfaceTarget = UISurface.currentTarget();
        var logicalTarget = surfaceTarget != null && surfaceTarget.frameBufferId == draw
                ? surfaceTarget : null;
        return new FramebufferState(read, draw, logicalTarget);
    }

    /** Restore split read/draw bindings and, when applicable, Iris' tracked main-target binding. */
    public void restore() {
        if (logicalDrawTarget != null) {
            // RenderTarget.bindWrite is how Iris tracks its logical main target. It binds both GL
            // targets, so put the caller's independent READ binding back after restoring the draw.
            logicalDrawTarget.bindWrite(false);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        } else {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        }
    }
}
