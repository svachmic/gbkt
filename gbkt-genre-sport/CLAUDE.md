# gbkt-genre-sport -- Sport Genre Plugin

Provides sport-game DSL constructs and GBDK code generation for three sub-genres: racing (tracks, vehicles, AI, laps), ball sports (field, ball physics, goals, scoring), and tournaments (brackets, standings, match progression).

## Dependencies
- **Depends on:** `gbkt-core`, `gbkt-backend-api`, `gbkt-backend-gbdk`, `gbkt-engine`
- **Used by:** `gbkt-backend-gbdk` (via ServiceLoader)

## Structure
- `domain/` -- Type definitions across four files: `SportTypes.kt`, `RacingTypes.kt`, `BallSportTypes.kt`, `TournamentTypes.kt`
- `dsl/` -- Builder DSL (`SportBuilders.kt`) and extension functions (`SportExtensions.kt`)
- `codegen/` -- GBDK visitor (`SportVisitor.kt`) for C generation

## Key Types -- Racing
| Type | Role |
|------|------|
| `RacingConfig` | Top-level racing config: mode, laps, track, vehicles, AI, pickups |
| `RacingMode` | Enum: racing variant (time-trial, head-to-head, etc.) |
| `TrackDef` | Track layout with waypoints and lap count |
| `WaypointDef` | Track waypoint with tile position and checkpoint flag |
| `VehicleDef` | Named vehicle with stats |
| `VehicleStats` | Speed, acceleration, handling |
| `RacingAIConfig` | AI speed percent, difficulty, rubber-banding |

## Key Types -- Ball Sports
| Type | Role |
|------|------|
| `BallSportConfig` | Field, ball physics, scoring rules, match structure, pickups |
| `FieldDef` | Playing field dimensions and optional goal config |
| `GoalConfig` | Goal width and height |
| `BallPhysicsConfig` | Ball speed, friction, bounce |
| `ScoringRules` | Points per goal, win condition, target score, time limit |
| `MatchStructure` | Halves, rounds to win, half duration |
| `WinCondition` | Enum: `SCORE_LIMIT`, `TIME_LIMIT`, etc. |

## Key Types -- Tournament
| Type | Role |
|------|------|
| `TournamentConfig` | Bracket type, participants, rounds per match, standings |
| `BracketType` | Enum: `SINGLE_ELIMINATION`, `ROUND_ROBIN`, etc. |
| `TournamentMatch` | Single match entry |
| `StandingEntry` | Participant standing with wins/losses |

## Key Types -- Shared
| Type | Role |
|------|------|
| `SportPickupDef` | Power-up pickup with type |
| `SportPickupType` | Enum: pickup variants (boost, shield, etc.) |

## DSL Extensions
`racing`, `ballSport`, `tournament` -- each takes a builder lambda to configure the respective sub-genre and registers the config on the active game.

## Codegen
`SportVisitor` dispatches to `visitRacing`, `visitBallSport`, and `visitTournament`, plus a shared `buildPickupResult` helper for power-up generation.

## Wall-Collision Sample (Plan 07.4-18)

`SportVisitor.buildPositionWriteBackWithCollision` uses a **4-corner OR-accept** rule introduced in Plan 07.4-18 to close the NAVIGABILITY-PLAYER-CORNER-TRAP gap.

### How it works

An actor move from `(carX, carY)` by `(vx, vy)` is accepted iff **at least one** of the sprite's four pixel corners (NW, NE, SW, SE of the bounding box) samples a non-WALL tile AND lies within map bounds:

```
accepted = (cornerNW || cornerNE || cornerSW || cornerSE)
where cornerXY = (col < mapWidth && row < mapHeight && tiles[row*mapWidth+col] != 0u)
      col = (proposedCornerX >> 3), row = (proposedCornerY >> 3)
```

The outer INT16 bounds check (Plan 07.4-12) is preserved byte-for-byte; only the inner single-center tile-sample chain is replaced by `buildFourCornerWallSampleAccept`.

### Key properties

- **D-09 (uniform physics path):** Both the player write-back and the AI per-instance write-back call `buildPositionWriteBackWithCollision`, so both benefit automatically.
- **D-17 (wall impassability):** A move where all 4 corners land on WALL tiles (tile==0) is still rejected — solid walls remain impassable.
- **D-04 (no magic strings):** The zone id flows into the emitted C via the `zoneId` parameter; no hardcoded fixture names exist in the helper.
- **Tile semantics (TrackSynthesizer, Plan 07.4-04):** `tile==0` = wall, `tile==1` = drivable corridor, `tile==2` = grass (also drivable). The `!= 0u` condition correctly treats both 1 and 2 as passable.

### Emitted C shape

```c
// INT16 outer bounds check (Plan 07.4-12)
INT16 propXs = (INT16)carX + vx;
INT16 propYs = (INT16)carY + vy;
if (propXs >= 0 && propXs < maxX && propYs >= 0 && propYs < maxY) {
    UINT8 propX = (UINT8)propXs;
    UINT8 propY = (UINT8)propYs;
    // 4-corner OR-accept (Plan 07.4-18)
    if (((propX >> 3) < 19u && (propY >> 3) < 19u && _zone_track1_tiles[...] != 0u)
     || ((propX+7u) >> 3 < 19u && ...  != 0u)
     || (... sw corner ... != 0u)
     || (... se corner ... != 0u)) {
        carX = propX;
        carY = propY;
    }
}
```

### Testing

`RacingPlayerTraversabilityTest` verifies corner-trap freedom and full-loop navigability using a pure-Kotlin simulation with `anyCornerDrivable` mirroring the production helper. `RacingCollisionGuardTest` verifies the emitted C contains `!= 0u`, `INT16`, `>> 3)`, and at least 8 tile-array samples (4 corners × player + AI).

## Scene-Aware clear() and print() Contract (Plan 07.4-20)

The `racing { }` block populates a scene's enter ops with a BG-paint splice
(`set_bkg_data` + `set_bkg_tiles` for the synthesized track). Earlier the user-authored
`enter { clear(); print("LAP:", ...) }` would lower to `cls()` + `gotoxy()/printf()` and
wipe the freshly-painted BG (Plan 07.4-19/20 closure). The fix lives in TWO places:

### Part 1 — DSL routing (gbkt-lang)

`ScriptBuilder.clear()` now emits `ScreenClear` IR (one-line change in
`gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ScriptBuilder.kt:432`). Previously
it emitted `RawOp("cls();")` directly, bypassing the codegen visitor entirely. Routing
through `ScreenClear` gives the backend a hook to make scene-aware decisions.

### Part 2 — Scene-aware codegen (gbkt-backend-gbdk)

`ScriptOpVisitor.visitScreenClear` reads scene context (set per-scene by
`GBDKPipeline.buildSceneFile` via `setSceneContext`) and lowers `ScreenClear`
differently:

| Scene shape                                      | Lowering                                    |
|--------------------------------------------------|---------------------------------------------|
| Has BG tilemap (tilesetRef OR genre BG splice)   | `HIDE_SPRITES; _win_clear_region(0,0,20,18);` (non-destructive — preserves BG) |
| No BG tilemap (title, results, gameover)         | `cls()` (back-compat — wipes everything as before) |

`ScriptOpVisitor.visitPrintOp` is also scene-aware:

| Scene shape                                      | Lowering                                    |
|--------------------------------------------------|---------------------------------------------|
| Has BG tilemap                                   | `_win_print_at(x, y, "text", len)` (window layer — preserves BG) |
| No BG tilemap                                    | `gotoxy(x, y); printf("text", ...)` (back-compat — preserves existing 5 example games' title screens byte-for-byte) |

The back-compat decision (scene-aware vs. universal `_win_print_at`) was REVISED during
Plan 07.4-20 revision 1: scene-aware was chosen over universal swap to avoid cross-ROM
regression risk across 5 non-racer example games. See Plan 20 SUMMARY for full rationale.

### What this means for `racing { }` authors

DSL authors write `clear()` and `print()` exactly as in any other scene. The codegen
handles the layer choice. There is no "use `_win_*` in racing scenes" rule for DSL
authors to remember.

### What this means for `gbkt-genre-sport` maintainers

The discriminator predicate `sceneHasBgTilemap(sceneId, gameIR, genreEnterOps)` lives in
`GBDKPipeline`. It detects a BG tilemap via:

1. `scene.tilesetRef != null` — scene declared a tileset.
2. Genre `enterOps` contain a `RawOp` with `set_bkg_tiles` or `set_bkg_data` —
   SportVisitor's enterOps splice triggers this branch.

If `racing { }` ever stops emitting `set_bkg_tiles` as a `RawOp` (e.g., switches to a
structured `LoadBackgroundTilemap` ScriptOp), update the predicate to detect the new
shape — otherwise `clear()` will silently start wiping the BG again. JVM-tier locks in
`gbkt-backend-gbdk/src/test/kotlin/.../ScreenClearSceneAwareTest` and
`PrintOpSceneAwareTest` will surface the regression.

### Why this matters (lessons learned)

Plan 07.4-14's verification trusted the variable assignment `_current_tileset_id = 1u`
as proof that "the track tilemap is rendered". That variable is set by the genre splice
moments before `cls()` wipes the painted tilemap — the variable evidence was correct;
the visual outcome was not. Five plans (15-18) built atop a verified state that did not
match runtime reality. Plans 19-20 closed the bug; this section codifies the contract so
it cannot regress.

See: `.planning/debug/racer-bg-tilemap-not-rendered.md`,
`.planning/phases/07.4-sport-genre-codegen-fix-inserted/07.4-UAT.md`.

## Testing
```bash
./gradlew :gbkt-genre-sport:test
```
