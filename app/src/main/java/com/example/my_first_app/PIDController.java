// PIDController.java
package com.example.my_first_app;

/**
 * PIDController
 * - Tỷ lệ (P): phản ứng ngay lập tức theo sai số hiện tại để giảm lệch nhanh.
 * - Tích phân (I): tích lũy sai số quá khứ để loại bỏ sai số dư.
 * - Đạo hàm (D): dự đoán sai số tương lai, giảm dao động và overshoot.
 *
 * -> Cái này mình sử dụng chung cho cả điều khiển góc - angular và tốc độ thẳng - linear.
 */
public class PIDController {
    private final float kp, ki, kd;
    private float prevError = 0f;
    private float integral = 0f;

    public PIDController(float kp, float ki, float kd) {
        this.kp = kp;
        this.ki = ki;
        this.kd = kd;
    }

    /**
     * Tính toán tín hiệu PID
     * @param error Sai số hiện tại
     * @param dt    Thời gian trôi qua s
     * @return      Giá trị điều khiển
     */
    public float update(float error, float dt) {
        integral += error * dt;                      // tích phân, giải tích :((
        float derivative = (error - prevError) / dt; // đạo hàm
        prevError = error;
        return kp * error + ki * integral + kd * derivative;
    }

    /**
     * Reset memory I và D
     */
    public void reset() {
        integral = 0f;
        prevError = 0f;
    }
}