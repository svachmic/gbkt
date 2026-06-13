# Requirements: gbkt v0.1.1 Hardening

**Defined:** 2026-06-12
**Core Value:** The framework automatically manages Game Boy hardware resources (VRAM, banking, OAM, RAM) so the developer writes only declarative Kotlin DSL — like Jetpack Compose for Game Boy.

**Milestone goal:** Drain the v0.1.0 deferred-debt backlog — every seed gets a terminal disposition (fixed, verified-closed, or explicitly re-routed), the docs tell the truth, and static-analysis debt is burned down.

## v0.1.1 Requirements

Requirements for this milestone. Each maps to roadmap phases.

### Seed Triage & Closure (TRIAGE)

- [x] **TRIAGE-01**: Every seed in `.planning/seeds/` carries a terminal disposition — FIXED, VERIFIED-ALREADY-FIXED, or RE-DEFERRED with explicit v0.2.0 rationale — backed by evidence (commit hash, green test run, or screenshot at HEAD)
- [x] **TRIAGE-02**: Visual-symptom seeds are closed only with runtime screenshot evidence at HEAD (Visual Evidence Rule), never variable assertions alone
- [x] **TRIAGE-03**: `.planning/seeds/` is empty at milestone close; re-deferred seeds move to a tracked v0.2.0 backlog record

### Docs Reconciliation (DOCS)

- [x] **DOCS-01**: Each of the 13 stale-API sections in `context/DSL_REFERENCE.md` is audited per-method against source; implemented APIs keep accurate, corrected documentation
- [x] **DOCS-02**: Unimplemented/aspirational API content is removed from DSL_REFERENCE.md and archived as tracked v0.2.0 feature candidates (no spec value silently lost)
- [x] **DOCS-03**: The 2 doc-only fixes are applied (deprecated-API example block, `subpixel {}` no-op clarification)

### Deprecation Removals (DEPR)

- [x] **DEPR-01**: `whenever`/`runIf` duplication is unified — the redundant API is removed and all in-tree usages migrated (exact semantics decided at discuss-phase per SEED-023)
- [x] **DEPR-02**: The deprecated `combatIsInState` String overload is removed with all in-tree usages migrated (SEED-025)
- [x] **DEPR-03**: The gbkt deprecation/removal convention is documented in CONTRIBUTING.md

### Quality Cleanup (QUAL — continues v0.1.0 numbering)

- [x] **QUAL-01**: Detekt violations cleared via exclusion-removal from `detekt.yml` (no committed baseline files); detekt coverage extended to the `gbkt-gradle-plugin` composite build
- [x] **QUAL-02**: Magic 160/144 pixel literals replaced with platform-aware screen constants
- [x] **QUAL-03**: Remaining magic-pixel literals eliminated (in-scope set enumerated at phase spec; intentional hardware constants exempt)

### Static Analysis Burn-down (SONAR)

- [x] **SONAR-01**: SonarCloud S3776 cognitive-complexity HIGH findings reduced 46 → 0 via extract-method refactoring (no threshold changes, ≤5 NOSONAR suppressions milestone-wide)
- [x] **SONAR-02**: Every refactor commit touching `codegen/visitor/**` or `GBDKPipeline.kt` passes a byte-identity ROM sweep (7 examples, pong PASS*)

### Codegen Defect Fixes (FIX — triage-confirmed-open seeds only)

- [ ] **FIX-01**: Metasprite visual-parity cluster — closed by Phase 16 triage (see TRIAGE.md): SEED-004 VERIFIED-ALREADY-FIXED (user override; elephant renders correctly), SEED-005 VERIFIED-ALREADY-FIXED (checkerboard present, Phase 10.1 fix), SEED-006 VERIFIED-ALREADY-FIXED (sub-palette assignment confirmed in main.c), SEED-013 VERIFIED-ALREADY-FIXED (GBC colors correct, Phase 10.2 fix). All 4 seeds moved to seeds/archive/. Phase 19 FIX-01 scope = screenshot evidence confirmation only.
- [ ] **FIX-02**: Metasprite structural latents — closed by Phase 16 triage (see TRIAGE.md): SEED-007 VERIFIED-ALREADY-FIXED (actorPaletteAutoSlot counter present), SEED-008 VERIFIED-ALREADY-FIXED (monotonic VRAM allocator confirmed), SEED-009 VERIFIED-ALREADY-FIXED (metasprites.h include present in bank1.c), SEED-010 VERIFIED-ALREADY-FIXED (namespaced symbol arrays confirmed), SEED-011 VERIFIED-ALREADY-FIXED (hiwater=0 once per frame, Route A fix confirmed). All 5 seeds moved to seeds/archive/. Phase 19 FIX-02 scope = emission-test guards for confirmed-already-fixed behavior.
- [ ] **FIX-03**: Banks trio — closed by Phase 16 triage (see TRIAGE.md): SEED-014 VERIFIED-ALREADY-FIXED (INV-2+INV-6 GREEN; hasZoneSceneBinder guard sufficient), SEED-015 VERIFIED-ALREADY-FIXED (INV-5 GREEN; title_enter deduplicated correctly), SEED-016 VERIFIED-ALREADY-FIXED (Anchor 4 @Test present; ran in substrate). All 3 seeds moved to seeds/archive/. Phase 20 FIX-03 scope = re-verify + guard; discuss-phase gate may be scoped down.
- [ ] **FIX-04**: Sprite-outline tRNS — closed by Phase 16 triage (see TRIAGE.md): SEED-PHASE-13-SPRITE-OUTLINE VERIFIED-ALREADY-FIXED (Phase 13.6 tRNS auto-route confirmed; visual closure oracle deferred to Phase 20 D-08). Seed moved to seeds/archive/. Phase 20 FIX-04 scope = visual oracle confirmation only.
- [ ] **FIX-05**: Platformer seeds — Phase 16 triage disposition: SEED-021 CONFIRMED-OPEN (pivot_adjust hardcoded, Phase 21), SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY CONFIRMED-OPEN (Phase 21), SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK CONFIRMED-OPEN (Phase 21, LOCKED visual review), SEED-platformer-template-spawn-polish CONFIRMED-OPEN (Phase 21, LOCKED visual review). All 4 remain in seeds/. UAT anchors re-shot scope unchanged.
- [ ] **FIX-06**: Small DSL/tooling seeds — Phase 16 triage disposition: SEED-002 VERIFIED-ALREADY-FIXED (→ archive), SEED-003 RE-DEFERRED (→ backlog/v0.2.0), SEED-012 VERIFIED-ALREADY-FIXED (→ archive), SEED-017 CONFIRMED-OPEN (sport-zone dual pipeline, stays in seeds/), SEED-020 CONFIRMED-OPEN (serializer stubs, stays in seeds/), SEED-021 CONFIRMED-OPEN (pivot_adjust, routed to FIX-05/Phase 21), SEED-022 CONFIRMED-OPEN (collision predicate, stays in seeds/), SEED-026 VERIFIED-ALREADY-FIXED (→ archive), zone magic-string CONFIRMED-OPEN (stays in seeds/), one-way tile RE-DEFERRED (→ backlog/v0.2.0), shared-tileset RE-DEFERRED (→ backlog/v0.2.0), per-zone-banks RE-DEFERRED (→ backlog/v0.2.0). Phase 21 FIX-06 active scope: SEED-017/020/022 + ZONE-MAGIC-STRING.

## Future Requirements (v0.2.0+)

Deferred to future release. Tracked but not in current roadmap.

### Feature Implementation (from pruned docs)

- **FEAT-STATE-MACHINES**: Implement top-level `states("player") { ... }` state machine DSL with enter/tick/on-transition blocks — see `.planning/backlog/v0.2.0/FEAT-STATE-MACHINES.md`
- **FEAT-DIALOG-TICK-API**: Implement `DialogHandle.tick()`, `isActive`, `isComplete`, `show()`, `hide()` for frame-driven dialog state — see `.planning/backlog/v0.2.0/FEAT-DIALOG-TICK-API.md`
- **FEAT-MENU-GRID-STYLE**: Implement `style { }` block on `MenuBuilder`, `gridMenu()`, `MenuHandle.tick()`, `isActive`, `isVisible`, `selectedIndex` — see `.planning/backlog/v0.2.0/FEAT-MENU-GRID-STYLE.md`
- **FEAT-SAVE-DATA-FIELDS**: Implement field-level save API (`u16Field()`, `flagsField()`, `save.load()`, `save.save()`, `save.exists()`, per-field delegates) — see `.planning/backlog/v0.2.0/FEAT-SAVE-DATA-FIELDS.md`
- **FEAT-ENTITY-POOL-LIFECYCLE**: Implement sprite/lifecycle entity pools with `onSpawn`, `onFrame`, `despawnWhen`, `spawn { }`, `forEachActive { }`, `despawnAll()` — see `.planning/backlog/v0.2.0/FEAT-ENTITY-POOL-LIFECYCLE.md`
- **FEAT-TWEENING**: Implement `tween(target, from, to, duration, easing)` and `Easing` enum with pre-computed lookup tables — see `.planning/backlog/v0.2.0/FEAT-TWEENING.md`
- **FEAT-CAMERA-EXTRAS**: Implement camera `offset()`, `deadzone()`, `followX/followY`, snap-to, shake-builder with decay, `camera.x/y` conditions, wipe/iris/flash transitions — see `.planning/backlog/v0.2.0/FEAT-CAMERA-EXTRAS.md`
- **FEAT-PHYSICS-WORLD**: Implement global `physics { }` world builder, `tag()`, `physicsWorld.collide()`, `gravityZone()`, `maxVelocity`, `mass`, `useLocalFriction` — see `.planning/backlog/v0.2.0/FEAT-PHYSICS-WORLD.md`
- **FEAT-PATHFINDING-NAVGRID**: Implement `navGrid()` builder, `player findPathTo target using navGrid` infix, weighted tiles, `Heuristic` enum, dynamic obstacles — see `.planning/backlog/v0.2.0/FEAT-PATHFINDING-NAVGRID.md`
- **FEAT-TESTING-DSL**: Implement `testGame { }` / `testScene { }` DSL wrappers, `press(Button) { }` input simulation, fluent assertions (`expect("x").toEqual(n)`) — see `.planning/backlog/v0.2.0/FEAT-TESTING-DSL.md`
- **FEAT-BATTLE-MENUS**: Implement `battleMenu { }` builder, `combatFormulas { }`, custom `battleState()` delegates, `battleTransition()` script op — see `.planning/backlog/v0.2.0/FEAT-BATTLE-MENUS.md`
- **FEAT-INVENTORY-DELEGATE**: Implement game-scope `val potion by item { }` delegate, `ItemCategory` global enum, `equipSlot()`, `ContainerRef.add/remove/equip/contains/count` — see `.planning/backlog/v0.2.0/FEAT-INVENTORY-DELEGATE.md`

### Architecture / Tooling

- **ARCH-01**: SEED-RAW-C-CODEGEN-AST-MIGRATION — migrate remaining raw-C emission to typed C AST (own architecture phase) — see `.planning/backlog/v0.2.0/SEED-RAW-C-CODEGEN-AST-MIGRATION.md`
- **ARCH-02**: SEED-PHASE-X-CPAREN — precedence-aware paren emission (~50+ fixture re-snapshots) — see `.planning/backlog/v0.2.0/SEED-PHASE-X-CPAREN-EXPR-IN-C-AST.md`
- **IDE-01**: SEED-019/024 — IntelliJ plugin test framework coverage + BuildLogPanel export dialog — see `.planning/backlog/v0.2.0/SEED-019-intellij-plugin-test-framework-coverage.md`, `.planning/backlog/v0.2.0/SEED-024-buildlog-export-save-dialog.md`
- **IDE-02**: SEED-001 — IDE feature enhancements (v2.0 trigger) — see `.planning/backlog/v0.2.0/SEED-001-ide-and-tooling.md`
- **RPG-01**: SEED-018 — RPG character codegen extern/decl mismatch (dormant with archived dungeon/explorer games) — see `.planning/backlog/v0.2.0/SEED-018-rpg-character-codegen-extern-decl-mismatch.md`

### Examples Polish (from triage re-deferred)

- **EXAMPLES-01**: SEED-003 — simple-physics playability polish (reference-faithful behavior; trigger is "playable demos" milestone) — see `.planning/backlog/v0.2.0/SEED-003-simple-physics-playability-polish.md`

### Platformer Extensions (from triage re-deferred)

- **PLAT-EXT-01**: SEED-PHASE-12-ONE-WAY-TILE — one-way tile collision not implemented (revival requires triggering platformer port) — see `.planning/backlog/v0.2.0/SEED-PHASE-12-ONE-WAY-TILE.md`
- **PLAT-EXT-02**: SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS — per-zone tilemap separate banks (trigger: bank overflow guard trips; 8216 bytes headroom remains) — see `.planning/backlog/v0.2.0/SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS.md`
- **PLAT-EXT-03**: SEED-PHASE-12-SHARED-TILESET — shared tileset deduplication (trigger: ROM size pressure; 64KB within 2x threshold) — see `.planning/backlog/v0.2.0/SEED-PHASE-12-SHARED-TILESET.md`

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Implementing the 13 absent DSL subsystems | v0.1.1 makes docs match reality; implementation is v0.2.0+ feature work |
| SEED-RAW-C-CODEGEN-AST-MIGRATION, SEED-PHASE-X-CPAREN | Wide blast radius; explicitly routed to own future phases |
| SEED-019/024, SEED-001 (IntelliJ/IDE work) | Test-infra lift disproportionate to a hardening patch |
| SEED-018 RPG codegen fix | Affected games archived in Phase 11.3; stays dormant |
| Genre-codegen phases 07.5–07.8, IDE-04 | Wait for their own milestone |
| Multiplatform backends, live preview, new game ports, community docs, link cable | Carried v0.1.0 exclusions, unchanged |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| TRIAGE-01 | Phase 16 | Complete — 47 rows finalized (24 VERIFIED-ALREADY-FIXED, 12 CONFIRMED-OPEN, 10 RE-DEFERRED, 1 TODO-VERIFIED) |
| TRIAGE-02 | Phase 16 | Complete — 10 visual seeds closed with runtime screenshots; D-08 visual review gate PASSED 2026-06-12 |
| TRIAGE-03 | Phase 16 | Complete — seeds/ holds 10 CONFIRMED-OPEN seeds; 10 RE-DEFERRED moved to backlog/v0.2.0/ (Plan 16-10) |
| DOCS-01 | Phase 17 | Complete |
| DOCS-02 | Phase 17 | Complete |
| DOCS-03 | Phase 17 | Complete |
| DEPR-01 | Phase 18 | Complete |
| DEPR-02 | Phase 18 | Complete |
| DEPR-03 | Phase 18 | Complete |
| QUAL-01 | Phase 17 | Complete |
| QUAL-02 | Phase 17 | Complete |
| QUAL-03 | Phase 17 | Complete |
| SONAR-01 | Phase 18 | Complete |
| SONAR-02 | Phase 18 | Complete |
| FIX-01 | Phase 19 | Pending |
| FIX-02 | Phase 19 | Pending |
| FIX-03 | Phase 20 | Pending |
| FIX-04 | Phase 20 | Pending |
| FIX-05 | Phase 21 | Pending |
| FIX-06 | Phase 21 | Pending |

**Coverage:**

- v0.1.1 requirements: 20 total
- Mapped to phases: 20/20 ✓
- Unmapped: 0

---
*Requirements defined: 2026-06-12*
*Last updated: 2026-06-12 after roadmap creation (Phases 16–21)*
