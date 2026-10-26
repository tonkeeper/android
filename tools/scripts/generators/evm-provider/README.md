# EVM provider bundle

Builds the `window.ethereum` (EIP-1193) provider injected into the Earn WebView,
so the dApp talks to the current wallet without a separate WalletConnect session.

The provider itself comes from [trust-web3-provider](https://github.com/trustwallet/trust-web3-provider)
(MIT), pinned to a tag in `build.mjs` and cloned on demand into `.upstream/`.
Only `packages/core` and `packages/ethereum` are bundled.

## Build

```bash
cd android/tools/scripts/generators/evm-provider
npm install
npm run build              # -> dist/evm_provider.js
node build.mjs <path>      # or write straight to an app resource
```

## Why the bundle is small

Upstream ships a ~2 MB bundle because `MobileAdapter` pulls in
`@metamask/eth-sig-util` (and with it the node polyfill chain) to hash EIP-712
payloads in JS. We run with `disableMobileAdapter: true`, so:

* the native side receives plain EIP-1193 method names (`eth_sendTransaction`,
  `personal_sign`, `eth_signTypedData_v4`, …) — the same vocabulary the
  WalletConnect request parser already speaks;
* typed data arrives as raw JSON and is hashed by chainkit;
* `@metamask/eth-sig-util` is replaced by `src/eth-sig-util-stub.ts`.

What the adapter used to cover and this entry now does itself: rejecting
subscription/filter methods with `4200` and normalising the legacy
`personal_sign` argument order.

## Contract with the native side

The bundle installs itself at document start, in the main frame only — the origin-scoped script
also runs in same-origin iframes, and a provider nobody answers would hang the page. It puts
`window.ethereum` in place before any page script runs and posts a handshake to the `TonkeeperEvm`
JavaScript interface:

```json
{ "method": "wallet_provider_ready" }
```

The wallet address and the chain are resolved natively, which the page never waits for, so requests
park on a readiness promise until the native side answers the handshake. Nothing is ever served
from an empty account, and the native side does not depend on page-lifecycle callback timing.

Requests are posted as `{ id, method, params }` with plain EIP-1193 method names. Answers come back
over the same `JavaScriptReplyProxy` the request arrived on — never through `evaluateJavascript`,
which would run in whatever document the WebView currently holds, including a third-party page the
user navigated to mid-request. Each answer is a JSON message on `TonkeeperEvm.onmessage`:

| message | effect |
| --- | --- |
| `{type: "config", address, chainId, info?}` | releases parked requests, emits `connect`, and announces over EIP-6963 when `info` is present |
| `{type: "unavailable", code, message}` | fails every request instead (no EVM account, resolve error) |
| `{type: "result", id, value}` | resolves the dApp's promise |
| `{type: "error", id, code, message}` | rejects it with an `Error` carrying `code` (`4001` on user rejection) |
| `{type: "chainSwitched", id, chainId}` | resolves the switch and emits `chainChanged` |

There is no `window.__tkEvm`: the reply handler lives in the bundle's closure, so a script on the
Earn origin cannot replace it to spoof an account or settle a pending request.

After the config message, `eth_chainId`, `eth_accounts`, `eth_coinbase` and `net_version` are
answered inside the provider and never reach the native side.
