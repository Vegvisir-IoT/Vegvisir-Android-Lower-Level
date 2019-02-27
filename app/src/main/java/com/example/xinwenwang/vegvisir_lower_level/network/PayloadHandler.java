package com.example.xinwenwang.vegvisir_lower_level.network;

import android.util.Log;
import android.support.v4.util.Pair;

import com.example.xinwenwang.vegvisir_lower_level.network.Exceptions.HandlerAlreadyExistsException;
import com.example.xinwenwang.vegvisir_lower_level.network.Exceptions.HandlerNotRegisteredException;
import com.vegvisir.lower.datatype.proto.Payload;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class PayloadHandler implements Runnable {

    private BlockingDeque<Pair<String, Payload>> payloads;
    private Handler handler;
    private Object handlerLock = new Object();
    private Thread runningThread;

    public PayloadHandler() {
        this.payloads = new LinkedBlockingDeque<>();
    }

    public PayloadHandler(Handler handler) {
        this();
        this.handler = handler;
    }

    public void onNewPayload(String remoteId, Payload payload) {
        payloads.add(new Pair<>(remoteId, payload));
    }

    public Pair<String, Payload> blockingRecv() throws InterruptedException, HandlerAlreadyExistsException {
        if (handler != null) {
            throw new HandlerAlreadyExistsException();
        }
        return payloads.take();
    }

    public void setRecvHandler(Handler handler) {
        synchronized (handlerLock) {
            this.handler = handler;
            if (runningThread != null) {
                runningThread.interrupt();
            }
        }
    }

    public void removeRecvHandler() {
        synchronized (handlerLock) {
            this.handler = null;
        }
    }

    @Override
    public void run() {
        runningThread = Thread.currentThread();
        while (true) {
            if (handler == null) {
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException ex) {
                    if (handler == null)
                        continue;
                }
            }
            /* handler already exists */
            try {
                Pair<String, Payload> input = payloads.take();
                synchronized (handlerLock) {
                    if (handler != null) {
                        handler.handle(input);
                    } else {
                        payloads.addFirst(input);
                    }
                }
            } catch (InterruptedException ex) {}
        }
    }

    class Builder {

       PayloadHandler handler;

       Builder() {
           handler = new PayloadHandler();
       }

    }

    public interface Handler {
        void handle(Pair<String, Payload> data);
    }
}
