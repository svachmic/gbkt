# Roadmap: gbkt Compiler Pipeline Rebuild

## Milestones

- ✅ **v0.1.0 MVP — Compiler Pipeline Rebuild** — Phases 1–15 (shipped 2026-06-09)
- 📋 **Next milestone** — genre-codegen completion, tech-debt cleanup, IDE DX (planned)

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

### 📋 Next milestone (deferred / planned)

Carried forward from v0.1.0 as deferred scope (see `MILESTONES.md` → Known Gaps and STATE.md → Deferred Items):

- [ ] Phase 07: UAT Gameplay Validation — manual play-testing across ROMs
- [ ] Phase 07.5: Platformer Genre Codegen Fix
- [ ] Phase 07.6: RPG Genre Codegen Audit (incl. SEED-018 RPG-port buildRom debt)
- [ ] Phase 07.7: GBC Palette Initialization
- [ ] Phase 07.8: UAT Re-run after genre codegen fixes
- [ ] Phase 08: Detekt and Tech Debt Cleanup (QUAL-01..03)
- [ ] IDE-04: IntelliJ DX completion
- [ ] Backlog triage — 56 deferred items (seeds + advisory codegen todos) via `/gsd-review-backlog`

## Progress

| Milestone | Phases | Status | Completed |
| --------- | ------ | ------ | --------- |
| v0.1.0 MVP — Compiler Pipeline Rebuild | 1–15 (66 incl. decimals) | ✅ Shipped | 2026-06-09 |
| Next milestone | 07, 07.5–07.8, 08, + backlog | 📋 Planned | — |
