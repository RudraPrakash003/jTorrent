package com.jtorrent.peer;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PeerManager {

    private static final int MAX_PEERS = 20;
    private final byte[] infoHash;
    private final byte[] peerId;
    private final ExecutorService peerPool;

    public PeerManager(byte[] infoHash, byte[] peerId) {
        this.infoHash = infoHash;
        this.peerId = peerId;
        this.peerPool = Executors.newFixedThreadPool(MAX_PEERS);
    }

    public void connectToPeers(List<Peer> peers) {
        System.out.println("Connecting to peers");

        peers.stream()
                .filter(PeerSelector::isValid)
                .sorted(PeerSelector::peerPriority)
                .limit(MAX_PEERS)
                .forEach(this::connectPeer);
    }

    public void connectPeer(Peer peer) {
        PeerConnection connection = new PeerConnection(peer, infoHash, peerId);
        try {
            connection.connect();
            System.out.println("Connected to peer: " + peer);
            connection.handshake();

            peerPool.submit(() -> {
                try{
                    connection.startMessageLoop();
                } catch (Exception ex) {
                    connection.closeQuietly();
                }
            });
        } catch (Exception e) {
            connection.closeQuietly();
            System.out.println("Connection failed for Peer: " + peer + " -> " + e.getClass().getSimpleName());
        }
    }


    public void shutdown() {
        peerPool.shutdown();
        try {
            if (!peerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                peerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            peerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
