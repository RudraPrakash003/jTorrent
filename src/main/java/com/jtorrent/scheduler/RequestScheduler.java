package com.jtorrent.scheduler;

import com.jtorrent.peer.PeerConnection;
import com.jtorrent.peer.PeerMessageBuilder;
import com.jtorrent.piece.Block;
import com.jtorrent.piece.BlockTracker;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

public class RequestScheduler {

    private static final int PIPELINE_DEPTH = 5;

    private final BlockTracker blockTracker;

    private final Map<PeerConnection, Integer> inFlight = new HashMap<>();

    public RequestScheduler(BlockTracker blockTracker) {
        this.blockTracker = blockTracker;
    }

    public synchronized void onUnchoke(PeerConnection peer) {
        if(peer.isPeerChoking()) return;
        requestMore(peer);
    }

    public  synchronized void onBlockReceived(PeerConnection peer, int pieceIndex, int begin, byte[] block) {
        int blockIndex = begin / BlockTracker.BLOCK_SIZE;
        blockTracker.markReceived(pieceIndex, blockIndex);

        inFlight.put(peer, inFlight.get(peer) - 1);
        requestMore(peer);
    }

    public void requestMore(PeerConnection peer) {
        if(peer.isPeerChoking()) return;

        int outstanding = inFlight.getOrDefault(peer, 0);

        while(outstanding < PIPELINE_DEPTH) {
            int piece = pickPiece(peer.getBitfield());
            if(piece == -1) return;

            Block block = blockTracker.nextBlockToRequest(piece);
            if(block == null) return;

            peer.send(PeerMessageBuilder.buildRequest(
                    block.pieceIndex(),
                    block.offset(),
                    block.length()
            ));

            outstanding++;
            inFlight.put(peer, outstanding);
        }
    }

    public int pickPiece(BitSet bitfield) {
        if(bitfield == null) return -1;
        return bitfield.nextSetBit(0); //change
    }
}
