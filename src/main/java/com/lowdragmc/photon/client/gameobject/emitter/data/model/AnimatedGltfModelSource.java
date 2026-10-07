package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigSetter;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.PhotonParticleManager;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.skin.AnimationClip;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.skin.ClipRetarget;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.skin.SkinnedModel;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.skin.VertexAnimationBake;
import dev.vfyjxf.taffy.style.AlignItems;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Runtime glTF model source with shared CPU skinning and optional baked per-particle VAT playback. */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "animated_gltf_model", registry = "photon:model_source")
public class AnimatedGltfModelSource implements IModelSource, IDynamicMesh {
    @Getter
    @Configurable(name = "AnimatedGltfModelSource.modelLocation")
    private ResourceLocation modelLocation = Photon.id("models/missing.glb");
    @Getter
    @Configurable(name = "GltfModelSource.flipV", tips = "photon.model_source.gltf_model.flipV.tips")
    private boolean flipV;
    @Getter
    private String animation = "";
    @Configurable(name = "AnimatedGltfModelSource.animationFiles",
            tips = "photon.model_source.animated_gltf_model.animationFiles.tips")
    private String animationFiles = "";
    @Getter
    @Configurable(name = "AnimatedGltfModelSource.speed")
    private float speed = 1f;
    @Getter
    @Configurable(name = "AnimatedGltfModelSource.loop")
    private boolean loop = true;
    @Getter
    @Configurable(name = "AnimatedGltfModelSource.perParticlePhase",
            tips = "photon.model_source.animated_gltf_model.perParticlePhase.tips")
    private boolean perParticlePhase;
    @Getter
    @Configurable(name = "AnimatedGltfModelSource.frames",
            tips = "photon.model_source.animated_gltf_model.frames.tips")
    @ConfigNumber(range = {2, 240})
    private int frames = 30;
    @Getter
    @Configurable(name = "AnimatedGltfModelSource.phaseSource",
            tips = "photon.model_source.animated_gltf_model.phaseSource.tips")
    private PhaseSource phaseSource = PhaseSource.Random;
    @Getter
    @Configurable(name = "AnimatedGltfModelSource.interpolate",
            tips = "photon.model_source.animated_gltf_model.interpolate.tips")
    private boolean interpolate = true;

    public enum PhaseSource { Random, Lifetime }

    /** Deterministic animation clock for editor previews and importer tests; null restores the live clock. */
    @Nullable
    private static volatile Float pinnedClock;

    public static void pinClock(@Nullable Float seconds) {
        pinnedClock = seconds;
    }

    public record BakedVertexAnimation(float[] table, int vertexCount, int frames, float phase,
                                       PhaseSource phaseSource, boolean interpolate) {}

    @Nullable private AnimatedPose posed;
    @Nullable private SkinnedModel combinedModel;
    @Nullable private SkinnedModel combinedFrom;
    private String combinedFiles = "";
    private long combinedGeneration = -1;
    @Nullable private SkinnedModel bakedFrom;
    @Nullable private String bakedAnimation;
    @Nullable private float[] bakedTable;
    private final DynamicMeshCache dynamicCache = new DynamicMeshCache();

    public AnimatedGltfModelSource() {
    }

    public AnimatedGltfModelSource(ResourceLocation modelLocation) {
        this.modelLocation = modelLocation;
    }

    @ConfigSetter(field = "modelLocation")
    public void setModelLocation(ResourceLocation value) {
        invalidate();
        modelLocation = value;
    }

    @ConfigSetter(field = "flipV")
    public void setFlipV(boolean value) {
        invalidate();
        flipV = value;
    }

    @ConfigSetter(field = "animation")
    public void setAnimation(String value) {
        animation = value == null ? "" : value;
        posed = null;
        dropBake();
        dynamicCache.invalidate();
    }

    @ConfigSetter(field = "animationFiles")
    public synchronized void setAnimationFiles(String value) {
        animationFiles = value == null ? "" : value;
        posed = null;
        dropBake();
        combinedModel = null;
        combinedFrom = null;
        combinedGeneration = -1;
        dynamicCache.invalidate();
    }

    /** External clip resources, in insertion order. The string-backed config field remains readable
     * for saves produced by earlier Forge port builds; additional NBT uses the 26.2 list format. */
    public List<ResourceLocation> getAnimationFiles() {
        return parseAnimationFiles(animationFiles);
    }

    private static List<ResourceLocation> parseAnimationFiles(String encoded) {
        if (encoded == null || encoded.isBlank()) return List.of();
        var locations = new ArrayList<ResourceLocation>();
        for (String entry : encoded.split("[,;\\s]+")) {
            var location = ResourceLocation.tryParse(entry.trim());
            if (location != null) locations.add(location);
        }
        return List.copyOf(locations);
    }

    @Override
    public Tag serializeAdditionalNBT(HolderLookup.@NotNull Provider provider) {
        var locations = getAnimationFiles();
        if (locations.isEmpty()) return IModelSource.super.serializeAdditionalNBT(provider);
        return AnimationFileListNbt.write(locations);
    }

    @Override
    public void deserializeAdditionalNBT(Tag tag, HolderLookup.@NotNull Provider provider) {
        var locations = AnimationFileListNbt.read(tag);
        // The upstream clears this list before decoding. An omitted field means an empty list,
        // which matters when a source instance is reused while loading older/default NBT.
        setAnimationFiles(locations.stream().map(ResourceLocation::toString)
                .collect(java.util.stream.Collectors.joining(",")));
    }

    @ConfigSetter(field = "speed")
    public void setSpeed(float value) {
        speed = value;
    }

    @ConfigSetter(field = "loop")
    public void setLoop(boolean value) {
        loop = value;
    }

    @ConfigSetter(field = "perParticlePhase")
    public void setPerParticlePhase(boolean value) { perParticlePhase = value; dropBake(); }

    @ConfigSetter(field = "frames")
    public void setFrames(int value) { frames = Math.max(2, Math.min(240, value)); dropBake(); }

    @ConfigSetter(field = "phaseSource")
    public void setPhaseSource(PhaseSource value) { phaseSource = value == null ? PhaseSource.Random : value; }

    @ConfigSetter(field = "interpolate")
    public void setInterpolate(boolean value) { interpolate = value; }

    public boolean usesPerParticlePhase() { return perParticlePhase; }

    @Nullable
    @Override
    public synchronized BakedVertexAnimation vertexAnimation() {
        if (!perParticlePhase) return null;
        SkinnedModel model = model();
        AnimationClip clip = selectedClip(model);
        if (!model.isAnimated()) return null;
        if (bakedTable == null || bakedFrom != model || !Objects.equals(bakedAnimation, animation)) {
            bakedTable = VertexAnimationBake.bake(model, clip, frames);
            bakedFrom = model;
            bakedAnimation = animation;
            if (bakedTable == null) {
                Photon.LOGGER.warn("Could not bake {} frames of animated glTF {}", frames, modelLocation);
                return null;
            }
        }
        float duration = clip == null ? 0f : clip.duration();
        float phase = duration <= 0f ? 0f : clipTime(clip) / duration;
        return new BakedVertexAnimation(bakedTable, model.mesh().quadCount() * 4, frames, phase,
                phaseSource, interpolate);
    }

    private void dropBake() {
        bakedFrom = null;
        bakedAnimation = null;
        bakedTable = null;
    }

    @Override
    public synchronized PhotonMesh getMesh() {
        SkinnedModel model = model();
        if (!model.isAnimated()) {
            posed = null;
            return model.mesh();
        }
        updatePose(model);
        return dynamicCache.resolve(this);
    }

    /** True when the resolved model has a skeleton and at least one playable clip. */
    public synchronized boolean hasAnimation() {
        SkinnedModel model = model();
        return model.isAnimated() && !model.clips().isEmpty();
    }

    /** Names of the base model clips and any clips retargeted from the additional animation files. */
    public synchronized List<String> getClipNames() {
        return model().clipNames();
    }

    /** Returns this source when it needs CPU deformation; VAT playback leaves deformation to the shader. */
    @Nullable
    public synchronized IDynamicMesh asDynamic() {
        return !perParticlePhase && model().isAnimated() ? this : null;
    }

    private void updatePose(SkinnedModel model) {
        AnimationClip clip = selectedClip(model);
        posed = AnimatedPose.of(model, clip, clipTime(clip), speed, loop);
    }

    @Override
    public synchronized PhotonMesh topology() {
        return model().mesh();
    }

    @Override
    public synchronized long revision() {
        return posed == null ? 0 : posed.revision();
    }

    @Override
    @Nullable
    public synchronized float[] geometry() {
        return posed == null ? null : posed.geometry();
    }

    @Override
    @Nullable
    public synchronized float[] tangents() {
        var model = model();
        if (!model.isAnimated() || posed == null) return null;
        return posed.tangents(model, selectedClip(model));
    }

    @Override
    public synchronized void invalidate() {
        PhotonMeshCache.INSTANCE.invalidate(key());
        posed = null;
        combinedModel = null;
        combinedFrom = null;
        combinedGeneration = -1;
        dropBake();
        dynamicCache.invalidate();
    }

    @Override
    public IModelSource copy() {
        AnimatedGltfModelSource copy = new AnimatedGltfModelSource(modelLocation);
        copy.flipV = flipV;
        copy.animation = animation;
        copy.animationFiles = animationFiles;
        copy.speed = speed;
        copy.loop = loop;
        copy.perParticlePhase = perParticlePhase;
        copy.frames = frames;
        copy.phaseSource = phaseSource;
        copy.interpolate = interpolate;
        return copy;
    }

    private SkinnedModel model() {
        SkinnedModel base = baseModel();
        if (animationFiles.isBlank()) return base;
        long generation = PhotonMeshCache.INSTANCE.generation();
        if (combinedModel != null && combinedFrom == base && combinedFiles.equals(animationFiles)
                && combinedGeneration == generation) return combinedModel;
        if (base.skeleton() == null) {
            Photon.LOGGER.warn("Animated glTF {} has no skeleton; animation files cannot be retargeted", modelLocation);
            combinedModel = base;
            combinedFrom = base;
            combinedFiles = animationFiles;
            combinedGeneration = generation;
            return base;
        }
        List<AnimationClip> clips = new ArrayList<>(base.clips());
        for (ResourceLocation location : getAnimationFiles()) {
            if (location.equals(modelLocation)) continue;
            SkinnedModel source = PhotonMeshCache.INSTANCE.getSkinnedModel(
                    new PhotonMeshCache.GltfKey(location, flipV), ignored -> load(location));
            if (source.skeleton() == null || source.clips().isEmpty()) {
                Photon.LOGGER.warn("Animation glTF {} has no skinned skeleton/clips to retarget", location);
                continue;
            }
            ClipRetarget.Result result = ClipRetarget.onto(base.skeleton(), source.skeleton(), source.clips(), baseName(location));
            if (result.clips().isEmpty()) {
                Photon.LOGGER.warn("No animation channels in {} match the joints of {}", location, modelLocation);
            } else if (result.droppedChannels() > 0) {
                Photon.LOGGER.warn("Dropped {} unmatched animation channels from {} for {}", result.droppedChannels(), location, modelLocation);
            }
            clips.addAll(result.clips());
        }
        combinedModel = new SkinnedModel(base.mesh(), base.skin(), base.skeleton(), List.copyOf(clips));
        combinedFrom = base;
        combinedFiles = animationFiles;
        combinedGeneration = generation;
        return combinedModel;
    }

    private SkinnedModel baseModel() {
        if (Minecraft.getInstance().getOverlay() instanceof LoadingOverlay) return SkinnedModel.EMPTY;
        return PhotonMeshCache.INSTANCE.getModel(key(), ignored -> load());
    }

    private PhotonMeshCache.GltfKey key() {
        return new PhotonMeshCache.GltfKey(modelLocation, flipV);
    }

    @Nullable
    private SkinnedModel load() {
        return load(modelLocation);
    }

    @Nullable
    private SkinnedModel load(ResourceLocation location) {
        if (Minecraft.getInstance().getOverlay() instanceof LoadingOverlay) return null;
        try (var in = Minecraft.getInstance().getResourceManager().open(location)) {
            SkinnedModel model = GltfMeshParser.parseModel(in, flipV, location);
            File file = new File(LDLib2.getAssetsDir(), location.getNamespace() + "/" + location.getPath());
            if (file.isFile()) PhotonMeshCache.INSTANCE.trackFile(new PhotonMeshCache.GltfKey(location, flipV), file);
            return model;
        } catch (Exception exception) {
            Photon.LOGGER.warn("Failed to load animated glTF model {}", location, exception);
            return SkinnedModel.EMPTY;
        }
    }

    private static String baseName(ResourceLocation location) {
        String path = location.getPath();
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return path.substring(slash + 1, dot > slash ? dot : path.length());
    }

    /** Shared picker for the character model and standalone animation clips. */
    private static void showGltfDialog(UIElement root, Consumer<ResourceLocation> onPicked) {
        Dialog.showFileDialog("photon.gui.editor.tips.select_gltf", LDLib2.getAssetsDir(), true, node -> {
            if (!node.getKey().isFile()) return true;
            String name = node.getKey().getName().toLowerCase();
            return name.endsWith(".glb") || name.endsWith(".gltf");
        }, file -> {
            if (file == null || !file.isFile()) return;
            ResourceLocation location = IModelSource.getAssetLocationFromFile(file);
            if (location != null) onPicked.accept(location);
        }).show(root);
    }

    private void appendAnimationFile(ResourceLocation location) {
        if (location.equals(modelLocation)) return; // the base file's clips are already included
        var files = new ArrayList<>(getAnimationFiles());
        if (files.contains(location)) return;
        files.add(location);
        setAnimationFiles(files.stream().map(ResourceLocation::toString).collect(java.util.stream.Collectors.joining(",")));
    }

    private void refreshClipChoices(List<String> clips, SelectorConfigurator<String> selector) {
        String selected = animation;
        clips.clear();
        clips.add("");
        clips.addAll(model().clipNames());
        if (!selected.isEmpty() && !clips.contains(selected)) clips.add(selected);
        selector.notifyChanges();
    }

    @Nullable
    private AnimationClip selectedClip(SkinnedModel model) {
        return animation.isEmpty() ? model.clipAt(0) : model.clip(animation);
    }

    private float clipTime(@Nullable AnimationClip clip) {
        float seconds;
        float editorSeconds = PhotonParticleManager.editorAnimationSeconds();
        if (pinnedClock != null) {
            seconds = pinnedClock;
        } else if (editorSeconds >= 0) {
            seconds = editorSeconds;
        } else {
            var level = Minecraft.getInstance().level;
            seconds = level == null ? 0f : (level.getGameTime() + Minecraft.getInstance().getFrameTime()) / 20f;
        }
        seconds *= speed;
        if (clip == null || !Float.isFinite(seconds) || clip.duration() <= 0f) return 0f;
        if (!loop) return Math.max(0f, Math.min(seconds, clip.duration()));
        float wrapped = seconds % clip.duration();
        return wrapped < 0f ? wrapped + clip.duration() : wrapped;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void buildConfigurator(ConfiguratorGroup father) {
        IModelSource.super.buildConfigurator(father);
        List<String> clips = new ArrayList<>();
        clips.add("");
        clips.addAll(model().clipNames());
        if (!animation.isEmpty() && !clips.contains(animation)) clips.add(animation);
        var clipSelector = new SelectorConfigurator<>("AnimatedGltfModelSource.animation", () -> animation,
                this::setAnimation, animation, true, clips,
                name -> name == null || name.isEmpty() ? "photon.model_source.animated_gltf_model.first_animation" : name);
        Configurator filePicker = new Configurator();
        filePicker.addInlineChild(new Button().setText("photon.gui.editor.tips.select_gltf").setOnClick(event -> {
            var mui = event.currentElement.getModularUI();
            if (mui == null) return;
            showGltfDialog(mui.ui.rootElement, location -> {
                if (location != null && !location.equals(modelLocation)) {
                    setModelLocation(location);
                    refreshClipChoices(clips, clipSelector);
                    filePicker.notifyChanges();
                }
            });
        }).layout(layout -> layout.alignSelf(AlignItems.CENTER)));
        Configurator animationPicker = new Configurator();
        animationPicker.addInlineChild(new Button().setText("photon.gui.editor.tips.add_animation_gltf").setOnClick(event -> {
            var mui = event.currentElement.getModularUI();
            if (mui == null) return;
            showGltfDialog(mui.ui.rootElement, location -> {
                int previousLength = animationFiles.length();
                appendAnimationFile(location);
                if (animationFiles.length() != previousLength) {
                    refreshClipChoices(clips, clipSelector);
                    animationPicker.notifyChanges();
                    filePicker.notifyChanges();
                }
            });
        }).layout(layout -> layout.alignSelf(AlignItems.CENTER)));
        Configurator reload = new Configurator().addInlineChild(new Button().setText("photon.reload_mesh")
                .setOnClick(event -> { invalidate(); filePicker.notifyChanges(); })
                .layout(layout -> layout.alignSelf(AlignItems.CENTER)));
        father.addConfigurators(clipSelector, filePicker, animationPicker, reload);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || getClass() != other.getClass()) return false;
        AnimatedGltfModelSource that = (AnimatedGltfModelSource) other;
        return flipV == that.flipV && Float.compare(speed, that.speed) == 0 && loop == that.loop
                && Objects.equals(modelLocation, that.modelLocation) && Objects.equals(animation, that.animation)
                && Objects.equals(animationFiles, that.animationFiles) && perParticlePhase == that.perParticlePhase
                && frames == that.frames && phaseSource == that.phaseSource && interpolate == that.interpolate;
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelLocation, flipV, animation, animationFiles, speed, loop, perParticlePhase, frames, phaseSource, interpolate);
    }
}
