package fr.upem.net.tcp;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;


public class OnDemandConcurrentLongSumServer {

    private static final Logger logger = Logger.getLogger(OnDemandConcurrentLongSumServer.class.getName());
    private static final int BUFFER_SIZE = 1024;
    private final ServerSocketChannel serverSocketChannel;

    public OnDemandConcurrentLongSumServer(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        logger.info(this.getClass().getName() + " starts on port " + port);
    }

    /**
     * Iterative server main loop
     *
     * @throws IOException
     */

    public void launch() throws IOException {
        logger.info("Server started");
        while (!Thread.interrupted()) {
            SocketChannel client = serverSocketChannel.accept();
            var t = new Thread(() -> {
                try {
                    logger.info("Connection accepted from " + client.getRemoteAddress());
                    serve(client);
                } catch (IOException ioe) {
                    logger.log(Level.SEVERE, "Connection terminated with client by IOException", ioe.getCause());
                } finally {
                    silentlyClose(client);
                }
            });
            t.start();
        }
    }

    /**
     * Treat the connection sc applying the protocol. All IOException are thrown
     *
     * @param sc
     * @throws IOException
     */
    private void serve(SocketChannel sc) throws IOException {


        // TODO
        var sizeBuffer = ByteBuffer.allocate(Integer.BYTES);

        while (sc.read(sizeBuffer) != -1) {

            //get the size from the client
            var nbOperand = sizeBuffer.flip().getInt();
            logger.info("==> Getting the size " + nbOperand);
            sizeBuffer.clear();

            //if nbOperant is not valid
            if (nbOperand < 0) {
                logger.warning("Invalid Number of Operand Received");
                return;
            }

            //création de bufferLong
            var longBuffer = ByteBuffer.allocate(nbOperand * Long.BYTES);

            if (!readFully(sc, longBuffer)) return;
            longBuffer.flip();

            //response
            long response = 0L;
            //create a boucle for nbOperant
            for (int i = 0; i < nbOperand; i++) {
                var res = longBuffer.getLong();
                logger.info("==> Getting a long || " + (i + 1) + " || ::> " + res);
                response += res;
            }

            ByteBuffer senderBuffer = ByteBuffer.allocate(Long.BYTES);

            var t = sc.write(senderBuffer.clear().putLong(response).flip());
            logger.info("<== Sending the sum ::: " + response);
        }

    }

    /**
     * Close a SocketChannel while ignoring IOException
     *
     * @param sc
     */

    private void silentlyClose(Closeable sc) {
        if (sc != null) {
            try {
                sc.close();
            } catch (IOException e) {
                // Do nothing
            }
        }
    }

    static boolean readFully(SocketChannel sc, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (sc.read(buffer) == -1) {
                logger.info("Input stream closed");
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        var server = new OnDemandConcurrentLongSumServer(Integer.parseInt(args[0]));
        server.launch();
    }
}