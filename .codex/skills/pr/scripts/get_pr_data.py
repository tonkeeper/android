#!/usr/bin/env python3
import argparse
import json
import os
import shutil
import subprocess
import sys

DEFAULT_REPO = "tonkeeper/ios_private"

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


def run_cmd(cmd):
  result = subprocess.run(cmd, capture_output=True, text=True)
  if result.returncode != 0:
    raise RuntimeError(result.stderr.strip() or "Command failed")
  return result.stdout


def gh_json(args, repo=None):
  cmd = ["gh"] + args
  if repo and "-R" not in cmd and "--repo" not in cmd:
    cmd += ["-R", repo]
  output = run_cmd(cmd)
  return json.loads(output)


def gh_text(args, repo=None):
  cmd = ["gh"] + args
  if repo and "-R" not in cmd and "--repo" not in cmd:
    cmd += ["-R", repo]
  return run_cmd(cmd)


def ensure_setup():
  errors = []
  if not shutil.which("gh"):
    errors.append("gh is required")
  if not shutil.which("python3") and not shutil.which("python"):
    errors.append("python3 is required")
  if errors:
    for error in errors:
      print(f"Setup error: {error}")
    sys.exit(1)
  try:
    run_cmd(["gh", "auth", "status", "-h", "github.com"])
  except RuntimeError:
    print("Setup error: gh is not authenticated for github.com")
    sys.exit(1)
  print("Setup OK.")


def fetch_pr(repo, number):
  fields = [
    "number",
    "title",
    "body",
    "author",
    "url",
    "createdAt",
    "updatedAt",
    "baseRefName",
    "headRefName",
    "additions",
    "deletions",
    "changedFiles",
    "labels",
  ]
  args = ["pr", "view", str(number), "--json", ",".join(fields)]
  return gh_json(args, repo=repo)


def fetch_pr_files(repo, number):
  args = [
    "api",
    "-H",
    "Accept: application/vnd.github+json",
    f"repos/{repo}/pulls/{number}/files",
    "--paginate",
    "--jq",
    ".[]",
  ]
  raw = gh_text(args)
  return _parse_json_lines(raw)


def fetch_pr_commits(repo, number):
  args = [
    "api",
    "-H",
    "Accept: application/vnd.github+json",
    f"repos/{repo}/pulls/{number}/commits",
    "--paginate",
    "--jq",
    ".[]",
  ]
  raw = gh_text(args)
  return _parse_json_lines(raw)


def fetch_issue_comments(repo, number):
  args = [
    "api",
    "-H",
    "Accept: application/vnd.github+json",
    f"repos/{repo}/issues/{number}/comments",
    "--paginate",
    "--jq",
    ".[]",
  ]
  raw = gh_text(args)
  return _parse_json_lines(raw)


def fetch_review_comments(repo, number):
  args = [
    "api",
    "-H",
    "Accept: application/vnd.github+json",
    f"repos/{repo}/pulls/{number}/comments",
    "--paginate",
    "--jq",
    ".[]",
  ]
  raw = gh_text(args)
  return _parse_json_lines(raw)


def fetch_pr_base_sha(repo, number):
  args = [
    "api",
    "-H",
    "Accept: application/vnd.github+json",
    f"repos/{repo}/pulls/{number}",
    "--jq",
    ".base.sha",
  ]
  sha = gh_text(args).strip()
  return sha or None


def fetch_commit(repo, sha):
  if not sha:
    return None
  args = [
    "api",
    "-H",
    "Accept: application/vnd.github+json",
    f"repos/{repo}/commits/{sha}",
  ]
  return gh_json(args)


def _parse_json_lines(raw):
  items = []
  for line in raw.splitlines():
    line = line.strip()
    if not line:
      continue
    try:
      items.append(json.loads(line))
    except json.JSONDecodeError:
      continue
  return items


def _stringify_value(value):
  if value is None:
    return "null"
  if isinstance(value, bool):
    return "true" if value else "false"
  if isinstance(value, (int, float)):
    return str(value)
  return str(value)


def _format_markdown_list(value, indent=0):
  indent_str = "  " * indent
  if isinstance(value, dict):
    lines = []
    for key, item in value.items():
      if isinstance(item, (dict, list)):
        lines.append(f"{indent_str}- {key}:")
        lines.extend(_format_markdown_list(item, indent + 1))
      else:
        lines.append(f"{indent_str}- {key}: {_stringify_value(item)}")
    return lines
  if isinstance(value, list):
    lines = []
    for item in value:
      if isinstance(item, (dict, list)):
        lines.append(f"{indent_str}-")
        lines.extend(_format_markdown_list(item, indent + 1))
      else:
        lines.append(f"{indent_str}- {_stringify_value(item)}")
    return lines
  return [f"{indent_str}- {_stringify_value(value)}"]


def _render_markdown(item):
  if item is None:
    return "# error\r\n\r\nNo file data returned.\r\n"
  if not isinstance(item, dict):
    return f"# result\r\n\r\n{_stringify_value(item)}\r\n"
  sections = []
  for key, value in item.items():
    sections.append(f"# {key}\r\n")
    if key == "patch":
      if value:
        sections.append("```diff\r\n")
        sections.append(f"{value}\r\n")
        sections.append("```\r\n")
      else:
        sections.append("null\r\n")
    elif isinstance(value, (dict, list)):
      lines = _format_markdown_list(value)
      sections.append("\r\n".join(lines) + "\r\n")
    else:
      sections.append(f"{_stringify_value(value)}\r\n")
  content = "\r\n".join(sections).rstrip("\r\n")
  return content


def _render_json(payload):
  output = json.dumps(payload, indent=2, ensure_ascii=False)
  output = output.replace("\n", "\r\n").rstrip("\r\n")
  return output


def _default_output_dir(pr_number):
  repo_root = _repo_root() or os.path.dirname(os.path.abspath(__file__))
  return os.path.join(repo_root, ".context", "pull-requests", str(pr_number))


def write_output(files, pr_number, output_path=None):
  if output_path:
    output_dir = output_path
  else:
    output_dir = _default_output_dir(pr_number)
  os.makedirs(output_dir, exist_ok=True)
  for index, item in enumerate(files):
    filename = f"{index}.md"
    final_path = os.path.join(output_dir, filename)
    output = _render_markdown(item)
    with open(final_path, "w", encoding="utf-8", newline="") as handle:
      handle.write(output)
    print(final_path)


def write_sha(commit, output_dir):
  if not commit:
    return
  payload = {
    "sha": commit.get("sha"),
    "url": commit.get("html_url"),
    "author": {
      "name": commit.get("commit", {}).get("author", {}).get("name"),
      "email": commit.get("commit", {}).get("author", {}).get("email"),
      "date": commit.get("commit", {}).get("author", {}).get("date"),
      "login": (commit.get("author") or {}).get("login"),
    },
    "message": commit.get("commit", {}).get("message"),
  }
  final_path = os.path.join(output_dir, "sha.json")
  output = _render_json(payload)
  with open(final_path, "w", encoding="utf-8", newline="") as handle:
    handle.write(output)
  print(final_path)


def write_commits(commits, output_dir):
  payload = []
  for commit in commits:
    payload.append(
      {
        "sha": commit.get("sha"),
        "url": commit.get("html_url"),
        "author": {
          "login": (commit.get("author") or {}).get("login"),
          "name": commit.get("commit", {}).get("author", {}).get("name"),
          "email": commit.get("commit", {}).get("author", {}).get("email"),
          "date": commit.get("commit", {}).get("author", {}).get("date"),
        },
        "committer": {
          "login": (commit.get("committer") or {}).get("login"),
          "name": commit.get("commit", {}).get("committer", {}).get("name"),
          "email": commit.get("commit", {}).get("committer", {}).get("email"),
          "date": commit.get("commit", {}).get("committer", {}).get("date"),
        },
        "message": commit.get("commit", {}).get("message"),
      }
    )
  final_path = os.path.join(output_dir, "commits.json")
  output = _render_json(payload)
  with open(final_path, "w", encoding="utf-8", newline="") as handle:
    handle.write(output)
  print(final_path)


def _reduce_issue_comment(comment):
  return {
    "id": comment.get("id"),
    "url": comment.get("html_url") or comment.get("url"),
    "user": (comment.get("user") or {}).get("login"),
    "body": comment.get("body"),
    "created_at": comment.get("created_at"),
    "updated_at": comment.get("updated_at"),
  }


def _reduce_review_comment(comment):
  return {
    "id": comment.get("id"),
    "url": comment.get("html_url") or comment.get("url"),
    "user": (comment.get("user") or {}).get("login"),
    "body": comment.get("body"),
    "path": comment.get("path"),
    "line": comment.get("line"),
    "original_line": comment.get("original_line"),
    "side": comment.get("side"),
    "created_at": comment.get("created_at"),
    "updated_at": comment.get("updated_at"),
  }


def write_comments(issue_comments, review_comments, output_dir):
  payload = {
    "issue_comments": [_reduce_issue_comment(comment) for comment in issue_comments],
    "review_comments": [_reduce_review_comment(comment) for comment in review_comments],
  }
  final_path = os.path.join(output_dir, "comments.json")
  output = _render_json(payload)
  with open(final_path, "w", encoding="utf-8", newline="") as handle:
    handle.write(output)
  print(final_path)


def main():
  load_env_file()
  parser = argparse.ArgumentParser(description="Fetch PR metadata and file list")
  subparsers = parser.add_subparsers(dest="command", required=True)

  subparsers.add_parser("ensure-setup", help="Verify dependencies and env vars")

  get_parser = subparsers.add_parser("get-pr", help="Fetch PR metadata and files")
  get_parser.add_argument("pr_number", type=int, help="PR number")
  get_parser.add_argument("--repo", default=DEFAULT_REPO, help="GitHub repo (owner/name)")
  get_parser.add_argument("--output", help="Write JSON output to file")

  args = parser.parse_args()

  if args.command == "ensure-setup":
    ensure_setup()
    return

  output_dir = args.output or _default_output_dir(args.pr_number)
  os.makedirs(output_dir, exist_ok=True)

  files = fetch_pr_files(args.repo, args.pr_number)
  write_output(files, args.pr_number, output_path=output_dir)
  commits = fetch_pr_commits(args.repo, args.pr_number)
  write_commits(commits, output_dir)
  issue_comments = fetch_issue_comments(args.repo, args.pr_number)
  review_comments = fetch_review_comments(args.repo, args.pr_number)
  write_comments(issue_comments, review_comments, output_dir)
  base_sha = fetch_pr_base_sha(args.repo, args.pr_number)
  commit = fetch_commit(args.repo, base_sha)
  write_sha(commit, output_dir)


if __name__ == "__main__":
  main()
