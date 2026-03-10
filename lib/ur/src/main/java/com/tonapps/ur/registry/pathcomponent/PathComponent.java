package com.tonapps.ur.registry.pathcomponent;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnsignedInteger;

import java.util.ArrayList;
import java.util.List;

public abstract class PathComponent {
    public static final int HARDENED_BIT = 0x80000000;

    public static DataItem toCbor(List<PathComponent> components) {
        Array componentArray = new Array();
        for(PathComponent pathComponent : components) {
            if(pathComponent instanceof WildcardPathComponent) {
                WildcardPathComponent wildcardPathComponent = (WildcardPathComponent)pathComponent;
                componentArray.add(new Array());
                componentArray.add(wildcardPathComponent.isHardened() ? SimpleValue.TRUE : SimpleValue.FALSE);
            } else if(pathComponent instanceof RangePathComponent) {
                RangePathComponent rangePathComponent = (RangePathComponent)pathComponent;
                Array array = new Array();
                array.add(new UnsignedInteger(rangePathComponent.getStart()));
                array.add(new UnsignedInteger(rangePathComponent.getEnd()));
                componentArray.add(array);
                componentArray.add(rangePathComponent.isHardened() ? SimpleValue.TRUE : SimpleValue.FALSE);
            } else if(pathComponent instanceof PairPathComponent) {
                PairPathComponent pairPathComponent = (PairPathComponent)pathComponent;
                Array array = new Array();
                array.add(new UnsignedInteger(pairPathComponent.getExternal().getIndex()));
                array.add(pairPathComponent.getExternal().isHardened() ? SimpleValue.TRUE : SimpleValue.FALSE);
                array.add(new UnsignedInteger(pairPathComponent.getInternal().getIndex()));
                array.add(pairPathComponent.getInternal().isHardened() ? SimpleValue.TRUE : SimpleValue.FALSE);
                componentArray.add(array);
            } else if(pathComponent instanceof IndexPathComponent) {
                IndexPathComponent indexPathComponent = (IndexPathComponent)pathComponent;
                componentArray.add(new UnsignedInteger(indexPathComponent.getIndex()));
                componentArray.add(indexPathComponent.isHardened() ? SimpleValue.TRUE : SimpleValue.FALSE);
            } else {
                throw new IllegalArgumentException("Unknown path component of " + pathComponent.getClass());
            }
        }

        return componentArray;
    }

    public static List<PathComponent> fromCbor(DataItem item) {
        List<PathComponent> components = new ArrayList<>();

        Array componentArray = (Array)item;
        for(int i = 0; i < componentArray.getDataItems().size(); i++) {
            DataItem component = componentArray.getDataItems().get(i);
            if(component instanceof Array) {
                Array subcomponentArray = (Array)component;
                if(subcomponentArray.getDataItems().isEmpty()) {
                    boolean hardened = (componentArray.getDataItems().get(++i) == SimpleValue.TRUE);
                    components.add(new WildcardPathComponent(hardened));
                } else if(subcomponentArray.getDataItems().size() == 2) {
                    boolean hardened = (componentArray.getDataItems().get(++i) == SimpleValue.TRUE);
                    UnsignedInteger startIndex = (UnsignedInteger)subcomponentArray.getDataItems().get(0);
                    UnsignedInteger endIndex = (UnsignedInteger)subcomponentArray.getDataItems().get(1);
                    components.add(new RangePathComponent(startIndex.getValue().intValue(), endIndex.getValue().intValue(), hardened));
                } else if(subcomponentArray.getDataItems().size() == 4) {
                    UnsignedInteger externalIndex = (UnsignedInteger)subcomponentArray.getDataItems().get(0);
                    boolean externalHardened = (subcomponentArray.getDataItems().get(1) == SimpleValue.TRUE);
                    IndexPathComponent externalPathComponent = new IndexPathComponent(externalIndex.getValue().intValue(), externalHardened);
                    UnsignedInteger internalIndex = (UnsignedInteger)subcomponentArray.getDataItems().get(2);
                    boolean internalHardened = (subcomponentArray.getDataItems().get(3) == SimpleValue.TRUE);
                    IndexPathComponent internalPathComponent = new IndexPathComponent(internalIndex.getValue().intValue(), internalHardened);
                    components.add(new PairPathComponent(externalPathComponent, internalPathComponent));
                }
            } else if(component instanceof UnsignedInteger) {
                UnsignedInteger index = (UnsignedInteger)component;
                boolean hardened = (componentArray.getDataItems().get(++i) == SimpleValue.TRUE);
                components.add(new IndexPathComponent(index.getValue().intValue(), hardened));
            }
        }

        return components;
    }
}