---
phase: 17-docs-reconciliation-and-quality-cleanup
reviewed: 2026-06-12T21:23:05Z
depth: standard
files_reviewed: 33
files_reviewed_list:
  - .github/workflows/kotlin.yml
  - context/DSL_REFERENCE.md
  - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BankingAnalysisPass.kt
  - gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/report/BudgetReporter.kt
  - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ActorVisitor.kt
  - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/CombatVisitor.kt
  - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/GBDKSystemVisitor.kt
  - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/RpgVisitor.kt
  - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/profiles/GameBoyConstants.kt
  - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/ExplorationCodegenTest.kt
  - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/SimpleBattleAndTilesetTest.kt
  - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/SpritePaletteSlotEmissionTest.kt
  - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/ZoneTilemapBankingTest.kt
  - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/MenuCodegenTest.kt
  - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ScriptOpVisitorTest.kt
  - gbkt-core/src/main/kotlin/io/github/gbkt/core/constraints/TargetProfiles.kt
  - gbkt-core/src/main/kotlin/io/github/gbkt/core/optimization/ConsoleReporter.kt
  - gbkt-core/src/main/kotlin/io/github/gbkt/core/PngValidator.kt
  - gbkt-core/src/main/kotlin/io/github/gbkt/core/TiledParser.kt
  - gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/StepAgentTest.kt
  - gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/ui/MemoryInspectorPanelTest.kt
  - gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateEmissionTest.kt
  - gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt
  - gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/RpgExtensions.kt
  - gbkt-gradle-plugin/build.gradle.kts
  - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/CompileRomTask.kt
  - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/SetupClaudeTask.kt
  - gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/debug/EntityPreviewPanel.kt
  - gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/editors/strings/PoEditorPanel.kt
  - gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/GbktLanguage.kt
  - gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/toolwindow/GbktToolWindowPanel.kt
  - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SystemBuilders.kt
  - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/VariableBuilders.kt
findings:
  critical: 0
  warning: 9
  info: 5
  total: 14
status: issues_found
---

# Phase 17: Code Review Report

**Reviewed:** 2026-06-12T21:23:05Z
**Depth:** standard
**Files Reviewed:** 33
**Status:** issues_found

## Summary

Reviewed all 33 phase-17 files against the diff range `aef056cc..HEAD` (milestone v0.1.1 start). The core deliverables hold up under adversarial inspection:

- **160/144 literal replacement (17-05):** All 8 replaced sites (`ActorVisitor.kt:472/498`, `GBDKSystemVisitor.kt:173-174`, `PlatformerVisitor.kt:1990/1993/2008/2011`) are arithmetic-equivalent to the literals they replace; byte-identity is preserved by construction since `GameBoyConstants.SCREEN_WIDTH/HEIGHT` resolve to 160/144 via `TargetProfiles.GAME_BOY_SCREEN`. No const-required usage sites (annotations, other `const val`s) of the demoted `const val → val` constants exist, so the demotion is safe within the repo.
- **CI workflow (kotlin.yml):** The new comment about the composite detekt bridge is accurate — `build.gradle.kts:213-214` contains the claimed `tasks.named("detekt") { dependsOn(gradle.includedBuild(...).task(":detekt")) }` wiring, and the composite's `detekt {}` block correctly reaches the shared `detekt.yml` via `${rootDir}/../detekt.yml`.
- **ConfigBuilder migration:** No remaining compilable usages of the removed property setters; all tests/fixtures migrated; `DSL_REFERENCE.md` config section uses the new function-setter syntax consistently and its quoted error message matches `BankingAnalysisPass.bankOverflowError` verbatim.
- **DSL_REFERENCE spot-verification:** Sampled 14 documented DSL identifiers (`easeToZero`, `wrapAt`, `i16FixedVar`, `toPixel`, `bindCurrentLevel`, `runIf`, `orElse`, `cameraOp`, `simpleBattle`, `battleUpdate`, `screen`, `spritePalette`, `subpixel`, `unless`) — all exist in source. The "implemented-only truth" claim holds for the sample.
- **PngValidator/TiledParser/ConsoleReporter/BudgetReporter/BankingAnalysisPass:** Pure refactors (De Morgan inversion, named-constant extraction) are semantics-preserving.

However, the review found 9 warnings: a factually wrong "single source of truth" GBC screen preset that nothing consumes, a doc/implementation contradiction in the new teardown-hook mechanism, a silent-failure path that can permanently disable the RpgRegistry leak fix it ships with, user-facing deprecation guidance that now instructs non-compiling syntax, a test class left with zero tests, and several places where dead-variable cleanup codified tests that do not assert what their names and comments claim.

## Warnings

### WR-01: `TargetProfiles.GAME_BOY_COLOR_SCREEN` is an unused "single source of truth" that contradicts the real GBC profile

**File:** `gbkt-core/src/main/kotlin/io/github/gbkt/core/constraints/TargetProfiles.kt:46-56`
**Issue:** The new GBC preset declares `bitsPerPixel = 4`, while the actually-consumed GBC spec (`GameBoyColorProfile.kt:40`) uses `GameBoyConstants.BITS_PER_PIXEL` = 2 with the explicit comment "Still 2bpp tiles, but with palettes" (GBC tiles are 2bpp in hardware; palettes provide the extra colors). Furthermore, `GAME_BOY_COLOR_SCREEN` has zero consumers — only `GAME_BOY_SCREEN.width/height` are read (by `GameBoyConstants`). The object's KDoc asserts "All backends and constants that need these values MUST derive from this object", but `GameBoyProfile`/`GameBoyColorProfile` still build their own `ScreenSpec` instances from `GameBoyConstants`. The first future consumer of this preset will inherit wrong VRAM/bpp math from a source explicitly labeled canonical.
**Fix:** Either align the preset with the shipped profile (`bitsPerPixel = 2`) and make `GameBoyColorProfile.screen = TargetProfiles.GAME_BOY_COLOR_SCREEN` (and `GameBoyProfile.screen = TargetProfiles.GAME_BOY_SCREEN`), or delete `GAME_BOY_COLOR_SCREEN` until SEED-TARGETPROFILE-SCREEN-THREADING lands and soften the "MUST derive" claim to cover only width/height.

### WR-02: `GameBuilderContext` teardown-hook KDoc contradicts the implementation for nested `with()` calls

**File:** `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/VariableBuilders.kt:60-61` (vs implementation at 90-112)
**Issue:** The KDoc states "Hooks are scoped to the outermost `game { }` call — nested `with()` calls (if any) share the same hook list and only invoke it on the final `finally` restoration." The implementation does the opposite: every `with()` call installs a **fresh** `mutableListOf()` (line 96), and each nesting level invokes **its own** hooks in **its own** `finally` (line 101) before restoring the parent list. Hooks registered inside a nested `with()` therefore fire at the inner scope's exit, not at the final restoration, and lists are never shared. Whoever later relies on the documented outermost-scope semantics (e.g. a genre module caching state across nested builders) will get early teardown.
**Fix:** Correct the KDoc to describe per-`with()` scoping:
```kotlin
 * Hooks are scoped to the `with()` call during which they were registered — each (possibly
 * nested) `with()` installs its own hook list and invokes those hooks in its own `finally`
 * block before restoring the enclosing scope's list.
```
(or, if outermost-scope semantics are actually desired, change line 96 to reuse the existing list when `previousHooks != null` and only invoke when `previous == null`).

### WR-03: RpgRegistry one-shot hook registration plus silent ignore can permanently re-introduce the cross-build leak

**File:** `gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/RpgExtensions.kt:66-74` (with `VariableBuilders.kt:86-88`)
**Issue:** `RpgRegistry.current()` registers the `::clear` teardown hook only on first initialization of the thread-local map (`holder.get() == null`). `GameBuilderContext.addTeardownHook` silently no-ops when no `game { }` context is active (documented at `VariableBuilders.kt:84`). Combined: if the registry's first use on a thread ever happens outside an active `with()` (e.g. a helper that registers a `CharacterDef` against a manually constructed `GameBuilder`, or future API surface that touches the registry pre-`game{}`), the hook is dropped, the map is never cleared, **and** every subsequent `game { }` on that thread sees a non-null map and never re-registers the hook — silently restoring the exact stale-entry leak across Gradle-daemon/JUnit builds that this change was built to fix. There is no diagnostic on either failure leg.
**Fix:** Make the registration resilient instead of one-shot, e.g. register on every game-scope entry rather than on map creation:
```kotlin
private fun current(): MutableMap<String, Any> {
    // Re-register on every access while a game{} is active; addTeardownHook is cheap and
    // duplicate clear() invocations are idempotent (holder.remove()).
    io.github.gbkt.core.dsl.GameBuilderContext.addTeardownHook(::clear)
    return holder.get() ?: mutableMapOf<String, Any>().also { holder.set(it) }
}
```
or have `addTeardownHook` return a Boolean / log a debug line when ignored so the dropped-hook leg is observable.

### WR-04: ConfigBuilder property setters removed with no deprecation cycle (hard source break in a 0.1.x hardening milestone)

**File:** `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SystemBuilders.kt:541-545` (private `_cartridge`/`_romBanks`/`_ramBanks`/`_gbcTarget`)
**Issue:** v0.1.0 shipped `config { cartridge = ...; romBanks = N; ramBanks = N; gbcTarget = ... }` as public mutable properties. Phase 17-11 deletes them outright in favor of function setters. Any external game written against 0.1.0 fails to compile against 0.1.1 with no migration signal beyond "unresolved reference". The internal migration is complete (verified: zero remaining property-syntax usages compile in-repo), but external consumers get a cliff instead of a ramp.
**Fix:** Reintroduce the four properties as `@Deprecated(message = "Use cartridge(type)/romBanks(n)/ramBanks(n)/target(mode)", replaceWith = ReplaceWith(...), level = DeprecationLevel.ERROR)` `var`s delegating to the private fields for one release, then delete. This preserves the function-setter convention while giving compiler-guided migration.

### WR-05: Deprecation guidance and code comments still instruct the removed `config { ramBanks = N }` property syntax

**File:** `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/GbktExtension.kt:166`
**Issue:** The user-facing `@deprecated` KDoc on `GbktExtension.ramBanks` says "Set `ramBanks` in the DSL `config { ramBanks = N }` block instead" — that exact syntax no longer compiles after 17-11. A user following the framework's own migration advice hits an unresolved-reference error. Same stale syntax survives in `CompileRomTask.kt:319` (comment), `gbkt-examples/platformer-template/.../PlatformerTemplate.kt:61` ("add back `romBanks = 8`"), and `gbkt-examples/metasprites/.../MetaspriteEmissionTest.kt:44` (comment). For a phase whose charter was cross-doc consistency (D-16 "zero stale references"), these are misses in the exact API the phase changed.
**Fix:** Update all four sites to function-setter syntax, e.g. `GbktExtension.kt:166`:
```kotlin
 * @deprecated Set `ramBanks` in the DSL `config { ramBanks(N) }` block instead. ...
```

### WR-06: PlatformerTemplateEmissionTest is now a test class with zero tests and a header that promises invariants it does not contain

**File:** `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateEmissionTest.kt:12-43`
**Issue:** Phase 17 deleted the unused `extractFunctionBody()` brace-walk helper, which was the *only* content besides the `EVIDENCE_DIR` companion. The class now contains no `@Test` methods at all, yet its header still claims "tests bound to the 5 UAT anchors (D-16 invariants 1..5)" arrive via plans 12-09/12-09b/12-12/12-14/12-15 and that "every invariant runs against a brace-walked function body" — the locking pattern those future tests were supposed to reuse is the thing that was deleted. The file is a dead scaffold: it asserts nothing, guards nothing, and its companion points at a phase-12 evidence directory. The narrow "delete unused symbol" cleanup made the file's stated purpose unfulfillable without restoring the helper.
**Fix:** Either delete the file (the platformer-template invariants live elsewhere or were never written), or restore the brace-walk helper and add at least one anchor invariant test so the class earns its name. Do not leave a zero-test class whose header advertises a regression-guard suite.

### WR-07: `zone_transition contains all four edge cases` test is vacuous and retains an unused variable the cleanup missed

**File:** `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/ExplorationCodegenTest.kt:393-409`
**Issue:** Phase 17 deleted three of four unused ordinal locals (`northOrdinal`, `southOrdinal`, `westOrdinal`) but left the fourth — `eastOrdinal` at line 401 is equally unused in this test (lines 404-408 never reference it). More substantively, the test named "contains all four edge cases" with the comment "All 4 direction ordinals should appear in the edge switch" asserts only that the function name exists and that `_player_x`/`_player_y` appear *somewhere in main.c*. It would pass with zero edge cases generated. The dead-variable deletion silently codified that the promised assertion never existed.
**Fix:** Remove the leftover `eastOrdinal` at line 401, and either assert all four ordinals inside the brace-walked `zone_transition_dungeon` body (e.g. `assertTrue(body.contains("case ${TransitionEdge.NORTH.ordinal}"))` for each direction) or rename the test/comment to match what it actually checks.

### WR-08: Shared-tileset test asserts nothing about tileset sharing; comment promises a check that does not exist

**File:** `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/SimpleBattleAndTilesetTest.kt:258-290`
**Issue:** The test `two scenes with same tilesetRef share same TILESET_ID constant` keeps the comments "Both scenes should use the SAME TILESET_ID (not two different ones)" and "One of them should be the canonical tileset ID", but the only assertion is `bank1C.contains("_current_tileset_id")` — which passes whether the scenes share one ID or emit two. The deleted `tileset1Count`/`tileset2Count` locals were the raw material for the real assertion (e.g. exactly one of the two IDs appears); deleting them as "unused" locked in a test that cannot detect the regression its name describes (duplicate TILESET_ID emission for a shared `tilesetRef`).
**Fix:** Restore the counts and assert the sharing property, e.g.:
```kotlin
val floor1Refs = bank1C.split("TILESET_ID_FLOOR1").size - 1
val floor2Refs = bank1C.split("TILESET_ID_FLOOR2").size - 1
assertTrue(floor1Refs == 0 || floor2Refs == 0,
    "Scenes sharing a tilesetRef must reuse one TILESET_ID (found FLOOR1=$floor1Refs, FLOOR2=$floor2Refs)")
```

### WR-09: New `gbkt-build.properties not found` warning also fires when the file exists but lacks `mbcType`

**File:** `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/CompileRomTask.kt:285-290`
**Issue:** The warning is reached by two paths in `readMbcType`: (a) `propsFile` missing, and (b) `propsFile` exists but `props.getProperty("mbcType")` is null (control falls out of the `if (propsFile.exists())` block at line 282). In case (b) the message "gbkt-build.properties not found" is false and will send the developer chasing a file that is sitting right there — the actual problem is a stale/partial properties file (e.g. written by an older GenerateCTask). Since 17-11's purpose was precise diagnostics for this exact fallback, the misdiagnosis matters.
**Fix:** Differentiate the two legs:
```kotlin
val reason = if (propsFile.exists()) {
    "gbkt-build.properties exists but has no mbcType entry (stale generateC output?)"
} else {
    "gbkt-build.properties not found"
}
logger.warn("$reason — MBC5 is assumed for banking. Run generateC first, or declare " +
    "`config { cartridge(Cartridge.MBC5) }` in your game DSL to silence this.")
```

## Info

### IN-01: `GB_MAX_TILE_COUNT` KDoc mislabels a count as a 0-based index

**File:** `gbkt-core/src/main/kotlin/io/github/gbkt/core/TiledParser.kt:27-28`
**Issue:** The KDoc reads "Maximum VRAM tile index (0-based), matching the 256-entry tile table" — but 256 is the entry *count*; the maximum 0-based index is 255. The constant name (`GB_MAX_TILE_COUNT`) and the usage (`totalTiles <= GB_MAX_TILE_COUNT`) are correct; only the doc is wrong.
**Fix:** "Maximum number of unique tiles (indices 0..255) in the 256-entry tile table."

### IN-02: `romBanks()` KDoc references a wrong, unresolvable FQN

**File:** `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SystemBuilders.kt` (romBanks KDoc)
**Issue:** The KDoc links `[io.github.gbkt.analysis.BankingAnalysisPass]` — the actual class is `io.github.gbkt.analysis.passes.BankingAnalysisPass`, and `gbkt-lang` has no dependency on `gbkt-analysis`, so the link can never resolve in Dokka either way.
**Fix:** Use plain text: "derives the correct count from the banking analysis pass (`BankingAnalysisPass` in gbkt-analysis)".

### IN-03: Teardown hooks swallow all exceptions with no logging

**File:** `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/VariableBuilders.kt:104-106`
**Issue:** `catch (_: Exception)` discards hook failures silently. A genre module whose cleanup throws (e.g. a future hook that flushes state) will fail invisibly, leaving partially-torn-down thread-locals with zero signal. Best-effort teardown is a fine policy, but invisible best-effort is not.
**Fix:** Emit a diagnostic before discarding, e.g. `System.err.println("gbkt: teardown hook failed: $e")` or route through a logger if one is available in gbkt-lang.

### IN-04: SetupClaudeTask leaks the skill resource InputStream

**File:** `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/SetupClaudeTask.kt:64-68`
**Issue:** `getResourceAsStream(...)` is consumed via `content.readBytes()` without `use { }` — the stream is never closed. In a long-lived Gradle daemon this leaks a jar-entry stream per skill per run. The line was touched this phase (the `?: error(...)` change), so it was in scope for the fix.
**Fix:** `.use { target.writeBytes(it.readBytes()) }`.

### IN-05: Dead-variable cleanups left tests whose names overstate their assertions

**File:** `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/MenuCodegenTest.kt:316-320`; `gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/ui/MemoryInspectorPanelTest.kt:276-288`
**Issue:** In MenuCodegenTest the deleted `hasHideCall` was the only specific check for the slot-38 hide call; what remains (`output.contains("move_sprite") && output.contains(", 0")`) matches nearly any generated file. In MemoryInspectorPanelTest, `panel refresh delegates to both tabs` verifies only the named-variables tab after `refreshCount` was removed; hex-tab delegation is unverified. Both weaknesses pre-date phase 17, but the cleanup made them permanent without a TODO or rename.
**Fix:** Either strengthen the assertions (assert `move_sprite(38` for the cursor-hide; assert an observable hex-tab effect) or rename the tests to match what they actually verify.

---

_Reviewed: 2026-06-12T21:23:05Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
