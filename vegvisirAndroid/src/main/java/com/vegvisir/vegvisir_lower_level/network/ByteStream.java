package com.vegvisir.vegvisir_lower_level.network;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.util.Pair;
import android.util.Log;

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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A stream connection in Google nearby
 */
public class ByteStream {

    private static final String SERVICE_ID = "Vegvisir-IoT";

    private static final Strategy STRATEGY = Strategy.P2P_STAR;

    private Context appContext;

    private ConnectionsClient client;

    private String advisingID;

    /* EndPointConnection mapping form id to connection */
    private HashMap<String, EndPointConnection> connections;

    private BlockingDeque<EndPointConnection> establishedConnection;

    private Object lock;

    private String activeEndPoint;

    private ByteStream self;

    private BlockingQueue<Pair<String, com.vegvisir.network.datatype.proto.Payload>> cachePayload;

    private BlockingQueue<String> disconnectedId;

    private Set<String> nearbyEndpoints = new HashSet<>();

    private boolean isDiscovering = false;

    private static final boolean ENABLE_GOOGLE_NEARBY = true;

    private static final String TAG = ByteStream.class.getName();


    /* Callbacks for receiving payloads */
    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endPointId, @NonNull Payload payload) {
            if (connections.containsKey(endPointId)) {
                recv(endPointId, payload);
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endPointId, @NonNull
                PayloadTransferUpdate payloadTransferUpdate) {
        }
    };

    /* Callbacks for finding other devices */
    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String endPoint, @NonNull DiscoveredEndpointInfo
                discoveredEndpointInfo) {
            Log.i(TAG, "onEndpointFound: "+ discoveredEndpointInfo.getEndpointName() + "/" + endPoint);
            if (discoveredEndpointInfo.getServiceId().equals(SERVICE_ID))
                nearbyEndpoints.add(endPoint);
            if (discoveredEndpointInfo.getServiceId().equals(SERVICE_ID) &&
                    (!connections.containsKey(endPoint) || (connections.containsKey(endPoint) &&
                    connections.get(endPoint).isWakeup() && !connections.get(endPoint).isConnected()))) {
                    Task<Void> requestTask = client.requestConnection(advisingID, endPoint, connectionLifecycleCallback);
                requestTask.addOnFailureListener((t) -> {
                    Log.e(TAG, "onEndpointFound: ", t);
                    Log.d(TAG, "onEndpointFound: " + t.getMessage());
                    if (t.getMessage().equals("8012: STATUS_ENDPOINT_IO_ERROR")) {
                        restart();
                    }
                });
                }
        }

        @Override
        public void onEndpointLost(@NonNull String endpoint) {
            Log.d("INFO", "ENDPOINT LOST");
            nearbyEndpoints.remove(endpoint);
        }
    };

    /* Callbacks for connections to other devices */
    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endPoint, @NonNull ConnectionInfo connectionInfo) {
            Log.d(TAG, "onConnectionInitiated: Received Connection Request");
            if (activeEndPoint != null) {
                client.rejectConnection(endPoint);
                Log.d(TAG, "onConnectionInitiated: Rejected request");
            }
            else {
                synchronized (lock) {
                    if (activeEndPoint == null) {
                        client.acceptConnection(endPoint, payloadCallback);
                        Log.d(TAG, "onConnectionInitiated: Accepted request");
                    } else {
                        client.rejectConnection(endPoint);
                        Log.d(TAG, "onConnectionInitiated: Rejected request");
                    }
                }
            }

        }

        @Override
        public void onConnectionResult(@NonNull String endPoint, @NonNull ConnectionResolution connectionResolution) {

            Log.d(TAG, "onConnectionResult: " + connectionResolution.getStatus().getStatusMessage());

            if (activeEndPoint != null)
                return;

            synchronized (lock) {
                if (activeEndPoint == null) {
                    if (connectionResolution.getStatus().isSuccess()) {
                        activeEndPoint = endPoint;
                        client.stopDiscovery();
                        client.stopAdvertising();
                        synchronized (this) {
                            isDiscovering = false;
                        }
                        connections.putIfAbsent(endPoint, new EndPointConnection(endPoint,
                                appContext,
                                self));
                        connections.get(endPoint).setConnected(true);
                        establishedConnection.push(connections.get(endPoint));
                        Log.i(TAG, "onConnectionResult: Connection established!");
                    } else {
                        Log.i("Vegivsir-EndPointConnection", "connection failed");
                        restart();
                    }
                }
            }
        }

        @Override
        public void onDisconnected(@NonNull String endPoint) {
            synchronized (lock) {
                activeEndPoint = null;
                connections.get(endPoint).setConnected(false);
                disconnectedId.add(endPoint);
            }
            Log.d(TAG, "disconnect: Disconnected with " + endPoint);
            start();
        }
    };


    public ByteStream(Context context, String advisingID) {
        appContext = context;
        client = Nearby.getConnectionsClient(appContext);
        this.advisingID = advisingID;
        lock = new Object();
        establishedConnection = new LinkedBlockingDeque<>(1);
        connections = new HashMap<>();
        cachePayload = new LinkedBlockingQueue<>();
        self = this;
        disconnectedId = new LinkedBlockingQueue<>();
    }

    public EndPointConnection getConnectionByID(String id) {
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
                        })
                .addOnCanceledListener(() -> {
                    Log.d(TAG, "startDiscovering: startDiscovering Cancelled");
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
                        })
                .addOnCanceledListener(() -> {
                    Log.d(TAG, "startAdvertising: startAdvertising: Cancelled");
                })
                ;
    }

    /**
     * Send payload to remote device
     * @param dest
     * @param payload
     * @return
     */
    public Task<Void> send(String dest, com.vegvisir.network.datatype.proto.Payload payload) {
        if (ENABLE_GOOGLE_NEARBY) {
            InputStream stream = new ByteArrayInputStream(payload.toByteArray());
            Task<Void> task = client.sendPayload(dest, Payload.fromStream(stream));
            return task;
        } else {
            connections.get(dest).onRecv(payload);
            return null;
        }
    }

    /**
     * Receive payload from specific remote device.
     * @param remoteId
     * @param payload
     */
    public void recv(String remoteId, Payload payload) {
        try {
            com.vegvisir.network.datatype.proto.Payload arrivedData = com.vegvisir.network.datatype
                    .proto.Payload.parseFrom(payload.asStream().asInputStream());
            cachePayload.add(new Pair<>(remoteId, arrivedData));
            connections.get(remoteId).onRecv(arrivedData);
        } catch (InvalidProtocolBufferException e) {

        } catch (IOException ex) {

        }
    }

    /**
     * [BLOCKING] waiting until a new data arrived.
     * @return
     */
    public Pair<String, com.vegvisir.network.datatype.proto.Payload> blockingRecv() {
        try {
            return cachePayload.take();
        } catch (InterruptedException ex) {
            return null;
        }
    }

    /**
     * Waiting for new connection available.
     * @return
     */
    public EndPointConnection establishConnection() {
        try {
            return establishedConnection.take();
        } catch (InterruptedException e) {
            return null;
        }
    }

    /**
     * Start google nearby broadcast.
     */
    public void start() {
        if (ENABLE_GOOGLE_NEARBY) {
            synchronized (this) {
                if (isDiscovering)
                    return;
                client.stopAllEndpoints();
                startAdvertising();
                startDiscovering();
                isDiscovering = true;
            }
        } else {
            String endPoint = "testConn";
            connections.putIfAbsent(endPoint, new
                    EndPointConnection(endPoint,
                    appContext,
                    self));
            activeEndPoint = endPoint;
            connections.get(activeEndPoint).setConnected(true);
            establishedConnection.add(connections.get(activeEndPoint));
        }
    }

    public Set<String> getNearbyEndpoints() {
        return nearbyEndpoints;
    }

    /**
     * Disconnect from particular endpoint.
     * @param id
     */
    public void disconnect(String id) {
        if (id.equals(getActiveEndPoint())) {
            connections.get(id).waitUntilFlushAllData();
//            client.disconnectFromEndpoint(id);
            synchronized (lock) {
                connections.get(id).setConnected(false);
                disconnectedId.add(id);
                activeEndPoint = null;
                start();
            }
        }
        Log.d(TAG, "disconnect: Disconnected with " + id);
    }

    public void pause() {
        client.stopDiscovery();
        client.stopAdvertising();
        client.stopAllEndpoints();
    }

    private void restart() {
        synchronized (this) {
            isDiscovering = false;
            client.stopDiscovery();
            client.stopAdvertising();
            start();
            Log.d(TAG, "onEndpointFound: Restarted!");
        }
    }

    public boolean isConnected() {
        return activeEndPoint != null;
    }

    public String getActiveEndPoint() {
        synchronized (lock) {
            return activeEndPoint;
        }
    }

    public BlockingQueue<String> getDisconnectedId() {
        return disconnectedId;
    }
}
