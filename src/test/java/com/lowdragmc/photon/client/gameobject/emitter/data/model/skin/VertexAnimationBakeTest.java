package com.lowdragmc.photon.client.gameobject.emitter.data.model.skin;

import com.lowdragmc.photon.client.gameobject.emitter.data.model.PhotonMesh;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class VertexAnimationBakeTest {
    @Test
    void retainsTheReferenceTexelStrideApi() {
        assertEquals(4, VertexAnimationBake.FLOATS_PER_TEXEL);
        assertEquals(VertexAnimationBake.FLOATS_PER_VERTEX, VertexAnimationBake.FLOATS_PER_TEXEL);
    }

    @Test
    void bakesFrameMajorPositionsAndPackedNormals() {
        var meshBuilder = new PhotonMesh.Builder();
        float[] a = vertex(0, 0), b = vertex(1, 0), c = vertex(1, 1), d = vertex(0, 1);
        meshBuilder.quad(a, b, c, d, 0, 0, 1, 1, 1);

        var skeletonBuilder = new Skeleton.Builder();
        float[] identity = {0, 0, 0, 0, 0, 0, 1, 1, 1, 1};
        skeletonBuilder.joint(0, -1, "root", identity, 0);
        Skeleton[] skeleton = new Skeleton[1];
        skeletonBuilder.sortInto(skeleton);

        var skin = new MeshSkin(new int[16], new float[]{
                1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0
        });
        var clip = new AnimationClip("translate", List.of(new AnimationClip.Channel(
                0, AnimationClip.Path.TRANSLATION, AnimationClip.Interpolation.LINEAR,
                new float[]{0, 2}, new float[]{0, 0, 0, 2, 0, 0})));
        var model = new SkinnedModel(meshBuilder.build(), skin, skeleton[0], List.of(clip));

        float[] table = VertexAnimationBake.bake(model, clip, 2);

        assertNotNull(table);
        assertEquals(2 * 4 * VertexAnimationBake.FLOATS_PER_VERTEX, table.length);
        assertEquals(0, table[0], 1e-6f);
        assertEquals(1, table[4 * VertexAnimationBake.FLOATS_PER_VERTEX], 1e-6f);
        float[] normal = new float[3];
        VertexAnimationBake.unpackNormal(table[3], normal);
        assertEquals(0, normal[0], 2e-3f);
        assertEquals(0, normal[1], 2e-3f);
        assertEquals(1, normal[2], 2e-3f);
    }

    @Test
    void rejectsStaticModelsAndInvalidFrameCounts() {
        var meshBuilder = new PhotonMesh.Builder();
        float[] a = vertex(0, 0), b = vertex(1, 0), c = vertex(1, 1), d = vertex(0, 1);
        meshBuilder.quad(a, b, c, d, 0, 0, 1, 1, 1);
        var staticModel = SkinnedModel.staticModel(meshBuilder.build());

        assertNull(VertexAnimationBake.bake(staticModel, null, 1));
        assertNull(VertexAnimationBake.bake(staticModel, null, 0));
    }

    @Test
    void nonLoopingBakeIncludesTheClipEndAndPlaybackClampsThere() {
        var meshBuilder = new PhotonMesh.Builder();
        meshBuilder.quad(vertex(0, 0), vertex(1, 0), vertex(1, 1), vertex(0, 1), 0, 0, 1, 1, 1);
        var skeletonBuilder = new Skeleton.Builder();
        skeletonBuilder.joint(0, -1, "root", new float[]{0, 0, 0, 0, 0, 0, 1, 1, 1, 1}, 0);
        Skeleton[] skeleton = new Skeleton[1];
        skeletonBuilder.sortInto(skeleton);
        var skin = new MeshSkin(new int[16], new float[]{
                1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0
        });
        var clip = new AnimationClip("translate", List.of(new AnimationClip.Channel(
                0, AnimationClip.Path.TRANSLATION, AnimationClip.Interpolation.LINEAR,
                new float[]{0, 2}, new float[]{0, 0, 0, 2, 0, 0})));
        var model = new SkinnedModel(meshBuilder.build(), skin, skeleton[0], List.of(clip));

        float[] table = VertexAnimationBake.bake(model, clip, 2, false);
        int lastFrame = 4 * VertexAnimationBake.FLOATS_PER_VERTEX;
        assertEquals(2f, table[lastFrame], 1e-6f);

        var end = VertexAnimationBake.playbackFrame(1f, 2, false, true);
        assertEquals(1, end.frame());
        assertEquals(1, end.nextFrame());
        assertEquals(0f, end.blend());
        var wrapped = VertexAnimationBake.playbackFrame(1f, 2, true, true);
        assertEquals(0, wrapped.frame());
        assertEquals(1, wrapped.nextFrame());
    }

    private static float[] vertex(float x, float y) {
        return new float[]{x, y, 0, x, y, 0, 0, 1};
    }
}
