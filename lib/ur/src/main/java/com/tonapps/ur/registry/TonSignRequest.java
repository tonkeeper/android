package com.tonapps.ur.registry;

import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnicodeString;
import co.nstant.in.cbor.model.UnsignedInteger;

public class TonSignRequest extends RegistryItem {

    public static final int REQUEST_ID_KEY = 1;
    public static final int SIGN_DATA_KEY = 2;
    public static final int DATA_TYPE_KEY = 3;
    public static final int DERIVATION_PATH_KEY = 4;
    public static final int ADDRESS_KEY = 5;
    public static final int ORIGIN_KEY = 6;

    private final byte[] requestId;
    private final byte[] signData;
    private final Integer dataType;
    private final CryptoKeypath path;
    private final String address;
    private final String origin;

    public TonSignRequest(byte[] requestId, byte[] signData, Integer dataType, CryptoKeypath path, String address, String origin) {
        this.requestId = requestId;
        this.signData = signData;
        this.dataType = dataType;
        this.path = path;
        this.address = address;
        this.origin = origin;
    }

    @Override
    public RegistryType getRegistryType() {
        return RegistryType.TON_SIGN_REQUEST;
    }

    @Override
    public DataItem toCbor() {
        Map map = new Map();
        map.put(new UnsignedInteger(SIGN_DATA_KEY), new ByteString(signData));
        map.put(new UnsignedInteger(DATA_TYPE_KEY), new UnsignedInteger(dataType));

        if (path != null) {
            DataItem pathDataItem = path.toCbor();
            pathDataItem.setTag(RegistryType.CRYPTO_KEYPATH.getTag());
            map.put(new UnsignedInteger(DERIVATION_PATH_KEY), pathDataItem);
        }

        if (requestId != null) {
            DataItem uuid = new ByteString(requestId);
            uuid.setTag(37);
            map.put(new UnsignedInteger(REQUEST_ID_KEY), uuid);
        }

        if (address != null) {
            map.put(new UnsignedInteger(ADDRESS_KEY), new UnicodeString(address));
        }

        if (origin != null) {
            map.put(new UnsignedInteger(ORIGIN_KEY), new UnicodeString(origin));
        }
        return map;
    }
}
