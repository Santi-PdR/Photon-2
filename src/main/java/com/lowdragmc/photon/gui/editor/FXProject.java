package com.lowdragmc.photon.gui.editor;

import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.editor.project.ProjectType;
import com.lowdragmc.lowdraglib2.editor.resource.BuiltinPath;
import com.lowdragmc.lowdraglib2.editor.resource.ColorsResource;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.lowdraglib2.editor.resource.Resources;
import com.lowdragmc.photon.client.PhotonIcons;
import com.lowdragmc.photon.client.util.PhotonPaths;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.instance.InstanceIds;
import com.lowdragmc.photon.client.gameobject.emitter.data.fixer.PhotonFXProjectDataFixer;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.IMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.UIResourceMaterial;
import com.lowdragmc.photon.gui.editor.resource.PhotonShaderFunctionGraphResource;
import com.lowdragmc.photon.gui.editor.resource.FullscreenShaderGraphResource;
import com.lowdragmc.photon.gui.editor.resource.RenderGraphResource;
import com.lowdragmc.photon.gui.editor.resource.CurveResource;
import com.lowdragmc.photon.gui.editor.resource.GradientResource;
import com.lowdragmc.photon.gui.editor.resource.MaterialResource;
import com.lowdragmc.photon.gui.editor.resource.MeshResource;
import com.lowdragmc.photon.gui.editor.resource.ShaderGraphResource;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * An FX editor project: the {@link FX} being authored. Exporting it and turning {@code .fx} files back into
 * projects live in {@link FXFileActions} and {@link FXProjectConverter}.
 */
public class FXProject implements IProject {
    public static int VERSION = 5;
    public static final ProjectType TYPE = new ProjectType(PhotonIcons.PHOTON_PROJECT, "fx_project", ".fxproj", FXProject::new) {
        @Override
        public IProject loadProjectFromFile(java.io.File file) throws Exception {
            if (FXProjectConverter.isLegacyEditorFile(file)) {
                var tag = FXProjectConverter.readFileTag(file);
                var result = FXProjectConverter.convertInMemory(tag, null);
                result.warnings().forEach(warning -> com.lowdragmc.photon.Photon.LOGGER.warn(
                        "Photon 1 project {}: {}", file.getAbsolutePath(), warning));
                com.lowdragmc.photon.Photon.LOGGER.info("Imported Photon 1 project {} in memory; source left at {}",
                        file.getAbsolutePath(), file.getAbsolutePath());
                return result.project();
            }
            try {
                var loaded = super.loadProjectFromFile(file);
                if (loaded instanceof FXProject fxProject && fxProject.replacePlayable == null) {
                    fxProject.setReplacePlayable(editFxIdFromProjectFile(file));
                }
                return loaded;
            } catch (Exception failure) {
                com.lowdragmc.photon.Photon.LOGGER.error("Failed to read Photon FX project {}", file.getAbsolutePath(), failure);
                throw failure;
            }
        }

        @Override
        public void saveProjectToFile(IProject project, java.io.File file) throws Exception {
            if (file.getName().endsWith(FX.SUFFIX)) {
                throw new IllegalArgumentException("Photon projects cannot be saved over an .fx definition: " + file);
            }
            super.saveProjectToFile(project, file);
            if (project instanceof FXProject fxProject && fxProject.replacePlayable != null) {
                FXFileActions.writePlayable(fxProject, fxProject.replacePlayable);
            }
        }
    };

    @Getter
    private final Resources resources;
    @Getter
    private final FX fx = new FX();

    public FX getFx() {
        return fx;
    }
    /** The fx ids this project was converted from or exported to: an FX instance leading back to one is a cycle. */
    @Getter
    private final Set<ResourceLocation> knownAs = new LinkedHashSet<>();
    /** The editor showing this project, so an undo back to the moment it opened shows the objects it restores. */
    @Setter
    @Nullable
    private FXEditor editor;
    /**
     * When set (by {@code /photon fx editfx}), saving the project also writes the playable {@code .fx}
     * of this id into the Photon workspace pack so the next play replaces what is currently loaded.
     */
    @Getter
    @Nullable
    private ResourceLocation replacePlayable;

    /** Reject malformed/unsafe IDs before a project save can write a playable file under the workspace. */
    public void setReplacePlayable(@Nullable ResourceLocation id) {
        replacePlayable = PhotonPaths.isSafeAssetLocation(id) ? id : null;
    }

    public FXProject() {
        this.resources = Resources.of(
                MaterialResource.INSTANCE,
                ShaderGraphResource.INSTANCE,
                PhotonShaderFunctionGraphResource.INSTANCE,
                FullscreenShaderGraphResource.INSTANCE,
                RenderGraphResource.INSTANCE,
                ColorsResource.INSTANCE,
                CurveResource.INSTANCE,
                GradientResource.INSTANCE,
                MeshResource.INSTANCE
        );
    }

    @Override
    public String getVersion() {
        return "%d.0".formatted(VERSION) ;
    }

    @Override
    public ProjectType getProjectType() {
        return TYPE;
    }

    @Override
    public CompoundTag serializeProject(@NotNull HolderLookup.Provider provider) {
        bakeEditorRoot();
        var data = new CompoundTag();
        data.put("fx", fx.serializeNBT(provider));
        if (!knownAs.isEmpty()) {
            var ids = new ListTag();
            knownAs.stream().filter(PhotonPaths::isSafeAssetLocation)
                    .forEach(id -> ids.add(StringTag.valueOf(id.toString())));
            if (!ids.isEmpty()) data.put("knownAs", ids);
        }
        if (replacePlayable != null) {
            data.putString("replacePlayable", replacePlayable.toString());
        }
        embedLibraryMaterials(data);
        return data;
    }

    @Override
    public void deserializeProject(@NotNull HolderLookup.Provider provider, @NotNull CompoundTag nbt) {
        knownAs.clear();
        var ids = nbt.getList("knownAs", Tag.TAG_STRING);
        for (int i = 0; i < ids.size(); i++) {
            var id = ResourceLocation.tryParse(ids.getString(i));
            if (PhotonPaths.isSafeAssetLocation(id)) knownAs.add(id);
        }
        restoreLibraryMaterials(nbt);
        fx.deserializeNBT(provider, nbt.getCompound("fx"), knownAs);
        setReplacePlayable(nbt.contains("replacePlayable", Tag.TAG_STRING)
                ? ResourceLocation.tryParse(nbt.getString("replacePlayable")) : null);
    }

    /**
     * Materials one creates in the editor live in the resource library (the global file provider), and
     * every material slot in the fx only references one by path. A project shared to another machine —
     * or opened after the library file is gone — resolved those references to the missing material and
     * the fx silently lost its look. Snapshot every referenced non-builtin material (following
     * {@link UIResourceMaterial} chains) into the project file; {@link #restoreLibraryMaterials}
     * re-registers any that no longer resolve on load.
     */
    private void embedLibraryMaterials(CompoundTag data) {
        var embedded = new ListTag();
        collectMaterials(data.getCompound("fx"), embedded, new HashSet<>());
        if (!embedded.isEmpty()) data.put("libraryMaterials", embedded);
    }

    private static void collectMaterials(Tag tag, ListTag out, Set<String> seen) {
        if (tag instanceof CompoundTag compound) {
            if ("ui_resource_material".equals(compound.getString("type")) && compound.contains("data", Tag.TAG_COMPOUND)) {
                embedChain(compound.getCompound("data").getString("resourcePath"), out, seen);
            }
            for (var key : compound.getAllKeys()) collectMaterials(compound.get(key), out, seen);
        } else if (tag instanceof ListTag list) {
            for (var child : list) collectMaterials(child, out, seen);
        }
    }

    private static void embedChain(String pathString, ListTag out, Set<String> seen) {
        var instance = MaterialResource.INSTANCE.getResourceInstance();
        var path = IResourcePath.parse(pathString);
        // bounded: a cyclic reference chain is already rejected at resolve time
        for (int hop = 0; path != null && !(path instanceof BuiltinPath) && hop < 16; hop++) {
            if (!seen.add(path.getPathWithType())) return;
            var material = instance.getResource(path);
            if (material == null) return; // not in the library: nothing to embed
            var wrapper = material.serializeWrapper();
            if (wrapper == null) return;
            var entry = new CompoundTag();
            entry.putString("path", path.getPathWithType());
            entry.put("material", wrapper);
            out.add(entry);
            path = material instanceof UIResourceMaterial reference ? reference.getResourcePath() : null;
        }
    }

    /**
     * Re-register embedded materials whose library copy no longer resolves. The local library always
     * wins; an embedded copy is only used to heal a reference that would otherwise fall back to the
     * missing material. When the saved path is not writable here (it points into another machine's
     * game directory), the material gets a fresh home in this machine's global library and the fx's
     * references are repointed at it before the objects deserialize.
     */
    private void restoreLibraryMaterials(CompoundTag data) {
        if (!data.contains("libraryMaterials", Tag.TAG_LIST)) return;
        var instance = MaterialResource.INSTANCE.getResourceInstance();
        var global = instance.getGlobalProvider();
        for (var entry : data.getList("libraryMaterials", Tag.TAG_COMPOUND)) {
            var compound = (CompoundTag) entry;
            var path = IResourcePath.parse(compound.getString("path"));
            if (path == null || path instanceof BuiltinPath) continue;
            if (instance.getResource(path) != null) continue; // the local library wins
            var material = IMaterial.deserializeWrapper(compound.getCompound("material"));
            var restoredAt = path;
            if (!global.addResource(path, material)) {
                restoredAt = global.createSubPath(path.getResourceName());
                if (!global.addResource(restoredAt, material)) continue;
                rewriteReferences(data.getCompound("fx"), path.getPathWithType(), restoredAt.getPathWithType());
            }
            com.lowdragmc.photon.Photon.LOGGER.info("Restored missing project material {} as {}",
                    path.getPathWithType(), restoredAt.getPathWithType());
        }
    }

    private static void rewriteReferences(Tag tag, String from, String to) {
        if (tag instanceof CompoundTag compound) {
            for (var key : compound.getAllKeys()) {
                var child = compound.get(key);
                if (child instanceof StringTag string && from.equals(string.getAsString())) {
                    compound.putString(key, to);
                } else {
                    rewriteReferences(child, from, to);
                }
            }
        } else if (tag instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                var child = list.get(i);
                if (child instanceof StringTag string && from.equals(string.getAsString())) {
                    list.set(i, StringTag.valueOf(to));
                } else {
                    rewriteReferences(child, from, to);
                }
            }
        }
    }

    /**
     * {@code photon/projects/editfx/<namespace>/<path>.fxproj} is the working copy {@code /photon fx editfx}
     * writes. Reopening that file must keep replacing the playable {@code .fx} on Save.
     */
    @Nullable
    static ResourceLocation editFxIdFromProjectFile(java.io.File file) {
        try {
            var projects = PhotonPaths.getProjectsDir().getCanonicalFile().toPath();
            var rel = projects.relativize(file.getCanonicalFile().toPath()).toString().replace('\\', '/');
            if (!rel.startsWith("editfx/") || !rel.endsWith(".fxproj")) return null;
            var rest = rel.substring("editfx/".length(), rel.length() - ".fxproj".length());
            var slash = rest.indexOf('/');
            if (slash <= 0) return null;
            return ResourceLocation.tryParse(rest.substring(0, slash) + ":" + rest.substring(slash + 1));
        } catch (Exception ignored) {
            return null;
        }
    }

    public void addKnownAs(ResourceLocation id) {
        if (PhotonPaths.isSafeAssetLocation(id)) knownAs.add(id);
    }

    /**
     * Gizmo edits of the synthetic editor root (position / rotation / scale of the whole FX)
     * are not in {@code fxData}. Fold them into the children that actually get written.
     * Skip while the scene animation overlay is driving the root — that pose is preview-only.
     */
    void bakeEditorRoot() {
        if (editor == null || editor.runtime == null) return;
        // Always commit world pose so child gizmo / inspector moves persist even while the
        // animation overlay is driving the synthetic root (that overlay pose is preview-only).
        editor.runtime.commitWorldTransforms();
        if (editor.sceneView.fxObjectAnimationView.isPlaying()) return;
        editor.runtime.bakeRootIntoChildren(FXEditor.SCENE_ROOT_POSITION);
    }

    @Override
    public CompoundTag getMetadata() {
        var meta = IProject.super.getMetadata();
        meta.putInt("version_num", VERSION);
        return meta;
    }

    @Override
    public void deserializeNBT(@NotNull HolderLookup.Provider provider, @NotNull CompoundTag nbt) {
        // apply data fix for cross-version
        var metadata = nbt.getCompound("meta");
        boolean hasVersion = metadata.contains("version_num", Tag.TAG_INT);
        int version = hasVersion ? Math.max(1, metadata.getInt("version_num")) : -1;
        var sourceData = nbt.getCompound("data");
        var fixedData = hasVersion
                ? PhotonFXProjectDataFixer.INSTANCE.applyFixes(version, VERSION, sourceData)
                : sourceData;
        // Keep current LDLib2 Set<String> fields in their native string-list shape. Rewriting
        // them as LDLib1 typed payloads makes the v3→v5 fixer drop enabled GPU shader channels.
        var normalizedData = fixedData;
        if (editor != null) {
            // an undo back to the moment the project opened restores the whole of it: when that is what it
            // already is, the objects the editor shows (and the history refers to) must stay the same ones.
            // What it was exported as since is no edit, so it is neither compared nor forgotten.
            var current = serializeProject(provider);
            if (withRootParents(normalizedData).getCompound("fx").equals(withRootParents(current).getCompound("fx"))) return;
            var exportedAs = new LinkedHashSet<>(knownAs);
            deserializeProject(provider, normalizedData);
            knownAs.addAll(exportedAs);
            editor.showReloadedProject();
            return;
        }
        deserializeProject(provider, normalizedData);
    }

    /**
     * {@code data} with every object whose parent it does not have hanging off the root, which is what opening it
     * does: a project made in code saves no parent for its top-level objects (the codec's "_NULL_"), and an object
     * anchored in an FX instance whose source is missing saves a parent nothing places.
     */
    private static CompoundTag withRootParents(CompoundTag data) {
        var copy = data.copy();
        var objects = copy.getCompound("fx").getCompound("fxData").getList("fxObjects", Tag.TAG_COMPOUND);
        var ids = new HashSet<UUID>();
        for (int i = 0; i < objects.size(); i++) {
            var id = InstanceIds.parse(objects.getCompound(i).getCompound("data").getCompound("transform").getString("id"));
            if (id != null) ids.add(id);
        }
        for (int i = 0; i < objects.size(); i++) {
            var transform = objects.getCompound(i).getCompound("data").getCompound("transform");
            var parent = InstanceIds.parse(transform.getString("_parentId"));
            if (parent == null || !ids.contains(parent)) {
                transform.putString("_parentId", FXRuntime.ROOT_UUID.toString());
            }
        }
        return copy;
    }
}

