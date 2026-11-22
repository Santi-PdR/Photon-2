package com.lowdragmc.photon.client.fx.fxpack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.core.mixins.accessor.SoundBufferLibraryAccessor;
import com.lowdragmc.photon.core.mixins.accessor.SoundEngineAccessor;
import com.lowdragmc.photon.core.mixins.accessor.SoundManagerAccessor;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundEventRegistration;
import net.minecraft.client.resources.sounds.SoundEventRegistrationSerializer;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.client.sounds.Weighted;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.MultipliedFloats;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The sound side of {@link FxPackHotMount}. The sound manager only learns {@code sounds.json} events on a reload, and
 * the sound engine only ever reads {@code .ogg} files from the snapshot that reload took of them — a pack mounted in
 * the running manager would be silent. So this class does for one pack what the reload does for all: parses its
 * {@code sounds.json}, lists its sound files, and puts both into the manager's tables below whatever is already
 * there; and takes them out again when the pack is unmounted, stopping what plays them and forgetting their decoded
 * buffers, so a pack update is heard and not the version before it.
 */
@OnlyIn(Dist.CLIENT)
final class FxPackSounds {
    private static final Gson GSON = new GsonBuilder()
            .registerTypeHierarchyAdapter(Component.class, new Component.Serializer())
            .registerTypeAdapter(SoundEventRegistration.class, new SoundEventRegistrationSerializer()).create();
    private static final TypeToken<Map<String, SoundEventRegistration>> REGISTRATIONS = new TypeToken<>() {
    };
    /** Ticks between stopping the sounds of a removed file and deleting its buffer, so no channel still plays it. */
    private static final int DISCARD_DELAY_TICKS = 40;

    /** What a mounted pack put into the sound manager: only these are taken out again, and only if still in place. */
    private record Registered(Map<ResourceLocation, WeighedSoundEvents> events, Map<ResourceLocation, Resource> files) {
    }

    /** Game thread only. */
    private static final Map<PackResources, Registered> REGISTERED = new IdentityHashMap<>();

    private FxPackSounds() {
    }

    /** A reload rebuilt the sound manager's tables from the folder: nothing of ours is in them any more. */
    static void onResourceReload() {
        REGISTERED.clear();
    }

    /** Put the pack's sound events and files into the sound manager, below everything it already knows. */
    static void register(PackResources pack) {
        try {
            var soundManager = Minecraft.getInstance().getSoundManager();
            var access = (SoundManagerAccessor) soundManager;
            var contents = read(pack, soundManager);
            var files = new LinkedHashMap<ResourceLocation, Resource>();
            var cache = access.photon$getSoundCache();
            contents.files.forEach((id, resource) -> {
                if (cache.putIfAbsent(id, resource) == null) files.put(id, resource);
            });
            var events = new LinkedHashMap<ResourceLocation, WeighedSoundEvents>();
            var registry = access.photon$getRegistry();
            contents.events.forEach((id, event) -> {
                if (registry.putIfAbsent(id, event) == null) events.put(id, event);
            });
            REGISTERED.put(pack, new Registered(events, files));
            if (!events.isEmpty() || !files.isEmpty()) {
                Photon.LOGGER.info("fxpacks: {} brings {} sound events and {} sound files", pack.packId(), events.size(), files.size());
            }
        } catch (RuntimeException e) {
            Photon.LOGGER.warn("fxpacks: could not register the sounds of {}", pack.packId(), e);
        }
    }

    /**
     * Take the pack's sounds out of the sound manager: what {@link #register} put in, or — for a pack a reload
     * mounted — the events its {@code sounds.json} declares and the files the snapshot took from it.
     */
    static void unregister(PackResources pack) {
        try {
            var soundManager = Minecraft.getInstance().getSoundManager();
            var access = (SoundManagerAccessor) soundManager;
            var registry = access.photon$getRegistry();
            var cache = access.photon$getSoundCache();
            var removedEvents = new LinkedHashSet<ResourceLocation>();
            var removedFiles = new LinkedHashSet<ResourceLocation>();
            var registered = REGISTERED.remove(pack);
            if (registered != null) {
                registered.events.forEach((id, event) -> {
                    if (registry.remove(id, event)) removedEvents.add(id);
                });
                registered.files.forEach((id, resource) -> {
                    if (cache.remove(id, resource)) removedFiles.add(id);
                });
            } else {
                var contents = read(pack, soundManager);
                for (var id : contents.files.keySet()) {
                    var current = cache.get(id);
                    if (current != null && pack.packId().equals(current.sourcePackId()) && cache.remove(id, current)) {
                        removedFiles.add(id);
                    }
                }
                for (var id : contents.events.keySet()) {
                    if (registry.remove(id) != null) removedEvents.add(id);
                }
            }
            if (removedEvents.isEmpty() && removedFiles.isEmpty()) return;
            for (var id : removedEvents) {
                soundManager.stop(id, null);
            }
            var buffers = ((SoundBufferLibraryAccessor) ((SoundEngineAccessor) access.photon$getSoundEngine())
                    .photon$getSoundBuffers()).photon$getCache();
            for (var id : removedFiles) {
                var buffer = buffers.remove(id);
                if (buffer != null) {
                    FxPackHotMount.later(DISCARD_DELAY_TICKS, () -> buffer.thenAccept(SoundBuffer::discardAlBuffer));
                }
            }
            Photon.LOGGER.info("fxpacks: {} took {} sound events and {} sound files with it", pack.packId(),
                    removedEvents.size(), removedFiles.size());
        } catch (RuntimeException e) {
            Photon.LOGGER.warn("fxpacks: could not unregister the sounds of {}", pack.packId(), e);
        }
    }

    private record PackSounds(Map<ResourceLocation, WeighedSoundEvents> events, Map<ResourceLocation, Resource> files) {
    }

    /** The pack's sound files and the events its {@code sounds.json} files declare, built the way a reload builds them. */
    private static PackSounds read(PackResources pack, SoundManager soundManager) {
        // a manager over this one pack: vanilla's own listing and parsing, scoped to it (never closed: that closes the pack)
        var solo = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(pack));
        var files = Sound.SOUND_LISTER.listMatchingResources(solo);
        var events = new LinkedHashMap<ResourceLocation, WeighedSoundEvents>();
        for (var namespace : solo.getNamespaces()) {
            var definitions = solo.getResource(new ResourceLocation(namespace, "sounds.json")).orElse(null);
            if (definitions == null) continue;
            try (var reader = definitions.openAsReader()) {
                Map<String, SoundEventRegistration> registrations = GsonHelper.fromJson(GSON, reader, REGISTRATIONS);
                if (registrations == null) continue;
                for (var entry : registrations.entrySet()) {
                    var id = ResourceLocation.tryBuild(namespace, entry.getKey());
                    if (id == null) {
                        Photon.LOGGER.warn("fxpacks: {} declares a sound event with an invalid name {}", pack.packId(), entry.getKey());
                        continue;
                    }
                    var registration = entry.getValue();
                    var event = events.get(id);
                    if (event == null || registration.isReplace()) {
                        event = new WeighedSoundEvents(id, registration.getSubtitle());
                        events.put(id, event);
                    }
                    for (var sound : registration.getSounds()) {
                        switch (sound.getType()) {
                            case FILE -> {
                                if (!files.containsKey(sound.getPath())) {
                                    Photon.LOGGER.warn("fxpacks: {} declares {} for event {} but has no such file",
                                            pack.packId(), sound.getPath(), id);
                                    continue;
                                }
                                event.addSound(sound);
                            }
                            case SOUND_EVENT -> event.addSound(new EventReference(sound, soundManager));
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                Photon.LOGGER.warn("fxpacks: invalid sounds.json in {} for namespace {}", pack.packId(), namespace, e);
            }
        }
        return new PackSounds(events, files);
    }

    /**
     * A {@code "type": "event"} entry: resolved against the sound manager at play time, as vanilla's own are, so the
     * referenced event may come from any pack and may arrive after this one.
     */
    private record EventReference(Sound reference, SoundManager soundManager) implements Weighted<Sound> {
        @Override
        public int getWeight() {
            var event = soundManager.getSoundEvent(reference.getLocation());
            return event == null ? 0 : event.getWeight();
        }

        @Override
        public Sound getSound(RandomSource random) {
            var event = soundManager.getSoundEvent(reference.getLocation());
            if (event == null) return SoundManager.EMPTY_SOUND;
            var resolved = event.getSound(random);
            return new Sound(resolved.getLocation().toString(),
                    new MultipliedFloats(reference.getVolume(), resolved.getVolume()),
                    new MultipliedFloats(reference.getPitch(), resolved.getPitch()),
                    reference.getWeight(), Sound.Type.FILE, reference.shouldStream() || resolved.shouldStream(),
                    reference.shouldPreload(), reference.getAttenuationDistance());
        }

        @Override
        public void preloadIfRequired(SoundEngine engine) {
            var event = soundManager.getSoundEvent(reference.getLocation());
            if (event != null) event.preloadIfRequired(engine);
        }
    }
}
