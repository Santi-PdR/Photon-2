package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigSetter;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.photon.client.gameobject.particle.TileParticle;
import com.lowdragmc.photon.client.util.PhotonAssetPath;
import dev.vfyjxf.taffy.style.AlignItems;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Geometry from a baked Minecraft JSON model. UVs are block-atlas coordinates and the model must be
 * known to the model bakery (registered via {@code ModelEvent.RegisterAdditional} or loadable
 * dynamically), so picking a new model file needs a resource-pack reload. Positions are shifted
 * into centered space and per-face shade factors are baked per quad (the {@code shade} toggle is
 * applied at consumption time).
 */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "json_model", registry = "photon:model_source")
public class JsonModelSource implements IModelSource {
    @Getter
    @Configurable(name = "MeshData.modelLocation")
    private ResourceLocation modelLocation = ResourceLocation.withDefaultNamespace("block/stone");
    private volatile ObjLoaderReference objLoaderReference;
    private volatile boolean objLoaderChecked;
    private volatile long objLoaderGeneration = Long.MIN_VALUE;

    public JsonModelSource() {
    }

    public JsonModelSource(ResourceLocation modelLocation) {
        this.modelLocation = modelLocation;
    }

    @ConfigSetter(field = "modelLocation")
    public void setModelLocation(ResourceLocation modelLocation) {
        invalidate(); // drop the old key's entry before it changes
        this.modelLocation = modelLocation;
    }

    @Override
    public PhotonMesh getMesh() {
        return PhotonMeshCache.INSTANCE.get(new PhotonMeshCache.JsonKey(modelLocation), k -> bake());
    }

    @Override
    public void invalidate() {
        PhotonMeshCache.INSTANCE.invalidate(new PhotonMeshCache.JsonKey(modelLocation));
        objLoaderReference = null;
        objLoaderChecked = false;
        objLoaderGeneration = PhotonMeshCache.INSTANCE.generation();
    }

    @Override
    public boolean hasAtlasUV() {
        return getObjLoaderReference() == null;
    }

    /**
     * OBJ model JSON files are parsed directly by Photon and do not need registration with Minecraft's
     * model bakery. In particular, a NeoForge "neoforge:obj" loader is not registered by Forge.
     */
    public boolean isObjLoaderModel() {
        try {
            ResourceLocation file = ModelBakery.MODEL_LISTER.idToFile(modelLocation);
            Path nestedFile = LDLibModelAssets.findNested(modelLocation, file.getPath());
            if (nestedFile == null) return false;
            try (BufferedReader reader = Files.newBufferedReader(nestedFile)) {
                return isObjLoaderJson(JsonParser.parseReader(reader).getAsJsonObject());
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isObjLoaderJson(JsonObject json) {
        String loader = json.has("loader") ? json.get("loader").getAsString() : "";
        return (loader.equals("forge:obj") || loader.equals("neoforge:obj"))
                && json.has("model") && json.get("model").isJsonPrimitive();
    }

    @Override
    public IModelSource copy() {
        return new JsonModelSource(modelLocation);
    }

    @Nullable
    private PhotonMesh bake() {
        // do not access the model bakery during reloading (null = retry later, not cached)
        if (Minecraft.getInstance().getOverlay() instanceof LoadingOverlay) {
            return null;
        }
        ObjLoaderReference objReference = getObjLoaderReference();
        if (objReference != null) {
            ResourceLocation objFile = ModelBakery.MODEL_LISTER.idToFile(objReference.location());
            Path nestedObj = LDLibModelAssets.findNested(objReference.location(), objFile.getPath());
            if (nestedObj != null) {
                PhotonMeshCache.INSTANCE.trackFile(new PhotonMeshCache.JsonKey(modelLocation), nestedObj.toFile());
            }
            ObjModelSource objSource = new ObjModelSource(objReference.location());
            objSource.setFlipV(objReference.flipV());
            return objSource.getMesh().translated(-0.5f, -0.5f, -0.5f);
        }
        var random = RandomSource.create();
        BakedModel bakedModel = PhotonModelBaker.bake(modelLocation);
        if (bakedModel == null) {
            return null;
        }
        var quads = new ArrayList<Pair<BakedQuad, Float>>();
        for (var side : TileParticle.MODEL_SIDES) {
            var brightness = side == null ? 1f : switch (side) {
                case DOWN, UP -> 0.9F;
                case NORTH, SOUTH -> 0.8F;
                case WEST, EAST -> 0.6F;
            };
            for (var quad : bakedModel.getQuads(null, side, random, ModelData.EMPTY, null)) {
                quads.add(Pair.of(quad, brightness));
            }
        }
        return PhotonMesh.fromBakedQuads(quads);
    }

    @Nullable
    private ObjLoaderReference getObjLoaderReference() {
        long generation = PhotonMeshCache.INSTANCE.generation();
        if (objLoaderGeneration != generation) {
            synchronized (this) {
                if (objLoaderGeneration != generation) {
                    objLoaderReference = null;
                    objLoaderChecked = false;
                    objLoaderGeneration = generation;
                }
            }
        }
        if (objLoaderChecked || Minecraft.getInstance().getOverlay() instanceof LoadingOverlay) {
            return objLoaderReference;
        }
        synchronized (this) {
            if (objLoaderChecked) return objLoaderReference;
            try {
                ResourceLocation file = ModelBakery.MODEL_LISTER.idToFile(modelLocation);
                Path nestedFile = LDLibModelAssets.findNested(modelLocation, file.getPath());
                if (nestedFile != null) {
                    PhotonMeshCache.INSTANCE.trackFile(new PhotonMeshCache.JsonKey(modelLocation), nestedFile.toFile());
                }
                Resource resource = nestedFile == null
                        ? Minecraft.getInstance().getResourceManager().getResource(file).orElse(null)
                        : null;
                if (resource == null && nestedFile == null) return null;

                try (BufferedReader reader = nestedFile == null
                        ? resource.openAsReader()
                        : Files.newBufferedReader(nestedFile)) {
                    JsonObject json = JsonParser.parseReader((Reader) reader).getAsJsonObject();
                    if (isObjLoaderJson(json)) {
                        boolean flipV = json.has("flip_v") ? json.get("flip_v").getAsBoolean()
                                : json.has("flipV") && json.get("flipV").getAsBoolean();
                        objLoaderReference = new ObjLoaderReference(
                                new ResourceLocation(json.get("model").getAsString()), flipV);
                    }
                    objLoaderChecked = true;
                    objLoaderGeneration = PhotonMeshCache.INSTANCE.generation();
                }
            } catch (Exception exception) {
                objLoaderChecked = true;
                objLoaderGeneration = PhotonMeshCache.INSTANCE.generation();
                com.lowdragmc.photon.Photon.LOGGER.warn(
                        "Failed to resolve OBJ model loader for {}", modelLocation, exception);
            }
        }
        return objLoaderReference;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void buildConfigurator(ConfiguratorGroup father) {
        IModelSource.super.buildConfigurator(father);
        var buttonConfigurator = new Configurator();
        buttonConfigurator.addInlineChild(new Button().setText("ldlib.gui.editor.tips.select_model").setOnClick(e -> {
            var mui = e.currentElement.getModularUI();
            if (mui == null) return;
            Dialog.showFileDialog("ldlib.gui.editor.tips.select_model", LDLib2.getAssetsDir(), true, node -> {
                return !node.getKey().isFile() || node.getKey().getName().toLowerCase().endsWith(".json");
            }, r -> {
                if (r != null && r.isFile()) {
                    var newModel = getModelLocationFromFile(r);
                    if (newModel == null) return;
                    if (newModel.equals(modelLocation)) return;
                    setModelLocation(newModel);
                    buttonConfigurator.notifyChanges();
                }
            }).show(mui.ui.rootElement);
        }).layout(layout -> layout.alignSelf(AlignItems.CENTER)));

        var reloadButton = new Configurator().addInlineChild(new Button()
                .setOnClick(event -> Minecraft.getInstance().reloadResourcePacks().thenAccept(v ->
                        Minecraft.getInstance().execute(() -> {
                            invalidate();
                            buttonConfigurator.notifyChanges();
                        })
                )).setText("photon.reload_mesh").layout(layout -> layout.alignSelf(AlignItems.CENTER)));
        father.addConfigurators(buttonConfigurator, reloadButton);
    }

    @Nullable
    private static ResourceLocation getModelLocationFromFile(java.io.File file) {
        return PhotonAssetPath.modelLocationFromFile(LDLib2.getAssetsDir().toPath(), file.toPath());
    }

    private record ObjLoaderReference(ResourceLocation location, boolean flipV) {
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(modelLocation, ((JsonModelSource) o).modelLocation);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(modelLocation);
    }
}

