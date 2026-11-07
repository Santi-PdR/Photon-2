package com.lowdragmc.photon.client.util;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

/** Compatibility helpers for the 1.20.1 BufferBuilder lifecycle. */
public final class PhotonBuffers {
    private PhotonBuffers() {}

    public static BufferBuilder begin(Tesselator tesselator, VertexFormat.Mode mode, VertexFormat format) {
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(mode, format);
        return builder;
    }
}
