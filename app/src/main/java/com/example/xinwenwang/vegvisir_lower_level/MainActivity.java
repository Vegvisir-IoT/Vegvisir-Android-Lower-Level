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
import android.widget.RadioGroup;
import android.widget.TextView;

import com.example.xinwenwang.vegvisir_lower_level.network.Exceptions.ConnectionNotAvailableException;
import com.example.xinwenwang.vegvisir_lower_level.network.Network;
import com.example.xinwenwang.vegvisir_lower_level.network.PayloadHandler;
import com.vegvisir.lower.datatype.proto.Payload;

public class MainActivity extends AppCompatActivity {

    Network network;
    String remoteid;
    Thread updateThread;
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
            final RadioGroup group = findViewById(R.id.RadioGroup1);
            String id;
            switch (group.getCheckedRadioButtonId()) {
                case R.id.radioButton1:
                    id = "echo";
                    break;
                case R.id.radioButton2:
                    id = "reverse";
                    break;
                case R.id.radioButton3:
                    id = "uppercase";
                    break;
                default:
                    id = "print";
            }
            boolean suc =   sendPing(id, input.getText().toString());
            if (!suc)
                Log.d("SendBtn", "onCreate: msg "+input.getText().toString()+"sent failed");
            else
                Log.d("SendBtn", "onCreate: msg "+input.getText().toString()+"sent");
        });
    }

    /**
     * Initialize the network module.
     */
    private void networkInit() {
        getNetwork();

        new Thread(() -> {
            String incomingId;
            while(true) {
                incomingId = network.onConnection();
                if (incomingId == null)
                    continue;
                synchronized (lock) {
                    remoteid = incomingId;
                }
                updateBanner("New Peer Connected: " + remoteid);
            }
        }).start();
    }

    /**
     * Update view with new message
     * @param payload a Payload contains messages to be shown on the screen.
     */
    private void updateView(Payload payload) {
        if (remoteid != null) {
//            Payload payload = network.recv(remoteid);
            runOnUiThread(() -> {
                TextView textView = new TextView(getApplicationContext());
                textView.setText(payload.getInfo());
                LinearLayout layout = findViewById(R.id.updates);
                layout.addView(textView);
            });
        }
    }

    /**
     * Sending a ping message.
     * @param id the remote id
     * @param info the message to be sent.
     * @return true if sending successfully.
     */
    private boolean sendPing(String id, String info) {
        if (remoteid != null) {
            try {
                network.send(remoteid, Payload.newBuilder().setType(id).setInfo(info).build());
                return true;
            } catch (ConnectionNotAvailableException ex) {
                updateBanner("Connection Lost");
                return false;
            }
        } else {
            updateBanner("No Peer Connected");
            return false;
        }
    }

    /**
     * Instantiate a new Google nearby network
     * @return
     */
    private synchronized Network getNetwork() {
        if (network == null)
            network = new Network(this.getApplicationContext(), "PingPong");
        PayloadHandler echo1 = new PayloadHandler((data) -> {
           updateBanner("Type "+data.second.getType()+": "+"Receiving Call from " + data.first + "\nEcho input " +
                           data.second.getInfo());
           try {
               network.send(Payload.newBuilder(data.second).setType("print").build());
           } catch (ConnectionNotAvailableException ex) {
               updateBanner("Connection Lost, Echo Failed");
           }
        });
        PayloadHandler echo2 = new PayloadHandler((data) -> {
            updateBanner("Type "+data.second.getType()+": "+"Receiving Call from " + data.first + "\nReverse input " +
                    data.second.getInfo());
            try {
                StringBuffer buffer = new StringBuffer(data.second.getInfo());
                Payload ret = Payload.newBuilder(data.second).setType("print").setInfo(buffer.reverse().toString()).build();
                network.send(ret);
            } catch (ConnectionNotAvailableException ex) {
                updateBanner("Connection Lost, Echo Failed");
            }
        });
        PayloadHandler echo3 = new PayloadHandler((data) -> {
            updateBanner("Type "+data.second.getType()+": "+"Receiving Call from " + data.first + "\nUppercase input " +
                    data.second.getInfo());
            try {
                Payload ret = Payload.newBuilder(data.second).setType("print").setInfo(data.second.getInfo().toUpperCase()).build();
                network.send(ret);
            } catch (ConnectionNotAvailableException ex) {
                updateBanner("Connection Lost, Echo Failed");
            }
        });
        PayloadHandler print1 = new PayloadHandler((data) -> {
            updateView(data.second);
        });
        network.registerHandler("echo", echo1);
        network.registerHandler("reverse", echo2);
        network.registerHandler("uppercase", echo3);
        network.registerHandler("print", print1);
        return network;
    }

    /**
     * Update upper banner with given text
     * @param info
     */
    private synchronized void updateBanner(String info) {
        TextView banner =  findViewById(R.id.Banner);
        runOnUiThread(() -> {
            banner.setText(info);
            banner.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).withEndAction(() -> {
                banner.animate().scaleX(1).scaleY(1).setDuration(200);
            });

        });

    }

    /**
     * Periodically check whether this device is connected to another device. If peer is lost, update the banner.
     */
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
