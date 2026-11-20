package com.lowdragmc.photon.client.gameobject.emitter.data.shape;

import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.utils.Vector3fHelper;
import com.lowdragmc.photon.client.gameobject.emitter.IParticleEmitter;
import com.lowdragmc.photon.client.gameobject.particle.TileParticle;
import com.lowdragmc.photon.client.util.CachedExpression;
import expr.Variable;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

@LDLRegisterClient(name = "function", registry = "photon:shape")
public class Function implements IShape {
    @Getter
    @Setter
    @Configurable(name = "Function.x", tips = {
            "photon.gui.editor.shape.function.tooltips.0",
            "photon.gui.editor.shape.function.tooltips.1",
            "photon.gui.editor.shape.function.tooltips.2",
            "photon.gui.editor.shape.function.tooltips.3"
    })
    private String x = "0";
    @Getter
    @Setter
    @Configurable(name = "Function.y", tips = {
            "photon.gui.editor.shape.function.tooltips.0",
            "photon.gui.editor.shape.function.tooltips.1",
            "photon.gui.editor.shape.function.tooltips.2",
            "photon.gui.editor.shape.function.tooltips.3"
    })
    private String y = "0";
    @Getter
    @Setter
    @Configurable(name = "Function.z", tips = {
            "photon.gui.editor.shape.function.tooltips.0",
            "photon.gui.editor.shape.function.tooltips.1",
            "photon.gui.editor.shape.function.tooltips.2",
            "photon.gui.editor.shape.function.tooltips.3"
    })
    private String z = "0";

    @Getter
    @Setter
    @Configurable(name = "Function.speedX", tips = {
            "photon.gui.editor.shape.function.tooltips.0",
            "photon.gui.editor.shape.function.tooltips.1",
            "photon.gui.editor.shape.function.tooltips.2",
            "photon.gui.editor.shape.function.tooltips.3"
    })
    private String speedX = "0";
    @Getter
    @Setter
    @Configurable(name = "Function.speedY", tips = {
            "photon.gui.editor.shape.function.tooltips.0",
            "photon.gui.editor.shape.function.tooltips.1",
            "photon.gui.editor.shape.function.tooltips.2",
            "photon.gui.editor.shape.function.tooltips.3"
    })
    private String speedY = "0";
    @Getter
    @Setter
    @Configurable(name = "Function.speedZ", tips = {
            "photon.gui.editor.shape.function.tooltips.0",
            "photon.gui.editor.shape.function.tooltips.1",
            "photon.gui.editor.shape.function.tooltips.2",
            "photon.gui.editor.shape.function.tooltips.3"
    })
    private String speedZ = "0";

    // runtime
    private static final Variable T = Variable.make("t");
    private static final Variable randomA = Variable.make("randomA");
    private static final Variable randomB = Variable.make("randomB");
    private static final Variable randomC = Variable.make("randomC");
    private static final Variable randomD = Variable.make("randomD");
    private static final Variable randomE = Variable.make("randomE");
    /** The identifiers a formula may use, as the tooltips document them (plus {@code PI}/{@code pi}). */
    public static final Variable[] VARIABLES = {T, randomA, randomB, randomC, randomD, randomE};
    // one cache per axis: a formula is parsed once per distinct source, a broken one is remembered as broken
    // (it used to be re-parsed, exception and all, for every particle spawned while the author was typing)
    private final CachedExpression xExpr = new CachedExpression(VARIABLES);
    private final CachedExpression yExpr = new CachedExpression(VARIABLES);
    private final CachedExpression zExpr = new CachedExpression(VARIABLES);
    private final CachedExpression sXExpr = new CachedExpression(VARIABLES);
    private final CachedExpression sYExpr = new CachedExpression(VARIABLES);
    private final CachedExpression sZExpr = new CachedExpression(VARIABLES);

    private void prepareExpr(IParticleEmitter emitter) {
        T.setValue(emitter.getT());

        randomA.setValue(emitter.getRandomSource().nextFloat());
        randomB.setValue(emitter.getRandomSource().nextFloat());
        randomC.setValue(emitter.getRandomSource().nextFloat());
        randomD.setValue(emitter.getRandomSource().nextFloat());
        randomE.setValue(emitter.getRandomSource().nextFloat());

        xExpr.update(x);
        yExpr.update(y);
        zExpr.update(z);
        sXExpr.update(speedX);
        sYExpr.update(speedY);
        sZExpr.update(speedZ);
    }

    /** Whether {@code source} is a formula this shape can use (blank reads as 0). */
    public static boolean isUsableFormula(String source) {
        return CachedExpression.problem(source, VARIABLES) == null;
    }

    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        int before = father.getConfigurators().size();
        IShape.super.buildConfigurator(father);
        // the six formula fields: a source that does not parse (or names an unknown variable) shows as invalid
        // and is not applied, instead of being accepted and silently reading as the previous formula or 0
        var all = father.getConfigurators();
        for (var configurator : all.subList(before, all.size())) {
            if (configurator instanceof StringConfigurator field) field.setTextValidator(Function::isUsableFormula);
        }
    }

    @Override
    public void nextPosVel(TileParticle particle, IParticleEmitter emitter, Vector3f position, Vector3f rotation, Vector3f scale) {
        prepareExpr(emitter);
        // a formula that is blank, broken or not finite here (1/t at 0, sqrt(-1)) reads as 0, never as NaN
        var pos = new Vector3f((float) xExpr.value(0), (float) yExpr.value(0), (float) zExpr.value(0));
        var speed = new Vector3f((float) sXExpr.value(0), (float) sYExpr.value(0), (float) sZExpr.value(0));
        particle.setSimPos(Vector3fHelper.rotateYXY(new Vector3f(pos), rotation).add(position).add(particle.getSimPosWithoutNoise()), true);
        particle.setInternalVelocity(Vector3fHelper.isZero(speed) ? speed : Vector3fHelper.rotateYXY(speed.normalize().mul(0.05f), rotation));
    }
}
