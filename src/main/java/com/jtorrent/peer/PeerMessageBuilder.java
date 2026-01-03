package com.jtorrent.peer;

import static com.jtorrent.util.Buffers.allocate;
import static com.jtorrent.util.Buffers.message;

public final class PeerMessageBuilder {

    private static final String PROTOCOL = "BitTorrent protocol";

    private PeerMessageBuilder() {}

    public static byte[] buildHandshake(byte[] infoHash, byte[] peerId) {
        return allocate(68)
                .put((byte) PROTOCOL.length())
                .put(PROTOCOL.getBytes())
                .put(new byte[8])          // reserved
                .put(infoHash)
                .put(peerId)
                .array();
    }

    public static byte[] buildKeepAlive() {
        return message(0).array();
    }

    public static byte[] buildChoke() {
        return message(1).put((byte) 0).array();
    }

    public static byte[] buildUnchoke() {
        return message(1).put((byte) 1).array();
    }

    public static byte[] buildInterested() {
        return message(1).put((byte) 2).array();
    }

    public static byte[] buildUnInterested() {
        return message(1).put((byte) 3).array();
    }

    public static byte[] buildHave(int pieceIndex) {
        return message(5)
                .put((byte) 4)
                .putInt(pieceIndex)
                .array();
    }

    public static byte[] buildBitfield(byte[] bitfield) {
        return message(bitfield.length + 1)
                .put((byte) 5)
                .put(bitfield)
                .array();
    }

    public static byte[] buildRequest(int index, int begin, int length) {
        return message(13)
                .put((byte) 6)
                .putInt(index)
                .putInt(begin)
                .putInt(length)
                .array();
    }

    public static byte[] buildPiece(int index, int begin, byte[] block) {
        return message(block.length + 9)
                .put((byte) 7)
                .putInt(index)
                .putInt(begin)
                .put(block)
                .array();
    }

    public static byte[] buildCancel(int index, int begin, int length) {
        return message(13)
                .put((byte) 8)
                .putInt(index)
                .putInt(begin)
                .putInt(length)
                .array();
    }

    public static byte[] buildPort(int port) {
        return message(3)
                .put((byte) 9)
                .putShort((short) port)
                .array();
    }
}
