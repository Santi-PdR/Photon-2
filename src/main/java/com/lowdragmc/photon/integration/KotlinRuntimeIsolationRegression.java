package com.lowdragmc.photon.integration;

import com.lowdragmc.photon.Photon;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;

/**
 * Photon ships LDLib2 by Jar-in-Jar, and LDLib2 is partly Kotlin. The upstream LDLib2 jar nests a plain
 * {@code kotlin-stdlib}, which the loader turns into a JPMS module named {@code kotlin.stdlib} exporting {@code kotlin.*};
 * Kotlin for Forge exports the same packages from its own module, and two modules exporting one package is a split
 * package: the game refused to start on any instance with a Kotlin mod ("Modules thedarkcolour.kotlinforforge and
 * kotlin.stdlib export package kotlin.contracts to module …"). The build therefore embeds {@code libs/ldlib2-2.2.39.a.jar},
 * whose Kotlin classes link against a runtime relocated under {@code com.lowdragmc.photon.internal.embedded} (see
 * {@code libs/upstream/README.md}). The dev run resolves LDLib2 through Gradle and the distributable through the JarJar
 * version pin, and the two used to differ — dev relocated, distributable upstream — without anything noticing. This pins,
 * from inside the running game, that the LDLib2 classes in use link only against the relocated runtime and that the
 * runtime loads; CI checks the packaged jar for the same invariant. Runs inside the client smoke test.
 */
public final class KotlinRuntimeIsolationRegression {
    public static final String RELOCATED_PREFIX = "com.lowdragmc.photon.internal.embedded.";
    /** A Kotlin class of LDLib2 whose constructor and accessors take Kotlin function types. */
    static final String LDLIB2_KOTLIN_CLASS = "com.lowdragmc.lowdraglib2.gui.ui.Element";
    static final String RELOCATED_UNIT = RELOCATED_PREFIX + "kotlin.Unit";
    static final String LOOSE_UNIT = "kotlin.Unit";

    private KotlinRuntimeIsolationRegression() {
    }

    public static void verify() {
        ClassLoader loader = KotlinRuntimeIsolationRegression.class.getClassLoader();
        Class<?> element = load(LDLIB2_KOTLIN_CLASS, loader, "LDLib2's Kotlin UI class is not loadable");
        Set<String> relocated = new TreeSet<>();
        Set<String> loose = new TreeSet<>();
        try {
            for (Constructor<?> constructor : element.getDeclaredConstructors()) {
                sort(constructor.getParameterTypes(), relocated, loose);
            }
            for (Method method : element.getDeclaredMethods()) {
                sort(method.getParameterTypes(), relocated, loose);
                sort(new Class<?>[]{method.getReturnType()}, relocated, loose);
            }
        } catch (NoClassDefFoundError e) {
            throw new IllegalStateException("a Kotlin type used by " + LDLIB2_KOTLIN_CLASS + " is missing at runtime: " + e.getMessage(), e);
        }
        check(!relocated.isEmpty(), LDLIB2_KOTLIN_CLASS + " links against no relocated Kotlin type: the LDLib2 in use is not libs/ldlib2-2.2.39.a.jar");
        check(loose.isEmpty(), LDLIB2_KOTLIN_CLASS + " links against a loose Kotlin runtime (kotlin.stdlib module, split package with Kotlin for Forge): " + loose);
        Class<?> unit = load(RELOCATED_UNIT, loader, "the relocated Kotlin runtime is not loadable");
        String module = unit.getModule().isNamed() ? unit.getModule().getName() : "unnamed module";
        boolean looseVisible;
        try {
            Class.forName(LOOSE_UNIT, false, loader);
            looseVisible = true;
        } catch (ClassNotFoundException | LinkageError e) {
            looseVisible = false;
        }
        Photon.LOGGER.info("[kotlin-runtime-isolation-smoke] LDLib2's Kotlin classes link only against the relocated runtime under com.lowdragmc.photon.internal.embedded and it loads (module {}; {} relocated types seen; a loose kotlin.* runtime is {} on this instance)",
                module, relocated.size(), looseVisible ? "also present" : "absent");
    }

    private static Class<?> load(String name, ClassLoader loader, String failure) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            throw new IllegalStateException(failure + ": " + name, e);
        }
    }

    private static void sort(Class<?>[] types, Set<String> relocated, Set<String> loose) {
        for (Class<?> type : types) {
            while (type.isArray()) {
                type = type.getComponentType();
            }
            String name = type.getName();
            if (name.startsWith(RELOCATED_PREFIX + "kotlin.") || name.startsWith(RELOCATED_PREFIX + "kotlinx.")) {
                relocated.add(name);
            } else if (name.startsWith("kotlin.") || name.startsWith("kotlinx.")) {
                loose.add(name);
            }
        }
    }

    private static void check(boolean condition, String failure) {
        if (!condition) {
            throw new IllegalStateException(failure);
        }
    }
}
