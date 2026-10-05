package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import org.jetbrains.annotations.Nullable;

/**
 * Runtime geometry provider for models whose positions or normals change over time.
 *
 * <p>The topology must remain stable for a given provider. Return a monotonically increasing
 * revision whenever {@link #geometry()} changes; Photon reuses the resolved mesh while the
 * topology identity and revision stay the same. Geometry is six floats per vertex in topology
 * order: position xyz followed by normal xyz.</p>
 *
 * <p>This Forge 1.20.1 port currently consumes CPU geometry. GPU-only buffers from newer Photon
 * versions are not supported by this renderer backend yet.</p>
 */
public interface IDynamicMesh {
    /** Immutable mesh carrying the topology, UVs, sprite bounds, and shade values. */
    PhotonMesh topology();

    /** Revision of the current deformation. Zero is reserved for the undeformed topology. */
    long revision();

    /** Current positions and normals in topology vertex order, or null when unchanged/no CPU copy exists. */
    @Nullable
    default float[] geometry() {
        return null;
    }

    /** Optional per-vertex tangent xyz and handedness values for this revision. */
    @Nullable
    default float[] tangents() {
        return null;
    }

    /** Called after Photon submits a draw using this mesh. */
    default void onDrawn() {
    }
}
