package com.jtorrent.peer;

import com.jtorrent.model.Peer;
import com.jtorrent.utils.HandshakeValidator;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class PeerConnection implements AutoCloseable {

    private final Peer peer;
    private final byte[] infoHash;

    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;

    public PeerConnection(Peer peer, byte[] infoHash) {
        this.peer = peer;
        this.infoHash = infoHash;
    }

    public void connect() throws Exception {
        socket = new Socket(peer.getIp(), peer.getPort());
        socket.setSoTimeout(10_000);
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
    }

    public void handshake() throws Exception {
        byte[] handshake = PeerMessageBuilder.buildHandshake(infoHash);
        outputStream.write(handshake);
        outputStream.flush();

        byte[] response = inputStream.readNBytes(68);
        HandshakeValidator.validate(response, infoHash);
    }

    @Override
    public void close() throws Exception {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

}
