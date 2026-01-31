package com.jtorrent.peer;

import com.jtorrent.scheduler.RequestScheduler;

import static com.jtorrent.util.Buffers.wrap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.InetSocketAddress;
import java.net.Socket;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class PeerConnection implements AutoCloseable {

    private final Peer peer;
    private final byte[] infoHash;
    private final byte[] peerId;
    private final int pieceCount;
    private final int pieceLength;
    private final long totalSize;

    private final RequestScheduler scheduler;

    private static final int MAX_MESSAGE_SIZE = 256 * 1024;

    private volatile boolean running = true;

    private boolean peerChoking = true;
    private boolean peerInterested = false;
    private boolean amInterested = false;

    private BitSet bitfield;

    private Socket socket;
    private InputStream in;
    private OutputStream out;

    private final Map<Integer, Consumer<ByteBuffer>> handlers = new HashMap<>();

    private ScheduledExecutorService keepAliveExecutor;
    private ScheduledFuture<?> keepAliveTask;

    public PeerConnection(Peer peer, byte[] infoHash, byte[] peerId, int pieceCount, int pieceLength, long totalSize, RequestScheduler scheduler) {
        this.peer = peer;
        this.infoHash = infoHash;
        this.peerId = peerId;
        this.pieceCount = pieceCount;
        this.pieceLength = pieceLength;
        this.totalSize = totalSize;
        this.scheduler = scheduler;
        registerHandlers();
    }

    public void connect() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(peer.ip(), peer.port()), 5_000);
        socket.setSoTimeout(120_000);
        socket.setKeepAlive(true);

        in = socket.getInputStream();
        out= socket.getOutputStream();

        startKeepAlive();
    }

    private void startKeepAlive() {
        keepAliveExecutor = Executors.newSingleThreadScheduledExecutor();
        keepAliveTask = keepAliveExecutor.scheduleAtFixedRate(() -> {
            try {
                send(PeerMessageBuilder.buildKeepAlive());
            } catch (Exception e) {
                closeQuietly();
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

    public void handshake() throws IOException {
        send(PeerMessageBuilder.buildHandshake(infoHash, peerId));
        byte[] response = readFully(68);
        validateHandshake(response, infoHash, peerId);
    }

    private static void validateHandshake(byte[] response, byte[] expectedInfoHash, byte[] localPeerId) {
        if(response == null || response.length < 68) {
            throw new RuntimeException("Invalid handshake length");
        }

        int pstrlen = response[0] & 0xff;
        int infoHashOffset = 1 + pstrlen + 8;
        if(infoHashOffset + 20 > response.length) {
            throw new RuntimeException("Malformed handshake response");
        }

        byte[] receivedInfoHash = Arrays.copyOfRange(response, infoHashOffset, infoHashOffset + 20);
        if(!Arrays.equals(receivedInfoHash, expectedInfoHash)) {
            throw new RuntimeException("Invalid handshake response");
        }

        int peerOffset = infoHashOffset + 20;
        if(peerOffset + 20 > response.length) {
            throw new RuntimeException("Malformed handshake response");
        }

        byte[] receivedPeerId = Arrays.copyOfRange(response, peerOffset, peerOffset + 20);
        if(Arrays.equals(localPeerId, receivedPeerId)) {
            throw new RuntimeException("Self connection detected");
        }
    }

    public void startMessageLoop() throws Exception {
        PeerMessageReader reader = new PeerMessageReader();

        try{
            reader.read(in, message -> {
                if(running) {
                    try {
                        handleMessage(message);
                    } catch (Exception e) {
                        System.err.println("Error handling message from " + peer + ": " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            System.err.println("Message loop error for " + peer + ": " + e.getMessage());
            throw e;
        } finally {
            closeQuietly();
        }
    }

    public void handleMessage(byte[] message) {
        ByteBuffer buffer = wrap(message);

        int length = buffer.getInt();
        if(length == 0) return; //keep-alive

        if(length < 0 || length > MAX_MESSAGE_SIZE) {
            throw new RuntimeException("Invalid message size");
        }

        int messageId = buffer.get() & 0xff;
        Consumer<ByteBuffer> handler = handlers.get(messageId);

        if(handler != null) {
            handler.accept(buffer);
        }
   }

    public void registerHandlers() {
        //choke
        handlers.put(0, b -> {
            peerChoking = true;
            System.out.println(peer + " choked you");
        });

        //unchoke
        handlers.put(1, b -> {
            peerChoking = false;
            System.out.println("Received unchoke from " + peer);
            scheduler.onUnchoke(this);
        });

        //interested
        handlers.put(2, b -> {
            peerInterested = true;
            System.out.println(peer + " is interested");
        });

        //not interested
        handlers.put(3, b -> {
            peerInterested = false;
            System.out.println(peer + " is not interested");
        });

        //have
        handlers.put(4, b -> {
            int pieceIndex = b.getInt();
            if(bitfield != null && pieceIndex < pieceCount) {
                bitfield.set(pieceIndex);
            }
            System.out.println(peer + " has piece " + pieceIndex);
        });

        //bitfield
        handlers.put(5, b -> {

            if (bitfield != null) {
                throw new IllegalStateException("Duplicate bitfield from " + peer);
            }

            byte[] raw = new byte[b.remaining()];
            b.get(raw);

            int expectedBytes = (pieceCount + 7) / 8;
            if(raw.length != expectedBytes) {
                throw new RuntimeException("Invalid bitfield length");
            }

            bitfield = BitSet.valueOf(raw);
            System.out.println("Received bitfield from " + peer);

            if(!amInterested) {
                send(PeerMessageBuilder.buildInterested());
                amInterested = true;
                System.out.println("Sent interested to " + peer);
            }
        });

        //request
        handlers.put(6, b -> {
            int index = b.getInt();
            int begin = b.getInt();
            int length = b.getInt();

            System.out.println(peer + " requested piece " + index + " offset " + begin + " length " + length);
        });

        //piece
        handlers.put(7, b -> {
            if(peerChoking) {
                throw new IllegalStateException("Received Piece while choked from " + peer);
            }

            int index = b.getInt();
            int begin = b.getInt();
            byte[] block = new byte[b.remaining()];
            b.get(block);
            System.out.println("Received piece " + index + " offset " + begin + " from " + peer);
            scheduler.onBlockReceived(this, index, begin, block);
        });

        //cancel
        handlers.put(8, b -> {
            int index = b.getInt();
            int begin = b.getInt();
            int length = b.getInt();

            System.out.println(peer + " cancelled request for piece " + index);
        });

    }

    public synchronized void send(byte[] message) {
        try {
            out.write(message);
            out.flush();
        } catch (IOException e) {
            closeQuietly();
        }
    }

    private byte[] readFully(int len) throws IOException {
        byte[] data = new byte[len];
        int offset = 0;

        while(offset < len) {
            int read = in.read(data, offset, len - offset);
            if(read == -1)
                throw new IOException("Stream closed while reading");
            offset += read;
        }
        return data;
    }

    public boolean isPeerChoking() { return peerChoking; }

    public BitSet getBitfield() { return bitfield; }

    public Peer getPeer() { return peer; }

    @Override
    public void close() throws Exception {
        running = false;

        if(keepAliveTask != null)
            keepAliveTask.cancel(true);
        if(keepAliveExecutor != null)
            keepAliveExecutor.shutdown();

        scheduler.onPeerDisconnected(this);

        if(in != null) in.close();
        if(out != null) out.close();
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public void closeQuietly() {
        try{
            close();
        } catch (Exception ignored){}
    }
}
