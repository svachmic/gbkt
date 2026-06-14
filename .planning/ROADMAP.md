# Roadmap: gbkt Compiler Pipeline Rebuild

## Milestones

- ✅ **v0.1.0 MVP — Compiler Pipeline Rebuild** — Phases 1–15 (shipped 2026-06-09)
- 🚧 **v0.1.1 Hardening** — Phases 16–22 (in progress)

## Phases

<details>
<summary>✅ v0.1.0 MVP — Compiler Pipeline Rebuild (66 phases, 652 plans, 887 tasks) — SHIPPED 2026-06-09</summary>

The rebuild transformed gbkt from a string-concatenating prototype into a clean compiler pipeline: Kotlin DSL → non-sealed IR + visitor dispatch → 9 ordered analysis passes (automatic bank/VRAM/OAM/RAM allocation) → structured C AST → GBDK C. Delivered across:

- **Phases 1–5** — IR foundation + DSL, structured codegen + migration cut, asset pipeline + JVM test runner, the 9 analysis passes, end-to-end integration.
- **Phases 5.05–06.x** — source maps, V2 runtime completion, DSL ergonomics, full gap closure, and the V1 feature-parity port (camera/movement/save/physics/UI/world/combat/RPG/genres).
- **Phases 07.1–07.4, 07.9** — agent-driven test infrastructure (GbktTestExtension, embedded Coffee-GB emulator, MCP server), entity-pool + sport-genre codegen fixes, signed/unsigned literal discipline.
- **Phases 09–13.8** — GBDK SDK reference-port validation track (simple_physics → metasprites → banks → platformer_template) with codegen-oracle byte-identity gates and binding visual UAT; framework primitives; palette/sprite/tilemap-collision hardening.
- **Phase 14** — release cleanup: retired dead examples (Explorer, racer), dropped V2 suffixes, removed pre-AST dead code.
- **Phase 15** — hard release gate: drove the entire pre-existing-red JVM suite green diagnose-first (18 tests; 0 threshold-weakenings), zero production-codegen drift. VERIFICATION passed 7/7.

Full phase-by-phase detail (goals, plans, success criteria) is archived in **`.planning/milestones/v0.1.0-ROADMAP.md`**.

</details>

### 🚧 v0.1.1 Hardening (In Progress)

**Milestone Goal:** Drain the v0.1.0 deferred-debt backlog — every seed gets a terminal disposition (fixed, verified-closed, or explicitly re-routed), the docs tell the truth, and static-analysis debt is burned down.

- [x] **Phase 16: Seed Triage** - Establish terminal dispositions for all 44 seeds against current master; gate for all codegen fix phases — COMPLETE 2026-06-12 (47 dispositions: 24 VERIFIED-FIXED, 12 CONFIRMED-OPEN, 10 RE-DEFERRED; seeds/ is live confirmed-open queue)
- [x] **Phase 17: Docs Reconciliation and Quality Cleanup** - DSL_REFERENCE.md accuracy restored; detekt clean; magic-pixel literals eliminated (completed 2026-06-12)
- [x] **Phase 18: Deprecation Removals and Sonar Burn-down** - `whenever`/`runIf` unified and deprecated `combatIsInState` overload removed; S3776 HIGH findings reduced to 0 (completed 2026-06-13)
- [x] **Phase 19: Codegen Fixes — Metasprite Cluster** - Visual-parity and structural latent metasprite bugs fixed with emission test coverage (completed 2026-06-13)
- [x] **Phase 20: Codegen Fixes — Banks and Sprite Transparency** - Banks trio (SEED-014/015/016) re-verified closed; tRNS sprite outline confirmed fixed — verification 5/5, both FIX-04 visual oracles human-signed-off; security verified (0 threats) (completed 2026-06-14)
- [x] **Phase 21: Codegen Fixes — Platformer and Remaining Seeds** - Platformer cEmit escapes replaced; all remaining seeds dispositioned; seeds directory empty — COMPLETE 2026-06-14 (pivotAdjust DSL lift, predicate consolidation, serializer stubs, 4 re-deferrals, GBC anchor re-shoot, D-13 byte-identity CLEAN, seeds/ empty)
- [ ] **Phase 22: Golden Screenshot and Evidence Storage Overhaul** - Store this milestone's visual+emission evidence correctly: replace the per-phase EVIDENCE_DIR pattern (27 test classes) with central ROM+anchor-keyed immutable goldens that tests DIFF against; gitignore per-phase evidence/ scratch; GBC auto-detect from ROM 0x143; migrate the 19/20/21 binding goldens

## Phase Details

### Phase 16: Seed Triage

**Goal**: Every seed in `.planning/seeds/` has a terminal, evidence-backed disposition against current master — establishing which bugs Phases 12–13.8 already fixed and which still require v0.1.1 fix work
**Depends on**: Nothing (first phase of milestone)
**Requirements**: TRIAGE-01, TRIAGE-02, TRIAGE-03
**Success Criteria** (what must be TRUE):

  1. A triage record exists for each of the 44 seeds with a written disposition: FIXED, VERIFIED-ALREADY-FIXED, or RE-DEFERRED
  2. Visual-symptom seeds each have a runtime screenshot at HEAD attached to their triage record (Visual Evidence Rule satisfied)
  3. A confirmed list of seeds still open on current master is published, clearly distinguishing those already closed by Phases 12–13.8
  4. No seed in `.planning/seeds/` is left without a reviewed, evidence-backed disposition**Plans**: 10 plans

**Wave 1**

- [x] 16-01-PLAN.md — Scaffold TRIAGE.md skeleton (47 rows), archive/backlog dirs, 6 fast-path RE-DEFERREDs (D-12)
- [x] 16-02-PLAN.md — Substrate pass: serial build of 7 ROMs + full JVM suite + plugin validation, pin SHA, rebuild MCP JAR

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 16-03-PLAN.md — Visual evidence capture (10 visual seeds, gbcMode) + batch review document draft
- [x] 16-04-PLAN.md — Metasprites emission triage (SEED-006..011, sprite-outline tRNS, stale-baseline todo)
- [x] 16-05-PLAN.md — Banks triage (SEED-014 INV-2 sentinel, 015, 016, retroactive/convert audits, 13.8 WR todo)
- [x] 16-06-PLAN.md — DSL/lang/tooling source triage (SEED-002/003/012/020/023/025/026 + triggersystem todo)
- [x] 16-07-PLAN.md — Platformer/zone source triage (SEED-017/021/022, zone-magic-string, cEmit gaps, RE-DEFERRED zone seeds)

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 16-08-PLAN.md — Batch visual review gate (binding human checkpoint, D-08) + verdict lock

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 16-09-PLAN.md — Finalize TRIAGE.md (merge clusters + locked verdicts) + D-02 seed stamps

**Wave 5** *(blocked on Wave 4 completion)*

- [x] 16-10-PLAN.md — Move seeds to archive/backlog + REQUIREMENTS.md/ROADMAP.md D-11 reconciliation

### Phase 17: Docs Reconciliation and Quality Cleanup

**Goal**: DSL_REFERENCE.md accurately describes the implemented DSL, the 2 pending doc-only fixes are applied, detekt passes across all modules, and magic-pixel literals are replaced with platform-aware constants
**Depends on**: Nothing (parallel-capable with Phase 16)
**Requirements**: DOCS-01, DOCS-02, DOCS-03, QUAL-01, QUAL-02, QUAL-03
**Success Criteria** (what must be TRUE):

  1. Each of the 13 stale-API sections in `context/DSL_REFERENCE.md` is updated: implemented APIs have accurate documentation; unimplemented sections are archived as tracked v0.2.0 candidate entries (no spec value lost)
  2. The 2 doc-only fixes are applied: deprecated-API example block corrected; `subpixel {}` no-op behavior clarified
  3. `./gradlew detekt` passes with zero violations across all modules including the `gbkt-gradle-plugin` composite build (no baseline files committed)
  4. All 160/144 magic-pixel literals are replaced by `ScreenSpec.WIDTH` / `ScreenSpec.HEIGHT` constants
  5. The in-scope set of remaining magic-pixel literals is fully enumerated and eliminated; intentional hardware constants are documented as exempt

**Plans**: 12 plans
**Wave 1**

- [x] 17-01-PLAN.md — DSL_REFERENCE accuracy audit evidence (13 sections, per-method, source-cited) + full-doc triage sweep
- [x] 17-02-PLAN.md — Screen-constant single source of truth: TargetProfiles.GAME_BOY_SCREEN preset + GameBoyConstants derivation
- [x] 17-03-PLAN.md — Re-enable 4 detekt rules (targeted) + commit violation inventory

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 17-04-PLAN.md — Archive 12 FEAT-*.md backlog seeds (verbatim + provenance) + expand REQUIREMENTS FEAT-XX
- [x] 17-05-PLAN.md — Replace 8 in-scope 160/144 literals + ROM byte-identity smoke + exemption table + D-06 seed

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 17-06-PLAN.md — Fix detekt violations to zero (real fixes or rationale-commented excludes; no baseline)
- [x] 17-08-PLAN.md — DOCS-03 fixes + rewrite sections 1-4 (State Machine, Dialog, Menu, Save) implemented-only

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 17-07-PLAN.md — Composite-build detekt bridge + delete baseline wiring + whole-repo detekt green
- [x] 17-09-PLAN.md — Rewrite sections 5-9 (Pools, Tweening, Camera, Camera Transitions, Physics) implemented-only

**Wave 5** *(blocked on Wave 4 completion)*

- [x] 17-10-PLAN.md — Rewrite sections 10-13 (Pathfinding, Testing, Battle, Inventory); zero caveats remain

**Wave 6** *(blocked on Wave 5 completion)*

- [x] 17-11-PLAN.md — Folded todos: ConfigBuilder setter unify + config{} docs + MBC5 warning + RpgRegistry.clear()

**Wave 7** *(blocked on Wave 6 completion)*

- [x] 17-12-PLAN.md — Cross-doc consistency pass (D-16) + backfill FEAT-* removal-commit provenance

**Cross-cutting constraints:**

- Every rewritten snippet is lifted/adapted from in-tree code that compiles today (D-14)

### Phase 18: Deprecation Removals and Sonar Burn-down

**Goal**: The `whenever`/`runIf` duplication and the deprecated `combatIsInState(String,String)` overload are removed with all in-tree usages migrated; the gbkt deprecation convention is documented; SonarCloud reports zero S3776 HIGH findings with strict byte-identity oracle discipline
**Depends on**: Nothing (parallel-capable with Phases 16 and 17)
**Requirements**: DEPR-01, DEPR-02, DEPR-03, SONAR-01, SONAR-02
**Success Criteria** (what must be TRUE):

  1. The `whenever`/`runIf` duplication is unified — the redundant API is removed and all in-tree call sites are migrated in the same change (exact semantics decided at discuss-phase per SEED-023)
  2. The `combatIsInState(String, String)` overload is removed and all in-tree call sites are migrated (SEED-025)
  3. CONTRIBUTING.md documents the gbkt deprecation train convention (`WARNING` → v+1 removal) for future API evolution
  4. SonarCloud S3776 HIGH finding count is 0, with at most 5 NOSONAR suppressions used across the entire milestone
  5. Every S3776 refactor commit touching `codegen/visitor/**` or `GBDKPipeline.kt` has a passing 7-example byte-identity ROM sweep as exit evidence; S3776 commits are never combined with seed-fix commits

**Phase 17 code-review carry-ins** (developer-decided 2026-06-13, fold into discuss-phase scope):

  - **SEED-027** (WR-01): align `GAME_BOY_COLOR_SCREEN.bitsPerPixel` 4→2 + narrow the "MUST derive" KDoc to width/height. Trivial constants/KDoc fix, byte-identical (zero consumers). See `.planning/seeds/SEED-027-gbc-screen-bitsperpixel-correctness.md`.
  - **SEED-028** (WR-04/05): accept the ConfigBuilder hard removal (no `@Deprecated` shim) + add a v0.1.1 migration note + fix the 4 stale `config { ramBanks = N }` guidance strings. See `.planning/seeds/SEED-028-configbuilder-removal-migration-guidance.md`.

**Plans**: 27 plans
Plans:
**Wave 1**

- [x] 18-01-PLAN.md — DEPR-01 code: remove whenever, relocate pool-collision runIf overload, migrate all call sites
- [x] 18-02-PLAN.md — DEPR-01 docs: migrate whenever→runIf in context/DSL_REFERENCE.md
- [x] 18-03-PLAN.md — DEPR-02: remove combatIsInState(String) overload + migrate sole test site
- [x] 18-04-PLAN.md — DEPR-03 CONTRIBUTING deprecation convention + new root CHANGELOG.md (D-09)
- [x] 18-05-PLAN.md — SEED-027: GBC bitsPerPixel 4→2 + KDoc narrowing (D-07)
- [x] 18-06-PLAN.md — SONAR-01 non-emitting: gbkt-analysis batch (N-02/N-08/N-13)
- [x] 18-07-PLAN.md — SONAR-01 non-emitting: analysis N-15 + emulator N-04/N-05
- [x] 18-08-PLAN.md — SONAR-01 non-emitting: emulator N-09/N-16 + mcp N-01 (NOSONAR candidate)
- [x] 18-09-PLAN.md — SONAR-01 non-emitting: mcp-server batch (N-07/N-10/N-12)
- [x] 18-10-PLAN.md — SONAR-01 non-emitting: test N-06 + core N-03 + lang GameBuilder N-11
- [x] 18-11-PLAN.md — SONAR-01 non-emitting: intellij-plugin batch (N-14/N-17)

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 18-12-PLAN.md — SEED-028: fix 4 stale ramBanks guidance strings (D-08)

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 18-13-PLAN.md — SONAR EMITTING GBDKPipeline: E-03/E-04 (per-commit ROM sweep)

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 18-14-PLAN.md — SONAR EMITTING GBDKPipeline: E-13/E-19 (walkOps promotion) + E-15

**Wave 5** *(blocked on Wave 4 completion)*

- [x] 18-15-PLAN.md — SONAR EMITTING GBDKPipeline: E-17/E-20/E-24

**Wave 6** *(blocked on Wave 5 completion)*

- [x] 18-16-PLAN.md — SONAR EMITTING GBDKPipeline: E-25/E-27 (file cleared)

**Wave 7** *(blocked on Wave 6 completion)*

- [x] 18-17-PLAN.md — SONAR EMITTING GBDKSystemVisitor: E-01 (cc92)

**Wave 8** *(blocked on Wave 7 completion)*

- [x] 18-18-PLAN.md — SONAR EMITTING GBDKSystemVisitor: E-05/E-08

**Wave 9** *(blocked on Wave 8 completion)*

- [x] 18-19-PLAN.md — SONAR EMITTING GBDKSystemVisitor: E-09/E-10

**Wave 10** *(blocked on Wave 9 completion)*

- [x] 18-20-PLAN.md — SONAR EMITTING GBDKSystemVisitor: E-11/E-14 (file cleared)

**Wave 11** *(blocked on Wave 10 completion)*

- [x] 18-21-PLAN.md — SONAR EMITTING MenuVisitor: E-02 (cc90)

**Wave 12** *(blocked on Wave 11 completion)*

- [x] 18-22-PLAN.md — SONAR EMITTING SceneVisitor E-07 + CombatVisitor E-16

**Wave 13** *(blocked on Wave 12 completion)*

- [x] 18-23-PLAN.md — SONAR EMITTING NOSONAR decisions: CEmitter E-12 + SoundVisitor E-06 (D-05)

**Wave 14** *(blocked on Wave 13 completion)*

- [x] 18-24-PLAN.md — SONAR EMITTING HudVisitor E-18 + ActorVisitor E-21

**Wave 15** *(blocked on Wave 14 completion)*

- [x] 18-25-PLAN.md — SONAR EMITTING Collection E-22 + SharedConstant E-23 + Dialog E-26

**Wave 16** *(blocked on Wave 15 completion)*

- [x] 18-26-PLAN.md — SONAR EMITTING TrackSynthesizer E-28 (sport) + RpgVisitor E-29

**Wave 17** *(blocked on Wave 16 completion)*

- [x] 18-27-PLAN.md — Phase-end consolidated sweep + full suite + SonarCloud S3776=0 gate

**Cross-cutting constraints:**

- 6/6 non-pong example ROMs are byte-identical to baseline after each commit; pong matches on generated C
- 6/6 non-pong example ROMs are byte-identical to baseline; pong matches on generated C

### Phase 19: Codegen Fixes — Metasprite Cluster

**Goal**: Metasprite codegen bugs confirmed open by Phase 16 triage are fixed — visual-parity issues closed with screenshot evidence, structural latent issues closed with emission test guards; FIX phases only address triage-confirmed-open seeds (several may already be VERIFIED-ALREADY-FIXED)
**Depends on**: Phase 16
**Requirements**: FIX-01, FIX-02
**D-11 Phase 16 triage note**: All SEED-004/005/006/013 (FIX-01) and SEED-007/008/009/010/011 (FIX-02) are VERIFIED-ALREADY-FIXED (see TRIAGE.md). Phase 19 scope = screenshot and emission-test evidence confirmation of already-fixed behavior, not new fixes.
**Success Criteria** (what must be TRUE):

  1. SEED-004/005/006/013 visual-parity issues are each confirmed closed: HEAD screenshot evidence for each (Phase 16 triage already verified — Phase 19 provides formal screenshot artifacts per FIX-01 requirement); seeds moved to seeds/archive/ by Plan 16-10
  2. SEED-007/008/009/010/011 structural latent issues are each confirmed closed: JVM emission tests guard already-fixed behavior (RED → GREEN cycle proves no regression); seeds moved to seeds/archive/ by Plan 16-10
  3. The metasprites example ROM builds successfully and renders correctly, confirmed by a runtime screenshot
  4. All metasprite fix commits are strictly separate from any S3776 commits so the byte-identity oracle can unambiguously attribute C output changes

**Plans**: 4 plans
Plans:
**Wave 1**

- [x] 19-01-PLAN.md — Byte-identity BEFORE baseline + clean dual buildRom smoke (FIX-01/FIX-02 / D-07)

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 19-02-PLAN.md — FIX-02 emission-guard audit (19-AUDIT-FIX-02.md) + 5 guards GREEN

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 19-03-PLAN.md — FIX-01 GBC-mode capture harness (Phase19VisualEvidenceTest) + SEED-004/005/006/013 + ROM-smoke screenshots

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 19-04-PLAN.md — Closure: byte-identity AFTER diff, seed-archive integrity, full suite GREEN, S3776 commit separation

### Phase 20: Codegen Fixes — Banks and Sprite Transparency

**Goal**: The banks trio seeds (SEED-014/015/016) are resolved after a mandatory discuss-phase gate; the tRNS sprite outline defect is fixed without regressing platformer player transparency; SEED-014 re-verified first since the `hasZoneSceneBinder` guard may already satisfy it on master
**Depends on**: Phase 19
**Requirements**: FIX-03, FIX-04
**D-11 Phase 16 triage note**: SEED-014 VERIFIED-ALREADY-FIXED (INV-2+INV-6 GREEN), SEED-015 VERIFIED-ALREADY-FIXED (INV-5 GREEN), SEED-016 VERIFIED-ALREADY-FIXED (Anchor 4 @Test confirmed); SEED-PHASE-13-SPRITE-OUTLINE VERIFIED-ALREADY-FIXED (Phase 13.6 tRNS auto-route). All 4 seeds moved to seeds/archive/ by Plan 16-10. Phase 20 scope = formal re-verification evidence + visual oracle D-08 confirmation; discuss-phase gate may be scoped down given triage findings.
**Success Criteria** (what must be TRUE):

  1. SEED-014 confirmed closed: BanksEmissionTest.kt INV-2 sentinel GREEN (already verified in Phase 16); formal Phase 20 evidence artifact; seed in seeds/archive/
  2. SEED-015 and SEED-016 confirmed closed: formal evidence artifacts per triage findings; discuss-phase gate reviews scope given VERIFIED-ALREADY-FIXED disposition (may confirm guard sufficiency rather than requiring new fix)
  3. The sprite-outline tRNS defect (SEED-PHASE-13-SPRITE-OUTLINE) confirmed closed — a runtime screenshot at HEAD confirms the outline renders without corruption (D-08 visual oracle for Phase 13.6 fix)
  4. Platformer player transparency is confirmed unchanged — a runtime screenshot at HEAD after the tRNS confirmation shows no regression
  5. A 7-example byte-identity ROM sweep passes after every commit in this phase

**Plans**: 4 plans
**Wave 1**

- [x] 20-01-PLAN.md — FIX-03 re-verify gate (D-02): run BanksEmissionTest INV-2/5/6 + BanksUatTest Anchor 4 fresh to GREEN at HEAD
- [x] 20-03-PLAN.md — FIX-04 visual oracle (D-04/D-05): two GBC-mode UAT screenshots (metasprites sprite-outline clean; platformer player-transparency no-regression)

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 20-02-PLAN.md — FIX-03 audit doc (D-01/D-03): produce 20-AUDIT-FIX-03.md mapping the banks trio to existing guards (zero new guards expected)

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 20-04-PLAN.md — Byte-identity oracle (D-06): per-commit affected-example diffs + full 7-example generated-C sweep at phase close

### Phase 21: Codegen Fixes — Platformer and Remaining Seeds

**Goal**: All platformer `cEmit()` escape hatches are replaced by proper `PlatformerVisitor.kt` auto-emission; all remaining open seeds from FIX-06 reach terminal disposition; `.planning/seeds/` is empty at phase close
**Depends on**: Phase 20
**Requirements**: FIX-05, FIX-06
**D-11 Phase 16 triage note (FIX-05)**: CONFIRMED-OPEN: SEED-021 (pivot_adjust hardcoded), SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY, SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK (LOCKED visual), SEED-platformer-template-spawn-polish (LOCKED visual). cEmit gaps VERIFIED-ALREADY-FIXED (Phase 13.5). FIX-05 active scope = pivot_adjust auto-derive + spawn position polish + sub-pixel fix + UAT re-shoot.
**D-11 Phase 16 triage note (FIX-06)**: VERIFIED-ALREADY-FIXED (→ archive): SEED-002, SEED-012, SEED-026. RE-DEFERRED (→ backlog/v0.2.0): SEED-003, one-way tile, shared-tileset, per-zone-banks. CONFIRMED-OPEN (active FIX-06 scope): SEED-017 (sport-zone dual pipeline), SEED-020 (serializer stubs), SEED-022 (collision predicate consolidation), SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION.
**Success Criteria** (what must be TRUE):

  1. All `cEmit()` escape hatches in the platformer template are replaced by corresponding `PlatformerVisitor.kt` auto-emission with no regression in platformer ROM behavior (cEmit gaps VERIFIED-ALREADY-FIXED by Phase 13.5; this criterion is pre-satisfied — Phase 21 adds UAT re-verification)
  2. Three platformer UAT anchor screenshots are re-shot in GBC mode (gbcMode=true) and all three pass assertion
  3. FIX-06 active seeds dispositioned: SEED-017 (sport-zone pipeline unification), SEED-020 (serializer round-trip stubs), SEED-022 (collision predicate consolidation), SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION fixed or explicitly re-deferred with evidence; SEED-021 (FIX-05/platformer scope) closes there
  4. FIX-05 platformer seeds resolved: SEED-021 pivot_adjust auto-derive, SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY + SEED-platformer-template-spawn-polish spawn polish, SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK sub-pixel fix
  5. `.planning/seeds/` is empty at phase close; all re-deferred items are already in v0.2.0 backlog (Plan 16-10 pre-moved: SEED-003, one-way tile, shared-tileset, per-zone-banks)

**Plans**: 8 plans

**Wave 1** *(parallel — no file overlap)*

- [x] 21-01-PLAN.md — D-05 pivotAdjust DSL lift (TilemapCollisionBuilder setter + visitor config-read) + snap-arithmetic emission test (D-07)
- [x] 21-03-PLAN.md — D-08 GameIRSerializer deserialize 10 stubbed collections + round-trip test (gbkt-ir)
- [x] 21-04-PLAN.md — D-10/D-11 SEED-027 + SEED-028 close VERIFIED-ALREADY-FIXED (structural grep + archive)
- [x] 21-05-PLAN.md — D-12 SEED-029 whenever→runIf doc/KDoc sweep (grep-first inventory) + archive
- [x] 21-06-PLAN.md — D-03/D-04 re-defer 4 seeds to backlog/v0.2.0 + REQUIREMENTS.md FIX-06 reconcile + evidence note

**Wave 2** *(blocked on 21-01 — shares PlatformerVisitor.kt)*

- [x] 21-02-PLAN.md — D-09 SEED-022 shared gameUsesTilemapCollisionPathC util (gbkt-backend-api) + delegate both callers + lockstep test

**Wave 3** *(blocked on 21-01 + 21-02 — needs final ROM)*

- [x] 21-07-PLAN.md — D-14/D-15 fix-first GBC 3-anchor re-shoot (EVIDENCE_DIR repoint) + binding visual sign-off (D-05/D-06/D-07) + archive 4 platformer seeds

**Wave 4** *(blocked on all — closeout)*

- [x] 21-08-PLAN.md — D-13 byte-identity unchanged-set sweep (pong PASS*) + D-01 seeds/ empty verification + FIX-05/FIX-06 Complete + ROADMAP finalize

**Cross-cutting constraints:**

- D-16 commit discipline — Phase 21 commits contain ONLY seed-closure work; zero PR-#77 / S3776 refactors interleaved; executors run `:module:spotlessApply :module:detekt` per-commit
- D-13 oracle — byte-identity only on the 5 UNTOUCHED examples; platformer-template (changed) proven by emission tests + UAT anchors; pong PASS*

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1–15 (66 incl. decimals) | v0.1.0 | 652/652 | Complete | 2026-06-09 |
| 16. Seed Triage | v0.1.1 | 10/10 | Complete    | 2026-06-12 |
| 17. Docs Reconciliation and Quality Cleanup | v0.1.1 | 12/12 | Complete   | 2026-06-12 |
| 18. Deprecation Removals and Sonar Burn-down | v0.1.1 | 30/27 | Complete   | 2026-06-13 |
| 19. Codegen Fixes — Metasprite Cluster | v0.1.1 | 4/4 | Complete    | 2026-06-13 |
| 20. Codegen Fixes — Banks and Sprite Transparency | v0.1.1 | 4/4 | Complete    | 2026-06-14 |
| 21. Codegen Fixes — Platformer and Remaining Seeds | v0.1.1 | 8/8 | Complete    | 2026-06-14 |
| 22. Golden Screenshot and Evidence Storage Overhaul | v0.1.1 | 6/14 | In Progress|  |

### Phase 22: Golden Screenshot and Evidence Storage Overhaul

**Goal**: This milestone's visual + emission evidence is stored as durable, immutable, ROM+anchor-keyed goldens that tests compare against — not scattered per-phase scratch. The `EVIDENCE_DIR`-pinned-to-a-phase pattern is eliminated across all UAT/emission tests, so test runs neither regenerate archived-phase directories nor churn committed sidecars, and GBC-vs-DMG capture mode is derived from the ROM rather than configured per-test.
**Requirements**: FIX-07 (golden evidence storage scheme)
**Depends on:** Phase 21

**Why now (not v0.2.0):** The visual + emission evidence IS the output of the v0.1.1 Hardening milestone (every codegen fix is *proven* by it). Today that proof is mishandled: 27 test classes hardcode `EVIDENCE_DIR = .planning/phases/<originating-phase>/evidence`, so archived v0.1.0 phases (07.9/09/10/11/12.x/13.5) are regenerated as untracked garbage on every test run, current phases (19/20/21) churn their committed `capturedAt` sidecars, and a GBC ROM captured in DMG mode reads as a false palette regression (the 21-07 inversion). Fixing the storage of this milestone's deliverable belongs in this milestone.

**Success Criteria:**

  1. No test writes evidence into a `.planning/phases/**/evidence` path; the per-phase `EVIDENCE_DIR` constant is removed from all 27 UAT/emission test classes and replaced with a central ROM+anchor-keyed location.
  2. Goldens are immutable + committed; UAT/emission tests capture to a gitignored scratch dir and DIFF against the committed golden, failing on mismatch. Re-baselining is an explicit, reviewed action (not a side effect of a normal test run).
  3. `.planning/phases/**/evidence/` is gitignored; a clean `./gradlew test` leaves zero new untracked files and zero modified committed evidence (no `capturedAt` churn; no archived-phase dir regeneration).
  4. `AgentSessionConfig.discoverFiles` auto-detects GBC mode from the ROM CGB-flag byte (0x143); the Phase 21 per-test `.copy(gbcMode = true)` workaround is removed; GBC-target example captures render with correct (non-inverted) palette.
  5. The genuine binding goldens from Phases 19/20/21 (metasprite, banks/tRNS, platformer GBC anchors) are migrated into the central goldens dir and remain the blessed baselines; the untracked archived-phase evidence garbage is removed.
  6. TESTING.md documents the goldens layout + the re-baseline command.

**Plans:** 6/14 plans executed

Plans:

- [x] 22-01-PLAN.md — assertGoldenMatch helper + GBKT_UPDATE_GOLDENS_PROP + drop capturedAt (gbkt-emulator) [Wave 1]
- [x] 22-02-PLAN.md — discoverFiles GBC auto-detect from ROM byte 0x143 + tests [Wave 1]
- [x] 22-03-PLAN.md — .gitignore evidence rule + -Pgbkt.updateGoldens wiring + goldens dir skeletons [Wave 1]
- [x] 22-09-PLAN.md — gbkt-backend-gbdk emission scratch redirect (9 classes) [Wave 1]
- [x] 22-10-PLAN.md — gbkt-genre-platformer emission scratch redirect (9 classes) [Wave 1]
- [x] 22-11-PLAN.md — example-module emission scratch redirect (7 classes) [Wave 1]
- [ ] 22-04-PLAN.md — byte-identity migration of 6 metasprites anchors (sha256-proven) [Wave 2]
- [ ] 22-05-PLAN.md — byte-identity migration of 16 platformer-template anchors (sha256-proven) [Wave 2]
- [ ] 22-06-PLAN.md — metasprites visual-UAT swaps to assertGoldenMatch + D-07 guard [Wave 3]
- [ ] 22-07-PLAN.md — platformer-template visual-UAT swaps (anchor4 compareRegion preserved) [Wave 3]
- [ ] 22-08-PLAN.md — simple-physics + banks visual-UAT scratch redirect (no golden) [Wave 3]
- [ ] 22-12-PLAN.md — git rm all tracked per-phase evidence (143 files) after goldens migrated [Wave 4]
- [ ] 22-14-PLAN.md — TESTING.md goldens layout + re-baseline command + GBC auto-detect [Wave 4]
- [ ] 22-13-PLAN.md — clean-tree acceptance test (R1/R5/R6 gates) + full-suite/buildRom sign-off [Wave 5]
