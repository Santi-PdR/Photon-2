package com.lowdragmc.photon.client.fx.compat;


import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A compatibility layer for porting Photon 1 FX to Photon 2
 * @author Cdogsnappy
 */
public class FXCompat {

    private static final Path FX_CVT_PATH = Path.of(LDLib2.getAssetsDir() + "/photon/fx_old");

    public static int convertFX() {
        if (!Files.exists(FX_CVT_PATH)) return 0;
        AtomicInteger count = new AtomicInteger();
        try {
            Files.createDirectories(Path.of(LDLib2.getAssetsDir() + "/photon/fx"));
        } catch (IOException e) {
            Photon.LOGGER.error(e.getMessage());
        }
        try (var paths = Files.list(FX_CVT_PATH)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                try {
                    CompoundTag photon1FX = NbtIo.readCompressed(path.toFile());
                    CompoundTag photon2FX = mapEffect(photon1FX);
                    // the mappers emit the current format; stamp it so the datafixer stays a no-op
                    photon2FX.putInt("version", FXProject.VERSION);
                    NbtIo.writeCompressed(photon2FX,
                            Path.of(LDLib2.getAssetsDir() + "/photon/fx/" + path.getFileName()).toFile());
                    count.getAndIncrement();
                }
                catch (IOException | IllegalArgumentException e) {
                    Photon.LOGGER.error("Failed to convert FX file {}: {}", path.getFileName(), e.getMessage());
                }
            });
        } catch (IOException e) {
            Photon.LOGGER.error("Failed to read fx_old directory");
        }
        return count.get();
    }

    public static CompoundTag mapEffect(CompoundTag fx) {
        return FXLegacyMapper.mapEffect(fx);
    }

}
