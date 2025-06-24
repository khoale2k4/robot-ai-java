package com.example.my_first_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.view.ViewGroup;
import android.graphics.PointF;

import java.util.ArrayList;

import androidx.fragment.app.Fragment;

import com.example.my_first_app.R;

public class InteractiveMapFragment extends Fragment {
    private InteractiveMapView interactiveMapView;
    private boolean creatingMap = true;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_auto, container, false);

        // Lấy reference đến InteractiveMapView từ layout
        interactiveMapView = view.findViewById(R.id.interactiveMapView);
        Button btnToggleMode = view.findViewById(R.id.btnToggleMode);

        // Tạo dữ liệu map
        MapData2 data = new MapData2();
        data.robot = new PointF(500, 1000);
        data.robotAngle = 0;
        data.walls = new ArrayList<>();

        // Gán dữ liệu cho map view
        if (interactiveMapView != null) {
            interactiveMapView.setMapData(data);
        }

        btnToggleMode.setOnClickListener(v -> {
            // Đảo ngược trạng thái hiện tại
            creatingMap = !creatingMap;

            // Cập nhật trạng thái cho InteractiveMapView
            if (interactiveMapView != null) {
                interactiveMapView.setCreating(creatingMap);
            }

            // Cập nhật văn bản của nút dựa trên trạng thái mới
            // Nếu creatingMap là true (chế độ xây dựng), nút sẽ hiển thị "Tự hành"
            // Nếu creatingMap là false (chế độ tự hành), nút sẽ hiển thị "Xây dựng bản đồ"
            if (creatingMap) {
                btnToggleMode.setText("Tự hành");
            } else {
                btnToggleMode.setText("Xây dựng bản đồ");
            }
        });

        return view;
    }
}
