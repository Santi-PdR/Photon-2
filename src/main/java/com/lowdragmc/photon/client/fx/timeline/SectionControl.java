package com.lowdragmc.photon.client.fx.timeline;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Moves a playing timeline between its sections from outside: Java, commands, the editor's preview. Requests take
 * effect at the next tick ({@link #jumpTo}) or at the end of the current pass ({@link #queue}, {@link #advance}); each
 * returns false when it does nothing (no such section, the timeline has ended, or the effect is being stopped).
 * <p>
 * Ported from Photon 26.3. Client thread only: everything here reads the clock the render thread ticks.
 */
public final class SectionControl {
    /** The playback behind this control; null when it was built over a bare clock. */
    @Nullable
    private final TimelinePlayer player;
    /** The bare clock of {@link #of(SectionClock)}; null when there is a player. */
    @Nullable
    private final SectionClock bareClock;

    SectionControl(TimelinePlayer player) {
        this.player = player;
        this.bareClock = null;
    }

    private SectionControl(SectionClock clock) {
        this.player = null;
        this.bareClock = clock;
    }

    /**
     * A control over a clock with no playback behind it: requests are accepted or refused on the sections alone. For
     * checks that drive the clock directly (the smoke test); in game a control always comes from a
     * {@link TimelinePlayer}, so "the effect has already ended" is part of the answer.
     */
    public static SectionControl of(SectionClock clock) {
        return new SectionControl(clock);
    }

    public boolean hasSections() {
        return clock().hasSections();
    }

    /** The section names, in time order. */
    public List<String> names() {
        return clock().names();
    }

    /** The section playing (a requested jump's target once requested); null before the first, or once it ended. */
    @Nullable
    public String current() {
        return playing() ? clock().current() : null;
    }

    /** Passes completed through the current section. */
    public int pass() {
        return clock().pass();
    }

    /** From the next tick on, play from the start of {@code section}. */
    public boolean jumpTo(String section) {
        return playing() && clock().jumpTo(section);
    }

    /** When this pass through the current section ends, go to {@code section} instead (seamless). */
    public boolean queue(String section) {
        return playing() && clock().queue(section);
    }

    /** Stop repeating the current section: when this pass ends, go where the section's "then" says. */
    public boolean advance() {
        return playing() && clock().advance();
    }

    /** Go to the release section, now or at the end of this pass as the timeline says. */
    public boolean release() {
        return playing() && clock().release();
    }

    public boolean isReleased() {
        return clock().isReleased();
    }

    public boolean hasRelease() {
        return clock().hasRelease();
    }

    /** The clock this control steers: the player's, or the bare one it was built over. */
    SectionClock clock() {
        return player != null ? player.clock() : bareClock;
    }

    /** Whether the playback can still take a request; a bare clock has no playback, so it always can. */
    boolean playing() {
        return player == null || !player.isStopped() && !player.isFinished();
    }
}
