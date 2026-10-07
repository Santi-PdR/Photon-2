package com.lowdragmc.photon.client.fx.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FXCompatTest {
    @Test
    void rejectsMalformedProjectStructureInsteadOfCreatingAnEmptyFx() {
        assertThrows(IllegalArgumentException.class, () -> FXLegacyMapper.mapEffect(new CompoundTag()));
    }

    @Test
    void rejectsUnknownLegacyObjectInsteadOfReturningAnIncompleteProject() {
        var object = new CompoundTag();
        object.putString("_type", "unknown_legacy_type");

        var objects = new ListTag();
        objects.add(object);
        var mainFx = new CompoundTag();
        mainFx.put("fxObjects", objects);
        var fx = new CompoundTag();
        fx.put("mainFX", mainFx);
        var input = new CompoundTag();
        input.put("fx", fx);

        var error = assertThrows(IllegalArgumentException.class, () -> FXLegacyMapper.mapEffect(input));
        assertTrue(error.getMessage().contains("unknown_legacy_type"));
    }
}
