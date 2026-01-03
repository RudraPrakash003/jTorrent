package com.jtorrent.peer;

import static com.jtorrent.util.Buffers.wrap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.InetSocketAddress;
import java.net.Socket;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class PeerConnection implements AutoCloseable {

    private final Peer peer;
    private final byte[] infoHash;
    private final byte[] peerId;

    private boolean peerChoking = true;
    private boolean peerInterested = false;
    private boolean amInterested = false;

    private byte[] bitfield;

    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;

    public PeerConnection(Peer peer, byte[] infoHash, byte[] peerId) {
        this.peer = peer;
        this.infoHash = infoHash;
        this.peerId = peerId;
    }

    public void connect() throws Exception {
        socket = new Socket();
        socket.connect(new InetSocketAddress(peer.ip(), peer.port()), 5_000);
        socket.setSoTimeout(60_000);
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
    }

    public byte[] readResponse(int len) throws IOException {
        byte[] data = new byte[len];
        int offset = 0;

        while(offset < len) {
            int read = inputStream.read(data, offset, len - offset);
            if(read == -1)
                throw new IOException("Stream closed while reading");
            offset += read;
        }
        return data;
    }

    public void handshake() throws Exception {
        outputStream.write(PeerMessageBuilder.buildHandshake(infoHash, peerId));
        outputStream.flush();

        byte[] response = readResponse(68);
        validateHandshake(response, infoHash, peerId);

//        outputStream.write(PeerMessageBuilder.buildInterested());
//        outputStream.flush();
//        amInterested = true;
    }

    private static void validateHandshake(byte[] response, byte[] expectedInfoHash, byte[] localPeerId) {

        if(response == null || response.length < 68) {
            throw new RuntimeException("Invalid handshake length");
        }

        int pstrlen = response[0];
        int infoHashOffset = 1 + pstrlen + 8;

        if(infoHashOffset + 20 > response.length) {
            throw new RuntimeException("Malformed handshake response");
        }

        byte[] receivedInfoHash = Arrays.copyOfRange(response, infoHashOffset, infoHashOffset + 20);

        if(!Arrays.equals(receivedInfoHash, expectedInfoHash)) {
            throw new RuntimeException("Invalid handshake response");
        }

        int peerOffset = infoHashOffset + 20;

        if(peerOffset + 20 > response.length) {
            throw new RuntimeException("Malformed handshake response");
        }

        byte[] receivedPeerId = Arrays.copyOfRange(response, peerOffset, peerOffset + 20);

        if(Arrays.equals(localPeerId, receivedPeerId)) {
            throw new RuntimeException("Self connection detected");
        }
    }

    public void handleMessage(byte[] message) throws IOException {
        ByteBuffer buffer = wrap(message);

        int length = buffer.getInt();

        if(length == 0) {
            return;
        }

        int messageId = buffer.get() & 0xff;

        switch (messageId) {
            case 0 -> { //choke
                peerChoking = true;
                System.out.println(peer + "choked you");
            }
            case 1 -> { //unchoke
                peerChoking  = false;
                System.out.println("Received unchoke from: " + peer);
            }
            case 2 -> { //Interested
                peerInterested = true;
                System.out.println(peer + " is interested: ");
            }
            case 3 ->  peerInterested = false; //Not Interested
            case 4 -> { //have
                int pieceIndex = buffer.getInt();
                System.out.println(peer + "has a piece " + pieceIndex);
            }
            case 5 -> { //bitfield
                bitfield = new byte[length -1];
                buffer.get(bitfield);
                System.out.println("Received bitfield from: " + peer);

                if(!amInterested) {
                    outputStream.write(PeerMessageBuilder.buildInterested());
                    outputStream.flush();
                    amInterested = true;

                    System.out.println("Sent interested to " + peer);
                }
            }
            case 6 -> System.out.println(peer + " requests");
            case 7 -> { //piece
                int index = buffer.getInt();
                int begin = buffer.getInt();
                byte[] block = new byte[length - 9];
                buffer.get(block);

                System.out.println("Received piece " + index + " offset " + begin + " from " + peer);
            }
            default -> {} //ignore other responses
        }

   }

    public void startMessageLoop() throws Exception {
        PeerMessageReader reader = new PeerMessageReader();

        reader.read(inputStream, message -> {
            try {
                handleMessage(message);
            } catch (Exception e) {
                closeQuietly();
            }
        });
    }

    @Override
    public void close() throws Exception {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public void closeQuietly() {
        try{
            close();
        } catch (Exception ignored){}
    }
}
