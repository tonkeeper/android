package com.tonapps.deposit.multicoin.domain

import com.tonapps.deposit.multicoin.data.RampAsset
import com.tonapps.deposit.multicoin.data.RampAssetDetail

interface RampSource {

    suspend fun findDefaultAsset(fiat: String): Result<RampAsset?>

    suspend fun loadAssetDetail(assetId: String): Result<RampAssetDetail?>

    // The app's own description of a token the onramp does not know, without methods; null if the app has none, a
    // failure if the request failed.
    suspend fun appAssetDetail(assetId: String): Result<RampAssetDetail?>

    suspend fun resolveFiat(asset: RampAssetDetail, preferred: String): String

    suspend fun createP2PSessionUrl(assetId: String, fiat: String): Result<String>
}
