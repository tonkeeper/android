# Required Data

## Startup Parameters

- `PLATFORM`: `Android`, `iOS`, or `Web`

Optional:

- `SOURCE_REPOSITORY`: override source repository location
- `BASE_BRANCH`: overrides the current checked out branch
- `RELEASE_BRANCH`: overrides the auto-selected release branch
- `REPO_PATH`: legacy alias for `SOURCE_REPOSITORY`
- `REGRESS_PATH`: override regression checklist TXT location
- `REPORT_PATH`: override Markdown report output
- `TESTS_COLLECTION_PATH`: override generated TXT collection output

Default report locations:

- template: `report.md`
- actual run report: `reports/<execution-date>/<platform>/report.md`
- tests template: `test-collection-template.txt`
- actual run tests collection: `reports/<execution-date>/<platform>/test-collection.txt`
- raw git bundle: `reports/<execution-date>/<platform>/raw-git-data.txt`

## Default Platform Mapping

- source repository:
  - auto-detect the nearest parent git repository containing the skill
- `iOS`
  - regress file: `sources/iOS/regress_EN.txt`
- `Android`
  - regress file: `sources/Android/regress.txt`
- `Web`
  - regress file: `sources/Web/regress.txt`

Resolve the default `regress.txt` paths relative to the skill directory.

## Current Workspace State

- Present now:
  - source repository auto-detects to the repo containing this skill
  - `sources/iOS/regress.txt`
  - `sources/iOS/regress_EN.txt`
- Missing now:
  - `sources/Android/regress.txt`
  - `sources/Web/regress.txt`

If the user selects `Android` or `Web` and does not provide override paths, the skill can still use the current repository for git comparison, but it must stop and report a missing default `regress.txt` file when the platform data is absent.

## Minimum Data Needed For A Useful Analysis

- A valid git repository for the chosen platform
- The current branch or an explicit `BASE_BRANCH`
- At least one release branch matching `release/YY.MM.Iterator` or `release/YY.MM.Iterator-hotfixiterator`
- A platform regression checklist in `regress.txt`

## Recommended Commands

Run these inside the selected repo:

```bash
git fetch origin '+refs/heads/release/*:refs/remotes/origin/release/*'
git rev-parse --is-inside-work-tree
git branch --show-current
git branch --list 'release/*'
git branch -r --list 'origin/release/*'
git diff --name-status CURRENT_BRANCH...SELECTED_RELEASE_BRANCH
git diff --stat CURRENT_BRANCH...SELECTED_RELEASE_BRANCH
git log --left-right --cherry-pick --oneline CURRENT_BRANCH...SELECTED_RELEASE_BRANCH
```

Branch selection rules:

- if `CURRENT_BRANCH` is the latest release, compare it with the previous release
- if `CURRENT_BRANCH` is a release but not the latest release, compare it with the latest release
- if `CURRENT_BRANCH` is not a release branch, compare it with the latest release

Then inspect high-risk files from the diff and cross-check them against relevant blocks in `regress.txt`.
Also inspect files that did not match any known QA area, explain probable impact, and suggest focused tests for them.

For the lowest-cost AI flow, generate these local artifacts first and then hand them to Codex:

- `raw-git-data.txt`
- `report.md`
- `test-collection.txt`

The generated `test-collection.txt` should include:

- selected existing regress rows
- unmatched-file suggested tests
- Missing Blocks
- Additional Checks
