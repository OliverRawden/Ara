#!/usr/bin/env bash
# Split the default GGUF into GitHub-Release-sized parts (< 2 GiB each) and upload to
# https://github.com/OliverRawden/Ara/releases/tag/models-v1
#
# Prerequisites: gh auth login, local model at ~/Documents/Ara/models/ (or pass path as $1)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="${1:-$HOME/Documents/Ara/models/Qwen2.5-7B-Instruct-Q4_K_M.gguf}"
TAG="models-v1"
TITLE="Ara default GGUF (Qwen2.5-7B Q4_K_M)"
CHUNK_BYTES=$((2 * 1024 * 1024 * 1024))
WORK="$ROOT/dist/model-upload"
BASE="Qwen2.5-7B-Instruct-Q4_K_M.gguf"

if [[ ! -f "$SOURCE" ]]; then
  echo "Model not found: $SOURCE" >&2
  exit 1
fi

if ! command -v gh >/dev/null; then
  echo "Install GitHub CLI: brew install gh && gh auth login" >&2
  exit 1
fi

rm -rf "$WORK"
mkdir -p "$WORK"

python3 - "$SOURCE" "$WORK" "$BASE" "$CHUNK_BYTES" <<'PY'
import pathlib
import sys

source, work, base, chunk = sys.argv[1:5]
chunk = int(chunk)
path = pathlib.Path(source)
out_dir = pathlib.Path(work)
i = 0
with path.open("rb") as src:
    while True:
        data = src.read(chunk)
        if not data:
            break
        part = out_dir / f"{base}.part{i}"
        part.write_bytes(data)
        print(f"Wrote {part.name} ({len(data)} bytes)")
        i += 1
print(f"Split into {i} parts")
PY

echo "SHA256: $(shasum -a 256 "$SOURCE" | awk '{print $1}')"
echo "Size:   $(stat -f%z "$SOURCE" 2>/dev/null || stat -c%s "$SOURCE") bytes"

if gh release view "$TAG" >/dev/null 2>&1; then
  echo "Release $TAG exists — uploading assets (replaces same names)."
else
  gh release create "$TAG" --title "$TITLE" --notes "Default on-device model for Ara (split parts; see installers/models.json)." 
fi

for part in "$WORK"/"$BASE".part*; do
  echo "Uploading $(basename "$part")..."
  gh release upload "$TAG" "$part" --clobber
done

echo "Done. Commit installers/models.json to main if part sizes changed, then verify:"
echo "  https://github.com/OliverRawden/Ara/releases/tag/$TAG"