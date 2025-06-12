package com.example.my_first_app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

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

public class CameraAIFragment extends Fragment {

    private RobotCommunicationInterface robotCommunication;
    private PreviewView previewView;
    private OverlayView overlayView;
    private ExecutorService cameraExecutor;
    private ObjectDetector objectDetector;
    private YOLOv10Detector yoloDetector;
    
    // Định nghĩa các model có sẵn
    private static final String MODEL_SSD_MOBILENET = "ssd_mobilenet_v1_1_metadata_1.tflite";
    private static final String MODEL_MOBILENET_V1 = "mobilenet_v1_1.0_224.tflite";
    private static final String MODEL_YOLOV10N = "yolov10n_float16.tflite";
    
    // Model hiện tại đang sử dụng
    private String currentModelName = MODEL_YOLOV10N;
    
    // Label cần phát hiện
    private static final String TARGET_LABEL = "person";
    
    // Detection mode
    private boolean useYOLOv10 = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d("CameraAIFragment", "onCreateView called");
        
        try {
            View view = inflater.inflate(R.layout.fragment_camera_ai, container, false);
            
            initializeViews(view);
            setupCommunicationService();
            
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Camera permission required", Toast.LENGTH_LONG).show();
                }
            }

            cameraExecutor = Executors.newSingleThreadExecutor();
            setupDetector();
            
            return view;
            
        } catch (Exception e) {
            Log.e("CameraAIFragment", "Error in onCreateView", e);
            // Return a simple view if layout inflation fails
            TextView errorView = new TextView(getContext());
            errorView.setText("Error loading camera interface");
            return errorView;
        }
    }
    
    private void initializeViews(View view) {
        try {
            previewView = view.findViewById(R.id.previewView);
            overlayView = view.findViewById(R.id.overlayView);
            
            if (previewView == null) {
                Log.e("CameraAIFragment", "previewView is null");
            }
            if (overlayView == null) {
                Log.e("CameraAIFragment", "overlayView is null");
            }
        } catch (Exception e) {
            Log.e("CameraAIFragment", "Error initializing camera views", e);
        }
    }
    
    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void setupCommunicationService() {
        // Get existing service from ConnectionManager
        robotCommunication = ConnectionManager.getInstance().getCommunicationService();

        if (robotCommunication != null) {
//            robotCommunication.setCommunicationListener(this);

            if (robotCommunication.isConnected()) {
                String deviceName = robotCommunication.getConnectedDevice() != null ?
                        robotCommunication.getConnectedDevice().getName() : "Unknown Device";
            } else {
            }
        } else {
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
                
                if (success && getContext() != null) {
                    Toast.makeText(getContext(), "Model không tương thích, đã chuyển về SSD MobileNet", Toast.LENGTH_LONG).show();
                }
            }
            
            if (!success && getContext() != null) {
                Toast.makeText(getContext(), "Không thể khởi tạo bất kỳ AI model nào", Toast.LENGTH_LONG).show();
            }
            
        } catch (Exception e) {
            Log.e("Detector", "Lỗi không xác định khi khởi tạo detector", e);
            if (getContext() != null) {
                Toast.makeText(getContext(), "Lỗi khởi tạo AI model", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private boolean tryInitializeYOLOv10(String modelName) {
        try {
            yoloDetector = new YOLOv10Detector(requireContext(), modelName);
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
                    
                ObjectDetector testDetector = ObjectDetector.createFromFileAndOptions(requireContext(), modelName, gpuTestOptions);
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
                
            objectDetector = ObjectDetector.createFromFileAndOptions(requireContext(), modelName, options);
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
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (Exception e) {
                Log.e("CameraX", "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindCameraUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

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
                        
                        Log.d("CameraAIFragment", "YOLOv10 found " + yoloResults.size() + " total detections, " + filteredYoloResults.size() + " person detections");
                        
                        // Convert sang format Task Vision API
                        results = DetectionAdapter.convertYOLOv10Detections(filteredYoloResults);
                        
                        Log.d("CameraAIFragment", "Converted to " + results.size() + " Task Vision detections");
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
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            String command = overlayView.setResults(results, image.getWidth(), image.getHeight());
                            sendRobotCommand(command);
                        });
                    }
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

    private void sendRobotCommand(String command) {
        if (robotCommunication != null && robotCommunication.isConnected()) {
            robotCommunication.sendRobotCommand(command);
        } else {
            Toast.makeText(getContext(), "Robot not connected", Toast.LENGTH_SHORT).show();
        }
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

    @Override
    public void onDestroy() {
        Log.d("CameraAIFragment", "onDestroy called");
        super.onDestroy();
        
        try {
            if (objectDetector != null) {
                objectDetector.close();
                objectDetector = null;
            }
        } catch (Exception e) {
            Log.e("CameraAIFragment", "Error closing objectDetector", e);
        }
        
        try {
            if (yoloDetector != null) {
                yoloDetector.close();
                yoloDetector = null;
            }
        } catch (Exception e) {
            Log.e("CameraAIFragment", "Error closing yoloDetector", e);
        }
        
        try {
            if (cameraExecutor != null && !cameraExecutor.isShutdown()) {
                cameraExecutor.shutdown();
                cameraExecutor = null;
            }
        } catch (Exception e) {
            Log.e("CameraAIFragment", "Error shutting down cameraExecutor", e);
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        Log.d("CameraAIFragment", "onPause called");
        
        // Stop any ongoing operations when fragment is paused
        try {
            if (cameraExecutor != null && !cameraExecutor.isShutdown()) {
                cameraExecutor.shutdown();
            }
        } catch (Exception e) {
            Log.e("CameraAIFragment", "Error in onPause", e);
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        Log.d("CameraAIFragment", "onResume called");
        
        // Restart camera executor if needed
        try {
            if (cameraExecutor == null || cameraExecutor.isShutdown()) {
                cameraExecutor = Executors.newSingleThreadExecutor();
            }
        } catch (Exception e) {
            Log.e("CameraAIFragment", "Error in onResume", e);
        }
    }
} 