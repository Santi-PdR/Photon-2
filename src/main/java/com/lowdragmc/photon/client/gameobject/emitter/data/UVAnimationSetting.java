package com.lowdragmc.photon.client.gameobject.emitter.data;

import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.photon.client.gameobject.RuntimeValue;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.Constant;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunctionConfig;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.RandomConstant;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.CurveConfig;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.RandomCurve;
import com.lowdragmc.photon.client.gameobject.particle.IParticle;
import lombok.Getter;
import lombok.Setter;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector2i;
import org.joml.Vector4f;

/**
 * @author KilaBash
 * @date 2023/5/31
 * @implNote UVAnimation
 */
@OnlyIn(Dist.CLIENT)
public class UVAnimationSetting extends ToggleGroup {
    public enum Animation {
        WholeSheet,
        SingleRow,
    }

    @Setter
    @Getter
    @Configurable(name = "UVAnimationSetting.tiles", tips = "photon.emitter.config.uvAnimation.tiles")
    @ConfigNumber(range = {1, Integer.MAX_VALUE}, type = ConfigNumber.Type.INTEGER)
    protected Vector2i tiles = new Vector2i(1, 1);

    @Setter
    @Getter
    @Configurable(name = "UVAnimationSetting.animation", tips = "photon.emitter.config.uvAnimation.animation")
    protected Animation animation = Animation.WholeSheet;

    @Setter
    @Getter
    @Configurable(name = "UVAnimationSetting.frameOverTime", tips = "photon.emitter.config.uvAnimation.frameOverTime")
    @NumberFunctionConfig(types = {Constant.class, RandomConstant.class, Curve.class, RandomCurve.class}, min = 0, curveConfig = @CurveConfig(bound = {0, 4}, xAxis = "lifetime", yAxis = "frame over time"))
    protected NumberFunction frameOverTime = NumberFunction.constant(0);

    @Setter
    @Getter
    @Configurable(name = "UVAnimationSetting.startFrame", tips = "photon.emitter.config.uvAnimation.startFrame")
    @NumberFunctionConfig(types = {Constant.class, RandomConstant.class}, min = 0)
    protected NumberFunction startFrame = NumberFunction.constant(0);

    @Setter
    @Getter
    @Configurable(name = "UVAnimationSetting.cycle", tips = "photon.emitter.config.uvAnimation.cycle")
    @ConfigNumber(range = {0, Integer.MAX_VALUE}, wheel = 1)
    protected float cycle = 1;

    public Vector4f getUVs(IParticle particle, float partialTicks) {
        var t = particle.getT(partialTicks);
        var frame = startFrame.get(t, () -> particle.getMemRandom("startFrame")).floatValue()
                + cycle * frameOverTime.get(t, () -> particle.getMemRandom("frameOverTime")).floatValue();
        return calculateUVs(tiles, animation, frame, animation == Animation.SingleRow ? particle.getMemRandom("randomRow") : 0f);
    }

    /** Loaded data and timeline overrides can bypass the editor's tile-count limits. */
    public static Vector4f calculateUVs(Vector2i tiles, Animation animation, float frame, float randomRow) {
        int columns = tiles == null ? 1 : Math.max(1, tiles.x());
        int rows = tiles == null ? 1 : Math.max(1, tiles.y());
        float cellU = 1f / columns, cellV = 1f / rows;
        long index = Float.isFinite(frame) ? (long) frame : 0L;
        long column, row;
        if (animation == Animation.SingleRow) {
            column = Math.floorMod(index, (long) columns);
            row = Math.max(0, Math.min(rows - 1L, (long) (randomRow * rows)));
        } else {
            index = Math.floorMod(index, (long) columns * rows);
            column = index % columns;
            row = index / columns;
        }
        float u = column * cellU, v = row * cellV;
        return new Vector4f(u, v, u + cellU, v + cellV);
    }

    public Runtime createRuntime() {
        return new Runtime(this);
    }

    /** Per-emitter runtime layer for ParticleEmitter. The config keeps its own {@link #getUVs} for the
     *  Beam/Trail emitters (which have no runtime layer). */
    public static class Runtime {
        private final UVAnimationSetting config;
        public final RuntimeValue<Boolean> enable;
        public final RuntimeValue<Vector2i> tiles;        // slot only (Vector2i → no timeline binding)
        public final RuntimeValue<Animation> animation;   // slot only (enum → no timeline binding)
        public final RuntimeValue<NumberFunction> frameOverTime;
        public final RuntimeValue<NumberFunction> startFrame;
        public final RuntimeValue<Float> cycle;

        public Runtime(UVAnimationSetting config) {
            this.config = config;
            this.enable = new RuntimeValue<>(config::isEnable);
            this.tiles = new RuntimeValue<>(config::getTiles);
            this.animation = new RuntimeValue<>(config::getAnimation);
            this.frameOverTime = new RuntimeValue<>(config::getFrameOverTime);
            this.startFrame = new RuntimeValue<>(config::getStartFrame);
            this.cycle = new RuntimeValue<>(config::getCycle);
        }

        public boolean isEnable() {
            return enable.get();
        }

        public Vector4f getUVs(IParticle particle, float partialTicks) {
            var t = particle.getT(partialTicks);
            var frame = startFrame.get().get(t, () -> particle.getMemRandom("startFrame")).floatValue()
                    + cycle.get() * frameOverTime.get().get(t, () -> particle.getMemRandom("frameOverTime")).floatValue();
            var mode = animation.get();
            return calculateUVs(tiles.get(), mode, frame, mode == Animation.SingleRow ? particle.getMemRandom("randomRow") : 0f);
        }

        public void clear() {
            enable.clear();
            tiles.clear();
            animation.clear();
            frameOverTime.clear();
            startFrame.clear();
            cycle.clear();
        }
    }
}
