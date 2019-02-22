package com.example.xinwenwang.vegvisir_lower_level;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Pair;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import com.google.android.gms.tasks.Task;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * A stream connection in Google nearby
 */
public class ByteStream {

    private static final String SERVICE_ID = "Vegvisir-IoT";

    private static final Strategy STRATEGY = Strategy.P2P_STAR;

    private BlockingDeque<Pair<String, com.vegvisir.lower.datatype.proto.Payload>> sendQueue;

    private BlockingDeque<com.vegvisir.lower.datatype.proto.Payload> recvQueue;

    private Context appContext;

    private ConnectionsClient client;

    private String advisingID;

    private HashMap<String, com.example.xinwenwang.vegvisir_lower_level.Connection> connections;

    private BlockingDeque<com.example.xinwenwang.vegvisir_lower_level.Connection> establishedConnection;

    private Object lock;

    private String activeEndPoint;

    private ByteStream self;


    /* Callbacks for receiving payloads */
    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endPointId, @NonNull Payload payload) {
            if (connections.containsKey(endPointId)) {
                recv(endPointId, payload);
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endPointId, @NonNull PayloadTransferUpdate payloadTransferUpdate) {

        }
    };

    /* Callbacks for finding other devices */
    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String endPoint, @NonNull DiscoveredEndpointInfo discoveredEndpointInfo) {
            if (discoveredEndpointInfo.getServiceId().equals(SERVICE_ID) &&
                    (!connections.containsKey(endPoint) || (connections.containsKey(endPoint) &&
                    connections.get(endPoint).isWakeup() && !connections.get(endPoint).isConnected()))) {
                    client.requestConnection(advisingID, endPoint, connectionLifecycleCallback);
                }
        }

        @Override
        public void onEndpointLost(@NonNull String s) {
            Log.d("INFO", "ENDPOINT LOST");

        }
    };

    /* Callbacks for connections to other devices */
    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endPoint, @NonNull ConnectionInfo connectionInfo) {
            synchronized (lock) {
                if (activeEndPoint == null) {
                    client.acceptConnection(endPoint, payloadCallback);
                } else {
                    client.rejectConnection(endPoint);
                }
            }
        }

        @Override
        public void onConnectionResult(@NonNull String endPoint, @NonNull ConnectionResolution connectionResolution) {
            synchronized (lock) {
                if (connectionResolution.getStatus().isSuccess()) {
                    activeEndPoint = endPoint;
                    client.stopDiscovery();
                    client.stopAdvertising();
                    connections.putIfAbsent(endPoint, new com.example.xinwenwang.vegvisir_lower_level.Connection(endPoint,
                            appContext,
                            self));
                    connections.get(endPoint).setConnected(true);
                    establishedConnection.push(connections.get(endPoint));
                } else {
                    Log.i("Vegivsir-Connection", "connection failed");
                }
            }
        }

        @Override
        public void onDisconnected(@NonNull String endPoint) {
            synchronized (lock) {
                activeEndPoint = null;
                connections.get(endPoint).setConnected(false);
                startAdvertising();
                startDiscovering();
            }
        }
    };


    public ByteStream(Context context, String advisingID) {
        appContext = context;
        client = Nearby.getConnectionsClient(appContext);
        this.advisingID = advisingID;
        lock = new Object();
        establishedConnection = new LinkedBlockingDeque<>(1);
        connections = new HashMap<>();
        self = this;
    }

    public Connection getConnectionByID(String id) {
        return connections.get(id);
    }

    /**
     * Set the device to discovery mode. It will now listen for devices in advertising mode.
     */
    public void startDiscovering() {
        client.startDiscovery(SERVICE_ID,
                endpointDiscoveryCallback,
                new DiscoveryOptions.Builder().setStrategy(STRATEGY).build())
                .addOnSuccessListener(
                        (Void unused) -> {
                            Log.d("INFO", "startDiscovering: success");
                            // We're advertising!
                        })
                .addOnFailureListener(
                        (Exception e) -> {
                            Log.d("INFO", "startDiscovering: failed");
                            e.printStackTrace();
                            // We were unable to start advertising.
                        });
    }

    public void startAdvertising() {
        client.startAdvertising(advisingID,
                SERVICE_ID,
                connectionLifecycleCallback,
                new AdvertisingOptions.Builder().setStrategy(STRATEGY).build())
                .addOnSuccessListener(
                        (Void unused) -> {
                            Log.d("INFO", "startAdvertising: success");
                            // We're advertising!
                        })
                .addOnFailureListener(
                        (Exception e) -> {
                            Log.d("INFO", "startAdvertising: failed");
                            e.printStackTrace();
                            // We were unable to start advertising.
                        });
    }

    public Task<Void> send(String dest, com.vegvisir.lower.datatype.proto.Payload payload) {
        InputStream stream = new ByteArrayInputStream(payload.toByteArray());
        return client.sendPayload(dest, Payload.fromStream(stream));
    }

    public void recv(String remoteId, Payload payload) {
        try {
            com.vegvisir.lower.datatype.proto.Payload arrivedData = com.vegvisir.lower.datatype.proto.Payload.parseFrom(payload.asStream().asInputStream());
            connections.get(remoteId).onRecv(arrivedData);
        } catch (InvalidProtocolBufferException e) {

        } catch (IOException ex) {

        }
    }

    public com.example.xinwenwang.vegvisir_lower_level.Connection establishConnection() {
        try {
            return establishedConnection.take();
        } catch (InterruptedException e) {
            return null;
        }
    }

    public void start() {
        startAdvertising();
        startDiscovering();
    }

    public void pause() {
        client.stopDiscovery();
        client.stopAdvertising();
        client.stopAllEndpoints();
    }

    public boolean isConnected() {
        return activeEndPoint != null;
    }
}
