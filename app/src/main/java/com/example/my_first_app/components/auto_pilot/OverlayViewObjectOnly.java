package com.example.my_first_app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;

import org.tensorflow.lite.task.vision.detector.Detection;

import java.util.ArrayList;
import java.util.List;

public class OverlayViewObjectOnly extends View {

    // Lớp nội bộ để chứa dữ liệu đã được xử lý sẵn cho việc vẽ
    private static class DrawInfo {
        final RectF box;
        final String text;

        DrawInfo(RectF box, String text) {
            this.box = box;
            this.text = text;
        }
    }

    private List<DrawInfo> drawData = new ArrayList<>();
    private Paint boxPaint;
    private Paint textPaint;

    public interface ObjectDetectionListener {
        void onObjectDetected(String label, float distanceMeters, float objectWidthRatio);
    }

    private ObjectDetectionListener listener;

    public OverlayViewObjectOnly(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaints();
    }

    private void initPaints() {
        boxPaint = new Paint();
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(6f);
        boxPaint.setColor(Color.RED);
        boxPaint.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40f);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setAntiAlias(true);
        textPaint.setShadowLayer(4f, 0f, 0f, Color.BLACK);
    }

    public void setObjectDetectionListener(ObjectDetectionListener listener) {
        this.listener = listener;
    }

    // Đổi kiểu trả về thành void và xử lý logic tại đây
    public String setResults(List<Detection> detections, int imageWidth, int imageHeight) {
        drawData.clear(); // Xóa dữ liệu cũ

        if (detections == null || detections.isEmpty()) {
            invalidate(); // Gọi invalidate để xóa các box cũ trên màn hình
            return "COMMAND";
        }

        float scaleX = (float) getWidth() / imageWidth;
        float scaleY = (float) getHeight() / imageHeight;

        for (Detection detection : detections) {
            if (detection.getCategories().isEmpty())
                continue;

            // 1. TÍNH TOÁN TỌA ĐỘ VÀ DỮ LIỆU
            RectF originalBox = detection.getBoundingBox();
            RectF scaledBox = new RectF(
                    originalBox.left * scaleX,
                    originalBox.top * scaleY,
                    originalBox.right * scaleX,
                    originalBox.bottom * scaleY);

            float widthPixels = scaledBox.width();
            float heightPixels = scaledBox.height();

            float widthRatio = widthPixels / getWidth();
            float distanceMeters = estimateDistance(heightPixels / getHeight());
            float score = detection.getCategories().get(0).getScore();
            String label = detection.getCategories().get(0).getLabel();

            // 2. GỌI LISTENER (Nằm ngoài onDraw)
            if (listener != null) {
                listener.onObjectDetected(label, distanceMeters, widthRatio);
            }

            // 3. TẠO CHUỖI (Nằm ngoài onDraw)
            String text = String.format("%s | %.1fm | %.0f%%", label, distanceMeters, score * 100);

            // Thêm dữ liệu đã xử lý vào danh sách để vẽ
            drawData.add(new DrawInfo(scaledBox, text));
        }

        invalidate(); // Yêu cầu vẽ lại View với dữ liệu mới
        return "COMMAND";
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // onDraw giờ chỉ làm một việc: VẼ từ dữ liệu đã được xử lý sẵn
        for (DrawInfo info : drawData) {
            canvas.drawRect(info.box, boxPaint);
            canvas.drawText(info.text, info.box.left, info.box.top - 10, textPaint);
        }
    }

    private float estimateDistance(float heightRatio) {
        if (heightRatio > 0.6f)
            return 0.5f;
        if (heightRatio > 0.4f)
            return 1f;
        if (heightRatio > 0.2f)
            return 2f;
        if (heightRatio > 0.1f)
            return 3.5f;
        return 5f;
    }
}