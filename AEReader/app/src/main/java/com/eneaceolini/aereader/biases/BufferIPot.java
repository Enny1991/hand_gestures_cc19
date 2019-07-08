package com.eneaceolini.aereader.biases;

public class BufferIPot {

    final int max = 63; // 8 bits
    private volatile int value;
    private final String key = "CochleaAMS1c.Biasgen.BufferIPot.value";

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        if (value > max) {
            value = max;
        } else if (value < 0) {
            value = 0;
        }

        this.value = value;

    }

    @Override
    public String toString() {
        return String.format("BufferIPot with max=%d, value=%d", max, value);
    }


}