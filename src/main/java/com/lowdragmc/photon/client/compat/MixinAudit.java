package com.lowdragmc.photon.client.compat;

import com.lowdragmc.photon.Photon;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.language.ModFileScanData;
import net.minecraftforge.forgespi.locating.IModFile;
import org.objectweb.asm.Type;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.Manifest;

/**
 * Writes down, once per launch, who is mixing into the frame.
 *
 * <p>"It was a mixin of Photon 1" is the kind of diagnosis a freeze report has to be able to confirm or refute, and
 * neither the log nor a crash report can: Mixin names its targets only in {@code debug.log}, and a GPU hang writes no
 * crash report at all. So at client setup this audit lists every mixin config the runtime registered with the mod jar
 * whose manifest declares it, names the mixins declared on the classes of the render path (the ones Photon hooks and
 * the ones a rendering fault would pass through) with the mod that ships each, and looks for the old Photon: a mod
 * file other than this one that ships classes under {@code com.lowdragmc.photon}, or the config Photon 1 called
 * {@code photon-common.mixins.json}. Two mods with the mod id {@code photon} cannot load side by side, but a mod that
 * bundles Photon 1's classes under its own id can, and its mixins would then run next to these. The same lines go
 * into every freeze report, with the list of loaded mods.
 *
 * <p>Everything is read from what Forge already scanned ({@code @Mixin} annotations and classes per mod file) and
 * from the jar manifests; nothing asks Mixin's transformer for its private state. The findings are built from plain
 * collections ({@link #summarise}) so the regression can feed them a synthetic runtime; nothing here throws into the
 * game.
 */
@OnlyIn(Dist.CLIENT)
public final class MixinAudit {
    /** Photon's own mixin config. */
    public static final String OWN_CONFIG = Photon.MOD_ID + ".mixins.json";
    /** The common mixin config of Photon 1 (its Forge one was also named {@value #OWN_CONFIG}). */
    public static final String PHOTON1_CONFIG = "photon-common.mixins.json";
    /** The package Photon 1 and Photon 2 share; outside this mod file it is a bundled Photon 1. */
    public static final String PHOTON_PACKAGE = "com/lowdragmc/photon/";
    private static final String PHOTON_PACKAGE_NAME = PHOTON_PACKAGE.substring(0, PHOTON_PACKAGE.length() - 1).replace('/', '.');
    /** The owner shown for a config no mod file's manifest declares (a library jar, or a launch argument in a development run). */
    static final String NO_MANIFEST = "no mod file declares it";
    private static final Type MIXIN_ANNOTATION = Type.getType("Lorg/spongepowered/asm/mixin/Mixin;");

    /**
     * Classes on the frame path. Photon hooks the first five; a foreign mixin on any of them is the first thing to look
     * at when a frame goes wrong. The last one is LDLib 1's scene renderer, the fourth class Photon 1 mixed into.
     */
    static final List<String> RENDER_PATH = List.of(
            "net.minecraft.client.Minecraft",
            "net.minecraft.client.renderer.GameRenderer",
            "net.minecraft.client.multiplayer.ClientLevel",
            "net.minecraft.client.particle.ParticleEngine",
            "net.minecraft.client.renderer.ShaderInstance",
            "net.minecraft.client.renderer.LevelRenderer",
            // Embeddium, EntityCulling and ImmediatelyFast commonly mix into these renderer entry points.
            // Recording them makes a modpack's overlap visible without claiming that a mixin caused a failure.
            "net.minecraft.client.renderer.entity.EntityRenderDispatcher",
            "net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher",
            "net.minecraft.client.renderer.RenderBuffers",
            "com.mojang.blaze3d.vertex.BufferBuilder",
            "net.minecraft.client.renderer.PostChain",
            "net.minecraft.client.renderer.LightTexture",
            "net.minecraft.client.renderer.RenderType",
            "net.minecraft.client.renderer.texture.TextureAtlas",
            "net.minecraft.client.renderer.texture.TextureManager",
            "com.mojang.blaze3d.pipeline.RenderTarget",
            "com.mojang.blaze3d.pipeline.MainTarget",
            "com.mojang.blaze3d.platform.GlStateManager",
            "com.mojang.blaze3d.platform.Window",
            "com.mojang.blaze3d.systems.RenderSystem",
            "com.mojang.blaze3d.vertex.BufferUploader",
            "com.mojang.blaze3d.vertex.VertexBuffer",
            "com.mojang.blaze3d.vertex.Tesselator",
            "com.lowdragmc.lowdraglib.client.scene.WorldSceneRenderer");

    /** The audit's result: the report lines and the counts the smoke marker carries. */
    public record Findings(List<String> lines, int configs, int renderPathTargets, int photon1Remnants) {
    }

    private static volatile Findings findings;

    private MixinAudit() {
    }

    /** The findings, computed on first use; never null, never throws. */
    public static Findings findings() {
        var current = findings;
        if (current == null) {
            synchronized (MixinAudit.class) {
                current = findings;
                if (current == null) {
                    findings = current = compute();
                }
            }
        }
        return current;
    }

    /** The report lines, for the log and the freeze report. */
    public static List<String> lines() {
        return findings().lines();
    }

    /** Logs the audit; INFO, except a bundled Photon 1, which is a WARN. */
    public static void log() {
        var result = findings();
        for (var line : result.lines()) {
            if (line.startsWith("[mixin-audit] Photon 1 remnant")) {
                Photon.LOGGER.warn(line);
            } else {
                Photon.LOGGER.info(line);
            }
        }
        Photon.LOGGER.info("[mixin-audit-smoke] configs={} renderPathTargets={} photon1Remnants={} own={}",
                result.configs(), result.renderPathTargets(), result.photon1Remnants(), OWN_CONFIG);
    }

    /** One line naming every loaded mod, for the freeze report ("Photon was the only mod" is then a fact, not a memory). */
    public static String modListLine() {
        try {
            var mods = new TreeMap<String, String>();
            for (IModInfo info : ModList.get().getMods()) {
                mods.put(info.getModId(), info.getModId() + "@" + info.getVersion());
            }
            return "Mods (" + mods.size() + "): " + String.join(", ", mods.values());
        } catch (Throwable t) {
            return "Mods: unavailable (" + t + ")";
        }
    }

    @SuppressWarnings("deprecation")
    private static Findings compute() {
        try {
            // the configs the runtime registered (every phase; Forge uses DEFAULT)
            var configNames = new TreeSet<String>();
            for (var phase : List.of(MixinEnvironment.Phase.PREINIT, MixinEnvironment.Phase.INIT, MixinEnvironment.Phase.DEFAULT)) {
                try {
                    configNames.addAll(MixinEnvironment.getEnvironment(phase).getMixinConfigs());
                } catch (Throwable ignored) {
                    // a phase this Mixin build has no environment for
                }
            }
            // the manifests: which file declares which config
            Map<String, String> configOwner = new TreeMap<>();
            String ownFile = "?";
            for (IModFileInfo info : ModList.get().getModFiles()) {
                IModFile file = info.getFile();
                String name = String.valueOf(file.getFileName());
                boolean own = info.getMods().stream().anyMatch(mod -> Photon.MOD_ID.equals(mod.getModId()));
                if (own) ownFile = name;
                for (var declared : manifestMixinConfigs(file)) {
                    configOwner.put(declared, name);
                }
            }
            // the scan data: every @Mixin and every class, per mod file
            Map<String, List<String>> declared = new LinkedHashMap<>();
            Map<String, Collection<String>> foreignClasses = new TreeMap<>();
            for (ModFileScanData scan : ModList.get().getAllScanData()) {
                var infos = scan.getIModInfoData();
                if (infos.isEmpty()) continue;
                var mods = infos.get(0).getMods();
                String owner = mods.isEmpty() ? String.valueOf(infos.get(0).getFile().getFileName()) : mods.get(0).getModId();
                boolean own = infos.stream().flatMap(info -> info.getMods().stream())
                        .anyMatch(mod -> Photon.MOD_ID.equals(mod.getModId()));
                for (var annotation : scan.getAnnotations()) {
                    if (!MIXIN_ANNOTATION.equals(annotation.annotationType())) continue;
                    var mixinName = simpleName(annotation.clazz().getClassName());
                    for (var target : mixinTargets(annotation.annotationData())) {
                        declared.computeIfAbsent(target, k -> new ArrayList<>()).add(owner + ":" + mixinName);
                    }
                }
                if (own) continue;
                var photonClasses = new ArrayList<String>();
                for (var classData : scan.getClasses()) {
                    var internalName = classData.clazz().getInternalName();
                    if (internalName.startsWith(PHOTON_PACKAGE)) photonClasses.add(internalName);
                }
                if (!photonClasses.isEmpty()) {
                    Collections.sort(photonClasses);
                    foreignClasses.put(String.valueOf(infos.get(0).getFile().getFileName()), photonClasses);
                }
            }
            return summarise(new ArrayList<>(configNames), configOwner, declared, foreignClasses, ownFile);
        } catch (Throwable t) {
            Photon.LOGGER.warn("[mixin-audit] could not inspect the mixin environment", t);
            return new Findings(List.of("[mixin-audit] unavailable: " + t), 0, 0, 0);
        }
    }

    /** The target classes of a {@code @Mixin} annotation as Forge's scanner recorded it: {@code value} and {@code targets}. */
    static List<String> mixinTargets(Map<String, Object> annotationData) {
        var targets = new ArrayList<String>();
        if (annotationData == null) return targets;
        if (annotationData.get("value") instanceof Collection<?> types) {
            for (var type : types) {
                if (type instanceof Type asmType) targets.add(asmType.getClassName());
            }
        }
        if (annotationData.get("targets") instanceof Collection<?> names) {
            for (var name : names) {
                if (name instanceof String className) targets.add(className.replace('/', '.'));
            }
        }
        return targets;
    }

    /** The {@code MixinConfigs} manifest attribute of a mod file, split; empty when there is none or it is unreadable. */
    static List<String> manifestMixinConfigs(IModFile file) {
        try {
            Path manifestPath = file.findResource("META-INF", "MANIFEST.MF");
            if (manifestPath == null || !Files.isRegularFile(manifestPath)) return List.of();
            try (InputStream in = Files.newInputStream(manifestPath)) {
                return splitConfigs(new Manifest(in).getMainAttributes().getValue("MixinConfigs"));
            }
        } catch (Throwable t) {
            return List.of();
        }
    }

    static List<String> splitConfigs(String value) {
        if (value == null || value.isBlank()) return List.of();
        var out = new ArrayList<String>();
        for (var part : value.split(",")) {
            var trimmed = part.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    /**
     * Turns the raw facts into the report.
     *
     * @param configNames    every mixin config the runtime registered
     * @param configOwner    config name to the file whose manifest declares it (a config a library jar or a launch
     *                       argument adds, as in a development run, has no owner)
     * @param declared       target class (dotted) to the mixins declared on it, each as {@code modid:MixinSimpleName}
     * @param foreignClasses mod files other than this one, with the classes they ship under {@link #PHOTON_PACKAGE}
     *                       (internal names); only files that ship some
     * @param ownFile        this mod's file name
     */
    static Findings summarise(List<String> configNames, Map<String, String> configOwner, Map<String, List<String>> declared,
                              Map<String, Collection<String>> foreignClasses, String ownFile) {
        var lines = new ArrayList<String>();

        // 1. the configs, grouped by the file that ships them
        var names = new TreeSet<>(configNames);
        Map<String, List<String>> byOwner = new LinkedHashMap<>();
        for (var name : names) {
            var owner = configOwner.get(name);
            byOwner.computeIfAbsent(owner == null ? NO_MANIFEST : owner, k -> new ArrayList<>()).add(name);
        }
        var groups = new ArrayList<String>();
        for (var entry : byOwner.entrySet()) {
            groups.add(String.join(", ", entry.getValue()) + " (" + entry.getKey() + ")");
        }
        lines.add("[mixin-audit] " + names.size() + " mixin config(s) registered, this mod's file is " + ownFile + ": "
                + (groups.isEmpty() ? "none" : String.join("; ", groups)));

        // 2. who is on the render path
        var renderPath = new ArrayList<String>();
        for (var target : RENDER_PATH) {
            var mixins = declared.get(target);
            if (mixins == null || mixins.isEmpty()) continue;
            renderPath.add(simpleName(target) + " <- " + String.join(", ", new TreeSet<>(mixins)));
        }
        lines.add("[mixin-audit] render path: " + (renderPath.isEmpty() ? "no mixin on it" : String.join("; ", renderPath)));

        // 3. the old Photon
        int remnants = 0;
        for (var entry : foreignClasses.entrySet()) {
            var classes = entry.getValue();
            if (classes.isEmpty()) continue;
            remnants++;
            var first = classes.iterator().next().replace('/', '.');
            lines.add("[mixin-audit] Photon 1 remnant: " + entry.getKey() + " ships " + classes.size() + " class(es) under "
                    + PHOTON_PACKAGE_NAME + " (first: " + first + "); that is a bundled copy of the old Photon running"
                    + " next to this one, and its hooks on the renderer are not this mod's. Remove it or ask its author for a"
                    + " build without it.");
        }
        for (var name : names) {
            if (!name.equalsIgnoreCase(PHOTON1_CONFIG)) continue;
            remnants++;
            var owner = configOwner.get(name);
            lines.add("[mixin-audit] Photon 1 remnant: the mixin config " + name + " (" + (owner == null ? NO_MANIFEST : owner)
                    + ") is Photon 1's and is registered in this runtime.");
        }
        if (remnants == 0) {
            lines.add("[mixin-audit] Photon 1: no other mod file ships " + PHOTON_PACKAGE_NAME
                    + " classes and " + PHOTON1_CONFIG + " is not registered");
        }
        return new Findings(List.copyOf(lines), names.size(), renderPath.size(), remnants);
    }

    private static String simpleName(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }
}
