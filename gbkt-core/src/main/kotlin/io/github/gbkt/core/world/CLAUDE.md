# World Module

Zones (dungeon floors, overworld regions), random encounters, global flags, and map objects for games.

## Files

| File | Purpose | LOC |
|------|---------|-----|
| `Zone.kt` | Generic zone/area definitions | ~665 |
| `Floor.kt` | Legacy dungeon floor definitions (deprecated) | ~624 |
| `FloorToZoneAdapter.kt` | Floor→Zone conversion | ~124 |
| `Encounter.kt` | Random encounter tables | ~200 |
| `Flags.kt` | Global boolean flags for game state | ~250 |
| `MapObject.kt` | Chests, NPCs, doors, etc. | ~200 |

## Quick Reference

### Zone Definition (Recommended)
```kotlin
val floor1 by zone {
    type(ZoneType.DUNGEON)  // DUNGEON, OVERWORLD, SIDE_SCROLLING, ARENA, ROOM
    name("Dungeon Level 1")
    defaultPosition(5, 5)
    map("entrance") {
        tileset("dungeon.png")
        size(32, 32)
    }
    objects {
        chest("chest1") { position(10, 5); contains(potion) }
        npc("elder") { position(15, 8); name("Elder") }
    }
    encounters {
        safeSteps(10)  // Steps before encounters can occur
        entry(weight = 30) { +goblin }
        entry(weight = 20) { +goblin; +goblin }
        entry(weight = 10) { +goblin; +kobold; +kobold }
    }
}
```

### Floor Definition (Deprecated)
```kotlin
// @Deprecated - Use zone { type(ZoneType.DUNGEON) } instead
val floor1 by floor {
    name("Dungeon Level 1")
    defaultPosition(5, 5)
    map("entrance") {
        tileset("dungeon.png")
        size(32, 32)
    }
    encounters { ... }
}
```

### Encounter Table
```kotlin
encounters {
    // Weighted random selection
    entry(weight = 50) { +slime }           // 50% chance
    entry(weight = 30) { +slime; +slime }   // 30% chance
    entry(weight = 20) { +goblin }          // 20% chance
}
```

### Global Flags
```kotlin
val flags by flags {
    page("story") {
        flag("metElder")
        flag("hasKey")
        flag("defeatedBoss")
    }
    page("chests") {
        flag("chest1Opened")
        flag("chest2Opened")
        // ... up to 8 flags per page
    }
}

// Usage
flags.story.metElder set true
whenever(flags.story.hasKey) {
    openDoor()
}
```

### Map Objects
```kotlin
// Inside zone {} builder
objects {
    chest("chest1") {
        position(10, 5)
        flag(0)  // Flag index for open state
        contains(potion, quantity = 2)
        locked(true, keyItem = "magic_key", consumeKey = true)
        onOpen { showMessage("Found 2 Potions!") }
    }

    npc("elder") {
        position(15, 8)
        name("Elder")
        facing(Direction.DOWN)
        onInteract {
            gameFlags.getFlag("metElder")?.set()
            showMessage("Welcome, adventurer!")
        }
    }

    savePoint("save1") {
        position(8, 5)
        healsParty(true)
    }

    sconce("sconce1") {
        position(12, 12)
        startsLit(true)
        lightRadius(3)
        onLit { refillTorch(100) }
    }
}

// Zone exits
exits {
    stairsUp(from = "main" at (5 x 5), to = previousFloor at (28 x 28))
    door(from = "main" at (15 x 10), to = "secret" atDest (0 x 5))
}
```

## Flag System

Flags are organized in pages (8 flags per page):

| Page | Purpose |
|------|---------|
| 0-26 | Game-specific flags |
| 27-28 | Chest open flags |
| 29-30 | Chest locked flags |
| 31 | Test/debug flags |

```kotlin
// 256 total flags (32 pages × 8 bits)
flags.page("story").flag("event1")  // Sets bit in page
```

## Encounter Mechanics

1. **Safe Steps** - Minimum steps before encounters possible
2. **Step Counter** - Counts movement in exploration
3. **Weight System** - Probabilistic encounter selection
4. **Danger Level** - Optional modifier for encounter rate

```kotlin
encounters {
    safeSteps(10)           // 10 steps immunity
    dangerMultiplier(1.5f)  // 50% more frequent

    // Entries with weights (total doesn't need to be 100)
    entry(weight = 30) { +goblin }
    entry(weight = 20) { +goblin; +kobold }
}
```

## Map Object Types

| Type | Purpose |
|------|---------|
| `chest` | Loot containers (locked/unlocked) |
| `npc` | Interactive characters |
| `stairs` | Floor transitions |
| `sconce` | Torch refill points |
| `door` | Locked passages |
| `savePoint` | Save/restore locations |
| `trigger` | Invisible event triggers |

## Key Types

- `Zone` - Interface for all zone types
- `GenericZone` - Standard zone implementation
- `ZoneType` - Zone category (DUNGEON, OVERWORLD, etc.)
- `ZoneConnection` - Connection between zones
- `ZoneMap` - Map area within a zone
- `EncounterTable` - Random encounter configuration
- `FlagRef` - Reference to single flag
- `FlagPageRef` - Reference to flag page
- `MapObject` - Interactive map objects (chests, NPCs, etc.)

## Zone Types

| Type | Description |
|------|-------------|
| `DUNGEON` | Multi-floor dungeon with stairs/elevators |
| `OVERWORLD` | Open world map with regions |
| `SIDE_SCROLLING` | Horizontal levels (platformers) |
| `ARENA` | Single combat zone |
| `ROOM` | Single screen room |

## Connection Types

| Type | Description |
|------|-------------|
| `WALK` | Standard transition (walk into edge) |
| `DOOR` | Requires interaction |
| `WARP` | Instant teleport |
| `VERTICAL` | Stairs, ladder, elevator |
| `AUTO` | Triggered by event |
| `SECRET` | Hidden passage |

## Related Modules

- `codegen/world/` - World code generation (ZoneCodegen, FloorCodegen)
- `ir/FlagsIR.kt` - Flag-related IR nodes
- `exploration/Exploration.kt` - Exploration controller
- `rpg/Monster.kt` - Monster definitions for encounters

## Migration from Floor to Zone

The `floor {}` DSL is deprecated. To migrate:

```kotlin
// Before (deprecated)
val floor1 by floor {
    name("Dungeon Level 1")
    defaultPosition(5, 5)
    map("main") { tileset("dungeon.png"); size(32, 32) }
}

// After (recommended)
val floor1 by zone {
    type(ZoneType.DUNGEON)
    name("Dungeon Level 1")
    defaultPosition(5, 5)
    map("main") { tileset("dungeon.png"); size(32, 32) }
}
```

Key changes:
- `floor {}` → `zone { type(ZoneType.DUNGEON) }`
- `Floor` type → `GenericZone` type
- `startFloor(floor)` → `startZone(zone)` in exploration
