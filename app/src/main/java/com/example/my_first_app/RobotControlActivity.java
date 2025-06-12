package com.example.my_first_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class RobotControlActivity extends AppCompatActivity implements JoystickView.JoystickListener, RobotCommunicationInterface.CommunicationListener {
    
    private RobotCommunicationInterface robotCommunication;
    private JoystickView joystickView;
    private TextView connectionStatusText;
    private TextView joystickStatusText;
    private TextView communicationLogText;
    private Button forwardButton, backwardButton, leftButton, rightButton, stopButton;
    private Button disconnectButton;
    
    private int currentDirection = JoystickView.JoystickDirection.IDLE;
    private String connectionType = "CLASSIC"; // Default to classic Bluetooth
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_robot_control);
        
        // Get connection type from intent
        Intent intent = getIntent();
        connectionType = intent.getStringExtra("CONNECTION_TYPE");
        if (connectionType == null) {
            connectionType = "CLASSIC";
        }
        
        initializeViews();
        setupCommunicationService();
        setupJoystick();
        setupControlButtons();
    }
    
    private void initializeViews() {
        joystickView = findViewById(R.id.joystickView);
        connectionStatusText = findViewById(R.id.connectionStatusText);
        joystickStatusText = findViewById(R.id.joystickStatusText);
        communicationLogText = findViewById(R.id.communicationLogText);
        
        forwardButton = findViewById(R.id.forwardButton);
        backwardButton = findViewById(R.id.backwardButton);
        leftButton = findViewById(R.id.leftButton);
        rightButton = findViewById(R.id.rightButton);
        stopButton = findViewById(R.id.stopButton);
        disconnectButton = findViewById(R.id.disconnectButton);
    }
    
    private void setupCommunicationService() {
        // Try to get existing service from ConnectionManager first
        robotCommunication = ConnectionManager.getInstance().getCommunicationService();
        
        if (robotCommunication == null) {
            // Fallback: create new service based on connection type
            if ("BLE".equals(connectionType)) {
                BLEService bleService = new BLEService(this);
                robotCommunication = bleService;
                connectionStatusText.setText("BLE Connection Mode - No active connection");
            } else {
                BluetoothService bluetoothService = new BluetoothService(this);
                robotCommunication = bluetoothService;
                connectionStatusText.setText("Bluetooth Classic Mode - No active connection");
            }
        } else {
            // Update connection type based on service type
            if (robotCommunication instanceof BLEService) {
                connectionType = "BLE";
            } else {
                connectionType = "CLASSIC";
            }
        }
        
        robotCommunication.setCommunicationListener(this);
        
        if (robotCommunication.isConnected()) {
            String deviceName = robotCommunication.getConnectedDevice() != null ? 
                robotCommunication.getConnectedDevice().getName() : "Unknown Device";
            connectionStatusText.setText("Connected to: " + deviceName + " (" + connectionType + ")");
        } else {
            connectionStatusText.setText("Not connected (" + connectionType + ")");
        }
    }
    
    private void setupJoystick() {
        joystickView.setJoystickListener(this);
        joystickStatusText.setText("Joystick: Idle");
    }
    
    private void setupControlButtons() {
        // Forward button
        forwardButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        sendRobotCommand("FORWARD");
                        updateButtonState("Moving Forward");
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        sendRobotCommand("STOP");
                        updateButtonState("Stopped");
                        return true;
                }
                return false;
            }
        });
        
        // Backward button
        backwardButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        sendRobotCommand("BACKWARD");
                        updateButtonState("Moving Backward");
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        sendRobotCommand("STOP");
                        updateButtonState("Stopped");
                        return true;
                }
                return false;
            }
        });
        
        // Left button
        leftButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        sendRobotCommand("LEFT");
                        updateButtonState("Turning Left");
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        sendRobotCommand("STOP");
                        updateButtonState("Stopped");
                        return true;
                }
                return false;
            }
        });
        
        // Right button
        rightButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        sendRobotCommand("RIGHT");
                        updateButtonState("Turning Right");
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        sendRobotCommand("STOP");
                        updateButtonState("Stopped");
                        return true;
                }
                return false;
            }
        });
        
        // Stop button
        stopButton.setOnClickListener(v -> {
            sendRobotCommand("STOP");
            updateButtonState("Stopped");
        });
        
        // Disconnect button
        disconnectButton.setOnClickListener(v -> {
            if (robotCommunication != null) {
                robotCommunication.disconnect();
            }
            finish();
        });
    }
    
    @Override
    public void onJoystickMoved(float x, float y, double angle, double power) {
        int direction = JoystickView.JoystickDirection.getDirection(x, y, power);
        
        if (direction != currentDirection) {
            currentDirection = direction;
            handleJoystickDirection(direction, power);
        }
        
        // Update joystick status
        String status = String.format("X: %.2f, Y: %.2f, Power: %.2f", x, y, power);
        joystickStatusText.setText("Joystick: " + status);
    }
    
    @Override
    public void onJoystickReleased() {
        currentDirection = JoystickView.JoystickDirection.IDLE;
        sendRobotCommand("STOP");
        joystickStatusText.setText("Joystick: Released - Stopped");
    }
    
    private void handleJoystickDirection(int direction, double power) {
        switch (direction) {
            case JoystickView.JoystickDirection.IDLE:
                sendRobotCommand("STOP");
                break;
            case JoystickView.JoystickDirection.FORWARD:
                sendRobotCommand("FORWARD");
                break;
            case JoystickView.JoystickDirection.BACKWARD:
                sendRobotCommand("BACKWARD");
                break;
            case JoystickView.JoystickDirection.LEFT:
                sendRobotCommand("LEFT");
                break;
            case JoystickView.JoystickDirection.RIGHT:
                sendRobotCommand("RIGHT");
                break;
            case JoystickView.JoystickDirection.FORWARD_LEFT:
                sendRobotCommand("FORWARD_LEFT");
                break;
            case JoystickView.JoystickDirection.FORWARD_RIGHT:
                sendRobotCommand("FORWARD_RIGHT");
                break;
            case JoystickView.JoystickDirection.BACKWARD_LEFT:
                sendRobotCommand("BACKWARD_LEFT");
                break;
            case JoystickView.JoystickDirection.BACKWARD_RIGHT:
                sendRobotCommand("BACKWARD_RIGHT");
                break;
        }
    }
    
    private void sendRobotCommand(String command) {
        if (robotCommunication != null && robotCommunication.isConnected()) {
            robotCommunication.sendRobotCommand(command);
        } else {
            Toast.makeText(this, "Robot not connected", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void updateButtonState(String state) {
        joystickStatusText.setText("Control: " + state);
    }
    
    // RobotCommunicationInterface.CommunicationListener implementation
    @Override
    public void onDataReceived(String data) {
        runOnUiThread(() -> {
            if (communicationLogText != null) {
                communicationLogText.append("Received: " + data + "\n");
            }
        });
    }
    
    @Override
    public void onDataSent(String data) {
        runOnUiThread(() -> {
            if (communicationLogText != null) {
                communicationLogText.append("Sent: " + data + "\n");
            }
        });
    }
    
    @Override
    public void onConnectionLost() {
        runOnUiThread(() -> {
            connectionStatusText.setText("Connection lost");
            Toast.makeText(this, "Connection to robot lost", Toast.LENGTH_LONG).show();
        });
    }
    
    @Override
    public void onCommunicationError(String error) {
        runOnUiThread(() -> {
            if (communicationLogText != null) {
                communicationLogText.append("Error: " + error + "\n");
            }
            Toast.makeText(this, "Communication error: " + error, Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (robotCommunication != null) {
            robotCommunication.sendRobotCommand("STOP"); // Stop robot before disconnecting
        }
        // Clear connection from ConnectionManager
        ConnectionManager.getInstance().clearConnection();
    }
    
    @Override
    public void onBackPressed() {
        if (robotCommunication != null) {
            robotCommunication.sendRobotCommand("STOP"); // Stop robot before going back
        }
        super.onBackPressed();
    }
} 