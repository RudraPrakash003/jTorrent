package com.jtorrent.piece;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

public class BlockTracker {
    public static final int BLOCK_SIZE = 16 * 1024;

    private final int pieceCount;
    private final int pieceLength;
    private final long totalSize;

    private final Map<Integer, BitSet> requestedBlocks = new HashMap<>();
    private final Map<Integer, BitSet> receivedBlocks = new HashMap<>();

    public BlockTracker(int pieceCount, int pieceLength, long totalSize) {
        this.pieceCount = pieceCount;
        this.pieceLength = pieceLength;
        this.totalSize = totalSize;
    }

    public synchronized Block nextBlockToRequest(int pieceIndex) {
        int blocks = blocksInPiece(pieceIndex);

        BitSet requested = requestedBlocks.computeIfAbsent(pieceIndex, p -> new BitSet(blocks));
        BitSet received = receivedBlocks.computeIfAbsent(pieceIndex, p -> new BitSet(blocks));

        for (int blockIndex = 0; blockIndex < blocks; blockIndex++) {
            if (!requested.get(blockIndex) && !received.get(blockIndex)) {
                int offset = blockIndex * BLOCK_SIZE;
                int length = blockLength(pieceIndex, offset);

                requested.set(blockIndex);
                return new Block(pieceIndex, offset, length, blockIndex);
            }
        }
        return null;
    }

    public synchronized void markReceived (int pieceIndex, int blockIndex){
        receivedBlocks.computeIfAbsent(pieceIndex, p -> new BitSet()).set(blockIndex);
    }

    public synchronized boolean isPieceComplete (int pieceIndex){
        int blocks = blocksInPiece(pieceIndex);
        BitSet received = receivedBlocks.get(pieceIndex);

        return received != null && received.cardinality() == blocks;
    }

    public synchronized void resetPiece (int pieceIndex){
        requestedBlocks.remove(pieceIndex);
        receivedBlocks.remove(pieceIndex);
    }

    public synchronized boolean isBlockRequested (int pieceIndex, int blockIndex) {
        BitSet requested = requestedBlocks.get(pieceIndex);

        return requested != null && requested.get(blockIndex);
    }


    private int blocksInPiece (int pieceIndex){
        int length = pieceSize(pieceIndex);
        return (length + BLOCK_SIZE - 1) / BLOCK_SIZE;
    }

    private int blockLength (int pieceIndex, int offset){
        int length = pieceSize(pieceIndex);
        return Math.min(BLOCK_SIZE, length - offset);
    }

    private int pieceSize ( int pieceIndex){
        if (pieceIndex == pieceCount - 1) {
            long remaining = totalSize - (long) pieceLength * (pieceCount - 1);
            return (int) remaining;
        }
        return pieceLength;
    }
}