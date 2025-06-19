package com.example.my_first_app;

public class Config {
    public static final float KP_PERSON = 0.005f;
    public static final float MAX_SPEED = 1.0f;
    public static final float MIN_SPEED = 0.2f;
    
    // Hệ số PID góc
    public static final float PID_KP_ANG     = 0.005f;
    public static final float PID_KI_ANG     = 0.0001f;
    public static final float PID_KD_ANG     = 0.002f;

    // Hệ số PID tốc độ thẳng
    public static final float PID_KP_LIN     = 1.0f;
    public static final float PID_KI_LIN     = 0.01f;
    public static final float PID_KD_LIN     = 0.1f;

    // Tham số filter
    public static final float FILTER_ALPHA   = 0.2f;

    // Tránh chướng ngại
    public static final float KP_OBS         = 0.01f;
    public static final float BACKOFF_SPEED  = -0.2f;

    // Tốc độ tối đa và tối thiểu
    public static final float MAX_LINEAR     = 1.0f;
    public static final float MAX_ANGULAR    = 1.0f;
    public static final float MIN_LINEAR     = 0.2f;
}