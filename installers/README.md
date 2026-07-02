# Ara Installers

This directory is a placeholder for stable release installers.

**Recommended practice:** Do **not** commit large binaries (.dmg, .msi) directly to the repository. Instead:

1. Build the installers locally or via CI:
   ```bash
   ./gradlew :dist:jpackage
   ```
   Outputs are in `dist/build/dist/` (platform-specific .dmg / .msi / .deb / .rpm etc.).

2. Create a new **GitHub Release**:
   - Go to the repo → Releases → "Draft a new release"
   - Tag version: `v5.6` (or the current stable from `version` file)
   - Target branch: `main`
   - Title: `Ara v5.6`
   - Description: Release notes, changelog highlights
   - Attach the built `.dmg` (macOS), `.msi` (Windows), and any other platform installers as release assets.

3. Test users with repository access can download the latest stable version directly from the [Releases page](https://github.com/OliverRawden/Ara/releases).

Mark the release as "Latest release" for easy access.

For automated CI/CD in the future, consider GitHub Actions to build and attach artifacts to releases.

Current stable version: see the root `version` file and the latest release tag.