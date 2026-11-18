package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import com.lowdragmc.lowdraglib2.client.shader.LDProgramDefineManager;
import com.lowdragmc.lowdraglib2.client.shader.LDShaderInstance;
import com.lowdragmc.lowdraglib2.configurator.ConfiguratorParser;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigHDR;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.math.HDRColor;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.PhotonShaders;
import com.lowdragmc.photon.client.fx.PreloadContext;
import com.lowdragmc.photon.client.gameobject.emitter.data.ToggleGroup;
import com.lowdragmc.photon.client.util.PhotonAssetPath;
import com.lowdragmc.photon.client.util.PhotonPaths;
import com.lowdragmc.photon.client.util.PhotonTextureCatalogue;
import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.vfyjxf.taffy.style.AlignItems;
import lombok.Getter;
import lombok.Setter;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
@ParametersAreNonnullByDefault
@LDLRegisterClient(name = "texture", registry = "photon:material")
@Setter
@Getter
public class TextureMaterial extends ShaderInstanceMaterial {
    public enum HDRMode {
        ADDITIVE(0),
        MULTIPLICATIVE(1);
        public final int mode;

        HDRMode(int mode) {
            this.mode = mode;
        }
    }

    public static class PixelArt extends ToggleGroup {
        @Configurable
        @ConfigNumber(range = {1, 4096})
        public int bits = 8;
    }

    @Configurable(name = "TextureMaterial.texture")
    protected ResourceLocation texture = Photon.id("textures/particle/circle.png");
    @Configurable(name = "TextureMaterial.discardThreshold")
    @ConfigNumber(range = {0, 1})
    protected float discardThreshold = 0.1f;
    @Configurable(name = "TextureMaterial.hdr")
    @ConfigHDR
    protected HDRColor hdr = HDRColor.black();
    @Configurable(name = "TextureMaterial.hdrMode")
    protected HDRMode hdrMode = HDRMode.ADDITIVE;
    @Configurable(name = "TextureMaterial.pixelArt", subConfigurable = true)
    protected final PixelArt pixelArt = new PixelArt();
    @Configurable(name = "TextureMaterial.softParticles", subConfigurable = true)
    protected final SoftParticles softParticles = new SoftParticles();
    @Configurable(name = "TextureMaterial.litParticles", subConfigurable = true)
    protected final LitParticles litParticles = new LitParticles();
    // runtime
    private static final int MAX_TEXTURE_PICKER_ROWS = 128;
    private static final Map<String, ShaderInstance> hdrParticleShaders = new HashMap<>();
    private static final Map<String, ShaderInstance> pixelHDRParticleShaders = new HashMap<>();

    /**
     * Resource reload replaces the base programs. Variants are separate GL programs and must not
     * survive that reload: using one would retain stale shader source and GL handles, and keeping
     * it in the maps pins the old programs for the lifetime of the client.
     */
    public static void clearShaderVariants() {
        closeVariants(hdrParticleShaders);
        closeVariants(pixelHDRParticleShaders);
    }

    private static void closeVariants(Map<String, ShaderInstance> variants) {
        for (var shader : variants.values()) {
            try {
                shader.close();
            } catch (RuntimeException failure) {
                Photon.LOGGER.warn("Failed to dispose a cached particle shader variant", failure);
            }
        }
        variants.clear();
    }

    public TextureMaterial() {
    }

    public TextureMaterial(ResourceLocation texture) {
        this.texture = texture;
    }

    public ResourceLocation getTexture() {
        return texture == null ? Photon.id("textures/particle/circle.png") : texture;
    }

    public HDRColor getHdr() {
        return hdr == null ? HDRColor.black() : hdr;
    }

    public HDRMode getHdrMode() {
        return hdrMode == null ? HDRMode.ADDITIVE : hdrMode;
    }

    @Override
    public IMaterial copy() {
        var mat = new TextureMaterial(getTexture());
        mat.discardThreshold = discardThreshold;
        mat.hdr = getHdr().copy(); // deep: HDRColor is mutable, copies must not share it
        mat.hdrMode = getHdrMode();
        mat.pixelArt.setEnable(pixelArt.isEnable());
        mat.pixelArt.bits = pixelArt.bits;
        mat.softParticles.copyFrom(softParticles);
        mat.litParticles.copyFrom(litParticles);
        return mat;
    }

    @Override
    public ShaderInstance getShader(MaterialContext context) {
        // The whole define SET matters, not just the path: PHOTON_TANGENT changes the mesh attribute
        // layout, and every material on a pass shares one VAO — compiling this one without it would
        // read brightness out of the tangent slot.
        var defines = context.getShaderDefines();
        if (defines.isEmpty()) {
            return pixelArt.isEnable() ? PhotonShaders.getPixelHDRParticleShader() : PhotonShaders.getHDRParticleShader();
        } else {
            if (pixelArt.isEnable()) {
                return pixelHDRParticleShaders.computeIfAbsent(context.getVariantKey(), key -> {
                    // remove cache
                    Program.Type.FRAGMENT.getPrograms().remove(PhotonShaders.getPixelHDRParticleShader().getFragmentProgram().getName());
                    Program.Type.VERTEX.getPrograms().remove(PhotonShaders.getPixelHDRParticleShader().getVertexProgram().getName());
                    defines.forEach(LDProgramDefineManager::addProgramDefine);
                    try {
                        return LDShaderInstance.create(Photon.id("pixel_hdr_particle"), DefaultVertexFormat.BLOCK);
                    } catch (Throwable e) {
                        Photon.LOGGER.error("Failed to create pixel HDR particle shader", e);
                        throw new RuntimeException(e);
                    } finally {
                        // finally, not after the try: a throw used to leak the define into every later compile
                        defines.forEach(LDProgramDefineManager::removeProgramDefine);
                    }
                });
            } else {
                return hdrParticleShaders.computeIfAbsent(context.getVariantKey(), key -> {
                    // remove cache
                    Program.Type.FRAGMENT.getPrograms().remove(PhotonShaders.getHDRParticleShader().getFragmentProgram().getName());
                    Program.Type.VERTEX.getPrograms().remove(PhotonShaders.getHDRParticleShader().getVertexProgram().getName());
                    defines.forEach(LDProgramDefineManager::addProgramDefine);
                    try {
                        return LDShaderInstance.create(Photon.id("hdr_particle"), DefaultVertexFormat.BLOCK);
                    } catch (Throwable e) {
                        Photon.LOGGER.error("Failed to create HDR particle shader", e);
                        throw new RuntimeException(e);
                    } finally {
                        defines.forEach(LDProgramDefineManager::removeProgramDefine);
                    }
                });
            }
        }
    }

    @Override
    public void preload(MaterialContext context, PreloadContext preload) {
        // Report synchronously: diagnostic collectors intentionally do not run render jobs, but still
        // need the material's texture id in their answer. The real preload sinks resolve it before decode.
        preload.texture(getTexture());
        super.preload(context, preload);
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0f;
    }

    @Override
    public void setupUniform(MaterialContext context) {
        // the id the effect names may not be served by any pack (the author's folder was never registered): bind
        // the copy the workspace adopted, so the particles draw their image instead of the missing checkerboard
        RenderSystem.setShaderTexture(0, com.lowdragmc.photon.client.fx.LooseTextures.resolveForRender(getTexture()));
        var shader = getShader(context);
        float threshold = Float.isFinite(discardThreshold)
                ? net.minecraft.util.Mth.clamp(discardThreshold, 0f, 1f) : 0.1f;
        shader.safeGetUniform("DiscardThreshold").set(threshold);
        // the preview thumbnail drops the intensity so a bright emissive material doesn't blow it out
        var safeHdr = getHdr();
        var color = context.isRenderingPreview() ? safeHdr.withIntensity(1f).toVector4fOpaque() : safeHdr.toVector4fOpaque();
        shader.safeGetUniform("HDR").set(finite(color.x), finite(color.y), finite(color.z), finite(color.w));
        shader.safeGetUniform("HDRMode").set(getHdrMode().mode);
        if (pixelArt.isEnable()) {
            shader.safeGetUniform("Bits").set(net.minecraft.util.Mth.clamp(pixelArt.bits, 1, 4096) * 1f);
        }
        softParticles.apply(shader, context);
        litParticles.apply(shader, context);
    }

    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        createPreview(father);

        var configurator = new Configurator();
        father.addConfigurators(configurator
                .addInlineChild(
                        // button to select an image
                        new Button().setText("ldlib.gui.editor.tips.select_image").setOnClick(e -> {
                            var mui = e.currentElement.getModularUI();
                            if (mui == null) return;
                            // opens where the player's content actually is: the workspace, but a folder the
                            // author keeps elsewhere can be browsed to, and the picked file is copied in
                            Dialog.showFileDialog("ldlib.gui.editor.tips.select_image", PhotonPaths.getDataDir(), true, Dialog.suffixFilter(".png"), r -> {
                                if (r == null || !r.isFile()) return;
                                var location = getTextureFromFile(r);
                                if (location == null) {
                                    // silence here read as "the button does nothing": say why the pick was refused
                                    var player = net.minecraft.client.Minecraft.getInstance().player;
                                    if (player != null) player.displayClientMessage(
                                            net.minecraft.network.chat.Component.literal("Photon: " + r.getName()
                                                    + " is not a readable PNG under an assets/<namespace>/ folder; "
                                                    + "put a valid image under photon/assets/<namespace>/textures/... and pick it again"),
                                            false);
                                    com.lowdragmc.photon.Photon.LOGGER.warn("photon: refused texture {} ({})", r, location);
                                    return;
                                }
                                texture = location;
                                configurator.notifyChanges();
                                // a picked file from outside the packs is adopted at once, so the preview and the
                                // next draw both see it without waiting for a reload
                                com.lowdragmc.photon.client.fx.LooseTextures.resolve(location);
                            }).show(mui.ui.rootElement);
                        }).layout(layout -> layout.alignSelf(AlignItems.CENTER))
                )
                .addInlineChild(
                        new Button().setText("photon.material.pick_loaded_texture").setOnClick(e -> {
                            var mui = e.currentElement.getModularUI();
                            if (mui == null) return;
                            openLoadedTexturePicker(mui.ui.rootElement, location -> {
                                texture = location;
                                configurator.notifyChanges();
                                com.lowdragmc.photon.client.fx.LooseTextures.resolve(location);
                            });
                        }).layout(layout -> layout.alignSelf(AlignItems.CENTER))
                ));
        ConfiguratorParser.createConfigurators(father, this);
    }

    /**
     * Textures already in the resource manager (Photon, other mods, mounted FX packs) so an author
     * can reuse one another effect already draws, without copying the file into the workspace.
     */
    private static void openLoadedTexturePicker(com.lowdragmc.lowdraglib2.gui.ui.UIElement host,
                                                Consumer<ResourceLocation> onPick) {
        var dialog = new Dialog().windowMode(
                host.getPositionX() + 20, host.getPositionY() + 20, 280, 260);
        dialog.setTitle("photon.material.pick_loaded_texture");
        var list = new ScrollerView();
        list.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL));
        list.layout(layout -> {
            layout.widthPercent(100);
            layout.flex(1);
        });
        var filter = new TextField();
        filter.layout(layout -> layout.widthPercent(100).height(14));
        Runnable refill = () -> {
            list.clearAllScrollViewChildren();
            var query = filter.getText().trim();
            if (query.isEmpty()) {
                // Building the catalogue walks loaded packs and loose asset roots; don't do that just to open the dialog.
                list.addScrollViewChild(new Label().setText("kila.picker.search_hint"));
                return;
            }
            var found = PhotonTextureCatalogue.matching(query, MAX_TEXTURE_PICKER_ROWS + 1);
            if (found.isEmpty()) {
                list.addScrollViewChild(new Label().setText("photon.material.pick_loaded_texture.empty"));
                if (PhotonTextureCatalogue.isCatalogueIncomplete()) {
                    list.addScrollViewChild(new Label().setText("photon.texture_catalogue.incomplete"));
                }
                return;
            }
            int shown = Math.min(found.size(), MAX_TEXTURE_PICKER_ROWS);
            for (int i = 0; i < shown; i++) {
                var location = found.get(i);
                var row = new Label().setText(Component.literal(location.toString()));
                row.layout(layout -> layout.widthPercent(100));
                row.addEventListener(UIEvents.MOUSE_DOWN, event -> {
                    if (event.button == 0) {
                        onPick.accept(location);
                        dialog.close();
                    }
                });
                list.addScrollViewChild(row);
            }
            if (found.size() > MAX_TEXTURE_PICKER_ROWS) {
                list.addScrollViewChild(new Label().setText(Component.translatable(
                        "photon.material.pick_loaded_texture.limit", MAX_TEXTURE_PICKER_ROWS)));
            }
            if (PhotonTextureCatalogue.isCatalogueIncomplete()) {
                list.addScrollViewChild(new Label().setText("photon.texture_catalogue.incomplete"));
            }
        };
        filter.setTextResponder(word -> refill.run());
        refill.run();
        dialog.addContent(filter);
        dialog.addContent(list);
        dialog.addButton(new Button().setOnClick(e -> dialog.close())
                .setText("ldlib.gui.tips.confirm").addClass("__confirm-button__"));
        dialog.show(host);
    }

    public @Nullable ResourceLocation getTextureFromFile(File filePath) {
        var location = PhotonAssetPath.fromFile(PhotonPaths.getAssetsDir().toPath(), filePath.toPath());
        return location == null ? null
                : com.lowdragmc.photon.client.fx.LooseTextures.adoptTextureFile(location, filePath.toPath());
    }
}

