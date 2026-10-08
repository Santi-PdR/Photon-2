package com.lowdragmc.photon.client.postfx.runtime;

import com.lowdragmc.photon.client.postfx.graph.SizeSpec;
import com.lowdragmc.photon.client.postfx.graph.TargetFormat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class RenderGraphResourceSizingTest {
    @Test
    void screenRelativeResourcesUseFrameSizeAndInputRelativeResourcesUseTheirProducer() {
        var resources = List.of(
                resource(SizeSpec.screen(0.5f)),
                resource(SizeSpec.screen(1f)),
                resource(SizeSpec.relativeTo(0, 0.25f)));

        var sizes = RenderGraphResourceSizing.resolve(resources, 1920, 1080);

        assertArrayEquals(new int[]{960, 1920, 240}, sizes.widths());
        assertArrayEquals(new int[]{540, 1080, 135}, sizes.heights());
    }

    @Test
    void absoluteDimensionsArePreserved() {
        var sizes = RenderGraphResourceSizing.resolve(
                List.of(resource(SizeSpec.absolute(320, 180))), 1920, 1080);

        assertArrayEquals(new int[]{320}, sizes.widths());
        assertArrayEquals(new int[]{180}, sizes.heights());
    }

    @Test
    void subpixelSizesClampToOne() {
        var sizes = RenderGraphResourceSizing.resolve(
                List.of(resource(SizeSpec.screen(0.0001f))), 320, 180);

        assertArrayEquals(new int[]{1}, sizes.widths());
        assertArrayEquals(new int[]{1}, sizes.heights());
    }

    private static CompiledEffect.ResourceDesc resource(SizeSpec size) {
        return new CompiledEffect.ResourceDesc(size, TargetFormat.RGBA16F, 0, 0, "test");
    }
}
