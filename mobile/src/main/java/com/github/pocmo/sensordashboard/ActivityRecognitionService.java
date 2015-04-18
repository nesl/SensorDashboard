package com.github.pocmo.sensordashboard;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.util.Arrays;

/**
 * Created by cgshen on 3/10/15.
 */
public class ActivityRecognitionService extends IntentService {

    public ActivityRecognitionService() {
        super("ActivityRecognitionService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        ActivityRecognitionResult res = ActivityRecognitionResult.extractResult(intent);
        DetectedActivity det = res.getMostProbableActivity();
        String content = det.getType() + "," + det.getConfidence() + "," + res.getElapsedRealtimeMillis();
        Log.d("ActivityRecognitionService", "new activity detected: " + content);
        Intent b = new Intent();
        b.setAction("nesl.wear.sensordata");
        b.putExtra("t", -5);
        b.putExtra("ts", res.getTime());
        b.putExtra("d", content);
        this.sendBroadcast(b);
    }
}
