package com.lowdragmc.photon.gui.editor;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextArea;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorTextInputPolicyTest {
    @Test
    void recognizesSingleLineMultilineAndDerivedInputs() {
        assertTrue(EditorTextInputPolicy.isTextInput(TextField.class));
        assertTrue(EditorTextInputPolicy.isTextInput(TextArea.class));
        assertTrue(EditorTextInputPolicy.isTextInput(CustomTextArea.class));
        assertFalse(EditorTextInputPolicy.isTextInput(Button.class));
        assertFalse(EditorTextInputPolicy.isTextInput(UIElement.class));
    }

    private static final class CustomTextArea extends TextArea {
    }
}
