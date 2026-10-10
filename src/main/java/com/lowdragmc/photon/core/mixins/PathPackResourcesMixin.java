package com.lowdragmc.photon.core.mixins;

import com.lowdragmc.photon.Photon;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A resource-pack namespace must be a directory. Vanilla's PathPackResources included every
 * top-level entry, including LDLib2's standalone .fxproj files, then tried to walk those files as
 * directories during resource reloads (Create's train-hat listener exposed this in test-1 logs).
 */
@Mixin(PathPackResources.class)
public abstract class PathPackResourcesMixin {
    @Shadow @Final private Path root;

    /** @author Photon @reason Ignore non-directory entries when discovering resource namespaces. */
    @Overwrite
    public Set<String> getNamespaces(PackType type) {
        var namespaces = new HashSet<String>();
        var assetsRoot = root.resolve(type.getDirectory());
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(assetsRoot)) {
            for (var entry : entries) {
                if (!Files.isDirectory(entry)) continue;
                var name = entry.getFileName().toString();
                if (name.equals(name.toLowerCase(Locale.ROOT))) {
                    namespaces.add(name);
                } else {
                    Photon.LOGGER.warn("Ignored non-lowercase namespace: {} in {}", name, root);
                }
            }
        } catch (NoSuchFileException ignored) {
            // A pack without an assets/ or data/ directory has no namespaces of this type.
        } catch (IOException e) {
            Photon.LOGGER.error("Failed to list resource namespaces in {}", assetsRoot, e);
        }
        return namespaces;
    }
}
