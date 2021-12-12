package com.mxplay.adloader;

public class CompanionSize {
    public final int width;
    public final int height;

    public CompanionSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompanionSize)) return false;
        CompanionSize that = (CompanionSize) o;
        return width == that.width &&
                height == that.height;
    }

    @Override
    public int hashCode() {
        int result = width;
        result = 31 * result + height;
        return result;
    }
}
