#!/usr/bin/env python3
import argparse
import os
import shutil
import subprocess
import sys

DEFAULT_REPO_URL = "git@github.com:ton-org/docs.git"
DEFAULT_DOCS_DIRNAME = "docs.ton.org"

def _repo_root():
  current = os.path.abspath(os.path.dirname(__file__))
  while True:
    if os.path.isdir(os.path.join(current, ".git")):
      return current
    parent = os.path.dirname(current)
    if parent == current:
      return None
    current = parent


def load_env_file(path=None):
  if path is None:
    root = _repo_root()
    path = os.path.join(root, ".env") if root else ".env"
  if not os.path.isfile(path):
    return
  with open(path, "r", encoding="utf-8") as handle:
    for raw_line in handle:
      line = raw_line.strip()
      if not line or line.startswith("#") or "=" not in line:
        continue
      key, value = line.split("=", 1)
      key = key.strip()
      value = value.strip().strip('"').strip("'")
      if key and key not in os.environ:
        os.environ[key] = value


def _docs_dir():
  root = _repo_root()
  base = root if root else os.getcwd()
  return os.path.join(base, ".context", DEFAULT_DOCS_DIRNAME)


def _repo_url():
  return os.environ.get("TON_DOCS_REPO_URL", DEFAULT_REPO_URL)


def _run_cmd(cmd, cwd=None, allowed_codes=(0,)):
  result = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True)
  if result.returncode not in allowed_codes:
    raise RuntimeError(result.stderr.strip() or "Command failed")
  return result.stdout


def ensure_setup():
  errors = []
  if not shutil.which("python3") and not shutil.which("python"):
    errors.append("python3 is required")
  if not shutil.which("git"):
    errors.append("git is required")
  if not shutil.which("rg"):
    errors.append("rg is required")
  if errors:
    for error in errors:
      print(f"Setup error: {error}")
    sys.exit(1)
  print("Setup OK.")


def sync_docs(update=False):
  docs_dir = _docs_dir()
  if not os.path.isdir(docs_dir):
    os.makedirs(os.path.dirname(docs_dir), exist_ok=True)
    _run_cmd(["git", "clone", _repo_url(), docs_dir])
    print(docs_dir)
    return
  if update:
    _run_cmd(["git", "-C", docs_dir, "pull", "--ff-only"])
  print(docs_dir)


def list_files():
  docs_dir = _docs_dir()
  if not os.path.isdir(docs_dir):
    raise RuntimeError("Docs repo not found; run sync first")
  output = _run_cmd(["rg", "--files", "-g", "*.mdx"], cwd=docs_dir)
  print(output.rstrip("\n"))


def search_docs(query, case_sensitive=False):
  docs_dir = _docs_dir()
  if not os.path.isdir(docs_dir):
    raise RuntimeError("Docs repo not found; run sync first")
  cmd = ["rg", "--glob", "*.mdx"]
  if not case_sensitive:
    cmd.append("-i")
  cmd.append(query)
  output = _run_cmd(cmd, cwd=docs_dir, allowed_codes=(0, 1))
  print(output.rstrip("\n"))


def main():
  load_env_file()
  parser = argparse.ArgumentParser(description="Sync and search TON docs")
  subparsers = parser.add_subparsers(dest="command", required=True)

  subparsers.add_parser("ensure-setup", help="Verify dependencies")

  sync_parser = subparsers.add_parser("sync", help="Clone docs repo or update it")
  sync_parser.add_argument("--update", action="store_true", help="Pull latest changes")

  subparsers.add_parser("list-files", help="List .mdx files in the docs repo")

  search_parser = subparsers.add_parser("search", help="Search .mdx files for a query")
  search_parser.add_argument("query", help="Search query (rg syntax)")
  search_parser.add_argument("--case-sensitive", action="store_true", help="Case-sensitive search")

  args = parser.parse_args()

  if args.command == "ensure-setup":
    ensure_setup()
    return

  if args.command == "sync":
    sync_docs(update=args.update)
    return

  if args.command == "list-files":
    list_files()
    return

  if args.command == "search":
    search_docs(args.query, case_sensitive=args.case_sensitive)
    return


if __name__ == "__main__":
  main()
