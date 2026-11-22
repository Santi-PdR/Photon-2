package com.lowdragmc.photon.core.mixins.accessor;

import net.minecraft.server.packs.PackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.jetbrains.annotations.Nullable;

/**
 * Which pack an entry of a namespace's stack reads from (null for a filter-only entry). The record is
 * package-private in vanilla, so it is reached by name.
 */
@Mixin(targets = "net.minecraft.server.packs.resources.FallbackResourceManager$PackEntry")
public interface FallbackPackEntryAccessor {
    @Accessor("resources")
    @Nullable
    PackResources photon$getResources();
}
