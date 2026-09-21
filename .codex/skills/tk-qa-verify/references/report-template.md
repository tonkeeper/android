# QA verification report — <YYYY-MM-DD>

Build: `com.ton_keeper.debug <version>` — how it was confirmed to contain the fix (commit / APK marker).
Device: <model, Android version, emulator flags>. Tools: Maestro <version>, `tools/qa_run.sh`, mocks yes/no.
Wallet: <created in onboarding, passcode 5555 | team test wallet | public phrase — quotes only>.

| Ticket | Verdict | Cases run / planned | One-line reason |
|---|---|---|---|
| TK-XXXX | PASS / PASS with questions / FAIL / BLOCKED | n / m | … |

---

## TK-XXXX — <title>

**Plan approved by:** <name, where> · **Estimate:** S/M/L, actual <time>.

### What was checked

| # | Case (class) | Expected | Result | Evidence |
|---|---|---|---|---|
| 1 | smoke — … | … | PASS | `tk-xxxx/<case>/02_screen.png`, `junit.xml` |
| 2 | positive — … | … | PASS | `mitm.log` MOCK HIT, `logcat.txt` |
| 3 | negative — … | … | FAIL | `fail.png`, console |

Cases added beyond the approved plan are marked "(added)".

### Video and artifacts

- `build/maestro-qa/<date>/tk-xxxx/<case>/qa_<case>_1.mp4` — <what it shows>
- `…/junit.xml`, `…/logcat.txt`, `…/mitm.log` (mock hits), screenshots `NN_*.png`

### Critical cases NOT covered

| Case | Why not | How to cover by hand |
|---|---|---|
| real refund via Play Billing | needs Play Console refund + backend processing | license-tester account, refund twice, reopen Battery |

### Findings

In scope:
- <bug or question, evidence path, repro>

Adjacent:
- <observation outside the ticket, evidence>

### For the Linear comment (posted only after "yes")

Failures: … · Bugs: … · Questions: … — one line each, with the artifact path or attached video.

### Next steps

- <what unblocks the uncovered cases; follow-up tasks proposed (testTags, deeplink fix)>
