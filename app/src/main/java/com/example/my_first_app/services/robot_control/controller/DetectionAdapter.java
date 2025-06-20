package com.example.my_first_app;

import android.graphics.RectF;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.support.label.Category;
import java.util.ArrayList;
import java.util.List;

public class DetectionAdapter {
    
    /**
     * Convert YOLOv10Detector.Detection to TensorFlow Lite Task Vision Detection
     */
    public static List<Detection> convertYOLOv10Detections(List<YOLOv10Detector.Detection> yoloDetections) {
        List<Detection> taskDetections = new ArrayList<>();
        
        for (YOLOv10Detector.Detection yoloDetection : yoloDetections) {
            // Create Category for the class
            Category category = Category.create(
                yoloDetection.className,          // label
                yoloDetection.className,          // displayName
                yoloDetection.confidence          // score
            );
            
            List<Category> categories = new ArrayList<>();
            categories.add(category);
            
            // Create Detection with bounding box and categories
            Detection detection = Detection.create(
                yoloDetection.bbox,               // boundingBox
                categories                        // categories
            );
            
            taskDetections.add(detection);
        }
        
        return taskDetections;
    }
    
    /**
     * Filter detections to only include specific target label
     */
    public static List<YOLOv10Detector.Detection> filterYOLOv10DetectionsByLabel(
            List<YOLOv10Detector.Detection> detections, String targetLabel) {
        List<YOLOv10Detector.Detection> filteredResults = new ArrayList<>();
        
        for (YOLOv10Detector.Detection detection : detections) {
            if (detection.className.toLowerCase().equals(targetLabel.toLowerCase())) {
                filteredResults.add(detection);
            }
        }
        
        return filteredResults;
    }
} 