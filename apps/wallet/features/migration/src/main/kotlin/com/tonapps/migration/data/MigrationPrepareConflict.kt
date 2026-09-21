package com.tonapps.migration.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class MigrationPrepareErrorBody(
    @SerialName("error_code") val errorCode: Long? = null,
    @SerialName("details") val details: MigrationPrepareErrorDetails? = null,
)

@Serializable
internal data class MigrationPrepareErrorDetails(
    @SerialName("required") val required: Long = 0,
    @SerialName("available") val available: Long? = null,
)
