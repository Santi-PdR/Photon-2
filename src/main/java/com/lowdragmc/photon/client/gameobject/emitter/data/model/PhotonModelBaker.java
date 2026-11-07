package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import com.lowdragmc.lowdraglib2.client.model.ModelFactory;
import com.lowdragmc.photon.Photon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** Bakes editable LDLib2 JSON models without requiring a resource-pack reload. */
@OnlyIn(Dist.CLIENT)
final class PhotonModelBaker implements ModelBaker {
    private static final int MAX_PARENT_DEPTH = 64;
    private final PhotonMeshCache.JsonKey cacheKey;
    private final Map<ResourceLocation, UnbakedModel> models = new HashMap<>();
    private final Set<ResourceLocation> resolving = new HashSet<>();
    private final Function<Material, TextureAtlasSprite> textureGetter = Material::sprite;

    private PhotonModelBaker(PhotonMeshCache.JsonKey cacheKey) {
        this.cacheKey = cacheKey;
    }

    @Nullable
    static BakedModel bake(ResourceLocation modelLocation) {
        PhotonModelBaker baker = new PhotonModelBaker(new PhotonMeshCache.JsonKey(modelLocation));
        try {
            UnbakedModel model = baker.getModel(modelLocation);
            return model == null ? null : ModelFactory.bakeUncached(
                    baker, model, BlockModelRotation.X0_Y0, baker.getModelTextureGetter(), modelLocation);
        } catch (Exception exception) {
            Photon.LOGGER.warn("Failed to bake standalone JSON model {}", modelLocation, exception);
            return null;
        }
    }

    @Override
    @Nullable
    public UnbakedModel getModel(ResourceLocation location) {
        return resolveModel(location, 0);
    }

    private UnbakedModel resolveModel(ResourceLocation location, int depth) {
        if (resolving.contains(location)) {
            Photon.LOGGER.warn("Cyclic JSON model parent chain at {}", location);
            return ModelFactory.getUnBakedModel(ModelBakery.MISSING_MODEL_LOCATION);
        }
        UnbakedModel cached = models.get(location);
        if (cached != null) return cached;
        if (location.equals(ResourceLocation.withDefaultNamespace("builtin/generated"))) {
            return ModelBakery.GENERATION_MARKER;
        }
        if (location.equals(ResourceLocation.withDefaultNamespace("builtin/entity"))) {
            return ModelBakery.BLOCK_ENTITY_MARKER;
        }

        Path nestedFile = findNestedModel(location);
        if (nestedFile != null) {
            PhotonMeshCache.INSTANCE.trackFile(cacheKey, nestedFile.toFile());
        }
        return selectModelSource(
                nestedFile,
                () -> readStandalone(location, depth),
                () -> ModelFactory.getCachedModel(location),
                () -> readStandalone(location, depth));
    }

    static <T> T selectModelSource(Path nestedFile, Supplier<T> editable, Supplier<T> bakery,
                                   Supplier<T> resourceManager) {
        if (nestedFile != null) return editable.get();
        T cached = bakery.get();
        return cached != null ? cached : resourceManager.get();
    }

    @Nullable
    private static Path findNestedModel(ResourceLocation location) {
        ResourceLocation file = ModelBakery.MODEL_LISTER.idToFile(location);
        return LDLibModelAssets.findNested(location, file.getPath());
    }

    @Nullable
    private UnbakedModel readStandalone(ResourceLocation location, int depth) {
        UnbakedModel cached = models.get(location);
        if (cached != null) return cached;
        if (depth > MAX_PARENT_DEPTH || !resolving.add(location)) {
            Photon.LOGGER.warn("Cyclic or too-deep JSON model parent chain at {}", location);
            return ModelFactory.getUnBakedModel(ModelBakery.MISSING_MODEL_LOCATION);
        }

        try {
            ResourceLocation file = ModelBakery.MODEL_LISTER.idToFile(location);
            Path nestedFile = LDLibModelAssets.findNested(location, file.getPath());
            Resource resource = nestedFile == null
                    ? Minecraft.getInstance().getResourceManager().getResource(file).orElse(null)
                    : null;
            if (nestedFile == null && resource == null) {
                return ModelFactory.getUnBakedModel(ModelBakery.MISSING_MODEL_LOCATION);
            }

            BlockModel model;
            try (BufferedReader reader = nestedFile == null
                    ? resource.openAsReader()
                    : Files.newBufferedReader(nestedFile)) {
                model = BlockModel.fromStream(reader);
            }
            model.name = location.toString();
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
    @Nullable
    public BakedModel bake(ResourceLocation location, ModelState state) {
        return bake(location, state, textureGetter);
    }

    @Override
    @Nullable
    public BakedModel bake(ResourceLocation location, ModelState state,
                           Function<Material, TextureAtlasSprite> sprites) {
        UnbakedModel model = getModel(location);
        return model == null ? null : ModelFactory.bakeUncached(this, model, state, sprites, location);
    }

    @Override
    public Function<Material, TextureAtlasSprite> getModelTextureGetter() {
        return textureGetter;
    }
}
