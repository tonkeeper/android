package com.tonapps.ur.registry;

import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnsignedInteger;

public class CryptoCoinInfo extends RegistryItem {
    public static final int TYPE_KEY = 1;
    public static final int NETWORK_KEY = 2;

    private final Integer type;
    private final Integer network;

    public CryptoCoinInfo(Integer type, Integer network) {
        if(network.equals(Network.GOERLI.networkValue) && !type.equals(Type.ETHEREUM.typeValue)) {
            throw new IllegalArgumentException("Goerli network can only be selected for Ethereum");
        }
        this.type = type;
        this.network = network;
    }

    public CryptoCoinInfo(Type type, Network network) {
        if(network == Network.GOERLI && type != Type.ETHEREUM) {
            throw new IllegalArgumentException("Goerli network can only be selected for Ethereum");
        }
        this.type = (type != null ? type.typeValue : null);
        this.network = (network != null ? network.networkValue : null);
    }

    public Type getType() {
        return type == null ? Type.BITCOIN : Type.getTypeFromValue(type);
    }

    public Network getNetwork() {
        return network == null ? Network.MAINNET : Network.getNetworkFromValue(network);
    }

    public DataItem toCbor() {
        Map map = new Map();
        if(type != null) {
            map.put(new UnsignedInteger(TYPE_KEY), new UnsignedInteger(type));
        }
        if(network != null) {
            map.put(new UnsignedInteger(NETWORK_KEY), new UnsignedInteger(network));
        }
        return map;
    }

    @Override
    public RegistryType getRegistryType() {
        return RegistryType.CRYPTO_COIN_INFO;
    }

    public static CryptoCoinInfo fromCbor(DataItem item) {
        Integer type = null;
        Integer network = null;

        Map map = (Map)item;
        for(DataItem key : map.getKeys()) {
            UnsignedInteger uintKey = (UnsignedInteger)key;
            int intKey = uintKey.getValue().intValue();

            if(intKey == TYPE_KEY) {
                type = ((UnsignedInteger)map.get(key)).getValue().intValue();
            } else if(intKey == NETWORK_KEY) {
                network = ((UnsignedInteger)map.get(key)).getValue().intValue();
            }
        }

        return new CryptoCoinInfo(type, network);
    }

    public enum Type {
        BITCOIN(0), ETHEREUM(60);

        Integer typeValue;

        Type(Integer typeValue) {
            this.typeValue = typeValue;
        }

        static Type getTypeFromValue(int value) {
            for (int i = 0; i < Type.values().length; i++) {
                Type current = Type.values()[i];
                if(value == current.typeValue) {
                    return current;
                }
            }
            return null;
        }
    }

    public enum Network {
        MAINNET(0), TESTNET(1), GOERLI(4);

        Integer networkValue;

        Network(Integer networkValue) {
            this.networkValue = networkValue;
        }

        static Network getNetworkFromValue(int value) {
            for (int i = 0; i < Network.values().length; i++) {
                Network current = Network.values()[i];
                if (value == current.networkValue) {
                    return current;
                }
            }
            return null;
        }
    }
}