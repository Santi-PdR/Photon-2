package com.lowdragmc.photon.gui.editor;

import com.lowdragmc.lowdraglib2.editor.project.ProjectType;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.photon.client.PhotonIcons;
import com.lowdragmc.photon.client.fx.FX;

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
        File destination = new File(file.getParentFile(), file.getName() + ".fxproj");
        return FXProjectConverter.convertFile(file, destination).project();
    }

    public void appendFileMenu(Editor editor, File file, TreeBuilder.Menu menu) {
        if (!(editor instanceof FXEditor fxEditor) || fxEditor.runtime == null) return;
        var id = FXInstanceEditing.idOf(file);
        if (id == null) {
            menu.leaf(FXInstanceEditing.isUnderFxFolder(file) ? "photon.fx_instance.add_file.invalid" : "photon.fx_instance.add_file.outside", () -> {});
            return;
        }
        menu.leaf(PhotonIcons.FX_FILE, "photon.fx_instance.add_file",
                () -> fxEditor.instances.create(id, fxEditor.runtime.root.transform()));
    }
}

