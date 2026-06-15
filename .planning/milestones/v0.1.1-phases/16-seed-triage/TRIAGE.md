# Phase 16: Seed Triage — Disposition Table

**Substrate SHA:** `8cef3dbca7d0868f42cf0d627921b8559d7754e8`
**Substrate build date:** 2026-06-12 (Phase 16 substrate pass W1)
**Total entries:** 47 (44 seeds + 3 folded todos)
**Status:** FINAL

> **Evidence substrate notice (D-14):** All evidence in this table was captured against the
> single pinned commit SHA above (recorded in `evidence/substrate-sha.txt` during Plan 02). Evidence
> captured against a different SHA is invalid and must be re-captured before finalizing any
> disposition.

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
| SEED-002 | Actor moveTo Expr overload missing | source-only | VERIFIED-ALREADY-FIXED | evidence/SEED-002/source-inspection.txt | — | moveTo(Expr,Expr) at ActorBuilder.kt:335-346; seed concern resolved |
| SEED-003 | simple-physics playability polish | emission | RE-DEFERRED | evidence/SEED-003/evidence.txt | v0.2.0 examples-polish | Reference-faithful behavior; trigger is "playable demos" milestone, not v0.1.1 |
| SEED-004 | Elephant tile rendering garbled | visual | VERIFIED-ALREADY-FIXED | evidence/SEED-004/screenshot.png | — | LOCKED via visual review 2026-06-12. USER OVERRIDE: agent proposed CONFIRMED-OPEN; human reviewer confirmed elephant renders correctly at HEAD. |
| SEED-005 | BG fill diagonal not checkerboard | visual | VERIFIED-ALREADY-FIXED | evidence/SEED-005/screenshot.png | — | LOCKED via visual review 2026-06-12. HEAD shows proper checkerboard; fix landed in Phase 10.1 or later. |
| SEED-006 | Sub-palette global not assigned before move | emission | VERIFIED-ALREADY-FIXED | evidence/SEED-006/main-c-excerpt.txt | — | `_elephant_subPalette = subpal;` present at play_frame():283 in metasprites/main.c; scoped function-body grep |
| SEED-007 | Actor palette slot always defaults to 0 | source-only | VERIFIED-ALREADY-FIXED | evidence/SEED-007/main-c-excerpt.txt | — | `actorPaletteAutoSlot++` counter present at GameBuilder.kt:716; comment documents the bug + fix |
| SEED-008 | Metasprite VRAM collision with actors | emission | VERIFIED-ALREADY-FIXED | evidence/SEED-008/main-c-excerpt.txt | — | metasprites-stress main.c: set_sprite_data with monotonic VRAM allocator (Route A) confirmed |
| SEED-009 | Metasprites header missing in bank1 | emission | VERIFIED-ALREADY-FIXED | evidence/SEED-009/main-c-excerpt.txt | — | metasprites-stress bank1.c:7 includes `<gbdk/metasprites.h>`; include present and required |
| SEED-010 | Symbol collision multi-metasprite games | emission | VERIFIED-ALREADY-FIXED | evidence/SEED-010/main-c-excerpt.txt | — | metasprites-stress: elephant_metasprites[] and tiger_metasprites[] namespaced by ID; no symbol collision |
| SEED-011 | hiwater collision multi-metasprite per frame | emission | VERIFIED-ALREADY-FIXED | evidence/SEED-011/main-c-excerpt.txt | — | metasprites-stress bank1.c: hiwater=0 once at frame start; hiwater+= throughout; hide_sprites_range once at end; Route A fix confirmed |
| SEED-012 | MCP emulator_read_memory tool missing | source-only | VERIFIED-ALREADY-FIXED | evidence/SEED-012/source-inspection.txt | — | Both emulator_read_memory and emulator_write_memory registered in ToolHandlers.kt |
| SEED-013 | GBC sub-palette write D-V3 visual | visual | VERIFIED-ALREADY-FIXED | evidence/SEED-013/screenshot-before-b-press.png, evidence/SEED-013/screenshot-after-b-press.png | — | LOCKED via visual review 2026-06-12. Elephant renders in correct GBC colors; Phase 10.2 fixed this. |
| SEED-014 | bkg_tiles_load_banked gating incomplete | jvm-test | VERIFIED-ALREADY-FIXED | evidence/SEED-014/inv2-test-output.txt | — | INV-2+INV-6 GREEN; hasZoneSceneBinder guard sufficient |
| SEED-015 | Banks trampoline body inheritance wrong | emission | VERIFIED-ALREADY-FIXED | evidence/SEED-015/main-c-excerpt.txt | — | INV-5 GREEN; title_enter deduplicated to pause_enter (correct canonical) |
| SEED-016 | Banks Anchor 4 SRAM test not executed | jvm-test | VERIFIED-ALREADY-FIXED | evidence/SEED-016/source-inspection.txt | — | Anchor 4 @Test present (BanksUatTest.kt:291); ran in substrate (0 failures) |
| SEED-017 | Sport zone tileset dual pipeline paths | source-only | CONFIRMED-OPEN | evidence/SEED-017/source-inspection.txt | Phase 21 FIX-06 | buildBuiltinTrackTilesetVarDecl still in SportVisitor.kt; LEGACY-path SEED-017 comment; INV-8 locks legacy path |
| SEED-018 | RPG character codegen extern/decl mismatch | re-deferred | RE-DEFERRED | REQUIREMENTS.md RPG-01 | v0.2.0 backlog | D-12 fast-path |
| SEED-019 | IntelliJ plugin test infra coverage gap | re-deferred | RE-DEFERRED | REQUIREMENTS.md IDE-01 | v0.2.0 backlog | D-12 fast-path |
| SEED-020 | GameIR serializer full roundtrip stubs | source-only | CONFIRMED-OPEN | evidence/SEED-020/evidence.txt | Phase 21 FIX-06 | 10 emptyList() stubs in deserializeGameIR with SEED-020 markers; substrate tests GREEN but don't exercise stubs |
| SEED-021 | platformer pivot_adjust hardcoded constant | source-only | CONFIRMED-OPEN | evidence/SEED-021/source-inspection.txt | Phase 21 FIX-05 | "Deferred (SEED-021)" marker at PlatformerVisitor.kt:625; visitor computes pivotAdjust internally; DSL builder has no pivotAdjust |
| SEED-022 | Tilemap collision predicate duplicated | source-only | CONFIRMED-OPEN | evidence/SEED-022/source-inspection.txt | Phase 21 FIX-06 | Two private implementations: PlatformerVisitor.kt:1663 + GBDKPipeline.kt:2183; "Deferred (SEED-022)" at PlatformerVisitor.kt:1589 |
| SEED-023 | whenever/runif DSL unification needed | source-only | CONFIRMED-OPEN | evidence/SEED-023/source-inspection.txt | Phase 18 DEPR-01 | whenever() not @Deprecated; KDoc says "Not deprecated this phase"; SEED-023 ref in KDoc |
| SEED-024 | Build log export save dialog | re-deferred | RE-DEFERRED | REQUIREMENTS.md IDE-01 | v0.2.0 backlog | D-12 fast-path |
| SEED-025 | Deprecated combatIsInState String overload | source-only | CONFIRMED-OPEN | evidence/SEED-025/source-inspection.txt | Phase 18 DEPR-02 | String overload still present @Deprecated(ReplaceWith); removal needed in v0.2.0; SonarCloud S1133 open |
| SEED-026 | Gradle plugin validatePlugins red | jvm-test | VERIFIED-ALREADY-FIXED | evidence/SEED-026/evidence.txt | — | validatePlugins=PASS, pluginTest=174/0 at substrate SHA; race not triggered |
| SEED-PHASE-12-CONVERTSPRITESTASK-AUDIT | ConvertSpritesTask stub paths audit | emission | VERIFIED-ALREADY-FIXED | evidence/SEED-PHASE-12-CONVERTSPRITESTASK-AUDIT/evidence.txt | — | player.c starts with AUTOGENERATED FROM png2asset; no stub comment; Phase 12.4 resolution holds |
| SEED-PHASE-12-GRASS-TILEMAP-WHITE-PIXELS | Grass tilemap white pixel artifacts | visual | VERIFIED-ALREADY-FIXED | evidence/SEED-PHASE-12-GRASS-TILEMAP-WHITE-PIXELS/screenshot.png | — | LOCKED via visual review 2026-06-12. No white pixel artifacts visible; Phase 12.9 palette polarity fix confirmed. |
| SEED-PHASE-12-ONE-WAY-TILE | One-way tile collision not implemented | re-deferred | RE-DEFERRED | evidence/SEED-PHASE-12-ONE-WAY-TILE/source-inspection.txt | v0.2.0 backlog (future platformer-port trigger) | Serena find_symbol "oneWayThreshold" → empty; symbol never implemented; revival requires triggering port |
| SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS | Per-zone tilemap separate banks missing | re-deferred | RE-DEFERRED | evidence/SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS/source-inspection.txt | v0.2.0 backlog (when overflow guard trips) | checkTilemapBankOverflow exists but not tripped; bank 2 = 6120 / 14336 bytes (42.7%); 8216 bytes headroom |
| SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY | Spawn position start ambiguity | source-only | CONFIRMED-OPEN | evidence/SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY/evidence.txt | Phase 21 FIX-05 (absorb into spawn-polish) | Superseded by SEED-platformer-template-spawn-polish (same root cause); spawn still at Y=72 mid-screen |
| SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS | PlatformerVisitor cEmit escape-hatch gaps | source-only | VERIFIED-ALREADY-FIXED | evidence/SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS/source-inspection.txt | — | grep -n "cEmit" PlatformerTemplate.kt → zero matches; Phase 13.5 removed all 4 escape-hatch cEmit calls |
| SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED | Player levitates above ground tile | visual | VERIFIED-ALREADY-FIXED | evidence/SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED/screenshot.png | — | LOCKED via visual review 2026-06-12. grounded=1 at frame 123; player visually on ground tile row; Phase 12.6/12.7 physics codegen fixed. |
| SEED-PHASE-12-PLAYER-METASPRITE-RENDER | Player renders as square placeholder | visual | VERIFIED-ALREADY-FIXED | evidence/SEED-PHASE-12-PLAYER-METASPRITE-RENDER/screenshot.png | — | LOCKED via visual review 2026-06-12. Player renders as proper duck sprite (6 OAM entries); Phase 12.4 ConvertSpritesTask fix confirmed. |
| SEED-PHASE-12-RETROACTIVE-BANKS-AUDIT | Banks example retroactive build audit | emission | VERIFIED-ALREADY-FIXED | evidence/SEED-PHASE-12-RETROACTIVE-BANKS-AUDIT/evidence.txt | — | bank1.c references real tileset via symbolic constants; D-01 Path A holds |
| SEED-PHASE-12-SHARED-TILESET | Shared tileset deduplication not implemented | re-deferred | RE-DEFERRED | evidence/SEED-PHASE-12-SHARED-TILESET/source-inspection.txt | v0.2.0 backlog (when ROM size pressure triggers) | No dedup code in ConvertZoneTilesetsTask; MultiTilesetAllocationTest.kt asserts duplication present (canary); ROM 64KB within 2x threshold |
| SEED-PHASE-12-TITLE-ZONE-PATH-A-SCENE-RENDER-DEFECT | Title-zone path-A scene render defect | visual | VERIFIED-ALREADY-FIXED | evidence/SEED-PHASE-12-TITLE-ZONE-PATH-A-SCENE-RENDER-DEFECT/screenshot.png | — | LOCKED via visual review 2026-06-12. Title shows single clean "GBDK-2020 PLATFORMER TEMPLATE"; Phase 12.2/12.3 Plans 11-13 fixed ConvertZoneTilesetsTask. |
| SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE-BG-AND-OBJ | Platformer BG+OBJ palette inverted | visual | VERIFIED-ALREADY-FIXED | evidence/SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE-BG-AND-OBJ/screenshot-head.png | — | LOCKED via visual review 2026-06-12. HEAD shows correct green colors; Phase 13.7 OBJ polarity fix + Phase 13.8 hardening approved BYTE-IDENTICAL. |
| SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK | Player feet 1-2px sunk into ground | visual | CONFIRMED-OPEN | evidence/SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK/screenshot.png | Phase 21 FIX-05 | LOCKED via visual review 2026-06-12. Sprite bottom at ~screenY=120, ground at ~screenY=128; 8px gap may be sub-pixel bug; remains open. |
| SEED-PHASE-13-SPRITE-OUTLINE-LOST-NONZERO-TRNS-INDEX | Sprite outline lost non-zero tRNS index | source-only | VERIFIED-ALREADY-FIXED | evidence/SEED-PHASE-13-SPRITE-OUTLINE-LOST-NONZERO-TRNS-INDEX/source-inspection.txt | — | Phase 13.6 tRNS auto-route in ConvertSpritesTask.kt:328-372; elephant.c header confirms gbkt_permuted_elephant.png temp path; visual closure oracle belongs to Phase 20 D-08 |
| SEED-PHASE-X-CPAREN-EXPR-IN-C-AST | C AST CParen expression wrapper | re-deferred | RE-DEFERRED | REQUIREMENTS.md ARCH-02 | v0.2.0 backlog | D-12 fast-path; ~50+ fixture re-snapshots |
| SEED-platformer-template-spawn-polish | Platformer spawn position polish | visual | CONFIRMED-OPEN | evidence/SEED-platformer-template-spawn-polish/screenshot.png | Phase 21 FIX-05 | LOCKED via visual review 2026-06-12. grounded=1 at spawn (functional); visual polish of exact spawn position remains open. |
| SEED-RAW-C-CODEGEN-AST-MIGRATION | Raw C codegen → full AST migration | re-deferred | RE-DEFERRED | REQUIREMENTS.md ARCH-01 | v0.2.0 backlog | D-12 fast-path; own architecture phase |
| SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION | zone() magic string → delegate migration | source-only | CONFIRMED-OPEN | evidence/SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION/source-inspection.txt | Phase 21 FIX-06 | Primary GameBuilder.zone(id:String) was FIXED; PickupBuilder.zone(id:String, pickupId:String) at PickupBuilder.kt:229 remains |
| TODO-metasprites-baseline | Metasprite byte-identity baseline stale since Ph 12.8 | jvm-test | VERIFIED-ALREADY-FIXED | evidence/TODO-metasprites-baseline/test-output.txt | — | MetaspritesGeneratedSpriteByteIdentityTest GREEN at HEAD; baseline modified Jun 10 2026; D-15 satisfied by Phase 13.6 visual approval; todo is moot. NOTE (D-15): baseline promotion deferred to Phase 19 after visual gate approval — that approval occurred 2026-06-12 (D-08 gate PASSED); baseline is now current. |
| TODO-13.8-wr-followups | Phase 13.8 code-review WR follow-ups (WR-01/02/03) | source-only | CONFIRMED-OPEN | evidence/TODO-13.8-wr-followups/source-inspection.txt | Phase 19/20 | WR-01: SceneIR.allocatedZoneBank misleading (Phase 19/20); WR-02: initialSubPaletteSlot no collision guard (Phase 19); WR-03: RGB555 fallback no range check (Phase 20) |
| TODO-triggersystem-validation | TriggerSystem ref-registry validation missing | source-only | CONFIRMED-OPEN | evidence/TODO-triggersystem-validation/source-inspection.txt | Phase 21 FIX-06 | triggerSystem() emits TriggerSystem(ref.systemId) with no registry check; RED repro: triggerSystem(SystemRef("nonexistent")) should throw at build() |

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
- **VERIFIED-ALREADY-FIXED:** 24
- **CONFIRMED-OPEN:** 12
- **RE-DEFERRED:** 11
- **INVALID:** 0
- **TBD:** 0

*Last updated: Plan 09 (final consolidation — all 47 rows finalized). Status: FINAL.*
