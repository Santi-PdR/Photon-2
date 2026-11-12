package com.lowdragmc.photon.client.fx.compat;


import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.nbt.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.util.PhotonPaths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A compatibility layer for porting Photon 1 FX to Photon 2
 * @author Cdogsnappy
 */
public class FXCompat {

    private static final Path FX_CVT_PATH = PhotonPaths.getAssetsDir().toPath().resolve("photon/fx_old");

    /**
     * What one conversion pass did, file by file. The command used to report only a count, so "it converted but the
     * list is not complete" had no answer: a file that already had a destination, or that was not recognised, was
     * skipped with a log line nobody sees and no mention in the chat feedback.
     *
     * @param converted files written this pass
     * @param skipped   files deliberately left alone, with the reason
     * @param failed    files that threw, with the reason
     * @param sources   the folders that were read, so an empty run says where it looked
     */
    public record Report(int converted, List<String> skipped, List<String> failed, List<String> sources) {
        public static Report empty() {
            return new Report(0, List.of(), List.of(), List.of());
        }

        public Report merge(Report other) {
            var mergedSkipped = new ArrayList<>(skipped);
            mergedSkipped.addAll(other.skipped);
            var mergedFailed = new ArrayList<>(failed);
            mergedFailed.addAll(other.failed);
            var mergedSources = new ArrayList<>(sources);
            mergedSources.addAll(other.sources);
            return new Report(converted + other.converted, mergedSkipped, mergedFailed, mergedSources);
        }

        /** Everything worth showing, converted first: a player who asked for a conversion wants the whole story. */
        public List<String> lines() {
            var lines = new ArrayList<String>();
            lines.add("converted " + converted + " effect(s) from " + (sources.isEmpty() ? "no folder" : String.join(", ", sources)));
            for (var failure : failed) lines.add("failed: " + failure);
            for (var skip : skipped) lines.add("skipped: " + skip);
            return lines;
        }
    }

    public static int convertFX() {
        return convertAllFX().converted();
    }

    /**
     * Every legacy folder Photon knows about, converted in one pass with a full report. The workspace folder
     * ({@code photon/assets/photon/fx_old}) and the two old editor trees are all read: an author who kept their
     * Photon 1 effects in any of them gets them converted, rather than only the first one that happened to exist.
     */
    public static Report convertAllFX() {
        var destination = destination();
        var report = Report.empty();
        for (var source : legacyRoots()) {
            report = report.merge(convertFX(source, destination));
        }
        report = report.merge(reportNestedLeftovers(destination));
        if (report.converted() > 0) FXHelper.clearCache();
        return report;
    }

    /**
     * Effects an older build wrote under a subfolder of the destination ({@code fx/fx_old/matrix.fx}) are invisible to
     * the picker: their id carries the subfolder. They are reported rather than moved, because a folder the author
     * made on purpose must not be rewritten silently.
     */
    private static Report reportNestedLeftovers(Path destination) {
        var skipped = new ArrayList<String>();
        if (!Files.isDirectory(destination)) return Report.empty();
        try (var paths = Files.walk(destination)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".fx"))
                    .filter(path -> path.getParent() != null && !path.getParent().equals(destination))
                    .forEach(path -> skipped.add(path.getFileName() + ": sits in a subfolder of the effect folder, "
                            + "so its id has an extra path segment and the picker cannot list it"));
        } catch (IOException ignored) {
            // nothing to report
        }
        return new Report(0, List.copyOf(skipped), List.of(), List.of());
    }

    /**
     * Where converted effects go: {@code assets/photon/fx/<name>.fx}, which is the id {@code photon:<name>} that
     * {@code FXHelper.listAllFX} reads. This is deliberately <em>not</em> the folder the source sits in
     * ({@code assets/photon/fx_old}): converting in place used to write {@code assets/photon/fx_old/<name>.fx} into
     * {@code assets/photon/fx/}, i.e. the effect's id came out as {@code photon:fx_old/<name>} and the picker, whose id
     * has no {@code fx_old/} segment, never listed it. Every legacy root now converts to the same place, flat.
     */
    public static Path destination() {
        return PhotonPaths.getAssetsDir().toPath().resolve("photon/fx");
    }

    /** The folders a Photon 1 tree may live in, workspace first, without repeats. */
    public static List<Path> legacyRoots() {
        var roots = new ArrayList<Path>();
        roots.add(FX_CVT_PATH);
        roots.add(LDLib2.getAssetsDir().toPath().resolve("photon/fx_old"));
        var legacy = com.lowdragmc.lowdraglib2.Platform.getGamePath().resolve("ldlib/assets/photon/fx_old");
        roots.add(legacy);
        return roots.stream().distinct().toList();
    }

    /** Convert a legacy tree without overwriting any existing Photon 2 effects. */
    public static Report convertFX(Path source, Path destination) {
        var skipped = new ArrayList<String>();
        var failed = new ArrayList<String>();
        if (!Files.isDirectory(source)) return Report.empty();
        AtomicInteger count = new AtomicInteger();
        try (var paths = Files.walk(source)) {
            paths.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".fx")).forEach(path -> {
                try {
                    // by file name, not by the path under the legacy root: nested folders under fx_old used to leak
                    // into the id (photon:fx_old/matrix) and make the effect unreachable from the picker
                    var target = destination.resolve(path.getFileName().toString());
                    if (Files.exists(target)) {
                        var reason = path.getFileName() + ": already converted (" + target.getFileName() + " exists)";
                        skipped.add(reason);
                        Photon.LOGGER.warn("Skipping Photon 1 FX {}: destination already exists", path);
                        return;
                    }
                    CompoundTag photon1FX;
                    try (var input = java.nio.file.Files.newInputStream(path)) {
                        // The loader accepts both Photon 1's gzip exports and raw NBT; conversion must too.
                        photon1FX = FXHelper.readNbt(input);
                    }
                    if (!isLegacyEffect(photon1FX)) {
                        var reason = path.getFileName() + ": not a Photon 1 effect";
                        skipped.add(reason);
                        Photon.LOGGER.warn("Skipping {}: not a recognized Photon 1 effect", path);
                        return;
                    }
                    CompoundTag photon2FX = mapEffect(photon1FX);
                    // the mappers emit the current format; stamp it so the datafixer stays a no-op
                    photon2FX.putInt("version", FXProject.VERSION);
                    writeConvertedEffect(target, photon2FX);
                    count.getAndIncrement();
                }
                catch(Exception e){
                    var reason = path.getFileName() + ": " + e.getClass().getSimpleName()
                            + (e.getMessage() == null ? "" : " (" + e.getMessage() + ")");
                    failed.add(reason);
                    Photon.LOGGER.error("Failed to convert Photon 1 FX {}", path, e);
                }
            });
        } catch (IOException e) {
            failed.add(source + ": " + e.getClass().getSimpleName());
            Photon.LOGGER.error("Failed to read Photon 1 FX directory {}", source, e);
        }
        return new Report(count.get(), List.copyOf(skipped), List.copyOf(failed), List.of(source.toString()));
    }

    /** Never leave a half-written destination that a later conversion would mistake for a valid effect. */
    private static void writeConvertedEffect(Path target, CompoundTag data) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".photon-convert-", ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) {
                NbtIo.writeCompressed(data, output);
            }
            // Do not replace existing effects, even if another conversion created one in the meantime.
            Files.move(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
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
        // Photon 1 EntityEffect followed the feet; Photon 2 follows the eyes. Keep the feet so an
        // aura stays on the torso after conversion.
        fxData.putBoolean("entityFollowsFeet", true);
        stampLegacyBloom(fxData, fxObjects);
        new_fx.put("fxData", fxData);
        var definition = fx.contains("fx", Tag.TAG_COMPOUND) ? fx.getCompound("fx") : fx;
        var oldSubEffects = definition.getCompound("subFXs");
        if (!oldSubEffects.isEmpty()) {
            var subEffects = new CompoundTag();
            for (var name : oldSubEffects.getAllKeys()) {
                var subObjects = oldSubEffects.getCompound(name).getList("fxObjects", Tag.TAG_COMPOUND);
                var subData = new CompoundTag();
                subData.put("fxObjects", mapObjects(subObjects));
                subData.putBoolean("entityFollowsFeet", true);
                stampLegacyBloom(subData, subObjects);
                var subEffect = new CompoundTag();
                subEffect.put("fxData", subData);
                subEffects.put(name, subEffect);
            }
            new_fx.put("legacySubFXs", subEffects);
        }
        return new_fx;
    }

    /**
     * Photon 1 bloom was per-emitter ({@code renderer.bloomEffect}) and always wrote those particles
     * into the bloom buffer. Photon 2 bloom is per-FX and threshold-based, so conversion has to
     * turn it on and use {@link com.lowdragmc.photon.client.fx.FXData#PHOTON1_BLOOM_INTENSITY} or
     * the halo disappears.
     */
    private static void stampLegacyBloom(CompoundTag fxData, ListTag objects) {
        if (!objectsHaveBloom(objects)) return;
        fxData.putBoolean("bloomEnabled", true);
        fxData.putFloat("bloomIntensity", com.lowdragmc.photon.client.fx.FXData.PHOTON1_BLOOM_INTENSITY);
    }

    private static boolean objectsHaveBloom(ListTag objects) {
        for (Tag object : objects) {
            if (!(object instanceof CompoundTag fxObject)) continue;
            var config = MapperUtils.legacyEmitterConfig(fxObject);
            if (config.getCompound("renderer").getBoolean("bloomEffect")) return true;
        }
        return false;
    }

    private static ListTag mapObjects(ListTag fxObjects) {
        var fxObj = new ListTag();
        for (Tag object : fxObjects) {
            CompoundTag fxObject = (CompoundTag) object;
            fxObj.add(mapObject(fxObject));
        }
        return fxObj;
    }

    private static CompoundTag mapObject(CompoundTag fxObject) {
        var type = fxObject.contains("_type", Tag.TAG_STRING) ? fxObject.getString("_type") : fxObject.getString("type");
        return switch (canonicalObjectType(type, fxObject)) {
            case "beam_emitter" -> BeamEmitterMapper.mapBeamEmitter(fxObject);
            case "trail_emitter" -> TrailEmitterMapper.mapTrailEmitter(fxObject);
            case "particle_emitter" -> ParticleEmitterMapper.mapParticleEmitter(fxObject);
            default -> EmptyMapper.mapEmpty(fxObject);
        };
    }

    /**
     * Photon 1 grouping nodes and unknown object kinds must still serialize as {@code empty}
     * so children keep their parent transform. Dropping them left 3D (and every other) child
     * hanging under a missing UUID — LDLib then logs "Parent transform not found."
     */
    static String canonicalObjectType(String type, CompoundTag fxObject) {
        return switch (type) {
            case "beam", "Beam", "beam_emitter" -> "beam_emitter";
            case "trail", "Trail", "trail_emitter" -> "trail_emitter";
            case "particle", "Particle", "particle_emitter" -> "particle_emitter";
            case "empty", "Empty" -> "empty";
            default -> inferLegacyObjectType(type, fxObject);
        };
    }

    private static String inferLegacyObjectType(String type, CompoundTag fxObject) {
        var config = MapperUtils.legacyEmitterConfig(fxObject);
        if (config.contains("startLifetime") && (config.contains("emission") || config.contains("startSize")
                || config.contains("startSpeed"))) {
            if (!type.isEmpty()) Photon.LOGGER.warn("Detected unknown type {}; treating as particle emitter", type);
            return "particle_emitter";
        }
        if (config.contains("emitRate") && config.contains("width") && config.contains("color")
                && !config.contains("startLifetime") && !config.contains("emission")) {
            if (!type.isEmpty()) Photon.LOGGER.warn("Detected unknown type {}; treating as beam emitter", type);
            return "beam_emitter";
        }
        if (config.contains("widthOverTrail") || (config.contains("minVertexDistance") && config.contains("time"))) {
            if (!type.isEmpty()) Photon.LOGGER.warn("Detected unknown type {}; treating as trail emitter", type);
            return "trail_emitter";
        }
        if (!type.isEmpty()) {
            Photon.LOGGER.warn("Detected unknown type {}; keeping it as an empty grouping node so children keep their parent transform", type);
        }
        return "empty";
    }

}
