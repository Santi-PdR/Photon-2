package com.lowdragmc.photon.util;

import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.TransformRef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TransformRefNbtTest {
    @Test
    void writesAndRestoresTransformIdAsStringNbt() {
        var id = UUID.randomUUID();
        var original = new TransformRef(id);
        var tag = new CompoundTag();

        TransformRefNbt.writeCustomSpace(tag, original);
        var restored = new TransformRef();
        TransformRefNbt.readCustomSpace(tag, restored);

        assertEquals(StringTag.valueOf(id.toString()), tag.get(TransformRefNbt.CUSTOM_SPACE_KEY));
        assertEquals(id, restored.getTransformId());
    }

    @Test
    void malformedValueDoesNotReplaceDefaultReference() {
        var tag = new CompoundTag();
        tag.putInt(TransformRefNbt.CUSTOM_SPACE_KEY, 12);
        var restored = new TransformRef();

        TransformRefNbt.readCustomSpace(tag, restored);

        assertNull(restored.getTransformId());
    }
}
