package com.lowdragmc.photon.client.fx.timeline;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigPropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigValueType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * Regression: an animated property this build cannot read (unregistered type, or a {@code config}
 * property whose {@code valueType} comes from a newer or edited file) is skipped with a warning; it
 * must never fail the whole track's load, because that would take the entire effect down with it.
 */
public final class AnimatedPropertyLoadRegression {
    private AnimatedPropertyLoadRegression() {}

    public static void verify() {
        var provider = Platform.getFrozenRegistry();
        var properties = new ListTag();
        properties.add(configProperty("photon_probe.unknown_value_type", "FROM_A_NEWER_BUILD"));
        properties.add(configProperty("photon_probe.readable", "FLOAT"));
        var unregistered = new CompoundTag();
        unregistered.putString("type", "photon_probe_unregistered_property");
        properties.add(unregistered);
        var data = new CompoundTag();
        data.putString("name", "probe");
        data.put("properties", properties);

        var track = new AnimationTrack();
        try {
            track.readData(provider, data);
        } catch (RuntimeException e) {
            throw new IllegalStateException("An unreadable animated property failed the whole track load", e);
        }
        if (track.properties().size() != 1) {
            throw new IllegalStateException("Expected only the readable property to survive the load, got "
                    + track.properties().size());
        }
        var loaded = track.properties().get(0);
        if (!(loaded.type() instanceof ConfigPropertyType config) || config.valueType() != ConfigValueType.FLOAT) {
            throw new IllegalStateException("The readable property did not load as a FLOAT config property: " + loaded.type());
        }
    }

    private static CompoundTag configProperty(String path, String valueType) {
        var tag = new CompoundTag();
        tag.putString("type", "config");
        tag.putString("path", path);
        tag.putString("valueType", valueType);
        tag.putString("label", path);
        return tag;
    }
}
