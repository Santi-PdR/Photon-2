package com.lowdragmc.photon.client.fx.timeline;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.util.PhotonPaths;
import net.minecraft.resources.ResourceLocation;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/** Imports user .ogg files into Photon's workspace and registers them through Minecraft's sounds.json format. */
public final class AudioAssetImporter {
    private static final String NAMESPACE = "photon";

    private AudioAssetImporter() {}

    /** Copies {@code source} to photon/assets and returns the dynamic sound event id to store in the FX. */
    public static ResourceLocation importOgg(File source) throws IOException {
        if (source == null || !source.isFile() || !source.getName().toLowerCase(Locale.ROOT).endsWith(".ogg")) {
            throw new IOException("Select a Minecraft .ogg sound file");
        }
        if (source.length() < 4) throw new IOException("The selected file is not a valid OGG stream");
        try (var input = Files.newInputStream(source.toPath())) {
            if (!java.util.Arrays.equals(input.readNBytes(4), new byte[]{'O', 'g', 'g', 'S'})) {
                throw new IOException("The selected file does not have an OGG container header");
            }
        }
        var baseName = source.getName().substring(0, source.getName().length() - 4)
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        if (baseName.isBlank()) baseName = "sound";
        var soundPath = "user/" + baseName;
        var id = new ResourceLocation(NAMESPACE, soundPath);
        var assets = PhotonPaths.getAssetsDir().toPath();
        var ogg = assets.resolve(NAMESPACE).resolve("sounds").resolve(soundPath + ".ogg");
        Files.createDirectories(ogg.getParent());
        Files.copy(source.toPath(), ogg, StandardCopyOption.REPLACE_EXISTING);

        var soundsJson = assets.resolve(NAMESPACE).resolve("sounds.json");
        Files.createDirectories(soundsJson.getParent());
        JsonObject root = Files.isRegularFile(soundsJson)
                ? JsonParser.parseString(Files.readString(soundsJson, StandardCharsets.UTF_8)).getAsJsonObject()
                : new JsonObject();
        qualifyLegacySoundNames(root);
        root.add(soundPath, soundDefinition(id));
        Files.writeString(soundsJson, root.toString(), StandardCharsets.UTF_8);

        Photon.LOGGER.info("Imported Photon audio {} from {}", id, source.getName());
        return id;
    }

    /** Repair custom sounds written by earlier Photon builds, before the workspace pack is mounted. */
    public static void repairLegacySoundDefinitions() {
        var soundsJson = PhotonPaths.getAssetsDir().toPath().resolve(NAMESPACE).resolve("sounds.json");
        if (!Files.isRegularFile(soundsJson)) return;
        try {
            var root = JsonParser.parseString(Files.readString(soundsJson, StandardCharsets.UTF_8)).getAsJsonObject();
            if (qualifyLegacySoundNames(root)) {
                Files.writeString(soundsJson, root.toString(), StandardCharsets.UTF_8);
                Photon.LOGGER.info("Repaired legacy Photon custom sound paths in {}", soundsJson);
            }
        } catch (IOException | RuntimeException failure) {
            Photon.LOGGER.warn("Could not repair legacy Photon custom sound paths in {}", soundsJson, failure);
        }
    }

    static boolean qualifyLegacySoundNames(JsonObject root) {
        var changed = false;
        for (var event : root.entrySet()) {
            var eventPath = event.getKey();
            if (!eventPath.startsWith("user/") || !event.getValue().isJsonObject()) continue;
            var sounds = event.getValue().getAsJsonObject().getAsJsonArray("sounds");
            if (sounds == null) continue;
            for (var value : sounds) {
                if (!value.isJsonObject()) continue;
                var sound = value.getAsJsonObject();
                var name = sound.get("name");
                if (name != null && name.isJsonPrimitive() && name.getAsString().equals(eventPath)) {
                    sound.addProperty("name", new ResourceLocation(NAMESPACE, eventPath).toString());
                    changed = true;
                }
            }
        }
        return changed;
    }

    static JsonObject soundDefinition(ResourceLocation id) {
        var definition = new JsonObject();
        var sounds = new JsonArray();
        var sound = new JsonObject();
        // A bare sound name is resolved in the minecraft namespace by SoundManager, even when
        // this sounds.json belongs to Photon. Qualify it so assets/photon/sounds/... is found.
        sound.addProperty("name", id.toString());
        sound.addProperty("stream", true);
        sounds.add(sound);
        definition.add("sounds", sounds);
        return definition;
    }
}
