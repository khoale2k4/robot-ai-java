package com.example.my_first_app;
// import com.example.my_first_app.datas.Instruction;

import android.content.Intent;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.Toast;
import android.util.Pair;
import android.util.Log;
import android.view.*;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class InteractiveMapView extends View implements RobotCommunicationInterface.CommunicationListener {
    private static final String TAG = "InteractiveMapView";
    private final float DISTANCE_THRESHOLD = 20f; // chấp nhận sai số vị trí
    private final float ANGLE_THRESHOLD = 5f; // chấp nhận sai số góc
    private List<PointF> waypoints = new ArrayList<>(); // danh sách các điểm cần đi qua
    private int currentTargetIndex = 0;
    private int currentRouteIndex = 0;

    private List<PointF> currentPath = new ArrayList<>(); // Đường đi hiện tại đến điểm đích
    private int currentPathIndex = 0; // Vị trí hiện tại trong đường đi

    private Drawable mapDrawable;
    private Matrix matrix = new Matrix();
    private float scale = 1f;
    private float minScale = 0.5f, maxScale = 3f;
    private float dx = 0f, dy = 0f;
    private List<Pair<PointF, PointF>> routes = new ArrayList<>();

    private boolean creatingMap = true;

    private MapData2 mapData;
    private Paint robotPaint;
    private Paint directionPaint;
    private Paint routesPaint;

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private RobotCommunicationInterface robotCommunication;

    private List<PointF> tapPoints = new ArrayList<>();
    private Paint pointPaint;

    public InteractiveMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public void setMapData(MapData2 data) {
        this.mapData = data;
        invalidate();
    }

    private void setupCommunicationService() {
        try {
            robotCommunication = ConnectionManager.getInstance().getCommunicationService();

            if (robotCommunication != null) {
                robotCommunication.setCommunicationListener(this);

                if (robotCommunication.isConnected()) {
                    String deviceName = "Unknown Device";
                    try {
                        if (robotCommunication.getConnectedDevice() != null) {
                            String name = robotCommunication.getConnectedDevice().getName();
                            if (name != null && !name.isEmpty()) {
                                deviceName = name;
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error getting device name", e);
                    }

                    // if (connectionStatusText != null) {
                    // connectionStatusText.setText("Connected to: " + deviceName + " (BLE)");
                    // }

                    // showControlsView(true);
                } else {
                    // if (connectionStatusText != null) {
                    // connectionStatusText.setText("Not connected");
                    // }
                    // showControlsView(false);
                }
            } else {
                // if (connectionStatusText != null) {
                // connectionStatusText.setText("No robot service available");
                // }
                // showControlsView(false);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error setting up communication service", e);
            // if (connectionStatusText != null) {
            // connectionStatusText.setText("Error setting up communication");
            // }
        }
    }

    public void setCreating(boolean creatingMap) {
        this.creatingMap = creatingMap;
        if (!creatingMap) {
            startAutonomous();
        }
    }

    private void init(Context context) {
        mapDrawable = ContextCompat.getDrawable(context, R.drawable.map_background);

        robotPaint = new Paint();
        robotPaint.setColor(Color.BLUE);
        robotPaint.setStyle(Paint.Style.FILL);
        robotPaint.setAntiAlias(true);

        routesPaint = new Paint();
        routesPaint.setColor(Color.YELLOW);
        routesPaint.setStrokeWidth(4);
        routesPaint.setAntiAlias(true);

        directionPaint = new Paint();
        directionPaint.setColor(Color.YELLOW);
        directionPaint.setStrokeWidth(4);
        directionPaint.setAntiAlias(true);

        setupCommunicationService();

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scale *= detector.getScaleFactor();
                scale = Math.max(minScale, Math.min(scale, maxScale));
                invalidate();
                return true;
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distX, float distY) {
                dx -= distX;
                dy -= distY;
                invalidate();
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                float x = (e.getX() - dx) / scale;
                float y = (e.getY() - dy) / scale;
                PointF tapPoint = new PointF(x, y);

                boolean isNearRoute = false;
                float threshold = 20f;

                for (Pair<PointF, PointF> segment : routes) {
                    PointF start = segment.first;
                    PointF end = segment.second;

                    if (distanceFromPointToSegment(tapPoint, start, end) <= threshold) {
                        isNearRoute = true;
                        break;
                    }
                }

                if (isNearRoute) {
                    tapPoints.add(tapPoint);
                    invalidate();
                    return true;
                } else {
                    return false;
                }
            }
        });

        pointPaint = new Paint();
        pointPaint.setColor(Color.RED);
        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setAntiAlias(true);
    }

    private float distanceFromPointToSegment(PointF p, PointF a, PointF b) {
        float dx = b.x - a.x;
        float dy = b.y - a.y;

        if (dx == 0 && dy == 0) {
            // a và b trùng nhau
            return distance(p, a);
        }

        float t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));

        float projX = a.x + t * dx;
        float projY = a.y + t * dy;

        return distance(p, new PointF(projX, projY));
    }

    private float distance(PointF p1, PointF p2) {
        float dx = p1.x - p2.x;
        float dy = p1.y - p2.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mapDrawable == null)
            return;

        canvas.save();
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);

        int imgWidth = mapDrawable.getIntrinsicWidth();
        int imgHeight = mapDrawable.getIntrinsicHeight();

        int viewWidth = (int) (getWidth() / scale) + imgWidth;
        int viewHeight = (int) (getHeight() / scale) + imgHeight;

        // Vẽ bản đồ vô tận
        for (int x = -imgWidth; x < viewWidth; x += imgWidth) {
            for (int y = -imgHeight; y < viewHeight; y += imgHeight) {
                mapDrawable.setBounds(x, y, x + imgWidth, y + imgHeight);
                mapDrawable.draw(canvas);
            }
        }

        // 🔻 Nếu có robot, xoá các điểm gần robot
        if (mapData != null && mapData.robot != null) {
            PointF robot = mapData.robot;
            float threshold = 20f;
            for (int i = 0; i < tapPoints.size(); i++) {
                PointF point = tapPoints.get(i);
                float dx = robot.x - point.x;
                float dy = robot.y - point.y;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                if (distance <= threshold) {
                    tapPoints.remove(i);
                    i--; // điều chỉnh index sau khi remove
                }
            }
        }

        // Vẽ các điểm tap
        for (PointF point : tapPoints) {
            canvas.drawCircle(point.x, point.y, 10 / scale, pointPaint);
        }

        // Vẽ robot + hướng
        if (mapData != null && mapData.robot != null) {
            PointF robot = mapData.robot;
            canvas.drawCircle(robot.x, robot.y, 20 / scale, robotPaint);

            float length = 40;
            float angleRad = (float) Math.toRadians(mapData.robotAngle);
            float dxArrow = (float) (length * Math.cos(angleRad));
            float dyArrow = (float) (length * Math.sin(angleRad));
            canvas.drawLine(robot.x, robot.y, robot.x + dxArrow, robot.y + dyArrow, directionPaint);
        }

        // Vẽ các route (đường đi của robot)
        for (Pair<PointF, PointF> segment : routes) {
            PointF start = segment.first;
            PointF end = segment.second;
            canvas.drawLine(start.x, start.y, end.x, end.y, routesPaint);
        }

        canvas.restore();
    }

    private void drawRobot(Canvas canvas, PointF pos, float angleDegrees) {
        // Xoá điểm gần robot (nếu có)
        float threshold = 20f;
        for (int i = 0; i < tapPoints.size(); i++) {
            PointF point = tapPoints.get(i);
            float dx = pos.x - point.x;
            float dy = pos.y - point.y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance <= threshold) {
                tapPoints.remove(i);
                i--; // Điều chỉnh chỉ số vì danh sách bị thay đổi
            }
        }

        // Vẽ robot (tam giác chỉ hướng)
        Paint robotPaint = new Paint();
        robotPaint.setColor(Color.BLUE);
        robotPaint.setStyle(Paint.Style.FILL);
        robotPaint.setAntiAlias(true);

        float size = 30f;
        float angleRad = (float) Math.toRadians(angleDegrees);
        float x = pos.x;
        float y = pos.y;

        PointF p1 = new PointF(x + (float) Math.cos(angleRad) * size, y + (float) Math.sin(angleRad) * size);
        PointF p2 = new PointF(x + (float) Math.cos(angleRad + 2.5) * size * 0.6f,
                y + (float) Math.sin(angleRad + 2.5) * size * 0.6f);
        PointF p3 = new PointF(x + (float) Math.cos(angleRad - 2.5) * size * 0.6f,
                y + (float) Math.sin(angleRad - 2.5) * size * 0.6f);

        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(p1.x, p1.y);
        path.lineTo(p2.x, p2.y);
        path.lineTo(p3.x, p3.y);
        path.close();

        canvas.drawPath(path, robotPaint);
    }

    public void updateRobot(float distance, float angleDeltaDegrees) {
        // Cập nhật góc quay mới
        mapData.robotAngle += angleDeltaDegrees;

        // Đảm bảo góc trong khoảng [0, 360)
        if (mapData.robotAngle < 0)
            mapData.robotAngle += 360;
        if (mapData.robotAngle >= 360)
            mapData.robotAngle -= 360;

        // Tính toán vị trí mới dựa trên góc hiện tại
        float angleRad = (float) Math.toRadians(mapData.robotAngle);
        float dx = (float) (distance * Math.cos(angleRad));
        float dy = (float) (distance * Math.sin(angleRad));

        // Cập nhật vị trí robot
        mapData.robot.x += dx;
        mapData.robot.y += dy;

        // Vẽ lại bản đồ
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        return true;
    }

    private void updateRobotAngle(float angle) {
        if (mapData == null)
            return;
        mapData.robotAngle = (angle + 360) % 360;
        setMapData(mapData);
    }

    public void moveForward(float distance) {
        if (mapData == null || mapData.robot == null)
            return;

        float angleDegrees = mapData.robotAngle;
        float angleRad = (float) Math.toRadians(angleDegrees);

        PointF oldPos = new PointF(mapData.robot.x, mapData.robot.y);

        float dx = (float) (distance * Math.cos(angleRad));
        float dy = (float) (distance * Math.sin(angleRad));

        mapData.robot.x += dx;
        mapData.robot.y += dy;

        if (creatingMap)
            routes.add(new Pair<>(oldPos, new PointF(mapData.robot.x, mapData.robot.y)));

        setMapData(mapData);
    }

    public void resetView() {
        dx = 0;
        dy = 0;
        scale = 1.0f;
        routes.clear();
        tapPoints.clear();
        invalidate();
    }

    private void sendRobotCommand(String command) {
        try {
            Log.d(TAG, "Sending " + command);

            if (robotCommunication != null && robotCommunication.isConnected()) {
                robotCommunication.sendRobotCommand(command);
            } else {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Robot not connected", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending robot command: " + command, e);
        }
    }

    public void startAutonomous() {
        if (tapPoints.isEmpty() || mapData == null || mapData.robot == null)
            return;

        waypoints.clear();
        waypoints.addAll(tapPoints);
        currentTargetIndex = 0;

        sendNextCommand();
    }

    private void sendNextCommand() {
        if (currentRouteIndex >= routes.size()) {
            creatingMap = true;
            sendRobotCommand(Instruction.STOP);
            return;
        }

        Pair<PointF, PointF> currentSegment = routes.get(currentRouteIndex);
        PointF target = currentSegment.second; // luôn đi đến điểm thứ hai của đoạn

        PointF robot = mapData.robot;
        float currentAngle = mapData.robotAngle;

        float dx = target.x - robot.x;
        float dy = target.y - robot.y;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        if (distance < DISTANCE_THRESHOLD) {
            currentRouteIndex++; // Đến cuối đoạn → sang đoạn tiếp theo
            sendNextCommand();
            return;
        }

        float desiredAngle = (float) Math.toDegrees(Math.atan2(dy, dx));
        float angleDiff = normalizeAngle(desiredAngle - currentAngle);

        if (Math.abs(angleDiff) > ANGLE_THRESHOLD) {
            if (angleDiff > 0) {
                sendRobotCommand(Instruction.TURN_RIGHT);
            } else {
                sendRobotCommand(Instruction.TURN_LEFT);
            }
        } else {
            sendRobotCommand(Instruction.FORWARD);
        }
    }

    private float normalizeAngle(float angle) {
        angle = ((angle + 180) % 360) - 180;
        if (angle < -180)
            angle += 360;
        return angle;
    }

    @Override
    public void onDataReceived(String data) {
        try {
            Context context = getContext();
            if (context instanceof android.app.Activity && !((android.app.Activity) context).isFinishing()) {
                ((android.app.Activity) context).runOnUiThread(() -> {
                    // Log.d(TAG, "Data received: " + data);
                    final String receivedData = data.trim();
                    if (receivedData.startsWith("D ")) {
                        String[] parts = receivedData.split(" ");
                        if (parts.length >= 2) {
                            try {
                                float d = Float.parseFloat(parts[1]);
                                moveForward(d);
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "Invalid destination format: " + receivedData, e);
                            }
                        }
                    } else if (receivedData.startsWith("A ")) {
                        String[] parts = receivedData.split(" ");
                        if (parts.length >= 2) {
                            try {
                                float angle = Float.parseFloat(parts[1]);
                                updateRobotAngle(angle);
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "Invalid angle format: " + receivedData, e);
                            }
                        }
                    } else {
                        Log.w(TAG, "Unknown command received: " + receivedData);
                    }

                    if (!creatingMap) {
                        post(() -> sendNextCommand());
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDataReceived", e);
        }
    }

    @Override
    public void onDataSent(String data) {
        Log.d(TAG, "Data sent: " + data);
        // Có thể xử lý log hoặc cập nhật UI
    }

    @Override
    public void onConnectionLost() {
        Log.w(TAG, "Connection lost");

        // Thông báo người dùng nếu cần
        if (getContext() != null) {
            Toast.makeText(getContext(), "Kết nối đến robot đã mất", Toast.LENGTH_SHORT).show();
        }

        // Có thể thực hiện thêm hành động như ẩn điều khiển
    }

    @Override
    public void onCommunicationError(String error) {
        Log.e(TAG, "Communication error: " + error);

        if (getContext() != null) {
            Toast.makeText(getContext(), "Lỗi giao tiếp: " + error, Toast.LENGTH_SHORT).show();
        }
    }

}
