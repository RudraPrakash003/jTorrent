package com.jtorrent.peer;


import com.jtorrent.scheduler.RequestScheduler;
import com.jtorrent.validation.MessageValidator;
import com.jtorrent.validation.MessageValidator.ParsedMessage;
import com.jtorrent.validation.MessageValidator.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(PeerConnection.class);
    private final Peer peer;
    private final byte[] infoHash;
    private final byte[] peerId;
    private final int pieceCount;
    private final int pieceLength;
    private final long totalSize;
    private int consecutiveErrors = 0;

    private final RequestScheduler scheduler;
    private final MessageValidator messageValidator;

    private static final int MAX_MESSAGE_SIZE = 256 * 1024;
    private static final int MAX_CONSECUTIVE_ERRORS = 5;

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
        this.messageValidator = new MessageValidator(pieceCount, pieceLength, totalSize);
        registerHandlers();
    }

    public void connect() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(peer.ip(), peer.port()), 5_000);
        socket.setSoTimeout(120_000);
        socket.setKeepAlive(true);

        in = socket.getInputStream();
        out= socket.getOutputStream();

        log.info("Connected to peer {}", peer);

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
        log.info("Handshake succeeded for peer {}", peer);
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
                        consecutiveErrors = 0;
                    } catch (IllegalStateException e) {
                        log.error("Protocol violation from {}", peer, e);
                        closeQuietly();
                    }
                    catch (Exception e) {
                        consecutiveErrors++;
                        log.error("Error handling message from {} - disconnecting", peer, e);

                        if(consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                            log.error("Consecutive errors, disconnecting from {}", peer, e);
                            closeQuietly();
                        }
                    }
                }
            });
        } catch (Exception e) {
            log.error("Message loop error for {}", peer, e);
            throw e;
        } finally {
            closeQuietly();
        }
    }

    public void handleMessage(byte[] message) {
        try {
            ParsedMessage parsed = messageValidator.validateAndParse(message);

            if (parsed.isKeepAlive()) return; //keep-alive

            Consumer<ByteBuffer> handler = handlers.get(parsed.messageId);

            if (handler != null) {
                handler.accept(parsed.payload);
            }
        } catch (ValidationException e) {
            log.error("Invalid message received from {}", peer, e);
            closeQuietly();
        } catch (Exception e) {
            log.error("Error parsing message from {}", peer, e);
        }
   }

    public void registerHandlers() {
        //choke
        handlers.put(0, b -> {
            peerChoking = true;
            log.debug("peer {} choked you", peer);
        });

        //unchoke
        handlers.put(1, b -> {
            peerChoking = false;
            log.debug("Received unchoke from {}", peer);
            scheduler.onUnchoke(this);
        });

        //interested
        handlers.put(2, b -> {
            peerInterested = true;
            log.debug("{} is interested", peer);
        });

        //not interested
        handlers.put(3, b -> {
            peerInterested = false;
            log.debug("{} is not interested", peer);
        });

        //have
        handlers.put(4, b -> {
            int pieceIndex = b.getInt();
            if(bitfield != null) {
                bitfield.set(pieceIndex);
            } else {
                bitfield = new BitSet(pieceCount);
                bitfield.set(pieceIndex);
            }
            log.debug("{} has piece {}", peer, pieceIndex);
        });

        //bitfield
        handlers.put(5, b -> {

            if (bitfield != null) {
                throw new IllegalStateException("Duplicate bitfield from " + peer);
            }

            byte[] raw = new byte[b.remaining()];
            b.get(raw);

            bitfield = BitSet.valueOf(raw);
            log.debug("Received bitfield from {}", peer);

            if(!amInterested) {
                send(PeerMessageBuilder.buildInterested());
                amInterested = true;
                log.debug("Sent interested to {}", peer);
            }
        });

        //request
        handlers.put(6, b -> {
            int index = b.getInt();
            int begin = b.getInt();
            int length = b.getInt();

            log.debug("{} requested piece: {} offset: {} length: {} ", peer, index, begin, length);
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
            log.debug("Received piece: {} offset: {} from {}", index, begin, peer);
            scheduler.onBlockReceived(this, index, begin, block);
        });

        //cancel
        handlers.put(8, b -> {
            int index = b.getInt();
            int begin = b.getInt();
            int length = b.getInt();

            log.debug("{} cancelled request for piece {} offset {} length {}", peer, index, begin,length);
        });

    }

    public synchronized void send(byte[] message) {
        try {
            out.write(message);
            out.flush();
        } catch (IOException e) {
            log.warn("Send failed to peer {} - disconnecting", peer, e);
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

        log.info("Disconnected from peer {}", peer);
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
