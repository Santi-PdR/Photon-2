package com.lowdragmc.photon.client.util;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.photon.Photon;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Why a file picked for a texture or model slot was refused, as something the author can act on.
 *
 * <p>Every "pick a file" dialog maps the file to a resource id with {@link PhotonAssetPath#fromFile}. That is null
 * for two very different reasons: the file is not inside any {@code assets/<namespace>/} folder, or it is but its
 * name carries characters a resource id cannot ({@code Fire.png}, {@code mi textura.png}, {@code humo_añil.png}).
 * Before, both ended as "the button does nothing" (or a chat line claiming the file is not under assets, which for
 * the second case is false). This names the right one and, for a bad name, says what to rename the file to.</p>
 */
public record AssetPickRejection(Kind kind, Path file, @Nullable String rawId, @Nullable String suggestedId) {
    // suggestedId: the rename that fixes an INVALID_NAME, or the folder a WRONG_FOLDER file belongs in; null otherwise.
    public enum Kind { NOT_UNDER_ASSETS, INVALID_NAME, RESERVED_NAMESPACE, WRONG_FOLDER, UNREADABLE }

    /** The rejection {@link PhotonAssetPath#fromFile} implies for this file, or null when it is accepted. */
    @Nullable
    public static AssetPickRejection of(Path assetsRoot, Path file) {
        var raw = PhotonAssetPath.rawId(assetsRoot, file);
        if (raw == null) return new AssetPickRejection(Kind.NOT_UNDER_ASSETS, file, null, null);
        if (ResourceLocation.tryParse(raw) != null) return null;
        return new AssetPickRejection(Kind.INVALID_NAME, file, raw, PhotonAssetPath.suggestedId(raw));
    }

    /** The explanation, translated: what was refused and what to do about it. */
    public Component message() {
        var name = file.getFileName() == null ? file.toString() : file.getFileName().toString();
        return switch (kind) {
            case NOT_UNDER_ASSETS -> Component.translatable("photon.asset_pick.not_under_assets", name);
            case RESERVED_NAMESPACE -> Component.translatable("photon.asset_pick.reserved_namespace", name,
                    rawId == null ? "" : rawId.substring(0, Math.max(0, rawId.indexOf(':'))));
            case WRONG_FOLDER -> Component.translatable("photon.asset_pick.wrong_folder", name, suggestedId == null ? "" : suggestedId);
            case UNREADABLE -> Component.translatable("photon.asset_pick.unreadable", name);
            case INVALID_NAME -> suggestedId == null
                    ? Component.translatable("photon.asset_pick.invalid_name", name)
                    : Component.translatable("photon.asset_pick.invalid_name.rename", name, suggestedId);
        };
    }

    /** Pops the explanation over {@code host} (a small confirm-only dialog) and logs it. */
    public void show(@Nullable UIElement host) {
        Photon.LOGGER.warn("photon: refused picked asset {} ({}{})", file, kind,
                rawId == null ? "" : ", id would be " + rawId);
        if (host == null) return;
        var dialog = new Dialog().setTitle("photon.asset_pick.rejected");
        var label = new Label().setText(message());
        label.textStyle(style -> style.textWrap(TextWrap.WRAP).adaptiveHeight(true));
        label.layout(layout -> layout.widthPercent(100));
        dialog.addContent(label);
        dialog.addButton(new Button().setOnClick(e -> dialog.close())
                .setText("ldlib.gui.tips.confirm").addClass("__confirm-button__"));
        dialog.show(host);
    }

    /**
     * Why a picker ended with {@code location} (what it mapped the file to) null: {@link #of}'s reasons first; when
     * the mapping itself accepted the file, adoption refused it — a game-owned namespace is never copied into the
     * workspace, otherwise the PNG did not decode or the copy failed (details in the log). Null when accepted.
     */
    @Nullable
    public static AssetPickRejection explain(@Nullable ResourceLocation location, Path assetsRoot, Path file) {
        if (location != null) return null;
        var rejection = of(assetsRoot, file);
        if (rejection != null) return rejection;
        var raw = PhotonAssetPath.rawId(assetsRoot, file);
        var id = raw == null ? null : ResourceLocation.tryParse(raw);
        var reserved = id != null && com.lowdragmc.photon.client.fx.LooseTextures.isBuiltinNamespace(id.getNamespace());
        return new AssetPickRejection(reserved ? Kind.RESERVED_NAMESPACE : Kind.UNREADABLE, file, raw, null);
    }

    /** {@link #explain} + {@link #show} for a picker callback: false (and the explanation shown) when refused. */
    public static boolean accepted(@Nullable ResourceLocation location, Path assetsRoot, Path file, @Nullable UIElement host) {
        var rejection = explain(location, assetsRoot, file);
        if (rejection == null) return true;
        rejection.show(host);
        return false;
    }

    /**
     * Like {@link #explain} for pickers that also demand a folder inside the namespace (shader JSONs under
     * {@code shaders/core/}, model JSONs under {@code models/}): a well-named file in the wrong place is reported
     * as that, with the expected folder, instead of as unreadable.
     */
    @Nullable
    public static AssetPickRejection explainUnder(@Nullable ResourceLocation location, Path assetsRoot, Path file, String folder) {
        if (location != null) return null;
        var rejection = of(assetsRoot, file);
        if (rejection != null) return rejection;
        var raw = PhotonAssetPath.rawId(assetsRoot, file);
        var id = raw == null ? null : ResourceLocation.tryParse(raw);
        if (id != null && !id.getPath().contains(folder)) {
            return new AssetPickRejection(Kind.WRONG_FOLDER, file, raw, "assets/" + id.getNamespace() + "/" + folder);
        }
        return explain(null, assetsRoot, file);
    }

    /** {@link #explainUnder} + {@link #show}: false (and the explanation shown) when refused. */
    public static boolean acceptedUnder(@Nullable ResourceLocation location, Path assetsRoot, Path file, String folder,
                                        @Nullable UIElement host) {
        var rejection = explainUnder(location, assetsRoot, file, folder);
        if (rejection == null) return true;
        rejection.show(host);
        return false;
    }
}
