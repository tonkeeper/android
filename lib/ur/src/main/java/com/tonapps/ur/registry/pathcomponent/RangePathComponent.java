package com.tonapps.ur.registry.pathcomponent;

public class RangePathComponent extends PathComponent {
    private final int start;
    private final int end;
    private final boolean hardened;

    public RangePathComponent(int start, int end, boolean hardened) {
        this.start = start;
        this.end = end;
        this.hardened = hardened;

        if((start & HARDENED_BIT) != 0 || (end & HARDENED_BIT) != 0) {
            throw new IllegalArgumentException("Invalid range [" + start + ", " + end + "] - most significant bit cannot be set");
        }

        if(start >= end) {
            throw new IllegalArgumentException("Invalid range [" + start + ", " + end + "] - start must be lower than end");
        }
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public boolean isHardened() {
        return hardened;
    }

    public String toString() {
        return "[" + start + (hardened ? "'" : "") + "-" + end + (hardened ? "'" : "") + "]";
    }
}