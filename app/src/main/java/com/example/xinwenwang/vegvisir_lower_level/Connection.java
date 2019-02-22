package com.example.xinwenwang.vegvisir_lower_level;

import android.content.Context;
import android.support.annotation.NonNull;

import com.vegvisir.lower.datatype.proto.Identifier;
import com.vegvisir.lower.datatype.proto.Payload;
import com.vegvisir.lower.datatype.proto.Timestamp;

import java.util.Date;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Function;

/**
 * Used for storing states for each connection
 */
public class Connection {

    private String endPointId;

    private java.util.function.Function<Payload, Void> recvHandler;

    private BlockingDeque<Payload> recvQueue;

    private ByteStream stream;

    private Long wakeupTime;

    private Long connectedTime;

    private boolean connected;

    public Connection(@NonNull String endPointId,
                      @NonNull Context context,
                      @NonNull ByteStream stream) {
        this.endPointId = endPointId;
        this.stream = stream;
        this.recvQueue = new LinkedBlockingDeque<>();
        connectedTime = Utils.getTimeInMilliseconds();
        wakeupTime = Utils.getTimeInMilliseconds();
    }

    public void send(Payload payload) {
        stream.send(endPointId, payload);
    }

    public void onRecv(Payload payload) {
        recvQueue.push(payload);
    }

    public Payload blockingRecv() throws InterruptedException {
        return recvQueue.take();
    }

    public Payload recv() {
        return recvQueue.remove();
    }

    public boolean isWakeup() {
        return Utils.getTimeInMilliseconds() > wakeupTime;
    }

    public void ignore(Long duration) {
        this.wakeupTime = duration + Utils.getTimeInMilliseconds();
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public boolean isConnected() {
        return connected;
    }

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
