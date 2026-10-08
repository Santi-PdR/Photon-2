package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lowdragmc.photon.client.render.PhotonCustomUniforms;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Reads the authored default values from a vanilla shader JSON uniform list. */
final class CustomShaderUniformDefaults {
    private static final Set<String> BUILTIN_UNIFORMS = Set.of(
            "ModelViewMat", "ProjMat", "IViewRotMat", "ColorModulator", "FogStart",
            "FogEnd", "FogColor", "FogShape", "GameTime", "ScreenSize", "LineWidth");

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

    static List<PhotonCustomUniforms.Field> fields(JsonObject shaderJson) {
        if (shaderJson == null || !shaderJson.has("uniforms") || !shaderJson.get("uniforms").isJsonArray()) {
            return List.of();
        }
        var fields = new ArrayList<PhotonCustomUniforms.Field>();
        for (JsonElement element : shaderJson.getAsJsonArray("uniforms")) {
            if (!element.isJsonObject()) continue;
            JsonObject uniform = element.getAsJsonObject();
            if (!uniform.has("name") || !uniform.get("name").isJsonPrimitive()) continue;
            String name = uniform.get("name").getAsString();
            if (name.isBlank() || name.startsWith("U_") || BUILTIN_UNIFORMS.contains(name)) continue;
            String typeName = uniform.has("type") && uniform.get("type").isJsonPrimitive()
                    ? uniform.get("type").getAsString() : "float";
            int count = uniform.has("count") && uniform.get("count").isJsonPrimitive()
                    ? uniform.get("count").getAsInt() : 1;
            PhotonCustomUniforms.Type type = switch (typeName) {
                case "matrix4x4" -> PhotonCustomUniforms.Type.MAT4;
                case "int" -> PhotonCustomUniforms.Type.INT;
                default -> switch (count) {
                    case 1 -> PhotonCustomUniforms.Type.FLOAT;
                    case 2 -> PhotonCustomUniforms.Type.VEC2;
                    case 3 -> PhotonCustomUniforms.Type.VEC3;
                    case 4 -> PhotonCustomUniforms.Type.VEC4;
                    case 16 -> PhotonCustomUniforms.Type.MAT4;
                    default -> null;
                };
            };
            if (type != null) fields.add(new PhotonCustomUniforms.Field(name, type));
        }
        return List.copyOf(fields);
    }
}
