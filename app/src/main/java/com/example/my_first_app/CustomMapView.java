package com.example.my_first_app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.List;

public class CustomMapView extends View {

    private Paint robotPaint, destPaint, obstaclePaint;
    private MapData mapData;

    public CustomMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaints();
    }

    private void initPaints() {
        robotPaint = new Paint();
        robotPaint.setColor(Color.BLUE);
        robotPaint.setStyle(Paint.Style.FILL);

        destPaint = new Paint();
        destPaint.setColor(Color.RED);
        destPaint.setStyle(Paint.Style.FILL);

        obstaclePaint = new Paint();
        obstaclePaint.setColor(Color.BLACK);
        obstaclePaint.setStyle(Paint.Style.FILL);
    }

    public void setMapData(MapData mapData) {
        this.mapData = mapData;
        invalidate(); // bắt buộc để vẽ lại
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mapData == null) return;

        int width = getWidth();
        int height = getHeight();

        for (PointF obstacle : mapData.obstacles) {
            canvas.drawCircle(obstacle.x / 100 * width, obstacle.y / 100 * height, 10, obstaclePaint);
        }

        if (mapData.robot != null) {
            canvas.drawCircle(mapData.robot.x / 100 * width, mapData.robot.y / 100 * height, 12, robotPaint);
        }

        if (mapData.destination != null) {
            canvas.drawCircle(mapData.destination.x / 100 * width, mapData.destination.y / 100 * height, 12, destPaint);
        }
    }
}

