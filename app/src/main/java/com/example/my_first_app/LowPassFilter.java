// LowPassFilter.java
package com.example.my_first_app;

/**
 * LowPassFilter -> Bộ lọc thông thấp
 * - alpha thuộc 0 tới 1: hệ số làm mượt, alpha nhỏ = mượt hơn nhưng trễ hơn.
 * - Loại bỏ nhiễu cao tần :> từ kết quả detection.
 */
public class LowPassFilter {
    private final float alpha;
    private float state;
    private boolean initialized = false;

    /**
     * @param alpha m làm mượt
     */
    public LowPassFilter(float alpha) {
        this.alpha = alpha;
    }

    /**
     * Lọc giá trị mới
     * @param value Giá trị raw input
     * @return      Giá trị khi lọc xong
     */
    public float filter(float value) {
        if (!initialized) {
            state = value;
            initialized = true;
        } else {
            state = alpha * value + (1 - alpha) * state;
        }
        return state;
    }

    /**
     * Reset state bộ lọc
     */
    public void reset() {
        initialized = false;
    }
}