package com.lowdragmc.photon.client.fx;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An FX definition: the authored object tree + timeline, as saved by the editor / a {@code .fx} file.
 * <p>
 * Loaded via {@link FXHelper#getFX} (which also sets {@link #getFxLocation()}). A definition is not
 * playable by itself — call {@link #createRuntime()} for a playable instance; each runtime works on
 * its own copy of the data, so one definition can play many times concurrently.
 */
@Getter
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class FX {
    public static final String SUFFIX = ".fx";
    @Nullable
    @Setter
    private ResourceLocation fxLocation;
    private final FXData fxData;
    private final Map<String, FX> legacySubFXs = new LinkedHashMap<>();

    public FX() {
        fxData = new FXData();
    }

    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        var tag = new CompoundTag();
        tag.put("fxData", fxData.serializeNBT(provider));
        if (!legacySubFXs.isEmpty()) {
            var subEffects = new CompoundTag();
            legacySubFXs.forEach((name, fx) -> subEffects.put(name, fx.serializeNBT(provider)));
            tag.put("legacySubFXs", subEffects);
        }
        return tag;
    }

    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        deserializeNBT(provider, tag, fxLocation == null ? List.of() : List.of(fxLocation));
    }

    /** @param as the ids this effect is known by, so an FX instance that leads back to one of them is a cycle */
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag, Collection<ResourceLocation> as) {
        fxData.deserializeNBT(provider, tag.getCompound("fxData"), as);
        legacySubFXs.clear();
        var subEffects = tag.getCompound("legacySubFXs");
        for (var name : subEffects.getAllKeys()) {
            var subEffect = new FX();
            subEffect.deserializeNBT(provider, subEffects.getCompound(name));
            legacySubFXs.put(name, subEffect);
        }
    }

    /**
     * Create a runtime of this FX.
     * @return a runtime of this FX
     */
    public FXRuntime createRuntime() {
        return createRuntime(false);
    }

    /**
     * Create a runtime of this FX.
     * @param deepCopy if true, deep copy the data
     * @return a runtime of this FX
     */
    public FXRuntime createRuntime(boolean deepCopy) {
        return new FXRuntime(fxData.copy(deepCopy));
    }

    /**
     * Create a runtime of this FX which use the raw data.
     */
    public FXRuntime createInternalRuntime() {
        return new FXRuntime(fxData);
    }

}


