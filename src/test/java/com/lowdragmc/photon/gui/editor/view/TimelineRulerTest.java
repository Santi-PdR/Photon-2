package com.lowdragmc.photon.gui.editor.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineRulerTest {
    @Test
    void onlyDrawsLabelsWhoseFullTextFitsInsideTheRuler() {
        assertTrue(TimelineRulerLayout.labelFits(10, 20, 10, 40));
        assertTrue(TimelineRulerLayout.labelFits(30, 20, 10, 40));
        assertFalse(TimelineRulerLayout.labelFits(9, 20, 10, 40));
        assertFalse(TimelineRulerLayout.labelFits(31, 20, 10, 40));
    }
}
