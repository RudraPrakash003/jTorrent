package com.jtorrent.tracker;

import com.jtorrent.model.Peer;
import com.jtorrent.network.UdpClient;
import com.jtorrent.service.AnnounceService;
import com.jtorrent.service.ConnectionService;

import java.net.*;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class Tracker {
    public void getPeers(String announce, byte[] infoHash, Map<String, Object> torrent, Consumer<List<Peer>> callback) {
        try (UdpClient udp = new UdpClient()) {

            URI uri = new URI(announce);
            String host = uri.getHost();
            int port = uri.getPort() == -1 ? 6969 : uri.getPort();

//            System.out.println(announce);
//            System.out.println(host + ":" + port);

            long connectionId = ConnectionService.connect(udp, host, port);
            List<Peer> peers = AnnounceService.announce(udp, connectionId, infoHash, torrent, host, port);

            callback.accept(peers);

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
