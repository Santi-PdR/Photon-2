package com.lowdragmc.photon.client.gameobject.emitter.data;

import com.lowdragmc.photon.client.gameobject.particle.IParticle;
import org.junit.jupiter.api.Test;

import java.nio.FloatBuffer;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdditionalGPUAttribLayoutTest {
    @Test
    void exposesTheLiveAttributePlanInFloatOffsets() throws NoSuchMethodException {
        assertEquals(java.util.List.class,
                AdditionalGPUDataSetting.class.getMethod("planAttribs", int.class).getReturnType());
        assertEquals(int.class, AdditionalGPUDataSetting.TailAttrib.class.getMethod("location").getReturnType());
        assertEquals(int.class, AdditionalGPUDataSetting.TailAttrib.class.getMethod("floats").getReturnType());
        assertEquals(int.class, AdditionalGPUDataSetting.TailAttrib.class.getMethod("offsetFloats").getReturnType());
        assertEquals(16, AdditionalGPUDataSetting.MAX_VERTEX_ATTRIBUTES);
    }

    @Test
    void livePlannerReturnsFloatOffsetsAndRefreshesTheUploadPlan() {
        var setting = new AdditionalGPUDataSetting() {
            @Override
            public PhotonGpuChannels.Kind kind() {
                return PhotonGpuChannels.Kind.TILE;
            }

            @Override
            protected Set<String> enabledChannelIds() {
                return Set.of("addition_gpu_data.random", "addition_gpu_data.position");
            }

            @Override
            protected void uploadChannel(PhotonGpuChannels.Channel channel, IParticle particle,
                                         FloatBuffer target, float partialTicks) {
            }
        };
        setting.setEnable(true);

        var attributes = setting.planAttribs(5);

        assertEquals(2, attributes.size());
        assertEquals(new AdditionalGPUDataSetting.TailAttrib(8, 1, 5), attributes.get(0));
        assertEquals(new AdditionalGPUDataSetting.TailAttrib(9, 3, 6), attributes.get(1));
        assertEquals(4, setting.attribFloats());
    }

    @Test
    void capsTileCustomShaderAttributesAtSixteenAndKeepsRecordOffsetsAligned() {
        var kind = PhotonGpuChannels.Kind.TILE;
        var mask = PhotonGpuChannels.supportedMask(kind);
        int initialByteOffset = 12;
        long uploadableChannels = PhotonGpuChannels.CHANNELS.stream()
                .filter(channel -> (mask & channel.bit()) != 0
                        && channel.supported().contains(kind) && channel.uploadable())
                .count();
        assertTrue(kind.baseAttribLocation + uploadableChannels + AdditionalGPUDataSetting.MAX_CUSTOM_DATA
                > AdditionalGPUDataSetting.MAX_VERTEX_ATTRIBUTES);
        var attributes = AdditionalGPUDataSetting.planAttribs(kind, mask,
                AdditionalGPUDataSetting.MAX_CUSTOM_DATA, initialByteOffset);

        assertEquals(AdditionalGPUDataSetting.MAX_VERTEX_ATTRIBUTES - kind.baseAttribLocation, attributes.size());
        int expectedLocation = kind.baseAttribLocation;
        int expectedOffset = initialByteOffset;
        int plannedIndex = 0;
        for (var channel : PhotonGpuChannels.CHANNELS) {
            if ((mask & channel.bit()) == 0 || !channel.supported().contains(kind) || !channel.uploadable()) continue;
            if (expectedLocation < AdditionalGPUDataSetting.MAX_VERTEX_ATTRIBUTES) {
                var attribute = attributes.get(plannedIndex++);
                assertEquals(expectedLocation, attribute.location());
                assertEquals(channel.floats(), attribute.floats());
                assertEquals(expectedOffset, attribute.byteOffset());
            }
            expectedLocation++;
            expectedOffset += channel.floats() * Float.BYTES;
        }
        for (int i = 0; i < AdditionalGPUDataSetting.MAX_CUSTOM_DATA; i++) {
            if (expectedLocation < AdditionalGPUDataSetting.MAX_VERTEX_ATTRIBUTES) {
                var attribute = attributes.get(plannedIndex++);
                assertEquals(expectedLocation, attribute.location());
                assertEquals(expectedOffset, attribute.byteOffset());
            }
            expectedLocation++;
            expectedOffset += 4 * Float.BYTES;
        }

        assertEquals(attributes.size(), plannedIndex);
        assertTrue(attributes.stream().allMatch(attribute ->
                attribute.location() < AdditionalGPUDataSetting.MAX_VERTEX_ATTRIBUTES));
    }

    @Test
    void modelCustomShaderTimeChannelKeepsLocationNineWhenRandomIsNotEnabled() {
        var kind = PhotonGpuChannels.Kind.TILE_MODEL;
        var timeMask = PhotonGpuChannels.maskOf(kind, Set.of("addition_gpu_data.t"));

        var attributes = AdditionalGPUDataSetting.planAttribs(kind, timeMask, 0, 0);

        assertEquals(1, attributes.size());
        assertEquals(new AdditionalGPUDataSetting.PlannedAttrib(9, 1, 0), attributes.get(0));
    }
}
