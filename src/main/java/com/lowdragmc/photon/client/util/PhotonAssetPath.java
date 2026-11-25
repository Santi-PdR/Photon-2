package com.lowdragmc.photon.client.util;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/** Converts selected files under a conventional assets directory into resource identifiers. */
public final class PhotonAssetPath {
    private PhotonAssetPath() {}

    @Nullable
    public static ResourceLocation fromFile(Path preferredAssetsRoot, Path filePath) {
        var raw = rawId(preferredAssetsRoot, filePath);
        return raw == null ? null : ResourceLocation.tryParse(raw);
    }

    /**
     * The {@code namespace:path} a file under an assets tree names, before resource-id validation: null when the
     * file is not inside any {@code assets/<namespace>/} folder. {@link #fromFile} is {@code tryParse} of this, so a
     * caller can tell "not under assets" from "the name has characters a resource id cannot use" (uppercase,
     * spaces, accents) and say so instead of silently doing nothing.
     */
    @Nullable
    public static String rawId(Path preferredAssetsRoot, Path filePath) {
        Path selected = filePath.toAbsolutePath().normalize();
        Path root = preferredAssetsRoot.toAbsolutePath().normalize();
        Path relative;
        if (selected.startsWith(root)) {
            relative = root.relativize(selected);
        } else {
            // File pickers open at the game directory so legacy ldlib/assets and ldlib2/assets
            // content remains selectable while new files default to photon/assets.
            int index = -1;
            for (int i = 0; i < selected.getNameCount(); i++) {
                if (selected.getName(i).toString().equals("assets")) index = i;
            }
            if (index < 0 || index + 2 >= selected.getNameCount()) return null;
            relative = selected.subpath(index + 1, selected.getNameCount());
        }
        if (relative.getNameCount() < 2) return null;
        StringBuilder id = new StringBuilder(relative.getName(0).toString()).append(':');
        for (int i = 1; i < relative.getNameCount(); i++) {
            if (i > 1) id.append('/');
            id.append(relative.getName(i));
        }
        return id.toString();
    }

    /**
     * A valid resource id as close as possible to {@code raw} (what {@link #rawId} produced for a file whose name a
     * resource id cannot carry): lower-cased, accents stripped, anything else outside {@code a-z 0-9 _ - . /}
     * replaced by {@code _}. What to suggest the author rename the file (and folders) to; null when even that is
     * not a valid id.
     */
    @Nullable
    public static String suggestedId(String raw) {
        var colon = raw.indexOf(':');
        var namespace = colon < 0 ? "" : raw.substring(0, colon);
        var path = colon < 0 ? raw : raw.substring(colon + 1);
        var candidate = (namespace.isEmpty() ? "" : sanitizeSegment(namespace, false) + ":") + sanitizeSegment(path, true);
        return ResourceLocation.tryParse(candidate) == null ? null : candidate;
    }

    private static String sanitizeSegment(String text, boolean allowSlash) {
        var folded = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "") // the accents split off by NFD
                .toLowerCase(java.util.Locale.ROOT);
        return folded.replaceAll(allowSlash ? "[^a-z0-9/._-]" : "[^a-z0-9._-]", "_");
    }

    @Nullable
    public static ResourceLocation shaderLocationFromFile(Path assetsRoot, Path file) {
        var asset = fromFile(assetsRoot, file);
        if (asset == null || !asset.getPath().endsWith(".json")) return null;
        String prefix = "shaders/core/";
        int index = asset.getPath().indexOf(prefix);
        if (index < 0) return null;
        String shader = asset.getPath().substring(index + prefix.length());
        if (!shader.endsWith(".json") || shader.length() <= 5) return null;
        return ResourceLocation.tryParse(asset.getNamespace() + ":" + shader.substring(0, shader.length() - 5));
    }

    @Nullable
    public static ResourceLocation modelLocationFromFile(Path assetsRoot, Path file) {
        var asset = fromFile(assetsRoot, file);
        if (asset == null || !asset.getPath().startsWith("models/") || !asset.getPath().endsWith(".json")) return null;
        String model = asset.getPath().substring(7, asset.getPath().length() - 5);
        return model.isEmpty() ? null : ResourceLocation.tryParse(asset.getNamespace() + ":" + model);
    }
}
