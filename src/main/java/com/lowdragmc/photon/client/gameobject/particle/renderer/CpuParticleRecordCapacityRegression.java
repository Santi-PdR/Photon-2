package com.lowdragmc.photon.client.gameobject.particle.renderer;

import com.lowdragmc.photon.Photon;

/** Guards the CPU shadergraph record path against oversized or stale GL buffer stores. */
public final class CpuParticleRecordCapacityRegression {
    private CpuParticleRecordCapacityRegression() {
    }

    public static void verify() {
        check(CpuParticleRecords.fitsRecordCapacity(8_192, 8, 4, 65_536),
                "records exactly at the GL texture-buffer boundary were rejected");
        check(!CpuParticleRecords.fitsRecordCapacity(8_193, 8, 4, 65_536),
                "oversized CPU shadergraph records were accepted");
        check(!CpuParticleRecords.fitsRecordCapacity(16_385, 0, 4, 65_536),
                "oversized CPU custom-data records were accepted");
        check(!CpuParticleRecords.fitsRecordCapacity(Long.MAX_VALUE, 1, 0, Integer.MAX_VALUE),
                "a record count that overflows Java's float staging buffer was accepted");
        check(CpuParticleRecords.fitsRecordCapacity(0, 0, 0, 0),
                "a pass with no record TBOs was rejected");
        check(CpuParticleRecords.needsBufferAllocation(true, 64, 256),
                "a recreated TBO did not request a new GL storage allocation");
        check(CpuParticleRecords.needsBufferAllocation(false, 257, 256),
                "a growing TBO did not request a storage allocation");
        check(!CpuParticleRecords.needsBufferAllocation(false, 256, 256),
                "an unchanged TBO unnecessarily requested reallocation");
        Photon.LOGGER.info("[cpu-record-capacity-smoke] oversized CPU records degrade safely and recreated TBO stores reallocate");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
