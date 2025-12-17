package com.jtorrent.network;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;

public class UdpClient implements AutoCloseable {
    private final DatagramSocket socket;

    public UdpClient() throws SocketException {
        this.socket = new DatagramSocket();
        this.socket.setSoTimeout(5000);
    }

    public void send(byte[] message, String host, int port) throws IOException {
        InetAddress address = InetAddress.getByName(host);
        DatagramPacket packet = new DatagramPacket(message, message.length, address, port);
        socket.send(packet);
    }

    public byte[] receive() throws IOException {
        byte[] response = new byte[2048];
        DatagramPacket responsePacket = new DatagramPacket(response, response.length);
        socket.receive(responsePacket);
        return Arrays.copyOf(responsePacket.getData(), responsePacket.getLength());
    }

    @Override
    public void close() {
        socket.close();
    }
}
