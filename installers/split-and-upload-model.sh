#!/usr/bin/env bash
# Split the default GGUF into GitHub-Release-sized parts (strictly < 2 GiB each) and upload to
# https://github.com/OliverRawden/Ara/releases/tag/models-v1
#
# Prerequisites: gh auth login, local model at ~/Documents/Ara/models/ (or pass path as $1)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="${1:-$HOME/Documents/Ara/models/Qwen2.5-7B-Instruct-Q4_K_M.gguf}"
TAG="models-v1"
TITLE="Ara default GGUF (Qwen2.5-7B Q4_K_M)"
# GitHub rejects assets >= 2147483648 bytes — use 2000 MiB per part.
CHUNK_BYTES=$((2000 * 1024 * 1024))
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

SHA256="$(shasum -a 256 "$SOURCE" | awk '{print $1}')"
SIZE="$(stat -f%z "$SOURCE" 2>/dev/null || stat -c%s "$SOURCE")"

python3 - "$SOURCE" "$WORK" "$BASE" "$CHUNK_BYTES" "$ROOT/installers/models.json" "$SHA256" "$SIZE" <<'PY'
import json
import pathlib
import sys

source, work, base, chunk, models_json, sha256, size = sys.argv[1:8]
chunk = int(chunk)
size = int(size)
path = pathlib.Path(source)
out_dir = pathlib.Path(work)
parts = []
i = 0
with path.open("rb") as src:
    while True:
        data = src.read(chunk)
        if not data:
            break
        part_path = out_dir / f"{base}.part{i}"
        part_path.write_bytes(data)
        parts.append({"filename": part_path.name, "sizeBytes": len(data)})
        print(f"Wrote {part_path.name} ({len(data)} bytes)")
        i += 1
print(f"Split into {i} parts")

tag = "models-v1"
release_parts = []
for p in parts:
    release_parts.append({
        "filename": p["filename"],
        "url": f"https://github.com/OliverRawden/Ara/releases/download/{tag}/{p['filename']}",
        "sizeBytes": p["sizeBytes"],
    })

doc = {
    "schemaVersion": 1,
    "defaultModel": {
        "id": "qwen2.5-7b-instruct-q4_k_m",
        "filename": base,
        "displayName": "Qwen2.5-7B-Instruct Q4_K_M",
        "sizeBytes": size,
        "sha256": sha256,
        "downloadUrl": None,
        "parts": release_parts,
    },
}
pathlib.Path(models_json).write_text(json.dumps(doc, indent=2) + "\n")
print(f"Updated {models_json}")
PY

echo "SHA256: $SHA256"
echo "Size:   $SIZE bytes"

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