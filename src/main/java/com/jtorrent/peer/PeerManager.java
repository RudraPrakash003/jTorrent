package com.jtorrent.peer;

import com.jtorrent.model.Peer;

import java.util.List;

public class PeerManager {

    private static final int MAX_PEERS = 5;
    private final byte[] infoHash;

    public PeerManager(byte[] infoHash) {
        this.infoHash = infoHash;
    }

    public void connectToPeers(List<Peer> peers) {
        System.out.println("Connecting to peers");

        peers.stream().limit(MAX_PEERS).forEach(this::connectPeer);
    }

    public void connectPeer(Peer peer) {
        PeerConnection connection = new PeerConnection(peer, infoHash);
        try {
            connection.connect();
            connection.handshake();

            Thread t = new Thread(() -> {
                try{
                    connection.startMessageLoop();
                } catch (Exception e) {
                    connection.closeQuietly();
                }
            });
            t.start();
        } catch (Exception e) {
            connection.closeQuietly();
            System.out.println("Connection failed for Peer: " + peer);
        }
    }
}
