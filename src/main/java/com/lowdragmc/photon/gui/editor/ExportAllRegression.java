package com.lowdragmc.photon.gui.editor;

import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.fx.fxpack.FXPacks;
import com.lowdragmc.photon.client.util.PhotonPaths;
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
 * File → Export → "Export All Projects to FXPack...": every {@code .fxproj} of the projects folder goes into one
 * pack in one go, instead of opening and exporting them one at a time (what an author with a dozen effects was
 * doing by hand). Pins down the naming — a project is exported as its path under the projects folder, an
 * {@code editfx/<namespace>/<path>} working copy as the effect it edits — that a name taken twice is skipped
 * rather than silently overwritten, and that the pack ends up with every effect, readable by the pack tools.
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

            var candidates = FXFileActions.findProjects(projectsDir.toFile()).stream()
                    .filter(candidate -> candidate.file().toPath().startsWith(batchDir)
                            || candidate.file().toPath().startsWith(editfxDir))
                    .toList();
            var names = new HashSet<String>();
            candidates.forEach(candidate -> names.add(candidate.fxName()));
            var expectedNames = Set.of("smoke-export-all/first", "smoke-export-all/nested/second", "deep/first");
            check(names.equals(expectedNames), "the projects folder was not listed as the names the pack gets: " + names);

            // the same name twice: the second one is skipped, the first is what the pack holds
            var chosen = new ArrayList<>(candidates);
            chosen.add(new FXFileActions.ProjectCandidate(second.toFile(), "smoke-export-all/first"));
            var outcome = FXFileActions.exportProjects(null, null, pack.toFile(), "smoke_all", chosen);
            var expectedIds = new HashSet<ResourceLocation>();
            for (var name : expectedNames) {
                expectedIds.add(new ResourceLocation("smoke_all", name));
            }
            check(new HashSet<>(outcome.exported()).equals(expectedIds) && outcome.exported().size() == 3,
                    "the batch did not export every project once: " + outcome.exported());
            check(outcome.skipped().equals(List.of(second.getFileName().toString())),
                    "a name taken twice was not skipped: " + outcome.skipped());
            check(Files.isRegularFile(pack), "the batch export wrote no pack");
            var packed = new HashSet<>(FXPacks.listFx(pack.toFile()));
            check(packed.equals(expectedIds), "the pack does not hold the exported effects: " + packed);

            // exporting again replaces in place: still one entry per effect
            var again = FXFileActions.exportProjects(null, null, pack.toFile(), "smoke_all", candidates);
            check(again.exported().size() == 3 && again.skipped().isEmpty()
                            && new HashSet<>(FXPacks.listFx(pack.toFile())).equals(expectedIds),
                    "a second batch into the same pack did not replace the effects in place");
        } finally {
            for (var file : created) {
                Files.deleteIfExists(file);
            }
            Files.deleteIfExists(pack);
            // the folders go only while empty: editfx/ may hold other working copies
            for (var folder : List.of(nestedDir, batchDir, editfxDir, editfxDir.getParent(), editfxDir.getParent().getParent(), packDir)) {
                deleteIfEmpty(folder);
            }
        }
        Photon.LOGGER.info("[export-all-fxpack-smoke] every saved project is exported into one pack under its path, editfx copies under the effect they edit, a name taken twice skipped");
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
