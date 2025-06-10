// MainActivity.java
package com.example.my_first_app; // Thay đổi package name

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.my_first_app.databinding.ActivityMainBinding; // Import ViewBinding
import com.google.common.util.concurrent.ListenableFuture;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.Rot90Op;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};
    private ExecutorService cameraExecutor;
    private ObjectDetector objectDetector;
    private YOLOv10Detector yoloDetector;
    
    // Định nghĩa các model có sẵn
    private static final String MODEL_SSD_MOBILENET = "ssd_mobilenet_v1_1_metadata_1.tflite";
    private static final String MODEL_MOBILENET_V1 = "mobilenet_v1_1.0_224.tflite";
    private static final String MODEL_YOLOV10N = "yolov10n_float16.tflite";
    // Tạm thời loại bỏ model FPN 640x640 vì không tương thích
    // private static final String MODEL_SSD_MOBILENET_FPN_640 = "ssd_mobilenet_v1_fpn_640x640.tflite";
    
    // Model hiện tại đang sử dụng - thử YOLOv10n mới
    private String currentModelName = MODEL_YOLOV10N;
    
    // Label cần phát hiện
    private static final String TARGET_LABEL = "person";
    
    // Detection mode
    private boolean useYOLOv10 = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        cameraExecutor = Executors.newSingleThreadExecutor();
        setupDetector();
        
        // Thêm listener để chuyển đổi model (có thể thêm button sau)
        setupModelSwitching();
    }
    
    private void setupModelSwitching() {
        // Bạn có thể thêm button để chuyển đổi model
        // Ví dụ: binding.switchModelButton.setOnClickListener(v -> switchModel());
        
        // Setup robot control FAB
        setupRobotControlFAB();
    }
    
    private void setupRobotControlFAB() {
        binding.robotControlFab.setOnClickListener(v -> {
            Intent intent = new Intent(this, BluetoothConnectionActivity.class);
            startActivity(intent);
        });
    }
    
    public void switchModel() {
        // Chuyển đổi giữa 3 model
        if (currentModelName.equals(MODEL_SSD_MOBILENET)) {
            currentModelName = MODEL_MOBILENET_V1;
        } else if (currentModelName.equals(MODEL_MOBILENET_V1)) {
            currentModelName = MODEL_YOLOV10N;
        } else {
            currentModelName = MODEL_SSD_MOBILENET;
        }
        
        setupDetector();
        Toast.makeText(this, "Đã chuyển sang model: " + getModelDisplayName(currentModelName), Toast.LENGTH_SHORT).show();
    }
    
    private String getModelDisplayName(String modelName) {
        switch (modelName) {
            case MODEL_SSD_MOBILENET:
                return "SSD MobileNet (cũ)";
            case MODEL_YOLOV10N:
                return "YOLOv10n";
            case MODEL_MOBILENET_V1:
                return "MobileNet V1 (classification)";
            default:
                return modelName;
        }
    }

    private void setupDetector() {
        try {
            // Giải phóng detector cũ nếu có
            if (objectDetector != null) {
                objectDetector.close();
                objectDetector = null;
            }
            if (yoloDetector != null) {
                yoloDetector.close();
                yoloDetector = null;
            }
            
            // Thử khởi tạo YOLOv10 trước nếu là YOLOv10 model
            if (currentModelName.equals(MODEL_YOLOV10N)) {
                boolean yoloSuccess = tryInitializeYOLOv10(currentModelName);
                if (yoloSuccess) {
                    useYOLOv10 = true;
                    return;
                }
            }
            
            // Nếu không phải YOLOv10 hoặc YOLOv10 fail, thử Task Vision API
            useYOLOv10 = false;
            boolean success = tryInitializeTaskVision(currentModelName);
            
            // Nếu model hiện tại fail, thử fallback về SSD MobileNet
            if (!success && !currentModelName.equals(MODEL_SSD_MOBILENET)) {
                Log.w("ObjectDetector", "Model " + currentModelName + " không tương thích, chuyển về SSD MobileNet");
                currentModelName = MODEL_SSD_MOBILENET;
                success = tryInitializeTaskVision(currentModelName);
                
                if (success) {
                    Toast.makeText(this, "Model không tương thích, đã chuyển về SSD MobileNet", Toast.LENGTH_LONG).show();
                }
            }
            
            if (!success) {
                Toast.makeText(this, "Không thể khởi tạo bất kỳ AI model nào", Toast.LENGTH_LONG).show();
            }
            
        } catch (Exception e) {
            Log.e("Detector", "Lỗi không xác định khi khởi tạo detector", e);
            Toast.makeText(this, "Lỗi khởi tạo AI model", Toast.LENGTH_LONG).show();
        }
    }
    
    private boolean tryInitializeYOLOv10(String modelName) {
        try {
            yoloDetector = new YOLOv10Detector(this, modelName);
            Log.i("YOLOv10Detector", "Đã khởi tạo YOLOv10 model: " + modelName);
            return true;
        } catch (Exception e) {
            Log.e("YOLOv10Detector", "Không thể khởi tạo YOLOv10 với model: " + modelName, e);
            return false;
        }
    }
    
    private boolean tryInitializeTaskVision(String modelName) {
        try {
            // Tùy chọn cho Object Detector, bắt đầu với CPU
            BaseOptions.Builder baseOptionsBuilder = BaseOptions.builder()
                .setNumThreads(4); // Sử dụng 4 thread để tăng hiệu suất
                
            // Thử sử dụng GPU nếu có sẵn
            boolean useGpu = false;
            try {
                // Tạo detector với GPU trước để test
                BaseOptions gpuOptions = BaseOptions.builder()
                    .setNumThreads(4)
                    .useGpu()
                    .build();
                    
                ObjectDetector.ObjectDetectorOptions gpuTestOptions = ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(gpuOptions)
                    .setMaxResults(1) // Test với ít kết quả
                    .setScoreThreshold(0.8f)
                    .build();
                    
                ObjectDetector testDetector = ObjectDetector.createFromFileAndOptions(this, modelName, gpuTestOptions);
                testDetector.close(); // Đóng detector test
                
                // Nếu không crash thì GPU khả dụng
                baseOptionsBuilder.useGpu();
                useGpu = true;
                Log.i("ObjectDetector", "Sử dụng GPU acceleration");
                
            } catch (Exception e) {
                Log.i("ObjectDetector", "GPU không khả dụng, sử dụng CPU: " + e.getMessage());
                // Giữ nguyên CPU-only configuration
            }
            
            // Tùy chỉnh cài đặt theo từng model
            int maxResults;
            float scoreThreshold;
            
            if (modelName.equals(MODEL_YOLOV10N)) {
                // Cài đặt tối ưu cho YOLOv10n
                maxResults = 10;
                scoreThreshold = 0.3f; // YOLOv10 thường chính xác hơn, có thể dùng ngưỡng thấp hơn
            } else if (modelName.equals(MODEL_MOBILENET_V1)) {
                // Cài đặt cho MobileNet V1 (classification model - có thể không hoạt động)
                maxResults = 3;
                scoreThreshold = 0.8f;
            } else {
                // Cài đặt mặc định cho SSD MobileNet
                maxResults = 5;
                scoreThreshold = 0.5f;
            }
            
            ObjectDetector.ObjectDetectorOptions options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setMaxResults(maxResults)
                .setScoreThreshold(scoreThreshold)
                .build();
                
            objectDetector = ObjectDetector.createFromFileAndOptions(this, modelName, options);
            Log.i("ObjectDetector", "Đã khởi tạo model: " + modelName + " với " + (useGpu ? "GPU" : "CPU"));
            return true;
            
        } catch (IOException e) {
            Log.e("ObjectDetector", "Lỗi khởi tạo object detector với model: " + modelName, e);
            return false;
        } catch (Exception e) {
            Log.e("ObjectDetector", "Model " + modelName + " không tương thích: " + e.getMessage(), e);
            return false;
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (Exception e) {
                Log.e("CameraX", "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());

        // Use case để phân tích ảnh
        ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalyzer.setAnalyzer(cameraExecutor, image -> {
            // Kiểm tra detector có sẵn không
            if (!useYOLOv10 && objectDetector == null) {
                image.close();
                return;
            }
            if (useYOLOv10 && yoloDetector == null) {
                image.close();
                return;
            }
            
            try {
                // Chuyển ImageProxy sang Bitmap
                Bitmap bitmap = image.toBitmap();

                if (bitmap != null) {
                    final List<Detection> results;
                    
                    if (useYOLOv10) {
                        // Sử dụng YOLOv10Detector
                        List<YOLOv10Detector.Detection> yoloResults = yoloDetector.detect(bitmap, image.getImageInfo().getRotationDegrees());
                        
                        // Lọc chỉ lấy những detection có label mong muốn
                        List<YOLOv10Detector.Detection> filteredYoloResults = DetectionAdapter.filterYOLOv10DetectionsByLabel(yoloResults, TARGET_LABEL);
                        
                        Log.d("MainActivity", "YOLOv10 found " + yoloResults.size() + " total detections, " + filteredYoloResults.size() + " person detections");
                        
                        // Convert sang format Task Vision API
                        results = DetectionAdapter.convertYOLOv10Detections(filteredYoloResults);
                        
                        Log.d("MainActivity", "Converted to " + results.size() + " Task Vision detections");
                    } else {
                        // Sử dụng Task Vision API ObjectDetector
                        // Sử dụng TFLite Support Library để xử lý ảnh
                        TensorImage tensorImage = TensorImage.fromBitmap(bitmap);
                        
                        // Xoay ảnh nếu cần thiết
                        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                            .add(new Rot90Op(-image.getImageInfo().getRotationDegrees() / 90))
                            .build();
                        tensorImage = imageProcessor.process(tensorImage);

                        // Chạy model
                        List<Detection> taskResults = objectDetector.detect(tensorImage);
                        
                        // Lọc chỉ lấy những detection có label mong muốn
                        results = filterDetectionsByLabel(taskResults, TARGET_LABEL);
                    }

                    // Cập nhật giao diện trên luồng UI
                    runOnUiThread(() -> {
                        binding.overlayView.setResults(results, image.getWidth(), image.getHeight());
                    });
                }
            } catch (Exception e) {
                Log.e("ImageAnalysis", "Lỗi phân tích ảnh", e);
            } finally {
                image.close(); // Rất quan trọng: phải đóng image để nhận frame tiếp theo
            }
        });

        // Gắn các use case vào lifecycle của camera
        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer);
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (objectDetector != null) {
            objectDetector.close();
        }
        if (yoloDetector != null) {
            yoloDetector.close();
        }
        cameraExecutor.shutdown();
    }

    private List<Detection> filterDetectionsByLabel(List<Detection> detections, String label) {
        List<Detection> filteredResults = new ArrayList<>();
        for (Detection detection : detections) {
            if (!detection.getCategories().isEmpty()) {
                String detectedLabel = detection.getCategories().get(0).getLabel().toLowerCase();
                if (detectedLabel.equals(label)) {
                    filteredResults.add(detection);
                }
            }
        }
        return filteredResults;
    }
}