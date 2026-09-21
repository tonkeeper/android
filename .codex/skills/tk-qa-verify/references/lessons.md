# Lessons that cost hours (read before writing a flow)

Collected on 2026-09-07 while verifying TK-3365/3330, TK-3277, TK-1276, TK-3254 and TK-2894 on `Medium_Phone_API_36.1`.

## Maestro 2.4 behaviour

- A flow-level `env:` block **overrides** `-e` from the CLI. A "default" in the file silently turns every parametrised run into that default. Pass parameters only with `-e` and `assertTrue` them at the top of the flow.
- `text:` is a full-match, case-insensitive regex. `"≈"` never matches "1 GRAM ≈ 1.40 USD₮"; use `".*≈.*"`. `^[A-Z]{2,6}$` also matches `Send` and `Swap`.
- Compose bottom sheets leave the home list in the hierarchy. `below:`/`above:` anchors must bracket the target (`below: "^Receive$"`, `above: "^Balance: 0$"`), otherwise the tap lands on the home screen behind the sheet.
- `scrollUntilVisible` on an emulator needs ~5 s per scroll-and-check round trip; 15 s ceilings fail on long pages. Use 30 s and say why in a comment.
- A center-screen swipe on an asset page is eaten by the chart; drag from a text anchor ("Last day") instead. "Your balance" only exists for funded assets.
- Stale `adb logcat` processes or a half-dead Maestro server produce "Android driver did not start up in time". `pkill -f 'adb.*logcat'`, `adb uninstall dev.mobile.maestro dev.mobile.maestro.test`.
- `io.grpc.StatusRuntimeException: UNAVAILABLE … Connection refused: localhost:7001` right at "Getting device info" (flow fails in 0 s, no install step in `maestro.log`, even with `--reinstall-driver`): the CLI found a session entry for the device in `~/.maestro/sessions` (`ANDROID_<serial>_<uuid>=<ms>`) and assumed another Maestro process already holds the driver, so it never starts one. Entries are left behind by killed runs and by Maestro Studio. Fix: `: > ~/.maestro/sessions` (it is a file, not a directory) and rerun; reinstalling the driver APKs or rebooting the emulator does not help.
- Screen recording through `adb shell screenrecord` uses a software codec on the emulator and eats a full core. Record at `--size 540x1200 --bit-rate 2000000` (already in `tools/qa_run.sh`).

## The app

- The pin overlay (`id: pin_title`) can appear a few seconds after Compose home (`Send`) is already in the hierarchy. Always handle the pin first, then assert `notVisible: pin_title`, then treat `Send` as home.
- When biometry is enabled, launch shows a BiometricPrompt over the pin. Tap `id: button_negative` (the scrim also has accessibilityText "Cancel", so `tapOn: "^Cancel$"` hits the scrim), then enter the pin.
- BiometricPrompt is a secure window: screenshots are black. Assert `id: button_negative` and title `Keeper.*` (debug flavour app name is "Keeper Dev" since the TK-3495 rebrand, "Tonkeeper Dev" before; the launcher label changed with it — TK-3691).
- Home top bar (scan / history / gear) and the battery icon next to the total balance are Compose `AndroidView`s without `testTag` and not clickable in the accessibility tree. Local flows tap by position (`point: "92%,6%"` gear, `"59%,16%"` battery with a `$ 0` balance). Follow-up task proposed to add testTags.
- Custom `SwitchView` (`id: push`, `id: biometric`) does not expose `checked`. Verify via `run-as com.ton_keeper.debug cat shared_prefs/wallet_prefs.xml` (`push_<walletId>`) or `settings.xml` (`biometric`).
- `tonkeeper://…` deeplinks are silently ignored on a wallet that has only a multichain account: `RootViewModel.processDeepLink` waits for a legacy `SelectedState.Wallet`. Do not navigate with deeplinks in MC-wallet flows; a follow-up task exists.
- `adb shell cmd locale set-app-locales` does not change the app language: on start the app calls `AppCompatDelegate.setApplicationLocales` from its own Settings → Language value and overwrites the per-app locale. Switch languages through Settings → Language (row `Language`/`Dil`, items by native name such as `Türkçe`, back with `System`/`Sistem`); pool names and tickers are not localized, so anchor waits on them.
- Liquid-staking (Tonstakers) `StakedCell` takes its amount from the tsTON jetton balance, not from `/v2/staking/nominator/…/pools` `amount`; a nominator-pools mock alone shows the cell only when `pending_withdraw` or `ready_withdraw` is non-zero. Mock the jetton balance too for a "staked, nothing pending" smoke.
- Debug build logs every HTTP call (`LoggingInterceptor`, tag `NetLog`, 6 KB cap per entry — long responses lose their "End of Response" line). Good enough to assert request bodies and the first fields of a response.
- Debug build trusts user CAs and has no pinning → mitmproxy mocks work with zero app changes. System apps (gstatic) will fail TLS through the proxy; ignore that noise.
- After `pm clear` the first cold start once hung on the splash for 2.5 min; a second launch was fine. Budget a retry.
- `/purchases` returning 500 makes the client retry 6× in a row (`withRetry`) — visible in `mitm.log`.

## Wallet and data facts

- Onboarding flows create a multichain wallet with passcode 5555 (`PASSWORD_KEY=5`).
- The public BIP39 vector (`abandon`×11 `about`) imports as a multichain wallet that shows ~$8k USDT on TRON belonging to strangers. Quotes only; never sign.
- Battery: `/config` `charge_cost` is 0.0026 TON, so a balance of `-0.0078` renders "-3 charges"; IAP lockout needs ≥2 refunded `android`/`ios` purchases; crypto refunds do not count.
- Swap provider is never rendered; the request/response `aggregator` is the only evidence. Without the hard switch the backend answered `swapsxyz` for 5…1000 USDT; with `android_is_swapkit_hard_switch_enabled` the client sends `["swapkit"]` and TON pairs return 503 `tokenPriceUnavailable`.

## Devices

- Samsung `R3CT60DD6JZ` had system locale ru-RU and a secure lock screen; unlock and set the app locale to en-US before using it.
- The emulator currently has PIN 1234 and one enrolled fingerprint (needed for biometry flows) and the mitmproxy CA installed.

## Added 2026-09-09 (TK-3481 / TK-3667 / TK-3148 session)

- Local Maestro `launchApp` can fail with "Unable to launch app" (5 s `TimeoutException` in `AndroidDriver.launchApp`) on a loaded host. Workaround: start the activity yourself before the flow — `adb shell "am start -W -n com.ton_keeper.debug/com.tonapps.tonkeeper.DefaultLauncherIcon --es featureFlags '{…}' --es bootFlags '{…}'"` — and keep `clearState`/`launchApp` out of the flow (`pm clear` / `pm grant` via adb instead). The whole command must be ONE quoted string: `adb shell` re-splits arguments on the device and strips the JSON quotes, after which `FeatureManager.applyStaticOverrides` silently ignores the flags.
- Parallel Maestro runs on one Mac did not work: with three extra emulators (API 37 images, and fresh API 33/36.1 AVDs) the driver never came up — `Connection refused localhost:7001`, `dev.mobile.maestro` not installed on the device. Run tickets sequentially on the known-good emulator.
- `adb shell run-as <pkg> sh -c "cat > file"` applies the redirect OUTSIDE `run-as` (Permission denied). Push the file to `/data/local/tmp` and copy it inside: `adb shell "run-as <pkg> sh -c 'cat /data/local/tmp/x > /data/data/<pkg>/shared_prefs/X.xml'"`. Force-stop the app before writing prefs.
- Analytics (`launch_app` and friends) go to Aptabase at `anonymous-analytics.tonkeeper.com` via the Aptabase SDK — not through `LoggingInterceptor`, so `NetLog` never shows them. Capture with mitmproxy (CA already on the emulator) and an addon that dumps request bodies; the SDK flushes within ~40–60 s. Do not use `--ignore-hosts '^(?!host)'` on mitmproxy 12 — it dropped every connection.
- `ToastView` lives 2 s (`DURATION_DEFAULT`); the emulator hierarchy fetch misses it. Assert the effect instead (clipboard → Send → Paste shows the address).
- `BalloonTooltip` auto-dismisses after 5 s; check it right after the pin disappears, before any `optional` step burns its timeout. `TooltipManager` prefs (`AppTooltipManager.xml`: `<name>_count`, `<name>_last_day`, `<name>_day_<placement>`, epoch days) are the reliable assert for "3 times, once a day".
- Home balance hides the "Your address" prefix after 3 copies (`SettingsRepository.addressCopyCount`); match `(Your address )?<short address>`.
- The 24-word TON seed imports as a legacy TON W5 wallet whether `android_is_import_multichain_wallet` is true or false; legacy TON wallets show TRON USDT/TRX rows and the Send action sheet ("Send tokens — To another TON/TRON wallet"), the send form field is "Address or name" with a "Paste" button.
