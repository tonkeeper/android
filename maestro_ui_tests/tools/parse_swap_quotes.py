#!/usr/bin/env python3
"""Usage: python3 maestro_ui_tests/tools/parse_swap_quotes.py <logcat.txt> [out.json]

TK-3254: pull every POST /v2/crosschain/quotes request/response pair out of a
logcat capture (debug build logs HTTP through NetLog) and print which
aggregator was requested / answered for which source amount."""
import json, re, sys

path = sys.argv[1]
text = open(path, encoding="utf-8", errors="replace").read()
# Each NetLog entry is prefixed by logcat header; strip headers so multi-line
# bodies join up again.
lines = []
for ln in text.splitlines():
    if "LoggingInterceptor" not in ln:
        continue
    ln = re.sub(r"^\d\d-\d\d \d\d:\d\d:\d\d\.\d+ +[VDIWE]/LoggingInterceptor\(\s*\d+\): ?", "", ln)
    ln = re.sub(r"^\[[^\]]+\] netLog:\d+ NetLog \S* ?", "", ln)
    lines.append(ln)
text = "\n".join(lines)

reqs = {}
for m in re.finditer(r"----> \[(\d+)\] =+ Request =+\s*\n(POST|GET) (\S+)(.*?)----> \[\1\] End of request", text, re.S):
    rid, method, url, body = m.groups()
    if "/crosschain/quotes" not in url:
        continue
    j = re.search(r"Request body:\s*\n(\{.*\})", body, re.S)
    try:
        reqs[rid] = json.loads(j.group(1)) if j else {}
    except Exception:
        reqs[rid] = {"_raw": body[:300]}

rows = []
# Responses longer than the 6 KB NetLog cap lose their "End of Response" line,
# so a response runs until the next NetLog frame marker (or end of capture).
for m in re.finditer(r"<---- \[(\d+)\] =+ Response =+\s*\n(\d{3}) [^\n]*\n(.*?)(?=\n(?:<----|---->) \[|\Z)", text, re.S):
    rid, code, body = m.groups()
    if rid not in reqs:
        continue
    req = reqs[rid]
    j = re.search(r"Response body:\s*\n(.*)", body, re.S)
    raw = j.group(1).strip() if j else ""
    truncated = "strip long data" in raw or not raw.rstrip().endswith("}")
    agg = None; perr = None; nroutes = None
    try:
        data = json.loads(raw)
        routes = data.get("routes") or []
        nroutes = len(routes)
        agg = routes[0].get("aggregator") if routes else None
        perr = data.get("provider_errors")
    except Exception:
        # truncated JSON: look only inside the routes array for the first aggregator
        rm = re.search(r'"routes"\s*:\s*\[(.*)', raw, re.S)
        if rm:
            inner = rm.group(1)
            if inner.lstrip().startswith("]"):
                nroutes = 0
            else:
                a = re.search(r'"aggregator"\s*:\s*"([a-z]+)"', inner)
                agg = a.group(1) if a else None
                nroutes = "1+"
        pe = re.search(r'"provider_errors"\s*:\s*(\[.*?\])', raw, re.S)
        perr = pe.group(1) if pe else None
    rows.append({
        "id": rid, "http": code,
        "source": req.get("source_asset"), "dest": req.get("destination_asset"),
        "source_amount": req.get("source_amount"), "dest_amount": req.get("destination_amount"),
        "exact_type": req.get("exact_type"), "requested_aggregators": req.get("aggregators"),
        "answered_aggregator": agg, "routes": nroutes,
        "provider_errors": (json.dumps(perr)[:220] if perr else None),
        "truncated": truncated,
    })

print(f"{'req':>4} {'http':>4} {'exact':<12} {'src amount':>22} {'dst amount':>22} {'asked':<14} {'answered':<10} {'routes':<6} src -> dst")
for r in rows:
    print(f"{r['id']:>4} {r['http']:>4} {str(r['exact_type']):<12} {str(r['source_amount']):>22} {str(r['dest_amount']):>22} "
          f"{str(r['requested_aggregators']):<14} {str(r['answered_aggregator']):<10} {str(r['routes']):<6} {r['source']} -> {r['dest']}"
          + (f"  provider_errors={r['provider_errors']}" if r['provider_errors'] else "")
          + ("  [resp truncated]" if r['truncated'] else ""))
if len(sys.argv) > 2:
    json.dump(rows, open(sys.argv[2], "w"), indent=2)
