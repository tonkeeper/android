package com.tonapps.ur.registry;

public enum RegistryType {
    BYTES("bytes", null, byte[].class),
    CRYPTO_HDKEY("crypto-hdkey", 303, CryptoHDKey.class),
    CRYPTO_KEYPATH("crypto-keypath", 304, CryptoKeypath.class),
    CRYPTO_COIN_INFO("crypto-coininfo", 305, CryptoCoinInfo.class),
    TON_SIGN_REQUEST("ton-sign-request", 7201, TonSignRequest.class),
    TON_SIGNATURE("ton-signature", 7202, TonSignature.class);

    private final String type;
    private final Integer tag;
    private final Class registryClass;

    private RegistryType(String type, Integer tag, Class registryClass) {
        this.type = type;
        this.tag = tag;
        this.registryClass = registryClass;
    }

    public String getType() {
        return type;
    }

    public Integer getTag() {
        return tag;
    }

    public Class getRegistryClass() {
        return registryClass;
    }

    @Override
    public String toString() {
        return type;
    }

    public static RegistryType fromString(String type) {
        for(RegistryType registryType : values()) {
            if(registryType.toString().equals(type.toLowerCase())) {
                return registryType;
            }
        }

        throw new IllegalArgumentException("Unknown UR registry type: " + type);
    }
}