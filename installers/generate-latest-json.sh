#!/usr/bin/env bash
# Generate installers/latest.json from the root version file and dist artifacts.
# Usage:
#   ./installers/generate-latest-json.sh
#   RELEASE_NOTES="Bug fixes" ./installers/generate-latest-json.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION_FILE="$ROOT/version"
ARTIFACTS_DIR="$ROOT/dist/build/dist/artifacts"
OUT_FILE="$ROOT/installers/latest.json"
DIST_NAME="ara"

if [[ ! -f "$VERSION_FILE" ]]; then
  echo "Missing version file: $VERSION_FILE" >&2
  exit 1
fi

RAW_VERSION="$(tr -d '[:space:]' < "$VERSION_FILE")"
CANONICAL="${RAW_VERSION%%-*}"
TAG="v${CANONICAL}"
BASE_URL="https://github.com/OliverRawden/Ara/releases/download/${TAG}"
RELEASE_DATE="${RELEASE_DATE:-$(date +%Y-%m-%d)}"
RELEASE_NOTES="${RELEASE_NOTES:-Ara ${CANONICAL} release. See GitHub Releases for details.}"

declare -A DOWNLOADS=()

add_download() {
  local key="$1"
  local filename="$2"
  DOWNLOADS["$key"]="${BASE_URL}/${filename}"
}

if [[ -d "$ARTIFACTS_DIR" ]] && compgen -G "$ARTIFACTS_DIR/*" > /dev/null; then
  for file in "$ARTIFACTS_DIR"/*; do
    [[ -f "$file" ]] || continue
    name="$(basename "$file")"
    if [[ "$name" =~ ^${DIST_NAME}-installer-macos-(.+)\.pkg$ ]]; then
      arch="${BASH_REMATCH[1]}"
      add_download "macos-pkg-${arch}" "$name"
      DOWNLOADS["macos-pkg"]="${DOWNLOADS[macos-pkg]:-${BASE_URL}/${name}}"
    elif [[ "$name" =~ ^${DIST_NAME}-portable-macos-(.+)\.dmg$ ]]; then
      arch="${BASH_REMATCH[1]}"
      add_download "macos-dmg-${arch}" "$name"
      DOWNLOADS["macos-dmg"]="${DOWNLOADS[macos-dmg]:-${BASE_URL}/${name}}"
    elif [[ "$name" =~ ^${DIST_NAME}-installer-windows-(.+)\.msi$ ]]; then
      arch="${BASH_REMATCH[1]}"
      add_download "windows-msi-${arch}" "$name"
      DOWNLOADS["windows-msi"]="${DOWNLOADS[windows-msi]:-${BASE_URL}/${name}}"
    elif [[ "$name" =~ ^${DIST_NAME}-installer-linux-(.+)\.deb$ ]]; then
      arch="${BASH_REMATCH[1]}"
      add_download "linux-deb-${arch}" "$name"
      DOWNLOADS["linux-deb"]="${DOWNLOADS[linux-deb]:-${BASE_URL}/${name}}"
    elif [[ "$name" =~ ^${DIST_NAME}-installer-linux-(.+)\.rpm$ ]]; then
      arch="${BASH_REMATCH[1]}"
      add_download "linux-rpm-${arch}" "$name"
      DOWNLOADS["linux-rpm"]="${DOWNLOADS[linux-rpm]:-${BASE_URL}/${name}}"
    fi
  done
else
  echo "No artifacts in $ARTIFACTS_DIR — writing skeleton URLs."
  add_download macos-pkg-arm64 "${DIST_NAME}-installer-macos-arm64.pkg"
  add_download macos-pkg-x86_64 "${DIST_NAME}-installer-macos-x86_64.pkg"
  DOWNLOADS["macos-pkg"]="${DOWNLOADS[macos-pkg-arm64]}"
  add_download macos-dmg-arm64 "${DIST_NAME}-portable-macos-arm64.dmg"
  add_download macos-dmg-x86_64 "${DIST_NAME}-portable-macos-x86_64.dmg"
  DOWNLOADS["macos-dmg"]="${DOWNLOADS[macos-dmg-arm64]}"
  add_download windows-msi-x86_64 "${DIST_NAME}-installer-windows-x86_64.msi"
  add_download windows-msi-arm64 "${DIST_NAME}-installer-windows-arm64.msi"
  DOWNLOADS["windows-msi"]="${DOWNLOADS[windows-msi-x86_64]}"
  add_download linux-deb-x86_64 "${DIST_NAME}-installer-linux-x86_64.deb"
  add_download linux-deb-arm64 "${DIST_NAME}-installer-linux-arm64.deb"
  add_download linux-rpm-x86_64 "${DIST_NAME}-installer-linux-x86_64.rpm"
  add_download linux-rpm-arm64 "${DIST_NAME}-installer-linux-arm64.rpm"
fi

mkdir -p "$(dirname "$OUT_FILE")"

# Build sorted downloads JSON object
DOWNLOAD_KEYS=($(printf '%s\n' "${!DOWNLOADS[@]}" | sort))

{
  printf '{\n'
  printf '  "latestVersion": "%s",\n' "$CANONICAL"
  printf '  "releaseDate": "%s",\n' "$RELEASE_DATE"
  printf '  "releaseNotes": "%s",\n' "$(printf '%s' "$RELEASE_NOTES" | sed 's/"/\\"/g')"
  printf '  "downloads": {\n'
  for i in "${!DOWNLOAD_KEYS[@]}"; do
    key="${DOWNLOAD_KEYS[$i]}"
    url="${DOWNLOADS[$key]}"
    comma=","
    [[ $i -eq $((${#DOWNLOAD_KEYS[@]} - 1)) ]] && comma=""
    printf '    "%s": "%s"%s\n' "$key" "$url" "$comma"
  done
  printf '  }\n'
  printf '}\n'
} > "$OUT_FILE"

echo "Wrote $OUT_FILE (tag $TAG, ${#DOWNLOAD_KEYS[@]} download entries)"