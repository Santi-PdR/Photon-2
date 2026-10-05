package com.lowdragmc.photon.gui.editor;

import com.lowdragmc.lowdraglib2.gui.ui.elements.TextArea;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;

/** Text inputs whose keystrokes must not trigger editor-wide shortcuts. */
final class EditorTextInputPolicy {
    private EditorTextInputPolicy() {
    }

    static boolean isTextInput(Class<?> type) {
        return TextField.class.isAssignableFrom(type) || TextArea.class.isAssignableFrom(type);
    }
}
