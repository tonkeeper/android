package com.tonapps.portfolio.screens.wallet

data class FinishSetupState(
    val lines: List<FinishSetupLine>,
    val skippable: Boolean,
)

enum class FinishSetupLineStatus {
    Pending,
    Complete,
}

sealed class FinishSetupLine {
    abstract val status: FinishSetupLineStatus

    data class Push(
        override val status: FinishSetupLineStatus,
    ) : FinishSetupLine()

    data class Backup(
        override val status: FinishSetupLineStatus,
    ) : FinishSetupLine()

    data class Migration(
        override val status: FinishSetupLineStatus,
        val walletsLeft: Int,
    ) : FinishSetupLine()

    data class Biometry(
        override val status: FinishSetupLineStatus,
    ) : FinishSetupLine()
}
