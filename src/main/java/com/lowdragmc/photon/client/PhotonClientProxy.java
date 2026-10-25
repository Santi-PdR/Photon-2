package com.lowdragmc.photon.client;

import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.PhotonCommonProxy;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.FXPreloader;
import com.lowdragmc.photon.client.fx.fxpack.FXPacks;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.PhotonMeshCache;
import com.lowdragmc.photon.gui.editor.resource.MeshResource;
import com.lowdragmc.photon.gui.editor.PhotonEditorKeyMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@OnlyIn(Dist.CLIENT)
public class PhotonClientProxy extends PhotonCommonProxy {

    public static void init(IEventBus eventBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, PhotonConfig.CONFIG_SPEC);
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new PhotonConfigScreen(parent)));
        new PhotonClientProxy(eventBus);
    }

    public PhotonClientProxy(IEventBus eventBus) {
        super(eventBus);
        eventBus.addListener(this::clientSetup);
        eventBus.addListener(this::shaderRegistry);
        eventBus.addListener(this::registerModels);
        eventBus.addListener(this::registerReloadListeners);
        eventBus.addListener(this::addPackFinders);
        eventBus.addListener(PhotonEditorKeyMappings::register);
    }

    /** Mount every .fxpack as a hidden, always-on, lowest-priority resource pack; see {@link FXPacks}. */
    @SubscribeEvent
    public void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            event.addRepositorySource(FXPacks.repositorySource());
        }
    }

    @SubscribeEvent
    public void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(PhotonMeshCache.INSTANCE);
        // both the loaded effects and the list of loadable ones are answers about the packs, so a reload
        // (a pack toggled, an .fxpack mounted, F3+T) is exactly when they stop being true
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            FXHelper.clearCache();
            FXPreloader.onResourcesReloaded();
        });
    }

    @SubscribeEvent
    public void clientSetup(final FMLClientSetupEvent e) {
        e.enqueueWork(PhotonShaders::init);
        // Touch the registry to trigger annotation scanning; classes annotated with @NodeAttribute
        // bound to ShaderGraph self-register (mirrors KilaGraph's own registry bootstrap).
        Photon.LOGGER.info("Photon shader graph nodes loaded: {}",
                com.lowdragmc.photon.client.shadergraph.ShaderGraph.NODE_REGISTRY.getNodeClasses().size());
    }

    @SubscribeEvent
    public void shaderRegistry(RegisterShadersEvent event) {
        PhotonShaders.registerShaders(event);
    }

    @SubscribeEvent
    public void registerModels(ModelEvent.RegisterAdditional event) {
        // load all models under the ldlib folder
        for (var entry : Minecraft.getInstance().getResourceManager().listResources("models",
                id -> id.getNamespace().equals(Photon.MOD_ID) && id.getPath().endsWith(".json")).entrySet()) {
            var modelLocation = new ResourceLocation(
                    entry.getKey().getNamespace(),
                    entry.getKey().getPath()
                            .replace("models/", "")
                            .replace(".json", ""));
            event.register(new ModelResourceLocation(modelLocation, "standalone"));
        }
        MeshResource.INSTANCE.onAdditionalModel(event::register);
    }
}
