package com.lowdragmc.photon;

import com.lowdragmc.photon.command.EntityEffectCommand;
import com.lowdragmc.photon.command.FxLocationArgument;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class PhotonCommonProxy {
    static final DeferredRegister<ArgumentTypeInfo<?, ?>> ARG_TYPES = DeferredRegister.create(Registries.COMMAND_ARGUMENT_TYPE, Photon.MOD_ID);
    static final RegistryObject<ArgumentTypeInfo<?, ?>> FX_LOCATION_ARG_TYPE
            = ARG_TYPES.register("fx_location", () -> ArgumentTypeInfos.registerByClass(FxLocationArgument.class,
            SingletonArgumentInfo.contextFree(FxLocationArgument::new)));
    static final RegistryObject<ArgumentTypeInfo<?, ?>> AUTO_ROTATE_ARG_TYPE
            = ARG_TYPES.register("fx_auto_rotate", () -> ArgumentTypeInfos.registerByClass(EntityEffectCommand.AutoRotateType.class,
            SingletonArgumentInfo.contextFree(EntityEffectCommand.AutoRotateType::new)));

    public PhotonCommonProxy(IEventBus eventBus) {
        PhotonNetworking.register();
        ARG_TYPES.register(eventBus);
        PhotonRegistries.init();
    }
}
