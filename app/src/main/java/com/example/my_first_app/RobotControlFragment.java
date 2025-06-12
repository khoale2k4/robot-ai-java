package com.example.my_first_app;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

public class RobotControlFragment extends Fragment implements RobotCommunicationInterface.CommunicationListener {
    
    private RobotCommunicationInterface robotCommunication;
//    private JoystickView joystickView;
    private TextView connectionStatusText;
//    private TextView joystickStatusText;
    private TextView communicationLogText;
    private Button forwardButton, backwardButton, leftButton, rightButton, turnLeftButton, turnRightButton;
    private Button disconnectButton;
    
    private int currentDirection = JoystickView.JoystickDirection.IDLE;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_robot_control, container, false);
        
        initializeViews(view);
        setupCommunicationService();
//        setupJoystick();
        setupControlButtons();
        
        return view;
    }
    
    private void initializeViews(View view) {
//        joystickView = view.findViewById(R.id.joystickView);
        connectionStatusText = view.findViewById(R.id.connectionStatusText);
//        joystickStatusText = view.findViewById(R.id.joystickStatusText);
        communicationLogText = view.findViewById(R.id.communicationLogText);
        
        forwardButton = view.findViewById(R.id.forwardButton);
        backwardButton = view.findViewById(R.id.backwardButton);
        leftButton = view.findViewById(R.id.leftButton);
        rightButton = view.findViewById(R.id.rightButton);
        turnRightButton = view.findViewById(R.id.rotateRightButton);
        turnLeftButton = view.findViewById(R.id.rotateLeftButton);
        backwardButton = view.findViewById(R.id.backwardButton);
        disconnectButton = view.findViewById(R.id.disconnectButton);
    }
    
    private void setupCommunicationService() {
        // Get existing service from ConnectionManager
        robotCommunication = ConnectionManager.getInstance().getCommunicationService();
        
        if (robotCommunication != null) {
            robotCommunication.setCommunicationListener(this);
            
            if (robotCommunication.isConnected()) {
                String deviceName = robotCommunication.getConnectedDevice() != null ?
                    robotCommunication.getConnectedDevice().getName() : "Unknown Device";
                connectionStatusText.setText("Connected to: " + deviceName + " (BLE)");
            } else {
                connectionStatusText.setText("Not connected");
            }
        } else {
            connectionStatusText.setText("No robot service available");
        }
    }
    
//    private void setupJoystick() {
//        joystickView.setJoystickListener(this);
//        joystickStatusText.setText("Joystick: Idle");
//    }
    
    private void setupControlButtons() {
        // Forward button
        forwardButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        sendRobotCommand("FW");
//                        updateButtonState("Moving Forward");
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        sendRobotCommand("ST");
//                        updateButtonState("Stopped");
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
                        sendRobotCommand("BW");
//                        updateButtonState("Moving Forward");
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        sendRobotCommand("ST");
//                        updateButtonState("Stopped");
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
                        sendRobotCommand("ML");
//                        updateButtonState("Turning Left");
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        sendRobotCommand("ST");
//                        updateButtonState("Stopped");
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
                        sendRobotCommand("MR");
//                        updateButtonState("Turning Right");
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        sendRobotCommand("ST");
//                        updateButtonState("Stopped");
                        return true;
                }
                return false;
            }
        });

        // Right button
        turnRightButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        sendRobotCommand("TR");
//                        updateButtonState("Turning Right");
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        sendRobotCommand("ST");
//                        updateButtonState("Stopped");
                        return true;
                }
                return false;
            }
        });

        // Right button
        turnLeftButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        sendRobotCommand("TL");
//                        updateButtonState("Turning Right");
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        sendRobotCommand("ST");
//                        updateButtonState("Stopped");
                        return true;
                }
                return false;
            }
        });

        
        // Disconnect button
        disconnectButton.setOnClickListener(v -> {
            if (robotCommunication != null) {
                robotCommunication.disconnect();
            }
            // Close the app
            if (getActivity() != null) {
                getActivity().finish();
            }
        });
    }
    
//    @Override
//    public void onJoystickMoved(float x, float y, double angle, double power) {
//        int direction = JoystickView.JoystickDirection.getDirection(x, y, power);
//
//        if (direction != currentDirection) {
//            currentDirection = direction;
//            handleJoystickDirection(direction, power);
//        }
//
//        // Update joystick status
//        String status = String.format("X: %.2f, Y: %.2f, Power: %.2f", x, y, power);
//        joystickStatusText.setText("Joystick: " + status);
//    }
    
//    @Override
//    public void onJoystickReleased() {
//        currentDirection = JoystickView.JoystickDirection.IDLE;
//        sendRobotCommand("STOP");
//        joystickStatusText.setText("Joystick: Released - Stopped");
//    }
    
//    private void handleJoystickDirection(int direction, double power) {
//        switch (direction) {
//            case JoystickView.JoystickDirection.IDLE:
//                sendRobotCommand("STOP");
//                break;
//            case JoystickView.JoystickDirection.FORWARD:
//                sendRobotCommand("FORWARD");
//                break;
//            case JoystickView.JoystickDirection.BACKWARD:
//                sendRobotCommand("BACKWARD");
//                break;
//            case JoystickView.JoystickDirection.LEFT:
//                sendRobotCommand("LEFT");
//                break;
//            case JoystickView.JoystickDirection.RIGHT:
//                sendRobotCommand("RIGHT");
//                break;
//            case JoystickView.JoystickDirection.FORWARD_LEFT:
//                sendRobotCommand("FORWARD_LEFT");
//                break;
//            case JoystickView.JoystickDirection.FORWARD_RIGHT:
//                sendRobotCommand("FORWARD_RIGHT");
//                break;
//            case JoystickView.JoystickDirection.BACKWARD_LEFT:
//                sendRobotCommand("BACKWARD_LEFT");
//                break;
//            case JoystickView.JoystickDirection.BACKWARD_RIGHT:
//                sendRobotCommand("BACKWARD_RIGHT");
//                break;
//        }
//    }
    
    private void sendRobotCommand(String command) {
        if (robotCommunication != null && robotCommunication.isConnected()) {
            robotCommunication.sendRobotCommand(command);
        } else {
            Toast.makeText(getContext(), "Robot not connected", Toast.LENGTH_SHORT).show();
        }
    }
    
//    private void updateButtonState(String state) {
//        joystickStatusText.setText("Control: " + state);
//    }
    
    // RobotCommunicationInterface.CommunicationListener implementation
    @Override
    public void onDataReceived(String data) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (communicationLogText != null) {
                    communicationLogText.append("Received: " + data + "\n");
                }
            });
        }
    }
    
    @Override
    public void onDataSent(String data) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (communicationLogText != null) {
                    communicationLogText.append("Sent: " + data + "\n");
                }
            });
        }
    }
    
    @Override
    public void onConnectionLost() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                connectionStatusText.setText("Connection lost");
                Toast.makeText(getContext(), "Connection to robot lost", Toast.LENGTH_LONG).show();
            });
        }
    }
    
    @Override
    public void onCommunicationError(String error) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (communicationLogText != null) {
                    communicationLogText.append("Error: " + error + "\n");
                }
                Toast.makeText(getContext(), "Communication error: " + error, Toast.LENGTH_SHORT).show();
            });
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (robotCommunication != null) {
            robotCommunication.sendRobotCommand("STOP"); // Stop robot before destroying fragment
        }
    }
} 