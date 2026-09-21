---
name: qa-report
description: Generate acceptance QA reports for Tonkeeper Android tasks by comparing a feature branch with dev, mapping changes to user-visible behavior, and producing smoke tests, side-effect checks, and risk assessment. Use for acceptance testing, "what to test in this task", "QA report for TK-XXXX", "приемочное тестирование", or pre-merge task sign-off. Do NOT use for release regression selection — use tk-impact-analisys instead.
metadata:
  short-description: Acceptance QA report for Tonkeeper Android tasks
---

# QA Report — Tonkeeper Android Acceptance Testing

Generate a structured acceptance QA report for a **single task or feature branch** in the Tonkeeper Android repository.

This skill is for **task acceptance before merge**. It answers: "Is the ticket implemented correctly, and how should QA validate it?"

For **release regression impact** and `regress.txt` mapping, use `tk-impact-analisys` instead. Do not merge the two workflows.

## When To Use

- Acceptance testing of a Linear ticket (`TK-XXXX`) or feature branch
- Pre-merge QA sign-off for a PR
- "What should QA test in this change?"
- Scope vs acceptance criteria review

## Required Inputs

Resolve from the user request or nearby context:

| Parameter | Default | Notes |
|-----------|---------|-------|
| `FEATURE_BRANCH` | current git branch | Branch under review |
| `BASE_BRANCH` | `dev` | Merge target; override if PR targets another branch |
| `TICKET_ID` | inferred from branch name or PR title | e.g. `TK-1855` |
| `REPORT_PATH` | `.codex/skills/qa-report/reports/acceptance/<TICKET_ID>_<branch-slug>.md` | Override if user specifies |

Optional:

- `PR_NUMBER` — fetch PR metadata and diff via the `pr` skill
- Task description — use when no Linear ticket is available

## Workflow

Follow these steps in order.

### Step 1 — Resolve git context

Always refresh remote refs before diffing:

```bash
git fetch origin
git rev-parse --abbrev-ref HEAD
git diff --name-status origin/<BASE_BRANCH>...<FEATURE_BRANCH>
git diff --stat origin/<BASE_BRANCH>...<FEATURE_BRANCH>
git log --oneline origin/<BASE_BRANCH>..<FEATURE_BRANCH>
```

Rules:

- Default `BASE_BRANCH` is `dev`, not `main`.
- Prefer three-dot diff (`A...B`) so the comparison uses the merge base.
- If the user provides a PR number, also run:
  ```bash
  python3 .codex/skills/pr/scripts/get_pr_data.py get-pr <PR_NUMBER>
  ```
  and read `.context/tasks/<PR_NUMBER>/` for patch-level detail.
- Stop and report blockers if the repo is not a git worktree or comparison refs are missing.

### Step 2 — Load task context

If `TICKET_ID` is known, use the `linear` skill (read-only) to fetch:

- Problem statement and scope
- Acceptance criteria / definition of done
- Labels, linked issues, comments with QA notes
- Figma or design links if present

If Linear MCP is unavailable, use the user-provided task description. Do not invent requirements.

If neither ticket nor description exists, state that **Scope vs Implementation** will be diff-only and list open questions.

### Step 3 — Inspect changed code

Read important changed files directly. Cluster changes by QA-relevant area using path heuristics:

| Path prefix | QA focus |
|-------------|----------|
| `apps/wallet/features/*` | Compose + MVI screens, new user flows |
| `apps/wallet/instance/app/` | Legacy Fragment UI, navigation, deeplinks |
| `apps/wallet/data/*` | Data persistence, sync, repository behavior |
| `apps/wallet/api/` | App-facing API layer |
| `tonapi/*` | Generated or hand-written API client contracts |
| `apps/wallet/localization/` | Copy, i18n regressions |
| `lib/security/`, `lib/blockchain/` | Crypto, signing, vault, KeyStore |
| `lib/wc/` | WalletConnect |
| `lib/features/`, `apps/wallet/data/features/` | Feature flags, Remote Config |
| `lib/ledger/` | Hardware wallet |
| `maestro_ui_tests/` | Automated UI coverage added or affected |
| `apps/wallet/instance/main/` | Build flavors, signing, R8/ProGuard |
| `kmp/mvi/`, `kmp/ui/` | Shared UI/MVI infrastructure |

For each cluster, identify:

- User-visible behavior change
- Whether UI is **Compose** (`features/`) or **Fragment** (`instance/app/`)
- Whether money, signing, or auth flows are touched
- Whether deeplinks, TonConnect, or WalletConnect surfaces changed

Ignore `AGENTS.md` — it has no direct QA impact.

### Step 4 — Check integration surfaces (Android-specific)

Only when the diff touches relevant areas:

**Feature flags / Remote Config**

- `lib/features/`, `WalletFeatureKey`, `FeatureManager`, `RemoteConfig`
- Static overrides via debug intent extras (`tryToApplyStaticFeatureFlags`)
- Dev menu overrides in Settings → Feature Flags

**Deeplinks / external entry points**

- Schemes: `tonkeeper://`, `ton://`, `tc://`, hosts like `app.tonkeeper.com`
- Routes in `apps/wallet/features/core/.../DeepLinkRoute.kt`
- Handlers in `RootViewModel.processDeepLink`, push extras, QR flow

**Build / distribution**

- Flavors: `default`, `site`, `uk`
- Build types: `debug`, `beta`, `release` (R8 minification)
- New permissions, manifest changes, widgets

**Localization**

- New/changed strings in `apps/wallet/localization/`
- Check critical locales if the ticket mentions translation

**Generated API clients**

- Changes under `tonapi/` or OpenAPI specs in `tools/scripts/generators/`
- Note whether regeneration was part of the diff

Do not clone external repositories or inspect backend services. Tonkeeper Android is the scope.

### Step 5 — Generate the report

Write the report to `REPORT_PATH`. Use [references/report-template.md](references/report-template.md) as the structure template.

All **9 sections are mandatory**. Fill every section; use `N/A — <reason>` when genuinely not applicable.

Section guidance for Tonkeeper Android:

1. **Scope vs Implementation** — map each acceptance criterion to code evidence; call out missing and out-of-scope changes.
2. **Changed Components** — module-aware list with behavior impact, not just filenames.
3. **Integration / Compatibility Impact** — replaces backend "cross-service" section: deeplinks, feature flags, API clients, TonConnect/WC, multichain, localization, flavor/build impact; label risk **low / medium / high**.
4. **Smoke Test Cases** — numbered manual steps with preconditions and expected results; happy path plus 1–2 edge/negative cases.
5. **Side-effect Checklist** — related flows to spot-check in the app; this is **not** the release `regress.txt` subset (that belongs to `tk-impact-analisys`).
6. **Risk Assessment** — prioritize money movement, signing, security, data loss, outage; suggest QA priority before merge.
7. **Gaps / Questions For Dev** — blockers for acceptance sign-off.
8. **Module Duplication / Consistency Check** — duplicate logic across `features/` vs `instance/app/`, or across `data/*` modules.
9. **Summary** — brief acceptance verdict inputs: scope fit, top risk, compatibility, recommended sign-off stance.

### Step 6 — Final checklist

Before finishing, verify:

- [ ] All 9 sections present
- [ ] Diff compared against `dev` (or explicit `BASE_BRANCH`)
- [ ] Acceptance criteria traced to code, not guessed
- [ ] Smoke tests and side-effect checklist are both present and distinct
- [ ] Money/security/signing risks explicitly called out when relevant
- [ ] Feature flags, deeplinks, flavors, or localization noted if touched
- [ ] Report saved to `REPORT_PATH`
- [ ] User told where the report was written

## Rules

- Assume a production wallet: money, blockchain, signing, biometrics, compliance.
- Never say "looks fine" or "should be ok" — cite specific files, classes, or behavior.
- Do not invent features absent from the diff, ticket, or PR.
- Do not use `regress.txt` as the primary source — that is `tk-impact-analisys`.
- Do not modify app code, tests, or Linear issues during report generation.
- Prefer reading changed files over summarizing from filenames alone.
- When UI changed, state whether the screen is Compose or Fragment-based.
- When Maestro flows exist for the affected area, reference them; when missing, note the gap.

## Relationship To Other Skills

| Skill | Purpose |
|-------|---------|
| `qa-report` (this) | Task acceptance before merge |
| `tk-impact-analisys` | Release regression impact vs `release/*` + `regress.txt` |
| `linear` | Ticket context and acceptance criteria |
| `pr` | PR metadata and per-file patches |

Typical flow: `linear` → `qa-report` at task completion; `tk-impact-analisys` later before release.

## Example Invocations

```text
Use $qa-report for TK-1855 on branch achupin/TK-1855. Compare with dev and save the acceptance report.
```

```text
Generate acceptance QA report for PR #506. Base branch dev.
```

```text
Приемочное тестирование текущей ветки: что проверить перед merge в dev?
```
