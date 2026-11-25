package com.lowdragmc.photon.core.mixins.accessor;

import net.minecraft.server.packs.resources.FallbackResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * One namespace's pack stack, lowest priority first. Its entries are {@code FallbackResourceManager.PackEntry}
 * records, a type this package cannot name, hence the raw list; {@link FallbackPackEntryAccessor} reads them.
 * {@code FxPackHotMount} only ever fills the list of a manager it just created, never one lookups are running on.
 */
@Mixin(FallbackResourceManager.class)
public interface FallbackResourceManagerAccessor {
    @Accessor("fallbacks")
    List<Object> photon$getFallbacks();
}
