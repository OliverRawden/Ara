# Zed IDE setup for Ara

Project-local Zed configuration so you can edit, run, and debug Ara without IntelliJ.

## One-time setup

1. **Install the Java extension** in Zed: command palette → `zed: extensions` → install **Java**.
2. **JDK 25** available (Homebrew example: `brew install openjdk@25`).
3. **Open this repository root** in Zed (the folder that contains `build.gradle` and `gradlew`), not a subfolder.
4. If language features fail, confirm the paths in `.zed/settings.json` match your machine:
   - `lsp.jdtls.initialization_options.settings.java.configuration.runtimes`
   - `lsp.jdtls.initialization_options.settings.java.import.gradle.java.home`
   - `lsp.gradle-language-server.settings.java_home`
   - `terminal.env.JAVA_HOME`

   On macOS you can print the correct path with:

   ```bash
   /usr/libexec/java_home -v 25
   ```

5. Reload the workspace once: command palette → `workspace: reload`.

## Run Ara

Preferred path (matches IntelliJ “run app” / `./gradlew :app:run`):

| Action | How |
|--------|-----|
| **Run Ara** | Command palette → `task: spawn` → **Run Ara** |
| **Compile only** | `task: spawn` → **Compile Ara** |
| **Format (Spotless)** | `task: spawn` → **Format (Spotless)** |
| Terminal | `./gradlew :app:run` |

Working directory is the repo root. JVM args, JavaFX modules, and JNA/llama native access are configured by Gradle.

## Debug

**Reliable (recommended):**

1. `task: spawn` → **Run Ara (debug wait :5005)**  
   (Gradle waits for a debugger on port 5005.)
2. Debugger menu → scenario **Attach Ara (Gradle --debug-jvm :5005)**.

**Experimental direct launch:** scenario **Launch Ara (Java)** uses JDTLS classpath resolution. Prefer Gradle attach for JavaFX + modular + native (llama.cpp / JNA) work.

## Tasks (`.zed/tasks.json`)

- **Run Ara** — `./gradlew :app:run`
- **Run Ara (debug wait :5005)** — `./gradlew :app:run --debug-jvm`
- **Compile Ara** — `./gradlew :app:compileJava`
- **Format (Spotless)** — `./gradlew spotlessApply`
- **Clean + Compile Ara**
- **Gradle: list app tasks**

## Notes

- Ara is a **modular JavaFX** app (`mainModule = tech.rawden.ara`, entry `tech.rawden.ara.Main`).
- Lombok is enabled for JDTLS (`lombok_support: true`).
- Large packaging trees under `dist/build`, `dist/javafx`, and model-upload dirs are excluded from import for faster LSP.
- Product data still lives under `~/Documents/Ara/` (see root `GROK.md`).
