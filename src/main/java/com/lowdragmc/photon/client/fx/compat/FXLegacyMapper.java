package com.lowdragmc.photon.client.fx.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Pure Photon 1 to Photon 2 FX data mapping, independent of the client asset-directory setup. */
public final class FXLegacyMapper {
    private FXLegacyMapper() {
    }

    public static CompoundTag mapEffect(CompoundTag fx) {
        var fxData = new CompoundTag();
        var newFx = new CompoundTag();
        var mappedObjects = new ListTag();
        if (!(fx.get("fx") instanceof CompoundTag fxRoot)
                || !(fxRoot.get("mainFX") instanceof CompoundTag mainFx)
                || !(mainFx.get("fxObjects") instanceof ListTag objects)) {
            throw new IllegalArgumentException("Malformed Photon 1 FX project: missing compound FX data or object list");
        }
        for (var object : objects) {
            if (!(object instanceof CompoundTag fxObject)) {
                throw new IllegalArgumentException("Malformed Photon 1 FX project: object list contains a non-compound entry");
            }
            switch (fxObject.getString("_type")) {
                case "beam" -> mappedObjects.add(BeamEmitterMapper.mapBeamEmitter(fxObject));
                case "trail" -> mappedObjects.add(TrailEmitterMapper.mapTrailEmitter(fxObject));
                case "particle" -> mappedObjects.add(ParticleEmitterMapper.mapParticleEmitter(fxObject));
                case "empty" -> mappedObjects.add(EmptyMapper.mapEmpty(fxObject));
                default -> throw new IllegalArgumentException("Unsupported Photon 1 FX object type '"
                        + fxObject.getString("_type") + "'; refusing to write an incomplete conversion");
            }
        }
        fxData.put("fxObjects", mappedObjects);
        newFx.put("fxData", fxData);
        return newFx;
    }
}
