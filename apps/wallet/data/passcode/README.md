# Passcode, Legacy Stores & Multichain Vault

`PasscodeManager` is the single entry point for everything PIN-related: UI validation, the lock
screen, biometrics, RN migration, and the multichain vault (`McPasscodeStore`). No other component
may talk to the vault directly — only the Koin binding references it elsewhere.

## Three generations of storage

```
RN era (React Native app)          Legacy native                    Multichain vault
─────────────────────────          ─────────────────────            ─────────────────────
RNSeedStorage                      PasscodeStore (ESP)              McPasscodeStore
  seeds: scrypt(passcode)            PIN: plaintext                   argon2id(PIN) → masterKey
  passcode via biometry entry      VaultSource (ESP "vault")          → mnemonics, sessionKey
  (KeyStore, RN-written)             mnemonics: NOT PIN-bound,        → wallet app keys
                                     Android KeyStore only          (vault_metadata + credential)
        │ migration                        │ still active                   │ active
        ▼                                  ▼                                ▼
   read-only, dies with RN         source of truth for UI PIN       source of truth for MC crypto
```

Crypto roles of the PIN differ per generation: in RN it IS the encryption key material (scrypt);
in legacy native it is only a UI gate (mnemonics are protected by Android KeyStore alone); in the
vault it feeds argon2id and actually seals the master key.

## Key hierarchy (vault)

```
PIN (user-typed) or DEFAULT_PIN (constant, users without a PIN)
 │  argon2id(pin, salt)
 ▼
derivedKey                     — lives for milliseconds, only to seal/open the master key
 │  AES-GCM
 ▼
masterKey (32B, random)        — never persisted in plaintext, never outlives one call
 ├── MC wallet mnemonics       — via MnemonicCoder (close() zeroes the key copy)
 └── sessionKey (32B, random)  — persisted wrapped by masterKey
      │  plaintext held in SessionKeyStore for the whole process lifetime
      ▼
     wallet app keys           — per-wallet auth key (m/13'), signs wallet-api proofs,
                                 cannot spend, re-derivable from the mnemonic
```

## Storage map

| Store | What | Protection | PIN involved? |
|---|---|---|---|
| ESP `passcode/code` (`PasscodeStore`) | user PIN, plaintext | KeyStore via ESP | is the PIN |
| ESP `passcode/pending_pin_change` | two-phase PIN-change marker | same | — |
| ESP `vault` (`VaultSource`, data/account) | legacy TON mnemonics & private keys | KeyStore via ESP only | **no** |
| `vault_metadata` KV (`VaultRepository`) | `salt`, `iv`, `master`, `verification`, `session_iv`, `session_key` | self-protecting ciphertext / KDF params | argon2 input |
| `credential` table | MC mnemonics (UUID ids), app keys (`app_key:<walletId>`) | masterKey / sessionKey | indirectly |
| RN secure store (`RNSeedStorage`) | scrypt-encrypted seed state, biometry passcode entry | scrypt(passcode) / KeyStore | is the scrypt key |

`vault_metadata` is deliberately a separate table: no bug against `credential` can wipe the master
key material and vice versa. Key names inside it must not be renamed — dev builds already store
them (DB v2 ships an empty `Migration(1, 2)`, schema-identical to v1).

## Invariants

1. **The vault always exists and the session is unlocked after any normal app start.**
2. `setPin` runs at most once per vault lifetime (between `deleteAll()` calls); it throws if a
   master key already exists.
3. The lock screen is driven ONLY by `hasPinCode()` (ESP ∪ RN) — never by the vault. DEFAULT_PIN
   is never written to ESP, so no-PIN users never see the lock screen.
4. Legacy stores are never trusted for MC: the vault re-checks every PIN cryptographically.
5. `masterKey` is unchanged by any PIN change — only the 32-byte sealed blob is re-wrapped, no
   data is re-encrypted anywhere (legacy native storage does not depend on the PIN at all; the RN
   store is re-encrypted separately and best-effort).

## Flows

### Fresh start, no PIN anywhere (fresh install, watch/signer-only)

```
app start ──► lockscreen: hidden (hasPinCode == false)
          └─► ensureMultichainVault:
                vault absent  ──► setPin(DEFAULT_PIN)      ──► masterKey+sessionKey minted
                vault exists  ──► unlockSession(DEFAULT_PIN) ──► session unlocked
```

Effectively unencrypted — the constant ships in the APK — matching the no-PIN security level,
but every vault path stays uniform.

### Cold start with a PIN

```
app start ──► lockscreen: Input | Biometric (mandatory)
user enters PIN ──► isValid: ESP string-compare (or RN migration, see below)
   valid ──► startMultichainSession:
               vault exists ──► unlockSession(pin) ──[fail]──► recoverMultichainVault
               vault absent ──► setPin(pin)          (updater: vault born on first entry)
          └─► pending_pin_change marker cleared on success
```

### Biometric unlock

```
BiometricPrompt (no CryptoObject — UI-only check)
   success ──► lockscreen hidden
           └─► helper.getPinCode() (plaintext from ESP) ──► startMultichainSession(pin)
```

### First real PIN (onboarding / RN migration)

```
user creates PIN ──► save(code):
   1. helper.save(code)              ESP ← code
   2. sealVaultWithFirstPin:
        vault exists (DEFAULT) ──► changePin(DEFAULT → code)   masterKey survives
        vault absent           ──► setPin(code)
runs BEFORE any wallet is created
```

### RN migration (first successful code entry after updating from the RN app)

```
AccountRepository.init (no passcode needed):
   isRequestMainMigration ──► import wallet entities, ton proofs, selected id

first code entry ──► isRequestMigration (ESP empty ∧ RN has PIN) ──► migration(code):
   1. loadSecureStore(code)          scrypt-decrypt RN seed state
   2. VaultSource.addMnemonic(...)   seeds move to legacy native ESP "vault"
   3. save(code)                     ESP ← code, vault ← setPin(code)
after this the user is indistinguishable from a native one; RN biometry entry
(exportPasscodeWithBiometry) can substitute the typed code
```

### MC wallet creation (onboarding, PIN already in hand)

```
unlockOrCreateMultichainVault(pin):
   vault exists ──► unlock(pin)         vault absent ──► setPin(pin)
        └─► MnemonicCoder ──► one Room transaction:
              mnemonic credential + wallet + accounts + sealed app key
```

### MC signing / backup

```
unlockMultichainVault(context):
   PasscodeDialog ──► isValid ──► unlock(pin) ──► short-lived MnemonicCoder ──► close()
```

### PIN change (two-phase across three stores)

```
change(old, new):
   0. [RN-era user: migration(old) first]
   1. ESP ← pending_pin_change = new          marker survives process death
   2. vault.changePin(old → new)              crypto check of old; failure aborts, nothing touched
   3. helper.change(old → new)                ESP; failure ──► vault rolled back to old
   4. ESP ← pending_pin_change = null
   5. rnLegacy.changePasscode(old → new)      best-effort, logged; RN store re-encrypted
legacy native VaultSource: untouched — its data never depended on the PIN
```

### Reset / sign out

```
callers wipe MC wallets first (deleteAllWallets)
reset():
   settings flags ← false ──► ESP cleared (PIN + marker) ──► RN mnemonics cleared
   ──► vault.deleteAll() ──► setPin(DEFAULT_PIN)           invariant 1 holds immediately
"PIN exists, no wallets" branch in RootViewModel is guarded by a raw-row
hasAnyWallet() across BOTH repositories — never resets on a read error
```

### Desync recovery (legacy accepted the code, vault did not open)

```
recoverMultichainVault(pin):
   changePin(DEFAULT_PIN → pin)   heals an interrupted save()
   changePin(marker → pin)        heals an interrupted / half-rolled-back change()
   neither ──► rethrow ──► Crashlytics, session stays locked
                (restored-backup vault: reset + re-import from backup words)
```

## Concurrency

- `vaultMutex` serializes the compound vault sections: `ensureMultichainVault`,
  `startMultichainSession` (+recovery), coder acquisition in `unlockOrCreateMultichainVault`,
  the marker sequence in `change()`, `sealVaultWithFirstPin`, `resetMultichainVault`. It is
  non-reentrant: leaf sections only, never held across dialogs or `migration()`.
- `McPasscodeStore` has its own internal mutex per operation; the `setPin` guard is the last line
  of defence below the manager.
- `SessionKeyStore` uses an `AtomicReference` with validated reads (reference is swapped before
  the old key is zeroed; a torn copy is detected and re-read).

## Known limitations

- Biometrics is not cryptographic: `BiometricPrompt` runs without a `CryptoObject` and only gates
  reading the ESP PIN in UI. A real design would wrap the PIN in a KeyStore key with
  `setUserAuthenticationRequired(true)`. The RN-era biometry entry was KeyStore-bound, but the
  native `setupBiometry`/`removeBiometry` are currently no-ops — only RN-written entries are read.
- Legacy native mnemonics (`VaultSource`) are not PIN-encrypted at all — Android KeyStore via ESP
  is their only protection; the PIN is a UI gate for them.
- The plaintext ESP PIN cannot be removed until biometrics gets a KeyStore design and legacy TON
  wallets migrate to the vault model; the RN store dies with the last unmigrated user.
