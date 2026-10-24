package com.lowdragmc.photon.core.mixins;

import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.editor.resource.Resources;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.view.HistoryView;
import com.lowdragmc.lowdraglib2.editor.ui.view.ResourceView;
import com.lowdragmc.lowdraglib2.configurator.SerializableRecordAction;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.util.INBTSerializable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.File;

/** Adds exact failure-stage context around LDLib2's otherwise opaque FX project initialization. */
@Mixin(value = Editor.class, remap = false)
public abstract class EditorProjectLoadDiagnosticsMixin {
    @Redirect(
            method = "loadNewProject(Lcom/lowdragmc/lowdraglib2/editor/project/IProject;Ljava/io/File;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/lowdragmc/lowdraglib2/editor/ui/view/ResourceView;loadResources(Lcom/lowdragmc/lowdraglib2/editor/resource/Resources;)V"
            ),
            remap = false
    )
    private void photon$logResourceViewFailure(ResourceView resourceView, Resources resources) {
        boolean photonProject = isPhotonProject(resourceView.editor);
        if (photonProject) logPhaseStart(resourceView.editor, "LDLib2 resource-view initialization");
        try {
            resourceView.loadResources(resources);
            if (photonProject) logPhaseComplete(resourceView.editor, "LDLib2 resource-view initialization");
        } catch (RuntimeException | Error failure) {
            logFailure("resource-view initialization", resourceView.editor, failure);
            throw failure;
        }
    }

    @Redirect(
            method = "loadNewProject(Lcom/lowdragmc/lowdraglib2/editor/project/IProject;Ljava/io/File;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/lowdragmc/lowdraglib2/editor/ui/view/HistoryView;recordSerializableObject(Lnet/minecraft/network/chat/Component;Lnet/minecraftforge/common/util/INBTSerializable;)Lcom/lowdragmc/lowdraglib2/configurator/SerializableRecordAction;"
            ),
            remap = false
    )
    private SerializableRecordAction<?> photon$logInitialHistoryFailure(HistoryView historyView, Component name,
                                                                        INBTSerializable<?> project) {
        boolean photonProject = isPhotonProject(historyView.editor);
        if (photonProject) logPhaseStart(historyView.editor, "LDLib2 initial history snapshot");
        try {
            var action = historyView.recordSerializableObject(name, project);
            if (photonProject) logPhaseComplete(historyView.editor, "LDLib2 initial history snapshot");
            return action;
        } catch (RuntimeException | Error failure) {
            logFailure("initial history snapshot", historyView.editor, failure);
            throw failure;
        }
    }

    @Redirect(
            method = "loadNewProject(Lcom/lowdragmc/lowdraglib2/editor/project/IProject;Ljava/io/File;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/lowdragmc/lowdraglib2/editor/project/IProject;onLoad(Lcom/lowdragmc/lowdraglib2/editor/ui/Editor;)V"
            ),
            remap = false
    )
    private void photon$logProjectOnLoadFailure(IProject project, Editor editor) {
        boolean photonProject = isPhotonProject(editor);
        if (photonProject) logPhaseStart(editor, "Photon project onLoad callback");
        try {
            project.onLoad(editor);
            if (photonProject) logPhaseComplete(editor, "Photon project onLoad callback");
        } catch (RuntimeException | Error failure) {
            logFailure("project onLoad callback", editor, failure);
            throw failure;
        }
    }

    private static boolean isPhotonProject(Editor editor) {
        return editor.getCurrentProject() instanceof FXProject;
    }

    private static void logPhaseStart(Editor editor, String phase) {
        var file = editor.getCurrentProjectFile();
        Photon.LOGGER.info("Starting Photon FX project {} subphase '{}'",
                file == null ? "<unsaved>" : file.getName(), phase);
    }

    private static void logPhaseComplete(Editor editor, String phase) {
        var file = editor.getCurrentProjectFile();
        Photon.LOGGER.info("Completed Photon FX project {} subphase '{}'",
                file == null ? "<unsaved>" : file.getName(), phase);
    }

    private static void logFailure(String phase, Editor editor, Throwable failure) {
        if (!(editor.getCurrentProject() instanceof FXProject)) return;
        File file = editor.getCurrentProjectFile();
        Photon.LOGGER.error("Photon FX project {} failed during LDLib2 {}",
                file == null ? "<unsaved>" : file.getAbsolutePath(), phase, failure);
    }
}
