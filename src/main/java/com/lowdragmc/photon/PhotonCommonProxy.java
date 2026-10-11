package com.lowdragmc.photon;

import com.lowdragmc.photon.command.EntityEffectCommand;
import com.lowdragmc.photon.command.FxLocationArgument;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Function;

public class PhotonCommonProxy {
    // Keep this retired development ID registered so saves created by the earlier Forge port
    // do not lose the stack during Forge's missing-mapping cleanup. It is intentionally not
    // placed in a creative tab and has no development/test behavior.
    private static final DeferredRegister<Item> LEGACY_ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Photon.MOD_ID);
    @SuppressWarnings("unused")
    private static final RegistryObject<Item> LEGACY_BLADE_TEST =
            LEGACY_ITEMS.register("blade_test", () -> new Item(new Item.Properties().stacksTo(1)));

    static final DeferredRegister<ArgumentTypeInfo<?, ?>> ARG_TYPES =
            DeferredRegister.create(ForgeRegistries.COMMAND_ARGUMENT_TYPES, Photon.MOD_ID);
    static final RegistryObject<ArgumentTypeInfo<FxLocationArgument, ?>> FX_LOCATION_ARG_TYPE =
            ARG_TYPES.register("fx_location", () -> SingletonArgumentInfo.contextFree(FxLocationArgument::new));
    static final RegistryObject<ArgumentTypeInfo<EntityEffectCommand.AutoRotateType, ?>> AUTO_ROTATE_ARG_TYPE =
            ARG_TYPES.register("fx_auto_rotate", () ->
                    SingletonArgumentInfo.contextFree(EntityEffectCommand.AutoRotateType::new));

    public static Function<IEventBus, ?> factory() {
        return PhotonCommonProxy::new;
    }

    public PhotonCommonProxy(IEventBus eventBus) {
        eventBus.addListener(this::commonSetup);
        LEGACY_ITEMS.register(eventBus);
        ARG_TYPES.register(eventBus);
        PhotonNetworking.init();
        PhotonRegistries.init();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ArgumentTypeInfos.registerByClass(FxLocationArgument.class, FX_LOCATION_ARG_TYPE.get());
            ArgumentTypeInfos.registerByClass(EntityEffectCommand.AutoRotateType.class, AUTO_ROTATE_ARG_TYPE.get());
        });
    }
}
