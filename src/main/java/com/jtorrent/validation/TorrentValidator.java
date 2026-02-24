package com.jtorrent.validation;

import com.jtorrent.exception.ValidationException;
import com.jtorrent.metaInfo.TorrentMetaData;

import java.util.List;
import java.util.Map;

public class TorrentValidator {
    private static final int MIN_PIECE_LENGTH = 16 * 1024;
    private static final int MAX_PIECE_LENGTH = 16 * 1024 * 1024;

    public static void validateTorrent(Map<String, Object> torrent) throws ValidationException {
        if(torrent == null) throw new ValidationException("Torrent file is empty");
        if(!torrent.containsKey("info")) throw new ValidationException("info is missing in the torrent file");
        if(!torrent.containsKey("announce")) throw new ValidationException("announce is missing in the torrent file");

        Map<String, Object> info = TorrentMetaData.info(torrent);

        Object pieceLengthObj = info.get("piece length");
        if(!(pieceLengthObj instanceof Number)) throw new ValidationException("Invalid torrent piece length");
        long pieceLength = ((Number) pieceLengthObj).longValue();
        if(pieceLength < MIN_PIECE_LENGTH || pieceLength > MAX_PIECE_LENGTH) throw new ValidationException("Invalid torrent piece length: " + pieceLength);
        if((pieceLength & (pieceLength - 1)) != 0) throw new ValidationException("Piece length must be a power of 2 -" + pieceLength);

        byte[] pieces = TorrentMetaData.piecesRaw(torrent);
        if(pieces.length % 20 != 0) throw new ValidationException("Invalid torrent piece hash length");


        long totalSize = TorrentMetaData.totalSize(torrent);
        if(totalSize <= 0) throw new ValidationException("Invalid torrent size");

        int expectedPieceCount = TorrentMetaData.pieceCount(torrent);
        int actualPieceCount = pieces.length / 20;
        if(expectedPieceCount != actualPieceCount) throw new ValidationException("Piece count mismatch. Expected: " + expectedPieceCount + ", got: " + actualPieceCount);

        if(TorrentMetaData.isMultiFile(torrent)) validateMultiFileStructure(info, totalSize);
        else validateSingleFileStructure(info);
    }

    public static void validateSingleFileStructure(Map<String, Object> info) throws ValidationException {
        if(!info.containsKey("name")) throw new ValidationException("Missing file name");
        if(!info.containsKey("length")) throw new ValidationException("Missing file length");

        Object nameObj =  info.get("name");
        if(!(nameObj instanceof String)) {
            throw new IllegalArgumentException("Invalid torrent name");
        }
        String name = (String) nameObj;
        if(name.isBlank() || name.contains("/") || name.contains("\\") || name.contains("..")) throw new ValidationException("Invalid file name: " + name);
    }

    public static void validateMultiFileStructure(Map<String, Object> info, long totalSize) throws ValidationException {
        List<Map<String, Object>> files = (List<Map<String, Object>>) info.get("files");
        if(files == null || files.isEmpty()) throw new ValidationException("Missing files");

        long calculatedSize = 0;

        for(Map<String, Object> file : files) {
            if(!file.containsKey("length")) throw new ValidationException("Missing file length");
            if(!file.containsKey("path")) throw new ValidationException("Missing file path");

            long length = ((Number) file.get("length")).longValue();
            if(length <= 0) throw new ValidationException("Invalid file length: " + length);
            calculatedSize += length;

            List<String> path = (List<String>) file.get("path");
            if(path == null || path.isEmpty()) throw new ValidationException("Missing file path");

            for(String component : path) {
                if(component.contains("/") || component.contains("\\") || component.contains("..")) throw new ValidationException("Invalid file path: " + component);
            }
        }

        if(calculatedSize != totalSize) throw new ValidationException("Files size mismatch");
    }
}
