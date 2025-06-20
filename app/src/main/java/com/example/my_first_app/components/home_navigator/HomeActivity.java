package com.example.my_first_app;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import android.widget.Toast;

public class HomeActivity extends AppCompatActivity {
    
    private BottomNavigationView bottomNavigationView;
    private FragmentManager fragmentManager;
    private boolean isRobotConnected = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_navigator);
        
        initializeViews();
        checkRobotConnection();
        setupBottomNavigation();
        
        // Load default fragment (Robot Control)
        if (savedInstanceState == null) {
            loadFragment(new RobotControlFragment());
        }
    }
    
    private void initializeViews() {
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        fragmentManager = getSupportFragmentManager();
    }
    
    private void checkRobotConnection() {
        // Check if robot is connected
        RobotCommunicationInterface robotCommunication = ConnectionManager.getInstance().getCommunicationService();
        isRobotConnected = (robotCommunication != null && robotCommunication.isConnected());
        
        if (!isRobotConnected) {
            Toast.makeText(this, "Không có kết nối với robot. Một số tính năng có thể bị hạn chế.", Toast.LENGTH_LONG).show();
        }
    }
    
    private void setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            
            int itemId = item.getItemId();
            if (itemId == R.id.nav_robot_control) {
                selectedFragment = new RobotControlFragment();
            } else if (itemId == R.id.nav_camera_ai) {
                selectedFragment = new CameraAIFragment();
            } 
            else if (itemId == R.id.nav_map) {
                selectedFragment = new MapFragment();
            }
            
            if (selectedFragment != null) {
                loadFragment(selectedFragment);
                return true;
            }
            return false;
        });
    }
    
    private void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cleanup connection when closing app
        RobotCommunicationInterface robotCommunication = ConnectionManager.getInstance().getCommunicationService();
        if (robotCommunication != null && robotCommunication.isConnected()) {
            robotCommunication.sendRobotCommand("ST");
            robotCommunication.disconnect();
        }
        ConnectionManager.getInstance().clearConnection();
    }
    
    @Override
    public void onBackPressed() {
        // Stop robot before closing app
        RobotCommunicationInterface robotCommunication = ConnectionManager.getInstance().getCommunicationService();
        if (robotCommunication != null && robotCommunication.isConnected()) {
            robotCommunication.sendRobotCommand("ST");
        }
        super.onBackPressed();
    }
} 