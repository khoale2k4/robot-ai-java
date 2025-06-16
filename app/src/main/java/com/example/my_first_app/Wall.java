package com.example.my_first_app;

import android.graphics.PointF;

public class Wall {
    public PointF start;
    public PointF end;

    public Wall(PointF start, PointF end) {
        this.start = start;
        this.end = end;
    }
}
