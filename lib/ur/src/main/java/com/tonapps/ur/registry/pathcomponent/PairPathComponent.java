package com.tonapps.ur.registry.pathcomponent;

public class PairPathComponent extends PathComponent {
    private final IndexPathComponent external;
    private final IndexPathComponent internal;

    public PairPathComponent(IndexPathComponent external, IndexPathComponent internal) {
        this.external = external;
        this.internal = internal;
    }

    public IndexPathComponent getExternal() {
        return external;
    }

    public IndexPathComponent getInternal() {
        return internal;
    }

    public String toString() {
        return "<" + external.toString() + ";" + internal.toString() + ">";
    }
}
