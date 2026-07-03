# Ara

**On-device AI assistant for macOS, Windows, and Linux.**

Ara is a desktop chat application that runs large language models **entirely on your machine** — no cloud inference, no API keys. Built with JavaFX and [java-llama.cpp](https://github.com/kherud/java-llama.cpp), it pairs a modern sidebar-first UI with agent tool calling, encrypted storage, optional in-app updates, and deep integration with [Vex](https://github.com/OliverRawden/Vex).

> Proprietary software — Copyright © 2026 Oliver Rawden. See [LICENSE](LICENSE).

---

## Highlights

- **Private by design** — GGUF models, chats, memory, and audit logs stay under `~/Documents/Ara/`
- **Streaming chat** — Real-time token streaming with Cupertino dark/light themes and system accent colours
- **Agent tools** — Terminal commands, web search, and persistent memory via Vex protocol definitions (101–106)
- **Fast startup** — Splash-first launch, background model preload, and deferred settings build
- **Optional encryption** — AES-256-GCM for chats, `context.md`, and audit logs (PBKDF2 passphrase unlock)
- **Optional updates** — Settings → Updates checks public release metadata on GitHub (off by default)

---

## Branches

| Branch | Role |
|--------|------|
| **`main`** | Stable (currently v5.6). Releases, `installers/latest.json`, default on GitHub. |
| **`develop`** | Active development (v5.5.0+). Auto-update client and new features land here first. |

```bash
git clone https://github.com/OliverRawden/Ara.git
cd Ara
git checkout develop   # for development
# or: git checkout main   # for stable line
./gradlew :app:run
```

---

## Quick start

**Requirements:** JDK 21+ (Java 25 recommended), Gradle wrapper included.

On first launch, open **Settings → Model** to download the recommended Qwen2.5-7B Q4_K_M (~4.5 GB), or place any `.gguf` file in `~/Documents/Ara/models/`.

---

## How it fits together

```
┌─────────────┐     protocol .md files      ┌─────────────┐
│     Vex     │ ──────────────────────────► │     Ara     │
│  Console &  │   ~/Documents/Vex/        │  Chat & AI  │
│  protocols  │      Protocols/           │  inference  │
└─────────────┘                           └─────────────┘
```

Vex defines protocols and ara-tool schemas; Ara auto-loads them into every system prompt. Edit tools in Vex — changes appear in Ara when protocol files change.

| Vex ID | Tool | Purpose |
|--------|------|---------|
| 101 | `execute_command` | Run shell commands (with confirmation) |
| 102 | `get_current_datetime` | Current date/time |
| 103 | `web_search` | DuckDuckGo Lite search |
| 104 | `read_memory` | Read `~/Documents/Ara/context.md` |
| 105 | `write_memory` | Replace persistent memory |
| 106 | `append_memory` | Append structured memory entries |

---

## Data layout

| Path | Contents |
|------|----------|
| `~/Documents/Ara/models/` | GGUF model files |
| `~/Documents/Ara/data/chats.json` | Chat sessions |
| `~/Documents/Ara/data/settings.json` | App preferences |
| `~/Documents/Ara/context.md` | Long-term AI memory |
| `~/Documents/Ara/logs/audit.log` | Tool & privacy audit trail |

---

## Build & package

```bash
# Run from source
./gradlew :app:run

# Native installer (platform-specific)
./gradlew :dist:jpackage

# Release build (non-SNAPSHOT embedded version)
RELEASE=true ./gradlew :dist:jpackage
```

Outputs land in `dist/build/dist/artifacts/`. Binaries are **not** committed to git.

---

## Downloads & updates

Installers are published as [GitHub Releases](https://github.com/OliverRawden/Ara/releases). Update metadata lives on `main`:

`https://raw.githubusercontent.com/OliverRawden/Ara/main/installers/latest.json`

See [`installers/README.md`](installers/README.md) for the release workflow:

1. `./gradlew :dist:jpackage` (per platform)
2. Attach artifacts to a GitHub Release on `main`
3. `./gradlew generateLatestJson -PreleaseNotes="…"`
4. Commit and push `installers/latest.json`

In the app (**develop** builds), open **Settings → Updates** to optionally check for newer versions. Checks are **off by default** and only fetch that JSON file until you tap **Download & Install** — nothing from your chats, memory, or models is sent. macOS prefers `.pkg` installers; Windows uses `.msi`.

---

## Recommended models

| Model | Size | Notes |
|-------|------|-------|
| Qwen2.5-7B-Instruct Q4_K_M | ~4.5 GB | Default download from [Ara `models-v1` release](https://github.com/OliverRawden/Ara/releases/tag/models-v1) via `installers/models.json` |
| Llama 3.2 3B / 1B Q4 variants | Smaller | Faster on low-RAM machines |

---

## Architecture

```
tech.rawden.ara/
├── Main.java          # JavaFX entry, staged startup
├── ai/                # LlamaCpp inference, model preload
├── ui/                # Sidebar, chat, settings
├── update/            # Optional auto-update (develop)
├── tool/              # Tool catalog, terminal, web search
├── integration/       # Vex protocol loader
├── model/             # Chat & settings persistence
├── core/              # Theme, paths, encryption, macOS
└── comp/              # Reactive UI builders
```

Full developer context: [`GROK.md`](GROK.md).

---

## Related projects

- **[Vex](https://github.com/OliverRawden/Vex)** — Protocol orchestration console; manages Ara tool schemas
- Bootstrapped from [KickstartFX](https://kickstartfx.xpipe.io/) (template scaffolding only)

---

## License

Proprietary and confidential. All rights reserved. Third-party library attributions are in `dist/licenses/`.

For licensing enquiries: contact-rawden@pm.me