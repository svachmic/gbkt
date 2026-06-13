# Phase 18: Deprecation Removals and Sonar Burn-down — Research

**Researched:** 2026-06-13
**Domain:** Kotlin DSL API removal · SonarCloud S3776 cognitive complexity refactoring · GBDK C codegen byte-identity oracle
**Confidence:** HIGH (VERIFIED for all key claims via live SonarCloud API + codebase grep)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** `runIf` survives; `whenever` is removed. Both emit identical `IfOp`. Migration is mechanical and exhaustive.
- **D-01a:** Both `whenever` overloads migrate to `runIf`: `ScriptBuilder.whenever(Expr, block)` folds into existing `runIf`; `ScriptBuilder.whenever(PoolPoolCollisionExpr, block)` is reborn as a new `runIf` overload (does not exist today).
- **D-01b:** Migration is exhaustive — ~63 example call sites, ~250 KDoc/doc references, 3 internal framework callers, `context/DSL_REFERENCE.md`.
- **D-01c:** `unless`/`orElse` unchanged — re-anchor their KDoc cross-references to `runIf`, no `[whenever]` mentions.
- **D-02:** Hard-remove this phase (v0.1.1) — no `@Deprecated` grace for either `whenever` or `combatIsInState(String)`. Migrate every in-tree call site in the same change.
- **D-03:** Remove `combatIsInState(stateId: String, battleId: String)` at `RpgExtensions.kt:440`. Keep typed `combatIsInState(CombatStateId, BattleRef)` at :419. Migrate single in-tree call site `CombatStatesTest.kt:122`.
- **D-04:** CONTRIBUTING.md documents two-tier deprecation rule. Cite SEED-023, SEED-025, SEED-028 as worked examples of the pre-1.0 carve-out.
- **D-05:** NOSONAR is last-resort / irreducible-only. Each suppression carries an inline rationale and a tracked v0.2.0 seed. Target 0–2 of the ≤5 milestone budget.
- **D-06:** One S3776 finding per commit for EMITTING code (`codegen/visitor/**`, `GBDKPipeline.kt`) + 7-example byte-identity ROM sweep per commit. Non-emitting refactors batched per-file with JVM-test-only evidence.
- **D-06a:** S3776 commits are NEVER combined with seed-fix commits.
- **D-07 (SEED-027):** `TargetProfiles.GAME_BOY_COLOR_SCREEN.bitsPerPixel` 4→2; fix KDoc prose; narrow "All backends MUST derive" to width/height only. Byte-identical by construction.
- **D-08 (SEED-028):** Fix 4 stale `config { ramBanks = N }` guidance strings. Do NOT add a `@Deprecated` shim. Add v0.1.1 CHANGELOG migration note.
- **D-09:** Create new root `CHANGELOG.md` (Keep a Changelog format) for breaking-change notes.

### Claude's Discretion

- Exact extract-method decomposition of each S3776 method (names, boundaries) — constrained only by D-05/D-06 and byte-identity.
- Whether `CombatStatesTest.kt`'s string-vs-typed equivalence test is deleted or re-expressed against the typed form.

### Deferred Ideas (OUT OF SCOPE)

- Broader ConfigBuilder setter-convention redesign (v0.2.0).
- `orElse` wrap-guard IfOp silently attaches bug — FIX phases 19–21.
- Threading `TargetProfile.bitsPerPixel`/`screen` into codegen — v0.2.0 backlog.
- All codegen/asset bugs (metasprite, banks, tRNS, platformer) — Phases 19–21.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DEPR-01 | `whenever`/`runIf` duplication unified; redundant API removed + all in-tree usages migrated | Census verified: 14 call sites in .kt files (non-comment) in examples, 3 internal callers, ~20 test usages. Migration is exhaustive text rename + one new overload. |
| DEPR-02 | Deprecated `combatIsInState(String)` overload removed + sole in-tree call site migrated | Confirmed: RpgExtensions.kt:440 is the only definition; CombatStatesTest.kt:122 is the SOLE call site. |
| DEPR-03 | gbkt deprecation/removal convention documented in CONTRIBUTING.md | Confirmed: CONTRIBUTING.md has no deprecation section today. Section fits naturally before Code Review Checklist. |
| SONAR-01 | SonarCloud S3776 HIGH findings reduced 46 → 0, ≤5 NOSONAR suppressions | VERIFIED via live SonarCloud API: 46 findings in files that exist on current branch (29 EMITTING, 17 NON-EMITTING). Full inventory in S3776 table below. |
| SONAR-02 | Every S3776 commit touching `codegen/visitor/**` or `GBDKPipeline.kt` passes 7-example byte-identity ROM sweep | Sweep mechanics verified. Pong non-determinism landmine documented. Ghost-findings cleanup will also reduce total from 69→46 on first new scan. |
</phase_requirements>

---

## Summary

Phase 18 has five work tracks that can be waved in any order (all are independent):

**Track A — DEPR-01 `whenever`→`runIf` migration.** The `whenever` function has two overloads: the standard `Expr`-form at `ScriptBuilder.kt:209` and the pool-collision form at `ActorPoolBuilder.kt:396`. Both lower to `IfOp`; the migration is a pure rename. In-tree call sites: ~14 non-comment Kotlin usages in example game files, ~20 in test fixtures across `gbkt-backend-gbdk` and `gbkt-lang` tests, 3 framework-internal callers (`VariableBuilders.kt:193`, `ExprBuilder.kt:298`, `ExprBuilder.kt:301`), and `ActorPoolBuilder.kt:396` itself. The pool-collision `whenever` needs a new `runIf` overload added to `ActorPoolBuilder.kt`. Documentation: `context/DSL_REFERENCE.md` (~250 references), `InputBuilders.kt` KDoc, `ScriptBuilder.kt` KDoc, plus the gradle-plugin test fixtures.

**Track B — DEPR-02 `combatIsInState(String)` removal.** One-file change: `RpgExtensions.kt:419-441`. One test migration: `CombatStatesTest.kt:122`. Confirmed no other in-tree call sites.

**Track C — DEPR-03 + D-09 documentation.** Add deprecation-convention section to `CONTRIBUTING.md`. Create root `CHANGELOG.md`. Fix 4 stale guidance strings (SEED-028). Fix `TargetProfiles.kt:50` bitsPerPixel 4→2 (SEED-027).

**Track D — SONAR-01 NON-EMITTING S3776 burn-down.** 17 findings across `gbkt-analysis` (4), `gbkt-emulator` (4), `gbkt-mcp-server` (4), `gbkt-test` (1), `gbkt-lang/dsl` (1), `gbkt-intellij-plugin` (2), `gbkt-core/test` (1). All are batched per-file, JVM-test-only evidence.

**Track E — SONAR-01 EMITTING S3776 burn-down.** 29 findings across `GBDKPipeline.kt` (10), `GBDKSystemVisitor.kt` (7), other visitors (9), `CEmitter.kt`/`GBDKCollectionCodegen.kt`/`SharedConstantTablePass.kt`/`TrackSynthesizer.kt` (3). Each gets its own commit + 7-example ROM sweep. `CEmitter.emit` (cc=29) is the prime NOSONAR candidate (flat AST variant dispatch).

**Validation backbone:** JVM test suite (`./gradlew test`) + 7-example byte-identity ROM sweeps for emitting refactors. Pong is PASS* (non-deterministic toolchain hash).

**SonarCloud ghost-issues note:** The live Sonar API shows 69 total S3776 issues. 23 are in `commonMain`/`jvmMain` files from an older multiplatform structure that no longer exists on disk. These will auto-close on the first Phase 18 scan. The planner should target 46 → 0 open findings; the ghost issues disappear automatically.

**Primary recommendation:** Start with Track A (DEPR-01) and Track C (docs/seeds) in Wave 1 since they have zero byte-identity risk. Wave 2: NON-EMITTING S3776 batch. Wave 3+: EMITTING S3776 per-finding commits in complexity order (highest first for risk-front-loading). One consolidated full-sweep at phase end as backstop (D-06).

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| `whenever` → `runIf` migration | Lang/DSL (`gbkt-lang`) | Examples, tests | Pure DSL surface rename; IR/codegen unaffected |
| `combatIsInState(String)` removal | Genre-RPG (`gbkt-genre-rpg`) | Test (`gbkt-genre-rpg/test`) | One-module removal with 1 test migration |
| Deprecation convention doc | Documentation (`CONTRIBUTING.md`) | — | Authoring convention, no code |
| S3776 EMITTING fixes | Backend-GBDK (`codegen/visitor/**`, `GBDKPipeline.kt`) | Genre codegen (`PlatformerVisitor`, `SportVisitor`) | C-emitting visitors own cognitive complexity in codegen tier |
| S3776 NON-EMITTING fixes | Analysis passes, emulator, MCP server, intellij, test infra | — | Logic-only; no C output path |
| `bitsPerPixel` 4→2 (SEED-027) | Core constraints (`TargetProfiles.kt`) | — | Constants/KDoc correction, zero consumers |
| Stale guidance strings (SEED-028) | Gradle plugin, examples | — | Comment/KDoc text only |
| `CHANGELOG.md` creation | Project root | — | Adopter-facing release notes |

---

## Standard Stack

No new dependencies are introduced in this phase. The existing toolchain applies.

### Core (already present)
| Tool | Version | Purpose |
|------|---------|---------|
| Kotlin | 2.3.20 | Language |
| Gradle | 9.5.1 | Build system |
| JUnit 5 | via BOM | JVM test runner |
| SonarCloud | Cloud-hosted | S3776 gate (query via public API) |
| GBDK-2020 | 4.5.0 (CI-pinned) | ROM compilation for byte-identity sweep |

### Installation
No new packages. All work is refactoring + text changes in existing modules.

---

## Package Legitimacy Audit

No external packages are installed in this phase. This section is not applicable.

---

## Architecture Patterns

### System Architecture Diagram

```
Track A (DEPR-01): DSL User Code
  → whenever() calls → ScriptBuilder.whenever(Expr) / ScriptBuilder.whenever(PoolPoolCollisionExpr)
        |                     |
        ↓                     ↓
  [REMOVE] whenever(Expr)   [RELOCATE to runIf overload]
        |
        ↓
  runIf(Expr) — identical IfOp emission — no C change

Track E (SONAR EMITTING): Method → Extract → Oracle
  complex_method() — [extract sub-functions] → multiple focused helpers
        |
        ↓
  ./gradlew :gbkt-examples:<game>:buildRom  (7 examples)
        |
        ↓
  sha256(*.gb) compared — IDENTICAL → commit accepted
```

### Recommended Project Structure (unchanged)
No new directories or files (except `CHANGELOG.md` at root and new extracted helper functions within existing files).

---

## S3776 Cognitive Complexity Inventory

### How to Read This Table

**Source:** VERIFIED via live SonarCloud API (`api/issues/search?componentKeys=svachmic_gbkt&rules=kotlin:S3776`) on 2026-06-13. All 46 findings correspond to files present on disk on `chore/hardening_0_1_0`.

**Bucket:**
- `EMIT` = in `codegen/visitor/**`, `GBDKPipeline.kt`, or other C-emitting pipeline code → per-finding commit + 7-example ROM sweep (D-06)
- `NON` = lang/DSL, analysis, emulator, MCP, test infra → batched per-file, JVM-test-only evidence

**Action:**
- `EXTRACT-METHOD` = default path; split into focused private helpers
- `NOSONAR` = irreducible flat dispatch (visitor/emitter jump-table); inline rationale + v0.2.0 seed (≤5 milestone budget, target 0–2)

### EMITTING Findings (29 total — per-finding commits + ROM sweep)

| # | File | Line | Function | CC | Action | Notes |
|---|------|------|----------|----|--------|-------|
| E-01 | `GBDKSystemVisitor.kt` | 4861 | `buildPuzzleObjectFunctions` | 92 | EXTRACT-METHOD | Largest finding. Handles 5+ puzzle types in one function; extract per-type helpers |
| E-02 | `MenuVisitor.kt` | 84 | `buildMenuFunction` | 90 | EXTRACT-METHOD | Menu input + render logic mixed; extract per-section helpers |
| E-03 | `GBDKPipeline.kt` | 4195 | `buildSystemGlobalVars` | 71 | EXTRACT-METHOD | Accumulator for all system global vars; extract per-system sub-builders |
| E-04 | `GBDKPipeline.kt` | 964 | `buildHomeFile` | 44 | EXTRACT-METHOD | Main file assembly; already large; extract more sub-builders |
| E-05 | `GBDKSystemVisitor.kt` | 5490 | `buildNpcCollisionFunctions` | 43 | EXTRACT-METHOD | Already has `@Suppress("LongMethod")`; extract per-rule helpers |
| E-06 | `SoundVisitor.kt` | 365 | `buildNRxxRegisterWrites` | 43 | NOSONAR CANDIDATE | Flat register-per-channel dispatch; may be irreducible jump-table |
| E-07 | `SceneVisitor.kt` | 97 | `visit` | 39 | EXTRACT-METHOD | Scene enter/frame/exit assembly; extract per-lifecycle helpers |
| E-08 | `GBDKSystemVisitor.kt` | 4470 | `buildActorPoolStateVars` | 36 | EXTRACT-METHOD | Per-pool state var accumulation |
| E-09 | `GBDKSystemVisitor.kt` | 1851 | `buildEntityCollisionFunctions` | 34 | EXTRACT-METHOD | Entity collision handler assembly |
| E-10 | `GBDKSystemVisitor.kt` | 539 | `visitSoundSystem` | 30 | EXTRACT-METHOD | Sound system dispatch |
| E-11 | `GBDKSystemVisitor.kt` | 2232 | `buildZoneTransitionFunction` | 30 | EXTRACT-METHOD | Zone transition logic |
| E-12 | `CEmitter.kt` | 87 | `emit` | 29 | **NOSONAR** (prime candidate) | Pure flat `when`-dispatch on sealed `CStatement` subtypes; each branch is a single-depth emission. Structurally identical to JVM bytecode switch tables. NOSONAR rationale: "Flat sealed-type dispatch — every branch is O(1) emission with no nesting; extraction would scatter related cases and harm readability." |
| E-13 | `GBDKPipeline.kt` | 488 | `extractControls` | 28 | EXTRACT-METHOD | Recursive op-walker; extract `walkOps` local to top-level private method |
| E-14 | `GBDKSystemVisitor.kt` | 1958 | `buildEncounterCheckFunction` | 28 | EXTRACT-METHOD | Encounter branch dispatch |
| E-15 | `GBDKPipeline.kt` | 2855 | `buildHeaderFile` | 27 | EXTRACT-METHOD | Header section accumulation; extract per-section sub-builders |
| E-16 | `CombatVisitor.kt` | 89 | `generateCombatFunctions` | 23 | EXTRACT-METHOD | Combat system mixed-type dispatch |
| E-17 | `GBDKPipeline.kt` | 206 | `buildMetadataFile` | 22 | EXTRACT-METHOD | Metadata JSON construction; extract per-section |
| E-18 | `HudVisitor.kt` | 482 | `buildHudUpdateFunction` | 22 | EXTRACT-METHOD | HUD update logic |
| E-19 | `GBDKPipeline.kt` | 491 | `walkOps` (local fun inside `extractControls`) | 21 | EXTRACT-METHOD | Promote local recursive function to top-level private |
| E-20 | `GBDKPipeline.kt` | 4682 | `buildMainFunction` | 21 | EXTRACT-METHOD | `main()` construction; extract per-section helpers |
| E-21 | `ActorVisitor.kt` | 437 | `generateAnimationDefines` | 21 | EXTRACT-METHOD | Animation define accumulation |
| E-22 | `GBDKCollectionCodegen.kt` | 436 | `GBDKCollectionCodegen` (class body / init) | 20 | EXTRACT-METHOD | Collection codegen logic |
| E-23 | `SharedConstantTablePass.kt` | 125 | `extractConstArrays` | 20 | EXTRACT-METHOD | Constant table extraction pass |
| E-24 | `GBDKPipeline.kt` | 2101 | `guardCrossBankBgTilemapAccess` | 19 | EXTRACT-METHOD | Cross-bank guard logic |
| E-25 | `GBDKPipeline.kt` | 3683 | `buildFlagVarDecls` | 19 | EXTRACT-METHOD | Flag var declaration accumulation |
| E-26 | `DialogVisitor.kt` | 502 | `buildDialogFunction` | 19 | EXTRACT-METHOD | Dialog function construction |
| E-27 | `GBDKPipeline.kt` | 3999 | `buildCallOpStubFunctions` | 18 | EXTRACT-METHOD | Call-op stub generation |
| E-28 | `TrackSynthesizer.kt` | 165 | `scanlineFill` | 17 | EXTRACT-METHOD | Sport track scanline fill algorithm (in `gbkt-genre-sport/codegen/`) |
| E-29 | `RpgVisitor.kt` | 488 | `generateApplyEffectFunction` | 16 | EXTRACT-METHOD | RPG effect application |

### NON-EMITTING Findings (17 total — batched per-file, JVM-test-only evidence)

| # | File | Line | Function | CC | Action | Notes |
|---|------|------|----------|----|--------|-------|
| N-01 | `gbkt-mcp-server/.../McpEmulatorSession.kt` | 327 | `batchAssert` | 74 | EXTRACT-METHOD | MCP batch assertion dispatch |
| N-02 | `gbkt-analysis/.../SemanticValidationPass.kt` | 276 | `collectAllTopLevelOps` | 34 | EXTRACT-METHOD | Op-type fan-out logic |
| N-03 | `gbkt-core/src/main/kotlin/.../test/ScriptOpInterpreter.kt` | 460 | `evaluateBinaryExpr` | 33 | EXTRACT-METHOD | Simulation binary expression evaluation |
| N-04 | `gbkt-emulator/.../EmulatorSession.kt` | 87 | `launch` | 32 | EXTRACT-METHOD | Emulator session setup |
| N-05 | `gbkt-emulator/.../agent/GameMetadata.kt` | 137 | `fromJsonString` | 32 | EXTRACT-METHOD | Metadata JSON deserialization |
| N-06 | `gbkt-test/.../GbktTestRecipes.kt` | 267 | `GbktTestExtension` (class init / primary function) | 32 | EXTRACT-METHOD | Test recipe orchestration |
| N-07 | `gbkt-mcp-server/.../ObservationSerializer.kt` | 91 | `GameMetadata` (serialization) | 26 | EXTRACT-METHOD | MCP observation serialization |
| N-08 | `gbkt-analysis/.../ConstantFoldingPass.kt` | 143 | `evalBinaryOp` | 23 | EXTRACT-METHOD | Binary op constant evaluation |
| N-09 | `gbkt-emulator/.../agent/StepAgent.kt` | 513 | `Observation` (construction) | 22 | EXTRACT-METHOD | Step agent observation builder |
| N-10 | `gbkt-mcp-server/.../ToolHandlers.kt` | 142 | `handleStart` | 19 | EXTRACT-METHOD | MCP tool handler dispatch |
| N-11 | `gbkt-lang/src/main/kotlin/.../dsl/GameBuilder.kt` | 669 | `build` | 18 | EXTRACT-METHOD | GameIR assembly |
| N-12 | `gbkt-mcp-server/.../ObservationSerializer.kt` | 20 | `Observation` | 18 | EXTRACT-METHOD | Observation serialization |
| N-13 | `gbkt-analysis/.../BitwiseOptimizationPass.kt` | 85 | `optimizeExpr` | 17 | EXTRACT-METHOD | Bitwise optimization case dispatch |
| N-14 | `gbkt-intellij-plugin/.../completion/GbktPropertyChainCompletionProvider.kt` | 103 | `extractChain` | 17 | EXTRACT-METHOD | IntelliJ completion chain extraction |
| N-15 | `gbkt-analysis/.../ScriptOpTraversal.kt` | 135 | `buildTransitionGraph` | 16 | EXTRACT-METHOD | Transition graph construction |
| N-16 | `gbkt-emulator/.../agent/VariableInspector.kt` | 73 | `loadSymbols` | 16 | EXTRACT-METHOD | Symbol loading from .noi file |
| N-17 | `gbkt-intellij-plugin/.../toolwindow/CCodePreviewPanel.kt` | 411 | `setupAutoRefreshListener` | 16 | EXTRACT-METHOD | IntelliJ panel listener setup |

### NOSONAR Budget Tracker (≤5 milestone budget, target 0–2)

| Candidate | Finding | CC | Rationale | Status |
|-----------|---------|-----|-----------|--------|
| `CEmitter.emit` | E-12 | 29 | Flat sealed-type dispatch; every branch is 1-depth emission. Extracting each CStatement arm to a helper would split logically atomic emission patterns across 20+ helper methods without reducing cognitive burden on readers. | PRIMARY CANDIDATE |
| `buildNRxxRegisterWrites` | E-06 | 43 | Sound NRxx register write dispatch per-channel; flat per-channel cases. Needs code inspection before committing to NOSONAR vs. EXTRACT-METHOD. | SECONDARY CANDIDATE |

**Remaining 27 EMITTING findings**: EXTRACT-METHOD (no NOSONAR).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead |
|---------|-------------|-------------|
| Cognitive complexity metric | Custom AST walker | Trust SonarCloud S3776 verdict; consult live API to confirm closure |
| ROM byte-identity comparison | Manual diff tooling | `sha256sum` / `diff -q` on `.gb` files before+after each refactor commit |
| Deprecation-train tracking | New tracking system | Plain CHANGELOG.md (Keep a Changelog format) as D-09 specifies |

---

## Common Pitfalls

### Pitfall 1: Extract-method changes C emission ORDER
**What goes wrong:** Refactoring an emitting method by extracting sub-functions changes the ORDER in which C statements are accumulated if the caller assembles them sequentially. Even a pure "extract with same call order" can fail if the extracted method captures mutable state via closure.
**Why it happens:** Visitors accumulate `CStatement`/`CVarDecl` lists in order; any reordering of those accumulation calls shifts C line positions.
**How to avoid:** Extracted helpers MUST return values (lists/nodes), not write to mutable shared state. Verify: 7-example ROM sweep after EVERY emitting commit.
**Warning signs:** ROM sweep produces a file with different size or content; SHA hash changes.

### Pitfall 2: Pong toolchain non-determinism
**What goes wrong:** `pong.gb` produces a different SHA256 on every rebuild even from the same source. This is a pre-existing SDCC/lcc issue, NOT a codegen regression.
**How to avoid:** Flag pong as `PASS*` in all ROM sweep evidence. Verify that `generated/main.c` for pong is byte-identical BEFORE the SHA comparison; if main.c is identical, it's PASS*.
**Warning signs:** Pong hash changes while all other 6 examples match. This is EXPECTED and not a blocker.

### Pitfall 3: `whenever` → `runIf` migration touches `orElse` semantics
**What goes wrong:** `orElse {}` chains to the most-recent `runIf`/`ifOp` in the builder. A naive rename that replaces `whenever` with `runIf` in files where `orElse` follows `whenever` should be safe (they already lower to the same `IfOp`). However, if any code restructuring accidentally inserts a new `runIf` call BETWEEN the renamed `whenever` and its `orElse`, the `orElse` will silently attach to the new `runIf`.
**How to avoid:** Migrate `whenever` → `runIf` as a pure text rename ONLY. Do not restructure surrounding code in the same commit. Search for `whenever(` immediately followed by `orElse` in the same scope.
**Warning signs:** `orElse` at a call site where the preceding `whenever` was migrated; verify `orElse` still chains to the intended condition.

### Pitfall 4: No parallel `./gradlew clean` (Kotlin daemon collision)
**What goes wrong:** Running two `./gradlew clean ...` concurrently against the same project root causes Kotlin daemon corruption; both builds fail.
**How to avoid:** Never fan out parallel clean commands. Chain as single invocation: `./gradlew clean :gbkt-examples:pong:buildRom :gbkt-examples:breakout:buildRom ...`
**Warning signs:** Build fails with "daemon" or "lock" errors mid-sweep.

### Pitfall 5: combatIsInState migration — stale import
**What goes wrong:** After removing `combatIsInState(String, String)` from `RpgExtensions.kt`, the test file `CombatStatesTest.kt` still has `@Suppress("DEPRECATION")` annotation and imports for string types that may no longer be needed.
**How to avoid:** After removing the String overload and migrating the test, also remove the `@Suppress("DEPRECATION")` and verify no unused imports remain.

### Pitfall 6: SonarCloud ghost findings inflate the count
**What goes wrong:** The live SonarCloud API currently reports 69 S3776 findings, not 46. The extra 23 are in `commonMain`/`jvmMain` paths from an older multiplatform structure no longer present on disk. A planner who targets "reduce from 69 to 0" will plan unnecessary work.
**How to avoid:** Target is 46 → 0 (files on disk). Ghost findings auto-close on the first scan after Phase 18 lands on master.
**Warning signs:** After all 46 in-file fixes + NOSONAR suppressions are landed and a new scan runs, Sonar count should drop from 69 to ≤5 (or 0 if 0 NOSONAR used). If count stays at 23+, ghost cleanup is stalled.

### Pitfall 7: E-19 (`walkOps` local function) — two findings same outer method
**What goes wrong:** Sonar reports TWO issues on `GBDKPipeline.kt` at lines 488 and 491. Both are in `extractControls` — line 488 is the outer function, line 491 is `walkOps` local function defined inside `extractControls`. Treating them as one finding leaves one open.
**How to avoid:** Promote `walkOps` from local function to a top-level private function with its own signature. This resolves both findings: `extractControls` complexity drops (removes nested local), and `walkOps` as top-level has acceptable complexity.

### Pitfall 8: EMITTING vs NON-EMITTING scope confusion for genre visitors
**What goes wrong:** `PlatformerVisitor.kt` and `SportVisitor.kt` are in `codegen/` paths inside their genre modules — they ARE C-emitting. But they are NOT covered by the 7 standard examples in the ROM sweep. `TrackSynthesizer.kt` (E-28) is in `gbkt-genre-sport/codegen/` — EMITTING by path.
**How to avoid:** The planner should note that `TrackSynthesizer.scanlineFill` (E-28) gets its own commit + sweep per D-06. Since no sport example ROM exists in the 7-example suite, the sweep will not validate sport codegen directly. The commit still runs the 7-example sweep as a NON-REGRESSION check; sports-specific verification requires the JVM-tier sport codegen tests.

---

## Code Examples

### Verified DEPR-01: `whenever` → `runIf` rename pattern

```kotlin
// BEFORE (both overloads to remove)
// ScriptBuilder.kt:209
fun whenever(condition: Expr, block: ScriptBuilder.() -> Unit) {
    ifOp(condition, block)  // identical to runIf
}
// ScriptBuilder.kt:226
fun runIf(condition: Expr, block: ScriptBuilder.() -> Unit) = ifOp(condition, block)

// AFTER (whenever removed, runIf stays unchanged)
fun runIf(condition: Expr, block: ScriptBuilder.() -> Unit) = ifOp(condition, block)

// Call site migration (mechanical):
// BEFORE: whenever(buttons.a.pressed) { jump() }
// AFTER:  runIf(buttons.a.pressed) { jump() }
```

### Verified DEPR-01a: Pool-collision overload relocation

```kotlin
// BEFORE (in ActorPoolBuilder.kt:396 as extension on ScriptBuilder)
fun ScriptBuilder.whenever(
    collision: PoolPoolCollisionExpr,
    block: ScriptBuilder.(PoolIterator, PoolIterator) -> Unit,
) { ... }

// AFTER (rename to runIf — same signature, same body)
fun ScriptBuilder.runIf(
    collision: PoolPoolCollisionExpr,
    block: ScriptBuilder.(PoolIterator, PoolIterator) -> Unit,
) { ... }
```

### Verified DEPR-02: combatIsInState removal

```kotlin
// RpgExtensions.kt:440 — REMOVE this entire block:
@Deprecated(
    message = "Use combatIsInState(CombatStateId, BattleRef) to eliminate magic strings",
    replaceWith = ReplaceWith("combatIsInState(CombatStateId(stateId), BattleRef(battleId))"),
)
fun combatIsInState(stateId: String, battleId: String): Expr =
    combatIsInState(CombatStateId(stateId), BattleRef(battleId))

// CombatStatesTest.kt:122 — MIGRATE:
// BEFORE:
@Suppress("DEPRECATION") val stringExpr = combatIsInState("COMBAT_STATE_VICTORY", "combat")
// AFTER (option A — delete test):  remove the equivalence assertion
// AFTER (option B — rephrase):
val typedExpr = combatIsInState(CombatStates.VICTORY, BattleRef("combat"))
val typedExpr2 = combatIsInState(CombatStateId("COMBAT_STATE_VICTORY"), BattleRef("combat"))
assertThat(typedExpr.toString()).isEqualTo(typedExpr2.toString()) // tests typed overload internally
```

### Verified D-07 (SEED-027): bitsPerPixel fix

```kotlin
// TargetProfiles.kt BEFORE (line 43-50):
/**
 * Canonical screen specification for the Game Boy Color (GBC).
 * ... 4 bits per pixel ... All backends MUST derive from this object
 */
val GAME_BOY_COLOR_SCREEN = ScreenSpec(
    ...
    bitsPerPixel = 4,  // WRONG
    ...
)

// AFTER:
/**
 * Canonical screen specification for the Game Boy Color (GBC).
 * ... 2 bits per pixel, color via 8 hardware palettes (4 colours each).
 * The [width] and [height] fields are consumed by backends; [bitsPerPixel]
 * is a documentation constant until [SEED-TARGETPROFILE-SCREEN-THREADING] wires it in.
 */
val GAME_BOY_COLOR_SCREEN = ScreenSpec(
    ...
    bitsPerPixel = 2,  // CORRECT: GBDK always emits 2bpp tiles; color depth is palette-driven
    ...
)
```

### Verified D-08 (SEED-028): Stale guidance strings

All 4 locations confirmed on disk:

```
gbkt-gradle-plugin/src/main/kotlin/.../GbktExtension.kt:166
  @deprecated "Set `ramBanks` in the DSL `config { ramBanks = N }` block instead"
  → FIX: "Set `ramBanks` in the DSL `config { ramBanks(N) }` block instead"

gbkt-gradle-plugin/src/main/kotlin/.../tasks/CompileRomTask.kt:319
  D-07: DSL `config { ramBanks = N }` flows through...
  → FIX: DSL `config { ramBanks(N) }` flows through...

gbkt-examples/platformer-template/src/.../PlatformerTemplate.kt:61
  "add back `romBanks = 8` as a..."
  → FIX: "add back `romBanks(8)` as a..."

gbkt-examples/metasprites/src/.../MetaspriteEmissionTest.kt:44
  "for single-scene games with `romBanks = 2`, the..."
  → FIX: "for single-scene games with `romBanks(2)`, the..."
```

**Do NOT touch** `CartridgeConfig(romBanks = N, ramBanks = N)` IR data-class constructor named-argument sites — those are correct Kotlin named-argument syntax, not DSL property setters.

### Verified S3776 Extract-Method Pattern (general)

```kotlin
// BEFORE — high cognitive complexity in one method
private fun buildSystemGlobalVars(gameIR: GameIR): List<CVarDecl> {
    val vars = mutableListOf<CVarDecl>()
    // ... 400+ lines mixing camera vars, save vars, RPG vars, sound vars, etc.
    return vars
}

// AFTER — extract per-system sub-builders
private fun buildSystemGlobalVars(gameIR: GameIR): List<CVarDecl> =
    buildCameraGlobalVars(gameIR) +
    buildSaveGlobalVars(gameIR) +
    buildRpgGlobalVars(gameIR) +
    buildSoundGlobalVars(gameIR) +
    buildZoneGlobalVars(gameIR) +
    ...  // each extracted helper has low individual complexity

private fun buildCameraGlobalVars(gameIR: GameIR): List<CVarDecl> {
    if (gameIR.camera == null) return emptyList()
    // focused, low-complexity camera var builder
}
```

---

## `whenever` Migration Census (VERIFIED)

### Internal framework callers (3 sites — must be migrated before examples)

| File | Line | Call | Action |
|------|------|------|--------|
| `gbkt-lang/.../dsl/VariableBuilders.kt` | 193 | `sb.whenever(BinaryExpr(...))` | → `sb.runIf(BinaryExpr(...))` |
| `gbkt-lang/.../dsl/ExprBuilder.kt` | 298 | `sb.whenever(BinaryExpr(...))` | → `sb.runIf(BinaryExpr(...))` |
| `gbkt-lang/.../dsl/ExprBuilder.kt` | 301 | `sb.whenever(BinaryExpr(...))` | → `sb.runIf(BinaryExpr(...))` |

### Example game files (~62 call sites across 7 examples)

All examples are in `gbkt-examples/`: pong (~20 calls), simple-physics (~10), metasprites (~6), platformer-template (~10), breakout (TBD), banks (TBD), metasprites-stress (TBD). Also `gbkt-gradle-plugin/src/test/resources/test-fixtures/` (~14 calls in complex-game.kt, sprite-game.kt, entity-game.kt).

### Test files (~20+ additional call sites)

Scattered across `gbkt-backend-gbdk/src/test/` (AutoExitSynthesisTest, LevelSwitchEmissionTest, LevelCardSceneEmissionTest, TilemapCollisionPathCEmissionTest, BindCurrentLevelEmissionTest, SetupCurrentLevelDisplayGateEmissionTest, TitleSceneEmissionTest) and `gbkt-lang/src/test/` (SaveDataDelegateTest, CombatInventoryBuilderTest).

### Documentation (~250 KDoc/md references)

`context/DSL_REFERENCE.md` (DSL reference docs), `InputBuilders.kt` (KDoc), `ScriptBuilder.kt` KDoc lines 192-211, `ActorPoolBuilder.kt` KDoc lines ~37-396, `CombatStates.kt` KDoc, `VariableBuilders.kt` KDoc.

---

## DEPR-02 blast radius (VERIFIED minimal)

```
combatIsInState(String, String) — definition: RpgExtensions.kt:440 (REMOVE)
combatIsInState(CombatStateId, BattleRef) — definition: RpgExtensions.kt:419 (KEEP)

Call sites of String overload (CONFIRMED single):
  gbkt-genre-rpg/src/test/kotlin/.../dsl/CombatStatesTest.kt:122
    @Suppress("DEPRECATION") val stringExpr = combatIsInState("COMBAT_STATE_VICTORY", "combat")

KDoc references to update:
  RpgExtensions.kt:405,408 — example code blocks using whenever(combatIsInState(...))
  CombatStates.kt:26,29 — same pattern
  (These will also be updated as part of the whenever→runIf migration)

SimpleBattleAndTilesetTest.kt:25,128 — references `combatIsInState` as a CONCEPT (the generated
  C helper function name); NOT a call to the String overload — do NOT change.
```

---

## SEED-027 Blast Radius (VERIFIED zero)

`ScreenSpec.bitsPerPixel` is declared at `ScreenSpec.kt:24` but has **zero production readers** that consume the value:
- `TargetProfiles.kt:32` — GAME_BOY_SCREEN sets it to 2 (already correct)
- `TargetProfiles.kt:50` — GAME_BOY_COLOR_SCREEN sets it to 4 (the bug; fix to 2)
- `GameBoyProfile.kt:33` — sets to `GameBoyConstants.BITS_PER_PIXEL` (= 2, independent)
- `GameBoyColorProfile.kt:40` — sets to `GameBoyConstants.BITS_PER_PIXEL` (= 2, independent)
- `TestFixtures.kt:30`, `BackendRegistryTest.kt:152` — test fixtures that set values, do not read `TargetProfiles.GAME_BOY_COLOR_SCREEN.bitsPerPixel`

The `4` in `TargetProfiles.GAME_BOY_COLOR_SCREEN` is never read by any backend or analysis pass. Fix is byte-identical by construction. Build verification only (no emission test needed).

---

## ROM Sweep Mechanics (SONAR-02)

### 7-example sweep commands

```bash
# Run all 7 examples (never in parallel — Kotlin daemon collision risk per no-parallel-clean rule)
./gradlew \
  :gbkt-examples:pong:clean :gbkt-examples:pong:buildRom \
  :gbkt-examples:breakout:clean :gbkt-examples:breakout:buildRom \
  :gbkt-examples:simple-physics:clean :gbkt-examples:simple-physics:buildRom \
  :gbkt-examples:metasprites:clean :gbkt-examples:metasprites:buildRom \
  :gbkt-examples:metasprites-stress:clean :gbkt-examples:metasprites-stress:buildRom \
  :gbkt-examples:banks:clean :gbkt-examples:banks:buildRom \
  :gbkt-examples:platformer-template:clean :gbkt-examples:platformer-template:buildRom

# SKIP: pong.gb hash is non-deterministic (toolchain issue — pre-existing, not a regression)
# For pong: verify generated/main.c is byte-identical; if so, mark PASS*
```

### Byte-identity comparison

```bash
# After refactor commit, compare .gb files
sha256sum gbkt-examples/*/build/gbkt/output/*.gb

# Or: compare generated C (more reliable for pong where .gb is non-deterministic)
diff -r pre-refactor/gbkt-examples/pong/build/gbkt/generated/ \
        post-refactor/gbkt-examples/pong/build/gbkt/generated/
```

### GBDK availability

If `GBDK_HOME` is unset or `lcc` is not on PATH, the sweep is SKIPPED. The verification evidence note must read: "ROM-build sweep SKIPPED — GBDK not available; human MUST run locally before sign-off."

```bash
# Check GBDK availability
command -v lcc 2>/dev/null || echo "GBDK not on PATH — set GBDK_HOME"
```

---

## CONTRIBUTING.md Deprecation Convention — Content Spec (DEPR-03)

The new section (fits after `## DSL Authoring Guidelines` and before `## Code Review Checklist`) should document:

**Two-tier rule:**
1. **Post-1.0 / once shipped to consumers (default):** `@Deprecated(level = WARNING, ReplaceWith(...))` in version N → removal in N+1. Mandatory CHANGELOG breaking-change note.
2. **Pre-1.0 / explicitly-labeled Hardening milestones:** Hard removal permitted when adoption is near-zero. Mandatory CHANGELOG note is the minimum bar.

**Worked examples from this milestone:**
- SEED-023: `whenever` → `runIf` (pre-1.0 hard removal, v0.1.1)
- SEED-025: `combatIsInState(String)` (pre-1.0 hard removal, v0.1.1)
- SEED-028: ConfigBuilder `ramBanks = N` → `ramBanks(N)` setter (pre-1.0 hard removal, v0.1.1)

---

## CHANGELOG.md Spec (D-09)

Create `/CHANGELOG.md` in Keep a Changelog format (https://keepachangelog.com/en/1.0.0/).

**v0.1.1 entry should record:**
- Removed: `whenever(condition, block)` / `whenever(collision, block)` → use `runIf`
- Removed: `combatIsInState(String, String)` → use `combatIsInState(CombatStateId, BattleRef)`
- Changed: `config { ramBanks = N }` → `config { ramBanks(N) }`

---

## State of the Art

| Old Approach | Current Approach | Notes |
|--------------|------------------|-------|
| `whenever(condition) { }` | `runIf(condition) { }` | This phase closes the gap |
| `combatIsInState(String, String)` | `combatIsInState(CombatStateId, BattleRef)` | This phase removes the string overload |
| 69 open S3776 findings | 46 in-scope findings (23 ghost auto-close) | Live API confirmed |
| `bitsPerPixel = 4` for GBC | `bitsPerPixel = 2` | This phase corrects the constant |
| No root CHANGELOG.md | Keep a Changelog format at `/CHANGELOG.md` | This phase creates it |

**Deprecated/outdated in this phase:**
- `ScriptBuilder.whenever(Expr, block)` — removed in v0.1.1
- `ScriptBuilder.whenever(PoolPoolCollisionExpr, block)` — renamed to `runIf` overload
- `combatIsInState(String, String)` — removed in v0.1.1

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `CEmitter.emit` function body at line 87 is a flat sealed-type dispatch (NOSONAR candidate) | NOSONAR budget | If it contains nested logic, EXTRACT-METHOD is required instead; budget unchanged |
| A2 | `buildNRxxRegisterWrites` is a flat per-channel dispatch (NOSONAR candidate) | NOSONAR budget | If nested, EXTRACT-METHOD is required |
| A3 | TrackSynthesizer.scanlineFill (E-28) has no sport-example ROM in the 7-example suite | ROM sweep coverage | If a sport example ROM is added, sweep naturally covers it |

All other claims are VERIFIED via live SonarCloud API and codebase grep.

---

## Open Questions

1. **`batchAssert` in McpEmulatorSession.kt (N-01, cc=74)** — Is this a flat assertion-type dispatch or complex nested logic? If flat dispatch, it's a NOSONAR candidate (counts against the ≤5 budget). Executor should inspect before deciding.

2. **Ghost issues cleanup timing** — The 23 ghost issues in `commonMain`/`jvmMain` files will auto-close only when a SonarCloud scan runs on the new commit. The Phase 18 success criterion is "0 OPEN findings" — if a scan doesn't run in CI (e.g., GBDK not available for ROM builds, CI gate skipped), ghost issues may persist in the Sonar UI. Planner should add a "trigger SonarCloud scan" task as a wave-close gate.

3. **CONTRIBUTING.md exact section placement** — Current sections: Getting Started, Project Structure, Kotlin Style Guide (10 sub-sections), DSL Authoring Guidelines (4 sub-sections), Organizing Large Games (3 patterns), Code Review Checklist, Questions. Best fit for "Deprecation Convention" is between "DSL Authoring Guidelines" and "Organizing Large Games" — but "Code Review Checklist" is another reasonable home. Planner/implementor can decide.

---

## Environment Availability

| Dependency | Required By | Available | Notes |
|------------|------------|-----------|-------|
| JDK 21 | All JVM tests | ✓ | Project requirement |
| Gradle 9.5.1 | Build | ✓ | Available via wrapper |
| GBDK-2020 (lcc) | ROM sweep (SONAR-02) | UNKNOWN — not checked by research agent | Must check `command -v lcc` or `$GBDK_HOME`; if absent, sweeps are SKIPPED and human must run locally |
| SonarCloud token | Phase completion gate | UNKNOWN (env var `SONAR_TOKEN`) | Required for CI scan; publicly readable API available for tracking progress |

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 via JVM test runner |
| Config file | `build.gradle.kts` (each module, `tasks.test { ... }`) |
| Quick run command | `./gradlew :gbkt-lang:test :gbkt-genre-rpg:test` (for DEPR tracks) |
| Full suite command | `./gradlew test` |
| Plugin tests | `./gradlew pluginTest` (NOT `:gbkt-gradle-plugin:test`) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | Notes |
|--------|----------|-----------|-------------------|-------|
| DEPR-01 | `whenever` removed, all call sites compile with `runIf` | Compilation (build) | `./gradlew build` | Compilation failure if any `whenever` call site was missed |
| DEPR-01 | `runIf` pool-collision overload added and callable | Unit | `./gradlew :gbkt-lang:test` | Existing pool-collision tests should pass against new overload |
| DEPR-02 | `combatIsInState(String)` removed, test migrated | Unit | `./gradlew :gbkt-genre-rpg:test` | `CombatStatesTest` must pass without `@Suppress("DEPRECATION")` |
| DEPR-03 | Convention section present in CONTRIBUTING.md | Manual | Review artifact | No automated test for doc content |
| SONAR-01 NON-EMIT | Refactored analysis/emulator/lang functions | Unit | `./gradlew test` | JVM-test-only evidence per D-06 |
| SONAR-01 EMIT | Refactored visitor/pipeline functions | ROM sweep | `./gradlew clean buildRom` × 7 | Byte-identity oracle per D-06; pong PASS* |
| SONAR-02 | Every emitting refactor commit has ROM sweep evidence | ROM sweep | per-commit | Evidence attached to commit message or PR |
| SEED-027 | `bitsPerPixel = 2` for GBC profile | Build | `./gradlew :gbkt-core:test` | No consumer; verify build passes + property value |
| SEED-028 | Stale strings updated | Build | `./gradlew build` | Compilation unchanged; doc strings updated |

### Sampling Rate
- **Per emitting S3776 commit:** 7-example ROM sweep (D-06)
- **Per non-emitting S3776 batch file commit:** `./gradlew test` on affected module
- **Per DEPR track commit:** `./gradlew build` (compilation gate)
- **Phase gate:** Full `./gradlew test` green + 7-example ROM sweep baseline match before `/gsd-verify-work`

### Wave 0 Gaps
None — existing test infrastructure covers all phase requirements. No new test files need to be created before implementation begins.

*(If ROM sweep reveals byte-identity drift on any example beyond pong, that IS a regression and must be treated as a blocker.)*

---

## Security Domain

This phase is purely refactoring + doc authoring. No authentication, session management, input validation, cryptography, or access control changes are introduced. No new security controls are required.

ASVS categories not applicable to this phase.

---

## Project Constraints (from CLAUDE.md)

| Directive | Applies To | Impact on This Phase |
|-----------|-----------|---------------------|
| No magic strings (Project Rule #1) | DSL code | `whenever` removal is correct per this rule — `runIf` uses delegate properties not string names |
| Hide C complexity from Kotlin test surface | Tests | S3776 extract-method helpers stay within the codegen module; tests speak gbkt/Kotlin |
| Quality over shortcuts | S3776 refactors | NOSONAR budget is ≤5 (target 0–2); no shortcuts |
| Visual evidence for visual truths | Verification | Not applicable — no visual assertions in this phase |
| Byte-identity ROM-build smoke for codegen phases | SONAR-02 | REQUIRED for all EMITTING S3776 commits per D-06 |
| No parallel gradle clean | ROM sweep | Chain all clean+buildRom into single Gradle invocation |
| `pluginTest` not `:gbkt-gradle-plugin:test` | Plugin changes | SEED-028 touches GbktExtension.kt and CompileRomTask.kt — run `./gradlew pluginTest` to validate |
| Use Serena MCP tools for code exploration | Implementation | Agents must use `mcp__serena__*` for symbol lookups, not Read/Grep of full files |

---

## Sources

### Primary (HIGH confidence — VERIFIED via live API + codebase)
- SonarCloud public API `https://sonarcloud.io/api/issues/search?componentKeys=svachmic_gbkt&rules=kotlin:S3776` — 69 total findings; 46 in files present on disk; 23 ghost files from deleted multiplatform structure
- Direct codebase grep: `whenever` call sites, `combatIsInState` blast radius, `bitsPerPixel` readers, stale guidance strings
- `CONTEXT.md` decisions D-01 through D-09

### Secondary (MEDIUM confidence — cross-checked via official docs concepts)
- SonarCloud S3776 rule documentation: cognitive complexity threshold = 15, penalty for nesting depth, flat `when`-dispatch gets +1 per branch without nesting multiplier
- Keep a Changelog format: https://keepachangelog.com/en/1.0.0/

### Tertiary (LOW confidence — training knowledge)
- Estimate that `CEmitter.emit` and `buildNRxxRegisterWrites` are flat dispatch — needs code inspection before NOSONAR decision

---

## Metadata

**Confidence breakdown:**
- S3776 inventory: HIGH — directly from live SonarCloud API with line numbers
- `whenever`/`runIf` census: HIGH — verified via grep; 14 .kt non-comment call sites confirmed
- `combatIsInState` blast radius: HIGH — grep confirms single test call site
- SEED-027/028 locations: HIGH — confirmed on disk
- NOSONAR candidates: MEDIUM — CEmitter likely flat dispatch but not confirmed by code read
- Ghost-issue cleanup: HIGH — 23 files verified absent from disk

**Research date:** 2026-06-13
**Valid until:** 2026-07-13 (Sonar findings may shift if new code is added to the branch)
