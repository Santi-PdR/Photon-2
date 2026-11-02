package com.lowdragmc.photon.client.fx.fxpack;

import com.google.gson.JsonParser;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.GltfMeshParser;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;

/** External geometry buffers used by glTF models; shared by pack export and GC. */
final class GltfAssetReferences {
    private GltfAssetReferences() {}

    static List<ResourceLocation> buffers(ResourceLocation model, byte[] bytes) throws IOException {
        int offset = 0;
        int length = bytes.length;
        if (model.getPath().endsWith(".glb")) {
            if (bytes.length < 20) throw new IOException("truncated GLB header");
            var header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if (header.getInt(0) != 0x46546c67 || header.getInt(16) != 0x4e4f534a)
                throw new IOException("invalid GLB JSON chunk");
            length = header.getInt(12);
            offset = 20;
            if (length < 0 || length > bytes.length - offset) throw new IOException("truncated GLB JSON chunk");
        }
        try {
            var json = JsonParser.parseString(new String(bytes, offset, length, StandardCharsets.UTF_8)).getAsJsonObject();
            var result = new LinkedHashSet<ResourceLocation>();
            if (json.has("buffers") && json.get("buffers").isJsonArray()) {
                for (var buffer : json.getAsJsonArray("buffers")) {
                    if (!buffer.isJsonObject() || !buffer.getAsJsonObject().has("uri")) continue;
                    var uri = buffer.getAsJsonObject().get("uri").getAsString();
                    if (!uri.startsWith("data:"))
                        result.add(GltfMeshParser.resolveRelativeBufferLocation(model, uri));
                }
            }
            return List.copyOf(result);
        } catch (RuntimeException e) {
            throw new IOException("invalid glTF JSON", e);
        }
    }
}
