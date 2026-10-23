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
        try {
            resourceView.loadResources(resources);
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
        try {
            return historyView.recordSerializableObject(name, project);
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
        try {
            project.onLoad(editor);
        } catch (RuntimeException | Error failure) {
            logFailure("project onLoad callback", editor, failure);
            throw failure;
        }
    }

    private static void logFailure(String phase, Editor editor, Throwable failure) {
        if (!(editor.getCurrentProject() instanceof FXProject)) return;
        File file = editor.getCurrentProjectFile();
        Photon.LOGGER.error("Photon FX project {} failed during LDLib2 {}",
                file == null ? "<unsaved>" : file.getAbsolutePath(), phase, failure);
    }
}
