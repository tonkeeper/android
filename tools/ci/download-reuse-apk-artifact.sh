#!/usr/bin/env bash
# Download a previously uploaded APK artifact by numeric ID or GitHub URL.
# Example URL:
#   https://github.com/tonkeeper/android_private/actions/runs/27105392298/artifacts/7468190537
# Example ID: 7468190537
set -euo pipefail

raw="${1:?usage: download-reuse-apk-artifact.sh <artifact-id-or-url> [dest-dir]}"
dest="${2:-dist/apk}"
repo="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
token="${GH_TOKEN:-${GITHUB_TOKEN:-}}"

if [[ -z "$token" ]]; then
  echo "::error::GH_TOKEN or GITHUB_TOKEN is required to download artifacts"
  exit 1
fi

artifact_id=""
if [[ "$raw" =~ artifacts/([0-9]+) ]]; then
  artifact_id="${BASH_REMATCH[1]}"
elif [[ "$raw" =~ ^[0-9]+$ ]]; then
  artifact_id="$raw"
else
  echo "::error::reuse_apk_artifact: need artifact ID (7468190537) or URL ending in /artifacts/7468190537"
  echo "::error::got: ${raw}"
  exit 1
fi

mkdir -p "$dest"
zip="/tmp/reuse-apk-${artifact_id}.zip"
api="https://api.github.com/repos/${repo}/actions/artifacts/${artifact_id}"
auth_header="Authorization: Bearer ${token}"

echo "========== REUSE APK =========="
echo "artifact_id=${artifact_id}"
echo "repo=${repo}"
echo "dest=${dest}"

meta_json="$(curl -fsSL \
  -H "${auth_header}" \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  "${api}" 2>&1)" || {
  echo "::error::Failed to fetch artifact metadata for id=${artifact_id} in repo=${repo}"
  echo "::error::Common causes: wrong ID, artifact expired (>90d), or token lacks actions:read"
  echo "${meta_json}"
  exit 1
}

if ! command -v jq >/dev/null 2>&1; then
  echo "::error::jq is required on the runner"
  exit 1
fi

name="$(echo "$meta_json" | jq -r '.name // empty')"
size="$(echo "$meta_json" | jq -r '.size_in_bytes // empty')"
expired="$(echo "$meta_json" | jq -r '.expired // empty')"
created="$(echo "$meta_json" | jq -r '.created_at // empty')"
echo "name=${name} size=${size} expired=${expired} created=${created}"

if [[ "$expired" == "true" ]]; then
  echo "::error::Artifact ${artifact_id} (${name}) is expired and cannot be downloaded"
  exit 1
fi

http_code="$(curl -sSL -w "%{http_code}" -o "${zip}" \
  -H "${auth_header}" \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  "${api}/zip")"

if [[ "$http_code" != "200" ]]; then
  echo "::error::Artifact zip download failed (HTTP ${http_code})"
  head -c 500 "${zip}" 2>/dev/null || true
  echo
  rm -f "${zip}"
  exit 1
fi

if ! file "${zip}" | grep -qiE 'Zip archive|compressed data'; then
  echo "::error::Downloaded file is not a zip archive (artifact ${artifact_id})"
  head -c 500 "${zip}" 2>/dev/null || true
  echo
  rm -f "${zip}"
  exit 1
fi

unzip -qo "${zip}" -d "${dest}" || {
  echo "::error::Failed to unzip artifact ${artifact_id}"
  exit 1
}
rm -f "${zip}"

apk_count="$(find "${dest}" -type f -name '*.apk' 2>/dev/null | wc -l | tr -d ' ')"
if [[ "$apk_count" -eq 0 ]]; then
  echo "::error::No .apk inside artifact ${artifact_id} (${name})"
  find "${dest}" -type f | head -20
  exit 1
fi

echo "apk_files_found=${apk_count}"
find "${dest}" -type f -name '*.apk' | sort | while read -r apk; do echo " - ${apk}"; done
echo "artifact_tag=reuse-${artifact_id}"
