package com.lowdragmc.photon.client.fx.compat;

import com.lowdragmc.photon.Photon;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec2;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MapperUtils {

    public static ListTag mapCoords(Tag coordTag){
        CompoundTag compoundTag = (CompoundTag)coordTag;
        ListTag newCoordTag = new ListTag();
        newCoordTag.add(FloatTag.valueOf(compoundTag.getFloat("x")));
        newCoordTag.add(FloatTag.valueOf(compoundTag.getFloat("y")));
        newCoordTag.add(FloatTag.valueOf(compoundTag.getFloat("z")));
        if (compoundTag.contains("w")) {
            newCoordTag.add(FloatTag.valueOf(compoundTag.getFloat("w")));

        }
        return newCoordTag;

    }
    public static ListTag mapCoordsDouble(Tag coordTag){
        CompoundTag compoundTag = (CompoundTag)coordTag;
        ListTag newCoordTag = new ListTag();
        newCoordTag.add(DoubleTag.valueOf(compoundTag.getFloat("x")));
        newCoordTag.add(DoubleTag.valueOf(compoundTag.getFloat("y")));
        newCoordTag.add(DoubleTag.valueOf(compoundTag.getFloat("z")));
        if (compoundTag.contains("w")) {
            newCoordTag.add(DoubleTag.valueOf(compoundTag.getFloat("w")));

        }
        return newCoordTag;

    }

    /**
     * This is for tags that contain 3 typed values
     * @param typed3VecTag the tag with the values, values from key will be used as keys inside of the tag, or (x,y,z) if null
     * @param key keys inside of the tag
     * @param key that the list tag lives under.
     * @return
     */
    public static ListTag mapTyped3Vec(CompoundTag typed3VecTag, String[] key, boolean floatToDouble){
        if(key == null){
            key = new String[]{"x", "y", "z"};
        }
        ListTag listTag = new ListTag();
        for(int i = 0; i < key.length; i++){
            listTag.add(mapTypedValue(typed3VecTag.getCompound(key[i]), floatToDouble));
        }
        return listTag;
    }



    /** Expand an old scalar function into independently serialized X, Y and Z functions. */
    public static ListTag mapUniformTyped3Vec(CompoundTag value, boolean floatToDouble) {
        var mapped = mapTypedValue(value, floatToDouble);
        var result = new ListTag();
        for (int i = 0; i < 3; i++) result.add(mapped.copy());
        return result;
    }

    public static CompoundTag mapTypedValue(CompoundTag typedValueTag,  boolean floatToDouble){
        CompoundTag newTypedValueTag = new CompoundTag();
        newTypedValueTag.putString("type", typedValueTag.getString("_type").toLowerCase());
        CompoundTag dataTag = new CompoundTag();
        switch(typedValueTag.getString("_type")){
            case "Curve":
               dataTag = typedValueTag.copy();
               dataTag.remove("_type");
               dataTag.remove("defaultValue");
               if (!dataTag.contains("lockControlPoint")) dataTag.putByte("lockControlPoint", (byte)1);
               break;
            case "RandomCurve":
               newTypedValueTag.putString("type", "random_curve");
               dataTag = typedValueTag.copy();
               dataTag.remove("_type");
               dataTag.remove("defaultValue");
               if (!dataTag.contains("lockControlPoint")) dataTag.putByte("lockControlPoint", (byte)1);
               break;
            case "Constant", "Color":
                dataTag = typedValueTag.copy();
                dataTag.remove("_type");
                if(floatToDouble && dataTag.contains("number", Tag.TAG_FLOAT)){
                    //convert to double
                    dataTag.putDouble("number", dataTag.getFloat("number"));
                }
                break;
            case "RandomConstant":
                if (typedValueTag.contains("a")) dataTag.put("a", typedValueTag.get("a").copy());
                if (typedValueTag.contains("b")) dataTag.put("b", typedValueTag.get("b").copy());
                // Photon 1 explicitly persisted whether this is a decimal range. Its integer
                // sampling includes the upper endpoint; dropping this made [0,1] bursts always 0.
                if (typedValueTag.contains("isDecimals", Tag.TAG_BYTE) && !typedValueTag.getBoolean("isDecimals")) {
                    dataTag.putBoolean("legacyIntegerInclusive", true);
                }
                newTypedValueTag.putString("type", "random_constant");
                break;
            case "Gradient":
                CompoundTag gradientTag = gradient(typedValueTag.getList("a", Tag.TAG_FLOAT), typedValueTag.getList("r", Tag.TAG_FLOAT), typedValueTag.getList("g", Tag.TAG_FLOAT), typedValueTag.getList("b", Tag.TAG_FLOAT));
                dataTag.put("gradientColor",  gradientTag);
                break;
            case "RandomGradient":
                newTypedValueTag.putString("type", "random_gradient");
                var first = typedValueTag.getCompound("a");
                var second = typedValueTag.getCompound("b");
                dataTag.put("gradientColor0", gradient(first.getList("a", Tag.TAG_FLOAT), first.getList("r", Tag.TAG_FLOAT),
                        first.getList("g", Tag.TAG_FLOAT), first.getList("b", Tag.TAG_FLOAT)));
                dataTag.put("gradientColor1", gradient(second.getList("a", Tag.TAG_FLOAT), second.getList("r", Tag.TAG_FLOAT),
                        second.getList("g", Tag.TAG_FLOAT), second.getList("b", Tag.TAG_FLOAT)));
                break;
            case "TextureMaterial", "BlockTextureSheetMaterial":
                newTypedValueTag.putString("type", "texture");
                dataTag = buildBlankTexture();
                dataTag.putString("texture",  typedValueTag.getString("texture"));
                dataTag.putFloat("discardThreshold", typedValueTag.getFloat("discardThreshold"));
                break;
            case "CustomShaderMaterial":
                newTypedValueTag.putString("type", "custom_shader");
                dataTag = mapPhoton1CustomShaderData(typedValueTag);
                break;

            case "RandomColor":
                newTypedValueTag.putString("type", "random_color");
                if(typedValueTag.contains("a", Tag.TAG_INT)){
                    dataTag.putInt("a", typedValueTag.getInt("a"));
                }
                else{
                    dataTag.putFloat("a", typedValueTag.getFloat("a"));
                }
                if(typedValueTag.contains("b", Tag.TAG_INT)){
                    dataTag.putInt("b", typedValueTag.getInt("b"));
                }
                else{
                    dataTag.putFloat("b", typedValueTag.getFloat("b"));
                }
                break;


        }
        newTypedValueTag.put("data", dataTag);
        return newTypedValueTag;
    }

    public static CompoundTag buildBlankTexture(){
        CompoundTag dataTag = new CompoundTag();
        dataTag.putString("texture",  "");
        dataTag.putFloat("discardThreshold", .01f);
        dataTag.putString("hdrMode", "ADDITIVE");
        ListTag hdr = new ListTag();
        hdr.add(FloatTag.ZERO);
        hdr.add(FloatTag.ZERO);
        hdr.add(FloatTag.ZERO);
        hdr.add(FloatTag.valueOf(1));
        dataTag.put("hdr", hdr);
        dataTag.put("pixelArt", new CompoundTag());
        dataTag.getCompound("pixelArt").putByte("_enable", (byte)0);
        return dataTag;
    }

    public static CompoundTag gradient(ListTag oldA, ListTag oldR, ListTag oldG, ListTag oldB) {
        // Load old data from tags
        List<Vec2> rP = new ArrayList<>();
        List<Vec2> gP = new ArrayList<>();
        List<Vec2> bP = new ArrayList<>();

        for (int i = 0; i < oldR.size(); i += 2) {
            rP.add(new Vec2(oldR.getFloat(i), oldR.getFloat(i + 1)));
        }
        for (int i = 0; i < oldG.size(); i += 2) {
            gP.add(new Vec2(oldG.getFloat(i), oldG.getFloat(i + 1)));
        }
        for (int i = 0; i < oldB.size(); i += 2) {
            bP.add(new Vec2(oldB.getFloat(i), oldB.getFloat(i + 1)));
        }

        // Helper method to interpolate value at t
        java.util.function.BiFunction<List<Vec2>, Float, Float> get = (data, t) -> {
            if (data.isEmpty()) return 1f;
            var value = data.get(0).y;
            var found = t < data.get(0).x;
            if (!found) {
                for (int i = 0; i < data.size() - 1; i++) {
                    var s = data.get(i);
                    var e = data.get(i + 1);
                    if (t >= s.x && t <= e.x) {
                        value = s.y * (e.x - t) / (e.x - s.x) + e.y * (t - s.x) / (e.x - s.x);
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                value = data.get(data.size() - 1).y;
            }
            return value;
        };


        // Create new RGB ListTag (merge r, g, b at each unique t position)
        ListTag newRGB = new ListTag();

        // Collect all unique t values from r, g, b channels
        var tValues = new java.util.TreeSet<Float>();
        for (var point : rP) tValues.add(point.x);
        for (var point : gP) tValues.add(point.x);
        for (var point : bP) tValues.add(point.x);

        // For each unique t value, sample r, g, b and create a combined point
        for (float t : tValues) {
            float r = get.apply(rP, t);
            float g = get.apply(gP, t);
            float b = get.apply(bP, t);

            newRGB.add(FloatTag.valueOf(t));
            newRGB.add(FloatTag.valueOf(r));
            newRGB.add(FloatTag.valueOf(g));
            newRGB.add(FloatTag.valueOf(b));
        }

        // Create and return new CompoundTag
        var tag = new CompoundTag();
        tag.put("a", oldA);
        tag.put("rgb", newRGB);
        return tag;
    }


    public static CompoundTag mapShape(CompoundTag shapeTag){
        CompoundTag newShapeTag = new CompoundTag();
        newShapeTag.putString("type", shapeTag.getString("_type"));
        CompoundTag dataTag = shapeTag.copy();
        dataTag.remove("_type");
        if ("mesh".equalsIgnoreCase(shapeTag.getString("_type")) && shapeTag.contains("mesh", Tag.TAG_COMPOUND)) {
            dataTag.put("meshData", shapeTag.getCompound("mesh").copy());
            dataTag.remove("mesh");
        }
        newShapeTag.put("data", dataTag);

        return newShapeTag;
    }

    public static CompoundTag blankColorTag(){
        CompoundTag colorTag = new CompoundTag();
        colorTag.putString("type", "color");
        CompoundTag dataTag = new CompoundTag();
        dataTag.putInt("number", -1);
        colorTag.put("data", dataTag);

        return colorTag;
    }

    public static CompoundTag blankTypedTag(){
        CompoundTag typedTag = new CompoundTag();
        typedTag.putString("type", "constant");
        CompoundTag dataTag = new CompoundTag();
        dataTag.putInt("number", 1);
        typedTag.put("data", dataTag);

        return typedTag;

    }

    public static CompoundTag blankRenderer(){
        var tag = new CompoundTag();

        // cull section
        var cull = new CompoundTag();
        cull.putByte("_enable", (byte) 0);
        tag.put("cull", cull);

        tag.putInt("orderInLayer", 0);
        tag.putString("layer", "Translucent");
        tag.putString("vertexSortingMode", "NONE");

        // materials section
        var materials = new CompoundTag();

        // payload list
        var payload = new ListTag();

        // First element (0) in payload
        var payloadElement0 = new CompoundTag();
        payloadElement0.putInt("cull", 1);

        // blendMode section
        var blendMode = new CompoundTag();
        blendMode.putString("srcColorFactor", "SRC_ALPHA");
        blendMode.putString("blendFunc", "ADD");
        blendMode.putInt("enableBlend", 1);
        blendMode.putString("srcAlphaFactor", "ONE");
        blendMode.putString("dstColorFactor", "ONE_MINUS_SRC_ALPHA");
        blendMode.putString("dstAlphaFactor", "ZERO");
        payloadElement0.put("blendMode", blendMode);

        // material section
        var material = new CompoundTag();
        material.putString("type", "texture");

        // data section (expanded)
        var data = new CompoundTag();
        data.putString("texture", "photon:textures/particle/circle.png");
        ListTag hdr =  new ListTag();
        hdr.add(FloatTag.ZERO);
        hdr.add(FloatTag.ZERO);
        hdr.add(FloatTag.ZERO);
        hdr.add(FloatTag.valueOf(1));
        data.put("hdr", hdr);
        data.putString("hdrMode", "ADDITIVE");
        data.put("pixelArt", new CompoundTag());
        data.putFloat("discardThreshold", 0.1f);
        material.put("data", data);

        material.putInt("depthMask", 0);
        material.putInt("depthTest", 1);
        payloadElement0.put("material", material);

        payload.add(payloadElement0);
        materials.put("payload", payload);
        materials.putInt("uid", 1);

        tag.put("materials", materials);


        return tag;


    }

    public static CompoundTag mapMaterial(CompoundTag materialTag){
        CompoundTag newMaterialTag = new CompoundTag();
        newMaterialTag.putByte("cull", materialTag.getByte("cull"));
        newMaterialTag.putByte("depthMask", materialTag.getByte("depthMask"));
        newMaterialTag.putByte("depthTest", materialTag.getByte("depthTest"));
        var blend = materialTag.getCompound("blendMode").copy();
        // Photon 1 used mixed-case names for these OpenGL equations; Photon 2 renamed them.
        switch (blend.getString("blendFunc")) {
            case "sub" -> blend.putString("blendFunc", "SUB");
            case "REVERSE_sub" -> blend.putString("blendFunc", "REVERSE_SUB");
        }
        newMaterialTag.put("blendMode", blend);
        newMaterialTag.put("material",  mapTypedValue(materialTag.getCompound("material"), false));
        // Photon 1 authored blend on the slot. Photon 2 follows the material when this is omitted.
        newMaterialTag.putString("renderStateSource", "CUSTOM");
        retainPresentFields(newMaterialTag, materialTag, "renderStateSource");
        return newMaterialTag;
    }


    public static CompoundTag mapLightTag(CompoundTag lightTag){
        CompoundTag newLightTag = new CompoundTag();
        newLightTag.putByte("_enable", lightTag.getByte("enable"));
        newLightTag.put("blockLight", MapperUtils.mapTypedValue(lightTag.getCompound("blockLight"), false));
        newLightTag.put("skyLight", MapperUtils.mapTypedValue(lightTag.getCompound("skyLight"), false));

        retainPresentFields(newLightTag, lightTag, "_enable");
        return newLightTag;
    }


    /** Photon 1 rendered bloom emitters at full brightness even when fixed lights were disabled.
     * This preserves the light behavior, not the old bloom post-processing or bloom color. */
    public static void applyLegacyBloomLight(CompoundTag mappedConfig, CompoundTag legacyConfig) {
        if (!legacyConfig.getCompound("renderer").getBoolean("bloomEffect")) return;
        var lights = mappedConfig.contains("lights", Tag.TAG_COMPOUND)
                ? mappedConfig.getCompound("lights") : mapLightTag(legacyConfig.getCompound("lights"));
        lights.putBoolean("legacyBloomFullBright", true);
        mappedConfig.put("lights", lights);
    }

    public static CompoundTag mapRendererTag(CompoundTag renderTag, CompoundTag configTag){
        CompoundTag newRendererTag = new CompoundTag();
        newRendererTag.putString("renderMode", renderTag.getString("renderMode"));
        if (renderTag.contains("model", Tag.TAG_COMPOUND)) {
            newRendererTag.put("model", renderTag.getCompound("model").copy());
        }
        newRendererTag.putByte("useBlockUV",  renderTag.getByte("useBlockUV"));
        newRendererTag.putByte("shade",  renderTag.getByte("shade"));
        newRendererTag.putString("layer",  renderTag.getString("layer"));
        newRendererTag.put("cull", mapCullTag(renderTag.getCompound("cull")));

        CompoundTag materialsTag = new CompoundTag();

        materialsTag.putInt("uid", 1);//Photon 1 doesn't support multiple materials on a single renderer!

        ListTag payloadTag = new ListTag();

        payloadTag.add(MapperUtils.mapMaterial(configTag.getCompound("material")));

        materialsTag.put("payload", payloadTag);

        newRendererTag.put("materials",  materialsTag);

        newRendererTag.putInt("orderInLayer", 0);
        newRendererTag.putByte("useGPUInstance", (byte)0);
        ListTag pivot = new ListTag();
        pivot.add(FloatTag.ZERO);
        pivot.add(FloatTag.ZERO);
        pivot.add(FloatTag.ZERO);
        newRendererTag.put("modelPivot", pivot);
        newRendererTag.putString("vertexSortingMode", "NONE");

        retainPresentFields(newRendererTag, renderTag, "orderInLayer", "useGPUInstance", "modelPivot", "vertexSortingMode");
        if (configTag.contains("material", Tag.TAG_COMPOUND)) newRendererTag.put("materials", materialsTag);
        return newRendererTag;
    }

    public static CompoundTag mapCullTag(CompoundTag cullTag){
        CompoundTag newCullTag = new CompoundTag();
        newCullTag.putByte("_enable", cullTag.getByte("enable"));
        if (cullTag.contains("from", Tag.TAG_COMPOUND) || cullTag.contains("to", Tag.TAG_COMPOUND)) {
            // Photon 2 persists an AABB under cullBox; its codec stores min/max vectors.
            var box = new CompoundTag();
            var from = cullTag.contains("from", Tag.TAG_COMPOUND) ? mapCoordsDouble(cullTag.get("from")) : constantCoords(-0.5);
            var to = cullTag.contains("to", Tag.TAG_COMPOUND) ? mapCoordsDouble(cullTag.get("to")) : constantCoords(0.5);
            box.put("min", from);
            box.put("max", to);
            newCullTag.put("cullBox", box);
        }
        return newCullTag;
    }

    private static ListTag constantCoords(double value) {
        var result = new ListTag();
        for (int i = 0; i < 3; i++) result.add(DoubleTag.valueOf(value));
        return result;
    }

    public static CompoundTag mapUVTag(CompoundTag uvTag){
        CompoundTag newUVTag = new CompoundTag();
        newUVTag.putByte("_enable", uvTag.getByte("enable"));
        newUVTag.putIntArray("tiles", new int[]{uvTag.getCompound("tiles").getInt("a"), uvTag.getCompound("tiles").getInt("b")});
        newUVTag.put("startFrame", MapperUtils.mapTypedValue(uvTag.getCompound("startFrame"), false));
        newUVTag.putFloat("cycle",  uvTag.getFloat("cycle"));
        newUVTag.putString("animation",  uvTag.getString("animation"));
        newUVTag.put("frameOverTime", MapperUtils.mapTypedValue(uvTag.getCompound("frameOverTime"), false));


        retainPresentFields(newUVTag, uvTag, "_enable");
        return newUVTag;
    }

    /** Do not turn omitted legacy fields into explicit zero/empty values; the decoder owns their defaults. */
    public static void retainPresentFields(CompoundTag mapped, CompoundTag source, String... synthesized) {
        var always = java.util.Set.of(synthesized);
        for (var key : java.util.List.copyOf(mapped.getAllKeys())) {
            if (!source.contains(key) && !always.contains(key)) mapped.remove(key);
        }
    }

    /** Photon 1 emitter version 0 stored its config directly on the object, before the config wrapper existed. */
    public static CompoundTag legacyEmitterConfig(CompoundTag emitter) {
        return emitter.contains("config", Tag.TAG_COMPOUND) ? emitter.getCompound("config") : emitter;
    }

    private static final java.util.Set<String> NUMBER_FUNCTION_TYPES = java.util.Set.of(
            "constant", "color", "random_constant", "random_color", "curve", "random_curve",
            "gradient", "random_gradient");
    private static final java.util.Set<String> MATERIAL_TYPES = java.util.Set.of(
            "texture", "custom_shader", "block_atlas");
    private static final java.util.Set<String> SHAPE_TYPES = java.util.Set.of(
            "circle", "cone", "sphere", "box", "cylinder", "dot", "function", "mesh");
    /**
     * Repair already-converted Photon 2 files that still carry Photon 1 codec names or shapes.
     * Unrecognized number-function types decode as constant 0, which makes particles invisible.
     * Never modifies the author's saved tag: callers must copy first, or use {@link #repairLegacyCodecs}.
     */
    public static Tag repairLegacyCodecs(Tag tag) {
        if (!needsLegacyCodecRepair(tag)) return tag;
        try {
            var copy = tag.copy();
            return normalizeLegacyCodecs(copy) ? copy : tag;
        } catch (Throwable failure) {
            Photon.LOGGER.warn("[fx-diagnostic] leftover Photon 1 codec repair failed; loading the original tag", failure);
            return tag;
        }
    }

    private static boolean needsLegacyCodecRepair(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            if (compound.contains("_type", Tag.TAG_STRING)) return true;
            if (compound.contains("type", Tag.TAG_STRING)) {
                var type = compound.getString("type");
                if (!type.equals(aliasLegacyType(type, compound))) return true;
                if (!compound.contains("data", Tag.TAG_COMPOUND)
                        && !compound.contains("resource", Tag.TAG_STRING)
                        && (NUMBER_FUNCTION_TYPES.contains(type) || MATERIAL_TYPES.contains(type)
                        || SHAPE_TYPES.contains(type))
                        && hasUnwrappedPayload(compound)) return true;
            }
            if (compound.contains("simulationSpace", Tag.TAG_STRING) && compound.getString("simulationSpace").isBlank()) {
                return true;
            }
            if (isPhoton1GradientColor(compound)) return true;
            if (compound.contains("isDecimals", Tag.TAG_BYTE) && !compound.contains("legacyIntegerInclusive")) return true;
            if (compound.contains("enable") && !compound.contains("_enable")) return true;
            if (compound.contains("shader", Tag.TAG_STRING) && !compound.contains("shaderLocation", Tag.TAG_STRING)) return true;
            if (compound.contains("tiles", Tag.TAG_COMPOUND) && compound.getCompound("tiles").contains("a")
                    && compound.getCompound("tiles").contains("b")) return true;
            if (compound.contains("shape") && compound.contains("position", Tag.TAG_COMPOUND)
                    && compound.getCompound("position").contains("x", Tag.TAG_ANY_NUMERIC)
                    && !compound.getCompound("position").contains("type")) return true;
            if (compound.contains("scale", Tag.TAG_COMPOUND) && !compound.contains("shape")
                    && isNumberFunctionCompound(compound.getCompound("scale"))) return true;
            if (compound.contains("emission") && compound.contains("startLifetime")
                    && !compound.contains("simulationSpace")
                    && compound.contains("startSize", Tag.TAG_COMPOUND)) return true;
            if (compound.contains("emitter", Tag.TAG_STRING) && !compound.contains("legacySubFXName")
                    && (compound.contains("emitProbability") || compound.contains("event"))) return true;
            if (compound.contains("bursts", Tag.TAG_LIST) && compound.contains("emissionRate")) return true;
            if (compound.contains("material", Tag.TAG_COMPOUND) && compound.contains("ratio")
                    && (compound.contains("widthOverTrail") || compound.contains("minimumVertexDistance"))) return true;
            if (compound.contains("material", Tag.TAG_COMPOUND) && compound.contains("renderer", Tag.TAG_COMPOUND)
                    && !hasMaterialsPayload(compound.getCompound("renderer"))
                    && isMaterialSettingCompound(compound.getCompound("material"))) return true;
            if (compound.contains("from", Tag.TAG_COMPOUND) && !compound.contains("cullBox")
                    && compound.getCompound("from").contains("x", Tag.TAG_ANY_NUMERIC)
                    && !compound.getCompound("from").contains("type")) return true;
            if (compound.contains("renderer", Tag.TAG_COMPOUND)
                    && compound.getCompound("renderer").getBoolean("bloomEffect")
                    && !compound.getCompound("lights").getBoolean("legacyBloomFullBright")) return true;
            var blendFunc = compound.contains("blendFunc", Tag.TAG_STRING) ? compound.getString("blendFunc") : "";
            if ("sub".equals(blendFunc) || "REVERSE_sub".equals(blendFunc)) return true;
            if (compound.contains("emissionMode", Tag.TAG_STRING) && compound.contains("emissionRate", Tag.TAG_COMPOUND)
                    && !compound.contains("legacyTickEmission")
                    && isUnwrappedLegacyNumberFunction(compound.getCompound("emissionRate"))) return true;
            if (isLegacyInheritVelocity(compound) && !compound.contains("legacyUnscaledVelocity")) return true;
            if (compound.contains("emitters", Tag.TAG_LIST) && isSubEmitterEntry(compound.getList("emitters", Tag.TAG_COMPOUND))) return true;
            if (compound.contains("localPosition", Tag.TAG_COMPOUND)
                    && compound.getCompound("localPosition").contains("x", Tag.TAG_ANY_NUMERIC)
                    && !compound.getCompound("localPosition").contains("type")) return true;
            if (asNumberFunction3List(compound) != null) return true;
            for (var key : compound.getAllKeys()) {
                var child = compound.get(key);
                if (("startSize".equals(key) || "startRotation".equals(key)) && child instanceof CompoundTag nested
                        && isNumberFunctionCompound(nested)) return true;
                if (needsLegacyCodecRepair(child)) return true;
            }
        } else if (tag instanceof ListTag list) {
            for (var entry : list) if (needsLegacyCodecRepair(entry)) return true;
        }
        return false;
    }

    private static boolean hasUnwrappedPayload(CompoundTag compound) {
        for (var key : compound.getAllKeys()) {
            if (!"type".equals(key) && !"_type".equals(key)) return true;
        }
        return false;
    }

    private static boolean normalizeLegacyCodecs(Tag tag) {
        boolean changed = false;
        if (tag instanceof CompoundTag compound) {
            changed |= rewriteLegacyTypeName(compound, "type");
            changed |= rewriteLegacyTypeName(compound, "_type");
            if (!compound.contains("type", Tag.TAG_STRING) && compound.contains("_type", Tag.TAG_STRING)) {
                compound.putString("type", aliasLegacyType(compound.getString("_type"), compound));
                changed = true;
            }
            changed |= wrapLegacyTypedPayload(compound);
            changed |= rewriteLegacyGradientPayload(compound);
            changed |= rewriteLegacyRandomRange(compound);
            changed |= rewriteLegacyCustomShader(compound);
            changed |= rewriteLegacyToggleEnable(compound);
            changed |= rewriteLegacyShapeMesh(compound);
            changed |= rewriteLegacyUvTiles(compound);
            changed |= rewriteLegacyShapeVectors(compound);
            changed |= rewriteLegacySizeOverLifetime(compound);
            changed |= rewriteLegacySimulationSpace(compound);
            changed |= rewriteLegacySubEmitter(compound);
            changed |= rewriteLegacyBurstList(compound);
            changed |= rewriteLegacyFlatTrails(compound);
            changed |= rewriteLegacyConfigMaterial(compound);
            changed |= rewriteLegacyCullBox(compound);
            changed |= rewriteLegacyBloomLight(compound);
            changed |= rewriteLegacyBlendFunc(compound);
            changed |= rewriteLegacyTickEmission(compound);
            changed |= rewriteLegacyInheritVelocity(compound);
            changed |= rewriteLegacySubEmitterList(compound);
            changed |= rewriteLegacyTransformCoords(compound);
            for (var key : java.util.List.copyOf(compound.getAllKeys())) {
                var child = compound.get(key);
                if (child instanceof CompoundTag nested) {
                    changed |= normalizeLegacyCodecs(nested);
                    var axes = asNumberFunction3List(nested);
                    if (axes != null) {
                        compound.put(key, axes);
                        changed = true;
                    } else if (("startSize".equals(key) || "startRotation".equals(key)) && isNumberFunctionCompound(nested)) {
                        compound.put(key, mapUniformTyped3VecFromMapped(nested));
                        changed = true;
                    }
                } else if (child instanceof ListTag list) {
                    changed |= normalizeLegacyCodecs(list);
                }
            }
            if (compound.contains("simulationSpace", Tag.TAG_STRING) && compound.getString("simulationSpace").isBlank()) {
                compound.remove("simulationSpace");
                changed = true;
            }
        } else if (tag instanceof ListTag list) {
            for (var entry : list) changed |= normalizeLegacyCodecs(entry);
        }
        return changed;
    }

    private static boolean rewriteLegacyTypeName(CompoundTag compound, String key) {
        if (!compound.contains(key, Tag.TAG_STRING)) return false;
        var original = compound.getString(key);
        var aliased = aliasLegacyType(original, compound);
        if (aliased.equals(original)) return false;
        compound.putString(key, aliased);
        return true;
    }

    static String aliasLegacyType(String type, CompoundTag compound) {
        return switch (type) {
            case "Constant" -> "constant";
            case "Color" -> "color";
            case "RandomConstant" -> "random_constant";
            case "RandomColor" -> "random_color";
            case "Curve" -> "curve";
            case "RandomCurve" -> "random_curve";
            case "Gradient" -> "gradient";
            case "RandomGradient" -> "random_gradient";
            case "TextureMaterial" -> "texture";
            case "CustomShaderMaterial" -> "custom_shader";
            case "BlockTextureSheetMaterial" -> "block_atlas";
            case "particle", "Particle" -> looksLikeFxObject(compound) ? "particle_emitter" : type;
            case "beam", "Beam" -> looksLikeFxObject(compound) ? "beam_emitter" : type;
            case "trail", "Trail" -> looksLikeFxObject(compound) ? "trail_emitter" : type;
            case "Circle" -> "circle";
            case "Cone" -> "cone";
            case "Sphere" -> "sphere";
            case "Box" -> "box";
            case "Cylinder" -> "cylinder";
            case "Dot" -> "dot";
            case "Function" -> "function";
            case "Mesh" -> "mesh";
            default -> type;
        };
    }

    private static boolean looksLikeFxObject(CompoundTag compound) {
        if (compound.contains("transform") || compound.contains("config")) return true;
        if (!compound.contains("data", Tag.TAG_COMPOUND)) return false;
        var data = compound.getCompound("data");
        return data.contains("transform") || data.contains("config") || data.contains("name");
    }

    /**
     * Photon 1 stored the payload next to {@code _type}. Photon 2 codecs read {@code data}.
     * Do not wrap sampler-like tags that already use {@code resource} at the top level,
     * or current Photon 2 tags that already use {@code data}.
     */
    private static boolean wrapLegacyTypedPayload(CompoundTag compound) {
        if (compound.contains("data", Tag.TAG_COMPOUND) || compound.contains("resource", Tag.TAG_STRING)) {
            return false;
        }
        var rawType = compound.contains("type", Tag.TAG_STRING)
                ? compound.getString("type") : compound.getString("_type");
        var type = aliasLegacyType(rawType, compound);
        if (!NUMBER_FUNCTION_TYPES.contains(type) && !MATERIAL_TYPES.contains(type)
                && !SHAPE_TYPES.contains(type)) return false;
        var data = new CompoundTag();
        for (var key : java.util.List.copyOf(compound.getAllKeys())) {
            if ("type".equals(key) || "_type".equals(key)) continue;
            data.put(key, compound.get(key));
            compound.remove(key);
        }
        if (data.isEmpty()) return false;
        compound.put("data", data);
        return true;
    }

    /**
     * Photon 1 Gradient.serializeNBT() was the LDLib1 colour channels ({@code a/r/g/b}).
     * Photon 2 stores {@code gradientColor}/{@code gradientColor0} with {@code a/rgb}.
     * Wrapping the old channels under {@code data} without renaming them decodes as an empty
     * gradient, which makes colour-over-lifetime fully transparent.
     */
    private static boolean rewriteLegacyGradientPayload(CompoundTag compound) {
        var type = aliasLegacyType(compound.contains("type", Tag.TAG_STRING)
                ? compound.getString("type") : compound.getString("_type"), compound);
        if ("gradient".equals(type)) {
            var payload = compound.contains("data", Tag.TAG_COMPOUND) ? compound.getCompound("data") : compound;
            if (payload.contains("gradientColor", Tag.TAG_COMPOUND)
                    && isPhoton1GradientColor(payload.getCompound("gradientColor"))) {
                payload.put("gradientColor", convertPhoton1GradientColor(payload.getCompound("gradientColor")));
                return true;
            }
            if (isPhoton1GradientColor(payload)) {
                var data = new CompoundTag();
                data.put("gradientColor", convertPhoton1GradientColor(payload));
                compound.put("data", data);
                return true;
            }
        } else if ("random_gradient".equals(type)) {
            var payload = compound.contains("data", Tag.TAG_COMPOUND) ? compound.getCompound("data") : compound;
            if (payload.contains("a", Tag.TAG_COMPOUND) && payload.contains("b", Tag.TAG_COMPOUND)
                    && !payload.contains("gradientColor0", Tag.TAG_COMPOUND)) {
                var data = new CompoundTag();
                data.put("gradientColor0", convertPhoton1GradientColor(payload.getCompound("a")));
                data.put("gradientColor1", convertPhoton1GradientColor(payload.getCompound("b")));
                compound.put("data", data);
                return true;
            }
            boolean changed = false;
            if (payload.contains("gradientColor0", Tag.TAG_COMPOUND)
                    && isPhoton1GradientColor(payload.getCompound("gradientColor0"))) {
                payload.put("gradientColor0", convertPhoton1GradientColor(payload.getCompound("gradientColor0")));
                changed = true;
            }
            if (payload.contains("gradientColor1", Tag.TAG_COMPOUND)
                    && isPhoton1GradientColor(payload.getCompound("gradientColor1"))) {
                payload.put("gradientColor1", convertPhoton1GradientColor(payload.getCompound("gradientColor1")));
                changed = true;
            }
            return changed;
        }
        return false;
    }

    private static boolean isPhoton1GradientColor(CompoundTag tag) {
        return tag.contains("r", Tag.TAG_LIST) && tag.contains("g", Tag.TAG_LIST) && tag.contains("b", Tag.TAG_LIST);
    }

    private static CompoundTag convertPhoton1GradientColor(CompoundTag tag) {
        if (!isPhoton1GradientColor(tag)) return tag.copy();
        return gradient(tag.getList("a", Tag.TAG_FLOAT), tag.getList("r", Tag.TAG_FLOAT),
                tag.getList("g", Tag.TAG_FLOAT), tag.getList("b", Tag.TAG_FLOAT));
    }

    private static ListTag mapUniformTyped3VecFromMapped(CompoundTag value) {
        var result = new ListTag();
        for (int i = 0; i < 3; i++) result.add(value.copy());
        return result;
    }

    /** Photon 1 integer ranges include the upper endpoint. Photon 2 only does that with this flag. */
    private static boolean rewriteLegacyRandomRange(CompoundTag compound) {
        var type = aliasLegacyType(compound.contains("type", Tag.TAG_STRING)
                ? compound.getString("type") : compound.getString("_type"), compound);
        if (!"random_constant".equals(type) && !"random_color".equals(type)) return false;
        var payload = compound.contains("data", Tag.TAG_COMPOUND) ? compound.getCompound("data") : compound;
        if (!payload.contains("isDecimals", Tag.TAG_BYTE) || payload.contains("legacyIntegerInclusive")) return false;
        if (!payload.getBoolean("isDecimals")) payload.putBoolean("legacyIntegerInclusive", true);
        payload.remove("isDecimals");
        return true;
    }

    private static boolean rewriteLegacyCustomShader(CompoundTag compound) {
        var type = aliasLegacyType(compound.contains("type", Tag.TAG_STRING)
                ? compound.getString("type") : compound.getString("_type"), compound);
        if (!"custom_shader".equals(type)) return false;
        var payload = compound.contains("data", Tag.TAG_COMPOUND) ? compound.getCompound("data") : compound;
        if (!payload.contains("shader", Tag.TAG_STRING) || payload.contains("shaderLocation", Tag.TAG_STRING)) return false;
        compound.put("data", mapPhoton1CustomShaderData(payload));
        return true;
    }

    static CompoundTag mapPhoton1CustomShaderData(CompoundTag photon1) {
        var dataTag = new CompoundTag();
        var shader = photon1.contains("shaderLocation", Tag.TAG_STRING)
                ? photon1.getString("shaderLocation") : photon1.getString("shader");
        if (!shader.isEmpty()) dataTag.putString("shaderLocation", shader);
        var oldUniforms = photon1.contains("uniform", Tag.TAG_COMPOUND) ? photon1.getCompound("uniform") : new CompoundTag();
        var shaderData = new CompoundTag();
        if (oldUniforms.contains("uniforms", Tag.TAG_COMPOUND)) {
            shaderData.put("uniforms", oldUniforms.getCompound("uniforms").copy());
        }
        var samplers = new CompoundTag();
        var oldSamplers = oldUniforms.getCompound("samplers");
        for (var key : oldSamplers.getAllKeys()) {
            if (!key.matches("[0-9]+") || !oldSamplers.contains(key, Tag.TAG_STRING)) continue;
            var texture = net.minecraft.resources.ResourceLocation.tryParse(oldSamplers.getString(key));
            if (texture == null) continue;
            var sampler = new CompoundTag();
            sampler.putString("type", "texture");
            sampler.putString("resource", texture.toString());
            samplers.put("Sampler" + key, sampler);
        }
        shaderData.put("samplers", samplers);
        var additional = new CompoundTag();
        additional.put("shaderData", shaderData);
        dataTag.put("_additional", additional);
        return dataTag;
    }

    /** LDLib2 toggle modules persist {@code _enable}; Photon 1 wrote {@code enable}. */
    private static boolean rewriteLegacyToggleEnable(CompoundTag compound) {
        if (!compound.contains("enable") || compound.contains("_enable")) return false;
        compound.putBoolean("_enable", compound.getBoolean("enable"));
        return true;
    }

    private static boolean rewriteLegacyShapeMesh(CompoundTag compound) {
        var type = aliasLegacyType(compound.contains("type", Tag.TAG_STRING)
                ? compound.getString("type") : compound.getString("_type"), compound);
        if (!"mesh".equals(type)) return false;
        var payload = compound.contains("data", Tag.TAG_COMPOUND) ? compound.getCompound("data") : compound;
        if (!payload.contains("mesh", Tag.TAG_COMPOUND) || payload.contains("meshData", Tag.TAG_COMPOUND)) return false;
        payload.put("meshData", payload.getCompound("mesh").copy());
        payload.remove("mesh");
        return true;
    }

    /** Photon 1 UV tiles were a Range `{a,b}`. Photon 2 Vector2i reads an int array. */
    private static boolean rewriteLegacyUvTiles(CompoundTag compound) {
        if (!compound.contains("tiles", Tag.TAG_COMPOUND)) return false;
        var tiles = compound.getCompound("tiles");
        if (!tiles.contains("a") || !tiles.contains("b") || tiles.contains("type")) return false;
        compound.putIntArray("tiles", new int[]{tiles.getInt("a"), tiles.getInt("b")});
        return true;
    }

    /**
     * Photon 1 ShapeSetting stored position/rotation/scale as raw x/y/z numbers before
     * NumberFunction3. Mapping those as typed compounds produced invalid functions.
     */
    private static boolean rewriteLegacyShapeVectors(CompoundTag compound) {
        if (!compound.contains("shape") || !compound.contains("position", Tag.TAG_COMPOUND)) return false;
        var position = compound.getCompound("position");
        if (!position.contains("x", Tag.TAG_ANY_NUMERIC) || position.contains("type")
                || position.contains("x", Tag.TAG_COMPOUND)) return false;
        boolean changed = false;
        for (var field : java.util.List.of("position", "rotation", "scale")) {
            if (!compound.contains(field, Tag.TAG_COMPOUND)) continue;
            var source = compound.getCompound(field);
            if (!source.contains("x", Tag.TAG_ANY_NUMERIC) || source.contains("type")) continue;
            var values = new ListTag();
            for (var axis : java.util.List.of("x", "y", "z")) {
                var constant = new CompoundTag();
                constant.putString("type", "constant");
                var data = new CompoundTag();
                data.putDouble("number", source.getDouble(axis));
                constant.put("data", data);
                values.add(constant);
            }
            compound.put(field, values);
            changed = true;
        }
        return changed;
    }

    /** Photon 1 SizeOverLifetime stored a single {@code scale} function before {@code size}. */
    private static boolean rewriteLegacySizeOverLifetime(CompoundTag compound) {
        if (compound.contains("shape") || !compound.contains("scale", Tag.TAG_COMPOUND)) return false;
        var scale = compound.getCompound("scale");
        if (!isNumberFunctionCompound(scale)) return false;
        compound.put("size", mapUniformTyped3VecFromMapped(scale));
        return true;
    }

    /** Early Photon 1 particle configs omitted simulationSpace and spawned in World. */
    private static boolean rewriteLegacySimulationSpace(CompoundTag compound) {
        if (!compound.contains("emission") || !compound.contains("startLifetime")
                || compound.contains("simulationSpace")
                || !compound.contains("startSize", Tag.TAG_COMPOUND)) return false;
        compound.putString("simulationSpace", "World");
        return true;
    }

    private static boolean rewriteLegacySubEmitter(CompoundTag compound) {
        if (!compound.contains("emitter", Tag.TAG_STRING) || compound.contains("legacySubFXName")) return false;
        if (!compound.contains("emitProbability") && !compound.contains("event")) return false;
        compound.putString("legacySubFXName", compound.getString("emitter"));
        if (!compound.contains("legacyTickGate")) compound.putBoolean("legacyTickGate", true);
        return true;
    }

    /** Photon 1 wrote bursts as a raw list; Photon 2 managed lists use `{uid, payload}`. */
    private static boolean rewriteLegacyBurstList(CompoundTag compound) {
        if (!compound.contains("emissionRate") || !compound.contains("bursts", Tag.TAG_LIST)) return false;
        var list = compound.getList("bursts", Tag.TAG_COMPOUND);
        var envelope = new CompoundTag();
        envelope.put("payload", list.copy());
        envelope.putInt("uid", list.size());
        compound.put("bursts", envelope);
        if (!compound.contains("legacyTickEmission")) compound.putBoolean("legacyTickEmission", true);
        return true;
    }

    /** Before TrailConfig was nested, trail material and widths lived on TrailsSetting. */
    private static boolean rewriteLegacyFlatTrails(CompoundTag compound) {
        if (!compound.contains("material", Tag.TAG_COMPOUND) || !compound.contains("ratio")) return false;
        if (!compound.contains("widthOverTrail") && !compound.contains("colorOverTrail")
                && !compound.contains("minimumVertexDistance")) return false;
        var config = compound.contains("config", Tag.TAG_COMPOUND) ? compound.getCompound("config") : new CompoundTag();
        for (var field : java.util.List.of("widthOverTrail", "colorOverTrail")) {
            if (compound.contains(field, Tag.TAG_COMPOUND))
                config.put(field, compound.getCompound(field).copy());
        }
        if (compound.contains("uvMode", Tag.TAG_STRING)) config.putString("uvMode", compound.getString("uvMode"));
        if (compound.contains("minimumVertexDistance"))
            config.putFloat("minVertexDistance", compound.getFloat("minimumVertexDistance"));
        var renderer = config.contains("renderer", Tag.TAG_COMPOUND)
                ? config.getCompound("renderer") : new CompoundTag();
        if (!renderer.contains("materials")) {
            var materials = new CompoundTag();
            var payload = new ListTag();
            payload.add(compound.getCompound("material").copy());
            materials.put("payload", payload);
            materials.putInt("uid", 1);
            renderer.put("materials", materials);
        }
        config.put("renderer", renderer);
        compound.put("config", config);
        return true;
    }

    /** Photon 1 stored the material slot beside the renderer, not in renderer.materials. */
    private static boolean rewriteLegacyConfigMaterial(CompoundTag compound) {
        if (!compound.contains("material", Tag.TAG_COMPOUND) || !compound.contains("renderer", Tag.TAG_COMPOUND)) return false;
        var renderer = compound.getCompound("renderer");
        if (hasMaterialsPayload(renderer)) return false;
        var slot = compound.getCompound("material");
        if (!isMaterialSettingCompound(slot)) return false;
        slot = slot.copy();
        if (!slot.contains("renderStateSource", Tag.TAG_STRING)) slot.putString("renderStateSource", "CUSTOM");
        var materials = new CompoundTag();
        var payload = new ListTag();
        payload.add(slot);
        materials.put("payload", payload);
        materials.putInt("uid", 1);
        renderer.put("materials", materials);
        return true;
    }

    private static boolean hasMaterialsPayload(CompoundTag renderer) {
        if (renderer.contains("materials", Tag.TAG_LIST))
            return !renderer.getList("materials", Tag.TAG_COMPOUND).isEmpty();
        if (!renderer.contains("materials", Tag.TAG_COMPOUND)) return false;
        return renderer.getCompound("materials").getList("payload", Tag.TAG_COMPOUND).size() > 0;
    }

    private static boolean isMaterialSettingCompound(CompoundTag tag) {
        return tag.contains("blendMode") || tag.contains("material", Tag.TAG_COMPOUND);
    }

    /** Photon 1 cull used from/to vectors; Photon 2 persists an AABB under cullBox. */
    private static boolean rewriteLegacyCullBox(CompoundTag compound) {
        if (compound.contains("cullBox") || !compound.contains("from", Tag.TAG_COMPOUND)) return false;
        var from = compound.getCompound("from");
        if (!from.contains("x", Tag.TAG_ANY_NUMERIC) || from.contains("type")) return false;
        var box = new CompoundTag();
        box.put("min", compound.contains("from") ? mapCoordsDouble(from) : constantCoords(-0.5));
        box.put("max", compound.contains("to", Tag.TAG_COMPOUND) ? mapCoordsDouble(compound.get("to")) : constantCoords(0.5));
        compound.put("cullBox", box);
        return true;
    }

    /** Photon 1 bloom emitters drew full-bright even with the light group disabled. */
    private static boolean rewriteLegacyBloomLight(CompoundTag compound) {
        if (!compound.contains("renderer", Tag.TAG_COMPOUND)
                || !compound.getCompound("renderer").getBoolean("bloomEffect")) return false;
        var lights = compound.contains("lights", Tag.TAG_COMPOUND) ? compound.getCompound("lights") : new CompoundTag();
        if (lights.getBoolean("legacyBloomFullBright")) return false;
        lights.putBoolean("legacyBloomFullBright", true);
        compound.put("lights", lights);
        return true;
    }

    /** Photon 1 used mixed-case OpenGL equation names. */
    private static boolean rewriteLegacyBlendFunc(CompoundTag compound) {
        if (!compound.contains("blendFunc", Tag.TAG_STRING)) return false;
        var original = compound.getString("blendFunc");
        var aliased = switch (original) {
            case "sub" -> "SUB";
            case "REVERSE_sub" -> "REVERSE_SUB";
            default -> original;
        };
        if (aliased.equals(original)) return false;
        compound.putString("blendFunc", aliased);
        return true;
    }

    /** Photon 1 sampled emission on integer ages, including age zero. */
    private static boolean rewriteLegacyTickEmission(CompoundTag compound) {
        if (!compound.contains("emissionMode", Tag.TAG_STRING) || !compound.contains("emissionRate", Tag.TAG_COMPOUND)
                || compound.contains("legacyTickEmission")) return false;
        if (!compound.contains("bursts", Tag.TAG_LIST)
                && !isUnwrappedLegacyNumberFunction(compound.getCompound("emissionRate"))) return false;
        compound.putBoolean("legacyTickEmission", true);
        return true;
    }

    /** Photon 1 inherited the raw emitter velocity in both CURRENT and INITIAL. */
    private static boolean rewriteLegacyInheritVelocity(CompoundTag compound) {
        if (!isLegacyInheritVelocity(compound) || compound.contains("legacyUnscaledVelocity")) return false;
        compound.putBoolean("legacyUnscaledVelocity", true);
        return true;
    }

    private static boolean isLegacyInheritVelocity(CompoundTag compound) {
        var mode = compound.contains("mode", Tag.TAG_STRING) ? compound.getString("mode") : "";
        if (!"CURRENT".equals(mode) && !"INITIAL".equals(mode)) return false;
        if (!compound.contains("multiply", Tag.TAG_COMPOUND)) return false;
        return isUnwrappedLegacyNumberFunction(compound.getCompound("multiply"))
                || (compound.contains("enable") && !compound.contains("_enable"));
    }

    /** Photon 1 wrote sub-emitters as a raw list; Photon 2 managed lists use `{uid, payload}`. */
    private static boolean rewriteLegacySubEmitterList(CompoundTag compound) {
        if (!compound.contains("emitters", Tag.TAG_LIST)) return false;
        var list = compound.getList("emitters", Tag.TAG_COMPOUND);
        if (!isSubEmitterEntry(list)) return false;
        var envelope = new CompoundTag();
        envelope.put("payload", list.copy());
        envelope.putInt("uid", list.size());
        compound.put("emitters", envelope);
        return true;
    }

    private static boolean isSubEmitterEntry(ListTag list) {
        if (list.isEmpty()) return false;
        var first = list.getCompound(0);
        return first.contains("event") || first.contains("emitter") || first.contains("emitProbability");
    }

    /** Photon 1 transforms stored x/y/z compounds; LDLib2 reads float lists. */
    private static boolean rewriteLegacyTransformCoords(CompoundTag compound) {
        if (!compound.contains("localPosition") && !compound.contains("localScale") && !compound.contains("localRotation"))
            return false;
        boolean changed = false;
        if (compound.contains("localPosition", Tag.TAG_COMPOUND)
                && compound.getCompound("localPosition").contains("x", Tag.TAG_ANY_NUMERIC)
                && !compound.getCompound("localPosition").contains("type")) {
            compound.put("localPosition", mapTransformCoords(compound.get("localPosition"), 0, 0, 0));
            changed = true;
        }
        if (compound.contains("localScale", Tag.TAG_COMPOUND)
                && compound.getCompound("localScale").contains("x", Tag.TAG_ANY_NUMERIC)
                && !compound.getCompound("localScale").contains("type")) {
            compound.put("localScale", mapTransformCoords(compound.get("localScale"), 1, 1, 1));
            changed = true;
        }
        if (compound.contains("localRotation", Tag.TAG_COMPOUND)
                && compound.getCompound("localRotation").contains("x", Tag.TAG_ANY_NUMERIC)) {
            compound.put("localRotation", mapTransformRotation(compound.get("localRotation")));
            changed = true;
        } else if (compound.contains("localRotation", Tag.TAG_LIST)
                && compound.getList("localRotation", Tag.TAG_FLOAT).size() == 3) {
            compound.put("localRotation", mapTransformRotation(compound.get("localRotation")));
            changed = true;
        }
        return changed;
    }

    private static boolean isUnwrappedLegacyNumberFunction(CompoundTag tag) {
        if (tag.contains("_type", Tag.TAG_STRING)) return true;
        var type = tag.contains("type", Tag.TAG_STRING) ? tag.getString("type") : "";
        if (type.isEmpty()) return false;
        return !type.equals(type.toLowerCase()) || (!tag.contains("data") && isNumberFunctionCompound(tag));
    }

    private static boolean isNumberFunctionCompound(CompoundTag tag) {
        var type = aliasLegacyType(tag.contains("type", Tag.TAG_STRING) ? tag.getString("type") : tag.getString("_type"), tag);
        return NUMBER_FUNCTION_TYPES.contains(type);
    }

    @Nullable
    private static ListTag asNumberFunction3List(CompoundTag tag) {
        if (!tag.contains("x", Tag.TAG_COMPOUND) || !tag.contains("y", Tag.TAG_COMPOUND)
                || !tag.contains("z", Tag.TAG_COMPOUND)) return null;
        var x = tag.getCompound("x");
        var y = tag.getCompound("y");
        var z = tag.getCompound("z");
        if (!isNumberFunctionCompound(x) || !isNumberFunctionCompound(y) || !isNumberFunctionCompound(z)) return null;
        var list = new ListTag();
        list.add(x);
        list.add(y);
        list.add(z);
        return list;
    }

    public static CompoundTag mapTransformTag(CompoundTag transformTag){
        CompoundTag newTransformTag = new CompoundTag();
        // Preserve authored sibling order. Dropping this list causes converted effects with layered
        // children to be rearranged when LDLib rebuilds their hierarchy.
        ListTag children = new ListTag();
        if (transformTag.get("_childrenId") instanceof ListTag oldChildren) {
            for (var child : oldChildren) {
                var id = readTransformUuid(child, null);
                if (id != null) children.add(StringTag.valueOf(id.toString()));
            }
        }
        newTransformTag.put("_childrenId", children);
        // Photon 1 leaves _parentId out for root-level emitters. A missing id is also valid in
        // older projects; the Photon 2 scene assigns that object a fresh identity on import.
        newTransformTag.putString("_parentId", readTransformUuid(transformTag.get("_parentId"), new UUID(0L, 0L)).toString());
        newTransformTag.putString("id", readTransformUuid(transformTag.get("id"), UUID.randomUUID()).toString());
        newTransformTag.put("localRotation", mapTransformRotation(transformTag.get("localRotation")));
        newTransformTag.put("localScale", mapTransformCoords(transformTag.get("localScale"), 1, 1, 1));
        newTransformTag.put("localPosition", mapTransformCoords(transformTag.get("localPosition"), 0, 0, 0));

        return newTransformTag;
    }

    private static UUID readTransformUuid(Tag tag, UUID fallback) {
        if (tag instanceof IntArrayTag array && array.getAsIntArray().length == 4) {
            return NbtUtils.loadUUID(array);
        }
        if (tag instanceof StringTag string) {
            try {
                return UUID.fromString(string.getAsString());
            } catch (IllegalArgumentException ignored) {
                // fall through to the scene-compatible default
            }
        }
        return fallback;
    }

    /** LDLib2's quaternion codec requires four components, even for an absent legacy rotation. */
    private static ListTag mapTransformRotation(Tag tag) {
        var rotation = new ListTag();
        if (tag instanceof CompoundTag coords) {
            rotation.add(FloatTag.valueOf(coords.getFloat("x")));
            rotation.add(FloatTag.valueOf(coords.getFloat("y")));
            rotation.add(FloatTag.valueOf(coords.getFloat("z")));
            rotation.add(FloatTag.valueOf(coords.contains("w", Tag.TAG_ANY_NUMERIC) ? coords.getFloat("w") : 1f));
        } else if (tag instanceof ListTag values && values.size() == 4 && values.getElementType() == Tag.TAG_FLOAT) {
            return values.copy();
        } else {
            rotation.add(FloatTag.ZERO);
            rotation.add(FloatTag.ZERO);
            rotation.add(FloatTag.ZERO);
            rotation.add(FloatTag.valueOf(1f));
        }
        return rotation;
    }

    private static ListTag mapTransformCoords(Tag tag, float x, float y, float z) {
        if (tag instanceof CompoundTag coords) return mapCoords(coords);
        ListTag result = new ListTag();
        result.add(FloatTag.valueOf(x));
        result.add(FloatTag.valueOf(y));
        result.add(FloatTag.valueOf(z));
        return result;
    }
}
