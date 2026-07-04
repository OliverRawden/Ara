# Ara — On-Device AI Assistant (JavaFX)

## Overview

Ara is a desktop JavaFX chat application that runs **fully on-device** using `java-llama.cpp` (GGUF models). It was bootstrapped from [KickstartFX](https://kickstartfx.xpipe.io/) and extensively customized. The UI uses AtlantaFX Cupertino theming with a sidebar-first layout.

**Light/heavy routing** (v5.9+): a ~7B light model stays hot for fast chat; a ~32B heavy model loads on demand for code, multi-step reasoning, and complex tool use, then unloads after idle. Routing mode: AUTO (keyword escalation), LIGHT_ONLY, or HEAVY_ONLY.

Agent **tool calling** is prompt-injected (ChatML + `<|tool_call|>` tokens). **All Vex protocols** are auto-loaded from `~/Documents/Vex/Protocols/` into every system prompt via `VexProtocolCatalog` (IDs, names, descriptions, ara-tool mappings). Agent tools (101–109) and **teams** (e.g. 20) are subsets; new protocols added in Vex appear automatically when files change. Tools are editable in Vex but built-ins cannot be deleted.

**Sibling app:** [Vex](../Vex) — protocol orchestration console; manages Ara tool schemas and Vex-native protocols.

**Version:** `6.0` on `develop`; stable `5.9` on `main` (see `version` file)
**Package:** `tech.rawden.ara`  
**Product name:** `Ara`  
**JDK:** Java 21+ (Java 25 recommended)  
**JavaFX:** `27-ea+16`  
**Build:** Gradle 9.2.1, multi-module (`app` + `dist`)

---

## Branch Strategy

| Branch | `version` file | Role |
|--------|----------------|------|
| **`main`** | `5.9` | Stable. Default branch. GitHub Releases and tags are cut from here. Holds `installers/latest.json` and `installers/models.json` (update + model metadata). |
| **`develop`** | `6.0` | Unstable. All active development and Grok build sessions land here first. Next-cycle features land here before merge to `main`. |
| **`master`** | (legacy) | Tracks older default; prefer `main` / `develop`. Keep `GROK.md` in sync when touched. |

**Rule:** Never commit installer binaries (`.pkg`, `.dmg`, `.msi`) to git. They live only as GitHub Release assets.

---

## Current Repository State (4 July 2026)

| Item | Status |
|------|--------|
| **`develop` tip** | v6.0 cycle — post v5.9 release |
| **`main` tip** | `87f6b0d` — Release v5.9 |
| **`develop` version** | `6.0` (`version` file) |
| **`main` version** | `5.9` (`version` file) |
| **`develop` vs `main`** | Next-cycle development after v5.9 cut |
| **Repo visibility** | **Public** — no GitHub token required for update checks, model downloads, or release assets |
| **Update metadata** | `installers/latest.json` on `main` → `latestVersion: 5.9` |
| **Model metadata** | `installers/models.json` on `main` — light (`models-v1`) + heavy (`models-heavy-v1`) manifests |
| **Metadata URLs** | `latest.json`: https://raw.githubusercontent.com/OliverRawden/Ara/main/installers/latest.json · `models.json`: https://raw.githubusercontent.com/OliverRawden/Ara/main/installers/models.json |
| **GitHub Releases** | [v5.7](https://github.com/OliverRawden/Ara/releases/tag/v5.7) (stable app) · [models-v1](https://github.com/OliverRawden/Ara/releases/tag/models-v1) (light GGUF parts) · [models-heavy-v1](https://github.com/OliverRawden/Ara/releases/tag/models-heavy-v1) (heavy GGUF parts) |
| **Release assets (v5.7)** | macOS arm64: `ara-installer-macos-arm64.pkg`, `ara-portable-macos-arm64.dmg` |

**Release status**

- v5.9 merged to `main` (4 Jul 2026). Cut GitHub Release v5.9 with `RELEASE=true` when installers are ready.

**Local paths**

- Project: `/Users/rawden/Developer/IdeaProjects/Ara`
- Built artifacts: `dist/build/dist/artifacts/`
- Runtime data: `~/Documents/Ara/`

### Recent `develop` work (v5.8 cycle)

1. **Light/heavy multi-model routing** — `ModelRouter`, `RoutingInferenceService`, `RoutingMode` (AUTO / LIGHT_ONLY / HEAVY_ONLY); chat routing chip; heavy auto-unload after 10 min idle.
2. **Ara-hosted model downloads** — `installers/models.json` + `ModelCatalog` / `ModelDownloader`; light Qwen2.5-7B (`models-v1`, 3 parts) and heavy Qwen2.5-Coder-32B (`models-heavy-v1`, 10 parts); 2000 MiB chunks under GitHub 2 GiB cap.
3. **Apple Silicon heavy-model tuning** — `ModelLoadProfile` per tier; `SystemMemory`-aware ctx sizing; OOM/KV overflow fixes for 24 GB Macs (Ollama-style Metal offload + mmap).
4. **Developer mode** — `AppLog` in-memory buffer + `DeveloperLogWindow` (live filtered diagnostics); toggle in Settings → System.
5. **UI polish** — `ModelStatusControl`, condensed model settings, routing badge in chat header.

---

## Release & Update System

### What lives where

```
main branch (git)          GitHub Release v5.7 (assets, not in git)
├── version → 5.7          ├── ara-installer-macos-arm64.pkg
├── installers/            └── ara-portable-macos-arm64.dmg
│   ├── latest.json  ──────────► app fetches for update checks
│   ├── models.json  ──────────► app fetches for GGUF download manifests
│   ├── README.md
│   ├── generate-latest-json.gradle
│   ├── generate-latest-json.sh
│   ├── split-and-upload-model.sh
│   └── split-and-upload-heavy-model.sh
└── (stable app code)

GitHub model releases (not in git)
├── models-v1          → Qwen2.5-7B Q4_K_M (.part0–.part2)
└── models-heavy-v1    → Qwen2.5-Coder-32B Q4_K_M (.part0–.part9)

develop branch (git)
├── version → 5.8.0
├── tech.rawden.ara.update/       ← optional auto-update checks
├── tech.rawden.ara.ai routing/   ← ModelRouter, RoutingInferenceService
├── tech.rawden.ara.core.AppLog   ← developer diagnostics
└── Settings → Updates + developer mode + light/heavy model UI
```

### Update flow (user)

1. User enables **Settings → Updates → Check for updates automatically on startup** (default **off**).
2. App fetches `installers/latest.json` from `main` (HttpClient + Jackson).
3. Compares `latestVersion` vs `AppVersion.current()` (`VersionComparer`).
4. If newer → dialog with release notes → **Download & Install**.
5. Downloads platform installer to temp → launches via `open` / `start` / `xdg-open`.
6. macOS prefers `macos-pkg-arm64` → `macos-pkg` → `macos-dmg*` keys in JSON.

**Privacy:** No telemetry. Network only on explicit or opted-in startup check. Only metadata until user downloads. Repository is **public** — unauthenticated fetch of `latest.json` and release assets.

### Cutting a stable release (full checklist)

Work on **`main`** unless preparing a develop-only experiment.

```bash
# 1. Bump version on main
echo "5.7" > version
git add version && git commit -m "Bump version to 5.7"

# 2. Build release installers (per platform; macOS example)
RELEASE=true ./gradlew :dist:jpackage
# Outputs: dist/build/dist/artifacts/
#   ara-installer-macos-arm64.pkg      (preferred on macOS)
#   ara-portable-macos-arm64.dmg
#   ara-installer-windows-x86_64.msi  (Windows CI/machine)
#   ara-installer-linux-*.deb / .rpm    (Linux CI/machine)

# 3. Verify embedded version matches tag (macOS)
#    pkgutil / PackageInfo should show 5.7, not *-SNAPSHOT

# 4. Create GitHub Release
#    Tag: v5.7  Target: main  Attach all built artifacts from artifacts/

# 5. Generate latest.json (only include URLs for assets you actually uploaded)
./gradlew generateLatestJson -PreleaseNotes="Release notes here"
# Or: RELEASE_NOTES="..." ./installers/generate-latest-json.sh

# 6. Commit metadata to main
git add installers/latest.json
git commit -m "Release v5.7: update latest.json"
git push origin main

# 7. Merge develop → main when stable features (e.g. auto-update) should ship in the .pkg
```

### Artifact filename patterns

| Key in `latest.json` | File pattern |
|----------------------|--------------|
| `macos-pkg-arm64` | `ara-installer-macos-arm64.pkg` |
| `macos-dmg-arm64` | `ara-portable-macos-arm64.dmg` |
| `windows-msi-x86_64` | `ara-installer-windows-x86_64.msi` |
| `linux-deb-x86_64` | `ara-installer-linux-x86_64.deb` |
| `linux-rpm-arm64` | `ara-installer-linux-arm64.rpm` |

Download URLs: `https://github.com/OliverRawden/Ara/releases/download/vX.Y/<filename>`

---

## Project Structure

```
Ara/
├── GROK.md                       # This file — project context for AI assistants
├── version                       # Plain-text version (5.8.0 on develop)
├── build.gradle                  # Root: product identity, JVM args, JavaFX version
├── settings.gradle               # include 'app', 'dist'
├── gradlew / gradlew.bat
├── gradle/gradle_scripts/        # Shared: java, javafx, jna, lombok, modules
├── app/                          # Application module
│   ├── build.gradle
│   └── src/main/
│       ├── java/
│       │   ├── module-info.java
│       │   └── tech/rawden/ara/
│       │       ├── Main.java              # JavaFX Application entry
│       │       ├── ai/                    # Inference, routing, model download/load profiles
│       │       ├── comp/                   # RegionBuilder + base components
│       │       ├── core/                   # AraConfig, AppLog, theme, paths, security, macOS
│       │       ├── integration/            # Vex protocol loader for tools
│       │       ├── model/                  # Chat, settings, audit persistence
│       │       ├── platform/               # Threading, logo, Mac window
│       │       ├── tool/                   # Tool catalog, executors, ToolCall parse
│       │       ├── ui/                     # Main, sidebar, chat, settings views
│       │       ├── update/                 # Optional auto-update checks + installer download
│       │       └── util/                   # RetryExecutor, AraFailures, OS detection, threading
│       └── resources/tech/rawden/ara/resources/
│           ├── style/ara.css
│           ├── font-config/font.css
│           └── fonts/Inter-*.ttf
├── installers/
│   ├── README.md                 # GitHub Releases + update/model metadata workflow
│   ├── latest.json               # App update metadata (on main)
│   ├── models.json               # Light + heavy GGUF manifests (on main)
│   ├── generate-latest-json.gradle  # ./gradlew generateLatestJson
│   ├── generate-latest-json.sh      # Shell alternative for latest.json
│   ├── split-and-upload-model.sh    # Split + upload light GGUF → models-v1
│   └── split-and-upload-heavy-model.sh  # Split + upload heavy GGUF → models-heavy-v1
└── dist/                         # jpackage / native packaging
```

---

## Data Layout

All runtime data lives under `~/Documents/Ara/`:

| Path | Purpose |
|------|---------|
| `~/Documents/Ara/models/` | GGUF model files |
| `~/Documents/Ara/data/chats.json` | Chat history (sessions + messages) |
| `~/Documents/Ara/data/settings.json` | App settings |
| `~/Documents/Ara/context.md` | Persistent AI memory (Active Context) |
| `~/Documents/Ara/data/memory_graph.db` | SQLite entity/relation memory graph (shared with Vex) |
| `~/Documents/Ara/logs/audit.log` | Activity / tool audit log |

Configured in `tech.rawden.ara.core.AraPaths`.

**Vex integration path:** `~/Documents/Vex/Protocols/` — `VexProtocolCatalog` loads **all** protocol `.md` files; auto-refreshes on directory mtime change. Injected into every inference prompt as **Vex Protocol Catalog**.

---

## Agent Tools (Vex Sync)

| Vex ID | Tool name | Group | Executor |
|--------|-----------|-------|----------|
| 101 | `execute_command` | terminal | `TerminalExecutor` (bash, 30s timeout, confirmation UI) |
| 102 | `get_current_datetime` | core | Inline in `ChatViewComp` |
| 103 | `web_search` | web | `WebSearchService` (DuckDuckGo Lite) |
| 104 | `read_memory` | memory | `ChatViewComp.loadSecureMemory()` |
| 105 | `write_memory` | memory | `ChatViewComp.saveSecureMemory()` |
| 106 | `append_memory` | memory | Structured append to `context.md` |
| 107 | `query_memory_graph` | memory-graph | `MemoryGraphService` (SQLite entity/relation query) |
| 108 | `upsert_memory_entity` | memory-graph | Create/update entity in `~/Documents/Ara/data/memory_graph.db` |
| 109 | `link_memory_entities` | memory-graph | Typed relation between two entities |

- **Memory graph:** `MemoryGraphService` — SQLite at `~/Documents/Ara/data/memory_graph.db`; editable in Vex Memory Graph view
- **Teams:** Vex `type: team` protocols (e.g. **20** Research & Code Team); activate in chat with `/team 20`, deactivate with `/team-off`. `TeamOrchestrator` injects member prompts and shared handoff context; `ModelRouter` routes by `[role]` prefix tier hints
- **Catalog:** `VexProtocolCatalog` — all protocols (1, 2, 9, 10, 16, 20, 101–109, user-added); `formatCatalogSection()` in every prompt
- **Load:** auto-reload when `~/Documents/Vex/Protocols/` files change; manual **Reload Vex protocols** in Settings
- **Tools:** `ToolCatalog` ← ara-tool subset; `ToolCall.getFunctionDefinitions()` (filtered by privacy toggles)
- **Mapping:** protocol 102 → `get_current_datetime`, 104 → `read_memory`, etc. (ara-tool name in `<|tool_call|>`, not ID)
- **No Java fallback:** if Vex protocols are missing, tools list is empty (warning logged)
- **Execution:** `ChatViewComp.handleToolCall()` — agent loop, max 5 tool rounds
- **Unknown tools:** denied with message to edit in Vex Protocols

Privacy toggles gate which tools appear in the prompt (`terminalEnabled`, `webSearchEnabled`, `contextMemoryEnabled`). Toggles sync live to `InferenceConfig` via `SettingsViewComp.syncInferenceConfigFromSettings()`.

---

## Source Packages

### `tech.rawden.ara.ai` — Inference & Routing
| File | Purpose |
|------|---------|
| `InferenceService.java` | Interface: loadModel, generate, generateWithTools, generateTitle, shutdown |
| `LlamaCppInferenceService.java` | java-llama.cpp bindings; ChatML prompt; `<|tool_call|>` stream detection; synchronized `loadModel`; tier-aware `ModelLoadProfile` |
| `RoutingInferenceService.java` | Facade: routes each request through `ModelRouter` before delegating to `LlamaCppInferenceService` |
| `ModelRouter.java` | Light/heavy tier selection (keyword escalation, user override, idle heavy unload); JavaFX badge properties |
| `RoutingMode.java` | `AUTO`, `LIGHT_ONLY`, `HEAVY_ONLY` |
| `ModelTier.java` | `LIGHT` / `HEAVY` badge labels and tooltips |
| `ModelLoadProfile.java` | Per-tier llama.cpp params; heavy profile resolved from `SystemMemory` + GGUF size at load time |
| `SystemMemory.java` | Unified RAM stats via `OperatingSystemMXBean` (Apple Silicon Metal shares RAM) |
| `DummyInferenceService.java` | Stub without real inference |
| `ModelManager.java` | `~/Documents/Ara/models/` — list, resolve, Ara-repo download, delete |
| `ModelCatalog.java` | Fetches + caches `installers/models.json` from GitHub |
| `ModelRelease.java` | Record for a hosted model manifest entry (filename, parts, sha256) |
| `ModelDownloader.java` | Multi-part parallel GGUF download, verify, assemble |
| `ModelPreloader.java` | Background light-model preload on `ara-model-preloader` virtual thread; `whenReady()` for chat send |

### `tech.rawden.ara.tool` — Agent Tools
| File | Purpose |
|------|---------|
| `ToolCatalog.java` | Loads tool defs from Vex protocols; builds OpenAI-style JSON for prompt |
| `ToolDefinition.java` | Record: name, description, parametersJson, toolGroup, builtin |
| `ToolCall.java` | Parse `<|tool_call|>` JSON; argument extractors |
| `TerminalExecutor.java` | Shell via `/bin/bash -c` |
| `WebSearchService.java` | DuckDuckGo Lite HTML scrape |

### `tech.rawden.ara.integration`
| File | Purpose |
|------|---------|
| `VexProtocol.java` | Record for any Vex protocol (id, action, modifier, ara-tool, description) |
| `VexProtocolLoader.java` | Parses all `~/Documents/Vex/Protocols/*.md` files |
| `VexProtocolCatalog.java` | Cached catalog, auto-refresh, `formatCatalogSection()` for system prompt |

### `tech.rawden.ara.core`
| File | Purpose |
|------|---------|
| `AraConfig.java` | Central config: metadata URLs, timeouts, retry policy, routing defaults; overridable via `-Dara.*` system properties |
| `AraModel.java` | Singleton navigation: `View` enum (CHAT, SETTINGS) |
| `AraTheme.java` | Cupertino dark/light + system accent CSS |
| `AraPaths.java` | Data paths + `vexProtocolsDir()` |
| `AppLog.java` | In-memory log buffer (50k entries) with process tags; root handler; `AppLog.of("routing")` etc. |
| `SecurityService.java` | AES-256-GCM encryption for chats, memory, audit log |
| `MacMenuBar.java` / `ShortcutManager.java` | macOS menu + keyboard shortcuts |

### `tech.rawden.ara.util` — Resilience & errors
| File | Purpose |
|------|---------|
| `RetryExecutor.java` | Exponential-backoff retries for transient HTTP/I/O (model catalog, GGUF parts, update metadata) |
| `AraFailures.java` | Centralized exception translation (model load, download, chat/settings persistence) |
| `OsType.java` / `ThreadHelper.java` | Platform detection and threading helpers |

### `tech.rawden.ara.model`
| File | Purpose |
|------|---------|
| `ChatMessage.java` | Roles: USER, ASSISTANT, SYSTEM, TOOL |
| `ChatSession.java` / `ChatHistory.java` | Session CRUD |
| `ChatStorage.java` | Jackson persistence to `chats.json`; lazy-load recent sessions (`AraConfig.chatLoadSessionLimit()`); `loadAsync` |
| `AppSettings.java` / `SettingsStorage.java` | Settings JSON |
| `SettingsReloader.java` | Hot-reload `settings.json` in developer mode (mtime poll on virtual thread) |
| `InferenceConfig.java` | Runtime inference + tool flags + `DEFAULT_SYSTEM_PROMPT` |
| `AuditLog.java` / `AuditLogStorage.java` | Tool/privacy audit events |

### `tech.rawden.ara.update` — Optional Auto-Update
| File | Purpose |
|------|---------|
| `AppVersion.java` | Resolves running version from JVM property or bundled `version` resource |
| `VersionComparer.java` | Dotted numeric version comparison (strips `-SNAPSHOT`) |
| `UpdateInfo.java` | Record: latest version, notes, platform download URL |
| `UpdateService.java` | Fetches `installers/latest.json`; downloads + launches native installer |
| `PlatformInstallerKey.java` | Maps OS + arch → `macos-pkg*` / `macos-dmg*` / `windows-msi*` / `linux-deb*` / `linux-rpm*` keys |
| `SystemArch.java` | Normalizes `os.arch` to `arm64` / `x86_64` for installer key lookup |
| `UpdateDialog.java` | Release-notes dialog with **Download & Install** |

Update checks are **off by default**. When enabled, the app fetches:
`https://raw.githubusercontent.com/OliverRawden/Ara/main/installers/latest.json`
Only version metadata is requested until the user taps **Download & Install**. Startup checks run on a virtual thread after session ready (never block launch).

**Release workflow (main branch):** bump `version` → `./gradlew :dist:jpackage` → GitHub Release `vX.Y` with artifacts from `dist/build/dist/artifacts/` → `./gradlew generateLatestJson` (or `installers/generate-latest-json.sh`) → commit `installers/latest.json` to `main`. See `installers/README.md`.

### `tech.rawden.ara.ui`
| File | Purpose |
|------|---------|
| `MainViewComp.java` | Sidebar + content; lazy settings build; chat session switching |
| `SidebarComp.java` | Logo, new chat, session list (max 60 at build), settings nav |
| `ChatViewComp.java` | Streaming chat, tool agent loop, routing chip, secure memory, audit, terminal bubbles |
| `SettingsViewComp.java` | Appearance, model (light/heavy + routing), inference, personality, memory, privacy, **updates**, system info, **developer mode** |
| `DeveloperLogWindow.java` | Live filtered diagnostic log window (process, level, text filters; copy/clear) |
| `ModelStatusControl.java` | Model load status indicator in chat header |

### `tech.rawden.ara.comp` — UI Builders
Reactive builders on `org.int4.fx-builders`. Base components: `ToggleSwitchComp`, `ButtonComp`, `LabelComp`, `FontIconComp`, layout wrappers. `RegionBuilder<T>` is the root abstraction.

---

## Startup & Data Flow

Staged startup keeps the window responsive; heavy work runs on dedicated virtual threads **after** the UI shell is visible.

```
Main.start()
  ├─ AppLog.install(); verbose if developerMode
  ├─ FX: splash → MainViewComp (empty ChatHistory) → stage.show()     ~2–3s
  ├─ Developer mode? → DeveloperLogWindow.show()
  ├─ Encryption enabled?
  │    └─ Modal unlock → PBKDF2 on virtual thread (ara-crypto)
  └─ onSessionReady()  [after unlock, or immediately if no encryption]
       ├─ ara-data-loader: ChatStorage.load() → decrypt → update sidebar
       ├─ ara-model-preloader: ModelPreloader.schedulePreload() → load light model
       └─ (optional) startup update check if Settings toggle enabled → UpdateService.checkForUpdate()
```

| Thread | Trigger | Work |
|--------|---------|------|
| JavaFX | `Main.start()` | Splash, UI shell, unlock dialog |
| `ara-crypto` | Unlock button | PBKDF2 key derivation |
| `ara-data-loader` | `onSessionReady` | Read + decrypt `chats.json`, refresh sidebar |
| `ara-model-preloader` | `onSessionReady` | Resolve + mmap GGUF via `LlamaCppInferenceService` |
| Inference executor | Chat send | Token streaming, tool-call detection |

**Chat inference flow**

1. User sends message → `ChatViewComp.sendMessage()` shows user bubble immediately
2. `RoutingInferenceService` → `ModelRouter.resolveTier()` (AUTO keyword escalation, or LIGHT_ONLY / HEAVY_ONLY override)
3. If heavy tier → load heavy GGUF on demand (unload after 10 min idle); light stays hot from preload
4. If model `READY` → `generateWithTools()`; else `ModelPreloader.whenReady()` waits for in-flight preload
5. Model may emit `<|tool_call|>{...}` → `handleToolCall()` → `TOOL` message → up to 5 rounds (each round re-routed)
6. Sessions persist to `~/Documents/Ara/data/chats.json`
7. Memory: `read_memory` / `write_memory` / `append_memory` on `~/Documents/Ara/context.md` (encrypted optional)

**Model resolution:** Light: `AppSettings.lightModel` (falls back to `selectedModel`) via `ModelManager`. Heavy: `AppSettings.heavyModel` (defaults to manifest filename from `models.json`). Download via Settings → Model → **Download Light** / **Download Heavy**.

### Light/heavy routing (`ModelRouter`)

| Mode | Behaviour |
|------|-----------|
| `AUTO` | Light model handles simple chat; keyword patterns (code, debug, refactor, tool use, etc.) escalate to heavy for that turn |
| `LIGHT_ONLY` | Never loads heavy; fast replies only |
| `HEAVY_ONLY` | Keeps heavy loaded for every turn |

- Heavy auto-unloads **10 minutes** after last heavy inference (`ara-heavy-unload` scheduler).
- Chat header shows routing chip (tier badge + escalation indicator) via `ModelRouter` JavaFX properties.
- Each tool-call round in the agent loop re-evaluates routing.
- `ModelLoadProfile.HEAVY` resolves ctx/gpu/batch from `SystemMemory.totalBytes()` and GGUF file size — tuned for 24 GB Apple Silicon (full Metal offload, mmap, reduced ctx when headroom is tight).

---

## Default System Prompt & Memory

`InferenceConfig.DEFAULT_SYSTEM_PROMPT` instructs Ara to:
- Use Vex protocols 101–106 for tools (not shell cat/echo for memory)
- Prefer `read_memory` at session start, `write_memory` / `append_memory` for persistence

`SettingsViewComp.defaultMemoryContent()` and empty `context.md` template reinforce the same. `LlamaCppInferenceService.buildPrompt()` adds tool JSON from `ToolCatalog` when tools are enabled.

---

## Performance & Startup

Heavy work is deferred after an early splash `stage.show()` so the dock icon appears in ~2–3s instead of 30s+. Model preload runs **in parallel** with chat decrypt/load so the first message usually hits a `READY` model (~9/10 when the user takes a few seconds to type).

| Optimization | Status |
|--------------|--------|
| Early splash + deferred MainView build | Done |
| Sidebar limited to 60 recent chats at build | Done |
| Static Jackson `ObjectMapper` in storage classes | Done |
| llama.cpp mmap, flash-attn, KV quant, capped n_threads, skipWarmup | Done |
| Lazy `SettingsViewComp` (first open only) | Done |
| Background model preload via `ModelPreloader` (post-unlock) | Done |
| Post-load 1-token warmup (`InferenceService.warmup()`) | Done |
| Chat send waits on in-flight preload (`whenReady`) — no duplicate load | Done |
| Title generation deferred until after first reply (no executor queue blocking) | Done |
| `setCachePrompt(true)` + cached system block (Vex/tools) | Done |
| Inference timing logs (`prefillMs`, `totalMs`, `promptChars`) | Done |
| `ToolCallDisplay` hides `<|tool_call|>` in assistant bubbles | Done |
| Synchronized `loadModel` — preload + Settings manual load are safe | Done |
| PBKDF2 iterations reduced (65k) | Done |
| Light/heavy routing — light hot, heavy on-demand + idle unload | Done (develop) |
| `ModelLoadProfile` per tier — heavy ctx sized from `SystemMemory` | Done (develop) |
| Ara-hosted multi-part GGUF download (`ModelDownloader`) | Done |
| Developer mode live log window (`AppLog` + `DeveloperLogWindow`) | Done (develop) |

---

## Key Dependencies

- **JavaFX 27-ea** — UI
- **atlantafx-base 2.1.0** — Cupertino theme
- **de.kherud:llama** — java-llama.cpp GGUF inference
- **jackson-databind 2.20.0** — JSON persistence
- **org.int4.fx:fx-builders 0.5** — Reactive builders
- **org.kordamp.ikonli** — Material Design icons
- **SLF4J 2.0.17** — Logging
- **JNA** — Native access

---

## Default Models (Ara-hosted)

Metadata: `installers/models.json` on `main` (fetched at runtime by `ModelCatalog`).

| Tier | Model | Size | Release tag | Parts |
|------|-------|------|-------------|-------|
| **Light** (hot) | `Qwen2.5-7B-Instruct-Q4_K_M.gguf` | ~4.5 GB | `models-v1` | 3 × 2000 MiB + remainder |
| **Heavy** (on-demand) | `Qwen2.5-Coder-32B-Instruct-Q4_K_M.gguf` | ~18.5 GB | `models-heavy-v1` | 10 × 2000 MiB + remainder |

Light model preloads in background after session ready. Heavy loads on routing escalation or `HEAVY_ONLY` mode. `AppSettings.lightModel` / `heavyModel` override filenames when set. Upload scripts: `installers/split-and-upload-model.sh`, `installers/split-and-upload-heavy-model.sh`.

---

## JVM Args

- Heap: `-Xms300m -Xmx4G`, G1GC
- Virtual threads (parallelism=8)
- Model load: mmap, controlled threading

---

## Build & Run

```bash
./gradlew :app:run
```

IntelliJ: open `Ara/`, trust Gradle, run **run app** configuration.

---

## Grok / AI Assistant Workflow

Use this loop when an AI agent ("grok build") implements changes from the TODO list or a user chat session.

### 1. Clone and sync (always `develop`)

```bash
git clone https://github.com/OliverRawden/Ara.git
cd Ara
git fetch origin
git checkout develop
git pull origin develop
```

Local dev copy: `/Users/rawden/Developer/IdeaProjects/Ara` (sibling to Vex).

### 2. Read context

1. **`GROK.md`** (this file) — architecture, branches, release/update workflow, TODOs.
2. **`README.md`** — user-facing summary.
3. **`installers/README.md`** — if touching releases or `latest.json`.
4. **`../Vex/GROK.md`** — if touching Vex protocols or tools.

### 3. Implement

Work through TODO items or the user's request. Prefer code over doc-only changes.

| Area | Key paths |
|------|-----------|
| Inference / routing / chat | `app/.../ai/`, `app/.../ui/ChatViewComp.java` |
| Model download / manifests | `app/.../ai/ModelCatalog.java`, `installers/models.json`, `installers/split-and-upload-*.sh` |
| Auto-update | `app/.../update/`, `SettingsViewComp.java`, `installers/latest.json` |
| Developer diagnostics | `app/.../core/AppLog.java`, `app/.../ui/DeveloperLogWindow.java` |
| Vex tools | `app/.../integration/`, `app/.../tool/` |
| Packaging | `dist/jpackage.gradle`, `dist/pkg/`, `dist/msi/` |
| CI | `.github/workflows/` |

```bash
./gradlew :app:compileJava
```

### 4. Update GROK.md

- Check off completed TODOs (`[x]`).
- Update **Current Repository State** if versions, releases, or branch tips changed.
- Extend workflow sections if the process changes.

### 5. Commit and push to `develop`

```bash
git add -A
git commit -m "Short imperative summary of what changed"
git push origin develop
```

If `GROK.md` workflow or branch strategy changed, sync the same `GROK.md` to `main` and `master` (content should be identical across branches).

### 6. Finish with a report

- What was accomplished.
- Errors or follow-ups.
- TODO status.
- Whether a `main` release or `latest.json` update is needed.

### 7. Cutting a stable release (on `main`, human or agent)

See **Release & Update System → Cutting a stable release** above. Summary:

1. Bump `version` on `main`.
2. `RELEASE=true ./gradlew :dist:jpackage` on each target platform.
3. Verify binary version matches tag (not `-SNAPSHOT`).
4. GitHub Release `vX.Y` + attach artifacts from `dist/build/dist/artifacts/`.
5. `./gradlew generateLatestJson -PreleaseNotes="…"`.
6. Commit + push `installers/latest.json` to `main`.
7. Merge `develop` → `main` when new features (e.g. auto-update) should ship in the installer.

### 8. Release-only checklist (signing / CI secrets)

Signing and notarization require CI secrets — not for local agent runs:

| Variable | Purpose |
|----------|---------|
| `RELEASE=true` | Non-SNAPSHOT build |
| `GPG_KEY_ID`, `GPG_KEY`, `GPG_PASSWORD` | DEB/RPM signing (`build.gradle`) |
| `MACOS_DEVELOPER_ID_*`, `MACOS_NOTARIZATION_*` | macOS sign + notarize (`dist/mac_app/`, `dist/pkg/`) |
| `AZURE_KEY_VAULT_URI` + Azure client vars | Windows Authenticode (`dist/tools/sign.bat`) |

---

## TODO

### Rebranding / Distribution

#### Installer & package metadata
- [x] MSI installer links (`dist/msi/Product.wxs`) — updated to Ara GitHub
- [x] Linux DEB/RPM metadata (`dist/linux_packages.gradle`) — Oliver Rawden vendor/maintainer
- [x] jpackage vendor set to Oliver Rawden (`dist/jpackage.gradle`)

#### Auto-update & release metadata
- [x] `tech.rawden.ara.update` package (optional checks, Settings UI, startup hook)
- [x] `installers/latest.json` + generators on `main`
- [x] GitHub Release v5.7 with macOS arm64 `.pkg` + `.dmg`
- [x] Merge auto-update from `develop` → `main` (shipped in v5.7)
- [x] Repository is public — update checks work without a GitHub token
- [ ] Merge v5.8 features (`develop` → `main`): routing, developer mode, heavy-model fixes
- [ ] Cut v5.8 release from `main` with `RELEASE=true`; bump `latest.json`
- [ ] Trim `latest.json` to only uploaded assets (or add Windows/Linux builds)

#### Model hosting
- [x] `installers/models.json` — light model manifest (`models-v1`, 3 parts)
- [x] Heavy model manifest (`models-heavy-v1`, 10 parts) on `main`
- [x] `ModelCatalog` + `ModelDownloader` — parallel multi-part download from Ara releases
- [x] `split-and-upload-model.sh` + `split-and-upload-heavy-model.sh` (disk-safe, resumable)
- [ ] Expose quant choice in settings + auto-download optimized GGUF variants

#### Documentation
- [x] README.md — cleaned up, correct package path
- [x] CONTRIBUTING.md — proprietary contribution policy
- [x] LICENSE / LICENSE.md — proprietary license (KickstartFX attribution retained)
- [x] `installers/README.md` — release workflow

#### Signing / distribution
- [x] GPG signing keys wired (`build.gradle` — needs CI secrets at release)
- [x] macOS notarization scripts wired (`dist/mac_app/`, `dist/pkg/` — needs Apple certs in CI)
- [x] Windows code signing wired (`dist/tools/sign.bat`, `dist/msi/msi.gradle` — needs Azure KV in CI)

#### Third-party license files
- `dist/licenses/kickstartfx.properties` — keep for template attribution

---

### Icon Status (completed)

- [x] macOS `.icns`, Windows `.ico`, MSI banners, Linux hicolor icons
- [x] Runtime sidebar logo; removed conflicting `Assets.car` override
- [x] `dist/logo/logo_composer.icon/` matches design source

---

### Build Environment

**Windows MSI**
- [ ] Visual Studio 2022 with C++ desktop workload
- [ ] .NET SDK 8.0+
- [ ] WiX Toolset v6: `dotnet tool install --global wix`

**macOS DMG**
- [ ] `create-dmg`: `brew install create-dmg`

---

### Environment Variables (full release)

| Variable | Purpose |
|----------|---------|
| `RELEASE` | `true` for non-SNAPSHOT builds |
| `CI` | Enables AOT training optimizations |
| `GPG_KEY_ID`, `GPG_KEY`, `GPG_PASSWORD` | DEB/RPM signing |
| `MACOS_DEVELOPER_ID_APPLICATION_CERTIFICATE_NAME` | macOS app signing |
| `MACOS_DEVELOPER_ID_INSTALLER_CERTIFICATE_NAME` | macOS PKG signing |
| `AZURE_KEY_VAULT_URI` | Windows Authenticode signing |

---

### Settings polish (completed)

- [x] Removed hardcoded admin gate for system prompt; always-editable Personality section
- [x] Section order: Appearance → Model → Inference → Personality → Memory → Privacy → Updates → System → Reset
- [x] Optional auto-update checks (Settings toggle, `tech.rawden.ara.update` package, `installers/latest.json`)
- [x] Inference sliders with value labels + help text
- [x] Unified `DEFAULT_SYSTEM_PROMPT` across model classes
- [x] Vex tool sync: live InferenceConfig sync, reload button, correct data paths
- [x] Condensed model settings — light/heavy filenames, routing mode combo, download buttons
- [x] Developer mode toggle (Settings → System) — opens `DeveloperLogWindow`

---

### Code quality & maintainability

- [x] `AraConfig` — externalized URLs, timeouts, retry policy (`-Dara.*` overrides for dev)
- [x] `RetryExecutor` — exponential backoff for model/update HTTP (no extra resilience library)
- [x] `AraFailures` — centralized exception messages for model, download, persistence
- [x] `ChatStorage` lazy session load (cap 60 at startup) + `loadAsync` on virtual thread
- [x] `SettingsReloader` — hot-reload settings in developer mode
- [x] Expanded Javadoc on inference/routing/threading classes
- [ ] Profile GGUF mmap/GPU init with VisualVM/JFR
- [ ] Async model switching with cancellation (beyond cooperative `cancelGeneration`)
- [ ] On-demand load of older chat sessions not in lazy-load window
- [ ] `dist/msi/msi.gradle` — replace placeholder UpgradeCode UUID (tracked TODO)

---

### Performance & inference

- [x] Background model preload after unlock (`ModelPreloader`) — first-message latency
- [x] KV cache quantization (Q8_0 K / Q4_0 V) in `LlamaCppInferenceService`
- [x] Prompt/context truncation for long chats (`PromptContextLimiter`, `InferenceConfig.maxContextChars`)
- [x] Light/heavy multi-model routing (`ModelRouter`, `RoutingInferenceService`)
- [x] Per-tier `ModelLoadProfile` — heavy ctx auto-sized for 24 GB Macs; Ollama-style Metal offload
- [x] Heavy model idle unload (10 min) to reclaim RAM
- [ ] Speculative decoding / draft model via llama.cpp when available
- [ ] Profile & lazy-load non-critical resources at launch
- [ ] Adaptive: pause model load on low battery / thermal
- [ ] Research faster inference bindings or prebuilt jlink images for IDE launch

### Developer diagnostics (completed on develop)

- [x] `AppLog` — in-memory buffer with process tags (`routing`, `model`, `startup`, etc.)
- [x] `DeveloperLogWindow` — live filtered log viewer; toggle in Settings → System
- [x] Verbose capture when developer mode enabled

---

### Vex / Ara integration (open)

- [x] Auto-reload protocol catalog when Vex protocol files change (mtime snapshot)
- [x] Full Vex Protocol Catalog injected into every Ara system prompt
- [x] Execute custom user-added ara-tool protocols (`CustomToolExecutor` — terminal/web/core/target groups)
- [x] Protocol 10 kill analogue — Stop button + `InferenceService.cancelGeneration()` during streaming

**Protocol 10 kill semantics (Ara mapping)**

Vex protocol 10 is a `kill` modifier: standalone `10` stops active subprocesses; piped `left | 10` aborts the left pipeline before it runs (see `../Vex/GROK.md`).

In Ara, the chat **Stop** button (send icon → stop while generating) calls `cancelGeneration()` on the active inference job. Partial assistant text is kept with a `[Stopped]` suffix; audit logs `GENERATION_STOPPED`. This mirrors abort semantics for the on-device agent loop (distinct from terminal `execute_command` confirmation).