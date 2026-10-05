package com.lowdragmc.photon.gui.editor;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/** Configurable defaults for the Photon FX editor, exposed in Minecraft's Controls screen. */
@OnlyIn(Dist.CLIENT)
public final class PhotonEditorKeyMappings {
    private static final String SCENE_CATEGORY = PhotonEditorActions.CATEGORY_SCENE;
    private static final String PLAYBACK_CATEGORY = PhotonEditorActions.CATEGORY_PLAYBACK;

    public static final KeyMapping GIZMO_NONE = sceneKey("keymap.photon.scene.gizmo_none", GLFW.GLFW_KEY_Q);
    public static final KeyMapping GIZMO_TRANSLATE = sceneKey("keymap.photon.scene.gizmo_translate", GLFW.GLFW_KEY_W);
    public static final KeyMapping GIZMO_ROTATE = sceneKey("keymap.photon.scene.gizmo_rotate", GLFW.GLFW_KEY_E);
    public static final KeyMapping GIZMO_SCALE = sceneKey("keymap.photon.scene.gizmo_scale", GLFW.GLFW_KEY_R);
    public static final KeyMapping GIZMO_SPACE = sceneKey("keymap.photon.scene.gizmo_space", GLFW.GLFW_KEY_X);
    public static final KeyMapping CYCLE_DRAW_MODE = sceneKey("keymap.photon.scene.cycle_draw_mode", GLFW.GLFW_KEY_Z);
    public static final KeyMapping TOGGLE_BLOOM = sceneKey("keymap.photon.scene.toggle_bloom", GLFW.GLFW_KEY_B);

    public static final KeyMapping PLAY_PAUSE = playbackKey("keymap.photon.playback.play_pause", GLFW.GLFW_KEY_SPACE);
    public static final KeyMapping STOP = playbackKey("keymap.photon.playback.stop", KeyModifier.SHIFT, GLFW.GLFW_KEY_SPACE);
    public static final KeyMapping STEP_BACK = playbackKey("keymap.photon.playback.step_back", GLFW.GLFW_KEY_COMMA);
    public static final KeyMapping STEP_FORWARD = playbackKey("keymap.photon.playback.step_forward", GLFW.GLFW_KEY_PERIOD);
    public static final KeyMapping RELOAD_EFFECT = playbackKey(
            "keymap.photon.playback.reload_effect", KeyModifier.CONTROL, GLFW.GLFW_KEY_R);

    private PhotonEditorKeyMappings() {}

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
        return new KeyMapping(name, KeyConflictContext.GUI, modifier,
                com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, keyCode, category);
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
        return mapping.isActiveAndMatches(com.mojang.blaze3d.platform.InputConstants.getKey(keyCode, scanCode));
    }
}
