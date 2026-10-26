package com.lowdragmc.photon.gui.editor;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.utils.TransformGizmo;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.gui.editor.view.FXHierarchyView;
import com.lowdragmc.photon.gui.editor.view.FXTimelineView;
import com.lowdragmc.photon.gui.editor.view.scene.SceneView;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.File;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class FXEditor extends Editor {
    public final static ResourceLocation WINDOW_ID = LDLib2.id("fx_editor");

    public final static SpriteTexture ICON = SpriteTexture.of("photon:textures/icon.png");
    public final FXHierarchyView hierarchyView = new FXHierarchyView(this);
    public final SceneView sceneView = new SceneView(this);
    public final FXTimelineView timelineView = new FXTimelineView(this);
    public final FXInstanceEditing instances = new FXInstanceEditing(this);

    // runtime
    @Nullable
    public FXRuntime runtime;

    public FXEditor() {
        this.icon.style(style -> style.backgroundTexture(ICON));
        this.leftWindow.getLeftTop().addView(hierarchyView);
        this.centerWindow.getLeftTop().addView(sceneView);
        this.bottomWindow.getLeftTop().addView(timelineView);
        // focusable, or clicks in the scene focus the panel around it and its chords never reach this listener
        sceneView.setFocusable(true);
        sceneView.addEventListener(UIEvents.EXECUTE_COMMAND, hierarchyView::handleCommand);
        addEventListener("keyDown", this::onEditorKeyDown);
        // follow sources that were exported again or whose pack was rewritten
        addEventListener(UIEvents.TICK, e -> instances.tick());
    }

    @Override
    protected void initMenus() {
        super.initMenus();
        fileMenu.addProjectProvider(FXProject.TYPE);
        // .fx files show in the asset browser and open (as a converted project); they are not created from New
        fileMenu.addProjectProvider(FXFileType.INSTANCE);
        fileMenu.registerNewMenuCreator((tab, menu) -> menu.remove(FXFileType.INSTANCE.name));
        // the editor's, not the project's: converting and managing packs need no project open
        fileMenu.registerMenuCreator((tab, menu) -> FXFileActions.appendTo(this, menu));
    }

    @Override
    protected Editor createNewEditorInstance() {
        return new FXEditor();
    }

    public void reloadEffect() {
        if (runtime != null) {
            sceneView.reset();
            sceneView.play();
        }
    }

    @Override
    protected void loadNewProject(IProject project, @Nullable File projectFile) {
        if (project instanceof FXProject fxProject) {
            try {
                super.loadNewProject(project, projectFile);
                fxProject.setEditor(this);
                showProject(fxProject);
            } catch (RuntimeException failure) {
                com.lowdragmc.photon.Photon.LOGGER.error("Failed to initialize Photon FX project {}", projectFile, failure);
                throw failure;
            }
        }
    }

    private void showProject(FXProject fxProject) {
        this.runtime = fxProject.getFx().createInternalRuntime();
        this.runtime.root.updatePos(new Vector3f(0.5f, 2, 0.5f));
        hierarchyView.loadFXRuntime(runtime);
        timelineView.rebuild();
        sceneView.loadScene();
        instances.changed();
        reloadEffect();
    }

    /**
     * The project read itself again from a snapshot (an undo back to when it opened) and now holds other
     * objects: show those. What the history did after that point acted on the old ones, so it cannot be redone.
     */
    void showReloadedProject() {
        if (!(getCurrentProject() instanceof FXProject fxProject)) return;
        inspectorView.clear();
        sceneView.sceneEditor.setTransformGizmoTarget(null);
        showProject(fxProject);
    }

    @Override
    protected void closeCurrentProject() {
        if (getCurrentProject() instanceof FXProject fxProject) {
            fxProject.setEditor(null);
        }
        super.closeCurrentProject();
        instances.changed();
        hierarchyView.clearFXRuntime();
        timelineView.clear();
        sceneView.clearScene();
        runtime = null;
    }

    private void onEditorKeyDown(UIEvent event) {
        if (PhotonEditorKeyMappings.isTyping(event)) return;
        var gizmo = sceneView.sceneEditor.getTransformGizmo();
        if (PhotonEditorKeyMappings.matches(PhotonEditorKeyMappings.PLAY_PAUSE, event.keyCode, event.scanCode)
                || PhotonEditorKeyMappings.matches(PhotonEditorKeyMappings.STOP, event.keyCode, event.scanCode)) {
            if (runtime == null) return;
            if (PhotonEditorKeyMappings.matches(PhotonEditorKeyMappings.STOP, event.keyCode, event.scanCode)) {
                timelineView.stop();
            } else {
                timelineView.togglePlay();
            }
            event.stopPropagation();
        } else if (PhotonEditorKeyMappings.matches(PhotonEditorKeyMappings.STEP_BACK, event.keyCode, event.scanCode)
                || PhotonEditorKeyMappings.matches(PhotonEditorKeyMappings.STEP_FORWARD, event.keyCode, event.scanCode)) {
            if (runtime == null) return;
            timelineView.stepFrames(PhotonEditorKeyMappings.matches(
                    PhotonEditorKeyMappings.STEP_BACK, event.keyCode, event.scanCode) ? -1 : 1);
            event.stopPropagation();
        } else if (PhotonEditorKeyMappings.matches(PhotonEditorKeyMappings.RELOAD_EFFECT, event.keyCode, event.scanCode)) {
            if (runtime == null) return;
            reloadEffect();
            event.stopPropagation();
        } else {
            if (sceneView.sceneEditor.isCameraMoving()) return;
            if (PhotonEditorKeyMappings.matches(PhotonEditorKeyMappings.GIZMO_NONE, event.keyCode, event.scanCode)) {
                gizmo.setMode(TransformGizmo.Mode.NONE);
                event.stopPropagation();
            } else if (PhotonEditorKeyMappings.matches(PhotonEditorKeyMappings.GIZMO_TRANSLATE, event.keyCode, event.scanCode)) {
                gizmo.setMode(TransformGizmo.Mode.TRANSLATE);
                event.stopPropagation();
            } else if (PhotonEditorKeyMappings.matches(PhotonEditorKeyMappings.GIZMO_ROTATE, event.keyCode, event.scanCode)) {
                gizmo.setMode(TransformGizmo.Mode.ROTATE);
                event.stopPropagation();
            } else if (PhotonEditorKeyMappings.matches(PhotonEditorKeyMappings.GIZMO_SCALE, event.keyCode, event.scanCode)) {
                gizmo.setMode(TransformGizmo.Mode.SCALE);
                event.stopPropagation();
            } else if (PhotonEditorKeyMappings.matches(PhotonEditorKeyMappings.GIZMO_SPACE, event.keyCode, event.scanCode)) {
                gizmo.setSpace(gizmo.getSpace() == TransformGizmo.Space.LOCAL
                        ? TransformGizmo.Space.GLOBAL : TransformGizmo.Space.LOCAL);
                event.stopPropagation();
            } else if (PhotonEditorKeyMappings.matches(PhotonEditorKeyMappings.CYCLE_DRAW_MODE, event.keyCode, event.scanCode)) {
            var modes = SceneView.DrawMode.values();
            sceneView.setDrawMode(modes[(sceneView.getDrawMode().ordinal() + 1) % modes.length]);
                event.stopPropagation();
            } else if (PhotonEditorKeyMappings.matches(PhotonEditorKeyMappings.TOGGLE_BLOOM, event.keyCode, event.scanCode)) {
                sceneView.setBloomEnabled(!sceneView.isBloomEnabled());
                event.stopPropagation();
            }
        }
    }
}

