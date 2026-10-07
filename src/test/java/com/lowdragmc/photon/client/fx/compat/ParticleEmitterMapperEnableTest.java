package com.lowdragmc.photon.client.fx.compat;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleEmitterMapperEnableTest {
    private static CompoundTag enabled() {
        var tag = new CompoundTag();
        tag.putByte("enable", (byte) 1);
        return tag;
    }

    private static CompoundTag constant(float number) {
        var tag = new CompoundTag();
        tag.putString("_type", "Constant");
        tag.putFloat("number", number);
        return tag;
    }

    private static CompoundTag vector() {
        var tag = new CompoundTag();
        tag.put("x", constant(1));
        tag.put("y", constant(2));
        tag.put("z", constant(3));
        return tag;
    }

    @Test
    void preservesEnabledRotationBySpeed() {
        var source = enabled();
        source.put("speedRange", new CompoundTag());
        source.put("roll", constant(1));
        source.put("pitch", constant(2));
        source.put("yaw", constant(3));

        var mapped = ParticleEmitterMapper.mapRotationBySpeedTag(source);

        assertEquals(1, mapped.getByte("_enable"));
        assertTrue(mapped.contains("speedRange"));
        assertTrue(mapped.contains("roll"));
    }

    @Test
    void preservesEnabledForceOverLifetime() {
        var source = enabled();
        source.putString("simulationSpace", "World");
        source.put("force", vector());

        var mapped = ParticleEmitterMapper.mapForceOTTag(source);

        assertEquals("World", mapped.getString("simulationSpace"));
        assertTrue(mapped.contains("force"));
    }

    @Test
    void preservesEnabledNoiseAndItsRemapCurve() {
        var source = enabled();
        source.put("size", constant(1));
        source.put("rotation", constant(2));
        source.put("position", vector());
        source.putString("quality", "High");
        source.putFloat("frequency", 0.5f);
        var remap = enabled();
        remap.put("remapCurve", constant(4));
        source.put("remap", remap);

        var mapped = ParticleEmitterMapper.mapNoiseTag(source);

        assertTrue(mapped.contains("size"));
        assertTrue(mapped.getCompound("remap").contains("remapCurve"));
    }

    @Test
    void preservesEnabledPhysics() {
        var source = enabled();
        source.putByte("hasCollision", (byte) 1);
        source.putByte("removeWhenCollided", (byte) 0);
        for (var key : new String[]{"friction", "gravity", "bounceSpreadRate", "bounceRate", "bounceChance"}) {
            source.put(key, constant(1));
        }

        var mapped = ParticleEmitterMapper.mapPhysicsTag(source);

        assertTrue(mapped.contains("hasCollision"));
        assertTrue(mapped.contains("friction"));
    }

    @Test
    void preservesEnabledSizeBySpeed() {
        var source = enabled();
        source.put("speedRange", new CompoundTag());
        source.put("size", vector());

        var mapped = ParticleEmitterMapper.mapSizeBySpeedTag(source);

        assertTrue(mapped.contains("speedRange"));
        assertTrue(mapped.contains("size"));
    }

    @Test
    void preservesEnabledVelocityOverLifetime() {
        var source = enabled();
        source.put("speedModifier", constant(1));
        source.putString("orbitalMode", "XYZ");
        source.put("offset", vector());
        source.put("orbital", vector());
        source.put("linear", vector());

        var mapped = ParticleEmitterMapper.mapVelocityOLTag(source);

        assertTrue(mapped.contains("speedModifier"));
        assertTrue(mapped.contains("orbital"));
    }

    @Test
    void preservesEnabledRotationOverLifetime() {
        var source = enabled();
        source.put("roll", constant(1));
        source.put("pitch", constant(2));
        source.put("yaw", constant(3));

        var mapped = ParticleEmitterMapper.mapRotationOLTTag(source);

        assertTrue(mapped.contains("roll"));
        assertTrue(mapped.contains("yaw"));
    }

    @Test
    void preservesEnabledTrails() {
        var source = enabled();
        source.putByte("dieWithParticles", (byte) 1);
        source.putByte("sizeAffectsWidth", (byte) 1);
        source.putByte("inheritParticleColor", (byte) 1);
        source.putByte("sizeAffectsLifetime", (byte) 0);
        source.putFloat("ratio", 0.5f);
        source.put("colorOverLifetime", constant(1));
        source.put("lifetime", constant(20));
        source.put("config", new CompoundTag());

        var mapped = ParticleEmitterMapper.mapTrailsTag(source);

        assertEquals(1, mapped.getByte("_enable"));
        assertTrue(mapped.contains("dieWithParticles"));
        assertTrue(mapped.contains("lifetime"));
    }
}
