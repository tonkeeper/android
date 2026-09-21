/**
 * Mirrors Android `CurrencyFormatter.format(value)` for integer minor-unit amounts.
 * String math only — no Number scaling (4270000000 nano must become 4.27, not 4.26).
 *
 * formatTon / formatJetton / formatMinorUnits: full getScale() rules from CurrencyFormat.kt.
 *
 * Keep in sync with scripts/api/compare_ton_balance.js
 */

var GROUPING_SEPARATOR = ',';
var DECIMAL_SEPARATOR = '.';
var ONE_E_MINUS_7_FRACTION = '0000001';
var ONE_E_MINUS_7_FRACTION_LEN = 7;
var TON_FRACTION_DIGITS = 9;

function digitString(amount) {
    if (amount === undefined || amount === null) return '0';
    if (typeof BigInt !== 'undefined' && typeof amount === 'bigint') {
        return amount < BigInt(0) ? (-amount).toString() : amount.toString();
    }
    var s = String(amount).trim();
    if (/^-?\d+$/.test(s)) return s.charAt(0) === '-' ? s.substring(1) : s;
    if (typeof amount === 'number' && Number.isFinite(amount)) {
        var rounded = Math.round(amount);
        if (Math.abs(amount - rounded) < 1e-9) amount = rounded;
        if (Number.isSafeInteger(amount)) return String(Math.trunc(Math.abs(amount)));
        var t = String(amount);
        if (/^-?\d+$/.test(t)) return t.charAt(0) === '-' ? t.substring(1) : t;
    }
    return '0';
}

function splitAmountParts(amount, fractionDigits) {
    var scale = Math.max(0, fractionDigits | 0);
    var amountString = digitString(amount);
    if (scale === 0) {
        return { integer: amountString === '' ? '0' : amountString, fraction: '' };
    }
    var needsPadding = amountString.length <= scale;
    var padded = needsPadding
        ? new Array(scale - amountString.length + 2).join('0') + amountString
        : amountString;
    var splitIndex = padded.length - scale;
    return {
        integer: padded.substring(0, splitIndex) || '0',
        fraction: padded.substring(splitIndex)
    };
}

function isZero(integer, fraction) {
    return integer === '0' && (fraction === '' || /^0+$/.test(fraction));
}

function trimTrailingZerosString(str) {
    var result = str;
    while (result.length > 0 && result.charAt(result.length - 1) === '0') {
        result = result.slice(0, -1);
    }
    return result;
}

function compareDecimalParts(integer, fraction, refInteger, refFraction, refFractionLen) {
    var scale = Math.max(fraction.length, refFractionLen);
    var a = BigInt(integer) * pow10(scale) + BigInt(fraction.padEnd(scale, '0'));
    var b = BigInt(refInteger) * pow10(scale) + BigInt(refFraction.padEnd(scale, '0'));
    if (a < b) return -1;
    if (a > b) return 1;
    return 0;
}

function pow10(exp) {
    var result = BigInt(1);
    var base = BigInt(10);
    var e = exp | 0;
    while (e > 0) {
        result *= base;
        e--;
    }
    return result;
}

function countLeadingZeros(fraction) {
    var i;
    for (i = 0; i < fraction.length; i++) {
        if (fraction.charAt(i) !== '0') break;
    }
    return i;
}

/** Mirrors CurrencyFormat.getScale(value.abs()). */
function getScale(integer, fraction) {
    if (isZero(integer, fraction)) return 0;
    if (BigInt(integer) >= BigInt(1000)) return 0;
    if (BigInt(integer) >= BigInt(1)) return 2;
    if (compareDecimalParts(integer, fraction, '0', ONE_E_MINUS_7_FRACTION, ONE_E_MINUS_7_FRACTION_LEN) <= 0) {
        return 0;
    }
    return countLeadingZeros(fraction) + 3;
}

function applyGrouping(integer) {
    if (integer.length <= 3) return integer;
    var parts = [];
    var index = integer.length;
    while (index > 0) {
        var start = Math.max(0, index - 3);
        parts.push(integer.substring(start, index));
        index = start;
    }
    parts.reverse();
    return parts.join(GROUPING_SEPARATOR);
}

function formatWithScale(integer, fraction, scale) {
    if (scale <= 0) {
        return applyGrouping(integer);
    }
    var truncated = fraction.substring(0, Math.min(scale, fraction.length));
    truncated = trimTrailingZerosString(truncated);
    if (truncated === '') {
        return applyGrouping(integer);
    }
    return applyGrouping(integer) + DECIMAL_SEPARATOR + truncated;
}

function formatMinorUnits(amount, fractionDigits) {
    var parts = splitAmountParts(amount, fractionDigits);
    return formatWithScale(parts.integer, parts.fraction, getScale(parts.integer, parts.fraction));
}

/**
 * TON balance on wallet screen: CurrencyFormatter.format(value = uiBalance).
 * Mirrors CurrencyFormat.format(): scale comes from getScale() (DOWN truncation),
 * so tiny amounts keep dynamic precision (e.g. 0.00000402), not a fixed 2 dp.
 */
function formatTon(amount) {
    return formatMinorUnits(amount, TON_FRACTION_DIGITS);
}

function formatJetton(amount, decimals) {
    var d = decimals === undefined || decimals === null ? TON_FRACTION_DIGITS : Number(decimals);
    return formatMinorUnits(amount, d);
}

output.formatter = {
    formatJetton: formatJetton,
    formatTon: formatTon,
    formatMinorUnits: formatMinorUnits
};

(function maestroFormatterSanityCheck() {
    var checks = [
        [2265256703, '2.26'],
        [4029, '0.00000402'],
        [4270000000, '4.27'],
        [4269999999, '4.26'],
        [1000000000000, '1,000']
    ];
    for (var i = 0; i < checks.length; i++) {
        var got = output.formatter.formatTon(checks[i][0]);
        if (got !== checks[i][1]) {
            throw new Error(
                'formatter.js: formatTon(' + checks[i][0] + ') expected "' + checks[i][1] + '", got "' + got + '"'
            );
        }
    }
})();
