#!/usr/bin/env python3
"""
Dump cross-chain swap *prepare* payloads from swap.tonkeeper.com.

For every test pair below this script:
  1. POST /v2/crosschain/quotes              -> a quote with N routes
  2. de-duplicates the routes by (aggregator, protocol)  ["provider distinct"]
  3. POST /v2/crosschain/routes/{route_id}/prepare for each distinct route
  4. writes the full signing payloads + human summaries to:
        swap_prepare_payloads.txt   (human-readable report)

Source assets exercised (per the brief): BTC, ETH (+ERC20 from), TON
(+jetton from), TRON (+TRC20 from). Each is paired with a same-chain
destination (which prepares cleanly) and, where relevant, a cross-chain
destination into TON (the Tonkeeper use-case).

Test wallets are the dummy addresses borrowed from swap_quotes_compare.py
— they only need to be format-valid. NOTE: the LiFi TON dummy
(`UQAJ...`) has an invalid CRC and is rejected by /prepare; the
checksum-valid `EQDtFp...` address is used instead.

Observed provider behaviour (2026-06, mainnet):
  - omniston (TON same-chain)        -> prepares ton_boc payloads.
  - swapkit  (EVM/TRON same-chain)   -> prepares evm_tx / tron_tx (many protocols).
  - swapkit/near_intents cross-chain -> rejects dummy addresses ("route_expired").
  - ERC20/TRC20 as *source*          -> swapkit may answer "unknown decimals".
  - swapsxyz                         -> frequently provider_unavailable (upstream timeout).
The report records whatever each provider actually returns — successes and errors.

Usage:
    python3 swap_prepare_dump.py
"""

import json
import os
import subprocess
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------

BASE_URL = "https://swap.tonkeeper.com"
SLIPPAGE_BPS = 300          # 3%
HTTP_TIMEOUT = 40
MAX_WORKERS = 12            # cases run concurrently; each case's quote->prepare stays sequential

# De-dup routes before preparing. "aggregator_protocol" keeps every distinct
# underlying protocol (1inch/chainflip/near_intents/...) under each aggregator;
# "aggregator" collapses to one route per aggregator (strict "provider distinct").
DEDUP_BY = "aggregator_protocol"

OUT_DIR = os.getcwd()          # write into the directory the script is invoked from
OUT_TXT = os.path.join(OUT_DIR, "swap_prepare_payloads.txt")

# Test wallets (dummy, format-valid only — never touched).
ADDR = {
    "btc":  "bc1q9d4ywgfnd8h43da5tpcxcn6ajv590cg6d3tg6axemvljvt2k76zs50tv4q",
    "eth":  "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045",
    "arb":  "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045",  # Arbitrum: EVM, same shape as eth
    "ton":  "EQBoRoyuTIex_onZwZimvMCPqFhdwwG96gyQLGuHFNRY6neF",
    "tron": "TSV12ENnpKarxKDEoB7tYWuQQ6Qx6ZBAmC",
}

# Internal asset ids (see the /v2/crosschain/quotes request schema).
USDT_ERC20 = "eth/mainnet/erc20/0xdAC17F958D2ee523a2206206994597C13D831ec7"
USDT_JETTON = "ton/mainnet/jetton/0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe"
USDT_TRC20 = "tron/mainnet/trc20/TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
# USDT on Arbitrum (exposed as USDT0 in the assets list — same contract).
USDT_ARB = "arb/mainnet/erc20/0xfd086bc7cd5c481dcc9c85ebe478a1c0b69fcbb9"

# Decimals per asset id, used to render human-readable amounts in the report.
DECIMALS = {
    "btc/mainnet/coin": 8,
    "eth/mainnet/coin": 18,
    "ton/mainnet/coin": 9,
    "tron/mainnet/coin": 6,
    USDT_ERC20: 6,
    USDT_JETTON: 6,
    USDT_TRC20: 6,
    USDT_ARB: 6,
}

# The chain key (-> ADDR) that owns the sender/recipient for a given asset id.
def chain_of(asset_id: str) -> str:
    return asset_id.split("/", 1)[0]


# ---------------------------------------------------------------------------
# Test matrix: (label, source_asset, source_amount_raw, destination_asset)
# ---------------------------------------------------------------------------

CASES = [
    # Exactly one swap per native coin and per token, on each chain.
    # Cross-chain target = ETH; sources already on Ethereum target BTC instead
    # (a source can't be swapped to its own chain's native).
    # Amounts ~ $20 worth (just above the measured ~$20 provider floor) — we
    # only need the prepare payload shape, not a good quote.
    # --- btc chain ---
    ("BTC -> ETH",         "btc/mainnet/coin",  "40000",              "eth/mainnet/coin"),     # 0.0004 BTC (~$40)
    # --- eth chain: coin + token ---
    ("ETH -> USDT-arb",    "eth/mainnet/coin",  "8000000000000000",   USDT_ARB),               # 0.008 ETH -> USDT (Arbitrum)
    ("USDT-ERC20 -> ETH",  USDT_ERC20,          "40000000",           "btc/mainnet/coin"),     # 20 USDT
    # --- ton chain: coin + token ---
    ("TON -> ETH",         "ton/mainnet/coin",  "8000000000",         "eth/mainnet/coin"),     # 8 TON
    ("USDT-jetton -> TON", USDT_JETTON,         "20000000",           "eth/mainnet/coin"),     # 20 USDT
    # --- tron chain: coin + token ---
    ("TRON -> ETH",        "tron/mainnet/coin", "100000000",          "eth/mainnet/coin"),     # 100 TRX
    ("USDT-TRC20 -> ETH",  USDT_TRC20,          "20000000",           "eth/mainnet/coin"),     # 20 USDT
]


# ---------------------------------------------------------------------------
# HTTP (curl bypasses macOS system-Python LibreSSL issues)
# ---------------------------------------------------------------------------

def post_json(path: str, payload: dict) -> dict:
    url = BASE_URL + path
    cmd = ["curl", "-sS", "--max-time", str(HTTP_TIMEOUT), "-X", "POST", url,
           "-H", "Content-Type: application/json", "-w", "\n__HTTP__%{http_code}",
           "--data", json.dumps(payload)]
    r = subprocess.run(cmd, capture_output=True, text=True)
    body, _, status = r.stdout.rpartition("\n__HTTP__")
    code = int(status) if status.strip().isdigit() else 0
    try:
        data = json.loads(body)
    except json.JSONDecodeError:
        data = {"error": f"non-JSON response: {body[:200]}", "code": "bad_response"}
    data["_http_status"] = code
    return data


# ---------------------------------------------------------------------------
# Core
# ---------------------------------------------------------------------------

def human_amount(raw: str, asset_id: str) -> str:
    dec = DECIMALS.get(asset_id)
    if dec is None or not str(raw).isdigit():
        return str(raw)
    val = int(raw) / (10 ** dec)
    return f"{val:.8f}".rstrip("0").rstrip(".")


def dedup_routes(routes: list) -> list:
    seen = set()
    out = []
    for r in routes:
        if DEDUP_BY == "aggregator":
            key = r.get("aggregator")
        else:
            key = (r.get("aggregator"), r.get("protocol"))
        if key in seen:
            continue
        seen.add(key)
        out.append(r)
    return out


def run_case(label, source_asset, source_amount, destination_asset) -> dict:
    sender = ADDR[chain_of(source_asset)]
    recipient = ADDR[chain_of(destination_asset)]
    quote_req = {
        "source_asset": source_asset,
        "source_amount": source_amount,
        "destination_asset": destination_asset,
        "sender_address": sender,
        "recipient_address": recipient,
        "slippage_bps": SLIPPAGE_BPS,
        "exact_type": "exact_input",
        "return_deposit_address": True,
        "include_payload": True,
    }
    quote = post_json("/v2/crosschain/quotes", quote_req)
    result = {
        "label": label,
        "request": quote_req,
        "human_source_amount": human_amount(source_amount, source_asset),
        "quote_http_status": quote.get("_http_status"),
        "quote_id": quote.get("quote_id"),
        "provider_errors": quote.get("provider_errors"),
        "quote_error": quote.get("error"),
        "routes": [],
    }
    routes = quote.get("routes") or []
    for route in dedup_routes(routes):
        rid = route.get("route_id")
        prepare = post_json(f"/v2/crosschain/routes/{rid}/prepare", {})
        result["routes"].append({
            "aggregator": route.get("aggregator"),
            "protocol": route.get("protocol"),
            "route_type": route.get("route_type"),
            "route_id": rid,
            "estimated_destination_amount": route.get("estimated_destination_amount"),
            "minimum_destination_amount": route.get("minimum_destination_amount"),
            "human_estimated_out": human_amount(
                route.get("estimated_destination_amount") or "", destination_asset),
            "estimated_time": route.get("estimated_time"),
            "total_slippage_bps": route.get("total_slippage_bps"),
            "value_difference_bps": route.get("value_difference_bps"),
            "tags": route.get("tags"),
            "fees": route.get("fees"),
            "prepare_http_status": prepare.get("_http_status"),
            "prepare": prepare,
        })
    return result


# ---------------------------------------------------------------------------
# Rendering
# ---------------------------------------------------------------------------

H1 = "=" * 100
H2 = "-" * 100


def render_human_summary(hs: dict, indent="        ") -> list:
    lines = []
    for k in ("action", "spend_asset", "spend_amount", "receive_asset",
              "receive_amount", "recipient_address", "deposit_address", "memo",
              "protocol", "approval_spender", "approval_amount"):
        if hs.get(k) not in (None, ""):
            lines.append(f"{indent}{k:18}: {hs[k]}")
    for w in hs.get("warnings") or []:
        lines.append(f"{indent}{'warning':18}: {w}")
    return lines


def render_fees(fees) -> str:
    if not fees:
        return "—"
    parts = []
    for f in fees:
        amt = f.get("amount")
        usd = f.get("amount_usd")
        seg = f"{f.get('type')}={amt} {f.get('asset','')}".strip()
        if usd:
            seg += f" (${usd})"
        parts.append(seg)
    return "; ".join(parts)


def render_report(cases: list, started_at: str) -> str:
    out = []
    out.append(H1)
    out.append("  CROSS-CHAIN SWAP PREPARE PAYLOADS")
    out.append(f"  base       : {BASE_URL}")
    out.append(f"  generated  : {started_at}")
    out.append(f"  slippage   : {SLIPPAGE_BPS} bps")
    out.append(f"  dedup      : {DEDUP_BY}")
    out.append(H1)

    for idx, case in enumerate(cases, 1):
        out.append("")
        out.append("")
        out.append(H1)
        out.append(f"  SWAP REQUEST #{idx}:  {case['label']}")
        out.append(H1)
        req = case["request"]
        out.append(f"  source     : {req['source_asset']}  "
                   f"({case['human_source_amount']}  raw={req['source_amount']})")
        out.append(f"  destination: {req['destination_asset']}")
        out.append(f"  sender     : {req['sender_address']}")
        out.append(f"  recipient  : {req['recipient_address']}")
        out.append(f"  quote      : HTTP {case['quote_http_status']}  "
                   f"quote_id={case['quote_id']}")
        if case.get("quote_error"):
            out.append(f"  quote ERROR: {case['quote_error']}")
        if case.get("provider_errors"):
            for pe in case["provider_errors"]:
                out.append(f"  provider err: {pe.get('aggregator')}/"
                           f"{pe.get('protocol') or '-'}: "
                           f"{pe.get('code')} — {pe.get('message')}")
        out.append("  json (one line) : " + json.dumps(
            case, ensure_ascii=False, separators=(",", ":")))
        if not case["routes"]:
            out.append("")
            out.append("  (no routes returned)")
        else:
            out.append("")

        for route in case["routes"]:
            out.append(H2)
            out.append(f"  ▸ {route['aggregator']} / {route['protocol']}  "
                       f"[{route.get('route_type')}]")
            out.append(f"    route_id   : {route['route_id']}")
            out.append(f"    est. out   : {route['human_estimated_out']}  "
                       f"(raw={route['estimated_destination_amount']}, "
                       f"min={route['minimum_destination_amount']})")
            et = route.get("estimated_time") or {}
            if et:
                out.append(f"    est. time  : {et.get('total_seconds')}s")
            if route.get("value_difference_bps") is not None:
                out.append(f"    value diff : {route['value_difference_bps']} bps")
            if route.get("tags"):
                out.append(f"    tags       : {', '.join(route['tags'])}")
            out.append(f"    fees       : {render_fees(route.get('fees'))}")

            prep = route["prepare"]
            payloads = prep.get("payloads")
            if payloads is None:
                out.append(f"    PREPARE ERR: HTTP {route['prepare_http_status']}  "
                           f"{prep.get('code')} — {prep.get('error')}")
                continue
            out.append(f"    PREPARE OK : HTTP {route['prepare_http_status']}  "
                       f"{len(payloads)} payload(s)")
            for i, pl in enumerate(payloads):
                out.append("")
                out.append(f"      payload[{i}] id={pl.get('payload_id')}")
                out.append(f"        kind            : {pl.get('kind')}")
                out.append(f"        chain_id        : {pl.get('chain_id')}")
                out.append(f"        chain_family    : {pl.get('chain_family')}")
                out.append(f"        payload_type    : {pl.get('payload_type')}")
                out.append(f"        validation      : {pl.get('validation_status')}")
                out.append(f"        date_expire     : {pl.get('date_expire')}")
                hs = pl.get("human_summary") or {}
                if hs:
                    out.append("        human_summary   :")
                    out.extend(render_human_summary(hs, indent="          "))
                out.append("        payload         : " + str(pl.get("payload")))
                out.append("        json (one line) : " + json.dumps(
                    pl, ensure_ascii=False, separators=(",", ":")))

        if case["routes"]:
            out.append(H2)
    out.append("")
    return "\n".join(out)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def _run_one(case) -> dict:
    label, sa, amt, da = case
    t = time.time()
    try:
        res = run_case(label, sa, amt, da)
        ok = sum(1 for r in res["routes"] if r["prepare"].get("payloads"))
        print(f"  • {label}: {len(res['routes'])} route(s), {ok} prepared  "
              f"({time.time() - t:.1f}s)")
    except Exception as e:  # noqa: BLE001 — keep sweeping on any single failure
        res = {"label": label, "fatal_error": str(e), "routes": []}
        print(f"  • {label}: ERROR: {e}")
    return res


def main():
    started_at = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%SZ")
    print(f"Dumping prepare payloads from {BASE_URL} "
          f"({len(CASES)} cases, {MAX_WORKERS} workers) ...")
    # Cases are independent -> fan out. Results are reassembled in CASES order
    # so the report stays deterministic regardless of completion order.
    with ThreadPoolExecutor(max_workers=MAX_WORKERS) as pool:
        results = list(pool.map(_run_one, CASES))

    report = render_report(results, started_at)
    with open(OUT_TXT, "w") as f:
        f.write(report)
    print(f"\nWrote:\n  {OUT_TXT}")


if __name__ == "__main__":
    main()
