package com.jtorrent.peer;

import com.jtorrent.piece.BlockTracker;
import com.jtorrent.piece.PieceManager;
import com.jtorrent.scheduler.RequestScheduler;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PeerManager {

    private static final int MAX_PEERS = 20;

    private final byte[] infoHash;
    private final byte[] peerId;

    private final int pieceCount;
    private final int pieceLength;

    private final long totalSize;

    private final BlockTracker blockTracker;
    private final RequestScheduler scheduler;
    private final PieceManager pieceManager;

    private final ExecutorService peerPool;

    public PeerManager(byte[] infoHash, byte[] peerId, int pieceCount, int pieceLength, long totalSize, List<byte[]> pieceHashes, String outputPath) {
        this.infoHash = infoHash;
        this.peerId = peerId;
        this.pieceCount = pieceCount;
        this.pieceLength = pieceLength;
        this.totalSize = totalSize;

        this.blockTracker = new BlockTracker(pieceCount, pieceLength, totalSize);
        this.pieceManager = new PieceManager(pieceCount, pieceLength, pieceHashes, totalSize, outputPath);
        this.scheduler = new RequestScheduler(blockTracker, pieceManager);

        this.peerPool = Executors.newFixedThreadPool(MAX_PEERS);
    }

    public void connectToPeers(List<Peer> peers) {
        System.out.println("Connecting to peers");

        peers.stream()
                .filter(PeerSelector::isValid)
                .sorted(PeerSelector::peerPriority)
                .limit(MAX_PEERS)
                .forEach(peer -> peerPool.submit(() -> startSession(peer)));
    }

    public void startSession(Peer peer) {
        PeerConnection connection = new PeerConnection(peer, infoHash, peerId, pieceCount, pieceLength, totalSize, scheduler);
        try {
            connection.connect();
            System.out.println("Connected to peer: " + peer);
            connection.handshake();
            connection.startMessageLoop();
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
