package com.example.my_first_app;

import java.util.List;

public class Controller {
    private int frameW;
    private int frameH;

    public Controller(int frameW, int frameH) {
        this.frameW = frameW;
        this.frameH = frameH;
    }

    public float[] compute(int[] bboxPerson, List<int[]> obstacles) {
        int x = bboxPerson[0];
        int y = bboxPerson[1];
        int w = bboxPerson[2];
        int h = bboxPerson[3];

        float cx = x + w / 2.0f;
        float errorP = cx - frameW / 2.0f;
        float angP = Config.KP_PERSON * errorP;

        float angO = 0;
        float linO = 0;

        if (obstacles != null && !obstacles.isEmpty()) {
            float sumAngles = 0;
            for (int[] obs : obstacles) {
                int ox = obs[0];
                int oy = obs[1];
                int ow = obs[2];
                int oh = obs[3];
                float ocx = ox + ow / 2.0f;
                float errorO = (frameW / 2.0f) - ocx;
                sumAngles += Config.KP_OBS * errorO;
                linO = -0.2f;  // lùi/giam toc khi obstacle
            }
            angO = sumAngles / obstacles.size();
        }

        float linearVel = Config.MAX_SPEED;
        if (linO != 0) {
            linearVel = linO;
        }

        if (w > (frameW * 0.5f)) {
            linearVel = Config.MIN_SPEED;
        }

        float angularVel = angP + angO;

        // Giới hạn
        angularVel = Math.max(-0.5f, Math.min(0.5f, angularVel));
        linearVel = Math.max(-Config.MAX_SPEED, Math.min(Config.MAX_SPEED, linearVel));

        return new float[]{linearVel, angularVel};
    }
}


