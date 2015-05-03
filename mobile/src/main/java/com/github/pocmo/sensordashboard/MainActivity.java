package com.github.pocmo.sensordashboard;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.*;
import android.widget.Button;
import android.widget.LinearLayout;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.PowerManager;
import android.os.PowerManager.*;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.widget.Toast;

import com.github.pocmo.sensordashboard.events.BusProvider;
import com.github.pocmo.sensordashboard.shared.TimeString;

import java.util.*;
import java.io.*;


public class MainActivity extends ActionBarActivity implements SensorEventListener, LocationListener {
    private static final String TAG = "MainActivity";

    private RemoteSensorManager remoteSensorManager;

    private TextViewBuf textStorage;

    private TextViewBuf textActivity;
    private TextViewBuf textGps;
    private TextViewBuf textAcc;
    private TextViewBuf textGyro;
    private TextViewBuf textStep;
    private TextViewBuf textMag;
    private TextViewBuf textRot;

    private TextViewBuf textWearStatus;

    private BufferedWriter loggerGps;
    private BufferedWriter loggerActivity;

    private BufferedWriter loggerAcc;
    private BufferedWriter loggerGyro;
    private BufferedWriter loggerStep;
    private BufferedWriter loggerMag;
    private BufferedWriter loggerRot;

    // log timestamp related information
    private BufferedWriter loggerInfo;

    private TimeString timeString = new TimeString();

    private int gpsGCnt = 0;
    private int gpsNCnt = 0;

    private SensorManager sensorManager;
    private LocationManager locationManager;

    private WakeLock wl;

    private BroadcastReceiver updateUIReciver;
    private IntentFilter filter;

    private SensorEventListener mSensorListener;
    private LocationListener mLocationListener;
    private Activity mThis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mThis = this;
//        Intent intent = new Intent(MainActivity.this, SensorReceiverService.class);
//        startService(intent);

        // Keep the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        remoteSensorManager = RemoteSensorManager.getInstance(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.my_awesome_toolbar);
        setSupportActionBar(toolbar);

        // calculate the different between real time and sensor timestamp
        long elapsedRealTime = SystemClock.elapsedRealtime();
        long current = System.currentTimeMillis();
        Log.i(TAG, "elapsedRealTime=" + elapsedRealTime + ", current time=" + current + ", diff=" + (current - elapsedRealTime));


        // log file
        String pathRoot = Environment.getExternalStorageDirectory() + "/wear_data/weardata_" + timeString.currentTimeForFile();
        try {
            // log time related information
            loggerInfo  = new BufferedWriter(new FileWriter(pathRoot + ".phone.info"));

            loggerGps  = new BufferedWriter(new FileWriter(pathRoot + ".phone.gps"));
            loggerActivity  = new BufferedWriter(new FileWriter(pathRoot + ".phone.activity"));

            loggerAcc  = new BufferedWriter(new FileWriter(pathRoot + ".phone.acc"));
            loggerGyro = new BufferedWriter(new FileWriter(pathRoot + ".phone.gyro"));
            loggerStep  = new BufferedWriter(new FileWriter(pathRoot + ".phone.step"));
            loggerMag = new BufferedWriter(new FileWriter(pathRoot + ".phone.baro"));
            loggerRot = new BufferedWriter(new FileWriter(pathRoot + ".phone.rot"));

            loggerInfo.write("elapsedRealTime=" + elapsedRealTime + ", current time=" + current + ", diff=" + (current - elapsedRealTime));
            loggerInfo.newLine();
            loggerInfo.flush();

        } catch (Exception e) {
            e.printStackTrace();
        }

        // Register phone sensors
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);


        // Wakelock
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wl = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SensorCollector");
        wl.acquire();

        filter = new IntentFilter();
        filter.addAction("nesl.wear.sensordata");

        updateUIReciver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int sensorType = intent.getIntExtra("t", 0);
                if (sensorType == 0) {
                    Toast.makeText(mThis, "empty sensor data received ", Toast.LENGTH_SHORT).show();
                }
                if (sensorType == -1) {
                    Toast.makeText(mThis, "wear data collection started", Toast.LENGTH_SHORT).show();
                    textWearStatus.setStr("Wear data collection started");
                }
                else if (sensorType == -2) {
                    Toast.makeText(mThis, "wear data collection stopped", Toast.LENGTH_SHORT).show();
                    textWearStatus.setStr("Wear data collection stopped");
                }
                else if (sensorType == -5) {
                    long timestamp = intent.getLongExtra("ts", 0);
                    String content = intent.getStringExtra("d");
                    updateSensorData(sensorType, content, timestamp);
                    textActivity.setStr("Device activity: " + content);
                }
            }
        };

        // UI
        LinearLayout la = (LinearLayout) findViewById(R.id.sensor_panel);
        // basic information
        TextViewBuf.createText(la, this, "File name: " + pathRoot + ".*");
        textStorage  = TextViewBuf.createText(la, this, "Storage: --");
        TextViewBuf.createText(la, this, "");

        // Google play service activity recognition
        textActivity      = TextViewBuf.createText(la, this, "Detected Activity: ");
        TextViewBuf.createText(la, this, "");

        // GPS from phone
        textGps      = TextViewBuf.createText(la, this, "GPS from gps: --  from network: --");
        TextViewBuf.createText(la, this, "");

        // sensor data from phone
        textAcc     = TextViewBuf.createText(la, this, "ACC x:------,y:------,z:------");
        textGyro    = TextViewBuf.createText(la, this, "GYRO x:------,y:------,z:------");
        textStep     = TextViewBuf.createText(la, this, "StepCount: x:--");
        textMag = TextViewBuf.createText(la, this, "MAG: --");
        textRot     = TextViewBuf.createText(la, this, "ROTATION VEC: --");
        TextViewBuf.createText(la, this, "");

        // info from wearable device
        textWearStatus = TextViewBuf.createText(la, this, "WEAR STATUS x:------,y:------,z:------");
        TextViewBuf.createText(la, this, "");

        frameUpdateHandler.sendEmptyMessage(0);
        storageCheckHandler.sendEmptyMessage(0);

        mSensorListener = this;
        mLocationListener = this;

        Button button1 = (Button) findViewById(R.id.button1);
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start data collection on wear
                remoteSensorManager.startMeasurement();

                // Start data collection on phone
                Sensor magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
                sensorManager.registerListener(mSensorListener, magSensor, SensorManager.SENSOR_DELAY_FASTEST);

                Sensor accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                sensorManager.registerListener(mSensorListener, accSensor, SensorManager.SENSOR_DELAY_FASTEST);

                Sensor gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
                sensorManager.registerListener(mSensorListener, gyroSensor, SensorManager.SENSOR_DELAY_FASTEST);

                Sensor stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
                sensorManager.registerListener(mSensorListener, stepCounter, SensorManager.SENSOR_DELAY_FASTEST);

                Sensor rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
                sensorManager.registerListener(mSensorListener, rotationVector, SensorManager.SENSOR_DELAY_FASTEST);

                // location update
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, mLocationListener);
            }
        });

        Button button2 = (Button) findViewById(R.id.button2);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Stop data collection on wear
                remoteSensorManager.stopMeasurement();

                // Stop data collection on phone
                sensorManager.unregisterListener(mSensorListener);
                locationManager.removeUpdates(mLocationListener);
            }
        });


    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_about) {

            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        BusProvider.getInstance().register(this);
        registerReceiver(updateUIReciver,filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        BusProvider.getInstance().unregister(this);

        // Unregister the boardcast receiver
        unregisterReceiver(updateUIReciver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called - closing all file logs...");
        try {
            loggerMag.close();
            loggerAcc.close();
            loggerGyro.close();
            loggerStep.close();
            loggerGps.close();
            loggerActivity.close();
            loggerRot.close();
            loggerInfo.close();

            // Release the wakelock
            wl.release();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Handler frameUpdateHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            TextViewBuf.update();
            sendEmptyMessageDelayed(0, 1000);
        }
    };

    private Handler storageCheckHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            stat.restat(Environment.getDataDirectory().getPath());
            long bytesAvailable = stat.getBlockSizeLong() * stat.getAvailableBlocksLong();
            textStorage.setStr("Remaining storage: " + String.format("%.2f GB", (double)(bytesAvailable >> 20) / (1 << 10)));
            sendEmptyMessageDelayed(0, 10 * 60 * 1000L);
        }
    };

    @Override
    public void onLocationChanged(Location location) {
        //Log.i("GPS", location.getTime() + "," + location.getLatitude() + "," + location.getLongitude() + "," + location.getAltitude() + "," + location.getProvider());
        int gpsType = (location.getProvider().equals(LocationManager.GPS_PROVIDER)) ? 0 : 1;
        if (gpsType == 0)
            gpsGCnt++;
        else
            gpsNCnt++;
        try {
            loggerGps.write(location.getTime() + "," + location.getLatitude() + "," + location.getLongitude() + "," + location.getAltitude() + "," + location.getAccuracy() + "," + location.getSpeed() + "," + gpsType);
            loggerGps.newLine();
            loggerGps.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        textGps.setStr("GPS from gps:" + gpsGCnt + "  from network:" + gpsNCnt);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    private void updateSensorData(int sensorType, String content, long timestamp) {
        try {
            switch (sensorType) {
                case Sensor.TYPE_MAGNETIC_FIELD: {
                    textMag.setStr("MAG: " + content);
                    loggerMag.write(timestamp + "," + content);
                    loggerMag.newLine();
                    loggerMag.flush();
                }
                break;
                case Sensor.TYPE_ROTATION_VECTOR: {
                    textRot.setStr("ROTATION: " + content);
                    loggerRot.write(timestamp + "," + content);
                    loggerRot.newLine();
                    loggerRot.flush();
                }
                break;
                case Sensor.TYPE_ACCELEROMETER: {
                    textAcc.setStr("ACC " + content);
                    loggerAcc.write(timestamp + "," + content);
                    loggerAcc.newLine();
                    loggerAcc.flush();
                }
                break;
                case Sensor.TYPE_GYROSCOPE: {
                    textGyro.setStr("GYRO " + content);
                    loggerGyro.write(timestamp + "," + content);
                    loggerGyro.newLine();
                    loggerGyro.flush();
                }
                break;
                case Sensor.TYPE_STEP_COUNTER: {
                    textStep.setStr("StepCounter: " + content);
                    loggerStep.write(timestamp + "," + content);
                    loggerStep.newLine();
                    loggerStep.flush();
                }
                break;
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    boolean flag = false;

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!flag && event.timestamp != 0) {
            long elapsed = SystemClock.elapsedRealtimeNanos();
            Log.i(TAG, "event timestamp=" + event.timestamp + ", elapsed=" + elapsed + ", diff=" + (event.timestamp - elapsed));
            try {
                loggerInfo.write("event timestamp=" + event.timestamp + ", elapsed=" + elapsed + ", diff=" + (event.timestamp - elapsed));
                loggerInfo.newLine();
                loggerInfo.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            flag = true;
        }
        updateSensorData(event.sensor.getType(), Arrays.toString(event.values), event.timestamp);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
