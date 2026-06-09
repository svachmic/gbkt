---
game: shmup
status: passed-07.3-verified
tester: claude-agent
session_date: 2026-05-07
prior_session_date: 2026-03-25
rom: gbkt-examples/shmup/build/gbkt/output/shmup.gb
metadata: gbkt-examples/shmup/build/gbkt/generated/game_metadata.json
symbols: gbkt-examples/shmup/build/gbkt/output/shmup.noi
verdict_reason: 07.3-fix-verified-against-deterministic-repro
fix_commit: 191c8f4c
related_debug_session: .planning/debug/shmup-073-ram-corruption.md
---

# UAT Report: Shmup — fast-pass 07.3-fix verification (post-fix)

## Result: PASS — all five 07.3 fix checkpoints verified. RAM corruption gone. Two minor gameplay polish findings logged for backlog (not 07.3-related).

This is a re-test after the `/gsd-debug` session resolved the RAM corruption regression. Phase 07.3 (entity-pool-codegen-fix-inserted) can now genuinely be considered complete — the fix in commit `191c8f4c` resolves the deterministic repro that the prior session of this report documented.

## What changed in the fix

The debug session (`.planning/debug/shmup-073-ram-corruption.md`) found two cooperating bugs in pool codegen and replaced the dynamic OAM free list with a static OAM layout:

1. **Dynamic OAM free list allocated 1 slot per entity** regardless of sprite tile count. 16×16 enemy sprites need 4 OAM slots; the free list popped only 1, so `move_sprite(oam[i] + 2, …)` and `+ 3` ran into shadow_OAM[40] = `__cpu` at 0xC0A0, then cascaded WRAM corruption.
2. **`forEachActive` called `move_sprite` unconditionally after destroy** — if the body destroyed an instance and set `oam[i] = 0xFF` (old sentinel), the trailing display-sync emitted `move_sprite(0xFF, …)` writing to shadow_OAM[255], far past the array. Fix: re-check `active[i]` between body and display-sync.

The static layout pre-computes `pool.oam[i] = poolOamBase + i * tilesPerEntity` at init, so slots are permanent and bounded. For Shmup: bullet template (1 tile) + enemy (4) + player (4) = 9; bulletPool oamBase = 9, enemyPool oamBase = 17. Max slot used = 32, well within the 40-slot shadow_OAM. The `oam_free_top` / `oam_free_list` globals are gone from the metadata entirely.

## Verification — same 5 checkpoints as the FAIL run

| # | Checkpoint | Status | Evidence |
|---|---|---|---|
| 1 | Bullet pool spawn (A press) | ✅ PASS | `pool_bulletPool_active`: 0→1; bullet sprite at OAM index 9 (was 39 dynamic), flying up (y: 116 → 104 → 100 → 0) |
| 2 | `shootCooldown` decrement gate | ✅ PASS | shootCooldown 8 → 6 → 3 → 0 across 5 frames |
| 3 | Enemy pool spawn at `waveTimer == 60` | ✅ **PASS — no corruption** | `pool_enemyPool_active`: 0→1; enemy sprite at OAM 17-20 (4 tiles for 16×16); all RAM/registers nominal at frame 188 (the previous corruption frame) |
| 4 | Pool-pool collision (bullet × enemy) | ✅ PASS | `score`: 0 → 50 from collision; HUD updated to "SC:50 LV:3"; no corruption |
| 5 | `destroyAll` on scene re-entry | ✅ PASS | gameover → title → gameplay: `pool_enemyPool_active`: 1 → 0; `scrollY: 164 → 0`; `waveTimer: 44 → 0`; `score/lives` reset; new spawn cycle starts cleanly |

### Frame-188 nominal state (post-fix)

| Variable | Pre-fix (frame 188) | Post-fix (frame 188) |
|---|---|---|
| `RAM` | 57 | **0** |
| `RAMBANK` | 57 | **0** |
| `cpu` | 57 | **1** |
| `lives` | 57 | **3** |
| `pool_bulletPool_oam` | 0 | **9 (static)** |
| `pool_enemyPool_oam` | (unset) | **17 (static)** |
| `oam_free_top` | 57 (illegal) | (variable removed entirely) |
| `shootCooldown` | 57 | **0** |
| `dialog_speed` | 57 | **1** |
| `joypad` | 57 | **0** |
| `sound_channels` | 57 | **255** |
| `current_scene` | 0 (gameover, forced) | **1 (gameplay, valid)** |
| BG tilemap | filled with 'Y' (0x59) | clean — only "SC:0 LV:3" HUD |
| Win tilemap | filled with '9' (0x39) | clean — empty dots |

Continued play past frame 497 (122 frames into a new gameplay cycle, well beyond the original failure point) shows the game running stably — multiple enemy spawns at OAM 17-20 and 21-24, bullet spawns/destroys at OAM 9, no corruption.

## Goldens

| Label | Path | Frame | Content |
|---|---|---|---|
| shmup-title | `gbkt-examples/shmup/src/test/resources/golden/shmup-title.png` | 120 | Title screen with "SHMUP", "SHOOT-EM-UP", "PRESS START" |
| shmup-gameplay | `gbkt-examples/shmup/src/test/resources/golden/shmup-gameplay.png` | 497 | Gameplay with player + 2 enemies (OAM 17-24), HUD "SC:0 LV:3", 122 frames into new cycle (post-corruption-frame, post-destroyAll) |
| shmup-gameover | `gbkt-examples/shmup/src/test/resources/golden/shmup-gameover.png` | 297 | Gameover with "GAME OVER", "SCORE: 50", "PRESS START" |

Note: the gameplay golden was captured well past the original corruption point and after one full gameover→title→gameplay cycle, so it serves as proof the fix is stable across scene transitions, not just the first 60 frames.

## Minor follow-up findings (NOT 07.3-related — for backlog)

These are gameplay polish issues that exist independently of the 07.3 fix. Documenting here so the user can decide scope: fix inline in this session, push to a follow-up plan, or leave for later UAT passes.

### F-A. Pool-pool collision lacks debounce / destroy-on-hit — RESOLVED 2026-05-08
Original symptom: one bullet through one enemy yielded `score = 50` (5 increments × 10) because the bullet kept registering hits on every frame the AABBs overlapped, with no destroy and no debounce. Resolved by introducing a typed `whenever(poolA.collides(poolB)) { idxA, idxB -> ... }` overload that surfaces the codegen's outer/inner loop indices to the user lambda as `PoolIterator` handles. The user calls `destroy(pool, idx)` against the colliding instances; the bullet+enemy vanish in one frame, score increments by exactly 10. No magic strings — lambda parameter names are user-chosen Kotlin convenience; the DSL maps the typed handles to the auto-named `_pool_<short>i` C variables that codegen already emits.

Verified via re-run: `score: 0 → 10 → 20` per kill (was 0 → 50 → 100 with debounce-bug); both pools deactivate immediately on hit; ROM stable across multiple kill cycles.

DSL form now used in `Shmup.kt:195`:
```kotlin
whenever(bulletPool.collides(enemyPool)) { bi, ei ->
    score += 10
    destroy(bulletPool, bi.toExpr())
    destroy(enemyPool, ei.toExpr())
    playSound(explodeSfx)
}
```

Codegen test added: `GenericPoolCodegenTest.both-pool-template collision body can call destroy on each pool with auto-named slot vars`.

### F-B. `destroyAll` clears `active` flags but doesn't move sprites off-screen
After `destroyAll(enemyPool)` on gameplay enter, `pool_enemyPool_active: 0` but the enemy OAM slots (17-24) keep their last-known positions until a new spawn overwrites them. Visible momentarily on scene re-entry. The pool-instance `destroy` now hides via `move_sprite(oam[i]+t, 0, 0)` (per the 07.3-fix description), but `destroyAll` likely zeroes the active array without iterating to call per-instance hide. **Severity: low — visual flicker for ~1 frame on re-entry.** Easy fix in `GBDKSystemVisitor` `destroyAll` codegen: emit a loop that calls per-instance `destroy` for each active slot.

### F-C. GBC palette init still missing (carried from Racer UAT)
Not exercised this session because we tested in DMG mode. Tracked separately under ROADMAP Phase 07.7 (GBC Palette Initialization).

## Sign-off

**Shmup PASSES UAT post-07.3 fix.** The deterministic repro that found the regression now produces clean gameplay end-to-end. Phase 07.3 (entity-pool-codegen-fix-inserted) is genuinely complete. Plan 07.2-02 task 2 is verified — but the plan as a whole still cannot close because Racer (task 1) FAILS on the racing() DSL decoration (separate phase 07.4). Two minor gameplay polish findings (F-A, F-B) logged for backlog; F-C deferred to phase 07.7.
