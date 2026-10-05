package com.lowdragmc.photon.client.gameobject.emitter.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdditionalGPUAttribLayoutTest {
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
}
