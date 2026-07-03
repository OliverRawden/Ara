# Ara Installers & Update Metadata

This directory holds **release metadata only**. Installer binaries (`.pkg`, `.dmg`, `.msi`, `.deb`, `.rpm`) are **never** committed to git — they are published as [GitHub Release](https://github.com/OliverRawden/Ara/releases) assets.

The running app fetches update metadata from this public URL:

```
https://raw.githubusercontent.com/OliverRawden/Ara/main/installers/latest.json
```

Update checks are **optional** (Settings → Updates, default off). Only version metadata is fetched until the user chooses **Download & Install**.

The app also fetches default GGUF model metadata from:

```
https://raw.githubusercontent.com/OliverRawden/Ara/main/installers/models.json
```

Model binaries are **not** committed to git (too large for a single GitHub Release asset). They are published as a dedicated release:

| Item | Value |
|------|--------|
| Release tag | `models-v1` |
| Asset pattern | `Qwen2.5-7B-Instruct-Q4_K_M.gguf.part0` … `.part2` (each part &lt; 2 GiB) |
| Upload script | `./installers/split-and-upload-model.sh` |

After uploading parts, commit `installers/models.json` to **`main`** (same policy as `latest.json`).

---

## `latest.json` structure

```json
{
  "latestVersion": "5.6",
  "releaseDate": "2026-07-02",
  "releaseNotes": "Short changelog shown in the update dialog.",
  "downloads": {
    "macos-pkg": "https://github.com/OliverRawden/Ara/releases/download/v5.6/ara-installer-macos-arm64.pkg",
    "macos-pkg-arm64": "...",
    "macos-pkg-x86_64": "...",
    "macos-dmg": "...",
    "macos-dmg-arm64": "...",
    "windows-msi": "...",
    "windows-msi-x86_64": "...",
    "windows-msi-arm64": "...",
    "linux-deb-x86_64": "...",
    "linux-deb-arm64": "...",
    "linux-rpm-x86_64": "...",
    "linux-rpm-arm64": "..."
  }
}
```

- `latestVersion` must match the root `version` file (canonical part, e.g. `5.6`).
- Download keys use arch suffixes where needed; the app prefers `macos-pkg*` on macOS, `windows-msi*` on Windows, `linux-deb*` / `linux-rpm*` on Linux.
- URLs must be **direct GitHub Release asset links**: `https://github.com/OliverRawden/Ara/releases/download/vX.Y/<filename>`.

Artifact filenames from `./gradlew :dist:jpackage` (in `dist/build/dist/artifacts/`):

| Platform | Filename pattern |
|----------|-------------------|
| macOS PKG (preferred) | `ara-installer-macos-{arm64\|x86_64}.pkg` |
| macOS DMG | `ara-portable-macos-{arm64\|x86_64}.dmg` |
| Windows | `ara-installer-windows-{arm64\|x86_64}.msi` |
| Linux DEB | `ara-installer-linux-{arm64\|x86_64}.deb` |
| Linux RPM | `ara-installer-linux-{arm64\|x86_64}.rpm` |

---

## Cutting a stable release (step-by-step)

Work on the **`main`** branch for releases.

### 1. Bump version

Edit the root `version` file (e.g. `5.7`).

### 2. Build installers

On each target platform (or via CI):

```bash
./gradlew :dist:jpackage
```

Collect outputs from `dist/build/dist/artifacts/`. Optionally stage for upload:

```bash
./gradlew :dist:prepareGitHubRelease
# copies artifacts + checksums to dist/build/dist/github-release/
```

### 3. Create GitHub Release

1. Repo → **Releases** → **Draft a new release**
2. Tag: `v5.7` (must match `latestVersion`)
3. Target: `main`
4. Title: `Ara v5.7`
5. Attach all built installers from `dist/build/dist/artifacts/` (or `github-release/`)
6. Mark as **Latest release**

### 4. Update `installers/latest.json`

**Option A — Gradle (recommended after local build):**

```bash
./gradlew generateLatestJson -PreleaseNotes="Your release notes here"
```

**Option B — Shell script:**

```bash
chmod +x installers/generate-latest-json.sh
RELEASE_NOTES="Your release notes" ./installers/generate-latest-json.sh
```

**Option C — Manual:** edit `installers/latest.json` with the correct version, notes, and asset URLs.

### 5. Commit and push to `main`

```bash
git add version installers/latest.json
git commit -m "Release v5.7: update latest.json"
git push origin main
```

Users with update checks enabled will see the new version on the next manual or startup check.

---

## Testing updates

1. Temporarily set `latestVersion` in `latest.json` on `main` to a version higher than your local build (or use a test release).
2. In Ara: **Settings → Updates → Check for updates now**.
3. Confirm the dialog shows release notes and **Download & Install** fetches and opens the installer.
4. Verify the startup toggle runs a non-blocking check after unlock.

---

## Privacy

- No telemetry or analytics.
- Network access only when the user enables checks or taps **Check for updates now**.
- Only `latest.json` is fetched for version comparison; the installer downloads only after explicit user action.