# Suggested Commands

## Build & Test
```bash
# Build entire project
./gradlew build

# Run core tests
./gradlew :gbkt-core:test

# Clean build
./gradlew clean build
```

## Code Generation (Labyrinth example)
```bash
# Generate C from Kotlin DSL
./gradlew :LabyrinthOfTheDragon-port:generateC

# Build ROM (requires GBDK-2020)
./gradlew :LabyrinthOfTheDragon-port:buildRom

# Clean + regenerate (recommended when checking output)
./gradlew :LabyrinthOfTheDragon-port:clean :LabyrinthOfTheDragon-port:generateC
```

## General Gradle
```bash
# Generate C only (no compilation)
./gradlew generateC

# Build ROM
./gradlew buildRom

# Run in emulator (auto-detects mGBA)
./gradlew runEmulator
```

## Output Locations
- Generated C: `<module>/build/generated/gbdk/` or `build/gbkt/generated/main.c`
- ROM: `build/gbkt/output/{name}.gb`
- Bank sizes: `.noi` file → `DEF l__CODE_<N>` gives hex size per bank

## System Commands (macOS/Darwin)
- `git`, `ls`, `cd`, `grep`, `find` — standard unix utils
- Gradle wrapper: `./gradlew` (no global Gradle needed)
