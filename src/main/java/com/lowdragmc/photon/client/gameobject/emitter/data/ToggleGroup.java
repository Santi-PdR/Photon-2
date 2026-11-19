package com.lowdragmc.photon.client.gameobject.emitter.data;

import com.lowdragmc.lowdraglib2.configurator.IToggleConfigurable;
import lombok.Getter;
import lombok.Setter;

public class ToggleGroup implements IToggleConfigurable {
    @Getter
    @Setter
    protected boolean enable;

    // A disabled module still owns authored settings. LDLib's default drops them on both
    // serialization and deserialization, losing Photon 1 imports and native edits alike.
    @Override
    public boolean skipDisableSerialize() {
        return false;
    }
}
