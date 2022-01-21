package fr.upem.net.buffers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;

public class ReadStandardInputWithEncoding {

    private static final int BUFFER_SIZE = 1024;

    private static void usage() {
        System.out.println("Usage: ReadStandardInputWithEncoding charset");
    }

    private static String stringFromStandardInput(Charset cs) throws IOException {
        // TODO
        ReadableByteChannel in = Channels.newChannel(System.in);
        ByteBuffer src = ByteBuffer.allocate(BUFFER_SIZE);
        while (in.read(src) != -1) {
            if (!src.hasRemaining()) {
                ByteBuffer dst = ByteBuffer.allocate(src.capacity() * 2);

                src.flip();
                dst.put(src);
                // src=ByteBuffer.allocate(dst.capacity()*2);
                // src.put(dst);

                src = dst;
            }

        }
        // var bb = cs.decode(src);
        src.flip();

        return cs.decode(src).toString();
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        Charset cs = Charset.forName(args[0]);
        System.out.print(stringFromStandardInput(cs));
    }
}