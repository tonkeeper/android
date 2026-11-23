#!/usr/bin/env bash
# Bumps the wallet's default versionName (YY.MM.N): N + 1 within the current month, else YY.MM.0.
# The same rule as ios/scripts/bump_version.sh; run by .github/workflows/bump_version.yaml.
set -euo pipefail

cd "$(dirname "$0")/../.."

GRADLE_FILE="apps/wallet/instance/main/build.gradle.kts"
# The line .github/workflows/publish.yml reads the version name from.
LINE='(versionName *= *System\.getenv\("VERSION_NAME"\) *\?: *")([0-9]{2})\.([0-9]{2})\.([0-9]+)(")'

MATCHES=$(grep -cE "$LINE" "$GRADLE_FILE" || true)
if [[ "$MATCHES" != 1 ]]; then
  echo "error: expected one versionName default (YY.MM.N) in $GRADLE_FILE, found $MATCHES" >&2
  exit 1
fi

# current YY and MM from one date call to avoid boundary race
read -r CUR_YY CUR_MM <<< "$(date +'%y %m')" # 26 02
read -r CUR_V_YY CUR_V_MM CUR_V_N <<< "$(sed -nE "s/.*${LINE}.*/\2 \3 \4/p" "$GRADLE_FILE")"

if [[ "$CUR_YY" == "$CUR_V_YY" && "$CUR_MM" == "$CUR_V_MM" ]]; then
  # year and month are current, just bump iteration
  NEW_N=$((10#$CUR_V_N + 1))
else
  # new month/year, start iteration from 0
  NEW_N=0
fi

NEW_VERSION="${CUR_YY}.${CUR_MM}.${NEW_N}"

echo "Current version: ${CUR_V_YY}.${CUR_V_MM}.${CUR_V_N}"
echo "New version:     $NEW_VERSION"

# -i with a suffix is the one form both GNU and BSD sed take
sed -E -i.bak "s/${LINE}/\1${NEW_VERSION}\5/" "$GRADLE_FILE"
rm "$GRADLE_FILE.bak"
