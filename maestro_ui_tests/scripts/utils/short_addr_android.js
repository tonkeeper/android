// Matches ui.shortAddress (kmp/ui) for "..." and
// com.tonapps.tonkeeper.api.shortAddress for "…".
// Confirmation / receive: 4+4 and three dots; history cells pass separator "…".
var rawPrefix = typeof prefixLength === "undefined" ? "" : prefixLength;
var rawSuffix = typeof suffixLength === "undefined" ? "" : suffixLength;
var prefixLen = parseInt(rawPrefix, 10);
var suffixLen = parseInt(rawSuffix, 10);
if (isNaN(prefixLen)) prefixLen = 4;
if (isNaN(suffixLen)) suffixLen = 4;

var sep = typeof separator === "undefined" || separator === "" ? "..." : separator;

var hexPrefix = addr.toLowerCase().indexOf("0x") === 0 ? addr.substring(0, 2) : "";
var body = addr.substring(hexPrefix.length);
if (body.length <= prefixLen + suffixLen) {
    output.shortAddrRecieve = addr;
} else {
    output.shortAddrRecieve = hexPrefix + body.substring(0, prefixLen) + sep + body.substring(body.length - suffixLen);
}
