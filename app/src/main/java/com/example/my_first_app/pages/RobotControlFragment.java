package com.example.my_first_app;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
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
import androidx.fragment.app.FragmentManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class RobotControlFragment extends Fragment implements RobotCommunicationInterface.CommunicationListener {
    
    private static final String TAG = "RobotControlFragment";
    
    private RobotCommunicationInterface robotCommunication;
//    private JoystickView joystickView;
    private TextView connectionStatusText;
//    private TextView joystickStatusText;
    private TextView communicationLogText;
    private Button forwardButton, backwardButton, leftButton, rightButton, turnLeftButton, turnRightButton;
    private Button disconnectButton, connectButton;
    private View noConnectionView, controlsView;
    
    // private int currentDirection = JoystickView.JoystickDirection.IDLE;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView called");
        
        try {
            View view = inflater.inflate(R.layout.fragment_robot_control, container, false);
            
            initializeViews(view);
            setupCommunicationService();
//            setupJoystick();
            setupControlButtons();
            
            return view;
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreateView", e);
            // Return a simple view if layout inflation fails
            TextView errorView = new TextView(getContext());
            errorView.setText("Error loading robot control interface");
            return errorView;
        }
    }
    
    private void initializeViews(View view) {
        try {
//            joystickView = view.findViewById(R.id.joystickView);
            connectionStatusText = view.findViewById(R.id.connectionStatusText);
//            joystickStatusText = view.findViewById(R.id.joystickStatusText);
            communicationLogText = view.findViewById(R.id.communicationLogText);
            
            forwardButton = view.findViewById(R.id.forwardButton);
            backwardButton = view.findViewById(R.id.backwardButton);
            leftButton = view.findViewById(R.id.leftButton);
            rightButton = view.findViewById(R.id.rightButton);
            turnRightButton = view.findViewById(R.id.rotateRightButton);
            turnLeftButton = view.findViewById(R.id.rotateLeftButton);
            backwardButton = view.findViewById(R.id.backwardButton);
            disconnectButton = view.findViewById(R.id.disconnectButton);
            
            // New views for no connection state
            noConnectionView = view.findViewById(R.id.noConnectionView);
            controlsView = view.findViewById(R.id.controlsView);
            connectButton = view.findViewById(R.id.connectButton);
            
            // Update the no connection description text
            TextView noConnectionDescription = view.findViewById(R.id.noConnectionDescription);
            if (noConnectionDescription != null) {
                noConnectionDescription.setText("Bạn cần kết nối với robot để sử dụng các tính năng điều khiển. Nhấn nút bên dưới để quay về màn hình quét thiết bị.");
            }
            
            // Check for null views
            if (connectionStatusText == null) {
                Log.e(TAG, "connectionStatusText is null");
            }
            if (communicationLogText == null) {
                Log.e(TAG, "communicationLogText is null");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views", e);
        }
    }
    
    private void setupCommunicationService() {
        try {
            // Get existing service from ConnectionManager
            robotCommunication = ConnectionManager.getInstance().getCommunicationService();
            
            if (robotCommunication != null) {
                robotCommunication.setCommunicationListener(this);
                
                if (robotCommunication.isConnected()) {
                    String deviceName = "Unknown Device";
                    try {
                        if (robotCommunication.getConnectedDevice() != null) {
                            String name = robotCommunication.getConnectedDevice().getName();
                            if (name != null && !name.isEmpty()) {
                                deviceName = name;
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error getting device name", e);
                    }
                    
                    if (connectionStatusText != null) {
                        connectionStatusText.setText("Connected to: " + deviceName + " (BLE)");
                    }
                    
                    // Show controls, hide no connection view
                    showControlsView(true);
                } else {
                    if (connectionStatusText != null) {
                        connectionStatusText.setText("Not connected");
                    }
                    
                    // Show no connection view, hide controls
                    showControlsView(false);
                }
            } else {
                if (connectionStatusText != null) {
                    connectionStatusText.setText("No robot service available");
                }
                
                // Show no connection view, hide controls
                showControlsView(false);
            }
            
            // Setup connect button
            if (connectButton != null) {
                connectButton.setOnClickListener(v -> {
                    // Navigate back to the main screen (MainActivity)
                    if (getActivity() != null) {
                        try {
                            // Quay về màn hình chính (MainActivity) với các mục Ohstem Robot Control, Sẵn sàng quét thiết bị BLE
                            Intent intent = new Intent(getActivity(), MainActivity.class);
                            startActivity(intent);
                            
                            // Đóng HomeActivity để không quay lại màn hình hiện tại khi nhấn back
                            getActivity().finish();
                        } catch (Exception e) {
                            Log.e(TAG, "Error navigating back to MainActivity", e);
                            Toast.makeText(getContext(), "Không thể quay về màn hình chính", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up communication service", e);
            if (connectionStatusText != null) {
                connectionStatusText.setText("Error setting up communication");
            }
            
            // Show no connection view, hide controls
            showControlsView(false);
        }
    }
    
    private void showControlsView(boolean showControls) {
        if (controlsView != null && noConnectionView != null) {
            controlsView.setVisibility(showControls ? View.VISIBLE : View.GONE);
            noConnectionView.setVisibility(showControls ? View.GONE : View.VISIBLE);
        }
    }
    
//    private void setupJoystick() {
//        joystickView.setJoystickListener(this);
//        joystickStatusText.setText("Joystick: Idle");
//    }
    
    private void setupControlButtons() {
        try {
            // Forward button
            if (forwardButton != null) {
                forwardButton.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                sendRobotCommand("FW");
                                return true;
                            case MotionEvent.ACTION_UP:
                            case MotionEvent.ACTION_CANCEL:
                                sendRobotCommand("ST");
                                return true;
                        }
                        return false;
                    }
                });
            }

            // Backward button
            if (backwardButton != null) {
                backwardButton.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                sendRobotCommand("BW");
                                return true;
                            case MotionEvent.ACTION_UP:
                            case MotionEvent.ACTION_CANCEL:
                                sendRobotCommand("ST");
                                return true;
                        }
                        return false;
                    }
                });
            }

            // Left button
            if (leftButton != null) {
                leftButton.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                sendRobotCommand("ML");
                                return true;
                            case MotionEvent.ACTION_UP:
                            case MotionEvent.ACTION_CANCEL:
                                sendRobotCommand("ST");
                                return true;
                        }
                        return false;
                    }
                });
            }

            // Right button
            if (rightButton != null) {
                rightButton.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                sendRobotCommand("MR");
                                return true;
                            case MotionEvent.ACTION_UP:
                            case MotionEvent.ACTION_CANCEL:
                                sendRobotCommand("ST");
                                return true;
                        }
                        return false;
                    }
                });
            }

            // Turn Right button
            if (turnRightButton != null) {
                turnRightButton.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                sendRobotCommand("TR");
                                return true;
                            case MotionEvent.ACTION_UP:
                            case MotionEvent.ACTION_CANCEL:
                                sendRobotCommand("ST");
                                return true;
                        }
                        return false;
                    }
                });
            }

            // Turn Left button
            if (turnLeftButton != null) {
                turnLeftButton.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                sendRobotCommand("TL");
                                return true;
                            case MotionEvent.ACTION_UP:
                            case MotionEvent.ACTION_CANCEL:
                                sendRobotCommand("ST");
                                return true;
                        }
                        return false;
                    }
                });
            }
            
            // Disconnect button
            if (disconnectButton != null) {
                disconnectButton.setOnClickListener(v -> {
                    try {
                        if (robotCommunication != null) {
                            robotCommunication.disconnect();
                            Toast.makeText(getContext(), "Đã ngắt kết nối với robot", Toast.LENGTH_SHORT).show();
                        }
                        
                        // Update UI to show disconnected state
                        if (connectionStatusText != null) {
                            connectionStatusText.setText("Đã ngắt kết nối");
                        }
                        
                        // Show no connection view
                        showControlsView(false);
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error disconnecting", e);
                    }
                });
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up control buttons", e);
        }
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
        try {
            if (robotCommunication != null && robotCommunication.isConnected()) {
                robotCommunication.sendRobotCommand(command);
            } else {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Robot not connected", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending robot command: " + command, e);
        }
    }
    
//    private void updateButtonState(String state) {
//        joystickStatusText.setText("Control: " + state);
//    }
    
    // RobotCommunicationInterface.CommunicationListener implementation
    @Override
    public void onDataReceived(String data) {
        try {
            if (getActivity() != null && !getActivity().isFinishing()) {
                getActivity().runOnUiThread(() -> {
                    try {
                        if (communicationLogText != null) {
                            communicationLogText.append("Received: " + data + "\n");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error updating received data UI", e);
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDataReceived", e);
        }
    }
    
    @Override
    public void onDataSent(String data) {
        try {
            if (getActivity() != null && !getActivity().isFinishing()) {
                getActivity().runOnUiThread(() -> {
                    try {
                        if (communicationLogText != null) {
                            communicationLogText.append("Sent: " + data + "\n");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error updating sent data UI", e);
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDataSent", e);
        }
    }
    
    @Override
    public void onConnectionLost() {
        try {
            if (getActivity() != null && !getActivity().isFinishing()) {
                getActivity().runOnUiThread(() -> {
                    try {
                        if (connectionStatusText != null) {
                            connectionStatusText.setText("Connection lost");
                        }
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Connection to robot lost", Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error updating connection lost UI", e);
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onConnectionLost", e);
        }
    }
    
    @Override
    public void onCommunicationError(String error) {
        try {
            if (getActivity() != null && !getActivity().isFinishing()) {
                getActivity().runOnUiThread(() -> {
                    try {
                        if (communicationLogText != null) {
                            communicationLogText.append("Error: " + error + "\n");
                        }
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Communication error: " + error, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error updating communication error UI", e);
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onCommunicationError", e);
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            Log.d(TAG, "onDestroy called");
            if (robotCommunication != null) {
                robotCommunication.sendRobotCommand("ST"); // Stop robot before destroying fragment
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        }
    }
} 