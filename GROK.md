# Ara — On-Device AI Assistant (JavaFX)

## Overview

Ara is a desktop JavaFX chat application that runs **fully on-device** using `java-llama.cpp` (GGUF models). It was bootstrapped from [KickstartFX](https://kickstartfx.xpipe.io/) and extensively customized. The UI uses AtlantaFX Cupertino theming with a sidebar-first layout.

Agent **tool calling** is prompt-injected (ChatML + `<|tool_call|>` tokens). **All Vex protocols** are auto-loaded from `~/Documents/Vex/Protocols/` into every system prompt via `VexProtocolCatalog` (IDs, names, descriptions, ara-tool mappings). Agent tools (101–106) are a subset; new protocols added in Vex appear automatically when files change. Tools are editable in Vex but built-ins cannot be deleted.

**Sibling app:** [Vex](../Vex) — protocol orchestration console; manages Ara tool schemas and Vex-native protocols.

**Version:** `5.5.0` on `develop`; stable `5.6` on `main` (see `version` file)  
**Package:** `tech.rawden.ara`  
**Product name:** `Ara`  
**JDK:** Java 21+ (Java 25 recommended)  
**JavaFX:** `27-ea+16`  
**Build:** Gradle 9.2.1, multi-module (`app` + `dist`)

---

## Branch Strategy

| Branch | `version` file | Role |
|--------|----------------|------|
| **`main`** | `5.6` | Stable. Default branch. GitHub Releases and tags are cut from here. Holds `installers/latest.json` (update metadata). |
| **`develop`** | `5.5.0` | Unstable. All active development and Grok build sessions land here first. Includes auto-update client code (`tech.rawden.ara.update`). |
| **`master`** | (legacy) | Tracks older default; prefer `main` / `develop`. Keep `GROK.md` in sync when touched. |

**Rule:** Never commit installer binaries (`.pkg`, `.dmg`, `.msi`) to git. They live only as GitHub Release assets.

---

## Current Repository State (July 2026)

| Item | Status |
|------|--------|
| `develop` pushed | `98face2` — auto-update system, Settings → Updates, `installers/` generators |
| `main` pushed | `ff486ff` — `installers/latest.json`, `generateLatestJson` Gradle task |
| GitHub Release **v5.6** | https://github.com/OliverRawden/Ara/releases/tag/v5.6 |
| Release assets (macOS arm64) | `ara-installer-macos-arm64.pkg`, `ara-portable-macos-arm64.dmg` |
| Update metadata URL | `https://raw.githubusercontent.com/OliverRawden/Ara/main/installers/latest.json` |
| Repo visibility | **Public** — `latest.json` and release assets fetch without authentication |
| Auto-update code on `main` | **Not merged yet** — only on `develop` |
| v5.6 release binary version | **Mismatch** — uploaded `.pkg` reports `5.5.0-SNAPSHOT` (built before release alignment). Rebuild from `main` with `RELEASE=true` before next public release. |

**Local paths**

- Project: `/Users/rawden/Developer/IdeaProjects/Ara`
- Built artifacts: `dist/build/dist/artifacts/`
- Runtime data: `~/Documents/Ara/`

---

## Release & Update System

### What lives where

```
main branch (git)          GitHub Release v5.6 (assets, not in git)
├── version → 5.6          ├── ara-installer-macos-arm64.pkg
├── installers/            └── ara-portable-macos-arm64.dmg
│   ├── latest.json  ──────────► app fetches this URL at runtime
│   ├── README.md
│   ├── generate-latest-json.gradle
│   └── generate-latest-json.sh
└── (stable app code; update UI merges from develop later)

develop branch (git)
├── version → 5.5.0
├── tech.rawden.ara.update/   ← client-side update checks
└── Settings → Updates section
```

### Update flow (user)

1. User enables **Settings → Updates → Check for updates automatically on startup** (default **off**).
2. App fetches `installers/latest.json` from `main` (HttpClient + Jackson).
3. Compares `latestVersion` vs `AppVersion.current()` (`VersionComparer`).
4. If newer → dialog with release notes → **Download & Install**.
5. Downloads platform installer to temp → launches via `open` / `start` / `xdg-open`.
6. macOS prefers `macos-pkg-arm64` → `macos-pkg` → `macos-dmg*` keys in JSON.

**Privacy:** No telemetry. Network only on explicit or opted-in startup check. Only metadata until user downloads.

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
├── version                       # Plain-text version (5.5.0)
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
│       │       ├── ai/                    # Inference engine + model manager
│       │       ├── comp/                   # RegionBuilder + base components
│       │       ├── core/                   # Model, theme, paths, security, macOS
│       │       ├── integration/            # Vex protocol loader for tools
│       │       ├── model/                  # Chat, settings, audit persistence
│       │       ├── platform/               # Threading, logo, Mac window
│       │       ├── tool/                   # Tool catalog, executors, ToolCall parse
│       │       ├── ui/                     # Main, sidebar, chat, settings views
│       │       ├── update/                 # Optional auto-update checks + installer download
│       │       └── util/                   # OS detection, threading
│       └── resources/tech/rawden/ara/resources/
│           ├── style/ara.css
│           ├── font-config/font.css
│           └── fonts/Inter-*.ttf
├── installers/
│   ├── README.md                 # GitHub Releases + update metadata workflow
│   ├── latest.json               # Version metadata fetched by the app (on main)
│   ├── generate-latest-json.gradle  # ./gradlew generateLatestJson
│   └── generate-latest-json.sh      # Shell alternative for metadata generation
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

- **Catalog:** `VexProtocolCatalog` — all protocols (1, 2, 9, 10, 16, 101–106, user-added); `formatCatalogSection()` in every prompt
- **Load:** auto-reload when `~/Documents/Vex/Protocols/` files change; manual **Reload Vex protocols** in Settings
- **Tools:** `ToolCatalog` ← ara-tool subset; `ToolCall.getFunctionDefinitions()` (filtered by privacy toggles)
- **Mapping:** protocol 102 → `get_current_datetime`, 104 → `read_memory`, etc. (ara-tool name in `<|tool_call|>`, not ID)
- **No Java fallback:** if Vex protocols are missing, tools list is empty (warning logged)
- **Execution:** `ChatViewComp.handleToolCall()` — agent loop, max 5 tool rounds
- **Unknown tools:** denied with message to edit in Vex Protocols

Privacy toggles gate which tools appear in the prompt (`terminalEnabled`, `webSearchEnabled`, `contextMemoryEnabled`). Toggles sync live to `InferenceConfig` via `SettingsViewComp.syncInferenceConfigFromSettings()`.

---

## Source Packages

### `tech.rawden.ara.ai` — Inference
| File | Purpose |
|------|---------|
| `InferenceService.java` | Interface: loadModel, generate, generateWithTools, generateTitle, shutdown |
| `LlamaCppInferenceService.java` | java-llama.cpp bindings; ChatML prompt; `<|tool_call|>` stream detection; synchronized `loadModel` |
| `DummyInferenceService.java` | Stub without real inference |
| `ModelManager.java` | `~/Documents/Ara/models/` — list, resolve, HuggingFace download, delete |
| `ModelPreloader.java` | Background GGUF preload on `ara-model-preloader` virtual thread; `whenReady()` for chat send |

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
| `AraModel.java` | Singleton navigation: `View` enum (CHAT, SETTINGS) |
| `AraTheme.java` | Cupertino dark/light + system accent CSS |
| `AraPaths.java` | Data paths + `vexProtocolsDir()` |
| `SecurityService.java` | AES-256-GCM encryption for chats, memory, audit log |
| `MacMenuBar.java` / `ShortcutManager.java` | macOS menu + keyboard shortcuts |

### `tech.rawden.ara.model`
| File | Purpose |
|------|---------|
| `ChatMessage.java` | Roles: USER, ASSISTANT, SYSTEM, TOOL |
| `ChatSession.java` / `ChatHistory.java` | Session CRUD |
| `ChatStorage.java` | Jackson persistence to `chats.json` |
| `AppSettings.java` / `SettingsStorage.java` | Settings JSON |
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
| `ChatViewComp.java` | Streaming chat, tool agent loop, secure memory, audit, terminal bubbles |
| `SettingsViewComp.java` | Appearance, model, inference, personality, memory, privacy, **updates**, system info |

### `tech.rawden.ara.comp` — UI Builders
Reactive builders on `org.int4.fx-builders`. Base components: `ToggleSwitchComp`, `ButtonComp`, `LabelComp`, `FontIconComp`, layout wrappers. `RegionBuilder<T>` is the root abstraction.

---

## Startup & Data Flow

Staged startup keeps the window responsive; heavy work runs on dedicated virtual threads **after** the UI shell is visible.

```
Main.start()
  ├─ FX: splash → MainViewComp (empty ChatHistory) → stage.show()     ~2–3s
  ├─ Encryption enabled?
  │    └─ Modal unlock → PBKDF2 on virtual thread (ara-crypto)
  └─ onSessionReady()  [after unlock, or immediately if no encryption]
       ├─ ara-data-loader: ChatStorage.load() → decrypt → update sidebar
       └─ ara-model-preloader: ModelPreloader.schedulePreload() → loadModel()
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
2. If model `READY` → `generateWithTools()`; else `ModelPreloader.whenReady()` waits for in-flight preload
3. Model may emit `<|tool_call|>{...}` → `handleToolCall()` → `TOOL` message → up to 5 rounds
4. Sessions persist to `~/Documents/Ara/data/chats.json`
5. Memory: `read_memory` / `write_memory` / `append_memory` on `~/Documents/Ara/context.md` (encrypted optional)

**Model resolution:** `ModelManager.resolveModel(selectedModel)` — settings filename if present, else largest `.gguf`.

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

## Default Model

`Qwen2.5-7B-Instruct-Q4_K_M.gguf` (~4.5GB) — preloaded in background after session ready (bartowski HuggingFace). `AppSettings.selectedModel` overrides when set.

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
| Inference / chat | `app/.../ai/`, `app/.../ui/ChatViewComp.java` |
| Auto-update | `app/.../update/`, `SettingsViewComp.java`, `installers/` |
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
- [x] `tech.rawden.ara.update` package on `develop` (optional checks, Settings UI, startup hook)
- [x] `installers/latest.json` + generators on `main`
- [x] GitHub Release v5.6 with macOS arm64 `.pkg` + `.dmg`
- [ ] Rebuild v5.6+ installers from `main` with `RELEASE=true` (fix 5.5.0-SNAPSHOT mismatch)
- [ ] Merge auto-update from `develop` → `main`
- [ ] Trim `latest.json` to only uploaded assets (or add Windows/Linux builds)
- [x] Repository is public — update checks work without a GitHub token

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

---

### Performance & inference (open)

- [x] Background model preload after unlock (`ModelPreloader`) — first-message latency
- [x] KV cache quantization (Q8_0 K / Q4_0 V) in `LlamaCppInferenceService`
- [x] Prompt/context truncation for long chats (`PromptContextLimiter`, `InferenceConfig.maxContextChars`)
- [ ] Speculative decoding / draft model via llama.cpp when available
- [ ] Profile & lazy-load non-critical resources at launch
- [ ] Adaptive: pause model load on low battery / thermal
- [ ] Research faster inference bindings or prebuilt jlink images for IDE launch
- [ ] Expose quant choice in settings + auto-download optimized GGUF variants

---

### Vex / Ara integration (open)

- [x] Auto-reload protocol catalog when Vex protocol files change (mtime snapshot)
- [x] Full Vex Protocol Catalog injected into every Ara system prompt
- [x] Execute custom user-added ara-tool protocols (`CustomToolExecutor` — terminal/web/core/target groups)
- [x] Protocol 10 kill analogue — Stop button + `InferenceService.cancelGeneration()` during streaming

**Protocol 10 kill semantics (Ara mapping)**

Vex protocol 10 is a `kill` modifier: standalone `10` stops active subprocesses; piped `left | 10` aborts the left pipeline before it runs (see `../Vex/GROK.md`).

In Ara, the chat **Stop** button (send icon → stop while generating) calls `cancelGeneration()` on the active inference job. Partial assistant text is kept with a `[Stopped]` suffix; audit logs `GENERATION_STOPPED`. This mirrors abort semantics for the on-device agent loop (distinct from terminal `execute_command` confirmation).