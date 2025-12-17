package com.jtorrent.bencoder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class BEncoder {

    public static byte[] encode(Object obj) {
        StringBuilder sb = new StringBuilder();
        encodeInternal(obj, sb);
        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private static void encodeInternal(Object obj, StringBuilder sb) {
        switch (obj) {
            case String s -> {
                byte[] bytes = s.getBytes(StandardCharsets.ISO_8859_1);
                sb.append(bytes.length).append(':');
                sb.append(s);
            }
            case Number n -> sb.append('i').append(n.longValue()).append('e');
            case List<?> list -> {
                sb.append('l');
                for (Object item : list) {
                    encodeInternal(item, sb);
                }
                sb.append('e');
            }
            case Map<?, ?> map -> {
                sb.append('d');
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String key = entry.getKey().toString();
                    byte[] keyBytes = key.getBytes(StandardCharsets.ISO_8859_1);
                    sb.append(keyBytes.length).append(':').append(key);
                    encodeInternal(entry.getValue(), sb);
                }
                sb.append('e');
            }
            default -> throw new IllegalArgumentException("Cannot bencode type: " + obj.getClass());
        }
    }
}
