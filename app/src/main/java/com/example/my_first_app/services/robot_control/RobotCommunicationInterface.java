package com.example.my_first_app;

import android.bluetooth.BluetoothDevice;

public interface RobotCommunicationInterface {
    
    // Connection methods
    boolean isConnected();
    BluetoothDevice getConnectedDevice();
    void disconnect();
    
    // Data sending methods  
    void sendData(String data);
    void sendRobotCommand(String command);
    
    // Robot control methods
    void moveForward();
    void moveBackward();
    void turnLeft();
    void turnRight();
    void stopMovement();
    
    // Communication listener interface
    interface CommunicationListener {
        void onDataReceived(String data);
        void onDataSent(String data);
        void onConnectionLost();
        void onCommunicationError(String error);
    }
    
    // Set listener for communication events
    void setCommunicationListener(CommunicationListener listener);
} 