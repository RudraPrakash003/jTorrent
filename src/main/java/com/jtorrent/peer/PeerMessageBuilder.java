package com.jtorrent.peer;

import com.jtorrent.utils.GeneratePeerId;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

public class PeerMessageBuilder {

    public PeerMessageBuilder() {}

    public static byte[] buildHandshake(byte[] infoHash) {
        ByteBuffer buffer = ByteBuffer.allocate(68);
        buffer.order(ByteOrder.BIG_ENDIAN);

        String protocol = "BitTorrent protocol";

        buffer.put((byte)protocol.length());
        buffer.put(protocol.getBytes());
        buffer.put(new byte[8]);
        buffer.put(infoHash);
        buffer.put(GeneratePeerId.generateId());

        return buffer.array();
    }

    public static byte[] buildKeepAlive(byte[] infoHash) {
        return ByteBuffer.allocate(4).putInt(0).array();
    }

    public static byte[] buildChoke(byte[] infoHash) {
        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.putInt(1);
        buffer.put((byte) 0);

        return buffer.array();
    }

    public static byte[] buildUnchoke(byte[] infoHash) {
        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.putInt(1);
        buffer.put((byte) 1);

        return buffer.array();
    }

    public static byte[] buildInterested(byte[] infoHash) {
        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.putInt(1);
        buffer.put((byte) 2);

        return buffer.array();
    }

    public static byte[] buildUnInterested(byte[] infoHash) {
        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.putInt(1);
        buffer.put((byte) 3);

        return buffer.array();
    }

    public static byte[] buildHave(int pieceIndex) {
        ByteBuffer buffer = ByteBuffer.allocate(9);
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.putInt(5);
        buffer.put((byte) 4);
        buffer.putInt(pieceIndex);

        return buffer.array();
    }

    public static byte[] buildBitfield(byte[] bitfield) {
        ByteBuffer buffer = ByteBuffer.allocate(14);
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.putInt(bitfield.length + 1);
        buffer.put((byte) 5);
        buffer.put(bitfield);

        return buffer.array();
    }

    public static byte[] buildRequest(int index, int begin, int length) {
        ByteBuffer buffer = ByteBuffer.allocate(17);
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.putInt(13);
        buffer.put((byte) 6);
        buffer.putInt(index);
        buffer.putInt(begin);
        buffer.putInt(length);

        return buffer.array();
    }

    public static byte[] buildPiece(int index, int begin, byte[] block) {
        ByteBuffer buffer = ByteBuffer.allocate(block.length + 9);
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.put((byte) 7);
        buffer.putInt(index);
        buffer.putInt(begin);
        buffer.put(block);

        return buffer.array();
    }

    public static byte[] buildCancel(int index, int begin, int length) {
        ByteBuffer buffer = ByteBuffer.allocate(17);
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.putInt(13);
        buffer.put((byte) 8);
        buffer.putInt(index);
        buffer.putInt(begin);
        buffer.putInt(length);

        return buffer.array();
    }

    public static byte[] buildPort(int port) {
        ByteBuffer buffer = ByteBuffer.allocate(7);

        buffer.putInt(3);
        buffer.put((byte) 9);
        buffer.putShort((short) port);

        return buffer.array();
    }
}
