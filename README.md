# Dự Án Robot Tự Hành bằng AI

![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Python](https://img.shields.io/badge/Python-3776AB?style=for-the-badge&logo=python&logoColor=white)
![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![TensorFlow](https://img.shields.io/badge/TensorFlow-FF6F00?style=for-the-badge&logo=tensorflow&logoColor=white)

[cite_start]Đây là tài liệu cho dự án "Robot Tự Hành bằng AI", được phát triển bởi sinh viên Khoa Khoa học và Kỹ thuật Máy tính, Trường Đại học Bách Khoa - ĐHQG TP.HCM. 

[cite_start]Mục tiêu chính của dự án là phát triển một robot có khả năng bám sát và đi theo người dùng dựa trên hình ảnh thu được từ camera,  tích hợp các thuật toán AI để nhận diện đối tượng, tránh vật cản và tự động di chuyển.

## 🌟 Tính Năng Chính

* [cite_start]**Theo Dõi Người Dùng:** Robot có khả năng nhận diện và bám sát theo sự di chuyển của người dùng. 
    * [cite_start]Tự động tiến tới khi người dùng ở xa và lùi lại hoặc đứng yên khi ở quá gần. 
    * [cite_start]Tự động xoay và quét để tìm lại khi không phát hiện người dùng trong khung hình. 
* [cite_start]**Tránh Vật Cản:** Tự động dừng lại khi phát hiện vật cản ở khoảng cách gần (dưới 20cm) để tránh va chạm. 
* [cite_start]**Robot Dò Đường:** Có khả năng ghi nhớ vị trí và tự động di chuyển đến một điểm được chọn trên bản đồ. 
* [cite_start]**Điều Khiển Qua App:** Cho phép điều khiển robot thủ công (tiến, lùi, trái, phải) thông qua ứng dụng di động. 
* [cite_start]**Xử lý AI trên di động:** Sử dụng mô hình **YOLOv4-Tiny** được tối ưu hóa với TensorFlow Lite để chạy tác vụ nhận diện đối tượng (người, vật cản) ngay trên điện thoại. 
* [cite_start]**Giao tiếp không dây:** Sử dụng Bluetooth Low Energy (BLE) để giao tiếp giữa điện thoại và robot. 

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
[cite_start]*(Dựa trên bảng danh sách linh kiện trong tài liệu)* 

### Công Nghệ Sử Dụng

* [cite_start]**Nền tảng Mobile App:** Android (Java) 
* [cite_start]**Firmware Robot:** Nền tảng lập trình kéo thả OhStem App 
* [cite_start]**AI/Machine Learning:** YOLOv4-Tiny,  [cite_start]TensorFlow Lite 
* [cite_start]**Giao tiếp:** Bluetooth Low Energy (BLE) 
* [cite_start]**Phần cứng:** Vi điều khiển ESP32, mô-tơ, cảm biến, module BLE/WiFi 

---

## 📱 Cài Đặt & Sử Dụng App Android

Ứng dụng Android đóng vai trò trung tâm, xử lý AI và gửi lệnh điều khiển đến robot.

**Quét mã QR dưới đây để tải và cài đặt tệp APK:**

<div align="center">
  <img src="https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=https://github.com/khoale2k4/robot-ai-java/raw/main/app-debug.apk" alt="QR Code to install Android App">
</div>

**Hướng dẫn gỡ lỗi (debug) ứng dụng:**
1.  [cite_start]Trên điện thoại Android, vào `Cài đặt` > `Giới thiệu điện thoại` > `Số phiên bản` và chạm 7 lần để bật chế độ Nhà phát triển. 
2.  [cite_start]Trong `Tùy chọn nhà phát triển`, bật `Gỡ lỗi USB`. 
3.  [cite_start]Kết nối điện thoại với máy tính qua cáp USB. 
4.  [cite_start]Mở dự án bằng Android Studio và nhấn `Run` để cài đặt và chạy ứng dụng trên thiết bị. 

---

## 🏗️ Cấu Trúc Dự Án

Mã nguồn của dự án được chia thành hai phần chính:

1.  **Mobile App (robot-ai-java):**
    * [cite_start]Chứa mã nguồn ứng dụng Android, có chức năng tích hợp AI, xây dựng giao diện điều khiển và giao tiếp BLE/WiFi với robot. 
    * [cite_start]**Link GitHub:** [https://github.com/khoale2k4/robot-ai-java](https://github.com/khoale2k4/robot-ai-java) 

2.  **Firmware Điều Khiển Robot (OhStem App):**
    * [cite_start]Chương trình nhúng để điều khiển động cơ, cảm biến và nhận lệnh từ mobile app. 
    * [cite_start]**Link dự án OhStem:** [https://app.ohstem.vn/#!/share/yolouno/2xw4r0eG6ktsxgc7ZaCw6Ev6ezQ](https://app.ohstem.vn/#!/share/yolouno/2xw4r0eG6ktsxgc7ZaCw6Ev6ezQ) 

---

## 📚 Tài Liệu & Hướng Dẫn Chi Tiết

Toàn bộ hướng dẫn chi tiết từ A-Z về dự án, bao gồm:
* [cite_start]Hướng dẫn lắp ráp robot và đấu nối linh kiện. 
* [cite_start]Cài đặt môi trường lập trình cho ESP32 và Android Studio. 
* [cite_start]Huấn luyện và triển khai mô hình AI. 
* [cite_start]Sơ đồ hệ thống và video demo. 

Vui lòng tham khảo tài liệu đầy đủ tại:
[cite_start]**[Tài liệu hướng dẫn trên Overleaf](https://www.overleaf.com/read/stpjqzqbtknp#dd9fc3)**
