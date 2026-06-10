# gbkt-cli -- Command-Line Tool

Standalone CLI for scaffolding new gbkt projects from templates and invoking code generation / ROM builds without Gradle.

## Dependencies

- **Depends on:** `gbkt-core`, `gbkt-backend-api`

## Key Files

| File | Role |
|------|------|
| `Main.kt` | Entry point -- parses args, dispatches to command handlers |
| `Commands.kt` | Implements `handleNew`, `handleBuild`, `handleRun`; holds the template registry |
| `templates/Template.kt` | `Template` interface -- contract for project scaffolding (name, description, buildGradle, settingsGradle, gameKt) |
| `templates/MinimalTemplate.kt` | Minimal starter -- empty game with one sprite |
| `templates/PlatformerTemplate.kt` | Platformer starter -- player with gravity and platforms |
| `templates/RpgTemplate.kt` | RPG starter -- top-down movement with basic tilemap |
| `templates/PuzzleTemplate.kt` | Puzzle starter -- grid-based puzzle game |

## Commands

| Command | Purpose |
|---------|---------|
| `new <name> [--template T]` | Scaffold a new project directory with `build.gradle.kts`, `settings.gradle.kts`, and `Game.kt` |
| `build [args]` | Generate C code and compile a ROM (delegates to GBDK backend) |
| `run [args]` | Build and launch the ROM in an emulator |
| `list-targets` | Print available backend targets discovered via ServiceLoader |
| `help` / `--help` / `-h` | Print usage information |
| `version` / `--version` / `-v` | Print gbkt version |

## Project Templates

| Template | Key | Description |
|----------|-----|-------------|
| `MinimalTemplate` | `minimal` | Empty game with one sprite |
| `PlatformerTemplate` | `platformer` | Player with gravity and platforms |
| `RpgTemplate` | `rpg` | Top-down movement with basic tilemap |
| `PuzzleTemplate` | `puzzle` | Grid-based puzzle game starter |

Each template is a Kotlin `object` implementing `Template`. The interface requires three generators: `buildGradle(projectName)`, `settingsGradle(projectName)`, and `gameKt(projectName)`. Helper functions `commonBuildGradle()` and `commonSettingsGradle()` provide shared boilerplate.

## Usage

```bash
# Scaffold a new project
gbkt new my-game --template platformer

# Generate C and build ROM
gbkt build

# Build and run in emulator
gbkt run

# List available backends
gbkt list-targets
```

## Adding a New Template

1. Create `templates/MyTemplate.kt` as an `object : Template`.
2. Implement `name`, `description`, `buildGradle()`, `settingsGradle()`, `gameKt()`.
3. Register it in the `templates` map in `Commands.kt`.
