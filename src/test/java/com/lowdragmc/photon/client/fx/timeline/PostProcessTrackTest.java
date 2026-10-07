package com.lowdragmc.photon.client.fx.timeline;

import com.lowdragmc.lowdraglib2.registry.AutoRegistry;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostProcessTrackTest {
    private final PostProcessTrack track = new PostProcessTrack();

    @BeforeAll
    static void initializeNumberFunctionCodecRegistry() {
        if (PhotonRegistries.NUMBER_FUNCTIONS == null) {
            PhotonRegistries.NUMBER_FUNCTIONS = AutoRegistry.LDLibRegisterClient.create(
                    Photon.id("number_function"), NumberFunction.class, AutoRegistry::noArgsCreator);
        }
    }

    @Test
    void readsLegacyNumericMaskFilterAsNamedGroup() {
        var tag = new CompoundTag();
        tag.putString("effect", "render_graph(photon:effects/outline)");
        tag.putInt("maskFilter", 7);

        var clip = new PostProcessClip();
        track.readClipExtra(clip, tag, null);

        assertEquals("render_graph(photon:effects/outline)", clip.effect());
        assertTrue(clip.maskCulling());
        assertEquals("7", clip.maskGroup());
        assertFalse(clip.independent());
    }

    @Test
    void readsStringMaskGroupsAndIndependentFlag() {
        var tag = new CompoundTag();
        tag.putBoolean("maskCulling", true);
        tag.putString("maskGroup", "smoke");
        tag.putBoolean("independent", true);

        var clip = new PostProcessClip();
        track.readClipExtra(clip, tag, null);

        assertTrue(clip.maskCulling());
        assertEquals("smoke", clip.maskGroup());
        assertTrue(clip.independent());
    }

    @Test
    void ignoresMalformedParameterKindsAndDefaultsMissingFields() {
        var params = new CompoundTag();
        var malformed = new CompoundTag();
        malformed.putString("kind", "not_a_parameter_kind");
        params.put("broken", malformed);
        var tag = new CompoundTag();
        tag.put("params", params);

        var clip = new PostProcessClip();
        track.readClipExtra(clip, tag, null);

        assertTrue(clip.params().isEmpty());
        assertFalse(clip.maskCulling());
        assertFalse(clip.independent());
    }
}
