package com.lowdragmc.photon.core.mixins;

import com.lowdragmc.lowdraglib2.utils.virtuallevel.DummyWorld;
import com.lowdragmc.photon.core.PhotonSceneTickContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.ForgeEventFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.BooleanSupplier;

/** Keeps LDLib2's editor preview world from dispatching real-world Forge tick events. */
@Mixin(value = DummyWorld.class, remap = false)
public abstract class DummyWorldMixin {
    @Redirect(
            method = "tickWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/event/ForgeEventFactory;onPreLevelTick(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V"
            )
    )
    private void photon$redirectPreLevelTick(Level level, BooleanSupplier hasTime) {
        if (!PhotonSceneTickContext.shouldSuppress(level)) {
            ForgeEventFactory.onPreLevelTick(level, hasTime);
        }
    }

    @Redirect(
            method = "tickWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/event/ForgeEventFactory;onPostLevelTick(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V"
            )
    )
    private void photon$redirectPostLevelTick(Level level, BooleanSupplier hasTime) {
        if (!PhotonSceneTickContext.shouldSuppress(level)) {
            ForgeEventFactory.onPostLevelTick(level, hasTime);
        }
    }
}
