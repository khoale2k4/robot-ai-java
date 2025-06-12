package com.example.my_first_app;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BLEService implements RobotCommunicationInterface {
    private static final String TAG = "BLEService";

    // UUIDs cho Dịch vụ UART
    private static final UUID UART_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    // Characteristic để gửi dữ liệu từ App -> Thiết bị
    private static final UUID TX_CHARACTERISTIC_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    // Characteristic để nhận dữ liệu từ Thiết bị -> App
    private static final UUID RX_CHARACTERISTIC_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    // UUID cho Client Characteristic Configuration Descriptor
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final long SCAN_PERIOD = 10000; // 10 giây

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic txCharacteristic;
    private BluetoothGattCharacteristic rxCharacteristic;
    
    private Context context;
    private Handler mainHandler;
    private boolean scanning = false;
    private boolean isConnected = false;
    
    private BLEConnectionListener connectionListener;
    private CommunicationListener communicationListener;
    private List<BluetoothDevice> discoveredDevices = new ArrayList<>();

    public interface BLEConnectionListener {
        void onDeviceFound(BluetoothDevice device);
        void onDeviceConnected(BluetoothDevice device);
        void onDeviceDisconnected();
        void onConnectionFailed(String error);
        void onScanStarted();
        void onScanStopped();
    }

    public BLEService(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        initializeBluetooth();
    }

    private void initializeBluetooth() {
        final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }
    }

    public void setConnectionListener(BLEConnectionListener listener) {
        this.connectionListener = listener;
    }

    @Override
    public void setCommunicationListener(CommunicationListener listener) {
        this.communicationListener = listener;
    }

    public boolean isBluetoothSupported() {
        return bluetoothAdapter != null;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    public void startScan() {
        if (!hasRequiredPermissions()) {
            if (connectionListener != null) {
                connectionListener.onConnectionFailed("Không có quyền quét BLE");
            }
            return;
        }

        if (scanning) return;

        discoveredDevices.clear();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        mainHandler.postDelayed(() -> {
            if (scanning) stopScan();
        }, SCAN_PERIOD);

        scanning = true;
        if (connectionListener != null) {
            connectionListener.onScanStarted();
        }

        bluetoothLeScanner.startScan(null, settings, leScanCallback);
        Log.d(TAG, "Bắt đầu quét BLE devices");
    }

    public void stopScan() {
        if (!scanning || bluetoothLeScanner == null) return;
        
        if (!hasRequiredPermissions()) return;

        scanning = false;
        bluetoothLeScanner.stopScan(leScanCallback);
        
        if (connectionListener != null) {
            connectionListener.onScanStopped();
        }
        
        Log.d(TAG, "Dừng quét BLE");
    }

    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            if (device == null) return;

            Log.d(TAG, "Phát hiện thiết bị: " + device.getAddress());

            String deviceName = null;
            if (hasRequiredPermissions()) {
                deviceName = device.getName();
            }

            if (deviceName != null && deviceName.toLowerCase().contains("ohstem")) {
                if (!discoveredDevices.contains(device)) {
                    discoveredDevices.add(device);
                    if (connectionListener != null) {
                        connectionListener.onDeviceFound(device);
                    }
                    Log.d(TAG, "Thêm thiết bị OhStem: " + deviceName);
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "Lỗi quét BLE: " + errorCode);
            if (connectionListener != null) {
                connectionListener.onConnectionFailed("Lỗi quét BLE: " + errorCode);
            }
        }
    };

    public void connectToDevice(BluetoothDevice device) {
        stopScan();
        
        if (!hasRequiredPermissions()) {
            if (connectionListener != null) {
                connectionListener.onConnectionFailed("Không có quyền kết nối");
            }
            return;
        }

        Log.d(TAG, "Đang kết nối tới " + device.getAddress());
        bluetoothGatt = device.connectGatt(context, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (!hasRequiredPermissions()) return;

            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.d(TAG, "Đã kết nối tới GATT server");
                isConnected = true;
                gatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.d(TAG, "Đã ngắt kết nối khỏi GATT server");
                isConnected = false;
                mainHandler.post(() -> {
                    if (connectionListener != null) {
                        connectionListener.onDeviceDisconnected();
                    }
                    if (communicationListener != null) {
                        communicationListener.onConnectionLost();
                    }
                });
                disconnect();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(UART_SERVICE_UUID);
                if (service != null) {
                    txCharacteristic = service.getCharacteristic(TX_CHARACTERISTIC_UUID);
                    rxCharacteristic = service.getCharacteristic(RX_CHARACTERISTIC_UUID);
                    
                    if (txCharacteristic != null && rxCharacteristic != null) {
                        Log.d(TAG, "Tìm thấy dịch vụ UART");
                        enableRxNotifications(gatt);
                        
                        mainHandler.post(() -> {
                            if (connectionListener != null) {
                                connectionListener.onDeviceConnected(gatt.getDevice());
                            }
                        });
                    } else {
                        Log.e(TAG, "Không tìm thấy characteristic UART");
                        disconnect();
                    }
                } else {
                    Log.e(TAG, "Không tìm thấy dịch vụ UART");
                    disconnect();
                }
            } else {
                Log.w(TAG, "onServicesDiscovered status: " + status);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Đã gửi dữ liệu thành công");
            } else {
                Log.e(TAG, "Gửi dữ liệu thất bại, status: " + status);
                mainHandler.post(() -> {
                    if (communicationListener != null) {
                        communicationListener.onCommunicationError("Gửi dữ liệu thất bại");
                    }
                });
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            if (RX_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                final String receivedData = new String(characteristic.getValue(), StandardCharsets.UTF_8);
                Log.d(TAG, "Nhận được dữ liệu: " + receivedData);
                
                mainHandler.post(() -> {
                    if (communicationListener != null) {
                        communicationListener.onDataReceived(receivedData);
                    }
                });
            }
        }
    };

    private void enableRxNotifications(BluetoothGatt gatt) {
        if (!hasRequiredPermissions() || rxCharacteristic == null) return;

        gatt.setCharacteristicNotification(rxCharacteristic, true);
        BluetoothGattDescriptor descriptor = rxCharacteristic.getDescriptor(CCCD_UUID);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);
            Log.d(TAG, "Đã kích hoạt thông báo cho RX");
        } else {
            Log.e(TAG, "Không tìm thấy CCCD cho RX characteristic");
        }
    }

    @Override
    public void sendData(String data) {
        if (bluetoothGatt == null || txCharacteristic == null || !isConnected) {
            Log.e(TAG, "Chưa kết nối với thiết bị - bluetoothGatt: " + (bluetoothGatt != null) + 
                  ", txCharacteristic: " + (txCharacteristic != null) + ", isConnected: " + isConnected);
            if (communicationListener != null) {
                communicationListener.onCommunicationError("Chưa kết nối với thiết bị");
            }
            return;
        }

        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Không có quyền gửi dữ liệu");
            if (communicationListener != null) {
                communicationListener.onCommunicationError("Không có quyền gửi dữ liệu");
            }
            return;
        }

        // Kiểm tra write properties của characteristic
        int properties = txCharacteristic.getProperties();
        Log.d(TAG, "TX Characteristic properties: " + properties);
        
        // Thêm byte lệnh 0x15 vào đầu
        byte command = 0x15;
        byte[] messageBytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] dataToSend = new byte[1 + messageBytes.length];
        dataToSend[0] = command;
        System.arraycopy(messageBytes, 0, dataToSend, 1, messageBytes.length);

        Log.d(TAG, "Chuẩn bị gửi " + dataToSend.length + " bytes: [0x" + 
              String.format("%02x", command) + "] + \"" + data + "\"");

        // Set giá trị cho characteristic
        boolean setValue = txCharacteristic.setValue(dataToSend);
        if (!setValue) {
            Log.e(TAG, "Không thể set value cho characteristic");
            if (communicationListener != null) {
                communicationListener.onCommunicationError("Không thể set value cho characteristic");
            }
            return;
        }

        // Thử writeCharacteristic với writeType tối ưu
        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            txCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            Log.d(TAG, "Sử dụng WRITE_TYPE_NO_RESPONSE");
        } else if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
            txCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            Log.d(TAG, "Sử dụng WRITE_TYPE_DEFAULT");
        } else {
            Log.e(TAG, "Characteristic không hỗ trợ write operations");
            if (communicationListener != null) {
                communicationListener.onCommunicationError("Characteristic không hỗ trợ write");
            }
            return;
        }

        boolean success = bluetoothGatt.writeCharacteristic(txCharacteristic);
        
        if (!success) {
            Log.e(TAG, "Gửi dữ liệu không thành công - writeCharacteristic failed");
            if (communicationListener != null) {
                communicationListener.onCommunicationError("Gửi dữ liệu không thành công");
            }
        } else {
            Log.d(TAG, "WriteCharacteristic initiated successfully for " + dataToSend.length + " bytes");
            // Thông báo tạm thời cho UI - kết quả thực tế sẽ có trong onCharacteristicWrite
            if (communicationListener != null) {
                communicationListener.onDataSent(data);
            }
        }
    }

    @Override
    public void sendRobotCommand(String command) {
        sendData(command);
    }

    @Override
    public void moveForward() {
        sendRobotCommand("FORWARD");
    }

    @Override
    public void moveBackward() {
        sendRobotCommand("BACKWARD");
    }

    @Override
    public void turnLeft() {
        sendRobotCommand("LEFT");
    }

    @Override
    public void turnRight() {
        sendRobotCommand("RIGHT");
    }

    @Override
    public void stopMovement() {
        sendRobotCommand("STOP");
    }

    @Override
    public void disconnect() {
        isConnected = false;
        
        if (bluetoothGatt != null) {
            if (hasRequiredPermissions()) {
                bluetoothGatt.close();
            }
            bluetoothGatt = null;
            txCharacteristic = null;
            rxCharacteristic = null;
        }
        
        stopScan();
        Log.d(TAG, "Đã đóng kết nối BLE");
    }

    @Override
    public boolean isConnected() {
        return isConnected;
    }

    @Override
    public BluetoothDevice getConnectedDevice() {
        return bluetoothGatt != null ? bluetoothGatt.getDevice() : null;
    }

    public boolean isScanning() {
        return scanning;
    }

    public List<BluetoothDevice> getDiscoveredDevices() {
        return new ArrayList<>(discoveredDevices);
    }
} 