package com.lowdragmc.photon.client;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.KGSamplerGl;
import com.lowdragmc.kilagraph.rendertype.compiler.SamplerDefault;
import com.lowdragmc.lowdraglib2.LDLib2;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_LINEAR_MIPMAP_LINEAR;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_NEAREST_MIPMAP_NEAREST;
import static org.lwjgl.opengl.GL11.GL_REPEAT;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/** OpenGL sampler conversion shared by post-FX passes and Shader Graph material overrides. */
@OnlyIn(Dist.CLIENT)
public final class PhotonSamplerState {
    private PhotonSamplerState() {}

    public static KGSamplerGl.GlSampler from(RenderTypeGraphTypes.Sampler2DValue sampler) {
        boolean linear = sampler.filter() == RenderTypeGraphTypes.SamplerFilter.LINEAR;
        int filter = linear ? GL_LINEAR : GL_NEAREST;
        int minFilter = sampler.mipmap()
                ? (linear ? GL_LINEAR_MIPMAP_LINEAR : GL_NEAREST_MIPMAP_NEAREST)
                : filter;
        int address = sampler.address() == RenderTypeGraphTypes.SamplerAddress.REPEAT
                ? GL_REPEAT : GL_CLAMP_TO_EDGE;
        return new KGSamplerGl.GlSampler(minFilter, filter, address, address);
    }

    /** Replace graph defaults only for exposed sampler values explicitly overridden by a material. */
    public static List<KGSamplerGl.Binding> overrides(List<String> samplerNames,
                                                       Map<String, String> variableSamplers,
                                                       Map<String, SamplerDefault> samplerDefaults,
                                                       Map<String, Object> values) {
        var result = new ArrayList<>(KGSamplerGl.resolveBindings(samplerNames, samplerDefaults));
        for (var entry : values.entrySet()) {
            if (!(entry.getValue() instanceof RenderTypeGraphTypes.Sampler2DValue sampler)
                    || !LDLib2.isValidResourceLocation(sampler.location())) continue;
            String samplerName = variableSamplers.getOrDefault(entry.getKey(), entry.getKey());
            int unit = samplerNames.indexOf(samplerName);
            if (unit < 0) continue;
            result.removeIf(binding -> binding.unit() == unit);
            result.add(new KGSamplerGl.Binding(unit, from(sampler)));
        }
        return result;
    }

    public static boolean hasValidSamplerOverride(Map<String, Object> values) {
        return values.values().stream().anyMatch(value ->
                value instanceof RenderTypeGraphTypes.Sampler2DValue sampler
                        && LDLib2.isValidResourceLocation(sampler.location()));
    }
}
