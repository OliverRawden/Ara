# Ara

**On-device AI assistant for macOS, Windows, and Linux.**

Ara is a desktop chat application that runs large language models **entirely on your machine** — no cloud inference, no API keys. Built with JavaFX and [java-llama.cpp](https://github.com/kherud/java-llama.cpp), it pairs a modern sidebar-first UI with agent tool calling, encrypted storage, and deep integration with [Vex](https://github.com/OliverRawden/Vex).

> Proprietary software — Copyright © 2026 Oliver Rawden. See [LICENSE](LICENSE).

---

## Downloads (Latest Stable)

**Test users with repository access:** Download the latest stable installers from the [Releases](https://github.com/OliverRawden/Ara/releases) page.

- Look for the release tagged with the current version (see root `version` file, currently 5.6).
- Assets include `.dmg` (macOS), `.msi` (Windows), and other platform packages.
- See `installers/README.md` for build and upload instructions.

Pre-built installers are attached to GitHub Releases (recommended — keeps the git history clean).

---

## Highlights

- **Private by design** — GGUF models, chats, memory, and audit logs stay under `~/Documents/Ara/`
- **Streaming chat** — Real-time token streaming with Cupertino dark/light themes and system accent colours
- **Agent tools** — Terminal commands, web search, and persistent memory via Vex protocol definitions (101–106)
- **Fast startup** — Splash-first launch, background model preload, and deferred settings build
- **Optional encryption** — AES-256-GCM for chats, `context.md`, and audit logs (PBKDF2 passphrase unlock)

---

## Quick start

**Requirements:** JDK 21+ (Java 25 recommended), Gradle wrapper included.

```bash
git clone https://github.com/OliverRawden/Ara.git
cd Ara
./gradlew :app:run
```

On first launch, open **Settings → Model** to download the recommended Qwen2.5-7B Q4_K_M (~4.5 GB), or place any `.gguf` file in `~/Documents/Ara/models/`.

---

## How it fits together

[Original content retained]

## Build & package

```bash
# Run from source
./gradlew :app:run

# Native installer (platform-specific)
./gradlew :dist:jpackage
```

Outputs land in `dist/build/dist/`. See `installers/README.md` for how to turn these into downloadable releases.

[Rest of original README retained]