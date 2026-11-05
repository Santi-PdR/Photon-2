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
        var assets = PhotonPaths.getAssetsDir().toPath();
        var ogg = assets.resolve(NAMESPACE).resolve("sounds").resolve(soundPath + ".ogg");
        Files.createDirectories(ogg.getParent());
        Files.copy(source.toPath(), ogg, StandardCopyOption.REPLACE_EXISTING);

        var soundsJson = assets.resolve(NAMESPACE).resolve("sounds.json");
        Files.createDirectories(soundsJson.getParent());
        JsonObject root = Files.isRegularFile(soundsJson)
                ? JsonParser.parseString(Files.readString(soundsJson, StandardCharsets.UTF_8)).getAsJsonObject()
                : new JsonObject();
        var definition = new JsonObject();
        var sounds = new JsonArray();
        var sound = new JsonObject();
        sound.addProperty("name", soundPath);
        sound.addProperty("stream", true);
        sounds.add(sound);
        definition.add("sounds", sounds);
        root.add(soundPath, definition);
        Files.writeString(soundsJson, root.toString(), StandardCharsets.UTF_8);

        var id = new ResourceLocation(NAMESPACE, soundPath);
        Photon.LOGGER.info("Imported Photon audio {} from {}", id, source.getName());
        return id;
    }
}
