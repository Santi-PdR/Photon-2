package com.lowdragmc.photon.gui.editor.view;

/** Pure layout checks for the timeline ruler, kept independent of GUI and Minecraft state. */
final class TimelineRulerLayout {
    private TimelineRulerLayout() {}

    static boolean labelFits(float labelX, float labelWidth, float rulerX, float rulerWidth) {
        return labelX >= rulerX && labelX + labelWidth <= rulerX + rulerWidth;
    }
}
