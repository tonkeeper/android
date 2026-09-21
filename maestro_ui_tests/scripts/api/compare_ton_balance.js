// Single script: formatter + API in one GraalVM context (avoids output.formatter GC across runScript calls).
// Keep formatter logic in sync with scripts/utils/formatter.js.

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

function compareDecimalParts(integer, fraction, refInteger, refFraction, refFractionLen) {
    var scale = Math.max(fraction.length, refFractionLen);
    var a = BigInt(integer) * pow10(scale) + BigInt(fraction.padEnd(scale, '0'));
    var b = BigInt(refInteger) * pow10(scale) + BigInt(refFraction.padEnd(scale, '0'));
    if (a < b) return -1;
    if (a > b) return 1;
    return 0;
}

function countLeadingZeros(fraction) {
    var i;
    for (i = 0; i < fraction.length; i++) {
        if (fraction.charAt(i) !== '0') break;
    }
    return i;
}

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

// Mirrors CurrencyFormat.format(): scale from getScale() (DOWN truncation).
function formatTon(amount) {
    var parts = splitAmountParts(amount, TON_FRACTION_DIGITS);
    return formatWithScale(parts.integer, parts.fraction, getScale(parts.integer, parts.fraction));
}

var response = http.request('https://block.tonapi.io/v2/accounts/' + addr, {
    method: 'GET',
    headers: {
        'Authorization': 'Bearer ' + auth_token,
        'Content-Type': 'application/json'
    }
});

if (!response || response.status < 200 || response.status >= 300) {
    var status = response && response.status != null ? response.status : 'no response';
    throw new Error('compare_ton_balance: tonapi request failed (' + status + ')');
}

var accData = json(response.body);
var balance = accData.balance != null ? String(accData.balance) : '0';

output.balance = balance;
output.status = accData.status;
output.walletAddress = addr;
output.screenTonValue = formatTon(balance);
