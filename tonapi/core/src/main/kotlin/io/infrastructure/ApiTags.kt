package io.infrastructure

sealed interface ApiTags {
    object DeviceAuth : ApiTags
    data class WalletAuth(val walletId: String) : ApiTags
}