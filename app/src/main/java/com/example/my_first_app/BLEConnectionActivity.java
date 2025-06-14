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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;

public class BLEConnectionActivity extends AppCompatActivity implements BLEService.BLEConnectionListener, RobotCommunicationInterface.CommunicationListener {
    
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 2;
    
    private BLEService bleService;
    private ListView deviceListView;
    private ArrayAdapter<String> deviceAdapter;
    private ArrayList<BluetoothDevice> bluetoothDevices;
    private Button scanButton;
    private Button enableBluetoothButton;
    private Button disconnectButton;
    private Button sendButton;
    private EditText messageEditText;
    private ProgressBar progressBar;
    private TextView statusTextView;
    private TextView receivedDataTextView;
    private LinearLayout communicationContainer;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble_connection);
        
        initializeViews();
        setupBLEService();
        setupDeviceList();
        checkBluetoothPermissions();
    }
    
    private void initializeViews() {
        deviceListView = findViewById(R.id.deviceListView);
        scanButton = findViewById(R.id.scanButton);
        enableBluetoothButton = findViewById(R.id.enableBluetoothButton);
        disconnectButton = findViewById(R.id.disconnectButton);
        sendButton = findViewById(R.id.sendButton);
        messageEditText = findViewById(R.id.messageEditText);
        progressBar = findViewById(R.id.progressBar);
        statusTextView = findViewById(R.id.statusTextView);
        receivedDataTextView = findViewById(R.id.receivedDataTextView);
        communicationContainer = findViewById(R.id.communicationContainer);
        
        scanButton.setOnClickListener(v -> {
            if (bleService.isScanning()) {
                bleService.stopScan();
            } else {
                bleService.startScan();
            }
        });
        
        enableBluetoothButton.setOnClickListener(v -> enableBluetooth());
        disconnectButton.setOnClickListener(v -> disconnectDevice());
        sendButton.setOnClickListener(v -> sendMessage());
        
        progressBar.setVisibility(View.GONE);
        disconnectButton.setVisibility(View.GONE);
        sendButton.setEnabled(false);
        communicationContainer.setVisibility(View.GONE);
    }
    
    private void setupBLEService() {
        bleService = new BLEService(this);
        bleService.setConnectionListener(this);
        bleService.setCommunicationListener(this);
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
                    bleService.connectToDevice(device);
                }
            }
        });
    }
    
    private void checkBluetoothPermissions() {
        if (!bleService.isBluetoothSupported()) {
            showAlert("Lỗi", "Thiết bị không hỗ trợ Bluetooth");
            return;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 and above
            String[] permissions = {
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            };
            
            if (!bleService.hasRequiredPermissions()) {
                ActivityCompat.requestPermissions(this, permissions, REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }
        } else {
            // Below Android 12
            String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION
            };
            
            if (!bleService.hasRequiredPermissions()) {
                ActivityCompat.requestPermissions(this, permissions, REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }
        }
        
        checkBluetoothEnabled();
    }
    
    private void checkBluetoothEnabled() {
        if (!bleService.isBluetoothEnabled()) {
            statusTextView.setText("Bluetooth tắt");
            enableBluetoothButton.setVisibility(View.VISIBLE);
            scanButton.setEnabled(false);
        } else {
            statusTextView.setText("Bluetooth đã bật");
            enableBluetoothButton.setVisibility(View.GONE);
            scanButton.setEnabled(true);
        }
    }
    
    private void enableBluetooth() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || 
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }
    
    private void disconnectDevice() {
        bleService.disconnect();
    }
    
    private void sendMessage() {
        String message = messageEditText.getText().toString().trim();
        if (message.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập tin nhắn", Toast.LENGTH_SHORT).show();
            return;
        }
        
        bleService.sendData(message);
        messageEditText.setText("");
        Toast.makeText(this, "Đã gửi: " + message, Toast.LENGTH_SHORT).show();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                checkBluetoothEnabled();
            } else {
                showAlert("Bluetooth bắt buộc", "Bluetooth cần thiết để kết nối với robot");
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
                showAlert("Quyền bắt buộc", "Quyền Bluetooth cần thiết để kết nối với robot");
            }
        }
    }
    
    // RobotCommunicationInterface.CommunicationListener implementation
    @Override
    public void onDataReceived(String data) {
        runOnUiThread(() -> {
            receivedDataTextView.append("Nhận: " + data + "\n");
        });
    }
    
    @Override
    public void onDataSent(String data) {
        runOnUiThread(() -> {
            receivedDataTextView.append("Gửi: " + data + "\n");
        });
    }
    
    @Override
    public void onConnectionLost() {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            scanButton.setEnabled(true);
            disconnectButton.setVisibility(View.GONE);
            sendButton.setEnabled(false);
            communicationContainer.setVisibility(View.GONE);
            
            statusTextView.setText("Mất kết nối");
            Toast.makeText(this, "Mất kết nối", Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    public void onCommunicationError(String error) {
        runOnUiThread(() -> {
            receivedDataTextView.append("Lỗi: " + error + "\n");
            Toast.makeText(this, "Lỗi: " + error, Toast.LENGTH_SHORT).show();
        });
    }
    
    // BLEService.BLEConnectionListener implementation
    @Override
    public void onDeviceFound(BluetoothDevice device) {
        runOnUiThread(() -> {
            if (!bluetoothDevices.contains(device)) {
                bluetoothDevices.add(device);
                String deviceInfo = device.getName() != null ? device.getName() : "Thiết bị không rõ tên";
                deviceInfo += "\n" + device.getAddress();
                deviceAdapter.add(deviceInfo);
                deviceAdapter.notifyDataSetChanged();
            }
        });
    }
    
    @Override
    public void onDeviceConnected(BluetoothDevice device) {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            scanButton.setEnabled(true);
            disconnectButton.setVisibility(View.VISIBLE);
            sendButton.setEnabled(true);
            
            String deviceName = device.getName() != null ? device.getName() : "Thiết bị không rõ tên";
            statusTextView.setText("Đã kết nối: " + deviceName);
            
            Toast.makeText(this, "Đã kết nối thành công!", Toast.LENGTH_SHORT).show();
            
            // Store BLE service in ConnectionManager
            ConnectionManager.getInstance().setCommunicationService(bleService);
            
            // Navigate to Robot Control Activity
            Intent intent = new Intent(BLEConnectionActivity.this, RobotControlActivity.class);
            intent.putExtra("CONNECTION_TYPE", "BLE");
            startActivity(intent);
        });
    }
    
    @Override
    public void onDeviceDisconnected() {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            scanButton.setEnabled(true);
            disconnectButton.setVisibility(View.GONE);
            sendButton.setEnabled(false);
            communicationContainer.setVisibility(View.GONE);
            
            statusTextView.setText("Đã ngắt kết nối");
            Toast.makeText(this, "Đã ngắt kết nối", Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    public void onConnectionFailed(String error) {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            scanButton.setEnabled(true);
            disconnectButton.setVisibility(View.GONE);
            sendButton.setEnabled(false);
            communicationContainer.setVisibility(View.GONE);
            
            statusTextView.setText("Kết nối thất bại");
            showAlert("Lỗi kết nối", error);
        });
    }
    
    @Override
    public void onScanStarted() {
        runOnUiThread(() -> {
            statusTextView.setText("Đang quét thiết bị BLE...");
            progressBar.setVisibility(View.VISIBLE);
            scanButton.setText("Dừng quét");
            scanButton.setEnabled(true);
            
            // Xóa danh sách cũ
            bluetoothDevices.clear();
            deviceAdapter.clear();
            deviceAdapter.notifyDataSetChanged();
        });
    }
    
    @Override
    public void onScanStopped() {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            scanButton.setText("Quét thiết bị");
            
            if (bluetoothDevices.isEmpty()) {
                statusTextView.setText("Không tìm thấy thiết bị OhStem nào");
            } else {
                statusTextView.setText("Tìm thấy " + bluetoothDevices.size() + " thiết bị. Chọn để kết nối.");
            }
        });
    }
    
    private void showAlert(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bleService != null) {
            bleService.disconnect();
        }
    }
} 