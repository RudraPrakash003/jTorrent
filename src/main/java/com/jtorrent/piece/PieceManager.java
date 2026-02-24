package com.jtorrent.piece;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PieceManager implements AutoCloseable{

    private static final Logger log = LoggerFactory.getLogger(PieceManager.class);
    private final int pieceCount;
    private final int pieceLength;
    private final long totalSize;
    private final List<byte[]> pieceHashes;

    private final Map<Integer, byte[]> pieceData = new ConcurrentHashMap<>();
    private final Map<Integer, BitSet> progressTracker  = new ConcurrentHashMap<>();
    private final List<MappedByteBuffer> regions = new ArrayList<>();

    private final BitSet verifiedPieces;
    private final BitSet completedPieces;

    private long totalDownloaded = 0;

    private final long REGION_SIZE = 1900L * 1024 * 1024;

    private final FileChannel fileChannel;

    public PieceManager(int pieceCount, int pieceLength, List<byte[]> pieceHashes, long totalSize, String outputPath) throws IOException {
        this.pieceCount = pieceCount;
        this.pieceLength = pieceLength;
        this.totalSize = totalSize;
        this.pieceHashes = pieceHashes;
        this.verifiedPieces = new BitSet(pieceCount);
        this.completedPieces = new BitSet(pieceCount);

        this.fileChannel = FileChannel.open(Paths.get(outputPath), StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        fileChannel.truncate(totalSize);
        mapRegions();
    }

    private void mapRegions() throws IOException{
        long position = 0;

        while(position < totalSize) {
            long chunkSize = Math.min(REGION_SIZE, totalSize - position);
            MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, chunkSize);
            regions.add(buffer);
            position += chunkSize;
        }
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
                log.warn("The piece {} is corrupted", pieceIndex);
                pieceData.remove(pieceIndex);
                return false;
            }

            writePieceToDisk(pieceIndex, piece);
            verifiedPieces.set(pieceIndex);
            completedPieces.set(pieceIndex);
            pieceData.remove(pieceIndex);

            log.info("Piece {} verified and saved. Progress: {}/{}", pieceIndex, verifiedPieces.cardinality(), pieceCount);
            return true;
        } catch (Exception e) {
            log.error("Error while verifying/saving piece {} ", pieceIndex, e);
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
        long globalOffset = (long) pieceIndex * pieceLength;
        if(globalOffset * pieceLength > totalSize) throw new IOException("Piece index out of range");
        int remaining = piece.length;
        int pieceOffset = 0;

        while(remaining > 0) {
            int regionIndex = (int) (globalOffset / REGION_SIZE);
            long regionOffset = regionIndex * REGION_SIZE;

            int offsetInRegion = (int) (globalOffset - regionOffset);

            MappedByteBuffer region = regions.get(regionIndex);
            int writable = Math.min(remaining, (int) (REGION_SIZE - offsetInRegion));

            region.position(offsetInRegion);
            region.put(piece, pieceOffset, writable);

            remaining -= writable;
            pieceOffset += writable;
            globalOffset += writable;
        }

        log.debug("Piece {} written to disk at offset {} ({} bytes)", pieceIndex, pieceOffset, pieceLength);
    }

    @Override
    public void close() throws Exception {
        for(MappedByteBuffer region : regions)
            region.force();
        fileChannel.close();
        log.info("PieceManager closed and file flushed to disk");
    }
}
