# Lib — offline Maven repository

This folder holds **all third-party dependency JARs, POMs, and plugins** required to build ProjectMind without internet access.

Maven stores artifacts here using the standard repository layout, for example:

```
Lib/org/springframework/boot/spring-boot/3.4.1/spring-boot-3.4.1.jar
Lib/com/projectmind/projectmind-core/0.1.0-SNAPSHOT/projectmind-core-0.1.0-SNAPSHOT.jar
```

## First-time setup (online machine)

```bash
./scripts/populate-lib.sh
```

Copy the entire project directory (including `Lib/`) to the offline machine.

## Build offline

From the **project root** (`LLM_EXAMPLE/`), run:

```bash
mvn -s .mvn/settings-offline.xml clean install
```

Important:
- Always run Maven from the repo root (not from a submodule like `projectmind-core/`).
- `Lib/` is resolved via `${maven.multiModuleProjectDirectory}/Lib`.
- If tests fail with missing Surefire/JUnit jars, re-run `./scripts/populate-lib.sh` once while online.
