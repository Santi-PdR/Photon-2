package com.lowdragmc.photon.client.compat;

import com.lowdragmc.photon.Photon;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pins {@link MixinAudit}: a synthetic runtime with a bundled Photon 1 must be called out, the render-path line must
 * name every mixin on a class with the mod that ships it, a config no mod file declares must still be listed, the
 * {@code @Mixin} targets must be read the way Forge's scanner records them, and the live audit of the client this runs
 * in must see Photon's own mixins on {@code GameRenderer} and {@code ParticleEngine} and no old Photon.
 */
@OnlyIn(Dist.CLIENT)
public final class MixinAuditRegression {
    private MixinAuditRegression() {
    }

    public static void verify() {
        // a synthetic runtime: this mod, LDLib 1, a quiet mod, and a third mod that bundles Photon 1 and registers its config
        var configs = List.of(MixinAudit.OWN_CONFIG, "ldlib-common.mixins.json", "ldlib.mixins.json", MixinAudit.PHOTON1_CONFIG, "quiet.mixins.json");
        Map<String, String> owners = new TreeMap<>();
        owners.put(MixinAudit.OWN_CONFIG, "photon-forge-1.20.1-test-all.jar");
        owners.put("ldlib-common.mixins.json", "ldlib1-1.0.52.a.jar");
        owners.put("ldlib.mixins.json", "ldlib1-1.0.52.a.jar");
        owners.put("quiet.mixins.json", "quiet-1.0.jar");
        Map<String, List<String>> declared = new TreeMap<>();
        declared.put("net.minecraft.client.renderer.GameRenderer", List.of("photon:GameRendererMixin"));
        declared.put("net.minecraft.client.particle.ParticleEngine", List.of("photon:ParticleEngineMixin", "oldfx:ParticleEngineMixin", "ldlib:ParticleEngineMixin"));
        declared.put("net.minecraft.client.renderer.LevelRenderer", List.of("oldfx:LevelRendererMixin", "ldlib:WorldRendererMixin"));
        declared.put("com.mojang.blaze3d.vertex.BufferBuilder", List.of("embeddium:BufferBuilderMixin", "immediatelyfast:BufferBuilderMixin"));
        declared.put("net.minecraft.world.entity.Entity", List.of("quiet:EntityMixin"));
        declared.put("com.lowdragmc.lowdraglib2.gui.ui.elements.Menu", List.of("photon:MenuMixin"));
        Map<String, Collection<String>> foreign = new TreeMap<>();
        foreign.put("oldfx-1.0.jar", List.of("com/lowdragmc/photon/client/fx/EntityEffect",
                "com/lowdragmc/photon/forge/core/mixins/LevelRendererMixin"));

        var findings = MixinAudit.summarise(configs, owners, declared, foreign, "photon-forge-1.20.1-test-all.jar");
        var text = String.join("\n", findings.lines());
        check(findings.configs() == 5, "the synthetic audit did not count its five configs: " + findings.configs() + "\n" + text);
        check(findings.renderPathTargets() == 4, "the render-path count is off: " + findings.renderPathTargets() + "\n" + text);
        check(findings.photon1Remnants() == 2, "a bundled Photon 1 (classes + config) was not counted twice: " + findings.photon1Remnants() + "\n" + text);
        check(text.contains("5 mixin config(s) registered, this mod's file is photon-forge-1.20.1-test-all.jar: "
                        + "ldlib-common.mixins.json, ldlib.mixins.json (ldlib1-1.0.52.a.jar); "
                        + MixinAudit.PHOTON1_CONFIG + " (" + MixinAudit.NO_MANIFEST + "); "
                        + MixinAudit.OWN_CONFIG + " (photon-forge-1.20.1-test-all.jar); quiet.mixins.json (quiet-1.0.jar)"),
                "the configs are not grouped by the file that ships them, in order:\n" + text);
        check(text.contains("[mixin-audit] render path: GameRenderer <- photon:GameRendererMixin; "
                        + "ParticleEngine <- ldlib:ParticleEngineMixin, oldfx:ParticleEngineMixin, photon:ParticleEngineMixin; "
                        + "LevelRenderer <- ldlib:WorldRendererMixin, oldfx:LevelRendererMixin; "
                        + "BufferBuilder <- embeddium:BufferBuilderMixin, immediatelyfast:BufferBuilderMixin"),
                "the render-path line does not name every mixin on each class, in frame order and sorted:\n" + text);
        check(!text.contains("Entity <-") && !text.contains("Menu <-"), "a class off the render path was listed:\n" + text);
        check(text.contains("[mixin-audit] Photon 1 remnant: oldfx-1.0.jar ships 2 class(es) under com.lowdragmc.photon"
                        + " (first: com.lowdragmc.photon.client.fx.EntityEffect)"),
                "the bundled Photon 1 classes were not called out:\n" + text);
        check(text.contains("[mixin-audit] Photon 1 remnant: the mixin config " + MixinAudit.PHOTON1_CONFIG + " ("
                        + MixinAudit.NO_MANIFEST + ") is Photon 1's and is registered in this runtime."),
                "the old Photon's config was not called out:\n" + text);
        check(!text.contains("[mixin-audit] Photon 1: no other mod file"), "a runtime with an old Photon was reported clean:\n" + text);

        // a clean runtime says so, in one line, and an empty one does not crash
        var clean = MixinAudit.summarise(List.of(MixinAudit.OWN_CONFIG), Map.of(MixinAudit.OWN_CONFIG, "main"),
                Map.of("net.minecraft.client.renderer.GameRenderer", List.of("photon:GameRendererMixin")), Map.of(), "main");
        var cleanText = String.join("\n", clean.lines());
        check(clean.photon1Remnants() == 0 && clean.renderPathTargets() == 1 && cleanText.contains(
                        "[mixin-audit] Photon 1: no other mod file ships com.lowdragmc.photon classes and " + MixinAudit.PHOTON1_CONFIG + " is not registered"),
                "a clean runtime was not reported clean:\n" + cleanText);
        var empty = MixinAudit.summarise(List.of(), Map.of(), Map.of(), Map.of(), "?");
        check(empty.configs() == 0 && empty.renderPathTargets() == 0 && empty.photon1Remnants() == 0
                        && String.join("\n", empty.lines()).contains("render path: no mixin on it"),
                "an empty runtime was not summarised:\n" + String.join("\n", empty.lines()));

        // the manifest attribute and the @Mixin annotation, as Forge's scanner hands them over
        check(MixinAudit.splitConfigs(" a.mixins.json, b.mixins.json ,,").equals(List.of("a.mixins.json", "b.mixins.json"))
                && MixinAudit.splitConfigs(null).isEmpty() && MixinAudit.splitConfigs("  ").isEmpty(),
                "the MixinConfigs attribute is not split as the manifest writes it");
        Map<String, Object> annotation = new TreeMap<>();
        annotation.put("value", new ArrayList<>(List.of(Type.getObjectType("net/minecraft/client/renderer/GameRenderer"))));
        annotation.put("targets", new ArrayList<>(List.of("net.minecraft.client.renderer.LevelRenderer$1", "com/example/Inner")));
        annotation.put("priority", 1100);
        check(MixinAudit.mixinTargets(annotation).equals(List.of("net.minecraft.client.renderer.GameRenderer",
                        "net.minecraft.client.renderer.LevelRenderer$1", "com.example.Inner")),
                "the @Mixin targets are not read from value and targets: " + MixinAudit.mixinTargets(annotation));
        check(MixinAudit.mixinTargets(Map.of()).isEmpty() && MixinAudit.mixinTargets(null).isEmpty(),
                "a @Mixin without targets is not read as empty");

        // the client this runs in
        var live = MixinAudit.findings();
        var liveText = String.join("\n", live.lines());
        check(live.configs() >= 1 && liveText.contains("[mixin-audit] render path: "), "the live audit produced nothing:\n" + liveText);
        check(liveText.matches("(?s).*GameRenderer <- [^;\\n]*photon:GameRendererMixin.*"),
                "the live audit does not see Photon's own mixin on GameRenderer:\n" + liveText);
        check(liveText.matches("(?s).*ParticleEngine <- [^;\\n]*photon:ParticleEngineMixin.*"),
                "the live audit does not see Photon's own mixin on ParticleEngine:\n" + liveText);
        check(live.photon1Remnants() == 0, "the live audit found an old Photon in the test client:\n" + liveText);
        check(MixinAudit.lines() == live.lines(), "the audit is not computed once");
        var mods = MixinAudit.modListLine();
        check(mods.startsWith("Mods (") && mods.contains("photon@") && mods.contains("minecraft@"),
                "the mod list line does not name this mod and the game: " + mods);

        Photon.LOGGER.info("[mixin-audit-regression-smoke] a bundled Photon 1 is called out, the render-path line names every config on a class and the live audit sees Photon's own hooks");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
