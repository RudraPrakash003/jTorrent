package com.jtorrent;

import com.jtorrent.bencoder.BDecoder;
import com.jtorrent.metaInfo.InfoHash;
import com.jtorrent.peer.PeerManager;
import com.jtorrent.tracker.Tracker;

import java.io.File;
import java.io.FileInputStream;
//import java.io.InputStream;
import java.util.Map;

public class TorrentClient {
    public static void main(String[] args) {
        if(args == null) {
            System.err.println("Torrent file is not provided.");
            System.exit(1);
        }

        File resource = new File(args[0]);

        if(!resource.exists() || !resource.isFile()) {
            throw new IllegalArgumentException("Torrent file does not exist or is not a file.");
        }

//        String resource = "Tron Ares.torrent";
//        ClassLoader cl = TorrentClient.class.getClassLoader();

//        try(InputStream input = cl.getResourceAsStream(resource)) {
        try(FileInputStream input = new FileInputStream(resource)) {
//           if(input == null) throw new RuntimeException("Resource not found: " + resource);

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


           byte[] infoHash = InfoHash.getInfoHash(torrent);
           if(infoHash == null || infoHash.length != 20) {
               throw new RuntimeException("Invalid InfoHash length");
           }
//            System.out.println("Info Hash: " + ByteConvertor.bytesToHex(infoHash));

            Object announceObj = torrent.get("announce");
            if(announceObj == null) {
                throw new RuntimeException("Announce key not found in torrent file");
            }
            String announce = announceObj.toString();
//           System.out.println("Announce: " + announce);

            Tracker tracker = new Tracker();
            tracker.getPeers(announce, infoHash, torrent, peers -> {
                PeerManager peerManager = new PeerManager(infoHash);
                peerManager.connectToPeers(peers);
            });

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}