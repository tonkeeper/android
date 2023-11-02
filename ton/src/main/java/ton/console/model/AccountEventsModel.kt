package ton.console.model

data class AccountEventsModel(
    val events: List<AccountEventModel>,
    val nextFrom: Long
) {
}