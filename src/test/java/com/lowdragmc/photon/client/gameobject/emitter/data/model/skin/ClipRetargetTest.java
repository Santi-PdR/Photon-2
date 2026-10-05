package com.lowdragmc.photon.client.gameobject.emitter.data.model.skin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClipRetargetTest {
    private static final float[] REST = {0, 0, 0, 0, 0, 0, 1, 1, 1, 1};

    @Test
    void mapsAnimationChannelsByJointNameAndDropsUnmatchedJoints() {
        var target = skeleton(new String[]{"root", "arm", "hand"}, new int[]{-1, 0, 1});
        // A source exporter is free to place sibling joints in a different order.
        var source = skeleton(new String[]{"root", "hand", "arm", "helper"}, new int[]{-1, 0, 0, 1});
        var first = clip("walk", 1, 3);
        var second = clip("run", 0, -1);

        var result = ClipRetarget.onto(target, source, List.of(first, second), "animations/hero");

        assertEquals(3, result.matchedJoints());
        assertEquals(1, result.droppedChannels());
        assertEquals(List.of("animations/hero/walk", "animations/hero/run"),
                result.clips().stream().map(AnimationClip::name).toList());
        assertEquals(2, result.clips().get(0).channels().get(0).joint()); // source hand -> target hand
        assertEquals(0, result.clips().get(1).channels().get(0).joint()); // source root -> target root
    }

    private static Skeleton skeleton(String[] names, int[] parents) {
        var builder = new Skeleton.Builder();
        for (int i = 0; i < names.length; i++) builder.joint(i, parents[i], names[i], REST, 0);
        var output = new Skeleton[1];
        builder.sortInto(output);
        return output[0];
    }

    private static AnimationClip clip(String name, int mappedJoint, int unmatchedJoint) {
        var channels = new java.util.ArrayList<AnimationClip.Channel>();
        channels.add(new AnimationClip.Channel(mappedJoint, AnimationClip.Path.TRANSLATION,
                AnimationClip.Interpolation.LINEAR, new float[]{0, 1},
                new float[]{0, 0, 0, 1, 0, 0}));
        if (unmatchedJoint >= 0) {
            channels.add(new AnimationClip.Channel(unmatchedJoint, AnimationClip.Path.SCALE,
                    AnimationClip.Interpolation.STEP, new float[]{0, 1},
                    new float[]{1, 1, 1, 2, 2, 2}));
        }
        return new AnimationClip(name, channels);
    }
}
