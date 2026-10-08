package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.gui.texture.DynamicTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import com.lowdragmc.lowdraglib2.registry.ILDLRegisterClient;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib.syncdata.IPersistedSerializable;
import com.lowdragmc.photon.util.RegistryAwareNBTSerializable;
import com.lowdragmc.photon.util.PersistedCodec;
import com.lowdragmc.photon.PhotonRegistries;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.vfyjxf.taffy.style.AlignItems;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.function.Supplier;

/**
 * @author KilaBash
 * @date 2023/5/29
 * @implNote Material
 */
@OnlyIn(Dist.CLIENT)
@ParametersAreNonnullByDefault
public interface IMaterial extends IConfigurable, IPersistedSerializable,
        RegistryAwareNBTSerializable<CompoundTag>, ILDLRegisterClient<IMaterial, Supplier<IMaterial>> {
    // region builtin material
    @LDLRegisterClient(name = "missing", registry = "photon:material", manual = true)
    final class MissingMaterial implements IMaterial {
        @Override
        public ShaderInstance begin(MaterialContext context) {
            RenderSystem.setShaderTexture(0, MissingTextureAtlasSprite.getTexture().getId());
            return GameRenderer.getRendertypeSolidShader();
        }

        @Override
        public IGuiTexture preview() {
            return IGuiTexture.MISSING_TEXTURE;
        }
    };
    MissingMaterial MISSING = new MissingMaterial();
    // endregion

    Codec<IMaterial> CODEC = PhotonRegistries.MATERIALS.optionalCodec().dispatch(ILDLRegisterClient::getRegistryHolderOptional,
            optional -> optional.map(holder -> PersistedCodec.createCodec(holder.value()).fieldOf("data").codec())
                    .orElseGet(() -> MapCodec.<IMaterial>unit(MISSING).codec()));

    @Nullable
    default CompoundTag serializeWrapper() {
        return (CompoundTag) CODEC.encodeStart(NbtOps.INSTANCE, this).result().orElse(null);
    }

    static IMaterial deserializeWrapper(Tag tag) {
        return CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(MISSING);
    }

    ShaderInstance begin(MaterialContext context);

    default Tag serializeAdditionalNBT(HolderLookup.Provider provider) {
        return new CompoundTag();
    }

    default void deserializeAdditionalNBT(Tag tag, HolderLookup.Provider provider) {
    }

    @Override
    default CompoundTag serializeNBT(HolderLookup.Provider provider) {
        var tag = IPersistedSerializable.super.serializeNBT();
        var additional = serializeAdditionalNBT(provider);
        if (additional != null && !(additional instanceof CompoundTag compound && compound.isEmpty())
                && !(additional instanceof net.minecraft.nbt.ListTag list && list.isEmpty())) {
            tag.put("_additional", additional);
        }
        return tag;
    }

    @Override
    default void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        IPersistedSerializable.super.deserializeNBT(tag);
        deserializeAdditionalNBT(tag.get("_additional"), provider);
    }

    IGuiTexture preview();

    /** Inspector preview rendered at its current size; materials may provide a dedicated live view. */
    default IGuiTexture previewLive() {
        return preview();
    }

    default void end(MaterialContext context) {

    }

    default IMaterial copy() {
        return CODEC.encodeStart(NbtOps.INSTANCE, this).result()
                .flatMap(tag -> CODEC.parse(NbtOps.INSTANCE, tag).result())
                .orElse(MISSING);
    }

    default void createPreview(ConfiguratorGroup father) {
        father.addConfigurators(new Configurator("ldlib.gui.editor.group.preview")
                .addChild(new UIElement().layout(layout -> {
                            layout.setAspectRatio(1.0f);
                            layout.widthPercent(80);
                            layout.alignSelf(AlignItems.CENTER);
                            layout.paddingAll(3);
                        }).addClass("preview_bg").style(style -> style.backgroundTexture(Sprites.BORDER1_RT1))
                        .moveInlineAsDefault()
                        .addChild(new UIElement().layout(layout -> {
                            layout.widthPercent(100);
                            layout.heightPercent(100);
                        }).style(style -> style.backgroundTexture(DynamicTexture.of(this::previewLive))))));
    }

    @Override
    default void buildConfigurator(ConfiguratorGroup father) {
        createPreview(father);
        IConfigurable.super.buildConfigurator(father);
    }
}
