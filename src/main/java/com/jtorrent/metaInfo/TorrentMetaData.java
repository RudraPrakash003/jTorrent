package com.jtorrent.metaInfo;

import com.jtorrent.bencoder.BDecoder;

import java.util.List;
import java.util.Map;

public final class TorrentMetaData {

    private TorrentMetaData() {}

    public static Map<String, Object> decode(byte[] data) {
        BDecoder decoder = new BDecoder(data);
        Object decoded =  decoder.decode();
        if(!(decoded instanceof Map)) {
            throw new RuntimeException("Decode error");
        }
        return (Map<String, Object>)decoded;
    }

    public static Map<String, Object> info(Map<String, Object> torrent) {
        Object info = torrent.get("info");
        if(!(info instanceof Map)) {
            throw new IllegalArgumentException("Missing info dictionary");
        }
        return (Map<String, Object>) info;
    }

    public static String announce(Map<String, Object> torrent) {
        Object announceObj = torrent.get("announce");
        if(announceObj == null) {
            throw new RuntimeException("Announce key not found in torrent file");
        }
        return announceObj.toString();
    }

    public static boolean isMultiFile(Map<String, Object> torrent) {
        return info(torrent).containsKey("files");
    }

    public static long totalSize(Map<String, Object> torrent) {
        Map<String, Object> info = info(torrent);

        if(isMultiFile(torrent)) {
            List<Map<String,Object>> files = (List<Map<String, Object>>)info.get("files");
            return files.stream()
                    .mapToLong(f -> ((Number) f.get("length")).longValue())
                    .sum();
        }
        Object length = info.get("length");
        if (!(length instanceof Number)) {
            throw new IllegalArgumentException("Invalid torrent length");
        }

        return ((Number) length).longValue();
    }
}