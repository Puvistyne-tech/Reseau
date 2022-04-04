package fr.upem.net.tcp.nonblocking.chaton.server.console;

import fr.upem.net.tcp.nonblocking.Helpers;
import fr.upem.net.tcp.nonblocking.chaton.Message;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerChatonWithConsole {
    private static final Logger logger = Logger.getLogger(ServerChatonWithConsole.class.getName());

    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;

    private final Thread console;

    private enum Commands {
        INFO, SHUTDOWN, SHUTDOWNNOW
    }

    private final ArrayBlockingQueue<Commands> commandQueue = new ArrayBlockingQueue<>(1);

    public ServerChatonWithConsole(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();

        this.console = new Thread(this::consoleRun);
    }

    private void consoleRun() {

        try {
            try (var scanner = new Scanner(System.in)) {
                while (scanner.hasNextLine()) {
                    var command = scanner.nextLine();
                    sendCommands(command);
                }
            }
            logger.info("Console thread stopping");
        } catch (InterruptedException e) {
            logger.info("Console thread has been interrupted");
        }

    }

    private void sendCommands(String command) throws InterruptedException {

        if (commandQueue.isEmpty()) {
            try {
                commandQueue.put(Commands.valueOf(command.toUpperCase()));
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid Command");
            }
            selector.wakeup();
        } else logger.info("Please wait!!!");
    }

    private void receiveCommand() throws IOException {

        if (commandQueue.isEmpty()) return;

        switch (commandQueue.poll()) {
            case INFO -> showAllClients();
            case SHUTDOWN -> serverSocketChannel.close();
            case SHUTDOWNNOW -> shutdownServer();
            default -> logger.warning("Invalid Command");
        }
    }

    private void showAllClients() {
        System.out.println("Clients Connected");
        AtomicInteger count = new AtomicInteger();
        selector.keys().forEach(oneKey -> {
            var context = (Context) oneKey.attachment();
            var client = oneKey.channel();
            if (client != serverSocketChannel && !context.isClosed()) {
                System.out.println(context);
                count.getAndIncrement();
            }
        });
        System.out.println("\nnumber of clients connected :: " + count);
    }

    private void shutdownServer() throws IOException {
        logger.info("Good Bye...");
        selector.keys().forEach(this::silentlyClose);
        serverSocketChannel.close();
        Thread.currentThread().interrupt();
    }

    public void launch() throws IOException {
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        console.start();

        while (!Thread.interrupted()) {
            Helpers.printKeys(selector); // for debug
            System.out.println("Starting select");
            try {
                selector.select(this::treatKey);
                receiveCommand();
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
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
        } catch (IOException ioe) {
            // lambda call in select requires to tunnel IOException
            throw new UncheckedIOException(ioe);
        }
        try {
            if (key.isValid() && key.isWritable()) {

                ((Context) key.attachment()).doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                ((Context) key.attachment()).doRead();
            }
        } catch (IOException e) {
            logger.log(Level.INFO, "Connection closed with client due to IOException", e);
            silentlyClose(key);
        }
    }

    private void doAccept(SelectionKey key) throws IOException {
        // TODO

        var sc = serverSocketChannel.accept();


        if (sc == null) {
            logger.warning("Selector Lied");
            return;
        }

        sc.configureBlocking(false);
        var clientKey = sc.register(selector, SelectionKey.OP_READ);

        clientKey.attach(new Context(this, clientKey));
    }

    private void silentlyClose(SelectionKey key) {
        Channel sc = (Channel) key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }




    /**
     * Add a message to all connected clients queue
     *
     * @param msg
     */
    public void broadcast(Message msg) {
        // TODO

        selector.keys().forEach(k -> {
            if (k.channel() == serverSocketChannel) return;

            var currentClient = msg.username();
            var context = (Context) k.attachment();

            if (context.getUsername() != currentClient) {
                (context).queueMessage(msg);
            }

        });
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        new ServerChatonWithConsole(Integer.parseInt(args[0])).launch();
    }

    private static void usage() {
        System.out.println("Usage : ServerChatonWithConsole port");
    }
}
