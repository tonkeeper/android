# QA Acceptance Report — <TICKET_ID>

## Metadata

- **Ticket:** `<TICKET_ID>` — <ticket title>
- **Feature branch:** `<FEATURE_BRANCH>`
- **Base branch:** `<BASE_BRANCH>` (default: `dev`)
- **PR:** `<PR_NUMBER or N/A>`
- **Report path:** `<REPORT_PATH>`
- **Generated:** `<YYYY-MM-DD>`
- **Assumptions:** <list any missing ticket context, unavailable Linear MCP, etc.>

---

## 1. Scope vs Implementation

### Acceptance criteria coverage

| # | Criterion (from ticket) | Status | Evidence in diff |
|---|-------------------------|--------|------------------|
| 1 | ... | Implemented / Partial / Missing | `path/to/file.kt` — ... |

### Not implemented / missing

- ...

### Out of scope changes

- ...

---

## 2. Changed Components

Group by module. For each item, state **what user-visible or system behavior changes**.

### `apps/wallet/features/<name>/`

- `path/to/File.kt` — <behavior impact>
- UI stack: Compose + MVI

### `apps/wallet/instance/app/`

- `path/to/Screen.kt` — <behavior impact>
- UI stack: Fragment (legacy)

### `apps/wallet/data/<name>/`

- ...

### `tonapi/<module>/`

- ...

### Other (lib/, localization/, CI/, maestro/)

- ...

---

## 3. Integration / Compatibility Impact

For each affected surface, assess **low / medium / high** risk.

| Surface | Changed? | Impact | Risk |
|---------|----------|--------|------|
| Feature flags / Remote Config | Yes/No | ... | low/medium/high |
| Deeplinks (`tonkeeper://`, `tc://`, push) | Yes/No | ... | ... |
| TonConnect / WalletConnect | Yes/No | ... | ... |
| API clients (`tonapi/*`) | Yes/No | ... | ... |
| Localization | Yes/No | ... | ... |
| Build flavors (`default`/`site`/`uk`) | Yes/No | ... | ... |
| Release minification (R8/ProGuard) | Yes/No | ... | ... |
| Multichain / WalletKit | Yes/No | ... | ... |

**Overall compatibility risk:** low / medium / high

---

## 4. Smoke Test Cases

Numbered manual steps. Include preconditions.

### Happy path

1. **Precondition:** ...
2. **Step:** ...
3. **Expected:** ...

### Edge / negative

1. ...
2. **Expected:** ...

---

## 5. Side-effect Checklist

Related areas to spot-check. Not the release regression suite — focused on this task's blast radius.

- [ ] ...
- [ ] Passcode / lockscreen if auth or sensitive screens touched
- [ ] Wallet switch / watch-only wallet if account layer touched
- [ ] Related send / receive / swap / staking flow if money modules adjacent
- [ ] Deeplink entry if navigation or routes changed
- [ ] Feature flag off/on if gated functionality
- [ ] `default` and `uk` flavor if manifest or resources changed
- [ ] Maestro coverage: `<flow path or "none — consider adding">`

**Requires new Remote Config / feature flag / secrets for QA?** Yes/No — ...

---

## 6. Risk Assessment

| Risk | Severity | Why |
|------|----------|-----|
| Money movement | ... | ... |
| Signing / key material | ... | ... |
| Security / auth | ... | ... |
| Data loss / corruption | ... | ... |
| Crash / outage | ... | ... |

**Suggested QA priority before merge:** Blocker / High / Medium / Low

---

## 7. Gaps / Questions For Dev

- ...

---

## 8. Module Duplication / Consistency Check

- ...

---

## 9. Summary

- **Scope fit:** ...
- **Top acceptance risk:** ...
- **Compatibility:** low / medium / high
- **Recommended sign-off stance:** Ready / Ready with caveats / Blocked — ...
