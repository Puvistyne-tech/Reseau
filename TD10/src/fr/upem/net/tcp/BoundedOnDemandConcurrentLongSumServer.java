package fr.upem.net.tcp;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;


public class BoundedOnDemandConcurrentLongSumServer {

    private static final Logger logger = Logger.getLogger(BoundedOnDemandConcurrentLongSumServer.class.getName());
    private static final int BUFFER_SIZE = 1024;
    private final ServerSocketChannel serverSocketChannel;
    private final Semaphore semaphore;

    private final int nbPermits=5;

    public BoundedOnDemandConcurrentLongSumServer(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        this.semaphore=new Semaphore(nbPermits);
        logger.info(this.getClass().getName() + " starts on port " + port);
    }

    /**
     * Iterative server main loop
     *
     * @throws IOException
     */

    public void launch() throws IOException, InterruptedException {
        logger.info("Server started");
        while (!Thread.interrupted()) {
            SocketChannel client = serverSocketChannel.accept();
            semaphore.acquire();
            var t = new Thread(() -> {
                System.out.println(Thread.currentThread().getName());
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
                this.semaphore.release();
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

    public static void main(String[] args) throws NumberFormatException, IOException, InterruptedException {
        var server = new BoundedOnDemandConcurrentLongSumServer(Integer.parseInt(args[0]));
        server.launch();

    }
}