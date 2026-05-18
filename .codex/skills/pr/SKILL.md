---
name: pr
description: toolkit for llm-friendly interaction with GitHub pull requests; using this skill the LLM can fetch PR data and analyze it
metadata:
  short-description: LLM-friendly interactions with GitHub PRs
---

# Import pull request data as markdown

Use this skill to fetch PR metadata and file diffs into LLM-friendly Markdown files.

## Prereqs
- Ensure `gh` is installed and `gh auth login` has been completed.
- Confirm setup: `python3 .codex/skills/pr/scripts/get_pr_data.py ensure-setup` returns zero exit code.

## Usage
Fetch a PR by number:
```bash
python3 .codex/skills/pr/scripts/get_pr_data.py get-pr 354
```

Fetch from a specific repo:
```bash
python3 .codex/skills/pr/scripts/get_pr_data.py get-pr 354 --repo tonkeeper/ios_private
```

Write to a custom output directory:
```bash
python3 .codex/skills/pr/scripts/get_pr_data.py get-pr 354 --output /tmp/pr-354
```

## Output
- Default directory: `.context/tasks/<PR_NUMBER>/` (relative to repo root)
- One markdown file per diff (`0.md`, `1.md`, ...) with keys as headings and the `patch` field in a `diff` code block.
- `sha.json` with base commit metadata is written to the same directory.
- `.env` is loaded from the repo root if present.

