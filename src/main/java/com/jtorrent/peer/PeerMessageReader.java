package com.jtorrent.peer;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

import static com.jtorrent.util.Buffers.allocate;

public class PeerMessageReader {

    private ByteBuffer buffer = allocate(64 * 1024);

    public void read(InputStream inputStream, Consumer<byte[]> callback) throws Exception {
        byte[] temp = new byte[4096];
        int bytesRead;

        while((bytesRead = inputStream.read(temp)) != -1) {
            checkCapacity(bytesRead);
            buffer.put(temp, 0, bytesRead);
            buffer.flip();

            while (buffer.remaining() >= 4) {
                buffer.mark();
                int length = buffer.getInt();

                if (buffer.remaining() < length) {
                    buffer.reset();
                    break;
                }

                byte[] message = new byte[length + 4];
                buffer.reset();
                buffer.get(message);

                callback.accept(message);
            }
        }
        buffer.compact();
    }
    private void checkCapacity(int incoming) {
        if(buffer.remaining() < incoming) {
            ByteBuffer newBuffer = ByteBuffer.allocate(buffer.capacity() * 2);
            buffer.flip();
            newBuffer.put(buffer);
            buffer = newBuffer;
        }
    }
}

