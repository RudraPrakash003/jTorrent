package com.jtorrent.peer;

import com.jtorrent.model.Peer;

import java.util.List;

public class PeerManager {

    private static final int MAX_PEERS = 5;
    private final byte[] infoHash;

    public PeerManager(byte[] infoHash) {
        this.infoHash = infoHash;
    }

    public void connectToPeers(List<Peer> peers) {
        System.out.println("Connecting to peers");

        peers.stream().limit(MAX_PEERS).forEach(this::connectPeer);
    }

    public void connectPeer(Peer peer) {
        try(PeerConnection connection = new PeerConnection(peer, infoHash)) {
            connection.connect();
            connection.handshake();
        } catch (Exception e) {
            System.out.println("Connection failed for Peer: " + peer);
        }

    }
}
