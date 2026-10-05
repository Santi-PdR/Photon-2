package com.lowdragmc.photon.client.gameobject.emitter.data.model.skin;

import com.lowdragmc.photon.client.gameobject.emitter.data.model.PhotonMesh;
import java.util.List;
import javax.annotation.Nullable;

/** Parsed static or skinned glTF model data. */
public record SkinnedModel(PhotonMesh mesh, @Nullable MeshSkin skin, @Nullable Skeleton skeleton,
                           List<AnimationClip> clips) {
    public static final SkinnedModel EMPTY = staticModel(PhotonMesh.EMPTY);

    public static SkinnedModel staticModel(PhotonMesh mesh) {
        return new SkinnedModel(mesh, null, null, List.of());
    }

    public boolean isAnimated() {
        return skeleton != null && skin != null && !skin.isEmpty() && skeleton.jointCount() > 0;
    }

    @Nullable
    public AnimationClip clip(String name) {
        for (AnimationClip clip : clips) if (clip.name().equals(name)) return clip;
        return null;
    }

    @Nullable
    public AnimationClip clipAt(int index) {
        return clips.isEmpty() ? null : clips.get(Math.max(0, Math.min(index, clips.size() - 1)));
    }

    public List<String> clipNames() {
        return clips.stream().map(AnimationClip::name).toList();
    }
}
