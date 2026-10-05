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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Loads and bakes standalone JSON models directly from the active resource packs. */
@OnlyIn(Dist.CLIENT)
final class PhotonModelBaker implements ModelBaker {
    private static final int MAX_PARENT_DEPTH = 64;
    private static final ResourceLocation GENERATED_MODEL = new ResourceLocation("minecraft", "builtin/generated");
    private static final ResourceLocation ENTITY_MODEL = new ResourceLocation("minecraft", "builtin/entity");

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
        var cached = models.get(location);
        if (cached != null) return cached;

        // Reuse models already loaded by Forge/LDLib, including vanilla's built-in generated and
        // block-entity parents. This also preserves custom unbaked model implementations.
        var bakeryCached = ModelFactory.getCachedModel(location);
        if (bakeryCached != null) return bakeryCached;

        if (location.equals(GENERATED_MODEL)) return ModelBakery.GENERATION_MARKER;
        if (location.equals(ENTITY_MODEL)) return ModelBakery.BLOCK_ENTITY_MARKER;
        return readStandalone(location, 0);
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
            var resource = Minecraft.getInstance().getResourceManager().getResource(file).orElse(null);
            if (resource == null) {
                return ModelFactory.getUnBakedModel(ModelBakery.MISSING_MODEL_LOCATION);
            }
            BlockModel model;
            try (var reader = resource.openAsReader()) {
                model = BlockModel.fromStream(reader);
            }
            model.name = location.toString();
            // Cache before resolving parents so a cycle terminates at the repeated model. The
            // vanilla resolver then logs and breaks the loop instead of recursing indefinitely.
            models.put(location, model);
            model.resolveParents(parent -> {
                if (parent.equals(GENERATED_MODEL)) return ModelBakery.GENERATION_MARKER;
                if (parent.equals(ENTITY_MODEL)) return ModelBakery.BLOCK_ENTITY_MARKER;
                var existing = ModelFactory.getCachedModel(parent);
                return existing != null ? existing : readStandalone(parent, depth + 1);
            });
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
