# gbkt Road to Release

Outstanding work for gbkt v1.0 release.

---

## Phase 1: Developer Experience & Onboarding

Adoption barriers.

### Documentation
- [ ] 1.1 Add Quick Start tutorial to main README (5-minute Hello World)

### Tooling
- [ ] 1.2 Docker Compose for development
- [ ] 1.3 CLI bundle templates (minimal, platformer, rpg, puzzle)

---

## Phase 2: Multi-Target Architecture (Major Restructure)

**Goal:** Modularize gbkt for multiple codegen targets beyond Game Boy.

### Core Separation
- [ ] 2.1 Extract target-agnostic DSL layer (`gbkt-dsl`)
- [ ] 2.2 Extract target-agnostic IR layer (`gbkt-ir`)
- [ ] 2.3 Create codegen interface/contract for targets

### Target Modules
- [ ] 2.4 `gbkt-target-gb` - Game Boy / GBC (GBDK)
- [ ] 2.5 `gbkt-target-gba` - Game Boy Advance (future)
- [ ] 2.6 `gbkt-target-ds` - Nintendo DS (future)

### Module Structure
```
gbkt/
├── gbkt-dsl/           # Target-agnostic DSL (Kotlin)
├── gbkt-ir/            # Target-agnostic IR nodes
├── gbkt-target-gb/     # GB/GBC codegen (GBDK C)
├── gbkt-target-gba/    # GBA codegen (future)
└── gbkt-gradle-plugin/ # Build orchestration
```

This enables writing games once and compiling for different Nintendo handhelds.

---

## Phase 3: Sample Games Extraction

**Goal:** Move sample games to separate repository before v1.0 release.

### Directories to Extract
- `LabyrinthOfTheDragon/` - Original C implementation
- `LabyrinthOfTheDragon-port/` - gbkt Kotlin port

### Tasks
- [ ] 3.1 Create `gbkt-examples` repository
- [ ] 3.2 Move sample game directories
- [ ] 3.3 Update main README with link to examples repo
- [ ] 3.4 Remove sample project includes from settings.gradle.kts

### Future Repository Structure
```
gbkt-examples/
├── README.md
├── labyrinth-original/     # Original C game
├── labyrinth-port/         # gbkt port
└── other-examples/         # Future examples
```

**Note:** Extraction happens after major architectural overhaul (Phase 2).

---

## Phase 4: Cleanup

- [ ] 4.1 Delete VSCode extension directory (IntelliJ is primary IDE)
- [ ] 4.2 Update documentation references

---

## Phase 5: Distribution

- [ ] 5.1 Publish Docker image to GitHub Container Registry
- [ ] 5.2 Generate SBOM in releases

---

## Phase 6: Publishing (Final Steps)

### Maven Central
- [ ] 6.1 GPG key generation
- [ ] 6.2 OSSRH (Sonatype) account
- [ ] 6.3 Gradle publishing config
- [ ] 6.4 POM metadata (license, developers, SCM)
- [ ] 6.5 Automated release workflow

---

## Deferred (Post-Release)

### Windows Support
- Add `mingwX64` target to CLI
- Reason: No Windows machine available for testing

### Documentation Site
- Set up documentation framework (Docusaurus/MkDocs)
- Tutorials: Getting Started, First Game walkthrough, Asset pipeline
- Generate API reference from KDoc
- Host example games with source

---

## Vision: gbkt as Complete Game Development Platform

The long-term vision for gbkt is to provide a **Spring Initializr-like experience** for Game Boy Color game development. This transforms gbkt from a DSL library into a complete, batteries-included game development platform.

### Phase 7: IntelliJ Project Generator

**Goal:** One-click project creation with everything needed to build and run a GBC game.

#### 7.1 Project Generation Wizard
- [ ] New Project wizard in IntelliJ: "New → Project → gbkt Game"
- [ ] Game name and package configuration
- [ ] Template selection (Minimal, RPG, Platformer, Puzzle)
- [ ] Feature checkboxes (Save System, Dialog, Physics, etc.)

#### 7.2 Generated Project Structure
```
my-game/
├── build.gradle.kts          # Pre-configured with gbkt plugin
├── settings.gradle.kts       # Project settings
├── gradlew / gradlew.bat     # Gradle wrapper (executable)
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
├── src/main/kotlin/
│   └── com/example/mygame/
│       ├── MyGame.kt         # Entry point (from template)
│       ├── rpg/              # (if RPG template)
│       ├── scenes/
│       └── world/
├── res/                      # Developer-friendly assets
│   ├── sprites/
│   │   └── player.png
│   ├── tilemaps/
│   │   └── level1.tmx        # Tiled editor format
│   ├── palettes/
│   │   └── main.gpl          # GIMP/Aseprite palette
│   ├── music/
│   └── sfx/
└── .gitignore
```

#### 7.3 Template Library
- [ ] **Minimal**: Single scene, one sprite, d-pad movement
- [ ] **RPG**: Character, battle system, inventory, save/load (Dragon port structure)
- [ ] **Platformer**: Physics, collision, scrolling camera
- [ ] **Puzzle**: Grid-based, state tracking, win conditions

---

### Phase 8: Asset Pipeline

**Goal:** Work with standard developer-friendly formats, auto-convert at build time.

#### 8.1 Sprite Processing
- [ ] PNG input with automatic palette validation (max 4 colors per palette)
- [ ] Auto-slicing to 8x8 or 8x16 tiles
- [ ] Sprite sheet detection and frame extraction
- [ ] Animation metadata from Aseprite JSON export
- [ ] Output: GBDK-compatible C arrays

#### 8.2 Tilemap Processing
- [ ] Tiled editor (.tmx/.json) import
- [ ] Layer separation (background, collision, objects)
- [ ] Tileset reference resolution
- [ ] Collision map generation from object layers
- [ ] Output: Tile indices + collision data

#### 8.3 Palette Management
- [ ] GIMP (.gpl) and Aseprite palette import
- [ ] Automatic GBC color conversion (RGB → 15-bit)
- [ ] Palette validation and optimization
- [ ] Shared palette detection across sprites

#### 8.4 Audio Pipeline
- [ ] MOD/XM tracker format → GBT Player conversion
- [ ] WAV → Sound effect conversion
- [ ] Automatic bank allocation for music

#### 8.5 Gradle Plugin Integration
```kotlin
// build.gradle.kts
plugins {
    id("io.github.gbkt") version "1.0.0"
}

gbkt {
    gameName = "MyGame"

    assets {
        sprites {
            sliceSize = 8 x 8
            maxColors = 4
            sourceDir = "res/sprites"
        }
        tilemaps {
            format = TilemapFormat.TILED
            sourceDir = "res/tilemaps"
        }
        audio {
            musicFormat = AudioFormat.GBT
            sourceDir = "res/music"
        }
    }

    output {
        romName = "mygame.gbc"
        generateSourceMap = true
    }
}
```

---

### Phase 9: IDE Integration Enhancements

#### 9.1 Live Preview
- [ ] Sprite preview in editor gutter
- [ ] Tilemap visual preview
- [ ] Palette color swatches inline
- [ ] Animation preview panel

#### 9.2 Emulator Integration
- [ ] One-click "Run in Emulator" (BGB, Emulicious, mGBA)
- [ ] Breakpoint synchronization with source maps
- [ ] Memory watch integration
- [ ] VRAM viewer integration

#### 9.3 Asset Editors
- [ ] Built-in sprite editor (4-color constrained)
- [ ] Palette editor with GBC color picker
- [ ] Tilemap editor (or deep Tiled integration)
- [ ] String/text editor with GBC font preview

---

### Success Criteria

A developer should be able to:
1. Install IntelliJ + gbkt plugin
2. Create new gbkt project (wizard)
3. Drop PNG sprites into `res/sprites/`
4. Write Kotlin DSL game logic
5. Click "Build ROM" → playable .gbc file
6. Click "Run" → opens in emulator

**No manual GBDK setup. No command-line builds. No asset conversion scripts.**

---

### Reference Implementation

The **Labyrinth of the Dragon** port serves as the reference implementation:
- Located at: `LabyrinthOfTheDragon-port/`
- Original C game: `LabyrinthOfTheDragon/`
- Status: **1:1 port complete** - All DSL definitions match original, encounter tables verified
- Demonstrates: Multi-file RPG structure, all DSL features
- Template source: RPG project template derives from this

**Current capabilities:**
- 4 character classes with 6 abilities each (24 total)
- 12 monster types with tier variants (C/B/A/S) matching original stat tables
- Turn-based combat with status effects and presentation callbacks
- Battle presentation: sound effects, damage numbers, screen shake
- Dungeon exploration with random encounters (all 8 floors verified)
- Character stats matching original Level 5 values from tables.c
- Encounter tables matching original weights and tier combinations
- Floor 8 scripted encounters (no random encounters, matching original)
- Save/load system structure
- Flag-based state tracking

**Assets Integrated:**
- All tile sheets (dungeon, battle, font, objects, world_map)
- Hero sprites
- All 12 monster sprites
- Title screen graphics

**Remaining for 100%:**
- Testing with GBDK compilation
- Emulator verification
