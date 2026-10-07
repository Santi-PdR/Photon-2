package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import com.lowdragmc.lowdraglib2.syncdata.IProviderAwareNBTSerializable;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomShaderMaterialCopyTest {
    @Test
    void samplerTextureDefinitionsAreCopied() {
        var provider = RegistryAccess.EMPTY;
        var sourceCurves = new ListState("curve-a", "curve-b");
        var sourceGradients = new ListState("gradient-a");
        var copiedCurves = new ListState();
        var copiedGradients = new ListState();

        MaterialSamplerTextureCopy.copy(sourceCurves, sourceGradients,
                copiedCurves, copiedGradients, provider);

        assertEquals(sourceCurves.value, copiedCurves.value);
        assertEquals(sourceGradients.value, copiedGradients.value);
    }

    private static final class ListState implements IProviderAwareNBTSerializable<ListTag> {
        private ListTag value = new ListTag();

        private ListState(String... values) {
            for (var value : values) this.value.add(StringTag.valueOf(value));
        }

        @Override
        public ListTag serializeNBT(HolderLookup.Provider provider) {
            return value.copy();
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider provider, ListTag tag) {
            value = tag.copy();
        }
    }
}
