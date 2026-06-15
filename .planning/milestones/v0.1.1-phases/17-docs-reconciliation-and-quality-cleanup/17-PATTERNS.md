# Phase 17: Docs Reconciliation and Quality Cleanup - Pattern Map

**Mapped:** 2026-06-12
**Files analyzed:** 18 (new/modified files across all three tracks)
**Analogs found:** 18 / 18 (all files have close existing analogs)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `context/DSL_REFERENCE.md` | doc | transform (audit+rewrite) | existing file (in-place rewrite) | exact |
| `.planning/backlog/v0.2.0/FEAT-STATE-MACHINES.md` | backlog-seed | — | `.planning/backlog/v0.2.0/SEED-001-ide-and-tooling.md` | exact |
| `.planning/backlog/v0.2.0/FEAT-DIALOG-TICK-API.md` | backlog-seed | — | `.planning/backlog/v0.2.0/SEED-001-ide-and-tooling.md` | exact |
| `.planning/backlog/v0.2.0/FEAT-MENU-GRID-STYLE.md` | backlog-seed | — | `.planning/backlog/v0.2.0/SEED-001-ide-and-tooling.md` | exact |
| `.planning/backlog/v0.2.0/FEAT-SAVE-DATA-FIELDS.md` | backlog-seed | — | `.planning/backlog/v0.2.0/SEED-001-ide-and-tooling.md` | exact |
| `.planning/backlog/v0.2.0/FEAT-ENTITY-POOL-LIFECYCLE.md` | backlog-seed | — | `.planning/backlog/v0.2.0/SEED-001-ide-and-tooling.md` | exact |
| `.planning/backlog/v0.2.0/FEAT-TWEENING.md` | backlog-seed | — | `.planning/backlog/v0.2.0/SEED-001-ide-and-tooling.md` | exact |
| `.planning/backlog/v0.2.0/FEAT-CAMERA-EXTRAS.md` | backlog-seed | — | `.planning/backlog/v0.2.0/SEED-001-ide-and-tooling.md` | exact |
| `.planning/backlog/v0.2.0/FEAT-PHYSICS-WORLD.md` | backlog-seed | — | `.planning/backlog/v0.2.0/SEED-001-ide-and-tooling.md` | exact |
| `.planning/backlog/v0.2.0/FEAT-PATHFINDING-NAVGRID.md` | backlog-seed | — | `.planning/backlog/v0.2.0/SEED-001-ide-and-tooling.md` | exact |
| `.planning/backlog/v0.2.0/FEAT-TESTING-DSL.md` | backlog-seed | — | `.planning/backlog/v0.2.0/SEED-001-ide-and-tooling.md` | exact |
| `.planning/backlog/v0.2.0/FEAT-BATTLE-MENUS.md` | backlog-seed | — | `.planning/backlog/v0.2.0/SEED-001-ide-and-tooling.md` | exact |
| `.planning/backlog/v0.2.0/FEAT-INVENTORY-DELEGATE.md` | backlog-seed | — | `.planning/backlog/v0.2.0/SEED-001-ide-and-tooling.md` | exact |
| `detekt.yml` | config | transform | existing file (in-place edit) | exact |
| `build.gradle.kts` (root) | config | transform | existing file (in-place edit) | exact |
| `gbkt-gradle-plugin/build.gradle.kts` | config | transform | `build.gradle.kts` lines 185-206 (kotlin-dsl block) | exact |
| `gbkt-core/src/main/kotlin/io/github/gbkt/core/constraints/TargetProfiles.kt` | utility | — | `GameBoyProfile.kt` (object implementing TargetProfile) | role-match |
| `gbkt-backend-gbdk/.../profiles/GameBoyConstants.kt` | utility | transform | existing file (in-place edit) | exact |

---

## Pattern Assignments

### `context/DSL_REFERENCE.md` — 13 stale-API section rewrites (DOCS-01/02/03)

**Analog:** The file itself — partial rewrites in-place, not a new file.

**Rewrite discipline (D-12/D-14):** Every rewritten snippet must be lifted or adapted from an in-tree file that compiles today. The known builder sources are:

- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/UIBuilders.kt` — `DialogBuilder`, `MenuBuilder`
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SystemBuilders.kt` — `CameraBuilder`, `SaveDataBuilder`, `PathfindingBuilder`, `ConfigBuilder`
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ActorBuilder.kt` — `PhysicsBuilder` (per-actor)
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/CollectionBuilders.kt` — `pool()` builders
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/InventoryBuilders.kt` — `ItemCatalogBuilder`, `ContainerBuilder`
- `gbkt-genre-rpg/src/main/kotlin/.../dsl/RpgExtensions.kt` — `simpleBattle()`, `battleUpdate()`

**Removal discipline (D-10):** DSL_REFERENCE.md gets clean removal with no "planned for v0.2.0" breadcrumbs. Removed content is archived verbatim in backlog FEAT-*.md files.

**DOCS-03 fix 1 — deprecated-API example block:**
Location: `context/DSL_REFERENCE.md` lines 35–39 (Variables section). Contains a comment block documenting the old `assign()`/`varRef()` string-based API with migration arrows. Verify migration arrows are correct before editing (RESEARCH.md marks exact correction as ASSUMED A7).

**DOCS-03 fix 2 — subpixel {} clarification:**
Location: `context/DSL_REFERENCE.md` line 44-45 / 58-62 (Variables section).
Current: "Group related declarations with the no-op `subpixel { }` scope."
Required addition: make explicit that `subpixel {}` emits no IR, creates no variable sub-scope, and exists solely for visual grouping (the inline comment at line 58 partially covers this; the section header text needs to match).

---

### `.planning/backlog/v0.2.0/FEAT-*.md` (12 files, backlog-seed)

**Analog:** `.planning/backlog/v0.2.0/SEED-001-ide-and-tooling.md`

**Front-matter pattern** (lines 1-11 of SEED-001):
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
```

**Body pattern** (follows front-matter in SEED-001):
```markdown
# FEAT-STATE-MACHINES: State Machine DSL

## Source
Removed from context/DSL_REFERENCE.md lines 371–408 (commit <hash>).
Implemented today: per-actor `animationStates { }` DSL + `setAnimationState(actor, "state")`.

## Verbatim removed content
[full section pasted here verbatim, code samples intact]
```

**Key requirements per D-11:** provenance header (source line range, removal commit, what IS implemented today) + verbatim removed content. Every FEAT-*.md must carry these three pieces.

**FEAT-* file → DSL_REFERENCE.md section mapping:**

| FEAT file | Covers | DSL_REFERENCE.md line |
|---|---|---|
| `FEAT-STATE-MACHINES.md` | State Machine DSL (#1) | ~372 |
| `FEAT-DIALOG-TICK-API.md` | Dialog `.tick()/.isActive/.isComplete` (#2) | ~922 |
| `FEAT-MENU-GRID-STYLE.md` | Menu `style{}/gridMenu()/tick()` (#3) | ~1007 |
| `FEAT-SAVE-DATA-FIELDS.md` | Save field-level API (u16Field, flagsField, load/save/exists) (#4) | ~1234 |
| `FEAT-ENTITY-POOL-LIFECYCLE.md` | Sprite/lifecycle pool API (#5) | ~1316 |
| `FEAT-TWEENING.md` | Tweening/Easing DSL — fully absent (#6) | ~1477 |
| `FEAT-CAMERA-EXTRAS.md` | Camera smoothing/deadzone/snapTo/followX/followY + wipe/iris/flash transitions (#7/#8) | ~1585/1658 |
| `FEAT-PHYSICS-WORLD.md` | Global physics world + gravity zones (#9) | ~1704 |
| `FEAT-PATHFINDING-NAVGRID.md` | navGrid/findPathTo/weighted tiles (#10) | ~1824 |
| `FEAT-TESTING-DSL.md` | testGame()/testScene() stale DSL (#11) | ~2011 |
| `FEAT-BATTLE-MENUS.md` | Battle menu/formulas/custom states (#12) | ~2408 |
| `FEAT-INVENTORY-DELEGATE.md` | `by item` delegate + ItemCategory + advanced inventory (#13) | ~2489 |

---

### `detekt.yml` — re-enable 4 globally-disabled rules (QUAL-01)

**Analog:** Existing detekt.yml — edit in-place using the file's own exclusion style.

**Current globally-disabled rules to re-enable** (detekt.yml lines 106-111):
```yaml
style:
  MagicNumber:
    active: false  # Game development uses many coordinate and color values
  UnusedPrivateMember:
    active: false  # DSL receivers trigger false positives
  UnusedPrivateProperty:
    active: false  # DSL builders may have unused properties that are needed for API
  ComplexCondition:
    active: false  # IR generation is inherently nested
```

**Exclusion style pattern** (from detekt.yml, e.g. LongMethod block lines 20-31):
Every exclude entry carries an inline `# rationale comment` matching the existing format:
```yaml
  LongMethod:
    active: true
    threshold: 80  # DSL builders get long
    excludes:
      - '**/codegen/**'  # Codegen methods generate C code strings
      - '**/test/**'  # Test files can have long test methods
```

**Required new exclusion blocks for re-enabled rules:**

`MagicNumber` (D-02) — re-enable with excludes + ignoreNumbers:
```yaml
  MagicNumber:
    active: true
    ignoreNumbers: ['0', '1', '2', '3', '4', '8', '16']  # idiomatic tile/bit values
    excludes:
      - '**/codegen/**'  # Codegen emits literal C values as construction arguments
      - '**/test/**'  # Test data uses numeric constants for readability
```
Note: exact `ignoreNumbers` list is Claude's discretion (D-02) — start with `[0,1,2,3,4,8,16]` and expand based on dry-run violations.

`ComplexCondition` (D-01) — re-enable with codegen exclusion:
```yaml
  ComplexCondition:
    active: true
    excludes:
      - '**/codegen/**'  # Codegen visitor conditions check many IR node types
      - '**/ir/**'  # IR generation is inherently nested
```

`UnusedPrivateMember` / `UnusedPrivateProperty` (D-01) — re-enable with DSL exclusion:
```yaml
  UnusedPrivateMember:
    active: true
    excludes:
      - '**/dsl/**'  # DSL receivers trigger false positives; backing fields serve API contract
  UnusedPrivateProperty:
    active: true
    excludes:
      - '**/dsl/**'  # DSL builders may have backing properties needed for DSL API surface
```

---

### `build.gradle.kts` (root) — delete baseline wiring (QUAL-01, D-04)

**Analog:** Existing file. Two identical dead `baseline` lines to delete.

**Lines to delete** (build.gradle.kts lines 180 and 204):
```kotlin
// Line 180 (inside pluginManager.withPlugin("org.jetbrains.kotlin.jvm") block):
baseline = file("detekt-baseline.xml")

// Line 204 (inside pluginManager.withPlugin("org.gradle.kotlin.kotlin-dsl") block):
baseline = file("detekt-baseline.xml")
```

The surrounding `configure<DetektExtension>` blocks (lines 175-181 and 199-205) remain; only the `baseline =` line inside each is removed.

**Composite build bridge to add** (D-03) — modeled on the existing `pluginTest` bridge at build.gradle.kts line 105:
```kotlin
// Existing precedent (build.gradle.kts line 105):
dependsOn(gradle.includedBuild("gbkt-gradle-plugin").task(":test"))

// New bridge to add (same pattern, after the subprojects{} block):
tasks.named("detekt") {
    dependsOn(gradle.includedBuild("gbkt-gradle-plugin").task(":detekt"))
}
```

---

### `gbkt-gradle-plugin/build.gradle.kts` — add detekt plugin (QUAL-01, D-03)

**Analog:** Root `build.gradle.kts` lines 185-206 (`pluginManager.withPlugin("org.gradle.kotlin.kotlin-dsl")` block) — the same pattern already fires for the composite because of the kotlin-dsl plugin. However, the composite is an independent Gradle project and needs explicit plugin application.

**Pattern to copy** from root `build.gradle.kts` lines 185-206:
```kotlin
// In gbkt-gradle-plugin/build.gradle.kts, add to the plugins block:
id("io.gitlab.arturbosch.detekt")

// Add detekt configuration block:
detekt {
    config.setFrom(file("${rootDir}/../detekt.yml"))  // composite rootDir is gbkt-gradle-plugin/
    buildUponDefaultConfig = true
    parallel = true
    // No baseline — D-04
}
```

**Pitfall (RESEARCH.md Pitfall 2):** `rootProject.files("detekt.yml")` is NOT available in the composite build — `rootProject` refers to the plugin project root, not the parent. Use `file("${rootDir}/../detekt.yml")` or verify with `./gradlew :gbkt-gradle-plugin:detekt --info` that the config path resolves correctly.

---

### `gbkt-core/src/main/kotlin/io/github/gbkt/core/constraints/TargetProfiles.kt` — new file (QUAL-02, D-05)

**Analog:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/profiles/GameBoyProfile.kt` — same pattern: object in the constraints package constructing a `ScreenSpec`.

**License header pattern** (from GameBoyProfile.kt lines 1-6):
```kotlin
/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
```

**Package and import pattern** (from ScreenSpec.kt):
```kotlin
package io.github.gbkt.core.constraints
```

**Core pattern — new TargetProfiles object** (modeled after GameBoyProfile.kt lines 25-39):
```kotlin
object TargetProfiles {
    /** Canonical Game Boy (DMG) screen specification — single source of truth for 160x144. */
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

    /** Canonical Game Boy Color screen specification (same resolution as DMG, adds palettes). */
    val GAME_BOY_COLOR_SCREEN = ScreenSpec(
        width = 160,
        height = 144,
        bitsPerPixel = 4,
        tileSize = 8,
        backgroundLayers = 1,
        supportsPalettes = true,
        paletteCount = 8,
        colorsPerPalette = 4,
    )
}
```

The naming (`TargetProfiles.GAME_BOY_SCREEN`) is Claude's discretion (D-05). Alternative: companion object on `ScreenSpec` with `ScreenSpec.GAME_BOY`. Pick one and be consistent — the planner uses whichever resolves `GameBoyConstants` derivation clearly.

---

### `gbkt-backend-gbdk/.../profiles/GameBoyConstants.kt` — derive from core preset (QUAL-02, D-05)

**Analog:** Existing file — in-place edit. Current lines 26-29:
```kotlin
/** Screen width in pixels. */
const val SCREEN_WIDTH = 160

/** Screen height in pixels. */
const val SCREEN_HEIGHT = 144
```

**After change** (deriving from TargetProfiles):
```kotlin
import io.github.gbkt.core.constraints.TargetProfiles

/** Screen width in pixels. Derived from the canonical Game Boy screen spec. */
val SCREEN_WIDTH = TargetProfiles.GAME_BOY_SCREEN.width   // 160

/** Screen height in pixels. Derived from the canonical Game Boy screen spec. */
val SCREEN_HEIGHT = TargetProfiles.GAME_BOY_SCREEN.height // 144
```

**Note:** `const val` drops to `val` (RESEARCH.md Pitfall 1). This is intentional and safe — grep confirmed zero annotation-argument uses of these constants. The `GameBoyProfile.kt` already constructs `ScreenSpec(width = GameBoyConstants.SCREEN_WIDTH, ...)` so the derivation chain becomes: `TargetProfiles.GAME_BOY_SCREEN` → `GameBoyConstants.SCREEN_WIDTH/HEIGHT` → `GameBoyProfile.screen`.

---

### `gbkt-backend-gbdk/.../codegen/visitor/ActorVisitor.kt` — 2 literal replacements (QUAL-02/03)

**Analog:** Existing file — mechanical in-place edits.

**Sites to replace** (RESEARCH.md Section 6):

Line 468: `CLiteral(144 - speed)` → `CLiteral(GameBoyConstants.SCREEN_HEIGHT - speed)`
Line 490: `CLiteral(160 - speed)` → `CLiteral(GameBoyConstants.SCREEN_WIDTH - speed)`

No import change needed — `GameBoyConstants` is already in scope in the visitor package.

---

### `gbkt-backend-gbdk/.../codegen/visitor/GBDKSystemVisitor.kt` — 2 literal replacements (QUAL-02/03)

**Analog:** Existing file — mechanical in-place edits.

**Sites to replace** (RESEARCH.md Section 6):

Line 172: `boundsWidth - 160` → `boundsWidth - GameBoyConstants.SCREEN_WIDTH`
Line 173: `boundsHeight - 144` → `boundsHeight - GameBoyConstants.SCREEN_HEIGHT`

---

### `gbkt-genre-platformer/.../codegen/PlatformerVisitor.kt` — 4 literal replacements (QUAL-02/03)

**Analog:** Existing file — mechanical in-place edits.

**Sites to replace** (RESEARCH.md Section 6):

Line 1986: `CLiteral(160)` → `CLiteral(GameBoyConstants.SCREEN_WIDTH)`
Line 1988: `CLiteral(160)` → `CLiteral(GameBoyConstants.SCREEN_WIDTH)`
Line 2000: `CLiteral(144)` → `CLiteral(GameBoyConstants.SCREEN_HEIGHT)`
Line 2002: `CLiteral(144)` → `CLiteral(GameBoyConstants.SCREEN_HEIGHT)`

Import needed: `import io.github.gbkt.backend.gbdk.profiles.GameBoyConstants` (verify if already present in the file's import block).

**ROM smoke required (D-17):** After editing PlatformerVisitor.kt, run the 7-example ROM sweep in a single chained Gradle invocation per D-18.

---

### Folded Todo — `gbkt-gradle-plugin/.../tasks/CompileRomTask.kt` — MBC5 warning (QUAL misc)

**Analog:** Existing file — in-place edit at `readMbcType()` lines 285-287.

**Pattern to copy** — `logger.warn()` style from any existing Gradle task warning in the plugin. The addition:
```kotlin
logger.warn(
    "gbkt-build.properties not found in $sourceDir — falling back to MBC5. " +
    "Run generateC to regenerate, or declare cartridge type in config { }."
)
```
Inserted before the `val hasRam = ...` fallback line. No output change, no byte-identity concern.

---

### Folded Todo — `gbkt-lang/.../dsl/SystemBuilders.kt` — ConfigBuilder setter consistency

**Analog:** Existing file — in-place edit of `ConfigBuilder` class (lines 531-585).

**Current inconsistency:** `cartridge` and `gbcTarget` each have both a `var` and a `fun` setter; `romBanks`/`ramBanks` have only `var`. Decision (Claude's discretion per RESEARCH.md Section 7): pick function-setter convention as primary (more DSL-idiomatic), make backing `var`s `private var`, add missing `fun romBanks(count: Int)` / `fun ramBanks(count: Int)`.

**DSL builder setter pattern** (from other builders in SystemBuilders.kt):
```kotlin
private var _fieldName: Type = defaultValue

fun fieldName(value: Type) {
    _fieldName = value
}
```

The `config{}` section in `context/DSL_REFERENCE.md` must be updated to document the unified function-setter convention in the same plan.

---

### Folded Todo — `gbkt-genre-rpg/.../dsl/RpgExtensions.kt` — RpgRegistry.clear() (QUAL misc)

**Analog:** `GameBuilderContext` in `gbkt-lang` — uses `with(builder) { ... }` pattern with `finally` cleanup.

**RESEARCH.md A5 assumption:** Verify whether `RpgRegistry.clear()` method exists or must be added. Grep: `grep -n "fun clear" gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/RpgExtensions.kt` — if absent, add it.

**Pattern for teardown** (modeled on GameBuilderContext):
```kotlin
// In game() function (GameBuilder.kt or RpgExtensions.kt):
try {
    GameBuilderContext.with(builder) { builder.block() }
} finally {
    RpgRegistry.clear()  // prevent stale entries across game{} builds in Gradle daemon
}
```

Or add a `with()` convenience to `RpgRegistry` matching the GameBuilderContext pattern.

---

### Evidence artifacts — `.planning/phases/17-.../evidence/` (D-15)

**Analog:** `.planning/phases/16-seed-triage/TRIAGE.md` — per-section audit table format.

**Pattern for DSL_REFERENCE.md audit table:**
```markdown
| # | Section | Line | Builder Source | Implemented Methods | Verdict |
|---|---------|------|----------------|---------------------|---------|
| 1 | State Machine DSL | ~372 | none | animationStates{}/setAnimationState() | archived→FEAT-STATE-MACHINES.md |
| 2 | Dialog System | ~922 | UIBuilders.kt:DialogBuilder | textSpeed,speaker,border,box,portrait,fontMode,say,choice,show,hide | rewritten |
| ... | | | | | |
```

**Evidence directory structure:**
```
.planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/
├── DOCS-AUDIT.md       — per-section audit table (D-15)
├── QUAL-LITERALS.md    — 160/144 exemption table (D-08)
└── QUAL-DETEKT.md      — detekt violation inventory after re-enable
```

---

## Shared Patterns

### License header
**Source:** Any existing Kotlin file in gbkt-backend-gbdk or gbkt-core (e.g., `GameBoyConstants.kt` lines 1-6).
**Apply to:** New `TargetProfiles.kt` file.
```kotlin
/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
```

### detekt.yml exclusion comment style
**Source:** `detekt.yml` (any rule block, e.g., LongMethod exclusions lines 22-31).
**Apply to:** Every new `excludes:` entry added to re-enabled rules (D-02).
Pattern: `- '**/path/**'  # One-sentence rationale matching the exclusion`

### Gradle includedBuild bridge
**Source:** `build.gradle.kts` line 105.
**Apply to:** Root `build.gradle.kts` detekt bridge (D-03).
```kotlin
dependsOn(gradle.includedBuild("gbkt-gradle-plugin").task(":detekt"))
```

### Backlog seed front-matter
**Source:** `.planning/backlog/v0.2.0/SEED-001-ide-and-tooling.md` lines 1-11.
**Apply to:** All 12 FEAT-*.md backlog files.
Required fields: `id`, `status: dormant`, `planted`, `planted_during`, `trigger_when`, `scope`, `triage_disposition: RE-DEFERRED`, `triage_evidence`, `triage_date`.

### ROM byte-identity smoke gate
**Source:** CONTEXT.md D-17/D-18, RESEARCH.md Section 6 "Byte-identity guarantee".
**Apply to:** Any plan that edits `ActorVisitor.kt`, `GBDKSystemVisitor.kt`, or `PlatformerVisitor.kt`.
Command pattern (single chained invocation per D-18):
```bash
./gradlew :gbkt-examples:pong:buildRom :gbkt-examples:platformer-template:buildRom \
  :gbkt-examples:metasprites:buildRom :gbkt-examples:breakout:buildRom \
  :gbkt-examples:banks:buildRom :gbkt-examples:simple-physics:buildRom \
  :gbkt-examples:metasprites-stress:buildRom
```

---

## No Analog Found

All files in this phase have direct analogs. No files require novel patterns from external references.

---

## Metadata

**Analog search scope:** `gbkt-backend-gbdk/`, `gbkt-core/`, `gbkt-lang/`, `gbkt-genre-rpg/`, `gbkt-genre-platformer/`, `gbkt-gradle-plugin/`, `.planning/backlog/v0.2.0/`, root config files
**Files scanned:** 14 source files read directly
**Pattern extraction date:** 2026-06-12
