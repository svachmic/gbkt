# gbkt — Project Vision & Architecture

## North Star

**gbkt** is an open-source Kotlin DSL framework that lets developers build Game Boy and Game Boy Color games the way they build modern Android apps — declaratively, with compile-time safety, automated resource management, and test-driven development on the JVM.

The developer writes Kotlin. gbkt produces a working `.gb` ROM.

No C. No manual banking. No `png2asset` commands. No VRAM math. No guessing why tiles are glitched until you count them by hand in a tile viewer.

> *Write Kotlin, ship a cartridge.*

---

## Who Is This For

gbkt is for the **Game Boy homebrew community** — hobbyists, jam participants, retro enthusiasts, and indie developers who want to make real Game Boy games but are turned off by the tooling friction of raw C development with GBDK.

The target user:
- Knows a programming language (ideally Kotlin/Java/JVM, but any modern language)
- Has a game idea they want to ship on actual Game Boy hardware
- Does NOT want to learn GB hardware registers, bank switching, VRAM layout, or DMA timing
- DOES want to focus on game design, mechanics, art, and music
- Expects modern DX: IDE autocomplete, compile-time errors, tests, dependency management

gbkt should feel as natural to a Kotlin developer as Android development does — Gradle builds it, IntelliJ supports it, tests run fast, and the framework handles the platform plumbing.

---

## What gbkt Is NOT

- **Not a game engine with a visual editor.** That's GB Studio. gbkt is a code-first framework for developers who want programmatic control.
- **Not a C library.** That's GBDK, ZGB, or hUGElib. gbkt uses GBDK as a backend but the user never writes or sees C.
- **Not limited to simple games.** The complexity ceiling is Pokémon Red, Super Mario Land, Tetris, or a top-down racer — full commercial-grade Game Boy games with save systems, complex state, scrolling worlds, and multiple game modes.

---

## Complexity Ceiling

gbkt must be capable of producing games at the complexity level of:

| Game | What It Demands |
|------|----------------|
| **Pokémon Red/Blue** | Overworld with 20+ maps, wild encounters, turn-based battle engine, 151-creature database, party management, evolution system, PC storage, save/load with multiple slots, NPCs with branching dialog, item inventory, badge progression |
| **Super Mario Bros (DX)** | Precision platformer physics, horizontal scrolling, enemy AI patterns, power-up state machine, level progression, score system, lives, timer |
| **Tetris** | Tight input handling, piece rotation with wall kicks, line clear detection, gravity/speed progression, score/high score persistence, simple but polished game loop |
| **Top-down racer** | Sprite-heavy rendering, AI opponents, track scrolling, collision detection, lap tracking, speed/acceleration physics, split-screen or alternating multiplayer |

If gbkt can handle a Pokémon-scale RPG and a Mario-scale platformer, it can handle anything the Game Boy is capable of.

---

## Core Principles

### 1. Declarative Over Imperative
The user declares *what* the game is — scenes, actors, sprites, behaviors — not *how* to render it. The framework decides how to load tiles, manage VRAM, switch banks, and schedule DMA.

### 2. Compile-Time Over Runtime
Every constraint that can be checked at build time MUST be. Tile budget overflows, sprite-per-scanline violations, ROM size exceeded, missing assets — these are compiler errors with actionable messages, not mysterious runtime glitches.

### 3. Zero-Cost Abstractions
Abstractions must not impose runtime overhead the user didn't ask for. Like Rust's borrow checker, gbkt's safety comes from compile-time analysis that produces the same efficient C code a skilled GBDK developer would write by hand.

### 4. Escape Hatches Exist
The Game Boy's constraints will always leak at the edges. Power users can drop to `inlineC {}` or `inlineAsm {}` blocks for hot paths, hardware tricks, or effects the framework doesn't cover. These blocks are clearly marked, excluded from JVM testing, and don't break the rest of the framework.

### 5. Good Defaults, Full Control
A new user running `gradle build` with a simple scene and a sprite sheet should get a working ROM with no configuration. An advanced user building a Pokémon-scale RPG should be able to tune bank allocation strategies, VRAM loading order, and interrupt priorities.

### 6. Multiplatform-Ready Architecture
gbkt ships targeting Game Boy / Game Boy Color via GBDK-2020. But the architecture — semantic IR, compiler passes, asset pipeline — is designed so that future backends (Game Boy Advance, SNES, NES, Analogue Pocket, Sega Master System) can be added without rewriting the DSL or analysis layers. The working name "gbkt" may evolve to **RetroKt** or **RetroCompose** when multiplatform support lands.

---

## Technology Stack

| Layer | Technology | Why |
|-------|-----------|-----|
| DSL & Compiler | **Kotlin** on JDK | Type-safe DSL builders, sealed types for exhaustive IR, excellent IDE support, familiar to Android devs |
| Build System | **Gradle** with custom plugin | Standard JVM build tool, supports multi-module projects, task dependency graph, incremental builds, plugin portal for distribution |
| IDE | **IntelliJ IDEA / Android Studio** | Best Kotlin IDE, plugin API for custom inspections, gutter icons, tool windows |
| C Backend | **GBDK-2020** | Mature, actively maintained, targets DMG + GBC + Analogue Pocket, proven by hundreds of homebrew games |
| Testing | **JUnit 5 / kotlin.test** on JVM | Game logic tests run in milliseconds without an emulator |
| Asset Formats | **PNG**, **Tiled (.tmx)**, **LDtk**, **hUGEtracker (.uge)** | Industry-standard tools the community already uses |

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│                     User's Game Project                       │
│                                                              │
│  build.gradle.kts          src/                 assets/      │
│  (gbkt Gradle plugin)      ├── game.kt          ├── sprites/│
│                            ├── scenes/          ├── tiles/  │
│                            ├── actors/          ├── maps/   │
│                            ├── systems/         ├── music/  │
│                            └── test/            └── sfx/    │
└──────────────┬───────────────────────┬───────────────────────┘
               │                       │
               ▼                       ▼
┌──────────────────────────┐ ┌─────────────────────────────────┐
│   Kotlin DSL Frontend    │ │   Asset Pipeline                 │
│   (Declarative game      │ │   PNG → 2bpp tiles               │
│    definitions)          │ │   TMX → tilemaps                 │
│                          │ │   UGE → music data               │
│   Module: :dsl           │ │   Module: :assets                │
└───────────┬──────────────┘ └──────────────┬──────────────────┘
            │                               │
            ▼                               ▼
┌──────────────────────────────────────────────────────────────┐
│                    Semantic IR (GbIR)                          │
│                    Module: :ir                                 │
│                                                              │
│  Pure data classes and sealed interfaces.                     │
│  Zero dependencies. The contract between all modules.         │
│                                                              │
│  GameIR                                                      │
│  ├── CartridgeConfig (MBC, ROM/RAM size, color mode)         │
│  ├── PaletteIR[]                                             │
│  ├── TilesetIR[] ← from asset pipeline                       │
│  ├── SpriteSheetIR[] ← from asset pipeline                   │
│  ├── ActorDefinitionIR[]                                     │
│  │   ├── sprite ref, collision box, states                   │
│  │   └── BehaviorIR (state machine or script)                │
│  ├── SceneIR[]                                               │
│  │   ├── BackgroundLayerIR (tilemap, scroll config)          │
│  │   ├── ActorInstanceIR[] (placed actors)                   │
│  │   ├── TriggerIR[] (zones, transitions, events)            │
│  │   └── SceneScriptIR (update loop as ScriptOp tree)        │
│  ├── SystemIR[] (battle engine, dialog, inventory, physics)  │
│  ├── GlobalStateIR (RAM variables, SRAM save structure)      │
│  ├── MusicBankIR[]                                           │
│  └── SFXBankIR[]                                             │
│                                                              │
│  ScriptOp: sealed, restricted instruction set the compiler   │
│  can fully reason about. Covers movement, dialog, branching, │
│  state mutation, scene transitions, battle triggers, math.   │
│                                                              │
│  Platform-specific concepts (bank slots, VRAM ranges, OAM    │
│  slots) are NULLABLE fields — null until analysis passes     │
│  fill them in. This keeps :ir platform-aware but not         │
│  platform-coupled.                                           │
└──────────────────────────┬───────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                  Compiler Passes (ordered)                     │
│                  Module: :analysis                             │
│                                                              │
│  Each pass: GameIR → GameIR (pure function, no side effects) │
│                                                              │
│  1. Validation         Ref resolution, type checks, DSL      │
│                        constraint enforcement                │
│  2. Asset Processing   Tile dedup, sprite slicing, palette   │
│                        assignment, tile hashing              │
│  3. Bank Allocation    Bin-pack code + data into ROM banks.  │
│                        Scene locality optimization.          │
│  4. VRAM Planning      Per-scene tile slot layout. Shared    │
│                        tile detection across transitions.    │
│  5. OAM Planning       Sprite slot assignment. Per-scanline  │
│                        density analysis.                     │
│  6. RAM Planning       WRAM layout, HRAM allocation, SRAM   │
│                        save data structure.                  │
│  7. Transition Plan    Per-transition load/unload sequence.  │
│                        VRAM transfer scheduling across       │
│                        VBlanks.                              │
│  8. Optimization       Dead asset elimination, constant      │
│                        folding, duplicate tile merging,      │
│                        unreachable scene detection.          │
│  9. Budget Audit       FINAL GATE. Hard fail on: tile        │
│                        overflow, sprite overflow, ROM/RAM    │
│                        exceeded. Warnings on: scanline       │
│                        density, transition cost, tight       │
│                        budgets.                              │
└──────────────────────────┬───────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                    Code Generation                            │
│                    Module: :codegen                            │
│                                                              │
│  Reads fully-annotated IR. Emits structured C AST.           │
│                                                              │
│  CSourceFile → CFunction → CStatement → CExpr               │
│  (own sealed hierarchy in :codegen, NOT in :ir)              │
│                                                              │
│  Final step: C AST pretty-printer → .c / .h strings.        │
│  This is the ONLY place raw strings are assembled.           │
│                                                              │
│  Output:                                                     │
│  ├── main.c (entry point, main loop)                         │
│  ├── scene_manager.c/.h (scene lifecycle)                    │
│  ├── scenes/<name>.c/.h (per-scene init/update/teardown)     │
│  ├── actors/<name>.c/.h (actor behavior)                     │
│  ├── assets/<name>.c/.h (tile data, tilemaps, sprites)       │
│  ├── systems/<name>.c/.h (battle, dialog, inventory, etc.)   │
│  ├── bank_map.c/.h (trampoline functions)                    │
│  ├── save_data.c/.h (SRAM read/write)                        │
│  └── interrupts.c/.h (VBlank, LCD STAT, timer)               │
│                                                              │
│  Future: additional codegen backends for GBA, SNES, etc.     │
│  The :codegen module is the only platform-specific part.     │
└──────────────────────────┬───────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────┐
│              GBDK-2020 Toolchain                              │
│              lcc → .o → .gb / .gbc ROM                        │
└──────────────────────────────────────────────────────────────┘
```

---

## Module Dependency Graph

```
:ir              ← depends on NOTHING (pure data)
:dsl             ← depends on :ir
:assets          ← depends on :ir
:analysis        ← depends on :ir
:codegen         ← depends on :ir (has its own sealed C AST)
:test-runner     ← depends on :ir
:gradle-plugin   ← depends on all above (orchestrates the build)
:intellij-plugin ← depends on :ir, :analysis (for inspections)
```

**The critical property:** you can delete `:codegen` and everything else still compiles. You can delete `:intellij-plugin` and the build still works. `:ir` is the contract. Everything fans out from it.

Sealed types live in `:ir` for game domain concepts (`ScriptOp`, `SceneIR`, `ActorIR`) and in `:codegen` for C output concepts (`CStatement`, `CExpr`, `CType`). Two separate sealed worlds, two separate modules, connected by a translation function `ScriptOp → List<CStatement>`.

---

## The Kotlin DSL

The user writes declarative Kotlin. No C concepts leak through.

### Minimal Example — Tetris-tier

```kotlin
val tetris = gbktGame("Tetris Clone") {

    cartridge {
        mbc = MBC.NONE
        romSize = ROM_32KB
        colorMode = DMG
    }

    palette("main") {
        colors(0x081820, 0x346856, 0x88C070, 0xE0F8D0)
    }

    tileset("blocks") {
        source = asset("tiles/blocks.png")
        palette = ref("main")
    }

    scene("game") {
        background {
            tileset = ref("blocks")
            tilemap = asset("maps/playfield.tmx")
        }

        state<GameState> {
            var board by scoped(Array(20) { IntArray(10) })
            var currentPiece by scoped(Piece.random())
            var score by persistent(0)          // saved to SRAM
            var highScore by persistent(0)
        }

        onUpdate {
            handleInput()
            if (gravityTick()) {
                if (!moveDown()) {
                    lockPiece()
                    clearLines()
                    spawnNext()
                }
            }
            drawBoard()
        }
    }

    startScene = ref("game")
}
```

### Full Example — Pokémon-tier

```kotlin
val pokemon = gbktGame("Monster Quest") {

    cartridge {
        mbc = MBC.MBC5
        romBanks = 128
        ramBanks = 4
        colorMode = GBC_COMPATIBLE
    }

    // --- Palettes ---

    palette("overworld") { /* ... */ }
    palette("battle_bg") { /* ... */ }
    palette("ui") { /* ... */ }

    // --- Shared Systems ---

    system("dialog") {
        font = asset("fonts/main_8x8.png")
        boxStyle = DialogBox.POKEMON_STYLE
        maxChoices = 4
        textSpeed = configurable(1..5, default = 3)  // player setting
    }

    system("inventory") {
        maxSlots = 20
        maxStack = 99
        itemDatabase = asset("data/items.json")
        categories = listOf("Key Items", "Potions", "Balls", "TMs")
    }

    system("save") {
        slots = 1
        autoSave = false
        includes {
            +playerState
            +partyState
            +pcStorage
            +worldFlags
            +inventoryState
            +badgeState
        }
    }

    system("battle") {
        type = BattleType.TURN_BASED_1V1
        monsterDatabase = asset("data/monsters.json")    // 151 entries
        moveDatabase = asset("data/moves.json")
        typeChart = asset("data/type_chart.json")
        maxPartySize = 6

        onBattleStart {
            transition(ref("battle_scene"), effect = Effect.FLASH)
        }

        damageFormula { attacker, defender, move ->
            // Custom formula, compiled to fixed-point GB math
            val base = (2 * attacker.level / 5 + 2) * move.power * attacker.atk / defender.def / 50 + 2
            val stab = if (move.type in attacker.types) fixedMul(base, 1.5) else base
            applyTypeEffectiveness(stab, move.type, defender.types)
        }
    }

    system("encounter") {
        table("route1_grass") {
            entry(monster = "Pidgey", levels = 2..5, weight = 40)
            entry(monster = "Rattata", levels = 2..4, weight = 55)
            entry(monster = "Pikachu", levels = 3..5, weight = 5)
        }
        // more tables...
    }

    // --- Sprite Sheets ---

    spriteSheet("player_overworld") {
        source = asset("sprites/player.png")
        frameSize = Size(16, 16)
        animations {
            "walk_down" frames 0..3 speed 150.ms
            "walk_up" frames 4..7 speed 150.ms
            "walk_left" frames 8..11 speed 150.ms
            "walk_right" frames 12..15 speed 150.ms
            "idle_down" frame 0
            "idle_up" frame 4
            "idle_left" frame 8
            "idle_right" frame 12
        }
    }

    // --- Actors ---

    actor("player") {
        sprite = ref("player_overworld")
        size = SPRITE_16x16
        collision = CollisionBox(4, 8, 8, 8)  // offset from sprite origin

        state<PlayerState> {
            var x by sceneLocal(0)
            var y by sceneLocal(0)
            var direction by sceneLocal(Direction.DOWN)
            var name by persistent("RED")
            var badges by persistent(0)
            var money by persistent(3000)
        }

        onUpdate {
            val input = joypad()
            if (input.anyDirection) {
                val (dx, dy) = input.toVector(speed = 2)
                if (canMove(state.x + dx, state.y + dy)) {
                    state.x += dx
                    state.y += dy
                    state.direction = input.toDirection()
                    playAnimation("walk_${state.direction.name.lowercase()}")
                } else {
                    playAnimation("idle_${state.direction.name.lowercase()}")
                }
            } else {
                playAnimation("idle_${state.direction.name.lowercase()}")
            }

            // A button interaction
            if (input.aPressed) {
                val target = facingTile(state.x, state.y, state.direction)
                interactWith(target)
            }
        }
    }

    // --- Scenes ---

    scene("pallet_town") {
        background {
            tileset = ref("overworld_tiles")
            tilemap = asset("maps/pallet_town.tmx")
            scrollable = true
            camera = followActor(ref("player"), smooth = true)
        }

        spawn("player") at Position(80, 120)

        npc("rival") {
            sprite = ref("rival_sprite")
            position = Position(120, 64)
            path = patrol(listOf(Position(120, 64), Position(150, 64)), speed = 1)

            onInteract {
                dialog("rival_intro") {
                    line("Hey! I'm your rival!")
                    line("Let's battle soon!")
                    choice("Want to battle now?") {
                        option("Yes") { /* trigger battle */ }
                        option("No") { line("Chicken!") }
                    }
                }
            }
        }

        npc("mom") {
            sprite = ref("mom_sprite")
            position = Position(40, 88)
            onInteract {
                if (!flags["got_starter"]) {
                    dialog {
                        line("Prof. Oak is looking for you!")
                        line("He's in his lab to the north.")
                    }
                } else {
                    dialog { line("Good luck on your adventure!") }
                    healParty()
                }
            }
        }

        wildGrass {
            area = Rect(32, 0, 128, 48)
            encounterRate = 20  // % per step
            encounterTable = ref("route1_grass")
        }

        trigger("to_route1") {
            area = Rect(64, 0, 32, 8)
            onEnter { transition(ref("route1"), entryPoint = "south") }
        }

        trigger("to_lab") {
            area = Rect(96, 56, 16, 8)
            onEnter { transition(ref("oak_lab"), entryPoint = "door") }
        }
    }

    scene("battle_scene") {
        // Battle UI scene — managed by the battle system
        background {
            tileset = ref("battle_tiles")
            tilemap = asset("maps/battle_bg.tmx")
        }

        // Battle system takes over rendering and input in this scene
        activateSystem("battle")
    }

    startScene = ref("pallet_town")
}
```

### DSL Design Principles

1. **`ref()` is a typed, compile-time-validated reference.** If the target doesn't exist, the build fails with a clear error.
2. **`asset()` is a path to a raw file.** The asset pipeline converts it. The user never runs manual conversion tools.
3. **State storage is declared, not managed.** `persistent` → SRAM (survives power off). `sceneLocal` → reset on scene load. `global` → persists across scenes in WRAM. The compiler maps these to specific RAM addresses.
4. **No banking syntax anywhere.** The compiler decides all bank assignments.
5. **`system()` blocks define reusable game systems** — battle engines, dialog, inventory, save/load — that are shared across scenes. The compiler allocates them to appropriate banks and generates their lifecycle code.
6. **`onUpdate {}` blocks compile to a restricted instruction set (`ScriptOp`)**, not arbitrary Kotlin. The compiler validates no heap allocation, no recursion, no floats, no unbounded loops. If you need raw power, use `inlineC {}`.

---

## The Semantic IR

The IR is the heart of gbkt. It is NOT a C AST. It is a **Game Boy domain model** that the compiler can reason about exhaustively.

### Key Design Decisions

**Closed instruction set (ScriptOp):** Every game behavior is expressed as a tree of `ScriptOp` nodes — movement, dialog, branching, state mutation, battle triggers, math. Because the set is sealed, every compiler pass can handle every case. No `else` branches, no silent fallthrough.

**Platform annotations are nullable:** `TilesetIR.bankAssignment`, `SceneIR.vramLayout`, `ActorIR.oamSlot` — all nullable, all `null` until the corresponding analysis pass fills them in. This means the IR can be constructed (by `:dsl`) and tested (by `:test-runner`) without any platform-specific analysis.

**Systems are first-class IR nodes:** A battle engine isn't just "some code in a scene." It's a `SystemIR` with declared state, lifecycle hooks, a dependency on specific assets, and known RAM usage. The compiler can bank-allocate it, compute its memory footprint, and wire its lifecycle into the scene manager.

**Multiplatform readiness:** The IR represents concepts like "tileset", "sprite", "scene", "palette" — not "VRAM address 0x8000" or "bank pragma 5." A future GBA backend would read the same IR and emit ARM C code instead of GBDK C code. Platform-specific fields (bank slots, VRAM ranges) are in a separate annotation layer, not baked into the core types.

### IR Modules Breakdown

```
:ir/
├── core/           GameIR, CartridgeConfig, Refs
├── assets/         TilesetIR, SpriteSheetIR, TilemapIR, MusicIR
├── scene/          SceneIR, BackgroundLayerIR, TriggerIR
├── actor/          ActorDefinitionIR, ActorInstanceIR, BehaviorIR
├── script/         ScriptOp (sealed), Expr (sealed), VarRef
├── system/         SystemIR (dialog, battle, inventory, save, physics)
├── state/          GlobalStateIR, SaveStructureIR, RAMLayout
└── platform/       BankSlot, VRAMRange, OAMSlot (annotation types)
```

---

## Compiler Passes

Each pass is a pure function `GameIR → GameIR`. They are developed independently and composed in sequence.

### Pass 1: Validation
- All `ref()` targets resolve
- No circular scene transitions that would deadlock
- Actor sprite sizes match hardware (8x8 or 8x16)
- State variable types fit GB registers (uint8, uint16, int8, int16, bool)
- ScriptOp blocks contain only supported operations
- Palette color counts ≤ 4 (DMG) or per-palette limits (GBC)
- Tilemap dimensions are multiples of 8
- System declarations are complete (battle system has a damage formula, etc.)

### Pass 2: Asset Processing
- PNG → 2bpp tile data (DMG) or indexed color (GBC)
- Tile deduplication across tilesets (hash-based)
- Sprite sheet slicing into frames
- Tilemap parsing (Tiled XML / LDtk JSON)
- Music conversion to target driver format
- SFX processing

### Pass 3: Bank Allocation
The key value proposition. Algorithm:
- Bank 0: core engine (scene manager, main loop, interrupt handlers, frequently-called utilities)
- Scene code grouped by transition locality (scenes that transition to each other share a bank when possible)
- Asset data bin-packed using first-fit-decreasing
- Cross-bank calls detected, trampolines generated automatically
- **Error** if total exceeds configured ROM size, with per-bank breakdown

### Pass 4: VRAM Planning
- Per-scene tile slot assignment (which tiles in block 0 vs block 1)
- Shared tile detection across scene transitions (keep loaded to reduce flicker)
- GBC: VRAM bank 0 vs bank 1 assignment
- **Error**: "Scene 'pallet_town' requires 412 unique tiles (max 384). Consider splitting tileset or enabling tile deduplication."

### Pass 5: OAM Planning
- Per-scene sprite slot allocation (40 max)
- Per-scanline density analysis (10 max per scanline)
- Priority ordering
- **Error/Warning**: sprite overflow, flicker prediction

### Pass 6: RAM Planning
- WRAM variable layout (global state, scene-local, actor state)
- HRAM allocation (performance-critical variables)
- SRAM layout (save data structure, checksum)
- Stack size estimation
- **Error** if RAM budget exceeded

### Pass 7: Scene Transition Planning
- Per-transition delta: tiles to unload, tiles to load
- Bank switching sequence
- VRAM transfer scheduling (spread across VBlanks to avoid tearing)
- Fade effects

### Pass 8: Optimization
- Dead asset elimination (declared but unreferenced)
- Duplicate tile merging
- Constant folding in ScriptOp expressions
- Unreachable scene detection
- Small function inlining

### Pass 9: Budget Audit (Final Gate)
The safety net. Produces a human-readable build report:

```
=== gbkt Build Report: Monster Quest ===

ROM: 98 / 128 banks (76.6%)
  Bank 0:   14,231 / 16,384 bytes (core engine)
  Banks 1-8:   scene code (8 scenes)
  Banks 9-98:  assets (tilesets, sprites, maps, music, monster data)
  Banks 99-128: FREE

WRAM:  3,412 / 4,096 bytes
HRAM:  52 / 127 bytes
SRAM:  2,048 / 8,192 bytes (save slot)

Scene Budgets:
  pallet_town:   287 / 384 tiles ✓   12 / 40 sprites ✓
  route1:        341 / 384 tiles ✓   18 / 40 sprites ✓
  oak_lab:       156 / 384 tiles ✓    6 / 40 sprites ✓
  battle_scene:  384 / 384 tiles ⚠   22 / 40 sprites ✓
                 ↑ AT LIMIT — no room for dynamic tiles

Scanline Analysis:
  ⚠ battle_scene: scanline 72 has 11 sprites (max 10), expect flicker

Transition Costs:
  pallet_town → route1:      182 tiles to swap (1 VBlank)
  pallet_town → oak_lab:     298 tiles to swap (2 VBlanks, brief fade)
  any → battle_scene:        384 tiles full reload (screen flash covers it)

Errors: 0
Warnings: 2
```

**Hard failures** with actionable messages. **Warnings** for things that will work but might have visible artifacts.

---

## Asset Pipeline

Integrated into Gradle. Runs before compilation. The user drops files into `assets/` and the framework handles everything.

### Supported Formats

| Input | Tool | Output |
|-------|------|--------|
| `.png` sprites | Built-in Kotlin processor | `SpriteSheetIR` with 2bpp tile data, frame metadata |
| `.png` tilesets | Built-in Kotlin processor | `TilesetIR` with deduplicated tiles, palette mapping |
| `.tmx` (Tiled) | Built-in parser | `TilemapIR` with tile indices, collision layers, object layers |
| `.ldtk` (LDtk) | Built-in parser | Alternative to Tiled, same IR output |
| `.uge` (hUGEtracker) | Wrapper around hUGEDriver | `MusicTrackIR` with bank assignment |
| `.json` data files | Schema-validated parser | `DataTableIR` for monster databases, item lists, etc. |
| `.fnt` / custom fonts | Glyph extractor | `FontIR` for dialog system |

### PNG Processing Pipeline

```
Input PNG
    │
    ├── Color quantization (reduce to 4 colors / palette)
    ├── 8x8 tile slicing
    ├── Tile hashing (SHA-256 per tile)
    ├── Deduplication (identical tiles across all tilesets → share)
    ├── Flipped variant detection (H-flip, V-flip → reuse with flags)
    ├── 2bpp encoding (DMG) or indexed color encoding (GBC)
    └── Metadata: tile count, unique count, palette mapping
```

**IDE integration goal:** if the user's PNG has more unique tiles than will fit in VRAM for the scenes that reference it, the IntelliJ plugin underlines the `asset()` call in red before they even build.

---

## Code Generation

Codegen is a **structured emitter**, not a template engine. It reads the fully-annotated IR and produces a C AST, which is then pretty-printed to strings.

### Why Structured Codegen Matters

With `line("UINT8 x = 5;")` (the old approach), the compiler cannot:
- Optimize: merge adjacent variable declarations
- Validate: check that `x` is used before overwritten
- Refactor: rename a variable across usages
- Debug: map a C line back to the Kotlin DSL source
- Retarget: emit ARM instead of Z80 without rewriting everything

With `CStatement.VarDecl(CType.UINT8, "x", CExpr.Literal(5))`, the compiler can do all of these.

### C AST (lives in :codegen, NOT in :ir)

```kotlin
sealed interface CStatement {
    data class VarDecl(val type: CType, val name: String, val init: CExpr?) : CStatement
    data class Assignment(val target: CExpr, val value: CExpr) : CStatement
    data class If(val cond: CExpr, val then: List<CStatement>, val els: List<CStatement>) : CStatement
    data class While(val cond: CExpr, val body: List<CStatement>) : CStatement
    data class Switch(val expr: CExpr, val cases: List<SwitchCase>) : CStatement
    data class Call(val func: String, val args: List<CExpr>) : CStatement
    data class Return(val expr: CExpr?) : CStatement
    data class InlineC(val code: String) : CStatement    // escape hatch
    data class InlineAsm(val asm: String) : CStatement   // escape hatch
    data class Comment(val text: String) : CStatement
    data class BankPragma(val bank: Int) : CStatement
    // ...
}
```

The pretty-printer (`CStatement → String`) is the **only place** in the entire codebase where C strings are assembled. One file, one responsibility, easily testable.

### Multiplatform Codegen Path

When future targets are added:

```
:codegen-gb      ← GameIR → GBDK C (exists today)
:codegen-gba     ← GameIR → libtonc / libgba C (future)
:codegen-snes    ← GameIR → cc65 C or ca65 ASM (future)
```

Each codegen module has its own sealed C/ASM AST. The `:ir` and `:analysis` layers don't change.

---

## JVM Test Runner

**The killer DX feature.** Game logic runs on the JVM for testing — no emulator, no ROM, millisecond feedback.

### How It Works

`ScriptOp` is platform-independent. The same logic that compiles to C for the Game Boy can be interpreted on the JVM in a simulated game environment.

```kotlin
class PalletTownTest {

    @Test
    fun `player can talk to rival`() {
        val sim = TestGame.load(pokemon).startScene("pallet_town")

        sim.moveActorTo("player", Position(118, 64))  // walk to rival
        sim.pressButton(Button.A)

        assertThat(sim.dialogHistory).contains("Hey! I'm your rival!")
    }

    @Test
    fun `wild encounter triggers in grass`() {
        val sim = TestGame.load(pokemon).startScene("pallet_town")
        sim.setRandomSeed(42)

        // Walk into grass area
        sim.moveActorTo("player", Position(64, 24))

        assertThat(sim.currentScene).isEqualTo("battle_scene")
        assertThat(sim.battleState.wildMonster.species).isIn("Pidgey", "Rattata", "Pikachu")
    }

    @Test
    fun `mom heals party after getting starter`() {
        val sim = TestGame.load(pokemon).startScene("pallet_town")
        sim.flags["got_starter"] = true
        sim.party[0].currentHp = 5

        sim.moveActorTo("player", Position(38, 88))
        sim.pressButton(Button.A)

        assertThat(sim.party[0].currentHp).isEqualTo(sim.party[0].maxHp)
    }

    @Test
    fun `damage formula matches expected values`() {
        val battle = TestBattle.create(pokemon)
        val attacker = battle.createMonster("Charmander", level = 10)
        val defender = battle.createMonster("Bulbasaur", level = 10)
        val move = battle.getMove("Ember")

        val damage = battle.calculateDamage(attacker, defender, move)

        // Super effective fire vs grass
        assertThat(damage).isBetween(18, 24)
    }
}
```

### What Tests on JVM vs What Requires Emulator

| JVM Tests ✓ | Emulator Required ✗ |
|---|---|
| Game logic, state machines | Visual rendering, sprite appearance |
| Dialog trees, branching | Actual frame timing |
| Scene transitions, entry points | Hardware-specific bugs (HALT, DMA) |
| Battle system, damage calc | Audio playback |
| Inventory management | LCD STAT timing tricks |
| Save/load state integrity | Scanline effects |
| NPC behavior scripts | Performance under load |
| Collision triggers | |
| Encounter rate distribution | |

---

## Gradle Plugin

The user's entire workflow goes through Gradle.

### User's `build.gradle.kts`

```kotlin
plugins {
    id("dev.gbkt") version "1.0.0"
}

gbkt {
    gbdk {
        version = "4.3.0"  // auto-downloads if not installed
    }

    target {
        platform = Platform.GBC_COMPATIBLE
        mbc = MBC.MBC5
        romSize = ROM_4Mbit
        ramSize = RAM_32KB
    }

    emulator {
        use = Emulator.EMULICIOUS
        // or BGB, SameBoy — auto-detected from PATH
    }
}

dependencies {
    // gbkt standard library: scene manager, input, core systems
    implementation("dev.gbkt:stdlib:1.0.0")

    // optional system libraries
    implementation("dev.gbkt:dialog-engine:1.0.0")
    implementation("dev.gbkt:battle-engine:1.0.0")
    implementation("dev.gbkt:save-manager:1.0.0")
    implementation("dev.gbkt:physics-platformer:1.0.0")

    // test on JVM
    testImplementation("dev.gbkt:test-runner:1.0.0")
    testImplementation(kotlin("test"))
}
```

### Tasks

```bash
gradle build          # full pipeline → .gb ROM
gradle run            # build + launch in emulator
gradle test           # game logic tests on JVM (fast!)
gradle budgetReport   # just run analysis, print budget report
gradle assets         # just process assets
gradle generateC      # emit C code without compiling ROM (for inspection)
gradle clean          # remove generated C and build artifacts
gradle init           # scaffold a new gbkt project (interactive)
```

### Build Task Graph

```
:processAssets          PNG/TMX/UGE → processed binary data
    ↓
:compileDsl             Kotlin DSL → GameIR
    ↓
:analyzeIR              9 compiler passes → fully annotated GameIR
    ↓
:generateC              GameIR → .c/.h files
    ↓
:compileRom             GBDK lcc → .o → .gb ROM
    ↓
:run                    (optional) launch in emulator
```

---

## IntelliJ Plugin

Real-time feedback without running a build. Developed iteratively.

### Phase A: Inspections (Compile-Time Errors in the IDE)

- 🔴 **"Tileset over budget"** on `tileset()` if the PNG exceeds VRAM capacity for its scenes
- 🔴 **"Missing asset"** on `asset("sprites/hero.png")` if file doesn't exist
- 🔴 **"Unresolved reference"** on `ref("nonexistent")` — immediate red underline
- 🟡 **"Scene sprite overflow"** on `scene()` if spawned actors exceed 40
- 🟡 **"Scanline density"** warning when actors cluster horizontally
- ℹ️ **"Unreachable scene"** — gray out scenes no transition targets

### Phase B: Gutter Icons & Quick Info

- 🎮 next to `scene()` — click to preview tilemap layout
- 🖼 next to `spriteSheet()` — hover to see animation frames
- 📊 next to `gbktGame()` — click for inline budget report
- 🏦 next to assets — shows bank assignment after analysis

### Phase C: Live Preview (Stretch)

Side panel rendering the scene from IR — tile placement, actor positions, trigger zones. No emulator, just the data visualized. Click an actor to see its state machine. Click a trigger to see where it transitions.

---

## Standard Library & Optional Engines

gbkt ships as a layered set of libraries. The user picks what they need.

### `dev.gbkt:stdlib` (always required)

- Scene manager (lifecycle: init, update, teardown, transition)
- Input handling (joypad read, buffered press detection)
- Sprite manager (OAM buffer, DMA scheduling)
- Tile loading (VRAM transfer, bank switching)
- Palette management
- Timer / frame counter
- Random number generator
- Fixed-point math utilities (for physics, damage calc, etc.)

### `dev.gbkt:dialog-engine` (optional)

- Text rendering with variable-width or fixed-width fonts
- Dialog box with customizable appearance
- Text speed settings
- Choice menus
- Portrait display
- Text variables ("You found {item.name}!")
- Auto-advance and input-wait modes

### `dev.gbkt:battle-engine` (optional)

- Turn-based 1v1 and 2v2 battle framework
- Pluggable damage formula
- Status effects with duration tracking
- Experience and leveling
- Monster/party data management
- Battle UI scaffolding (HP bars, menus)
- Wild encounter and trainer battle modes

### `dev.gbkt:save-manager` (optional)

- SRAM read/write with checksum validation
- Multiple save slots
- Auto-save support
- Data migration between versions (for ROM updates)
- Corruption detection and recovery

### `dev.gbkt:physics-platformer` (optional)

- Gravity, acceleration, friction
- AABB collision detection
- Tile-based collision maps
- Moving platforms
- Slopes (45°)
- Jump buffering and coyote time

### Future Libraries

- `dev.gbkt:physics-topdown` — top-down movement, grid-based or free
- `dev.gbkt:multiplayer-link` — link cable communication protocol
- `dev.gbkt:cutscene-engine` — scripted camera movements, cinematic sequences
- `dev.gbkt:menu-engine` — hierarchical menu system, settings screens

---

## Project Structure (User's Game)

```
my-game/
├── build.gradle.kts              # gbkt plugin, dependencies, config
├── settings.gradle.kts
├── assets/
│   ├── sprites/
│   │   ├── player.png
│   │   └── enemies/
│   ├── tiles/
│   │   ├── overworld.png
│   │   └── interior.png
│   ├── maps/
│   │   ├── pallet_town.tmx
│   │   └── oak_lab.tmx
│   ├── music/
│   │   ├── title.uge
│   │   └── battle.uge
│   ├── data/
│   │   ├── monsters.json
│   │   ├── moves.json
│   │   └── items.json
│   └── fonts/
│       └── main_8x8.png
├── src/
│   ├── main/kotlin/
│   │   ├── Game.kt               # gbktGame { } definition
│   │   ├── scenes/
│   │   │   ├── PalletTown.kt
│   │   │   ├── Route1.kt
│   │   │   └── BattleScene.kt
│   │   ├── actors/
│   │   │   ├── Player.kt
│   │   │   └── NPCs.kt
│   │   ├── systems/
│   │   │   ├── Battle.kt
│   │   │   └── Encounters.kt
│   │   └── data/
│   │       └── MonsterTypes.kt   # enum/sealed class for type chart
│   └── test/kotlin/
│       ├── BattleTest.kt
│       ├── PalletTownTest.kt
│       └── EncounterTest.kt
├── build/
│   ├── generated/c/              # compiler output (gitignored)
│   └── rom/
│       └── my-game.gb            # final ROM
└── .gitignore
```

---

## Phased Roadmap

### Phase 1: Semantic IR (4-6 weeks)
Define all IR node types. Build the `:ir` module as a standalone zero-dependency Kotlin library. Validate that the IR can represent Tetris, a platformer, and a Pokémon-scale RPG.

**Deliverable:** `:ir` module that compiles independently. Unit tests for IR construction and validation.

### Phase 2: Structured Codegen (4-6 weeks)
Build `:codegen` with the C AST hierarchy and pretty-printer. Port one existing game from the old `line("")` approach to IR → C AST → string. Verify ROM output is identical.

**Deliverable:** `:codegen` module. At least one game compiles through the new pipeline.

### Phase 3: Asset Pipeline (3-4 weeks)
Build `:assets` module. PNG→tiles, Tiled→tilemaps, sprite sheet slicing. Integrate into Gradle as a task.

**Deliverable:** `gradle processAssets` works. Users drop PNGs and TMX files into `assets/` and get processed data.

### Phase 4: Bank Allocator + Analysis Passes (4-6 weeks)
Build `:analysis` module. Implement validation, bank allocation (bin-packing), VRAM planning, OAM planning, budget audit. This is the core compiler intelligence.

**Deliverable:** `gradle budgetReport` produces the build report shown above. Bank allocation is fully automatic.

### Phase 5: DSL Redesign (4-6 weeks)
Build `:dsl` module with the final declarative API shown in this document. Scene, actor, system declarations. Ref resolution. Asset references.

**Deliverable:** A user can write a game in the new DSL and compile it to a ROM.

### Phase 6: Test Runner (2-4 weeks)
Build `:test-runner` module. JVM interpreter for ScriptOp. Simulated game environment. Test API.

**Deliverable:** `gradle test` runs game logic tests on JVM in under 5 seconds.

### Phase 7: Gradle Plugin (2-3 weeks)
Package everything as `dev.gbkt` Gradle plugin. Publish to Gradle Plugin Portal. Auto-download GBDK. `gradle init` project scaffolding.

**Deliverable:** A new user can start a gbkt project with one `plugins {}` block.

### Phase 8: Standard Library + Engines (ongoing)
Build stdlib, dialog engine, battle engine, save manager, platformer physics. Each is a separate library published to Maven Central.

**Deliverable:** Libraries that make the Pokémon-scale and Mario-scale examples actually work.

### Phase 9: IntelliJ Plugin (ongoing)
Inspections first, gutter icons second, live preview as stretch goal.

**Deliverable:** IDE catches tile budget overflows and missing assets before the user hits build.

### Phase 10: Multiplatform (future)
Add `:codegen-gba` backend. Potentially rename to RetroKt or RetroCompose.

---

## Risk Assessment

### Solvable Challenges

| Challenge | Mitigation |
|-----------|-----------|
| ScriptOp too restrictive for advanced use cases | `inlineC {}` and `inlineAsm {}` escape hatches |
| Bank allocator produces suboptimal layouts | Bin-packing is well-studied (FFD); allow manual override hints |
| Tiled/LDtk format changes | Pin supported versions, thin adapter layer |
| Performance of generated C vs hand-written | Budget audit catches issues; escape hatches for hot paths |
| IR needs to evolve as more passes are built | Expected — plan for 2-3 IR revision cycles in Phase 1-4 |

### Genuine Risks

| Risk | Assessment |
|------|-----------|
| **GB constraints always leak** | True at edges. Goal: cover 80-90% declaratively. Power users get escape hatches. |
| **Scope creep into "game engine"** | Clear line: gbkt provides scene management, asset loading, and build tooling. Genre-specific logic (battle systems, physics) ships as optional libraries. |
| **Community adoption requires docs + examples** | Budget time for a tutorial game, API docs, and example projects alongside each phase. |
| **Single developer bandwidth** | Phases deliver standalone value. An MVP (Phase 1-5) that just does banking + asset pipeline + budget reporting is already worth releasing. |

---

## Success Criteria

gbkt is successful when:

1. **A new developer** can `gradle init`, drop PNGs and a Tiled map into `assets/`, write a scene in Kotlin, run `gradle build`, and get a `.gb` ROM — without ever seeing a bank pragma, VRAM address, or C code.

2. **A compile error** like *"Scene 'dungeon' uses 401 unique tiles (max 384). Consider splitting tileset 'dungeon_walls'."* replaces a mysterious graphical glitch discovered 2 hours into emulator debugging.

3. **Game logic tests** run in under 5 seconds on JVM with `gradle test`.

4. **A Pokémon-scale RPG** (20+ scenes, battle system, save data, 100+ monsters) can be built entirely in Kotlin using gbkt's DSL and optional engine libraries.

5. **An experienced GBDK developer** looks at the generated C code and says "yeah, that's what I would have written."

6. **The Game Boy homebrew community** adopts gbkt because it makes Game Boy development feel as productive as modern app development — without sacrificing the charm and constraints that make the platform special.

---

*This is the north star. Ship Phase 1-5 as the MVP. Everything else is incremental. Every phase delivers standalone value.*

---

## Current State & Rebuild Context

The current codebase was built as a greenfield prototype. To stress-test the framework, a full port of **Labyrinth of the Dragon** (an existing Game Boy C game) was attempted using the Kotlin DSL. This port exposed fundamental architectural problems:

1. **Game-specific coupling:** The codegen and DSL became tightly coupled to LabyrinthOfTheDragon's specific structure rather than being generic.
2. **String-based codegen:** The `line("")` approach cannot be optimized, analyzed, or retargeted. No structured C AST exists.
3. **No compiler intelligence:** VRAM planning, bank allocation, and OAM management are all manual. The framework should handle these automatically — like a JVM garbage collector handles heap memory, the user should never think about it.
4. **Packages too intertwined:** Module boundaries are blurred. Code that should be in separate modules (IR, DSL, analysis, codegen) is tangled together.

**The rebuild approach:** Incremental, phased refactoring — not a fresh start. Decouple packages, rebuild the IR as the clean contract, introduce structured codegen with a C AST, and add analysis passes for automatic resource management. Converge toward the vision architecture described above.

**LabyrinthOfTheDragon and LabyrinthOfTheDragon-port** remain in the repo as the ultimate integration test. They'll move to a separate repo when the framework is mature.

---

## Core Value

The framework automatically manages Game Boy hardware resources (VRAM, banking, OAM, RAM) so the developer writes only declarative Kotlin DSL — like Jetpack Compose for Game Boy.

## Current State

**v0.1.0 MVP shipped 2026-06-09** — the compiler pipeline rebuild is complete and released. The string-concatenating prototype is replaced by a layered pipeline (Kotlin DSL → non-sealed IR + visitor dispatch → 9 ordered analysis passes → structured C AST → GBDK C), validated end-to-end against four GBDK SDK reference examples. The full JVM test suite is green (`./gradlew test --continue` + `./gradlew pluginTest`, 0 failures) as a hard release gate, reached diagnose-first with zero threshold-weakening. 20-module architecture with ServiceLoader genre plugins (RPG, platformer, puzzle, sport), an embedded Coffee-GB emulator, JVM test runner, and an MCP server for agent-driven UAT.

See `.planning/MILESTONES.md` and `.planning/milestones/v0.1.0-ROADMAP.md` for the full record.

## Current Milestone: v0.1.1 Hardening

**Goal:** Drain the v0.1.0 deferred-debt backlog — every seed gets a terminal disposition (fixed, verified-closed, or explicitly re-routed), the docs tell the truth, and static-analysis debt is burned down.

**Progress:** Phase 16 (Seed Triage) complete 2026-06-12 — all 47 entries (44 seeds + 3 folded todos) terminally dispositioned against substrate SHA `8cef3dbc`: 24 verified-already-fixed (archived), 10 re-deferred to v0.2.0 backlog, 10 confirmed-open remaining in `.planning/seeds/` as the live fix queue for Phases 19–21. D-08 binding visual review passed (10 verdicts human-locked). Phase 19 (Codegen Fixes — Metasprite Cluster) complete 2026-06-13 — FIX-01 closed with fresh GBC-mode HEAD screenshots (SEED-004/005/006/013 + ROM-smoke; SEED-006/013 confirmed cyan sub-palette) captured via a committed UAT scaffold against a clean build; FIX-02 discharged as a 1:1 seed→guard audit with all 5 emission guards GREEN. Byte-identity oracle CLEAN (no codegen drift), D-08 commit separation intact, verifier 4/4. Phase 22 (Golden Screenshot & Evidence Storage Overhaul — FIX-07, the final v0.1.1 phase) complete 2026-06-15 — replaced the per-phase `EVIDENCE_DIR` pattern (27 test classes) with central immutable ROM+anchor-keyed goldens that tests DIFF against (capture to gitignored `build/` scratch), gitignored + untracked the 143 archived-phase evidence files, dropped `capturedAt` sidecar churn, added GBC auto-detect from ROM byte 0x143, and migrated 22 binding goldens (6 metasprites + 16 platformer) byte-identically from Phase 19/20/21. Verifier 10/10; full suite green with empty git delta; binding USER visual sign-off on the cyan-elephant + platformer goldens. Code review fixed 5 Warnings (incl. a broken `-Pgbkt.updateGoldens` bless path); the regression gate caught and fixed a metasprites DMG-build gap (same class as the platformer 22-07 fix) so its golden tests now execute against a real GBC ROM.

**Target features:**
- Seed triage & closure — all 44 seeds end fixed, closed-with-evidence, or re-deferred with an explicit v0.2.0 disposition; `.planning/seeds/` is empty at milestone close. Stale status hints (written before Phases 12.6–13.8 shipped) must be re-verified against current master, not trusted.
- Deprecation removals pulled forward — SEED-023 (whenever/runIf unification) and SEED-025 (remove deprecated combat String overload) land in v0.1.1
- DSL_REFERENCE.md reconciliation — prune/rewrite the 13 dead-API sections so docs match the implemented DSL; each removed subsystem becomes a tracked v0.2.0 feature candidate; apply the 2 doc-only fixes
- QUAL-01..03 cleanup — detekt violations, platform-aware screen constants, magic-pixel elimination (the deferred Phase 08 scope)
- Sonar S3776 burn-down — clear the 46 cognitive-complexity HIGH findings

## Requirements

### Validated

- ✓ Decouple intertwined packages into clean module boundaries (IR, DSL, analysis, codegen) — v0.1.0
- ✓ Rebuild IR as platform-agnostic semantic game model (non-sealed interfaces + visitor dispatch, nullable platform annotations) — v0.1.0
- ✓ Replace string-based codegen with structured C AST emission — v0.1.0
- ✓ Automatic bank allocation (FFD bin-packing, scene locality, trampoline generation) — v0.1.0
- ✓ VRAM planning (per-scene tile slot assignment, shared tile detection) — v0.1.0
- ✓ OAM planning (sprite slot allocation, scanline density analysis) — v0.1.0
- ✓ RAM planning (WRAM layout, HRAM allocation, SRAM structure) — v0.1.0
- ✓ Budget audit pass (human-readable build report with actionable errors) — v0.1.0
- ✓ Example games compile through the restructured pipeline (7 KEEP examples; Explorer retired in Phase 14) — v0.1.0
- ✓ Asset pipeline integrated into Gradle (PNG → tiles, TMX/LDtk → tilemaps, sprite slicing, hUGETracker music) — v0.1.0
- ✓ JVM test runner for game logic (ScriptOp interpreter, simulated environment) — v0.1.0
- ✓ Framework codegen correctness validated against 4 GBDK SDK reference examples (simple_physics → metasprites → banks → platformer_template) via the Phases 9–13 reference-port track with binding visual UAT — v0.1.0

### Active (v0.1.1)

- [ ] Seed triage & closure — every seed fixed, verified-closed with evidence, or re-deferred with an explicit v0.2.0 disposition; seeds directory empty at close
- [ ] Deprecation removals — SEED-023 (whenever/runIf unification), SEED-025 (remove deprecated combat String overload)
- [ ] DSL_REFERENCE.md reconciliation — prune/rewrite the 13 dead-API sections; removed subsystems tracked as v0.2.0 feature candidates
- [ ] QUAL-01..03 — detekt violations, platform-aware screen constants, magic-pixel elimination (deferred Phase 08 scope)
- [ ] Sonar S3776 burn-down — 46 cognitive-complexity HIGH findings

### Out of Scope

- Multiplatform backends (GBA, SNES, NES) — architecture supports it but not this milestone
- IntelliJ plugin live preview — stretch goal for later
- New game ports — focus on framework correctness, not new game content
- Community docs / tutorials — premature until architecture stabilizes
- Link cable multiplayer support — future library
- Implementing the 13 documented-but-absent DSL subsystems (state machines, dialog/menu property APIs, save fields, entity-pool lifecycle, tweening, camera extras, physics property API, pathfinding, battle menus, items) — v0.1.1 makes docs match reality; implementation is v0.2.0+ feature work
- Big-ticket seeds deferred from v0.1.1: SEED-RAW-C-CODEGEN-AST-MIGRATION (own architecture phase), SEED-PHASE-X-CPAREN (~50+ fixture re-snapshots), SEED-019/024 (IntelliJ test infra), SEED-001 (IDE features, v2.0 trigger)
- SEED-018 RPG character codegen extern/decl mismatch — stays dormant with the archived dungeon/explorer games
- Genre-codegen phases 07.5–07.8 and IDE-04 — wait for their own milestone

## Constraints

- **Tech stack**: Kotlin 2.3.0, Gradle 9.0, JVM 21, GBDK-2020 for compilation
- **Backward compatibility**: None required — breaking changes are acceptable during rebuild
- **No constraints on timeline**: Quality over speed

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Incremental refactoring over fresh start | Preserve working test infrastructure and example games | ✓ Good — shipped v0.1.0 without losing the example/test corpus |
| IR as the central contract between all modules | Exhaustive matching + platform-agnostic design enables future backends | ✓ Good — non-sealed interfaces + visitor dispatch enabled the 20-module split and genre plugins |
| Structured C AST over string-based codegen | Enables optimization passes, source mapping, validation, and future retargeting | ✓ Good — ~150 CRawCode hatches eliminated; source maps + dedup passes built on it |
| Automatic resource management (VRAM, banking, OAM) | Core value proposition — the "GC for Game Boy hardware" | ✓ Good — 9 analysis passes ship with budget audit as the final gate |
| Non-sealed IR + visitor pattern (V2) over sealed `when` | Sealed interfaces forced all IR into one module; visitors enable the multi-module split | ✓ Good — genre packages extend IR without modifying core |
| Full-green suite as the v0.1.0 release gate | A cleanup release must leave a tree that works end-to-end; a red suite is unacceptable | ✓ Good — Phase 15 drove 18 red tests green diagnose-first, zero threshold-weakening |
| Keep LabyrinthOfTheDragon in-repo for now | Ultimate integration test; move to separate repo when mature | ⚠️ Revisit — RPG-port buildRom debt (SEED-018) deferred; reassess next milestone |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-06-15 — Phase 22 (Golden Screenshot & Evidence Storage Overhaul — FIX-07) complete: central immutable ROM+anchor goldens replace per-phase EVIDENCE_DIR, GBC auto-detect from ROM 0x143, 22 goldens migrated byte-identically, verifier 10/10, clean-tree gate green, binding USER visual sign-off. Final v0.1.1 phase.*

*Prior: 2026-06-12 — Phase 16 (Seed Triage) complete: 47/47 dispositions final, TRIAGE.md canonical, 10 confirmed-open seeds queued for Phases 19–21.*

*Prior: 2026-06-09 after v0.1.0 milestone — MVP Compiler Pipeline Rebuild shipped (66 phases, 652 plans, 887 tasks; full-green release gate satisfied via Phase 15). Prior: 2026-06-03 after Phase 13.2 (framework primitives — delegate ergonomics + variable/control-flow) completion — 7/7 plans shipped, verifier 6/6 success criteria passed. Phase 13.2 removed the delegate-ceremony tax and the hand-rolled variable/frame-logic patterns: a uniform single-use `delegateUsed` guard across all five delegate types with one `@file:Suppress` per example file replacing 18 per-site suppresses (Req #12 + carried-in WR-06); `runIf`/`unless`/`orElse` single-frame conditional aliases over `IfOp` (Req #2); `i16FixedVar`/`toPixel`/`subpixel` fixed-point sub-pixel abstraction (Req #3); `easeToZero` decay primitive (Req #8); and `u8Var(wrapAt = N)` declarative wrap with mask vs compare-reset emission (Req #9). Four audited example ports migrated; full `:gbkt-lang:test` 292/0; SimplePhysics D-12 byte-identical codegen oracle GREEN; D-18 ROM sweep 8/8 buildRom EXIT 0 (pong PASS*). Code review surfaced 1 BLOCKER in the phase's own new code — fixed in-phase: CR-01 (i16FixedVar `fractionalBits` not flowing to `toPixel`, RED→GREEN, commits e24fc345/c205be1b). Five advisory items filed as backlog todos: W1–W4 (wrapAt=0, wrapAt-decrement asymmetry, orElse-after-wrap-guard, easeToZero by>1) and a pre-existing metasprites byte-identity baseline staleness (Phase 12.8, not a 13.2 regression). Resume target: Phase 13 parent (next decimal is 13.3 — metasprite, sprite & color). Advisory items still open from 13.1-REVIEW.md: WR-01, WR-03, WR-07.*

*Prior: 2026-06-03 after Phase 12.11 (platformer level-2 gameplay-zone near-blank render in UAT harness) completion — 4/4 plans shipped, verifier 5/5 must-haves passed. Phase 12.11 closed the two entangled anchor-5 defects: Failure A (card→gameplay level-2 switch never completing, `_current_level` stuck at 0) root-caused to a frame-boundary VBlank collision — the main-loop level-switch guard called `nextLevelScene_enter()` every frame, leaving the ROM paused mid-enter so START was never registered — fixed by extending `buildMainLoopLevelSwitchGuardIfNeeded` with `&& current_scene != SCENE_NEXTLEVELSCENE`; Failure B (level-2 BG near-blank, 0.983 dominant ratio) fixed by wrapping `setup_current_level()`'s per-zone VRAM writes in `DISPLAY_OFF`/`DISPLAY_ON` in `buildSetupCurrentLevelFunctionIfNeeded`. Both edits gated by `gameUsesTilemapCollision` (7-target ROM sweep byte-identical, pong PASS*). `anchor5LevelSwitch()` re-armed (no @Disabled, live `assertScreenshotIsNonUniform`), passing 3/3 with binding PNG `evidence/anchor-5/03-level-2.png`. RED→GREEN JVM guard `SetupCurrentLevelDisplayGateEmissionTest` added. Phase 12 itself was SHIPPED earlier (2026-06-02 via Phase 12.9, 28/28 plans, `phase.complete 12` invoked). Resume target: Phase 13 (framework primitives surfaced by example ports). Advisory code-review items carried (12.11-REVIEW.md): WR-01 stale KDoc on `buildMainLoopLevelSwitchGuardIfNeeded`, IN-01 dead `Disabled` import, IN-02 no JVM guard for the Failure-A guard condition (covered by the re-armed UAT).*

*Prior: 2026-05-19 after Phase 10.1 (metasprites-surplus-codegen-defects-inserted) completion — 22/22 plans shipped, 12/12 must-haves verified.*