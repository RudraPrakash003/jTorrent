    package com.jtorrent.tracker.protocol;

    import java.nio.ByteBuffer;
    import java.security.SecureRandom;

    public class ConnectionProtocol {

        private static final long PROTOCOL_ID = 0x41727101980L;
        private static final SecureRandom RANDOM = new SecureRandom();

        public static byte[] buildConnectionRequest() {
            ByteBuffer buffer = ByteBuffer.allocate(16);

            buffer.putLong(PROTOCOL_ID);
            buffer.putInt(0);

            byte[] transactionId = new byte[4];
            RANDOM.nextBytes(transactionId);
            buffer.put(transactionId);
//            System.out.println(buffer.toString());
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
