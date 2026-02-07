package com.jtorrent;

import com.jtorrent.metaInfo.InfoHash;
import com.jtorrent.metaInfo.TorrentMetaData;
import com.jtorrent.peer.PeerManager;
import com.jtorrent.tracker.Tracker;
import com.jtorrent.metaInfo.ClientId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Map;

public class TorrentClient {

    private static final Logger log = LoggerFactory.getLogger(TorrentClient.class );

    public static void main(String[] args) {
        if(args == null) {
            log.error("Torrent file is not provided.");
            System.exit(1);
        }

        File resource = new File(args[0]);

        if(!resource.exists() || !resource.isFile()) {
            log.error("Invalid torrent file path: {}", resource.getAbsolutePath());
            System.exit(1);
        }

        try(FileInputStream input = new FileInputStream(resource)) {
           byte[] data = input.readAllBytes();
           Map<String, Object> torrent = TorrentMetaData.decode(data);

           log.info("Torrent file decoded successfully.");
//           System.out.println("Torrent File:");
//           torrent.forEach((k,v)-> System.out.println(k + ":" + v));
            byte[] infoHash = InfoHash.getInfoHash(torrent); // receives SHA1 of BeEncoded info
            if(infoHash == null || infoHash.length != 20) {
                throw new RuntimeException("Invalid InfoHash length");
            }

            int pieceCount = TorrentMetaData.pieceCount(torrent);
            int pieceLength = TorrentMetaData.pieceLength(torrent);
            long totalSize = TorrentMetaData.totalSize(torrent);
            String announce = TorrentMetaData.announce(torrent);
            List<byte[]> pieceHashes = TorrentMetaData.pieceHashes(torrent);
            String fileName = TorrentMetaData.fileName(torrent);

            log.info("Torrent info: name={}, size={}, pieces count={}, pieceLength={}", fileName, totalSize, pieceCount, pieceLength);
            String outputPath = "D:/" + fileName;

            log.info("Torrent download path: {}", outputPath);
            byte[] peerId = ClientId.generateId();

            log.info("Connecting to Tracker: {}", announce);
            Tracker tracker = new Tracker();
            tracker.getPeers(announce, infoHash, torrent, peerId, peers -> {
                PeerManager peerManager = new PeerManager(infoHash, peerId, pieceCount, pieceLength, totalSize, pieceHashes, outputPath);
                peerManager.connectToPeers(peers);
            });

        } catch (Exception e) {
            log.error("Fatal error in TorrentClient", e);
            System.exit(1);
        }
    }
}