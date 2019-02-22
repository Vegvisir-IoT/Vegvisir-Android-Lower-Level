package com.example.xinwenwang.vegvisir_lower_level;

import android.content.Context;

import com.google.android.gms.tasks.Task;
import com.vegvisir.lower.datatype.proto.Payload;
import com.vegvisir.lower.datatype.proto.SendResponse;

import java.util.HashMap;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Function;

/**
 * expose lower network layer APIs to upper layers.
 */
public class Network {

    private HashMap<String, Connection> endPoints;
    private ByteStream byteStream;

    public Network(Context context, String advisingID) {
        endPoints = new HashMap<>();
        byteStream = new ByteStream(context, advisingID);
        byteStream.start();
    }

    /**
     * Blocking wait for a new connection
     * @return a id for the incoming connection
     */
    public String onConnection() {
        return byteStream.establishConnection().getEndPointId();
    }

    public void send(String id, Payload payload) {
        byteStream.getConnectionByID(id).send(payload);
    }

    /**
     * Blocking until new payload arrived from connection @id
     * @param id connection id
     * @return
     */
    public Payload recv(String id) {
        try {
            return byteStream.getConnectionByID(id).blockingRecv();
        } catch (InterruptedException e) {
            return null;
        }
    }

    /**
     * Unblocking handling received payload
     * @param id the connection id
     * @param callback the callbck function taking received payload.
     */
    public void recv(final String id, Function<Payload, Void> callback) {
        new Thread(() -> {
            try {
                callback.apply(byteStream.getConnectionByID(id).blockingRecv());
            } catch (InterruptedException e) {
            }
        }).run();
    }

    /**
     * Ignore connections from device with @id for @timeout milliseconds.
     * @param id connection/device id
     * @param timeout in milliseconds
     */
    public void ignore(String id, Long timeout) {
        Connection connection = byteStream.getConnectionByID(id);
        if (connection != null) {
            connection.ignore(timeout);
        }
    }

    public boolean isConnected() {
        return byteStream.isConnected();
    }
}
