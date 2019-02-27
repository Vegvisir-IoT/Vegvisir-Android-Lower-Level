package com.example.xinwenwang.vegvisir_lower_level.network;

import android.content.Context;
import android.util.Log;

import com.example.xinwenwang.vegvisir_lower_level.network.ByteStream;
import com.example.xinwenwang.vegvisir_lower_level.network.EndPointConnection;
import com.example.xinwenwang.vegvisir_lower_level.network.Exceptions.ConnectionNotAvailableException;
import com.example.xinwenwang.vegvisir_lower_level.network.Exceptions.HandlerNotRegisteredException;
import com.vegvisir.lower.datatype.proto.Payload;

import java.util.HashMap;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Function;

/**
 * expose lower network layer APIs to upper layers.
 */
public class Network {

    private HashMap<String, EndPointConnection> endPoints;
    private ByteStream byteStream;
    private Dispatcher dispatcher;
    private Thread pollingThread;
    private BlockingDeque<String> activeConnection;


    public Network(Context context, String advisingID) {
        endPoints = new HashMap<>();
        byteStream = new ByteStream(context, advisingID);
        dispatcher = new Dispatcher();
        activeConnection = new LinkedBlockingDeque<>(1);
        byteStream.start();
        startDispatcher();
    }

    /**
     * Start dispatching arrived payload by running a separate polling thread whose job is
     * keeping blocking reading input from current connection.
     */
    private void startDispatcher() {
        pollingThread = new Thread(() -> {
            for (;;) {
                String remoteId = waitingConnection();
                activeConnection.add(remoteId);
                EndPointConnection connection = byteStream.getConnectionByID(remoteId);
                while (connection.isConnected()) {
                    try {
                        Payload payload = connection.blockingRecv();
                        if (payload == null)
                            break;
                        else
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
        try {
            return activeConnection.take();
        } catch (InterruptedException ex) {
            return null;
        }
    }

    private String waitingConnection() {
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

    /**
     * Register a RPC @handler for given @id. There should be only one handler for a particular RPC id. If RPC id has
     * been associated with another handler. This will return false. Use PayloadHandler setRecvHandler() function to
     * update handler instead.
     * @param id
     * @param handler
     * @return true if register successfully
     */
    public boolean registerHandler(String id, PayloadHandler handler) {
        return dispatcher.registerHandler(id, handler);
    }
}
