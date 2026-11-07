package com.lowdragmc.photon.client.gameobject.emitter.data;

import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.SearchComponentConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Editing and saving lists of block or entity ids and {@code #tags}, for the settings that filter by them. */
@OnlyIn(Dist.CLIENT)
final class IdLists {
    private IdLists() {
    }

    static Configurator blockEntry(Supplier<String> getter, Consumer<String> setter) {
        return search("block", getter, setter, consumer -> list(BuiltInRegistries.BLOCK, consumer));
    }

    static Configurator entityEntry(Supplier<String> getter, Consumer<String> setter) {
        return search("entity", getter, setter, consumer -> list(BuiltInRegistries.ENTITY_TYPE, consumer));
    }

    /** The ids and {@code #tags} of a registry, tags first, in the order the search hands them over. */
    private static void list(Registry<?> registry, Consumer<String> consumer) {
        var candidates = new ArrayList<String>();
        registry.getTagNames().forEach(tag -> candidates.add("#" + tag.location()));
        registry.keySet().forEach(id -> candidates.add(id.toString()));
        candidates.sort(String::compareTo);
        candidates.forEach(consumer);
    }

    private static Configurator search(String name, Supplier<String> getter, Consumer<String> setter, Consumer<Consumer<String>> candidates) {
        return new SearchComponentConfigurator<>(name, getter, setter, "", true,
                // the registry is read per search rather than captured once, so a row that outlives a data
                // reload (or a configurator built before the registries were frozen) still lists everything
                (word, handler) -> {
                    var search = word.toLowerCase(Locale.ROOT);
                    candidates.accept(candidate -> {
                        if (Thread.currentThread().isInterrupted()) return;
                        if (candidate.toLowerCase(Locale.ROOT).contains(search)) handler.accept(candidate);
                    });
                },
                s -> s,
                UIElementProvider.text(s -> Component.literal(s == null || s.isEmpty() ? "---" : s)));
    }

    static ListTag serialize(List<String> values) {
        var list = new ListTag();
        for (var value : values) list.add(StringTag.valueOf(value == null ? "" : value));
        return list;
    }

    static List<String> deserialize(ListTag tag) {
        var values = new ArrayList<String>();
        for (Tag element : tag) values.add(element.getAsString());
        return values;
    }
}
