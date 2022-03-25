package fr.upem.net.tcp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.logging.Logger;

public class MyServerSum {

    private static final Logger logger = Logger.getLogger(MyServerSum.class.getName());
    private static final int BUFFER_SIZE = 1_024;

    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;

    public MyServerSum(int port) throws IOException {

        //returns a new socket initialized channel
        this.serverSocketChannel = ServerSocketChannel.open();
        //binding an ip address to the server
        this.serverSocketChannel.bind(new InetSocketAddress(port));

        //opening a selector
        this.selector = Selector.open();
    }

    public void launch() throws IOException {

        //configure in non bloquant mode
        serverSocketChannel.configureBlocking(false);
        //register the selector to the server socket
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        while (!Thread.interrupted()) {
            Helpers.printKeys(selector);

            System.out.println("Starting Select");

            try {
                selector.select(this::treatKey);
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
            System.out.println("Select Finished");
        }
    }

    public void treatKey(SelectionKey key) {
        Helpers.printSelectedKey(key);

        try {
            if (key.isValid() && key.isAcceptable()) {
                doAccept(key);
            }
        } catch (IOException uioe) {
            throw new UncheckedIOException(uioe);
        }

        try {
            if (key.isValid() && key.isReadable()) {
                ((Context) key.attachment()).doRead();
            }
            if (key.isValid() && key.isWritable()) {
                ((Context) key.attachment()).doWrite();
            }
        } catch (IOException e) {
            logger.warning("Connection with the client is closed due to IOException");
            silentlyClose(key);
        }
    }

    private void silentlyClose(SelectionKey key) {
        var sc = (Channel) key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            //
            logger.warning("Could not close the channel");
        }
    }


    private void doAccept(SelectionKey key) throws IOException {

        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel client = serverSocketChannel.accept();

        if (client == null) {
            return;
        }

        client.configureBlocking(false);
        var selectionKey = client.register(selector, SelectionKey.OP_READ);
        selectionKey.attach(new Context(selectionKey));
    }

    static private class Context {
        private final SelectionKey key;
        private final SocketChannel sc;
        private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        private boolean closed = false;

        public Context(SelectionKey key) {
            this.key = key;
            sc = (SocketChannel) key.channel();
        }

        private void updateInterestOps() {

            if (closed || !buffer.hasRemaining()) {
                key.interestOps(SelectionKey.OP_WRITE);
            } else if (buffer.position() == 0) {
                key.interestOps(SelectionKey.OP_READ);
            } else {
                key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            }

        }

        private void doRead() throws IOException {
            if (sc.read(buffer) == -1) {
                logger.info("Connection closed by " + sc.getRemoteAddress());
                closed = true;
            }
            updateInterestOps();
        }

        private void doWrite() throws IOException {
            buffer.flip();
            if (closed && !buffer.hasRemaining()) {
                silentlyClose();
                return;
            }
            sc.write(buffer);
            buffer.compact();
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

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        new MyServerSum(Integer.parseInt(args[0])).launch();
    }

    private static void usage() {
        System.out.println("Usage : ServerEcho port");
    }
}
