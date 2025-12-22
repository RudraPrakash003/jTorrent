package com.jtorrent.utils;

import java.util.Arrays;

public class HandshakeValidator {

    private HandshakeValidator() {}

    public static void validate(byte[] response, byte[] expectedInfoHash) {

        if(response == null || response.length != 68) {
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
    }
}
