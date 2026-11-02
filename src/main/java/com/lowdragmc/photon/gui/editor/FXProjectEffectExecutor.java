package com.lowdragmc.photon.gui.editor;

import com.lowdragmc.photon.client.fx.IEffectExecutor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.world.level.Level;
import com.lowdragmc.photon.client.fx.FX;
import org.jetbrains.annotations.Nullable;
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
