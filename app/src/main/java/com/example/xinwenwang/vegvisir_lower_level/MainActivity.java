package com.example.xinwenwang.vegvisir_lower_level;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.vegvisir.lower.datatype.proto.Payload;

import java.util.concurrent.BlockingDeque;

public class MainActivity extends AppCompatActivity {

    Network network;
    String remoteid;
    Thread updateThread;
    BlockingDeque<String> tempBuf;
    Object lock = new Object();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* Get Permission */
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION)) {
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
            }
        } else {
        }

        networkInit();
        checkConnection();

        final Button sendButton = findViewById(R.id.send_btn);
        sendButton.setOnClickListener((v) -> {
            Log.d("SendBtn", "onCreate: click send");
            final EditText input = findViewById(R.id.editText);
            boolean suc = sendPing(input.getText().toString());
            if (!suc)
                Log.d("SendBtn", "onCreate: msg "+input.getText().toString()+"sent failed");
            else
                Log.d("SendBtn", "onCreate: msg "+input.getText().toString()+"sent");
        });
    }

    private void networkInit() {
        getNetwork();

        new Thread(() -> {
            String incomingId;
            while(true) {
                incomingId = network.onConnection();
                synchronized (lock) {
                    remoteid = incomingId;
                }
                updateBanner("New Peer Connected: " + remoteid);
            }
        }).start();

        if (updateThread == null)  {
            updateThread = new Thread(
                    () -> {
                        while (true) {
                            updateView();
                        }
                    }
            );
            updateThread.start();
        }
    }

    private void updateView() {
        if (remoteid != null) {
            Payload payload = network.recv(remoteid);
            runOnUiThread(() -> {
                TextView textView = new TextView(getApplicationContext());
                textView.setText(payload.getInfo());
                LinearLayout layout = findViewById(R.id.updates);
                layout.addView(textView);
            });
        }
    }

    private boolean sendPing(String info) {
        if (remoteid != null) {
            network.send(remoteid, Payload.newBuilder().setInfo(info).build());
            return true;
        } else {
            updateBanner("No Peer Connected");
            return false;
        }
    }

    private synchronized Network getNetwork() {
        if (network == null)
            network = new Network(this.getApplicationContext(), "PingPong");
        return network;
    }

    private synchronized void updateBanner(String info) {
        TextView banner =  findViewById(R.id.Banner);
        runOnUiThread(() -> {
            banner.setText(info);
            banner.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).withEndAction(() -> {
                banner.animate().scaleX(1).scaleY(1).setDuration(200);
            });

        });

    }

    private void checkConnection() {
        new Thread(() -> {
            while(true) {
                synchronized (lock) {
                    if (!network.isConnected() && remoteid != null) {
                        updateBanner("Peer Lost");
                        remoteid = null;
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {

                }
            }
        }).start();

    }

}
