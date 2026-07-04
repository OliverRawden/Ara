#!/usr/bin/env bash
# Split the heavy GGUF into GitHub-Release-sized parts and upload to models-heavy-v1.
#
# Disk-safe: creates ONE part at a time, uploads it, then deletes the local copy.
# Resumable: skips parts already present on the GitHub release.
#
# Prerequisites:
#   brew install gh && gh auth login
#   Local GGUF (default): ~/Documents/Ara/models/Qwen2.5-Coder-32B-Instruct-Q4_K_M.gguf
#
# Usage:
#   ./installers/split-and-upload-heavy-model.sh
#   ./installers/split-and-upload-heavy-model.sh /path/to/model.gguf
#
# After it finishes, tell the agent (or run yourself):
#   git add installers/models.json
#   git commit -m "Update heavy model manifest (models-heavy-v1)"
#   git push origin develop
#   # Also sync installers/models.json to main (runtime catalog fetches from main).
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
  echo "" >&2
  echo "Download first (≈18.5 GB):" >&2
  echo "  curl -L -o \"$SOURCE\" \\" >&2
  echo "    \"https://huggingface.co/bartowski/Qwen2.5-Coder-32B-Instruct-GGUF/resolve/main/Qwen2.5-Coder-32B-Instruct-Q4_K_M.gguf\"" >&2
  exit 1
fi

if ! command -v gh >/dev/null; then
  echo "Install GitHub CLI: brew install gh && gh auth login" >&2
  exit 1
fi

mkdir -p "$WORK"

echo "Computing SHA-256 (≈30s for 18 GB)..."
SHA256="$(shasum -a 256 "$SOURCE" | awk '{print $1}')"
SIZE="$(stat -f%z "$SOURCE" 2>/dev/null || stat -c%s "$SOURCE")"
echo "SHA256: $SHA256"
echo "Size:   $SIZE bytes"

if ! gh release view "$TAG" >/dev/null 2>&1; then
  echo "Creating release $TAG..."
  gh release create "$TAG" --title "$TITLE" \
    --notes "Heavy on-demand model for Ara multi-model routing (split parts; see installers/models.json)."
fi

echo "Checking existing release assets..."
UPLOADED="$(gh release view "$TAG" --json assets --jq '.assets[].name' 2>/dev/null || true)"
echo "Already uploaded: ${UPLOADED:-none}"

python3 - "$SOURCE" "$WORK" "$BASE" "$CHUNK_BYTES" "$TAG" <<'PY'
import pathlib
import subprocess
import sys

source = pathlib.Path(sys.argv[1])
work = pathlib.Path(sys.argv[2])
base = sys.argv[3]
chunk = int(sys.argv[4])
tag = sys.argv[5]

# Fetch already-uploaded asset names
result = subprocess.run(
    ["gh", "release", "view", tag, "--json", "assets", "--jq", ".assets[].name"],
    capture_output=True, text=True,
)
already = set(result.stdout.strip().splitlines()) if result.returncode == 0 else set()

size = source.stat().st_size
offset = 0
i = 0

with source.open("rb") as src:
    while offset < size:
        part_name = f"{base}.part{i}"
        remaining = size - offset
        this_chunk = min(chunk, remaining)

        if part_name in already:
            print(f"SKIP {part_name} (already on GitHub, {this_chunk} bytes)")
            src.seek(this_chunk, 1)
            offset += this_chunk
            i += 1
            continue

        part_path = work / part_name
        print(f"Writing {part_name} ({this_chunk} bytes)...")
        data = src.read(this_chunk)
        part_path.write_bytes(data)
        del data

        print(f"Uploading {part_name}... (this may take several minutes)")
        subprocess.run(["gh", "release", "upload", tag, str(part_path), "--clobber"], check=True)

        part_path.unlink()
        print(f"Done {part_name}")
        offset += this_chunk
        i += 1

print(f"All {i} parts complete.")
PY

echo "Updating installers/models.json..."
python3 - "$ROOT/installers/models.json" "$BASE" "$SIZE" "$SHA256" "$TAG" "$CHUNK_BYTES" <<'PY'
import json
import pathlib
import sys

models_json, base, size, sha256, tag, chunk = sys.argv[1:7]
chunk = int(chunk)
size = int(size)

parts = []
offset = 0
i = 0
while offset < size:
    this_chunk = min(chunk, size - offset)
    parts.append({
        "filename": f"{base}.part{i}",
        "url": f"https://github.com/OliverRawden/Ara/releases/download/{tag}/{base}.part{i}",
        "sizeBytes": this_chunk,
    })
    offset += this_chunk
    i += 1

path = pathlib.Path(models_json)
doc = json.loads(path.read_text()) if path.exists() else {"schemaVersion": 1}
doc["heavyModel"] = {
    "id": "qwen2.5-coder-32b-q4_k_m",
    "filename": base,
    "displayName": "Qwen2.5-Coder-32B Q4_K_M (Heavy)",
    "sizeBytes": size,
    "sha256": sha256,
    "downloadUrl": None,
    "parts": parts,
}
path.write_text(json.dumps(doc, indent=2) + "\n")
print(f"Updated {path} ({len(parts)} parts, sha256={sha256[:16]}...)")
PY

echo ""
echo "=== Complete ==="
echo "Release: https://github.com/OliverRawden/Ara/releases/tag/$TAG"
echo "Manifest: $ROOT/installers/models.json"
echo ""
echo "When done, commit the manifest:"
echo "  cd $ROOT"
echo "  git add installers/models.json installers/split-and-upload-heavy-model.sh"
echo "  git commit -m \"Update heavy model manifest (models-heavy-v1)\""
echo "  git push origin develop"