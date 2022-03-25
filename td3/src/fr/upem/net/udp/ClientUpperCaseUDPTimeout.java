package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ClientUpperCaseUDPTimeout {
    private static final int capacity = 2;

    private static final Logger LOGGER = Logger.getLogger(ClientUpperCaseUDPTimeout.class.getName());

    public static final int BUFFER_SIZE = 1024;

    private static void usage() {
        System.out.println("Usage : NetcatUDP host port charset");
    }

    private record Packet(InetSocketAddress address, int buffer, String message) {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            usage();
            return;
        }

        //ArrayBlockingQueue<Packet> queue = new ArrayBlockingQueue<>(capacity);

        var server = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
        var cs = Charset.forName(args[2]);
        var buffer = ByteBuffer.allocate(BUFFER_SIZE);
//        var bb = ByteBuffer.allocate(BUFFER_SIZE);
        DatagramChannel channel = DatagramChannel.open();

        try (var scanner = new Scanner(System.in);) {
            while (scanner.hasNextLine()) {
                var line = scanner.nextLine();
                var bytes = cs.encode(line);

                channel.send(bytes, server);
                LOGGER.info("Sent : " + line);

                var queue = new ArrayBlockingQueue<Packet>(1);
                var listener = new Thread(() -> {
                    try {
                        var sender = (InetSocketAddress) channel.receive(buffer);
                        buffer.flip();
                        queue.put(new Packet(sender, buffer.remaining(), cs.decode(buffer).toString()));
                        buffer.clear();
                    } catch (InterruptedException e) {
                        LOGGER.warning("Listener interrupted");
                    } catch (IOException e) {
                        LOGGER.warning("Error while receiving");
                    }
                });

                listener.start();
                var packet = queue.poll(2, TimeUnit.SECONDS);
                listener.interrupt();
                if (packet == null) {
                    LOGGER.warning("No answer from the server");
                } else {
                    LOGGER.warning("Received %d bytes from %s : %s%n".formatted(
                            buffer.remaining(),
                            packet.address(),
                            packet.message()
                    ));
                }
//                // TODO
//                buffer.put(cs.encode(line));
//                buffer.flip();
//                dc.bind(null);
//
//                dc.send(buffer, server);
//                buffer.clear();
//
//                new Thread(() -> {
//                    try {
//                        queue.poll(2, TimeUnit.SECONDS);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }).start();
//                InetSocketAddress exp = (InetSocketAddress) dc.receive(bb);
//                bb.flip();
//                System.out.println("Received " + bb.remaining() + " bytes from " + exp);
//                System.out.println("String :" + cs.decode(bb));
//                bb.clear();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        channel.close();
    }
}
