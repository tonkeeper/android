---
name: tk-qa-verify
description: Verify a Tonkeeper Android Linear task (Ready for Verification / PR) end-to-end with Maestro on a local emulator or device — assess whether Claude can test it, propose an estimate and a smoke/positive/negative test plan for human approval, analyse the PR for affected adjacent areas, execute the flows with screen recordings, and deliver a mandatory report (what was checked, video artifacts, critical cases not covered). Use for "протестируй TK-XXXX", "можем ли мы это проверить клодом/Maestro", "прогони задачу из RFV". Do NOT use for release regression selection (tk-impact-analisys) or for a static acceptance report without execution (qa-report).
metadata:
  short-description: Maestro verification of a Linear task with plan approval, execution and evidence
---

# TK QA Verify — Maestro verification of a Linear task

Run a Linear task through real verification on a device: read the ticket and the PR, decide what Claude can test, get the test plan approved by a human, execute Maestro flows with video, report evidence and gaps.

This skill **executes**. For a static acceptance report use `qa-report`; for release regression selection use `tk-impact-analisys`.

## Inputs

| Parameter | Default | Notes |
|---|---|---|
| `TICKET_ID` | required | `TK-XXXX`, or a Linear URL / view URL (then list issues in that status first) |
| `PR_NUMBER` | from the ticket attachments / branch name | GitHub PR in `tonkeeper/android_private` |
| `DEVICE` | first online `adb` device | serial; emulator `Medium_Phone_API_36.1` is the known-good default |
| `MODE` | `full` | `assess` (feasibility only) · `plan` (feasibility + test plan, stop for approval) · `run` (plan already approved) · `full` |
| `WALLET` | none (unlock the wallet already on the device) | seed profile from `maestro_ui_tests/secrets/wallets.env`: `ton_only` · `multichain` · `ton_bip39`; passed as `tools/qa_run.sh --wallet <profile>` |
| `OUT` | `build/maestro-qa/<YYYY-MM-DD>/<ticket>` | artifacts root, gitignored |

## What is committed and what stays on the machine

| Material | Location | In git? |
|---|---|---|
| Runner, preflight, helpers, mock addon | `maestro_ui_tests/tools/`, `maestro_ui_tests/mocks/mock.py` | yes |
| Cloud-safe flows and steps | `flows/<shard>/subflows/`, `steps/**` | yes |
| Local-only flows (clearState, position taps, mocks, biometry) | `maestro_ui_tests/flows/<area>_local/` | **no — gitignored** |
| Mock scenarios and recorded responses | `maestro_ui_tests/mocks/scenarios/`, `mocks/_recorded/` | **no — gitignored** |
| Seed phrases and tokens | `maestro_ui_tests/secrets/wallets.env` (template `wallets.env.example`) | **no — gitignored**, only the example is tracked |
| Videos, junit, logcat, reports | `build/maestro-qa/<date>/` | no |

Anything Maestro Cloud cannot run is not pushed. If a local flow or a scenario must reach a colleague, attach it to the ticket or the report; if it becomes Cloud-safe (stable `testTag`s, no clearState, no proxy), move it into a shard and add it to the wrapper.

Before the first run go through the prerequisites checklist in [references/setup.md](references/setup.md) §0 (Android SDK, Maestro CLI, Linear MCP, `gh`, debug build, seeds; optional mitmproxy, Maestro MCP, Figma MCP) — the whole file once per machine. Read [references/lessons.md](references/lessons.md) before writing any flow — it holds the Maestro and app gotchas that cost hours the first time.

## Workflow

Do the phases in order. Phase C never starts before a human approved the plan from Phase B, except when the user explicitly said "run without approval".

### Phase A — Understand and assess feasibility

1. Ticket context via the `linear` skill (read-only): description, acceptance criteria, comments, attachments (PR links), labels, status history. If a status view URL was given, `list_issues` with that status and let the user pick, or assess all Android-labelled ones.
2. Code context: find the commits (`git log --oneline dev | grep -i TK-XXXX`), the PR (`pr` skill or `gh pr view`), and read the diff. Confirm the fix is in the build under test: `git rev-parse` of `dev` vs the installed APK (compare a string or class added by the PR inside the pulled APK when a local build is impossible).
3. Classify every acceptance criterion with [references/feasibility.md](references/feasibility.md):
   - **UI-observable on a normal wallet** → Maestro directly.
   - **Depends on backend state** (refunds, provider outages, limits, balances) → Maestro + mocked responses (`maestro_ui_tests/mocks`, local only).
   - **Value never shown in UI** (which provider answered, analytics) → Maestro drives, logcat asserts (`NetLog` in the debug build).
   - **Needs money movement / real signing / hardware / store purchase / another device** → manual, say so.
   - **Visual-only** (colour, animation, icon) → screenshot review, not an assertion.
4. Produce the feasibility verdict: `Automatable` / `Automatable with mocks` / `Partial` / `Manual only`, the blockers, and an estimate: **S** ≤ 1 h (existing steps, no new locators), **M** 1–3 h (new flow, one or two new locators or one mock scenario), **L** half a day+ (new device state such as biometrics enrolment, several mock scenarios, a wallet with funds).

In `assess` mode stop here and report.

### Phase B — Test plan for approval

Build the plan from the acceptance criteria **and** from the PR diff, in this priority order:

1. **Smoke** — the screen opens, the feature is reachable, nothing crashes (1–2 cases).
2. **Positive** — each acceptance criterion as the user sees it.
3. **Negative** — denied permission, error from the backend, empty state, cancelled system dialog, insufficient funds, wrong input.
4. **Edge / boundaries** — exact threshold values (`19.99 / 20 / 20.01`), zero, retries, resume after background, repeated entry.
5. **Adjacent areas from the PR** — for every changed module outside the ticket's own screen, add a side-effect check. Use the path heuristics from `qa-report` (features/ vs instance/app/, data/, api/, deeplinks, feature flags, localization, generated clients). A change in a shared repository or ViewModel means the other screens that read it get one smoke check each.

For each case write: precondition, steps, expected result, **how it will be verified** (Maestro assert / logcat / prefs via `run-as` / screenshot only) and **what it needs** (mock scenario, funded wallet, enrolled biometry, Samsung).

Mark the cases you cannot run and why. Then stop and ask the human to approve, trim or extend the list. Present the estimate again next to the plan.

### Phase C — Execute

1. `maestro_ui_tests/tools/preflight.sh <DEVICE>` — fix every FAIL, read every WARN.
   Pick the wallet fraction the ticket needs and say it in the plan: `ton_only` for deeplinks, TonConnect and legacy Fragment screens; `multichain` for MC-only behaviour; `ton_bip39` when the import picker itself matters. `tools/qa_run.sh --wallet <profile> …` feeds the seed from `secrets/wallets.env` into the standard `WALLET_WITH_MONEY` variables; flows that need a specific fraction state it in their header (`# wallet: ton_only`). A wallet already on the device is only reused when its fraction is known.
2. Prefer existing steps (`steps/**`) and existing shard subflows. New flows that need `clearState`, coordinates, mocks or device-specific state go to `flows/<area>_local/` — gitignored, never in `config.yaml`, never pushed. A new case that is Cloud-safe goes to the right shard `subflows/` and its wrapper (README "Add a case to a shard") and is committed.
3. Run every case through the runner so evidence is collected automatically:
   - `maestro_ui_tests/tools/qa_run.sh <DEVICE> <flow> <case> <OUT> -e PASSWORD_KEY=5 -e …` — video (`qa_<case>_N.mp4`), `junit.xml`, `logcat.txt`, screenshots, `console.txt`.
   - `maestro_ui_tests/tools/qa_run_mock.sh <scenario> <DEVICE> <flow> <case> <OUT> -e …` — the same behind a mitmproxy scenario; it sets and resets the device proxy itself.
   - Out-of-band checks live next to them: `check_push_pref.sh`, `parse_swap_quotes.py`, `bio_driver.sh`, `tk1276_intent_injection.sh`.
4. Backend-driven states: add `mocks/scenarios/<name>/<host>/<path>.json` (gitignored; record real responses first with `--set record=<host>`) and list the scenario with its expected UI in the report. The addon and its README are versioned, the fixtures are not.
5. Money safety: never tap Continue / Confirm / slide-to-confirm on a real send, swap, stake or purchase unless the plan approved it and the wallet is the team's funded test wallet. Quote screens are fine. Never sign from a public or someone else's wallet.
6. Look at the screenshots yourself after each run — a passing assert with the wrong screen behind it is still a fail. Re-run flaky steps once; if it still fails, report it as a failure, not as "flaky".
7. Anything you notice outside the ticket (a dead deeplink, a hang, a retry storm) goes to the report's Findings, with evidence.

### Phase D — Report (mandatory)

Write `OUT/../REPORT.md` (one report per day, sections per ticket) using [references/report-template.md](references/report-template.md). Every report has these sections, in this order, none optional:

1. **Verdict** — PASS / PASS with questions / FAIL / BLOCKED per ticket, one line why.
2. **Environment** — build (version + how you confirmed it contains the fix), device, tools, wallet used.
3. **What was checked** — table: case → expected → result → evidence file. Cases from the approved plan only; if you added one, say so.
4. **Video and artifacts** — absolute or repo-relative paths to every `qa_*.mp4`, the key screenshots, `junit.xml`, `logcat.txt`, `mitm.log`.
5. **Critical cases NOT covered** — every approved case you did not run, why (needs funds / hardware / backend / time), and how a human can cover it by hand.
6. **Findings** — bugs or questions for product and backend with evidence and repro; separate "in scope of the ticket" from "adjacent".
7. **Next steps** — what unblocks the uncovered cases.

Also tell the user in chat the verdict, the report path and the folder with videos. Attach two or three representative videos when the client can show files.

### Phase E — Publish findings to Linear (ask first)

After the report is written, segregate what came out of the run:

- **Failures** — approved cases whose Maestro asserts failed, with video + screenshot + junit.
- **Bugs / problems** — behaviour that contradicts the ticket or common sense even where the assert passed (a retry storm, a dead deeplink, a leftover control), with evidence.
- **Questions** — behaviour that may be intended; needs product or backend.

Then ask the user exactly once: **"Хотим опубликовать найденные баги в комментарии?"** with the list of items and the ticket(s) they would go to. Only after a clear yes, post one comment per ticket through the `linear` skill (`save_comment`): verdict, the failures and bugs with one-line repro and the artifact path or attached video (upload via `create_attachment_from_upload` when the file is small enough), the open questions. Adjacent-area findings go to their own ticket only if the user names it; otherwise they stay in the report and in the chat summary. Never post without the yes, never change the issue status.

## Rules

- Production wallet mindset: money, signing, keys. Quotes and previews yes; real transactions only with explicit approval on the team's test wallet.
- Evidence over claims. "Passed" means a Maestro assert, a logcat line, a prefs value or a screenshot you looked at. Cite the file.
- Keep the shared suite clean: no coordinates, no `hideKeyboard`, no `env:` defaults in flows, timeouts ≤ 15 s unless justified — README rules apply. Local-only flows may bend them with a comment saying why.
- Never leave the device in a broken state: reset `http_proxy`, stop `mitmdump`, kill stale `adb logcat`, mention enrolled PIN/fingerprint or installed CA in the report.
- Do not fix product code while verifying. Propose follow-up tasks instead.
- One ticket, one folder under `OUT`; the day's `REPORT.md` aggregates.

## Relationship to other skills

| Skill | Use it for |
|---|---|
| `linear` | ticket text, acceptance criteria, PR links |
| `pr` | PR metadata and per-file patches |
| `qa-report` | the static acceptance analysis; its path heuristics feed Phase B "adjacent areas" |
| `tk-impact-analisys` | release regression selection — not this skill |
| `tk-qa-verify` (this) | feasibility → approved plan → Maestro execution → evidence report |

## Example invocations

```text
Use $tk-qa-verify for TK-2894: assess first, then propose the plan.
```

```text
$tk-qa-verify TK-3277 on emulator-5554, run — plan approved in the thread above.
```

```text
Изучи задачи в статусе Ready for Verification (ссылка на view) и скажи, какие можно проверить Maestro и за сколько.
```
