package com.example.my_first_app;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import androidx.core.app.ActivityCompat;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BluetoothService implements RobotCommunicationInterface {
    private static final String TAG = "BluetoothService";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private BluetoothDevice bluetoothDevice;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Context context;
    private Handler mainHandler;
    
    private boolean isConnected = false;
    private BluetoothConnectionListener connectionListener;
    private CommunicationListener communicationListener;
    
    public interface BluetoothConnectionListener {
        void onDeviceConnected(BluetoothDevice device);
        void onDeviceDisconnected();
        void onConnectionFailed(String error);
        void onDataReceived(String data);
    }
    
    public BluetoothService(Context context) {
        this.context = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public void setConnectionListener(BluetoothConnectionListener listener) {
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
    
    public List<BluetoothDevice> getPairedDevices() {
        List<BluetoothDevice> devices = new ArrayList<>();
        if (bluetoothAdapter != null) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) 
                == PackageManager.PERMISSION_GRANTED) {
                Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
                devices.addAll(pairedDevices);
            }
        }
        return devices;
    }
    
    public void connectToDevice(BluetoothDevice device) {
        this.bluetoothDevice = device;
        new Thread(() -> {
            try {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) 
                    == PackageManager.PERMISSION_GRANTED) {
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
                    bluetoothSocket.connect();
                    
                    inputStream = bluetoothSocket.getInputStream();
                    outputStream = bluetoothSocket.getOutputStream();
                    
                    isConnected = true;
                    
                    mainHandler.post(() -> {
                        if (connectionListener != null) {
                            connectionListener.onDeviceConnected(device);
                        }
                    });
                    
                    // Start listening for incoming data
                    startDataListener();
                }
            } catch (IOException e) {
                isConnected = false;
                mainHandler.post(() -> {
                    if (connectionListener != null) {
                        connectionListener.onConnectionFailed(e.getMessage());
                    }
                    if (communicationListener != null) {
                        communicationListener.onCommunicationError(e.getMessage());
                    }
                });
            }
        }).start();
    }
    
    private void startDataListener() {
        new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;
            
            while (isConnected) {
                try {
                    bytes = inputStream.read(buffer);
                    String data = new String(buffer, 0, bytes);
                    
                    mainHandler.post(() -> {
                        if (connectionListener != null) {
                            connectionListener.onDataReceived(data);
                        }
                        if (communicationListener != null) {
                            communicationListener.onDataReceived(data);
                        }
                    });
                } catch (IOException e) {
                    isConnected = false;
                    mainHandler.post(() -> {
                        if (connectionListener != null) {
                            connectionListener.onDeviceDisconnected();
                        }
                        if (communicationListener != null) {
                            communicationListener.onConnectionLost();
                        }
                    });
                    break;
                }
            }
        }).start();
    }
    
    @Override
    public void sendData(String data) {
        if (isConnected && outputStream != null) {
            new Thread(() -> {
                try {
                    outputStream.write(data.getBytes());
                    outputStream.flush();
                    
                    mainHandler.post(() -> {
                        if (communicationListener != null) {
                            communicationListener.onDataSent(data);
                        }
                    });
                } catch (IOException e) {
                    mainHandler.post(() -> {
                        if (connectionListener != null) {
                            connectionListener.onConnectionFailed("Failed to send data: " + e.getMessage());
                        }
                        if (communicationListener != null) {
                            communicationListener.onCommunicationError("Failed to send data: " + e.getMessage());
                        }
                    });
                }
            }).start();
        } else if (communicationListener != null) {
            communicationListener.onCommunicationError("Device not connected");
        }
    }
    
    @Override
    public void sendRobotCommand(String command) {
        sendData(command + "\n");
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
        
        new Thread(() -> {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
                if (bluetoothSocket != null) {
                    bluetoothSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            mainHandler.post(() -> {
                if (connectionListener != null) {
                    connectionListener.onDeviceDisconnected();
                }
            });
        }).start();
    }
    
    @Override
    public boolean isConnected() {
        return isConnected;
    }
    
    @Override
    public BluetoothDevice getConnectedDevice() {
        return bluetoothDevice;
    }
} 