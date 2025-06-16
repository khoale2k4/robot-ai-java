package com.example.my_first_app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
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

public class MapFragment extends Fragment {

    private static final int REQUEST_CODE_LOAD_MAP = 101;
    private static final int REQUEST_CODE_OPEN_MAP = 102;
    private static final String TAG = "MapFragment";

    private ImageView mapView;
    private PreviewView previewView;
    private Button btnLoadMap, btnSaveMap;
    private CustomMapView customMapView;
    private Button btnCancelDestination;

    private ProcessCameraProvider cameraProvider;
    private boolean isCameraStarted = false;
    private boolean isFragmentActive = false;
    private MapData mapData;

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
        btnCancelDestination.setVisibility(View.VISIBLE);
        btnCancelDestination.setOnClickListener(v -> {
            if (mapData != null) {
                mapData.destination = null;
                customMapView.setMapData(mapData);
            }
        });

        // Khởi động Camera
        startCamera();
        Toast.makeText(getContext(), "Khởi tạo bản đồ", Toast.LENGTH_SHORT).show();
        customMapView.setMapData(loadMapData("map_1.json"));

        // Tương tác bản đồ
        customMapView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                PointF point = convertTouchToMapCoordinates(event.getX(), event.getY(), customMapView);
                sendRobotTo(point);
                return true;
            }
            return false;
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

        if (requestCode == REQUEST_CODE_LOAD_MAP && resultCode == Activity.RESULT_OK){
            Uri uri = data.getData();
            if (uri != null) {
                readMapFromUri(uri);
            }
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

    private void sendRobotTo(PointF point) {
        String msg = String.format("Robot đến: (%.1f, %.1f)", point.x, point.y);
        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
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

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                // Gỡ camera cũ nếu có
                cameraProvider.unbindAll();

                // Preview
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Chọn camera sau
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                // Gắn camera vào lifecycle
                cameraProvider.bindToLifecycle(this, cameraSelector, preview);
                isCameraStarted = true;

            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraX", "Lỗi khi khởi tạo camera", e);
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

    private void bindCameraUseCases(ProcessCameraProvider provider) {
        provider.unbindAll();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;

        provider.bindToLifecycle(this, selector, preview);
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }
}
