package com.lowdragmc.photon.core.mixins.accessor;

import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The manager a reload installed and every lookup goes through; see {@code FxPackHotMount}. */
@Mixin(ReloadableResourceManager.class)
public interface ReloadableResourceManagerAccessor {
    @Accessor("resources")
    CloseableResourceManager photon$getResources();
}
