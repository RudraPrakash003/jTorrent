package com.jtorrent.tracker;

import java.nio.ByteBuffer;

public class ResponseType {
    public static String getType(byte[] response) {
        if(response == null || response.length < 4) {
            return "unknown";
        }

        int action = ByteBuffer.wrap(response).getInt(0);

        return switch (action) {
            case 0 -> "connect";
            case 1 -> "announce";
            case 3 -> "error";
            default -> "unknown";
        };
    }
}
