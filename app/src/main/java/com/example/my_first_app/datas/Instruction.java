package com.example.my_first_app;

public class Instruction {
    public static final String FORWARD = "FW";
    public static final String BACKWARD = "BW";
    public static final String MOVE_LEFT = "ML";
    public static final String MOVE_RIGHT = "MR";
    public static final String TURN_LEFT = "TL";
    public static final String TURN_RIGHT = "TR";
    public static final String STOP = "ST";

    public static String getCommand(String command, float speed) {
        return command + ":" + String.format("%.2f", speed);
    }
}
