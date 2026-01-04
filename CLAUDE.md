# CLAUDE.md - gbkt Development Guide

## Project Overview

gbkt (Game Boy Kotlin) is a DSL framework that compiles Kotlin code to GBDK-compatible C for Game Boy development.

**Architecture:** Kotlin DSL → IR (Intermediate Representation) → C Code Generation

## Build & Run

```bash
# Build the project
./gradlew build

# Build ROM (one command - requires GBDK-2020)
./gradlew :sample-game:buildRom

# Or step by step:
./gradlew :sample-game:generateC    # Generate C code
./gradlew :sample-game:compileRom   # Compile to ROM

# Run tests
./gradlew :gbkt-core:test
```

## Tech Stack

- **Kotlin**: 2.3.0 (multiplatform)
- **Gradle**: 9.0
- **JVM Target**: 21
- **Native Targets**: Linux x64, macOS x64, macOS ARM64

## Documentation Index

| Document | Use When You Need To... |
|----------|-------------------------|
| [context/ARCHITECTURE.md](context/ARCHITECTURE.md) | Understand IR nodes, data flow, codegen structure, module organization |
| [context/DSL_REFERENCE.md](context/DSL_REFERENCE.md) | Look up DSL syntax for variables, entities, scenes, dialogs, menus, saves, camera, collision |
| [context/DEVELOPER_EXPERIENCE.md](context/DEVELOPER_EXPERIENCE.md) | Add new IR nodes, DSL constructs, or platform-specific code |
| [context/TOOLING.md](context/TOOLING.md) | Work with asset pipeline, GBC palettes, Gradle plugin, VSCode extension |
| [context/ROADMAP.md](context/ROADMAP.md) | Check what's implemented, planned, or in progress |

## Common Tasks Routing

| Task | Go To |
|------|-------|
| Add a new DSL keyword/construct | DEVELOPER_EXPERIENCE.md → "Adding DSL Constructs" |
| Add a new IR node type | DEVELOPER_EXPERIENCE.md → "Adding IR Nodes" |
| Understand the compilation pipeline | ARCHITECTURE.md → "Data Flow" |
| Add platform-specific codegen | DEVELOPER_EXPERIENCE.md → "Platform Code" |
| Fix/add asset processing | TOOLING.md → "Asset Pipeline" |
| Add VSCode extension features | TOOLING.md → "VSCode Extension" |

## Quick DSL Examples

```kotlin
// Variables (u8Var, u16Var)
var score by u8Var(0)
score += 10

// Entities with sprites and position
val player by entity {
    position(80, 72)
    sprite(SpriteAsset("player.png")) {
        size = 8 x 16
        hitbox(0, 0, 8, 16)
    }
}

// D-pad input (returns Condition directly)
whenever(dpad.right) { player.x += 2 }
whenever(dpad.left) { player.x -= 2 }

// Button input (has .held, .pressed, .released)
whenever(buttons.a.pressed) { jump() }
whenever(buttons.start.pressed) { scene(pauseScene) }

// Conditions with comparisons
whenever(player.x isAbove 160) { player.x set 0 }
whenever(score isAtLeast 100) { win() }

// Collision detection
whenever(player collidesWith enemy) { takeDamage() }

// Scenes with lifecycle (capture SceneRef for type-safe transitions)
lateinit var gameplayScene: SceneRef
gameplayScene = scene("gameplay") {
    enter { screen.showSprites() }
    every.frame { updatePlayer() }
    exit { screen.hideSprites() }
}
start = gameplayScene

// Camera with smooth follow and transitions
val camera = camera { smoothing = 0.15f }
camera.follow(player)
camera.shake(4, 10.frames)
camera.fadeOut(30.frames) { scene(gameoverScene) }

// Tweening with easing
tween(player.x, from = 0, to = 100, duration = 60.frames, easing = Easing.EASE_OUT)
```

## Input API Distinction

**D-pad** (`dpad.right`, `dpad.left`, `dpad.up`, `dpad.down`, `dpad.none`):
- Returns `Condition` directly
- Use: `whenever(dpad.right) { ... }`

**Buttons** (`buttons.a`, `buttons.b`, `buttons.start`, `buttons.select`):
- Has `.held`, `.pressed`, `.released` properties
- Use: `whenever(buttons.a.pressed) { ... }`

## Key Source Locations

| Component | Path |
|-----------|------|
| DSL builders | `gbkt-core/src/commonMain/kotlin/io/github/gbkt/core/dsl/` |
| IR nodes | `gbkt-core/src/commonMain/kotlin/io/github/gbkt/core/ir/` |
| Code generation | `gbkt-core/src/commonMain/kotlin/io/github/gbkt/core/codegen/` |
| Entity system | `gbkt-core/src/commonMain/kotlin/io/github/gbkt/core/entity/` |
| Gradle plugin | `gbkt-gradle-plugin/` |
| CLI tool | `gbkt-cli/` |
| VSCode extension | `vscode-extension/` |

See [context/DSL_REFERENCE.md](context/DSL_REFERENCE.md) for complete syntax reference.
