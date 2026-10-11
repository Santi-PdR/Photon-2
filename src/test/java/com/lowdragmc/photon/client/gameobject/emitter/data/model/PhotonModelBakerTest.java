package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhotonModelBakerTest {
    @Test
    void editableLdLibModelWinsOverAnAlreadyBakedModelWithTheSameId() {
        var bakeryLookups = new AtomicInteger();
        var resourceLookups = new AtomicInteger();

        var selected = PhotonModelBaker.selectModelSource(Path.of("editable/model.json"),
                () -> "editable",
                () -> {
                    bakeryLookups.incrementAndGet();
                    return "stale bakery model";
                },
                () -> {
                    resourceLookups.incrementAndGet();
                    return "resource manager";
                });

        assertEquals("editable", selected);
        assertEquals(0, bakeryLookups.get());
        assertEquals(0, resourceLookups.get());
    }

    @Test
    void bakeryModelIsReusedWhenThereIsNoEditableCopy() {
        var resourceLookups = new AtomicInteger();

        var selected = PhotonModelBaker.selectModelSource(null,
                () -> "editable",
                () -> "cached bakery model",
                () -> {
                    resourceLookups.incrementAndGet();
                    return "resource manager";
                });

        assertEquals("cached bakery model", selected);
        assertEquals(0, resourceLookups.get());
    }

    @Test
    void resourceManagerIsUsedWhenNeitherEditableNorBakedModelExists() {
        var selected = PhotonModelBaker.selectModelSource(null,
                () -> "editable",
                () -> null,
                () -> "resource manager");

        assertEquals("resource manager", selected);
    }
}
