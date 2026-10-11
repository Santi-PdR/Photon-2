package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import com.lowdragmc.lowdraglib2.client.model.ModelFactory;
import com.lowdragmc.photon.Photon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** Loads and bakes standalone JSON models directly from the active resource packs. */
@OnlyIn(Dist.CLIENT)
final class PhotonModelBaker implements ModelBaker {
    private static final int MAX_PARENT_DEPTH = 64;
    private static final ResourceLocation GENERATED_MODEL = ResourceLocation.fromNamespaceAndPath("minecraft", "builtin/generated");
    private static final ResourceLocation ENTITY_MODEL = ResourceLocation.fromNamespaceAndPath("minecraft", "builtin/entity");

    private final Map<ResourceLocation, UnbakedModel> models = new HashMap<>();
    private final Set<ResourceLocation> resolving = new HashSet<>();
    private final Function<Material, TextureAtlasSprite> textureGetter = Material::sprite;

    private PhotonModelBaker() {
    }

    @Nullable
    static BakedModel bake(ResourceLocation modelLocation) {
        var baker = new PhotonModelBaker();
        try {
            UnbakedModel model = baker.getModel(modelLocation);
            if (model == null) return null;
            return ModelFactory.bakeUncached(baker, model, BlockModelRotation.X0_Y0,
                    baker.getModelTextureGetter(), modelLocation);
        } catch (Exception exception) {
            Photon.LOGGER.warn("Failed to bake standalone JSON model {}", modelLocation, exception);
            return null;
        }
    }

    @Override
    public UnbakedModel getModel(ResourceLocation location) {
        return resolveModel(location, 0);
    }

    /**
     * LDLib2's editable asset tree is authoritative for a model while that file exists. Only fall
     * back to Forge/LDLib's bakery cache when there is no editable copy; otherwise a model already
     * baked from an older resource-pack version can shadow the file the user is editing.
     */
    private UnbakedModel resolveModel(ResourceLocation location, int depth) {
        var cached = models.get(location);
        if (cached != null) return cached;
        if (location.equals(GENERATED_MODEL)) return ModelBakery.GENERATION_MARKER;
        if (location.equals(ENTITY_MODEL)) return ModelBakery.BLOCK_ENTITY_MARKER;

        Path nestedFile = findNestedModel(location);
        return selectModelSource(nestedFile,
                () -> readStandalone(location, depth),
                () -> ModelFactory.getCachedModel(location),
                () -> readStandalone(location, depth));
    }

    /** Small, testable priority rule: editable file, existing bakery model, then resource manager. */
    static <T> T selectModelSource(Path nestedFile, Supplier<T> editable, Supplier<T> bakery,
                                   Supplier<T> resourceManager) {
        if (nestedFile != null) return editable.get();
        T cached = bakery.get();
        return cached != null ? cached : resourceManager.get();
    }

    @Nullable
    private static Path findNestedModel(ResourceLocation location) {
        var file = ModelBakery.MODEL_LISTER.idToFile(location);
        return LDLibModelAssets.findNested(location, file.getPath());
    }

    @Nullable
    private UnbakedModel readStandalone(ResourceLocation location, int depth) {
        var cached = models.get(location);
        if (cached != null) return cached;
        if (depth > MAX_PARENT_DEPTH || !resolving.add(location)) {
            Photon.LOGGER.warn("Cyclic or too-deep JSON model parent chain at {}", location);
            return ModelFactory.getUnBakedModel(ModelBakery.MISSING_MODEL_LOCATION);
        }
        try {
            var file = ModelBakery.MODEL_LISTER.idToFile(location);
            // LDLib2 keeps editable assets under <assets>/<namespace>/assets/<namespace>. Prefer
            // that copy just as JsonModelSource does for Forge/NeoForge OBJ-loader references;
            // otherwise a model that exists only in the editor tree silently becomes MISSING_MODEL,
            // or an older resource-pack override wins over the model the project is editing.
            Path nestedFile = LDLibModelAssets.findNested(location, file.getPath());
            var resource = nestedFile == null
                    ? Minecraft.getInstance().getResourceManager().getResource(file).orElse(null)
                    : null;
            if (resource == null && nestedFile == null) {
                return ModelFactory.getUnBakedModel(ModelBakery.MISSING_MODEL_LOCATION);
            }
            BlockModel model;
            try (var reader = nestedFile == null ? resource.openAsReader() : Files.newBufferedReader(nestedFile)) {
                model = BlockModel.fromStream(reader);
            }
            model.name = location.toString();
            // Cache before resolving parents so a cycle terminates at the repeated model. The
            // vanilla resolver then logs and breaks the loop instead of recursing indefinitely.
            models.put(location, model);
            model.resolveParents(parent -> resolveModel(parent, depth + 1));
            return model;
        } catch (Exception exception) {
            Photon.LOGGER.warn("Failed to read standalone JSON model {}", location, exception);
            return ModelFactory.getUnBakedModel(ModelBakery.MISSING_MODEL_LOCATION);
        } finally {
            resolving.remove(location);
        }
    }

    @Override
    public BakedModel bake(ResourceLocation location, ModelState state) {
        var model = getModel(location);
        return model == null ? null : ModelFactory.bakeUncached(this, model, state, textureGetter, location);
    }

    @Override
    public BakedModel bake(ResourceLocation location, ModelState state,
                           Function<Material, TextureAtlasSprite> sprites) {
        var model = getModel(location);
        return model == null ? null : ModelFactory.bakeUncached(this, model, state, sprites, location);
    }

    @Override
    public Function<Material, TextureAtlasSprite> getModelTextureGetter() {
        return textureGetter;
    }
}
