package com.lowdragmc.photon.integration;

import com.lowdragmc.lowdraglib2.plugin.ILDLibPlugin;
import com.lowdragmc.lowdraglib2.plugin.LDLibPlugin;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;


/**
 * @author KilaBash
 * @date 2023/6/4
 * @implNote LDLibPlugin
 */
@LDLibPlugin
public class PhotonLDLibPlugin implements ILDLibPlugin {

    @Override
    public void onLoad() {
        AccessorRegistries.setPriority(1000);
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> PhotonLDLibClientPlugin::registerAccessors);
    }
}
