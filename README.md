# Dự Án Robot Tự Hành bằng AI

![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Python](https://img.shields.io/badge/Python-3776AB?style=for-the-badge&logo=python&logoColor=white)
![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![TensorFlow](https://img.shields.io/badge/TensorFlow-FF6F00?style=for-the-badge&logo=tensorflow&logoColor=white)

Đây là tài liệu cho dự án "Robot Tự Hành bằng AI", được phát triển bởi sinh viên Khoa Khoa học và Kỹ thuật Máy tính, Trường Đại học Bách Khoa - ĐHQG TP.HCM.

Mục tiêu chính của dự án là phát triển một robot có khả năng bám sát và đi theo người dùng dựa trên hình ảnh thu được từ camera, tích hợp các thuật toán AI để nhận diện đối tượng, tránh vật cản và tự động di chuyển.

## 🌟 Tính Năng Chính

* **Theo Dõi Người Dùng:** Robot có khả năng nhận diện và bám sát theo sự di chuyển của người dùng.
    * Tự động tiến tới khi người dùng ở xa và lùi lại hoặc đứng yên khi ở quá gần.
    * Tự động xoay và quét để tìm lại khi không phát hiện người dùng trong khung hình.
* **Tránh Vật Cản:** Tự động dừng lại khi phát hiện vật cản ở khoảng cách gần (dưới 20cm) để tránh va chạm.
* **Robot Dò Đường:** Có khả năng ghi nhớ vị trí và tự động di chuyển đến một điểm được chọn trên bản đồ.
* **Điều Khiển Qua App:** Cho phép điều khiển robot thủ công (tiến, lùi, trái, phải) thông qua ứng dụng di động.
* **Xử lý AI trên di động:** Sử dụng mô hình **YOLOv4-Tiny** được tối ưu hóa với TensorFlow Lite để chạy tác vụ nhận diện đối tượng (người, vật cản) ngay trên điện thoại.
* **Giao tiếp không dây:** Sử dụng Bluetooth Low Energy (BLE) để giao tiếp giữa điện thoại và robot.

---

## 🛠️ Phần Cứng & Công Nghệ

### Danh Sách Linh Kiện (Khung xe ORC K2)

| Tên Linh Kiện | Số Lượng | Ghi Chú |
| :--- | :---: | :--- |
| Động cơ DC | 2 | Dùng cho 2 bánh xe |
| Động cơ Encoder | 2 | Dùng cho 2 bánh xe còn lại |
| Bánh xe Mecanum | 4 | Lắp hình chữ X để di chuyển đa hướng |
| Control Hub | 1 | Mạch điều khiển trung tâm |
| Mạch nhận tín hiệu I2C | 1 | |
| Pin | 1 | Nguồn chính cho robot |
| Bu lông, đai ốc... | | |
*(Dựa trên bảng danh sách linh kiện trong tài liệu)*

### Công Nghệ Sử Dụng

* **Nền tảng Mobile App:** Android (Java)
* **Firmware Robot:** Nền tảng lập trình kéo thả OhStem App
* **AI/Machine Learning:** YOLOv4-Tiny, TensorFlow Lite
* **Giao tiếp:** Bluetooth Low Energy (BLE)
* **Phần cứng:** Vi điều khiển ESP32, mô-tơ, cảm biến, module BLE/WiFi

---

## 📱 Cài Đặt & Sử Dụng App Android

Ứng dụng Android đóng vai trò trung tâm, xử lý AI và gửi lệnh điều khiển đến robot.

**Quét mã QR dưới đây để tải và cài đặt tệp APK:**

<div align="center">
  <img src="https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=https://github.com/khoale2k4/robot-ai-java/raw/main/app-debug.apk" alt="QR Code to install Android App">
</div>

**Hướng dẫn gỡ lỗi (debug) ứng dụng:**
1.  Trên điện thoại Android, vào `Cài đặt` > `Giới thiệu điện thoại` > `Số phiên bản` và chạm 7 lần để bật chế độ Nhà phát triển.
2.  Trong `Tùy chọn nhà phát triển`, bật `Gỡ lỗi USB`.
3.  Kết nối điện thoại với máy tính qua cáp USB.
4.  Mở dự án bằng Android Studio và nhấn `Run` để cài đặt và chạy ứng dụng trên thiết bị.

---

## 🏗️ Cấu Trúc Dự Án

Mã nguồn của dự án được chia thành hai phần chính:

1.  **Mobile App (robot-ai-java):**
    * Chứa mã nguồn ứng dụng Android, có chức năng tích hợp AI, xây dựng giao diện điều khiển và giao tiếp BLE/WiFi với robot.
    * **Link GitHub:** [https://github.com/khoale2k4/robot-ai-java](https://github.com/khoale2k4/robot-ai-java)

2.  **Firmware Điều Khiển Robot (OhStem App):**
    * Chương trình nhúng để điều khiển động cơ, cảm biến và nhận lệnh từ mobile app.
    * **Link dự án OhStem:** [https://app.ohstem.vn/#!/share/yolouno/2xw4r0eG6ktsxgc7ZaCw6Ev6ezQ](https://app.ohstem.vn/#!/share/yolouno/2xw4r0eG6ktsxgc7ZaCw6Ev6ezQ)

---

## 📚 Tài Liệu & Hướng Dẫn Chi Tiết

Toàn bộ hướng dẫn chi tiết từ A-Z về dự án, bao gồm:
* Hướng dẫn lắp ráp robot và đấu nối linh kiện.
* Cài đặt môi trường lập trình cho ESP32 và Android Studio.
* Huấn luyện và triển khai mô hình AI.
* Sơ đồ hệ thống và video demo.

Vui lòng tham khảo tài liệu đầy đủ tại:
**[Tài liệu hướng dẫn trên Overleaf](https://www.overleaf.com/read/stpjqzqbtknp#dd9fc3)**
