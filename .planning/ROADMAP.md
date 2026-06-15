# Roadmap: gbkt Compiler Pipeline Rebuild

## Milestones

- ✅ **v0.1.0 MVP — Compiler Pipeline Rebuild** — Phases 1–15 (shipped 2026-06-09)
- ✅ **v0.1.1 Hardening** — Phases 16–22 (shipped 2026-06-15)

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

<details>
<summary>✅ v0.1.1 Hardening (7 phases, 79 plans, 110 tasks) — SHIPPED 2026-06-15</summary>

Drained the v0.1.0 deferred-debt backlog: every seed reached a terminal disposition (fixed, verified-closed, or explicitly re-routed to v0.2.0); docs now tell the truth; static-analysis debt burned down to zero. Delivered across:

- **Phase 16: Seed Triage** — 47-entry TRIAGE.md with 24 VERIFIED-ALREADY-FIXED, 12 CONFIRMED-OPEN, 10 RE-DEFERRED dispositions against substrate SHA `8cef3dbc`; D-08 binding human visual review gate passed (completed 2026-06-12)
- **Phase 17: Docs Reconciliation and Quality Cleanup** — DSL_REFERENCE.md accuracy restored across 13 sections; `./gradlew detekt` zero violations (no baselines); 8 in-scope 160/144 magic-pixel literals replaced with `GameBoyConstants.SCREEN_WIDTH/HEIGHT`; `TargetProfiles.GAME_BOY_SCREEN` canonical preset added (completed 2026-06-12)
- **Phase 18: Deprecation Removals and Sonar Burn-down** — `whenever`/`runIf` unified (80+ in-tree call sites migrated); deprecated `combatIsInState(String,String)` removed; CONTRIBUTING.md deprecation convention documented; SonarCloud S3776 HIGH findings: 46 → 0 via extract-method, 0 NOSONAR used; byte-identity oracle held across all 29 EMITTING commits (completed 2026-06-13)
- **Phase 19: Codegen Fixes — Metasprite Cluster** — FIX-01 closed with fresh GBC-mode HEAD screenshots for SEED-004/005/006/013; FIX-02 discharged as a 1:1 seed→guard audit (5 emission guards GREEN); byte-identity CLEAN (completed 2026-06-13)
- **Phase 20: Codegen Fixes — Banks and Sprite Transparency** — SEED-014/015/016 formally re-verified CLOSED; tRNS sprite outline (SEED-PHASE-13-SPRITE-OUTLINE) confirmed fixed via GBC UAT screenshots; 7-example byte-identity sweep PASS; verification 5/5 (completed 2026-06-14)
- **Phase 21: Codegen Fixes — Platformer and Remaining Seeds** — `pivotAdjust(Int)` DSL lift (SEED-021); `gameUsesTilemapCollisionPathC` predicate consolidated; GameIRSerializer 10 deserialization stubs replaced with real round-trip tests; `.planning/seeds/` empty at phase close; D-13 byte-identity oracle CLEAN (completed 2026-06-14)
- **Phase 22: Golden Screenshot and Evidence Storage Overhaul** — per-phase `EVIDENCE_DIR` pattern eliminated from 27 UAT/emission test classes; central ROM+anchor-keyed immutable goldens replacing it; `discoverFiles` GBC auto-detect from ROM byte 0x143; 22 binding goldens migrated byte-identically from Phases 19/20/21; 143 tracked per-phase evidence files removed; clean-tree gate green (completed 2026-06-15)

Full phase-by-phase detail (goals, plans, success criteria, wave structure) is archived in **`.planning/milestones/v0.1.1-phases/`** and **`.planning/milestones/v0.1.1-ROADMAP.md`**.

</details>

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1–15 (66 incl. decimals) | v0.1.0 | 652/652 | Complete | 2026-06-09 |
| 16. Seed Triage | v0.1.1 | 10/10 | Complete | 2026-06-12 |
| 17. Docs Reconciliation and Quality Cleanup | v0.1.1 | 12/12 | Complete | 2026-06-12 |
| 18. Deprecation Removals and Sonar Burn-down | v0.1.1 | 30/27 | Complete | 2026-06-13 |
| 19. Codegen Fixes — Metasprite Cluster | v0.1.1 | 4/4 | Complete | 2026-06-13 |
| 20. Codegen Fixes — Banks and Sprite Transparency | v0.1.1 | 4/4 | Complete | 2026-06-14 |
| 21. Codegen Fixes — Platformer and Remaining Seeds | v0.1.1 | 8/8 | Complete | 2026-06-14 |
| 22. Golden Screenshot and Evidence Storage Overhaul | v0.1.1 | 14/14 | Complete | 2026-06-15 |
