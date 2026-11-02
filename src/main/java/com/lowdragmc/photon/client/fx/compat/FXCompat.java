package com.lowdragmc.photon.client.fx.compat;


import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.nbt.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.lowdragmc.photon.client.fx.FXHelper;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A compatibility layer for porting Photon 1 FX to Photon 2
 * @author Cdogsnappy
 */
public class FXCompat {

    private static final Path FX_CVT_PATH = Path.of(LDLib2.getAssetsDir() + "/photon/fx_old");

    public static int convertFX() {
        var converted = convertFX(FX_CVT_PATH, LDLib2.getAssetsDir().toPath().resolve("photon/fx"));
        if (converted > 0) FXHelper.clearCache();
        return converted;
    }

    /** Convert a legacy tree without overwriting any existing Photon 2 effects. */
    public static int convertFX(Path source, Path destination) {
        if (!Files.isDirectory(source)) return 0;
        AtomicInteger count = new AtomicInteger();
        try (var paths = Files.walk(source)) {
            paths.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".fx")).forEach(path -> {
                try {
                    var target = destination.resolve(source.relativize(path));
                    if (Files.exists(target)) {
                        Photon.LOGGER.warn("Skipping Photon 1 FX {}: destination already exists", path);
                        return;
                    }
                    CompoundTag photon1FX;
                    try (var input = java.nio.file.Files.newInputStream(path)) {
                        photon1FX = NbtIo.readCompressed(input);
                    }
                    if (!isLegacyEffect(photon1FX)) {
                        Photon.LOGGER.warn("Skipping {}: not a recognized Photon 1 effect", path);
                        return;
                    }
                    CompoundTag photon2FX = mapEffect(photon1FX);
                    // the mappers emit the current format; stamp it so the datafixer stays a no-op
                    photon2FX.putInt("version", FXProject.VERSION);
                    Files.createDirectories(target.getParent());
                    try (var output = Files.newOutputStream(target, java.nio.file.StandardOpenOption.CREATE_NEW)) {
                        NbtIo.writeCompressed(photon2FX, output);
                    }
                    count.getAndIncrement();
                }
                catch(Exception e){
                    Photon.LOGGER.error("Failed to convert Photon 1 FX {}", path, e);
                }
            });
        } catch (IOException e) {
            Photon.LOGGER.error("Failed to read Photon 1 FX directory {}", source, e);
        }
        return count.get();
    }

    public static boolean isLegacyEffect(CompoundTag tag) {
        var definition = tag.contains("fx", Tag.TAG_COMPOUND) ? tag.getCompound("fx") : tag;
        return (definition.contains("mainFX", Tag.TAG_COMPOUND)
                && definition.getCompound("mainFX").contains("fxObjects", Tag.TAG_LIST))
                || tag.contains("emitters", Tag.TAG_LIST);
    }

    public static CompoundTag mapEffect(CompoundTag fx) {
        //top level mapping
        CompoundTag fxData = new CompoundTag();
        CompoundTag new_fx = new CompoundTag();
        //drill down to fxData
        ListTag fxObjects;
        if (fx.contains("emitters", Tag.TAG_LIST)) {
            fxObjects = fx.getList("emitters", Tag.TAG_COMPOUND);
        } else {
            var definition = fx.contains("fx", Tag.TAG_COMPOUND) ? fx.getCompound("fx") : fx;
            fxObjects = definition.getCompound("mainFX").getList("fxObjects", Tag.TAG_COMPOUND);
        }
        fxData.put("fxObjects", mapObjects(fxObjects));
        new_fx.put("fxData", fxData);
        var definition = fx.contains("fx", Tag.TAG_COMPOUND) ? fx.getCompound("fx") : fx;
        var oldSubEffects = definition.getCompound("subFXs");
        if (!oldSubEffects.isEmpty()) {
            var subEffects = new CompoundTag();
            for (var name : oldSubEffects.getAllKeys()) {
                var subData = new CompoundTag();
                subData.put("fxObjects", mapObjects(oldSubEffects.getCompound(name).getList("fxObjects", Tag.TAG_COMPOUND)));
                var subEffect = new CompoundTag();
                subEffect.put("fxData", subData);
                subEffects.put(name, subEffect);
            }
            new_fx.put("legacySubFXs", subEffects);
        }
        return new_fx;
    }

    private static ListTag mapObjects(ListTag fxObjects) {
        var fxObj = new ListTag();
        for (Tag object : fxObjects) {
            CompoundTag fxObject = (CompoundTag) object;
            switch (fxObject.getString("_type")) {
                case "beam":
                    fxObj.add(BeamEmitterMapper.mapBeamEmitter(fxObject));
                    break;
                case "trail":
                    fxObj.add(TrailEmitterMapper.mapTrailEmitter(fxObject));
                    break;
                case "particle":
                    fxObj.add(ParticleEmitterMapper.mapParticleEmitter(fxObject));
                    break;
                case "empty":
                    fxObj.add(EmptyMapper.mapEmpty(fxObject));
                    break;
                default:
                    Photon.LOGGER.warn("Detected unknown type {}", fxObject.getString("_type"));

            }
        }
        return fxObj;
    }

}
