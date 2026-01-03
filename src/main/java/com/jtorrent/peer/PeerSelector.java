package com.jtorrent.peer;

import java.net.InetAddress;

public class PeerSelector {
    public PeerSelector() {}

    //Filter
    public static boolean isValid(Peer peer) {
        return validPort(peer.port())
                && validIp(peer.ip())
                && notLoopBack(peer.ip())
                && notPrivate(peer.ip());
    }

    private static boolean validPort(int port) {
        return port > 0 && port <= 65535;
    }

    private static boolean validIp(String ip) {
        try {
            InetAddress.getByName(ip);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean notLoopBack(String ip) {
        return !ip.startsWith("127.");
    }

    private static boolean notPrivate(String ip) {
        return !(ip.startsWith("10.") || ip.startsWith("192.168") || ip.startsWith("172.16"));
    }


    //Sort
    public static int peerPriority(Peer a, Peer b) {
        return Integer.compare(scorePeer(a), scorePeer(b));
    }

    private static int scorePeer(Peer peer) {
        int score = 0;
        score += portPenalty(peer.port());
        score += Math.random() * 10;
        return score;
    }

    private static int portPenalty(int port) {
        if(port == 6881) return 50;
        if(port < 1024) return 30;
        if(port == 1337) return 20;

        return 0;
    }
}
