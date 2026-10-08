package com.lowdragmc.photon.client.render;

import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * 1.20.1-facing custom-uniform API. Values are applied to the linked ShaderInstance's ordinary uniforms;
 * this target has no {@code GpuBufferSlice} API for the 26.2 std140 upload path.
 */
public final class PhotonCustomUniforms implements AutoCloseable {
    public static final String UBO_NAME = "PhotonCustomMaterial";

    private final List<Field> fields;
    private final BiConsumer<String, float[]> setter;
    private final Runnable prepare;

    /** Public layout constructor retained for callers that only inspect or stage a uniform layout. */
    public PhotonCustomUniforms(List<Field> fields) {
        this(fields, (name, values) -> {}, () -> {});
    }

    public PhotonCustomUniforms(List<Field> fields, BiConsumer<String, float[]> setter, Runnable prepare) {
        this.fields = List.copyOf(fields);
        this.setter = setter;
        this.prepare = prepare;
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }

    public List<Field> fields() {
        return fields;
    }

    /** Stage values through the owning material, which persists and binds them to Forge shader uniforms. */
    public void set(String name, float... values) {
        if (name == null || values == null) return;
        setter.accept(name, values.clone());
    }

    /** Applies staged values to the current Forge ShaderInstance when one is already linked. */
    public void prepareUpload() {
        prepare.run();
    }

    /** No GPU buffer is owned by this 1.20.1 adapter. */
    @Override
    public void close() {
    }

    public enum Type {
        FLOAT(1), INT(1), VEC2(2), VEC3(3), VEC4(4), MAT4(16);

        public final int components;

        Type(int components) {
            this.components = components;
        }

        public static Type parse(String type) {
            if (type == null) return null;
            try {
                return valueOf(type.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    public record Field(String name, Type type) {}
}
