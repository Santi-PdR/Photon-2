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
        collect(json, models, assets);
        return new Dependencies(List.copyOf(new ArrayList<>(models)), List.copyOf(new ArrayList<>(assets)));
    }

    private static void collect(JsonObject json, LinkedHashSet<ResourceLocation> models,
                                LinkedHashSet<ResourceLocation> assets) {
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
                addTexture(entry.getValue().getAsString(), assets);
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
        if (json.has("elements") && json.get("elements").isJsonArray()) {
            for (var element : json.getAsJsonArray("elements")) {
                if (!element.isJsonObject() || !element.getAsJsonObject().has("faces")
                        || !element.getAsJsonObject().get("faces").isJsonObject()) continue;
                for (var face : element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                    if (!face.getValue().isJsonObject()) continue;
                    var value = face.getValue().getAsJsonObject().get("texture");
                    if (value != null && value.isJsonPrimitive()) addTexture(value.getAsString(), assets);
                }
            }
        }
        if (json.has("children") && json.get("children").isJsonObject()) {
            for (var child : json.getAsJsonObject("children").entrySet())
                if (child.getValue().isJsonObject()) collect(child.getValue().getAsJsonObject(), models, assets);
        }
    }

    private static void addTexture(String value, LinkedHashSet<ResourceLocation> assets) {
        if (value.startsWith("#")) return;
        var texture = ResourceLocation.tryParse(value);
        if (texture == null) return;
        var path = texture.getPath();
        if (!path.startsWith("textures/")) path = "textures/" + path;
        if (!path.endsWith(".png")) path += ".png";
        assets.add(new ResourceLocation(texture.getNamespace(), path));
    }
}
