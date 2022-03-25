package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;


public class ClientConcatenation {

    //Send

    //int - nombre de chaine

    //int - taille de la chaine suivante
    //UTF8 - chaine

    //Receive
    //int - la taille de la reponse
    //UTF8 - la reponse
    public static final Logger logger = Logger.getLogger(ClientLongSum.class.getName());

    private static final Charset UTF8 = StandardCharsets.UTF_8;

    private static final int BUFFER_SIZE = 1024;

    public static void main(String[] args) throws IOException {
        var server = new InetSocketAddress(args[0], Integer.parseInt(args[1]));

        Scanner sc = new Scanner(System.in);
        sc.useDelimiter("");

        try (var socketChannel = SocketChannel.open(server)) {

            List<String> lines = new ArrayList<>();

            while (sc.hasNext()) {
                var input = sc.nextLine();
                if (input.isEmpty()) {
                    break;
                }
                lines.add(input);
            }

            Optional<String> response = getResponse(socketChannel, lines);

            if (response.isEmpty()) {
                logger.warning("Connection with the server lost");
            } else {
                System.out.println(response.get());
                logger.info("Everything seems ok");
            }
        }

    }

    private static Optional<String> getResponse(SocketChannel sc, List<String> lines) throws IOException {
        System.out.println(lines);
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

        buffer.clear().putInt(lines.size());
        try {
            sc.write(buffer.flip());
            logger.info("==> List size send :: " + lines.size());
            buffer.clear();

            for (var line : lines) {

                var tmp = ByteBuffer.allocate(1024).put(UTF8.encode(line)).flip();
                var size = tmp.remaining();

                sc.write(buffer.putInt(size).put(tmp).flip());
                logger.info("==> List send :: " + line);
                buffer.clear();
            }

        } catch (IOException e) {//
            logger.warning("IOException " + e);
            return Optional.empty();
        }

        buffer.clear();

        //on va recevoir un INT
        //buffer.limit de taille INT pour éviter l'attente
        if (readFully(sc, buffer.limit(Integer.BYTES))) {
            var bufferSize = buffer.flip().getInt();
            var bufferRes = ByteBuffer.allocate(bufferSize);

            //pour recevoir la réponse
            if (readFully(sc, bufferRes)) {
                var res = UTF8.decode(bufferRes.flip()).toString();
                logger.info("<== receiving response of size :: " + bufferSize);
                return Optional.of(res);
            }
            return Optional.empty();
        }
        return Optional.empty();
    }

    static boolean readFully(SocketChannel sc, ByteBuffer buffer) throws IOException {
        // TODO
        while (buffer.hasRemaining()) {
            var response = sc.read(buffer);
            if (response == -1) {
                logger.warning("Connection terminated");
                return false;
            } else {
                logger.info("Packet(Bytes) received ::: " + response);
            }
        }
        return true;
    }
}
