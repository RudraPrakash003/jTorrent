package com.jtorrent.tracker;

import com.jtorrent.peer.Peer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class Tracker {
    private static final Logger log = LoggerFactory.getLogger(Tracker.class);
    public void getPeers(String announce, byte[] infoHash, Map<String, Object> torrent, byte[] peerId, Consumer<List<Peer>> callback) {
        try (UdpClient udp = new UdpClient()) {
            URI uri = new URI(announce);
            String host = uri.getHost();
            int port = uri.getPort() == -1 ? 6969 : uri.getPort();
//            System.out.println(announce);
//            System.out.println(host + ":" + port);
            long connectionId = TrackerConnection.connect(udp, host, port);
            log.info("Tracker connection established {}:{} connectionId={}", host, port, connectionId);

            List<Peer> peers = TrackerAnnounce.announce(udp, connectionId, infoHash, torrent, host, port, peerId);
            log.info("Tracker announce successful {}:{} peers={} received", host, port, peers.size());

            callback.accept(peers);
        } catch (Exception e) {
            log.error("Error in getting Peers", e);
            throw new RuntimeException(e);
        }
    }
}
