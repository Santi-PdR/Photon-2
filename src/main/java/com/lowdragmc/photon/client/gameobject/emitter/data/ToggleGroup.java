package com.lowdragmc.photon.client.gameobject.emitter.data;

import com.lowdragmc.lowdraglib2.configurator.IToggleConfigurable;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import lombok.Getter;
import lombok.Setter;

public class ToggleGroup implements IToggleConfigurable {
    @Getter
    @Setter
    @Persisted
    protected boolean enable;

}
