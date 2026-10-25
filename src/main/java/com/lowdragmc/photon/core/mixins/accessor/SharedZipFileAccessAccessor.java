package com.lowdragmc.photon.core.mixins.accessor;

import net.minecraft.server.packs.FilePackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.io.File;

@Mixin(targets = "net.minecraft.server.packs.FilePackResources$SharedZipFileAccess")
public interface SharedZipFileAccessAccessor {
    @Accessor
    File getFile();

    @Accessor
    void setFailedToLoad(boolean failedToLoad);
}
