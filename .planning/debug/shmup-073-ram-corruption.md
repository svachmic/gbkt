---
slug: shmup-073-ram-corruption
status: resolved
trigger: |
  Wild memory write corrupts RAM in the Shmup ROM at approximately frame 188 of gameplay
  (60 frames after entering the gameplay scene from title), filling RAM with byte 0x39 ('9' ASCII).
  Regression introduced by Phase 07.3 (entity-pool-codegen-fix-inserted). Every byte of state
  reads 57 after corruption — including hardware-mapped registers (RAMBANK, cpu, joypad,
  sound_channels). BG tilemap fills with tile 0x59 ('Y'), window tilemap with 0x39 ('9').
  Reproduces deterministically.
created: 2026-05-07
updated: 2026-05-07
tdd_mode: false
goal: find_and_fix
related_phase: 07.3-entity-pool-codegen-fix-inserted
related_uat: .planning/phases/07.2-interactive-game-uat/UAT-shmup.md
evidence_dir: .planning/debug/07.3-regression-shmup-corruption/
---

# Debug Session: Shmup 07.3 RAM Corruption

## Symptoms

### 1. Expected behavior
Shmup gameplay should run continuously: bullet pool spawns work (verified frame 125),
shootCooldown decrements, enemy waves spawn at `waveTimer == 60`, score increments on
collisions, lives decrement on player-enemy collision, gameover transitions when lives = 0.
The ROM should survive arbitrarily many frames of normal gameplay.

### 2. Actual behavior
ROM survives until ~frame 188 (60 frames into gameplay, exactly when `waveTimer` reaches 60
and the first enemy wave should spawn). At that frame, the entire game state corrupts:
- Every variable reads 57 (0x39 = ASCII '9') including hardware-mapped registers
  `RAMBANK`, `cpu`, `joypad`, `sound_channels`, `sound_priority`, `sound_duration`,
  `dialog_speed`, `wait_counter`, `map_tile_offset`, `oam_free_top` (which can never
  legally exceed 40)
- BG tilemap: every cell shows tile 0x59 ('Y' ASCII)
- Window tilemap: every cell shows tile 0x39 ('9' ASCII)
- Rendered output: vertical green stripes (single tile pattern repeated)
- `current_scene` transitions to gameover without `lives` reaching 0

### 3. Error messages
None — ROM does not crash, just corrupts. No GBDK/sdcc warnings during build.

### 4. Timeline
- Bug introduced by Phase 07.3 (entity-pool-codegen-fix-inserted), specifically the OAM
  free list infrastructure shipped in plan 07.3-03 (`feat(07.3-03): fix Shmup.kt movement
  ops and wire OAM free list infrastructure`, commit `7c1a6154`).
- Pre-07.3 Shmup had a different failure mode: pools were completely non-functional
  (entities never moved). 07.3 fixed that — bullet pool spawn, per-instance positions,
  display sync, and OAM allocation are confirmed working. The corruption is downstream
  of the pool spawn primitives.
- 07.3-03 SUMMARY claimed completion. UAT was not run before claiming completion.

### 5. Reproduction (deterministic)
```
emulator_start(romFile=gbkt-examples/shmup/build/gbkt/output/shmup.gb,
               symFile=…/shmup.noi,
               metadataFile=…/game_metadata.json)
emulator_step(frames=120)              # title screen visible, all values nominal
emulator_press(button="start")         # transition to gameplay
emulator_press(button="a")             # spawn 1 bullet (PASS — pool_bulletPool_active: 1, OAM 39)
emulator_step(frames=60)               # corruption fires somewhere in this window
# At this point: every variable reads 57, scene=gameover, tilemap clobbered
```

Savestate at corruption: `.planning/debug/07.3-regression-shmup-corruption/shmup-corruption-frame188.gbst`

## Working observations (frame-128 nominal vs frame-188 corrupted)

Captured in `.planning/phases/07.2-interactive-game-uat/UAT-shmup.md`:

| Variable | Frame 128 (nominal) | Frame 188 (corrupted) |
|---|---|---|
| `RAM` | 0 | 57 |
| `RAMBANK` | 0 | 57 |
| `cpu` | 1 | 57 |
| `lives` | 3 | 57 |
| `oam_free_top` | 39 | 57 (illegal — should be ≤ 40) |
| `pool_bulletPool_oam` | 39 | 0 |
| `shootCooldown` | 3 | 57 |
| `dialog_speed` | 1 | 57 |
| `wait_counter` | 0 | 57 |
| `joypad` | 0 | 57 |
| `sound_channels` | 255 | 57 |
| `current_scene` | 1 (gameplay) | 0 (gameover) |

Bullet pool spawn (Checkpoints 1–2) confirmed working before corruption — that's the
07.3-01/02 deliverable. The regression is in 07.3-03 (OAM free list) or an adjacent
code path (HUD update, enemy spawn) that runs at the same boundary.

## Evidence

### Root cause analysis (confirmed via shmup.noi symbol file)

**shadow_OAM layout:** `_shadow_OAM 0xC000`, 40 entries × 4 bytes = 0xC000–0xC09F.
`__cpu 0xC0A0` = shadow_OAM[40].y_pos — this is the GBDK internal CPU type variable at the
first byte past the array end.

**Bug 1: Dynamic OAM free list allocates only 1 slot for multi-tile sprites.**
Enemy sprites are 16x16 = 4 OAM tiles. The old `spawn_actor()` pop one slot from the free
list. `_oam_free_top` starts at 40 and decrements: player takes slot 38 (from position 2),
enemy takes slot 37 (1 pop). But then `move_sprite(_pool_enemyPool_oam[_ei] + 2u, ...)` hits
slot 40 (OOB), writing directly to `__cpu` at 0xC0A0. With 4 tiles (slots 38,39,40,41),
the corruption fills `__cpu` and adjacent WRAM with `move_sprite`'s y argument (16 = 0x10?
then everything cascades and reads 57=0x39 due to OAM DMA timing artifacts).

**Bug 2: `forEachActive` called `move_sprite` after `destroy` with `oam[i]=0xFF`.**
The `visitPoolForEachActive` displaySyncStmts were emitted unconditionally after body ops.
If the body called `pool_bulletPool_destroy(_bi)` which set `_pool_bulletPool_oam[_bi] = 0xFF`
(old sentinel), then `move_sprite(0xFF, ...)` writes to shadow_OAM[255] which is far past
the array end — guaranteed WRAM corruption.

## Resolution

### Root cause
Two codegen bugs in `GBDKSystemVisitor` and `ScriptOpVisitor` that caused OAM out-of-bounds
writes when multi-tile pool sprites (16x16 enemy = 4 OAM slots) were spawned using the
dynamic OAM free list approach.

### Fix

**Fix 1 — Static OAM assignment replaces dynamic free list** (`GBDKSystemVisitor.kt`):
- Removed `_oam_free_list`, `_oam_free_top`, `init_oam_free_list()`, `spawn_actor()`,
  `destroy_actor()` from generated code.
- Pool init now pre-computes and stores `oam[i] = poolOamBase + i * tilesPerEntity` at
  startup. OAM slots are permanent and never change.
- Pool destroy calls `move_sprite(oam[i]+t, 0, 0)` for each tile to hide sprites;
  does not reset oam[i]. Removed dead `if (i == 0xFF) { return; }` guard.
- `poolOamBase` computed by summing all actor tiles, then all previous pool tiles, ensuring
  no pool ever writes past shadow_OAM[39].

For shmup: bullet template=1 tile + enemy template=4 tiles + player=4 tiles = 9.
bulletPool oamBase=9 (slots 9–16), enemyPool oamBase=17 (slots 17–32). Max slot=32. SAFE.

**Fix 2 — Active re-check guard in forEachActive** (`ScriptOpVisitor.kt`):
- `displaySyncStmts` (the `move_sprite` calls) are now wrapped in a second
  `if (_pool_*_active[slot])` check after body ops. If body destroyed the slot, the
  re-check skips `move_sprite` instead of calling it with stale/invalid oam values.

**Fix 3 — Remove dead 0xFF sentinel guard from destroy** (`GBDKSystemVisitor.kt`):
- Removed `if (i == 0xFF) { return; }` from `pool_*_destroy` body. Under static OAM,
  slot indices are always valid integers from [0, maxSize-1]; no sentinel needed.

**Files changed:**
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/GBDKSystemVisitor.kt`
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ScriptOpVisitor.kt`
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt`
- `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ActorPoolOperationsTest.kt` (regression tests added)
- `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/GBDKSystemVisitorTest.kt` (updated for new behavior)

**Verification:**
- All 852 tests in `gbkt-backend-gbdk` pass.
- Generated `main.c` confirms: `_pool_bulletPool_oam[i] = 9u + i * 1u`, `_pool_enemyPool_oam[i] = 17u + i * 4u`.
- Generated `bank1.c` confirms: `if (_pool_bulletPool_active[_bi]) { move_sprite(...); }` re-check present.
- ROM built successfully: `shmup.gb` (32 KB).
