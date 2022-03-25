package fr.upem.net.tcp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerSum {

    private static final int BUFFER_SIZE = 2 * Integer.BYTES;
    private static final Logger logger = Logger.getLogger(ServerSumOneShot.class.getName());

    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;

    private final ByteBuffer senderBuffer = ByteBuffer.allocate(Integer.BYTES);

    public ServerSum(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
    }

    public void launch() throws IOException {
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        while (!Thread.interrupted()) {
            Helpers.printKeys(selector); // for debug
            System.out.println("Starting select");
            try {
                selector.select(this::treatKey);
            } catch (UncheckedIOException tunneled) {
                logger.log(Level.SEVERE, "your network card is frying", tunneled);
                return;
            }
            System.out.println("Select finished");
        }
    }

    private void treatKey(SelectionKey key) {
        Helpers.printSelectedKey(key); // for debug
        try {
            if (key.isValid() && key.isAcceptable()) {
                doAccept(key);
            }
        } catch (IOException e) {
            logger.warning("### Problem with server port. Stopping the server");
            throw new UncheckedIOException(e);
            //should stop the server
        }

        try {
            if (key.isValid() && key.isWritable()) {
                doWrite(key);
            }
            if (key.isValid() && key.isReadable()) {
                doRead(key);
            }
        } catch (IOException e) {
            logger.warning("### Problem with the client, Closing connection with the client");
            //close the client connection
            silentlyClose(key);
        }
    }

    private void doAccept(SelectionKey key) throws IOException {
        // TODO
        //getting the server socket channel
        ServerSocketChannel ssc = (ServerSocketChannel) key.channel();

        //getting socket channel
        var sc = ssc.accept();

        //do nothing if sc is null, le selector a manti
        if (sc == null) {
            logger.warning("accept() returned null");
            return;
        }

        //setting non blocking mode
        sc.configureBlocking(false);

        //register its key to read
        sc.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(BUFFER_SIZE));

    }

    private void doRead(SelectionKey key) throws IOException {
        // TODO

        //getting client from key
        var sc = (SocketChannel) key.channel();

        var buffer = (ByteBuffer) key.attachment();


        if (sc.read(buffer) == -1) {
            logger.warning("### Protocol Not respected closing client connection");
            silentlyClose(key);
            return;
        }

        if (buffer.hasRemaining()) {
            //logger.warning("### Invalid packet format");
            return;
        }

        buffer.flip();
        var one = buffer.getInt();
        var two = buffer.getInt();

        logger.info("==> Received ::: " + one + " , " + two);


        //should prepare the sending buffer
        buffer.clear().putInt(one + two);

        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void doWrite(SelectionKey key) throws IOException {
        // TODO

        var sc = (SocketChannel) key.channel();
        var buffer = (ByteBuffer) key.attachment();

        buffer.flip();
        logger.info("<== sending response");
        sc.write(buffer);

        if (buffer.remaining() > Integer.BYTES) {
            logger.warning("###Writing error");
            buffer.compact();
            return;
        }
        buffer.clear();
        logger.info("Writing successful");
        //silentlyClose(key);
        key.interestOps(SelectionKey.OP_READ);

    }

    private void silentlyClose(SelectionKey key) {
        var sc = (Channel) key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        new ServerSum(Integer.parseInt(args[0])).launch();
    }

    private static void usage() {
        System.out.println("Usage : ServerSumOneShot port");
    }
}
