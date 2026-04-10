# Tonkeeper

A production-grade Android wallet application for TON blockchain, built with Kotlin and Jetpack
Compose.

## Project Overview

Tonkeeper is a multi-app Android project consisting of:

- **Tonkeeper Wallet** — main cryptocurrency wallet application
- **Tonkeeper Signer** — companion offline signer application
- Multiple shared libraries and Kotlin Multiplatform modules

## Project Structure

```
repo_root/
├── apps/                              # Applications
│   ├── wallet/                        # Main wallet app
│   │   ├── instance/
│   │   │   ├── app/                   # Core wallet (com.tonapps.tonkeeperx)
│   │   │   └── main/                  # Main release (com.ton_keeper)
│   │   ├── data/                      # Data layer (18 modules)
│   │   │   ├── core/                  # Core data infrastructure
│   │   │   ├── account/               # Account management
│   │   │   ├── tokens/                # Token management
│   │   │   ├── settings/              # User settings
│   │   │   ├── rates/                 # Exchange rates
│   │   │   ├── events/                # Transaction history
│   │   │   ├── collectibles/          # NFT support
│   │   │   ├── browser/               # DApp browser
│   │   │   ├── backup/                # Backup/restore
│   │   │   ├── passcode/              # Biometric/PIN
│   │   │   ├── staking/               # Staking operations
│   │   │   ├── purchase/              # In-app purchases
│   │   │   ├── battery/               # Battery/status
│   │   │   ├── dapps/                 # DApp connections
│   │   │   ├── contacts/              # Address book
│   │   │   ├── swap/                  # Token swap
│   │   │   ├── plugins/               # Plugin system
│   │   │   └── rn/                    # React Native bridge
│   │   ├── features/                  # Feature modules
│   │   ├── api/                       # API layer
│   │   └── localization/              # i18n/localization
│   └── signer/                        # Signer app (com.tonapps.signer)
│
├── kmp/                               # Kotlin Multiplatform modules
│   ├── mvi/                           # MVI architecture framework
│   ├── async/                         # Coroutine scope management
│   ├── ui/                            # Compose Multiplatform UI
│   └── core/                          # Platform abstractions
│
├── lib/                               # Shared libraries
│   ├── extensions/                    # Kotlin extensions
│   ├── security/                      # Crypto, vault, KeyStore
│   ├── network/                       # OkHttp/SSE networking
│   ├── blockchain/                    # TON blockchain operations
│   ├── sqlite/                        # Database layer
│   ├── qr/                            # QR code handling
│   ├── ledger/                        # Ledger hardware wallet
│   ├── log/                           # Logging
│   ├── icu/                           # ICU internationalization
│   ├── emoji/                         # Emoji handling
│   ├── base64/                        # Base64 encoding
│   ├── bus/                           # Event bus
│   └── ur/                            # Uniform Resources
│
├── ui/                                # UI modules
│   ├── uikit/
│   │   ├── core/                      # BaseFragment, Navigation, components
│   │   ├── color/                     # Color themes
│   │   ├── icon/                      # Icon set
│   │   ├── list/                      # List components
│   │   └── flag/                      # Flag components
│   ├── blur/                          # Blur effects
│   └── shimmer/                       # Shimmer animations
│
├── tonapi/                            # TON API client library
│   ├── core/                          # Core API client
│   ├── tonkeeper/                     # Tonkeeper-specific API
│   ├── battery/                       # Battery API
│   └── legacy/                        # Legacy API
│
├── buildLogic/                        # Custom Gradle plugins
├── baselineprofile/                   # Performance optimization profiles
├── detekt/                            # Code quality configuration
├── fastlane/                          # CI/CD automation
└── tools/                             # Build tools and scripts
    ├── hooks/                         # Git pre-commit hooks
    └── scripts/                       # Code generators (tonapi, battery)
```

## Tech Stack

| Category      | Technologies                                                  |
|---------------|---------------------------------------------------------------|
| Language      | Kotlin, KMP (Kotlin Multiplatform)                            |
| UI            | Jetpack Compose, Compose Multiplatform, Material 3            |
| Architecture  | MVI (custom KMP implementation)                               |
| DI            | Koin                                                          |
| Async         | Kotlin Coroutines, Flow                                       |
| Networking    | OkHttp (Cronet), SSE                                          |
| Serialization | Kotlin Serialization                                          |
| Database      | SQLite (bundled)                                              |
| Blockchain    | ton-kotlin (tvm, crypto, tlb, contract), Web3j, Bouncy Castle |
| Firebase      | Analytics, Crashlytics, Messaging, Performance, Remote Config |
| Image Loading | Coil                                                          |
| Code Quality  | Detekt, R8/ProGuard, Baseline Profiles                        |
| CI/CD         | GitHub Actions, Fastlane                                      |

## Build Configuration

**Requirements:** JDK 21 (Temurin/Zulu), Android SDK (API 36), 6 GB RAM

### Build Flavors

| Flavor    | Description              |
|-----------|--------------------------|
| `default` | Standard build           |
| `site`    | Web distribution variant |

### Build Types

| Type      | Minification | ABIs                                |
|-----------|--------------|-------------------------------------|
| `debug`   | No           | arm64-v8a                           |
| `beta`    | Yes          | arm64-v8a, x86_64                   |
| `release` | Yes          | arm64-v8a, armeabi-v7a, x86, x86_64 |

### Building

```bash
# Wallet debug
./gradlew :apps:wallet:instance:main:assembleDefaultDebug

# Wallet release
./gradlew :apps:wallet:instance:main:assembleDefaultRelease

# Signer
./gradlew :apps:signer:assembleDebug
```

### Signing Configuration

The project supports flexible signing configuration for both debug and release builds:

#### Release Builds

Release builds use injected signing properties for CI/CD:

```properties
android.injected.signing.store.file=<path-to-keystore>
android.injected.signing.store.password=<store-password>
android.injected.signing.key.alias=<key-alias>
android.injected.signing.key.password=<key-password>
```

#### Debug Builds

Debug builds support two signing methods:

1. **Injected Properties** (for CI/CD):

```properties
android.injected.signing.debug.file=<path-to-debug-keystore>
android.injected.signing.debug.password=<debug-password>
android.injected.signing.debug.key.alias=<debug-alias>
android.injected.signing.debug.key.password=<debug-key-password>
```

2. **Default Debug Keystore** (local development):
    - File: [debug.keystore](debug.keystore) (in project root)
    - Store Password: `android`
    - Key Alias: `androiddebugkey`
    - Key Password: `android`

The build system automatically falls back to the default debug keystore if no injected properties
are provided.

## Architecture

The project uses a custom **MVI (Model-View-Intent)** architecture built as a KMP module.
See [kmp/mvi/README.md](kmp/mvi/README.md) for detailed documentation.

Key patterns:

- **Unidirectional data flow:** Action → State → ViewState → Compose
- **Repository pattern:** Data access abstracted through 18 focused data modules
- **Koin DI:** ViewModels and repositories registered in module-level Koin modules
- **Thread safety:** Dedicated dispatchers for state mutations, computation, IO

## CI/CD

GitHub Actions workflows in `.github/workflows/`:

| Workflow              | Trigger        | Purpose                        |
|-----------------------|----------------|--------------------------------|
| `debug.yml`           | Push to `dev`  | Dev builds                     |
| `ci.yml`              | Tagged commits | Release builds                 |
| `baselineprofile.yml` | Manual         | Performance profile generation |
| `publish.yml`         | Manual         | Mirror to public repository    |

## Key Configuration Files

| File                        | Purpose                                     |
|-----------------------------|---------------------------------------------|
| `settings.gradle.kts`       | Module definitions                          |
| `build.gradle.kts`          | Global plugins, signing, git hooks          |
| `gradle.properties`         | JVM args (-Xmx6g), caching, Kotlin settings |
| `gradle/libs.versions.toml` | Centralized dependency versions             |
| `detekt/detekt.yml`         | Static analysis rules                       |
