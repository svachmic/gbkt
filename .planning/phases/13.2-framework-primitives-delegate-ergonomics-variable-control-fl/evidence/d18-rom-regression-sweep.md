# Phase 13.2 — D-18 ROM Regression Sweep Evidence

**Plan:** 13.2-07  
**Date:** 2026-06-03  
**Branch:** feat/d_and_d_gaps (post-Phase-13.2 — plans 01–07 executed)  
**GBDK_HOME:** /Users/michalsvacha/gbdk  
**lcc path:** /Users/michalsvacha/gbdk/bin/lcc  

---

## JVM Tier Results

**Command:**
```
./gradlew :gbkt-lang:test :gbkt-ir:test :gbkt-examples:simple-physics:test :gbkt-examples:metasprites:test --rerun-tasks
```

**Result:** BUILD SUCCESSFUL (all tests GREEN)

| Test Suite | Tests | Status |
|---|---|---|
| `:gbkt-lang:test` | All Wave 0 tests | GREEN |
| `:gbkt-ir:test` | All Wave 0 tests | GREEN |
| `:gbkt-examples:simple-physics:test` | SimplePhysicsEmissionTest (D-12 oracle, byte-identical) | GREEN |
| `:gbkt-examples:metasprites:test` | MetaspritesIRTest | GREEN |

**Oracle verification (D-12):**
- `SimplePhysicsEmissionTest` D-11.1/D-11.2/D-11.3: all GREEN — i16FixedVar(64) emits identical
  IR shape as prior i16Var(1024); `.toPixel()` emits identical `BinaryExpr(VarRef, SHR, Literal(4))`

---

## D-18 ROM Build Sweep

**Command (single chained invocation — per "No parallel gradle clean" project rule):**
```
./gradlew clean \
  :gbkt-examples:simple-physics:buildRom \
  :gbkt-examples:metasprites:buildRom \
  :gbkt-examples:banks:buildRom \
  :gbkt-examples:platformer-template:buildRom \
  :gbkt-examples:pong:buildRom \
  :gbkt-examples:breakout:buildRom \
  :gbkt-examples:metasprites-stress:buildRom \
  :gbkt-examples:racer:buildRom
```

**Result:** BUILD SUCCESSFUL in 10s (all 8 targets EXIT 0)

| Target | Exit Code | ROM Size (bytes) | vs 13.1 Baseline | Status |
|---|---|---|---|---|
| simple-physics | 0 | 32768 | 32768 → 32768 (0 delta) | PASS |
| metasprites | 0 | 32768 | 32768 → 32768 (0 delta) | PASS |
| banks | 0 | 65536 | 65536 → 65536 (0 delta) | PASS |
| platformer-template | 0 | 65536 | 65536 → 65536 (0 delta) | PASS |
| pong | 0 | 32768 | 32768 → 32768 (0 delta) | PASS* (toolchain non-determinism) |
| breakout | 0 | 32768 | 32768 → 32768 (0 delta) | PASS |
| metasprites-stress | 0 | 32768 | 32768 → 32768 (0 delta) | PASS (after Rule 1 fix — see below) |
| racer | 0 | 65536 | 65536 → 65536 (0 delta) | PASS |

**pong PASS* note:** Per project memory `project_pong_toolchain_nondeterminism` — pong.gb hashes
differently every rebuild even from same commit (pre-existing sdcc/lcc non-determinism).
Judged on EXIT 0 + byte size only (both = PASS). Generated C is byte-identical between builds.

---

## Auto-Fix: metasprites-stress ANLZ-03 (Rule 1 — Bug)

**Found during:** Task 1 (first buildRom sweep attempt)

**Error:** `ANLZ-03: ROM_ONLY supports at most 2 ROM banks; you declared romBanks=4.`

**Root cause:** Phase 13.1 commit `7afe462c` (WR-03) added an early-guard in `BankingAnalysisPass`
that fires BEFORE the auto-upgrade path. The metasprites-stress example was configured with
`Cartridge.ROM_ONLY` + `romBanks=4` — internally inconsistent since Phase 13.1-08 migrated it
to `cartridge(Cartridge.ROM_ONLY)`. The `romBanks=4` was intentional (CR-02 forcing condition to
produce real `bank1.c`), but was previously saved by the auto-upgrade belt-and-suspenders path.
WR-03 bypassed auto-upgrade with a pre-emptive error.

**Fix:** Changed `cartridge(Cartridge.ROM_ONLY)` → `cartridge(Cartridge.MBC5)` in
`MetaspritesStress.kt`. MBC5 supports up to 256 banks; the explicit `romBanks=4` is valid.
The CR-02 forcing condition (multi-bank codegen path → real `bank1.c`) is preserved.

**Files modified:** `gbkt-examples/metasprites-stress/src/main/kotlin/.../MetaspritesStress.kt`

**Commit:** `89d944b4`

**ROM size after fix:** 32768 bytes (identical to Phase 13.1 baseline — no regression)

---

## Pre-existing Out-of-Scope Issue (Deferred)

**`metasprites-stress:test` byte-identity test failure (pre-existing, NOT 13.2 scope):**

After the clean buildRom sweep triggers `:convertSprites`, the `MetaspritesStressGeneratedSpriteByteIdentityTest`
runs and finds the generated `elephant.c` / `tiger.c` differ from the Phase 12.4-captured baselines.

**Root cause (pre-existing):** Phase 12.9 (commit `a04ebff6` or similar) added `-keep_palette_order`
flag to `ConvertSpritesTask`. The generated sprite header now includes `-keep_palette_order` in
its command comment, changing the byte content vs the May 24 baselines. Size delta:
- elephant.c: actual=10286 vs baseline=10469 (baseline is larger — lacks `-keep_palette_order`)
- tiger.c: actual=10238 vs baseline=10421

**Why it wasn't caught before:** The test uses `assumeTrue(GENERATED_ELEPHANT.exists())` — a
relative-path check against `build/gbkt/generated/sprites/elephant.c`. This file is only present
after a `:buildRom` or `:convertSprites` run in the same clean workspace. Prior sweeps either ran
UP-TO-DATE (cached result) or ran before `-keep_palette_order` was added.

**Disposition:** Deferred to `.planning/phases/13.2-.../deferred-items.md`. This test requires
updating the `elephant.c.baseline` and `tiger.c.baseline` after Phase 12.9's flag change. Not in
13.2 scope. The 13.2 plan only requires `:gbkt-examples:metasprites:test` (the plain Metasprites
IR test, which is GREEN), NOT `:gbkt-examples:metasprites-stress:test`.

---

## Phase 13.2 Migration Surface Spot-Check

Migrated files verified (spot-check of D-18 migration from Plans 13.2-01..06):

**`gbkt-examples/metasprites/src/.../Metasprites.kt`**
- `posX`/`posY`: `i16FixedVar(80)`/`i16FixedVar(72)` (Req #3)
- `idx`: `u8Var(0, wrapAt = NUM_FRAMES)` (Req #9)
- `rot`: `u8Var(0, wrapAt = 16)` (Req #9)
- `runIf(...)` replacing nested clamp `whenever` blocks (Req #2)
- `spdY.easeToZero()`/`spdX.easeToZero()` replacing two-whenever decay ladders (Req #8)
- Pitfall-6 comment block deleted
- No `and 0xF` bitmask-wrap, no `isAtLeast NUM_FRAMES` explicit wrap

**`gbkt-examples/simple-physics/src/.../SimplePhysics.kt`**
- `posX`/`posY`: `i16FixedVar(64)` (Req #3)
- `ball.moveTo(posX.toPixel(), posY.toPixel())` (Req #3)
- Oracle D-12 confirms byte-identical generated C (SimplePhysicsEmissionTest GREEN)

**`gbkt-examples/platformer-template/src/.../PlatformerTemplate.kt`**
- `playerX`/`playerY`: `i16FixedVar(80)`/`i16FixedVar(72)` (Req #12/#3)

**`context/DSL_REFERENCE.md`**
- All five primitives documented with before/after examples (D-19)

---

## Summary

- **JVM tier:** GREEN (all required suites pass)
- **D-18 buildRom sweep:** 8/8 EXIT 0 (pong PASS*, metasprites-stress fixed inline)
- **ROM size regression:** None — all targets same size as Phase 13.1 baseline
- **Deviations:** 1 Rule 1 auto-fix (metasprites-stress ANLZ-03), commit `89d944b4`
- **Pre-existing deferred:** metasprites-stress byte-identity baseline mismatch (Phase 12.9 flag change)
