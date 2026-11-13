package com.lowdragmc.photon.gui.editor;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.project.ProjectType;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.photon.client.PhotonIcons;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;

import java.io.File;

/**
 * {@code .fx} files as the editor sees them: listed in the asset browser like projects, opened by converting
 * them into one, and placeable as FX instances from their menu (or by dragging them into the hierarchy). Not a
 * document type of its own: nothing is ever saved as one, so it is left out of File → New.
 */
public final class FXFileType extends ProjectType {
    public static final FXFileType INSTANCE = new FXFileType();

    private FXFileType() {
        super(PhotonIcons.FX_FILE, "photon.fx_file", FX.SUFFIX, FXProject::new);
    }

    @Override
    public com.lowdragmc.lowdraglib2.editor.project.IProject loadProjectFromFile(File file) throws Exception {
        // Convert in memory only. Writing a sibling .fxproj here is what made "deny save" still
        // leave a project on disk, and binding the .fx as the document let Save overwrite it.
        var tag = FXProjectConverter.readFileTag(file);
        if (FXHelper.isProjectDocument(tag)) {
            var project = new FXProject();
            project.deserializeNBT(Platform.getFrozenRegistry(), tag);
            return project;
        }
        var result = FXProjectConverter.convertInMemory(tag, FXInstanceEditing.idOf(file));
        result.warnings().forEach(warning -> com.lowdragmc.photon.Photon.LOGGER.warn(
                "Photon FX {}: {}", file.getAbsolutePath(), warning));
        return result.project();
    }

    public void appendFileMenu(Editor editor, File file, TreeBuilder.Menu menu) {
        if (!(editor instanceof FXEditor fxEditor) || fxEditor.runtime == null) return;
        var id = FXInstanceEditing.idOf(file);
        if (id == null) {
            PhotonMenus.disabledLeaf(menu, FXInstanceEditing.isUnderFxFolder(file) ? "photon.fx_instance.add_file.invalid" : "photon.fx_instance.add_file.outside");
            return;
        }
        menu.leaf(PhotonIcons.FX_FILE, "photon.fx_instance.add_file",
                () -> fxEditor.instances.create(id, fxEditor.runtime.root.transform()));
    }
}

