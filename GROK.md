# Ara — On-Device AI Assistant (JavaFX)

## Overview

Ara is a desktop JavaFX chat application that runs **fully on-device** using `java-llama.cpp` (GGUF models). It was bootstrapped from [KickstartFX](https://kickstartfx.xpipe.io/) and extensively customized. The UI uses AtlantaFX Cupertino theming with a sidebar-first layout.

Agent **tool calling** is prompt-injected (ChatML + `<|tool_call|>` tokens). **All Vex protocols** are auto-loaded from `~/Documents/Vex/Protocols/` into every system prompt via `VexProtocolCatalog` (IDs, names, descriptions, ara-tool mappings). Agent tools (101–106) are a subset; new protocols added in Vex appear automatically when files change. Tools are editable in Vex but built-ins cannot be deleted.

**Sibling app:** [Vex](../Vex) — protocol orchestration console; manages Ara tool schemas and Vex-native protocols.

**Version:** `5.5.0` (see `version` file)  
**Package:** `tech.rawden.ara`  
**Product name:** `Ara`  
**JDK:** Java 21+ (Java 25 recommended)  
**JavaFX:** `27-ea+16`  
**Build:** Gradle 9.2.1, multi-module (`app` + `dist`)

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
│       │       └── util/                   # OS detection, threading
│       └── resources/tech/rawden/ara/resources/
│           ├── style/ara.css
│           ├── font-config/font.css
│           └── fonts/Inter-*.ttf
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

### `tech.rawden.ara.ui`
| File | Purpose |
|------|---------|
| `MainViewComp.java` | Sidebar + content; lazy settings build; chat session switching |
| `SidebarComp.java` | Logo, new chat, session list (max 60 at build), settings nav |
| `ChatViewComp.java` | Streaming chat, tool agent loop, secure memory, audit, terminal bubbles |
| `SettingsViewComp.java` | Appearance, model, inference, personality, memory editor, privacy, system info |

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

## TODO

### Rebranding / Distribution

#### Installer & package metadata
- [x] MSI installer links (`dist/msi/Product.wxs`) — updated to Ara GitHub
- [ ] Linux DEB/RPM metadata (`dist/linux_packages.gradle`) — vendor/maintainer for real releases
- [x] jpackage vendor set to `Ara` (`dist/jpackage.gradle`)

#### Documentation
- [x] README.md — cleaned up, correct package path
- [ ] CONTRIBUTING.md — deleted in rebrand; decide if needed
- [ ] LICENSE.md — KickstartFX license text; decide final licensing

#### Signing / distribution
- [ ] GPG signing keys (env vars in gradle scripts)
- [ ] macOS notarization (Apple Developer certs)
- [ ] Windows code signing (Azure Key Vault for production MSI)

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
- [x] Section order: Appearance → Model → Inference → Personality → Memory → Privacy → System → Reset
- [x] Inference sliders with value labels + help text
- [x] Unified `DEFAULT_SYSTEM_PROMPT` across model classes
- [x] Vex tool sync: live InferenceConfig sync, reload button, correct data paths

---

### Performance & inference (open)

- [x] Background model preload after unlock (`ModelPreloader`) — first-message latency
- [x] KV cache quantization (Q8_0 K / Q4_0 V) in `LlamaCppInferenceService`
- [ ] Prompt/context truncation or summarization for long chats (quadratic prefill cost)
- [ ] Speculative decoding / draft model via llama.cpp when available
- [ ] Profile & lazy-load non-critical resources at launch
- [ ] Adaptive: pause model load on low battery / thermal
- [ ] Research faster inference bindings or prebuilt jlink images for IDE launch
- [ ] Expose quant choice in settings + auto-download optimized GGUF variants

---

### Vex / Ara integration (open)

- [x] Auto-reload protocol catalog when Vex protocol files change (mtime snapshot)
- [x] Full Vex Protocol Catalog injected into every Ara system prompt
- [ ] Execute custom user-added ara-tool protocols beyond built-in executors
- [ ] Shared design doc for protocol 10 kill semantics in Ara agent cancellation flow