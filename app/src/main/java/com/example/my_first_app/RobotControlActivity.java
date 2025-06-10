package com.example.my_first_app;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class RobotControlActivity extends AppCompatActivity implements JoystickView.JoystickListener {
    
    private BluetoothService bluetoothService;
    private JoystickView joystickView;
    private TextView connectionStatusText;
    private TextView joystickStatusText;
    private Button forwardButton, backwardButton, leftButton, rightButton, stopButton;
    private Button disconnectButton;
    
    private int currentDirection = JoystickView.JoystickDirection.IDLE;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_robot_control);
        
        initializeViews();
        setupBluetoothService();
        setupJoystick();
        setupControlButtons();
    }
    
    private void initializeViews() {
        joystickView = findViewById(R.id.joystickView);
        connectionStatusText = findViewById(R.id.connectionStatusText);
        joystickStatusText = findViewById(R.id.joystickStatusText);
        
        forwardButton = findViewById(R.id.forwardButton);
        backwardButton = findViewById(R.id.backwardButton);
        leftButton = findViewById(R.id.leftButton);
        rightButton = findViewById(R.id.rightButton);
        stopButton = findViewById(R.id.stopButton);
        disconnectButton = findViewById(R.id.disconnectButton);
    }
    
    private void setupBluetoothService() {
        bluetoothService = new BluetoothService(this);
        
        if (bluetoothService.isConnected()) {
            connectionStatusText.setText("Connected to: " + 
                (bluetoothService.getConnectedDevice() != null ? 
                 bluetoothService.getConnectedDevice().getName() : "Unknown Device"));
        } else {
            connectionStatusText.setText("Not connected");
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
            if (bluetoothService != null) {
                bluetoothService.disconnect();
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
        if (bluetoothService != null && bluetoothService.isConnected()) {
            bluetoothService.sendRobotCommand(command);
        } else {
            Toast.makeText(this, "Robot not connected", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void updateButtonState(String state) {
        joystickStatusText.setText("Control: " + state);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothService != null) {
            sendRobotCommand("STOP"); // Stop robot before disconnecting
        }
    }
    
    @Override
    public void onBackPressed() {
        if (bluetoothService != null) {
            sendRobotCommand("STOP"); // Stop robot before going back
        }
        super.onBackPressed();
    }
} 