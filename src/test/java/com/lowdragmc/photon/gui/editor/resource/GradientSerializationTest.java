package com.lowdragmc.photon.gui.editor.resource;

import com.lowdragmc.lowdraglib2.math.GradientColor;
import com.lowdragmc.photon.client.fx.timeline.GradientClip;
import com.lowdragmc.photon.client.fx.timeline.property.ColorAnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.property.ColorPropertyType;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradientSerializationTest {
    private static final RegistryAccess PROVIDER = RegistryAccess.EMPTY;

    @Test
    void gradientResourceRoundTripsBothGradientsAndHdrState() {
        var original = new GradientResource.Gradients(
                new GradientColor(0xff123456, 0xffabcdef),
                new GradientColor(0x80102030, 0x40f0e0d0)).setHDR(true);

        var encoded = original.serializeNBT(PROVIDER);
        var restored = new GradientResource.Gradients(new GradientColor(), new GradientColor());
        restored.deserializeNBT(PROVIDER, encoded);

        assertTrue(restored.hdr);
        assertEquals(original.gradient0, restored.gradient0);
        assertEquals(original.gradient1, restored.gradient1);
    }

    @Test
    void gradientResourceKeepsLegacyTagsLdrWhenHdrFlagIsAbsent() {
        var original = new GradientResource.Gradients(new GradientColor(0xff123456, 0xffabcdef));
        var encoded = original.serializeNBT(PROVIDER);
        encoded.remove("hdr");

        var restored = new GradientResource.Gradients(new GradientColor());
        restored.deserializeNBT(PROVIDER, encoded);

        assertFalse(restored.hdr);
        assertEquals(original.gradient0, restored.gradient0);
    }

    @Test
    void gradientResourceReadsThe26_2VectorListSchema() {
        var encoded = new CompoundTag();
        encoded.put("a", vectorGradient());
        encoded.put("b", vectorGradient());
        encoded.putBoolean("hdr", true);

        var restored = new GradientResource.Gradients(new GradientColor(), new GradientColor());
        restored.deserializeNBT(PROVIDER, encoded);

        assertTrue(restored.hdr);
        assertEquals(new Vector2f(0, 0.25f), restored.gradient0.getAP().get(0));
        assertEquals(new Vector2f(1, 0.75f), restored.gradient0.getAP().get(1));
        assertEquals(new Vector4f(0, 0.1f, 0.2f, 0.3f), restored.gradient0.getRgbP().get(0));
        assertEquals(new Vector4f(1, 0.7f, 0.8f, 0.9f), restored.gradient0.getRgbP().get(1));
        assertEquals(restored.gradient0, restored.gradient1);
    }

    @Test
    void colorTrackRoundTripsHdrStopsAndGradientClips() {
        var type = new ColorPropertyType("startColor", "test.color");
        var original = new ColorAnimatedProperty(type);
        original.setHDR(true);
        original.addStop(3.5f, 0x80112233, 2.25f);
        original.gradientClips().add(new GradientClip(5.0, 4.5,
                new GradientColor(0xff112233, 0xffaabbcc)));

        var encoded = type.serialize(PROVIDER, original);
        var restored = (ColorAnimatedProperty) type.deserialize(PROVIDER, encoded);

        assertTrue(restored.isHDR());
        assertEquals(1, restored.stops().size());
        assertEquals(original.stops().get(0).tick, restored.stops().get(0).tick);
        assertEquals(original.stops().get(0).argb, restored.stops().get(0).argb);
        assertEquals(original.stops().get(0).intensity, restored.stops().get(0).intensity);
        assertEquals(1, restored.gradientClips().size());
        assertEquals(original.gradientClips().get(0).start(), restored.gradientClips().get(0).start());
        assertEquals(original.gradientClips().get(0).duration(), restored.gradientClips().get(0).duration());
        assertEquals(original.gradientClips().get(0).gradient(), restored.gradientClips().get(0).gradient());
    }

    @Test
    void colorTrackReadsThe26_2VectorListGradientClipSchema() {
        var type = new ColorPropertyType("startColor", "test.color");
        var original = new ColorAnimatedProperty(type);
        original.gradientClips().add(new GradientClip(2, 3, new GradientColor()));
        var encoded = type.serialize(PROVIDER, original);
        var clips = encoded.getList("gradientClips", Tag.TAG_COMPOUND);
        clips.getCompound(0).put("gradient", vectorGradient());

        var restored = (ColorAnimatedProperty) type.deserialize(PROVIDER, encoded);

        assertEquals(new Vector2f(0, 0.25f),
                restored.gradientClips().get(0).gradient().getAP().get(0));
        assertEquals(new Vector4f(1, 0.7f, 0.8f, 0.9f),
                restored.gradientClips().get(0).gradient().getRgbP().get(1));
    }

    private static CompoundTag vectorGradient() {
        var tag = new CompoundTag();
        tag.put("a", vectors(new float[]{0, 0.25f}, new float[]{1, 0.75f}));
        tag.put("rgb", vectors(new float[]{0, 0.1f, 0.2f, 0.3f},
                new float[]{1, 0.7f, 0.8f, 0.9f}));
        return tag;
    }

    private static ListTag vectors(float[]... values) {
        var vectors = new ListTag();
        for (float[] value : values) {
            var vector = new ListTag();
            for (float coordinate : value) vector.add(FloatTag.valueOf(coordinate));
            vectors.add(vector);
        }
        return vectors;
    }
}
