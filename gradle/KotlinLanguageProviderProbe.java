import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Exercises the KFF scan visitor in the same named-module arrangement used by Forge. */
public final class KotlinLanguageProviderProbe {
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 2) {
            throw new IllegalArgumentException("Expected extracted module JAR paths");
        }

        Path[] modulePaths = new Path[arguments.length];
        for (int index = 0; index < arguments.length; index++) {
            modulePaths[index] = Path.of(arguments[index]);
        }

        ModuleFinder finder = ModuleFinder.of(modulePaths);
        Set<String> roots = new HashSet<>();
        finder.findAll().forEach(module -> roots.add(module.descriptor().name()));
        if (!roots.contains("thedarkcolour.kotlinforforge")
                || !roots.contains("thedarkcolour.kotlinforforge.kfflang")
                || !roots.contains("photon.forge")
                || roots.contains("kotlin.stdlib")) {
            throw new IllegalStateException("Unexpected Forge JPMS roots: " + roots);
        }

        Configuration configuration = ModuleLayer.boot().configuration()
                .resolveAndBind(finder, ModuleFinder.of(), roots);
        ModuleLayer layer = ModuleLayer.boot().defineModulesWithOneLoader(
                configuration, ClassLoader.getSystemClassLoader());

        ClassLoader kffLoader = layer.findLoader("thedarkcolour.kotlinforforge.kfflang");
        Class<?> providerClass = Class.forName(
                "thedarkcolour.kotlinforforge.KotlinLanguageProvider", true, kffLoader);
        Object provider = providerClass.getConstructor().newInstance();
        Consumer visitor = (Consumer) providerClass.getMethod("getFileVisitor").invoke(provider);

        ClassLoader forgeSpiLoader = layer.findLoader("net.minecraftforge.forgespi");
        Class<?> scanDataClass = Class.forName(
                "net.minecraftforge.forgespi.language.ModFileScanData", true, forgeSpiLoader);
        Object scanData = scanDataClass.getConstructor().newInstance();
        visitor.accept(scanData);

        Map<?, ?> targets = (Map<?, ?>) scanDataClass.getMethod("getTargets").invoke(scanData);
        if (!targets.isEmpty()) {
            throw new IllegalStateException("An empty scan must not register Kotlin mod targets");
        }
        Class<?> intrinsics = Class.forName(
                "kotlin.jvm.internal.Intrinsics", false, layer.findLoader("thedarkcolour.kotlinforforge"));
        Class<?> versionClass = Class.forName(
                "kotlin.KotlinVersion", true, layer.findLoader("thedarkcolour.kotlinforforge"));
        Object currentVersion = versionClass.getField("CURRENT").get(null);
        int major = (int) versionClass.getMethod("getMajor").invoke(currentVersion);
        int minor = (int) versionClass.getMethod("getMinor").invoke(currentVersion);
        int patch = (int) versionClass.getMethod("getPatch").invoke(currentVersion);
        if (major < 2 || (major == 2 && minor < 1) || (major == 2 && minor == 1 && patch < 20)) {
            throw new IllegalStateException("LDLib2 requires Kotlin 2.1.20 or newer, found "
                    + major + "." + minor + "." + patch);
        }
        System.out.println("KFF scan visitor executed with " + intrinsics.getModule().getName()
                + " across " + roots.size() + " resolved modules; Kotlin "
                + major + "." + minor + "." + patch + " satisfies LDLib2.");
    }
}
