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
                dataTag.putString("shaderLocation", typedValueTag.getString("shader"));
                var oldUniforms = typedValueTag.getCompound("uniform");
                var shaderData = new CompoundTag();
                shaderData.put("uniforms", oldUniforms.getCompound("uniforms").copy());
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
            if (asNumberFunction3List(compound) != null) return true;
            for (var key : compound.getAllKeys()) {
                if (needsLegacyCodecRepair(compound.get(key))) return true;
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
            for (var key : java.util.List.copyOf(compound.getAllKeys())) {
                var child = compound.get(key);
                if (child instanceof CompoundTag nested) {
                    changed |= normalizeLegacyCodecs(nested);
                    var axes = asNumberFunction3List(nested);
                    if (axes != null) {
                        compound.put(key, axes);
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
