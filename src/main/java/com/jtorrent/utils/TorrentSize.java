package com.jtorrent.utils;

import java.util.List;
import java.util.Map;


public class TorrentSize {

    public static long calculateSize(Map<String, Object> torrent) {
        Map<String,Object> info = (Map<String,Object>)torrent.get("info");
        long size;

        if(info.containsKey("files")) {
            List<Map<String,Object>> files = (List<Map<String, Object>>)info.get("files");
            size = files.stream().mapToLong(f -> ((Number) f.get("length")).longValue()).sum();
        } else {
            size = ((Number)info.get("length")).longValue();
        }

        return size;
    }
}
