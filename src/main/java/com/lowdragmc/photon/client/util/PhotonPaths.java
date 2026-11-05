package com.lowdragmc.photon.client.util;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.Photon;

import java.io.File;

/** Writable authoring folders owned by Photon, kept separate from LDLib2. */
public final class PhotonPaths {
    private PhotonPaths() {}

    /** {@code <gameDir>/photon}. */
    public static File getDataDir() {
        return new File(Platform.getGamePath().toFile(), Photon.MOD_ID);
    }

    /** Resource-pack layout: {@code <gameDir>/photon/assets/<namespace>/...}. */
    public static File getAssetsDir() {
        return new File(getDataDir(), "assets");
    }

    /** Editable .fxproj files, separate from assets that Minecraft loads. */
    public static File getProjectsDir() {
        return new File(getDataDir(), "projects");
    }

    /** LDLib2 Resource instances keep their per-type editor files here. */
    public static File getEditorResourcesDir() {
        return new File(getDataDir(), "resources/global");
    }

    public static void ensureDirectories() {
        getAssetsDir().mkdirs();
        getProjectsDir().mkdirs();
        getEditorResourcesDir().mkdirs();
    }
}
