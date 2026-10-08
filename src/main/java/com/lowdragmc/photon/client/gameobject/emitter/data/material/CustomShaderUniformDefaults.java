package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Reads the authored default values from a vanilla shader JSON uniform list. */
final class CustomShaderUniformDefaults {
    private CustomShaderUniformDefaults() {}

    static float[] read(JsonObject shaderJson, String name, int count) {
        if (count <= 0) return new float[0];
        var result = new float[count];
        if (shaderJson == null || name == null || !shaderJson.has("uniforms")
                || !shaderJson.get("uniforms").isJsonArray()) return result;

        for (JsonElement element : shaderJson.getAsJsonArray("uniforms")) {
            if (!element.isJsonObject()) continue;
            JsonObject uniform = element.getAsJsonObject();
            if (!uniform.has("name") || !uniform.get("name").isJsonPrimitive()
                    || !name.equals(uniform.get("name").getAsString())) continue;
            if (!uniform.has("values") || !uniform.get("values").isJsonArray()) return result;
            JsonArray values = uniform.getAsJsonArray("values");
            for (int i = 0; i < Math.min(count, values.size()); i++) {
                JsonElement value = values.get(i);
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) break;
                float component = value.getAsFloat();
                if (!Float.isFinite(component)) break;
                result[i] = component;
            }
            return result;
        }
        return result;
    }
}
