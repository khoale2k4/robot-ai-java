package com.example.my_first_app;

public class ConnectionManager {
    private static ConnectionManager instance;
    private RobotCommunicationInterface communicationService;
    
    private ConnectionManager() {}
    
    public static synchronized ConnectionManager getInstance() {
        if (instance == null) {
            instance = new ConnectionManager();
        }
        return instance;
    }
    
    public void setCommunicationService(RobotCommunicationInterface service) {
        this.communicationService = service;
    }
    
    public RobotCommunicationInterface getCommunicationService() {
        return communicationService;
    }
    
    public boolean hasActiveConnection() {
        return communicationService != null && communicationService.isConnected();
    }
    
    public void clearConnection() {
        if (communicationService != null) {
            communicationService.disconnect();
            communicationService = null;
        }
    }
} 