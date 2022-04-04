package fr.upem.net.tcp.nonblocking.chat;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class Queue {

    private String message;

    public boolean isEmpty(){
        return false;
    }

    public String getMessage(){
        return message;
    }

}
