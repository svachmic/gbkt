# Phase 07.4 Deferred Items

Out-of-scope discoveries surfaced during plan execution. NOT fixed by the surfacing
plan; logged here for a future phase to triage.

---

## DEFERRED-07.4-20-01: dungeon + explorer ROMs fail to compile (RPG character extern definition mismatches)

**Surfaced during:** Plan 07.4-20 Task 3 (regression matrix —
`:gbkt-examples:dungeon:buildRom`, `:gbkt-examples:explorer:buildRom`)

**Reproduces at:** parent commit `f966cfda` (pre-Plan-20). Confirmed by checking out
the three Plan 20 source files at `f966cfda` and re-running both `buildRom` tasks
— same failure cascade for both games. Root cause is therefore NOT introduced by
Plan 07.4-20.

**Symptom (dungeon):**
```
build/gbkt/generated/main.c:60: error 91: extern definition for '_char_adventurer_hp' mismatches with declaration.
build/gbkt/generated/game.h:66: error 177: previously defined here
build/gbkt/generated/main.c:61: error 91: extern definition for '_char_adventurer_sp' mismatches with declaration.
... (continues for hp, sp, atk, def, matk, mdef, agl — 7 character stat slots)
```

**Symptom (explorer):**
```
build/gbkt/generated/main.c:82: error 91: extern definition for '_char_hero_hp' mismatches with declaration.
build/gbkt/generated/main.c:83: error 91: extern definition for '_char_hero_sp' mismatches with declaration.
... (same 7 stat slots, character id 'hero' instead of 'adventurer')
```

Both games use `gbkt-genre-rpg` `character { stats { hp(N); sp(N); atk(N); ... } }`.
Both fail with the same shape (extern in `game.h` vs definition in `main.c`,
mismatched types for the 7 standard stat slots).

**Likely cause (not investigated in this plan):** The character/stat globals declared
in `game.h` (extern, type X) and defined in `main.c` (definition, type Y) have
mismatched type signatures. This is in the RPG/character codegen path
(`gbkt-genre-rpg` + `RpgVisitor` in `gbkt-backend-gbdk`), unrelated to the
DSL/codegen surface that Plan 20 touches (ScriptBuilder.clear / visitScreenClear /
visitPrintOp / GBDKPipelineV2.buildSceneFile scene-context threading).

**Triage:**
- Open a follow-up plan in a future wave to diagnose the extern/definition mismatch
  for character stats in the dungeon AND explorer examples. Likely belongs in a
  phase that revisits RPG codegen or character system header generation.
- Plan 07.4-20's Task 3 verification SKIPS dungeon and explorer; the other 4
  example ROMs (racer, shmup, platformer, platformer-gbc) build successfully and
  exercise the scene-aware code path. Plan 20's scene-aware fix is locked at the
  JVM tier (Plans 19 + 20 RED→GREEN tests).
- Per executor scope-boundary: do NOT auto-fix unrelated pre-existing failures.

---

## DEFERRED-07.4-20-02: RacerEmulatorTest "driving steers and accelerates so camera_x changes" pre-existing failure

**Surfaced during:** Plan 07.4-20 Task 3 (regression matrix — `:gbkt-examples:racer:test`)

**Reproduces at:** parent commit `f966cfda` (pre-Plan-20). Confirmed by checking
out the three Plan 20 source files at `f966cfda` and re-running `:gbkt-examples:racer:test`
— same 1/8 failure: 35 tests completed, 1 failed (the same test name).

**Symptom:**
```
RacerEmulatorTest > driving steers and accelerates so camera_x changes() FAILED
    org.opentest4j.AssertionFailedError at RacerEmulatorTest.kt:120
    camera_x should change once the car translates east: start=248, end=248
```

The car never moves east in the emulator under the test's input sequence, so
`camera_x` stays at 248. This is a pre-existing emulator-tier bug in the racer
example (likely related to input timing, integration with the SportVisitor
movement codegen, or the camera follow logic) — it is NOT touched by Plan 20's
scene-aware DSL routing fix.

**Triage:**
- The other 7 RacerEmulatorTest cases pass at HEAD (camera target binding, lap
  counter, AI pool spawn, tileset id, ping-pong oscillation, held-UP wrap, dpad
  ramp). RacerGameTest, RacerIRTest, RacerStepAgentTest all 100% GREEN at HEAD.
- Open a follow-up plan in a future wave to diagnose the camera_x stuck-at-start
  failure under the test's driving sequence. Likely a Sport-genre input/movement
  integration issue.
- Plan 20 verification accepts 7/8 racer tests as the post-Plan-20 baseline,
  matching the pre-Plan-20 baseline byte-for-byte.

---

## DEFERRED-07.4-27-01: GAP-WIN-HELPER-UNINIT-LOOP-COUNTER — _win_* helpers leave loop counters uninitialised

**Status:** CLOSED 2026-05-12 — Plan 07.4-31 (fix commit follows RED commit bb7a9eef). See `.planning/phases/07.4-sport-genre-codegen-fix-inserted/07.4-31-SUMMARY.md` for full fix record.

**Remaining deferred (out of Plan 31 scope):** The second bug noted below — `_win_clear_region`'s inner loop never resets `rx` to 0 between outer iterations — remains DEFERRED. The inner-loop `rx` reset requires reshaping the for-loop body (adding a `CExprStatement` to reset `rx` before each inner-loop entry), which is a structural change beyond the one-line CVarDecl initializer fix. This bug means `_win_clear_region` only correctly clears the first row on repeated use. A separate future plan should address it.

**Surfaced during:** Plan 07.4-27 Task 2 H-E experiment (code inspection while
running the round-6 hypothesis matrix).

**File:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/DialogVisitor.kt`

**Affected functions:**
- `buildWinPrintAtHelper()` (line 179) — emits `UINT8 i;` then `for (; i < len; i++)`
- `buildWinClearRegionHelper()` (line 343) — emits `UINT8 ry; UINT8 rx;` then
  `for (; ry < h; ry++) { for (; rx < w; rx++) { ... } }`
- `buildWinFillScreenHelper()` (line 405) — emits `UINT8 fy; UINT8 fx;` then nested
  `for` loops with no initialiser

**Generated C (current, broken):**
```c
void _win_print_at(UINT8 x, UINT8 y, const UINT8* str, UINT8 len) {
    UINT8 i;                       // ← uninitialised
    for (; i < len; i++) {         // ← condition uses stack garbage
        set_win_tiles(x + i, y, 1u, 1u, (unsigned char*)&str[i]);
    }
}
```

**Expected (per the docstring at line 169-176):**
```c
void _win_print_at(UINT8 x, UINT8 y, const char* str, UINT8 len) {
    UINT8 i;
    for (i = 0; i < len; i++) {
        set_win_tiles(x + i, y, 1, 1, (unsigned char*)&str[i]);
    }
}
```

**Severity:** Real undefined-behaviour codegen bug. SDCC may pick a stack slot
that happens to be 0 (giving correct behaviour by luck on small fixtures) or any
value 1..255 (truncating / expanding the printed string). The bug is latent —
not the cause of the Plan 27 round-6 hang per the H-E REFUTED experiment, but
will surface as wrong text rendering or partial clear regions in larger games.

**Fix shape (one line per helper):**
```kotlin
// Before
val loopVar = CVarDecl("i", CU8, initializer = null)
// After
val loopVar = CVarDecl("i", CU8, initializer = CLiteral(0))
```

Same change for `ry`, `rx`, `fy`, `fx` in the clear-region and fill-screen
helpers. Note also that `_win_clear_region`'s inner loop never resets `rx` to 0
between outer iterations — a second bug. After fixing the init, the
clear-region helper still only clears one row because the inner-loop reset is
missing.

**Recommended follow-up:** open a small JVM-tier RED→GREEN plan in a future
phase that adds a `WindowTextHelperInitTest` asserting the four loop counters
are initialised in the generated C. The fix is mechanical; the test prevents
regression. Independent of the round-6 hang — can land in parallel with any
round-6 fix.
