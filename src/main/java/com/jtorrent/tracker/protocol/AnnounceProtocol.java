package com.jtorrent.tracker.protocol;

import com.jtorrent.model.Peer;
import com.jtorrent.utils.GeneratePeerId;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class AnnounceProtocol {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static  byte[] buildAnnounceRequest(long connectionId, byte[] infoHash, long left, int port) {
        ByteBuffer buffer = ByteBuffer.allocate(98);

        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.putLong(connectionId);
        buffer.putInt(1);

        byte[] transactionId = new byte[4];
        RANDOM.nextBytes(transactionId);
        buffer.put(transactionId);

        buffer.put(infoHash);
        buffer.put(GeneratePeerId.generateId());

        buffer.putLong(0L); //downloaded
        buffer.putLong(left); //left
        buffer.putLong(0L); //uploaded

        buffer.putInt(0); // event
        buffer.putInt(0); //IP

        byte[] key =  new byte[4];
        RANDOM.nextBytes(key);
        buffer.put(key);

        buffer.putInt(-1); // num want
        buffer.putShort((short) port);

        return buffer.array();
    }




    public static List<Peer> parseAnnounceResponse(byte[] responseData, byte[]  announceRequest) {
        if(responseData == null || responseData.length < 20) {
            throw new RuntimeException("Invalid announce response");
        }
        ByteBuffer buffer = ByteBuffer.wrap(responseData);
        buffer.order(ByteOrder.BIG_ENDIAN);

        int sentTransactionId = ByteBuffer.wrap(announceRequest).getInt(12);
        int action = buffer.getInt(0);
        int receivedTransactionId = buffer.getInt(4);
        int leechers  = buffer.getInt(8);
        int seeders = buffer.getInt(12);
        int offset = 20;

        if(sentTransactionId != receivedTransactionId) {
            throw new RuntimeException("Invalid transaction id");
        }


        if (action != 1) {
            throw new RuntimeException("Expected announce response, got action: " + action);
        }

//        System.out.println("Announce interval: " + interval + "s, seeders: " + seeders + ", leechers: " + leechers);

        List<Peer> peers = new ArrayList<>();

        for(int i = offset; i + 6 < responseData.length; i += 6) {
            int ip1 = Byte.toUnsignedInt(responseData[i]);
            int ip2 = Byte.toUnsignedInt(responseData[i + 1]);
            int ip3 = Byte.toUnsignedInt(responseData[i + 2]);
            int ip4 = Byte.toUnsignedInt(responseData[i + 3]);

            String ip = ip1 + "." + ip2 + "." + ip3 + "." + ip4;
            int port = ((responseData[i + 4] & 0xff) << 8) | (responseData[i + 5] & 0xff);

            peers.add(new Peer(ip, port));

        }
        return peers;
    }
}
