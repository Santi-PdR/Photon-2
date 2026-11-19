package com.lowdragmc.photon.client.fx.timeline;

import net.minecraft.resources.ResourceLocation;

/** Regression check for custom OGG paths in namespaced sounds.json files. */
public final class AudioAssetImporterRegression {
    private AudioAssetImporterRegression() {}

    public static void verifyNamespacedSoundPath() {
        var id = new ResourceLocation("photon", "user/import_probe");
        var definition = AudioAssetImporter.soundDefinition(id);
        var actual = definition.getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString();
        if (!id.toString().equals(actual)) {
            throw new IllegalStateException("Imported sound path lost its namespace: " + actual);
        }

        var legacy = new com.google.gson.JsonObject();
        var event = new com.google.gson.JsonObject();
        var sounds = new com.google.gson.JsonArray();
        var oldSound = new com.google.gson.JsonObject();
        oldSound.addProperty("name", id.getPath());
        sounds.add(oldSound);
        event.add("sounds", sounds);
        legacy.add(id.getPath(), event);
        if (!AudioAssetImporter.qualifyLegacySoundNames(legacy)
                || !id.toString().equals(legacy.getAsJsonObject(id.getPath()).getAsJsonArray("sounds")
                .get(0).getAsJsonObject().get("name").getAsString())) {
            throw new IllegalStateException("Legacy custom sound definitions were not upgraded to their namespace");
        }
    }
}
