package com.lowdragmc.photon.client.fx.fxpack;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Minecraft model references shared by export and fxpack garbage collection. */
final class ModelAssetReferences {
    record Dependencies(List<ResourceLocation> models, List<ResourceLocation> assets) {}

    private ModelAssetReferences() {}

    static ResourceLocation modelFile(ResourceLocation id) {
        return new ResourceLocation(id.getNamespace(), "models/" + id.getPath() + ".json");
    }

    static Dependencies fromJson(JsonObject json) {
        var models = new LinkedHashSet<ResourceLocation>();
        var assets = new LinkedHashSet<ResourceLocation>();
        if (json.has("parent") && json.get("parent").isJsonPrimitive()) {
            var parent = ResourceLocation.tryParse(json.get("parent").getAsString());
            if (parent != null && !parent.getPath().startsWith("builtin/")) models.add(parent);
        }
        if (json.has("model") && json.get("model").isJsonPrimitive()) {
            var file = ResourceLocation.tryParse(json.get("model").getAsString());
            if (file != null && FXPacks.isPackableAsset(file.toString())) assets.add(file);
        }
        if (json.has("textures") && json.get("textures").isJsonObject()) {
            for (var entry : json.getAsJsonObject("textures").entrySet()) {
                if (!entry.getValue().isJsonPrimitive()) continue;
                var value = entry.getValue().getAsString();
                if (value.startsWith("#")) continue;
                var texture = ResourceLocation.tryParse(value);
                if (texture == null) continue;
                var path = texture.getPath();
                if (!path.startsWith("textures/")) path = "textures/" + path;
                if (!path.endsWith(".png")) path += ".png";
                assets.add(new ResourceLocation(texture.getNamespace(), path));
            }
        }
        if (json.has("overrides") && json.get("overrides").isJsonArray()) {
            for (var override : json.getAsJsonArray("overrides")) {
                if (!override.isJsonObject()) continue;
                var item = override.getAsJsonObject();
                if (!item.has("model") || !item.get("model").isJsonPrimitive()) continue;
                var model = ResourceLocation.tryParse(item.get("model").getAsString());
                if (model != null) models.add(model);
            }
        }
        return new Dependencies(List.copyOf(new ArrayList<>(models)), List.copyOf(new ArrayList<>(assets)));
    }
}
