package com.jtorrent.bencoder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BDecoder {

    private final byte[] data;
    private int id = 0;

    public BDecoder(byte[] data) {
        this.data = data;
    }

    public Object decode() {
        return decodeNext();
    }

    private Object decodeNext() {
        if (id >= data.length) {
            throw new RuntimeException("Unexpected end of data while decoding");
        }

        byte b = data[id];

        if (b == 'i') {
            return decodeInteger();
        } else if (b == 'l') {
            return decodeList();
        } else if (b == 'd') {
            return decodeDictionary();
        } else if (b >= '0' && b <= '9') {
            return decodeString();
        } else {
            throw new RuntimeException("Invalid bencode prefix: " + (char) b + " at id " + id);
        }
    }

    private long decodeInteger() {
        if (data[id] != 'i') {
            throw new RuntimeException("Expected 'i' at id " + id);
        }
        id++;

        int start = id;
        while (id < data.length && data[id] != 'e') {
            id++;
        }
        if (id >= data.length) {
            throw new RuntimeException("Integer not terminated");
        }

        String intStr = new String(data, start, id - start, StandardCharsets.US_ASCII);
        id++;

        return Long.parseLong(intStr);
    }

    private String decodeString() {
        int length = 0;
        while (id < data.length && data[id] != ':') {
            byte b = data[id];
            if (b < '0' || b > '9') {
                throw new RuntimeException("Invalid string length character at id " + id);
            }
            length = length * 10 + (b - '0');
            id++;
        }

        if (id >= data.length || data[id] != ':') {
            throw new RuntimeException("Missing ':' after string length at id " + id);
        }
        id++;

        if (id + length > data.length) {
            throw new RuntimeException("String length out of bounds");
        }

        String value = new String(data, id, length, StandardCharsets.ISO_8859_1);
        id += length;
        return value;
    }

    private List<Object> decodeList() {
        if (data[id] != 'l') {
            throw new RuntimeException("Expected 'l' at id " + id);
        }
        id++; 

        List<Object> list = new ArrayList<>();
        while (id < data.length && data[id] != 'e') {
            list.add(decodeNext());
        }

        if (id >= data.length || data[id] != 'e') {
            throw new RuntimeException("List not terminated");
        }
        id++;

        return list;
    }

    private Map<String, Object> decodeDictionary() {
        if (data[id] != 'd') {
            throw new RuntimeException("Expected 'd' at id " + id);
        }
        id++;

        Map<String, Object> map = new LinkedHashMap<>();
        while (id < data.length && data[id] != 'e') {
            String key = decodeString();
            Object value = decodeNext();
            map.put(key, value);
        }

        if (id >= data.length || data[id] != 'e') {
            throw new RuntimeException("Dictionary not terminated");
        }
        id++;

        return map;
    }
}
