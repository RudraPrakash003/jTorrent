package com.jtorrent.metaInfo;

import java.security.SecureRandom;

public class ClientId {
    private static byte[] peerId;
    public static byte[] generateId() {
        if(peerId == null) {
            peerId = new byte[20];
            new SecureRandom().nextBytes(peerId);

            byte[] prefix = "-JT00001-".getBytes();
            System.arraycopy(prefix, 0, peerId, 0, prefix.length);
        }
        return peerId;
    }
}
