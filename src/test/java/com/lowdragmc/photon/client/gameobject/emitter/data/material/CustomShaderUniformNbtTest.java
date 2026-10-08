package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomShaderUniformNbtTest {
    @Test
    void roundTripsOverrideComponents() {
        Map<String, float[]> source = Map.of("GlowColor", new float[]{0.25f, 0.5f, 0.75f, 1f});
        Map<String, float[]> restored = new HashMap<>();

        CustomShaderUniformNbt.readInto(restored, CustomShaderUniformNbt.write(source));

        assertArrayEquals(source.get("GlowColor"), restored.get("GlowColor"));
    }

    @Test
    void missingOrMalformedDataClearsPriorOverrides() {
        Map<String, float[]> restored = new HashMap<>();
        restored.put("Stale", new float[]{1f});

        CustomShaderUniformNbt.readInto(restored, EndTag.INSTANCE);

        assertTrue(restored.isEmpty());

        var malformed = new CompoundTag();
        var uniforms = new CompoundTag();
        uniforms.put("WrongTag", IntTag.valueOf(1));
        var wrongNumberKind = new ListTag();
        wrongNumberKind.add(IntTag.valueOf(1));
        uniforms.put("WrongNumberKind", wrongNumberKind);
        var tooMany = new ListTag();
        for (int i = 0; i < 17; i++) tooMany.add(FloatTag.valueOf(i));
        uniforms.put("TooMany", tooMany);
        var invalidFloat = new ListTag();
        invalidFloat.add(FloatTag.valueOf(Float.NaN));
        uniforms.put("NonFinite", invalidFloat);
        malformed.put("uniformOverrides", uniforms);

        restored.put("StaleAgain", new float[]{2f});
        CustomShaderUniformNbt.readInto(restored, malformed.get("uniformOverrides"));

        assertTrue(restored.isEmpty());
        assertEquals(0, CustomShaderUniformNbt.write(Map.of("U_EngineOwned", new float[]{1f})).size());
    }

    @Test
    void readsPhoton262FloatAndIntegerUniformsFromShaderData() {
        var legacyUniforms = new CompoundTag();
        var floatComponents = new ListTag();
        floatComponents.add(FloatTag.valueOf(0.25f));
        floatComponents.add(FloatTag.valueOf(-2.5f));
        legacyUniforms.put("Glow", floatComponents);
        legacyUniforms.put("Mode", new IntArrayTag(new int[]{2, -3}));
        legacyUniforms.put("ModelViewMat", new IntArrayTag(new int[]{1}));
        legacyUniforms.put("U_CameraPosition", new IntArrayTag(new int[]{1, 2, 3}));

        Map<String, float[]> restored = new HashMap<>();
        CustomShaderUniformNbt.readLegacyUniformsInto(restored, legacyUniforms);

        assertArrayEquals(new float[]{0.25f, -2.5f}, restored.get("Glow"));
        assertArrayEquals(new float[]{2f, -3f}, restored.get("Mode"));
        assertEquals(2, restored.size());
    }

    @Test
    void distinguishesLegacyShaderDataFromLdLib2UniformState() {
        var legacy = new CompoundTag();
        legacy.put("uniforms", new CompoundTag());
        assertTrue(CustomShaderUniformNbt.isPhoton262LegacyShaderData(legacy));

        var ldlib = legacy.copy();
        ldlib.putInt("hdrVersion", 1);
        ldlib.put("hdrUniforms", new CompoundTag());
        ldlib.put("samplers", new CompoundTag());
        assertTrue(!CustomShaderUniformNbt.isPhoton262LegacyShaderData(ldlib));
    }
}
