package com.example.my_first_app;

import android.util.Log;
import android.graphics.PointF;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Set;

public class MapData {

    // --- CÁC TRƯỜNG DỮ LIỆU GỐC ---
    public List<PointF> obstacles;
    public PointF robot;
    public float robotAngle; // Góc quay của robot (độ)
    public PointF destination;
    public List<Wall> walls;

    // --- CÁC HẰNG SỐ CẤU HÌNH CHO THUẬT TOÁN ---

    /**
     * Kích thước mỗi ô trong lưới (đơn vị: cm). Càng nhỏ càng chính xác nhưng xử lý
     * càng lâu.
     */
    private static final float CELL_SIZE = 10.0f;
    /** Bán kính an toàn của robot (đơn vị: cm), dùng để kiểm tra va chạm. */
    private static final float ROBOT_RADIUS = 15.0f;
    /**
     * Ngưỡng sai số góc (độ). Nếu góc lệch nhỏ hơn giá trị này, coi như đã quay
     * đúng hướng.
     */
    private static final float ANGLE_TOLERANCE_DEGREES = 5.0f;
    /**
     * Ngưỡng sai số khoảng cách (cm). Nếu gần hơn giá trị này, coi như đã đến điểm
     * trên đường đi.
     */
    private static final float DISTANCE_TOLERANCE_CM = 2.0f;
    /**
     * Kích thước tối đa của bản đồ (cm), dùng để tạo lưới. Bạn nên chỉnh lại cho
     * phù hợp.
     */
    private static final float MAP_MAX_DIMENSION = 1000.0f;

    // --- CÁC PHƯƠNG THỨC PUBLIC (API ĐỂ SỬ DỤNG) ---

    /**
     * Tìm đường đi ngắn nhất từ vị trí robot hiện tại đến đích.
     * Sử dụng thuật toán A* trên lưới và làm mượt kết quả.
     * 
     * @return Một danh sách các điểm (List<PointF>) tạo thành đường đi đã được làm
     *         mượt.
     *         Trả về null nếu không tìm thấy đường đi.
     */
    public List<PointF> findPathToDestination() {
        if (this.robot == null || this.destination == null) {
            return null;
        }

        Node[][] grid = createGridFromMap();
        if (grid == null)
            return null;

        List<Node> pathNodes = aStarSearch(grid);
        if (pathNodes == null) {
            return null; // Không tìm thấy đường đi
        }

        List<PointF> pathPoints = new ArrayList<>();
        for (Node node : pathNodes) {
            pathPoints.add(new PointF(node.x * CELL_SIZE, node.y * CELL_SIZE));
        }

        return smoothPath(pathPoints);
    }

    public String getNextCommand(List<PointF> path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        PointF nextWaypoint = path.get(0);
        float distanceToWaypoint = getDistance(this.robot, nextWaypoint);

        // Kiểm tra đã đến điểm hiện tại chưa
        if (distanceToWaypoint < DISTANCE_TOLERANCE_CM) {
            path.remove(0);
            return path.isEmpty() ? null : getNextCommand(path); // Đệ quy cho điểm tiếp theo
        }

        // Tính góc mục tiêu và chuẩn hóa
        float targetAngle = (float) Math.toDegrees(Math.atan2(
                nextWaypoint.y - this.robot.y,
                nextWaypoint.x - this.robot.x));
        targetAngle = (targetAngle + 360) % 360;

        // Tính góc quay tối ưu nhất
        float angleToRotate = targetAngle - this.robotAngle;
        angleToRotate = (angleToRotate + 180) % 360 - 180; // Chuẩn hóa về [-180,180]

        // Kiểm tra nên lệnh xoay
        if (Math.abs(angleToRotate) > ANGLE_TOLERANCE_DEGREES) {
            String direction = angleToRotate > 0 ? "LEFT" : "RIGHT";
            return String.format(Locale.US, "ROTATE %s %.1f degree",
                    direction, Math.abs(angleToRotate));
        }

        // Lệnh di chuyển thẳng
        return String.format(Locale.US, "ADVANCE %.1f cm", distanceToWaypoint);
    }

    // --- LOGIC TÌM ĐƯỜNG A* (PRIVATE) ---

    private Node[][] createGridFromMap() {
        int gridWidth = (int) (MAP_MAX_DIMENSION / CELL_SIZE);
        int gridHeight = (int) (MAP_MAX_DIMENSION / CELL_SIZE);
        Node[][] grid = new Node[gridWidth][gridHeight];

        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                PointF worldPoint = new PointF(x * CELL_SIZE, y * CELL_SIZE);
                if (isWalkable(worldPoint)) {
                    grid[x][y] = new Node(x, y);
                }
            }
        }
        return grid;
    }

    private boolean isWalkable(PointF point) {
        // Check obstacles
        if (this.obstacles != null) {
            for (PointF obstacle : this.obstacles) {
                if (getDistance(point, obstacle) < ROBOT_RADIUS) {
                    return false;
                }
            }
        }

        // Check walls
        if (this.walls != null) {
            for (Wall wall : this.walls) {
                if (distanceToWall(point, wall) < ROBOT_RADIUS) {
                    return false;
                }
            }
        }
        return true;
    }

    private float distanceToWall(PointF point, Wall wall) {
        // Calculate minimum distance between point and wall segment
        // Using vector projection math
        float x1 = wall.start.x;
        float y1 = wall.start.y;
        float x2 = wall.end.x;
        float y2 = wall.end.y;
        float x0 = point.x;
        float y0 = point.y;

        float A = x0 - x1;
        float B = y0 - y1;
        float C = x2 - x1;
        float D = y2 - y1;

        float dot = A * C + B * D;
        float len_sq = C * C + D * D;
        float param = (len_sq != 0) ? dot / len_sq : -1;

        float xx, yy;

        if (param < 0) {
            xx = x1;
            yy = y1;
        } else if (param > 1) {
            xx = x2;
            yy = y2;
        } else {
            xx = x1 + param * C;
            yy = y1 + param * D;
        }

        return (float) Math.sqrt((x0 - xx) * (x0 - xx) + (y0 - yy) * (y0 - yy));
    }

    private List<Node> aStarSearch(Node[][] grid) {
        PointF startPos = this.robot;
        PointF destPos = this.destination;
        Node startNode = grid[(int) (startPos.x / CELL_SIZE)][(int) (startPos.y / CELL_SIZE)];
        Node destNode = grid[(int) (destPos.x / CELL_SIZE)][(int) (destPos.y / CELL_SIZE)];

        if (startNode == null || destNode == null)
            return null; // Vị trí bắt đầu/kết thúc bị chặn

        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Set<Node> closedSet = new HashSet<>();
        openSet.add(startNode);

        while (!openSet.isEmpty()) {
            Node currentNode = openSet.poll();
            closedSet.add(currentNode);

            if (currentNode == destNode) {
                return retracePath(startNode, destNode);
            }

            for (Node neighbor : getNeighbors(currentNode, grid)) {
                if (neighbor == null || closedSet.contains(neighbor))
                    continue;
                float newGCost = currentNode.gCost + getDistance(currentNode, neighbor);
                if (newGCost < neighbor.gCost || !openSet.contains(neighbor)) {
                    neighbor.gCost = newGCost;
                    neighbor.hCost = getDistance(neighbor, destNode);
                    neighbor.parent = currentNode;
                    if (!openSet.contains(neighbor))
                        openSet.add(neighbor);
                }
            }
        }
        return null;
    }

    private List<Node> getNeighbors(Node node, Node[][] grid) {
        List<Node> neighbors = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                if (x == 0 && y == 0)
                    continue;
                int checkX = node.x + x;
                int checkY = node.y + y;
                if (checkX >= 0 && checkX < grid.length && checkY >= 0 && checkY < grid[0].length) {
                    neighbors.add(grid[checkX][checkY]);
                }
            }
        }
        return neighbors;
    }

    private List<Node> retracePath(Node startNode, Node endNode) {
        List<Node> path = new ArrayList<>();
        Node currentNode = endNode;
        while (currentNode != startNode) {
            path.add(currentNode);
            currentNode = currentNode.parent;
        }
        Collections.reverse(path);
        return path;
    }

    private List<PointF> smoothPath(List<PointF> path) {
        if (path.size() < 3)
            return path;

        List<PointF> smoothed = new ArrayList<>();
        smoothed.add(path.get(0));

        for (int i = 1; i < path.size() - 1; i++) {
            PointF prev = path.get(i - 1);
            PointF current = path.get(i);
            PointF next = path.get(i + 1);

            // Calculate average position
            PointF smoothedPoint = new PointF(
                    (prev.x + current.x + next.x) / 3,
                    (prev.y + current.y + next.y) / 3);

            // Only add if it maintains visibility
            if (hasLineOfSight(smoothed.get(smoothed.size() - 1), smoothedPoint)) {
                smoothed.add(smoothedPoint);
            } else {
                smoothed.add(current);
            }
        }

        smoothed.add(path.get(path.size() - 1));
        return smoothed;
    }

    private boolean hasLineOfSight(PointF p1, PointF p2) {
        // Cần cài đặt logic kiểm tra va chạm của đường thẳng (p1, p2) với các vật cản
        return true;
    }

    private float getDistance(PointF p1, PointF p2) {
        return (float) Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2));
    }

    private float getDistance(Node nodeA, Node nodeB) {
        float dstX = Math.abs(nodeA.x - nodeB.x);
        float dstY = Math.abs(nodeA.y - nodeB.y);
        if (dstX > dstY)
            return 14 * dstY + 10 * (dstX - dstY);
        return 14 * dstX + 10 * (dstY - dstX);
    }

    public void executeCommand(String command) {
        if (command == null || this.robot == null) {
            return;
        }

        Log.d("MapData", "Executing command: " + command);
        String[] parts = command.split(" ");
        if (parts.length < 2)
            return;

        try {
            if (parts[0].equals("ROTATE")) {
                float degrees = Float.parseFloat(parts[2]);
                if (parts[1].equals("LEFT")) {
                    this.robotAngle += degrees; // Quay ngược chiều kim đồng hồ
                } else if (parts[1].equals("RIGHT")) {
                    this.robotAngle -= degrees; // Quay cùng chiều kim đồng hồ
                }
                // Chuẩn hóa góc quay trong khoảng [0, 360)
                while (this.robotAngle >= 360)
                    this.robotAngle -= 360;
                while (this.robotAngle < 0)
                    this.robotAngle += 360;

            } else if (parts[0].equals("ADVANCE")) {
                float distance = Float.parseFloat(parts[1]);
                float angleRad = (float) Math.toRadians(this.robotAngle);

                float deltaX = distance * (float) Math.cos(angleRad);
                float deltaY = distance * (float) Math.sin(angleRad);

                this.robot.x += deltaX;
                this.robot.y += deltaY;
            }
        } catch (NumberFormatException e) {
            Log.e("MapData", "Failed to parse command: " + command, e);
        }
    }

    // --- LỚP NỘI BỘ (HELPER CLASS) ---
    private static class Node implements Comparable<Node> {
        int x, y;
        float gCost = 0;
        float hCost = 0;
        Node parent = null;

        Node(int x, int y) {
            this.x = x;
            this.y = y;
        }

        float fCost() {
            return gCost + hCost;
        }

        @Override
        public int compareTo(Node other) {
            return Float.compare(this.fCost(), other.fCost());
        }
    }
}