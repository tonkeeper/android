package com.tonapps.ur.registry;

import android.util.Log;

import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnicodeString;
import co.nstant.in.cbor.model.UnsignedInteger;

public class TonSignature extends RegistryItem {

    public static final int REQUEST_ID_KEY = 1;
    public static final int SIGNATURE_KEY = 2;
    public static final int ORIGIN_KEY = 3;

    private final byte[] signature;
    private final byte[] requestId;
    private final String origin;

    public TonSignature(byte[] signature, byte[] requestId, String origin) {
        this.signature = signature;
        this.requestId = requestId;
        this.origin = origin;
    }

    public byte[] getSignature() {
        return signature;
    }

    public byte[] getRequestId() {
        return requestId;
    }

    public String getOrigin() {
        return origin;
    }

    @Override
    public RegistryType getRegistryType() {
        return RegistryType.TON_SIGNATURE;
    }

    @Override
    public DataItem toCbor() {
        return null;
    }

    public static TonSignature fromCbor(DataItem item) {
        Map map = (Map)item;
        byte[] signature = null;
        byte[] requestId = null;
        String origin = null;
        for(DataItem key : map.getKeys()) {
            UnsignedInteger uintKey = (UnsignedInteger)key;
            int intKey = uintKey.getValue().intValue();
            if (intKey == REQUEST_ID_KEY) {
                requestId = ((ByteString)map.get(key)).getBytes();
            } else if (intKey == SIGNATURE_KEY) {
                signature = ((ByteString)map.get(key)).getBytes();
            } else if (intKey == ORIGIN_KEY) {
                origin = ((UnicodeString)map.get(key)).getString();
            }
        }
        return new TonSignature(signature, requestId, origin);
    }
}
