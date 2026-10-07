package com.lowdragmc.photon.client.fx.timeline;

import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/** Tracks whether a timeline sound follows a world position or stays listener-relative. */
final class TimelineSoundPosition {
    @Nullable
    private Supplier<Vector3f> supplier;

    TimelineSoundPosition(boolean attenuated, @Nullable Supplier<Vector3f> supplier) {
        update(attenuated ? supplier : null);
    }

    void update(@Nullable Supplier<Vector3f> supplier) {
        this.supplier = supplier;
    }

    boolean isPositional() {
        return supplier != null;
    }

    @Nullable
    Vector3f getPosition() {
        return supplier == null ? null : supplier.get();
    }
}
