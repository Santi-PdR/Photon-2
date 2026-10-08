package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import org.jetbrains.annotations.Nullable;

/** Keeps the same mesh object for unchanged dynamic geometry revisions. */
final class DynamicMeshCache {
    @Nullable
    private PhotonMesh current;

    synchronized PhotonMesh resolve(IDynamicMesh dynamic) {
        PhotonMesh topology = dynamic.topology();
        long revision = dynamic.revision();
        PhotonMesh cached = current;
        if (cached != null && cached.geometryRevision() == revision && topology == cachedTopology) {
            return cached;
        }

        float[] geometry = dynamic.geometry();
        PhotonMesh resolved = geometry == null || revision == 0
                ? topology
                : topology.withGeometry(geometry, () -> tangentsForRevision(dynamic, revision), revision);
        current = resolved;
        cachedTopology = topology;
        return resolved;
    }

    /**
     * Tangent generation is lazy, but the provider may reuse its backing array on the next pose.
     * Keep a later animation revision from supplying tangents for this cached geometry snapshot.
     */
    @Nullable
    private static float[] tangentsForRevision(IDynamicMesh dynamic, long expectedRevision) {
        synchronized (dynamic) {
            if (dynamic.revision() != expectedRevision) return null;
            float[] tangents = dynamic.tangents();
            if (dynamic.revision() != expectedRevision || tangents == null) return null;
            return tangents.clone();
        }
    }

    @Nullable
    private PhotonMesh cachedTopology;

    synchronized void invalidate() {
        current = null;
        cachedTopology = null;
    }
}
