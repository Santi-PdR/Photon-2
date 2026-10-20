import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/** Relocates Kotlin runtime packages into Photon's private namespace. */
public final class RelocateKotlinJar {
    private static final String PREFIX = "com/lowdragmc/photon/internal/embedded/";

    private static final Remapper REMAPPER = new Remapper() {
        @Override
        public String map(String name) {
            if (isKotlinPackage(name)) return PREFIX + name;
            return name;
        }

        @Override
        public String mapPackageName(String name) {
            if (isKotlinPackage(name)) return PREFIX + name;
            return name;
        }

        @Override
        public Object mapValue(Object value) {
            if (value instanceof String string) return mapKotlinString(string);
            return super.mapValue(value);
        }
    };

    private RelocateKotlinJar() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: RelocateKotlinJar INPUT.jar OUTPUT.jar");
        }
        try (var input = new JarFile(args[0]);
             var output = new JarOutputStream(new FileOutputStream(args[1]))) {
            Enumeration<JarEntry> entries = input.entries();
            while (entries.hasMoreElements()) {
                JarEntry source = entries.nextElement();
                String name = source.getName();
                if (name.endsWith("/module-info.class") || name.equals("module-info.class")) continue;

                byte[] bytes;
                try (InputStream stream = input.getInputStream(source)) {
                    bytes = stream.readAllBytes();
                }
                if (name.endsWith(".class")) {
                    ClassReader reader = new ClassReader(bytes);
                    ClassWriter writer = new ClassWriter(0);
                    ClassVisitor visitor = new ClassRemapper(writer, REMAPPER);
                    reader.accept(visitor, 0);
                    bytes = writer.toByteArray();
                } else if (name.startsWith("META-INF/services/")) {
                    bytes = mapKotlinString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8))
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }

                JarEntry destination = new JarEntry(mapPath(name));
                if (source.getTime() >= 0) destination.setTime(source.getTime());
                output.putNextEntry(destination);
                output.write(bytes);
                output.closeEntry();
            }
        }
    }

    private static boolean isKotlinPackage(String name) {
        return name.startsWith("kotlin/") || name.startsWith("kotlinx/") || name.startsWith("_COROUTINE/");
    }

    private static String mapPath(String name) {
        String prefix = "META-INF/versions/";
        if (name.startsWith(prefix)) {
            int packageStart = name.indexOf('/', prefix.length());
            if (packageStart >= 0) {
                String version = name.substring(0, packageStart + 1);
                String packagePath = name.substring(packageStart + 1);
                if (isKotlinPackage(packagePath)) return version + PREFIX + packagePath;
            }
        }
        return isKotlinPackage(name) ? PREFIX + name : name;
    }

    private static String mapKotlinString(String value) {
        if (value.startsWith("kotlin.")) return "com.lowdragmc.photon.internal.embedded." + value;
        if (value.startsWith("kotlinx.")) return "com.lowdragmc.photon.internal.embedded." + value;
        if (value.startsWith("_COROUTINE.")) return "com.lowdragmc.photon.internal.embedded." + value;
        if (value.startsWith("kotlin/") || value.startsWith("kotlinx/") || value.startsWith("_COROUTINE/")) {
            return PREFIX + value;
        }
        return value;
    }
}
