package com.lowdragmc.photon.client.compat.gpu;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL21;

/**
 * Texture uploads that leave the GL texture state exactly as they found it.
 *
 * <p>Vanilla binds textures through {@link GlStateManager}'s per-unit cache and trusts it: a draw re-binds a unit only
 * when the cache says the texture changed. Photon uploads its data textures in the middle of the level render, on
 * whatever unit happens to be active. Binding there and leaving 0 behind is cache-consistent, but it is a change of
 * state the surrounding code never asked for, on a unit it may be in the middle of using; and any disagreement
 * between the cache and the driver (a failed bind, a raw call elsewhere) would then be paid for by whatever draws
 * next — terrain with the wrong texture, for instance. So an upload here always happens on unit 0, and when it is
 * done the unit's previous texture is bound again by name, read back from the driver, which also re-synchronises the
 * cache with reality; the active unit is restored last. Render thread only.
 */
@OnlyIn(Dist.CLIENT)
public final class GlUploads {
    private GlUploads() {
    }

    /**
     * Binds {@code texture} to {@code GL_TEXTURE_2D} on unit 0 with vanilla's unpack state reset, ready for
     * {@code glTexImage2D}/{@code glTexSubImage2D}. Returns the token {@link #restore} needs.
     */
    public static long bind(int texture) {
        RenderSystem.assertOnRenderThreadOrInit();
        int unit = GlStateManager._getActiveTexture();
        if (unit != GL13.GL_TEXTURE0) GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        int previous = Math.max(GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D), 0);
        // the cache is told what the driver has, so the bind below is issued even when the cache thought it was in place
        GlStateManager._bindTexture(previous);
        GlStateManager._bindTexture(texture);
        resetUnpack();
        return ((long) unit << 32) | (previous & 0xFFFFFFFFL);
    }

    /** Puts back what {@link #bind} found: unit 0's texture, then the active unit. */
    public static void restore(long token) {
        int unit = (int) (token >>> 32);
        int previous = (int) token;
        GlStateManager._bindTexture(previous);
        if (unit != GL13.GL_TEXTURE0) GlStateManager._activeTexture(unit);
    }

    /** Tightly packed client memory, no pixel unpack buffer: what every Photon upload assumes. */
    public static void resetUnpack() {
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 1);
        GlStateManager._glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
    }
}
