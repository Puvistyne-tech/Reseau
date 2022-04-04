package fr.upem.net.tcp.nonblocking.chaton.client;

import fr.upem.net.tcp.nonblocking.chaton.Message;
import fr.upem.net.tcp.nonblocking.chaton.MessageReader;
import fr.upem.net.tcp.nonblocking.chaton.Reader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;

public class Context {
    static private final int BUFFER_SIZE = 10_000;
    private final static Charset CHARSET = StandardCharsets.UTF_8;


    private final SelectionKey key;
    private final SocketChannel sc;
    private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
    private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
    public final ArrayDeque<Message> queue = new ArrayDeque<>();
    private boolean closed = false;

    public Context(SelectionKey key) {
        this.key = key;
        this.sc = (SocketChannel) key.channel();
    }

    /**
     * Process the content of bufferIn
     * <p>
     * The convention is that bufferIn is in write-mode before the call to process
     * and after the call
     */
    private void processIn() {
        for (; ; ) {
            Reader<Message> reader = new MessageReader();
            Reader.ProcessStatus status = reader.process(bufferIn);
            switch (status) {
                case DONE:
                    var message = reader.get();
                    reader.reset();

                    var dtf = DateTimeFormatter.ofPattern("HH:mm");
                    System.out.println(dtf.format(LocalDateTime.now()) +" from "+ message.username()+" ::: " + message.text());
                    //System.out.println(message);

                    break;
                case REFILL:
                    return;
                case ERROR:
                    silentlyClose();
                    return;
            }
        }

    }

    /**
     * Add a message to the message queue, tries to fill bufferOut and updateInterestOps
     *
     * @param bb
     */
    public void queueMessage(Message msg) {

        if (!queue.isEmpty()) {
            return;
        }
        queue.offer(msg);
        processOut();
        updateInterestOps();
    }

    /**
     * Try to fill bufferOut from the message queue
     */
    private void processOut() {
        while (!queue.isEmpty() && bufferOut.hasRemaining()) {
            var message = queue.peek();

            var userBuffer = CHARSET.encode(message.username());
 //          var textBuffer = CHARSET.encode(message.text());
            var tmp=message.text();
//            System.out.println(message.text());
//
//
            var textBuffer = ByteBuffer.allocate(tmp.length());
            textBuffer.put(CHARSET.encode(tmp)).flip();


            ByteBuffer mgsBuffer = ByteBuffer.allocate(userBuffer.capacity() + textBuffer.capacity() + Integer.BYTES * 2);
            if (mgsBuffer.capacity() >= 1024) {
                return;
            }

            mgsBuffer
                    .putInt(userBuffer.capacity())
                    .put(userBuffer)
                    .putInt(textBuffer.capacity())
                    .put(textBuffer)
                    .flip();

            if (mgsBuffer.remaining() <= bufferOut.remaining()) {
                bufferOut.put(mgsBuffer);
                queue.pop();
            } else {
                return;
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
        // TODO
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
        var t = sc.read(bufferIn);

        if (t == 0) {
            return;
        }
        if (t == -1) {
            System.out.println("Connection closed");
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

        bufferOut.flip();

        sc.write(bufferOut);

        if (bufferOut.hasRemaining()){
            System.out.println("writing error");
            return;
        }

        bufferOut.compact();

        processOut();
        updateInterestOps();
    }

    public void doConnect() throws IOException {

        if (!sc.finishConnect()){
            return;
        }
        key.interestOps(SelectionKey.OP_READ);
        //updateInterestOps();
    }
}