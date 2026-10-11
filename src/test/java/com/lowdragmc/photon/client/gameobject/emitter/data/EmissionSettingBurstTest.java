package com.lowdragmc.photon.client.gameobject.emitter.data;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmissionSettingBurstTest {
    @Test
    void firesAtConfiguredTimeAndStopsAtCycleLimit() {
        assertEquals(4, triggers(3, 2, 2, 2, 0, 8));
    }

    @Test
    void zeroCyclesMeansRepeatUntilTheEmitterWindowEnds() {
        assertEquals(6, triggers(3, 2, 0, 2, 0, 8));
    }

    @Test
    void adjacentSimulationWindowsDoNotDuplicateBoundaryTriggers() {
        assertEquals(1, triggers(2, 1, 0, 1, 0, 3));
        assertEquals(2, triggers(2, 1, 0, 1, 3, 5));
    }

    @Test
    void clampsMalformedZeroIntervalInsteadOfRepeatingForever() {
        assertEquals(3, triggers(2, 0, 3, 1, 0, 8));
    }

    private static int triggers(int time, int interval, int cycles, int count, float start, float end) {
        return EmissionSetting.Runtime.burstTriggers(time, interval, cycles, 1f,
                count, RandomSource.create(1), start, end);
    }
}
