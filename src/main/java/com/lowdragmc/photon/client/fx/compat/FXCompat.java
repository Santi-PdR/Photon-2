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
                        photon1FX = NbtIo.readCompressed(input);
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
                    Files.createDirectories(target.getParent());
                    try (var output = Files.newOutputStream(target, java.nio.file.StandardOpenOption.CREATE_NEW)) {
                        NbtIo.writeCompressed(photon2FX, output);
                    }
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
