package com.jtorrent.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class Buffers {

    private Buffers() {}

    public static ByteBuffer allocate(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
    }

    public static ByteBuffer wrap(byte[] data) {
        return ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
    }

    public static ByteBuffer message(int payloadLength) {
        return allocate(4 + payloadLength)
                .putInt(payloadLength);
    }
}
