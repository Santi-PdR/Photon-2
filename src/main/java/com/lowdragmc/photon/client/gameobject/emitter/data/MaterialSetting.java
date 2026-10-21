package com.lowdragmc.photon.client.gameobject.emitter.data;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.editor.resource.BuiltinPath;
import com.lowdragmc.lowdraglib.gui.editor.runtime.PersistedParser;
import com.lowdragmc.lowdraglib.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.BlendMode;
import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.PremultipliedBlendPlan;
import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.IMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import com.lowdragmc.photon.gui.editor.resource.MaterialResource;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import com.lowdragmc.lowdraglib.gui.editor.runtime.PersistedParser;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Optional;

@OnlyIn(Dist.CLIENT)
@Getter
@Setter
@Accessors(chain = true)
@EqualsAndHashCode
public class MaterialSetting implements IConfigurable, IPersistedSerializable {
    @Nonnull
    @Configurable(name = "material")
    @Persisted
    protected IMaterial material;
    @Configurable(name = "MaterialSetting.blendMode", subConfigurable = true)
    @Persisted(subPersisted = true)
    protected final BlendMode blendMode = new BlendMode();
    @Configurable
    @Persisted
    protected boolean cull = true;
    @Configurable
    @Persisted
    protected boolean depthTest = true;
    @Configurable
    @Persisted
    protected boolean depthMask = false;

    public MaterialSetting() {
        this(Optional.ofNullable(MaterialResource.INSTANCE.getResourceInstance().getResource(new BuiltinPath("circle"))).orElseGet(TextureMaterial::new));
    }

    public MaterialSetting(@Nonnull IMaterial material) {
        this.material = material;
    }

    /**
     * Persist the registry-dispatched material wrapper explicitly; the legacy LDLib parser cannot
     * reconstruct an {@link IMaterial} subtype from the generic field alone.
     */
    @Override
    public CompoundTag serializeNBT() {
        var tag = new CompoundTag();
        PersistedParser.serializeNBT(tag, BlendMode.class, blendMode);
        tag.putBoolean("cull", cull);
        tag.putBoolean("depthTest", depthTest);
        tag.putBoolean("depthMask", depthMask);
        var materialTag = material.serializeWrapper();
        if (materialTag == null) {
            throw new IllegalStateException("Failed to serialize Photon material " + material.getClass().getName());
        }
        tag.put("material", materialTag);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        if (tag.contains("blendMode", Tag.TAG_COMPOUND)) {
            PersistedParser.deserializeNBT(tag.getCompound("blendMode"), new HashMap<>(), BlendMode.class, blendMode);
        }
        if (tag.contains("cull", Tag.TAG_BYTE)) cull = tag.getBoolean("cull");
        if (tag.contains("depthTest", Tag.TAG_BYTE)) depthTest = tag.getBoolean("depthTest");
        if (tag.contains("depthMask", Tag.TAG_BYTE)) depthMask = tag.getBoolean("depthMask");
        var materialTag = tag.get("material");
        if (materialTag != null) {
            material = IMaterial.deserializeWrapper(materialTag);
        }
    }

    /**
     * Apply this material's GL state.
     *
     * <p>The blend half is asked of the pipeline rather than passed in: under a shader pack — and on
     * the {@code FXCompositeMode.LATE} path — Photon draws into a transparent premultiplied
     * accumulator, where the alpha channel has to carry coverage instead of whatever the author
     * wrote (see {@link PremultipliedBlendPlan}). Reading it from
     * {@link RenderPassPipeline#getCurrent()} — the same seam materials already use for scene
     * samplers — means a new renderer cannot forget to opt in and silently punch a hole in the
     * pack's translucent layer.
     */
    public void pre() {
        // ShaderInstance.apply() and UI RenderTypes both mutate these global states. A submitted
        // particle VBO can otherwise pass through the pipeline while every fragment is rejected or
        // colour writes are disabled.
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        var pipeline = RenderPassPipeline.getCurrent();
        if (pipeline != null && pipeline.isPremultipliedAccumulation()) {
            PremultipliedBlendPlan.applyPremultiplied(blendMode);
        } else {
            blendMode.apply();
        }
        if (cull) {
            RenderSystem.enableCull();
            GL11.glCullFace(GL11.GL_BACK);
        } else {
            RenderSystem.disableCull();
        }
        if (depthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        RenderSystem.depthMask(depthMask);
    }

    public void post() {
        if (blendMode.getBlendFunc() != BlendMode.BlendFuc.ADD) {
            RenderSystem.blendEquation(BlendMode.BlendFuc.ADD.op);
        }
        blendMode.reset();
        if (!cull) RenderSystem.enableCull();
        if (!depthTest) RenderSystem.enableDepthTest();
        if (!depthMask) RenderSystem.depthMask(true);
    }

}
