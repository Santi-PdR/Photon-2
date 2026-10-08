package com.lowdragmc.photon.client.gameobject.emitter.data.number.curve;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ECBCurvesCodecTest {
    @Test
    void codecRoundTripsEveryBezierControlPoint() {
        var encoded = JsonParser.parseString("[[0.0,0.5,0.1,0.5,0.9,0.5,1.0,0.5],[1.0,0.5,1.1,0.6,1.9,0.8,2.0,1.0]]");

        var curves = ECBCurves.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();
        var roundTrip = ECBCurves.CODEC.encodeStart(JsonOps.INSTANCE, curves).result().orElseThrow();

        assertEquals(2, curves.getSegments().size());
        assertEquals(encoded.toString(), roundTrip.toString());
    }

    @Test
    void codecSkipsSegmentsWithFewerThanEightCoordinates() {
        var curves = ECBCurves.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("[[0,0.5,1]]")).result().orElseThrow();

        assertEquals(0, curves.getSegments().size());
        assertEquals(JsonParser.parseString("[]"),
                ECBCurves.CODEC.encodeStart(JsonOps.INSTANCE, curves).result().orElseThrow());
    }
}
