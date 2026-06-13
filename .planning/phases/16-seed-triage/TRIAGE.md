# Phase 16: Seed Triage — Disposition Table

**Substrate SHA:** `TBD (pinned by Plan 02)`
**Substrate build date:** TBD (Plan 02 substrate pass)
**Total entries:** 47 (44 seeds + 3 folded todos)
**Status:** IN PROGRESS

> **Evidence substrate notice (D-14):** All evidence in this table will be captured against a
> single pinned commit SHA (recorded in `evidence/substrate-sha.txt` during Plan 02). Evidence
> captured against a different SHA is invalid and must be re-captured before finalizing any
> disposition. The SHA field above will be updated to the actual commit hash by Plan 02.

---

## Disposition Reference

| Value | Meaning | Evidence required |
|-------|---------|-------------------|
| `VERIFIED-ALREADY-FIXED` | Defect absent at HEAD | Green test run or generated-C inspection showing defect pattern absent |
| `CONFIRMED-OPEN` | Defect present at HEAD | Failing probe/emission test, defect screenshot, or generated-C showing defect present |
| `RE-DEFERRED` | Moved to v0.2.0 backlog | Rationale citation (REQUIREMENTS.md Future Requirements ID) |
| `INVALID` | Not a bug | Written rationale + same evidence bar as VERIFIED-ALREADY-FIXED |
| `TBD` | Pending cluster triage | — |

Type values: `visual` · `emission` · `jvm-test` · `source-only` · `re-deferred`

---

## Disposition Table

| ID | Title | Type | Disposition | Evidence | Fix-phase routing | Notes |
|----|-------|------|-------------|----------|------------------|-------|
| SEED-001 | IDE tooling integration | re-deferred | RE-DEFERRED | REQUIREMENTS.md IDE-02 | v0.2.0 backlog | D-12 fast-path |
| SEED-002 | Actor moveTo Expr overload missing | source-only | TBD | evidence/SEED-002/ | Phase 21 FIX-06 | Cluster D |
| SEED-003 | simple-physics playability polish | source-only | TBD | evidence/SEED-003/ | Phase 21 FIX-06 | Cluster D |
| SEED-004 | Elephant tile rendering garbled | visual | TBD | evidence/SEED-004/ | Phase 19 FIX-01 | Cluster A; W3 visual gate |
| SEED-005 | BG fill diagonal not checkerboard | visual | TBD | evidence/SEED-005/ | Phase 19 FIX-01 | Cluster A; W3 visual gate |
| SEED-006 | Sub-palette global not assigned before move | emission | TBD | evidence/SEED-006/ | Phase 19 FIX-01 | Cluster A |
| SEED-007 | Actor palette slot always defaults to 0 | emission | TBD | evidence/SEED-007/ | Phase 19 FIX-01 | Cluster A |
| SEED-008 | Metasprite VRAM collision with actors | emission | TBD | evidence/SEED-008/ | Phase 19 FIX-01 | Cluster A; latent |
| SEED-009 | Metasprites header missing in bank1 | emission | TBD | evidence/SEED-009/ | Phase 19 FIX-01 | Cluster A |
| SEED-010 | Symbol collision multi-metasprite games | emission | TBD | evidence/SEED-010/ | Phase 19 FIX-01 | Cluster A; latent |
| SEED-011 | hiwater collision multi-metasprite per frame | emission | TBD | evidence/SEED-011/ | Phase 19 FIX-01 | Cluster A; latent |
| SEED-012 | MCP emulator_read_memory tool missing | source-only | TBD | evidence/SEED-012/ | Phase 21 FIX-06 | Cluster D |
| SEED-013 | GBC sub-palette write D-V3 visual | visual | TBD | evidence/SEED-013/ | Phase 19 FIX-01 | Cluster A; W3 visual gate; check Ph 10.2 closure |
| SEED-014 | bkg_tiles_load_banked gating incomplete | jvm-test | TBD | evidence/SEED-014/ | Phase 20 FIX-03 | Cluster B; run INV-2 sentinel |
| SEED-015 | Banks trampoline body inheritance wrong | emission | TBD | evidence/SEED-015/ | Phase 20 FIX-03 | Cluster B |
| SEED-016 | Banks Anchor 4 SRAM test not executed | jvm-test | TBD | evidence/SEED-016/ | Phase 20 FIX-03 | Cluster B |
| SEED-017 | Sport zone tileset dual pipeline paths | source-only | TBD | evidence/SEED-017/ | Phase 21 FIX-06 | Cluster D |
| SEED-018 | RPG character codegen extern/decl mismatch | re-deferred | RE-DEFERRED | REQUIREMENTS.md RPG-01 | v0.2.0 backlog | D-12 fast-path |
| SEED-019 | IntelliJ plugin test infra coverage gap | re-deferred | RE-DEFERRED | REQUIREMENTS.md IDE-01 | v0.2.0 backlog | D-12 fast-path |
| SEED-020 | GameIR serializer full roundtrip stubs | jvm-test | TBD | evidence/SEED-020/ | Phase 21 FIX-06 | Cluster D |
| SEED-021 | platformer pivot_adjust hardcoded constant | source-only | TBD | evidence/SEED-021/ | Phase 21 FIX-05 | Cluster C |
| SEED-022 | Tilemap collision predicate duplicated | source-only | TBD | evidence/SEED-022/ | Phase 21 FIX-05 | Cluster C |
| SEED-023 | whenever/runif DSL unification needed | source-only | TBD | evidence/SEED-023/ | Phase 21 FIX-06 | Cluster D |
| SEED-024 | Build log export save dialog | re-deferred | RE-DEFERRED | REQUIREMENTS.md IDE-01 | v0.2.0 backlog | D-12 fast-path |
| SEED-025 | Deprecated combatIsInState String overload | source-only | TBD | evidence/SEED-025/ | Phase 21 FIX-06 | Cluster D |
| SEED-026 | Gradle plugin validatePlugins red | jvm-test | TBD | evidence/SEED-026/ | Phase 21 FIX-06 | Cluster D; run pluginTest not :test |
| SEED-PHASE-12-CONVERTSPRITESTASK-AUDIT | ConvertSpritesTask stub paths audit | emission | TBD | evidence/SEED-PHASE-12-CONVERTSPRITESTASK-AUDIT/ | TBD | Cluster D; self-reported RESOLVED Ph 12.4 — verify at HEAD |
| SEED-PHASE-12-GRASS-TILEMAP-WHITE-PIXELS | Grass tilemap white pixel artifacts | visual | TBD | evidence/SEED-PHASE-12-GRASS-TILEMAP-WHITE-PIXELS/ | TBD | Cluster D; self-reported RESOLVED Ph 12.9 — verify at HEAD |
| SEED-PHASE-12-ONE-WAY-TILE | One-way tile collision not implemented | source-only | TBD | evidence/SEED-PHASE-12-ONE-WAY-TILE/ | TBD | Cluster D |
| SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS | Per-zone tilemap separate banks missing | source-only | TBD | evidence/SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS/ | TBD | Cluster D |
| SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY | Spawn position start ambiguity | visual | TBD | evidence/SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY/ | Phase 21 FIX-05 | Cluster C; superseded by spawn-polish |
| SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS | PlatformerVisitor cEmit escape-hatch gaps | emission | TBD | evidence/SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS/ | Phase 21 FIX-05 | Cluster C; check Ph 13.5 closures |
| SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED | Player levitates above ground tile | visual | TBD | evidence/SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED/ | TBD | Cluster C; W3 visual gate; may be fixed by Ph 12.6/12.7 |
| SEED-PHASE-12-PLAYER-METASPRITE-RENDER | Player renders as square placeholder | visual | TBD | evidence/SEED-PHASE-12-PLAYER-METASPRITE-RENDER/ | TBD | Cluster A; W3 visual gate; may be fixed by Ph 12.4 |
| SEED-PHASE-12-RETROACTIVE-BANKS-AUDIT | Banks example retroactive build audit | emission | TBD | evidence/SEED-PHASE-12-RETROACTIVE-BANKS-AUDIT/ | TBD | Cluster B; self-reported trivially satisfied — verify at HEAD |
| SEED-PHASE-12-SHARED-TILESET | Shared tileset deduplication not implemented | source-only | TBD | evidence/SEED-PHASE-12-SHARED-TILESET/ | TBD | Cluster D |
| SEED-PHASE-12-TITLE-ZONE-PATH-A-SCENE-RENDER-DEFECT | Title-zone path-A scene render defect | emission | TBD | evidence/SEED-PHASE-12-TITLE-ZONE-PATH-A-SCENE-RENDER-DEFECT/ | TBD | Cluster D; self-reported RESOLVED Ph 12.2 — verify at HEAD |
| SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE-BG-AND-OBJ | Platformer BG+OBJ palette inverted | visual | TBD | evidence/SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE-BG-AND-OBJ/ | TBD | Cluster C; W3 visual gate; may be fixed by Ph 13.8; see seeds/evidence/platformer-gbc-inverted-colors-2026-06-04.png |
| SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK | Player feet 1-2px sunk into ground | visual | TBD | evidence/SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK/ | Phase 21 FIX-05 | Cluster C; W3 visual gate |
| SEED-PHASE-13-SPRITE-OUTLINE-LOST-NONZERO-TRNS-INDEX | Sprite outline lost non-zero tRNS index | emission | TBD | evidence/SEED-PHASE-13-SPRITE-OUTLINE-LOST-NONZERO-TRNS-INDEX/ | Phase 20 FIX-04 | Cluster A; OPEN as of Ph 13.3 |
| SEED-PHASE-X-CPAREN-EXPR-IN-C-AST | C AST CParen expression wrapper | re-deferred | RE-DEFERRED | REQUIREMENTS.md ARCH-02 | v0.2.0 backlog | D-12 fast-path; ~50+ fixture re-snapshots |
| SEED-platformer-template-spawn-polish | Platformer spawn position polish | visual | TBD | evidence/SEED-platformer-template-spawn-polish/ | Phase 21 FIX-05 | Cluster C; W3 visual gate; supersedes spawn-clarity |
| SEED-RAW-C-CODEGEN-AST-MIGRATION | Raw C codegen → full AST migration | re-deferred | RE-DEFERRED | REQUIREMENTS.md ARCH-01 | v0.2.0 backlog | D-12 fast-path; own architecture phase |
| SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION | zone() magic string → delegate migration | source-only | TBD | evidence/SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION/ | Phase 21 FIX-06 | Cluster D |
| TODO-metasprites-baseline | Metasprite byte-identity baseline stale since Ph 12.8 | jvm-test | TBD | evidence/TODO-metasprites-baseline/ | Phase 19 | Cluster A; expected RED due to stale golden — not a new defect |
| TODO-13.8-wr-followups | Phase 13.8 code-review WR follow-ups (WR-01/02/03) | source-only | TBD | evidence/TODO-13.8-wr-followups/ | Phase 19/20 | Cluster A/D; 3 advisory items (allocatedZoneBank, initialSubPaletteSlot, RGB555 fallback) |
| TODO-triggersystem-validation | TriggerSystem ref-registry validation missing | source-only | TBD | evidence/TODO-triggersystem-validation/ | Phase 21 FIX-06 | Cluster D; verify: triggerSystem(nonexistent) should throw at build() |

---

## Fast-Path RE-DEFERRED Detail (D-12)

These six entries require no verification work — their disposition is finalized by the REQUIREMENTS.md
Future Requirements entry that defines them as out-of-scope for v0.1.1.

- **`SEED-001`** → IDE-02: v2.0 trigger — DSL-to-IDE tooling integration; no v0.1.1 driver
- **`SEED-018`** → RPG-01: Archived dungeon/explorer games; RPG character codegen extern/decl fix deferred
- **`SEED-019`** → IDE-01: IntelliJ plugin test infra lift; requires own plugin-infra phase
- **`SEED-024`** → IDE-01: Build log export save dialog; part of IDE-01 scope
- **`SEED-RAW-C-CODEGEN-AST-MIGRATION`** → ARCH-01: Own architecture phase required; ~full backend rewrite scope
- **`SEED-PHASE-X-CPAREN-EXPR-IN-C-AST`** → ARCH-02: ~50+ fixture re-snapshots; requires ARCH-01 as prerequisite

---

## Progress Summary

- **Total entries:** 47
- **RE-DEFERRED (fast-path):** 6
- **TBD (awaiting cluster triage):** 41
- **VERIFIED-ALREADY-FIXED:** 0
- **CONFIRMED-OPEN:** 0
- **INVALID:** 0

*Last updated: Plan 01 (skeleton creation). Status advances to FINAL after W3 batch visual review gate.*
