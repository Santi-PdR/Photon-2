package com.lowdragmc.photon.client.compat.gpu;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Which of Photon's GPU paths a client may use; see {@link GpuCompat}.
 *
 * <ul>
 *   <li>{@link #AUTO} — the conservative pipeline on drivers Photon has seen hang or corrupt the frame (AMD's and
 *       Intel's Windows OpenGL drivers), everything elsewhere.</li>
 *   <li>{@link #FULL} — every path, whatever the driver.</li>
 *   <li>{@link #SAFE} — the conservative pipeline everywhere.</li>
 * </ul>
 */
public enum GpuProfile {
    AUTO,
    FULL,
    SAFE;

    public Component getTranslatedName() {
        return Component.translatable("photon.enum.gpu_profile." + name().toLowerCase(Locale.ROOT));
    }
}
