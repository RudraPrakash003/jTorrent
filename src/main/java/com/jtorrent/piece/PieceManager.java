package com.jtorrent.piece;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PieceManager implements AutoCloseable{

    private final int pieceCount;
    private final int pieceLength;
    private final long totalSize;
    private final List<byte[]> pieceHashes;

    private final Map<Integer, byte[]> pieceData = new ConcurrentHashMap<>();
    private final Map<Integer, BitSet> progressTracker  = new ConcurrentHashMap<>();

    private final BitSet verifiedPieces;
    private final BitSet completedPieces;

    private long totalDownloaded = 0;

    RandomAccessFile file;

    public PieceManager(int pieceCount, int pieceLength, List<byte[]> pieceHashes, long totalSize, String outputPath) throws IOException {
        this.pieceCount = pieceCount;
        this.pieceLength = pieceLength;
        this.totalSize = totalSize;
        this.pieceHashes = pieceHashes;
        this.verifiedPieces = new BitSet(pieceCount);
        this.completedPieces = new BitSet(pieceCount);

        file = new RandomAccessFile(outputPath, "rw");
        file.setLength(totalSize);

    }

    public synchronized void addBlock(int pieceIndex, int begin, byte[] block) {
        byte[] piece = pieceData.computeIfAbsent(pieceIndex, k ->  new byte[getPieceLength(pieceIndex)]);
        System.arraycopy(block, 0, piece, begin, block.length);

        int blockIndex = begin / BlockTracker.BLOCK_SIZE;

        BitSet counted = progressTracker.computeIfAbsent(pieceIndex, k -> new BitSet());
        if(!counted.get(blockIndex)) {
            totalDownloaded += block.length;
            counted.set(blockIndex);
        }
    }

    public synchronized boolean verifyAndSavePiece(int pieceIndex) {
        byte[] piece = pieceData.get(pieceIndex);
        if(piece == null) return false;

        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hash = sha1.digest(piece);

            if(!Arrays.equals(hash, pieceHashes.get(pieceIndex))) {
                System.out.println("The piece " + pieceIndex + " is corrupted");
                pieceData.remove(pieceIndex);
                return false;
            }

            writePieceToDisk(pieceIndex, piece);
            verifiedPieces.set(pieceIndex);
            completedPieces.set(pieceIndex);
            pieceData.remove(pieceIndex, hash);

            System.out.println("Piece " + pieceIndex + " verified and saved. Progress: " + verifiedPieces.cardinality() + "/" + pieceCount);
            return true;
        } catch (Exception e) {
            System.err.println("Error while verifiying piece " + pieceIndex + ": " + e.getMessage());
            return false;
        }
    }


    public BitSet getCompletedPieces() {
        return (BitSet)completedPieces.clone();
    }

    public boolean isComplete(){
        return verifiedPieces.cardinality() == pieceCount;
    }

    public double getProgress() {
        return (double)totalDownloaded / totalSize * 100.0;
    }

    private int getPieceLength(int pieceIndex) {
        if(pieceIndex != pieceCount - 1)
            return pieceLength;
        return (int) (totalSize - (long) pieceLength * (pieceCount - 1));
    }


    private synchronized void writePieceToDisk(int pieceIndex, byte[] piece) throws IOException {
        long offset = (long) pieceIndex * pieceLength;
        file.seek(offset);
        file.write(piece);
    }

    @Override
    public void close() throws Exception {
        if(file != null) {
            file.getFD().sync();
            file.close();
            file = null;
        }
    }
}
