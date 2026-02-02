package com.jtorrent.metaInfo;

import com.jtorrent.bencoder.BDecoder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

    public static int pieceLength(Map<String, Object> torrent) {
        Object pieceLength = info(torrent).get("piece length");

        if(!(pieceLength instanceof Number)) {
            throw new IllegalArgumentException("Invalid torrent piece length");
        }
        return ((Number) pieceLength).intValue();
    }

    public static int pieceCount(Map<String, Object> torrent) {
        return (int) ((totalSize(torrent) + pieceLength(torrent) - 1)/ pieceLength(torrent));
    }

    public static List<byte[]> pieceHashes(Map<String, Object> torrent) {
        Object piecesObj = info(torrent).get("pieces");

        byte[] pieces;
        if(piecesObj instanceof byte[]) {
            pieces = (byte[]) piecesObj;
        } else if(piecesObj instanceof String) {
            pieces = ((String) piecesObj).getBytes(StandardCharsets.ISO_8859_1);
        } else {
            throw new IllegalArgumentException("Invalid torrent pieces");
        }

        if(pieces.length % 20 != 0) {
            throw new IllegalArgumentException("Invalid torrent piece hash length");
        }

        int count = pieces.length / 20;
        List<byte[]> hashes = new ArrayList<>(count);

        for(int i = 0; i < count; i++) {
            byte[] hash = new byte[20];
            System.arraycopy(pieces, i * 20, hash, 0, 20);
            hashes.add(hash);
        }
        return hashes;
    }

    public static String fileName(Map<String, Object> torrent) {
        Object fileNameObj = info(torrent).get("name");
        if(!(fileNameObj instanceof String)) {
            throw new IllegalArgumentException("Invalid torrent name");
        }

        return (String) fileNameObj;
    }
}