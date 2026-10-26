#!/bin/sh
# Clones or fast-forwards the tonkeeper/analytics-schemas checkout both apps generate
# their analytics events from, and prints its path (.context/ at the repository root).
set -eu

url=${ANALYTICS_SCHEMAS_REPO_URL:-git@github.com:tonkeeper/analytics-schemas.git}
root=${ANALYTICS_SCHEMAS_ROOT:-"$(git rev-parse --show-toplevel)/.context/analytics-schemas"}

if [ ! -d "$root" ]; then
  mkdir -p "$(dirname "$root")"
  git clone "$url" "$root" >&2
elif [ -d "$root/.git" ]; then
  git -C "$root" fetch --prune origin >&2
  git -C "$root" pull --ff-only origin >&2
else
  echo "Analytics schemas path exists but is not a git repo: $root" >&2
  exit 1
fi
printf '%s\n' "$root"
