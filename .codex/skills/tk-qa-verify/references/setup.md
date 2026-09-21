# One-time machine setup for tk-qa-verify

Everything below is local. Nothing here changes CI or Maestro Cloud.

## 0. Prerequisites checklist

| # | What | Required? | Why the skill needs it | Install / connect |
|---|---|---|---|---|
| 1 | Android SDK platform-tools + emulator (`adb`, `emulator`) | required | drive the device, record video, read prefs | Android Studio → SDK Manager; put `$ANDROID_HOME/platform-tools` and `$ANDROID_HOME/emulator` on `PATH` |
| 2 | JDK 17+ | required | Maestro CLI runs on the JVM | `brew install openjdk@17` or Android Studio's JBR |
| 3 | Maestro CLI ≥ 2.4 | required | runs the flows | `curl -fsSL https://get.maestro.mobile.dev \| bash` |
| 4 | Python 3.9+ | required | runner helpers, quote parser, mock addon | ships with macOS / Xcode CLT |
| 5 | **Linear MCP** | required | ticket text, acceptance criteria, PR links; posting the findings comment | Claude Code: `claude mcp add --transport http linear https://mcp.linear.app/mcp`, then `/mcp` → Linear → login. Codex: `codex mcp add linear --url https://mcp.linear.app/mcp`, `[features] rmcp_client = true`, `codex mcp login linear` (see the `linear` skill, Step 0) |
| 6 | GitHub access for PRs: `gh` CLI | required | read the PR diff and metadata (`pr` skill, `gh pr view/diff`) | `brew install gh && gh auth login` (SSO for `tonkeeper`) |
| 7 | Debug build `com.ton_keeper.debug` | required | the thing under test | nightly artifact `tonkeeper-nightly-debug-*` or `:apps:wallet:instance:main:assembleDefaultDebug` (needs a `GITHUB_TOKEN` with `read:packages` on `tonkeeper/chainkit-publishing` in `local.properties`) |
| 8 | `maestro_ui_tests/secrets/wallets.env` | required for import-based cases | seed profiles TON_ONLY / MULTICHAIN / TON_BIP39 | `cp secrets/wallets.env.example secrets/wallets.env`, fill in (§3a) |
| 9 | mitmproxy | optional | backend-driven states (refunds, provider errors, staking status) | `brew install mitmproxy` + CA on the device (§4) |
| 10 | **Maestro MCP** | optional | interactive exploration from the agent: hierarchy, taps, screenshots without writing a flow first | Claude Code: `claude mcp add maestro -- maestro mcp --working-dir maestro_ui_tests`. Codex: `codex mcp add maestro -- maestro mcp --working-dir maestro_ui_tests`. The skill itself runs flows through the CLI runner so evidence is recorded; the MCP is for poking around |
| 11 | **Figma MCP** | optional | only when the ticket links a design and visual acceptance matters | see `figma-implement-design` skill, Step 0 |
| 12 | Repo permissions | already in git | `.claude/settings.json` allows `adb`, `maestro`, `mitmdump`, `maestro_ui_tests/tools/*`; `.codex/rules/allow_skill_scripts.rules` allows the tools | nothing to do; if prompts appear for these commands, the settings did not load |

Quick check after setup: `maestro_ui_tests/tools/preflight.sh <serial>` (§6) covers 1–4, 7, 9 and the device; Linear/GitHub/Figma are checked by the first tool call of the skill.

## 1. Host tools

```bash
curl -fsSL https://get.maestro.mobile.dev | bash     # Maestro CLI (2.4+)
brew install mitmproxy                               # only for mocked scenarios
gh auth login                                        # pr skill / gh pr view
python3 --version                                    # 3.9+ is enough
```

Linear MCP must be connected (see the `linear` skill, Step 0). `gh` and Linear are read-only for this skill.

## 2. Emulator

Create or reuse a Google Play image AVD (Play services are needed for the push permission step and IAP products):

```bash
$ANDROID_HOME/emulator/emulator -avd Medium_Phone_API_36.1 -no-boot-anim -no-audio -memory 4096 -cores 4
```

- **4 GB RAM is not optional.** With the default 2 GB the app plus Maestro swap and single taps take 60 s+.
- After a snapshot restore the emulator can come up in airplane mode: `adb shell cmd connectivity airplane-mode disable`.
- Keep the screen on during runs: `adb shell svc power stayon true`.
- If `maestro test` reports "Android driver did not start up in time": `pkill -f 'adb.*logcat'; adb uninstall dev.mobile.maestro; adb uninstall dev.mobile.maestro.test` and retry.

## 3. The app

Install the debug build `com.ton_keeper.debug` (`:apps:wallet:instance:main:assembleDefaultDebug`, or the nightly `tonkeeper-nightly-debug-*` artifact). The app must be in **English**: flows assert English copy. On a device with another system locale:

```bash
adb shell cmd locale set-app-locales com.ton_keeper.debug --locales en-US   # revert: --locales ""
```

Wallet: onboarding flows create one with passcode **5555** (`PASSWORD_KEY=5`). For import-based scenarios use the seed profiles below, never a public phrase for anything that signs.

## 3a. Seed profiles (never in git)

```bash
cp maestro_ui_tests/secrets/wallets.env.example maestro_ui_tests/secrets/wallets.env
```

Fill in the three fractions and keep the file local (`maestro_ui_tests/secrets/*` is gitignored, only the example is tracked):

| Profile | Phrase | Imports as | Use for |
|---|---|---|---|
| `TON_ONLY` | 24-word TON mnemonic | legacy TON wallet | deeplinks, TonConnect, Fragment screens, positive controls that need `SelectedState.Wallet` |
| `MULTICHAIN` | BIP39 12/24 words, not a valid TON phrase | multichain wallet | MC-only behaviour, swaps across chains, Trade |
| `TON_BIP39` | 24 words valid under both checksums | shows the "Choose wallet" picker | the import picker itself, mixed-account scenarios |

Run a flow against a fraction: `tools/qa_run.sh --wallet ton_only <serial> <flow> <case> <out> …`. The runner maps the profile onto `WALLET_WITH_MONEY` / `WALLET_WITH_MONEY_ADDR` (what `steps/wallet/import_wallet.yaml` and `service/**` expect) and passes `PASSWORD_KEY`, `MAESTRO_AUTH` (as `auth`) and `RECIEVE_WALLET` from the same file unless given on the command line. Switching fractions on a device means `clearState` + import, so plan it per ticket, not per case.

## 4. Mocks (optional, for backend-driven states)

The debug build trusts user CA certificates and has no certificate pinning, so mitmproxy works without app changes.

1. Run `mitmdump` once to generate `~/.mitmproxy/mitmproxy-ca-cert.cer`.
2. `adb push ~/.mitmproxy/mitmproxy-ca-cert.cer /sdcard/Download/`
3. On the device: Settings → search "CA certificate" → Install a certificate → CA certificate → Install anyway → confirm PIN/fingerprint → pick the file from Downloads. Toast "CA certificate installed".
4. `maestro_ui_tests/tools/qa_run_mock.sh` sets `http_proxy 10.0.2.2:8080` for the run and resets it afterwards. If a run was killed, reset by hand: `adb shell settings put global http_proxy :0` — otherwise the emulator has no network.

Scenario layout and record mode: `maestro_ui_tests/mocks/README.md`.

## 5. Biometry scenarios (optional)

Onboarding shows the system BiometricPrompt only when a fingerprint is enrolled. On the emulator: set a device PIN, `adb shell am start -a android.settings.FINGERPRINT_ENROLL`, walk the UI, touch the virtual sensor with `adb emu finger touch 1` until "Done" (`tools/enroll_fingerprint_emulator.sh` scripts most of it). The prompt is a secure window: screenshots are black, assert on the hierarchy (`id: button_negative`, title `Keeper.*` — "Keeper Dev" since the TK-3495 rebrand). Remove afterwards: `adb shell locksettings clear --old <pin>`.

## 6. Preflight

```bash
maestro_ui_tests/tools/preflight.sh <serial>
```

Fix every `FAIL`; read every `WARN` (locale, keyguard, leftover proxy, stale processes).

## 7. Where things go

| What | Where |
|---|---|
| Cloud-safe cases | `flows/<shard>/subflows/` + wrapper, per README |
| Local-only flows (clearState, coordinates, mocks, biometry) | `flows/<area>_local/` — gitignored, not in `config.yaml` |
| Mock scenarios, recorded responses | `maestro_ui_tests/mocks/scenarios/<name>/`, `mocks/_recorded/` — gitignored |
| Seed profiles, tokens | `maestro_ui_tests/secrets/wallets.env` — gitignored |
| Runner and helpers | `maestro_ui_tests/tools/` |
| Artifacts (videos, junit, logcat, report) | `build/maestro-qa/<YYYY-MM-DD>/` (gitignored) |
