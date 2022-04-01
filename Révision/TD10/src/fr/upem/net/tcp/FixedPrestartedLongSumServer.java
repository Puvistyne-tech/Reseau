package fr.upem.net.tcp;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FixedPrestartedLongSumServer {
    private static final Logger logger = Logger.getLogger(IterativeLongSumServer.class.getName());
    private static final int BUFFER_SIZE = 1024;
    private final ServerSocketChannel serverSocketChannel;

    private final int nbMaxThread;


    public FixedPrestartedLongSumServer(int port, int nbMaxThread) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));

        this.nbMaxThread = nbMaxThread;

        logger.info(this.getClass().getName() + " starts on port " + port);
    }


    /**
     * Iterative server main loop
     *
     * @throws IOException
     */

    public void launch() throws IOException, InterruptedException {

        logger.info("Server started");

        var threads = ThreadPool.create(nbMaxThread, this::gerer);

        threads.startAll();


    }

    private void gerer() {
        while (!Thread.interrupted()) {

            SocketChannel client = null;
            try {
                client = serverSocketChannel.accept();
                logger.info("Connection accepted from " + client.getRemoteAddress());
                serve(client);
            } catch (IOException ioe) {
                logger.log(Level.SEVERE, "Connection terminated with client by IOException", ioe.getCause());
            } finally {
                silentlyClose(client);
            }
        }
    }

    /**
     * Treat the connection sc applying the protocol. All IOException are thrown
     *
     * @param sc
     * @throws IOException
     */
    private void serve(SocketChannel sc) throws IOException {
        while (!Thread.interrupted()) {
            var sizeBuff = ByteBuffer.allocate(Integer.BYTES);
            var results = 0L;

            if (!readFully(sc, sizeBuff)) {
                logger.warning("### Unsupported Size");
                return;
            }
            var nbOperand = sizeBuff.flip().getInt();


            var operands = ByteBuffer.allocate(Long.BYTES * nbOperand);

            if (!readFully(sc, operands)) {
                logger.warning("## Not enough operands");
                return;
            }

            operands.flip();
            while (nbOperand > 0) {
                results += operands.getLong();
                nbOperand--;
            }
            var buffSender = ByteBuffer.allocate(Long.BYTES);
            buffSender.putLong(results).flip();

            sc.write(buffSender);
            logger.info("<== Sending the sum ::: " + results);
        }

    }

    /**
     * Close a SocketChannel while ignoring IOExecption
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

    public static void main(String[] args) throws NumberFormatException, IOException, InterruptedException {
        var server = new FixedPrestartedLongSumServer(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
        server.launch();
    }

    private static class ThreadPool {
        ArrayList<Thread> threads = new ArrayList<>();

        private ThreadPool(int nbThread, Runnable runnable) {
            for (int i = 0; i < nbThread; i++) {
                threads.add(new Thread(runnable));
            }
        }

        public static ThreadPool create(int nbThreads, Runnable runnable) {
            return new ThreadPool(nbThreads, runnable);
        }

        public void startAll() {
            threads.forEach(Thread::start);
        }

    }
}
