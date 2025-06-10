package com.example.my_first_app;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class BluetoothConnectionActivity extends AppCompatActivity implements BluetoothService.BluetoothConnectionListener {
    
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 2;
    
    private BluetoothService bluetoothService;
    private ListView deviceListView;
    private ArrayAdapter<String> deviceAdapter;
    private List<BluetoothDevice> bluetoothDevices;
    private Button scanButton;
    private Button enableBluetoothButton;
    private ProgressBar progressBar;
    private TextView statusTextView;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_connection);
        
        initializeViews();
        setupBluetoothService();
        setupDeviceList();
        checkBluetoothPermissions();
    }
    
    private void initializeViews() {
        deviceListView = findViewById(R.id.deviceListView);
        scanButton = findViewById(R.id.scanButton);
        enableBluetoothButton = findViewById(R.id.enableBluetoothButton);
        progressBar = findViewById(R.id.progressBar);
        statusTextView = findViewById(R.id.statusTextView);
        
        scanButton.setOnClickListener(v -> scanForDevices());
        enableBluetoothButton.setOnClickListener(v -> enableBluetooth());
        
        progressBar.setVisibility(View.GONE);
    }
    
    private void setupBluetoothService() {
        bluetoothService = new BluetoothService(this);
        bluetoothService.setConnectionListener(this);
    }
    
    private void setupDeviceList() {
        bluetoothDevices = new ArrayList<>();
        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        deviceListView.setAdapter(deviceAdapter);
        
        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < bluetoothDevices.size()) {
                    BluetoothDevice device = bluetoothDevices.get(position);
                    connectToDevice(device);
                }
            }
        });
    }
    
    private void checkBluetoothPermissions() {
        if (!bluetoothService.isBluetoothSupported()) {
            showAlert("Error", "Bluetooth is not supported on this device");
            return;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 and above
            String[] permissions = {
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            };
            
            boolean hasPermissions = true;
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    hasPermissions = false;
                    break;
                }
            }
            
            if (!hasPermissions) {
                ActivityCompat.requestPermissions(this, permissions, REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }
        } else {
            // Below Android 12
            String[] permissions = {
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            };
            
            boolean hasPermissions = true;
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    hasPermissions = false;
                    break;
                }
            }
            
            if (!hasPermissions) {
                ActivityCompat.requestPermissions(this, permissions, REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }
        }
        
        checkBluetoothEnabled();
    }
    
    private void checkBluetoothEnabled() {
        if (!bluetoothService.isBluetoothEnabled()) {
            statusTextView.setText("Bluetooth is disabled");
            enableBluetoothButton.setVisibility(View.VISIBLE);
            scanButton.setEnabled(false);
        } else {
            statusTextView.setText("Bluetooth is enabled");
            enableBluetoothButton.setVisibility(View.GONE);
            scanButton.setEnabled(true);
            loadPairedDevices();
        }
    }
    
    private void enableBluetooth() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }
    
    private void scanForDevices() {
        statusTextView.setText("Scanning for devices...");
        progressBar.setVisibility(View.VISIBLE);
        scanButton.setEnabled(false);
        
        loadPairedDevices();
        
        // Reset UI after simulated scan
        progressBar.postDelayed(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.GONE);
                scanButton.setEnabled(true);
                statusTextView.setText("Scan complete. Tap a device to connect.");
            }
        }, 2000);
    }
    
    private void loadPairedDevices() {
        bluetoothDevices.clear();
        deviceAdapter.clear();
        
        List<BluetoothDevice> pairedDevices = bluetoothService.getPairedDevices();
        for (BluetoothDevice device : pairedDevices) {
            bluetoothDevices.add(device);
            String deviceInfo = device.getName() != null ? device.getName() : "Unknown Device";
            deviceInfo += "\n" + device.getAddress();
            deviceAdapter.add(deviceInfo);
        }
        
        deviceAdapter.notifyDataSetChanged();
        
        if (pairedDevices.isEmpty()) {
            statusTextView.setText("No paired devices found. Please pair your robot first.");
        }
    }
    
    private void connectToDevice(BluetoothDevice device) {
        statusTextView.setText("Connecting to " + (device.getName() != null ? device.getName() : "Unknown Device"));
        progressBar.setVisibility(View.VISIBLE);
        scanButton.setEnabled(false);
        
        bluetoothService.connectToDevice(device);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                checkBluetoothEnabled();
            } else {
                showAlert("Bluetooth Required", "Bluetooth is required to connect to the robot");
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            
            if (allPermissionsGranted) {
                checkBluetoothEnabled();
            } else {
                showAlert("Permissions Required", "Bluetooth permissions are required to connect to the robot");
            }
        }
    }
    
    // BluetoothService.BluetoothConnectionListener implementation
    @Override
    public void onDeviceConnected(BluetoothDevice device) {
        progressBar.setVisibility(View.GONE);
        scanButton.setEnabled(true);
        
        showAlert("Connected", "Successfully connected to " + (device.getName() != null ? device.getName() : "Unknown Device"), 
            new Runnable() {
                @Override
                public void run() {
                    // Go to robot control activity
                    Intent intent = new Intent(BluetoothConnectionActivity.this, RobotControlActivity.class);
                    startActivity(intent);
                }
            });
    }
    
    @Override
    public void onDeviceDisconnected() {
        progressBar.setVisibility(View.GONE);
        scanButton.setEnabled(true);
        statusTextView.setText("Device disconnected");
        Toast.makeText(this, "Device disconnected", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onConnectionFailed(String error) {
        progressBar.setVisibility(View.GONE);
        scanButton.setEnabled(true);
        statusTextView.setText("Connection failed");
        showAlert("Connection Failed", error);
    }
    
    @Override
    public void onDataReceived(String data) {
        // Handle incoming data if needed
    }
    
    private void showAlert(String title, String message) {
        showAlert(title, message, null);
    }
    
    private void showAlert(String title, String message, Runnable onOkClick) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", (dialog, which) -> {
                if (onOkClick != null) {
                    onOkClick.run();
                }
            })
            .show();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothService != null) {
            bluetoothService.disconnect();
        }
    }
} 