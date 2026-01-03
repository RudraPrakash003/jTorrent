package com.jtorrent.tracker;

import com.jtorrent.peer.Peer;

import java.net.*;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class Tracker {
    public void getPeers(String announce, byte[] infoHash, Map<String, Object> torrent, byte[] peerId, Consumer<List<Peer>> callback) {
        try (UdpClient udp = new UdpClient()) {

            URI uri = new URI(announce);
            String host = uri.getHost();
            int port = uri.getPort() == -1 ? 6969 : uri.getPort();

//            System.out.println(announce);
//            System.out.println(host + ":" + port);

            long connectionId = TrackerConnection.connect(udp, host, port);
            List<Peer> peers = TrackerAnnounce.announce(udp, connectionId, infoHash, torrent, host, port, peerId);

            callback.accept(peers);

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
