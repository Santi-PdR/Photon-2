package com.lowdragmc.photon.gui.editor;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.fxpack.FXPacks;
import com.lowdragmc.photon.client.util.PhotonPaths;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * File → Export → "Export All Effects to FXPack...": every {@code .fxproj} of the projects folder and every
 * playable {@code .fx} of the workspace go into one pack in one go, instead of opening and exporting them one at a
 * time (what an author with a dozen effects was doing by hand). Pins down the naming — a project is exported as its
 * path under the projects folder, an {@code editfx/<namespace>/<path>} working copy as the effect it edits, an
 * {@code .fx} as its path under {@code assets/<namespace>/fx/} — which {@code .fx} files are left out (one a working
 * copy stands for, one named like a project, the same id in a second root) and which are not (a name no id allows,
 * which the pack sanitizes), that a name taken twice is skipped rather than silently overwritten, and that the pack
 * ends up with every effect, readable by the pack tools. The first batch listed only the projects, and an author
 * whose effects were converted Photon 1 {@code .fx} files got 20 of 60 ("only 20 exported, and only the .fxproj").
 * Runs inside the client smoke test, in the game directory the test owns.
 */
@OnlyIn(Dist.CLIENT)
public final class ExportAllRegression {
    private ExportAllRegression() {
    }

    public static void verify(FXProject source) throws Exception {
        var projectsDir = PhotonPaths.getProjectsDir().getAbsoluteFile().toPath();
        var batchDir = projectsDir.resolve("smoke-export-all");
        var nestedDir = batchDir.resolve("nested");
        var editfxDir = projectsDir.resolve("editfx").resolve("smoke_all").resolve("deep");
        var packDir = Files.createTempDirectory("photon-export-all-");
        var pack = packDir.resolve("smoke_all" + FXPacks.SUFFIX);
        // two asset roots the way photon/assets and ldlib2/assets are, each with assets/<namespace>/fx/
        var assetsDir = Files.createTempDirectory("photon-export-all-assets-");
        var workspaceFx = assetsDir.resolve("workspace").resolve("smoke_all").resolve("fx");
        var olderFx = assetsDir.resolve("older").resolve("smoke_all").resolve("fx");
        var created = new ArrayList<Path>();
        try {
            Files.createDirectories(nestedDir);
            Files.createDirectories(editfxDir);
            var first = batchDir.resolve("first" + FXProject.TYPE.getSuffix());
            var second = nestedDir.resolve("second" + FXProject.TYPE.getSuffix());
            var edited = editfxDir.resolve("first" + FXProject.TYPE.getSuffix());
            FXProject.TYPE.saveProjectToFile(source, first.toFile());
            FXProject.TYPE.saveProjectToFile(source, second.toFile());
            Files.copy(first, edited);
            created.add(first);
            created.add(second);
            created.add(edited);
            // playable definitions: one of its own; one the editfx copy already stands for (smoke_all:deep/first);
            // one named like a project; one whose name no id allows; and the own one again in the older root
            var alpha = writePlayable(source, workspaceFx.resolve("alpha" + FX.SUFFIX));
            var editedFx = writePlayable(source, workspaceFx.resolve("deep").resolve("first" + FX.SUFFIX));
            var projectNamed = writePlayable(source, workspaceFx.resolve("smoke-export-all").resolve("first" + FX.SUFFIX));
            var loud = writePlayable(source, workspaceFx.resolve("Loud Aura" + FX.SUFFIX));
            var olderAlpha = writePlayable(source, olderFx.resolve("alpha" + FX.SUFFIX));
            created.add(alpha);
            created.add(editedFx);
            created.add(projectNamed);
            created.add(loud);
            created.add(olderAlpha);

            var assetRoots = List.of(assetsDir.resolve("workspace").toFile(), assetsDir.resolve("older").toFile());
            var candidates = FXFileActions.findCandidates(projectsDir.toFile(), assetRoots).stream()
                    .filter(candidate -> candidate.file().toPath().startsWith(batchDir)
                            || candidate.file().toPath().startsWith(editfxDir)
                            || candidate.file().toPath().startsWith(assetsDir))
                    .toList();
            var names = new HashSet<String>();
            candidates.forEach(candidate -> names.add(candidate.fxName()));
            var expectedNames = Set.of("smoke-export-all/first", "smoke-export-all/nested/second", "deep/first", "alpha", "Loud Aura");
            check(names.equals(expectedNames) && candidates.size() == expectedNames.size(),
                    "the workspace was not listed as the names the pack gets: " + names);
            var files = new HashSet<Path>();
            candidates.forEach(candidate -> files.add(candidate.file().toPath()));
            check(files.contains(alpha) && !files.contains(olderAlpha) && !files.contains(editedFx) && !files.contains(projectNamed),
                    "the .fx files a project or an earlier root stands for were not left out: " + files);
            var alphaCandidate = candidates.stream().filter(candidate -> candidate.file().toPath().equals(alpha)).findFirst().orElseThrow();
            var loudCandidate = candidates.stream().filter(candidate -> candidate.file().toPath().equals(loud)).findFirst().orElseThrow();
            check(alphaCandidate.definition() && new ResourceLocation("smoke_all", "alpha").equals(alphaCandidate.standsFor())
                            && loudCandidate.standsFor() == null,
                    "an .fx candidate does not know the id it plays as: " + alphaCandidate + " / " + loudCandidate);

            // the same name twice: the second one is skipped, the first is what the pack holds
            var chosen = new ArrayList<>(candidates);
            chosen.add(new FXFileActions.ProjectCandidate(second.toFile(), "smoke-export-all/first", null));
            var outcome = FXFileActions.exportProjects(null, null, pack.toFile(), "smoke_all", chosen);
            var expectedIds = new HashSet<ResourceLocation>();
            for (var name : Set.of("smoke-export-all/first", "smoke-export-all/nested/second", "deep/first", "alpha", "loud_aura")) {
                expectedIds.add(new ResourceLocation("smoke_all", name));
            }
            check(new HashSet<>(outcome.exported()).equals(expectedIds) && outcome.exported().size() == 5,
                    "the batch did not export every project and definition once: " + outcome.exported());
            check(outcome.skipped().equals(List.of(second.getFileName().toString())),
                    "a name taken twice was not skipped: " + outcome.skipped());
            check(Files.isRegularFile(pack), "the batch export wrote no pack");
            var packed = new HashSet<>(FXPacks.listFx(pack.toFile()));
            check(packed.equals(expectedIds), "the pack does not hold the exported effects: " + packed);
            check(Files.isRegularFile(alpha) && !Files.exists(workspaceFx.resolve("alpha" + FXProject.TYPE.getSuffix())),
                    "exporting an .fx wrote a project next to it");

            // exporting again replaces in place: still one entry per effect
            var again = FXFileActions.exportProjects(null, null, pack.toFile(), "smoke_all", candidates);
            check(again.exported().size() == 5 && again.skipped().isEmpty()
                            && new HashSet<>(FXPacks.listFx(pack.toFile())).equals(expectedIds),
                    "a second batch into the same pack did not replace the effects in place");
        } finally {
            for (var file : created) {
                Files.deleteIfExists(file);
            }
            Files.deleteIfExists(pack);
            // the folders go only while empty: editfx/ may hold other working copies
            for (var folder : List.of(nestedDir, batchDir, editfxDir, editfxDir.getParent(), editfxDir.getParent().getParent(), packDir,
                    workspaceFx.resolve("deep"), workspaceFx.resolve("smoke-export-all"), workspaceFx, workspaceFx.getParent(),
                    workspaceFx.getParent().getParent(), olderFx, olderFx.getParent(), olderFx.getParent().getParent(), assetsDir)) {
                deleteIfEmpty(folder);
            }
        }
        Photon.LOGGER.info("[export-all-fxpack-smoke] every saved project and workspace .fx is exported into one pack under its path, editfx copies under the effect they edit, a name taken twice skipped");
    }

    /** The playable the one-project Export FX writes: the definition, gzip NBT, stamped with the format version. */
    private static Path writePlayable(FXProject source, Path file) throws java.io.IOException {
        Files.createDirectories(file.getParent());
        var data = source.getFx().serializeNBT(Platform.getFrozenRegistry());
        data.putInt("version", FXProject.VERSION);
        NbtIo.writeCompressed(data, file.toFile());
        return file;
    }

    private static void deleteIfEmpty(Path folder) throws java.io.IOException {
        try {
            Files.deleteIfExists(folder);
        } catch (java.nio.file.DirectoryNotEmptyException ignored) {
            // somebody else's files: not ours to remove
        }
    }

    private static void check(boolean condition, String failure) {
        if (!condition) {
            throw new IllegalStateException(failure);
        }
    }
}
