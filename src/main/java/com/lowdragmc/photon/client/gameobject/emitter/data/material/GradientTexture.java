package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.syncdata.IProviderAwareNBTSerializable;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigList;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigSetter;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.math.GradientColor;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.color.GradientColorConfigurator;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.FastColor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class GradientTexture implements AutoCloseable, IConfigurable, IProviderAwareNBTSerializable<ListTag> {
    private final int width;
    private final int height;
    @Configurable(name = "GradientTexture.gradients", canCollapse = false, collapse = false)
    @ConfigList(configuratorMethod = "buildGradientConfigurator", addDefaultMethod = "addDefaultGradient")
    private final List<GradientColor> gradients = new ArrayList<>();
    // Upload once on first use, even when the gradient list is empty: shader samplers must never receive null.
    private boolean isDirty = true;
    @Nullable
    private DynamicTexture gradientTexture;

    public GradientTexture(int width, int height) {
        this.width = Math.max(width, 2);
        this.height = Math.max(height, 1);
    }

    public void markAsDirty() {
        isDirty = true;
    }

    public void addGradient(GradientColor gradient) {
        this.gradients.add(gradient);
        markAsDirty();
    }

    public void removeGradient(GradientColor gradient) {
        if (this.gradients.remove(gradient)) markAsDirty();
    }

    public DynamicTexture getGradientTexture() {
        uploadTexture();
        return gradientTexture;
    }

    @Override
    public void close() {
        if (gradientTexture != null) {
            gradientTexture.close();
            gradientTexture = null;
        }
        isDirty = true;
    }

    public void uploadTexture() {
        if (!isDirty && gradientTexture != null && gradientTexture.getPixels() != null) return;
        RenderSystem.assertOnRenderThread();
        if (gradientTexture == null || gradientTexture.getPixels() == null)  {
            this.gradientTexture = new DynamicTexture(width, height, false);
        }
        var pixels = gradientTexture.getPixels();
        assert pixels != null;
        for (int h = 0; h < height; h++) {
            var gradient = h < gradients.size() ? gradients.get(h) : null;
            if (gradient == null) {
                for (int w = 0; w < width; w++) pixels.setPixelRGBA(w, h, 0);
                continue;
            }
            for (int w = 0; w < width; w++) {
                int argb = gradient.getColor(w / (width - 1f));
                pixels.setPixelRGBA(w, h, FastColor.ABGR32.color(FastColor.ARGB32.alpha(argb),
                        FastColor.ARGB32.blue(argb), FastColor.ARGB32.green(argb), FastColor.ARGB32.red(argb)));
            }
        }
        this.gradientTexture.upload();
        isDirty = false;
    }

    private Configurator buildGradientConfigurator(Supplier<GradientColor> getter, Consumer<GradientColor> setter) {
        return new GradientColorConfigurator("", () -> getter.get().copy(), gradientColor -> {
            setter.accept(gradientColor);
            markAsDirty();
        }, addDefaultGradient(), true);
    }

    private GradientColor addDefaultGradient() {
        return new GradientColor();
    }

    @ConfigSetter(field = "gradients")
    private void setGradients(List<GradientColor> gradients) {
        if (gradients != this.gradients) {
            this.gradients.clear();
            if (gradients != null) this.gradients.addAll(gradients);
        }
        markAsDirty();
    }

    public ListTag serializeNBT(@Nonnull HolderLookup.Provider provider) {
        var listTag = new ListTag();
        for (var gradientColor : gradients) {
            listTag.add(gradientColor.serializeNBT(provider));
        }
        return listTag;
    }

    public void deserializeNBT(@Nonnull HolderLookup.Provider provider, @Nonnull ListTag listTag) {
        gradients.clear();
        for (Tag tag : listTag) {
            if (!(tag instanceof CompoundTag compound)) continue;
            var gradientColor = new GradientColor();
            gradientColor.deserializeNBT(provider, compound);
            gradients.add(gradientColor);
        }
        markAsDirty();
    }
}

