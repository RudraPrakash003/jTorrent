package com.jtorrent.peer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

import static com.jtorrent.util.Buffers.allocate;

public class PeerMessageReader {

    private static final int MAX_BUFFER_SIZE = 256 * 1024;
    private static final int MAX_BLOCK_SIZE = 256 * 1024;

    private ByteBuffer buffer = allocate(64 * 1024);

    public void read(InputStream in, Consumer<byte[]> callback) throws Exception {
        byte[] temp = new byte[4096];
        int bytesRead;

        while((bytesRead = in.read(temp)) != -1) {
            checkCapacity(bytesRead);
            buffer.put(temp, 0, bytesRead);
            buffer.flip();

            while (buffer.remaining() >= 4) {
                buffer.mark();
                int length = buffer.getInt();

                if(length < 0 || length > MAX_BLOCK_SIZE) {
                    throw new IOException("Invalid length: " + length);
                }

                if (buffer.remaining() < length) {
                    buffer.reset();
                    break;
                }

                byte[] message = new byte[length + 4];
                buffer.reset();
                buffer.get(message);

                callback.accept(message);
            }
            buffer.compact();
        }
    }
    private void checkCapacity(int incoming) throws IOException {
        if(buffer.remaining() < incoming) {
            if(buffer.capacity() >= MAX_BUFFER_SIZE) {
                throw new IOException("Buffer Overflow");
            }
            ByteBuffer newBuffer = ByteBuffer.allocate(buffer.capacity() * 2);
            buffer.flip();
            newBuffer.put(buffer);
            buffer = newBuffer;
        }
    }
}

