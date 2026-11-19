package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Bounds model-file reads before parsers allocate text, JSON, or binary-buffer structures. */
final class ModelInput {
    static final int MAX_FILE_BYTES = 64 * 1024 * 1024;

    private ModelInput() {
    }

    static byte[] readAllBytes(InputStream input, String description) throws IOException {
        return bounded(input, MAX_FILE_BYTES, description).readAllBytes();
    }

    static InputStream bounded(InputStream input, long limit, String description) {
        if (limit < 0) throw new IllegalArgumentException("negative model input limit");
        return new FilterInputStream(input) {
            private long remaining = limit;

            @Override
            public int read() throws IOException {
                if (remaining == 0) return checkEnd();
                int value = super.read();
                if (value >= 0) remaining--;
                return value;
            }

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                if (length == 0) return 0;
                if (remaining == 0) return checkEnd();
                int count = super.read(bytes, offset, (int) Math.min(length, remaining));
                if (count > 0) remaining -= count;
                return count;
            }

            private int checkEnd() throws IOException {
                if (super.read() < 0) return -1;
                throw new IOException(description + " exceeds the " + limit + " byte model input limit");
            }
        };
    }
}
