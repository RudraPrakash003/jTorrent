package com.jtorrent.metaInfo;

import com.jtorrent.bencoder.BEncoder;

import java.security.MessageDigest;
import java.util.Map;

public class InfoHash {

    public static byte[] getInfoHash(Map<String, Object> torrent) {
        Map<String, Object> infoMap = TorrentMetaData.info(torrent);
/*
        System.out.println("Info Map:");
        infoMap.forEach((k,v)->{System.out.println(k+":"+v);});
*/
        byte[] encodedInfo = BEncoder.encode(infoMap);

        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            return sha1.digest(encodedInfo);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}
