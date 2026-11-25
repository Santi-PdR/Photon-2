package com.lowdragmc.photon.client.util;

import expr.Expr;
import expr.Parser;
import expr.SyntaxException;
import expr.Variable;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One author-typed formula ({@code y = f(t)} of an expression clip, a Function shape axis), parsed once per
 * distinct source and evaluated safely.
 *
 * <p>Three things the raw {@link Parser} use did not give: a source that fails to parse is remembered as failed
 * (the Function shape re-parsed a broken formula — and built a {@link SyntaxException} — for every particle it
 * spawned, while silently keeping the previous formula); identifiers that are not one of the documented variables
 * are a parse error instead of a fresh variable that reads 0 ({@code time * 2}, {@code Pi}); and a non-finite
 * result ({@code 1/t} at 0, {@code sqrt(-1)}) falls back instead of becoming a NaN position.</p>
 */
public final class CachedExpression {
    // Photon's logger by name: this class stays free of Minecraft (and of the mod main class) like the timeline core
    private static final Logger LOGGER = LoggerFactory.getLogger("Photon");
    /** {@code PI} as the tooltips document it; the parser itself only knows lowercase {@code pi}. */
    public static final Variable PI = Variable.make("PI");

    static {
        PI.setValue(Math.PI);
    }

    private final Variable[] allowed;
    @Nullable
    private String source;
    @Nullable
    private Expr expr;
    @Nullable
    private String error;
    private int parseAttempts;

    /** @param allowed the variables a formula may reference, besides {@code PI}/{@code pi}. */
    public CachedExpression(Variable... allowed) {
        this.allowed = allowed.clone();
    }

    /** A parser that accepts exactly {@code PI}, {@code pi} and {@code allowed}; any other identifier is an error. */
    public static Parser parser(Variable... allowed) {
        var parser = new Parser();
        parser.allow(PI);
        for (var variable : allowed) parser.allow(variable);
        return parser;
    }

    /** Why {@code source} is not a usable formula under {@link #parser}, or null when it is (blank counts as usable: no formula). */
    @Nullable
    public static String problem(String source, Variable... allowed) {
        if (source == null || source.isBlank()) return null;
        try {
            parser(allowed).parseString(source);
            return null;
        } catch (SyntaxException failure) {
            return describe(failure);
        }
    }

    /** Point this cache at {@code source}; parses only when it differs from the last source seen, valid or not. */
    public void update(@Nullable String source) {
        var text = source == null ? "" : source;
        if (text.equals(this.source)) return;
        this.source = text;
        parseAttempts++;
        expr = null;
        error = null;
        if (text.isBlank()) return;
        try {
            expr = parser(allowed).parseString(text);
        } catch (SyntaxException failure) {
            error = describe(failure);
            LOGGER.warn("photon: expression '{}' is not usable: {}", text, error);
        }
    }

    /** The compiled formula, or null when the source is blank or does not parse. */
    @Nullable
    public Expr expr() {
        return expr;
    }

    /** The parse problem of the current source, or null when it is blank or parses. */
    @Nullable
    public String error() {
        return error;
    }

    public boolean isValid() {
        return expr != null;
    }

    /** How many distinct sources were parsed so far (a broken source is parsed once, not once per use). */
    public int parseAttempts() {
        return parseAttempts;
    }

    /** The formula's value for the variables' current values, or {@code fallback} when there is no formula or it is not finite. */
    public double value(double fallback) {
        if (expr == null) return fallback;
        var value = expr.value();
        return Double.isFinite(value) ? value : fallback;
    }

    private static String describe(SyntaxException failure) {
        var message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
