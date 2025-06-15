// Controller.java
package com.example.my_first_app;

import java.util.List;

/**
 * Controller: Điều khiển robot theo người và tránh chướng ngại.
 * Cơ chế:
 * - Lọc jitter tâm người bằng LowPassFilter.
 * - PID cho điều khiển quay.
 * - Tín hiệu đẩy lùi từ obstacles.
 * - Tính tốc độ thẳng mục tiêu theo khoảng cách qua tỷ lệ bboxHeight/frameHeight.
 * - PID cho tốc độ thẳng để di chuyển mượt.
 */
public class Controller {
    private int frameW, frameH;
    private PIDController pidAngular, pidLinear;
    private LowPassFilter filterCx;
    private long lastTime;

    public Controller(int frameW, int frameH) {
        this.frameW = frameW;
        this.frameH = frameH;
        pidAngular = new PIDController(
            Config.PID_KP_ANG, Config.PID_KI_ANG, Config.PID_KD_ANG);
        pidLinear  = new PIDController(
            Config.PID_KP_LIN, Config.PID_KI_LIN, Config.PID_KD_LIN);
        filterCx   = new LowPassFilter(Config.FILTER_ALPHA);
        lastTime   = System.currentTimeMillis();
    }

    /**
     * Tính lệnh điều khiển
     * @param bboxPerson [x, y, w, h]
     * @param obstacles  List [x, y, w, h]
     * @return           [linearVel, angularVel]
     */
    public float[] compute(int[] bboxPerson, List<int[]> obstacles) {
        long now = System.currentTimeMillis();
        float dt = (now - lastTime) / 1000f;
        if (dt <= 0f) dt = 0.02f;
        lastTime = now;

        //Lọc tâm người
        float cxRaw = bboxPerson[0] + bboxPerson[2] / 2f;
        float cx    = filterCx.filter(cxRaw);

        //PID góc
        float errorAng = cx - frameW / 2f;
        float angOut   = pidAngular.update(errorAng, dt);

        //Tránh chướng ngại
        float repulseAng = 0f;
        if (obstacles != null && !obstacles.isEmpty()) {
            for (int[] obs : obstacles) {
                float ocx = obs[0] + obs[2] / 2f;
                repulseAng += Config.KP_OBS * ((frameW / 2f) - ocx);
            }
            repulseAng /= obstacles.size();
        }
        float angularVel = angOut + repulseAng;
        angularVel = Math.max(-Config.MAX_ANGULAR, 
                       Math.min(Config.MAX_ANGULAR, angularVel));

        //Tốc độ thẳng mục tiêu
        float ratio = frameH > 0 ? (bboxPerson[3] / (float) frameH) : 0;
        float targetLin;
        if (ratio > 0.6f)      targetLin = Config.BACKOFF_SPEED;   // gần quá đi
        else if (ratio < 0.2f) targetLin = Config.MAX_LINEAR;    // xa quá
        else                   targetLin = Config.MIN_LINEAR;    // hẹ hẹ hẹ

        // Override nếu có obstacles
        if (obstacles != null && !obstacles.isEmpty()) {
            targetLin = Config.BACKOFF_SPEED;
        }

        //PID tốc độ
        float linearVel = pidLinear.update(targetLin, dt);
        linearVel = Math.max(-Config.MAX_LINEAR, 
                      Math.min(Config.MAX_LINEAR, linearVel));

        return new float[]{linearVel, angularVel};
    }

    /**
     * Reset PID và filter
     */
    public void reset() {
        pidAngular.reset();
        pidLinear.reset();
        filterCx.reset();
    }
}