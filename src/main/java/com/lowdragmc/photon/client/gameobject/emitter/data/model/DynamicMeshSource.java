package com.lowdragmc.photon.client.gameobject.emitter.data.model;

/** Runtime-only adapter that lets a dynamic mesh be used anywhere a model source is accepted. */
public final class DynamicMeshSource implements IModelSource {
    private final IDynamicMesh dynamic;
    private final DynamicMeshCache cache = new DynamicMeshCache();

    public DynamicMeshSource(IDynamicMesh dynamic) {
        this.dynamic = dynamic;
    }

    public IDynamicMesh getDynamic() {
        return dynamic;
    }

    @Override
    public IDynamicMesh asDynamic() {
        return dynamic;
    }

    @Override
    public PhotonMesh getMesh() {
        return cache.resolve(dynamic);
    }

    @Override
    public void invalidate() {
        cache.invalidate();
    }

    /** Returns itself so wrappers around one live provider continue to batch together. */
    @Override
    public IModelSource copy() {
        return this;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DynamicMeshSource source && source.dynamic == dynamic;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(dynamic);
    }
}
