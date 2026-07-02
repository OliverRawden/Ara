# Ara

A clean, cross-platform desktop on-device AI chat application built with JavaFX.

> Ara was bootstrapped from the excellent [KickstartFX](https://kickstartfx.xpipe.io/) project template and has since been extensively rebranded and customized.

## Features

- **Modern sidebar-first UI** with dark/light mode and system accent integration
- **Fully on-device AI inference** using GGUF models via java-llama.cpp
- **Streaming chat** with tool calling support (web search + local terminal execution)
- **Settings** for appearance, model management (list, load, download), inference params, and system prompt
- **Efficient** — virtual threads for non-blocking inference

## Architecture

```
tech.rawden.ara/
├── Main.java              # Entry point (JavaFX Application)
├── core/                  # AraModel, AraTheme, Mac integration, shortcuts
├── ui/                    # MainView, Sidebar, ChatView, SettingsView
├── ai/                    # InferenceService + LlamaCpp impl, ModelManager (HF downloads)
├── model/                 # Chat*, AppSettings, persistence via Jackson
├── comp/                  # RegionBuilder + reusable base components (ToggleSwitch etc.)
├── platform/              # Threading helpers, AraLogo, color utils, Mac window tweaks
├── tool/                  # Optional agent tools (TerminalExecutor, WebSearchService)
└── util/                  # OsType, ThreadHelper, etc.
```

## Prerequisites

- **JDK 21+** (Java 25 recommended)
- **Gradle** (wrapper included)

## Running (from source)

```bash
./gradlew :app:run
```

## Building Native Installers

```bash
./gradlew :dist:jpackage
```

Outputs (platform-specific) go to `dist/build/dist/`.

## Model Setup

Ara keeps models in `~/Ara/models/`.

- Use the in-app **Settings → Model** section to download the recommended ~4.5 GB Qwen2.5-7B Q4_K_M.
- Or manually place any GGUF into that folder; the app will list and let you load them.
- The first GGUF found on startup is auto-loaded if no model is active.

## Recommended Models

- [Qwen2.5-7B-Instruct (Q4_K_M)](https://huggingface.co/bartowski/Qwen2.5-7B-Instruct-GGUF) — current default download
- Smaller alternatives: Llama-3.2-3B or 1B Q4/Q8 variants from bartowski on HF

## Data & Config

- Chats: `~/Ara/data/chats.json`
- Settings: `~/Ara/data/settings.json`
- Models: `~/Ara/models/*.gguf`

## License

Multi-licensed (see LICENSE* files). Originally based on KickstartFX template.
