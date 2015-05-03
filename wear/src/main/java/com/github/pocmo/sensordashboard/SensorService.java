package com.github.pocmo.sensordashboard;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Vibrator;
import android.util.Log;

import com.github.pocmo.sensordashboard.shared.TimeString;

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SensorService extends Service implements SensorEventListener {
    private static final String TAG = "SensorDashboard/SensorService";

    private final static int SENS_ACCELEROMETER = Sensor.TYPE_ACCELEROMETER;
    private final static int SENS_MAGNETIC_FIELD = Sensor.TYPE_MAGNETIC_FIELD;
    // 3 = @Deprecated Orientation
    private final static int SENS_GYROSCOPE = Sensor.TYPE_GYROSCOPE;
    private final static int SENS_LIGHT = Sensor.TYPE_LIGHT;
    private final static int SENS_PRESSURE = Sensor.TYPE_PRESSURE;
    // 7 = @Deprecated Temperature
    private final static int SENS_PROXIMITY = Sensor.TYPE_PROXIMITY;
    private final static int SENS_GRAVITY = Sensor.TYPE_GRAVITY;
    private final static int SENS_LINEAR_ACCELERATION = Sensor.TYPE_LINEAR_ACCELERATION;
    private final static int SENS_ROTATION_VECTOR = Sensor.TYPE_ROTATION_VECTOR;
    private final static int SENS_HUMIDITY = Sensor.TYPE_RELATIVE_HUMIDITY;
    // TODO: there's no Android Wear devices yet with a body temperature monitor
    private final static int SENS_AMBIENT_TEMPERATURE = Sensor.TYPE_AMBIENT_TEMPERATURE;
    private final static int SENS_MAGNETIC_FIELD_UNCALIBRATED = Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED;
    private final static int SENS_GAME_ROTATION_VECTOR = Sensor.TYPE_GAME_ROTATION_VECTOR;
    private final static int SENS_GYROSCOPE_UNCALIBRATED = Sensor.TYPE_GYROSCOPE_UNCALIBRATED;
    private final static int SENS_SIGNIFICANT_MOTION = Sensor.TYPE_SIGNIFICANT_MOTION;
    private final static int SENS_STEP_DETECTOR = Sensor.TYPE_STEP_DETECTOR;
    private final static int SENS_STEP_COUNTER = Sensor.TYPE_STEP_COUNTER;
    private final static int SENS_GEOMAGNETIC = Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR;
    private final static int SENS_HEARTRATE = Sensor.TYPE_HEART_RATE;

    SensorManager mSensorManager;

    private Sensor mHeartrateSensor;

    private DeviceClient client;

    private PowerManager.WakeLock wl;

    private BufferedWriter outputAcc, outputGyr, outputStep, outputHR, outputMag, outputRot;

    private TimeString timeString = new TimeString();

    private Vibrator v;

    @Override
    public void onCreate() {
        super.onCreate();

        client = DeviceClient.getInstance(this);

        Notification.Builder builder = new Notification.Builder(this);
        builder.setContentTitle("Sensor Dashboard");
        builder.setContentText("Collecting sensor data..");
        builder.setSmallIcon(R.drawable.ic_launcher);

        v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        startForeground(1, builder.build());

        startMeasurement();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopMeasurement();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    protected void startMeasurement() {
        Log.d(TAG, "start measurement in wear: SensorService");

        v.vibrate(1000);

        client.sendSensorData(-1, 1, 111, new float[]{1.0f});

        String prefix = "/storage/sdcard0/sensor_data/weardata_" + timeString.currentTimeForFile();

        try {
            outputAcc = new BufferedWriter(new FileWriter(prefix + ".wear.acc"));
            outputGyr = new BufferedWriter(new FileWriter(prefix + ".wear.gyro"));
            outputStep = new BufferedWriter(new FileWriter(prefix + ".wear.step"));
            outputHR = new BufferedWriter(new FileWriter(prefix + ".wear.heartrate"));
            outputMag = new BufferedWriter(new FileWriter(prefix + ".wear.mag"));
            outputRot = new BufferedWriter(new FileWriter(prefix + ".wear.rot"));
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Wakelock
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wl = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SensorCollector");
        wl.acquire();

        mSensorManager = ((SensorManager) getSystemService(SENSOR_SERVICE));

        Sensor accelerometerSensor = mSensorManager.getDefaultSensor(SENS_ACCELEROMETER);
        Sensor gyroscopeSensor = mSensorManager.getDefaultSensor(SENS_GYROSCOPE);
        mHeartrateSensor = mSensorManager.getDefaultSensor(SENS_HEARTRATE);
        Sensor magneticFieldSensor = mSensorManager.getDefaultSensor(SENS_MAGNETIC_FIELD);
        Sensor stepCounterSensor = mSensorManager.getDefaultSensor(SENS_STEP_COUNTER);
        Sensor rotationVectorSensor = mSensorManager.getDefaultSensor(SENS_ROTATION_VECTOR);
//        Sensor ambientTemperatureSensor = mSensorManager.getDefaultSensor(SENS_AMBIENT_TEMPERATURE);
//        Sensor gameRotationVectorSensor = mSensorManager.getDefaultSensor(SENS_GAME_ROTATION_VECTOR);
//        Sensor geomagneticSensor = mSensorManager.getDefaultSensor(SENS_GEOMAGNETIC);
//        Sensor gravitySensor = mSensorManager.getDefaultSensor(SENS_GRAVITY);
//        Sensor gyroscopeUncalibratedSensor = mSensorManager.getDefaultSensor(SENS_GYROSCOPE_UNCALIBRATED);
//        Sensor heartrateSamsungSensor = mSensorManager.getDefaultSensor(65562);
//        Sensor lightSensor = mSensorManager.getDefaultSensor(SENS_LIGHT);
//        Sensor linearAccelerationSensor = mSensorManager.getDefaultSensor(SENS_LINEAR_ACCELERATION);
//        Sensor magneticFieldUncalibratedSensor = mSensorManager.getDefaultSensor(SENS_MAGNETIC_FIELD_UNCALIBRATED);
//        Sensor pressureSensor = mSensorManager.getDefaultSensor(SENS_PRESSURE);
//        Sensor proximitySensor = mSensorManager.getDefaultSensor(SENS_PROXIMITY);
//        Sensor humiditySensor = mSensorManager.getDefaultSensor(SENS_HUMIDITY);
//        Sensor significantMotionSensor = mSensorManager.getDefaultSensor(SENS_SIGNIFICANT_MOTION);
//        Sensor stepDetectorSensor = mSensorManager.getDefaultSensor(SENS_STEP_DETECTOR);


        // Register the listener
        if (mSensorManager != null) {
            if (accelerometerSensor != null) {
                mSensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_FASTEST);
            } else {
                Log.w(TAG, "No Accelerometer found");
            }
            if (gyroscopeSensor != null) {
                mSensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_FASTEST);
            } else {
                Log.w(TAG, "No Gyroscope Sensor found");
            }
            if (mHeartrateSensor != null) {
                final int measurementDuration   = 10;   // Seconds
                final int measurementBreak      = 5;    // Seconds

                ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
                scheduler.scheduleAtFixedRate(
                        new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "register Heartrate Sensor");
                                mSensorManager.registerListener(SensorService.this, mHeartrateSensor, SensorManager.SENSOR_DELAY_FASTEST);

                                try {
                                    Thread.sleep(measurementDuration * 1000);
                                } catch (InterruptedException e) {
                                    Log.e(TAG, "Interrupted while waitting to unregister Heartrate Sensor");
                                }

                                Log.d(TAG, "unregister Heartrate Sensor");
                                mSensorManager.unregisterListener(SensorService.this, mHeartrateSensor);
                            }
                        }, 3, measurementDuration + measurementBreak, TimeUnit.SECONDS);
            } else {
                Log.d(TAG, "No Heartrate Sensor found");
            }
            if (magneticFieldSensor != null) {
                mSensorManager.registerListener(this, magneticFieldSensor, SensorManager.SENSOR_DELAY_FASTEST);
            } else {
                Log.d(TAG, "No Magnetic Field Sensor found");
            }
            if (rotationVectorSensor != null) {
                mSensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_FASTEST);
            } else {
                Log.d(TAG, "No Rotation Vector Sensor found");
            }
            if (stepCounterSensor != null) {
                mSensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_FASTEST);
            } else {
                Log.d(TAG, "No Step Counter Sensor found");
            }
        }
    }

    private void stopMeasurement() {
        client.sendSensorData(-2, 2, 2, new float[]{2.0f});

        v.vibrate(200);

        mSensorManager.unregisterListener(this);
        mSensorManager = null;

        try {
            outputAcc.flush();
            outputAcc.close();

            outputGyr.flush();
            outputGyr.close();

            outputStep.flush();
            outputStep.close();

            outputHR.flush();
            outputHR.close();

            outputMag.flush();
            outputMag.close();

            outputRot.flush();
            outputRot.close();
        } catch (IOException e) {
            e.printStackTrace();
        }


        wl.release();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // client.sendSensorData(event.sensor.getType(), event.accuracy, event.timestamp, event.values);
        int type = event.sensor.getType();
        long timestamp = event.timestamp;
        try {
            if (type == SENS_ACCELEROMETER) {
                outputAcc.write(timestamp + "," + event.values[0] + "," + event.values[1] + "," + event.values[2] + "\n");
                outputAcc.flush();
            }
            else if (type == SENS_GYROSCOPE) {
                outputGyr.write(timestamp + "," + event.values[0] + "," + event.values[1] + "," + event.values[2] + "\n");
                outputGyr.flush();
            }
            else if (type == SENS_MAGNETIC_FIELD) {
                outputMag.write(timestamp + "," + event.values[0] + "," + event.values[1] + "," + event.values[2] + "\n");
                outputMag.flush();
            }
            else if (type == SENS_ROTATION_VECTOR) {
                outputRot.write(timestamp + "," + event.values[0] + "," + event.values[1] + "," + event.values[2] + "," + event.values[3] + "," + event.values[4] + "\n");
                outputRot.flush();
            }
            else if (type == SENS_STEP_COUNTER) {
                outputStep.write(timestamp + "," + event.values[0] + "\n");
                outputStep.flush();
            }
            else if (type == SENS_HEARTRATE) {
                outputHR.write(timestamp + "," + event.values[0] + "\n");
                outputHR.flush();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
