package com.jtorrent.peer;

public record Peer(String ip, int port) {

    @Override
    public String toString() {
        return ip + ":" + port;
    }
}
