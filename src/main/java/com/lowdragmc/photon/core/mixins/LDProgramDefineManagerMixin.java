package com.lowdragmc.photon.core.mixins;

import com.lowdragmc.lowdraglib2.client.shader.LDProgramDefineManager;
import com.lowdragmc.photon.client.shader.ProgramCacheName;
import com.lowdragmc.photon.client.shader.ProgramDefineSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Veil parses every compiled program name as a Minecraft ResourceLocation. LDLib2 normally adds
 * a GLSL {@code #define ...} block to that name as a cache discriminator, which is not a valid
 * ResourceLocation path and makes Veil fail before custom Photon shaders can link.
 *
 * @author Photon
 * @reason Preserve variant cache isolation using a key valid to mods that parse it as a resource ID.
 */
@Mixin(value = LDProgramDefineManager.class, remap = false)
public abstract class LDProgramDefineManagerMixin {

    /**
     * LDLib2 concatenates {@code #define} directly with each value. GLSL requires whitespace
     * between the directive and the macro name, otherwise every instanced shader fails to compile.
     * Keep define values themselves unchanged for LDLib2 callers and fix only generated source.
     *
     * @author Photon
     * @reason Emit valid GLSL while preserving LDLib2's bare define values and delimiter API.
     */
    @Overwrite(remap = false)
    public static String createProgramDefinesString(char delimiter) {
        return ProgramDefineSource.format(LDProgramDefineManager.getProgramDefines(), delimiter);
    }

    /**
     * Keep LDLib2's per-define program cache isolation with a ResourceLocation-safe key.
     *
     * @author Photon
     * @reason Veil parses shader program cache keys as ResourceLocations.
     */
    @Overwrite(remap = false)
    public static String createProgramNameWithDefines(String name) {
        if (!LDProgramDefineManager.hasProgramDefines()) return name;
        return ProgramCacheName.withDefines(name, LDProgramDefineManager.createProgramDefinesString('_'));
    }
}
