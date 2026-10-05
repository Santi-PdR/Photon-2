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
                : topology.withGeometry(geometry, dynamic::tangents, revision);
        current = resolved;
        cachedTopology = topology;
        return resolved;
    }

    @Nullable
    private PhotonMesh cachedTopology;

    synchronized void invalidate() {
        current = null;
        cachedTopology = null;
    }
}
