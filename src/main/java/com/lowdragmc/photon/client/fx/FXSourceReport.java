package com.lowdragmc.photon.client.fx;

import com.lowdragmc.lowdraglib2.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Read-only provenance: a listed FX may be supplied by several resource packs at once. */
public final class FXSourceReport {
    private FXSourceReport() { }

    public static List<String> describe(ResourceLocation id) {
        var manager = Minecraft.getInstance().getResourceManager();
        var file = FXHelper.fileLocation(id);
        var stack = manager.getResourceStack(file);
        var selected = manager.getResource(file).orElse(null);
        var lines = new ArrayList<String>();
        lines.add("FX " + id + " (" + file + ")");
        if (selected == null) {
            lines.add("Not found in the currently loaded resource packs.");
            return List.copyOf(lines);
        }
        lines.add("Selected pack: " + selected.sourcePackId());
        lines.add("All sources, lowest to highest priority (removing one may reveal another):");
        for (var resource : stack) {
            var pack = resource.sourcePackId();
            var location = knownFile(Platform.getGamePath(), pack, id);
            lines.add("  " + pack + (location == null ? " (resource pack or mod; inspect its contents)" : " -> " + location));
        }
        return List.copyOf(lines);
    }

    /** Only return physical paths for packs whose mount roots Photon controls. */
    public static Path knownFile(Path gameDir, String pack, ResourceLocation id) {
        var root = switch (pack) {
            case "photon/workspace" -> gameDir.resolve("photon");
            case "fxpack/editor-ldlib2" -> gameDir.resolve("ldlib2");
            case "fxpack/legacy-ldlib" -> gameDir.resolve("ldlib");
            default -> null;
        };
        return root == null ? null : root.resolve("assets").resolve(id.getNamespace())
                .resolve(FXHelper.FX_PATH).resolve(id.getPath() + FX.SUFFIX);
    }
}
