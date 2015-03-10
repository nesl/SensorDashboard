package com.github.pocmo.sensordashboard;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.*;
import android.widget.LinearLayout;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.PowerManager;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;

import com.github.pocmo.sensordashboard.events.BusProvider;

import java.util.*;
import java.io.*;


public class MainActivity extends ActionBarActivity implements SensorEventListener, LocationListener {
    private static final String TAG = "MainActivity";

    private RemoteSensorManager remoteSensorManager;

    private TextViewBuf textStorage;

    private TextViewBuf textFileName;

    private TextViewBuf textGps;

    private TextViewBuf textAcc;
    private TextViewBuf textAccHz;
    private TextViewBuf textGyro;
    private TextViewBuf textGyroHz;
    private TextViewBuf textStep;
    private TextViewBuf textStepHz;
    private TextViewBuf textBaro;
    private TextViewBuf textBaroHz;

    private TextViewBuf textAccWear;
    private TextViewBuf textAccWearHz;
    private TextViewBuf textGyroWear;
    private TextViewBuf textGyroWearHz;
    private TextViewBuf textStepWear;
    private TextViewBuf textStepWearHz;
    private TextViewBuf textHeartRateWear;
    private TextViewBuf textHeartRateWearHz;

    private String deviceNo;

    private BufferedWriter loggerGps;

    private BufferedWriter loggerAcc;
    private BufferedWriter loggerGyro;
    private BufferedWriter loggerStep;
    private BufferedWriter loggerBaro;

    private BufferedWriter loggerAccWear;
    private BufferedWriter loggerGyroWear;
    private BufferedWriter loggerStepWear;
    private BufferedWriter loggerHeartRateWear;

    private TimeString timeString = new TimeString();
    private long startTime = 0;

    private int gpsGCnt = 0;
    private int gpsNCnt = 0;

    private int baroCnt = 0;
    private int accCnt = 0;
    private int gyroCnt = 0;
    private int stepCnt = 0;

    private int hearRateWearCnt = 0;
    private int accWearCnt = 0;
    private int gyroWearCnt = 0;
    private int stepWearCnt = 0;

    private SensorManager sensorManager;
    private Sensor barometerSensor;
    private Sensor accSensor;
    private Sensor gyroSensor;
    private Sensor stepCounter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        remoteSensorManager = RemoteSensorManager.getInstance(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.my_awesome_toolbar);
        setSupportActionBar(toolbar);

        // log file
        deviceNo = getDeviceNo();
        String pathRoot = Environment.getExternalStorageDirectory() + "/wear_data/weardata_" + deviceNo + "_" + timeString.currentTimeForFile();
        try {
            loggerGps  = new BufferedWriter(new FileWriter(pathRoot + ".phone.gps"));

            loggerAcc  = new BufferedWriter(new FileWriter(pathRoot + ".phone.acc"));
            loggerGyro = new BufferedWriter(new FileWriter(pathRoot + ".phone.gyro"));
            loggerStep  = new BufferedWriter(new FileWriter(pathRoot + ".phone.step"));
            loggerBaro = new BufferedWriter(new FileWriter(pathRoot + ".phone.baro"));

            loggerAccWear  = new BufferedWriter(new FileWriter(pathRoot + ".wear.acc"));
            loggerGyroWear = new BufferedWriter(new FileWriter(pathRoot + ".wear.gyro"));
            loggerStepWear  = new BufferedWriter(new FileWriter(pathRoot + ".wear.step"));
            loggerHeartRateWear = new BufferedWriter(new FileWriter(pathRoot + ".wear.heartrate"));

        } catch (Exception e) {
            e.printStackTrace();
        }

        // Register phone sensors
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);

        // Wakelock
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SensorCollector").acquire();


        // UI
        LinearLayout la = (LinearLayout) findViewById(R.id.sensor_panel);
        // basic information
        textFileName = TextViewBuf.createText(la, this, "File name: " + pathRoot + ".*");
        textStorage  = TextViewBuf.createText(la, this, "Storage: --");
        TextViewBuf.createText(la, this, "");

        // GPS from phone
        textGps      = TextViewBuf.createText(la, this, "GPS from gps: --  from network: --");
        TextViewBuf.createText(la, this, "");

        // sensor data from phone
        textAcc     = TextViewBuf.createText(la, this, "ACC x:------,y:------,z:------");
        textAccHz    = TextViewBuf.createText(la, this, "ACC freq: -- Hz");
        textGyro    = TextViewBuf.createText(la, this, "GYRO x:------,y:------,z:------");
        textGyroHz   = TextViewBuf.createText(la, this, "GYRO freq: -- Hz");
        textStep     = TextViewBuf.createText(la, this, "StepCount: x:--");
        textStepHz    = TextViewBuf.createText(la, this, "StepCount freq: --");
        textBaro     = TextViewBuf.createText(la, this, "BARO value: --");
        textBaroHz   = TextViewBuf.createText(la, this, "BARO freq: -- Hz");
        TextViewBuf.createText(la, this, "");

        // sensor data from wearable device
        textAccWear     = TextViewBuf.createText(la, this, "ACC_W x:------,y:------,z:------");
        textAccWearHz    = TextViewBuf.createText(la, this, "ACC_W freq: -- Hz");
        textGyroWear    = TextViewBuf.createText(la, this, "GYRO_W x:------,y:------,z:------");
        textGyroWearHz   = TextViewBuf.createText(la, this, "GYRO_W freq: -- Hz");
        textStepWear     = TextViewBuf.createText(la, this, "StepCount_W x:--");
        textStepWearHz    = TextViewBuf.createText(la, this, "StepCount_W freq: --");
        textHeartRateWear     = TextViewBuf.createText(la, this, "HeartRate_W --");
        textHeartRateWearHz   = TextViewBuf.createText(la, this, "HeartRate_W freq: -- Hz");
        TextViewBuf.createText(la, this, "");

        frameUpdateHandler.sendEmptyMessage(0);
        storageCheckHandler.sendEmptyMessage(0);
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

        // Start data collection on wear
        remoteSensorManager.startMeasurement();

        // Start data collection on phone
        barometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        sensorManager.registerListener(this, barometerSensor, SensorManager.SENSOR_DELAY_FASTEST);

        accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, accSensor, SensorManager.SENSOR_DELAY_FASTEST);

        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_FASTEST);

        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_FASTEST);

    }

    @Override
    protected void onPause() {
        super.onPause();
        BusProvider.getInstance().register(this);

        // Stop data collection on wear
        remoteSensorManager.stopMeasurement();

        // Stop data collection on phone
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called - closing all file logs...");
        //sensorManager.unregisterListener(this);
        try {
            loggerBaro.close();
            loggerAcc.close();
            loggerGyro.close();
            loggerStep.close();
            loggerGps.close();

            loggerHeartRateWear.close();
            loggerAccWear.close();
            loggerGyroWear.close();
            loggerStepWear.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getDeviceNo() {
        String fileName = Environment.getExternalStorageDirectory() + "/nesldev";
        try {
            FileInputStream fis = new FileInputStream(new File(fileName));
            Scanner scanner = new Scanner(fis);
            String firstLine = scanner.nextLine();
            firstLine.trim();
            fis.close();
            return firstLine;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "x";
    }

    private String freqStr(long curSensorTimestamp, int count) {
        long dt = curSensorTimestamp / 1000000 - startTime;
        return String.format("%.2f", ((double)count) / (dt / 1000.0)) + "  (" + count + " / " + timeString.ms2watch(dt) + ")";
    }

    private Handler frameUpdateHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            TextViewBuf.update();
            sendEmptyMessageDelayed(0, 20);
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

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (startTime == 0) {
            startTime = System.currentTimeMillis();
        }
        try {
            switch (event.sensor.getType()) {
                case Sensor.TYPE_PRESSURE: {
                    long timestamp = event.timestamp;
                    baroCnt++;
                    textBaro.setStr("BARO value: " + event.values[0]);
                    textBaroHz.setStr("BARO freq: " + freqStr(timestamp, baroCnt));
                    loggerBaro.write(timestamp + "," + event.values[0]);
                    loggerBaro.newLine();
                    loggerBaro.flush();
                }
                break;
                case Sensor.TYPE_ACCELEROMETER: {
                    long timestamp = event.timestamp;
                    accCnt++;
                    textAcc.setStr("ACC " + Arrays.toString(event.values));
                    textAccHz.setStr("ACC freq: " + freqStr(timestamp, accCnt));
                    loggerAcc.write(timestamp + "," + Arrays.toString(event.values));
                    loggerAcc.newLine();
                    loggerAcc.flush();
                }
                break;
                case Sensor.TYPE_GYROSCOPE: {
                    long timestamp = event.timestamp;
                    gyroCnt++;
                    textGyro.setStr("GYRO " + Arrays.toString(event.values));
                    textGyroHz.setStr("GYRO freq: " + freqStr(timestamp, gyroCnt));
                    loggerGyro.write(timestamp + "," + Arrays.toString(event.values));
                    loggerGyro.newLine();
                    loggerGyro.flush();
                }
                break;
                case Sensor.TYPE_STEP_COUNTER: {
                    long timestamp = event.timestamp;
                    float value = event.values[0];
                    stepCnt++;
                    textStep.setStr("StepCounter: " + event.values[0]);
                    textStepHz.setStr("StepCounter freq: " + freqStr(timestamp, stepCnt));
                    loggerStep.write(timestamp + "," + event.values[0]);
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

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
