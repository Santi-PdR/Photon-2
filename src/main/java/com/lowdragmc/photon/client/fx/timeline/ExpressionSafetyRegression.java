package com.lowdragmc.photon.client.fx.timeline;

import com.lowdragmc.photon.client.fx.timeline.property.ConfigPropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigValueType;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Function;
import com.lowdragmc.photon.client.util.CachedExpression;
import expr.Variable;

/**
 * Author-typed formulas (expression clips, the Function shape) used to fail silently in three ways: a source
 * that did not parse was accepted by the inspector and the clip simply fell back to its curve (the Function
 * shape kept the previous formula, re-parsing the broken one for every particle it spawned); an identifier
 * that was not {@code t}/{@code PI}/{@code randomA..E} became a fresh variable reading 0 ({@code time * 2});
 * and a non-finite result ({@code 1/t} at 0) went straight into a particle position. Pins the shared
 * {@link CachedExpression} contract behind both, plus the field validators the inspector attaches.
 */
public final class ExpressionSafetyRegression {
    private ExpressionSafetyRegression() {}

    public static void verify() {
        verifyParseOncePerSource();
        verifyUnknownIdentifiersAreErrors();
        verifyNonFiniteValuesFallBack();
        verifyClipAndShapeValidators();
    }

    private static void verifyParseOncePerSource() {
        var t = Variable.make("t");
        var cache = new CachedExpression(t);
        cache.update("sin(t");
        cache.update("sin(t");
        cache.update("sin(t");
        if (cache.isValid() || cache.error() == null || cache.parseAttempts() != 1) {
            throw new IllegalStateException("Broken formula was not parsed exactly once and remembered as broken: attempts="
                    + cache.parseAttempts() + " error=" + cache.error());
        }
        cache.update("sin(t) + 1");
        t.setValue(0);
        if (!cache.isValid() || cache.error() != null || cache.parseAttempts() != 2 || Math.abs(cache.value(-1) - 1) > 1e-9) {
            throw new IllegalStateException("Repaired formula did not compile on the next distinct source: attempts="
                    + cache.parseAttempts() + " value=" + cache.value(-1));
        }
        cache.update("");
        if (cache.isValid() || cache.error() != null || cache.value(7) != 7) {
            throw new IllegalStateException("Blank formula must read as no formula (fallback), not as an error");
        }
    }

    private static void verifyUnknownIdentifiersAreErrors() {
        var t = Variable.make("t");
        if (CachedExpression.problem("time * 2", t) == null || CachedExpression.problem("Pi * t", t) == null
                || CachedExpression.problem("sin(t) + randomA", t) == null) {
            throw new IllegalStateException("Identifiers outside the documented variables were accepted (they read as 0)");
        }
        if (CachedExpression.problem("sin(t * PI) + pi + max(t, 1) + if(t > 1, 1, 0)", t) != null
                || CachedExpression.problem("   ", t) != null || CachedExpression.problem(null, t) != null) {
            throw new IllegalStateException("A formula using only t, PI/pi and the built-in functions was rejected: "
                    + CachedExpression.problem("sin(t * PI) + pi + max(t, 1) + if(t > 1, 1, 0)", t));
        }
        var cache = new CachedExpression(t);
        cache.update("PI");
        if (!cache.isValid() || Math.abs(cache.value(0) - Math.PI) > 1e-12) {
            throw new IllegalStateException("PI did not evaluate to pi: " + cache.value(0));
        }
    }

    private static void verifyNonFiniteValuesFallBack() {
        var t = Variable.make("t");
        var cache = new CachedExpression(t);
        cache.update("1 / t");
        t.setValue(0);
        if (cache.value(0.5) != 0.5) {
            throw new IllegalStateException("1/t at t=0 must fall back, got " + cache.value(0.5));
        }
        t.setValue(2);
        if (Math.abs(cache.value(0.5) - 0.5) > 1e-12 || Math.abs(cache.value(0) - 0.5) > 1e-12) {
            throw new IllegalStateException("1/t at t=2 must evaluate to 0.5, got " + cache.value(0));
        }
        cache.update("sqrt(-1)");
        if (cache.value(3) != 3) {
            throw new IllegalStateException("A NaN formula must fall back, got " + cache.value(3));
        }
        // the clip path: a broken or NaN formula yields no override, so the property samples its curve (base 4)
        float[] base = {4f};
        var type = new ConfigPropertyType("photon_probe.expr", ConfigValueType.FLOAT, "photon_probe.expr");
        var property = new AnimatedProperty(type, base, AnimatedProperty.seedChannels(base), 0, 10);
        var clip = new ExprClip(0, 100, "sqrt(-1)");
        property.addExprClip(0, clip);
        if (property.evalExpr(0, 5) != null || property.sampleChannelValue(0, 5) != 4f) {
            throw new IllegalStateException("NaN clip formula must fall back to the curve, got " + property.sampleChannelValue(0, 5));
        }
        clip.expression("time * 2");
        if (clip.compiled() != null || clip.error() == null || property.sampleChannelValue(0, 5) != 4f) {
            throw new IllegalStateException("Unknown identifier in a clip must be an error with curve fallback: " + clip.error());
        }
        clip.expression("t * 2");
        if (clip.error() != null || property.sampleChannelValue(0, 5) != 10f) {
            throw new IllegalStateException("t*2 at clip-local t=5 must read 10, got " + property.sampleChannelValue(0, 5));
        }
    }

    private static void verifyClipAndShapeValidators() {
        if (!ExprClip.isUsableFormula("sin(t * PI)") || ExprClip.isUsableFormula("sin(t") || ExprClip.isUsableFormula("randomA")
                || !ExprClip.isUsableFormula("")) {
            throw new IllegalStateException("Expression clip field validator does not match the clip's variables");
        }
        if (!Function.isUsableFormula("PI * (2 + t) + randomA / min(randomA, 0.5)") || Function.isUsableFormula("speed * t")
                || Function.isUsableFormula("t +") || !Function.isUsableFormula("0")) {
            throw new IllegalStateException("Function shape field validator does not match the shape's variables");
        }
    }
}
