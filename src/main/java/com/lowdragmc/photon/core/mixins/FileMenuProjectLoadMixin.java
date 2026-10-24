package com.lowdragmc.photon.core.mixins;

import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.editor.project.ProjectType;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.photon.Photon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.File;
import java.util.concurrent.TimeUnit;

/** Keeps actionable load errors for Photon projects opened through LDLib2's File → Open picker. */
@Mixin(value = com.lowdragmc.lowdraglib2.editor.ui.menu.FileMenu.class, remap = false)
public abstract class FileMenuProjectLoadMixin {
    @Redirect(
            method = "lambda$onOpenProject$8",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/lowdragmc/lowdraglib2/editor/project/ProjectType;loadProjectFromFile(Ljava/io/File;)Lcom/lowdragmc/lowdraglib2/editor/project/IProject;"
            ),
            remap = false
    )
    private IProject photon$logProjectLoadFailure(ProjectType projectType, File file) throws Exception {
        boolean photonProject = isPhotonProject(file);
        long started = System.nanoTime();
        if (photonProject) {
            Photon.LOGGER.info("Opening Photon FX project {} from disk", file.getAbsolutePath());
        }
        try {
            IProject project = projectType.loadProjectFromFile(file);
            if (photonProject) {
                Photon.LOGGER.info("Read Photon FX project {} in {} ms", file.getName(),
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            }
            return project;
        } catch (Exception | Error exception) {
            logFailure("load", file, exception);
            throw exception;
        }
    }

    @Redirect(
            method = "lambda$onOpenProject$8",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/lowdragmc/lowdraglib2/editor/ui/Editor;loadProject(Lcom/lowdragmc/lowdraglib2/editor/project/IProject;Ljava/io/File;)V"
            ),
            remap = false
    )
    private void photon$logProjectInitializationFailure(Editor editor, IProject project, File file) {
        boolean photonProject = isPhotonProject(file);
        long started = System.nanoTime();
        if (photonProject) {
            Photon.LOGGER.info("Initializing Photon FX project {} in the editor", file.getAbsolutePath());
        }
        try {
            editor.loadProject(project, file);
            if (photonProject) {
                Photon.LOGGER.info("Opened Photon FX project {} in {} ms", file.getName(),
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            }
        } catch (RuntimeException | Error exception) {
            logFailure("initialize", file, exception);
            throw exception;
        }
    }

    private static boolean isPhotonProject(File file) {
        return file != null && file.getName().endsWith(".fxproj");
    }

    private static void logFailure(String stage, File file, Throwable exception) {
        if (file != null && file.getName().endsWith(".fxproj")) {
            Photon.LOGGER.error("Failed to {} Photon FX project from {}", stage, file.getAbsolutePath(), exception);
        }
    }
}
