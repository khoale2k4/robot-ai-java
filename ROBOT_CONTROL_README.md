# Robot Control Features - Android App

## Tổng quan

Ứng dụng Android này đã được tích hợp các tính năng điều khiển robot qua Bluetooth, dựa trên dự án [ohstem-robot-ai](https://github.com/khoale2k4/ohstem-robot-ai). Ứng dụng kết hợp tính năng AI Camera hiện tại với khả năng điều khiển robot thông minh.

## Tính năng mới

### 1. 🔷 Kết nối Bluetooth
- Quét và hiển thị các thiết bị Bluetooth đã ghép nối
- Kết nối an toàn với robot qua giao thức SPP (Serial Port Profile)
- Xử lý quyền Bluetooth cho Android 12+ và các phiên bản cũ hơn
- Tự động phát hiện và kích hoạt Bluetooth

### 2. 🎮 Điều khiển Robot
**Joystick Control:**
- Joystick tùy chỉnh với 8 hướng di chuyển
- Điều khiển mượt mà với phản hồi thời gian thực
- Tự động dừng khi thả joystick

**Button Control:**
- 4 nút điều hướng: Tiến, Lùi, Trái, Phải
- Nút STOP khẩn cấp
- Các nút hoạt động khi nhấn và thả

### 3. 🎨 Giao diện người dùng
- Thiết kế material design với màu sắc hiện đại
- Floating Action Button để truy cập nhanh từ camera
- Giao diện trực quan với status hiển thị rõ ràng
- Responsive layout cho nhiều kích thước màn hình

## Cấu trúc dự án

```
app/src/main/java/com/example/my_first_app/
├── BluetoothService.java          # Dịch vụ quản lý kết nối Bluetooth
├── JoystickView.java              # Custom View cho joystick
├── BluetoothConnectionActivity.java # Màn hình kết nối Bluetooth
├── RobotControlActivity.java      # Màn hình điều khiển robot
└── MainActivity.java              # Màn hình chính (đã cập nhật)

app/src/main/res/
├── layout/
│   ├── activity_bluetooth_connection.xml
│   ├── activity_robot_control.xml
│   └── activity_main.xml (đã cập nhật)
├── drawable/
│   ├── button_primary.xml
│   ├── button_danger.xml
│   ├── list_background.xml
│   └── ic_robot.xml
├── values/
│   ├── colors.xml (đã cập nhật)
│   └── strings.xml (đã cập nhật)
```

## Hướng dẫn sử dụng

### Bước 1: Chuẩn bị Robot
1. Đảm bảo robot có module Bluetooth (HC-05, HC-06 hoặc ESP32)
2. Ghép nối robot với điện thoại qua Settings > Bluetooth
3. Ghi nhớ tên thiết bị Bluetooth của robot

### Bước 2: Kết nối từ App
1. Mở ứng dụng và nhấn nút Robot (FAB) ở góc dưới phải
2. Cấp quyền Bluetooth khi được yêu cầu
3. Nhấn "Scan Devices" để tìm các thiết bị đã ghép nối
4. Chọn robot của bạn từ danh sách
5. Chờ kết nối thành công

### Bước 3: Điều khiển Robot
1. Sau khi kết nối, bạn sẽ được chuyển đến màn hình điều khiển
2. **Sử dụng Joystick**: Kéo joystick để di chuyển robot theo 8 hướng
3. **Sử dụng Button**: Nhấn và giữ các nút mũi tên để di chuyển
4. **Dừng khẩn cấp**: Nhấn nút STOP màu đỏ
5. **Ngắt kết nối**: Nhấn nút "Disconnect" khi hoàn thành

## Giao thức điều khiển

Robot sẽ nhận các lệnh sau qua Bluetooth:

```
FORWARD         # Di chuyển tiến
BACKWARD        # Di chuyển lùi
LEFT            # Xoay trái
RIGHT           # Xoay phải
FORWARD_LEFT    # Di chuyển tiến + trái
FORWARD_RIGHT   # Di chuyển tiến + phải
BACKWARD_LEFT   # Di chuyển lùi + trái
BACKWARD_RIGHT  # Di chuyển lùi + phải
STOP            # Dừng tất cả động cơ
```

## Lập trình Robot (Arduino)

Ví dụ code Arduino để nhận lệnh:

```cpp
#include <SoftwareSerial.h>

SoftwareSerial bluetooth(2, 3); // RX, TX

void setup() {
  bluetooth.begin(9600);
  // Khởi tạo motor pins
}

void loop() {
  if (bluetooth.available()) {
    String command = bluetooth.readStringUntil('\n');
    command.trim();
    
    if (command == "FORWARD") {
      moveForward();
    } else if (command == "BACKWARD") {
      moveBackward();
    } else if (command == "LEFT") {
      turnLeft();
    } else if (command == "RIGHT") {
      turnRight();
    } else if (command == "STOP") {
      stopMotors();
    }
    // Thêm các lệnh khác...
  }
}
```

## Yêu cầu hệ thống

- **Android SDK**: 26+ (Android 8.0+)
- **Bluetooth**: BLE hoặc Classic Bluetooth
- **Quyền cần thiết**:
  - `BLUETOOTH`
  - `BLUETOOTH_ADMIN`
  - `BLUETOOTH_CONNECT` (Android 12+)
  - `BLUETOOTH_SCAN` (Android 12+)
  - `ACCESS_FINE_LOCATION`

## Xử lý lỗi

### Không tìm thấy thiết bị:
- Kiểm tra Bluetooth đã bật
- Đảm bảo robot đã được ghép nối trước
- Thử scan lại

### Không kết nối được:
- Kiểm tra robot có đang hoạt động
- Thử reset module Bluetooth
- Kiểm tra khoảng cách (< 10m)

### App crash khi điều khiển:
- Kiểm tra quyền đã được cấp đầy đủ
- Restart app và thử lại

## Tùy chỉnh và mở rộng

### Thêm lệnh mới:
1. Cập nhật `BluetoothService.java` thêm method mới
2. Thêm button/gesture trong `RobotControlActivity.java`
3. Cập nhật code Arduino để xử lý lệnh

### Thay đổi giao diện:
- Chỉnh sửa file layout XML
- Cập nhật colors.xml cho theme mới
- Tùy chỉnh JoystickView cho giao diện khác

## Demo và Video

- Xem demo tại: [GitHub ohstem-robot-ai](https://github.com/khoale2k4/ohstem-robot-ai)
- Video hướng dẫn: (sẽ cập nhật)

## Hỗ trợ

Nếu gặp vấn đề, hãy:
1. Kiểm tra log Android Studio
2. Đảm bảo robot và app dùng cùng giao thức
3. Thử với thiết bị Android khác
4. Tạo issue trên GitHub với thông tin chi tiết

---

**Phát triển dựa trên**: [ohstem-robot-ai Flutter App](https://github.com/khoale2k4/ohstem-robot-ai)  
**Ngôn ngữ**: Java/Android  
**Version**: 1.0.0 