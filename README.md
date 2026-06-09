# gbkt — Game Boy Kotlin

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=svachmic_gbkt&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=svachmic_gbkt)

> Write Game Boy games in Kotlin. Compiles to GBDK-compatible C.

```kotlin
val game = gbGame("MyGame") {

    var score by u16Var(0)

    val player by entity {
        position(80, 72)
        sprite(SpriteAsset("player.png")) { size = 8 x 16 }
    }

    start = scene("gameplay") {
        enter {
            screen.clear()
            screen.showSprites()
        }

        every.frame {
            whenever(dpad.right) { player.x += 2 }
            whenever(dpad.left) { player.x -= 2 }
            whenever(dpad.down) { player.y += 2 }
            whenever(dpad.up) { player.y -= 2 }

            whenever(buttons.a.pressed) { score += 10 }
        }
    }
}

game.compile() // → GBDK-compatible C code
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
git clone https://github.com/anthropics/gbkt.git
cd gbkt
./gradlew build
```

### Step 3: Create Your First Game

Create a new Kotlin file `src/main/kotlin/MyGame.kt`:

```kotlin
import io.github.gbkt.core.builder.*
import io.github.gbkt.core.assets.SpriteAsset
import io.github.gbkt.core.ir.*

val myFirstGame = gbGame("HelloGB") {
    // Define a variable
    var counter by u8Var(0)

    // Define the starting scene
    start = scene("main") {
        enter {
            screen.clear()
        }

        every.frame {
            // Increment counter each frame
            counter += 1

            // Wrap at 255
            whenever(counter isAbove 254) {
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
val myFirstGame = gbGame("HelloGB") {
    val player by entity {
        position(80, 72)  // Center of screen
        sprite(SpriteAsset("player.png")) {
            size = 8 x 8
        }
    }

    start = scene("main") {
        enter {
            screen.clear()
            screen.showSprites()
        }

        every.frame {
            whenever(dpad.right) { player.x += 1 }
            whenever(dpad.left) { player.x -= 1 }
            whenever(dpad.up) { player.y -= 1 }
            whenever(dpad.down) { player.y += 1 }
        }
    }
}
```

Place your `player.png` in `src/main/resources/assets/` (8x8 or 8x16 pixels, using 4 shades of gray).

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
| `if (joypad() & J_RIGHT) { playerX++; }`       | `whenever(dpad.right) { playerX += 1 }` |
| `if ((joypad() & J_A) && !(prev & J_A)) {...}` | `whenever(buttons.a.pressed) { ... }`   |
| Manual sprite/OAM management                   | `entity { sprite(...) }`                |
| Manual scene state machines                    | `scene("name") { ... }`                 |

## Documentation

| Topic | Document |
|-------|----------|
| System architecture | [context/ARCHITECTURE.md](context/ARCHITECTURE.md) |
| Complete DSL reference | [context/DSL_REFERENCE.md](context/DSL_REFERENCE.md) |
| Contributing guide | [context/DEVELOPER_EXPERIENCE.md](context/DEVELOPER_EXPERIENCE.md) |
| Build tools & assets | [context/TOOLING.md](context/TOOLING.md) |

## License

This project is licensed under the [Mozilla Public License 2.0](LICENSE).

**Your games are yours.** The license above applies only to the gbkt framework—games you create are your property and can use any license.

See [NOTICE](NOTICE) for third-party dependencies including GBDK-2020.

## Acknowledgments

- [GBDK-2020](https://github.com/gbdk-2020/gbdk-2020) — The C toolchain we target
- [Pan Docs](https://gbdev.io/pandocs/) — The Game Boy hardware bible
