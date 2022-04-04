package fr.upem.net.tcp.nonblocking.chaton;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class MessageTest {

    @Test
    public void simple() {
        var username = "Chorche";
        var text = "Zebiiii!";
        var buffer = ByteBuffer.allocate(1024);
        var usernameBytes = StandardCharsets.UTF_8.encode(username);
        var messageBytes = StandardCharsets.UTF_8.encode(text);
        buffer
                .putInt(usernameBytes.remaining())
                .put(usernameBytes)
                .putInt(messageBytes.remaining())
                .put(messageBytes);

        var reader = new MessageReader();
        assertEquals(Reader.ProcessStatus.DONE, reader.process(buffer));
        var message = reader.get();
        assertEquals(username, message.username());
        assertEquals(text, message.text());
        assertEquals(0, buffer.position());
        assertEquals(buffer.capacity(), buffer.limit());
    }


}