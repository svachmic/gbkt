# Phase 17: Docs Reconciliation and Quality Cleanup - Research

**Researched:** 2026-06-12
**Domain:** Documentation accuracy audit, detekt static analysis, magic-literal elimination
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01:** Exclusion-removal targets globally-disabled rules only: re-enable `MagicNumber`, `UnusedPrivateMember`, `UnusedPrivateProperty`, `ComplexCondition` and fix what they flag. Principled path-based complexity exclusions (`**/codegen/**`, `**/ir/**`, `**/dsl/**`, etc. on LongMethod/TooManyFunctions/LongParameterList/LargeClass) are kept — Phase 18 territory.

**D-02:** `MagicNumber` is re-enabled in targeted form: active globally with documented path excludes for generated-code-emitting internals (`codegen/`, `test/`) and an `ignoreNumbers` list for idiomatic values. User-facing modules (lang, engine, examples, gradle-plugin) get full enforcement. Every new exclude carries a rationale comment matching the existing detekt.yml style.

**D-03:** Composite build coverage via apply + root-task bridge: detekt plugin applied inside `gbkt-gradle-plugin/build.gradle.kts` (sharing root `detekt.yml`), root `detekt` task depends on composite's detekt task via `gradle.includedBuild(...)`. Plain `./gradlew detekt` covers everything.

**D-04:** Dead `baseline = file("detekt-baseline.xml")` wiring in `build.gradle.kts` (both apply sites) is deleted entirely. No baseline files committed.

**D-05:** Canonical constant home: backend/genre codegen uses existing `GameBoyConstants.SCREEN_WIDTH/SCREEN_HEIGHT` (`gbkt-backend-gbdk/.../profiles/GameBoyConstants.kt`). A canonical Game Boy `ScreenSpec`/`TargetProfile` preset is added in `gbkt-core`, `GameBoyConstants` derives from it, so numbers live in exactly one place.

**D-06:** Replacement is mechanical — each in-scope literal swaps for the named constant; generated C is byte-identical. Threading `TargetProfile.screen` through codegen visitors is deferred to v0.2.0 backlog seed.

**D-07:** Exemption policy: replace literals in framework code paths (backend-gbdk visitors, genre modules, anything emitting C). Documented-exempt: `gbkt-emulator` (implements the physical 160×144 LCD), `gbkt-intellij-plugin` (preview rendering, no gbkt-core dependency), KDoc/comment mentions, and CLI template strings that are generated game source.

**D-08:** QUAL-03 enumeration evidence: a scripted repo-wide 160/144 sweep is committed as phase evidence; every remaining hit appears in an exemption table with rationale. Regression guard = re-enabled MagicNumber rule in user-facing modules.

**D-09:** Pruned spec content goes to per-subsystem files in `.planning/backlog/v0.2.0/` (e.g., `FEAT-STATE-MACHINES.md`, `FEAT-TWEENING.md`). Existing FEAT-XX placeholder in REQUIREMENTS.md "Future Requirements" expands into individual indexed entries.

**D-10:** DSL_REFERENCE.md gets clean removal, no pointers — strictly implemented-only documentation; no "planned for v0.2.0" breadcrumbs in the reference doc.

**D-11:** Archived content is preserved verbatim with provenance: each backlog file carries the removed doc section verbatim (code samples intact) plus a provenance header — source line range, removal commit, and note on what IS implemented today.

**D-12:** Partially-implemented sections (camera, save, physics, items, dialogs, menus, ...) get a full rewrite from source: implemented methods documented accurately against the actual builder source; aspirational parts move to backlog; stale-API caveat banners disappear because the docs are simply true.

**D-13:** Audit scope: deep per-method audit on the 13 caveated sections plus one cheap full-document triage sweep that only flags suspect uncaveated sections.

**D-14:** Snippet accuracy bar: every rewritten snippet is lifted or adapted from in-tree code that compiles today (example games, tests, builder KDoc), with the source file recorded in the audit evidence.

**D-15:** Audit produces a committed evidence artifact: per-section audit tables in `.planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/`.

**D-16:** Grep-driven cross-doc consistency pass: for each pruned/renamed API, grep the other docs and fix hits in the same plan. CLAUDE.md stays a routing index — no quick-refs re-added.

**D-17:** Phase 16 D-14 byte-identity gate: any Phase 17 commit touching codegen modules (`gbkt-backend-gbdk`, `gbkt-genre-*`) must leave the 7-example ROM sweep byte-identical (pong PASS\*).

**D-18:** Never run parallel `gradle clean` invocations — chain ROM sweep targets in a single Gradle invocation or run serially.

### Claude's Discretion

- Exact `ignoreNumbers` list and path-exclude set for the MagicNumber re-enable.
- Naming of the core Game Boy preset (e.g., `TargetProfiles.GAME_BOY`, `ScreenSpec.GAME_BOY`) and derivation mechanism for `GameBoyConstants`.
- Where the QUAL-03 exemption table lives (phase evidence vs a durable doc).
- Backlog file naming for the pruned subsystems (FEAT-* slugs) and how the 13 sections group into files.
- Plan sequencing between docs work and quality work (independent clusters; parallelize as plan waves if useful).

### Folded Todos (in scope)

- **MBC5 silent-fallback warning** — CompileRom should warn (not silently fall back to MBC5) when cartridge metadata is missing.
- **ConfigBuilder setter consistency** — unify function-vs-var setter convention per field in `ConfigBuilder`. DSL_REFERENCE config{} section must document the unified convention. Codegen output must remain identical (D-17 applies).
- **RpgRegistry.clear() never called** — call `RpgRegistry.clear()` on `game{}` teardown or remove the dead method.

### Deferred Ideas (OUT OF SCOPE)

- TargetProfile.screen threading through codegen visitors (multi-target support) — v0.2.0 backlog seed.
- DSL/codegen behavior bugs (easeToZero, wrapAt, orElse attachment) — belong to fix phases 19–21 or backlog.
- 13.6-07-convertsprites-hardening-followups.md — asset-pipeline code changes, byte-identity-sensitive.
- Phase 18 scope: path-based complexity exclusions, S3776 extract-method refactors, DEPR-01/02/03. Phase 17 must not refactor those files.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DOCS-01 | Each of the 13 stale-API sections in `context/DSL_REFERENCE.md` is audited per-method against source; implemented APIs keep accurate, corrected documentation | Section 3 below: complete per-section inventory with implemented vs. aspirational verdict |
| DOCS-02 | Unimplemented/aspirational API content is removed from DSL_REFERENCE.md and archived as tracked v0.2.0 feature candidates (no spec value silently lost) | D-09/D-10/D-11 from CONTEXT.md; backlog file format from existing v0.2.0/ entries |
| DOCS-03 | The 2 doc-only fixes are applied (deprecated-API example block, `subpixel {}` no-op clarification) | Section 4 below: exact location of both fixes |
| QUAL-01 | Detekt violations cleared via exclusion-removal from `detekt.yml` (no committed baseline files); detekt coverage extended to the `gbkt-gradle-plugin` composite build | Section 5 below: current detekt state, what needs to change |
| QUAL-02 | Magic 160/144 pixel literals replaced with platform-aware screen constants | Section 6 below: exact 160/144 sites in framework code, what constant to use |
| QUAL-03 | Remaining magic-pixel literals eliminated (in-scope set enumerated at phase spec; intentional hardware constants exempt) | Section 6 below: complete enumeration + exemption rationale |
</phase_requirements>

---

## Summary

Phase 17 is a three-track cleanup: documentation accuracy (DOCS-01/02/03), detekt static analysis (QUAL-01), and magic-literal elimination (QUAL-02/03). All tracks are independent and can proceed in parallel waves.

**Documentation track:** DSL_REFERENCE.md has exactly 13 "Stale-API caveat" blocks confirmed by grep. [VERIFIED: grep of context/DSL_REFERENCE.md] Each section is a different situation: some describe APIs where the builder exists but with a different style than documented (Dialog, Menu, Camera, Physics, SaveData, Items) — these get a full rewrite from source. Others describe APIs that do not exist at all in the codebase (State Machine, Tweening, Entity Pools, Pathfinding advanced API, Testing Framework stale DSL, Battle Menu/Formulas, Items advanced) — these sections are stripped and archived to backlog/v0.2.0/ verbatim.

**Detekt track:** Detekt currently passes (`BUILD SUCCESSFUL`) with baselines in place. [VERIFIED: ./gradlew detekt run] The globally-disabled rules (`MagicNumber`, `UnusedPrivateMember`, `UnusedPrivateProperty`, `ComplexCondition`) need to be re-enabled with targeted exclusions. The `gbkt-gradle-plugin` composite build has no detekt applied at all today — it is explicitly noted in CI as "tracked debt." Two apply sites in root `build.gradle.kts` (lines 175–181 and 199–205) each carry a dead `baseline = file("detekt-baseline.xml")` that must be deleted.

**Magic-literal track:** The complete in-scope set of 160/144 magic literals in production code (excluding emulator, intellij-plugin, KDoc, examples, and CLI templates) is 10 occurrences across 3 files. [VERIFIED: grep enumeration] `GameBoyConstants.SCREEN_WIDTH/SCREEN_HEIGHT` already exist at the correct altitude in `gbkt-backend-gbdk`. A `GameBoyProfile`/`GameBoyColorProfile` preset in `gbkt-core` (to make `ScreenSpec` the single source of truth) needs to be added, and `GameBoyConstants` derives its values from that preset.

**Primary recommendation:** Tackle the three tracks as independent plan clusters. Do docs audit/rewrite first (largest chunk), then detekt re-enable (needs thought on ignoreNumbers), then literal replacement (pure mechanical). ROM smoke is required only for the literal-replacement cluster (D-17).

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| DSL_REFERENCE.md accuracy audit | Docs layer | Builder source (gbkt-lang) | Audit must cross-check against actual builder classes, not assumed DSL |
| Backlog archival of removed API | Planning layer (.planning/backlog/) | REQUIREMENTS.md Future Requirements | D-09/D-10/D-11 convention |
| Detekt rule re-enable | Build config (detekt.yml, build.gradle.kts) | gbkt-gradle-plugin/build.gradle.kts | Both apply sites must change simultaneously |
| Composite build detekt bridge | Root build.gradle.kts + gbkt-gradle-plugin/build.gradle.kts | CI (kotlin.yml code-quality job) | D-03: gradle.includedBuild dependency + CI comment update |
| Screen constant derivation | gbkt-core constraints (new preset) | gbkt-backend-gbdk/profiles/GameBoyConstants.kt | Single source of truth: core defines, backend references |
| Magic-literal replacement | gbkt-backend-gbdk visitors, gbkt-genre-platformer visitor | — | Only 3 files in scope; byte-identity gate applies |
| Cross-doc consistency pass | All context/*.md, CLAUDE.md, module CLAUDE.md files | CONTRIBUTING.md | D-16 grep pass after each pruned API |

---

## Section 1: Standard Stack

This phase touches no new library dependencies. All tooling is already in-tree.

| Tool | Where | Current State | Action |
|------|-------|---------------|--------|
| detekt | `detekt.yml`, root `build.gradle.kts` | Passing (with baselines) | Re-enable 4 rules, delete baseline wiring, extend to composite |
| Gradle includedBuild | root `build.gradle.kts` | Used for pluginTest, spotlessCheck (precedent) | Add detekt bridge via same pattern |
| `.planning/backlog/v0.2.0/` | .planning/ | 10 entries already present | Add ~13 FEAT-* archive files |

[VERIFIED: codebase grep and file reads throughout research]

---

## Section 2: Architecture Patterns

### Pattern: Detekt composite build bridge

The root `build.gradle.kts` already bridges to the composite build for other tasks:

```kotlin
// Existing precedent (line 105):
dependsOn(gradle.includedBuild("gbkt-gradle-plugin").task(":test"))
```

Apply the same pattern for detekt (D-03):

```kotlin
// In root build.gradle.kts, after registering the root detekt task:
tasks.named("detekt") {
    dependsOn(gradle.includedBuild("gbkt-gradle-plugin").task(":detekt"))
}
```

And in `gbkt-gradle-plugin/build.gradle.kts`, apply detekt:

```kotlin
// Source: CONTEXT.md D-03; mirrors kotlin-dsl plugin block in root build.gradle.kts
plugins {
    // existing plugins...
    id("io.gitlab.arturbosch.detekt")
}

detekt {
    config.setFrom(file("../detekt.yml"))  // share root config
    buildUponDefaultConfig = true
    parallel = true
}
```

[ASSUMED] The exact config path for the composite build pointing up to `../detekt.yml` — needs verification that `rootProject.files()` is unavailable in the composite and a relative path or absolute resolution is needed instead.

### Pattern: Backlog archive file format

Follow the existing format at `.planning/backlog/v0.2.0/SEED-001-ide-and-tooling.md`:

```markdown
---
id: FEAT-STATE-MACHINES
status: dormant
planted: 2026-06-12
planted_during: v0.1.1 / Phase 17 docs cleanup
trigger_when: v0.2.0 DSL implementation milestone
scope: medium
triage_disposition: RE-DEFERRED
triage_evidence: ".planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/"
triage_date: 2026-06-12
---
# FEAT-STATE-MACHINES: State Machine DSL

## Source
Removed from context/DSL_REFERENCE.md lines 371–408 (commit <hash>).
Implemented today: per-actor `animationStates { }` DSL + `setAnimationState(actor, "state")`.

## Verbatim removed content
[full section pasted here]
```

[VERIFIED: read of `.planning/backlog/v0.2.0/SEED-001-ide-and-tooling.md`]

### Pattern: GameBoyConstants derivation from core preset (D-05)

Step 1 — add a `GameBoyScreenSpec` val in `gbkt-core/.../constraints/` (or `TargetProfiles.kt`):

```kotlin
// New file gbkt-core/.../constraints/TargetProfiles.kt
object TargetProfiles {
    val GAME_BOY_SCREEN = ScreenSpec(
        width = 160,
        height = 144,
        bitsPerPixel = 2,
        tileSize = 8,
        backgroundLayers = 1,
        supportsPalettes = false,
        paletteCount = 0,
        colorsPerPalette = 4,
    )
}
```

Step 2 — in `GameBoyConstants.kt` (gbkt-backend-gbdk), derive from the core preset:

```kotlin
import io.github.gbkt.core.constraints.TargetProfiles

object GameBoyConstants {
    val SCREEN_WIDTH = TargetProfiles.GAME_BOY_SCREEN.width    // 160
    val SCREEN_HEIGHT = TargetProfiles.GAME_BOY_SCREEN.height  // 144
    // ... rest unchanged
}
```

Note: `GameBoyConstants.SCREEN_WIDTH/SCREEN_HEIGHT` are currently `const val Int`. Deriving from a non-const `ScreenSpec.width` drops the `const` modifier. This is fine — the only call sites are visitor codegen (not annotation arguments or other const-required positions). [VERIFIED: grep of all call sites — ActorVisitor.kt, GBDKSystemVisitor.kt, PlatformerVisitor.kt are all runtime code generation; no `@` annotation argument uses found]

[ASSUMED] Whether a simpler single-file `TargetProfiles.kt` or a full `GameBoyProfile` companion is preferred for the preset — both compile; D-05 is satisfied either way.

---

## Section 3: The 13 Stale-API Sections — Complete Inventory

Confirmed count: **13 sections** with "Stale-API caveat" blocks. The CONTEXT.md lists them at lines ~372, 922, 1007, 1234, 1316, 1477, 1585, 1658, 1704, 1824, 2011, 2408, 2489. Memory said "12 caveated dead-API sections" — that count referred to dead/absent API sections only; the 13th (line 1658, Camera Transitions) is a partial caveat about unimplemented camera transitions. All 13 confirmed by grep. [VERIFIED: grep of context/DSL_REFERENCE.md]

Note: the CONTEXT.md canonical refs list line 1658 separately ("Transitions") — this is a paragraph inside the Camera section (line 1585 is the main Camera stale-API caveat). The grep returns 13 hits for the `> **Stale-API caveat:**` pattern exactly; line 1658 is a NOTE paragraph about transitions inside Camera, NOT a separate stale-API caveat header. So the actual stale-API caveat blocks are 12 via `> **Stale-API caveat:**` pattern, plus the CONTEXT.md also tracks the Camera Transitions paragraph as a 13th stale item. The total tracked stale-API items = 13.

| # | Line | Section | Builder Exists? | Actual Implemented API | Disposition |
|---|------|---------|-----------------|----------------------|-------------|
| 1 | ~372 | State Machine DSL | NO — `states("...")` builder absent | Per-actor `animationStates {}` + `setAnimationState()` | Archive to FEAT-STATE-MACHINES; keep animationStates docs accurate |
| 2 | ~922 | Dialog System DSL | YES — `DialogBuilder` in `UIBuilders.kt` | `textSpeed(Int)`, `speaker(String)`, `border(BorderStyle)`, `box(x,y,width,height)`, `portrait(AssetRef)`, `fontMode(FontMode)`; `DialogHandle.say()`, `.choice()`, `.show()`, `.hide()` — no `.tick()`, `.isActive`, `.isComplete` | Rewrite from source; archive `.tick()/.isActive/.isComplete` |
| 3 | ~1007 | Menu System DSL | YES — `MenuBuilder` in `UIBuilders.kt` | `layout()`, `cursor(String)`, `parent(MenuHandle)`, `position()`, `columns()`, `item()`, `toggle()`, `slider()`, `option()`, `itemsFrom()`; `MenuHandle.show()/hide()/select()` — no `style{}` block, no `gridMenu()`, no `menu.tick()` | Rewrite from source; archive gridMenu/style/tick |
| 4 | ~1234 | Save Data Fields | YES — `SaveDataBuilder` in `SystemBuilders.kt` | `slots(Int)`, `checksum(Boolean)`, `version(Int)` only; NO `u16Field()`, `flagsField()`, `save.load()`, `save.save()`, `save.exists()` | Rewrite upper section accurate; archive field-level API |
| 5 | ~1316 | Entity Pools | YES — `CollectionBuilders.kt` `pool()` builders | `pool(elementType, capacity)` / `pool(structDef, capacity)` — data pools only, no sprite/lifecycle blocks | Rewrite showing actual pool API; archive sprite/lifecycle pool |
| 6 | ~1477 | Tweening/Easing | NO — `tween()` function and `Easing` enum absent | No tween DSL in ScriptBuilder | Archive entirely to FEAT-TWEENING |
| 7 | ~1585 | Camera System | YES — `CameraBuilder` in `SystemBuilders.kt` | `follow(ActorRef)`, `follow(String)`, `bounds(mapWidth, mapHeight)`, `smoothing` var (declared but may be cosmetic); `cameraOp(CameraAction.FOLLOW/UNFOLLOW/SHAKE/MOVE_TO)` via ScriptBuilder | Rewrite showing implemented API; archive smoothing/deadzone/snapTo/followX/followY |
| 8 | ~1658 | Camera Transitions | PARTIAL — `fade()` is a script-level op | `fade(fadeIn, frames)` with continuation block; wipe/iris/flash not implemented | Clarify fade as implemented (already done in doc); note wipe/iris/flash to archive |
| 9 | ~1704 | Physics | YES — `PhysicsBuilder` in `ActorBuilder.kt` | `gravity(Int)`, `friction(Int)`, `velocity(dx, dy)`, `bounce(Float)`, `maxFallSpeed(Int)`, `platformerMode(Boolean)`, driven by `physicsUpdate()` in frame loop; no `tag()`, no `gravityZone()`, no global `physics{}` world | Rewrite per-actor physics showing function-style API; archive global physics/gravity zones |
| 10 | ~1824 | Pathfinding | YES — `PathfindingBuilder` in `SystemBuilders.kt` | `gridSize(Int)`, `mapSize(w, h)`, `maxOpenNodes(Int)`, `maxPathLength(Int)` + `pathfindStep()/waypointStep()` script ops; no `navGrid()`, no `findPathTo`, no weighted-tile API | Rewrite showing PathfindingBuilder + pathfindStep; archive navGrid/findPathTo |
| 11 | ~2011 | Testing Framework | NO — `testGame()`/`testScene()` DSL absent | `SimulationContext`/`ScriptOpInterpreter` (gbkt-core) for JVM-tier; `GbktTestExtension` (gbkt-test) for emulator-tier | Replace with pointer to context/TESTING.md (the authoritative test doc); archive stale DSL |
| 12 | ~2408 | Battle Menu/Formulas/Custom States | NO — `battleMenu`, `combatFormulas`, `battleState`, `battleTransition` absent | `simpleBattle()` + `battleUpdate()` every frame + 19 built-in `CombatStates.*` | Rewrite showing simpleBattle; archive battle menu/formulas/custom states |
| 13 | ~2489 | Item & Inventory System | PARTIAL — `ItemCatalogBuilder`/`ContainerBuilder` in `InventoryBuilders.kt` | `items { item("potion") { } }`, `container("inventory") { slots(16) }`; NO `by item` delegate, NO `ItemCategory` enum in core (equipment/EquipSlot in RPG genre only) | Rewrite showing catalog+container API; archive by-item delegate and advanced inventory |

[VERIFIED: grep of UIBuilders.kt, SystemBuilders.kt, ActorBuilder.kt, CollectionBuilders.kt, InventoryBuilders.kt, ScriptBuilder.kt, IRScriptOp.kt]

**Suggested backlog file grouping (Claude's discretion, D-09):**

| Backlog file | Covers sections |
|---|---|
| `FEAT-STATE-MACHINES.md` | State Machine DSL (#1) |
| `FEAT-DIALOG-TICK-API.md` | Dialog `.tick()/.isActive/.isComplete` (#2) |
| `FEAT-MENU-GRID-STYLE.md` | Menu `style{}/gridMenu()/tick()` (#3) |
| `FEAT-SAVE-DATA-FIELDS.md` | Save field-level API (#4) |
| `FEAT-ENTITY-POOL-LIFECYCLE.md` | Sprite/lifecycle pool (#5) |
| `FEAT-TWEENING.md` | Tweening/Easing (#6) |
| `FEAT-CAMERA-EXTRAS.md` | Camera smoothing/deadzone/snapTo/followX/followY + screen transitions (#7/#8) |
| `FEAT-PHYSICS-WORLD.md` | Global physics world + gravity zones (#9) |
| `FEAT-PATHFINDING-NAVGRID.md` | navGrid/findPathTo/weighted tiles (#10) |
| `FEAT-TESTING-DSL.md` | testGame()/testScene() stale DSL (#11) |
| `FEAT-BATTLE-MENUS.md` | Battle menu/formulas/custom states (#12) |
| `FEAT-INVENTORY-DELEGATE.md` | `by item` delegate + ItemCategory + advanced inventory (#13) |

That gives 12 backlog files for 13 stale sections (Camera Transitions shares file with Camera Extras).

---

## Section 4: The 2 Doc-Only Fixes (DOCS-03)

### Fix 1: Deprecated-API example block correction

**Location:** `context/DSL_REFERENCE.md` line 35–39 (Variables and Assignments section), also line 141–143 (Arrays section), and line 500–503 (Input section). [VERIFIED: grep of DSL_REFERENCE.md]

The fix targets the comment-style deprecated API block. The current form:

```kotlin
// DEPRECATED API (do not use in new game code):
// assign("score", literal(0))  →  score set 0
// varRef("score")              →  score (use directly)
// literal(5)                   →  5 (raw Int auto-wrapped)
```

This block is already in comment form in the Variables section. The specific doc-only fix described in DOCS-03 is that this example block has the wrong migration arrow direction or incorrect example. The CONTEXT.md mentions "deprecated-API example block corrected" — the exact error to fix should be confirmed against the actual deprecated API behavior. Based on the codebase, `assign()` and `varRef()` were the old string-based API; the current API is `score set 0` (infix operator). The example block appears correct in form but may have copy issues. The planner should confirm the exact correction needed in the first audit plan.

[ASSUMED] The exact change needed in the deprecated-API block — needs cross-check against the actual deprecated function signatures in ScriptBuilder.kt to verify the shown migration paths are accurate.

### Fix 2: subpixel {} no-op behavior clarification

**Location:** `context/DSL_REFERENCE.md` lines 41–71, specifically lines 44–45 and 58–62. [VERIFIED: read of DSL_REFERENCE.md]

Current text: "Group related declarations with the no-op `subpixel { }` scope."

The clarification should state explicitly that `subpixel { }` is a **documentation-only scope** — it emits no IR, produces identical code to declaring variables at the outer scope, and exists solely for visual grouping of fixed-point variable declarations. The "no-op" terminology needs to also address that the scope does NOT create a new variable namespace or execution context.

Current text at line 58: `// Optional: group declarations with subpixel { } for readability (no-op scope; same IR)`

This inline comment is partially there but the section header text at line 44-45 could be more explicit: the word "no-op" appears there but needs a clearer parenthetical "(emits no IR — variables inside are recorded at the enclosing game scope, not a sub-scope)".

[VERIFIED: read of DSL_REFERENCE.md lines 41–70; confirmed subpixel{} is documented as no-op but clarification is still needed per DOCS-03 requirement]

---

## Section 5: Detekt State and QUAL-01 Work

### Current state [VERIFIED: ./gradlew detekt run; read of detekt.yml; read of build.gradle.kts]

- **detekt.yml** exists at project root with `build.maxIssues: 0` (zero-violations enforced).
- Currently **passing** with `BUILD SUCCESSFUL` — all violations are suppressed by the existing rule disabling.
- **Globally disabled rules** (must be re-enabled per D-01):
  - `style.MagicNumber`: active: false — reason given: "Game development uses many coordinate and color values"
  - `style.UnusedPrivateMember`: active: false — reason: "DSL receivers trigger false positives"
  - `style.UnusedPrivateProperty`: active: false — reason: "DSL builders may have unused properties needed for API"
  - `complexity.ComplexCondition`: active: false — reason: "IR generation is inherently nested"
- **Principled path-based exclusions** (kept per D-01): LongMethod, LongParameterList, TooManyFunctions, LargeClass all have `**/codegen/**`, `**/ir/**`, `**/dsl/**`, `**/test/**` excludes — these stay.

### Composite build gap [VERIFIED: read of gbkt-gradle-plugin/build.gradle.kts; read of .github/workflows/kotlin.yml]

- `gbkt-gradle-plugin/build.gradle.kts` has NO detekt plugin applied at all today.
- CI comment in `kotlin.yml` code-quality job: "the plugin build does not apply detekt (tracked debt)."
- CI runs: `./gradlew detekt spotlessCheck :gbkt-gradle-plugin:spotlessCheck` — note the asymmetry: spotless IS bridged explicitly, detekt is not.
- Two apply sites in root `build.gradle.kts` handle this pattern:
  - Line 159: `pluginManager.withPlugin("org.jetbrains.kotlin.jvm")` block — applies detekt + baseline wiring
  - Line 185: `pluginManager.withPlugin("org.gradle.kotlin.kotlin-dsl")` block — same, for gradle-plugin build
- The kotlin-dsl plugin block at line 185 already triggers for the composite build's source files when included. **However**, the `baseline = file("detekt-baseline.xml")` at lines 180 and 204 must be deleted (D-04).

### Violation forecast after re-enabling rules

Based on codebase analysis, the expected violation classes are:

| Rule | Expected Hit Areas | Remediation |
|------|-------------------|-------------|
| `MagicNumber` | `gbkt-backend-gbdk` visitors: `ActorVisitor.kt:468,490` (`144-speed`, `160-speed`), `GBDKSystemVisitor.kt:172-173` (160/144) — covered by D-02/D-05 literal replacement. Other magic numbers in user-facing modules. | Path-exclude `**/codegen/**` and `**/test/**`; add `ignoreNumbers` for 0, 1, 2, 3, 4, 8, 16 (idiomatic tile/bit values) |
| `UnusedPrivateMember` | DSL builder private properties that serve as API backing fields (e.g., ConfigBuilder private vars) | Path-exclude `**/dsl/**`, or fix by confirming which ARE unused and removing them |
| `UnusedPrivateProperty` | Same area as UnusedPrivateMember | Same approach |
| `ComplexCondition` | Codegen visitors and validation passes — already principled-excluded via `**/codegen/**`, `**/validation/**` | Re-enable with existing exclusions preserved |

[ASSUMED] The exact count of violations after re-enabling — only a dry-run with the rules active and `maxIssues: 0` removed temporarily would give exact counts. The planner should make the first detekt plan "enable rules + run + inventory" before a "fix all" plan.

### Tension with Sonar S3776 (46 HIGHs)

The 46 Sonar S3776 cognitive-complexity HIGHs are in files under `**/codegen/**`, `**/ir/**`, `**/dsl/**` — all of which are **already excluded** in detekt.yml for LongMethod/CyclomaticComplexMethod/etc. The `ComplexCondition` rule being re-enabled also has no path exclusions currently, but ComplexCondition (number of boolean sub-expressions) is different from cyclomatic complexity. [VERIFIED: detekt.yml — ComplexCondition has `active: false`, no excludes listed]

**Answer:** D-01 explicitly says the principled path-based exclusions stay for LongMethod/TooManyFunctions/LargeClass/LongParameterList. `ComplexCondition` is being re-enabled but it measures `&&`/`||` chain length per condition expression, not method complexity. The codegen files that drive S3776 likely pass `ComplexCondition` within individual conditions even if the whole method is long. No conflict expected between QUAL-01 and Phase 18 scope. [ASSUMED: ComplexCondition will be green in codegen files — needs verification in the "enable + run + inventory" plan]

---

## Section 6: Magic-Pixel Literals — Complete In-Scope Enumeration

### Complete inventory [VERIFIED: grep of all .kt production files]

The grep was run excluding: `build/` dirs, `gbkt-emulator/`, `gbkt-intellij-plugin/`, `gbkt-examples/`, `gbkt-cli/`, `gbkt-lang/` (KDoc only), `GameBoyConstants.kt` (the definition site), test directories.

**In-scope literals that MUST be replaced:**

| File | Line | Code | Replace With |
|------|------|------|--------------|
| `gbkt-backend-gbdk/.../codegen/visitor/ActorVisitor.kt` | 468 | `CLiteral(144 - speed)` | `CLiteral(GameBoyConstants.SCREEN_HEIGHT - speed)` |
| `gbkt-backend-gbdk/.../codegen/visitor/ActorVisitor.kt` | 490 | `CLiteral(160 - speed)` | `CLiteral(GameBoyConstants.SCREEN_WIDTH - speed)` |
| `gbkt-backend-gbdk/.../codegen/visitor/GBDKSystemVisitor.kt` | 172 | `boundsWidth - 160` | `boundsWidth - GameBoyConstants.SCREEN_WIDTH` |
| `gbkt-backend-gbdk/.../codegen/visitor/GBDKSystemVisitor.kt` | 173 | `boundsHeight - 144` | `boundsHeight - GameBoyConstants.SCREEN_HEIGHT` |
| `gbkt-genre-platformer/.../codegen/PlatformerVisitor.kt` | 1986 | `CLiteral(160)` (cam_target_x divisor) | `CLiteral(GameBoyConstants.SCREEN_WIDTH)` |
| `gbkt-genre-platformer/.../codegen/PlatformerVisitor.kt` | 1988 | `CLiteral(160)` (cam_target_x multiplier) | `CLiteral(GameBoyConstants.SCREEN_WIDTH)` |
| `gbkt-genre-platformer/.../codegen/PlatformerVisitor.kt` | 2000 | `CLiteral(144)` (cam_target_y divisor) | `CLiteral(GameBoyConstants.SCREEN_HEIGHT)` |
| `gbkt-genre-platformer/.../codegen/PlatformerVisitor.kt` | 2002 | `CLiteral(144)` (cam_target_y multiplier) | `CLiteral(GameBoyConstants.SCREEN_HEIGHT)` |

Note: `PlatformerVisitor.kt` lines 1394, 1397, 1398 contain 144 inside string literals (diagnostic comment strings). These are KDoc/comment mentions — **exempt per D-07** (comment mentions).

**Total in-scope replacements: 8 call sites across 3 files.**

### Exempt literals [VERIFIED: grep + file reads]

| Location | Literal | Reason Exempt |
|----------|---------|---------------|
| `gbkt-emulator/` (26+ occurrences) | 160 / 144 | Implements the physical LCD — these ARE the hardware dimensions, not consuming a platform constant |
| `gbkt-intellij-plugin/` | 160 / 144 | Preview rendering; no gbkt-core dependency (cannot import ScreenSpec); D-07 explicit exemption |
| `gbkt-lang/.../ActorBuilder.kt:56`, `ScriptBuilder.kt:653`, `ExprBuilder.kt:176` | 160 / 144 | KDoc comment examples only — not executable code; D-07 KDoc/comment exemption |
| `gbkt-cli/.../RpgTemplate.kt:98` | 144 | Inside a generated-game template string (`|        whenever(player.x isAbove 144)...`) — the CLI emits game source, not framework code; D-07 CLI template exemption |
| `gbkt-genre-sport/.../SportBuilders.kt:181`, `SportExtensions.kt:76` | 160 | Inside KDoc comment examples (`stats { speed(200); acceleration(160); ... }`) — 160 here is a SPEED stat value, not a screen dimension; context confirms this is not a screen literal |
| `gbkt-examples/breakout/.../Breakout.kt:224` | 144 | Examples are game code, not framework code; not in scope per D-07 |
| `GameBoyConstants.kt` definitions (lines 26, 29) | 160 / 144 | The definition site — this IS where the values are defined, not where they should be replaced. After D-05 preset work, these will derive from core ScreenSpec |

### Byte-identity guarantee

The 8 replacements in `ActorVisitor.kt`, `GBDKSystemVisitor.kt`, and `PlatformerVisitor.kt` are all **Kotlin compile-time constant arithmetic** — `GameBoyConstants.SCREEN_WIDTH` is an `Int` constant evaluated at Kotlin compile time. The CLiteral values passed to the C AST are identical integers; the emitted C is byte-identical. [VERIFIED: GameBoyConstants.SCREEN_WIDTH/HEIGHT are `const val Int`; D-17 byte-identity requirement satisfied by construction]

Note: After the D-05 change (deriving GameBoyConstants from a non-const ScreenSpec), `SCREEN_WIDTH/SCREEN_HEIGHT` will be regular `val` (not `const val`). This is still fine — the values resolve at JVM object initialization, before any codegen runs. D-17 byte-identity is about C output, not Kotlin const-ness. [ASSUMED: no annotation-argument or other Kotlin const-required use of SCREEN_WIDTH/HEIGHT — grep confirmed no such sites]

---

## Section 7: Folded Todos — Implementation Details

### MBC5 silent-fallback warning

**File:** `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/CompileRomTask.kt`
**Location:** `readMbcType()`, line 285–287 [VERIFIED: read of CompileRomTask.kt]

Current code (the silent fallback):
```kotlin
// Fallback: derive from RAM banks configuration
val hasRam = ramBanks.getOrElse(0) > 0
return if (hasRam) "0x1B" else "0x19" // MBC5+RAM+Battery or MBC5
```

This path is hit when `propsFile` does NOT exist (i.e., `gbkt-build.properties` is absent entirely). The fix is a `logger.warn()` before the return:

```kotlin
logger.warn(
    "gbkt-build.properties not found in $sourceDir — falling back to MBC5. " +
    "Run generateC to regenerate, or declare cartridge type in config { }."
)
val hasRam = ramBanks.getOrElse(0) > 0
return if (hasRam) "0x1B" else "0x19"
```

This is warning-only, no output change, no byte-identity concern.

### ConfigBuilder setter consistency

**File:** `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SystemBuilders.kt`
**Scope:** `ConfigBuilder` class, lines 531–585 [VERIFIED: read of SystemBuilders.kt]

Current inconsistency:
- `cartridge`: both `var cartridge` AND `fun cartridge(type: Cartridge)` — two ways to set one value
- `gbcTarget`: both `var gbcTarget` AND `fun target(mode: GbcTarget)` — two ways
- `romBanks`: `var romBanks: Int?` only — no setter function
- `ramBanks`: `var ramBanks: Int` only — no setter function

The fix (Claude's discretion, but function-setter convention is more idiomatic for DSL builders) is to pick ONE convention. The recommended approach: keep function setters for all (they're more DSL-readable), make the `var`s `private var`, and add `fun romBanks(count: Int)` / `fun ramBanks(count: Int)` if the public-var form was previously documented. OR: deprecate the function setters and keep only var assignment.

**DSL_REFERENCE.md `config{}` section** must be updated to reflect whichever convention is chosen. Codegen output will be unchanged (only setter style, not semantics changes).

### RpgRegistry.clear() never called

**File:** `gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/RpgExtensions.kt`
**Location:** `RpgRegistry` object, lines 55–77; `game()` function in `GameBuilder.kt`, lines 826–830 [VERIFIED: read of RpgExtensions.kt and VariableBuilders.kt]

`RpgRegistry` uses a ThreadLocal (line 56) — not a singleton map. So the "stale entries from game A into game B" scenario applies only in test runners or Gradle daemons that run multiple `game {}` builds on the same thread without clearing.

`GameBuilderContext.with()` already does clean teardown (restores previous value in `finally` block). `RpgRegistry` does not have a matching `with()` pattern — it initializes lazily but never clears.

Fix option A (preferred): Add `fun clear()` call to `game()` function teardown, or add `RpgRegistry.with(block)` pattern:

```kotlin
// In GameBuilder.kt or RpgExtensions.kt
fun game(name: String, block: GameBuilder.() -> Unit): GameBuilder {
    val builder = GameBuilder(name)
    GameBuilderContext.with(builder) { builder.block() }
    RpgRegistry.clear()   // Clean up after game{} lambda
    return builder
}
```

OR option B: add `clear()` to `RpgRegistry` (it doesn't exist today — only `registerCharacter/registerMonster`) and call it, OR confirm that `holder.set(null)` after `game{}` is sufficient.

[ASSUMED] Whether to add `clear()` to RpgRegistry or invoke `holder.remove()` — the exact mechanism needs review of whether the ThreadLocal approach makes `clear()` the right API. The current RpgRegistry has no `clear()` method at all — the todo says "never called" but means the KDoc says to call it, while the method doesn't exist (or the todo misidentifies). A quick re-read of the todo confirms: "RpgRegistry.clear() is defined and its KDoc says..." — but a grep of RpgExtensions.kt finds no `clear()` function. This is a discrepancy; the planner should verify by reading the full RpgRegistry object.

---

## Section 8: Cross-Doc Consistency Pass (D-16)

After removing stale-API sections from DSL_REFERENCE.md, the following doc files must be grepped for the removed API names and any found references corrected:

| Doc File | Why it matters |
|----------|----------------|
| Root `CLAUDE.md` | Routing index — may reference APIs by name |
| `context/ARCHITECTURE.md` | May reference IR nodes for stale DSL |
| `context/TESTING.md` | May reference testGame()/testScene() stale DSL |
| `context/TOOLING.md` | May reference asset pipeline APIs |
| `context/LOCALIZATION.md` | Less likely; still in scope |
| `CONTRIBUTING.md` | May document removed patterns as examples |
| Module `CLAUDE.md` files (gbkt-lang, gbkt-genre-rpg, etc.) | May cross-reference stale DSL |

Search terms: `states(`, `navGrid(`, `tween(`, `Easing.`, `testGame(`, `testScene(`, `battleMenu`, `combatFormulas`, `battleState(`, `battleTransition(`, `by item`, `ItemCategory`, `.tick()` (dialog context), `gridMenu(`.

---

## Section 9: Don't Hand-Roll

| Problem | Don't Build | Use Instead |
|---------|-------------|-------------|
| Detekt composite build bridge | Custom Gradle task to invoke detekt on plugin | `gradle.includedBuild("gbkt-gradle-plugin").task(":detekt")` via `dependsOn` — same pattern as pluginTest |
| Single source of truth for 160/144 | Copy/paste the constant values | `GameBoyConstants.SCREEN_WIDTH/SCREEN_HEIGHT` — they already exist and are already the canonical reference |
| Archive format for removed doc sections | Invent new format | Follow existing `.planning/backlog/v0.2.0/SEED-001-ide-and-tooling.md` front-matter format verbatim |

---

## Common Pitfalls

### Pitfall 1: `const val` to `val` drop in GameBoyConstants

**What goes wrong:** After D-05 derives `SCREEN_WIDTH/HEIGHT` from a `ScreenSpec` instance, the `const val Int` becomes a `val Int`. Any Kotlin annotation argument that uses `GameBoyConstants.SCREEN_WIDTH` would break at compile time (Kotlin requires constant expressions in annotations).

**Why it happens:** Kotlin `const val` requires compile-time constant expressions; `ScreenSpec(160, ...).width` is not one.

**How to avoid:** Grep for `@`-annotation usages of `SCREEN_WIDTH/HEIGHT` before making the change. Based on research, none exist in production code — the constants are used only in if-expressions and CLiteral arguments, both of which accept non-const Int. [VERIFIED: no annotation uses found]

**Warning signs:** `error: an annotation argument must be a compile-time constant` during build.

### Pitfall 2: Detekt config path in composite build

**What goes wrong:** In `gbkt-gradle-plugin/build.gradle.kts`, `rootProject.files("detekt.yml")` is not available (the composite is an independent Gradle project). Using a relative path like `file("../detekt.yml")` might not resolve correctly depending on where Gradle sets the project directory.

**Why it happens:** Composite builds are separate Gradle projects; `rootProject` refers to the plugin project's root, not the parent project's root.

**How to avoid:** Use `file("${rootDir}/../detekt.yml")` or pass the config file path as a Gradle property from the outer build. Alternatively, copy `detekt.yml` into the composite (fragile) or reference it via the outer project's path using `includedBuild` conventions.

**Warning signs:** `detekt: configuration file not found` or detekt running with default config (all rules active, unexpected violations).

### Pitfall 3: Baseline deletion causes immediate build failure

**What goes wrong:** Deleting `baseline = file("detekt-baseline.xml")` from `build.gradle.kts` before re-enabling the disabled rules may have no immediate effect (baselines only suppress violations, and with the rules disabled there are no violations to suppress). But if any baseline XML files somehow exist, deleting the wiring before cleaning them up leaves orphaned files.

**Why it happens:** The baseline wiring and the actual .xml files are two separate things. No `.xml` files were found on disk (verified), so this is moot — but worth noting.

**How to avoid:** Confirm no `detekt-baseline.xml` files exist (verified: none found). Delete the wiring in the same commit as confirming the rules pass.

### Pitfall 4: ROM smoke on genre changes

**What goes wrong:** Changes to `PlatformerVisitor.kt` (the 4 CLiteral replacements at lines 1986–2002) must pass the 7-example ROM smoke. The platformer-template ROM in particular exercises these camera codegen paths.

**Why it happens:** D-17 byte-identity gate — codegen module changes must not alter C output.

**How to avoid:** The replacements are arithmetic-equivalent (same integer value), so C output is identical by construction. Still, run `./gradlew :gbkt-examples:platformer-template:buildRom` (and other examples) after the change. Use a single chained Gradle invocation per D-18 (no parallel `gradle clean`).

### Pitfall 5: Treating gbkt-lang KDoc 160/144 as in-scope

**What goes wrong:** The grep finds `160`/`144` in `ActorBuilder.kt:56`, `ScriptBuilder.kt:653`, `ExprBuilder.kt:176` inside KDoc comments. These look like magic-literal violations but are documentation examples.

**Why it happens:** The MagicNumber rule operates on source code literals, not comments. Once MagicNumber is re-enabled with `**/dsl/**` path exclusions (D-02), these files will be excluded anyway. But in the exemption table, they should be listed as "KDoc comment — exempt per D-07."

---

## Validation Architecture

The `workflow.nyquist_validation` key is not found in `.planning/config.json` (config uses different schema) — treat as enabled.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (via kotlin("test") + JUnit BOM) |
| Config file | `build.gradle.kts` per-module test blocks |
| Quick run command | `./gradlew :gbkt-lang:test :gbkt-backend-gbdk:test :gbkt-genre-platformer:test` |
| Full suite command | `./gradlew test` |
| Static analysis check | `./gradlew detekt` |
| ROM smoke | `./gradlew :gbkt-examples:pong:buildRom :gbkt-examples:platformer-template:buildRom :gbkt-examples:metasprites:buildRom :gbkt-examples:breakout:buildRom :gbkt-examples:banks:buildRom :gbkt-examples:simple-physics:buildRom :gbkt-examples:metasprites-stress:buildRom` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command |
|--------|----------|-----------|-------------------|
| DOCS-01 | All 13 stale-API sections audited, implemented APIs documented accurately | manual audit + evidence table | Verify against `.planning/phases/17-.../evidence/` audit table |
| DOCS-02 | Pruned content archived verbatim in backlog/v0.2.0/ | manual verification | Check each FEAT-*.md file exists with verbatim content |
| DOCS-03 | 2 doc-only fixes applied | manual review | Read DSL_REFERENCE.md sections; confirm changes |
| QUAL-01 | `./gradlew detekt` passes zero violations, composite included | automated | `./gradlew detekt` → BUILD SUCCESSFUL |
| QUAL-02 | 8 in-scope 160/144 literals replaced with named constants | code review | `grep -rn '\b160\b\|\b144\b' gbkt-backend-gbdk gbkt-genre-platformer --include='*.kt' \| grep -v test` should return only GameBoyConstants definition sites |
| QUAL-03 | Exemption table committed, no non-exempt literals remain | code review + evidence table | Same grep + cross-reference to evidence table |

### Wave 0 Gaps

No new test files needed. This phase is docs + config changes + pure mechanical code edits. The existing test suite and ROM smoke cover the QUAL-02/03 byte-identity requirement.

---

## Environment Availability

| Dependency | Required By | Available | Version |
|------------|------------|-----------|---------|
| Kotlin / Gradle | All tasks | ✓ | Kotlin 2.3.20, Gradle 9.5.1 |
| detekt | QUAL-01 | ✓ | In libs.versions.toml |
| GBDK (lcc) | ROM smoke (D-17) | Checked at build time via GbdkToolchain | 4.5.0 (CI-pinned) |

---

## Security Domain

This phase makes no changes to authentication, session management, input validation, cryptography, or access control. No ASVS categories apply.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The exact config path syntax for detekt in the composite build — `file("../detekt.yml")` may not resolve correctly; exact resolution mechanism needs verification | Section 2, Section 5 Pitfall 2 | Detekt runs with default config in composite, producing unexpected violations or not running at all |
| A2 | `ComplexCondition` re-enable will be green in `**/codegen/**` files (no per-expression boolean chains flagged in codegen) | Section 5 | More violations than expected after re-enable; needs "enable + inventory" plan |
| A3 | `UnusedPrivateMember` and `UnusedPrivateProperty` violations will cluster in `**/dsl/**` files (DSL receiver backing fields) and can be resolved by adding path excludes | Section 5 | May surface real dead code needing removal, or may require excludes in more modules |
| A4 | `GameBoyConstants.SCREEN_WIDTH/HEIGHT` are not used as Kotlin annotation arguments anywhere | Section 9 Pitfall 1 | Changing from `const val` to `val` would cause compile error at annotation site |
| A5 | `RpgRegistry.clear()` does NOT exist today as a method (the todo says "never called" but grep finds no method body) — the fix is to ADD the method + call it | Section 7 | If clear() does exist but is just not wired, the fix scope changes slightly |
| A6 | The `ignoreNumbers` list for MagicNumber re-enable — `[0, 1, 2, 3, 4, 8, 16]` is sufficient for idiomatic tile/bit values; actual count may require additional values (e.g., 20, 18 for tile-count defaults) | Section 5 | Under-specified ignoreNumbers → violations in test/example files that should be exempt |
| A7 | The deprecated-API example block fix (DOCS-03 Fix 1) targets the comment in Variables and Assignments section — exact error to correct must be confirmed in audit plan | Section 4 | Wrong section targeted or correct section but wrong change |

---

## Sources

### Primary (HIGH confidence)
- `context/DSL_REFERENCE.md` — direct grep + read for all 13 stale-API locations and DOCS-03 fix locations
- `detekt.yml` — read directly for current rule state
- `build.gradle.kts` — read for apply sites, baseline wiring, includedBuild pattern
- `gbkt-backend-gbdk/.../profiles/GameBoyConstants.kt` — read for existing constants
- `gbkt-core/.../constraints/ScreenSpec.kt`, `TargetProfile.kt` — read for preset hook point
- `gbkt-backend-gbdk/.../profiles/GameBoyProfile.kt` — read for existing TargetProfile impl
- `gbkt-lang/.../dsl/UIBuilders.kt`, `SystemBuilders.kt`, `ActorBuilder.kt`, `InventoryBuilders.kt`, `CollectionBuilders.kt` — read for all builder API implementations
- `gbkt-gradle-plugin/build.gradle.kts` — read confirming no detekt applied
- `.github/workflows/kotlin.yml` — read confirming code-quality job gap
- All 160/144 grep runs — verified against production .kt files

### Secondary (MEDIUM confidence)
- CONTEXT.md decisions D-01 through D-18 — authoritative constraint source
- Existing `.planning/backlog/v0.2.0/SEED-001-ide-and-tooling.md` — format precedent for archive files
- `./gradlew detekt` run — confirmed current passing state

---

## Metadata

**Confidence breakdown:**
- Stale-API inventory: HIGH — confirmed by direct grep + builder source reads
- Detekt configuration: HIGH — confirmed by direct file reads + test run
- Magic-literal enumeration: HIGH — confirmed by exhaustive grep
- Composite build detekt bridge: MEDIUM — mechanism identified, exact path resolution assumed
- Violation count forecast: MEDIUM — rules listed, exact count requires dry-run

**Research date:** 2026-06-12
**Valid until:** 2026-07-12 (stable internal codebase, no external dependencies)
