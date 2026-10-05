package com.lowdragmc.photon.gui.editor;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.utils.TransformGizmo;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.gui.editor.view.FXHierarchyView;
import com.lowdragmc.photon.gui.editor.view.FXTimelineView;
import com.lowdragmc.photon.gui.editor.view.scene.SceneView;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

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

    // runtime
    @Nullable
    public FXRuntime runtime;

    public FXEditor() {
        this.icon.style(style -> style.backgroundTexture(ICON));
        this.leftWindow.getLeftTop().addView(hierarchyView);
        this.centerWindow.getLeftTop().addView(sceneView);
        this.bottomWindow.getLeftTop().addView(timelineView);
        addEventListener(UIEvents.KEY_DOWN, this::onEditorKeyDown);
    }

    @Override
    protected void initMenus() {
        super.initMenus();
        fileMenu.addProjectProvider(FXProject.TYPE);
    }

    /** Default FX-editor shortcuts, guarded so text editing and camera flight keep their keys. */
    private void onEditorKeyDown(UIEvent event) {
        if (event.target instanceof TextField) return;

        var scene = sceneView;
        var gizmo = scene.sceneEditor.getTransformGizmo();
        boolean cameraMoving = GLFW.glfwGetMouseButton(
                net.minecraft.client.Minecraft.getInstance().getWindow().getWindow(),
                GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

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
        } else if (PhotonEditorKeyMappings.matches(
                PhotonEditorKeyMappings.RELOAD_EFFECT, event.keyCode, event.scanCode)) {
            if (runtime == null) return;
            reloadEffect();
            event.stopPropagation();
        } else if (cameraMoving) {
            return;
        } else if (PhotonEditorKeyMappings.matches(
                PhotonEditorKeyMappings.GIZMO_NONE, event.keyCode, event.scanCode)) {
            gizmo.setMode(TransformGizmo.Mode.NONE);
            event.stopPropagation();
        } else if (PhotonEditorKeyMappings.matches(
                PhotonEditorKeyMappings.GIZMO_TRANSLATE, event.keyCode, event.scanCode)) {
            gizmo.setMode(TransformGizmo.Mode.TRANSLATE);
            event.stopPropagation();
        } else if (PhotonEditorKeyMappings.matches(
                PhotonEditorKeyMappings.GIZMO_ROTATE, event.keyCode, event.scanCode)) {
            gizmo.setMode(TransformGizmo.Mode.ROTATE);
            event.stopPropagation();
        } else if (PhotonEditorKeyMappings.matches(
                PhotonEditorKeyMappings.GIZMO_SCALE, event.keyCode, event.scanCode)) {
            gizmo.setMode(TransformGizmo.Mode.SCALE);
            event.stopPropagation();
        } else if (PhotonEditorKeyMappings.matches(
                PhotonEditorKeyMappings.GIZMO_SPACE, event.keyCode, event.scanCode)) {
            gizmo.setSpace(gizmo.getSpace() == TransformGizmo.Space.LOCAL
                    ? TransformGizmo.Space.GLOBAL : TransformGizmo.Space.LOCAL);
            event.stopPropagation();
        } else if (PhotonEditorKeyMappings.matches(
                PhotonEditorKeyMappings.CYCLE_DRAW_MODE, event.keyCode, event.scanCode)) {
            var modes = SceneView.DrawMode.values();
            scene.setDrawMode(modes[(scene.getDrawMode().ordinal() + 1) % modes.length]);
            event.stopPropagation();
        } else if (PhotonEditorKeyMappings.matches(
                PhotonEditorKeyMappings.TOGGLE_BLOOM, event.keyCode, event.scanCode)) {
            scene.setBloomEnabled(!scene.isBloomEnabled());
            event.stopPropagation();
        }
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
            super.loadNewProject(project, projectFile);
            this.runtime = fxProject.getFx().createInternalRuntime();
            this.runtime.root.updatePos(new Vector3f(0.5f, 2, 0.5f));
            hierarchyView.loadFXRuntime(runtime);
            timelineView.rebuild();
            sceneView.loadScene();
            reloadEffect();
        }
    }

    @Override
    protected void closeCurrentProject() {
        super.closeCurrentProject();
        hierarchyView.clearFXRuntime();
        timelineView.clear();
        sceneView.clearScene();
        runtime = null;
    }
}
