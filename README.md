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

```bash
# Clone and build
git clone https://github.com/anthropics/gbkt.git
cd gbkt
./gradlew build

# Run a sample game (outputs C code)
./gradlew :sample-minimal:run

# Build ROM (requires GBDK-2020)
./gradlew :sample-game:buildRom
```

**Sample projects:**
- `sample-minimal/` — Basic movement and boundaries
- `sample-dialog/` — Dialog and menu systems
- `sample-save/` — SRAM persistence
- `sample-adventure/` — Complete mini-game with enemies, coins, camera

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
| Project roadmap | [context/ROADMAP.md](context/ROADMAP.md) |

## License

This project uses multiple licenses:

| Component | License |
|-----------|---------|
| gbkt DSL & Core | [MPL-2.0](LICENSE) |
| VSCode Extension | [Apache-2.0](vscode-extension/LICENSE) |

**Your games are yours.** The licenses above apply only to the gbkt framework—games you create are your property and can use any license.

See [NOTICE](NOTICE) for third-party dependencies including GBDK-2020.

## Acknowledgments

- [GBDK-2020](https://github.com/gbdk-2020/gbdk-2020) — The C toolchain we target
- [Pan Docs](https://gbdev.io/pandocs/) — The Game Boy hardware bible
