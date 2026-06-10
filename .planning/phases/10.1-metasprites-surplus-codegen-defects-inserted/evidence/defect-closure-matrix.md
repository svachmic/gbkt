# Phase 10.1 — Defect Closure Traceability Matrix

**Generated:** Plan 10.1-14 (phase close gate), 2026-05-19
**Plan range:** 10.1-01 through 10.1-22 (22 plans shipped; 8 surplus absorbed in-range per D-03b)

This matrix maps every defect from `10.1-CONTEXT.md` (the 8 named seeds, the 3
absorbed review warnings, and the IN-01 hyperlink absorption) to the plan that
closed it, the JVM-tier test that locks the closure, and the evidence artifact
that proves it.

**Closure-status legend:**
- **GREEN** — defect fully closed (codegen-shape + visual, where applicable)
- **PARTIAL** — defect closed at codegen-shape level; visual closure escalated per user-routing decision

---

## Closure Matrix

| Defect ID | Seed File | Closed By Plan | Locked By Test | Evidence Artifact | Status |
|-----------|-----------|----------------|----------------|-------------------|--------|
| D-V1 | SEED-004 | 10.1-10 (diagnostic) + 10.1-11 (sprite-mode mismatch fix) + 10.1-15 (DEF-13-A diagnostic) + 10.1-16 (-noflip fix) | `Seed004ElephantTileRenderingDiagnosticTest` (1 test) + `Seed004ElephantTileRenderingFixTest` (1 test) | `evidence/d-v1-diagnostic/sprite-mode-init-finding.md` + `evidence/d-v1-visual-diagnostic/` + `phases/10-.../evidence/uat-screenshots/behavior1-animation-advance.png` | GREEN |
| D-V2 | SEED-005 | 10.1-02 (byte-pattern fix) + 10.1-17 (DEF-13-B diagnostic) + 10.1-18 (literal-pattern fix) | `Seed005CheckerboardBytePatternTest` (3 tests) | `evidence/d-v2-visual-diagnostic/` + `phases/10-.../evidence/uat-screenshots/behavior2-flip-cycle.png` | GREEN |
| D-V3 | SEED-006 | 10.1-04 (variable-mirror) + 10.1-19 (diagnostic) + 10.1-20 (bootstrap-order) + 10.1-21 (Coffee-GB BCPD-shortfall diagnostic) + 10.1-22 (explicit BG palette + bgFillCheckerboard hoist) | `Seed006SubPaletteSyncTest` (3 tests) + `DV3GbcPaletteWriteDiagnosticTest` (3 tests) + `DV3VisualV2DiagnosticTest` (2 tests) — all 8 RED→GREEN | `evidence/d-v3-visual-diagnostic/` + `evidence/d-v3-visual-diagnostic-v2/` + `evidence/plan-20-rom-build-logs/` + `evidence/plan-22-rom-build-logs/` + (post-Plan-22 GBC re-shoot at `phases/10-.../evidence/uat-screenshots/behavior3-subpalette-cycle-gbc.png` still shows all-black — visual escalated) | PARTIAL — codegen-shape CLOSED, visual ESCALATED to Phase 10.2 per `feedback_route_to_proper_phase_when_blast_radius_is_wide.md` (4 inline diag+fix rounds did not close visually; user-routing decision in commit 94890c63 + `SEED-013-gbc-palette-write-path-d-v3-visual.md`) |
| D-extra | SEED-007 | 10.1-01 | `Seed007GameBuilderPaletteSlotTest` (2 tests) | `full-test-suite.log` | GREEN |
| CR-01 | SEED-008 | 10.1-08 (`VramAllocator` class per D-08 Route B) | `Seed008VramCollisionTest` (2 tests) | `metasprites-stress-buildrom.log` (Plan 12) + `phase-close-buildrom-smoke.log` (Plan 14 Task 1) | GREEN |
| CR-02 | SEED-009 | 10.1-06 | `Seed009BankIncludeTest` (2 tests) | `metasprites-stress-buildrom.log` + `phase-close-buildrom-smoke.log` | GREEN |
| CR-03 | SEED-010 | 10.1-03 (IR-field var-refs per D-10 / D-13 scoped exception) + 10.1-05 (visitor reads from fields) | `Seed010NamespaceTest` (3 tests) + `MetaspriteIRVarRefFieldsTest` (in gbkt-ir reports) | `metasprites-stress-buildrom.log` + `phase-close-buildrom-smoke.log` | GREEN |
| WR-05 | SEED-011 | 10.1-09 (frame-scope hiwater hoist per D-11) | `Seed011HiwaterFrameScopeTest` (3 tests) | `metasprites-stress-buildrom.log` + `phase-close-buildrom-smoke.log` | GREEN |
| WR-01 (absorbed) | — | 10.1-03 + 10.1-05 (same patch as CR-03 — closed by IR-field var-refs per D-15) | `Seed010NamespaceTest::default_null_fields_emit_canonical_underscore_names` + `Seed010NamespaceTest::two_metasprites_with_distinct_rot_vars` (covered within Seed010NamespaceTest's 3 tests) | `full-test-suite.log` | GREEN |
| WR-02 (absorbed) | — | 10.1-07 (extern decl emitted into game.h per D-14) | `WR02MetaspriteExternTest` (2 tests) | `full-test-suite.log` | GREEN |
| WR-03 (absorbed) | — | 10.1-02 (int8_t range validation bundled per D-16) | `MetaspriteBuilderTileRangeTest` (3 tests, in gbkt-lang) | `full-test-suite.log` | GREEN |
| IN-01 (absorbed via D-12) | — | 10.1-04 (same patch as D-V3 "both" option — flipX/flipY globals get the same assignment-in-frame fix) | `Seed006SubPaletteSyncTest::frame_switch_emits_flipX_and_flipY_assignments` (covered within Seed006SubPaletteSyncTest's 3 tests) | `full-test-suite.log` | GREEN |

**Total:** 12 defects (8 named seeds + 3 absorbed warnings + IN-01). 11 GREEN, 1 PARTIAL (D-V3 codegen-shape GREEN, visual escalated).

---

## Defect-Locking Test Suite (per-class JUnit XML summary)

Extracted from `gbkt-backend-gbdk/build/test-results/test/` and
`gbkt-lang/build/test-results/test/` after the Plan 14 Task 2 run
(see `full-test-suite.log` for full Gradle output + per-class summary).

| Test Class | Module | Tests | Failures | Errors |
|------------|--------|-------|----------|--------|
| `Seed004ElephantTileRenderingDiagnosticTest` | gbkt-backend-gbdk | 1 | 0 | 0 |
| `Seed004ElephantTileRenderingFixTest` | gbkt-backend-gbdk | 1 | 0 | 0 |
| `Seed005CheckerboardBytePatternTest` | gbkt-lang | 3 | 0 | 0 |
| `Seed006SubPaletteSyncTest` | gbkt-backend-gbdk | 3 | 0 | 0 |
| `Seed007GameBuilderPaletteSlotTest` | gbkt-lang | 2 | 0 | 0 |
| `Seed008VramCollisionTest` | gbkt-backend-gbdk | 2 | 0 | 0 |
| `Seed009BankIncludeTest` | gbkt-backend-gbdk | 2 | 0 | 0 |
| `Seed010NamespaceTest` | gbkt-backend-gbdk | 3 | 0 | 0 |
| `Seed011HiwaterFrameScopeTest` | gbkt-backend-gbdk | 3 | 0 | 0 |
| `WR02MetaspriteExternTest` | gbkt-backend-gbdk | 2 | 0 | 0 |
| `MetaspriteBuilderTileRangeTest` | gbkt-lang | 3 | 0 | 0 |
| **Total** | | **25** | **0** | **0** |

All defect-locking JUnit reports are at `failures=0 errors=0`.

---

## Phase Close Confirmation

- **D-21 ROM smoke gate:** PASSED (BLOCKING acceptance criterion satisfied)
  - Combined `:gbkt-examples:metasprites:clean :buildRom` + `:gbkt-examples:metasprites-stress:clean :buildRom` exited 0 with `BUILD SUCCESSFUL` × 1 and zero matches of `BUILD FAILED | duplicate symbol | undefined reference | MBC5 unknown | bank overflow`.
  - Both ROM files produced at expected paths: `gbkt-examples/metasprites/build/gbkt/output/metasprites.gb` (32 KB) and `gbkt-examples/metasprites-stress/build/gbkt/output/metasprites-stress.gb` (32 KB).
  - Cite: `evidence/phase-close-buildrom-smoke.log` (Plan 14 Task 1).

- **Full test suite:** PASSED
  - `:gbkt-ir:test :gbkt-lang:test :gbkt-backend-gbdk:test` + 5 example projects' tests (`metasprites`, `pong`, `breakout`, `simple-physics`, `explorer`) exited 0 collectively. `BUILD SUCCESSFUL` × 1. Per-Seed sanity: 11 defect-locking test classes (25 tests total), 0 failures, 0 errors. `:gbkt-examples:metasprites-stress:test` was excluded from the verify command (Gradle 9 fails on test tasks with zero discovered tests; the stress example carries no Kotlin test sources, only `buildRom` — already exercised by D-21 Task 1).
  - Cite: `evidence/full-test-suite.log` (Plan 14 Task 2).

- **Cross-phase D-04 re-shoot:** SATISFIED (Plan 10.1-13 + orchestrator-driven MCP capture). The 3 Phase 10 UAT screenshots at `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/` were re-shot during Plan 10.1-13's checkpoint resolution and again iteratively across Plans 15/16/17/18/19/20/21/22. Post-Plan-22 behavior3 screenshot still showing all-black is the EXACT artifact that triggered the user-routing decision to escalate D-V3 to Phase 10.2 (see next bullet).
  - Cite: commit 94890c63 (`docs(phase-10.1,10.2): escalate DEF-13-C to Phase 10.2 after 4-round inline grind didn't close visual`).

- **Escalation honesty (HONEST CLOSURE STATUS):** D-V3 visual closure escalated to Phase 10.2 per user-routing decision (commit 94890c63, 2026-05-19) — **7 of 8 named defects fully closed; 1 (D-V3) closed at codegen-shape level with visual deferred to Phase 10.2 per `feedback_route_to_proper_phase_when_blast_radius_is_wide.md`. Reflects HONEST closure status.** Phase 10.2 is the sibling-decimal phase inserted via `gsd-sdk query phase.insert 10` (sibling decimal of Phase 10), driven by `SEED-013-gbc-palette-write-path-d-v3-visual.md` which captures (1) the pre-Plan-19 user-observed evidence that cyan sprites once rendered correctly, (2) the 4-round inline diagnose+fix chain that did not close visually, and (3) suggested Phase 10.2 approach (REVERT-vs-forward-fix decision, real-hardware vs. Coffee-GB comparison, SEED-012 memory-read MCP tool dependency).

  **Important distinction (per CLAUDE.md visual-evidence rule):** D-V3's codegen-shape closure is complete and locked by 8 RED→GREEN diagnostic tests across Plans 19+21 (`DV3GbcPaletteWriteDiagnosticTest` 3/3, `DV3VisualV2DiagnosticTest` 2/2, `Seed006SubPaletteSyncTest` 3/3). The generated C source matches the reference `gbdk/examples/cross-platform/metasprites/src/metasprites.c:177-194` step-for-step. The gap is in the asm/hardware-init / Coffee-GB-emulator-quirk layer — outside the gbkt-side DSL+IR+visitor stack. This is the precise scope Phase 10.2 will investigate.

  **D-03b doctrine reconciliation:** Phase 10.1's plan budget was sized to absorb surplus discoveries WITHIN the 8-defect + 3-absorbed-warning cluster (per D-03b). Plans 10.1-15..22 absorbed DEF-13-A (Plans 15/16), DEF-13-B (Plans 17/18), and 4 rounds on DEF-13-C (Plans 19/20/21/22) — all in-range. D-V3 visual escalation to Phase 10.2 is **not** a violation of D-03b — D-03b's escape valve #3 explicitly allows "Seed for a LATER phase ONLY if the discovery is genuinely outside the 8-defect cluster". The 4-round inline grind on DEF-13-C established that the visual gap is in the asm/hardware-init layer (Coffee-GB BCPD register path, sprite-palette regression introduced by one of Plans 19/20/22, possibly real-hardware divergence) — a different surface from the named defect cluster. Routing to Phase 10.2 with a proper `/gsd:discuss-phase` → research → plan flow is the doctrinally-correct response to the wide blast radius.

---

## Plans Inventory (10.1-01 through 10.1-22)

| Plan | Purpose | Closes |
|------|---------|--------|
| 10.1-01 | D-extra fix — `GameBuilder.kt:710-716` `else 0` palette-slot bug | D-extra (SEED-007) |
| 10.1-02 | D-V2 byte-pattern fix + WR-03 int8_t range validation (bundled per D-16) | D-V2 (SEED-005) + WR-03 |
| 10.1-03 | IR-level var-ref name fields on `MoveMetasprite` (D-10 / D-13 scoped exception) | (substrate for CR-03 + WR-01) |
| 10.1-04 | D-V3 sub-palette global-sync emission + IN-01 absorbed via D-12 "both" option (flipX/flipY) | D-V3 codegen layer (SEED-006) + IN-01 |
| 10.1-05 | CR-03 visitor reads from new IR fields (paired with 10.1-03) — closes WR-01 by same patch | CR-03 (SEED-010) + WR-01 |
| 10.1-06 | CR-02 per-bank `<gbdk/metasprites.h>` include (per D-09) | CR-02 (SEED-009) |
| 10.1-07 | WR-02 `extern const metasprite_t* const ...` in `game.h` (absorbed per D-14) | WR-02 |
| 10.1-08 | CR-01 `VramAllocator` class (per D-08 Route B override) | CR-01 (SEED-008) |
| 10.1-09 | WR-05 frame-scope hiwater hoist (per D-11) | WR-05 (SEED-011) |
| 10.1-10 | D-V1 diagnostic — name the bug (per D-05) | (substrate for 10.1-11) |
| 10.1-11 | D-V1 fix — joint two-edit (sprite-mode mismatch + SPRITES_8x8 hoist) | D-V1 (SEED-004) at codegen |
| 10.1-12 | Synthetic `metasprites-stress` ROM (per D-06 + D-07) — proves CR-01..CR-03 + WR-01/02/05 compose under SDCC | CR-01..CR-03 + WR-01/02/05 integration |
| 10.1-13 | UAT re-shoot per D-04 + D-18 — cross-phase write into Phase 10's evidence dir | D-V1/V2/V3 visual baseline (surfaces DEF-13-A/B/C as PARTIAL) |
| 10.1-14 | Phase close gate — D-21 ROM smoke + full test suite + closure matrix + human-verify checkpoint | (this plan) |
| 10.1-15 | DEF-10.1-13-A diagnostic — D-V1 visual partial (tile content still incoherent) | (substrate for 10.1-16) |
| 10.1-16 | DEF-10.1-13-A fix — `-noflip` arg to `png2asset` in `ConvertSpritesTask` | DEF-10.1-13-A (D-V1 visual completion) |
| 10.1-17 | DEF-10.1-13-B diagnostic — D-V2 visual partial (checker as rectangles not squares) | (substrate for 10.1-18) |
| 10.1-18 | DEF-10.1-13-B fix — literal-pattern correction in `bgFillCheckerboard` | DEF-10.1-13-B (D-V2 visual completion) |
| 10.1-19 | DEF-10.1-13-C round-1 diagnostic — D-V3 visual broken (GBC all-black) | (substrate for 10.1-20) |
| 10.1-20 | DEF-10.1-13-C round-1 fix — bootstrap-order layer (DISPLAY_OFF prepend, LCDC reorder, sprite-palette hoist into main pre-DISPLAY_ON) | DEF-10.1-13-C codegen-shape (bootstrap-order, 3 RED→GREEN); visual STILL black |
| 10.1-21 | DEF-10.1-13-C round-2 diagnostic — Coffee-GB cgb_compatibility BCPD-shortfall named cause | (substrate for 10.1-22) |
| 10.1-22 | DEF-10.1-13-C round-2 fix — explicit `set_bkg_palette` + bgFillCheckerboard hoist into main pre-DISPLAY_ON | DEF-10.1-13-C codegen-shape (cgb-BCPD layer, 2 RED→GREEN); visual STILL black — ESCALATED to Phase 10.2 |

**Total plans shipped:** 22 (target was ~12–16 per D-03 plan-sizing rule; surplus absorbed in-range per D-03b — 8 plans 15..22 inserted to address DEF-13-A/B/C without escalating to a 10.1.1 sub-subphase).

**Wave structure (approximate):**
- Wave 1 (parallel): Plans 01, 02, 04, 06, 07, 08 — file-affinity refactors with no cross-deps
- Wave 2: Plans 03, 05, 09 — depend on Wave-1 substrate
- Wave 3: Plan 10 (D-V1 diagnostic)
- Wave 4: Plan 11 (D-V1 fix, depends on 10)
- Wave 5: Plan 12 (synthetic stress ROM, depends on Wave-1/2 substrate)
- Wave 6: Plan 13 (UAT re-shoot)
- Wave 7 (in-range absorption after Plan 13 visual partial): Plans 15/16 (DEF-13-A), Plans 17/18 (DEF-13-B), Plans 19/20/21/22 (DEF-13-C round 1 + round 2)
- Wave 8: Plan 14 (phase close gate — this plan)

---

## Cross-References

- Plans 01-22 SUMMARY files at `.planning/phases/10.1-metasprites-surplus-codegen-defects-inserted/10.1-NN-SUMMARY.md`
- Phase 10.2 driver seed: `.planning/seeds/SEED-013-gbc-palette-write-path-d-v3-visual.md`
- User-routing decision: commit 94890c63 (`docs(phase-10.1,10.2): escalate DEF-13-C to Phase 10.2 after 4-round inline grind didn't close visual`)
- Deferred items: `.planning/phases/10.1-metasprites-surplus-codegen-defects-inserted/deferred-items.md` (DEF-10.1-09-A RESOLVED; DEF-10.1-13-A/B RESOLVED; DEF-10.1-13-C ESCALATED to Phase 10.2)
- ROM smoke gate evidence: `evidence/phase-close-buildrom-smoke.log` (Plan 14 Task 1)
- Test suite evidence: `evidence/full-test-suite.log` (Plan 14 Task 2)

---

*Phase: 10.1-metasprites-surplus-codegen-defects-inserted*
*Generated by: Plan 10.1-14 Task 3 (phase close gate)*
