package com.example.xinwenwang.vegvisir_lower_level.network;

import android.content.Context;
import android.util.Log;

import com.example.xinwenwang.vegvisir_lower_level.network.ByteStream;
import com.example.xinwenwang.vegvisir_lower_level.network.EndPointConnection;
import com.example.xinwenwang.vegvisir_lower_level.network.Exceptions.ConnectionNotAvailableException;
import com.example.xinwenwang.vegvisir_lower_level.network.Exceptions.HandlerNotRegisteredException;
import com.vegvisir.lower.datatype.proto.Payload;

import java.util.HashMap;
import java.util.function.Function;

/**
 * expose lower network layer APIs to upper layers.
 */
public class Network {

    private HashMap<String, EndPointConnection> endPoints;
    private ByteStream byteStream;
    private Dispatcher dispatcher;
    private Thread pollingThread;


    public Network(Context context, String advisingID) {
        endPoints = new HashMap<>();
        byteStream = new ByteStream(context, advisingID);
        dispatcher = new Dispatcher();
        byteStream.start();
        pollingThread = new Thread(() -> {
            for (;;) {
                String remoteId = onConnection();
                EndPointConnection connection = byteStream.getConnectionByID(remoteId);
                while (connection.isConnected()) {
                    try {
                        Payload payload = connection.blockingRecv();
                        if (!connection.isConnected())
                            break;
                        else if(payload != null)
                            dispatcher.dispatch(remoteId, payload);
                    } catch (InterruptedException ex) {

                    } catch (HandlerNotRegisteredException ex) {
                        Log.e(ex.getLocalizedMessage(), ex.getMessage());
                    }
                }
            }
        });
        pollingThread.start();
    }

    /**
     * Blocking wait for a new connection
     * @return a id for the incoming connection
     */
    public String onConnection() {
        return byteStream.establishConnection().getEndPointId();
    }

    public void send(String id, Payload payload) throws ConnectionNotAvailableException {
        byteStream.getConnectionByID(id).send(payload);
    }

    public void send(Payload payload) throws ConnectionNotAvailableException {
        send(getActiveRemoteID(), payload);
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
     * @param callback the callback function taking received payload.
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
        EndPointConnection connection = byteStream.getConnectionByID(id);
        if (connection != null) {
            connection.ignore(timeout);
        }
    }

    /**
     * Whether this device is connecting to another device at this moment.
     * @return True connected.
     */
    public boolean isConnected() {
        return byteStream.isConnected();
    }

    public String getActiveRemoteID() {
        return byteStream.getActiveEndPoint();
    }
}
