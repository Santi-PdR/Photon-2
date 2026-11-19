package com.lowdragmc.photon.client.fx;

import com.lowdragmc.photon.client.gameobject.FXInstanceObject;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import com.lowdragmc.photon.client.gameobject.light.LifetimeFXObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashSet;
import java.util.Set;

/** Client-side test used only for late-join replay: one-shot FX must not be restarted from the beginning. */
@OnlyIn(Dist.CLIENT)
public final class FXLooping {
    private FXLooping() {
    }

    public static boolean hasLoopingContent(FX fx) {
        return hasLoopingContent(fx, new HashSet<>());
    }

    private static boolean hasLoopingContent(FX fx, Set<ResourceLocation> visited) {
        var id = fx.getFxLocation();
        if (id != null && !visited.add(id)) return false;

        for (IFXObject object : fx.getFxData().objects()) {
            if (object instanceof Emitter emitter && emitter.isLooping()) return true;
            if (object instanceof LifetimeFXObject lifetime && lifetime.isLoopingEffect()) return true;
            if (object instanceof FXInstanceObject instance && instance.getFx() != null) {
                var nested = FXHelper.getFX(instance.getFx());
                if (nested != null && hasLoopingContent(nested, visited)) return true;
            }
        }
        for (var subFx : fx.getLegacySubFXs().values()) {
            if (hasLoopingContent(subFx, visited)) return true;
        }
        // A forever section keeps revisiting its content even when its individual emitters are one-shot.
        return fx.getFxData().timeline().sections().stream()
                .anyMatch(section -> section.repeat() == com.lowdragmc.photon.client.fx.timeline.TimelineSection.FOREVER);
    }
}
