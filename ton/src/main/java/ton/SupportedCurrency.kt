package ton

import java.util.Currency

enum class SupportedCurrency(
    val code: String,
    val currency: Currency = Currency.getInstance(code),
    val symbol: String = currency.symbol
) {
    USD("USD"),
    EUR("EUR"),
    RUB("RUB"),
    AED("AED"),
    UAH("UAH"),
    UZS("UZS"),
    GBP("GBP"),
    CHF("CHF"),
    CNY("CNY"),
    KRW("KRW"),
    IDR("IDR"),
    INR("INR"),
    JPY("JPY"),
    TON("TON"),
    // BTC("BTC");
}