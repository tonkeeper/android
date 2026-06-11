---
name: tk-impact-analisys
description: Analyze QA regression impact for Tonkeeper Android, iOS, or Web by comparing the current branch with the relevant release branch, reviewing the platform regress.txt, and recommending test blocks, missing coverage, extra checks, and a generated TXT test collection.
---

# TK Impact Analisys

Use this skill when the user wants release impact analysis for Tonkeeper and needs QA recommendations driven by code changes plus the platform regression checklist.

## Startup Parameters

Require these parameters in the user request or infer them from nearby context:

- `PLATFORM`: one of `Android`, `iOS`, `Web`

Optional parameters:

- `BASE_BRANCH`: overrides the current checked out branch
- `RELEASE_BRANCH`: overrides the auto-selected release branch
- `SOURCE_REPOSITORY`: overrides the source repository used for git comparison
- `REPO_PATH`: legacy alias for `SOURCE_REPOSITORY`
- `REGRESS_PATH`: overrides the default `regress.txt` path for the chosen platform
- `REPORT_PATH`: overrides the default Markdown report path
- `TESTS_COLLECTION_PATH`: overrides the default generated TXT collection path

For default folder mapping and current workspace availability, read [references/required-data.md](references/required-data.md).

For the reusable invocation prompt, read [references/prompt-template.md](references/prompt-template.md).

## Workflow

1. Prefer existing generated artifacts when they are available.
- Before redoing git discovery, check whether the run already has:
  - `reports/<execution-date>/<platform>/raw-git-data.txt`
  - `reports/<execution-date>/<platform>/report.md`
  - `reports/<execution-date>/<platform>/test-collection.txt`
- If those files exist and look current for the requested run, use them as the primary input for the AI refinement pass.
- Only redo repository inspection if the generated files are missing, clearly stale, or the user asked for a fresh rerun.

2. Resolve the platform-specific paths.
- Use `SOURCE_REPOSITORY` or `REPO_PATH` and `REGRESS_PATH` if the user provided them.
- Otherwise auto-detect the source repository from the nearest parent git repository that contains the skill, and use the default `regress.txt` mapping from [references/required-data.md](references/required-data.md).
- This keeps the skill portable: you can copy the skill into another platform repository and it will compare against that repository by default.
- If the selected source repository or `regress.txt` is missing, stop the analysis and report exactly what is missing.

3. Validate the git inputs inside the selected repository.
- Confirm the repo exists and is a git worktree.
- In the local-script flow, fetch release refs from `origin` first so the latest release set is available locally.
- If fetch fails with SSH authentication errors such as passphrase prompts or `Permission denied (publickey)`, stop and ask the user to run `python3 .codex/skills/tk-impact-analisys/scripts/tk_impact_analysis.py --platform <PLATFORM> --write-raw` manually in their terminal.
- After the user says it is done, do not trust that confirmation by itself. Verify that `reports/<execution-date>/<platform>/raw-git-data.txt`, `report.md`, and `test-collection.txt` were updated recently before using them.
- Detect the current branch unless `BASE_BRANCH` was provided explicitly.
- Discover release branches matching:
  - `release/YY.MM.Iterator`
  - `release/YY.MM.Iterator-hotfixiterator`
- If the current branch is the latest release branch, compare it with the previous release branch.
- If the current branch is a release branch but not the latest release, compare it with the latest release branch.
- If the current branch is not a release branch, compare it with the latest release branch.
- If `RELEASE_BRANCH` was provided explicitly, use it as the comparison target.
- Confirm both comparison refs exist locally or as remote refs.
- Prefer comparing `CURRENT_BRANCH...RELEASE_BRANCH` so the diff is relative to the merge base.

4. Inspect branch delta with git.
- Start with:
  - `git diff --name-status CURRENT_BRANCH...RELEASE_BRANCH`
  - `git diff --stat CURRENT_BRANCH...RELEASE_BRANCH`
  - `git log --left-right --cherry-pick --oneline CURRENT_BRANCH...RELEASE_BRANCH`
- Then inspect important changed files directly.
- Cluster changes into QA-relevant areas such as onboarding, wallets, send/receive, swaps, signing, security, settings, localization, analytics, widgets, build config, feature flags, and shared infrastructure.
- Extract concrete module or feature names from changed paths and use them in the report.
- Search `regress.txt` by those module names first, including obvious localized variants when they exist.
- Example: `BatteryRecharge` and `BatteryRefill` should trigger searches such as `Battery`, `батарейка`, `Recharge`, and `Refill`.
- Highlight files that did not match any known QA area.
- For each unmatched file, explain the affected module or feature directly instead of using generic filename-role guesses.
- Suggest one or more concrete tests for each unmatched file and include them in both the Markdown report and the generated TXT test collection.
- Ignore the `AGENTS.md` file since it is not user-facing code and does not have direct QA implications.

5. Review the platform regression file as an expert QA.
- Treat `regress.txt` as the current regression source of truth for that platform.
- Identify the existing regression blocks already covering the changed areas.
- Call out weak coverage, stale wording, duplicated checks, overly broad blocks, or missing negative cases.
- Do not just keyword-match titles; reason from the changed code paths and user flows.

6. Build the impact recommendation.
- Recommend regression blocks from `regress.txt` that should be executed.
- Add a dedicated section for changed files that did not match a known QA area.
- Show which module names and search terms were used for each unmatched file.
- If the module-aware search still finds no matching regression cases, call out that the functionality is new or uncovered and should be tested explicitly.
- Suggest new blocks that should be added to `regress.txt` because code changes are not covered well enough.
- Avoid grouping existing regression cases when the recommended set is small and specific.
- If there are only a few relevant existing tests, list them as separate rows in the report instead of collapsing them into a broad range.
- Use grouped ranges only when the recommendation truly covers a large contiguous block and individual rows would hurt readability.
- Suggest extra checks outside the listed blocks when the diff implies risk:
  - migrations
  - permissions
  - offline mode
  - upgrade path
  - localization
  - analytics
  - feature flags
  - crash-prone edge cases
  - platform-specific UI states

7. Persist the result to Markdown.
- Use `report.md` in the skill directory as the template for the report structure.
- Duplicate the final user-facing analysis into `REPORT_PATH` or the default run report path `reports/<execution-date>/<platform>/report.md`.
- Create the dated platform folder if it does not exist.
- If the run report already exists, update the existing sections in place instead of regenerating the whole document.
- Preserve stable headings and only replace the content under the analysis sections that changed.
- Keep the Markdown easy to scan: short sections, flat bullets, blank lines between sections, and no giant unbroken paragraphs.

8. Generate a TXT test collection with the recommended tests.
- Use `test-collection-template.txt` in the skill directory as the TXT template.
- Write the actual collection into `TESTS_COLLECTION_PATH` or the default run path `reports/<execution-date>/<platform>/test-collection.txt`.
- The generated file must follow the same indented plain-text style as the platform `regress.txt`.
- Keep the generated collection focused on the recommended execution scope for this run, not the entire master regression suite.
- Group tests into logical blocks with indentation.
- It is acceptable to rewrite the run TXT collection fully on each execution; treat it as a generated artifact.
- When useful, append short source hints inline such as `[regress: 24]` or `[reason: high risk]`.

## Output Contract

Return these sections in this order:

1. `Scope`
- platform
- repo path
- regress path
- current branch
- compared release branch
- generated test collection path
- assumptions and missing inputs

2. `Changed Areas`
- changed modules and user-facing flows
- branch-level summary with risk notes

3. `Changed Files Without Known QA Area`
- list unmatched files or the most important unmatched subset
- explain probable impact
- suggest focused tests

4. `Run These Existing Regression Blocks`
- list concrete blocks or cases from `regress.txt`
- when there are only a few relevant existing cases, list each case on its own row
- explain why each block matters for this diff

5. `Add These Missing Blocks`
- propose new regression blocks or case groups missing from `regress.txt`
- explain what code change created the need

6. `Additional Checks`
- targeted exploratory checks, integrations, edge cases, and non-functional risks

7. `Open Questions`
- anything blocking confidence, such as missing platform data, unclear feature flags, or suspicious files without matching tests

Also mirror the same content into the Markdown report file with the same section order.
Also generate a TXT test collection with the recommended tests for this run.
Also include the unmatched-file test suggestions, Missing Blocks, and Additional Checks in the generated TXT collection.

## Report File Rules

- Template path: `tk-impact-analisys/report.md`
- Default run report path: `tk-impact-analisys/reports/<execution-date>/<platform>/report.md`
- Default raw git bundle path: `tk-impact-analisys/reports/<execution-date>/<platform>/raw-git-data.txt`
- Use the root `report.md` as the template only.
- Write actual analysis results into the dated run report path.
- Do not delete unrelated notes that are outside the standard analysis sections.
- If the run report already has the standard headings, replace only the section bodies.
- If the run report is missing, create it from the root `report.md` template and then fill the sections.
- Make the file more readable without full regeneration:
  - keep headings as `#` and `##`
  - use short summary bullets instead of dense prose
  - keep one idea per bullet
  - keep file paths, branches, dates, and case ids in backticks
  - do not collapse a short list of existing regression cases into one grouped range

## TXT Test Collection Rules

- Template path: `tk-impact-analisys/test-collection-template.txt`
- Default run test collection path: `tk-impact-analisys/reports/<execution-date>/<platform>/test-collection.txt`
- Use the root `test-collection-template.txt` as the template only.
- Write actual generated test collections into the dated run TXT path.
- Keep the structure compatible with the platform `regress.txt`:
  - one test or block per line
  - indentation defines nesting
  - no CSV headers or metadata rows
- Include only the recommended test subset for the specific analysis.
- Include:
  - existing regress rows selected for this run
  - suggested tests for unmatched files
  - Missing Blocks
  - Additional Checks
- Prefer this line style:
  - block line: `Wallet import and legacy mnemonic`
  - child line: `    Import TON wallet by 24-word phrase [regress: 24]`
- Keep rationale short and inline.
- When only a few existing regress tests are selected, prefer one line per concrete test instead of one line with a wide grouped range.
- Do not keep grouped existing regress references in the generated TXT collection when the selected set is small enough to list individually.

## Quality Bar

- Be opinionated and risk-based, not exhaustive for its own sake.
- Prefer concrete test intent over generic QA advice.
- Prefer module names and user flows over generic type-based guesses like `ViewModel` or `Service`.
- Tie every recommendation back to changed files, changed behavior, or a clear coverage gap.
- If the chosen platform has no repo or no `regress.txt`, say so plainly instead of inventing analysis.
