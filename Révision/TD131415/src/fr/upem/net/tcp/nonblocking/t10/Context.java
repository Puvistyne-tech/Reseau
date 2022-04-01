package fr.upem.net.tcp.nonblocking.t10;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class Context {
    private static final int BUFFER_SIZE = 1_024;

    private final SelectionKey key;
    private final SocketChannel sc;
    private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
    private boolean closed = false;

    public Context(SelectionKey key) {
        this.key = key;
        this.sc = (SocketChannel) key.channel();
    }

    /**
     * Update the interestOps of the key looking only at values of the boolean
     * closed and the ByteBuffer buffer.
     * <p>
     * The convention is that buff is in write-mode.
     */
    private void updateInterestOps() {
        // TODO

        if (closed) {
            key.interestOps(SelectionKey.OP_ACCEPT);
        } else {
            if (buffer.capacity() == buffer.remaining()) {
                key.interestOps(SelectionKey.OP_READ);
            } else {
                key.interestOps(SelectionKey.OP_WRITE);
            }
        }
    }

    /**
     * Performs the read action on sc
     * <p>
     * The convention is that buffer is in write-mode before calling doRead and is in
     * write-mode after calling doRead
     *
     * @throws IOException
     */
    void doRead() throws IOException {
        // TODO
        if (sc.read(buffer) == -1) {
            System.err.println("Connection Closed by " + sc.getRemoteAddress());
            closed = true;
        }
        System.out.println("==> Receiving from the client");
        updateInterestOps();
    }

    /**
     * Performs the write action on sc
     * <p>
     * The convention is that buffer is in write-mode before calling doWrite and is in
     * write-mode after calling doWrite
     *
     * @throws IOException
     */
    void doWrite() throws IOException {
        // TODO

        buffer.flip();

        if (closed && !buffer.hasRemaining()) {
            silentlyClose();
            return;
        }

        System.out.println("<== Sending to the client");
        sc.write(buffer);
        buffer.clear();
        updateInterestOps();

    }

    private void silentlyClose() {
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }
}
