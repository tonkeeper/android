package com.tonapps.ur.registry;

import com.tonapps.ur.registry.pathcomponent.PathComponent;

import co.nstant.in.cbor.model.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

public class CryptoKeypath extends RegistryItem {
    public static final int COMPONENTS_KEY = 1;
    public static final int SOURCE_FINGERPRINT_KEY = 2;
    public static final int DEPTH_KEY = 3;

    private final List<PathComponent> components;
    private final byte[] sourceFingerprint;
    private final Integer depth;

    public CryptoKeypath(List<PathComponent> components, byte[] sourceFingerprint) {
        this(components, sourceFingerprint, null);
    }

    public CryptoKeypath(List<PathComponent> components, byte[] sourceFingerprint, Integer depth) {
        this.components = components;
        this.sourceFingerprint = sourceFingerprint == null ? null : Arrays.copyOfRange(sourceFingerprint, sourceFingerprint.length - 4, sourceFingerprint.length);
        this.depth = depth;
    }

    public List<PathComponent> getComponents() {
        return components;
    }

    public String getPath() {
        if(components.isEmpty()) {
            return null;
        }

        StringJoiner joiner = new StringJoiner("/");
        for(PathComponent component : components) {
            joiner.add(component.toString());
        }
        return joiner.toString();
    }

    public byte[] getSourceFingerprint() {
        return sourceFingerprint;
    }

    public Integer getDepth() {
        return depth;
    }

    public DataItem toCbor() {
        Map map = new Map();
        map.put(new UnsignedInteger(COMPONENTS_KEY), PathComponent.toCbor(components));
        if(sourceFingerprint != null) {
            map.put(new UnsignedInteger(SOURCE_FINGERPRINT_KEY), new UnsignedInteger(new BigInteger(1, sourceFingerprint)));
        }
        if(depth != null) {
            map.put(new UnsignedInteger(DEPTH_KEY), new UnsignedInteger(depth));
        }
        return map;
    }

    @Override
    public RegistryType getRegistryType() {
        return RegistryType.CRYPTO_KEYPATH;
    }

    public static CryptoKeypath fromCbor(DataItem item) {
        List<PathComponent> components = new ArrayList<>();
        byte[] sourceFingerprint = null;
        Integer depth = null;

        Map map = (Map)item;
        for(DataItem key : map.getKeys()) {
            UnsignedInteger uintKey = (UnsignedInteger)key;
            int intKey = uintKey.getValue().intValue();
            if(intKey == COMPONENTS_KEY) {
                components = PathComponent.fromCbor(map.get(key));
            } else if(intKey == SOURCE_FINGERPRINT_KEY) {
                sourceFingerprint = bigIntegerToBytes(((UnsignedInteger)map.get(key)).getValue(), 4);
            } else if(intKey == DEPTH_KEY) {
                depth = ((UnsignedInteger)map.get(key)).getValue().intValue();
            }
        }

        return new CryptoKeypath(components, sourceFingerprint, depth);
    }
}