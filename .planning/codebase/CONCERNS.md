# Codebase Concerns

**Analysis Date:** 2026-05-27

This document catalogs technical debt, known defects, fragile areas, and architectural concerns in the gbkt codebase. Evidence is drawn from `.planning/STATE.md` (46/60 phases complete), the Phase 12.x palette/VRAM-clear debug chain (`12.4` → `12.6` → `12.7` → `12.8` → `12.9`), `.planning/seeds/`, project memory feedback rules, and TODO/FIXME comments in source.

## Tech Debt

### Deprecated DSL surface — annotation gap vs documentation

**Issue:** `CLAUDE.md` documents that `assign()`, `varRef()`, `literal()`, `arrayAssign()`, `arrayRef()`, `raw()`, `dpadHeld()`, `buttonPressed()`, and `dpadAny()` are deprecated in favor of delegate operators, BUT only one `@Deprecated` annotation exists in the entire main source tree (`gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/RpgExtensions.kt:414` on `combatIsInState(stateId: String, battleId: String)`). The `assign()` / `arrayAssign()` functions are merely marked `internal fun` in `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ScriptBuilder.kt:116,126`, not `@Deprecated`.
**Files:**
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ScriptBuilder.kt:116` — `internal fun assign(...)`
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ScriptBuilder.kt:126` — `internal fun arrayAssign(...)`
- `gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/RpgExtensions.kt:414` — only annotated `@Deprecated` in main code
**Impact:** Compiler warnings do not fire on usage; users may keep writing magic-string code instead of migrating to delegate operators. Phase 06.6 SC #6 (per ROADMAP) calls for "Old deprecated API methods (`assign()`, `varRef()`, `literal()`, `raw()`) escalated from WARNING to ERROR" — escalation is incomplete.
**Fix approach:** Audit DSL surface; add `@Deprecated(level = DeprecationLevel.WARNING)` to legacy entry points; track timeline for `DeprecationLevel.ERROR` escalation per Phase 06.6 success criteria.

### RPG character codegen extern/declaration mismatch

**Issue:** `:gbkt-examples:dungeon:buildRom` and `:gbkt-examples:explorer:buildRom` both fail with `extern definition for '_char_<name>_<stat>' mismatches with declaration` errors for all 7 RPG stats (hp/sp/atk/def/matk/mdef/agl). Defect pre-dates Phase 11.1 base; the `gbkt-genre-rpg` character codegen path has not been touched since the regression first appeared.
**Files:**
- `gbkt-examples/dungeon/build/gbkt/generated/main.c:60-66` — extern definitions
- `gbkt-examples/dungeon/build/gbkt/generated/game.h:66-72` — conflicting declarations
- `gbkt-examples/explorer/build/gbkt/generated/main.c` — same pattern
- `gbkt-backend-gbdk/.../codegen/visitor/CharacterVisitor.kt` (and/or wherever `_char_*` are emitted) — emission site
**Impact:** 2 of 8 example games cannot build to ROM. Blocks any future Pokémon-scale RPG work outlined in `PROJECT.md`. Banks and racer (no character block) build clean.
**Fix approach:** Per project memory `feedback_route_to_proper_phase_when_blast_radius_is_wide`, route to a dedicated discuss-phase → research → plan cycle. Decisions needed: which side is authoritative (extern in `.c` or declaration in `.h`), what type (`UINT8`/`UINT16`/signed), whether to migrate to per-character struct. See `.planning/seeds/SEED-018-rpg-character-codegen-extern-decl-mismatch.md`.

### IntegrationTest baseline RED — 14 failures

**Issue:** 14 IntegrationTest failures inherited from Phase 11.1-04 due to `SceneIR.<init>` signature change (zoneRefs added in commit `eda282ec`); IntegrationTest fixtures last touched in 09.2-02 and were never updated for the new signature.
**Files:**
- `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/SceneIR.kt` — current signature
- IntegrationTest fixtures — stale `SceneIR(...)` constructor calls
**Impact:** Not a buildRom-gate failure (phase acceptance is `:buildRom`), but masks any real regression in the IntegrationTest surface. Skews CI noise budget.
**Fix approach:** Separate test-infra phase to update fixtures. NOT in scope of Phase 12 or 12.4.

### Banking complexity — bank-restore landmines

**Issue:** `ZoneCodegen.generateBankedTilemapData()` must end with `setBank(16)` (NOT `returnToHome()`); without the bank-restore, all subsequent codegen inherits the tilemap bank and produces bank overflow at compile time. The `currentBank` state in the C-codegen layer is mutable and persistent — forgetting `returnToHome()`/`setBank()` leaks bank assignment across emission sites.
**Files:**
- `gbkt-backend-gbdk/.../codegen/visitor/ZoneCodegen.kt` (the `generateBankedTilemapData` site)
- `gbkt-backend-gbdk/.../codegen/pipeline/GBDKPipelineV2.kt` — owns the bank-routing state
**Impact:** Subtle defects surface only as "bank N overflow" build errors. Phase 12.1 Defect 6 (BANK macro vs data-array `#pragma bank` mismatch) was a closely related class of bug.
**Fix approach:** Introduce a scoped bank-context wrapper (RAII-style) so callers cannot forget to restore. Tracked implicitly via `BankingAnalysisPass` evolution.

### GBDK BANKED calling convention — silent corruption when missing

**Issue:** Functions in non-zero banks must be tagged `BANKED` (calling convention requirement). When `splitByBank` does not add `BANKED` to every function def in non-zero banks, the resulting ROM crashes with `MBC5 unknown address/value` errors at runtime (e.g. 0xBA00+, value 64). The fix landed in `splitByBank`, `processBankedLine()`, `extractFunctionPrototypes()`; forward declarations are also filtered.
**Files:**
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/postprocess/` (splitByBank, processBankedLine, processPreBankLine)
- `funcDefPattern` / `funcDeclPattern` — required `(?:const\s+)?` prefix for `const char*` return types
**Impact:** No JVM-tier test will catch this — only runtime emulator failure. Per project memory, recovery required adding `const` to the patterns and filtering pre-bank forward declarations.
**Fix approach:** Add invariant tests at the `splitByBank` boundary; ensure every emitted function def in bank N where N>0 carries `BANKED`. JVM grep over all generated bankN.c files for `^(?:const\s+)?\w+\s+\w+\(.*\)\s*\{` lines missing `BANKED`.

### debugGraphics toggle hazard

**Issue:** `debugGraphics = true` emits `printf()` calls in zone_load_tilemap, scene enters, and init. GBDK `printf` writes to the BACKGROUND tile layer — when tile slots 0..127 hold dungeon graphics (custom tileset), `printf` corrupts the tilemap. The toggle does not fail safe.
**Files:**
- `gbkt-backend-gbdk/.../codegen/pipeline/GBDKPipelineV2.kt` (debugGraphics emission sites)
- Per project memory, setting `debugGraphics = false` is mandatory for games with custom tilesets.
**Impact:** Cross-cutting visual corruption that masquerades as a tileset bug. Took multiple debug sessions in Phase 11.x to root-cause.
**Fix approach:** Route ALL debug text through the window layer (matching the Window-Layer UI rule below), or gate `debugGraphics = true` behind a custom-tileset-detection guard so it cannot ship into a tileset-loaded build.

### Window-layer vs background-layer UI separation

**Issue:** All UI text (dialogs, menus, battle UI, status bars) MUST render via `_win_*` helpers from `WindowTextCodegen`. ANY `gotoxy`/`printf` call corrupts the background tile layer when custom tilesets are loaded. Phase 07.4 round-2 racer bug surfaced exactly this: `clear()` in `raceScene.enter` (`Racer.kt:137`) lowered to GBDK `cls()` AFTER the racing genre's BG paint, wiping the freshly-painted track. `print("LAP:", ...)` in the same enter block lowered to BG-layer `gotoxy`/`printf` — never the window layer.
**Files:**
- `gbkt-backend-gbdk/.../codegen/WindowTextCodegen.kt` — canonical helper site
- `gbkt-genre-sport/.../SportVisitor.kt:enterOps` — addGenreEnterOps@`GBDKPipelineV2.kt:1479` prepends; user ops always run AFTER
**Impact:** Reframing required by Phase 07.4 UAT (`.planning/phases/07.4-sport-genre-codegen-fix-inserted/07.4-UAT.md`) — three suggested fix paths (patch DSL, defensive visitor enterOps, scene-aware `clear()`). Path A landed.
**Fix approach:** Treat `clear()` lowering as scene-aware (deprecate or lower differently when a BG tilemap is loaded). See "Anti-Patterns" in ARCHITECTURE.md once that document exists.

### Stale generated/ output — must `clean` before regenerating

**Issue:** `./gradlew :<example>:generateC` does NOT delete stale output files. If a prior build emitted `bank2.c` and the new pipeline emits only `bank1.c`, the stale `bank2.c` persists and is fed to lcc → link errors or worse, MBC drift in the final ROM. Phase 09.2-04/05 closed a CR-01 BLOCKER on this exact write-blind emittedSet gap.
**Files:**
- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/GenerateCTask.kt` — owns the file-emission write loop
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` — file-set decision
**Impact:** Per project memory, must always `clean` before regenerating C if checking output. Confusing for users; debug sessions waste hours.
**Fix approach:** `GenerateCTask` must sync its output dir to exactly the pipeline's emitted file set. Phase 09.2 added a ROM-build smoke gate (`.planning/verifier-gates.md`) but the underlying sync gap is the deeper concern.

### Pong toolchain non-determinism

**Issue:** `pong.gb` hashes differently every rebuild from the same commit even when generated C is byte-identical. Pre-existing sdcc/lcc issue; NOT a gbkt regression.
**Files:**
- `gbkt-examples/pong/build/gbkt/output/pong.gb`
- sdcc/lcc toolchain (external)
**Impact:** Per project memory `project_pong_toolchain_nondeterminism`, do NOT re-investigate; flag as PASS* in regression sweeps. Costs a small amount of cognitive overhead in every 7-target regression cycle.
**Fix approach:** External; not actionable inside gbkt. Documented as PASS* with sidecar note.

### GameIRSerializer round-trip stubs

**Issue:** `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIRSerializer.kt` has 10 partial deserialization TODOs for systems, zones, flags, itemCategories, items, containers, dropTables, puzzleObjects, collisionGroups, collisionRules — each emits `emptyList()` instead of a real round-trip.
**Files:**
- `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIRSerializer.kt:178-187`
- `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIRSerializer.kt:1197` — "full SystemIR round-trip not needed for external tool use cases"
**Impact:** IR serialization is not actually a round-trip for most domain types. Any future tool that depends on a stable on-disk IR (IntelliJ plugin asset analyzer, external optimizers) will silently drop data.
**Fix approach:** Each TODO is a small, targeted plan. Aggregate into a dedicated IR-roundtrip-completion phase when external tooling demands it.

### Phase 12.3 unwired auto-emission gaps

**Issue:** `PlatformerVisitor` (`gbkt-genre-platformer/.../codegen/PlatformerVisitor.kt`) has 4 known TODOs (lines 613, 1340, 1662, 1736) lifting `pivot_adjust` resolution into `tilemapCollision { }` builder, consolidating `gameUsesTilemapCollision()` duplication, and others. Plan 12-21 needed THREE inline cEmit fixes in `PlatformerTemplate.kt` to paper over missing auto-emission (input→playerVx, `platformer_camera_update()` call, metasprite-camera-offset). Phase 12.3 was inserted to lift these into the visitor; Phase 12.5 also closed gaps inline.
**Files:**
- `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt:613,1340,1662,1736`
- See `.planning/seeds/SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS.md`
**Impact:** New platformer-genre ports will hit the same "user must inline-cEmit the workaround" wall until the auto-emission is complete.
**Fix approach:** Resolve all 4 TODOs as `SEED-PHASE-13-PIVOT-ADJUST-AUTO-DERIVE.md` and the seed above. Defer to Phase 13 framework primitives.

### IntelliJ quickfix templates have user-facing TODOs

**Issue:** `CreateEntityQuickFix.kt`, `CreateMonsterQuickFix.kt`, `CreateCharacterQuickFix.kt` emit `// TODO: Configure ...` comments into the generated code. These are intentional user-prompt placeholders, NOT framework debt.
**Files:**
- `gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/quickfix/CreateCharacterQuickFix.kt:60`
- `gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/quickfix/CreateEntityQuickFix.kt:43,51,53`
- `gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/quickfix/CreateMonsterQuickFix.kt:57,62`
**Impact:** Low — by design.
**Fix approach:** No action. Document as intentional in the IntelliJ plugin CLAUDE.md if not already.

### IntelliJ plugin internal TODOs

**Issue:** Two real internal TODOs in `AssetPipelineDashboard.kt:182` ("Open file in editor") and `BuildLogPanel.kt:249` ("Save to file dialog"). Both are missing UI affordances.
**Files:**
- `gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/buildtools/AssetPipelineDashboard.kt:182`
- `gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/buildtools/BuildLogPanel.kt:249`
**Impact:** Low — IDE convenience features.
**Fix approach:** Deferred per `.planning/seeds/SEED-001-ide-and-tooling.md` (v2.0 milestone DX scope).

### "if/unless DSL" deferred placeholder

**Issue:** Two example games (`Metasprites.kt:56`, `SimplePhysics.kt:41`) carry `// TODO(phase-13): remove @Suppress after if/unless DSL lands` markers — the games suppress detekt warnings that would disappear when a proposed `if`/`unless` DSL primitive ships.
**Files:**
- `gbkt-examples/metasprites/src/main/kotlin/io/github/gbkt/examples/metasprites/Metasprites.kt:56`
- `gbkt-examples/simple-physics/src/main/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysics.kt:41`
**Impact:** Cosmetic — DSL surface gap.
**Fix approach:** Phase 13 framework-primitives candidate.

## Known Bugs

### Phase 12 G3 — palette inversion (active blocker)

**Symptoms:** After Phase 12.8 W3 conditional `-keep_palette_order` flag activated, anchor-5 re-shoot showed COLOR INVERSION: "all colors are inverted, next level still broken, second level character still sunk." Root cause: A6-CONFIRMED palette-wiring gap — runtime never calls `set_bkg_palette` on per-zone `_zone_<id>_tileset_palettes[16]` arrays; only `_gbkt_default_bg_pal` is uploaded via `main.c:698` (GBC-gated). After `-keep_palette_order`, indices reference palette index-0 = near-black per PLTE, but BG palette RAM still holds cream from default → visual inversion.
**Files:**
- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ConvertZoneTilesetsTask.kt:288-298` (flag site, W3 REVERTED 2026-05-27)
- `gbkt-backend-gbdk/.../codegen/pipeline/GBDKPipelineV2.kt:698` (default palette upload site)
- `.planning/phases/12.8-grass-tileset-white-pixels-diagnostic/12.8-07-SUMMARY.md` — binding-gate BLOCKED record
- `.planning/phases/12.8-grass-tileset-white-pixels-diagnostic/12.8-10-SUMMARY.md` — terminal close PARTIAL-BLOCKED
**Trigger:** Building `:gbkt-examples:platformer-template:buildRom` with `-keep_palette_order`; visible in anchor-5 capture sequence.
**Workaround:** Phase 12.8 W3 runtime change REVERTED post-close 2026-05-27. ROM back to pre-12.8 visual baseline (white pixels on grass — known G3 RED — but no inverted colors). `isIndexedPng()` helper retained at `ConvertZoneTilesetsTask.kt:480`; `World1TilesetGrassEncodingTest` `@Disabled` pending Phase 12.9.
**Routing:** Phase 12.9 — palette-inversion-asset-pipeline (NEXT phase; G3+G4 owners).

### Phase 12 G4 — nextLevel scene-transition VRAM-clear defect

**Symptoms:** anchor-5 sequence `00-last-gameplay`, `01-nextlevel-flip`, `02-nextlevel-card` all show effectively the same screen — BG persists, sprites persist, character at same x,y across all three frames. The nextLevel scene is not clearing/replacing BG state.
**Files:**
- `gbkt-backend-gbdk/.../codegen/pipeline/GBDKPipelineV2.kt` (`nextLevel_enter` codegen)
- Reference: `.planning/STATE.md` lines 53-54
**Trigger:** Cross-scene navigation in platformer-template ROM.
**Workaround:** None. Hypothesis: `nextLevel_enter` is missing `hideSprites()` + `fill_bkg_rect()` clear, OR card-draw is rendering on top of stale state.
**Routing:** Phase 12.9 (carry-forward scope expansion per STATE.md).

### Cross-scene "0F" / "F" text artifact

**Symptoms:** Persistent "0F" text artifact in bottom-right quadrant of anchor-5/00, 01, 02 PNGs. May pre-date Phase 12.8 (Phase 12.7's H3 close did not touch scene-transition VRAM).
**Files:** Unknown — investigate whether it's pre-existing through anchor history.
**Trigger:** Visible across all 3 anchor-5 frames in platformer-template.
**Workaround:** None. Diagnosed as NEUTRAL (orthogonal) in Phase 12.8 — next-level card uses static `title-screen.png` RGB path; W3 conditional flag skipped this surface.
**Routing:** Phase 12.9 scope (investigate during palette-wiring fix).

### Player metasprite levitating 1-2 px above ground tiles

**Symptoms:** In `anchor-5/00-last-gameplay.png`, player metasprite renders 1-2 pixels BELOW the visual top of the ground tile (foot pixels sunk). Phase 12.7 H3 fix (grounded-blind trigger) closed; symptom PERSISTS.
**Files:**
- `gbkt-genre-platformer/.../PlatformerVisitor.kt:buildVerticalFootProbe` (snap-to-tile-top arithmetic)
- Player metasprite PNG asset (potentially has 1-2 px gutter)
**Trigger:** Visible in platformer-template anchor-5 captures.
**Workaround:** None — CARRIED-AS-NEW-SEED per Plan 12.8-07.
**Routing:** `.planning/seeds/SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK.md` — Phase 13 framework primitives or dedicated sibling phase.

### Grass tilemap white-pixel artifacts (PARTIALLY RESOLVED)

**Symptoms:** Grass tilemap renders with stray white pixels through tile cells that should display solid grass. Visible only in world1 area renders (anchor-1 gameplay and anchor-5 near-end). Phase 12.8 closed the asset-pipeline boundary (W3 conditional flag + W4 emission invariant + W5 sweep); runtime palette-wiring left open.
**Files:**
- `gbkt-examples/platformer-template/res/graphics/world1-tileset.png` — source PNG
- `gbkt-gradle-plugin/.../ConvertZoneTilesetsTask.kt` — png2asset invocation flags
- `.planning/seeds/SEED-PHASE-12-GRASS-TILEMAP-WHITE-PIXELS.md` — Status: PARTIALLY RESOLVED 2026-05-27
**Trigger:** Visible in platformer-template anchor-1 and anchor-5 captures.
**Workaround:** None.
**Routing:** Phase 12.9 (runtime palette-wiring closes this).

## Security Considerations

### Not applicable — single-developer Game Boy ROM compiler

gbkt is a build-time DSL compiler that produces .gb ROM files. There is no network surface, no user input handling, no credential storage, no third-party data ingestion at runtime. Security considerations are limited to:

- **Asset PNG parsing:** Java ImageIO + bundled `png2asset` external binary. PNG parser CVEs in the JVM are a downstream concern.
- **External binary invocation:** `png2asset` (GBDK-2020) and `lcc` (sdcc-based) are invoked via Gradle exec. Files are paths under the project tree; no shell injection vectors observed.

No active security debt.

## Performance Bottlenecks

### Asset pipeline — `ConvertZoneTilesetsTask` shared-tileset duplication

**Problem:** When 2+ zones reference the same source PNG (e.g. `world1Area1Zone` AND `world1Area2Zone` both use `world1-tileset.png`), the task invokes `png2asset` TWICE and emits two byte-identical `_zone_<id>_tileset.c` files. The tilemap data is correctly per-zone, but the tileset payload duplicates (~1-3KB extra ROM per shared pair).
**Files:**
- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ConvertZoneTilesetsTask.kt`
- `gbkt-backend-gbdk/.../pipeline/GBDKPipelineV2.kt` (bank allocator)
- `gbkt-lang/.../ZoneBuilder.kt`
**Cause:** No `-source_tileset` dedup pass; each zone goes through its own `png2asset` invocation.
**Improvement path:** Phase 13 candidate per `.planning/seeds/SEED-PHASE-12-SHARED-TILESET.md`. Touch `ConvertZoneTilesetsTask` + `GBDKPipelineV2` allocator + `ZoneBuilder` + `ZoneIR` + `game_metadata.json` schema.

### Per-zone tilemap bank co-location

**Problem:** Phase 12.2 emits all `_zone_<id>_tilemap.c` files with `#pragma bank 2` (shared bank). `BankingAnalysisPass` guards against cumulative overflow at 14336 bytes. Phase 12's 5 platformer-template zones sum to ~6480 bytes — well under threshold, so no fire today; fires when more zones land.
**Files:**
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BankingAnalysisPass.kt`
- `gbkt-backend-gbdk/.../pipeline/GBDKPipelineV2.allocateZoneBanks` (mirror)
**Cause:** No per-zone bank allocation strategy yet.
**Improvement path:** Dormant; see `.planning/seeds/SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS.md`. Fires only when the cumulative-overflow guard trips.

## Fragile Areas

### Visual-evidence-rule fragility — variable assertions can mask visual regressions

**Component:** Phase 07.4 round-2 SC-4 ("track tilemap is visible") was verified via `assertVariable("_current_tileset_id", 1)` — the variable was correctly written at scene-enter but a user-authored `clear()` lowered to `cls()` wiped the visual outcome before the player saw the screen. The variable assertion was technically GREEN; the runtime ROM never rendered the track. Bug took 5 plans (15-18) to surface and was caught only by user UAT in round 4.
**Files:**
- `CLAUDE.md` — "Verification Methodology — Visual Evidence Rule" (codified post-incident)
- `.planning/phases/07.4-sport-genre-codegen-fix-inserted/07.4-UAT.md` — historical evidence
- `.planning/phases/07.4-sport-genre-codegen-fix-inserted/07.4-19-PLAN.md` — JVM-tier RED tests
- `.planning/phases/07.4-sport-genre-codegen-fix-inserted/07.4-20-PLAN.md` — codegen fix
**Why fragile:** Visual SCs (anything shaped "X is visible on screen") are NOT structurally guarded against passing-by-coincidence at the JVM tier. The Phase 07.4 bug class produced a 5-plan debug chain.
**Safe modification:** Any visual SC MUST capture a runtime screenshot via MCP `emulator_screenshot(path)` and place it under the phase's `evidence/` directory. Codegen GREEN is necessary but not sufficient — per `feedback_visual_evidence_for_visual_truths` (project memory).
**Test coverage gap:** No automated mechanism enforces "this SC has a PNG"; relies on planner/verifier discipline. Phase 12 used 5 anchors as a binding contract per 12-UAT.md.

### Scope-level grep gates can mask per-function regressions

**Component:** `grep -c cls() bank1.c` cannot distinguish `race_enter` from `title_enter` — if `title_enter` has back-compat `cls()`, the count masks a regression in `race_enter`.
**Files:** Affects all phase verifications that use file-level greps.
**Why fragile:** Per-function invariants need brace-walk (awk) extraction WITHIN scope. Plan 07.4-23 Task 1 step 3 demonstrates the awk pattern.
**Safe modification:** When writing invariant tests for per-function shape, extract the function body via awk brace-walk and grep WITHIN the body — never file-level.
**Test coverage gap:** Not enforced by any rule; relies on planner discipline.

### Phase 12.x palette/VRAM-clear bug class — multi-phase debug chain

**Component:** Palette wiring (per-zone `set_bkg_palette`), scene-transition VRAM clear, png2asset palette index ordering, GBC vs DMG conditional gates. The bug class has produced a 5-phase chain: 12.4 (sprite pipeline) → 12.6 (level-switch codegen) → 12.7 (player levitating) → 12.8 (grass tileset diagnostic) → 12.9 (palette inversion + VRAM-clear).
**Files:**
- `gbkt-backend-gbdk/.../pipeline/GBDKPipelineV2.kt:698` — default BG palette upload (GBC-gated)
- `gbkt-gradle-plugin/.../ConvertZoneTilesetsTask.kt:288-318,480` — png2asset args + `isIndexedPng()` helper
- `gbkt-backend-gbdk/.../codegen/visitor/ZoneCodegen.kt` — zone enter codegen ordering
- `.planning/phases/12.4-...`, `.planning/phases/12.6-...`, `.planning/phases/12.7-...`, `.planning/phases/12.8-...`, `.planning/phases/12.9-...`
**Why fragile:** Each "fix" papered over one layer of a stacked defect. Phase 12.8 W3 conditional flag layered color inversion on top of existing breakage rather than fixing it. Per `feedback_dont_pay_to_confirm_obvious` (project memory) — when W1 diagnostic evidence already says the cheap fix is insufficient, ROUTE-TO from the start.
**Safe modification:** Phase 12.9 must address palette wiring AT zone-load codegen ordering, NOT at the png2asset boundary alone. Re-shoot anchor-5 + anchor-1 PNGs post-fix; bind G3 + G4 verdicts.
**Test coverage gaps:** No JVM-tier emission test for `set_bkg_palette(0u, 1u, _zone_<id>_tileset_palettes)` ordering at zone-load. Phase 12.9 must add one before the codegen fix lands.

### Banking + BANKED calling convention is a structural minefield

**Component:** GBDK's banking system: setBank(N), returnToHome(), BANKED tags, splitByBank, forward declarations, cross-bank calls via SWITCH_ROM(N), HOME helpers (`_bkg_tiles_load_banked`).
**Files:**
- `gbkt-backend-gbdk/.../codegen/pipeline/GBDKPipelineV2.kt:972-980,1882+` (helper gating + emission)
- `gbkt-backend-gbdk/.../codegen/postprocess/` (splitByBank, processBankedLine, processPreBankLine)
- `.planning/seeds/SEED-014-banks-bkg-tiles-load-banked-gating.md`
**Why fragile:** Helper gating like `hasSportRacing && bank > 1` (Phase 11 INV-2 finding) was overly narrow — the correct gate is `gameIR.zones.any { it.bankOverride != null || allocateZoneBanks(...) > 1 }`. The fix touches every game with banked zones (pong, dungeon, racer, banks, future ports), so blast radius is WIDE.
**Safe modification:** Per `feedback_route_to_proper_phase_when_blast_radius_is_wide`, banking changes MUST go through discuss-phase → research → plan cycle. Phase 11.1 was the inserted closer subphase.
**Test coverage gap:** JVM-tier sentinel `BanksEmissionTest > INV-2 bkg_tiles_load_banked wrapper in main_c has SWITCH_ROM sequence` locks one invariant; no fleet-wide audit of "every game with banked zones has the SWITCH_ROM wrapper emitted."

### `zone(id: String)` magic-string violation

**Component:** `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/GameBuilder.kt:480` — `fun zone(id: String, block: ZoneBuilder.() -> Unit): ZoneRef` accepts a magic string. Usage in current ports duplicates the property name: `val world1Area1 = zone("world1Area1") { ... }`. Violates Project Rule #1 (`feedback_no_magic_strings`).
**Files:**
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/GameBuilder.kt:480`
- Every game using `zone()`: dungeon, explorer, rpg-lite, banks, racer, platformer-template, simple-physics — plus all in-tree test fixtures.
- `.planning/seeds/SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION.md`
**Why fragile:** Wide blast radius. Touches `gbkt-lang` (builder + new `ZoneDelegate.provideDelegate`), `gbkt-engine` (IR `Zone` data class — id field semantics), and EVERY game.
**Safe modification:** Phase 13 framework-primitives candidate; coordinated rewrite via delegate pattern `val world1Area1 by zone { ... }`. NOT inline-fixable during a port-specific phase.

### CParenExpr missing from C AST — precedence trap

**Component:** Plan 12.7-04 emitted `_player_y = foot_tile_row << 3u - 24u << 4u;` which C parses as `foot_tile_row << (3u - 24u) << 4u` due to C11 operator precedence (`+`/`-` higher than `<<`/`>>`). Player glued to top of screen.
**Files:**
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CExpr.kt`
- `gbkt-backend-gbdk/.../codegen/emitter/CEmitter.kt:emitExpr` (every CBinaryExpr printer site)
- `.planning/seeds/SEED-PHASE-X-CPAREN-EXPR-IN-C-AST.md`
**Why fragile:** Every CBinaryExpr printer site in the backend is vulnerable to the same precedence trap. Plan 12.7-11 used Path A (intermediate-vars workaround) to scope the fix locally; Path B (add `CParenExpr` + precedence-aware paren emission) is deferred.
**Safe modification:** Path B is backend-wide; ~50+ test fixtures will need re-snapshotting. Schedule as proactive audit phase, NOT inline during a feature plan.
**Test coverage gap:** No invariant tests guarding against precedence-trap re-emergence; relies on visual evidence + downstream UAT.

## Scaling Limits

### ROM size — current Phase 12 substrate

**Current capacity:** Phase 12.1 / 12.6 builds produce 64–65,536 byte ROM with 4 banks; max bank utilization 37.4%. 5 platformer-template zones total ~6480 bytes in shared bank 2.
**Limit:** 16,384 bytes per bank (GBDK ROM bank size); cumulative-overflow guard at 14,336 bytes (2KB safety margin for headers / prologue) in `BankingAnalysisPass`.
**Scaling path:** Per-zone bank allocation (`.planning/seeds/SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS.md`); shared-tileset dedup (`SEED-PHASE-12-SHARED-TILESET.md`). MBC5 supports 8MB; current builds are ~64KB.

### Bank count

**Current capacity:** All 8 example games build in ≤4 banks with the existing `BankingAnalysisPass` FFD bin-packing.
**Limit:** MBC1 = 16 banks (max 256KB), MBC5 = 512 banks (max 8MB).
**Scaling path:** No active concern. `BankingAnalysisPass` is already FFD-based per Phase 04-03.

### IR serialization round-trip

**Current capacity:** Primitive subset of GameIR round-trips. Systems, zones, flags, items, containers, drop tables, puzzle objects, collision groups, collision rules all deserialize as `emptyList()`.
**Limit:** External tools that depend on a stable IR JSON cannot rely on completeness.
**Scaling path:** Close GameIRSerializer TODOs (see Tech Debt above) when external tooling lands.

## Dependencies at Risk

### png2asset (GBDK-2020)

**Risk:** External binary bundled by user via `GBDK_HOME` env var. Phase 12.4 + 12.5 had to debug png2asset metasprite layout (3col×2row vs 2col×3row), flag semantics (`-keep_palette_order`, `-source_tileset`), and IHDR color-type assumptions. Phase 12.8 added an `isIndexedPng()` IHDR guard at `ConvertZoneTilesetsTask.kt:480` precisely because png2asset behavior differs based on PNG color type.
**Impact:** Any png2asset upgrade can shift output bytes; byte-identity baselines (Phase 12.4-07 elephant/tiger fixtures) lock current behavior but not future versions.
**Migration plan:** Maintain explicit byte-identity baselines per game; gate `-keep_palette_order` and similar flags behind PNG-format detection.

### lcc / sdcc (GBDK-2020)

**Risk:** Non-deterministic output for some pathological cases (pong toolchain non-determinism). Linker order and SDCC version drift can shift ROM bytes without source change.
**Impact:** Regression sweeps must accommodate PASS* for pong; cannot use byte-identity as a regression gate for that ROM.
**Migration plan:** None — external. Document PASS* via sidecar; do not invest in re-investigation.

### Coffee-GB (embedded emulator)

**Risk:** UAT harness uses Coffee-GB via `gbkt-emulator`. Pre-enter VRAM capture timing is unreliable — Phase 12.6 debug Cycle 2 lesson: "Coffee-GB capture timing is unreliable, Cycle 2 lesson" — verification gates used direct MCP-driven capture against reference ROMs instead of UAT test-harness PNGs.
**Impact:** UAT screenshot tests can flake on capture timing; anchor4 6.60% threshold required retune (routed to Phase 12.10).
**Migration plan:** Phase 12.10 (`uat-test-harness-capture-timing`) — Coffee-GB pre-enter VRAM capture + anchor4 threshold retune.

## Missing Critical Features

### Phase 12 G3/G4 visual closure blocking Phase 12 ship

**Problem:** Phase 12 currently `status: partial`. G1+G2 closed by 12.6/12.7; G3+G4 routed to 12.9. Phase 12 cannot ship until 12.9 closes both gates.
**Blocks:** Phase 12 SHIP, advancing the v1.0 ROADMAP (46/60 phases done; Phase 12 is the platformer-template reference port and a milestone-critical example game).

### `oneWayThreshold(M)` tile collision (deferred)

**Problem:** Phase 12 ships solid-only tilemap collision (`platformerPhysics { solidThreshold(N) }`). Classic platformers (Mario, Mega Man, Castlevania) need ONE_WAY tiles (solid from above, passable from below).
**Blocks:** Any future port requiring traversable platforms.
**Reference:** `.planning/seeds/SEED-PHASE-12-ONE-WAY-TILE.md` — Phase 13 IFF a future port surfaces real need.

### Phase 06.6 audio + GBC color completions

**Problem:** Phase 06.6 is still listed as INCOMPLETE in the ROADMAP — SC #6 (deprecation escalation WARNING → ERROR), SC #10 (AudioMixer full priority mixing), SC #11 (hUGETracker `.uge` → C conversion), SC #12 (tracker-format BGM ADV-03).
**Blocks:** Any game wanting tracker-format music; full DSL deprecation discipline.
**Reference:** ROADMAP `Phase 06.6` line — work remains.

### IDE & Tooling (v2.0 milestone)

**Problem:** Phase 09 (IDE & Tooling) was removed from v1.0 ROADMAP per `/gsd-phase --remove 09` on 2026-05-13. v1.0 scope is framework correctness; DX is v2.0.
**Blocks:** Mainstream developer adoption — without rich IDE feedback, the framework reduces to Kotlin syntax + Gradle from the user's POV.
**Reference:** `.planning/seeds/SEED-001-ide-and-tooling.md` — auto-surfaces during `/gsd-new-milestone` for v2.0.

## Test Coverage Gaps

### Visual-truth SCs without screenshot evidence

**What's not tested:** Any visual SC NOT bound to an MCP `emulator_screenshot()` artifact in the phase's `evidence/` directory. Phase 07.4 round-2 SC-4 was the precedent failure.
**Files:**
- `.planning/phases/*/evidence/` — convention only; no enforcement
- `CLAUDE.md` — "Verification Methodology — Visual Evidence Rule" (codified)
**Risk:** Visual regressions ship past JVM-GREEN verdicts.
**Priority:** HIGH — codified into project methodology after a 5-plan debug chain.

### `set_bkg_palette` per-zone emission

**What's not tested:** No JVM-tier emission test guards that `set_bkg_palette(0u, 1u, _zone_<id>_tileset_palettes)` is emitted at zone-load codegen ordering for non-default-palette zones.
**Files:**
- `gbkt-backend-gbdk/.../codegen/visitor/ZoneCodegen.kt`
- `gbkt-backend-gbdk/.../codegen/pipeline/GBDKPipelineV2.kt`
**Risk:** Phase 12.8 G3 failure root cause is exactly this missing invariant.
**Priority:** HIGH — must land in Phase 12.9 before the codegen fix.

### Cross-scene VRAM-clear at scene transition

**What's not tested:** No JVM-tier test guards that `nextLevel_enter` (and similar level-switch scene enters) emit `hideSprites()` + `fill_bkg_rect()` (or equivalent VRAM clear) before card-draw.
**Files:**
- `gbkt-backend-gbdk/.../codegen/visitor/SceneVisitor.kt`
- `gbkt-backend-gbdk/.../codegen/pipeline/GBDKPipelineV2.kt`
**Risk:** Phase 12 G4 failure (`01-nextlevel-flip` = `00-last-gameplay` = `02-nextlevel-card`).
**Priority:** HIGH — Phase 12.9 scope.

### IntegrationTest baseline RED (14 failures)

**What's not tested:** IntegrationTest suite has 14 failures pre-existing from a `SceneIR.<init>` signature change. No phase has owned the test-fixture update.
**Files:**
- IntegrationTest fixtures (location TBD)
- `gbkt-ir/.../SceneIR.kt`
**Risk:** Any new failure that should land in IntegrationTest will be invisible against a RED baseline.
**Priority:** MEDIUM — does not block buildRom acceptance gate but skews signal-to-noise.

### Per-function invariant tests via brace-walk extraction

**What's not tested:** Most invariant tests use file-level grep (`grep -c cls() bank1.c`) which cannot distinguish caller scope. Per-function emission shape needs awk brace-walk.
**Files:** Affects most `*EmissionTest.kt` files.
**Risk:** Per-scope regressions hide behind file-level passing counts.
**Priority:** MEDIUM — codified pattern exists (Plan 07.4-23 Task 1 step 3) but adoption is per-test.

### `BANKED` calling-convention sweep

**What's not tested:** No fleet-wide audit that every function definition in bank N (N>0) carries `BANKED`. The `processBankedLine()` pattern fix landed reactively after MBC5-runtime crashes.
**Files:**
- `gbkt-backend-gbdk/.../postprocess/splitByBank.kt`
- Generated `bankN.c` files across all examples
**Risk:** Future codegen changes (new return types, new prefixes like `static const`) can re-introduce the regression class.
**Priority:** MEDIUM — JVM grep over all generated bankN.c files would close this.

### IntelliJ plugin features

**What's not tested:** `AssetPipelineDashboard.kt:182` "Open file in editor" and `BuildLogPanel.kt:249` "Save to file dialog" carry user-facing TODOs with no tests.
**Files:** As above.
**Risk:** Low — IDE convenience.
**Priority:** LOW — bundled with v2.0 DX milestone.

## Architectural Concerns

### Detekt exclusions encode tech-debt acceptance

**Decision:** `detekt.yml` excludes LongMethod / TooManyFunctions / LongParameterList / CyclomaticComplexMethod / NestedBlockDepth for `**/codegen/**`, `**/ir/**`, `**/dsl/**`, `**/rpg/**`, `**/entity/**`, etc. `MagicNumber` is globally disabled. `UnusedPrivateMember` and `UnusedPrivateProperty` globally disabled.
**Files:** `detekt.yml` (root)
**Rationale (per CLAUDE.md):**
- DSL ergonomics over code metrics — DSL is user-facing
- IR as boundary — both sides may be complex internally
- Domain-driven modeling — RPG types have many fields by nature
- Generated code is different — human readability matters less than correctness
**Concern:** These exclusions are PRINCIPLED but they accumulate untested complexity. The `codegen/` exclusion covers files like `GBDKPipelineV2.kt` which sit at the center of every Phase 12.x debug chain. Without metric pressure, complexity grows monotonically.
**Mitigation:** New code in excluded packages should still be reviewed for clarity, even if tools don't flag it. Plan-level invariant tests partly substitute for static analysis here.

### Experimental `battleEngine()` DSL

**Component:** `CLAUDE.md` (line 84) and `context/DSL_REFERENCE.md` (line 1734) state: "Use `battle()` for v1 combat. `battleEngine()` is experimental and will be revised in a future release."
**Files:**
- `gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/RpgExtensions.kt` — DSL surface
- `.planning/phases/07-uat-gameplay-validation/07-RESEARCH.md:505` — "experimental, not used in any example game. Not in UAT scope."
- `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/PongPipelineTest.kt:384` — asserts no `battle_engine` in non-RPG output
**Risk:** API will be revised; downstream consumers should not rely on the current shape. No example game uses it.

### Worktree drift quirks

**Component:** `isolation="worktree"` agent dispatch (Claude Code) can leak agent commits onto the parent branch directly, leak test output to the main checkout as untracked files, and let orchestrator CWD drift into a worktree.
**Files:** `.claude/worktrees/`
**Risk:** Per project memory `feedback_claude_code_worktree_drift_quirks` — verify with `git reflog` + hash-compare before merge; never `git branch -D worktree-agent-*` until merge commit is visible; rescue orphaned branches via `git fsck --unreachable`. NOT a code defect, but operationally fragile.

---

*Concerns audit: 2026-05-27*
