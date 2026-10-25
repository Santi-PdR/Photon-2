package com.lowdragmc.photon.gui.editor;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextArea;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;

/** Rebindable shortcuts used while the Photon scene and timeline editors have focus. */
public final class PhotonEditorKeyMappings {
    private static final String SCENE_CATEGORY = "keymap.category.photon.scene";
    private static final String PLAYBACK_CATEGORY = "keymap.category.photon.playback";

    public static final KeyMapping GIZMO_NONE = sceneKey("keymap.photon.scene.gizmo_none", 81);
    public static final KeyMapping GIZMO_TRANSLATE = sceneKey("keymap.photon.scene.gizmo_translate", 87);
    public static final KeyMapping GIZMO_ROTATE = sceneKey("keymap.photon.scene.gizmo_rotate", 69);
    public static final KeyMapping GIZMO_SCALE = sceneKey("keymap.photon.scene.gizmo_scale", 82);
    public static final KeyMapping GIZMO_SPACE = sceneKey("keymap.photon.scene.gizmo_space", 88);
    public static final KeyMapping CYCLE_DRAW_MODE = sceneKey("keymap.photon.scene.cycle_draw_mode", 90);
    public static final KeyMapping TOGGLE_BLOOM = sceneKey("keymap.photon.scene.toggle_bloom", 66);
    public static final KeyMapping PLAY_PAUSE = playbackKey("keymap.photon.playback.play_pause", 32);
    public static final KeyMapping STOP = playbackKey("keymap.photon.playback.stop", KeyModifier.SHIFT, 32);
    public static final KeyMapping STEP_BACK = playbackKey("keymap.photon.playback.step_back", 44);
    public static final KeyMapping STEP_FORWARD = playbackKey("keymap.photon.playback.step_forward", 46);
    public static final KeyMapping RELOAD_EFFECT = playbackKey("keymap.photon.playback.reload_effect", KeyModifier.CONTROL, 82);

    private PhotonEditorKeyMappings() {
    }

    private static KeyMapping sceneKey(String name, int keyCode) {
        return key(name, SCENE_CATEGORY, KeyModifier.NONE, keyCode);
    }

    private static KeyMapping playbackKey(String name, int keyCode) {
        return playbackKey(name, KeyModifier.NONE, keyCode);
    }

    private static KeyMapping playbackKey(String name, KeyModifier modifier, int keyCode) {
        return key(name, PLAYBACK_CATEGORY, modifier, keyCode);
    }

    private static KeyMapping key(String name, String category, KeyModifier modifier, int keyCode) {
        IKeyConflictContext context = KeyConflictContext.GUI;
        return new KeyMapping(name, context, modifier, InputConstants.Type.KEYSYM, keyCode, category);
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(GIZMO_NONE);
        event.register(GIZMO_TRANSLATE);
        event.register(GIZMO_ROTATE);
        event.register(GIZMO_SCALE);
        event.register(GIZMO_SPACE);
        event.register(CYCLE_DRAW_MODE);
        event.register(TOGGLE_BLOOM);
        event.register(PLAY_PAUSE);
        event.register(STOP);
        event.register(STEP_BACK);
        event.register(STEP_FORWARD);
        event.register(RELOAD_EFFECT);
    }

    public static boolean matches(KeyMapping mapping, int keyCode, int scanCode) {
        return mapping.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode));
    }

    public static boolean isTyping(UIEvent event) {
        for (UIElement target = event.target; target != null; target = target.getParent()) {
            if (target instanceof TextField || target instanceof TextArea) return true;
        }
        return false;
    }
}

