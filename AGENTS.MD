# Agent Guide

This file helps AI agents navigate the project based on the task at hand.

## Task-Based Navigation

### Understanding Project Structure

- [README.MD](README.MD) — project overview, module map, tech stack, build instructions
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

- [tonapi/](tonapi/) — TON API client (core, tonkeeper, battery, legacy)
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
- [tools/hooks/](tools/hooks/) — Git pre-commit hooks
- [.github/workflows/](/.github/workflows/) — CI/CD workflows (debug, ci, release, baselineprofile)

### Localization

- [apps/wallet/localization/](apps/wallet/localization/) — i18n string resources

### API Code Generation

- [tools/scripts/generators/tonapi/](tools/scripts/generators/tonapi/) — TON API client generation
- [tools/scripts/generators/battery/](tools/scripts/generators/battery/) — Battery API generation
