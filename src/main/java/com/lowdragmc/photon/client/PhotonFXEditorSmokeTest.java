package com.lowdragmc.photon.client;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.JsonModelSource;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.ObjModelSource;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.PhotonMesh;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.PhotonMeshCache;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.CustomShaderMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.MaterialContext;
import com.lowdragmc.lowdraglib2.client.shader.LDShaderInstance;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.resources.ResourceLocation;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.MaterialSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.SubEmittersSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.PhotonGpuChannels;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.MeshData;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleRendererSetting;
import com.lowdragmc.photon.client.gameobject.emitter.trail.TrailEmitter;
import com.lowdragmc.photon.client.gameobject.particle.TrailParticle;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

/** Exercises the editor load and preview path only in the opt-in remote client smoke run. */
public final class PhotonFXEditorSmokeTest {
    private static boolean started;
    private static boolean finished;
    private static int ticks;
    private static FXEditor editor;
    private static Screen previousScreen;
    private static Path projectFile;
    private static TrailEmitter smokeTrail;
    private static TrailEmitter smokeCpuTrail;
    private static CustomShaderMaterial smokeScreenShader;

    private PhotonFXEditorSmokeTest() {}

    public static void prepareModelFixtureIfEnabled() {
        if (!Boolean.getBoolean("photon.fxprojSmokeTest")) return;
        try {
            Path modelDir = LDLib2.getAssetsDir().toPath().resolve("photon").resolve("models").resolve("ci");
            Files.createDirectories(modelDir);
            Files.writeString(modelDir.resolve("obj_smoke.json"),
                    "{\"loader\":\"neoforge:obj\",\"model\":\"photon:models/ci/obj_smoke.obj\",\"textures\":{\"particle\":\"minecraft:block/stone\"}}");
            Files.writeString(modelDir.resolve("obj_smoke.obj"),
                    "mtllib obj_smoke.mtl\no smoke\nv 0 0 0\nv 1 0 0\nv 0 1 0\nvt 0 0\nvt 1 0\nvt 0 1\nvn 0 0 1\nusemtl stone\nf 1/1/1 2/2/1 3/3/1\n");
            Files.writeString(modelDir.resolve("obj_smoke.mtl"), "newmtl stone\nKd 1 1 1\nmap_Kd minecraft:block/stone\n");
            Path ldlibModelDir = LDLib2.getAssetsDir().toPath().resolve("ldlib2").resolve("models");
            Files.createDirectories(ldlibModelDir.resolve("block"));
            Files.createDirectories(ldlibModelDir.resolve("obj"));
            Files.writeString(ldlibModelDir.resolve("block").resolve("ci_obj_smoke.json"),
                    "{\"loader\":\"neoforge:obj\",\"model\":\"ldlib2:models/obj/ci_obj_smoke.obj\",\"textures\":{}}");
            Files.writeString(ldlibModelDir.resolve("obj").resolve("ci_obj_smoke.obj"),
                    "o smoke\nv -0.5 -0.5 0\nv 0.5 -0.5 0\nv 0 0.5 0\nvt 0 0\nvt 1 0\nvt 0.5 1\nvn 0 0 1\nf 1/1/1 2/2/1 3/3/1\n");
            Path nestedModelDir = LDLib2.getAssetsDir().toPath().resolve("ldlib2/assets/ldlib2/models");
            Files.createDirectories(nestedModelDir.resolve("block"));
            Files.createDirectories(nestedModelDir.resolve("obj"));
            Files.writeString(nestedModelDir.resolve("block/ci_nested_obj.json"),
                    "{\"loader\":\"neoforge:obj\",\"model\":\"ldlib2:models/obj/ci_nested_obj.obj\",\"textures\":{}}");
            Files.writeString(nestedModelDir.resolve("obj/ci_nested_obj.obj"),
                    "v 0 0 0\nv 1 0 0\nv 0 1 0\nvt 0 0\nvt 1 0\nvt 0 1\nvn 0 0 1\nf 1/1/1 2/2/1 3/3/1\n");
            Path shaderDir = LDLib2.getAssetsDir().toPath().resolve("photon").resolve("shaders").resolve("core").resolve("ci");
            Files.createDirectories(shaderDir);
            try (var vertex = Photon.class.getResourceAsStream("/assets/photon/shaders/core/particle.vsh");
                 var manifest = Photon.class.getResourceAsStream("/assets/photon/shaders/core/circle.json")) {
                String source = new String(vertex.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                        .replace("fog_distance(ModelViewMat, data.Position, FogShape)", "fog_distance(data.Position, FogShape)");
                var json = com.google.gson.JsonParser.parseString(new String(manifest.readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                json.addProperty("vertex", "photon:ci/fog_smoke");
                Files.writeString(shaderDir.resolve("fog_smoke.vsh"), source);
                Files.writeString(shaderDir.resolve("fog_smoke.json"), json.toString());
            }
            Files.writeString(shaderDir.resolve("tornado_smoke.vsh"), """
                    #version 330 core
                    #moj_import <photon:particle.glsl>
                    layout(location = 9) in float T;
                    uniform mat4 ModelViewMat;
                    uniform mat4 ProjMat;
#ifdef PARTICLE_MODEL_INSTANCE
                    uniform float LegacyVariantOnly;
#endif
                    out float particleT;
                    out vec2 tornadoUv;
                    out vec4 tornadoColor;
                    void main() {
                        ParticleData data = getParticleData();
#ifdef PARTICLE_MODEL_INSTANCE
                        data.UV = aUV;
                        data.Color = vec4(iColor.rgb * aBrightness, iColor.a);
#endif
                        particleT = T;
                        tornadoUv = data.UV;
                        tornadoColor = data.Color;
                        gl_Position = ProjMat * ModelViewMat * vec4(data.Position, 1.0);
#ifdef PARTICLE_MODEL_INSTANCE
                        gl_Position.x += LegacyVariantOnly * 0.01;
#endif
                    }
                    """);
            Files.writeString(shaderDir.resolve("tornado_smoke.fsh"), """
                    #version 330 core
                    #moj_import <photon:particle_utils.glsl>
                    uniform sampler2D SamplerCurve;
                    uniform vec2 ScreenSize;
                    in float particleT;
                    in vec2 tornadoUv;
                    in vec4 tornadoColor;
                    out vec4 fragColor;
                    void main() {
                        float value = getCurveValue(SamplerCurve, 0, clamp(particleT, 0.0, 1.0));
                        float uvPattern = 0.75 + 0.25 * fract(tornadoUv.x + tornadoUv.y);
                        float viewportAspect = ScreenSize.x / max(ScreenSize.y, 1.0);
                        vec3 curveColor = vec3(0.35 + value * 0.65, 0.1, 1.0 - value * 0.8);
                        curveColor.r *= clamp(viewportAspect, 0.1, 1.0);
                        fragColor = vec4(curveColor * tornadoColor.rgb * uvPattern, tornadoColor.a);
                    }
                    """);
            try (var manifest = Photon.class.getResourceAsStream("/assets/photon/shaders/core/circle.json")) {
                var json = com.google.gson.JsonParser.parseString(new String(manifest.readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                json.addProperty("vertex", "photon:ci/tornado_smoke");
                json.addProperty("fragment", "photon:ci/tornado_smoke");
                var samplers = new com.google.gson.JsonArray();
                var curveSampler = new com.google.gson.JsonObject();
                curveSampler.addProperty("name", "SamplerCurve");
                samplers.add(curveSampler);
                json.add("samplers", samplers);
                var uniforms = json.has("uniforms") ? json.getAsJsonArray("uniforms") : new com.google.gson.JsonArray();
                var screenSize = new com.google.gson.JsonObject();
                screenSize.addProperty("name", "ScreenSize");
                screenSize.addProperty("type", "float");
                screenSize.addProperty("count", 2);
                var defaults = new com.google.gson.JsonArray();
                defaults.add(1.0);
                defaults.add(1.0);
                screenSize.add("values", defaults);
                uniforms.add(screenSize);
                var legacyVariant = new com.google.gson.JsonObject();
                legacyVariant.addProperty("name", "LegacyVariantOnly");
                legacyVariant.addProperty("type", "float");
                legacyVariant.addProperty("count", 1);
                var legacyDefaults = new com.google.gson.JsonArray();
                legacyDefaults.add(0.0);
                legacyVariant.add("values", legacyDefaults);
                uniforms.add(legacyVariant);
                json.add("uniforms", uniforms);
                Files.writeString(shaderDir.resolve("tornado_smoke.json"), json.toString());
            }
            Files.writeString(shaderDir.resolve("trail_smoke.fsh"), """
                    #version 330 core
                    in float vertexDistance;
                    in vec2 texCoord0;
                    in vec4 vertexColor;
                    out vec4 fragColor;
                    void main() {
                        float sweep = 0.8 + 0.2 * sin((texCoord0.x + texCoord0.y) * 6.2831853);
                        fragColor = vec4(vertexColor.rgb * vec3(0.65, 0.9, 1.0) * sweep, vertexColor.a);
                    }
                    """);
            try (var manifest = Photon.class.getResourceAsStream("/assets/photon/shaders/core/circle.json")) {
                var json = com.google.gson.JsonParser.parseString(new String(manifest.readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                json.addProperty("fragment", "photon:ci/trail_smoke");
                Files.writeString(shaderDir.resolve("trail_smoke.json"), json.toString());
            }
            Files.writeString(shaderDir.resolve("trail_cpu_smoke.fsh"), """
                    #version 150
                    uniform sampler2D TailTexture;
                    uniform vec4 colorHDR1;
                    uniform vec4 colorHDR2;
                    in vec2 texCoord0;
                    out vec4 fragColor;
                    void main() {
                        vec3 sampleColor = texture(TailTexture, texCoord0).rgb;
                        vec4 color = mix(colorHDR1, colorHDR2, texCoord0.x);
                        fragColor = vec4(sampleColor * color.rgb * color.a, 1.0);
                    }
                    """);
            try (var manifest = Photon.class.getResourceAsStream("/assets/photon/shaders/core/circle.json")) {
                var json = com.google.gson.JsonParser.parseString(new String(manifest.readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                json.addProperty("fragment", "photon:ci/trail_cpu_smoke");
                var tailSampler = new com.google.gson.JsonObject();
                tailSampler.addProperty("name", "TailTexture");
                json.getAsJsonArray("samplers").add(tailSampler);
                for (var name : java.util.List.of("colorHDR1", "colorHDR2")) {
                    var colorUniform = new com.google.gson.JsonObject();
                    colorUniform.addProperty("name", name);
                    colorUniform.addProperty("type", "float");
                    colorUniform.addProperty("count", 4);
                    var defaults = new com.google.gson.JsonArray();
                    for (int i = 0; i < 4; i++) defaults.add(1.0);
                    colorUniform.add("values", defaults);
                    json.getAsJsonArray("uniforms").add(colorUniform);
                }
                Files.writeString(shaderDir.resolve("trail_cpu_smoke.json"), json.toString());
            }
            Files.writeString(shaderDir.resolve("tangent_smoke.vsh"), """
                    #version 330 core
                    #moj_import <photon:particle.glsl>
                    uniform mat4 ModelViewMat;
                    uniform mat4 ProjMat;
                    out float vertexDistance;
                    out vec2 texCoord0;
                    out vec4 vertexColor;
                    void main() {
                        ParticleData data = getParticleData();
#if defined(PARTICLE_MODEL_INSTANCE) && defined(PHOTON_TANGENT)
                        data.UV = aUV;
                        data.Normal = aNormal.xyz;
                        data.Color.rgb *= aNormal.w;
                        data.Position += aTangent.xyz * 0.001;
#endif
                        vertexDistance = 0.0;
                        texCoord0 = data.UV;
                        vertexColor = data.Color;
                        gl_Position = ProjMat * ModelViewMat * vec4(data.Position, 1.0);
                    }
                    """);
            try (var manifest = Photon.class.getResourceAsStream("/assets/photon/shaders/core/circle.json")) {
                var json = com.google.gson.JsonParser.parseString(new String(manifest.readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                json.addProperty("vertex", "photon:ci/tangent_smoke");
                Files.writeString(shaderDir.resolve("tangent_smoke.json"), json.toString());
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Could not prepare OBJ model and tornado-shader smoke fixtures", failure);
        }
    }

    public static void registerIfEnabled() {
        if (Boolean.getBoolean("photon.fxprojSmokeTest")) {
            MinecraftForge.EVENT_BUS.addListener(PhotonFXEditorSmokeTest::onClientTick);
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.getOverlay() != null || minecraft.level == null || minecraft.player == null) return;
        try {
            if (started && smokeTrail != null) {
                ticks++;
                float x = -2.5f + ticks * 0.12f;
                float z = (float) Math.sin(ticks * 0.12f) * 0.7f;
                smokeTrail.transform().localPosition(new org.joml.Vector3f(x, 2, z));
                smokeCpuTrail.transform().localPosition(new org.joml.Vector3f(2.5f - ticks * 0.12f, 2, z));
            }
            if (!started) {
                started = true;
                openLegacyProject(minecraft);
            } else if (ticks == 30) {
                int queued = editor.sceneView.particleManager.particlesByRenderType().values().stream()
                        .mapToInt(Collection::size).sum();
                if (queued == 0) throw new IllegalStateException("Editor preview did not queue any particles");
                var trailParticle = trailParticle(smokeTrail);
                if (smokeTrail.getParticleAmount() != 1 || trailParticle.getTails().size() < 3) {
                    throw new IllegalStateException("Moving GPU trail did not accumulate renderable segments: alive="
                            + smokeTrail.getParticleAmount() + ", segments=" + trailParticle.getTails().size());
                }
                var cpuTrailParticle = trailParticle(smokeCpuTrail);
                if (smokeCpuTrail.getParticleAmount() != 1 || cpuTrailParticle.getTails().size() < 3) {
                    throw new IllegalStateException("Moving CPU trail did not accumulate renderable segments");
                }
                int live = editor.runtime.objects.values().stream().filter(ParticleEmitter.class::isInstance)
                        .map(ParticleEmitter.class::cast).mapToInt(ParticleEmitter::getParticleAmount).sum();
                if (live != 3) throw new IllegalStateException("Expected legacy model, tornado-style OBJ shader model, and CPU-billboard particles, got " + live);
                var screenUniform = smokeScreenShader.getShader(MaterialContext.PARTICLE_MODEL_INSTANCE).getUniform("ScreenSize");
                var target = minecraft.getMainRenderTarget();
                if (screenUniform == null || screenUniform.getFloatBuffer().get(0) != target.width ||
                        screenUniform.getFloatBuffer().get(1) != target.height)
                    throw new IllegalStateException("ScreenSize did not match the scene framebuffer");
                Screenshot.grab(new File("."), minecraft.getMainRenderTarget(),
                        message -> Photon.LOGGER.info("[fxproj-editor-smoke] screenshot: {}", message.getString()));
                Photon.LOGGER.info("[trail-render-smoke] custom TRAIL_INSTANCE shader rendered moving GPU trail with {} live segments", trailParticle.getTails().size());
                Photon.LOGGER.info("[screen-size-smoke] custom GPU model shader rendered using framebuffer-sized ScreenSize");
                Photon.LOGGER.info("[fxproj-editor-smoke] opened v3 managed-list project and previewed legacy model, tornado-style OBJ shader model, CPU-billboard particles, and moving GPU trail ({} live, {} queued objects)", live, queued);
            } else if (ticks == 110) {
                Screenshot.grab(new File("."), minecraft.getMainRenderTarget(),
                        message -> {
                            Photon.LOGGER.info("[fxproj-editor-smoke] expired screenshot: {}", message.getString());
                            minecraft.execute(minecraft::stop);
                        });
                finished = true;
                editor.closeCurrentProject(false, null);
                minecraft.setScreen(previousScreen);
                Files.deleteIfExists(projectFile);
            }
        } catch (Throwable failure) {
            finished = true;
            Photon.LOGGER.error("[fxproj-editor-smoke] failed to open or preview the legacy project", failure);
            throw new IllegalStateException("Photon editor project smoke test failed", failure);
        }
    }

    private static void openLegacyProject(Minecraft minecraft) throws Exception {
        verifyNestedMeshLoad();
        var provider = com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry();
        var legacyMaterial = new CustomShaderMaterial(new ResourceLocation("photon", "ci/tornado_smoke"));
        var legacyData = new CompoundTag();
        var holderData = new CompoundTag();
        var legacyUniforms = new CompoundTag();
        var variantValue = new ListTag();
        variantValue.add(FloatTag.valueOf(0.37f));
        legacyUniforms.put("LegacyVariantOnly", variantValue);
        holderData.put("uniforms", legacyUniforms);
        holderData.put("samplers", new CompoundTag());
        legacyData.put("shaderData", holderData);
        legacyMaterial.deserializeAdditionalNBT(legacyData, provider);
        if (legacyMaterial.getShader(MaterialContext.NORMAL).getUniform("LegacyVariantOnly") != null) {
            throw new IllegalStateException("Legacy variant-only uniform unexpectedly exists in the base shader");
        }
        var legacyVariant = legacyMaterial.getShader(MaterialContext.PARTICLE_MODEL_INSTANCE);
        var restoredVariantUniform = legacyVariant.getUniform("LegacyVariantOnly");
        if (restoredVariantUniform == null || Math.abs(restoredVariantUniform.getFloatBuffer().get(0) - 0.37f) > 0.0001f) {
            throw new IllegalStateException("Legacy material with samplers lost its variant-only uniform");
        }
        Photon.LOGGER.info("[legacy-variant-uniform-smoke] restored variant-only uniform from legacy shader data with samplers");
        var original = new CustomShaderMaterial();
        var authoredCurve = new com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve(
                0, 1, 0, 1, "", "", new com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.ECBCurves(
                0, 0.15f, 0.1f, 0.15f, 0.9f, 0.85f, 1, 0.85f));
        original.curveTexture.addCurve(authoredCurve);
        var saved = com.lowdragmc.lowdraglib2.utils.PersistedParser.serializeNBT(original, provider);
        if (!saved.contains("curveTexture", Tag.TAG_LIST)) throw new IllegalStateException("Curve sampler did not serialize as a list");
        var savedSegments = saved.getList("curveTexture", Tag.TAG_COMPOUND).getCompound(0).getList("curves", Tag.TAG_LIST);
        if (savedSegments.size() != 1 || !savedSegments.get(0).equals(floatList(0f, 0.15f, 0.1f, 0.15f, 0.9f, 0.85f, 1f, 0.85f)))
            throw new IllegalStateException("Shader curve did not preserve its authored control points: " + savedSegments);
        var restoredMaterial = new CustomShaderMaterial();
        com.lowdragmc.lowdraglib2.utils.PersistedParser.deserializeNBT(saved, restoredMaterial, provider);
        if (restoredMaterial.curveTexture.serializeNBT(provider).size() != 1 || restoredMaterial.curveTexture.getCurveTexture() == null)
            throw new IllegalStateException("Saved shader curve was not restored and uploaded");
        var restoredCurves = restoredMaterial.curveTexture.serializeNBT(provider);
        if (!saved.getList("curveTexture", Tag.TAG_COMPOUND).equals(restoredCurves))
            throw new IllegalStateException("Shader curve control points changed on reload: " + restoredCurves);
        var loadedCurve = new com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve();
        loadedCurve.deserializeNBT(provider, restoredCurves.getCompound(0));
        if (Math.abs(loadedCurve.getCurves().getCurveY(0f) - 0.15f) > 0.0001f ||
                Math.abs(loadedCurve.getCurves().getCurveY(1f) - 0.85f) > 0.0001f)
            throw new IllegalStateException("Shader curve values changed on reload");
        var randomCurve = new com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.RandomCurve(
                0, 1, 0, 1, "", "", new com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.ECBCurves(
                0, 0.2f, 0.1f, 0.2f, 0.9f, 0.2f, 1, 0.2f),
                new com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.ECBCurves(
                0, 0.8f, 0.1f, 0.8f, 0.9f, 0.8f, 1, 0.8f));
        var randomSaved = randomCurve.serializeNBT(provider);
        var randomLoaded = new com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.RandomCurve();
        randomLoaded.deserializeNBT(provider, randomSaved);
        if (Math.abs(randomLoaded.get(0.5f, () -> 0f) - 0.2f) > 0.0001f ||
                Math.abs(randomLoaded.get(0.5f, () -> 1f) - 0.8f) > 0.0001f)
            throw new IllegalStateException("Random shader curves changed on reload: " + randomSaved);
        var firstCurve = new com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve();
        var secondCurve = new com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve();
        secondCurve.getCurves().getSegments().get(0).p0.y = 0.9f;
        var curveRemoval = new com.lowdragmc.photon.client.gameobject.emitter.data.material.CurveTexture(8, 2);
        curveRemoval.addCurve(firstCurve);
        curveRemoval.addCurve(secondCurve);
        int curveBefore = curveRemoval.getCurveTexture().getPixels().getPixelRGBA(0, 0);
        curveRemoval.removeCurve(firstCurve);
        int curveAfter = curveRemoval.getCurveTexture().getPixels().getPixelRGBA(0, 0);
        curveRemoval.close();
        if (curveBefore == curveAfter) throw new IllegalStateException("Removing a curve did not refresh its texture");
        var firstGradient = new com.lowdragmc.lowdraglib2.math.GradientColor(0xffff0000);
        var secondGradient = new com.lowdragmc.lowdraglib2.math.GradientColor(0xff0000ff);
        var gradientRemoval = new com.lowdragmc.photon.client.gameobject.emitter.data.material.GradientTexture(8, 2);
        gradientRemoval.addGradient(firstGradient);
        gradientRemoval.addGradient(secondGradient);
        int gradientBefore = gradientRemoval.getGradientTexture().getPixels().getPixelRGBA(0, 0);
        gradientRemoval.removeGradient(firstGradient);
        int gradientAfter = gradientRemoval.getGradientTexture().getPixels().getPixelRGBA(0, 0);
        gradientRemoval.close();
        if (gradientBefore == gradientAfter) throw new IllegalStateException("Removing a gradient did not refresh its texture");
        restoredMaterial.curveTexture.close();
        Photon.LOGGER.info("[shader-curve-smoke] restored and uploaded a saved shader curve");
        var fogMaterial = new CustomShaderMaterial(new ResourceLocation("photon", "ci/fog_smoke"));
        fogMaterial.recompile();
        if (fogMaterial.isCompiledError()) throw new IllegalStateException(fogMaterial.getCompiledErrorMessage());
        if (!(fogMaterial.getShader(MaterialContext.PARTICLE_MODEL_INSTANCE) instanceof LDShaderInstance variant)
                || !variant.defines.contains("PARTICLE_MODEL_INSTANCE")) {
            throw new IllegalStateException("Two-argument fog shader failed its instanced variant");
        }
        Photon.LOGGER.info("[shader-compat-smoke] compiled two-argument fog shader and instanced variant");
        var tangentMaterial = new CustomShaderMaterial(new ResourceLocation("photon", "ci/tangent_smoke"));
        tangentMaterial.recompile();
        if (tangentMaterial.isCompiledError()
                || !(tangentMaterial.getShader(MaterialContext.PARTICLE_MODEL_INSTANCE_TANGENT) instanceof LDShaderInstance tangentVariant)
                || !tangentVariant.defines.containsAll(Set.of("PARTICLE_MODEL_INSTANCE", "PHOTON_TANGENT"))) {
            throw new IllegalStateException("Photon 26.2 tangent shader inputs are incompatible");
        }
        Photon.LOGGER.info("[tangent-abi-smoke] compiled original vec2 UV and vec4 normal tangent inputs");
        var location = new ResourceLocation("photon", "ci/obj_smoke");
        var resource = minecraft.getResourceManager().getResource(new ResourceLocation("photon", "models/ci/obj_smoke.json")).orElseThrow();
        try (var reader = resource.openAsReader()) {
            // This invokes Forge's ExtendedBlockModelDeserializer, not just Photon's loader detection.
            BlockModel.fromStream(reader);
        }
        var mesh = new JsonModelSource(location).getMesh();
        if (mesh == null || mesh.vertexCount() < 3) {
            throw new IllegalStateException("NeoForge OBJ resource did not produce a Photon mesh");
        }
        Photon.LOGGER.info("[obj-model-smoke] parsed neoforge:obj through Forge and built {} vertices", mesh.vertexCount());
        var externalLocation = new ResourceLocation("ldlib2", "block/ci_obj_smoke");
        var externalResource = minecraft.getResourceManager()
                .getResource(new ResourceLocation("ldlib2", "models/block/ci_obj_smoke.json")).orElseThrow();
        try (var reader = externalResource.openAsReader()) {
            BlockModel.fromStream(reader);
        }
        var externalMesh = new JsonModelSource(externalLocation).getMesh();
        if (externalMesh == null || externalMesh.vertexCount() < 3) {
            throw new IllegalStateException("LDLib2-namespace NeoForge OBJ did not produce a Photon mesh");
        }
        Photon.LOGGER.info("[external-obj-model-smoke] loaded ldlib2:block/ci_obj_smoke with {} vertices", externalMesh.vertexCount());
        var nestedSource = new JsonModelSource(new ResourceLocation("ldlib2", "block/ci_nested_obj"));
        var nestedMesh = nestedSource.getMesh();
        if (nestedMesh == null || nestedMesh.vertexCount() < 3)
            throw new IllegalStateException("Nested LDLib2 OBJ did not produce a Photon mesh");
        var nestedObj = LDLib2.getAssetsDir().toPath().resolve("ldlib2/assets/ldlib2/models/obj/ci_nested_obj.obj");
        Files.writeString(nestedObj, "v 0 0 0\nv 2 0 0\nv 0 2 0\nv 2 2 0\nf 1 2 3\nf 2 4 3\n");
        Files.setLastModifiedTime(nestedObj, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5000));
        com.lowdragmc.photon.client.gameobject.emitter.data.model.PhotonMeshCache.INSTANCE.pollFileChanges();
        var reloadedNestedMesh = nestedSource.getMesh();
        if (reloadedNestedMesh == nestedMesh || reloadedNestedMesh.vertexCount() <= nestedMesh.vertexCount())
            throw new IllegalStateException("Editing a nested OBJ did not invalidate its JSON model");
        Photon.LOGGER.info("[nested-obj-model-smoke] loaded nested LDLib2 OBJ with {} vertices", nestedMesh.vertexCount());
        var source = new FXProject();
        var emitter = new ParticleEmitter();
        emitter.transform().localPosition(new org.joml.Vector3f(0, 2, 0));
        emitter.config.setLooping(false);
        emitter.config.setStartLifetime(NumberFunction.constant(70));
        emitter.config.setStartSpeed(NumberFunction.constant(0));
        emitter.config.setStartSize(new com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3(4, 4, 4));
        emitter.config.emission.setEmissionRate(NumberFunction.constant(0));
        emitter.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Model);
        emitter.config.renderer.setModel(new MeshData(new JsonModelSource(location)));
        emitter.config.renderer.setUseGPUInstance(true);
        emitter.config.renderer.getMaterials().clear();
        var visibleMaterial = new MaterialSetting(new com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial());
        visibleMaterial.setCull(false);
        visibleMaterial.setDepthTest(false);
        emitter.config.renderer.getMaterials().add(visibleMaterial);
        var burst = new EmissionSetting.Burst();
        burst.time = 0;
        burst.cycles = 1;
        burst.setCount(NumberFunction.constant(1));
        burst.interval = 4;
        emitter.config.emission.getBursts().add(burst);
        emitter.config.subEmitters.setEnable(true);
        emitter.config.subEmitters.getEmitters().add(new SubEmittersSetting.Emitter());
        source.getFx().getFxData().objects().add(emitter);

        var tornadoModel = new ParticleEmitter();
        tornadoModel.transform().localPosition(new org.joml.Vector3f(2.5f, 2, 0));
        tornadoModel.config.setLooping(false);
        tornadoModel.config.setStartLifetime(NumberFunction.constant(70));
        tornadoModel.config.setStartSpeed(NumberFunction.constant(0));
        tornadoModel.config.setStartSize(new com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3(4, 4, 4));
        tornadoModel.config.emission.setEmissionRate(NumberFunction.constant(0));
        tornadoModel.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Model);
        tornadoModel.config.renderer.setModel(new MeshData(new JsonModelSource(externalLocation)));
        tornadoModel.config.renderer.setUseGPUInstance(true);
        tornadoModel.config.renderer.getMaterials().clear();
        var tornadoShader = new CustomShaderMaterial(new ResourceLocation("photon", "ci/tornado_smoke"));
        tornadoShader.curveTexture.addCurve(new com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve());
        tornadoShader.recompile();
        if (tornadoShader.isCompiledError()) throw new IllegalStateException(tornadoShader.getCompiledErrorMessage());
        if (!(tornadoShader.getShader(MaterialContext.PARTICLE_MODEL_INSTANCE) instanceof LDShaderInstance tornadoVariant)
                || !tornadoVariant.defines.contains("PARTICLE_MODEL_INSTANCE")) {
            throw new IllegalStateException("Tornado-style custom shader failed its GPU model variant");
        }
        if (tornadoShader.curveTexture.getCurveTexture() == null) {
            throw new IllegalStateException("Tornado-style shader curve sampler did not upload");
        }
        var tornadoMaterial = new MaterialSetting(tornadoShader);
        tornadoMaterial.setCull(false);
        tornadoMaterial.setDepthTest(false);
        tornadoModel.config.renderer.getMaterials().add(tornadoMaterial);
        enableLegacyTChannel(tornadoModel);
        var tornadoBurst = new EmissionSetting.Burst();
        tornadoBurst.time = 0;
        tornadoBurst.cycles = 1;
        tornadoBurst.setCount(NumberFunction.constant(1));
        tornadoBurst.interval = 4;
        tornadoModel.config.emission.getBursts().add(tornadoBurst);
        source.getFx().getFxData().objects().add(tornadoModel);

        var billboard = new ParticleEmitter();
        billboard.transform().localPosition(new org.joml.Vector3f(-2, 2, 0));
        billboard.config.setLooping(false);
        billboard.config.setStartLifetime(NumberFunction.constant(70));
        billboard.config.setStartSpeed(NumberFunction.constant(0));
        billboard.config.setStartSize(new com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3(0.8f, 0.8f, 0.8f));
        billboard.config.emission.setEmissionRate(NumberFunction.constant(0));
        billboard.config.renderer.setRenderMode(ParticleRendererSetting.Mode.Billboard);
        billboard.config.renderer.setUseGPUInstance(false);
        billboard.config.renderer.getMaterials().clear();
        var billboardMaterial = new MaterialSetting(new com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial());
        billboardMaterial.setCull(false);
        billboardMaterial.setDepthTest(false);
        billboard.config.renderer.getMaterials().add(billboardMaterial);
        var billboardBurst = new EmissionSetting.Burst();
        billboardBurst.time = 0;
        billboardBurst.cycles = 1;
        billboardBurst.setCount(NumberFunction.constant(1));
        billboardBurst.interval = 4;
        billboard.config.emission.getBursts().add(billboardBurst);
        source.getFx().getFxData().objects().add(billboard);

        var trail = new TrailEmitter();
        trail.config.setDuration(150);
        trail.config.setLooping(false);
        trail.config.setTime(150);
        trail.config.setMinVertexDistance(0.02f);
        trail.config.renderer.setUseGPUInstance(true);
        trail.config.renderer.getMaterials().clear();
        var trailShader = new CustomShaderMaterial(new ResourceLocation("photon", "ci/trail_smoke"));
        trailShader.recompile();
        if (trailShader.isCompiledError()) throw new IllegalStateException(trailShader.getCompiledErrorMessage());
        if (!(trailShader.getShader(MaterialContext.TRAIL_INSTANCE) instanceof LDShaderInstance trailVariant)
                || !trailVariant.defines.contains("TRAIL_INSTANCE")) {
            throw new IllegalStateException("Arrow-trail custom shader failed its GPU trail variant");
        }
        var trailMaterial = new MaterialSetting(trailShader);
        trailMaterial.setCull(false);
        trailMaterial.setDepthTest(false);
        trail.config.renderer.getMaterials().add(trailMaterial);
        trail.transform().localPosition(new org.joml.Vector3f(-2.5f, 2, 0));
        source.getFx().getFxData().objects().add(trail);

        // Old arrow_trail projects omit the GPU toggle and restore a texture sampler from shaderData.
        var cpuTrail = new TrailEmitter();
        cpuTrail.config.setDuration(150);
        cpuTrail.config.setLooping(false);
        cpuTrail.config.setTime(150);
        cpuTrail.config.setMinVertexDistance(0.02f);
        cpuTrail.config.setWidthOverTrail(NumberFunction.constant(0.4f));
        cpuTrail.config.renderer.setUseGPUInstance(false);
        cpuTrail.config.renderer.getMaterials().clear();
        var cpuShader = new CustomShaderMaterial(new ResourceLocation("photon", "ci/trail_cpu_smoke"));
        var cpuData = new CompoundTag();
        var cpuShaderData = new CompoundTag();
        var samplers = new CompoundTag();
        var tailTexture = new CompoundTag();
        tailTexture.putString("type", "texture");
        tailTexture.putString("resource", "minecraft:textures/block/stone.png");
        samplers.put("TailTexture", tailTexture);
        cpuShaderData.put("samplers", samplers);
        var cpuUniforms = new CompoundTag();
        cpuUniforms.put("colorHDR1", floatList(0.8f, 0.4f, 0.2f, 3.0f));
        cpuUniforms.put("colorHDR2", floatList(0.2f, 0.6f, 0.9f, 2.0f));
        cpuShaderData.put("uniforms", cpuUniforms);
        cpuData.put("shaderData", cpuShaderData);
        cpuShader.deserializeAdditionalNBT(cpuData, provider);
        if (cpuShader.isCompiledError()) throw new IllegalStateException(cpuShader.getCompiledErrorMessage());
        assertLegacyHdr(cpuShader, 0.8f, 3.0f);
        Field holderField = CustomShaderMaterial.class.getDeclaredField("shaderHolder");
        holderField.setAccessible(true);
        var holder = (com.lowdragmc.lowdraglib2.client.shader.LDShaderHolder) holderField.get(cpuShader);
        holder.setHDRUniform("colorHDR1", new com.lowdragmc.lowdraglib2.math.HDRColor(0.4f, 0.3f, 0.2f, 1.0f, 4.0f));
        assertLegacyHdr(cpuShader, 0.4f, 4.0f);
        var savedCpuData = (CompoundTag) cpuShader.serializeAdditionalNBT(provider);
        if (!savedCpuData.getCompound("shaderData").getCompound("samplers").getCompound("TailTexture")
                .getString("resource").equals("minecraft:textures/block/stone.png")) {
            throw new IllegalStateException("Legacy trail sampler was lost when saving the material");
        }
        cpuShader = new CustomShaderMaterial(new ResourceLocation("photon", "ci/trail_cpu_smoke"));
        cpuShader.deserializeAdditionalNBT(savedCpuData, provider);
        assertLegacyHdr(cpuShader, 0.4f, 4.0f);
        Photon.LOGGER.info("[legacy-hdr-smoke] preserved legacy HDR values through editing and material reload");
        // Match the sample FXP's external LDLib material wrapper and legacy shaderData layout.
        var materialFile = LDLib2.getAssetsDir().toPath().resolve("ldlib2/resources/global/ci_tail.material.nbt");
        Files.createDirectories(materialFile.getParent());
        var oldMaterialData = new CompoundTag();
        oldMaterialData.putString("shaderLocation", "photon:ci/trail_cpu_smoke");
        oldMaterialData.put("_additional", cpuData.copy());
        var oldMaterial = new CompoundTag();
        oldMaterial.putString("type", "custom_shader");
        oldMaterial.put("data", oldMaterialData);
        var resourceFile = new CompoundTag();
        resourceFile.putString("type", "material");
        resourceFile.put("data", oldMaterial);
        NbtIo.write(resourceFile, materialFile.toFile());
        var resourceReference = new com.lowdragmc.photon.client.gameobject.emitter.data.material.UIResourceMaterial(
                new com.lowdragmc.lowdraglib2.editor.resource.FilePath(
                        new File("./ldlib2/assets/ldlib2/resources/global/ci_tail.material.nbt")));
        var cpuMaterial = new MaterialSetting(resourceReference);
        cpuMaterial.setCull(false);
        cpuMaterial.setDepthTest(false);
        cpuTrail.config.renderer.getMaterials().add(cpuMaterial);
        cpuTrail.transform().localPosition(new org.joml.Vector3f(2.5f, 2, 0));
        source.getFx().getFxData().objects().add(cpuTrail);

        projectFile = Files.createTempFile("photon-editor-v3-", ".fxproj");
        FXProject.TYPE.saveProjectToFile(source, projectFile.toFile());
        var encoded = NbtIo.read(projectFile.toFile());
        var fixtureConfig = encoded.getCompound("data").getCompound("fx").getCompound("fxData")
                .getList("fxObjects", Tag.TAG_COMPOUND).getCompound(0).getCompound("data").getCompound("config");
        var tornadoFixtureConfig = encoded.getCompound("data").getCompound("fx").getCompound("fxData")
                .getList("fxObjects", Tag.TAG_COMPOUND).getCompound(1).getCompound("data").getCompound("config");
        var trailFixtureConfig = encoded.getCompound("data").getCompound("fx").getCompound("fxData")
                .getList("fxObjects", Tag.TAG_COMPOUND).getCompound(4).getCompound("data").getCompound("config");
        var widthCurve = new CompoundTag();
        var widthData = new CompoundTag();
        var widthSegments = new ListTag();
        widthSegments.add(floatList(0f, 0f, 0.1f, 0f, 0.72f, 1f, 1f, 1f));
        widthData.put("curves", widthSegments);
        widthData.putFloat("min", 0f);
        widthData.putFloat("max", Float.MAX_VALUE);
        widthData.putFloat("lower", 0f);
        widthData.putFloat("upper", 0.2f);
        widthData.putBoolean("lockControlPoint", true);
        widthData.putString("xAxis", "trail position");
        widthData.putString("yAxis", "width");
        widthCurve.putString("type", "curve");
        widthCurve.put("data", widthData);
        trailFixtureConfig.put("widthOverTrail", widthCurve);
        Photon.LOGGER.info("[tornado-render-smoke] saved GPU channel setting before migration: {}",
                tornadoFixtureConfig.getCompound("additionalGPUDataSetting"));
        if (fixtureConfig.getCompound("subEmitters").getCompound("emitters")
                .getList("payload", Tag.TAG_COMPOUND).size() != 1) {
            throw new IllegalStateException("The enabled sub-emitter fixture was not written: " + fixtureConfig.getCompound("subEmitters"));
        }
        var legacyModel = new CompoundTag();
        legacyModel.putString("modelLocation", "photon:block/sphere");
        fixtureConfig.getCompound("renderer").put("model", legacyModel);
        encoded.getCompound("meta").putInt("version_num", 3);
        encoded.getCompound("meta").putString("version", "3.0");
        replaceManagedListUids(encoded);
        NbtIo.write(encoded, projectFile.toFile());
        var fixedData = com.lowdragmc.photon.client.gameobject.emitter.data.fixer.PhotonFXProjectDataFixer.INSTANCE
                .applyFixes(3, FXProject.VERSION, encoded.getCompound("data"));
        var fixedTornadoSetting = fixedData.getCompound("fx").getCompound("fxData")
                .getList("fxObjects", Tag.TAG_COMPOUND).getCompound(1).getCompound("data")
                .getCompound("config").getCompound("additionalGPUDataSetting");
        Photon.LOGGER.info("[tornado-render-smoke] setting after v3 datafix: {}", fixedTornadoSetting);

        var loaded = (FXProject) FXProject.TYPE.loadProjectFromFile(projectFile.toFile());
        if (loaded.getFx().getFxData().objects().size() != 5
                || !(loaded.getFx().getFxData().objects().get(0) instanceof ParticleEmitter restored)
                || !(loaded.getFx().getFxData().objects().get(1) instanceof ParticleEmitter restoredTornado)
                || restored.config.renderer.getMaterials().size() != 1
                || restored.config.emission.getBursts().size() != 1
                || restored.config.subEmitters.getEmitters().size() != 1
                || restoredTornado.config.renderer.getMaterials().size() != 1
                || restoredTornado.config.emission.getBursts().size() != 1
                || !(loaded.getFx().getFxData().objects().get(2) instanceof ParticleEmitter)
                || !(loaded.getFx().getFxData().objects().get(3) instanceof TrailEmitter)
                || !(loaded.getFx().getFxData().objects().get(4) instanceof TrailEmitter)
                || restored.config.emission.getBursts().get(0).cycles != 1) {
            throw new IllegalStateException("v3 managed lists were not restored: " + loaded.serializeProject(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry()));
        }
        if (!(restored.config.renderer.getModelSource() instanceof ObjModelSource sphere)
                || !sphere.getModelLocation().equals(new ResourceLocation("photon", "models/sphere.obj"))
                || sphere.getMesh().vertexCount() < 3) {
            throw new IllegalStateException("Legacy photon:block/sphere did not resolve to the bundled sphere OBJ");
        }
        var restoredCpuTrail = (TrailEmitter) loaded.getFx().getFxData().objects().get(4);
        var restoredWidth = restoredCpuTrail.config.getWidthOverTrail().get(0.8f, () -> 0.5f).floatValue();
        if (!Float.isFinite(restoredWidth) || restoredWidth <= 0.01f) {
            throw new IllegalStateException("Legacy trail width curve did not restore: " + restoredWidth);
        }
        Photon.LOGGER.info("[legacy-trail-curve-smoke] restored a nonzero width from the legacy curve: {}", restoredWidth);
        var externalMaterial = restoredCpuTrail.config.renderer.getMaterials().get(0).getMaterial();
        if (!(externalMaterial instanceof com.lowdragmc.photon.client.gameobject.emitter.data.material.UIResourceMaterial reference)
                || !(reference.getRawMaterial() instanceof CustomShaderMaterial restoredCpuShader)) {
            throw new IllegalStateException("Legacy trail did not resolve its external custom shader material");
        }
        assertLegacyHdr(restoredCpuShader, 0.8f, 3.0f);
        var restoredExternalData = (CompoundTag) restoredCpuShader.serializeAdditionalNBT(provider);
        if (!restoredExternalData.getCompound("shaderData").getCompound("samplers").getCompound("TailTexture")
                .getString("resource").equals("minecraft:textures/block/stone.png")) {
            throw new IllegalStateException("External legacy trail material lost its texture sampler");
        }
        Photon.LOGGER.info("[external-trail-material-smoke] loaded the legacy file-backed trail material");
        Photon.LOGGER.info("[legacy-model-alias-smoke] photon:block/sphere resolved to bundled OBJ with {} vertices",
                sphere.getMesh().vertexCount());
        long tChannel = PhotonGpuChannels.CHANNELS.stream()
                .filter(channel -> channel.id().equals("addition_gpu_data.t"))
                .findFirst().orElseThrow().bit();
        Photon.LOGGER.info("[tornado-render-smoke] loaded enabled channel ids: {}",
                enabledChannelIds(restoredTornado));
        var restoredModel = restoredTornado.config.renderer.getModelSource();
        var restoredTornadoMaterial = restoredTornado.config.renderer.getMaterials().get(0).getMaterial();
        boolean objLoaded = restoredModel instanceof JsonModelSource obj && obj.getMesh() != null && obj.getMesh().vertexCount() >= 3;
        boolean tChannelEnabled = restoredTornado.config.additionalGPUDataSetting.isEnable()
                && (restoredTornado.config.additionalGPUDataSetting.userMask() & tChannel) != 0;
        boolean shaderLoaded = restoredTornadoMaterial instanceof CustomShaderMaterial shader
                && !shader.isCompiledError() && shader.curveTexture.getCurveTexture() != null;
        if (restoredTornadoMaterial instanceof CustomShaderMaterial shader) smokeScreenShader = shader;
        if (!objLoaded || !tChannelEnabled || !shaderLoaded) {
            Photon.LOGGER.error("[tornado-render-smoke] reload diagnostics: model={} objLoaded={} tEnabled={} tMask={} tBit={} material={} shaderLoaded={}",
                    restoredModel, objLoaded, restoredTornado.config.additionalGPUDataSetting.isEnable(),
                    restoredTornado.config.additionalGPUDataSetting.userMask(), tChannel,
                    restoredTornadoMaterial.getClass().getName(), shaderLoaded);
            if (restoredTornadoMaterial instanceof CustomShaderMaterial shader) {
                Photon.LOGGER.error("[tornado-render-smoke] shader diagnostics: error={} curveTexture={}",
                        shader.getCompiledErrorMessage(), shader.curveTexture.getCurveTexture());
            }
            throw new IllegalStateException("Tornado-style OBJ, T channel, custom shader, or curve sampler did not survive v3 project loading");
        }
        Photon.LOGGER.info("[tornado-render-smoke] loaded LDLib2 OBJ with legacy UV/brightness attributes, location-9 T data, GPU shader variant, and SamplerCurve");

        previousScreen = minecraft.screen;
        editor = new FXEditor();
        minecraft.setScreen(new ModularUIScreen(new ModularUI(UI.of(editor)), Component.literal("Photon CI editor")));
        editor.loadProject(loaded, projectFile.toFile());
        editor.sceneView.fxObjectInfoView.setDisplay(false);
        editor.sceneView.fxObjectAnimationView.setDisplay(false);
        if (editor.getCurrentProject() != loaded || editor.runtime == null || editor.runtime.objects.size() < 2) {
            throw new IllegalStateException("Editor did not initialize the loaded project runtime");
        }
        smokeTrail = editor.runtime.objects.values().stream().filter(TrailEmitter.class::isInstance)
                .map(TrailEmitter.class::cast).filter(t -> t.config.renderer.isUseGPUInstance()).findFirst()
                .orElseThrow(() -> new IllegalStateException("Editor runtime did not instantiate the moving trail"));
        smokeCpuTrail = editor.runtime.objects.values().stream().filter(TrailEmitter.class::isInstance)
                .map(TrailEmitter.class::cast).filter(t -> !t.config.renderer.isUseGPUInstance()).findFirst()
                .orElseThrow(() -> new IllegalStateException("Editor runtime did not instantiate the CPU trail"));
        Photon.LOGGER.info("[fxproj-editor-smoke] initialized v3 project with materials, bursts and sub-emitters");
    }

    private static TrailParticle trailParticle(TrailEmitter emitter) throws ReflectiveOperationException {
        Field field = TrailEmitter.class.getDeclaredField("trailParticle");
        field.setAccessible(true);
        return (TrailParticle) field.get(emitter);
    }

    private static ListTag floatList(float... values) {
        var list = new ListTag();
        for (float value : values) list.add(FloatTag.valueOf(value));
        return list;
    }

    private static void assertLegacyHdr(CustomShaderMaterial material, float red, float intensity) {
        var uniform = material.getShader(MaterialContext.NORMAL).getUniform("colorHDR1");
        if (uniform == null || Math.abs(uniform.getFloatBuffer().get(0) - red) > 0.0001f
                || Math.abs(uniform.getFloatBuffer().get(3) - intensity) > 0.0001f) {
            throw new IllegalStateException("Legacy HDR uniform was changed to premultiplied form");
        }
    }

    @SuppressWarnings("unchecked")
    private static void enableLegacyTChannel(ParticleEmitter emitter) throws ReflectiveOperationException {
        var setting = emitter.config.additionalGPUDataSetting;
        setting.setEnable(true);
        Field field = setting.getClass().getDeclaredField("additionalData");
        field.setAccessible(true);
        ((Set<String>) field.get(setting)).add("addition_gpu_data.t");
    }

    @SuppressWarnings("unchecked")
    private static Object enabledChannelIds(ParticleEmitter emitter) throws ReflectiveOperationException {
        Field field = emitter.config.additionalGPUDataSetting.getClass().getDeclaredField("additionalData");
        field.setAccessible(true);
        return field.get(emitter.config.additionalGPUDataSetting);
    }

    private static void replaceManagedListUids(Tag value) {
        if (value instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                Tag child = compound.get(key);
                if ((key.equals("materials") || key.equals("bursts") || key.equals("emitters"))
                        && child instanceof CompoundTag managed
                        && managed.contains("uid") && managed.get("payload") instanceof ListTag payload) {
                    managed.put("uid", IntTag.valueOf(payload.size()));
                }
                replaceManagedListUids(child);
            }
        } else if (value instanceof ListTag list) {
            for (Tag child : list) replaceManagedListUids(child);
        }
    }

    private record CollidingKey(String name) {
        @Override public int hashCode() { return 0; }
    }

    private static void verifyNestedMeshLoad() {
        var cache = PhotonMeshCache.INSTANCE;
        var parent = new CollidingKey("ci-parent");
        var child = new CollidingKey("ci-child");
        try {
            var loaded = cache.get(parent, ignored -> {
                cache.invalidate(child);
                return cache.get(child, alsoIgnored -> PhotonMesh.EMPTY);
            });
            if (loaded != PhotonMesh.EMPTY) throw new IllegalStateException("Nested mesh load changed its result");
            Photon.LOGGER.info("[mesh-cache-smoke] nested load and invalidation with colliding keys succeeded");
        } finally {
            cache.invalidate(parent);
            cache.invalidate(child);
        }
    }
}



