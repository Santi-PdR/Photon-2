package com.lowdragmc.photon.gui.editor.view;

import com.lowdragmc.photon.client.fx.timeline.AnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.AnimationTrack;
import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.fx.timeline.Marker;
import com.lowdragmc.photon.client.fx.timeline.Signal;
import com.lowdragmc.photon.client.fx.timeline.SignalTrack;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.fx.timeline.TimelineSection;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigPropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigValueType;
import com.lowdragmc.photon.gui.editor.view.timeline.PhotonTrackTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * Regression for the timeline's ripple "Insert time": the editor undoes an insert by running the same
 * ripple with {@code -delta}, which is only exact for a positive insert. The dialog therefore refuses
 * anything that is not a finite positive number of seconds (it used to accept {@code -1}, {@code NaN},
 * {@code Infinity} and {@code 1e999}, each of which left clip starts, keyframes, markers and signals
 * where no undo could reach them), accepts the locale's decimal comma, and the model ignores a
 * non-finite point or delta outright.
 */
public final class TimelineInsertTimeRegression {
    private TimelineInsertTimeRegression() {}

    public static void verify() {
        verifyDialogParsing();
        verifyPositiveInsertIsExactlyUndone();
        verifyNonFiniteInsertIsIgnored();
    }

    private static void verifyDialogParsing() {
        expectTicks("1.0", 20);
        expectTicks("1,5", 30);   // the locale's comma
        expectTicks(" 2 ", 40);
        expectTicks("0.05", 1);
        for (var refused : new String[]{"-1", "0", "-0,5", "NaN", "Infinity", "-Infinity", "1e999", "abc", "", "3601"}) {
            if (!Double.isNaN(FXTimelineView.insertTicksFromDialog(refused))) {
                throw new IllegalStateException("Insert-time dialog accepted '" + refused + "'");
            }
        }
        if (FXTimelineView.parseDecimal("40,5") != 40.5 || !Double.isNaN(FXTimelineView.parseDecimal("x"))) {
            throw new IllegalStateException("Section-move dialog decimal parsing broke");
        }
    }

    private static void expectTicks(String text, double ticks) {
        var parsed = FXTimelineView.insertTicksFromDialog(text);
        if (Math.abs(parsed - ticks) > 1e-9) {
            throw new IllegalStateException("Insert-time dialog parsed '" + text + "' as " + parsed + " ticks, expected " + ticks);
        }
    }

    private static void verifyPositiveInsertIsExactlyUndone() {
        var timeline = sample();
        var before = snapshot(timeline);
        timeline.insertTime(10, 20);
        var after = snapshot(timeline);
        // everything at or after tick 10 moved 20 later; the straddling clip grew; what ended before stayed
        var expected = List.of(
                "clip 0.0+10.0", "clip 35.0+5.0", "clip 5.0+30.0",
                "signal 8.0", "signal 30.0", "signal 50.0",
                "marker 9.0", "marker 30.0", "marker 45.0",
                "section a@0.0", "section b@32.0",
                "key 0.0", "key 5.0", "key 40.0");
        if (!after.equals(expected)) {
            throw new IllegalStateException("Insert of 20 ticks at 10 produced " + after + ", expected " + expected);
        }
        timeline.insertTime(10, -20); // the editor's undo
        var undone = snapshot(timeline);
        if (!undone.equals(before)) {
            throw new IllegalStateException("Undo of the insert left " + undone + ", expected " + before);
        }
    }

    private static void verifyNonFiniteInsertIsIgnored() {
        var timeline = sample();
        var before = snapshot(timeline);
        timeline.insertTime(10, Double.NaN);
        timeline.insertTime(10, Double.POSITIVE_INFINITY);
        timeline.insertTime(10, Double.NEGATIVE_INFINITY);
        timeline.insertTime(Double.NaN, 20);
        timeline.insertTime(Double.POSITIVE_INFINITY, 20);
        var after = snapshot(timeline);
        if (!after.equals(before)) {
            throw new IllegalStateException("A non-finite insert changed the timeline: " + after);
        }
    }

    /** Clips, signals, markers, sections and keyframes on both sides of (and exactly on) tick 10. */
    private static Timeline sample() {
        var timeline = new Timeline();
        var control = PhotonTrackTypes.CONTROL.create();
        control.clips().add(new Clip(0, 10, 1));   // ends exactly at the point: untouched
        control.clips().add(new Clip(15, 5, 1));   // after the point: shifts
        control.clips().add(new Clip(5, 10, 1));   // straddles the point: grows
        timeline.tracks().add(control);
        var signals = (SignalTrack) PhotonTrackTypes.SIGNAL.create();
        signals.signals().add(new Signal(8, "before"));
        signals.signals().add(new Signal(10, "at"));
        signals.signals().add(new Signal(30, "after"));
        timeline.tracks().add(signals);
        var animation = (AnimationTrack) PhotonTrackTypes.ANIMATION.create();
        var type = new ConfigPropertyType("photon_probe.gap", ConfigValueType.FLOAT, "photon_probe.gap");
        var base = new float[]{0};
        var property = new AnimatedProperty(type, base, AnimatedProperty.seedChannels(base), 0, 1);
        property.addKey(0, 5, 0.5f);
        property.addKey(0, 20, 1);
        animation.properties().add(property);
        timeline.tracks().add(animation);
        timeline.markers().add(new Marker(9, "before"));
        timeline.markers().add(new Marker(10, "at"));
        timeline.markers().add(new Marker(25, "after"));
        timeline.sections().add(new TimelineSection("a", 0));
        timeline.sections().add(new TimelineSection("b", 12));
        return timeline;
    }

    private static List<String> snapshot(Timeline timeline) {
        var out = new ArrayList<String>();
        for (var track : timeline.tracks()) {
            for (var clip : track.clips()) out.add("clip " + clip.start() + "+" + clip.duration());
            if (track instanceof SignalTrack signalTrack) {
                for (var signal : signalTrack.signals()) out.add("signal " + signal.time());
            }
        }
        for (var marker : timeline.markers()) out.add("marker " + marker.tick());
        for (var section : timeline.sections()) out.add("section " + section.name() + "@" + section.start());
        for (var track : timeline.tracks()) {
            if (!(track instanceof AnimationTrack animation)) continue;
            for (var property : animation.properties()) {
                for (int k = 0; k < property.keyCount(0); k++) out.add("key " + property.key(0, k).x);
            }
        }
        return out;
    }
}
