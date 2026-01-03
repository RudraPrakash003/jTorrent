package com.jtorrent;

import com.jtorrent.metaInfo.InfoHash;
import com.jtorrent.metaInfo.TorrentMetaData;
import com.jtorrent.peer.PeerManager;
import com.jtorrent.tracker.Tracker;
import com.jtorrent.metaInfo.ClientId;

import java.io.File;
import java.io.FileInputStream;
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

        try(FileInputStream input = new FileInputStream(resource)) {
           byte[] data = input.readAllBytes();

           Map<String, Object> torrent = TorrentMetaData.decode(data);

//           System.out.println("Torrent File:");
//           torrent.forEach((k,v)->{ System.out.println(k + ":" + v);});

            byte[] infoHash = InfoHash.getInfoHash(torrent); // receives SHA1 of BeEncoded info
            if(infoHash == null || infoHash.length != 20) {
                throw new RuntimeException("Invalid InfoHash length");
            }

            byte[] peerId = ClientId.generateId();

            String announce = TorrentMetaData.announce(torrent);
//           System.out.println("Announce: " + announce);

            Tracker tracker = new Tracker();
            tracker.getPeers(announce, infoHash, torrent, peerId, peers -> {
                PeerManager peerManager = new PeerManager(infoHash, peerId);
                peerManager.connectToPeers(peers);
            });

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}