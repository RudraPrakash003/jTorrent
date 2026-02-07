package com.jtorrent.tracker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

import static com.jtorrent.util.Buffers.allocate;

public class TrackerConnection {
    private TrackerConnection() {}

    private static final Logger log = LoggerFactory.getLogger(TrackerConnection.class);

    private static final long PROTOCOL_ID = 0x41727101980L;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_RETRIES = 8;
    private static final int BASE_DELAY_SECONDS = 15;

    public static long connect(UdpClient udp, String host, int port) throws Exception {
        byte[] connectionRequest = buildConnectionRequest();

        for(int attempt = 0; attempt < MAX_RETRIES; attempt++){
            try{
                udp.send(connectionRequest, host, port);
                byte[] responseData = udp.receive();

                if(!ResponseType.getType(responseData).equals("connect")) {
                    throw new RuntimeException("Connection action mismatched");
                }

                return parseConnectResponse(responseData, connectionRequest);
            } catch (SocketTimeoutException e) {
                int waitTime = (int) Math.pow(2, attempt) * BASE_DELAY_SECONDS;
                log.warn("Tracker {}:{} timeout (attempt {}/{}) retrying in {} Seconds", host, port, attempt + 1, MAX_RETRIES, waitTime);
                Thread.sleep(waitTime * 1000L);
            }
        }
        log.error("Failed to connect to Tracker {}:{} after {} retries", host, port, MAX_RETRIES);
        throw new RuntimeException("Failed to connect to the tracker");
    }

    public static byte[] buildConnectionRequest() {
        ByteBuffer buffer = allocate(16);

        buffer.putLong(PROTOCOL_ID);
        buffer.putInt(0);

        byte[] transactionId = new byte[4];
        RANDOM.nextBytes(transactionId);
        buffer.put(transactionId);
        return buffer.array();
    }

    public static long parseConnectResponse(byte[] responseData, byte[] connectionRequest) {
        if(responseData == null || responseData.length < 16) {
            throw new RuntimeException("Invalid connect response");
        }

        ByteBuffer buffer = ByteBuffer.wrap(responseData);

        int sentTransactionId = ByteBuffer.wrap(connectionRequest).getInt(12);
        int action =  buffer.getInt(0);
        int responseTransactionId = buffer.getInt(4);
        long connectionId = buffer.getLong(8);

        if(responseTransactionId != sentTransactionId) {
            throw new RuntimeException("Invalid transaction id");
        }

        if(action != 0) {
            throw new RuntimeException("Invalid action,expected CONNECT(0), but got "+ action);
        }

        return connectionId;
    }
}
