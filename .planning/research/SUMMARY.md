# Project Research Summary

**Project:** gbkt v0.1.1 Hardening
**Domain:** Kotlin DSL-to-C compiler pipeline — internal hardening patch release
**Researched:** 2026-06-12
**Confidence:** HIGH

## Executive Summary

v0.1.1 is a focused internal hardening milestone, not a feature release. Its five deliverables are: (1) close all 44 open seeds with evidence-backed dispositions, (2) annotate two deprecated DSL APIs (SEED-023 `whenever`, SEED-025 `combatIsInState(String,String)`), (3) reconcile the 13 stale-API sections in `context/DSL_REFERENCE.md` to match the implemented DSL, (4) resolve QUAL-01..03 (detekt violations, screen-constant replacements, magic-pixel elimination), and (5) burn down 46 SonarCloud S3776 HIGH cognitive-complexity findings via extract-method refactoring. No new public API symbols, no new DSL constructs, and no new Gradle/library dependencies are introduced in this milestone.

The recommended approach is to work in three parallel streams where possible: a docs/static-analysis stream (DSL_REFERENCE reconciliation, QUAL-01..03, S3776 burn-down) that carries zero C-output risk; a low-risk DSL/infrastructure stream (deprecation annotations, small seed fixes); and a sequential codegen-defect stream that begins only after a per-seed triage confirms which of the 44 seeds are still open on current master versus already fixed by Phases 12–13.8. The byte-identity oracle — a 7-example `buildRom` sweep comparing ROM hashes before and after every change touching codegen — is the authoritative gate for the S3776 and seed-fix streams.

The dominant risk is the S3776 cognitive-complexity refactors. The hotspot files (`GBDKPipeline.kt` at 5,397 lines, `GBDKSystemVisitor.kt` at 6,390 lines) emit C by constructing `CStatement` trees through conditional accumulation — nesting order IS the correctness guarantee. An extract-method refactor that looks semantically neutral can silently change which C branch fires first, which a JVM test suite will not detect. The prevention is mandatory: byte-identity ROM sweep after every S3776 refactor commit, S3776 refactors in separate commits from seed fixes (so oracle assertions can distinguish "intended C change" from "unintended regression"), and a hard cap of 5 NOSONAR suppressions across the entire milestone.

## Key Findings

### Recommended Stack

No new Gradle plugins or library dependencies are added in v0.1.1. The toolchain is frozen at Kotlin 2.3.20 / Gradle 9.5.1 / JVM 21. Detekt stays at 1.23.8 (not officially supported for Kotlin 2.3.20 but CI-green in practice; detekt 2.0.0-alpha.3 targets 2.3.21 but is alpha and wrong risk profile for a hardening milestone). The composite build (`gbkt-gradle-plugin`) currently receives no detekt coverage because `subprojects {}` in the root does not reach included builds; this gap is closed by adding detekt directly to `gbkt-gradle-plugin/build.gradle.kts` using the already-available root version catalog alias.

**Core technologies:**
- Kotlin 2.3.20 / Gradle 9.5.1: unchanged, stay here — no upgrades during hardening
- detekt 1.23.8: static analysis for QUAL-01 — fix via exclusion removal from `detekt.yml`, NOT via baseline file generation
- SonarCloud (sonarqube plugin 7.3.1.8318): authoritative source for 46 S3776 HIGH findings — Sonar runs independently of detekt; detekt exclusions do not suppress Sonar
- Kotlin `@Deprecated` + `ReplaceWith`: SEED-023/025 deprecation mechanics — use `DeprecationLevel.WARNING` with accurate `ReplaceWith`; skip the `ERROR` intermediate level for pre-1.0 APIs where all call sites are owned

### Expected Features

**Must have (table stakes):**
- All 44 seeds reach terminal disposition (FIXED / VERIFIED-ALREADY-FIXED / RE-DEFERRED) with evidence artifacts — seed closure without evidence is assertion-based and insufficient
- `whenever()` annotated `@Deprecated(ReplaceWith("runIf(...)"))`, all in-tree usages migrated in the same commit
- `combatIsInState(String,String)` annotated `@Deprecated`; removal deferred to v0.2.0
- All 13 stale-API sections in `DSL_REFERENCE.md` rewritten to match implemented APIs, stub unimplemented sections as "v0.2.0 candidate", or moved to an archive; every removed section produces a v0.2.0 tracking entry
- QUAL-01 detekt violation set from Phase 08 deferral eliminated (no baseline files committed)
- QUAL-02: 160/144 magic-pixel literals replaced with `ScreenSpec.WIDTH`/`HEIGHT` constants
- Sonar S3776: 46 → 0 HIGH findings via extract-method refactoring, not threshold raising

**Should have (differentiators):**
- Re-verification of seeds against current master (not trust of planted-time status) — correctly surfaces how many seeds Phases 12–13.8 actually closed
- Batch deprecation train convention documented in CONTRIBUTING.md — establishes the `WARNING → v+1 removal` pattern for all future gbkt API evolution
- Aspirational DSL sections archived (e.g., `DSL_ASPIRATIONAL.md`) rather than deleted — preserves design spec for v0.2.0 implementers
- SEED-026 validatePlugins green (optional if bandwidth permits after P1 complete)

**Defer to v0.2.0+:**
- Implement any of the 13 stale DSL sections (dialog, entity pools, tweening, pathfinding, full camera/physics API)
- `whenever` and `combatIsInState(String)` removal (one release cycle after deprecation marking)
- SEED-RAW-C-CODEGEN-AST-MIGRATION, SEED-PHASE-X-CPAREN, genre-codegen phases 07.5–07.8

### Architecture Approach

All v0.1.1 work is modifications to existing files within the current 20-module layout. No new modules, no new public IR interfaces, no new visitor contracts. The three work streams are structurally independent: Stream A (docs + static analysis) has zero C-output risk and can start immediately; Stream C (deprecations + small DSL fixes) touches only DSL-surface files with no codegen impact; Stream D (codegen defect fixes) must be preceded by seed triage (Stream B) and proceeds sequentially through defect clusters to minimize oracle churn.

**Major components touched in v0.1.1:**
1. `GBDKPipeline.kt` + `GBDKSystemVisitor.kt` (gbkt-backend-gbdk) — S3776 extract-method refactors + SEED-014/015 banking gate fixes; highest risk; byte-identity oracle required per commit
2. `MetaspriteVisitor.kt` / `PlatformerVisitor.kt` — seed cluster fixes (Clusters B, C, D, E); C output changes intentionally; RED→GREEN oracle cycle
3. `ConvertSpritesTask.kt` + `PngUtils.kt` (gbkt-gradle-plugin) — SEED-PHASE-13 tRNS transparency fix; byte-identity sweep mandatory
4. `ScriptBuilder.kt` / `RpgExtensions.kt` — SEED-023/025 deprecation; zero codegen risk
5. `context/DSL_REFERENCE.md` — 13 stale sections reconciled; zero codegen risk

### Critical Pitfalls

1. **Behavior-changing "pure" complexity refactor** — Extract-method in visitor/pipeline files can change which C branches fire first without any JVM test detecting it. Prevention: byte-identity ROM sweep after EVERY S3776 commit touching `codegen/visitor/**` or `GBDKPipeline.kt`; S3776 commits and bug-fix commits must never be combined.

2. **Closing seeds "already fixed" without reproduction evidence** — Seeds planted pre-Phase 12.6 may reference bugs that later phases fixed via a different code path; the original root cause may still be latent. Prevention: per-seed triage record with commit hash or screenshot; visual-symptom seeds require a screenshot at HEAD.

3. **Deleting DSL doc sections for APIs that partially exist** — Each of the 13 flagged sections contains a mix of implemented and unimplemented APIs. Prevention: per-method source audit before editing any section; aspirational content moves to an archive document.

4. **Detekt baseline file misuse** — Generating and committing a baseline to silence violations defeats the cleanup goal. Prevention: fix violations directly or add explicit `detekt.yml` exclusions with rationale comments; never commit a baseline file.

5. **Formatting churn polluting git blame in visitor/pipeline files** — Reformatting `GBDKPipeline.kt` (5,397 lines) or `GBDKSystemVisitor.kt` (6,390 lines) destroys `git blame` history critical for future palette/bank debugging. Prevention: formatting-only commits labeled `style:` strictly separated from `refactor:` commits.

## Implications for Roadmap

### Phase 1: Seed Triage
**Rationale:** Every downstream codegen fix stream depends on knowing the true open state of the 44 seeds against current master. Without triage, fix phases risk spending effort on already-resolved bugs or re-introducing a fix that conflicts with the existing Phases 12.9/13.3/13.7/13.8 fix chains for palette/bank seeds.
**Delivers:** Disposition table for all 44 seeds with commit attribution or screenshot evidence per seed; confirmed list of seeds requiring v0.1.1 fix phases.
**Addresses:** Backlog triage table-stakes requirement.
**Avoids:** Closing seeds without evidence; symptom-fix without root-cause understanding.

### Phase 2: Docs and Static Analysis (parallel-capable)
**Rationale:** Zero C-output risk. Can begin immediately and run concurrently with Phase 1. Three sub-streams: DSL_REFERENCE.md reconciliation, QUAL-01..03 cleanup, S3776 cognitive-complexity burn-down. S3776 carries the highest execution risk in the milestone but is architecturally independent of seed fix work.
**Delivers:** `context/DSL_REFERENCE.md` accurate for all implemented APIs; aspirational sections archived with v0.2.0 tracking entries; QUAL-01 detekt clean; QUAL-02/03 screen constants in place; Sonar S3776 finding count at 0.
**Uses:** detekt 1.23.8 exclusion-removal workflow; byte-identity ROM sweep as exit criterion for every S3776 commit; composite-build detekt wiring fix for `gbkt-gradle-plugin`.
**Avoids:** S3776 suppression via NOSONAR (hard cap 5/milestone); complexity displacement into untestable helpers; formatting churn mixed with logic changes; cross-module detekt drift via per-module-only runs; partial section deletion for mixed implemented/unimplemented APIs.

### Phase 3: Deprecation Removals and Low-Risk DSL Fixes (parallel-capable)
**Rationale:** DSL-surface only. Zero codegen risk. Can run concurrently with Phase 2. Establishes the gbkt deprecation convention for future API evolution. SEED-007 (`GameBuilder.kt` palette slot default) and SEED-026 (Gradle hygiene) bundle cleanly at the same risk tier.
**Delivers:** `whenever()` `@Deprecated` with all in-tree usages migrated; `combatIsInState(String,String)` `@Deprecated`; deprecation convention in CONTRIBUTING.md; SEED-007 palette slot fix; SEED-026 pluginTest race workaround.
**Avoids:** Changelog-only deprecation without `@Deprecated` annotation; `ERROR` intermediate deprecation level for pre-1.0 owned call sites.

### Phase 4: Codegen Defect Fixes — Metasprite Clusters (sequential, post-triage)
**Rationale:** Must follow Phase 1. Addresses Cluster B (SEED-004/005/006/013 visual parity) then Cluster C (SEED-008/009/010/011 structural/latent). D1 before D2: both touch `MetaspriteVisitor.kt`; D1 fixes byte layout, D2 builds on it.
**Delivers:** Metasprites example ROM visually correct; emission tests guarding structural fixes; corrected metasprites baseline for Phase 5 oracle.
**Avoids:** S3776 refactors mixed with bug fixes in same commit.

### Phase 5: Codegen Defect Fixes — Banks and Sprite Transparency (sequential, post-Phase 4)
**Rationale:** Widest blast radius (SEED-014/015/016 banking). Must follow Phase 4 so metasprites oracle is stable before banking changes sweep all zone games. Requires dedicated discuss-phase + research before code changes per `feedback_route_to_proper_phase_when_blast_radius_is_wide`.
**Delivers:** Banking gate and trampoline emission correct for zone games; SRAM round-trip UAT executed; tRNS sprite outline fix; platformer player transparency unchanged.
**Avoids:** Parallel `gradle clean` invocations (Kotlin daemon collision); byte-identity oracle mixed with intentional C changes.

### Phase 6: Codegen Defect Fixes — Platformer and Remaining Seeds (sequential, post-Phase 5)
**Rationale:** Follows Phase 5 (7-target oracle stable). Removes 4 `cEmit()` escape hatches from `PlatformerTemplate.kt` by adding proper auto-emission to `PlatformerVisitor.kt`. Closes remaining open seeds.
**Delivers:** Platformer template without `cEmit` escapes; 3 platformer UAT anchors re-shot; all remaining open seeds dispositioned.
**Avoids:** Re-breaking platformer UAT baselines; missing GBC mode in MCP emulator for platformer captures (always `gbcMode=true`).

### Phase Ordering Rationale

- Seed triage (Phase 1) is the sole hard gate for codegen fix phases (4–6); it feeds all of them.
- Docs/static analysis (Phase 2) and deprecation removals (Phase 3) are fully parallel to Phase 1 and to each other — maximizing throughput by running zero-risk work alongside triage.
- Codegen fix phases (4–6) are sequenced to minimize oracle churn: metasprites first (establishes baseline), banks second (widest blast radius, needs stable oracle), sprite transparency third (verifies against Phase 4 baseline), platformer last (independent of banking, benefits from stable overall oracle).
- S3776 burn-down commits must be strictly separated from seed-fix commits at the plan/commit level so the byte-identity oracle can unambiguously distinguish "must-be-zero-change" (refactor) from "intended C change" (bug fix).

### Research Flags

Phases needing deeper research during planning:
- **Phase 5 (Banks cluster SEED-014/015/016):** Highest blast radius. Trampoline emission logic and banking gate require dedicated discuss-phase + research before code changes.

Phases with standard patterns (skip research-phase):
- **Phase 2 (Docs + static analysis):** detekt exclusion-removal and S3776 extract-method patterns are fully documented in STACK.md and ARCHITECTURE.md.
- **Phase 3 (Deprecation removals):** Kotlin `@Deprecated` mechanics are well-understood; one-liner changes, zero codegen impact.
- **Phase 4 (Metasprite clusters):** Root causes enumerated at file/line granularity in ARCHITECTURE.md. Standard RED→GREEN emission test cycle.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Toolchain decisions verified against official sources and CI evidence; all alternatives explicitly rejected with rationale |
| Features | HIGH | Deliverables drawn directly from PROJECT.md, seed corpus, and DSL_REFERENCE.md audit |
| Architecture | HIGH | Root causes enumerated at file/line level from direct codebase inspection; component touch map verified |
| Pitfalls | HIGH | Drawn from project post-mortems and verifier-gates contract — codebase-specific incident patterns, not generic advice |

**Overall confidence:** HIGH

### Gaps to Address

- **SEED-014 gate status:** The `hasZoneSceneBinder` guard at `GBDKPipeline.kt:1164-1168` may already satisfy SEED-014 on current master. Phase 1 triage must run `BanksEmissionTest.kt` INV-2 sentinel to confirm before opening a Phase 5 fix plan.
- **Phase 08 detekt scope enumeration:** QUAL-01 is described as "violations from the Phase 08 deferral" but the specific rule set is not enumerated. Phase 2 planning must enumerate the exact violation classes at spec time.
- **QUAL-03 scope:** Remaining magic-pixel elimination beyond 160/144 screen constants is not fully defined. Confirm at Phase 2 spec time which additional numeric literals are in scope vs. intentional hardware constants (tile size 8/16, bank slot numbers).
- **`whenever` semantic decision (SEED-023):** FEATURES.md flags a discuss-phase decision needed: does `whenever` get deprecated toward `runIf`, or receive distinct reactive semantics as a v0.2.0 feature? Phase 3 deprecation action is blocked until this decision is made.

## Sources

### Primary (HIGH confidence)
- `.planning/research/STACK.md` — detekt/Sonar/deprecation tooling decisions, configuration patterns
- `.planning/research/FEATURES.md` — hardening deliverable taxonomy, dependency graph, MVP definition
- `.planning/research/ARCHITECTURE.md` — S3776 hotspot analysis, seed cluster root causes, work stream ordering
- `.planning/research/PITFALLS.md` — 12 pitfalls with prevention strategies drawn from project post-mortems
- `.planning/PROJECT.md` — authoritative milestone scope
- `.planning/seeds/` — all 44 seed files, direct inspection
- `context/DSL_REFERENCE.md` — 13 stale-API sections
- `.planning/verifier-gates.md` — Visual Evidence Rule, rom-build gate definition

### Secondary (MEDIUM confidence)
- detekt compatibility table (https://detekt.dev/docs/introduction/compatibility/) — version support matrix
- Kotlin `@Deprecated` / `DeprecationLevel` API — annotation mechanics

---
*Research completed: 2026-06-12*
*Ready for roadmap: yes*
