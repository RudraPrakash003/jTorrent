package com.jtorrent.service;

import com.jtorrent.model.Peer;
import com.jtorrent.network.UdpClient;
import com.jtorrent.protocol.AnnounceProtocol;
import com.jtorrent.protocol.ResponseType;
import com.jtorrent.utils.TorrentSize;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

public class AnnounceService {

    private static final int MAX_RETRIES = 8;
    private static final int BASE_DELAY_SECONDS = 15;

    public static List<Peer> announce(UdpClient udp, long connectionId, byte[] infoHash, Map<String, Object> torrent, String host, int port) throws Exception{

        long left = TorrentSize.calculateSize(torrent);
        byte[] announceRequest = AnnounceProtocol.buildAnnounceRequest(connectionId, infoHash, left, port);
        for(int attempt = 0;  attempt < MAX_RETRIES; attempt++){
            try {
                udp.send(announceRequest, host, port);

                byte[] responseData = udp.receive();

                if(!ResponseType.getType(responseData).equals("announce")) {
                    throw new RuntimeException("Announce action mismatched");
                }

                return AnnounceProtocol.parseAnnounceResponse(responseData, announceRequest);
            } catch (SocketTimeoutException e) {
                int waitTime = (int) (Math.pow(2, attempt) *  BASE_DELAY_SECONDS);
                System.out.println("Announce connection timedout, retrying in " + waitTime + "seconds");
                Thread.sleep(waitTime * 1000L);
            }
        }
        throw new  RuntimeException("Failed to make announce connection");
    }
}
