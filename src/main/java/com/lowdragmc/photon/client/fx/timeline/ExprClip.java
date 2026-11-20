package com.lowdragmc.photon.client.fx.timeline;

import com.lowdragmc.photon.client.util.CachedExpression;
import expr.Expr;
import expr.Variable;

import javax.annotation.Nullable;

/**
 * An <b>expression clip</b> on a single {@link AnimatedProperty} channel's curve lane: a time window
 * {@code [start, end)} over which the channel value is produced by a {@code y = f(t)} expression instead
 * of the underlying keyframe curve. The expression's {@code t} is <b>clip-local</b> (time since the
 * clip's start, in ticks), so moving a clip preserves its shape. A parse/eval failure lets the caller
 * fall back to the curve.
 * <p>
 * Like {@link Clip}, this class is intentionally free of any {@code net.minecraft} dependency so the
 * timeline evaluation core stays unit-testable with plain JUnit. Serialization lives in
 * {@link AnimatedProperty}.
 */
public class ExprClip implements SubClip {
    /** The only variable a clip formula may use: clip-local {@code t} (plus {@code PI}/{@code pi}). */
    public static final Variable[] VARIABLES = {Variable.make("t")};

    private double start;
    private double duration;
    private String expression;
    // transient parse cache (rebuilt lazily on expression change; never serialized/copied). Parses once per
    // distinct source, rejects identifiers other than t/PI (they used to read as a silent 0) and remembers a
    // broken source as broken.
    private final transient CachedExpression compiled = new CachedExpression(VARIABLES);

    public ExprClip() {
        this(0.0, 1.0, "");
    }

    public ExprClip(double start, double duration, String expression) {
        this.start = start;
        this.duration = duration;
        this.expression = expression == null ? "" : expression;
    }

    public double start() {
        return start;
    }

    public ExprClip start(double start) {
        this.start = start;
        return this;
    }

    public double duration() {
        return duration;
    }

    public ExprClip duration(double duration) {
        this.duration = duration;
        return this;
    }

    /** Exclusive end time of this clip. */
    public double end() {
        return start + duration;
    }

    /** Half-open {@code [start, end)} so adjacent clips never both match a single time. */
    public boolean contains(double time) {
        return time >= start && time < end();
    }

    public String expression() {
        return expression;
    }

    public ExprClip expression(String expression) {
        this.expression = expression == null ? "" : expression;
        return this;
    }

    /** (Re)parse the expression when it changed, caching the {@link Expr} or the error message. Returns
     *  the compiled expression, or {@code null} when the source is empty or fails to parse. */
    @Nullable
    public Expr compiled() {
        compiled.update(expression);
        return compiled.expr();
    }

    /** The expression's syntax error (null if it is empty or parses). */
    @Nullable
    public String error() {
        compiled.update(expression);
        return compiled.error();
    }

    /** Whether {@code source} is a formula a clip can use (blank reads as "no formula"). */
    public static boolean isUsableFormula(String source) {
        return CachedExpression.problem(source, VARIABLES) == null;
    }

    /** An independent value copy of this clip (parse cache is not copied — it rebuilds lazily). */
    public ExprClip copy() {
        return new ExprClip(start, duration, expression);
    }
}
