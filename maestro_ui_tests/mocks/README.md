# Local response mocks (mitmproxy)

`mock.py` is a mitmproxy addon that serves JSON fixtures per scenario and passes
everything else through to the real backend. **Local only** — Maestro Cloud
cannot reach the proxy. The debug build trusts user CA certificates and has no
certificate pinning, so no app change is needed.

Only `mock.py` and this README are versioned. `scenarios/` and `_recorded/` are
gitignored: fixtures describe backend states for a specific ticket and are kept
on the tester's machine (and referenced from the QA report), not pushed.

## Run

```bash
# one case behind a scenario; the runner sets/resets the device proxy itself
maestro_ui_tests/tools/qa_run_mock.sh battery_negative_two_refunds emulator-5554 \
  flows/battery_local/battery_refund_state.yaml two_refunds build/maestro-qa/$(date +%F)/tk-2894 \
  -e PASSWORD_KEY=5 -e EXPECT_CHARGES="-3 charges" -e EXPECT_WARNING=true -e EXPECT_IAP=false \
  -e OUT=build/maestro-qa/$(date +%F)/tk-2894/two_refunds

# record real responses of a host into mocks/_recorded/ to start a new fixture
mitmdump -q -s maestro_ui_tests/mocks/mock.py --set record=battery.tonkeeper.com
```

`mitm.log` in the case folder lists every `MOCK HIT` so the report can prove the
fixture was actually served.

## Device prerequisites (once per emulator/device)

1. Generate the CA by running `mitmdump` once → `~/.mitmproxy/mitmproxy-ca-cert.cer`.
2. `adb push ~/.mitmproxy/mitmproxy-ca-cert.cer /sdcard/Download/`
3. Settings → search "CA certificate" → Install a certificate → CA certificate →
   Install anyway → confirm PIN/fingerprint → pick the file in Downloads.
4. Emulator reaches the host as `10.0.2.2`; a physical device needs the host LAN IP
   in the Wi-Fi proxy settings instead of the `settings put global http_proxy` the runner uses.

If a mocked run shows `Client TLS handshake failed … com.ton_keeper.debug` in
`mitm.log`, the CA is not installed. Failures for `connectivitycheck.gstatic.com`
and other system hosts are expected and harmless.

If a run was killed before the runner reset the proxy: `adb shell settings put global http_proxy :0`.

## Layout

```
mocks/
├── mock.py                       # addon: scenario serving + record mode
├── scenarios/<scenario>/<host>/<path with "/" → "__">.json
│                              …/<same>.meta.json   # optional {"status": 503, "delay_ms": 1500, "headers": {…}}
│                              …/GET__<path>.json   # method-specific match (optional prefix)
└── _recorded/<host>/<METHOD>__<path>.json          # raw captures from --set record
```

Query strings are ignored when matching. Everything without a fixture passes through.

## Scenarios (example set from TK-2894, local, not in git)

| scenario | /balance | /purchases | expected UI |
|---|---|---|---|
| `battery_negative_two_refunds` | -0.0078 TON (= -3 charges at charge_cost 0.0026) | 2 refunded android purchases (fully + partially) + 1 refunded crypto (must not count) | red battery, "-3 charges", refund warning, IAP hidden |
| `battery_negative_one_refund` | same | 1 refunded android + 1 pending (not refunded) + 1 refunded crypto | red battery, warning, IAP still offered |
| `battery_zero_no_refunds` | 0 | none | baseline: default subtitle, no warning, IAP offered |
| `battery_negative_purchases_500` | -0.0078 TON | `/purchases` → HTTP 500 | warning shown, IAP still offered (client falls back to ALLOWED on error) |
| `battery_negative_two_ios_refunds` | -0.0078 TON | 2 refunded **ios** purchases | IAP hidden — store refunds count regardless of platform |

Adding a scenario: copy the closest folder, edit the JSON, add a row here, and
note in the fixture's `.meta.json` or a comment in the flow header which real
response it was derived from (`_recorded/` keeps the capture).
