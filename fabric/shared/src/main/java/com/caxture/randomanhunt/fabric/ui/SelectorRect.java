package com.caxture.randomanhunt.fabric.ui;

public record SelectorRect(int x, int y, int width, int height) {
    public SelectorRect {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("rectangle dimensions cannot be negative");
        }
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public boolean contains(int pointX, int pointY) {
        return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
    }
}
