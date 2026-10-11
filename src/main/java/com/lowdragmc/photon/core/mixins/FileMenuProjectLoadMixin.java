package com.lowdragmc.photon.core.mixins;

import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.editor.project.ProjectType;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.photon.Photon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.File;

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
        try {
            return projectType.loadProjectFromFile(file);
        } catch (Exception exception) {
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
        try {
            editor.loadProject(project, file);
        } catch (RuntimeException exception) {
            logFailure("initialize", file, exception);
            throw exception;
        }
    }

    private static void logFailure(String stage, File file, Exception exception) {
        if (file != null && file.getName().endsWith(".fxproj")) {
            Photon.LOGGER.error("Failed to {} Photon FX project from {}", stage, file.getAbsolutePath(), exception);
        }
    }
}
