package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import com.lowdragmc.photon.util.RegistryAwareNBTSerializable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

/** NBT-copy support for editable sampler data, kept independent of shader/registry initialization. */
final class MaterialSamplerTextureCopy {
    private MaterialSamplerTextureCopy() {
    }

    static <T extends Tag> void copy(RegistryAwareNBTSerializable<T> sourceCurve,
                                     RegistryAwareNBTSerializable<T> sourceGradient,
                                     RegistryAwareNBTSerializable<T> targetCurve,
                                     RegistryAwareNBTSerializable<T> targetGradient,
                                     HolderLookup.@NotNull Provider provider) {
        targetCurve.deserializeNBT(provider, sourceCurve.serializeNBT(provider));
        targetGradient.deserializeNBT(provider, sourceGradient.serializeNBT(provider));
    }
}
