package com.lowdragmc.photon.client.gameobject.emitter.data.material.kila;

import com.lowdragmc.lowdraglib2.gui.texture.DynamicTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.photon.Photon;
import net.minecraft.network.chat.Component;
import com.lowdragmc.photon.client.util.PhotonTextureCatalogue;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.function.BooleanSupplier;

/** A texture slot's picker: a click applies at once so the material preview shows it, a double click also closes. */
@OnlyIn(Dist.CLIENT)
public final class KilaTexturePicker {
    private static final int TILE = 40;
    private static final int MAX_RESOURCE_TILES = 128;
    private static final int RECENT_LIMIT = 8;
    private static final Deque<ResourceLocation> RECENT = new ArrayDeque<>();
    public static final List<ResourceLocation> PARTICLES = List.of(
            Photon.id("textures/particle/circle.png"), Photon.id("textures/particle/smoke.png"),
            Photon.id("textures/particle/ring.png"), Photon.id("textures/particle/laser.png"),
            Photon.id("textures/particle/kila_tail.png"), Photon.id("textures/particle/thaumcraft.png"));

    private KilaTexturePicker() {
    }

    static void remember(ResourceLocation location) {
        RECENT.remove(location);
        RECENT.addFirst(location);
        while (RECENT.size() > RECENT_LIMIT) RECENT.removeLast();
    }

    /** A starting scale that shows the noise's character. */
    static float defaultScale(KilaTexture.Noise noise) {
        if (noise == null) return 6;
        return switch (noise) {
            case SIMPLE -> 12;
            case GRADIENT -> 5;
            default -> 6;
        };
    }

    public static Dialog open(KilaTextureField field, float x, float y) {
        var texture = field.texture;
        var original = texture.getTexture();
        var originalNoise = texture.effectiveNoise();
        var originalScale = texture.noiseScale;
        if (!texture.isProcedural()) remember(original);

        var list = KilaDialogs.list();
        list.addClass("__kila-picker__");
        var dialog = new Dialog().windowMode(x, y, 236, 240);
        dialog.setTitle("kila.picker.title");

        if (field.procedural) {
            var noises = KilaDialogs.section(list, "kila.picker.procedural");
            for (var noise : KilaTexture.Noise.values()) {
                if (noise == KilaTexture.Noise.TEXTURE) continue;
                var candidate = new KilaTexture(KilaTextures.WHITE).noise(noise, defaultScale(noise));
                candidate.noiseMotion = 0;
                noises.addChild(tile(dialog, field, candidate,
                        Component.translatable("kila.texture.noise." + noise.name().toLowerCase(java.util.Locale.ROOT)),
                        () -> texture.effectiveNoise() == noise, () -> field.pick(noise)));
            }
        }
        if (!RECENT.isEmpty()) {
            var recent = KilaDialogs.section(list, "kila.picker.recent");
            for (var location : List.copyOf(RECENT)) {
                recent.addChild(textureTile(dialog, field, location));
            }
        }
        var builtin = KilaDialogs.section(list, "kila.picker.builtin");
        for (var location : KilaTextures.ALL) {
            builtin.addChild(textureTile(dialog, field, location));
        }
        var particles = KilaDialogs.section(list, "kila.picker.particles");
        for (var location : PARTICLES) {
            particles.addChild(textureTile(dialog, field, location));
        }

        // Resource ids retain the `textures/...png` path expected by Minecraft's texture manager.
        // Keep mounted/loose mod textures searchable rather than constructing an unbounded grid of
        // preview widgets for every texture in a large modpack. Builtins, particles and recents are
        // already visible above; searching adds at most MAX_RESOURCE_TILES more cells.
        var alreadyShown = new HashSet<ResourceLocation>();
        alreadyShown.addAll(KilaTextures.ALL);
        alreadyShown.addAll(PARTICLES);
        alreadyShown.addAll(RECENT);
        var resources = KilaDialogs.section(list, "kila.picker.resources");
        var search = new TextField();
        search.setId("kila_texture_picker_search");
        search.layout(layout -> layout.widthPercent(100).height(16));
        Runnable refillResources = () -> {
            resources.clearAllChildren();
            var query = search.getText().trim();
            if (query.isEmpty()) {
                resources.addChild(new Label().setText("kila.picker.search_hint"));
                return;
            }
            var found = PhotonTextureCatalogue.matching(query, MAX_RESOURCE_TILES + 1,
                    location -> !alreadyShown.contains(location));
            int shown = Math.min(found.size(), MAX_RESOURCE_TILES);
            for (int i = 0; i < shown; i++) {
                resources.addChild(textureTile(dialog, field, found.get(i)));
            }
            if (shown == 0) {
                resources.addChild(new Label().setText("kila.picker.search_empty"));
            } else if (found.size() > MAX_RESOURCE_TILES) {
                resources.addChild(new Label().setText(Component.translatable(
                        "kila.picker.search_limit", MAX_RESOURCE_TILES)));
            }
            if (PhotonTextureCatalogue.isCatalogueIncomplete()) {
                resources.addChild(new Label().setText("photon.texture_catalogue.incomplete"));
            }
        };
        search.setTextResponder(word -> refillResources.run());
        refillResources.run();

        dialog.addContent(new Label().setText("kila.picker.search"));
        dialog.addContent(search);
        dialog.addContent(list);
        dialog.addButton(new Button().setText("kila.texture.file").setOnClick(e -> {
            dialog.close();
            field.browseFile();
        }));
        dialog.addButton(new Button().setText("ldlib.gui.tips.confirm").setOnClick(e -> dialog.close())
                .addClass("__confirm-button__"));
        dialog.addButton(new Button().setText("ldlib.gui.tips.cancel").setOnClick(e -> {
            field.restore(original, originalNoise, originalScale);
            dialog.close();
        }).addClass("__cancel-button__"));
        dialog.show(field.getModularUI());
        return dialog;
    }

    private static UIElement textureTile(Dialog dialog, KilaTextureField field, ResourceLocation location) {
        var candidate = new KilaTexture(location);
        var texture = field.texture;
        return tile(dialog, field, candidate, KilaTextureField.displayName(location),
                () -> !texture.isProcedural() && location.equals(texture.getTexture()), () -> field.pick(location));
    }

    private static UIElement tile(Dialog dialog, KilaTextureField field, KilaTexture candidate, Component name,
                                  BooleanSupplier selected, Runnable pick) {
        candidate.channel = field.texture.effectiveChannel();
        candidate.colorMode = field.texture.effectiveColorMode();
        candidate.nearest = field.texture.nearest;
        boolean[] hovered = {false};
        long[] hoverStarted = {0L};
        KilaSlotPreview[] preview = {null};
        var previewWidget = KilaDialogs.fill(DynamicTexture.of(() -> {
            if (!hovered[0] || System.nanoTime() - hoverStarted[0] < 250_000_000L) return Icons.MATERIAL;
            if (preview[0] == null) preview[0] = new KilaSlotPreview(candidate, field.kind, false);
            return preview[0];
        }));
        var thumbnail = KilaDialogs.fill(KilaSlotPreview.checker()).addChild(previewWidget);
        var cell = KilaDialogs.cell(thumbnail, name, TILE, selected);
        previewWidget.addEventListener(UIEvents.MOUSE_ENTER, event -> {
            hovered[0] = true;
            hoverStarted[0] = System.nanoTime();
        });
        previewWidget.addEventListener(UIEvents.MOUSE_LEAVE, event -> hovered[0] = false);
        cell.addEventListener(UIEvents.MOUSE_ENTER, event -> {
            hovered[0] = true;
            hoverStarted[0] = System.nanoTime();
        });
        cell.addEventListener(UIEvents.MOUSE_LEAVE, event -> hovered[0] = false);
        cell.style(style -> style.tooltips(name));
        cell.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 0) pick.run();
        });
        cell.addEventListener(UIEvents.DOUBLE_CLICK, event -> dialog.close());
        return cell;
    }
}

