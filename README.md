# gbkt — Game Boy Kotlin

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=svachmic_gbkt&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=svachmic_gbkt)

> Write Game Boy games in Kotlin. Compiles to GBDK-compatible C.

```kotlin
val myGame = game("MyGame") {

    var score by u16Var(0)

    val player by actor {
        position(80, 72)
        sprite(asset("sprites/player.png")) { size(8, 16) }
    }

    start = scene("gameplay") {
        enter {
            clear()
            showSprites()
        }

        frame {
            runIf(dpad.right.held) { player.x += 2 }
            runIf(dpad.left.held) { player.x -= 2 }
            runIf(dpad.down.held) { player.y += 2 }
            runIf(dpad.up.held) { player.y -= 2 }

            runIf(buttons.a.pressed) { score += 10 }
        }
    }
}
// ./gradlew buildRom → GBDK-compatible C → .gb ROM
```

## Quick Start

### Prerequisites

- **Java 21** or later ([Temurin](https://adoptium.net/) recommended)
- **GBDK-2020** ([Download here](https://github.com/gbdk-2020/gbdk-2020/releases))
- **mGBA** or another Game Boy emulator (optional, for testing)

### Step 1: Install GBDK

Download and extract GBDK-2020. Then set the `GBDK_HOME` environment variable:

```bash
# macOS/Linux
export GBDK_HOME=/path/to/gbdk-2020

# Or install to a common path (auto-detected):
# /opt/gbdk-2020, ~/gbdk-2020
```

### Step 2: Clone and Build

```bash
git clone https://github.com/svachmic/gbkt.git
cd gbkt
./gradlew build
```

### Step 3: Create Your First Game

Create a new Kotlin file `src/main/kotlin/MyGame.kt`:

```kotlin
import io.github.gbkt.core.dsl.*

val myFirstGame = game("HelloGB") {
    // Define a variable
    var counter by u8Var(0)

    // Define the starting scene
    start = scene("main") {
        enter {
            clear()
        }

        frame {
            // Increment counter each frame
            counter += 1

            // Wrap at 255
            runIf(counter isAbove 254) {
                counter set 0
            }
        }
    }
}
```

### Step 4: Configure build.gradle.kts

```kotlin
plugins {
    kotlin("jvm") version "2.3.0"
    id("io.github.gbkt")
}

gbkt {
    game("MyGameKt::myFirstGame")
    assets("res")
    outputName.set("hello")
}
```

### Step 5: Build and Run

```bash
# Generate C code and compile ROM
./gradlew buildRom

# Run in emulator (requires mGBA)
./gradlew runEmulator
```

Your ROM will be at `build/gbkt/output/hello.gb`.

### Step 6: Add a Sprite

Add a player sprite with D-pad movement:

```kotlin
val myFirstGame = game("HelloGB") {
    val player by actor {
        position(80, 72)  // Center of screen
        sprite(asset("sprites/player.png")) {
            size(8, 8)
        }
    }

    start = scene("main") {
        enter {
            clear()
            showSprites()
        }

        frame {
            runIf(dpad.right.held) { player.x += 1 }
            runIf(dpad.left.held) { player.x -= 1 }
            runIf(dpad.up.held) { player.y -= 1 }
            runIf(dpad.down.held) { player.y += 1 }
        }
    }
}
```

Place your `player.png` in `res/sprites/` (8x8 or 8x16 pixels, using 4 shades of gray).

## Example Games

The `gbkt-examples/` directory contains complete games demonstrating the framework:

| Game | Complexity | Demonstrates |
|------|-----------|-------------|
| **Pong** | Beginner | Entities, input, collision, scenes |
| **Breakout** | Intermediate | Sound effects, entity pools, status bars, 4 scenes |
| **Simple Physics** | Intermediate | Physics variables, gravity, collision detection |
| **Metasprites** | Intermediate | GBC color palettes, multi-tile sprites |
| **Metasprites Stress** | Intermediate | Multiple metasprites, palette variants |
| **Banks** | Intermediate | Multi-bank ROM layout, tileset data |
| **Platformer Template** | Advanced | Platformer physics, zones, GBC palettes, camera |

```bash
# Generate C and build any example
./gradlew :gbkt-examples:pong:generateC
./gradlew :gbkt-examples:pong:buildRom

# Or try any other example
./gradlew :gbkt-examples:breakout:buildRom
./gradlew :gbkt-examples:simple-physics:buildRom
./gradlew :gbkt-examples:platformer-template:buildRom
```

## Why gbkt?

| C (GBDK)                                       | gbkt                                    |
|------------------------------------------------|-----------------------------------------|
| `UINT8 playerX = 80;`                          | `var playerX by u8Var(80)`              |
| `if (joypad() & J_RIGHT) { playerX++; }`       | `runIf(dpad.right.held) { playerX += 1 }` |
| `if ((joypad() & J_A) && !(prev & J_A)) {...}` | `runIf(buttons.a.pressed) { ... }`   |
| Manual sprite/OAM management                   | `actor { sprite(...) }`                 |
| Manual scene state machines                    | `scene("name") { ... }`                 |

## Documentation

| Topic | Document |
|-------|----------|
| System architecture & extending the framework | [context/ARCHITECTURE.md](context/ARCHITECTURE.md) |
| Complete DSL reference | [context/DSL_REFERENCE.md](context/DSL_REFERENCE.md) |
| Contributing guide | [CONTRIBUTING.md](CONTRIBUTING.md) |
| Build tools & assets | [context/TOOLING.md](context/TOOLING.md) |
| Testing (unit → emulator → AI agent) | [context/TESTING.md](context/TESTING.md) |

## License

This project is licensed under the [Mozilla Public License 2.0](LICENSE).

**Your games are yours.** The license above applies only to the gbkt framework—games you create are your property and can use any license.

See [NOTICE](NOTICE) for third-party dependencies including GBDK-2020.

## Acknowledgments

- [GBDK-2020](https://github.com/gbdk-2020/gbdk-2020) — The C toolchain we target
- [Pan Docs](https://gbdev.io/pandocs/) — The Game Boy hardware bible
