package com.lowdragmc.photon.client.fx.compat;

import net.minecraft.nbt.CompoundTag;

public class TrailEmitterMapper implements Mapper {

    public static CompoundTag mapTrailEmitter(CompoundTag emitterTag){
        CompoundTag newEmitterTag = new CompoundTag();
        newEmitterTag.putString("type", emitterTag.getString("_type") + "_emitter");
        CompoundTag dataTag = new CompoundTag();

        //data tag
        dataTag.putString("name", emitterTag.getString("name"));
        dataTag.putInt("version",  emitterTag.getInt("_version"));
        dataTag.put("transform", MapperUtils.mapTransformTag(emitterTag.getCompound("transform")));
        dataTag.put("config", mapTrailConfig(MapperUtils.legacyEmitterConfig(emitterTag)));

        newEmitterTag.put("data", dataTag);

        return newEmitterTag;
    }

    public static CompoundTag mapTrailConfig(CompoundTag trailConfigTag){
        CompoundTag newTrailConfigTag = new CompoundTag();
        newTrailConfigTag.putInt("startDelay", trailConfigTag.getInt("startDelay"));
        newTrailConfigTag.put("lights", MapperUtils.mapLightTag(trailConfigTag.getCompound("lights")));
        newTrailConfigTag.put("uvAnimation", MapperUtils.mapUVTag(trailConfigTag.getCompound("uvAnimation")));
        newTrailConfigTag.put("colorOverTrail", MapperUtils.mapTypedValue(trailConfigTag.getCompound("colorOverTrail"), false));
        newTrailConfigTag.put("widthOverTrail",  MapperUtils.mapTypedValue(trailConfigTag.getCompound("widthOverTrail"), false));
        newTrailConfigTag.putFloat("minVertexDistance",  trailConfigTag.getFloat("minVertexDistance"));
        newTrailConfigTag.putInt("time",  trailConfigTag.getInt("time"));
        newTrailConfigTag.putString("uvMode",  trailConfigTag.getString("uvMode"));
        newTrailConfigTag.putInt("duration",   trailConfigTag.getInt("duration"));
        newTrailConfigTag.put("renderer", MapperUtils.mapRendererTag(trailConfigTag.getCompound("renderer"),trailConfigTag));
        newTrailConfigTag.putByte("smoothInterpolation",  trailConfigTag.getByte("smoothInterpolation"));
        newTrailConfigTag.putByte("looping", trailConfigTag.getByte("looping"));
        MapperUtils.retainPresentFields(newTrailConfigTag, trailConfigTag);
        // Photon 1 migrates older trail configs (without smoothInterpolation) on load. It divides
        // constant widths by ten; for Curve it changes editor bounds only, not lower/upper, which
        // are what actually determine the sampled width. Do this once during import, including
        // particle-attached trails, without mutating or repeatedly scaling a saved Photon 2 file.
        if (!trailConfigTag.contains("smoothInterpolation")) {
            if (!trailConfigTag.contains("widthOverTrail", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                var width = new CompoundTag();
                width.putString("type", "constant");
                var data = new CompoundTag();
                data.putFloat("number", 0.02f);
                width.put("data", data);
                newTrailConfigTag.put("widthOverTrail", width);
            } else {
                var width = newTrailConfigTag.getCompound("widthOverTrail");
                var data = width.getCompound("data");
                if ("constant".equals(width.getString("type"))) {
                    data.putFloat("number", data.getFloat("number") / 10f);
                } else if ("curve".equals(width.getString("type"))) {
                    if (data.contains("min")) data.putFloat("min", data.getFloat("min") / 10f);
                    if (data.contains("max")) data.putFloat("max", data.getFloat("max") / 10f);
                }
            }
        }
        MapperUtils.applyLegacyBloomLight(newTrailConfigTag, trailConfigTag);
        if (trailConfigTag.contains("material", net.minecraft.nbt.Tag.TAG_COMPOUND) && !newTrailConfigTag.contains("renderer")) {
            newTrailConfigTag.put("renderer", MapperUtils.mapRendererTag(trailConfigTag.getCompound("renderer"), trailConfigTag));
        }
        return  newTrailConfigTag;
    }
}
