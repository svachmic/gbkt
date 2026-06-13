# Roadmap: gbkt Compiler Pipeline Rebuild

## Milestones

- ✅ **v0.1.0 MVP — Compiler Pipeline Rebuild** — Phases 1–15 (shipped 2026-06-09)
- 🚧 **v0.1.1 Hardening** — Phases 16–21 (in progress)

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

- [ ] **Phase 16: Seed Triage** - Establish terminal dispositions for all 44 seeds against current master; gate for all codegen fix phases
- [ ] **Phase 17: Docs Reconciliation and Quality Cleanup** - DSL_REFERENCE.md accuracy restored; detekt clean; magic-pixel literals eliminated
- [ ] **Phase 18: Deprecation Removals and Sonar Burn-down** - `whenever`/`runIf` unified and deprecated `combatIsInState` overload removed; S3776 HIGH findings reduced to 0
- [ ] **Phase 19: Codegen Fixes — Metasprite Cluster** - Visual-parity and structural latent metasprite bugs fixed with emission test coverage
- [ ] **Phase 20: Codegen Fixes — Banks and Sprite Transparency** - Banks trio resolved (discuss-phase gated); tRNS sprite outline fixed
- [ ] **Phase 21: Codegen Fixes — Platformer and Remaining Seeds** - Platformer cEmit escapes replaced; all remaining seeds dispositioned; seeds directory empty

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

- [ ] 16-03-PLAN.md — Visual evidence capture (10 visual seeds, gbcMode) + batch review document draft
- [ ] 16-04-PLAN.md — Metasprites emission triage (SEED-006..011, sprite-outline tRNS, stale-baseline todo)
- [ ] 16-05-PLAN.md — Banks triage (SEED-014 INV-2 sentinel, 015, 016, retroactive/convert audits, 13.8 WR todo)
- [ ] 16-06-PLAN.md — DSL/lang/tooling source triage (SEED-002/003/012/020/023/025/026 + triggersystem todo)
- [ ] 16-07-PLAN.md — Platformer/zone source triage (SEED-017/021/022, zone-magic-string, cEmit gaps, RE-DEFERRED zone seeds)

**Wave 3** *(blocked on Wave 2 completion)*

- [ ] 16-08-PLAN.md — Batch visual review gate (binding human checkpoint, D-08) + verdict lock

**Wave 4** *(blocked on Wave 3 completion)*

- [ ] 16-09-PLAN.md — Finalize TRIAGE.md (merge clusters + locked verdicts) + D-02 seed stamps

**Wave 5** *(blocked on Wave 4 completion)*

- [ ] 16-10-PLAN.md — Move seeds to archive/backlog + REQUIREMENTS.md/ROADMAP.md D-11 reconciliation

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

**Plans**: TBD

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

**Plans**: TBD

### Phase 19: Codegen Fixes — Metasprite Cluster

**Goal**: Metasprite codegen bugs confirmed open by Phase 16 triage are fixed — visual-parity issues closed with screenshot evidence, structural latent issues closed with emission test guards; FIX phases only address triage-confirmed-open seeds (several may already be VERIFIED-ALREADY-FIXED)
**Depends on**: Phase 16
**Requirements**: FIX-01, FIX-02
**Success Criteria** (what must be TRUE):

  1. SEED-004/005/006/013 visual-parity issues are each closed: fixed seeds have a runtime screenshot at HEAD showing correct rendering; seeds verified-already-fixed have HEAD screenshot evidence confirming closure
  2. SEED-007/008/009/010/011 structural latent issues are each closed: confirmed-open seeds are fixed and guarded by a JVM emission test (RED → GREEN cycle)
  3. The metasprites example ROM builds successfully and renders correctly, confirmed by a runtime screenshot
  4. All metasprite fix commits are strictly separate from any S3776 commits so the byte-identity oracle can unambiguously attribute C output changes

**Plans**: TBD

### Phase 20: Codegen Fixes — Banks and Sprite Transparency

**Goal**: The banks trio seeds (SEED-014/015/016) are resolved after a mandatory discuss-phase gate; the tRNS sprite outline defect is fixed without regressing platformer player transparency; SEED-014 re-verified first since the `hasZoneSceneBinder` guard may already satisfy it on master
**Depends on**: Phase 19
**Requirements**: FIX-03, FIX-04
**Success Criteria** (what must be TRUE):

  1. SEED-014 is re-verified against current master with the `BanksEmissionTest.kt` INV-2 sentinel — closed VERIFIED-ALREADY-FIXED if the `hasZoneSceneBinder` guard satisfies it; otherwise fixed with a targeted change
  2. SEED-015 and SEED-016 are resolved per the discuss-phase specification, or explicitly re-deferred to v0.2.0 with documented rationale (discuss-phase gate required before any code changes due to blast radius)
  3. The sprite-outline tRNS defect (SEED-PHASE-13-SPRITE-OUTLINE) is fixed — a runtime screenshot at HEAD confirms the outline renders without corruption
  4. Platformer player transparency is confirmed unchanged — a runtime screenshot at HEAD after the tRNS fix shows no regression
  5. A 7-example byte-identity ROM sweep passes after every commit in this phase

**Plans**: TBD

### Phase 21: Codegen Fixes — Platformer and Remaining Seeds

**Goal**: All platformer `cEmit()` escape hatches are replaced by proper `PlatformerVisitor.kt` auto-emission; all remaining open seeds from FIX-06 reach terminal disposition; `.planning/seeds/` is empty at phase close
**Depends on**: Phase 20
**Requirements**: FIX-05, FIX-06
**Success Criteria** (what must be TRUE):

  1. All `cEmit()` escape hatches in the platformer template are replaced by corresponding `PlatformerVisitor.kt` auto-emission with no regression in platformer ROM behavior
  2. Three platformer UAT anchor screenshots are re-shot in GBC mode (gbcMode=true) and all three pass assertion
  3. Every seed from FIX-06 (SEED-002, 003, 012, 017, 020, 021, 022, 026, zone magic-string migration, one-way tile / shared-tileset / per-zone-banks) is dispositioned with evidence — fixed or explicitly re-deferred to v0.2.0
  4. `.planning/seeds/` is empty at phase close; all re-deferred items are recorded in the v0.2.0 backlog record

**Plans**: TBD

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1–15 (66 incl. decimals) | v0.1.0 | 652/652 | Complete | 2026-06-09 |
| 16. Seed Triage | v0.1.1 | 2/10 | In Progress|  |
| 17. Docs Reconciliation and Quality Cleanup | v0.1.1 | 0/TBD | Not started | - |
| 18. Deprecation Removals and Sonar Burn-down | v0.1.1 | 0/TBD | Not started | - |
| 19. Codegen Fixes — Metasprite Cluster | v0.1.1 | 0/TBD | Not started | - |
| 20. Codegen Fixes — Banks and Sprite Transparency | v0.1.1 | 0/TBD | Not started | - |
| 21. Codegen Fixes — Platformer and Remaining Seeds | v0.1.1 | 0/TBD | Not started | - |
