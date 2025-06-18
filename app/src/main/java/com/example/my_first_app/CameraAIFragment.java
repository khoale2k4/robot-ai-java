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

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class CameraAIFragment extends Fragment {

    private static final String TAG = "CameraAIFragment";

    private RobotCommunicationInterface robotCommunication;
    private PreviewView previewView;
    private OverlayView overlayView;
    private ExecutorService cameraExecutor;
    private ObjectDetector objectDetector;
    private YOLOv10Detector yoloDetector;
    private ProcessCameraProvider cameraProvider;
    private TextView noRobotWarning;
    private boolean isRobotConnected = false;

    // Định nghĩa các model có sẵn
    private static final String MODEL_SSD_MOBILENET = "ssd_mobilenet_v1_1_metadata_1.tflite";
    private static final String MODEL_MOBILENET_V1 = "mobilenet_v1_1.0_224.tflite";
    private static final String MODEL_YOLOV10N = "yolov10n_float16_old.tflite";

    // Model hiện tại đang sử dụng
    private String currentModelName = MODEL_YOLOV10N;

    // Label cần phát hiện
    private static final String TARGET_LABEL = "person";

    // Detection mode
    private boolean useYOLOv10 = false;
    private boolean isDetectorInitialized = false;
    private boolean isCameraStarted = false;
    private boolean isFragmentActive = true;
    private boolean isScanning = false;
    private FloatingActionButton scanButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView called");
        isFragmentActive = true;

        try {
            View view = inflater.inflate(R.layout.fragment_camera_ai, container, false);

            initializeViews(view);
            setupCommunicationService();
            setupScanButton(view);

            // Initialize camera executor early
            if (cameraExecutor == null || cameraExecutor.isShutdown()) {
                cameraExecutor = Executors.newSingleThreadExecutor();
            }

            return view;

        } catch (Exception e) {
            Log.e(TAG, "Error in onCreateView", e);
            // Return a simple view if layout inflation fails
            TextView errorView = new TextView(getContext());
            errorView.setText("Error loading camera interface");
            return errorView;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated called");

        // Setup detector after view is created
        setupDetector();

        // Start camera if permissions are granted
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            if (getContext() != null) {
                Toast.makeText(getContext(), "Camera permission required", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void initializeViews(View view) {
        try {
            previewView = view.findViewById(R.id.previewView);
            overlayView = view.findViewById(R.id.overlayView);
            noRobotWarning = view.findViewById(R.id.noRobotWarning);

            if (previewView == null) {
                Log.e(TAG, "previewView is null");
            }
            if (overlayView == null) {
                Log.e(TAG, "overlayView is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing camera views", e);
        }
    }

    private boolean allPermissionsGranted() {
        try {
            return ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            Log.e(TAG, "Error checking camera permissions", e);
            return false;
        }
    }

    private void setupCommunicationService() {
        try {
            // Get existing service from ConnectionManager
            robotCommunication = ConnectionManager.getInstance().getCommunicationService();

            if (robotCommunication != null && robotCommunication.isConnected()) {
                Log.d(TAG, "Robot communication service is available and connected");
                isRobotConnected = true;
                if (noRobotWarning != null) {
                    noRobotWarning.setVisibility(View.GONE);
                }
            } else {
                Log.d(TAG, "Robot communication service not available or not connected");
                isRobotConnected = false;
                if (noRobotWarning != null) {
                    noRobotWarning.setVisibility(View.VISIBLE);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up communication service", e);
            isRobotConnected = false;
            if (noRobotWarning != null) {
                noRobotWarning.setVisibility(View.VISIBLE);
            }
        }
    }

    private void setupDetector() {
        if (isDetectorInitialized) {
            Log.d(TAG, "Detector already initialized, skipping setup");
            return;
        }

        try {
            Log.d(TAG, "Setting up AI detector...");

            // Giải phóng detector cũ nếu có
            cleanupDetectors();

            // Thử khởi tạo YOLOv10 trước nếu là YOLOv10 model
            if (currentModelName.equals(MODEL_YOLOV10N)) {
                boolean yoloSuccess = tryInitializeYOLOv10(currentModelName);
                if (yoloSuccess) {
                    useYOLOv10 = true;
                    isDetectorInitialized = true;
                    Log.i(TAG, "YOLOv10 detector initialized successfully");
                    return;
                }
            }

            // Nếu không phải YOLOv10 hoặc YOLOv10 fail, thử Task Vision API
            useYOLOv10 = false;
            boolean success = tryInitializeTaskVision(currentModelName);

            // Nếu model hiện tại fail, thử fallback về SSD MobileNet
            if (!success && !currentModelName.equals(MODEL_SSD_MOBILENET)) {
                Log.w(TAG, "Model " + currentModelName + " không tương thích, chuyển về SSD MobileNet");
                currentModelName = MODEL_SSD_MOBILENET;
                success = tryInitializeTaskVision(currentModelName);

                if (success && getContext() != null) {
                    Toast.makeText(getContext(), "Model không tương thích, đã chuyển về SSD MobileNet",
                            Toast.LENGTH_LONG).show();
                }
            }

            if (success) {
                Toast.makeText(getContext(), "SUCCESS",
                            Toast.LENGTH_LONG).show();
                isDetectorInitialized = true;
                Log.i(TAG, "Task Vision detector initialized successfully");
            } else {
                Log.e(TAG, "Failed to initialize any AI detector");
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Không thể khởi tạo bất kỳ AI model nào", Toast.LENGTH_LONG).show();
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Lỗi không xác định khi khởi tạo detector", e);
            if (getContext() != null) {
                Toast.makeText(getContext(), "Lỗi khởi tạo AI model", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void cleanupDetectors() {
        try {
            Log.d(TAG, "Giải phóng tài nguyên AI detector");

            // Giải phóng YOLOv10 detector trước
            if (yoloDetector != null) {
                try {
                    yoloDetector.close();
                    yoloDetector = null;
                    Log.d(TAG, "YOLOv10 detector đã được giải phóng");
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi khi giải phóng YOLOv10 detector", e);
                }
            }

            // Giải phóng Task Vision API detector
            if (objectDetector != null) {
                try {
                    objectDetector.close();
                    objectDetector = null;
                    Log.d(TAG, "Task Vision detector đã được giải phóng");
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi khi giải phóng Task Vision detector", e);
                }
                objectDetector.close();
                objectDetector = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing objectDetector", e);
        }

        try {
            if (yoloDetector != null) {
                yoloDetector.close();
                yoloDetector = null;
            }

            isDetectorInitialized = false;

            // Gọi System.gc() để khuyến khích garbage collector chạy
            System.gc();
        } catch (Exception e) {
            Log.e(TAG, "Lỗi khi giải phóng tài nguyên detector", e);
        }

        isDetectorInitialized = false;
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

                ObjectDetector testDetector = ObjectDetector.createFromFileAndOptions(requireContext(), modelName,
                        gpuTestOptions);
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
        if (!isFragmentActive || !isAdded() || getActivity() == null) {
            Log.d(TAG, "Cannot start camera - fragment not active or attached");
            return;
        }

        try {
            // Kiểm tra quyền camera
            if (!allPermissionsGranted()) {
                Log.e(TAG, "Camera permissions not granted");
                return;
            }

            // Nếu camera đã được khởi động, không cần khởi động lại
            if (isCameraStarted && cameraProvider != null) {
                Log.d(TAG, "Camera already started");
                return;
            }

            // Lấy ProcessCameraProvider future
            ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider
                    .getInstance(requireContext());

            // Thêm listener cho future
            cameraProviderFuture.addListener(() -> {
                try {
                    // Kiểm tra lại fragment còn active không
                    if (!isFragmentActive || !isAdded() || getActivity() == null) {
                        Log.d(TAG, "Fragment no longer active, cannot start camera");
                        return;
                    }

                    // Lấy ProcessCameraProvider
                    cameraProvider = cameraProviderFuture.get();

                    // Bind camera use cases
                    bindCameraUseCases(cameraProvider);

                    // Đánh dấu camera đã được khởi động
                    isCameraStarted = true;

                } catch (Exception e) {
                    Log.e(TAG, "Error starting camera", e);
                }
            }, ContextCompat.getMainExecutor(requireContext()));

        } catch (Exception e) {
            Log.e(TAG, "Error starting camera", e);
        }
    }

    private void bindCameraUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        try {
            // Chọn camera sau
            CameraSelector cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build();

            // Preview use case
            Preview preview = new Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .build();

            // Gắn surface provider
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            // Đảm bảo unbind tất cả trước khi bind lại
            cameraProvider.unbindAll();

            // Kiểm tra xem fragment còn active không trước khi bind
            if (!isFragmentActive || !isAdded() || isDetached() || getActivity() == null) {
                Log.d(TAG, "Fragment no longer active, skipping camera binding");
                return;
            }

            // Nếu detector đã được khởi tạo, thêm image analyzer
            if (isDetectorInitialized && cameraExecutor != null && !cameraExecutor.isShutdown()) {
                // Kiểm tra lại detector
                boolean detectorReady = (useYOLOv10 && yoloDetector != null) || (!useYOLOv10 && objectDetector != null);

                if (!detectorReady) {
                    Log.w(TAG, "Detector không sẵn sàng, chỉ bind preview");
                    // Kiểm tra lại fragment còn active không trước khi bind
                    if (isFragmentActive && isAdded() && !isDetached() && getActivity() != null) {
                        cameraProvider.bindToLifecycle(this, cameraSelector, preview);
                        Log.d(TAG, "Camera bound with preview only (detector not ready)");
                    }
                    return;
                }

                ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalyzer.setAnalyzer(cameraExecutor, image -> {
                    // Check if fragment is still active and detector is available
                    if (!isFragmentActive || !isAdded() || getActivity() == null || getActivity().isFinishing()) {
                        image.close();
                        return;
                    }

                    // Kiểm tra detector có sẵn không
                    if ((!useYOLOv10 && objectDetector == null) || (useYOLOv10 && yoloDetector == null)) {
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
                                List<YOLOv10Detector.Detection> yoloResults = yoloDetector.detect(bitmap,
                                        image.getImageInfo().getRotationDegrees());

                                // Lọc chỉ lấy những detection có label mong muốn
                                List<YOLOv10Detector.Detection> filteredYoloResults = DetectionAdapter
                                        .filterYOLOv10DetectionsByLabel(yoloResults, TARGET_LABEL);

                                Log.d(TAG, "YOLOv10 found " + yoloResults.size() + " total detections, "
                                        + filteredYoloResults.size() + " person detections");

                                // Convert sang format Task Vision API
                                results = DetectionAdapter.convertYOLOv10Detections(filteredYoloResults);

                                Log.d(TAG, "Converted to " + results.size() + " Task Vision detections");
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
                            if (isFragmentActive && isAdded() && getActivity() != null && !getActivity().isFinishing()
                                    && overlayView != null) {
                                getActivity().runOnUiThread(() -> {
                                    try {
                                        if (isFragmentActive && isAdded() && overlayView != null) {
                                            String command = overlayView.setResults(results, image.getWidth(),
                                                    image.getHeight());
                                            sendRobotCommand(command);
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error updating overlay", e);
                                    }
                                });
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Lỗi phân tích ảnh", e);
                    } finally {
                        try {
                            image.close(); // Rất quan trọng: phải đóng image để nhận frame tiếp theo
                        } catch (Exception e) {
                            Log.e(TAG, "Lỗi khi đóng image", e);
                        }
                    }
                });

                // Kiểm tra lại fragment còn active không trước khi bind
                if (isFragmentActive && isAdded() && !isDetached() && getActivity() != null) {
                    // Gắn các use case vào lifecycle của fragment
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer);
                    Log.d(TAG, "Camera bound with preview and analyzer");
                }
            } else {
                // Kiểm tra lại fragment còn active không trước khi bind
                if (isFragmentActive && isAdded() && !isDetached() && getActivity() != null) {
                    // Bind preview only if detector is not ready
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview);
                    Log.d(TAG, "Camera bound with preview only (detector not ready)");
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error binding camera use cases", e);
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

    private void setupScanButton(View view) {
        scanButton = view.findViewById(R.id.scanButton);
        scanButton.setOnClickListener(v -> {
            boolean wasScanning = isScanning;
            isScanning = !isScanning;
            updateScanButtonState();
            Log.d(TAG, "Scan button clicked - wasScanning: " + wasScanning + ", isScanning: " + isScanning);
            if (wasScanning) {
                // Send ST command when stopping the scan
                Log.d(TAG, "Sending ST command to stop robot");
                sendRobotCommand("ST");
            }
        });
        updateScanButtonState();
    }

    private void updateScanButtonState() {
        if (scanButton != null) {
            scanButton.setImageResource(
                    isScanning ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
            Log.d(TAG, "Button state updated - isScanning: " + isScanning);
        }
    }

    private void sendRobotCommand(String command) {
        if (isRobotConnected && robotCommunication != null) {
            if (isScanning) {
                Log.d(TAG, "Sending command: " + command);
                robotCommunication.sendRobotCommand(command);
            } else {
                Log.d(TAG, "Skipping command " + command + " - scanning is disabled");
            }
        } else {
            Log.d(TAG, "Cannot send command: " + command + " - Robot not connected");
        }
    }

    // @Override
    // public void onDestroy() {
    //     Log.d(TAG, "onDestroy called");
    //     super.onDestroy();

    //     // Clean up everything
    //     cleanupCamera();
    //     cleanupDetectors();
    //     cleanupExecutor();
    // }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause called");

        // Luôn dừng camera khi fragment bị tạm dừng
        cleanupCamera();
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop called");

        // Đảm bảo camera được dừng khi fragment không hiển thị
        cleanupCamera();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called");

        // Đánh dấu fragment đang hoạt động
        isFragmentActive = true;

        // Khởi động lại camera executor nếu cần
        try {
            if (cameraExecutor == null || cameraExecutor.isShutdown()) {
                cameraExecutor = Executors.newSingleThreadExecutor();
            }

            // Nếu camera đã dừng và chúng ta có quyền, khởi động lại nó
            if (!isCameraStarted && allPermissionsGranted() && isDetectorInitialized) {
                startCamera();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume", e);
        }
    }

    @Override
    public void onDestroyView() {
        Log.d(TAG, "onDestroyView called");
        isFragmentActive = false;

        // Giải phóng tài nguyên camera trước khi view bị hủy
        cleanupCamera();

        // Đảm bảo không còn tham chiếu đến view
        previewView = null;
        overlayView = null;
        noRobotWarning = null;

        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy called");
        isFragmentActive = false;

        // Giải phóng tất cả tài nguyên
        cleanupCamera();
        cleanupDetectors();
        cleanupExecutor();

        // Giúp GC giải phóng tài nguyên
        robotCommunication = null;

        super.onDestroy();
    }

    @Override
    public void onDetach() {
        Log.d(TAG, "onDetach called");
        isFragmentActive = false;

        super.onDetach();

        // Final cleanup
        cleanupCamera();
        cleanupDetectors();
        cleanupExecutor();
    }

    private void cleanupCamera() {
        try {
            if (cameraProvider != null) {
                Log.d(TAG, "Unbinding all camera use cases");
                cameraProvider.unbindAll();
                cameraProvider = null;
            }
            isCameraStarted = false;
            Log.d(TAG, "Camera cleaned up");
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up camera", e);
        }
    }

    private void cleanupExecutor() {
        try {
            if (cameraExecutor != null) {
                if (!cameraExecutor.isShutdown()) {
                    Log.d(TAG, "Shutting down camera executor");
                    cameraExecutor.shutdownNow(); // Sử dụng shutdownNow thay vì shutdown để dừng ngay lập tức
                }
                cameraExecutor = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error shutting down cameraExecutor", e);
        }
    }
}