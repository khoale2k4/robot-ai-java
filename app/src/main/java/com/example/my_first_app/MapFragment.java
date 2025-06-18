package com.example.my_first_app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.AspectRatio;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.gson.Gson;
import com.google.common.util.concurrent.ListenableFuture;

import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.Rot90Op;

public class MapFragment extends Fragment {

    private static final int REQUEST_CODE_LOAD_MAP = 101;
    private static final int REQUEST_CODE_OPEN_MAP = 102;
    private static final String TAG = "MapFragment";

    private ImageView mapView;
    private PreviewView previewView;
    private Button btnLoadMap, btnSaveMap;
    private CustomMapView customMapView;
    private Button btnCancelDestination;
    private ExecutorService cameraExecutor;
    private OverlayViewObjectOnly overlayView;

    private ProcessCameraProvider cameraProvider;
    private boolean isCameraStarted = false;
    private boolean isFragmentActive = false;
    private MapData mapData;
    private List<PointF> currentPath;
    private final Handler simulationHandler = new Handler(Looper.getMainLooper());
    private Runnable simulationRunnable;

    private ObjectDetector objectDetector;
    private YOLOv10Detector yoloDetector;
    private static final String MODEL_SSD_MOBILENET = "ssd_mobilenet_v1_1_metadata_1.tflite";
    private static final String MODEL_MOBILENET_V1 = "mobilenet_v1_1.0_224.tflite";
    private static final String MODEL_YOLOV10N = "yolov10n_float16_old.tflite";
    private boolean isDetectorInitialized = false;
    private String currentModelName = MODEL_YOLOV10N;
    private boolean useYOLOv10 = false;
    private static final String TARGET_LABEL = "person";

    public MapFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        isFragmentActive = true;
    }

    @Override
    public void onPause() {
        isFragmentActive = false;
        super.onPause();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        previewView = view.findViewById(R.id.previewView);
        btnLoadMap = view.findViewById(R.id.btnLoadMap);
        btnSaveMap = view.findViewById(R.id.btnSaveMap);
        mapView = view.findViewById(R.id.mapView);
        customMapView = view.findViewById(R.id.customMapView);
        btnCancelDestination = view.findViewById(R.id.btnCancelDestination);
        overlayView = view.findViewById(R.id.overlayViewObjectOnly);
        btnCancelDestination.setVisibility(View.VISIBLE);
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Khởi động Camera
        setupDetector();
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            if (getContext() != null) {
                Toast.makeText(getContext(), "Camera permission required", Toast.LENGTH_LONG).show();
            }
        }
        // Toast.makeText(getContext(), "Khởi tạo bản đồ", Toast.LENGTH_SHORT).show();

        mapData = new MapData();
        mapData.obstacles = new ArrayList<>();
        mapData.robot = new PointF(50, 50);
        mapData.robotAngle = 90;
        mapData.walls = new ArrayList<>();
        customMapView.setMapData(mapData);

        // Tương tác bản đồ
        customMapView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                currentPath = null;
                PointF point = convertTouchToMapCoordinates(event.getX(), event.getY(), customMapView);
                if (mapData != null) {
                    mapData.destination = point;
                    customMapView.setMapData(mapData);
                    new Thread(() -> {
                        final List<PointF> path = mapData.findPathToDestination();
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                currentPath = path;
                                executePathLoop();
                            });
                        }
                    }).start();
                }
                sendRobotTo(point);
                return true;
            }
            return false;
        });
        btnCancelDestination.setOnClickListener(v -> {
            if (mapData != null) {
                mapData.destination = null;
                currentPath = null;
                customMapView.setMapData(mapData);
            }
        });
        btnLoadMap.setOnClickListener(v -> openMapFilePicker());
        btnSaveMap.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Đã lưu bản đồ!", Toast.LENGTH_SHORT).show();
            // TODO: Lưu map vào local
        });
    }

    private void openMapFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] { "application/json", "text/plain" });
        startActivityForResult(intent, REQUEST_CODE_LOAD_MAP);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_LOAD_MAP && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            if (uri != null) {
                readMapFromUri(uri);
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
        Log.d(TAG, "Giải phóng tài nguyên AI detector");
        try {
            if (yoloDetector != null) {
                yoloDetector.close();
                yoloDetector = null;
                Log.d(TAG, "YOLOv10 detector đã được giải phóng");
            }
            if (objectDetector != null) {
                objectDetector.close();
                objectDetector = null;
                Log.d(TAG, "Task Vision detector đã được giải phóng");
            }
        } catch (Exception e) {
            Log.e(TAG, "Lỗi khi giải phóng tài nguyên detector", e);
        } finally {
            isDetectorInitialized = false;
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
                    // Log.i(TAG, "Đang phân tích");
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
                            // Log.i(TAG, "Tìm thấy gì đó");
                            final List<Detection> results;

                            // if (useYOLOv10) {
                            // // Sử dụng YOLOv10Detector
                            // List<YOLOv10Detector.Detection> yoloResults = yoloDetector.detect(bitmap,
                            // image.getImageInfo().getRotationDegrees());

                            // // Lọc chỉ lấy những detection có label mong muốn
                            // List<YOLOv10Detector.Detection> filteredYoloResults = DetectionAdapter
                            // .filterYOLOv10DetectionsByLabel(yoloResults, TARGET_LABEL);

                            // Log.d(TAG, "YOLOv10 found " + yoloResults.size() + " total detections, "
                            // + filteredYoloResults.size() + " person detections");

                            // // Convert sang format Task Vision API
                            // results = DetectionAdapter.convertYOLOv10Detections(filteredYoloResults);
                            // // results = DetectionAdapter.convertYOLOv10Detections(yoloResults);

                            // Log.d(TAG, "Converted to " + results.size() + " Task Vision detections");
                            // } else {
                            // // Sử dụng Task Vision API ObjectDetector
                            // // Sử dụng TFLite Support Library để xử lý ảnh
                            // TensorImage tensorImage = TensorImage.fromBitmap(bitmap);

                            // // Xoay ảnh nếu cần thiết
                            // ImageProcessor imageProcessor = new ImageProcessor.Builder()
                            // .add(new Rot90Op(-image.getImageInfo().getRotationDegrees() / 90))
                            // .build();
                            // tensorImage = imageProcessor.process(tensorImage);

                            // // Chạy model
                            // List<Detection> taskResults = objectDetector.detect(tensorImage);

                            // // Lọc chỉ lấy những detection có label mong muốn
                            // results = filterDetectionsByLabel(taskResults, TARGET_LABEL);
                            // }
                            // Trong imageAnalyzer, sau khi có kết quả từ model

                            if (useYOLOv10) {
                                List<YOLOv10Detector.Detection> yoloResults = yoloDetector.detect(bitmap,
                                        image.getImageInfo().getRotationDegrees());

                                // Tạm thời không lọc, chuyển đổi tất cả kết quả
                                results = DetectionAdapter.convertYOLOv10Detections(yoloResults);

                            } else {
                                TensorImage tensorImage = TensorImage.fromBitmap(bitmap);
                                List<Detection> taskResults = objectDetector.detect(tensorImage);

                                // Tạm thời không lọc
                                results = taskResults;
                            }

                            // Log.d(TAG, "SỐ LƯỢNG VẬT THỂ PHÁT HIỆN ĐƯỢC: " + results.size());

                            // Cập nhật giao diện trên luồng UI
                            if (isFragmentActive && isAdded() && getActivity() != null && !getActivity().isFinishing()
                                    && overlayView != null) {
                                getActivity().runOnUiThread(() -> {
                                    try {
                                        if (isFragmentActive && isAdded() && overlayView != null) {
                                            String command = overlayView.setResults(results, image.getWidth(),
                                                    image.getHeight());
                                            // sendRobotCommand(command);
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error updating overlay", e);
                                    }
                                });
                            }
                        } else {
                            Log.i(TAG, "Không tìm thấy đối tượng");
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

    private void readMapFromUri(Uri uri) {
        try (InputStream inputStream = getContext().getContentResolver().openInputStream(uri);
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            // Parse JSON thành MapData
            String json = builder.toString();
            Gson gson = new Gson();
            mapData = gson.fromJson(json, MapData.class);

            if (mapData != null && customMapView != null) {
                customMapView.setMapData(mapData);
                Toast.makeText(getContext(), "Đã tải bản đồ!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "File không hợp lệ", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Toast.makeText(getContext(), "Không thể đọc file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
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

    private void sendRobotTo(PointF point) {
        String msg = String.format("Robot đến: (%.1f, %.1f)", point.x, point.y);
        // Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private PointF convertTouchToMapCoordinates(float x, float y, View view) {
        int width = view.getWidth();
        int height = view.getHeight();
        float mapX = (x / width) * 100;
        float mapY = (y / height) * 100;
        return new PointF(mapX, mapY);
    }

    private void startCamera() {
        Log.d("CameraX", "Starting camera...");

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
    }

    private MapData loadMapData(String fileName) {
        try {
            InputStream is = requireContext().getAssets().open("maps/" + fileName);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, StandardCharsets.UTF_8);

            Gson gson = new Gson();
            return gson.fromJson(json, MapData.class);

        } catch (IOException e) {
            e.printStackTrace();
            return null;
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

    private void executePathLoop() {
        // Hàm này không cần thay đổi
        simulationRunnable = new Runnable() {
            @Override
            public void run() {
                // Toast.makeText(getContext(), "RobotControl, Đang chạy hàm run", Toast.LENGTH_SHORT).show();
                if (currentPath == null || currentPath.isEmpty()) {
                    Log.d("RobotControl", "Đã đến đích!");
                    return;
                }
                String command = mapData.getNextCommand(currentPath);
                if (command != null) {
                    Log.d("RobotControl", command);
                    Log.d("Robot position", 
    "x: " + mapData.robot.x +
    ", y: " + mapData.robot.y +
    ", angle: " + mapData.robotAngle);

                    mapData.executeCommand(command);
                    customMapView.setMapData(mapData);
                    sendCommandAndWait(command);
                } else {
                    Log.d("RobotControl", "Hoàn thành đường đi.");
                }
            }
        };
        simulationHandler.post(simulationRunnable);
    }

    private void sendCommandAndWait(String command) {
        // Log.d("RobotControl", "Gửi lệnh: " + command);
        // sendCommandToRealRobot(command); // Hàm gửi lệnh thật của bạn

        // GIẢ LẬP CHỜ KẾT QUẢ: Sau 0.5 giây, gọi lại vòng lặp để lấy lệnh tiếp theo
        simulationHandler.postDelayed(simulationRunnable, 1000);
    }
}
