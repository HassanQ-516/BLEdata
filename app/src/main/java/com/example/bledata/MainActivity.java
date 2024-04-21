package com.example.bledata;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private BluetoothAdapter bluetoothAdapter;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_FINE_LOCATION = 2;
    private static final long SCAN_PERIOD = 10000; // 10 seconds
    private Handler handler = new Handler();
    private boolean scanning;
    private Map<String, BluetoothDevice> devicesMap = new HashMap<>();
    private List<String> devicesList = new ArrayList<>();
    private ArrayAdapter<String> devicesAdapter;
    private ListView listView;
    private BluetoothGatt bluetoothGatt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            final BluetoothManager bluetoothManager =
                    (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = bluetoothManager.getAdapter();

            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }

            checkPermissions();  // Checking runtime permissions

            setupUI();
        } catch (Exception e) {
            Log.e("MainActivity", "Error initializing the BLE: " + e.getMessage());
            Toast.makeText(this, "Failed to initialize BLE", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupUI() {
        Button scanButton = findViewById(R.id.btnScan);
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanLeDevice(true);
            }
        });

        listView = findViewById(R.id.listView);
        devicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, devicesList);
        listView.setAdapter(devicesAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String deviceInfo = devicesList.get(position);
                BluetoothDevice device = devicesMap.get(deviceInfo);
                if (device != null) {
                    connectToDevice(device);
                }
            }
        });
    }

    private void scanLeDevice(final boolean enable) {
        if (enable && !scanning) {
            devicesMap.clear();
            devicesList.clear();
            devicesAdapter.notifyDataSetChanged();

            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    scanning = false;
                    bluetoothAdapter.stopLeScan(leScanCallback);
                    Toast.makeText(MainActivity.this, "Scanning stopped", Toast.LENGTH_SHORT).show();
                }
            }, SCAN_PERIOD);

            scanning = true;
            bluetoothAdapter.startLeScan(leScanCallback);
            Toast.makeText(this, "Scanning started", Toast.LENGTH_SHORT).show();
        } else {
            scanning = false;
            bluetoothAdapter.stopLeScan(leScanCallback);
            Toast.makeText(this, "Scanning stopped", Toast.LENGTH_SHORT).show();
        }
    }

    private BluetoothAdapter.LeScanCallback leScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!devicesMap.containsKey(device.getAddress())) {
                                String deviceInfo = device.getName() + " (" + device.getAddress() + ")";
                                devicesMap.put(deviceInfo, device);
                                devicesList.add(deviceInfo);
                                devicesAdapter.notifyDataSetChanged();
                            }
                        }
                    });
                }
            };

    private void connectToDevice(BluetoothDevice device) {
        updateConnectionStatus("Connecting to " + device.getName() + "...");
        Toast.makeText(this, "Connecting to " + device.getName(), Toast.LENGTH_SHORT).show();

        bluetoothGatt = device.connectGatt(this, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    runOnUiThread(() -> {
                        updateConnectionStatus("Connected to " + device.getName());
                        showTransmissionButtons(true);
                    });
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    runOnUiThread(() -> {
                        updateConnectionStatus("Disconnected");
                        showTransmissionButtons(false);
                    });
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    runOnUiThread(() -> updateSensorList(gatt));
                }
            }
        });
    }

    private void updateConnectionStatus(final String status) {
        TextView tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvConnectionStatus.setText(status);
    }

    private void updateSensorList(BluetoothGatt gatt) {
        List<BluetoothGattService> services = gatt.getServices();
        List<String> sensorNames = new ArrayList<>();
        for (BluetoothGattService service : services) {
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                sensorNames.add(characteristic.getUuid().toString());
            }
        }
        Spinner sensorSpinner = findViewById(R.id.sensorSpinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sensorNames);
        sensorSpinner.setAdapter(adapter);
    }

    private void showTransmissionButtons(boolean connected) {
        Button startButton = findViewById(R.id.btnStart);
        Button stopButton = findViewById(R.id.btnStop);
        startButton.setVisibility(connected ? View.VISIBLE : View.GONE);
        stopButton.setVisibility(View.GONE);  // Start with Stop button hidden
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start data transmission logic
                stopButton.setVisibility(View.VISIBLE);
                startButton.setVisibility(View.GONE);
            }
        });
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Stop data transmission logic
                stopButton.setVisibility(View.GONE);
                startButton.setVisibility(View.VISIBLE);
            }
        });
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_FINE_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_FINE_LOCATION && grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission denied by user", Toast.LENGTH_SHORT).show();
        }
    }
}
