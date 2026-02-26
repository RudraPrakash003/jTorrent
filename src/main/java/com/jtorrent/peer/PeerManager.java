package com.jtorrent.peer;

import com.jtorrent.piece.BlockTracker;
import com.jtorrent.piece.PieceManager;
import com.jtorrent.statistics.ProgressBar;
import com.jtorrent.scheduler.RequestScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class PeerManager {

    private static final Logger log = LoggerFactory.getLogger(PeerManager.class);

    private static final int MAX_PEERS = 30;
    private static final int MAX_PEER_FAILURES = 3;

    private final byte[] infoHash;
    private final byte[] peerId;
    private final int pieceCount;
    private final int pieceLength;
    private final long totalSize;

    private final BlockTracker blockTracker;
    private final RequestScheduler requestScheduler;
    private final PieceManager pieceManager;
    private final ExecutorService peerPool;

    private final Map<Peer, Integer> connectionFailures= new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public PeerManager(byte[] infoHash, byte[] peerId, int pieceCount, int pieceLength, long totalSize, List<byte[]> pieceHashes, String outputPath) {
        this.infoHash = infoHash;
        this.peerId = peerId;
        this.pieceCount = pieceCount;
        this.pieceLength = pieceLength;
        this.totalSize = totalSize;

        try {
            this.pieceManager = new PieceManager(pieceCount, pieceLength, pieceHashes, totalSize, outputPath);
        } catch (IOException e) {
            log.error("Unable to create output file at {}", outputPath);
            throw new RuntimeException("Error while creating output file: " + e.getMessage());
        }
        this.blockTracker = new BlockTracker(pieceCount, pieceLength, totalSize);
        this.requestScheduler = new RequestScheduler(blockTracker, pieceManager);


        this.peerPool = Executors.newFixedThreadPool(MAX_PEERS);

        startProgressMonitor();
        log.info("Progress Monitoring started");
    }

    public void connectToPeers(List<Peer> peers) {
        log.info("Received {} peers from tracker", peers.size());
        Collections.shuffle(peers);
        peers.stream()
                .filter(PeerSelector::isValid)
                .sorted(PeerSelector::peerPriority)
                .limit(MAX_PEERS)
                .forEach(peer -> peerPool.submit(() -> startSession(peer)));
        log.info("Attempting connections up to {} peers,", MAX_PEERS);
    }

    public void startSession(Peer peer) {
        Thread.currentThread().setName("peer-" + peer);
        PeerConnection connection = new PeerConnection(peer, infoHash, peerId, pieceCount, pieceLength, totalSize, requestScheduler);
        try {
            log.info("Connecting to peer {}", peer);
            connection.connect();

            log.info("Handshake started with peer {} ", peer);
            connection.handshake();

            connectionFailures.remove(peer);

            log.info("Session established with Peer {}", peer);
            connection.startMessageLoop();
        } catch (SocketTimeoutException e) {
            handleFailures(peer, "Connection timed out");
        } catch (IOException e) {
            handleFailures(peer, "I/O error: " + e.getMessage());
        }
        catch (Exception e) {
            handleFailures(peer, "Unexpected error: " + e.getClass().getSimpleName());
        }
        finally {
            connection.closeQuietly();
        }
    }

    private void handleFailures(Peer peer, String reason) {
        int failures = connectionFailures.merge(peer, 1, Integer::sum);
        log.warn("Connection failed for {} ({}/{}, {}", peer, failures, MAX_PEER_FAILURES, reason);

        if(failures < MAX_PEER_FAILURES) {
            int delaySeconds = (int) Math.pow(2, failures) * 30;
            log.info("Retrying connection with {}, in {} seconds", peer, delaySeconds);
            scheduler.schedule(() -> startSession(peer), delaySeconds, TimeUnit.SECONDS);
        } else {
            log.info("Connection failed for {} after {} retries.", peer, MAX_PEER_FAILURES);
        }
    }

    private void startProgressMonitor() {
        Thread monitor = new Thread(() -> {
            while(!pieceManager.isComplete()) {
                try {
                    Thread.sleep(5000);
                    ProgressBar.showProgressBar(pieceManager.getDownloaded(), totalSize);
                } catch (InterruptedException e) {
                    log.debug("Progress monitor interrupted");
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            log.info("Download Complete");
            shutdown();
        });
        monitor.setName("progress-monitor");
        monitor.setDaemon(true);
        monitor.start();
    }

    public void shutdown() {
        log.info("Shutting down");
        peerPool.shutdown();
        try {
            if (!peerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Peer pool did not gracefully shutdown - forcing shutdown");
                peerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("Shutdown interrupted");
            peerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        try {
            pieceManager.close();
        } catch (Exception e) {
            System.err.println("Error closing piece manager - "  + e.getMessage());
        }
    }
}
