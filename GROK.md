# Ara — On-Device AI Assistant (JavaFX)

## Overview

Ara is a desktop JavaFX chat application that runs **fully on-device** using `java-llama.cpp` (GGUF models). [...] 

**Version:** `5.6` (see `version` file; main is stable 5.6, develop for next in-dev versions)

[Full project structure, data layout, tools, packages, startup, performance, dependencies, build & run sections retained from original.]

## Branch Strategy

- `main`: Stable current version (5.6). Production-ready, default branch, source for releases and tags. Keep clean.
- `develop`: Unstable branch for new in-development versions and all active development work. This is where new features, Vex extensions, and Grok-driven changes live.

## Grok / AI Assistant Workflow (grok build)

The workflow for Grok build (using this GROK.md):

1. Clone the unstable repo and checkout the develop branch.
2. Do the work on it as listed in the TODO list or as stated by the user in a chat session.
3. Check for errors (compile with ./gradlew :app:compileJava, test key flows like inference and tool calls).
4. Push the code to the unstable (develop) branch.
5. Finish with a report of how it went, any errors that need addressing, and status of TODO items.

Detailed steps:
- git clone https://github.com/OliverRawden/Ara.git
- cd Ara && git checkout develop && git pull
- Read GROK.md, implement changes
- Compile and error-check
- git add -A && git commit -m "..." && git push origin develop
- Report summary, errors, next actions.

[TODO section retained and ready for new items on develop.]

This ensures stable main stays polished while develop advances the next version.