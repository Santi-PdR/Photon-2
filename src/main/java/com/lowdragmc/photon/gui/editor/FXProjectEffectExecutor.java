package com.lowdragmc.photon.gui.editor;

import com.lowdragmc.photon.client.fx.IEffectExecutor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.world.level.Level;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.SpawnChain;
import org.jetbrains.annotations.Nullable;
import net.minecraft.resources.ResourceLocation;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author KilaBash
 * @date 2023/7/17
 * @implNote EditorEffect
 */
@OnlyIn(Dist.CLIENT)
public class FXProjectEffectExecutor implements IEffectExecutor {
    @Getter
    public final Level level;
    @Getter @Setter
    private long seed = 0;
    @Setter
    private Supplier<FX> definitionSupplier = () -> null;
    @Setter
    private Supplier<String> diagnosticLabel = () -> "unsaved-editor-project";
    /** The ids the previewed project is known by (converted from, exported to), beyond its definition's location. */
    private Supplier<Collection<ResourceLocation>> knownAs = List::of;

    public void setDefinitionSupplier(Supplier<FX> definitionSupplier) {
        this.definitionSupplier = definitionSupplier == null ? () -> null : definitionSupplier;
    }

    public void setDiagnosticLabel(Supplier<String> diagnosticLabel) {
        this.diagnosticLabel = diagnosticLabel == null ? () -> "unsaved-editor-project" : diagnosticLabel;
    }

    public String diagnosticLabel() {
        return diagnosticLabel.get();
    }

    public void setKnownAs(Supplier<Collection<ResourceLocation>> knownAs) {
        this.knownAs = knownAs == null ? List::of : knownAs;
    }

    /**
     * The previewed project's ids: a sub-emitter in it that spawns one of them would spawn the project
     * inside itself, in the preview exactly as in the world once saved.
     */
    @Override
    public List<String> playingAs() {
        var ids = new LinkedHashSet<String>();
        var definition = definitionSupplier.get();
        if (definition != null && definition.getFxLocation() != null) ids.add(SpawnChain.id(definition.getFxLocation()));
        var known = knownAs.get();
        if (known != null) {
            for (var id : known) {
                if (id != null) ids.add(SpawnChain.id(id));
            }
        }
        return List.copyOf(ids);
    }
    @Getter
    public final RandomSource randomSource = RandomSource.create(seed);

    public FXProjectEffectExecutor(Level level) {
        this.level = level;
    }

    public void reset() {
        randomSource.setSeed(seed);
    }

    @Override
    @Nullable
    public FX legacySubEffect(String name) {
        var definition = definitionSupplier.get();
        return definition == null ? null : definition.getLegacySubFXs().get(name);
    }

    /** Editor previews feed the isolated scene stack — never the world's post-effect chain. */
    @Override
    public com.lowdragmc.photon.client.postfx.runtime.PostEffectStack postEffectSink() {
        return com.lowdragmc.photon.client.postfx.runtime.PostEffectStack.EDITOR_SCENE;
    }
}
