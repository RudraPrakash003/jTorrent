package com.jtorrent;

import com.jtorrent.bencoder.BDecoder;
import com.jtorrent.tracker.Tracker;
import com.jtorrent.metaInfo.InfoHash;

import java.io.InputStream;
import java.util.Map;

public class TorrentClient {
    public static void main(String[] args) {
        String resource = "Tron Ares.torrent";
        ClassLoader cl = TorrentClient.class.getClassLoader();

        try(InputStream input = cl.getResourceAsStream(resource)) {
            if(input == null) throw new RuntimeException("Resource not found: " + resource);

            byte[] data = input.readAllBytes();
//              String text = new String(data, StandardCharsets.ISO_8859_1);
//              System.out.println(text);

            BDecoder decoder = new BDecoder(data);
            Object decoded =  decoder.decode();
            if(!(decoded instanceof Map)) {
                throw new RuntimeException("Decode error");
            }
            Map<String, Object> torrent = (Map<String, Object>) decoded;

//              torrent.forEach((k,v)->{ System.out.println(k + ":" + v);});
            Object announceObj = torrent.get("announce");
            if(announceObj == null) {
                throw new RuntimeException("Announce key not found in torrent file");
            }
            String announce = announceObj.toString();
//            System.out.println("Announce: " + announce);

            byte[] infoHash = InfoHash.getInfoHash(torrent);
            if(infoHash == null || infoHash.length != 20) {
                throw new RuntimeException("Invalid InfoHash length");
            }
//            System.out.println("Info Hash: " + ByteConvertor.bytesToHex(infoHash));


            Tracker tracker = new Tracker();
            tracker.getPeers(announce, infoHash, torrent, peers -> {
                System.out.println("Peers:");
                peers.forEach(System.out::println);
            });
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}