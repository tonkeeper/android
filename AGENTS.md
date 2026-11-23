# Agent Guide

This file helps AI agents navigate the project based on the task at hand.

## Task-Based Navigation

### Understanding Project Structure

- [../README.md](../README.md) — monorepo layout
- [README.md](README.md) — Android project overview, module map, tech stack, build instructions
- This directory is the Android codebase (Gradle root); run all `./gradlew` commands from here,
  with `-q`: it prints only errors and failed tests with their assertions; drop it only to diagnose
- [settings.gradle.kts](settings.gradle.kts) — all modules and their include paths
- [gradle/libs.versions.toml](gradle/libs.versions.toml) — centralized dependency versions
- [kmp/mvi/README.MD](kmp/mvi/README.MD) — MVI architecture guide with examples

### Build Configuration

- [build.gradle.kts](build.gradle.kts) — root build config, signing, git hooks
- [gradle.properties](gradle.properties) — JVM args, Kotlin settings, caching
- [apps/wallet/instance/main/build.gradle.kts](apps/wallet/instance/main/build.gradle.kts) — main
  app build (flavors, signing, R8)
- [apps/wallet/instance/main/proguard-rules.pro](apps/wallet/instance/main/proguard-rules.pro) —
  ProGuard/R8 rules

### Networking & API

- [tonapi/](tonapi/) — generated REST clients (TonAPI, battery, perps, trading, exchange, …)
- [lib/network/](lib/network/) — OkHttp/SSE networking utilities
- [apps/wallet/api/](apps/wallet/api/) — wallet API layer

### Security & Crypto

- [lib/security/](lib/security/) — encryption, vault, KeyStore
- [lib/blockchain/](lib/blockchain/) — TON blockchain operations

### KMP Modules

- [kmp/mvi/](kmp/mvi/) — MVI architecture framework
- [kmp/async/](kmp/async/) — coroutine scope management (Async.Io, Async.stateDispatcher)
- [kmp/ui/](kmp/ui/) — Compose Multiplatform UI components
- [kmp/core/](kmp/core/) — platform abstractions

### Threading Model

- [kmp/mvi/src/commonMain/kotlin/com/tonapps/mvi/AsyncViewModel.kt](kmp/mvi/src/commonMain/kotlin/com/tonapps/mvi/AsyncViewModel.kt) —
  bgScope, stateScope, mainScope
- [kmp/async/src/commonMain/kotlin/com/tonapps/async/Async.kt](kmp/async/src/commonMain/kotlin/com/tonapps/async/Async.kt) —
  dispatcher definitions

### Code Quality & CI

- [detekt/detekt.yml](detekt/detekt.yml) — Detekt static analysis rules
- [tools/scripts/pre-commit-exec](tools/scripts/pre-commit-exec) — detekt pre-commit check, run by `../shared/githooks/pre-commit`
- [../.github/workflows/](../.github/workflows/) — CI/CD workflows (debug, ci, release)

### Play Store Release

- `make playstore_toolings [ACTION=status] [VERSION=YY.MM.N] [DRY_RUN=1] [YES=1]` runs
  `../shared/scripts/playstore_toolings.py`: it reads the production track in Google Play and posts the
  release's state to Slack (`waiting for review`, `20%`, `20%, halted`, `100%`), the Android counterpart
  of iOS `make appstore_toolings ACTION=status`. Without `VERSION` it announces the release rolling out
  or in review, else the completed one, skipping a release that is not live (users still have the
  previous one). It prompts before posting; `YES=1` posts without a terminal.
- Root `.env`: `ANDROID_PUBLISHER_CREDENTIALS_PATH` (either the Play service account JSON, the contents
  of the CI `ANDROID_PUBLISHER_CREDENTIALS` secret, or the user credential written by
  `gcloud auth application-default login --scopes=https://www.googleapis.com/auth/androidpublisher`;
  the account needs "View app information" on the app), `SLACK_BOT_TOKEN` and `SLACK_RELEASES_CHANNEL`.
- The track's rollout status (`inProgress 5%`) is the configured rollout, not what users get: a release
  in review, or not yet sent for review, already reports it. The release's lifecycle state
  (`applications.tracks.releases.list`) decides: `in review` posts "waiting for review", `published`
  posts the rollout, anything else (draft, not sent for review, approved not published, rejected) is
  nothing to announce and is printed instead. Uploads still go through `publish.yml` (fastlane,
  internal track) and promotion to production stays in Play Console.
- `publish.yml` runs on `release/android/<YY.MM.N>` and takes the version name from the branch. It
  builds only when no PR into the branch is open and every key is translated; for untranslated keys
  it starts `Translations` (`android-locales-translate.yml`) on the branch and fails: merge that PR, then run it again.
  `skip_inspections` drops all of that: any branch builds, with the version name from the
  `versionName` default in `apps/wallet/instance/main/build.gradle.kts`.
- The `Bump App version` workflow (`bump_version.yaml`, `platform: android`) opens a PR into `dev`
  that raises that default with `tools/scripts/bump_version.sh`: `N + 1` within the current month,
  else `YY.MM.0`.

### Localization

- [apps/wallet/localization/](apps/wallet/localization/) — i18n string resources

### API Code Generation

- [tools/scripts/generators/tonapi/](tools/scripts/generators/tonapi/) — REST client generation from `../shared/api/` specs
- `../shared/api/filters/<api>.yaml` is the operation/model allowlist shared with iOS; opt in there before using a new endpoint. `filter.schemas` keeps standalone models that operations do not reference.
- [../shared/tools/openapi-generator.sh](../shared/tools/openapi-generator.sh) — generator version pin
