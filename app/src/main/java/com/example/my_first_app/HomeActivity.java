package com.example.my_first_app;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class HomeActivity extends AppCompatActivity {
    
    private BottomNavigationView bottomNavigationView;
    private FragmentManager fragmentManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        
        initializeViews();
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
    
    private void setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            
            int itemId = item.getItemId();
            if (itemId == R.id.nav_robot_control) {
                selectedFragment = new RobotControlFragment();
            } else if (itemId == R.id.nav_camera_ai) {
                selectedFragment = new CameraAIFragment();
            } else if (itemId == R.id.nav_settings) {
                selectedFragment = new SettingsFragment();
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
            robotCommunication.sendRobotCommand("STOP");
            robotCommunication.disconnect();
        }
        ConnectionManager.getInstance().clearConnection();
    }
    
    @Override
    public void onBackPressed() {
        // Stop robot before closing app
        RobotCommunicationInterface robotCommunication = ConnectionManager.getInstance().getCommunicationService();
        if (robotCommunication != null && robotCommunication.isConnected()) {
            robotCommunication.sendRobotCommand("STOP");
        }
        super.onBackPressed();
    }
} 