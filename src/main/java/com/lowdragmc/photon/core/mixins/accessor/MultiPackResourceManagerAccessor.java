package com.lowdragmc.photon.core.mixins.accessor;

import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

/**
 * The two tables a reload builds once and every lookup reads: namespace → its pack stack, and the flat pack list
 * {@code listPacks()} answers from. {@code FxPackHotMount} replaces both wholesale (never edits them in place), so a
 * lookup on another thread sees either the old tables or the new ones, complete.
 */
@Mixin(MultiPackResourceManager.class)
public interface MultiPackResourceManagerAccessor {
    @Accessor("namespacedManagers")
    Map<String, FallbackResourceManager> photon$getNamespacedManagers();

    @Accessor("namespacedManagers")
    @Mutable
    void photon$setNamespacedManagers(Map<String, FallbackResourceManager> managers);

    @Accessor("packs")
    List<PackResources> photon$getPacks();

    @Accessor("packs")
    @Mutable
    void photon$setPacks(List<PackResources> packs);
}
