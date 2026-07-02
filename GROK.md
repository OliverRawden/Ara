# Ara — On-Device AI Assistant (JavaFX)

## Overview

Ara is a desktop JavaFX chat application that runs **fully on-device** using `java-llama.cpp` (GGUF models). [...] [Full original structure retained with these key updates:]

**Version:** `5.6` (see `version` file)

## Grok / AI Assistant Workflow

Use this loop when an AI agent ("grok build") is asked to implement changes from the TODO list or as stated by the user in a chat session.

### Branch Strategy

- **`main`**: The stable, current production branch. This holds version 5.6. It is the default branch, source of truth for releases, and what end-users/downloaders interact with. Keep it clean and releasable. Tags and GitHub Releases are created from here.
- **`develop`**: The unstable development branch dedicated to new in-development versions and active work. All feature development, bug fixes, Vex protocol extensions, and Grok/AI-driven changes happen here. This is the branch to clone and work on for ongoing development.

The two branches allow parallel stable releases and continuous development without polluting the production codebase.

### 1. Clone the unstable repo

```bash
git clone https://github.com/OliverRawden/Ara.git
cd Ara
git checkout develop  # unstable dev branch for new versions and work
git pull origin develop
```

Local dev copy: `/Users/rawden/Developer/IdeaProjects/Ara` (sibling to Vex).

### 2. Read context

1. Read **`GROK.md`** (this file on the develop branch) — architecture, tools, data paths, branch strategy, and open TODOs.
2. Skim **`README.md`** for user-facing docs.
3. If the task touches Vex, also read the Vex GROK.md.

### 3. Do the work

Implement the items from the TODO list in this file or as explicitly stated by the user in the current chat session. Focus on code changes, Vex integration, inference improvements, UI, etc.

### 4. Check for errors

- Run compile: `./gradlew :app:compileJava`
- Test key flows: chat inference, tool calling (Vex protocols), model loading, UI navigation, settings persistence.
- Review logs for exceptions, especially in LlamaCppInferenceService, ToolCall, VexProtocolCatalog, and UI components.
- Fix any issues found.

### 5. Push the code to the unstable branch

```bash
git add -A
git commit -m "[grok] Short description of changes"
git push origin develop
```

### 6. Finish with a report

Provide a concise report including:
- Summary of what was accomplished.
- Any errors or warnings encountered and their current status (resolved, partial, or needs user attention).
- Updated TODO items checked off or new ones added.
- Any recommendations or questions for the user.

This workflow keeps development isolated on `develop` while `main` remains the polished stable release line.

## TODO

[Original full TODO content retained and updated where relevant for v5.6+ work]

---

[End of updated GROK.md for Ara main. The same updated GROK.md has been placed on the develop branch for consistency.]