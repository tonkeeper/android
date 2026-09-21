#!/usr/bin/env bash
# Upload the Maestro workspace to Maestro Cloud and wait for results.
#
# Replaces mobile-dev-inc/action-maestro-cloud so that the JUnit report the
# CLI writes (--format junit) is kept: it carries per-flow status, failure
# message, and cloud.runUrl deep links used by the GitHub summary and the
# Slack report (maestro_ui_tests/ci/slack_bot/post_maestro_cloud_slack.py).
# The action parses the same file but deletes it on exit.
#
# Required env: MAESTRO_CLOUD_ACCESS_KEY, MAESTRO_CLOUD_PROJECT_ID, APP_FILE.
# Optional env: UPLOAD_NAME, DEVICE_OS, TIMEOUT_MIN, EXCLUDE_TAGS, WORKSPACE,
#               REPORT_FILE, MAESTRO_FLOW_ENV (multiline KEY=VALUE, passed as -e).
# Writes to GITHUB_OUTPUT: console_url, app_binary_id, exit_code.
# Exits with the CLI's exit code (non-zero when flows failed).
set -uo pipefail

: "${MAESTRO_CLOUD_ACCESS_KEY:?MAESTRO_CLOUD_ACCESS_KEY is required}"
: "${MAESTRO_CLOUD_PROJECT_ID:?MAESTRO_CLOUD_PROJECT_ID is required}"
: "${APP_FILE:?APP_FILE is required}"

WORKSPACE="${WORKSPACE:-maestro_ui_tests}"
REPORT_FILE="${REPORT_FILE:-build/maestro-cloud-report.xml}"
mkdir -p "$(dirname "$REPORT_FILE")"

env_args=()
if [[ -n "${MAESTRO_FLOW_ENV:-}" ]]; then
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    env_args+=("-e" "$line")
  done <<< "$MAESTRO_FLOW_ENV"
fi

cmd=(maestro cloud
  --api-key "$MAESTRO_CLOUD_ACCESS_KEY"
  --project-id "$MAESTRO_CLOUD_PROJECT_ID"
  --app-file "$APP_FILE"
  --format junit
  --output "$REPORT_FILE"
)
[[ -n "${UPLOAD_NAME:-}" ]] && cmd+=(--name "$UPLOAD_NAME")
[[ -n "${DEVICE_OS:-}" ]] && cmd+=(--device-os "$DEVICE_OS")
[[ -n "${TIMEOUT_MIN:-}" ]] && cmd+=(--timeout "$TIMEOUT_MIN")
[[ -n "${EXCLUDE_TAGS:-}" ]] && cmd+=(--exclude-tags "$EXCLUDE_TAGS")
[[ -n "${GITHUB_REF_NAME:-}" ]] && cmd+=(--branch "$GITHUB_REF_NAME")
[[ -n "${GITHUB_SHA:-}" ]] && cmd+=(--commit-sha "$GITHUB_SHA")
if [[ -n "${GITHUB_REPOSITORY:-}" ]]; then
  cmd+=(--repo-owner "${GITHUB_REPOSITORY%%/*}" --repo-name "${GITHUB_REPOSITORY##*/}")
fi
cmd+=("${env_args[@]+"${env_args[@]}"}" --flows "$WORKSPACE")

output_file="$(mktemp)"
trap 'rm -f "$output_file"' EXIT

"${cmd[@]}" | tee "$output_file"
exit_code=${PIPESTATUS[0]}

# The CLI prints the console URL / binary id with ANSI colors; strip them
# before grepping (BSD sed needs a literal ESC byte, GNU accepts it too).
esc="$(printf '\033')"
cleaned="$(sed -E "s/${esc}\[[0-9;]*m//g" "$output_file")"
console_url="$(echo "$cleaned" | grep -oE 'https://app\.maestro\.dev/\S+' | head -n 1)"
app_binary_id="$(echo "$cleaned" | grep -oE 'App binary id: \S+' | awk '{print $NF}' | head -n 1)"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "console_url=${console_url}"
    echo "app_binary_id=${app_binary_id}"
    echo "exit_code=${exit_code}"
  } >> "$GITHUB_OUTPUT"
fi

[[ -n "$console_url" ]] && echo "::notice::Maestro Cloud console: ${console_url}"
exit "$exit_code"
