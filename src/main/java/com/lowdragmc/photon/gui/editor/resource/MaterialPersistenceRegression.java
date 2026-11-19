package com.lowdragmc.photon.gui.editor.resource;

import com.lowdragmc.lowdraglib2.editor.resource.FilePath;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.IMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.UIResourceMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.nio.file.Files;

/**
 * Materials one creates in the editor live in the resource library (the global file provider), while
 * every material slot in an fx only references one by path. Two things must hold for a created
 * material to "stay saved":
 * <ol>
 *   <li>creating it writes it to disk and it round-trips through the library, and</li>
 *   <li>a project referencing it carries a snapshot, so the reference still resolves after the
 *       library copy is gone (another machine, a reinstall, a deleted file).</li>
 * </ol>
 * The second half covers {@link FXProject#serializeProject}'s {@code libraryMaterials} embed and
 * {@code restoreLibraryMaterials}' heal-on-load, added after an audit showed projects silently fell
 * back to the missing material outside the author's machine.
 */
public final class MaterialPersistenceRegression {
    private MaterialPersistenceRegression() {
    }

    public static void verify() throws Exception {
        var instance = MaterialResource.INSTANCE.getResourceInstance();
        var global = instance.getGlobalProvider();
        var path = global.createSubPath("persistence_smoke");

        // 1. a created material reaches the disk and resolves through the library
        var material = new TextureMaterial(Photon.id("textures/particle/ring.png"));
        material.setDiscardThreshold(0.37f);
        if (!global.addResource(path, material)) {
            throw new IllegalStateException("could not create the persistence smoke material");
        }
        try {
            instance.saveResource(); // flush any deferred write before asserting the disk state
        } catch (RuntimeException failure) {
            throw new IllegalStateException("could not flush the material library", failure);
        }
        if (!(path instanceof FilePath filePath) || !filePath.getFile().isFile()) {
            throw new IllegalStateException("a created material did not reach the disk: " + path.getPathWithType());
        }
        if (!(instance.getResource(path) instanceof TextureMaterial roundTrip)
                || Float.compare(roundTrip.getDiscardThreshold(), 0.37f) != 0) {
            throw new IllegalStateException("a created material did not round-trip through the library");
        }

        // 2. a project referencing it embeds the material payload
        var project = new FXProject();
        var emitter = new ParticleEmitter();
        emitter.config.renderer.getMaterials().get(0).setMaterial(new UIResourceMaterial(path));
        project.getFx().getFxData().objects().add(emitter);
        var file = Files.createTempFile("photon-material-persistence-", ".fxproj");
        try {
            FXProject.TYPE.saveProjectToFile(project, file.toFile());
            var saved = NbtIo.read(file.toFile());
            var embedded = saved.getCompound("data").getList("libraryMaterials", Tag.TAG_COMPOUND);
            if (embedded.isEmpty()) {
                throw new IllegalStateException("a referenced user material was not embedded into the project");
            }

            // 3. with the library copy gone, loading the project heals the reference
            global.removeResource(path);
            if (instance.getResource(path) != null) {
                throw new IllegalStateException("the smoke material is still resolvable after removal");
            }
            var loaded = FXProject.TYPE.loadProjectFromFile(file.toFile());
            if (!(instance.getResource(path) instanceof TextureMaterial healed)
                    || Float.compare(healed.getDiscardThreshold(), 0.37f) != 0) {
                throw new IllegalStateException("the project did not restore its missing material");
            }
            if (!(loaded instanceof FXProject loadedProject)
                    || !(loadedProject.getFx().getFxData().objects().get(0) instanceof ParticleEmitter loadedEmitter)) {
                throw new IllegalStateException("the smoke project did not load its emitter");
            }
            var slot = loadedEmitter.config.renderer.getMaterials().get(0).getMaterial();
            if (!(slot instanceof UIResourceMaterial reference) || reference.getRawMaterial() == IMaterial.MISSING) {
                throw new IllegalStateException("the loaded project's material slot does not resolve");
            }
        } finally {
            // leave no library entry or temp file behind
            global.removeResource(path);
            Files.deleteIfExists(file);
        }
        Photon.LOGGER.info("[material-persistence-smoke] created materials persist on disk and travel inside the project file");
    }
}
