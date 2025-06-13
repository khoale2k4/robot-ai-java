// MainActivity.java
package com.example.my_first_app; // Thay đổi package name

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.my_first_app.databinding.ActivityMainBinding; // Import ViewBinding
import android.bluetooth.BluetoothDevice;

public class MainActivity extends AppCompatActivity implements BLEService.BLEConnectionListener, BLEDeviceAdapter.OnDeviceClickListener {
    private ActivityMainBinding binding;
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    };
    
    // BLE Service
    private BLEService bleService;
    private boolean isScanning = false;
    private boolean isConnecting = false;
    
    // RecyclerView
    private BLEDeviceAdapter deviceAdapter;
    private RecyclerView devicesRecyclerView;
    private Button scanButton, stopScanButton, skipConnectionButton;
    private TextView connectionStatusText, scanStatusText, bluetoothStatusText;
    
    // Animation
    private Animation pulseAnimation, spinningArcAnimation, fadeInAnimation;
    private View scanningContainer, spinningArc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initializeViews();
        
        if (allPermissionsGranted()) {
            initializeBLE();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }
    
    private void initializeViews() {
        devicesRecyclerView = binding.devicesRecyclerView;
        scanButton = binding.scanButton;
        stopScanButton = binding.stopScanButton;
        skipConnectionButton = binding.skipConnectionButton;
        connectionStatusText = binding.connectionStatusText;
        scanStatusText = binding.scanStatusText;
        bluetoothStatusText = binding.bluetoothStatusText;
        scanningContainer = binding.scanningContainer;
        spinningArc = binding.spinningArc;
        
        // Initialize animations
        pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse_animation);
        spinningArcAnimation = AnimationUtils.loadAnimation(this, R.anim.spinning_arc);
        fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        
        // Setup RecyclerView
        deviceAdapter = new BLEDeviceAdapter();
        deviceAdapter.setOnDeviceClickListener(this);
        devicesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        devicesRecyclerView.setAdapter(deviceAdapter);
        
        // Setup scan buttons
        scanButton.setOnClickListener(v -> {
            if (isScanning) {
                stopScan();
            } else {
                startScan();
            }
        });
        stopScanButton.setOnClickListener(v -> stopScan());
        
        // Setup skip connection button
        skipConnectionButton.setOnClickListener(v -> skipConnection());
        
        // Initially setup UI state
        stopScanButton.setVisibility(View.GONE);
        scanStatusText.setText("Quét");
    }
    
    private void initializeBLE() {
        try {
            bleService = new BLEService(this);
            bleService.setConnectionListener(this);
            
            if (!bleService.isBluetoothSupported()) {
                showError("Thiết bị không hỗ trợ Bluetooth");
                return;
            }
            
            if (!bleService.isBluetoothEnabled()) {
                showError("Vui lòng bật Bluetooth");
                return;
            }
            
            if (!bleService.hasRequiredPermissions()) {
                showError("Cần cấp quyền Bluetooth");
                return;
            }
            
            connectionStatusText.setText("Nhấn vào vòng tròn để bắt đầu quét");
            bluetoothStatusText.setText("Bluetooth sẵn sàng");
            scanButton.setEnabled(true);
            scanStatusText.setText("Quét");
            
        } catch (Exception e) {
            Log.e("MainActivity", "Lỗi khởi tạo BLE service", e);
            showError("Lỗi khởi tạo Bluetooth: " + e.getMessage());
        }
    }
    
    private void startScan() {
        if (bleService == null) {
            showError("BLE service chưa sẵn sàng");
            return;
        }
        
        if (isScanning) {
            return;
        }
        
        try {
            deviceAdapter.clearDevices();
            connectionStatusText.setText("Đang quét thiết bị BLE...");
            scanStatusText.setText("Đang quét...");
            
            // Start spinning arc animation
            if (spinningArc != null && spinningArcAnimation != null) {
                spinningArc.setVisibility(View.VISIBLE);
                spinningArc.startAnimation(spinningArcAnimation);
            }
            
            bleService.startScan();
            isScanning = true;
            stopScanButton.setVisibility(View.VISIBLE);
            
        } catch (Exception e) {
            Log.e("MainActivity", "Lỗi bắt đầu quét", e);
            showError("Lỗi bắt đầu quét: " + e.getMessage());
        }
    }
    
    private void stopScan() {
        if (bleService == null || !isScanning) {
            return;
        }
        
        try {
            bleService.stopScan();
            connectionStatusText.setText("Đã dừng quét");
            scanStatusText.setText("Quét");
            
            // Stop spinning arc animation
            if (spinningArc != null) {
                spinningArc.clearAnimation();
                spinningArc.setVisibility(View.GONE);
            }
            
            isScanning = false;
            stopScanButton.setVisibility(View.GONE);
            
        } catch (Exception e) {
            Log.e("MainActivity", "Lỗi dừng quét", e);
            showError("Lỗi dừng quét: " + e.getMessage());
        }
    }
    
    private void skipConnection() {
        Intent intent = new Intent(MainActivity.this, HomeActivity.class);
        startActivity(intent);
        finish(); // Kết thúc MainActivity để không quay lại
    }
    
    private void showError(String message) {
        connectionStatusText.setText("Lỗi: " + message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
    
    // BLE Connection Callbacks
    @Override
    public void onDeviceFound(BluetoothDevice device) {
        runOnUiThread(() -> {
            try {
                if (device != null && device.getAddress() != null) {
                    deviceAdapter.addDevice(device);
                    
                    String deviceName = device.getName();
                    if (deviceName != null && deviceName.toLowerCase().contains("ohstem")) {
                        connectionStatusText.setText("Tìm thấy robot OhStem: " + deviceName);
                    }
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Lỗi thêm thiết bị vào danh sách", e);
            }
        });
    }
    
    @Override
    public void onDeviceConnected(BluetoothDevice device) {
        runOnUiThread(() -> {
            try {
                isConnecting = false;
                deviceAdapter.clearConnectingDevice();
                
                if (device == null) {
                    connectionStatusText.setText("Kết nối thành công nhưng thiết bị không xác định");
                    Toast.makeText(this, "Kết nối thành công!", Toast.LENGTH_SHORT).show();
                } else {
                    String deviceName = "Robot";
                    
                    // Safely get device name
                    if (bleService != null && bleService.hasRequiredPermissions()) {
                        try {
                            String name = device.getName();
                            if (name != null && !name.isEmpty()) {
                                deviceName = name;
                            }
                        } catch (SecurityException e) {
                            Log.w("MainActivity", "SecurityException getting device name in onDeviceConnected", e);
                        } catch (Exception e) {
                            Log.w("MainActivity", "Exception getting device name in onDeviceConnected", e);
                        }
                    }
                    
                    connectionStatusText.setText("Kết nối thành công với " + deviceName);
                    Toast.makeText(this, "Kết nối thành công!", Toast.LENGTH_SHORT).show();
                }
                
                // Store BLE service in ConnectionManager
                if (bleService != null) {
                    ConnectionManager.getInstance().setCommunicationService(bleService);
                }
                
                // Navigate to Home Activity after 1 second delay
                connectionStatusText.postDelayed(() -> {
                    try {
                        Intent intent = new Intent(MainActivity.this, HomeActivity.class);
                        startActivity(intent);
                        finish(); // Kết thúc MainActivity để không quay lại
                    } catch (Exception e) {
                        Log.e("MainActivity", "Lỗi chuyển trang", e);
                        showError("Lỗi chuyển trang: " + e.getMessage());
                    }
                }, 1000);
                
            } catch (Exception e) {
                Log.e("MainActivity", "Lỗi xử lý kết nối thành công", e);
                showError("Lỗi: " + e.getMessage());
            }
        });
    }
    
    @Override
    public void onDeviceDisconnected() {
        runOnUiThread(() -> {
            isConnecting = false;
            deviceAdapter.clearConnectingDevice();
            connectionStatusText.setText("Robot đã ngắt kết nối");
            Toast.makeText(this, "Robot đã ngắt kết nối", Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    public void onConnectionFailed(String error) {
        runOnUiThread(() -> {
            isConnecting = false;
            deviceAdapter.clearConnectingDevice();
            showError("Kết nối thất bại: " + error);
        });
    }
    
    @Override
    public void onScanStarted() {
        runOnUiThread(() -> {
            connectionStatusText.setText("Đang quét thiết bị BLE...");
            scanStatusText.setText("Đang quét...");
            isScanning = true;
            stopScanButton.setVisibility(View.VISIBLE);
        });
    }
    
    @Override
    public void onScanStopped() {
        runOnUiThread(() -> {
            isScanning = false;
            scanStatusText.setText("Quét");
            stopScanButton.setVisibility(View.GONE);
            
            // Stop spinning arc animation when scan stops
            if (spinningArc != null) {
                spinningArc.clearAnimation();
                spinningArc.setVisibility(View.GONE);
            }
            
            if (deviceAdapter.getItemCount() == 0) {
                connectionStatusText.setText("Không tìm thấy thiết bị nào");
            } else {
                connectionStatusText.setText("Tìm thấy " + deviceAdapter.getItemCount() + " thiết bị");
            }
        });
    }
    
    // Device click from adapter
    @Override
    public void onDeviceConnect(BluetoothDevice device) {
        if (device == null) {
            showError("Thiết bị không hợp lệ");
            return;
        }
        
        if (isConnecting) {
            Toast.makeText(this, "Đang kết nối với thiết bị khác...", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (bleService == null) {
            showError("BLE service không khả dụng");
            return;
        }
        
        try {
            String deviceName = "Unknown";
            String deviceAddress = device.getAddress();
            
            // Safely get device name with permission check
            if (bleService.hasRequiredPermissions()) {
                try {
                    String name = device.getName();
                    if (name != null && !name.isEmpty()) {
                        deviceName = name;
                    }
                } catch (SecurityException e) {
                    Log.w("MainActivity", "SecurityException getting device name", e);
                } catch (Exception e) {
                    Log.w("MainActivity", "Exception getting device name", e);
                }
            }
            
            connectionStatusText.setText("Đang kết nối với " + deviceName + "...");
            Toast.makeText(this, "Đang kết nối với " + deviceName, Toast.LENGTH_SHORT).show();
            
            isConnecting = true;
            deviceAdapter.setConnectingDevice(device);
            bleService.connectToDevice(device);
            
        } catch (Exception e) {
            Log.e("MainActivity", "Lỗi kết nối thiết bị", e);
            showError("Lỗi kết nối: " + e.getMessage());
            isConnecting = false;
            deviceAdapter.clearConnectingDevice();
        }
    }

    @Override
    public void onCancelConnection(BluetoothDevice device) {
        if (device == null) {
            return;
        }
        
        try {
            if (bleService != null && isConnecting) {
                bleService.disconnect();
                connectionStatusText.setText("Đã hủy kết nối");
                Toast.makeText(this, "Đã hủy kết nối", Toast.LENGTH_SHORT).show();
            }
            
            isConnecting = false;
            deviceAdapter.clearConnectingDevice();
            
        } catch (Exception e) {
            Log.e("MainActivity", "Lỗi hủy kết nối", e);
            showError("Lỗi hủy kết nối: " + e.getMessage());
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                initializeBLE();
            } else {
                showError("Cần cấp quyền để sử dụng Bluetooth");
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (bleService != null && isScanning) {
                bleService.stopScan();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Lỗi cleanup", e);
        }
    }
}