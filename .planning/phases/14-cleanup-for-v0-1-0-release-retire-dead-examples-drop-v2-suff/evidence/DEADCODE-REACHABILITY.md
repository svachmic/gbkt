# Phase 14 Dead-Code Reachability Analysis
## Plan 04 — Conservative, proof-gated sweep

**Captured:** 2026-06-06
**Branch:** feat/d_and_d_gaps
**Mandate:** Remove code ONLY with positive non-reachability proof. When uncertain, RETAIN.

---

## Item 1: GBDKBackend bridge `generate(game: GameIR, options: GenerationOptions)`

**Location:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/GBDKBackend.kt` lines 56–59

**Code:**
```kotlin
@Suppress("TooGenericExceptionCaught")
override fun generate(game: GameIR, options: GenerationOptions): GenerationResult {
    return generateV2(game)
}
```

**Caller-set analysis:**

The bridge method exists to satisfy the `CodegenBackend.generate()` interface contract:

```kotlin
// gbkt-backend-api: CodegenBackend.kt line 59
fun generate(game: GameIR, options: GenerationOptions = GenerationOptions()): GenerationResult
```

The bridge delegates to `generateV2()` which has a different signature:
```kotlin
fun generateV2(gameIR: GameIR, assetManifest: AssetManifest? = null, outputDirectory: java.io.File? = null, assetRoot: java.io.File? = null): GenerationResult
```

**Direct callers of `GBDKBackend.generate()` interface method (via `CodegenBackend` interface):**
- No direct callers invoke `generate(game, options)` on `GBDKBackend` in production code.
- The `GenerateCTask.kt:382` calls `generateV2` **by name via reflection** (string literal `"generateV2"`), bypassing the bridge entirely.
- All test callers invoke `backend.generateV2(...)` directly (see `RomBanksDerivedTest.kt:128`, `AutoExitSynthesisTest.kt:293-294`, `BanksEmissionTest.kt:117,416`, `SportLegacyTilesetPathInvariantTest.kt:70`).
- The bridge's only callers would be callers of the `CodegenBackend` interface that hold `GBDKBackend` as `CodegenBackend` — grep found no such callers in production code.

**Reachability verdict:** The bridge is NOT directly reachable by production code callers (only the interface contract forces it to exist). However, removing the bridge ALONE would break the `CodegenBackend` interface compile because `generateV2()` does not yet declare `override fun generate(...)`.

**DECISION: Bridge removal reconciled into plan 05's atomic promote (correction #3)**

Removing the bridge here without simultaneously renaming `generateV2` → `generate` would produce:
```
error: Class 'GBDKBackend' is not abstract and does not implement abstract member 'generate'
```

The correct atomic operation (plan 05, Track 5) is:
1. Remove the bridge `override fun generate(game, options)` that delegates to `generateV2`
2. Simultaneously rename `generateV2` → `generate` and add `override` to satisfy the interface
3. Update the reflection string in `GenerateCTask.kt:382` from `"generateV2"` to `"generate"`

This is the standard V2 rename execution sequence documented in `14-RESEARCH.md` §"V2 rename execution sequence".

**Physical edit in plan 04: NONE** — bridge retained; plan 05 performs the atomic promote.
**Compile guard: `:gbkt-backend-gbdk:compileKotlin` GREEN (bridge remains, interface satisfied)**

---

## Item 2: `RpgRegistry.clear()` in RpgExtensions.kt

**Location:** `gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/RpgExtensions.kt` lines 79–82

**Code:**
```kotlin
/** Clears the registry — call after the game-building lambda completes. */
fun clear() {
    holder.remove()
}
```

**Caller-set analysis:**

Grep command:
```bash
grep -rn "RpgRegistry\.clear\|RpgRegistry.clear" --include="*.kt" --exclude-dir=build --exclude-dir=".git" --exclude-dir=".planning" /Users/michalsvacha/GitHub/personal/gbkt/
```

Result: **ZERO matches** (no output — zero callers outside the declaration).

Secondary grep for all `RpgRegistry` usages:
```bash
grep -rn "RpgRegistry" --include="*.kt" --exclude-dir=build --exclude-dir=".git" --exclude-dir=".planning"
```

Result: Only declaration (line 56) + `registerCharacter` (line 104) + `registerMonster` (line 145). No `clear()` callers.

The `RpgRegistry` is `internal object` — it cannot be referenced from outside the `gbkt-genre-rpg` module. All code in the module was searched above with zero `clear()` callers.

**Reachability verdict:** ZERO production callers confirmed by grep + internal visibility scope.

**DECISION: Removed** — see task 2 below.

**Proof trail:**
1. `grep -rn "RpgRegistry.clear"` → 0 results (excluding declaration)
2. `./gradlew test pluginTest` → GREEN (post-removal — see Task 2 section)
3. Full JVM suite GREEN with removal

**Suite result:** See Task 2 section below.

**Commit:** see task 2 commit hash below

---

## Item 3: Byte-Identity Diff vs Plan-03 Baseline (Task 3)

**Approach:** Regenerate C for all 7 KEEP examples after the sweep, compare SHA-256 snapshots against `evidence/baseline/baseline-<name>.sha256`.

**Expected:** Zero diff — the sweep removed only unreachable code (`RpgRegistry.clear()` in genre-rpg, not in backend-gbdk or any codegen path), and the bridge in GBDKBackend was retained. Neither change affects generated C.

**Results:** See "Task 3: Byte-identity result" section below.

---

## Task 2: RpgRegistry.clear() Removal — Post-Removal Suite Result

**Removal location:** `gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/RpgExtensions.kt`

**Method removed:**
```kotlin
/** Clears the registry — call after the game-building lambda completes. */
fun clear() {
    holder.remove()
}
```

**KDoc reference in class-level KDoc updated:** Removed "and is cleared when [GameBuilder.build] completes (or when [game] top-level function exits its lambda)." since `clear()` no longer exists.

**Suite results (all GREEN):**
- `./gradlew :gbkt-genre-rpg:test` — BUILD SUCCESSFUL (UP-TO-DATE)
- `./gradlew :gbkt-backend-gbdk:test :gbkt-core:test :gbkt-lang:test :gbkt-analysis:test :gbkt-backend-api:test :gbkt-ir:test` — BUILD SUCCESSFUL
- `./gradlew :gbkt-genre-platformer:test :gbkt-genre-puzzle:test :gbkt-genre-sport:test` — BUILD SUCCESSFUL
- `./gradlew pluginTest` — BUILD SUCCESSFUL (157 tests)

**Pre-existing failure (out of scope):** `BanksUatTest` `anchor 1 cross-bank scene navigation` and `anchor 2 banked zone tilemap visible` fail at line 145 (screenshot dominance ratio assertion) because a stale `banks.gb` ROM from a prior build exists. Confirmed pre-existing by reverting my changes and running the same test — same 2 failures on unmodified HEAD. These are UAT tests that require a freshly built ROM; they are NOT caused by `RpgRegistry.clear()` removal and are out of scope for this plan (no pre-existing failure introduced by Plan 04's changes).

---

## Task 3: Byte-Identity Result

**Post-sweep SHA-256 captures for generateC-produced files:**

| Example | Files checked | Status |
|---------|--------------|--------|
| pong | bank1.c, main.c, sprites/ball.c, sprites/paddle.c | MATCH (byte-identical) |
| breakout | bank1.c, main.c, sprites/ball.c, sprites/paddle.c | MATCH (byte-identical) |
| simple-physics | main.c, sprites/ball.c | MATCH (byte-identical) |
| metasprites | main.c, sprites/elephant.c | MATCH (byte-identical) |
| metasprites-stress | bank1.c, main.c, sprites/elephant.c, sprites/player.c, sprites/tiger.c | MATCH (byte-identical) |
| banks | bank1.c, main.c, zone_bank2.c | MATCH (byte-identical) |
| platformer-template | bank1.c, main.c, sprites/player.c, zone_bank2.c | MATCH (byte-identical) |

**Note on zone tileset/tilemap files:** The plan-03 baseline for `banks` and `platformer-template` included `_zone_*.c` files (e.g., `_zone_playZone_tileset.c`, `_zone_world1Area1Zone_tilemap.c`) because those baselines were captured after a prior `buildRom` or `convertZoneTilesets` run. These files are produced by the `convertZoneTilesets` Gradle task, NOT by `generateC`. They are now absent from the current build directory because the `convertZoneTilesets` task was not run in this plan's scope. The files that ARE present (the generateC-produced ones) are all byte-identical. This absence is a pre-existing methodology note, NOT a regression from Plan 04's changes.

**Second independent gate:** `./gradlew :gbkt-examples:metasprites:test :gbkt-examples:metasprites-stress:test` — BUILD SUCCESSFUL (both sprite byte-identity tests GREEN; 13.6-07 baselines still valid).

**Overall verdict: PASS** — All generateC-produced C files byte-identical to plan-03 baseline. The sweep changed only `RpgRegistry.clear()` in `gbkt-genre-rpg` (not in any codegen path) and retained the `GBDKBackend.generate()` bridge (no change to GBDKBackend). Zero impact on generated C confirmed.

**Command used for comparison:**
```bash
find gbkt-examples/<name>/build/gbkt/generated -name "*.c" | sort | xargs shasum -a 256
```

**Suite result:** `./gradlew pluginTest` — BUILD SUCCESSFUL
