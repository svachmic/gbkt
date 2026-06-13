---
phase: 17-docs-reconciliation-and-quality-cleanup
verified: 2026-06-12T22:00:00Z
status: human_needed
score: 5/5 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Confirm WR-01 is advisory: GAME_BOY_COLOR_SCREEN bpp=4 vs shipped profile bpp=2"
    expected: "Developer either acknowledges the preset is forward-looking only (and downgrades MUST to SHOULD in KDoc) or aligns with GameBoyColorProfile before milestone close"
    why_human: "WR-01 from 17-REVIEW is a real correctness concern (wrong bitsPerPixel in the labeled canonical preset) but GAME_BOY_COLOR_SCREEN has zero current consumers — it cannot cause a runtime regression until SEED-TARGETPROFILE-SCREEN-THREADING lands. Whether the advisory is a blocker for milestone sign-off requires developer judgment."
  - test: "Confirm WR-04 / WR-05 are advisory for v0.1.1 (ConfigBuilder property-to-function-setter breaking change)"
    expected: "Developer confirms no external consumers exist on v0.1.0 that would break on upgrade, OR approves @Deprecated shim plan for v0.1.2"
    why_human: "WR-04 (removed public mutable properties with no deprecation cycle) and WR-05 (stale deprecation guidance in GbktExtension.kt KDoc, CompileRomTask comment, and example comments still using old property-setter syntax) are user-visible but only affect external callers of the v0.1.0 DSL. Cannot verify mechanically whether any external callers exist."
---

# Phase 17: Docs Reconciliation and Quality Cleanup Verification Report

**Phase Goal:** DSL_REFERENCE.md accurately describes the implemented DSL, the 2 pending doc-only fixes are applied, detekt passes across all modules including the gbkt-gradle-plugin composite build (no baseline files committed), and magic-pixel literals are replaced with platform-aware constants

**Verified:** 2026-06-12T22:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Each of the 13 stale-API sections in DSL_REFERENCE.md is updated: implemented APIs have accurate documentation; unimplemented sections archived as v0.2.0 candidates | VERIFIED | Zero "Stale-API caveat" blocks in DSL_REFERENCE.md (grep count=0). 12 FEAT-*.md files exist in .planning/backlog/v0.2.0/ each with "Verbatim removed content" and "Removed from context/DSL_REFERENCE.md" provenance. Section 1 renamed to Animation State Machine; sections 2-13 rewritten across plans 17-08/09/10. |
| 2 | The 2 doc-only fixes are applied: deprecated-API example block corrected; subpixel {} no-op behavior clarified | VERIFIED | DSL_REFERENCE.md lines 35-38 show accurate DEPRECATED API block. Lines 45 and 60 state "emits no IR — variables declared inside are recorded at the enclosing game scope, not a sub-scope" — two locations consistent. |
| 3 | ./gradlew detekt passes with zero violations across all modules including the gbkt-gradle-plugin composite build (no baseline files committed) | VERIFIED | ./gradlew detekt → BUILD SUCCESSFUL, 38 actionable tasks, 0 violations. No detekt-baseline.xml anywhere outside build/. Four rules (MagicNumber, UnusedPrivateMember, UnusedPrivateProperty, ComplexCondition) all show active: true in detekt.yml. Root build.gradle.kts has zero "baseline = file" lines. Composite bridge present: gradle.includedBuild("gbkt-gradle-plugin").task(":detekt"). |
| 4 | All 160/144 magic-pixel literals replaced by ScreenSpec.WIDTH/HEIGHT constants (via TargetProfiles.GAME_BOY_SCREEN feeding GameBoyConstants.SCREEN_WIDTH/SCREEN_HEIGHT) | VERIFIED | TargetProfiles.kt exists with GAME_BOY_SCREEN (width=160, height=144). GameBoyConstants.kt derives SCREEN_WIDTH/HEIGHT via TargetProfiles.GAME_BOY_SCREEN.width/.height (val, not const val — zero annotation-argument usages confirmed). All 8 in-scope literals replaced in ActorVisitor.kt (2), GBDKSystemVisitor.kt (2), PlatformerVisitor.kt (4). Zero bare 160/144 in non-comment executable code in any of the 3 visitor files. |
| 5 | The in-scope set of remaining magic-pixel literals is fully enumerated and eliminated; intentional hardware constants are documented as exempt | VERIFIED | QUAL-LITERALS.md exists with 47-entry exemption table using implements-the-hardware vs consumes-the-platform rationale axis (52 hits in the rationale column). 7-example ROM sweep shows BUILD SUCCESSFUL across all examples. Seed SEED-TARGETPROFILE-SCREEN-THREADING.md filed for v0.2.0 (D-06). |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `gbkt-core/src/main/kotlin/io/github/gbkt/core/constraints/TargetProfiles.kt` | GAME_BOY_SCREEN canonical preset | VERIFIED | Exists, 58 lines, MPL header, object TargetProfiles with GAME_BOY_SCREEN (160x144) and GAME_BOY_COLOR_SCREEN |
| `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/profiles/GameBoyConstants.kt` | SCREEN_WIDTH/HEIGHT derived from TargetProfiles | VERIFIED | Imports TargetProfiles; val SCREEN_WIDTH = TargetProfiles.GAME_BOY_SCREEN.width; val SCREEN_HEIGHT = TargetProfiles.GAME_BOY_SCREEN.height |
| `detekt.yml` | 4 rules active: true; rationale-commented excludes; no baseline | VERIFIED | MagicNumber, UnusedPrivateMember, UnusedPrivateProperty, ComplexCondition all active: true. ignoreNumbers list present. No detekt-baseline.xml. |
| `build.gradle.kts` | Composite detekt bridge; no baseline wiring | VERIFIED | gradle.includedBuild("gbkt-gradle-plugin").task(":detekt") at line 214. Zero "baseline = file" lines. |
| `gbkt-gradle-plugin/build.gradle.kts` | detekt plugin applied; config pointing at root detekt.yml | VERIFIED | alias(libs.plugins.detekt) applied; detekt { config.setFrom(file("${rootDir}/../detekt.yml")) } |
| `.planning/phases/17-.../evidence/DOCS-AUDIT.md` | 174-row per-method audit; Full-Document Triage Sweep section | VERIFIED | 483 lines; 176 verdict rows (accurate/corrected/moved-to-backlog); Full-Document Triage Sweep heading present twice (section + cross-doc append) |
| `.planning/backlog/v0.2.0/FEAT-*.md` (12 files) | Verbatim archive + provenance headers | VERIFIED | Exactly 12 files; all 12 have "Verbatim removed content" section; all 12 have "Removed from context/DSL_REFERENCE.md" provenance; zero "removal-commit-TBD" placeholders (all backfilled in plan 17-12) |
| `.planning/REQUIREMENTS.md` | 12 FEAT-* IDs replacing FEAT-XX placeholder | VERIFIED | All 12 FEAT-* IDs present; FEAT-XX count = 0 |
| `.planning/phases/17-.../evidence/QUAL-LITERALS.md` | ROM sweep results + exemption table | VERIFIED | Contains D-17 ROM smoke table (all 7 PASS), byte-identity verdict, 47-entry exemption table with consumes-the-platform rationale |
| `.planning/backlog/v0.2.0/SEED-TARGETPROFILE-SCREEN-THREADING.md` | D-06 v0.2.0 seed | VERIFIED | Exists; 11 TargetProfile references; documents D-06 deferral |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| GameBoyConstants.kt | TargetProfiles.GAME_BOY_SCREEN | import + .width/.height | VERIFIED | Import confirmed; val SCREEN_WIDTH = TargetProfiles.GAME_BOY_SCREEN.width at line 30 |
| ActorVisitor.kt | GameBoyConstants.SCREEN_WIDTH/HEIGHT | import + CLiteral() | VERIFIED | Import confirmed; CLiteral(GameBoyConstants.SCREEN_HEIGHT - speed) at line 472; CLiteral(GameBoyConstants.SCREEN_WIDTH - speed) at line 498 |
| GBDKSystemVisitor.kt | GameBoyConstants.SCREEN_WIDTH/HEIGHT | import + arithmetic | VERIFIED | Import confirmed; boundsWidth - GameBoyConstants.SCREEN_WIDTH at line 173; boundsHeight - GameBoyConstants.SCREEN_HEIGHT at line 174 |
| PlatformerVisitor.kt | GameBoyConstants.SCREEN_WIDTH/HEIGHT | import + CLiteral() | VERIFIED | Import confirmed; all 4 CLiteral(GameBoyConstants.SCREEN_WIDTH/HEIGHT) at lines 1990/1993/2008/2011 |
| root detekt task | gbkt-gradle-plugin :detekt | gradle.includedBuild bridge | VERIFIED | tasks.named("detekt") { dependsOn(gradle.includedBuild("gbkt-gradle-plugin").task(":detekt")) } at build.gradle.kts:214 |
| gbkt-gradle-plugin detekt config | root detekt.yml | file("${rootDir}/../detekt.yml") | VERIFIED | config.setFrom(file("${rootDir}/../detekt.yml")) in gbkt-gradle-plugin/build.gradle.kts |
| FEAT-*.md provenance headers | DSL_REFERENCE.md removal commits | real git short hashes | VERIFIED | Plan 17-12 backfilled all 12 files with real commit hashes (eb0c6aaa, d6e1e5f7, 183bd5a3, 63afe76a, 929653a4) |

### Data-Flow Trace (Level 4)

All phase-17 artifacts are configuration, documentation, or pure refactors (constant derivation chain, literal replacement). The data flow: TargetProfiles.GAME_BOY_SCREEN.width (=160) → GameBoyConstants.SCREEN_WIDTH → CLiteral(GameBoyConstants.SCREEN_WIDTH) → emitted C integer. Arithmetic-equivalent by construction; verified by ROM byte-identity sweep.

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|-------------------|--------|
| GameBoyConstants.SCREEN_WIDTH | 160 | TargetProfiles.GAME_BOY_SCREEN.width | Yes — 160 (ScreenSpec field) | FLOWING |
| GameBoyConstants.SCREEN_HEIGHT | 144 | TargetProfiles.GAME_BOY_SCREEN.height | Yes — 144 (ScreenSpec field) | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| TargetProfiles.kt compiles | ./gradlew :gbkt-core:compileKotlin -q | BUILD SUCCESSFUL (no output) | PASS |
| GameBoyConstants.kt compiles with derivation | ./gradlew :gbkt-backend-gbdk:compileKotlin -q | BUILD SUCCESSFUL (no output) | PASS |
| PlatformerVisitor.kt compiles with replacements | ./gradlew :gbkt-genre-platformer:compileKotlin -q | BUILD SUCCESSFUL (no output) | PASS |
| detekt whole-repo zero violations | ./gradlew detekt | BUILD SUCCESSFUL, 38 actionable tasks | PASS |
| No detekt-baseline.xml outside build/ | find . -name detekt-baseline.xml -not -path '*/build/*' | Empty output (0 files) | PASS |
| 160/144 bare literals in 3 visitor files (executable code) | grep -n '\b160\b\|\b144\b' ActorVisitor.kt GBDKSystemVisitor.kt PlatformerVisitor.kt | grep -vE '//|"' | All empty | PASS |

### Requirements Coverage

| Requirement | Phase | Description | Status | Evidence |
|-------------|-------|-------------|--------|---------|
| DOCS-01 | 17 | 13 stale-API sections audited; implemented APIs accurately documented | SATISFIED | DOCS-AUDIT.md has 176 verdict rows; all 13 section names present; DSL_REFERENCE.md zero stale-API caveat blocks |
| DOCS-02 | 17 | Unimplemented content archived as v0.2.0 candidates | SATISFIED | 12 FEAT-*.md files with verbatim content + provenance headers; REQUIREMENTS.md has 12 individual FEAT-* entries |
| DOCS-03 | 17 | 2 doc-only fixes applied (deprecated block + subpixel clarification) | SATISFIED | DSL_REFERENCE.md deprecated-API block verified accurate; subpixel section states "emits no IR" in two consistent locations |
| QUAL-01 | 17 | Detekt zero violations including composite build; no baselines | SATISFIED | ./gradlew detekt → BUILD SUCCESSFUL, 0 violations; no detekt-baseline.xml; composite bridge wired |
| QUAL-02 | 17 | Magic 160/144 literals replaced with platform-aware constants | SATISFIED | 8 literals replaced in 3 visitor files; TargetProfiles → GameBoyConstants derivation chain established |
| QUAL-03 | 17 | Remaining magic-pixel literals enumerated; hardware constants exempt | SATISFIED | QUAL-LITERALS.md exemption table with 47 entries; SEED-TARGETPROFILE-SCREEN-THREADING filed for v0.2.0 |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| detekt.yml:181 | 181 | `# TODO/FIXME are fine` comment | Info | This is the ForbiddenComment rule's own description, not a debt marker in code — not a blocker |

No TBD/FIXME/XXX unresolved debt markers found in phase-17 modified source files. The one "TODO/FIXME" hit is inside the ForbiddenComment rule block in detekt.yml and is a rule description, not a debt marker.

### Probe Execution

No formal probe-*.sh scripts declared. The ROM byte-identity sweep was executed as part of plan 17-05 and 17-11 — results captured in QUAL-LITERALS.md.

| Probe | Command | Result | Status |
|-------|---------|--------|--------|
| 7-example ROM sweep (17-05) | ./gradlew :pong:buildRom :platformer-template:buildRom :metasprites:buildRom :breakout:buildRom :banks:buildRom :simple-physics:buildRom :metasprites-stress:buildRom | All 7 BUILD SUCCESSFUL (QUAL-LITERALS.md) | PASS |
| 7-example ROM sweep (17-11) | Same chained invocation | All 7 BUILD SUCCESSFUL (17-11-SUMMARY.md) | PASS |

### Human Verification Required

#### 1. WR-01: GAME_BOY_COLOR_SCREEN bitsPerPixel correctness vs advisory status

**Test:** Review `TargetProfiles.GAME_BOY_COLOR_SCREEN` in `gbkt-core/src/main/kotlin/io/github/gbkt/core/constraints/TargetProfiles.kt:46-56`. The preset declares `bitsPerPixel = 4` while the actual shipped `GameBoyColorProfile.kt` uses `bitsPerPixel = 2` with the comment "Still 2bpp tiles, but with palettes." The object's KDoc asserts "All backends MUST derive from this object" but only SCREEN_WIDTH/HEIGHT are consumed (by GameBoyConstants).

**Expected:** Developer either (a) aligns GAME_BOY_COLOR_SCREEN to bitsPerPixel=2 and wires GameBoyColorProfile.screen to the preset, OR (b) softens the MUST-derive KDoc to cover only width/height and defers full alignment to SEED-TARGETPROFILE-SCREEN-THREADING.

**Why human:** GAME_BOY_COLOR_SCREEN has zero consumers today so cannot cause a runtime regression. Whether the KDoc overstatement constitutes a blocker for milestone sign-off requires developer judgment on external risk posture.

#### 2. WR-04/WR-05: ConfigBuilder breaking change advisory for external consumers

**Test:** Review `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SystemBuilders.kt` — public mutable properties `romBanks`, `ramBanks` (property setters) were removed and replaced with function setters in plan 17-11 with no deprecation shim. Also check `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/GbktExtension.kt:166` — the @deprecated KDoc still says "Set `ramBanks` in the DSL `config { ramBanks = N }` block" which no longer compiles.

**Expected:** Developer confirms either (a) no external consumers exist against v0.1.0 who would see this break, OR (b) approves adding @Deprecated shims for the removed property setters and updating the stale deprecation guidance at the 4 sites identified in WR-05 (GbktExtension.kt:166, CompileRomTask.kt:319, platformer-template example comment, metasprites test comment).

**Why human:** Cannot determine programmatically whether external consumers of the v0.1.0 public DSL exist. The in-repo migration is complete (zero property-setter usages compile). External ecosystem risk is a developer call.

### Gaps Summary

No blocking gaps found. All 5 success criteria are met in the codebase. The 9 warnings from 17-REVIEW.md are advisory findings from the code review layer — none block the phase goal as stated (WR-01 has zero consumers; WR-02 is a KDoc/implementation divergence on an internal hook; WR-03 is a resilience concern; WR-04/05 affect external callers only; WR-06/07/08/09 are test quality and diagnostic precision issues).

The two human verification items above are the only open questions relevant to milestone sign-off decision.

---

_Verified: 2026-06-12T22:00:00Z_
_Verifier: Claude (gsd-verifier)_
