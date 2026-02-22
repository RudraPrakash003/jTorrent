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
        return (Map<String, Object>) torrent.get("info");
    }

    public static String announce(Map<String, Object> torrent) {
        return torrent.get("announce").toString();
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
        return ((Number) info.get("length")).longValue();
    }

    public static int pieceLength(Map<String, Object> torrent) {
        return ((Number) info(torrent).get("piece length")).intValue();
    }

    public static int pieceCount(Map<String, Object> torrent) {
        return (int) ((totalSize(torrent) + pieceLength(torrent) - 1)/ pieceLength(torrent));
    }

    public static byte[] piecesRaw(Map<String, Object> torrent) {
        Object piecesObj = info(torrent).get("pieces");
        if(piecesObj instanceof byte[])
            return  (byte[]) piecesObj;
        else if(piecesObj instanceof String)
            return  ((String) piecesObj).getBytes(StandardCharsets.ISO_8859_1);
        else throw new IllegalArgumentException("Invalid torrent pieces");
    }

    public static List<byte[]> pieceHashes(Map<String, Object> torrent) {
        byte[] pieces = piecesRaw(torrent);
        
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
        return (String) info(torrent).get("name");
    }
}