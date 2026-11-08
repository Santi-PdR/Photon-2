package com.lowdragmc.photon.client.fx.compat;

import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec2;

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
        newMaterialTag.put("blendMode", materialTag.getCompound("blendMode"));
        newMaterialTag.put("material",  mapTypedValue(materialTag.getCompound("material"), false));

        return newMaterialTag;
    }


    public static CompoundTag mapLightTag(CompoundTag lightTag){
        CompoundTag newLightTag = new CompoundTag();
        newLightTag.putByte("_enable", lightTag.getByte("enable"));
        if(newLightTag.getByte("_enable") == 1){
            newLightTag.put("blockLight", MapperUtils.mapTypedValue(lightTag.getCompound("blockLight"), false));
            newLightTag.put("skyLight", MapperUtils.mapTypedValue(lightTag.getCompound("skyLight"), false));
        }
        return newLightTag;
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
        if(newCullTag.getByte("_enable") == 1){
            newCullTag.put("min", MapperUtils.mapCoordsDouble(cullTag.get("from")));
            newCullTag.put("max", MapperUtils.mapCoordsDouble(cullTag.get("to")));
        }
        return newCullTag;
    }

    public static CompoundTag mapUVTag(CompoundTag uvTag){
        CompoundTag newUVTag = new CompoundTag();
        newUVTag.putByte("_enable", uvTag.getByte("enable"));
        if(newUVTag.getByte("_enable") == 1){
            newUVTag.putIntArray("tiles", new int[]{uvTag.getCompound("tiles").getInt("a"), uvTag.getCompound("tiles").getInt("b")});
            newUVTag.put("startFrame", MapperUtils.mapTypedValue(uvTag.getCompound("startFrame"), false));
            newUVTag.putFloat("cycle",  uvTag.getFloat("cycle"));
            newUVTag.putString("animation",  uvTag.getString("animation"));
            newUVTag.put("frameOverTime", MapperUtils.mapTypedValue(uvTag.getCompound("frameOverTime"), false));
        }

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
