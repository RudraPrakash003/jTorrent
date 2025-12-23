package com.jtorrent.peer;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class PeerMessageReader {

    private final ByteBuffer buffer = ByteBuffer.allocate(32 * 1024);
    private boolean handshake = true;

    public void read(InputStream inputStream, Consumer<byte[]> callback) throws Exception {

        byte[] temp = new byte[4096];
        int bytesRead;

        while((bytesRead = inputStream.read(temp)) != -1) {

            buffer.put(temp, 0, bytesRead);
            buffer.flip();

            while(true) {
                Integer msglen = getMessageLength();
                if(msglen == null || buffer.remaining() < msglen) {
                    break;
                }

                byte[] message = new byte[msglen];
                buffer.get(message);
                callback.accept(message);

                handshake = false;
            }

            buffer.compact();
        }
    }
    private Integer getMessageLength() {
        if(handshake) {
            if(buffer.remaining() < 1) return null;

            int pstrlen = buffer.get(buffer.position()) & 0xff;
            return pstrlen + 49;
        } else {
            if(buffer.remaining() < 4) return null;
            int len =  buffer.getInt(buffer.position());
            return len + 4;
        }
    }
}
