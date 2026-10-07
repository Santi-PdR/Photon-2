package com.lowdragmc.photon.client.gameobject.emitter.data.model.skin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimationClipTest {
    @Test
    void stepKeepsPreviousValueUntilTheNextKey() {
        var clip = clip(AnimationClip.Path.TRANSLATION, AnimationClip.Interpolation.STEP,
                new float[]{0, 1}, new float[]{2, 0, 0, 8, 0, 0});

        assertEquals(2f, sample(clip, 0.999f)[0], 1e-6f);
        assertEquals(8f, sample(clip, 1f)[0], 1e-6f);
    }

    @Test
    void cubicSplineScalesTangentsByTheKeyInterval() {
        var clip = clip(AnimationClip.Path.TRANSLATION, AnimationClip.Interpolation.CUBICSPLINE,
                new float[]{0, 2}, new float[]{
                        0, 0, 0,  0, 0, 0,  2, 0, 0,
                        0, 0, 0,  4, 0, 0,  0, 0, 0
                });

        assertEquals(2.5f, sample(clip, 1f)[0], 1e-6f);
    }

    @Test
    void linearRotationUsesShortestArcAndReturnsUnitQuaternion() {
        var clip = clip(AnimationClip.Path.ROTATION, AnimationClip.Interpolation.LINEAR,
                new float[]{0, 1}, new float[]{0, 0, 0, 1,  0, 0, 1, 0});

        float[] pose = sample(clip, 0.5f);
        float half = (float) Math.sqrt(0.5);
        assertEquals(0f, pose[3], 1e-5f);
        assertEquals(0f, pose[4], 1e-5f);
        assertEquals(half, pose[5], 1e-5f);
        assertEquals(half, pose[6], 1e-5f);
        assertEquals(1f, pose[3] * pose[3] + pose[4] * pose[4] + pose[5] * pose[5] + pose[6] * pose[6], 1e-6f);
    }

    private static AnimationClip clip(AnimationClip.Path path, AnimationClip.Interpolation interpolation,
                                     float[] times, float[] values) {
        return new AnimationClip("test", List.of(new AnimationClip.Channel(0, path, interpolation, times, values)));
    }

    private static float[] sample(AnimationClip clip, float seconds) {
        float[] pose = {0, 0, 0, 0, 0, 0, 1, 1, 1, 1};
        clip.sample(seconds, pose);
        return pose;
    }
}
