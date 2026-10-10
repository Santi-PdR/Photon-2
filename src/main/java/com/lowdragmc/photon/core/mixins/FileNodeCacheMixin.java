package com.lowdragmc.photon.core.mixins;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.util.FileNode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.nio.file.Path;

/**
 * Avoid rescanning large LDLib2 asset directories several times per second while a file dialog or
 * the editor's asset browser is open. Directory timestamp changes still invalidate the listing
 * immediately when files are added or removed.
 */
@Mixin(value = FileNode.class, remap = false)
public abstract class FileNodeCacheMixin {
    // The directory timestamp already invalidates a listing when entries are added or removed.
    // Keep a longer fallback for filesystems with coarse or unreliable directory timestamps;
    // five-second expiry caused the open dialog and asset browser to rescan unchanged folders
    // repeatedly while they stayed open.
    private static final long PHOTON_ASSET_CACHE_NS = 60_000_000_000L;

    @Shadow @Final public File key;
    @Shadow private long cacheStamp;
    @Shadow private long cacheTime;

    @Inject(method = "isCacheStale", at = @At("HEAD"), cancellable = true, remap = false)
    private void photon$cacheAssetListings(CallbackInfoReturnable<Boolean> cir) {
        Path assetsDir = LDLib2.getAssetsDir().toPath().toAbsolutePath().normalize();
        Path directory = key.toPath().toAbsolutePath().normalize();
        if (!directory.startsWith(assetsDir)) return;

        boolean stale = System.nanoTime() - cacheTime >= PHOTON_ASSET_CACHE_NS
                || key.lastModified() != cacheStamp;
        cir.setReturnValue(stale);
    }
}
