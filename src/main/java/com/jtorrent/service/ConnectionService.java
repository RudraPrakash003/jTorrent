package com.jtorrent.service;

import com.jtorrent.network.UdpClient;
import com.jtorrent.protocol.ConnectionProtocol;
import com.jtorrent.protocol.ResponseType;

import java.net.SocketTimeoutException;

public class ConnectionService {
    private ConnectionService() {}

    private static final int MAX_RETRIES = 8;
    private static final int BASE_DELAY_SECONDS = 15;

    public static long connect(UdpClient udp, String host, int port) throws Exception {
        byte[] connectionRequest = ConnectionProtocol.buildConnectionRequest();

        for(int attempt = 0; attempt < MAX_RETRIES; attempt++){
            try{
                udp.send(connectionRequest, host, port);

                byte[] responseData = udp.receive();

                if(!ResponseType.getType(responseData).equals("connect")) {
                    throw new RuntimeException("Connection action mismatched");
                }
                return ConnectionProtocol.parseConnectResponse(responseData, connectionRequest);
            } catch (SocketTimeoutException e) {
                int waitTime = (int) Math.pow(2, attempt) * BASE_DELAY_SECONDS;
                System.out.println("Connection timeout, retrying in " + waitTime + "Seconds");
                Thread.sleep(waitTime * 1000L);
            }
        }
        throw new RuntimeException("Failed to connect to the tracker");
    }
}
