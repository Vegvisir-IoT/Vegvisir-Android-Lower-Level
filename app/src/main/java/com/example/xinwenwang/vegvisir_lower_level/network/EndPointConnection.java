package com.example.xinwenwang.vegvisir_lower_level.network;

import android.content.Context;
import android.support.annotation.NonNull;

import com.example.xinwenwang.vegvisir_lower_level.Utils.Utils;
import com.example.xinwenwang.vegvisir_lower_level.network.Exceptions.ConnectionNotAvailableException;
import com.vegvisir.lower.datatype.proto.Identifier;
import com.vegvisir.lower.datatype.proto.Payload;
import com.vegvisir.lower.datatype.proto.Timestamp;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Used for storing states for each connection
 */
public class EndPointConnection {

    private String endPointId;

    private java.util.function.Function<Payload, Void> recvHandler;

    private BlockingDeque<Payload> recvQueue;

    private ByteStream stream;

    private Long wakeupTime;

    private Long connectedTime;

    private boolean connected;

    public EndPointConnection(@NonNull String endPointId,
                              @NonNull Context context,
                              @NonNull ByteStream stream) {
        this.endPointId = endPointId;
        this.stream = stream;
        this.recvQueue = new LinkedBlockingDeque<>();
        connectedTime = Utils.getTimeInMilliseconds();
        wakeupTime = Utils.getTimeInMilliseconds();
    }

    /**
     * Send @payload to remote device.
     * @param payload
     * @throws ConnectionNotAvailableException
     */
    public void send(Payload payload) throws ConnectionNotAvailableException {
        if (isConnected())
            stream.send(endPointId, payload);
        else
            throw new ConnectionNotAvailableException();
    }

    /**
     * Save new payload to receiving queue.
     * @param payload
     */
    public void onRecv(Payload payload) {
        recvQueue.push(payload);
    }

    /**
     * Wait until next payload available for this connection.
     * @return the arrived payload.
     * @throws InterruptedException
     */
    public Payload blockingRecv() throws InterruptedException {
        return recvQueue.take();
    }

    @Deprecated
    public Payload recv() {
        return recvQueue.remove();
    }

    /**
     * @return whether this connection is ignored or not.
     */
    public boolean isWakeup() {
        return Utils.getTimeInMilliseconds() > wakeupTime;
    }

    /**
     * Ignore any connections from this endpoint.
     * @param duration amount of time in milliseconds.
     */
    public void ignore(Long duration) {
        this.wakeupTime = duration + Utils.getTimeInMilliseconds();
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
        if (this.connected == false) {
            recvQueue.add(Payload.newBuilder().build());
        }
    }

    /**
     * @return whether this connection is live.
     */
    public boolean isConnected() {
        return connected;
    }

    @Deprecated
    public com.vegvisir.lower.datatype.proto.Connection toProtoConnection() {
        return com.vegvisir.lower.datatype.proto.Connection.newBuilder()
                .setRemoteId(Identifier.newBuilder().setName(endPointId).build())
                .setWakeupTime(Timestamp.newBuilder().setUtcTime(wakeupTime).build())
                .setConnectedTime(Timestamp.newBuilder().setElapsedTime(connectedTime).build())
                .build();
    }

    public String getEndPointId() {
        return endPointId;
    }
}
