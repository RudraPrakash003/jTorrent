package com.jtorrent.validation;

import java.nio.ByteBuffer;

import static com.jtorrent.util.Buffers.wrap;

public class MessageValidator {

    private final int pieceCount;
    private final int pieceLength;
    private final long totalSize;

    private static final int MAX_MESSAGE_SIZE = 256 * 1024;
    private static final int MAX_BLOCK_SIZE = 16 * 1024 * 1024 + 1024;

    public MessageValidator(int pieceCount, int pieceLength, long totalSize) {
        this.pieceCount = pieceCount;
        this.pieceLength = pieceLength;
        this.totalSize = totalSize;
    }

    public ParsedMessage validateAndParse(byte[] message) throws ValidationException{
        if(message == null || message.length < 4) {
            throw new ValidationException("Invalid message length");
        }

        ByteBuffer buffer = wrap(message);
        int length = buffer.getInt();

        if(length < 0 || length > MAX_MESSAGE_SIZE || message.length != length + 4) {
            throw new ValidationException("Invalid message size");
        }

        if(length == 0)
            return ParsedMessage.keepAlive();

        int messageId = buffer.get() & 0xff;

        switch(messageId) {
            case 0, 1, 2, 3 -> validateStateMessage(messageId, length);
            case 4 -> validateHaveMessage(buffer.duplicate(), length);
            case 5 -> validateBitfieldMessage(buffer.duplicate(), length);
            case 6 -> validateRequestMessage(buffer.duplicate(), length);
            case 7 -> validatePieceMessage(buffer.duplicate(), length);
            case 8 -> validateCancelMessage(buffer.duplicate(), length);
            default -> throw new ValidationException("Unkown message ID: " + messageId);
        }
        return new ParsedMessage(messageId, length, buffer);
    }

    private void validateStateMessage(int messageId, int length) throws ValidationException {
        if(length != 1)
            throw new ValidationException("Invalid length for state message, expected 1, received: " + length);
    }

    private void validateHaveMessage(ByteBuffer buffer, int length) throws ValidationException {
        if(length != 5)
            throw new ValidationException("Invalid length for state message, expected 5, received: " + length);
        if(buffer.remaining() < 4)
            throw new ValidationException("HAVE message is too short to contain pieceIndex");

        int pieceIndex = buffer.getInt();
        validatePieceIndex(pieceIndex);
    }

    private void validateBitfieldMessage(ByteBuffer buffer, int length) throws ValidationException {
        int expectedBytes = (pieceCount + 7) / 8;
        int actualBytes = length - 1;

        if (actualBytes != expectedBytes) {
            throw new ValidationException("Invalid bitfield length, expected: " + expectedBytes + "received: " + actualBytes);
        }

        if (buffer.remaining() < actualBytes) {
            throw new ValidationException("BITFIELD message truncated");
        }

        byte[] bitfield = new byte[actualBytes];
        buffer.get(bitfield);

        int spareBits = (8 - (pieceCount % 8)) % 8;
        if (spareBits > 0) {
            int lastByte = bitfield[bitfield.length - 1] & 0xFF;
            int mask = (1 << spareBits) - 1;

            if ((lastByte & mask) != 0)
                throw new ValidationException("Spare bits in bitfield are not zero");
        }
    }

    private void validateRequestMessage(ByteBuffer buffer, int length) throws ValidationException {
        if(length != 13) {
            throw new ValidationException("Invalid REQUEST message length, expected 13, got " + length);
        }

        if(buffer.remaining() < 12)
            throw new ValidationException("REQUEST message too short");

        int index = buffer.getInt();
        int begin = buffer.getInt();
        int requestLength = buffer.getInt();

        validatePieceIndex(index);
        validateBlockParameters(index, begin, requestLength);
    }

    private void validatePieceMessage(ByteBuffer buffer, int length) throws ValidationException {
        if(length < 9)
            throw new ValidationException("Invalid PIECE message length, expected 9, got " + length);

        if(buffer.remaining() < 8)
            throw new ValidationException("PIECE message truncated");

        int index = buffer.getInt();
        int begin = buffer.getInt();
        int blockLength = length - 9;

        validatePieceIndex(index);
        validateBlockParameters(index, begin, blockLength);

        if(buffer.remaining() < blockLength)
            throw new ValidationException("PIECE message missing block data. Expected: " + blockLength + " bytes, got " + buffer.remaining() + " bytes");
    }

    private void validateCancelMessage(ByteBuffer buffer, int length) throws ValidationException {
        if(length != 13) {
            throw new ValidationException("Invalid CANCEL message length, expected 13, got " + length);
        }

        if(buffer.remaining() < 12)
            throw new ValidationException("CANCEL message too short");

        int index = buffer.getInt();
        int begin = buffer.getInt();
        int cancelLength = buffer.getInt();

        validatePieceIndex(index);
        validateBlockParameters(index, begin, cancelLength);
    }


    private void validatePieceIndex(int pieceIndex) throws ValidationException {
        if(pieceIndex < 0) {
            throw new ValidationException("Negative piece index: " + pieceIndex);
        }

        if(pieceIndex >= pieceCount) {
            throw new ValidationException("Piece index out of bounds: " + pieceIndex + " (valid range: 0 -" + (pieceCount - 1) + ")");
        }
    }

    private void validateBlockParameters(int pieceIndex, int begin, int length) throws ValidationException {
        if(begin < 0) {
            throw new ValidationException("Negative piece offset: " + begin);
        }

        if(length <= 0)
            throw new ValidationException("Invalid block length: " + length);

        if(length > MAX_BLOCK_SIZE)
            throw new ValidationException("Block length is too large: " + length + " bytes, maximum: " + MAX_BLOCK_SIZE + " bytes");

        long pieceSize;
        if(pieceIndex == pieceCount - 1)
            pieceSize = totalSize - (long) pieceLength * (pieceLength - 1);
        else
            pieceSize = pieceLength;

        if(begin >= pieceSize)
            throw new ValidationException("Block offset beyond piece boundary: offset - " + begin + ", piece size - " + pieceSize);

        if(begin + length > pieceSize)
            throw new ValidationException("Block extends beyond piece boundary: offset - " + begin + ", length - " + length + ", piece size - " + pieceSize);
    }

    public static class ParsedMessage {
        private static final ParsedMessage KEEP_ALIVE = new ParsedMessage(-1, 0, null);

        public final int messageId;
        public final int length;
        public final ByteBuffer payload;

        public ParsedMessage(int messageId, int length, ByteBuffer payload) {
            this.messageId = messageId;
            this.length = length;
            this.payload = payload;
        }

        public static ParsedMessage keepAlive() {
            return KEEP_ALIVE;
        }

        public boolean isKeepAlive() {
            return messageId == -1;
        }
    }

    public static class ValidationException extends Exception{
        public ValidationException(String message) {
            super(message);
        }
    }
}
