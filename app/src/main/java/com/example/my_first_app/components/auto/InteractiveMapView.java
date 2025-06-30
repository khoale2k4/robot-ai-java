package com.example.my_first_app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class InteractiveMapView extends View implements RobotCommunicationInterface.CommunicationListener {
    private static final String TAG = "InteractiveMapView";
    private final float DISTANCE_THRESHOLD = 20f; // Chấp nhận sai số vị trí để đến waypoint tiếp theo
    private final float ANGLE_THRESHOLD = 10f; // Chấp nhận sai số góc để đi thẳng
    private final float CLOCK = 0.2f;

    private List<PointF> obstacles = new ArrayList<>();
    private Paint obstaclePaint;
    private final float OBSTACLE_RADIUS = 15f;
    private final float DETOUR_SAFETY_MARGIN = 40f;

    // === CÁC BIẾN MỚI CHO VIỆC TÌM ĐƯỜNG VÀ ĐIỀU HƯỚNG ===
    private List<PointF> currentPath = new ArrayList<>(); // Đường đi được tính toán đến điểm đích
    private int currentPathIndex = 0; // Vị trí waypoint hiện tại trong currentPath

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
    private Paint pathPaint; // Paint để vẽ đường đi đã tính toán

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

    public void setCreating(boolean creatingMap) {
        this.creatingMap = creatingMap;
        if (!creatingMap) {
            // Không tự động bắt đầu, đợi người dùng tap điểm đến
        } else {
            // Xóa đường đi cũ khi vào chế độ tạo bản đồ
            currentPath.clear();
            tapPoints.clear();
            invalidate();
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

        // Paint mới để vẽ đường đi được tính toán
        pathPaint = new Paint();
        pathPaint.setColor(Color.CYAN); // Màu xanh lam để phân biệt
        pathPaint.setStrokeWidth(6);
        pathPaint.setStyle(Paint.Style.STROKE);
        pathPaint.setAntiAlias(true);

        directionPaint = new Paint();
        directionPaint.setColor(Color.YELLOW);
        directionPaint.setStrokeWidth(4);
        directionPaint.setAntiAlias(true);

        obstaclePaint = new Paint();
        obstaclePaint.setColor(Color.argb(150, 255, 100, 100));
        obstaclePaint.setStyle(Paint.Style.FILL);

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
                if (creatingMap) {
                    Toast.makeText(getContext(), "Switch to Autonomous Mode to set a destination.", Toast.LENGTH_SHORT)
                            .show();
                    return false;
                }

                float x = (e.getX() - dx) / scale;
                float y = (e.getY() - dy) / scale;
                PointF tapPoint = new PointF(x, y);

                boolean isNearRoute = false;
                float threshold = 20f / scale; // Ngưỡng tap phụ thuộc vào mức zoom

                for (Pair<PointF, PointF> segment : routes) {
                    if (distanceFromPointToSegment(tapPoint, segment.first, segment.second) <= threshold) {
                        isNearRoute = true;
                        break;
                    }
                }

                if (isNearRoute) {
                    tapPoints.clear();
                    tapPoints.add(tapPoint); // Chỉ giữ lại điểm tap cuối cùng
                    startAutonomous(); // Bắt đầu tìm đường và di chuyển
                    invalidate();
                    return true;
                } else {
                    Toast.makeText(getContext(), "Please tap near an existing route.", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        pointPaint = new Paint();
        pointPaint.setColor(Color.RED);
        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setAntiAlias(true);
    }

    // Các hàm tiện ích distance và distanceFromPointToSegment giữ nguyên
    private float distanceFromPointToSegment(PointF p, PointF a, PointF b) {
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        if (dx == 0 && dy == 0)
            return distance(p, a);
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

        // Vẽ map lặp lại
        int imgWidth = mapDrawable.getIntrinsicWidth();
        int imgHeight = mapDrawable.getIntrinsicHeight();
        int viewWidth = (int) (getWidth() / scale) + imgWidth;
        int viewHeight = (int) (getHeight() / scale) + imgHeight;
        for (int x = -imgWidth; x < viewWidth; x += imgWidth) {
            for (int y = -imgHeight; y < viewHeight; y += imgHeight) {
                mapDrawable.setBounds(x, y, x + imgWidth, y + imgHeight);
                mapDrawable.draw(canvas);
            }
        }

        // Vẽ các route (đường đi gốc của robot)
        for (Pair<PointF, PointF> segment : routes) {
            canvas.drawLine(segment.first.x, segment.first.y, segment.second.x, segment.second.y, routesPaint);
        }

        // Vẽ đường đi đã được tính toán (currentPath)
        if (!currentPath.isEmpty()) {
            for (int i = 0; i < currentPath.size() - 1; i++) {
                PointF start = currentPath.get(i);
                PointF end = currentPath.get(i + 1);
                canvas.drawLine(start.x, start.y, end.x, end.y, pathPaint);
            }
        }

        // Vẽ điểm tap (điểm đích)
        for (PointF point : tapPoints) {
            canvas.drawCircle(point.x, point.y, 10 / scale, pointPaint);
        }

        for (PointF obstacle : obstacles) {
            canvas.drawCircle(obstacle.x, obstacle.y, OBSTACLE_RADIUS / scale, obstaclePaint);
        }

        // Vẽ robot + hướng
        if (mapData != null && mapData.robot != null) {
            PointF robot = mapData.robot;
            canvas.drawCircle(robot.x, robot.y, 20 / scale, robotPaint);
            float length = 40 / scale;
            float angleRad = (float) Math.toRadians(mapData.robotAngle);
            float dxArrow = (float) (length * Math.cos(angleRad));
            float dyArrow = (float) (length * Math.sin(angleRad));
            canvas.drawLine(robot.x, robot.y, robot.x + dxArrow, robot.y + dyArrow, directionPaint);
        }

        canvas.restore();
    }

    // ================================================================//
    // HÀM TÌM ĐƯỜNG MỚI (PATHFINDING)
    // ================================================================//

    /**
     * Tìm điểm trên mạng lưới routes gần nhất với một điểm cho trước.
     */
    private PointF findClosestPointOnRoutes(PointF point, List<Pair<PointF, PointF>> availableRoutes) {
        if (routes.isEmpty())
            return null;

        PointF closestPoint = null;
        float minDistance = Float.MAX_VALUE;

        for (Pair<PointF, PointF> segment : routes) {
            PointF start = segment.first;
            PointF end = segment.second;

            float distToStart = distance(point, start);
            float distToEnd = distance(point, end);

            if (distToStart < minDistance) {
                minDistance = distToStart;
                closestPoint = start;
            }
            if (distToEnd < minDistance) {
                minDistance = distToEnd;
                closestPoint = end;
            }
        }
        return closestPoint;
    }

    public void replanPath() {
        if (tapPoints.isEmpty() || mapData == null || mapData.robot == null) {
            Log.w(TAG, "Cannot replan: no destination or robot data.");
            return;
        }

        PointF destination = tapPoints.get(0);
        PointF robotPosition = mapData.robot;

        Log.d(TAG, "Re-planning path from " + robotPosition + " to " + destination + " avoiding " + obstacles.size()
                + " obstacles.");

        // 1. Lấy danh sách các đường đi hợp lệ (không bị chặn)
        List<Pair<PointF, PointF>> validRoutes = getValidRoutes();

        // 2. Tìm đường đi mới trên các đường đi hợp lệ đó
        List<PointF> newPath = findPathOnRoutes(robotPosition, destination, validRoutes);

        if (newPath != null && !newPath.isEmpty()) {
            currentPath = newPath;
            currentPathIndex = 0; // Bắt đầu lại từ đầu đường đi mới
            Log.d(TAG, "New path found with " + currentPath.size() + " waypoints. Resuming navigation.");
            invalidate(); // Vẽ lại đường đi mới

            // Gửi lệnh tiếp theo để tiếp tục di chuyển theo đường mới
            post(this::sendNextCommand);
        } else {
            Log.e(TAG, "No alternative path found to the destination!");
            Toast.makeText(getContext(), "Path blocked! Cannot find alternative route.", Toast.LENGTH_LONG).show();
            currentPath.clear();
            invalidate();
        }
    }

    /**
     * Thuật toán BFS để tìm đường đi trên đồ thị routes.
     */
    private List<PointF> findPathOnRoutes(PointF startPos, PointF endPos, List<Pair<PointF, PointF>> availableRoutes) {
        if (routes.isEmpty())
            return null;

        // 1. Tìm điểm bắt đầu và kết thúc trên đồ thị
        PointF startNode = findClosestPointOnRoutes(startPos, availableRoutes);
        PointF endNode = findClosestPointOnRoutes(endPos, availableRoutes);

        if (startNode == null || endNode == null)
            return null;

        // 2. Xây dựng đồ thị (adjacency list)
        Map<PointF, List<PointF>> adjMap = new HashMap<>();
        Set<PointF> nodes = new HashSet<>();
        for (Pair<PointF, PointF> segment : availableRoutes) {
            nodes.add(segment.first);
            nodes.add(segment.second);
            adjMap.computeIfAbsent(segment.first, k -> new ArrayList<>()).add(segment.second);
            adjMap.computeIfAbsent(segment.second, k -> new ArrayList<>()).add(segment.first);
        }

        // 3. Chạy BFS
        Queue<PointF> queue = new ArrayDeque<>();
        Map<PointF, PointF> cameFrom = new HashMap<>(); // Để truy vết đường đi

        queue.add(startNode);
        cameFrom.put(startNode, null);

        PointF current = null;
        while (!queue.isEmpty()) {
            current = queue.poll();
            if (current.equals(endNode)) {
                break; // Tìm thấy đích
            }

            if (adjMap.containsKey(current)) {
                for (PointF neighbor : adjMap.get(current)) {
                    if (!cameFrom.containsKey(neighbor)) {
                        cameFrom.put(neighbor, current);
                        queue.add(neighbor);
                    }
                }
            }
        }

        // 4. Truy vết ngược để tạo đường đi
        if (current == null || !current.equals(endNode)) {
            return null; // Không tìm thấy đường đi
        }

        List<PointF> path = new ArrayList<>();
        PointF temp = endNode;
        while (temp != null) {
            path.add(temp);
            temp = cameFrom.get(temp);
        }
        Collections.reverse(path);

        // Nếu điểm đầu tiên của path không phải là vị trí robot, thêm vị trí robot vào
        // đầu
        if (!path.isEmpty() && distance(path.get(0), startPos) > 1f) {
            path.add(0, startPos);
        }

        return path;
    }

    private List<Pair<PointF, PointF>> getValidRoutes() {
        if (obstacles.isEmpty()) {
            return new ArrayList<>(routes); // Nếu không có vật cản, trả về tất cả các đường
        }

        List<Pair<PointF, PointF>> validRoutes = new ArrayList<>();
        for (Pair<PointF, PointF> segment : routes) {
            boolean isBlocked = false;
            for (PointF obstacle : obstacles) {
                // Kiểm tra xem khoảng cách từ vật cản đến đoạn đường có nhỏ hơn bán kính ảo
                // không
                if (distanceFromPointToSegment(obstacle, segment.first, segment.second) < OBSTACLE_RADIUS) {
                    isBlocked = true;
                    Log.d(TAG, "Route segment " + segment.first + " -> " + segment.second
                            + " is blocked by obstacle at " + obstacle);
                    break; // Đoạn đường này bị chặn, không cần kiểm tra các vật cản khác
                }
            }
            if (!isBlocked) {
                validRoutes.add(segment);
            }
        }
        return validRoutes;
    }

    // ================================================================//
    // LOGIC ĐIỀU KHIỂN ROBOT ĐÃ ĐƯỢC CẬP NHẬT
    // ================================================================//

    /**
     * Bắt đầu chế độ tự hành. Tìm đường và khởi động chuỗi lệnh.
     */
    public void startAutonomous() {
        if (tapPoints.isEmpty() || mapData == null || mapData.robot == null) {
            Log.w(TAG, "Cannot start autonomous: no destination or robot data.");
            return;
        }

        // PointF destination = tapPoints.get(0); // Lấy điểm đích đã tap
        // Log.d(TAG, "Finding path from " + mapData.robot + " to " + destination);

        // // Tìm đường đi mới
        // List<PointF> foundPath = findPathOnRoutes(mapData.robot, destination);

        PointF destination = tapPoints.get(0);
        Log.d(TAG, "Finding path from " + mapData.robot + " to " + destination);

        // Lấy danh sách đường đi hợp lệ (có thể có vật cản đã tồn tại từ trước)
        List<Pair<PointF, PointF>> validRoutes = getValidRoutes();
        List<PointF> foundPath = findPathOnRoutes(mapData.robot, destination, validRoutes);

        if (foundPath != null && !foundPath.isEmpty()) {
            currentPath = foundPath;
            currentPathIndex = 0; // Bắt đầu từ waypoint đầu tiên
            Log.d(TAG, "Path found with " + currentPath.size() + " waypoints. Starting navigation.");
            sendNextCommand(); // Gửi lệnh đầu tiên
        } else {
            Log.e(TAG, "No path found to the destination.");
            Toast.makeText(getContext(), "No path found!", Toast.LENGTH_SHORT).show();
            currentPath.clear();
        }
        invalidate(); // Vẽ lại để hiển thị đường đi mới
    }

    /**
     * Gửi lệnh tiếp theo (xoay hoặc đi thẳng) để đến waypoint kế tiếp.
     */
    private void sendNextCommand() {
        // Kiểm tra điều kiện dừng
        if (currentPath.isEmpty() || currentPathIndex >= currentPath.size() || mapData == null
                || mapData.robot == null) {
            Log.d(TAG, "Navigation finished or aborted.");
            sendRobotCommand(Instruction.STOP); // Gửi lệnh dừng
            currentPath.clear();
            tapPoints.clear();
            invalidate();
            return;
        }

        PointF target = currentPath.get(currentPathIndex);
        PointF robot = mapData.robot;
        float currentAngle = mapData.robotAngle;

        float dx = target.x - robot.x;
        float dy = target.y - robot.y;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        // 1. Kiểm tra xem đã đến waypoint chưa
        if (distance < DISTANCE_THRESHOLD) {
            Log.d(TAG, "Reached waypoint " + currentPathIndex + ": " + target);
            currentPathIndex++; // Chuyển sang waypoint tiếp theo
            if (currentPathIndex >= currentPath.size()) {
                Log.d(TAG, "Destination reached!");
                sendRobotCommand(Instruction.STOP); // Dừng khi đến đích cuối cùng
                currentPath.clear();
                tapPoints.clear();
                invalidate();
                return;
            }
            // Gọi lại để xử lý cho waypoint tiếp theo ngay lập tức
            sendNextCommand();
            return;
        }

        // 2. Nếu chưa đến, tính toán góc và gửi lệnh
        float desiredAngle = (float) Math.toDegrees(Math.atan2(dy, dx));
        float angleDiff = normalizeAngle(desiredAngle - currentAngle);

        if (Math.abs(angleDiff) > ANGLE_THRESHOLD) {
            // Cần xoay
            if (angleDiff > 0) {
                Log.d(TAG, "Turning RIGHT. Diff: " + angleDiff);
                sendRobotCommand(Instruction.TURN_RIGHT); // Instruction.TURN_RIGHT
            } else {
                Log.d(TAG, "Turning LEFT. Diff: " + angleDiff);
                sendRobotCommand(Instruction.TURN_LEFT); // Instruction.TURN_LEFT
            }
        } else {
            // Đã đúng hướng, đi thẳng
            Log.d(TAG, "Moving FORWARD. Distance: " + distance);
            sendRobotCommand(Instruction.FORWARD); // Instruction.FORWARD
        }
    }

    private float normalizeAngle(float angle) {
        angle %= 360;
        if (angle > 180) {
            angle -= 360;
        } else if (angle < -180) {
            angle += 360;
        }
        return angle;
    }

    @Override
    public void onDataReceived(String data) {
        try {
            Context context = getContext();
            if (context instanceof android.app.Activity && !((android.app.Activity) context).isFinishing()) {
                ((android.app.Activity) context).runOnUiThread(() -> {
                    final String receivedData = data.trim();
                    // Giả sử robot gửi lại "D <distance>" sau khi di chuyển và "A <angle>" sau khi
                    // xoay
                    if (receivedData.startsWith("D ")) {
                        String[] parts = receivedData.split(" ");
                        if (parts.length >= 2) {
                            try {
                                float d = Float.parseFloat(parts[1]);
                                moveForward(d);
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "Invalid distance format: " + receivedData, e);
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
                    } else if (receivedData.startsWith("V ")) {
                        String[] parts = receivedData.split(" ");
                        if (parts.length >= 2) {
                            try {
                                float speed = Float.parseFloat(parts[1]); // cm/s
                                moveForward(speed * CLOCK);
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "Invalid angle format: " + receivedData, e);
                            }
                        }
                    } else if (receivedData.startsWith("O ")) {
                        String[] parts = receivedData.split(" ");
                        if (parts.length >= 2) {
                            try {
                                float distance = Float.parseFloat(parts[1]);
                                sendRobotCommand(Instruction.STOP);
                                addObstacleInFront(distance);
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "Invalid angle format: " + receivedData, e);
                            }
                        }
                    } else {
                        Log.w(TAG, "Unknown command received: " + receivedData);
                    }

                    // Sau khi nhận được phản hồi và cập nhật trạng thái robot, gửi lệnh tiếp theo
                    if (!creatingMap) {
                        // Dùng post để đảm bảo việc vẽ lại hoàn tất trước khi gửi lệnh mới
                        post(this::sendNextCommand);
                    }
                });
            }
        } catch (

        Exception e) {
            Log.e(TAG, "Error in onDataReceived", e);
        }
    }

    // =================================================== //
    // CÁC HÀM KHÁC (GIỮ NGUYÊN)
    // =================================================== //
    private void setupCommunicationService() {
        try {
            robotCommunication = ConnectionManager.getInstance().getCommunicationService();
            if (robotCommunication != null) {
                robotCommunication.setCommunicationListener(this);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up communication service", e);
        }
    }

    public void updateRobot(float distance, float angleDeltaDegrees) {
        mapData.robotAngle += angleDeltaDegrees;
        mapData.robotAngle = (mapData.robotAngle + 360) % 360;

        float angleRad = (float) Math.toRadians(mapData.robotAngle);
        float dx = (float) (distance * Math.cos(angleRad));
        float dy = (float) (distance * Math.sin(angleRad));
        mapData.robot.x += dx;
        mapData.robot.y += dy;
        invalidate();
    }

    public void addObstacleInFront(float distance) {
        if (mapData == null || mapData.robot == null) {
            return;
        }

        float angleRad = (float) Math.toRadians(mapData.robotAngle);
        PointF oldPos = new PointF(mapData.robot.x, mapData.robot.y);
        float dx = (float) (distance * Math.cos(angleRad));
        float dy = (float) (distance * Math.sin(angleRad));
        PointF newObstacle = new PointF(mapData.robot.x + dx, mapData.robot.y + dy);
        obstacles.add(newObstacle);
        Log.d(TAG, "Added obstacle at: " + newObstacle.toString());
        invalidate();
        // handleObstacle(newObstacle);
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
        float angleRad = (float) Math.toRadians(mapData.robotAngle);
        PointF oldPos = new PointF(mapData.robot.x, mapData.robot.y);
        float dx = (float) (distance * Math.cos(angleRad));
        float dy = (float) (distance * Math.sin(angleRad));
        mapData.robot.x += dx;
        mapData.robot.y += dy;

        if (creatingMap) {
            routes.add(new Pair<>(oldPos, new PointF(mapData.robot.x, mapData.robot.y)));
        }
        setMapData(mapData);
    }

    public void resetView() {
        dx = 0;
        dy = 0;
        scale = 1.0f;
        routes.clear();
        tapPoints.clear();
        currentPath.clear();
        obstacles.clear();
        invalidate();
    }

    private void sendRobotCommand(String command) {
        try {
            if (robotCommunication != null && robotCommunication.isConnected()) {
                robotCommunication.sendRobotCommand(command, true);
            } else {
                Toast.makeText(getContext(), "Robot not connected", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending robot command: " + command, e);
        }
    }

    @Override
    public void onDataSent(String data) {
        Log.d(TAG, "Data sent: " + data);
    }

    @Override
    public void onConnectionLost() {
        Log.w(TAG, "Connection lost");
        Toast.makeText(getContext(), "Connection to robot lost", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onCommunicationError(String error) {
        Log.e(TAG, "Communication error: " + error);
        Toast.makeText(getContext(), "Communication Error: " + error, Toast.LENGTH_SHORT).show();
    }
}