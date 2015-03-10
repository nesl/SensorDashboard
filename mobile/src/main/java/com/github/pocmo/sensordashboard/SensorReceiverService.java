package com.github.pocmo.sensordashboard;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.github.pocmo.sensordashboard.shared.DataMapKeys;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.Arrays;

public class SensorReceiverService extends WearableListenerService {
    private static final String TAG = "SensorDashboard/SensorReceiverService";

    private RemoteSensorManager sensorManager;

    @Override
    public void onCreate() {
        super.onCreate();

        sensorManager = RemoteSensorManager.getInstance(this);
    }

    @Override
    public void onPeerConnected(Node peer) {
        super.onPeerConnected(peer);

        Log.i(TAG, "Connected: " + peer.getDisplayName() + " (" + peer.getId() + ")");
    }

    @Override
    public void onPeerDisconnected(Node peer) {
        super.onPeerDisconnected(peer);

        Log.i(TAG, "Disconnected: " + peer.getDisplayName() + " (" + peer.getId() + ")");
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.d(TAG, "onDataChanged()");

        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                DataItem dataItem = dataEvent.getDataItem();
                Uri uri = dataItem.getUri();
                String path = uri.getPath();

                if (path.startsWith("/sensors/")) {
                    unpackSensorData(
                        Integer.parseInt(uri.getLastPathSegment()),
                        DataMapItem.fromDataItem(dataItem).getDataMap()
                    );
                }
            }
        }
    }

    private void unpackSensorData(int sensorType, DataMap dataMap) {
        int accuracy = dataMap.getInt(DataMapKeys.ACCURACY);
        long timestamp = dataMap.getLong(DataMapKeys.TIMESTAMP);
        float[] values = dataMap.getFloatArray(DataMapKeys.VALUES);

        Log.d(TAG, "Received sensor data " + sensorType + " = " + Arrays.toString(values));

        if (sensorType == 0) {
            Toast.makeText(this, "empty sensor data received", Toast.LENGTH_SHORT).show();;
        }
        else {
            Intent intent = new Intent();
            intent.setAction("nesl.wear.sensordata");
            intent.putExtra("t", sensorType);
            intent.putExtra("ts", timestamp);
            intent.putExtra("d",Arrays.toString(values));
            this.sendBroadcast(intent);
        }


        sensorManager.addSensorData(sensorType, accuracy, timestamp, values);
    }
}
