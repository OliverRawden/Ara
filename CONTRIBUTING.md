# Contributing to Ara

Ara is **proprietary software** owned by Oliver Rawden. This repository is not open source.

## Before you contribute

Contact **contact-rawden@pm.me** for written permission before submitting changes, forks, or derivative work.

## Development setup

```bash
git clone https://github.com/OliverRawden/Ara.git
cd Ara
./gradlew :app:run
```

Requirements: JDK 21+ (Java 25 recommended). See `GROK.md` for architecture, data paths, and the active TODO list.

## Pull requests

1. Open an issue or email first for scope agreement.
2. Keep changes focused — match existing style in `tech.rawden.ara`.
3. Run `./gradlew :app:compileJava` before pushing.
4. Update `GROK.md` if you change architecture, tools, or release workflow.

## Related projects

- **[Vex](https://github.com/OliverRawden/Vex)** — protocol console; Ara loads ara-tool definitions from `~/Documents/Vex/Protocols/`.