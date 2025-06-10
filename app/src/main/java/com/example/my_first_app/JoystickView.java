package com.example.my_first_app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class JoystickView extends View {
    private static final int JOYSTICK_RADIUS = 150;
    private static final int BUTTON_RADIUS = 50;
    
    private Paint backgroundPaint;
    private Paint buttonPaint;
    private Paint centerPaint;
    
    private float centerX, centerY;
    private float buttonX, buttonY;
    private boolean isDragging = false;
    
    private JoystickListener joystickListener;
    
    public interface JoystickListener {
        void onJoystickMoved(float x, float y, double angle, double power);
        void onJoystickReleased();
    }
    
    public JoystickView(Context context) {
        super(context);
        init();
    }
    
    public JoystickView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public JoystickView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        // Initialize paints
        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.LTGRAY);
        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setAntiAlias(true);
        
        buttonPaint = new Paint();
        buttonPaint.setColor(Color.BLUE);
        buttonPaint.setStyle(Paint.Style.FILL);
        buttonPaint.setAntiAlias(true);
        
        centerPaint = new Paint();
        centerPaint.setColor(Color.DKGRAY);
        centerPaint.setStyle(Paint.Style.STROKE);
        centerPaint.setStrokeWidth(3);
        centerPaint.setAntiAlias(true);
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        buttonX = centerX;
        buttonY = centerY;
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // Draw joystick background
        canvas.drawCircle(centerX, centerY, JOYSTICK_RADIUS, backgroundPaint);
        canvas.drawCircle(centerX, centerY, JOYSTICK_RADIUS, centerPaint);
        
        // Draw center point
        canvas.drawCircle(centerX, centerY, 5, centerPaint);
        
        // Draw joystick button
        canvas.drawCircle(buttonX, buttonY, BUTTON_RADIUS, buttonPaint);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Check if touch is within joystick area
                double distance = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2));
                if (distance <= JOYSTICK_RADIUS) {
                    isDragging = true;
                    updateButtonPosition(x, y);
                    return true;
                }
                break;
                
            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    updateButtonPosition(x, y);
                    return true;
                }
                break;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    isDragging = false;
                    returnButtonToCenter();
                    if (joystickListener != null) {
                        joystickListener.onJoystickReleased();
                    }
                    return true;
                }
                break;
        }
        
        return super.onTouchEvent(event);
    }
    
    private void updateButtonPosition(float x, float y) {
        // Calculate distance from center
        double distance = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2));
        
        if (distance <= JOYSTICK_RADIUS) {
            // Within joystick bounds
            buttonX = x;
            buttonY = y;
        } else {
            // Outside joystick bounds, constrain to edge
            double angle = Math.atan2(y - centerY, x - centerX);
            buttonX = (float) (centerX + Math.cos(angle) * JOYSTICK_RADIUS);
            buttonY = (float) (centerY + Math.sin(angle) * JOYSTICK_RADIUS);
        }
        
        // Calculate joystick values
        float deltaX = buttonX - centerX;
        float deltaY = buttonY - centerY;
        double power = Math.min(Math.sqrt(deltaX * deltaX + deltaY * deltaY) / JOYSTICK_RADIUS, 1.0);
        double angle = Math.atan2(deltaY, deltaX);
        
        // Normalize values to -1 to 1 range
        float normalizedX = deltaX / JOYSTICK_RADIUS;
        float normalizedY = -deltaY / JOYSTICK_RADIUS; // Invert Y axis
        
        if (joystickListener != null) {
            joystickListener.onJoystickMoved(normalizedX, normalizedY, angle, power);
        }
        
        invalidate();
    }
    
    private void returnButtonToCenter() {
        buttonX = centerX;
        buttonY = centerY;
        invalidate();
    }
    
    public void setJoystickListener(JoystickListener listener) {
        this.joystickListener = listener;
    }
    
    // Helper methods to get movement direction
    public static class JoystickDirection {
        public static final int IDLE = 0;
        public static final int FORWARD = 1;
        public static final int BACKWARD = 2;
        public static final int LEFT = 3;
        public static final int RIGHT = 4;
        public static final int FORWARD_LEFT = 5;
        public static final int FORWARD_RIGHT = 6;
        public static final int BACKWARD_LEFT = 7;
        public static final int BACKWARD_RIGHT = 8;
        
        public static int getDirection(float x, float y, double power) {
            if (power < 0.2) {
                return IDLE;
            }
            
            double angle = Math.atan2(y, x) * 180 / Math.PI;
            if (angle < 0) angle += 360;
            
            if (angle >= 337.5 || angle < 22.5) {
                return RIGHT;
            } else if (angle >= 22.5 && angle < 67.5) {
                return BACKWARD_RIGHT;
            } else if (angle >= 67.5 && angle < 112.5) {
                return BACKWARD;
            } else if (angle >= 112.5 && angle < 157.5) {
                return BACKWARD_LEFT;
            } else if (angle >= 157.5 && angle < 202.5) {
                return LEFT;
            } else if (angle >= 202.5 && angle < 247.5) {
                return FORWARD_LEFT;
            } else if (angle >= 247.5 && angle < 292.5) {
                return FORWARD;
            } else {
                return FORWARD_RIGHT;
            }
        }
    }
} 