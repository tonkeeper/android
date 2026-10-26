#!/bin/sh
# Runs openapi-generator at the version Android generates its API clients with, fetching the
# jar into a user cache on first use. The generated sources are checked in, so a floating
# generator version would make the output depend on who ran it.
set -eu

version=7.26.0
sha256=1760050094997b9cc790cc1350be095f15da8c08a996b184d3eadc4ccda5e35e

cache=${OPENAPI_GENERATOR_CACHE:-"${XDG_CACHE_HOME:-$HOME/.cache}/tonkeeper/openapi-generator"}
jar="$cache/openapi-generator-cli-$version.jar"

if [ ! -f "$jar" ] || [ "$(shasum -a 256 "$jar" | cut -d' ' -f1)" != "$sha256" ]; then
  mkdir -p "$cache"
  tmp=$(mktemp "$cache/openapi-generator.XXXXXX")
  trap 'rm -f "$tmp"' EXIT
  curl -fsSL "https://repo1.maven.org/maven2/org/openapitools/openapi-generator-cli/$version/openapi-generator-cli-$version.jar" -o "$tmp"
  if [ "$(shasum -a 256 "$tmp" | cut -d' ' -f1)" != "$sha256" ]; then
    rm -f "$tmp"
    echo "error: openapi-generator-cli-$version.jar checksum mismatch" >&2
    exit 1
  fi
  mv "$tmp" "$jar"
fi

exec java -jar "$jar" "$@"
