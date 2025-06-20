// OverlayView.java
package com.example.my_first_app; // Thay đổi package name

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.detector.Detection;
import java.util.LinkedList;
import java.util.List;
import com.example.my_first_app.Controller;

public class OverlayView extends View {

    private List<Detection> results = new LinkedList<>();
    private Paint boxPaint = new Paint();
    private Paint textPaint = new Paint();
    private Paint backgroundPaint = new Paint();
    private int imageWidth = 0;
    private int imageHeight = 0;
    private String callBackCommand = "";
    private Controller controller;

    // Các màu khác nhau cho từng loại object - dùng màu nổi bật hơn
    private int[] colors = {
            Color.RED, Color.GREEN, Color.YELLOW, Color.CYAN,
            Color.MAGENTA, Color.BLUE, Color.WHITE
    };

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        controller = new Controller(640, 480);
        initPaints();
    }

    private void initPaints() {
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(8f);
        boxPaint.setAntiAlias(true);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(45f);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setAntiAlias(true);
        textPaint.setShadowLayer(4f, 0f, 0f, Color.BLACK);

        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setAlpha(200);
    }

    public String setResults(List<Detection> detectionResults, int imageWidth, int imageHeight) {
        this.results = detectionResults;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;

        // Phân tích kết quả ngay tại đây
        String command = analyzePersonPosition(results);
        if (command.contains("XOAY TRÁI")) {
            callBackCommand = "TL";
        } else if (command.contains("XOAY PHẢI")) {
            callBackCommand = "TR";
        } else if (command.contains("TIẾN")) {
            callBackCommand = "FW";
        } else if (command.contains("DỪNG")) {
            callBackCommand = "ST";
        } else {
            callBackCommand = "TR";
        }

        Log.d("OverlayView", "Command: " + callBackCommand);
        invalidate(); // Vẽ lại giao diện

        return callBackCommand; // Trả về lệnh ngay sau khi set
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Log.d("OverlayView", "onDraw called with " + (results != null ? results.size() : 0) + " results");
        Log.d("OverlayView",
                "View size: " + getWidth() + "x" + getHeight() + ", Image size: " + imageWidth + "x" + imageHeight);

        // Phân tích và hiển thị lệnh điều khiển
        String command = analyzePersonPosition(results);
        if (command.contains("XOAY TRÁI")) {
            callBackCommand = "TL";
        } else if (command.contains("TIẾN")) {
            callBackCommand = "FW";
        } else if (command.contains("DỪNG")) {
            callBackCommand = "ST";
        } else {
            callBackCommand = "TR";
        }
        displayCommand(canvas, command);

        if (results == null || results.isEmpty()) {
            Log.d("OverlayView", "No results to draw, showing 'no person detected' message");
            return;
        }

        Log.d("OverlayView", "Drawing " + results.size() + " detection boxes");

        float scaleX = (float) getWidth() / imageWidth;
        float scaleY = (float) getHeight() / imageHeight;

        for (int i = 0; i < results.size(); i++) {
            Detection result = results.get(i);
            RectF box = result.getBoundingBox();

            // Chọn màu cho object này
            int colorIndex = i % colors.length;
            int currentColor = colors[colorIndex];
            boxPaint.setColor(currentColor);
            backgroundPaint.setColor(currentColor);

            // Chuyển đổi tọa độ từ ảnh gốc sang view
            float left = box.left * scaleX;
            float top = box.top * scaleY;
            float right = box.right * scaleX;
            float bottom = box.bottom * scaleY;

            // Clamp coordinates to view bounds
            left = Math.max(0, Math.min(left, getWidth()));
            top = Math.max(0, Math.min(top, getHeight()));
            right = Math.max(0, Math.min(right, getWidth()));
            bottom = Math.max(0, Math.min(bottom, getHeight()));

            Log.d("OverlayView", "Drawing box " + i + ": left=" + left + ", top=" + top + ", right=" + right
                    + ", bottom=" + bottom + ", color=" + Integer.toHexString(currentColor));

            // Vẽ bounding box với double border để rõ ràng hơn
            // Vẽ viền ngoài màu trắng trước
            boxPaint.setColor(Color.WHITE);
            boxPaint.setStrokeWidth(10f);
            canvas.drawRect(left, top, right, bottom, boxPaint);

            // Vẽ viền trong màu chính
            boxPaint.setColor(currentColor);
            boxPaint.setStrokeWidth(6f);
            canvas.drawRect(left, top, right, bottom, boxPaint);

            // Lấy thông tin label và confidence
            if (!result.getCategories().isEmpty()) {
                Category category = result.getCategories().get(0);
                String text = category.getLabel() + " " + String.format("%.1f%%", category.getScore() * 100);

                // Đo kích thước text để vẽ background
                float textWidth = textPaint.measureText(text);
                float textHeight = textPaint.getTextSize();

                // Vẽ background cho text
                float textLeft = left;
                float textTop = top - textHeight - 10;
                float textRight = textLeft + textWidth + 20;
                float textBottom = top - 5;

                // Đảm bảo text không bị cắt ở đầu màn hình
                if (textTop < 0) {
                    textTop = bottom + 5;
                    textBottom = textTop + textHeight + 10;
                }

                canvas.drawRect(textLeft, textTop, textRight, textBottom, backgroundPaint);

                // Vẽ text
                canvas.drawText(text, textLeft + 10, textBottom - 15, textPaint);
            }
        }

        // Hiển thị số lượng objects được phát hiện
        if (!results.isEmpty()) {
            String countText = "Phát hiện: " + results.size() + " đối tượng";
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(30f);
            textPaint.setTextAlign(Paint.Align.LEFT);

            float countTextWidth = textPaint.measureText(countText);
            backgroundPaint.setColor(Color.BLACK);
            backgroundPaint.setAlpha(150);

            canvas.drawRect(10, getHeight() - 50, 20 + countTextWidth, getHeight() - 10, backgroundPaint);
            canvas.drawText(countText, 15, getHeight() - 25, textPaint);
        }
    }

    private String analyzePersonPosition(List<Detection> results) {
        if (results == null || results.isEmpty()) {
            return "XOAY PHẢI - Tìm kiếm người";
        }

        Detection bestPerson = null;
        float bestConfidence = 0f;
        List<int[]> obstacles = new LinkedList<>();

        for (Detection detection : results) {
            if (detection.getCategories().isEmpty())
                continue;

            Category category = detection.getCategories().get(0);
            RectF box = detection.getBoundingBox();
            int[] bbox = {
                    (int) (box.left),
                    (int) (box.top),
                    (int) (box.right - box.left),
                    (int) (box.bottom - box.top)
            };

            if (category.getLabel().toLowerCase().equals("person")) {
                if (category.getScore() > bestConfidence) {
                    bestPerson = detection;
                    bestConfidence = category.getScore();
                }
            } else {
                obstacles.add(bbox); // vật cản
            }
        }

        if (bestPerson == null) {
            return "XOAY PHẢI - Tìm kiếm người";
        }

        RectF personBox = bestPerson.getBoundingBox();
        int[] bboxPerson = {
                (int) (personBox.left),
                (int) (personBox.top),
                (int) (personBox.right - personBox.left),
                (int) (personBox.bottom - personBox.top)
        };

        float[] control = controller.compute(bboxPerson, obstacles);
        float linear = control[0];
        float angular = control[1];

        String movement;
        if (Math.abs(linear) <= 0.2f) {
            movement = "DỪNG";
        } else if (linear > 0) {
            movement = "TIẾN";
        } else {
            movement = "LÙI";
        }

        String turn;
        if (angular < -0.1f) {
            turn = "XOAY TRÁI";
        } else if (angular > 0.1f) {
            turn = "XOAY PHẢI";
        } else {
            turn = "ĐI THẲNG";
        }

        String distance = estimateDistance(bboxPerson[2], bboxPerson[3]);

        return movement + " " + linear + " - " + turn + " " + angular + " | " + distance;
    }

    private String estimateDistance(float personWidth, float personHeight) {
        float screenWidth = getWidth();
        float screenHeight = getHeight();

        // Tính tỷ lệ người so với màn hình
        float widthRatio = personWidth / screenWidth;
        float heightRatio = personHeight / screenHeight;
        float avgRatio = (widthRatio + heightRatio) / 2;

        // Ước lượng khoảng cách dựa trên tỷ lệ
        if (avgRatio > 0.6f) {
            return "Rất gần (~0.5m)";
        } else if (avgRatio > 0.4f) {
            return "Gần (~1m)";
        } else if (avgRatio > 0.2f) {
            return "Trung bình (~2m)";
        } else if (avgRatio > 0.1f) {
            return "Xa (~3-4m)";
        } else {
            return "Rất xa (~5m+)";
        }
    }

    private void displayCommand(Canvas canvas, String command) {
        // Thiết lập paint cho command text
        Paint commandPaint = new Paint();
        commandPaint.setColor(Color.WHITE);
        commandPaint.setTextSize(50f);
        commandPaint.setStyle(Paint.Style.FILL);
        commandPaint.setAntiAlias(true);
        commandPaint.setShadowLayer(4f, 0f, 0f, Color.BLACK);
        commandPaint.setTextAlign(Paint.Align.CENTER);

        // Thiết lập background cho command
        Paint commandBgPaint = new Paint();
        commandBgPaint.setStyle(Paint.Style.FILL);
        commandBgPaint.setColor(Color.BLACK);
        commandBgPaint.setAlpha(180);

        // Vẽ command ở giữa màn hình phía trên
        float centerX = getWidth() / 2f;
        float commandY = 100f;

        // Đo kích thước text
        float textWidth = commandPaint.measureText(command);
        float textHeight = commandPaint.getTextSize();

        // Vẽ background
        float padding = 20f;
        canvas.drawRect(
                centerX - textWidth / 2 - padding,
                commandY - textHeight - padding,
                centerX + textWidth / 2 + padding,
                commandY + padding,
                commandBgPaint);

        // Vẽ text
        canvas.drawText(command, centerX, commandY - 10, commandPaint);

        Log.d("OverlayView", "Command: " + command);
    }
}