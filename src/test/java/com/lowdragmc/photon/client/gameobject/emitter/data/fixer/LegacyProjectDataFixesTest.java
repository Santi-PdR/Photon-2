package com.lowdragmc.photon.client.gameobject.emitter.data.fixer;

import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.DataFixerBuilder;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/** V1-to-V3 project migration checks using Forge 1.20.1 NBT accessors. */
class LegacyProjectDataFixesTest {
    private static final BiFunction<Integer, Schema, Schema> SAME = Schema::new;

    private static DataFixer fixer() {
        var builder = new DataFixerBuilder(5);
        var schema1 = builder.addSchema(1, PhotonSchemas.V1::new);
        var schema2 = builder.addSchema(2, SAME);
        var schema3 = builder.addSchema(3, SAME);
        builder.addSchema(4, SAME);
        var schema5 = builder.addSchema(5, SAME);
        builder.addFixer(new MaterialToRendererMaterialsFix(schema2));
        builder.addFixer(new UVAnimationTilesFix(schema3));
        builder.addFixer(new ModelLocationToModelSourceFix(schema5));
        return builder.buildUnoptimized();
    }

    private static CompoundTag apply(int fromVersion, int toVersion, CompoundTag data) {
        var fixed = fixer().update(PhotonReferences.FX_PROJECT,
                new Dynamic<>(NbtOps.INSTANCE, data), fromVersion, toVersion);
        return (CompoundTag) fixed.getValue();
    }

    /** {@code data.fx.fxData.fxObjects[0].data.config = config}. */
    private static CompoundTag project(CompoundTag config) {
        var objectData = new CompoundTag();
        objectData.put("config", config);
        var fxObject = new CompoundTag();
        fxObject.put("data", objectData);
        var fxObjects = new ListTag();
        fxObjects.add(fxObject);
        var fxData = new CompoundTag();
        fxData.put("fxObjects", fxObjects);
        var fx = new CompoundTag();
        fx.put("fxData", fxData);
        var data = new CompoundTag();
        data.put("fx", fx);
        return data;
    }

    private static CompoundTag configOf(CompoundTag fixedProject) {
        return fixedProject.getCompound("fx").getCompound("fxData")
                .getList("fxObjects", Tag.TAG_COMPOUND).getCompound(0)
                .getCompound("data").getCompound("config");
    }

    private static CompoundTag materialConfig(String materialName) {
        var material = new CompoundTag();
        material.putString("type", materialName);
        var renderer = new CompoundTag();
        renderer.putString("renderMode", "Billboard");
        var config = new CompoundTag();
        config.put("material", material);
        config.put("renderer", renderer);
        return config;
    }

    private static void assertRendererMaterials(CompoundTag config, String expectedType) {
        assertFalse(config.contains("material"), "legacy material key must be removed");
        var materials = config.getCompound("renderer").getCompound("materials");
        assertEquals(1, materials.getInt("uid"), materials.toString());
        var payload = materials.getList("payload", Tag.TAG_COMPOUND);
        assertEquals(1, payload.size());
        assertEquals(expectedType, payload.getCompound(0).getString("type"));
    }

    private static CompoundTag tiledUVConfig(int a, int b) {
        var tiles = new CompoundTag();
        tiles.putInt("a", a);
        tiles.putInt("b", b);
        var uvAnimation = new CompoundTag();
        uvAnimation.put("tiles", tiles);
        var config = new CompoundTag();
        config.put("uvAnimation", uvAnimation);
        return config;
    }

    @Test
    void movesMainAndTrailMaterialsIntoRendererMaterialPayloads() {
        var config = materialConfig("texture");
        var trails = new CompoundTag();
        trails.put("config", materialConfig("sprite"));
        config.put("trails", trails);

        var fixed = configOf(apply(1, 2, project(config)));

        assertRendererMaterials(fixed, "texture");
        assertRendererMaterials(fixed.getCompound("trails").getCompound("config"), "sprite");
    }

    @Test
    void convertsMainUVTileDimensionsIntoIntegerList() {
        var fixed = configOf(apply(2, 3, project(tiledUVConfig(4, 7))));
        var uvAnimation = fixed.getCompound("uvAnimation");
        assertArrayEquals(new int[]{4, 7}, uvAnimation.getIntArray("tiles"), uvAnimation.toString());
    }

    @Test
    void convertsUVTilesRecursivelyInsideTrails() {
        var config = new CompoundTag();
        var trails = new CompoundTag();
        trails.put("config", tiledUVConfig(3, 5));
        config.put("trails", trails);

        var trailConfig = configOf(apply(2, 3, project(config)))
                .getCompound("trails").getCompound("config");
        var uvAnimation = trailConfig.getCompound("uvAnimation");
        assertArrayEquals(new int[]{3, 5}, uvAnimation.getIntArray("tiles"), trailConfig.toString());
    }
}
