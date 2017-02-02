package com.vistawearable.vistaapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.prefs.PreferenceChangeEvent;


public class MyActivity extends ActionBarActivity {
    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;
    private static final long SCAN_PERIOD = 10000;

    private BluetoothGattCharacteristic enableCh;
    private BluetoothGattCharacteristic rawCh;
    private BluetoothGattCharacteristic filteredCh;
    private BluetoothGattCharacteristic clampCh;

    private ArrayList<BluetoothGatt> btgatts = new ArrayList<BluetoothGatt>();
    private ArrayList<BluetoothDevice> btdevices = new ArrayList<BluetoothDevice>();

    private BluetoothGattCharacteristic startCh;
    private BluetoothGattCharacteristic effectCh;
    private BluetoothGattCharacteristic timerCh;

    boolean effectOn = false;
    boolean timerOn = false;
    int pre_char_int_value;
    int char_int_value;
    int preLevel;
    int level;
    boolean conditioned = false;

    Queue<BluetoothGattCharacteristic> qe = new LinkedList<BluetoothGattCharacteristic>();
    String TAG = "report: ";

    public void processCharacteristic(BluetoothGattCharacteristic c) {
        if (qe.isEmpty()) {
            qe.add(c);
            btgatts.get(0).writeCharacteristic(c);
        } else {
            qe.add(c);
        }
    }

    public void processQ() {
        Iterator it = qe.iterator();

        if (it.hasNext()) {
            BluetoothGattCharacteristic c = (BluetoothGattCharacteristic) it.next();
            btgatts.get(0).writeCharacteristic(c);
        }
    }

    //TextView sensorView  = (TextView) findViewById(R.id.sensor_reading);
    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            BluetoothGatt gatt = device.connectGatt(MyActivity.this, false, mGattCallback);
                            btdevices.add(device);
                            btgatts.add(gatt);
                            Toast.makeText(MyActivity.this, "device: " + device.getName(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            };


    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("gattCallback", "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i("gattCallback", "Attempting to start service discovery:" +
                        gatt.discoverServices());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("gattCallback", "Disconnected from GATT server.");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.w("gattCallback", "servicesDiscovered");

                BluetoothGattService svc = gatt.getService(VistaGattAttributes.UUID_SENSOR_SERVICE);
                if (svc == null) {
                    Log.d("onSD", "Sensor Service not found!");
                    return;
                }

                filteredCh = svc.getCharacteristic(VistaGattAttributes.UUID_FILTERED_SENSOR_READING_CHARACTERISTIC);
                if (filteredCh == null) {
                    Log.d("onSD", "filtered reading characteristic not found!");
                    return;
                }

                /*svc = gatt.getService(VistaGattAttributes.UUID_VIBRATE_SERVICE);
                if(svc == null){
                    Log.d("onSD", "vibration service not found!");
                    return;
                }*/
                gatt.readCharacteristic(filteredCh);

                //Enable Notification for filteredCh
                gatt.setCharacteristicNotification(filteredCh, true);
                UUID uuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
                BluetoothGattDescriptor descriptor = filteredCh.getDescriptor(uuid);
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);

                //Register for "Mode" (I am not sure what exactly this Mode does yet")
                //svc = gatt.getService(VistaGattAttributes.UUID_VISTA_SERVICE);
                //BluetoothGattCharacteristic mode = svc.getCharacteristic(VistaGattAttributes.UUID_MODE_CHARACTERISTIC);
                //mode.setValue(2,BluetoothGattCharacteristic.FORMAT_UINT8,0);
                //gatt.writeCharacteristic(mode);

            } else {
                Log.w("gattCallback", "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.w("gattCallback", "onCharacteristicRead");

                pre_char_int_value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
                Log.i("onCharacteristicRead", "Value: " + Integer.toString(pre_char_int_value));

                if (pre_char_int_value >= 0 && pre_char_int_value <= 300) {
                    preLevel = 1;
                } else if (pre_char_int_value >= 301 && pre_char_int_value <= 600) {
                    preLevel = 2;
                } else if (pre_char_int_value >= 601 && pre_char_int_value <= 900) {
                    preLevel = 3;
                } else if (pre_char_int_value >= 901 && pre_char_int_value <= 1200) {
                    preLevel = 4;
                } else if (pre_char_int_value >= 1201 && pre_char_int_value <= 1500) {
                    preLevel = 5;
                } else if (pre_char_int_value >= 1500 && pre_char_int_value <= 1800) {
                    preLevel = 6;
                } else {
                    preLevel = 7;
                }
            }
        }


        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            //Log.w("gattCallback", "onCharacteristicChanged");
            char_int_value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);

            //Log.i("onCharacteristicChanged", "Value: " + Integer.toString(char_int_value));

            if (char_int_value >= 0 && char_int_value <= 300) {
                level = 1;
            } else if (char_int_value >= 301 && char_int_value <= 600) {
                level = 2;
            } else if (char_int_value >= 601 && char_int_value <= 900) {
                level = 3;
            } else if (char_int_value >= 901 && char_int_value <= 1200) {
                level = 4;
            } else if (char_int_value >= 1201 && char_int_value <= 1500) {
                level = 5;
            } else if (char_int_value >= 1501 && char_int_value <= 1800) {
                level = 6;
            } else {
                level = 7;
            }

            if (conditioned == true && preLevel != level) {
                //Log.w(TAG, "ConditionChanged");
                conditionChanged();
                preLevel = level;
            }

        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //Log.w(TAG, "onCharacteristicWrite");
                qe.remove(characteristic);
                processQ();
            } else {
                //Log.w(TAG, "onCharacteristicWrite: ERROR");
            }
        }
    };

    // called on scan button press
    public void scanDevices(View view) {
        final Button b = (Button) view;
        b.setText("Scanning!");

        // Stops scanning after a pre-defined scan period.
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
                Button button = (Button) findViewById(R.id.scan_button);
                button.setText(R.string.scan_button);
            }
        }, SCAN_PERIOD);

        mBluetoothAdapter.startLeScan(mLeScanCallback);
    }


    public void stopVibration(View view) {
        Button start = (Button) view;
        start.setText("Starting");
        Log.d("in stopVibration", "stop!");
        conditioned = false;
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Button button = (Button) findViewById(R.id.stop_vibration);
                button.setText("Stop Vibration");

                BluetoothGattService svc = btgatts.get(0).getService(VistaGattAttributes.UUID_VIBRATE_SERVICE);
                if (svc == null) {
                    Log.d("in stopVibration", "Vibration Service not found!");
                    return;
                }
                if (effectOn == false) {
                    effectCh = svc.getCharacteristic(VistaGattAttributes.UUID_EFFECT_CHARACTERISTIC);
                    effectOn = true;
                }

                effectCh.setValue(1, BluetoothGattCharacteristic.FORMAT_UINT8, 0);


                if (timerOn == false) {
                    timerCh = svc.getCharacteristic(VistaGattAttributes.UUID_TIMER_CHARACTERISTIC);
                    timerOn = true;
                }
                timerCh.setValue(0, BluetoothGattCharacteristic.FORMAT_UINT16, 0);

                startCh = svc.getCharacteristic(VistaGattAttributes.UUID_START_CHARACTERISTIC);

                byte[] bbuf = new byte[1];
                bbuf[0] = (byte) (1);
                startCh.setValue(bbuf);

                //register for the mode
                svc = btgatts.get(0).getService(VistaGattAttributes.UUID_VISTA_SERVICE);
                BluetoothGattCharacteristic mode = svc.getCharacteristic(VistaGattAttributes.UUID_MODE_CHARACTERISTIC);
                mode.setValue(2, BluetoothGattCharacteristic.FORMAT_UINT8, 0);

                processCharacteristic(mode);
                processCharacteristic(effectCh);
                processCharacteristic(timerCh);

                processCharacteristic(startCh);

            }
        }, 1000);
    }

    public void startVibration(View view) {
        Button start = (Button) view;
        start.setText("Starting");
        Log.d("in startVibration", "top!");
        conditioned = false;
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Button button = (Button) findViewById(R.id.start_vibration);
                button.setText("Start Vibration");
                BluetoothGattService svc = btgatts.get(0).getService(VistaGattAttributes.UUID_VIBRATE_SERVICE);

                if (svc == null) {
                    Log.d("in stopVibration", "Vibration Service not found!");
                    return;
                }

                effectCh = svc.getCharacteristic(VistaGattAttributes.UUID_EFFECT_CHARACTERISTIC);
                effectCh.setValue(1, BluetoothGattCharacteristic.FORMAT_UINT8, 0);


                timerCh = svc.getCharacteristic(VistaGattAttributes.UUID_TIMER_CHARACTERISTIC);
                timerCh.setValue(3000, BluetoothGattCharacteristic.FORMAT_UINT16, 0);

                startCh = svc.getCharacteristic(VistaGattAttributes.UUID_START_CHARACTERISTIC);

                byte[] bbuf = new byte[1];
                bbuf[0] = (byte) (1);
                startCh.setValue(bbuf);

                //register for the mode
                svc = btgatts.get(0).getService(VistaGattAttributes.UUID_VISTA_SERVICE);
                BluetoothGattCharacteristic mode = svc.getCharacteristic(VistaGattAttributes.UUID_MODE_CHARACTERISTIC);
                mode.setValue(2, BluetoothGattCharacteristic.FORMAT_UINT8, 0);

                processCharacteristic(mode);
                processCharacteristic(effectCh);
                processCharacteristic(timerCh);

                processCharacteristic(startCh);
            }
        }, 1000);
    }

    public void startCondition(View view) {
        Log.w(TAG, "in startCondition");
        Button start = (Button) view;
        start.setText("Starting");
        conditioned = true;

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Button button = (Button) findViewById(R.id.start_condition);
                button.setText("Start Condition");


                BluetoothGattService svc = btgatts.get(0).getService(VistaGattAttributes.UUID_VIBRATE_SERVICE);
                if (svc == null) {
                    Log.d("in conditionChanged", "Vibration Service not found!");
                    return;
                }

                int p2;

                if (level == 1) {
                    p2 = 10000;
                } else if (level == 2) {
                    p2 = 15265;
                } else if (level == 3) {
                    p2 = 20265;
                } else if (level == 4) {
                    p2 = 31250;
                } else if (level == 5) {
                    p2 = 40000;
                } else if (level == 6) {
                    p2 = 50000;
                } else {
                    p2 = 0;
                }

                Log.d("conditionChanged: p2 = ", Integer.toString(p2));

                effectCh = svc.getCharacteristic(VistaGattAttributes.UUID_EFFECT_CHARACTERISTIC);
                effectCh.setValue(1, BluetoothGattCharacteristic.FORMAT_UINT8, 0);

                timerCh = svc.getCharacteristic(VistaGattAttributes.UUID_TIMER_CHARACTERISTIC);
                timerCh.setValue(p2, BluetoothGattCharacteristic.FORMAT_UINT16, 0);

                startCh = svc.getCharacteristic(VistaGattAttributes.UUID_START_CHARACTERISTIC);

                byte[] bbuf = new byte[1];
                bbuf[0] = (byte) (1);
                startCh.setValue(bbuf);

                //register for the mode
                svc = btgatts.get(0).getService(VistaGattAttributes.UUID_VISTA_SERVICE);
                BluetoothGattCharacteristic mode = svc.getCharacteristic(VistaGattAttributes.UUID_MODE_CHARACTERISTIC);
                mode.setValue(2,BluetoothGattCharacteristic.FORMAT_UINT8,0);

                processCharacteristic(mode);

                processCharacteristic(effectCh);
                processCharacteristic(timerCh);

                processCharacteristic(startCh);
            }
        }, 1000);
    }

    public void conditionChanged() {

        BluetoothGattService svc = btgatts.get(0).getService(VistaGattAttributes.UUID_VIBRATE_SERVICE);
        if (svc == null) {
            Log.d("in conditionChanged", "Vibration Service not found!");
            return;
        }

        int p2;

        if (level == 1) {
            p2 = 10000;
        } else if (level == 2) {
            p2 = 15265;
        } else if (level == 3) {
            p2 = 20265;
        } else if (level == 4) {
            p2 = 31250;
        } else if (level == 5) {
            p2 = 40000;
        } else if (level == 6) {
            p2 = 50000;
        } else {
            p2 = 0;
        }

        Log.d("conditionChanged: p2 = ", Integer.toString(p2));

        effectCh = svc.getCharacteristic(VistaGattAttributes.UUID_EFFECT_CHARACTERISTIC);
        effectCh.setValue(1, BluetoothGattCharacteristic.FORMAT_UINT8, 0);

        timerCh = svc.getCharacteristic(VistaGattAttributes.UUID_TIMER_CHARACTERISTIC);
        timerCh.setValue(p2, BluetoothGattCharacteristic.FORMAT_UINT16, 0);

        startCh = svc.getCharacteristic(VistaGattAttributes.UUID_START_CHARACTERISTIC);

        byte[] bbuf = new byte[1];
        bbuf[0] = (byte) (1);
        startCh.setValue(bbuf);

        processCharacteristic(effectCh);
        processCharacteristic(timerCh);

        processCharacteristic(startCh);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);

        mHandler = new Handler();

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Toast.makeText(this, "success", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_my, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
