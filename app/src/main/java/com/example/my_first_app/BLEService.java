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
import java.util.Queue;
import java.util.LinkedList;

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
    private static final long WRITE_DELAY_MS = 100; // Delay between BLE writes

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic txCharacteristic;
    private BluetoothGattCharacteristic rxCharacteristic;
    
    private Context context;
    private Handler mainHandler;
    private boolean scanning = false;
    private boolean isConnected = false;
    
    // Command queue for BLE writes
    private final Queue<String> commandQueue = new LinkedList<>();
    private boolean isWriting = false;
    private long lastWriteTime = 0;
    
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

            // Check permissions before accessing device info
            if (!hasRequiredPermissions()) {
                Log.w(TAG, "No permissions to access device info");
                return;
            }

            String deviceAddress = null;
            String deviceName = null;
            
            try {
                deviceAddress = device.getAddress();
                deviceName = device.getName();
            } catch (SecurityException e) {
                Log.w(TAG, "SecurityException accessing device info", e);
                return;
            } catch (Exception e) {
                Log.w(TAG, "Exception accessing device info", e);
                return;
            }
            
            if (deviceAddress == null) {
                Log.w(TAG, "Device address is null");
                return;
            }
            
            Log.d(TAG, "Phát hiện thiết bị: " + deviceAddress + ", RSSI: " + result.getRssi());
            Log.d(TAG, "Device name: " + (deviceName != null ? deviceName : "null"));

            // Show all devices, not just OhStem devices
            if (!discoveredDevices.contains(device)) {
                // Skip devices with very weak signal (RSSI < -80)
                if (result.getRssi() > -80) {
                    discoveredDevices.add(device);
                    if (connectionListener != null) {
                        connectionListener.onDeviceFound(device);
                    }
                    Log.d(TAG, "Thêm thiết bị: " + (deviceName != null ? deviceName : "Unknown") + " (" + deviceAddress + "), RSSI: " + result.getRssi());
                } else {
                    Log.d(TAG, "Skipping weak signal device: " + deviceAddress + ", RSSI: " + result.getRssi());
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "Lỗi quét BLE: " + errorCode);
            String errorMessage = "Unknown error";
            switch (errorCode) {
                case SCAN_FAILED_ALREADY_STARTED:
                    errorMessage = "Scan already started";
                    break;
                case SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                    errorMessage = "Application registration failed";
                    break;
                case SCAN_FAILED_FEATURE_UNSUPPORTED:
                    errorMessage = "Feature unsupported";
                    break;
                case SCAN_FAILED_INTERNAL_ERROR:
                    errorMessage = "Internal error";
                    break;
            }
            Log.e(TAG, "Scan failed reason: " + errorMessage);
            
            if (connectionListener != null) {
                connectionListener.onConnectionFailed("Lỗi quét BLE: " + errorMessage);
            }
        }
    };

    public void connectToDevice(BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "Device is null");
            if (connectionListener != null) {
                connectionListener.onConnectionFailed("Thiết bị không hợp lệ");
            }
            return;
        }
        
        stopScan();
        
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing required permissions");
            if (connectionListener != null) {
                connectionListener.onConnectionFailed("Không có quyền kết nối");
            }
            return;
        }

        // Disconnect existing connection if any
        if (bluetoothGatt != null) {
            try {
                Log.d(TAG, "Closing existing GATT connection");
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing existing GATT", e);
            }
            bluetoothGatt = null;
            txCharacteristic = null;
            rxCharacteristic = null;
        }

        try {
            String deviceAddress = device.getAddress();
            Log.d(TAG, "Đang kết nối tới " + deviceAddress);
            
            // Reset connection state
            isConnected = false;
            
            bluetoothGatt = device.connectGatt(context, false, gattCallback);
            
            if (bluetoothGatt == null) {
                Log.e(TAG, "Failed to create GATT connection");
                if (connectionListener != null) {
                    connectionListener.onConnectionFailed("Không thể tạo GATT connection");
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException connecting to device", e);
            if (connectionListener != null) {
                connectionListener.onConnectionFailed("Lỗi quyền truy cập: " + e.getMessage());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error connecting to device", e);
            if (connectionListener != null) {
                connectionListener.onConnectionFailed("Lỗi kết nối: " + e.getMessage());
            }
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "onConnectionStateChange: status=" + status + ", newState=" + newState);
            
            if (gatt == null) {
                Log.e(TAG, "GATT is null in onConnectionStateChange");
                return;
            }
            
            try {
                if (!hasRequiredPermissions()) {
                    Log.e(TAG, "No permissions in onConnectionStateChange");
                    return;
                }

                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG, "Đã kết nối tới GATT server, discovering services...");
                        isConnected = true;
                        
                        // Add small delay before service discovery
                        mainHandler.postDelayed(() -> {
                            try {
                                if (gatt != null && isConnected) {
                                    boolean discoverResult = gatt.discoverServices();
                                    Log.d(TAG, "discoverServices() result: " + discoverResult);
                                    
                                    if (!discoverResult) {
                                        Log.e(TAG, "Failed to start service discovery");
                                        mainHandler.post(() -> {
                                            if (connectionListener != null) {
                                                connectionListener.onConnectionFailed("Không thể khám phá dịch vụ");
                                            }
                                        });
                                    }
                                } else {
                                    Log.e(TAG, "GATT is null or not connected when trying to discover services");
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Exception during service discovery", e);
                                mainHandler.post(() -> {
                                    if (connectionListener != null) {
                                        connectionListener.onConnectionFailed("Lỗi khám phá dịch vụ: " + e.getMessage());
                                    }
                                });
                            }
                        }, 100); // 100ms delay
                        
                    } else {
                        Log.e(TAG, "Connection failed with status: " + status);
                        isConnected = false;
                        mainHandler.post(() -> {
                            if (connectionListener != null) {
                                connectionListener.onConnectionFailed("Kết nối thất bại, status: " + status);
                            }
                        });
                    }
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    Log.d(TAG, "Đã ngắt kết nối khỏi GATT server, status: " + status);
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
                } else {
                    Log.w(TAG, "Unknown connection state: " + newState + ", status: " + status);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in onConnectionStateChange", e);
                isConnected = false;
                mainHandler.post(() -> {
                    if (connectionListener != null) {
                        connectionListener.onConnectionFailed("Lỗi callback: " + e.getMessage());
                    }
                });
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "onServicesDiscovered: status=" + status);
            
            if (gatt == null) {
                Log.e(TAG, "GATT is null in onServicesDiscovered");
                mainHandler.post(() -> {
                    if (connectionListener != null) {
                        connectionListener.onConnectionFailed("GATT connection lost");
                    }
                });
                return;
            }
            
            try {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // List all available services
                    List<BluetoothGattService> services = null;
                    try {
                        services = gatt.getServices();
                    } catch (Exception e) {
                        Log.e(TAG, "Error getting services", e);
                        mainHandler.post(() -> {
                            if (connectionListener != null) {
                                connectionListener.onConnectionFailed("Lỗi lấy danh sách dịch vụ: " + e.getMessage());
                            }
                        });
                        return;
                    }
                    
                    if (services == null || services.isEmpty()) {
                        Log.e(TAG, "No services found");
                        mainHandler.post(() -> {
                            if (connectionListener != null) {
                                connectionListener.onConnectionFailed("Không tìm thấy dịch vụ nào");
                            }
                        });
                        return;
                    }
                    
                    for (BluetoothGattService service : services) {
                        if (service != null && service.getUuid() != null) {
                            Log.d(TAG, "Found service: " + service.getUuid().toString());
                        }
                    }
                    
                    BluetoothGattService service = gatt.getService(UART_SERVICE_UUID);
                    if (service != null) {
                        Log.d(TAG, "Found UART service!");
                        
                        // List all characteristics in UART service
                        List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                        if (characteristics != null) {
                            for (BluetoothGattCharacteristic characteristic : characteristics) {
                                if (characteristic != null && characteristic.getUuid() != null) {
                                    Log.d(TAG, "Found characteristic: " + characteristic.getUuid().toString() + 
                                          ", properties: " + characteristic.getProperties());
                                }
                            }
                        }
                        
                        txCharacteristic = service.getCharacteristic(TX_CHARACTERISTIC_UUID);
                        rxCharacteristic = service.getCharacteristic(RX_CHARACTERISTIC_UUID);
                        
                        Log.d(TAG, "TX Characteristic: " + (txCharacteristic != null));
                        Log.d(TAG, "RX Characteristic: " + (rxCharacteristic != null));
                        
                        if (txCharacteristic != null && rxCharacteristic != null) {
                            Log.d(TAG, "Both characteristics found, enabling notifications...");
                            
                            // Add delay before enabling notifications
                            mainHandler.postDelayed(() -> {
                                try {
                                    if (gatt != null && rxCharacteristic != null && isConnected) {
                                        enableRxNotifications(gatt);
                                        
                                        mainHandler.post(() -> {
                                            if (connectionListener != null) {
                                                connectionListener.onDeviceConnected(gatt.getDevice());
                                            }
                                        });
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error enabling notifications", e);
                                    mainHandler.post(() -> {
                                        if (connectionListener != null) {
                                            connectionListener.onConnectionFailed("Lỗi bật thông báo: " + e.getMessage());
                                        }
                                    });
                                }
                            }, 100);
                            
                        } else {
                            Log.e(TAG, "Không tìm thấy characteristic UART - TX: " + (txCharacteristic != null) + 
                                  ", RX: " + (rxCharacteristic != null));
                            mainHandler.post(() -> {
                                if (connectionListener != null) {
                                    connectionListener.onConnectionFailed("Không tìm thấy UART characteristics");
                                }
                            });
                            disconnect();
                        }
                    } else {
                        Log.e(TAG, "Không tìm thấy dịch vụ UART: " + UART_SERVICE_UUID.toString());
                        // List again for debugging
                        Log.d(TAG, "Available services count: " + services.size());
                        mainHandler.post(() -> {
                            if (connectionListener != null) {
                                connectionListener.onConnectionFailed("Không tìm thấy dịch vụ UART");
                            }
                        });
                        disconnect();
                    }
                } else {
                    Log.e(TAG, "onServicesDiscovered failed with status: " + status);
                    mainHandler.post(() -> {
                        if (connectionListener != null) {
                            connectionListener.onConnectionFailed("Khám phá dịch vụ thất bại, status: " + status);
                        }
                    });
                    disconnect();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in onServicesDiscovered", e);
                mainHandler.post(() -> {
                    if (connectionListener != null) {
                        connectionListener.onConnectionFailed("Lỗi khám phá dịch vụ: " + e.getMessage());
                    }
                });
                disconnect();
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            
            // Always reset writing state
            isWriting = false;
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Đã gửi dữ liệu thành công");
            } else {
                Log.e(TAG, "Gửi dữ liệu thất bại, status: " + status);
                mainHandler.post(() -> {
                    if (communicationListener != null) {
                        communicationListener.onCommunicationError("Gửi dữ liệu thất bại, status: " + status);
                    }
                });
            }
            
            // Process next command in queue after a small delay
            mainHandler.postDelayed(() -> processCommandQueue(), WRITE_DELAY_MS);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            if (RX_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                final String receivedData = new String(characteristic.getValue(), StandardCharsets.UTF_8);
                // Log.d(TAG, "Nhận được dữ liệu: " + receivedData);
                
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

        // Add to queue
        synchronized (commandQueue) {
            commandQueue.offer(data);
            Log.d(TAG, "Added to queue: " + data + " (queue size: " + commandQueue.size() + ")");
        }
        
        // Process queue
        processCommandQueue();
    }
    
    private void processCommandQueue() {
        if (isWriting) {
            Log.d(TAG, "Still writing, waiting...");
            return;
        }
        
        synchronized (commandQueue) {
            if (commandQueue.isEmpty()) {
                return;
            }
            
            // Check if enough time has passed since last write
            long currentTime = System.currentTimeMillis();
            long timeSinceLastWrite = currentTime - lastWriteTime;
            
            if (timeSinceLastWrite < WRITE_DELAY_MS) {
                long delayNeeded = WRITE_DELAY_MS - timeSinceLastWrite;
                Log.d(TAG, "Delaying next write by " + delayNeeded + "ms");
                mainHandler.postDelayed(this::processCommandQueue, delayNeeded);
                return;
            }
            
            String data = commandQueue.poll();
            if (data != null) {
                isWriting = true;
                sendDataImmediate(data);
            }
        }
    }
    
    private void sendDataImmediate(String data) {
        if (bluetoothGatt == null || txCharacteristic == null || !isConnected) {
            Log.e(TAG, "Cannot send data - bluetoothGatt: " + (bluetoothGatt != null) + 
                  ", txCharacteristic: " + (txCharacteristic != null) + ", isConnected: " + isConnected);
            isWriting = false;
            if (communicationListener != null) {
                communicationListener.onCommunicationError("Chưa kết nối với thiết bị");
            }
            return;
        }

        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Không có quyền gửi dữ liệu");
            isWriting = false;
            if (communicationListener != null) {
                communicationListener.onCommunicationError("Không có quyền gửi dữ liệu");
            }
            return;
        }

        if (data == null) {
            Log.e(TAG, "Data to send is null");
            isWriting = false;
            if (communicationListener != null) {
                communicationListener.onCommunicationError("Dữ liệu gửi không hợp lệ");
            }
            return;
        }

        try {
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
                isWriting = false;
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
                isWriting = false;
                if (communicationListener != null) {
                    communicationListener.onCommunicationError("Characteristic không hỗ trợ write");
                }
                return;
            }

            boolean success = bluetoothGatt.writeCharacteristic(txCharacteristic);
            
            if (!success) {
                Log.e(TAG, "Gửi dữ liệu không thành công - writeCharacteristic failed");
                isWriting = false;
                if (communicationListener != null) {
                    communicationListener.onCommunicationError("Gửi dữ liệu không thành công");
                }
                // Try to process next command in queue
                mainHandler.postDelayed(this::processCommandQueue, WRITE_DELAY_MS);
            } else {
                Log.d(TAG, "WriteCharacteristic initiated successfully for " + dataToSend.length + " bytes");
                lastWriteTime = System.currentTimeMillis();
                // Thông báo tạm thời cho UI - kết quả thực tế sẽ có trong onCharacteristicWrite
                if (communicationListener != null) {
                    communicationListener.onDataSent(data);
                }
                // isWriting will be set to false in onCharacteristicWrite callback
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException sending data", e);
            isWriting = false;
            if (communicationListener != null) {
                communicationListener.onCommunicationError("Lỗi quyền truy cập: " + e.getMessage());
            }
            // Try to process next command in queue
            mainHandler.postDelayed(this::processCommandQueue, WRITE_DELAY_MS);
        } catch (Exception e) {
            Log.e(TAG, "Exception sending data", e);
            isWriting = false;
            if (communicationListener != null) {
                communicationListener.onCommunicationError("Lỗi gửi dữ liệu: " + e.getMessage());
            }
            // Try to process next command in queue
            mainHandler.postDelayed(this::processCommandQueue, WRITE_DELAY_MS);
        }
    }

    @Override
    public void sendRobotCommand(String command) {
        sendData(command);
    }

    @Override
    public void moveForward() {
        sendRobotCommand("FW");
    }

    @Override
    public void moveBackward() {
        sendRobotCommand("BW");
    }

    @Override
    public void turnLeft() {
        sendRobotCommand("ML");
    }

    @Override
    public void turnRight() {
        sendRobotCommand("MR");
    }

    @Override
    public void stopMovement() {
        sendRobotCommand("ST");
    }

    @Override
    public void disconnect() {
        isConnected = false;
        
        // Clear command queue
        synchronized (commandQueue) {
            commandQueue.clear();
            isWriting = false;
            Log.d(TAG, "Cleared command queue");
        }
        
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