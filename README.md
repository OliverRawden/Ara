# Ara

**On-device AI assistant for macOS, Windows, and Linux.**

Ara is a desktop chat application that runs large language models **entirely on your machine** — no cloud inference, no API keys. Built with JavaFX and [java-llama.cpp](https://github.com/kherud/java-llama.cpp), it pairs a modern sidebar-first UI with agent tool calling, encrypted storage, and deep integration with [Vex](https://github.com/OliverRawden/Vex).

> Proprietary software — Copyright © 2026 Oliver Rawden. See [LICENSE](LICENSE).

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
```

Outputs land in `dist/build/dist/`.

---

## Recommended models

| Model | Size | Notes |
|-------|------|-------|
| [Qwen2.5-7B-Instruct Q4_K_M](https://huggingface.co/bartowski/Qwen2.5-7B-Instruct-GGUF) | ~4.5 GB | Default download |
| Llama 3.2 3B / 1B Q4 variants | Smaller | Faster on low-RAM machines |

---

## Architecture

```
tech.rawden.ara/
├── Main.java          # JavaFX entry, staged startup
├── ai/                # LlamaCpp inference, model preload
├── ui/                # Sidebar, chat, settings
├── tool/              # Tool catalog, terminal, web search
├── integration/       # Vex protocol loader
├── model/             # Chat & settings persistence
├── core/              # Theme, paths, encryption, macOS
└── comp/              # Reactive UI builders
```

---

## Related projects

- **[Vex](https://github.com/OliverRawden/Vex)** — Protocol orchestration console; manages Ara tool schemas
- Bootstrapped from [KickstartFX](https://kickstartfx.xpipe.io/) (template scaffolding only)

---

## License

Proprietary and confidential. All rights reserved. Third-party library attributions are in `dist/licenses/`.

For licensing enquiries: contact-rawden@pm.me