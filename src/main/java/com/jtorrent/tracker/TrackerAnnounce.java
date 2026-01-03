package com.jtorrent.tracker;

import com.jtorrent.metaInfo.TorrentMetaData;
import com.jtorrent.peer.Peer;

import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.jtorrent.util.Buffers.allocate;
import static com.jtorrent.util.Buffers.wrap;

public class TrackerAnnounce {
    private TrackerAnnounce() {}

    private static final int MAX_RETRIES = 8;
    private static final int BASE_DELAY_SECONDS = 15;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static List<Peer> announce(UdpClient udp, long connectionId, byte[] infoHash, Map<String, Object> torrent, String host, int port, byte[] peerId) throws Exception{

        long left = TorrentMetaData.totalSize(torrent);
        byte[] announceRequest = buildRequest(connectionId, infoHash, left, port, peerId);
        for(int attempt = 0;  attempt < MAX_RETRIES; attempt++){
            try {
                udp.send(announceRequest, host, port);

                byte[] responseData = udp.receive();

                if(!ResponseType.getType(responseData).equals("announce")) {
                    throw new RuntimeException("Announce action mismatched");
                }

                return parseResponse(responseData, announceRequest);
            } catch (SocketTimeoutException e) {
                int waitTime = (int) (Math.pow(2, attempt) *  BASE_DELAY_SECONDS);
                System.out.println("Announce connection timeout, retrying in " + waitTime + " seconds");
                Thread.sleep(waitTime * 1000L);
            }
        }
        throw new  RuntimeException("Failed to make announce connection");
    }

    public static  byte[] buildRequest(long connectionId, byte[] infoHash, long left, int port, byte[] peerId) {
        ByteBuffer buffer = allocate(98);

        buffer.putLong(connectionId);
        buffer.putInt(1);

        byte[] transactionId = new byte[4];
        RANDOM.nextBytes(transactionId);
        buffer.put(transactionId);

        buffer.put(infoHash);
        buffer.put(peerId);

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

    public static List<Peer> parseResponse(byte[] responseData, byte[]  announceRequest) {
        if(responseData == null || responseData.length < 20) {
            throw new RuntimeException("Invalid announce response");
        }
        ByteBuffer buffer = wrap(responseData);

        int sentTransactionId = wrap(announceRequest).getInt(12);
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
