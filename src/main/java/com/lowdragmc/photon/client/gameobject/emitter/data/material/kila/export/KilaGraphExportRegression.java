package com.lowdragmc.photon.client.gameobject.emitter.data.material.kila.export;

import com.lowdragmc.lowdraglib2.math.HDRColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Regression for keeping malformed persisted values out of exported shader graph parameters. */
@OnlyIn(Dist.CLIENT)
public final class KilaGraphExportRegression {
    private KilaGraphExportRegression() {
    }

    public static void verify() {
        if (KilaGraphExport.safeGraphFloat(Float.NaN) != 0f
                || KilaGraphExport.safeGraphFloat(Float.NEGATIVE_INFINITY) != 0f
                || KilaGraphExport.safeGraphFloat(2_000_000f) != 1_000_000f
                || KilaGraphExport.safeGraphFloat(-2_000_000f) != -1_000_000f) {
            throw new IllegalStateException("Kila graph export did not clamp invalid scalar parameters");
        }
        var fallback = KilaGraphExport.safeGraphColor(null);
        if (fallback == null || !Float.isFinite(fallback.getR()) || !Float.isFinite(fallback.getG())
                || !Float.isFinite(fallback.getB()) || !Float.isFinite(fallback.getA())
                || !Float.isFinite(fallback.getIntensity())) {
            throw new IllegalStateException("Kila graph export did not provide a finite default HDR color");
        }
        var safe = KilaGraphExport.safeGraphColor(new HDRColor(Float.NaN, 0.25f, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, 2f));
        if (safe.getR() != 0f || safe.getG() != 0.25f || safe.getB() != 0f || safe.getA() != 0f
                || safe.getIntensity() != 2f) {
            throw new IllegalStateException("Kila graph export did not sanitize HDR color components");
        }
    }
}
