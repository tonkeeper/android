package com.tonapps.ur.registry;

import co.nstant.in.cbor.model.DataItem;

public interface CborSerializable {
    DataItem toCbor();
}