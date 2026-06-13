# Requirements: gbkt v0.1.1 Hardening

**Defined:** 2026-06-12
**Core Value:** The framework automatically manages Game Boy hardware resources (VRAM, banking, OAM, RAM) so the developer writes only declarative Kotlin DSL — like Jetpack Compose for Game Boy.

**Milestone goal:** Drain the v0.1.0 deferred-debt backlog — every seed gets a terminal disposition (fixed, verified-closed, or explicitly re-routed), the docs tell the truth, and static-analysis debt is burned down.

## v0.1.1 Requirements

Requirements for this milestone. Each maps to roadmap phases.

### Seed Triage & Closure (TRIAGE)

- [ ] **TRIAGE-01**: Every seed in `.planning/seeds/` carries a terminal disposition — FIXED, VERIFIED-ALREADY-FIXED, or RE-DEFERRED with explicit v0.2.0 rationale — backed by evidence (commit hash, green test run, or screenshot at HEAD)
- [ ] **TRIAGE-02**: Visual-symptom seeds are closed only with runtime screenshot evidence at HEAD (Visual Evidence Rule), never variable assertions alone
- [ ] **TRIAGE-03**: `.planning/seeds/` is empty at milestone close; re-deferred seeds move to a tracked v0.2.0 backlog record

### Docs Reconciliation (DOCS)

- [ ] **DOCS-01**: Each of the 13 stale-API sections in `context/DSL_REFERENCE.md` is audited per-method against source; implemented APIs keep accurate, corrected documentation
- [ ] **DOCS-02**: Unimplemented/aspirational API content is removed from DSL_REFERENCE.md and archived as tracked v0.2.0 feature candidates (no spec value silently lost)
- [ ] **DOCS-03**: The 2 doc-only fixes are applied (deprecated-API example block, `subpixel {}` no-op clarification)

### Deprecation Removals (DEPR)

- [ ] **DEPR-01**: `whenever`/`runIf` duplication is unified — the redundant API is removed and all in-tree usages migrated (exact semantics decided at discuss-phase per SEED-023)
- [ ] **DEPR-02**: The deprecated `combatIsInState` String overload is removed with all in-tree usages migrated (SEED-025)
- [ ] **DEPR-03**: The gbkt deprecation/removal convention is documented in CONTRIBUTING.md

### Quality Cleanup (QUAL — continues v0.1.0 numbering)

- [ ] **QUAL-01**: Detekt violations cleared via exclusion-removal from `detekt.yml` (no committed baseline files); detekt coverage extended to the `gbkt-gradle-plugin` composite build
- [ ] **QUAL-02**: Magic 160/144 pixel literals replaced with platform-aware screen constants
- [ ] **QUAL-03**: Remaining magic-pixel literals eliminated (in-scope set enumerated at phase spec; intentional hardware constants exempt)

### Static Analysis Burn-down (SONAR)

- [ ] **SONAR-01**: SonarCloud S3776 cognitive-complexity HIGH findings reduced 46 → 0 via extract-method refactoring (no threshold changes, ≤5 NOSONAR suppressions milestone-wide)
- [ ] **SONAR-02**: Every refactor commit touching `codegen/visitor/**` or `GBDKPipeline.kt` passes a byte-identity ROM sweep (7 examples, pong PASS*)

### Codegen Defect Fixes (FIX — triage-confirmed-open seeds only)

- [ ] **FIX-01**: Metasprite visual-parity cluster (SEED-004/005/006/013) fixed or verified-already-fixed with screenshot evidence
- [ ] **FIX-02**: Metasprite structural latents (SEED-007/008/009/010/011) fixed with emission-test guards
- [ ] **FIX-03**: Banks trio (SEED-014/015/016) resolved — discuss-phase gated due to blast radius; SEED-014 first re-verified against master (`hasZoneSceneBinder` guard may already satisfy it)
- [ ] **FIX-04**: Sprite-outline tRNS≠index-0 defect (SEED-PHASE-13-SPRITE-OUTLINE) fixed without regressing platformer player transparency
- [ ] **FIX-05**: Platformer seeds resolved — `cEmit()` escapes replaced by PlatformerVisitor auto-emission, spawn-position polish, sub-pixel sink (UAT anchors re-shot in GBC mode)
- [ ] **FIX-06**: Small DSL/tooling seeds dispositioned: SEED-002 (moveTo Expr overload), 003 (bounds clamp), 012 (MCP read_memory), 017 (sport-zone pipeline unification), 020 (serializer round-trip), 021 (pivot_adjust), 022 (collision predicate consolidation), 026 (build hygiene), zone magic-string migration, one-way tile / shared-tileset / per-zone-banks (fix or explicit re-defer)

## Future Requirements (v0.2.0+)

Deferred to future release. Tracked but not in current roadmap.

### Feature Implementation (from pruned docs)

- **FEAT-XX**: Implement the 13 documented-but-absent DSL subsystems (state machines, dialog/menu property APIs, save fields, entity-pool lifecycle, tweening, camera extras, physics property API, pathfinding, battle menus, items) — each tracked as a v0.2.0 candidate by DOCS-02

### Architecture / Tooling

- **ARCH-01**: SEED-RAW-C-CODEGEN-AST-MIGRATION — migrate remaining raw-C emission to typed C AST (own architecture phase)
- **ARCH-02**: SEED-PHASE-X-CPAREN — precedence-aware paren emission (~50+ fixture re-snapshots)
- **IDE-01**: SEED-019/024 — IntelliJ plugin test framework coverage + BuildLogPanel export dialog
- **IDE-02**: SEED-001 — IDE feature enhancements (v2.0 trigger)
- **RPG-01**: SEED-018 — RPG character codegen extern/decl mismatch (dormant with archived dungeon/explorer games)

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
| TRIAGE-01 | — | Pending |
| TRIAGE-02 | — | Pending |
| TRIAGE-03 | — | Pending |
| DOCS-01 | — | Pending |
| DOCS-02 | — | Pending |
| DOCS-03 | — | Pending |
| DEPR-01 | — | Pending |
| DEPR-02 | — | Pending |
| DEPR-03 | — | Pending |
| QUAL-01 | — | Pending |
| QUAL-02 | — | Pending |
| QUAL-03 | — | Pending |
| SONAR-01 | — | Pending |
| SONAR-02 | — | Pending |
| FIX-01 | — | Pending |
| FIX-02 | — | Pending |
| FIX-03 | — | Pending |
| FIX-04 | — | Pending |
| FIX-05 | — | Pending |
| FIX-06 | — | Pending |

**Coverage:**
- v0.1.1 requirements: 20 total
- Mapped to phases: 0 (roadmap pending)
- Unmapped: 20 ⚠️ (filled by roadmap creation)

---
*Requirements defined: 2026-06-12*
*Last updated: 2026-06-12 after initial definition*
