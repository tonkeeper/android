---
name: tondocs
description: toolkit to sync and search TON documentation from git@github.com:ton-org/docs.git; use when answering TON-related questions or referencing TON docs
---

# Search TON documentation

Use this skill to clone/update TON docs into `.context/docs.ton.org`, then search `.mdx` files for answers.

## Prereqs
- Ensure `git` and `rg` are installed.
- Optional: set `TON_DOCS_REPO_URL` in the repo-root `.env` to override the default repo URL.
- Confirm setup: `python3 .codex/skills/tondocs/scripts/tondocs.py ensure-setup` returns zero exit code.

## Usage
Clone docs (first time) or update:
```bash
python3 .codex/skills/tondocs/scripts/tondocs.py sync
python3 .codex/skills/tondocs/scripts/tondocs.py sync --update
```

List .mdx files:
```bash
python3 .codex/skills/tondocs/scripts/tondocs.py list-files
```

Search docs:
```bash
python3 .codex/skills/tondocs/scripts/tondocs.py search "wallet address"
python3 .codex/skills/tondocs/scripts/tondocs.py search "Message" --case-sensitive
```

## Output
- Docs checkout: `.context/docs.ton.org` (relative to repo root)
- Search output: raw `rg` matches with file and line numbers

