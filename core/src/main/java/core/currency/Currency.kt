package core.currency

enum class Currency(
    val code: String,
    val symbol: String
) {
    USD("USD", "$"),
    EUR("EUR", "€"),
    RUB("RUB", "₽"),
    AED("AED", "د.إ"),
    KZT("KZT", "₸"),
    UAH("UAH", "₴"),
    UZS("UZS", "лв"),
    GBP("GBP", "£"),
    CHF("CHF", "₣"),
    CNY("CNY", "¥"),
    KRW("KRW", "₩"),
    IDR("IDR", "Rp"),
    INR("INR", "₹"),
    JPY("JPY", "¥"),
}
