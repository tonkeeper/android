# Maestro UI tests

Android UI tests run with [Maestro](https://maestro.mobile.dev) against the
Tonkeeper **debug** APK (`com.ton_keeper.debug`).

- **Local:** emulator (`maestro --device emulator-5554 test …`).
- **CI:** Maestro Cloud (mobile.dev) on **1 device max** — every flow in
  `config.yaml` runs **sequentially**. Do not request sharding or extra devices.

This README is the fast-context map: **where things live, how CI is wired, and
the rules for writing flows**. Update it as the setup evolves.

---

## TL;DR — where do I change …?

| I want to… | Go to |
| --- | --- |
| Add/adjust a test case | shard `subflows/` (send, staking, portfolio, trade, swap_common, tonconnect, browser) or `flows/<section>/*.yaml` |
| Include a flow in Cloud CI | add `flows/<path>/*` to `config.yaml` — `*` is **not** recursive |
| Merge independent cases into one Cloud run | shard wrapper (`send.yaml`, …) + `subflows/` (see **Shards and subflows**) |
| Share steps between flows | `steps/**` (relative `runFlow`) |
| Add an API assertion (TonAPI, on-chain) | `scripts/api/**` + a `service/**` subflow |
| Skip a flaky/broken flow but keep it green | wrap its body in `runFlow: { when: { true: "${false}" } }` |
| Dismiss the keyboard after `inputText` | **do not** `hideKeyboard` — it closes the bottom sheet; tap the next control instead |
| Enable / recover a multichain wallet | `steps/wallet/launch_app_ensure_multichain_wallet.yaml` (Firebase + `/keys/all`; no `clearState`) |
| Change Cloud / nightly wiring | `.github/workflows/maestro-tests.yml` (called from `nightly.yaml`) |
| Understand CI pass/fail | Maestro Cloud upload status in `maestro-tests.yml` — the whole listed suite is gating |
| Verify a Linear task locally with video and a report | `$tk-qa-verify` skill (`.codex/skills/tk-qa-verify`) + `tools/qa_run.sh` (see **Local-only flows, mocks and tools**) |
| Fake a backend state (refunds, provider errors, balances) | `mocks/` + `tools/qa_run_mock.sh` — mitmproxy, local only |
| Check the machine/device before a local run | `tools/preflight.sh <serial>` |

---

## `config.yaml`

There are **no iOS-style clusters**. Cloud reads this file as the workspace
config and runs every glob, in order, on a **single** device.

```yaml
platform:
  android:
    disableAnimations: true

disableRetries: true

flows:
  - flows/browser/*
  - flows/transactions/*
  - flows/tonconnect/*
  - flows/deeplinks/*
  - flows/portfolio/*
  - flows/send/*
  - flows/staking/*
  - flows/swap_common/*
  - flows/trade/*
  - "!flows/**/subflows/**"

testOutputDir: ../build/maestro-output
```

- Maestro Cloud **wipes/restarts the app between every top-level flow**
  matched by these globs. That is why transactions / send / staking /
  portfolio / trade / swap_common / tonconnect are **one wrapper file each** — one Cloud row per
  shard. Cases inside a shard relaunch via `launch_app_ensure` (unlock
  only; no `clearState`).
- `*` is a Java glob path segment: `flows/send/*` matches
  `send/send.yaml` only, **not** `send/subflows/*.yaml`. Do not switch
  these to `**`.
- Nested `subflows/` are `runFlow` targets, not Cloud entries. The
  `!flows/**/subflows/**` line is extra exclusion if a glob is ever
  widened.
- A glob that does not match files is silently empty — still add the folder
  **and** the glob, or Cloud will not pick new tests up.
- Omniston is intentionally absent from the transactions shard until its
  scenario is restored.
- `disableRetries: true` — flaky flows stay red; do not paper over them with
  Maestro retries. Document a product bug in the flow header instead.
- `.maestro/config.yaml` only turns off Android animations for local runs.

---

## Directory layout

```
maestro_ui_tests/
├── config.yaml                 # Cloud workspace: flow globs + android flags
├── .maestro/config.yaml        # local Maestro defaults (disableAnimations)
├── flows/                      # Cloud entries = yaml at this glob depth only
│   ├── transactions/transactions.yaml + subflows/ # one Cloud case
│   ├── send/send.yaml + subflows/
│   ├── staking/staking.yaml + subflows/        # smoke → deposit → withdraw
│   ├── portfolio/portfolio.yaml + subflows/
│   ├── trade/trade.yaml + subflows/
│   ├── swap_common/swap_common.yaml + subflows/
│   ├── tonconnect/tonconnect.yaml + subflows/
│   └── browser/ deeplinks/
├── steps/                      # reusable runFlow targets (not Cloud entries)
│   ├── wallet/                 # launch, import, passcode
│   ├── recieve_modal/ send/ swap/ trade/ token_picker/ confirm/ transactions/
│   └── password/ mnemonic/ history/ …
├── service/                    # on-chain assertion subflows (runScript + assert)
│   ├── account/ history/
├── scripts/
│   ├── api/                    # device JS: TonAPI GET helpers
│   └── utils/                  # formatter.js, short_addr_android.js, …
├── flows/*_local/              # LOCAL ONLY, gitignored: clearState / position taps / mocks / biometry
├── mocks/                      # mitmproxy addon (versioned) + scenarios/, _recorded/ (gitignored)
├── secrets/                    # wallets.env with seed profiles (gitignored; wallets.env.example tracked)
└── tools/                      # local runner (video + logcat + junit), mock runner, preflight, helpers
```

`appId` is always `com.ton_keeper.debug`. The launcher label is **Keeper Dev** (**Tonkeeper Dev** before the TK-3495 rebrand).

---

## Shards and subflows

Cloud bills a **wipe + launch** per **top-level** file matched by
`config.yaml`. Transactions, send, staking, portfolio, trade,
swap_common, tonconnect, and browser are **one wrapper + `subflows/`**.
Deeplinks stay one yaml per Cloud case.

`folder/*` is **one path segment**. Nested
`flows/<shard>/subflows/*.yaml` are **not** Cloud entries. Never change
those globs to `**`. The `!flows/**/subflows/**` line is extra exclusion.

The wrapper is a **thin sequencer**. It does **not** launch. Each
subflow launches itself.

### What Cloud runs vs what is only `runFlow`'d

| Wrapper | Cloud `name:` | Subflow (order) | Case `name:` / wrapper `label:` |
| --- | --- | --- | --- |
| `flows/transactions/transactions.yaml` | `Transactions` | `subflows/transactions_tests.yaml` | `Transactions — send GRAM` |
| | | `subflows/send_ton_confirmation_fee_visible.yaml` | `Transactions — GRAM send fee on confirm` |
| `flows/send/send.yaml` | `Multichain — send` | `subflows/send_arb_eth_to_self.yaml` | `Multichain — send Arbitrum ETH to self` |
| | | `subflows/send_bnb_to_self.yaml` | `Multichain — send BNB to self` |
| | | `subflows/send_btc_to_self.yaml` | `Multichain — send BTC to self` |
| | | `subflows/send_eth_to_self.yaml` | `Multichain — send ETH to self` |
| | | `subflows/send_base_eth_to_self.yaml` | `Multichain — send Base ETH to self` |
| | | `subflows/send_gram_to_self.yaml` | `Multichain — send GRAM to self` |
| `flows/staking/staking.yaml` | `Multichain — staking` | `subflows/tonstakers_stake_screen_smoke.yaml` | `Multichain — Tonstakers stake screen` |
| | | `subflows/tonstakers_asset_list_deposit_one_ton.yaml` | `Multichain — Tonstakers deposit 1 GRAM` |
| | | `subflows/tonstakers_asset_list_withdraw_one_ton.yaml` | `Multichain — Tonstakers withdraw 1 GRAM` |
| `flows/portfolio/portfolio.yaml` | `Multichain — portfolio` | `subflows/recieve_smoke.yaml` | `Multichain — receive networks smoke` |
| | | `subflows/hide_show_btc_in_wallet.yaml` | `Multichain — hide and show BTC` |
| `flows/trade/trade.yaml` | `Multichain — trade` | `subflows/core_tokens_open_details.yaml` | `Multichain — core tokens open details` |
| | | `subflows/trending_network_filters.yaml` | `Multichain — trending network filters` |
| `flows/swap_common/swap_common.yaml` | `Multichain — swap` | `subflows/swap_token_picker_search.yaml` | `Multichain — swap token picker search` |
| | | `subflows/btc_fiat_amount_too_low.yaml` | `Multichain — BTC fiat amount too low` |
| `flows/tonconnect/tonconnect.yaml` | `TonConnect` | `subflows/work_via_service.yaml` | `TonConnect — STON.fi browser swap` |
| | | `subflows/tonconnect_tc_deeplink_connect_sheet.yaml` | `TonConnect — tc deeplink connect sheet` |

Final `assertTrue` short names (e.g. `send shard: failed BTC, GRAM`):
transactions `send GRAM` / `GRAM fee`; send `ARB ETH` / `BNB` / `BTC` /
`ETH` / `Base ETH` / `GRAM`; staking `smoke` / `deposit` / `withdraw`;
portfolio `receive` / `hide/show BTC`; trade `core tokens` /
`trending filters`; swap `token picker` / `BTC fiat too low`;
tonconnect `STON.fi` / `tc deeplink`.

### Launch

Each subflow **starts** with
`steps/wallet/launch_app_ensure_multichain_wallet.yaml`: `stopApp` +
extras + pin; import only if the app is empty; **no** `clearState`.
After a mid-fail (or a green case), the next case relaunches and unlocks;
it does not re-import if a wallet exists. Do not add a home teardown —
the next `launch_app_ensure` is the cleanup.

### Continue-on-error

Idiom from `flows/send/send.yaml` (same on the other wrappers):

```yaml
- runFlow:
    optional: true
    label: "Multichain — send BTC to self"
    commands:
      - runFlow: subflows/send_btc_to_self.yaml
      - evalScript: ${output.send_btc_ok = true}
# …
- evalScript: ${var f = []; if (output.send_btc_ok !== true) f.push('BTC'); output.send_failed = f.join(', ');}
- assertTrue:
    condition: ${output.send_failed === ''}
    label: "${output.send_failed ? 'send shard: failed ' + output.send_failed : 'send shard: all cases passed'}"
```

- `optional: true` continues the wrapper after a case dies (Maestro ⚠️).
- `evalScript` is a sibling **after** the nested `runFlow`, so it runs
  only if that file succeeded.
- The final `assertTrue` fails the Cloud row and `label:` lists failed
  case names.
- Without that assert the shard would stay **green** (`optional` = ⚠️
  only). Keep the assert.

There is **no** `continueOnFailure`. `onFlowComplete` is teardown only;
it does not continue siblings.

### Cloud report

One row per wrapper; no per-subflow Cloud row.

| What you see | Where |
| --- | --- |
| `Multichain — send` (failed) | Top-level Cloud row (`name:` on the wrapper) |
| `Multichain — send BTC to self` | Nested subflow `name:` + wrapper `label:` in that row's command log |
| ⚠️ on the dead case | `optional: true` — warning, not a separate Cloud row |
| `send shard: failed BTC, GRAM` | Final `assertTrue` `label:` — this is what makes CI red |

### Add a case to a shard

1. Write the yaml in that shard's `subflows/` (not at wrapper-glob depth).
2. Start with `steps/wallet/launch_app_ensure_multichain_wallet.yaml`.
3. Give it a `name:` (reuse that string as the wrapper `label:`).
4. In the wrapper: init `output.<case>_ok = false`, add an `optional`
   `runFlow` block + `_ok` flag, include the short name in the final
   `assertTrue` list.
5. Do **not** add the file next to the wrapper (`<shard>/*.yaml` would
   become a second Cloud row).
6. Do **not** add a new `config.yaml` glob for it.
7. Do not put full cases in `steps/` — that folder is shared helpers.

To merge another folder later (deeplinks, …): move cases into
`flows/<shard>/subflows/` (each starts with
`launch_app_ensure`), add `<shard>.yaml` at the shard root as an
`optional` sequencer + final `assertTrue`, leave the `config.yaml` glob
as `<shard>/*` (not `**`).

### Run locally

A single subflow is OK because it has launch (wallet already imported).

```sh
# Whole send shard (what Cloud runs)
maestro --device emulator-5554 test maestro_ui_tests/flows/send/send.yaml -e PASSWORD_KEY=5

# One send case
maestro --device emulator-5554 test maestro_ui_tests/flows/send/subflows/send_btc_to_self.yaml -e PASSWORD_KEY=5
```

Same pattern for `transactions.yaml`, `staking.yaml`, `portfolio.yaml`,
`trade.yaml`, `swap_common.yaml`, `tonconnect.yaml`, and `browser.yaml`.

**Staking order is required:** smoke → deposit → withdraw. Deposit
before withdraw is load-bearing (withdraw needs the staked position).

**TonConnect order:** STON.fi first (empty connected-apps after the
Cloud wipe), then the tc deeplink sheet.

**Browser order:** tabs smoke first (asserts the empty Connected tab), then
WalletConnect via Aave (`subflows/walletconnect_aave.yaml`), which
disconnects from the Connected tab at the end so the wallet is left clean.

---

## How a flow runs

### CI (Maestro Cloud)

`.github/workflows/maestro-tests.yml` builds
`:apps:wallet:instance:main:assembleDefaultDebug` (or reuses an APK artifact)
and uploads workspace `maestro_ui_tests` to Cloud via
`tools/ci/run-maestro-cloud.sh` (`maestro cloud --format junit`). The JUnit
report (`build/maestro-cloud-report.xml`, uploaded as a run artifact) carries
per-flow status, the failure message (our shard `assertTrue` labels), and
`cloud.runUrl` deep links; `ci/slack_bot/post_maestro_cloud_slack.py` renders
it into the GitHub step summary and the Slack report.

APK source priority: `reuse_apk_artifact` (ID/URL from a past run) →
`apk_prebuilt` (nightly already uploaded `tonkeeper-nightly-debug-*` in the
same run) → fresh build. The debug APK is always downloaded by the
`tonkeeper-nightly-debug-*` name pattern, never by a job-output name —
GitHub drops job outputs that look like they contain a secret, and an empty
download name pulls in every artifact of the run (incl. the beta APK).

**Slack:** the `notify_slack` input (default: on for `workflow_call`/nightly,
off for manual dispatch) posts to `SLACK_AUTOTESTS_CHANNEL` via
`SLACK_BOT_TOKEN`. Root message = per-flow ✅/❌ + console/workflow links;
one thread reply per failed flow with the failure reason and a link to that
flow's Cloud run page (video, screenshots, logs). Maestro Cloud has no public
artifact API, so screenshots are linked, not attached.

**Android Cloud concurrency is 1.** All **top-level** flows from `config.yaml`
share one device and run one after another, with a wipe/restart between
them. A leftover sheet or a 180s waiter burns the whole remaining suite —
shard **cases** `stopApp` via `launch_app_ensure`; keep waiters tight.

Nightly (`nightly.yaml`) calls the same workflow. Manual dispatch can skip the
build via `reuse_apk_artifact`.

Videos/logs: `app.maestro.dev` (link in the job summary).

iOS-style shard matrices / `ci/run_maestro_shard.sh` **do not exist** here.
Do not add extra devices or Cloud sharding.

### Env vars (`-e` locally, `env:` in Cloud)

| var | Source | Used for |
| --- | --- | --- |
| `PASSWORD_KEY=5` | literal | passcode digit taps |
| `WALLET_WITH_MONEY` | `MAESTRO_WALLET_WITH_MONEY` | 12-word import when the app has no wallet |
| `WALLET_WITH_MONEY_ADDR` | `MAESTRO_WALLET_WITH_MONEY_ADDR` | TonAPI balance assert |
| `auth` | `MAESTRO_AUTH` | TonAPI bearer for `scripts/api/**` |
| `RECIEVE_WALLET` | `MAESTRO_RECIEVE_WALLET` | send recipient |

Local example (unlock-only smoke; import/send also need the secrets above):

```sh
maestro --device emulator-5554 test maestro_ui_tests/flows/portfolio/portfolio.yaml -e PASSWORD_KEY=5
```

Flows reference shared steps with **relative** `runFlow`. From a shard
wrapper (`flows/<section>/x.yaml`) the steps dir is
`../../steps/…`. From `subflows/` it is `../../../steps/…`.

---

## Launch

Every Cloud **top-level** flow (browser, deeplinks, …) and
every **shard subflow** starts with
**`steps/wallet/launch_app_ensure_multichain_wallet.yaml`**
(or a `launchApp` that passes the same extras). Shard wrappers do **not**
launch; they only sequence cases. There is no separate non-multichain
launch. Do **not** `clearState` — relaunch keeps the imported wallet.

```yaml
- stopApp
- launchApp:
    arguments:
      featureFlags: '{"android_is_multichain_enabled": true, "android_is_import_multichain_wallet": true}'
      bootFlags: '{"multichain_enabled": true}'
```

1. `stopApp` — leftover sheets/tabs from the previous test are gone.
2. `launchApp` with `featureFlags` + `bootFlags` (debug extras on `RootActivity`).
3. If the launcher is still showing, tap **Keeper Dev** (process already has extras in memory).
4. If **Import Existing Wallet** → import. If **Enter passcode** → pin.
   Otherwise wait for home **Send**.

The debug APK registers several LAUNCHER aliases. If local `launchApp`
fails with `Unable to launch app com.ton_keeper.debug`, Cloud still uses
`launchApp` successfully — do not replace this step with icon-only or
`openLink: tonkeeper://` (Chrome Custom Tabs steal the deeplink).

**Passcode overlay:** Compose home (`Send`) stays in the hierarchy *under* the
lock. Always handle `Enter passcode` first, then assert `notVisible: Enter passcode`.
Do not treat `visible: Send` as “unlocked”.

**Tap-through:** Compose sheets (Swap, Receive, Manage, Choose asset) leave
home-list labels (`BTC`, `USDT`, `GRAM`) in the hierarchy. `tapOn: BTC` will
hit the list behind the sheet. Prefer `id:` / a unique `below:`/`above:` pair
with `index: 0`. If that is still ambiguous, add a `testTag` in the app.

---

## Locators

Android `id:` is either:

- a Compose `testTag` (`Modifier.testTag("…")` with `testTagsAsResourceId = true`)
- or a View `android:id="@+id/…"` in XML

`text:` / bare `tapOn:` match **visible copy** (string resource *value*, not
the `name=`). Home on the Compose wallet is text `Send` / `Swap` / `Stake` —
legacy XML `id: send` is gone on that screen.

**Prefer stable ids.** Add a `testTag` or XML id in the app rather than guessing
on-screen text or geometry.

**Forbidden: `hideKeyboard`.** On Android it is a back-press: it dismisses
the current bottom sheet / modal, not just the IME. After `inputText`, tap
the next real control (or `pressKey: Enter` if the field accepts it). Do
not “close the keyboard” as its own step.

**Forbidden:** percentage / absolute coordinates for `tapOn` / `swipe`
(`point: 81%,7%`, `start: "88%, 25%"`, etc.). Local emulator size ≠ Cloud
device. Bind to:

- `id:` — preferred
- visible `text:` / regex only when an id is impractical
- `swipe.from.id` / `scrollUntilVisible.element` (id or text)

**Amount strings:** watch thin space (`\u2009`) vs normal space. Join with
`"[ \u2009]"` in shared steps when the ticker may be non-ASCII (`USD₮`).
ASCII tickers (`GRAM`, `BTC`) use a plain space.

**Shortened addresses:** do not hardcode a `UQ…` fragment. Use
`scripts/utils/short_addr_android.js` (`output.shortAddrRecieve`). Compose
confirm/receive is 4+4 with `...` (`steps/transactions/assert_receive_wallet_visible.yaml`);
history cells pass separator `…` (`steps/history/transaction_cell.yaml`).

**Waiters & timeouts:** an `extendedWaitUntil` timeout is a *ceiling*, not a
budget — it returns as soon as the element appears. On Cloud, one long waiter
blocks **every later flow** on the same device. Rules:

- Default to a **tight** timeout that covers the happy path. Most UI
  transitions settle in 1–6s; a screen behind balance load or a signed
  on-chain action gets 10–15s.
- **Anything above 15s must be agreed with the task owner**, with a one-line
  comment *why* (broadcast settlement, first cold launch). No silent
  30s/60s/180s ceilings.
- `waitForAnimationToEnd` is close to a *fixed* pause — keep these ≤ ~3s.
  Prefer `extendedWaitUntil` on the next real element.
- Shared steps (`steps/**`) run across many flows — a timeout you bump there
  multiplies across the sequential Cloud run.

---

## Multichain flag

The product gate is **AND** of two sources (see `API` / `InitViewModel`):

1. Firebase: `android_is_multichain_enabled` (`WalletFeature.Multichain`) and, for
   the import path, `android_is_import_multichain_wallet` (`WalletFeature.ImportMultichainWallet`)
2. Boot config `/keys/all?`: `flags.multichain_enabled` (`FlagsEntity.multichainEnabled`)

The whole suite (send, staking, portfolio, trade, swap_common, browser,
transactions, tonconnect, deeplinks) starts with
`launch_app_ensure_multichain_wallet.yaml`, which passes both as debug
launch extras (all three keys — since TK-3647 the debug build no longer forces
the import flag, and the fetched Firebase value is `false`, so without it
`import_wallet.yaml` produces a legacy TON wallet, TK-3691):

```yaml
- launchApp:
    arguments:
      featureFlags: '{"android_is_multichain_enabled": true, "android_is_import_multichain_wallet": true}'
      bootFlags: '{"multichain_enabled": true}'
```

`bootFlags` is applied by `BootConfigOverrides` on top of the fetched/cached
`/keys/all` config (debug APK only). Both in-app defaults are `true`, so the
extras only matter when the fetched Firebase or keys/all config explicitly
disables multichain. Pass all of them to pin the gate regardless of remote state.

Native ticker in the product is **GRAM** (not TON) on these builds.

---

## API scripts

Flows assert on-chain state via `runScript` in `scripts/api/**`. Maestro has
**no JS import** — each file is isolated; only `output` crosses steps.

There is no iOS-style `_api_runtime.js` codegen here. Keep HTTP helpers
local to each script (or copy the retry/`_httpGetJSON` pattern from an
existing file). Do not invent a `make maestro_api_sync` until that pipeline
exists in this repo.

---

## Conventions

- Cloud entry = one yaml matched by `config.yaml` globs. Merged shards
  use one wrapper + `subflows/` cases (each subflow relaunches; wrapper
  continues after a case fail and `assertTrue`s the names); unmerged
  folders still use one yaml per case. Shared helpers go to `steps/**`
  or `service/**`.
- Prefer `id:` (`testTag` / XML id). Add them in the app when missing —
  especially on Swap send/receive capsules and overflow menus.
- **Forbidden:** `hideKeyboard` — it closes the bottom sheet / modal, not
  only the keyboard.
- **Forbidden:** `%` / absolute coordinates for taps and swipes.
- No throwaway comments; keep intent-only notes.
- A flow that catches a product bug stays enabled and red; document the bug
  in the flow header instead of relaxing the assertion.
- Do not put iOS `accessibilityIdentifier`s, pasteboard hacks, or
  `confirm_swipe` ids into Android flows. XML stake/unstake still uses
  `stake_amount` / `unstake_amount` / `next_button`; Compose confirm uses
  `steps/confirm/swipe_slide_to_confirm.yaml`.

---

## Local-only flows, mocks and tools

Everything in this section runs on a developer machine only. Maestro Cloud never
sees `flows/*_local/`, `mocks/` or `tools/`. What Cloud cannot run is not
pushed either: `flows/*_local/`, `mocks/scenarios/`, `mocks/_recorded/` and
`secrets/` are gitignored; only the tooling, `mocks/mock.py` and
`secrets/wallets.env.example` are versioned.

- **`flows/<area>_local/`** — cases that need `clearState`, a position tap, a
  mocked backend or device state (enrolled fingerprint). Each file header says
  why it is local, which wallet fraction it expects (`# wallet: ton_only`) and
  which `-e` parameters are required; parameters are never defaulted in an
  `env:` block (Maestro 2.4 lets the file default override `-e`). When a locator
  gap is the only reason a flow is local, the fix is a `testTag` in the app, not
  a permanent local flow.
- **`secrets/wallets.env`** — seed profiles per product fraction: `TON_ONLY`
  (legacy TON wallet), `MULTICHAIN` (BIP39-only phrase), `TON_BIP39` (valid under
  both, triggers the "Choose wallet" picker). `tools/qa_run.sh --wallet <profile>`
  maps them onto `WALLET_WITH_MONEY` / `WALLET_WITH_MONEY_ADDR`, the variables the
  import steps already use, and passes `PASSWORD_KEY`, `auth`, `RECIEVE_WALLET`
  from the same file.
- **`tools/qa_run.sh <serial> <flow> <case> <out> -e …`** — runs one flow with
  screen recording (`qa_<case>_N.mp4`, 180 s segments), `logcat.txt`,
  `junit.xml`, screenshots and `console.txt` under `<out>/<case>/`. Use
  `build/maestro-qa/<date>/<ticket>` as `<out>` (gitignored).
- **`tools/qa_run_mock.sh <scenario> …`** — the same behind a mitmproxy
  scenario from `mocks/scenarios/`; sets and resets the device proxy.
- **`tools/preflight.sh [serial]`** — host tools, device, RAM, locale, keyguard,
  installed build, leftover proxy, stale processes.
- Helpers: `check_push_pref.sh` (push toggle via prefs), `parse_swap_quotes.py`
  (aggregator per quote from NetLog), `bio_driver.sh` + `enroll_fingerprint_emulator.sh`
  (biometry branch), `tk1276_intent_injection.sh` (exported-activity checks via `am start`).
- The end-to-end procedure (feasibility → approved plan → run → report) is the
  `tk-qa-verify` skill in `.codex/skills/tk-qa-verify/`; its
  `references/lessons.md` lists the locator and timing gotchas found so far.

---

## Known product / locator gaps

| Flow | Issue |
| --- | --- |
| `flows/trade/subflows/core_tokens_open_details.yaml` | Order is BTC → ETH → BNB → TRX → GRAM → USD₮. One scroll loop from below the chart (not a center swipe — ChartSection eats it). BNB has no `Transaction history` (`EXPECT_TRANSACTION_HISTORY: false`). |
| `flows/swap_common/subflows/btc_fiat_amount_too_low.yaml` | Swap send capsule / fiat toggle have no `testTag`; a `.*BTC.*` tap under Send opens the token picker. Wait for the quote error; do not tap Continue first. |
| `flows/portfolio/subflows/hide_show_btc_in_wallet.yaml` | Details `…` menu has no id; Android path uses Manage + eye toggle. `text: BTC` matches the Bitcoin chip, search field, home row under the sheet, and WBTC. Use `^BTC$` below `Bitcoin` above `WBTC`. |
| `flows/portfolio/subflows/recieve_smoke.yaml` | Android list order is TON, TRON, Ethereum, Bitcoin, Base, BSC, Arbitrum (iOS paints Base before Bitcoin and Arbitrum before BSC). Home `ARBITRUM` / `BASE` badges sit under the sheet and steal a bare network tap. |
| `flows/deeplinks/deeplinks.yaml` | `openLink` must use `browser: false` or Chrome Custom Tabs steal `ton://` / `tonkeeper://`. Multichain transfer without a jetton opens **Choose asset** first (`WithdrawMulticoinRoutes.Picker`); pick GRAM before asserting the address. |
