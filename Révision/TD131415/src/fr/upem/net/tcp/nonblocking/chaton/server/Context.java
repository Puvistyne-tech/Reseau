package fr.upem.net.tcp.nonblocking.chaton.server;

import fr.upem.net.tcp.nonblocking.chaton.Message;
import fr.upem.net.tcp.nonblocking.chaton.MessageReader;
import fr.upem.net.tcp.nonblocking.chaton.Reader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

public class Context {

    private static final int BUFFER_SIZE = 1_024;
    private final static Charset CHARSET = StandardCharsets.UTF_8;
    private final SelectionKey key;
    private final SocketChannel sc;
    private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
    private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
    private final ArrayDeque<ByteBuffer> queue = new ArrayDeque<>();
    private final ServerChaton server; // we could also have Context as an instance class, which would naturally
    // give access to ServerChatInt.this
    private boolean closed = false;

    private String text;
    private String username;


    public Context(ServerChaton server, SelectionKey key) {
        this.key = key;
        this.sc = (SocketChannel) key.channel();
        this.server = server;
    }

    /**
     * Process the content of bufferIn
     * <p>
     * The convention is that bufferIn is in write-mode before the call to process and
     * after the call
     */
    private void processIn() {
        for (; ; ) {
            Reader<Message> reader = new MessageReader();
            Reader.ProcessStatus status = reader.process(bufferIn);
            switch (status) {
                case DONE:
                    var value = reader.get();
                    server.broadcast(value);
                    reader.reset();
                    break;
                case REFILL:
                    return;
                case ERROR:
                    silentlyClose();
                    return;
            }
        }

//        bufferIn.flip();
//
//        while (bufferIn.remaining() >= Integer.BYTES) {
//            var mgs = bufferIn.getInt();
//            System.out.println("==> Getting ::::: "+mgs);
//            server.broadcast(mgs);
//        }
//
//        bufferIn.compact();

    }

    /**
     * Add a message to the message queue, tries to fill bufferOut and updateInterestOps
     *
     * @param msg
     */
    public void queueMessage(Message msg) {
//TODO


//        queue.offer(msg);
//        processOut();
//        updateInterestOps();

        var username = CHARSET.encode(msg.username());
        var text = CHARSET.encode(msg.text());
        var buffer = ByteBuffer.allocate(username.remaining() + text.remaining() + Integer.BYTES * 2);
        buffer.putInt(username.remaining())
                .put(username)
                .putInt(text.remaining())
                .put(text)
                .flip();
        queue.offer(buffer);
        processOut();
        updateInterestOps();

    }

    /**
     * Try to fill bufferOut from the message queue
     */
    private void processOut() {

//        while (!queue.isEmpty() && bufferOut.hasRemaining()) {
//            var message = queue.poll();
//            username = message.username();
//            text = message.text();
//
//            bufferOut.put(CHARSET.encode(text));
//        }

        while (!queue.isEmpty() && bufferOut.hasRemaining()) {
            var  msg= queue.peek();
            if (!msg.hasRemaining()) {
                queue.poll();
                continue;
            }
            if (msg.remaining() <= bufferOut.remaining()) {
                bufferOut.put(msg);
            } else {
                var oldLimit = msg.limit();
                msg.limit(bufferOut.remaining());
                bufferOut.put(msg);
                msg.limit(oldLimit);
            }
        }

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
    public void doRead() throws IOException {
        // TODO
        System.out.println(bufferIn);

        if (sc.read(bufferIn) == -1) {
            System.err.println("Connection closed by " + sc.getRemoteAddress());
            closed = true;
        }

        processIn();
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

    public void doWrite() throws IOException {
        // TODO
        bufferOut.flip();
        System.out.println("<== Sending .... ");
        System.out.println(username + " :::: " + text);


        System.out.println(bufferOut);
        sc.write(bufferOut);


        bufferOut.compact();

        processOut();
        updateInterestOps();
    }


}
