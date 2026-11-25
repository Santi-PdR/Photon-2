package com.lowdragmc.photon.core.mixins;

import com.lowdragmc.lowdraglib2.utils.virtuallevel.DummyWorld;
import com.lowdragmc.photon.client.virtuallevel.VirtualLevelTickGuard;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.BooleanSupplier;

/**
 * Stops LDLib2's virtual levels from posting Forge's {@code LevelTickEvent}.
 *
 * <p>{@code DummyWorld.tickWorld()} (LDLib2 2.2.39.a, lines 316 and 321) calls
 * {@code ForgeEventFactory.onPreLevelTick} and {@code onPostLevelTick} with the virtual level, so every
 * {@code LevelTickEvent} listener on the instance receives a {@code Level} that is neither a {@code ClientLevel}
 * nor a {@code ServerLevel} while an LDLib2 scene is on screen. Brutality 0.8.7 casts it and the FX editor died
 * with a "Ticking screen" {@code ClassCastException}; any mod with the same cast does the same. Both calls are
 * redirected to {@link VirtualLevelTickGuard#suppressed(Level)}, which only counts them; nothing else in the tick
 * changes. The redirects are {@code require = 0}: should a future LDLib2 build tick its virtual levels differently,
 * the game must start anyway (the editor's own scene is protected regardless by
 * {@link com.lowdragmc.photon.client.virtuallevel.EditorSceneLevel}) and the client smoke test reports the gap.</p>
 */
@Mixin(value = DummyWorld.class, remap = false)
public abstract class DummyWorldMixin {
    @Redirect(method = "tickWorld",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/event/ForgeEventFactory;onPreLevelTick(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V"),
            remap = false, require = 0, expect = 1)
    private void photon$skipPreLevelTick(Level level, BooleanSupplier haveTime) {
        VirtualLevelTickGuard.suppressed(level);
    }

    @Redirect(method = "tickWorld",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/event/ForgeEventFactory;onPostLevelTick(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V"),
            remap = false, require = 0, expect = 1)
    private void photon$skipPostLevelTick(Level level, BooleanSupplier haveTime) {
        VirtualLevelTickGuard.suppressed(level);
    }
}
