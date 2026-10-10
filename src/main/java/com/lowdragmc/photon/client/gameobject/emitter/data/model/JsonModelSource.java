package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.client.renderer.impl.IModelRenderer;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigSetter;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.gameobject.particle.TileParticle;
import dev.vfyjxf.taffy.style.AlignItems;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Geometry from a Minecraft JSON model. Standalone models are parsed from the active resource packs
 * on demand, including their parent chain, so picking a new model does not require bakery registration
 * or a resource reload. UVs remain block-atlas coordinates; positions are centered and per-face shade
 * factors are baked per quad.
 */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "json_model", registry = "photon:model_source")
public class JsonModelSource implements IModelSource {
    @Getter
    @Configurable(name = "MeshData.modelLocation")
    private ResourceLocation modelLocation = ResourceLocation.withDefaultNamespace("block/stone");
    @Nullable
    private volatile ObjLoaderReference objLoaderReference;
    private volatile boolean objLoaderChecked;

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
    }

    @Override
    public boolean hasAtlasUV() {
        return getObjLoaderReference() == null;
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
        // The editor projects can reference OBJ models through Forge/NeoForge's custom model
        // loader JSON. Photon bakes standalone JSON models itself, so BlockModel.fromStream does
        // not dispatch that loader; it sees an empty vanilla model and the actual effect vanishes.
        // Parse those model references through our runtime OBJ path instead.
        var objReference = getObjLoaderReference();
        if (objReference != null) {
            var objSource = new ObjModelSource(objReference.location());
            objSource.setFlipV(objReference.flipV());
            return objSource.getMesh();
        }
        var random = RandomSource.create();
        var bakedModel = PhotonModelBaker.bake(modelLocation);
        if (bakedModel == null) {
            var fallbackLocation = ResourceLocation.withDefaultNamespace("block/stone");
            bakedModel = PhotonModelBaker.bake(fallbackLocation);
        }
        // The model cache treats null as "not ready; try again next frame". A resource reload can
        // begin after the LoadingOverlay check above, leaving even the fallback temporarily unavailable.
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

    /** Extract the OBJ target from a Forge/NeoForge OBJ-loader model JSON, if present. */
    @Nullable
    private ObjLoaderReference getObjLoaderReference() {
        if (objLoaderChecked) return objLoaderReference;
        if (Minecraft.getInstance().getOverlay() instanceof LoadingOverlay) return null;
        synchronized (this) {
            if (objLoaderChecked) return objLoaderReference;
            try {
                var file = ModelBakery.MODEL_LISTER.idToFile(modelLocation);
                var resource = Minecraft.getInstance().getResourceManager().getResource(file).orElse(null);
                Path nestedFile = resource == null ? LDLibModelAssets.findNested(modelLocation, file.getPath()) : null;
                if (resource == null && nestedFile == null) {
                    return null; // resource reload may still be in progress; retry later
                }
                try (var reader = resource != null ? resource.openAsReader() : Files.newBufferedReader(nestedFile)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    var loader = json.has("loader") ? json.get("loader").getAsString() : "";
                    if (loader.equals("forge:obj") || loader.equals("neoforge:obj")) {
                        if (json.has("model") && json.get("model").isJsonPrimitive()) {
                            var flipV = json.has("flip_v") ? json.get("flip_v").getAsBoolean()
                                    : json.has("flipV") && json.get("flipV").getAsBoolean();
                            objLoaderReference = new ObjLoaderReference(
                                    ResourceLocation.parse(json.get("model").getAsString()), flipV);
                        }
                    }
                    objLoaderChecked = true;
                }
            } catch (Exception exception) {
                objLoaderChecked = true;
                Photon.LOGGER.warn("Failed to resolve OBJ model loader for {}", modelLocation, exception);
            }
        }
        return objLoaderReference;
    }

    private record ObjLoaderReference(ResourceLocation location, boolean flipV) { }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void buildConfigurator(ConfiguratorGroup father) {
        IModelSource.super.buildConfigurator(father);
        var buttonConfigurator = new Configurator();
        buttonConfigurator.addInlineChild(new Button().setText("ldlib.gui.editor.tips.select_model").setOnClick(e -> {
            var mui = e.currentElement.getModularUI();
            if (mui == null) return;
            Dialog.showFileDialog("ldlib.gui.editor.tips.select_model", LDLib2.getAssetsDir(), true, node -> {
                if (!node.getKey().isFile() || node.getKey().getName().toLowerCase().endsWith(".json".toLowerCase())) {
                    if (node.getKey().isFile()) {
                        return IModelRenderer.getModelFromFile(node.getKey()) != null;
                    }
                    return true; // allow directories
                }
                return false;
            }, r -> {
                if (r != null && r.isFile()) {
                    var newModel = IModelRenderer.getModelFromFile(r);
                    if (newModel == null) return;
                    if (newModel.equals(modelLocation)) return;
                    setModelLocation(newModel);
                    buttonConfigurator.notifyChanges();
                }
            }).show(mui.ui.rootElement);
        }).layout(layout -> layout.alignSelf(AlignItems.CENTER)));

        var reloadButton = new Configurator().addInlineChild(new Button()
                .setOnClick(event -> { invalidate(); buttonConfigurator.notifyChanges(); })
                .setText("photon.reload_mesh").layout(layout -> layout.alignSelf(AlignItems.CENTER)));
        father.addConfigurators(buttonConfigurator, reloadButton);
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
