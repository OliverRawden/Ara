#!/usr/bin/env bash
# Split the heavy GGUF into GitHub-Release-sized parts (strictly < 2 GiB each) and upload to
# https://github.com/OliverRawden/Ara/releases/tag/models-heavy-v1
#
# Prerequisites: gh auth login, local model at ~/Documents/Ara/models/ (or pass path as $1)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="${1:-$HOME/Documents/Ara/models/Qwen2.5-Coder-32B-Instruct-Q4_K_M.gguf}"
TAG="models-heavy-v1"
TITLE="Ara heavy GGUF (Qwen2.5-Coder-32B Q4_K_M)"
CHUNK_BYTES=$((2000 * 1024 * 1024))
WORK="$ROOT/dist/model-upload-heavy"
BASE="Qwen2.5-Coder-32B-Instruct-Q4_K_M.gguf"

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

python3 - "$SOURCE" "$WORK" "$BASE" "$CHUNK_BYTES" "$ROOT/installers/models.json" "$SHA256" "$SIZE" "$TAG" <<'PY'
import json
import pathlib
import sys

source, work, base, chunk, models_json, sha256, size, tag = sys.argv[1:9]
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

release_parts = []
for p in parts:
    release_parts.append({
        "filename": p["filename"],
        "url": f"https://github.com/OliverRawden/Ara/releases/download/{tag}/{p['filename']}",
        "sizeBytes": p["sizeBytes"],
    })

models_path = pathlib.Path(models_json)
if models_path.exists():
    doc = json.loads(models_path.read_text())
else:
    doc = {"schemaVersion": 1}

doc["heavyModel"] = {
    "id": "qwen2.5-coder-32b-q4_k_m",
    "filename": base,
    "displayName": "Qwen2.5-Coder-32B Q4_K_M (Heavy)",
    "sizeBytes": size,
    "sha256": sha256,
    "downloadUrl": None,
    "parts": release_parts,
}
models_path.write_text(json.dumps(doc, indent=2) + "\n")
print(f"Updated {models_json} (heavyModel only)")
PY

echo "SHA256: $SHA256"
echo "Size:   $SIZE bytes"

if gh release view "$TAG" >/dev/null 2>&1; then
  echo "Release $TAG exists — uploading assets (replaces same names)."
else
  gh release create "$TAG" --title "$TITLE" --notes "Heavy on-demand model for Ara multi-model routing (split parts; see installers/models.json)."
fi

for part in "$WORK"/"$BASE".part*; do
  echo "Uploading $(basename "$part")..."
  gh release upload "$TAG" "$part" --clobber
done

echo "Done. Commit installers/models.json to main, then verify:"
echo "  https://github.com/OliverRawden/Ara/releases/tag/$TAG"