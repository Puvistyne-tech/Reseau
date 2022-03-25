package fr.upem.net.udp;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static java.lang.System.exit;

public class ServerLongSum {

    private static final Logger logger = Logger.getLogger(ServerLongSum.class.getName());
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final int BUFFER_SIZE = 1024;

    private final DatagramChannel dc;
    private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

    public ServerLongSum(int port) throws IOException {
//        map = new HashMap<>();
        dc = DatagramChannel.open();
        dc.bind(new InetSocketAddress(port));
        logger.info("ServerLongSum started on port " + port);
    }

    public class SessionHandler {
        private final HashMap<InetSocketAddress, Map<Long, Session>> map;

        public SessionHandler() {
            this.map = new HashMap<>();
        }

        public void createUpdateSession(InetSocketAddress address, long sessionID, long idPosOper, long totalOper, long opValue) {

            var session = map
                    .computeIfAbsent(address, k -> new HashMap<>())
                    .computeIfAbsent(sessionID, k -> new Session((int) totalOper));

            session.tryRegisterOne(idPosOper, opValue);

            //return session;


        }

        //retourne le somme ou 0 si une session n'existe pas pour SessionID
        public long getSumFor(InetSocketAddress address, long sessionID) {
            if (map.containsKey(address)) {
                var sessionMap = map.get(address);
                if (sessionMap.containsKey(sessionID)) {
                    var session = sessionMap.get(sessionID);
                    if (session.isCompleted()) {
                        return session.getSum();
                    }
                    return 0;
                }
                return 0;
            }
            return 0;
        }

    }

    public void sendRES(InetSocketAddress address, long sessionID, long result) throws IOException {
        buffer.clear();
        buffer.put((byte) 3).putLong(sessionID).putLong(result).flip();
        dc.send(buffer, address);
    }

    public void sendACK(InetSocketAddress address, long sessionID, long idPosOper) throws IOException {
        buffer.clear();
        buffer.put((byte) 2).putLong(sessionID).putLong(idPosOper).flip();
        dc.send(buffer, address);
    }

    private void receiveOP(SessionHandler sessionHandler) throws IOException {
        buffer.clear();

        var sender = (InetSocketAddress) dc.receive(buffer);
        buffer.flip();
        if (buffer.remaining() < Byte.BYTES) {
            logger.warning("Invalid message received from " + sender);
        }

        byte OP_ID = buffer.get();
        if (OP_ID != (byte) 1) {
            logger.warning("Malformed Request");
            exit(0);
        }

        if (buffer.remaining() < Long.BYTES * 4) {
            logger.warning("Invalid message received from " + sender);
            exit(0);
            //return;
        }

        var sessionID = buffer.getLong();
        var idPosOper = buffer.getLong();
        var totalOper = buffer.getLong();
        var opValue = buffer.getLong();


        logger.info("==> Received ss id : %d, idPosOper : %d, totalOper : %d, opValue : %d ".formatted(
                sessionID,
                idPosOper,
                totalOper,
                opValue
        ));

        sendACK(sender, sessionID, idPosOper);

        sessionHandler.createUpdateSession(sender, sessionID, idPosOper, totalOper, opValue);

        var sum = sessionHandler.getSumFor(sender, sessionID);
        if (sum != 0) {
            sendRES(sender, sessionID, sum);
        }

    }

    public void serve() throws IOException {

        try {
            var sessionHandler = new SessionHandler();

            while (!Thread.interrupted()) {
                //
                //receiveOP(sessionHandler);

                buffer.clear();

                var sender = (InetSocketAddress) dc.receive(buffer);
                buffer.flip();
                if (buffer.remaining() < Byte.BYTES) {
                    logger.warning("Invalid message received from 1 " + sender);
                    continue;
                }

                byte OP_ID = buffer.get();
                if (OP_ID != 1) {
                    logger.warning("Malformed Request");
                    continue;
                }

                if (buffer.remaining() < Long.BYTES * 4) {
                    logger.warning("Invalid message received from " + sender);
//                    exit(0);
                    //return;
                    continue;
                }

                var sessionID = buffer.getLong();
                var idPosOper = buffer.getLong();
                var totalOper = buffer.getLong();
                var opValue = buffer.getLong();


                logger.info("==> Received ss id : %d, idPosOper : %d, totalOper : %d, opValue : %d ".formatted(
                        sessionID,
                        idPosOper,
                        totalOper,
                        opValue
                ));

                sendACK(sender, sessionID, idPosOper);

                sessionHandler.createUpdateSession(sender, sessionID, idPosOper, totalOper, opValue);

                var sum = sessionHandler.getSumFor(sender, sessionID);
                if (sum != 0) {
                    sendRES(sender, sessionID, sum);
                }

            }
        } finally {
            dc.close();
        }

    }

    public static void usage() {
        System.out.println("Usage : ServerLongSumUDP port");
    }

    private static class Session {
        private final BitSet bitSet;
        private long sum;

        public Session(int size) {
            bitSet = new BitSet(size);
            synchronized (bitSet) {
                bitSet.set(0, size - 1);
                sum = 0;
            }
        }

        public boolean isCompleted() {
            synchronized (bitSet) {
                return bitSet.cardinality() == 0;
            }
        }

        private boolean isExists(long id) {
            synchronized (bitSet) {
                return bitSet.get((int) id);
            }
        }

        public void tryRegisterOne(long id, long value) {
            synchronized (bitSet) {
                if (!isExists(id)) {
                    bitSet.clear((int) id);
                    sum += value;
                }
            }
        }

        public int[] forRemaining() {
            synchronized (bitSet) {
                return bitSet.stream().toArray();
            }
        }

        public long getSum() {
            return this.sum;
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }

        var port = Integer.parseInt(args[0]);

        if (!(port >= 1024) & port <= 65535) {
            logger.severe("The port number must be between 1024 and 65535");
            return;
        }

        try {
            new ServerLongSum(port).serve();
        } catch (BindException e) {
            logger.severe("Server could not bind on " + port + "\nAnother server is probably running on this port.");
            exit(1);
        }
    }
}
