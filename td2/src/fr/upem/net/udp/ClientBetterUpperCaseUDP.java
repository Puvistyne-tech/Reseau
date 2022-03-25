package fr.upem.net.udp;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Scanner;
import java.util.logging.Logger;

public class ClientBetterUpperCaseUDP {
    private static final int MAX_PACKET_SIZE = 1024;

    private static final Charset ASCII_CHARSET = StandardCharsets.US_ASCII; //Charset.forName("US-ASCII");

    /**
     * Creates and returns an Optional containing a new ByteBuffer containing the encoded representation
     * of the String <code>msg</code> using the charset <code>charsetName</code>
     * in the following format:
     * - the size (as a Big Indian int) of the charsetName encoded in ASCII<br/>
     * - the bytes encoding this charsetName in ASCII<br/>
     * - the bytes encoding the String msg in this charset.<br/>
     * The returned ByteBuffer is in <strong>write mode</strong> (i.e. need to
     * be flipped before to be used).
     * If the buffer is larger than MAX_PACKET_SIZE bytes, then returns Optional.empty.
     *
     * @param msg         the String to encode
     * @param charsetName the name of the Charset to encode the String msg
     * @return an Optional containing a newly allocated ByteBuffer containing the representation of msg,
     * or an empty Optional if the buffer would be larger than 1024
     */
    public static Optional<ByteBuffer> encodeMessage(String msg, String charsetName) {
        // TODO
        int bufferSize = 2000;
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        var longer = 4;
        buffer.position(longer);
        System.out.println(longer);
        System.out.println("buffer remaining "+buffer.remaining());
        var cs = StandardCharsets.US_ASCII;
        buffer.put(cs.encode(charsetName));

        System.out.println("buffer remaining "+buffer.remaining());
        var mgsSize = buffer.capacity() - longer - buffer.remaining();
        System.out.println("mgsSize " + mgsSize);
//        buffer.position(0);
        buffer.clear();
        System.out.println(" p l "+buffer.position()+" : "+buffer.limit());
        buffer.limit(longer);
        System.out.println(" p l "+buffer.position()+" : "+buffer.limit());
        buffer.putInt(mgsSize);
        System.out.println(" p l "+buffer.position()+" : "+buffer.limit());

        buffer.clear();
        System.out.println(" p l "+buffer.position()+" : "+buffer.limit());
        buffer.position(longer + mgsSize);
        System.out.println(" p l "+buffer.position()+" : "+buffer.limit());

        buffer.put(Charset.forName(charsetName).encode(msg));
        System.out.println(" p l "+buffer.position()+" : "+buffer.limit());
        buffer.flip();
        System.out.println(" p l "+buffer.position()+" : "+buffer.limit());
//        buffer.compact();
//        System.out.println(" p l "+buffer.position()+" : "+buffer.limit());
        System.out.println(buffer.remaining());
        if (buffer.capacity() < 513) return Optional.of(buffer);
        else return Optional.empty();

    }

    /**
     * Creates and returns an Optional containing a String message represented by the ByteBuffer buffer,
     * encoded in the following representation:
     * - the size (as a Big Indian int) of a charsetName encoded in ASCII<br/>
     * - the bytes encoding this charsetName in ASCII<br/>
     * - the bytes encoding the message in this charset.<br/>
     * The accepted ByteBuffer buffer must be in <strong>write mode</strong>
     * (i.e. need to be flipped before to be used).
     *
     * @param buffer a ByteBuffer containing the representation of an encoded String message
     * @return an Optional containing the String represented by buffer, or an empty Optional if the buffer cannot be decoded
     */
    public static Optional<String> decodeMessage(ByteBuffer buffer) {
        int longer = 4;
        System.out.println(buffer.capacity());

        System.out.println(" p l " + buffer.position() + " : " + buffer.limit());
        buffer.flip();

        if (buffer.limit() != buffer.capacity()) return Optional.empty();
        System.out.println(" p l " + buffer.position() + " : " + buffer.limit());
        buffer.limit(longer);
        var mgsSize = buffer.getInt();

        if (mgsSize < 0 || mgsSize > buffer.capacity()) return Optional.empty();
        System.out.println(" p l " + buffer.position() + " : " + buffer.limit());
        System.out.println(" mgs size " + mgsSize);

        buffer.position(longer).limit(mgsSize + longer);
        System.out.println(" p l " + buffer.position() + " : " + buffer.limit());

        var charsetName = StandardCharsets.US_ASCII.decode(buffer).toString();
        System.out.println(charsetName);

        if (!Charset.isSupported(charsetName))
            return Optional.empty();

        var message = Charset
                .forName(charsetName.toString())
                .decode(buffer.clear().position(mgsSize + longer))
                .toString();

        if (message.isEmpty()) return Optional.empty();
        return Optional.of(message);
    }

    public static void usage() {
        System.out.println("Usage : ClientBetterUpperCaseUDP host port charsetName");
    }

    public static void main(String[] args) throws IOException {
        // check and retrieve parameters
        if (args.length != 3) {
            usage();
            return;
        }
        var host = args[0];
        var port = Integer.valueOf(args[1]);
        var charsetName = args[2];

        var destination = new InetSocketAddress(host, port);
        // buffer to receive messages
        var buffer = ByteBuffer.allocateDirect(MAX_PACKET_SIZE);

        try (var scanner = new Scanner(System.in);
             var dc = DatagramChannel.open()) {
            while (scanner.hasNextLine()) {
                var line = scanner.nextLine();

                var message = encodeMessage(line, charsetName);
                if (message.isEmpty()) {
                    System.out.println("Line is too long to be sent using the protocol BetterUpperCase");
                    continue;
                }
                var packet = message.get();
                packet.flip();
                dc.send(packet, destination);
                buffer.clear();
                dc.receive(buffer);

                decodeMessage(buffer).ifPresentOrElse(
                        (str) -> System.out.println("Received: " + str),
                        () -> System.out.println("Received an invalid paquet"));
            }
        }
    }
}
