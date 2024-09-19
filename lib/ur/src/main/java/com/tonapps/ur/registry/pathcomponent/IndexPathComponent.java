package com.tonapps.ur.registry.pathcomponent;

public class IndexPathComponent extends PathComponent {
    private final int index;
    private final boolean hardened;

    public IndexPathComponent(int index, boolean hardened) {
        this.index = index;
        this.hardened = hardened;

        if((index & HARDENED_BIT) != 0) {
            throw new IllegalArgumentException("Invalid index " + index + " - most significant bit cannot be set");
        }
    }

    public int getIndex() {
        return index;
    }

    public boolean isHardened() {
        return hardened;
    }

    public String toString() {
        return index + (hardened ? "'" : "");
    }
}