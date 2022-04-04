package fr.upem.net.tcp.nonblocking.t11;

import fr.upem.net.tcp.nonblocking.Helpers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Context {

    private static final int BUFFER_SIZE = 1_024;

    private final SelectionKey key;
    private final SocketChannel sc;
    private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
    private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
    private boolean closed = false;

    public Context(SelectionKey key) {
        this.key = key;
        this.sc = (SocketChannel) key.channel();
    }

    /**
     * Process the content of bufferIn into bufferOut
     * <p>
     * The convention is that both buffers are in write-mode before the call to
     * process and after the call
     */

    private void process() {

        bufferIn.flip();
        while (bufferIn.remaining() >= Integer.BYTES * 2 && bufferOut.remaining() >= Integer.BYTES) {
            var one = bufferIn.getInt();
            var two = bufferIn.getInt();
            System.out.println("==> Receiving from clint " + one + ", " + two);
            bufferOut.putInt(one + two);
        }
        bufferIn.compact();
    }

    /**
     * Update the interestOps of the key looking only at values of the boolean
     * closed and of both ByteBuffers.
     * <p>
     * The convention is that both buffers are in write-mode before the call to
     * updateInterestOps and after the call. Also it is assumed that process has
     * been be called just before updateInterestOps.
     */

    private void updateInterestOps() {
//        if (!closed) {
//            if (bufferIn.hasRemaining()) {
//                key.interestOps(SelectionKey.OP_READ);
//            }
//        } else {
//            silentlyClose();
//            return;
//        }
//        if (bufferOut.position() != 0) {
//            key.interestOps(SelectionKey.OP_WRITE);
//        }

        int interestOps = 0;
        if (!closed && bufferIn.hasRemaining()) {
            interestOps |= SelectionKey.OP_READ;
        }
        if (bufferOut.position() != 0) {
            interestOps |= SelectionKey.OP_WRITE;
        }

        if (interestOps == 0) {
            silentlyClose();
            return;
        }
        key.interestOps(interestOps);

    }

    private void silentlyClose() {

        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    /**
     * Performs the read action on sc
     * <p>
     * The convention is that both buffers are in write-mode before the call to
     * doRead and after the call
     *
     * @throws IOException
     */

    void doRead() throws IOException {
        // TODO
        if (sc.read(bufferIn) == -1) {
            System.err.println("Connection closed by " + sc.getRemoteAddress());
            closed = true;
        }

        process();
        updateInterestOps();

    }

    /**
     * Performs the write action on sc
     * <p>
     * The convention is that both buffers are in write-mode before the call to
     * doWrite and after the call
     *
     * @throws IOException
     */

    void doWrite() throws IOException {

        bufferOut.flip();
        System.out.println("<== Sending .... ");

        sc.write(bufferOut);

        bufferOut.compact();
        updateInterestOps();


    }


}