package com.jtorrent.scheduler;

import com.jtorrent.peer.PeerConnection;
import com.jtorrent.peer.PeerMessageBuilder;
import com.jtorrent.piece.Block;
import com.jtorrent.piece.BlockTracker;
import com.jtorrent.piece.PieceManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RequestScheduler {

    private static final int PIPELINE_DEPTH = 10;

    private final BlockTracker blockTracker;
    private final PieceManager pieceManager;

    private final Map<PeerConnection, Integer> inFlight = new ConcurrentHashMap<>();
    private final Map<PeerConnection, Integer> currentPiece = new ConcurrentHashMap<>();
    private final Set<Integer> activePieces = ConcurrentHashMap.newKeySet();

    public RequestScheduler(BlockTracker blockTracker, PieceManager pieceManager) {
        this.blockTracker = blockTracker;
        this.pieceManager = pieceManager;
    }

    public synchronized void onUnchoke(PeerConnection peer) {
        if(peer.isPeerChoking()) return;
        requestMore(peer);
    }

    public synchronized void onBlockReceived(PeerConnection peer, int pieceIndex, int begin, byte[] block) {
        int blockIndex = begin / BlockTracker.BLOCK_SIZE;

        pieceManager.addBlock(pieceIndex, begin, block);
        blockTracker.markReceived(pieceIndex, blockIndex);

        if(blockTracker.isPieceComplete(pieceIndex)) {
            System.out.println("Piece " + pieceIndex + " is being verified...");
            if(!pieceManager.verifyAndSavePiece(pieceIndex)) {
                blockTracker.resetPiece(pieceIndex);
            }
            currentPiece.remove(peer);
            activePieces.remove(pieceIndex);
        }

        inFlight.merge(peer, -1, Integer::sum);
        requestMore(peer);
    }

    public void onPeerDisconnected(PeerConnection peer) {
        inFlight.remove(peer);
        Integer piece = currentPiece.remove(peer);
        if(piece != null) {
            activePieces.remove(piece);
        }
    }

    public void requestMore(PeerConnection peer) {
        if(peer.isPeerChoking()) return;

        int outstanding = inFlight.getOrDefault(peer, 0);

        while(outstanding < PIPELINE_DEPTH) {
            int piece = currentPiece.computeIfAbsent(peer, p -> pickPiece(peer.getBitfield()));
            if(piece == -1) {
                piece = pickPiece(peer.getBitfield());
                if(piece == -1) return;
                currentPiece.put(peer, piece);
            }

            Block block = blockTracker.nextBlockToRequest(piece);
            if(block == null) {
                currentPiece.remove(peer);
                piece =  pickPiece(peer.getBitfield());
                if(piece == -1) return;
                currentPiece.put(peer, piece);
                block = blockTracker.nextBlockToRequest(piece);
                if(block == null) return;
            }

            peer.send(PeerMessageBuilder.buildRequest(
                    block.pieceIndex(),
                    block.offset(),
                    block.length()
            ));

            System.out.println("Requested for the pieceIndex: " + block.pieceIndex() + ", offset: " + block.offset() + ", length: " + block.length());

            outstanding++;
            inFlight.put(peer, outstanding);
        }
    }

    public int pickPiece(BitSet bitfield) {
        if(bitfield == null) return -1;

        BitSet completed = pieceManager.getCompletedPieces();
        List<Integer> available = new ArrayList<>();

        for(int i = bitfield.nextSetBit(0); i >= 0; i = bitfield.nextSetBit(i + 1)) {
            if(!completed.get(i) && !activePieces.contains(i)) {
                available.add(i);
            }
        }

        if(available.isEmpty()) return -1;

        int selected = available.get(new Random().nextInt(available.size()));
        activePieces.add(selected);
        return selected;
    }
}
