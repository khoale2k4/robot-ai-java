package com.example.my_first_app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.Rot90Op;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class YOLOv10Detector {
    private static final String TAG = "YOLOv10Detector";

    // Model constants
    private static final int INPUT_SIZE = 320;
    private static final int NUM_CLASSES = 80;
    private static final float CONFIDENCE_THRESHOLD = 0.25f;
    private static final float NMS_THRESHOLD = 0.45f;

    // COCO class names
    private static final String[] CLASS_NAMES = {
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
            "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
            "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
            "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
            "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
            "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair",
            "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
            "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
            "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
    };

    private Interpreter interpreter;
    private ImageProcessor imageProcessor;
    private AtomicBoolean isReleased = new AtomicBoolean(false);
    private String modelName;

    // Detection result class
    public static class Detection {
        public RectF bbox;
        public float confidence;
        public int classId;
        public String className;

        public Detection(RectF bbox, float confidence, int classId, String className) {
            this.bbox = bbox;
            this.confidence = confidence;
            this.classId = classId;
            this.className = className;
        }
    }

    public YOLOv10Detector(Context context, String modelPath) throws IOException {
        this.modelName = modelPath;

        try {
            // Load model
            MappedByteBuffer modelBuffer = loadModelFile(context, modelPath);

            // Create interpreter with options
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);
            // Try GPU acceleration
            try {
                options.setUseNNAPI(true);
                Log.i(TAG, "Sử dụng NNAPI acceleration");
            } catch (Exception e) {
                Log.i(TAG, "NNAPI không khả dụng, sử dụng CPU");
            }

            interpreter = new Interpreter(modelBuffer, options);

            // Create image processor
            imageProcessor = new ImageProcessor.Builder()
                    .add(new ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                    .build();

            Log.i(TAG, "YOLOv10Detector khởi tạo thành công");
            Log.i(TAG, "Đã khởi tạo YOLOv10 model: " + modelPath);
        } catch (Exception e) {
            Log.e(TAG, "Lỗi khởi tạo YOLOv10Detector", e);
            close();
            throw e;
        }
    }

    private MappedByteBuffer loadModelFile(Context context, String modelPath) throws IOException {
        try {
            FileInputStream inputStream = new FileInputStream(
                    context.getAssets().openFd(modelPath).getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = context.getAssets().openFd(modelPath).getStartOffset();
            long declaredLength = context.getAssets().openFd(modelPath).getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        } catch (IOException e) {
            Log.e(TAG, "Lỗi khi đọc file model: " + modelPath, e);
            throw e;
        }
    }

    public synchronized List<Detection> detect(Bitmap bitmap, int imageRotation) {
        // Kiểm tra xem detector đã bị giải phóng chưa
        if (isReleased.get() || interpreter == null) {
            Log.w(TAG, "Không thể detect: Detector đã bị giải phóng hoặc chưa được khởi tạo");
            return new ArrayList<>();
        }

        try {
            // Preprocess image
            TensorImage tensorImage = TensorImage.fromBitmap(bitmap);

            // Luôn áp dụng rotation + resize
            ImageProcessor.Builder builder = new ImageProcessor.Builder();
            if (imageRotation != 0) {
                builder.add(new Rot90Op(-imageRotation / 90));
            }
            builder.add(new ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR));
            ImageProcessor processor = builder.build();

            tensorImage = processor.process(tensorImage);

            // Chuẩn bị input buffer
            ByteBuffer inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3);
            inputBuffer.order(ByteOrder.nativeOrder());

            // Chuyển ảnh về buffer float (RGB, normalized)
            int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
            tensorImage.getBitmap().getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);
            for (int pixel : pixels) {
                inputBuffer.putFloat(((pixel >> 16) & 0xFF) / 255.0f); // R
                inputBuffer.putFloat(((pixel >> 8) & 0xFF) / 255.0f); // G
                inputBuffer.putFloat((pixel & 0xFF) / 255.0f); // B
            }

            float[][][] output = new float[1][300][6];

            if (isReleased.get() || interpreter == null) {
                Log.w(TAG, "Không thể chạy inference: Detector đã bị giải phóng");
                return new ArrayList<>();
            }

            interpreter.run(inputBuffer, output);

            return postProcess(output[0], bitmap.getWidth(), bitmap.getHeight());

        } catch (Exception e) {
            Log.e(TAG, "Lỗi khi detect", e);
            return new ArrayList<>();
        }

    }

    private List<Detection> postProcess(float[][] outputs, int originalWidth, int originalHeight) {
        List<Detection> detections = new ArrayList<>();

        Log.d(TAG, "Processing " + outputs.length + " potential detections");
        Log.d(TAG, "Original image size: " + originalWidth + "x" + originalHeight);

        for (int i = 0; i < outputs.length; i++) {
            float[] output = outputs[i];
            // Check if detection is valid (confidence > 0)
            if (output.length < 6)
                continue;

            // Extract detection data - format appears to be [x1, y1, x2, y2, confidence,
            // class_id]
            float x1 = output[0];
            float y1 = output[1];
            float x2 = output[2];
            float y2 = output[3];
            float confidence = output[4];
            float classId = output[5];

            // Log first few detections for debugging
            if (i < 5 && confidence > 0.1f) {
                Log.d(TAG, "Detection " + i + ": bbox=[" + x1 + "," + y1 + "," + x2 + "," + y2 + "], conf=" + confidence
                        + ", class=" + (int) classId);
            }

            // Filter by confidence threshold
            if (confidence < CONFIDENCE_THRESHOLD)
                continue;

            // YOLOv10 coordinates are normalized (0-1), so we need to convert them directly
            // to pixel coordinates
            // without additional scaling since they are already relative to the original
            // image size
            float left = x1 * originalWidth;
            float top = y1 * originalHeight;
            float right = x2 * originalWidth;
            float bottom = y2 * originalHeight;

            // Clamp coordinates to image bounds
            left = Math.max(0, Math.min(left, originalWidth));
            top = Math.max(0, Math.min(top, originalHeight));
            right = Math.max(0, Math.min(right, originalWidth));
            bottom = Math.max(0, Math.min(bottom, originalHeight));

            Log.d(TAG,
                    "Converted coordinates: left=" + left + ", top=" + top + ", right=" + right + ", bottom=" + bottom);

            // Get class name
            int classIdInt = (int) classId;
            String className = (classIdInt >= 0 && classIdInt < CLASS_NAMES.length)
                    ? CLASS_NAMES[classIdInt]
                    : "unknown";

            RectF bbox = new RectF(left, top, right, bottom);
            detections.add(new Detection(bbox, confidence, classIdInt, className));

            Log.d(TAG, "Valid detection: " + className + " (" + confidence + ")");
        }

        Log.d(TAG, "Found " + detections.size() + " valid detections");

        // Since this model appears to have NMS already applied, we may not need
        // additional NMS
        // But let's apply light NMS just in case
        return applyNMS(detections);
    }

    private List<Detection> applyNMS(List<Detection> detections) {
        // Sort by confidence (descending)
        Collections.sort(detections, new Comparator<Detection>() {
            @Override
            public int compare(Detection a, Detection b) {
                return Float.compare(b.confidence, a.confidence);
            }
        });

        List<Detection> nmsResults = new ArrayList<>();
        boolean[] suppressed = new boolean[detections.size()];

        for (int i = 0; i < detections.size(); i++) {
            if (suppressed[i])
                continue;

            nmsResults.add(detections.get(i));

            // Suppress overlapping detections
            for (int j = i + 1; j < detections.size(); j++) {
                if (suppressed[j])
                    continue;

                float iou = calculateIoU(detections.get(i).bbox, detections.get(j).bbox);
                if (iou > NMS_THRESHOLD) {
                    suppressed[j] = true;
                }
            }
        }

        return nmsResults;
    }

    private float calculateIoU(RectF box1, RectF box2) {
        float intersectionLeft = Math.max(box1.left, box2.left);
        float intersectionTop = Math.max(box1.top, box2.top);
        float intersectionRight = Math.min(box1.right, box2.right);
        float intersectionBottom = Math.min(box1.bottom, box2.bottom);

        if (intersectionLeft >= intersectionRight || intersectionTop >= intersectionBottom) {
            return 0.0f;
        }

        float intersectionArea = (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop);
        float box1Area = (box1.right - box1.left) * (box1.bottom - box1.top);
        float box2Area = (box2.right - box2.left) * (box2.bottom - box2.top);
        float unionArea = box1Area + box2Area - intersectionArea;

        return intersectionArea / unionArea;
    }

    public synchronized void close() {
        if (isReleased.getAndSet(true)) {
            return; // Đã được giải phóng rồi
        }

        Log.d(TAG, "Đang giải phóng tài nguyên YOLOv10Detector: " + modelName);
        try {
            if (interpreter != null) {
                interpreter.close();
                interpreter = null;
            }

            imageProcessor = null;

            Log.d(TAG, "Đã giải phóng tài nguyên YOLOv10Detector: " + modelName);
        } catch (Exception e) {
            Log.e(TAG, "Lỗi khi giải phóng tài nguyên YOLOv10Detector", e);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }
}