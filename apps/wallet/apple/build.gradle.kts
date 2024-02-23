plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("apple")
    dynamicDelivery {
        deliveryType.set("fast-follow")
    }
}