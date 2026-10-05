package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObjMeshParserTest {
    @Test
    void sharesSamplingVerticesByPositionUvAndNormalTuple() {
        var mesh = ObjMeshParser.parseText("""
                v 0 0 0
                v 1 0 0
                v 1 1 0
                v 0 1 0
                vt 0 0
                vt 1 0
                vt 1 1
                vt 0 1
                vt 0.25 0.25
                vn 0 0 1
                f 1/1/1 2/2/1 3/3/1
                f 1/5/1 3/3/1 4/4/1
                """, false);

        var topology = mesh.samplingTopology();
        assertEquals(5, topology.vertexCount(), "the changed UV tuple splits one shared position");
        assertEquals(2, topology.triangleCount());
        assertEquals(topology.triangleVertex(0, 2), topology.triangleVertex(1, 1),
                "the other shared OBJ tuple remains welded across faces");
    }
}
