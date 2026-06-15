# Phase 16: Seed Triage - Research

**Researched:** 2026-06-12
**Domain:** Evidence-backed triage of 44 seeds + 3 folded todos against current master (gbkt v0.1.1 Hardening)
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01:** `TRIAGE.md` in the phase dir is the canonical record — one row per entry: disposition, evidence link, fix-commit/screenshot ref, routing (for open seeds). This is the published confirmed-open list (Success Criterion 3).
**D-02:** Each seed file additionally gets a small frontmatter stamp (`triage_disposition:` + pointer to TRIAGE.md). The stamp is a pointer, not a duplicate record.
**D-03:** Seeds closed by Phase 16 (VERIFIED-ALREADY-FIXED, INVALID) are archived out of `.planning/seeds/` at phase close (e.g., `.planning/seeds/archive/`). `seeds/` becomes the live confirmed-open work queue for Phases 19–21.
**D-04:** RE-DEFERRED seeds move at phase close to `.planning/backlog/v0.2.0/` as full seed files, plus a one-line index entry under REQUIREMENTS.md "Future Requirements".
**D-05:** The 3 folded todos get full TRIAGE.md rows with the same disposition taxonomy and evidence bar as the 44 seeds (47 entries total).
**D-06:** Bare commit attribution is NEVER sufficient for any disposition. VERIFIED-ALREADY-FIXED requires executable evidence at HEAD: a green test run covering the seed's specific failure mode, or generated-C inspection at HEAD showing the defect pattern absent.
**D-07:** CONFIRMED-OPEN requires a repro at HEAD: a failing probe/emission test, a defect screenshot, or generated-C inspection showing the defect present. Each repro is deliberately the RED half of the receiving fix phase's RED→GREEN cycle — triage pre-builds the fix phases' failing tests.
**D-08:** Visual-seed verdicts go through ONE binding batch human review gate: agents capture all visual-seed screenshots at HEAD, assemble a single review document (seed → screenshot → proposed verdict → reference image), and the user does one review pass before TRIAGE.md verdicts are finalized. Agent pixel-judgment alone never closes a visual seed.
**D-09:** Evidence artifacts (screenshots, test outputs, generated-C excerpts) live in `.planning/phases/16-seed-triage/evidence/`, organized per seed ID. The existing `.planning/seeds/evidence/platformer-gbc-inverted-colors-2026-06-04.png` is referenced or moved in. Links remain valid after seed files archive.
**D-10:** Phase 16 issues exactly four dispositions: VERIFIED-ALREADY-FIXED, RE-DEFERRED, INVALID (not-a-bug, same evidence bar as verified-fixed), and CONFIRMED-OPEN (with a routing column naming the receiving fix phase 19/20/21 — or 17/18 where an item belongs there). FIXED appears in TRIAGE.md only later, as fix phases update rows. No code fixes in Phase 16, no exceptions for trivial seeds.
**D-11:** TRIAGE.md findings are authoritative over the pre-assigned FIX-01..06 seed lists. At phase close, REQUIREMENTS.md/ROADMAP.md get a reconciliation pass.
**D-12:** The six seeds already deferred by REQUIREMENTS.md fast-path to RE-DEFERRED: SEED-001, SEED-018, SEED-019, SEED-024, SEED-RAW-C-CODEGEN-AST-MIGRATION, SEED-PHASE-X-CPAREN.
**D-13:** One shared substrate pass at triage start: a single clean, serial Gradle invocation builds all 7 example ROMs + runs the full JVM suite. NEVER parallel `gradle clean` (Kotlin daemon collision).
**D-14:** The substrate-build commit SHA is pinned and recorded in TRIAGE.md; all evidence is attributed to that SHA.
**D-15:** Stale metasprite/metasprites-stress byte-identity baselines: ROM hashes + screenshots are captured during the substrate pass but promoted to official baselines ONLY after the batch visual review gate approves the screenshots.
**D-16:** After the substrate pass, per-seed work runs as parallel cluster agents (metasprites, banks, platformer, DSL/tooling misc) reading the shared artifacts. Agents NEVER run `clean`/`buildRom` themselves; emulator screenshot capture is serialized through the gbkt-emulator MCP server; agent prompts must explicitly instruct use of Serena MCP tools.

### Claude's Discretion

- Exact TRIAGE.md column layout and row schema.
- Cluster boundaries for agent assignment (the four named clusters are a guide, not a contract).
- Archive directory naming (`.planning/seeds/archive/` vs milestone dir) — pick one and be consistent.
- Whether SEED-014's mandated `BanksEmissionTest.kt` INV-2 sentinel run happens in the substrate pass or the banks cluster agent.

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| TRIAGE-01 | Every seed in `.planning/seeds/` carries a terminal disposition (FIXED, VERIFIED-ALREADY-FIXED, or RE-DEFERRED) backed by evidence (commit hash, green test run, or screenshot at HEAD) | Seed corpus fully inventoried: 44 seeds, 3 folded todos, 6 fast-path RE-DEFERREDs, verified test infrastructure available; substrate pass design documented |
| TRIAGE-02 | Visual-symptom seeds are closed only with runtime screenshot evidence at HEAD (Visual Evidence Rule), never variable assertions alone | 8–10 visual-symptom seeds identified; MCP emulator available at HEAD; shadow JAR present; gbcMode=true requirement documented for platformer and metasprites; batch review gate (D-08) designed |
| TRIAGE-03 | `.planning/seeds/` is empty at milestone close; re-deferred seeds move to a tracked v0.2.0 backlog record | Archive (D-03) and backlog (D-04) file-movement protocol defined; D-03/D-04 are phase-close steps; TRIAGE-03 "seeds/ empty" is milestone-close criterion, Phase 16 contributes by archiving closed seeds |
</phase_requirements>

---

## Summary

Phase 16 is a pure triage and evidence-gathering phase — no code fixes are produced. Its output is a canonical `TRIAGE.md` disposition table (47 rows: 44 seeds + 3 folded todos), per-seed evidence artifacts under `.planning/phases/16-seed-triage/evidence/`, a batch visual review document for human sign-off, and the file-system cleanup actions (archive closed seeds, move RE-DEFERREDs to `.planning/backlog/v0.2.0/`).

The phase executes in four waves: (W0) setup — create TRIAGE.md skeleton, evidence directory, and fast-path the 6 RE-DEFERRED seeds from REQUIREMENTS.md; (W1) substrate pass — a single serial `./gradlew` invocation builds all 7 example ROMs and the full JVM suite, pins a SHA; (W2) cluster-parallel triage — four agent clusters (metasprites, banks, platformer, DSL/tooling misc) read the shared substrate artifacts and draft per-seed dispositions and repro probes; (W3) batch visual review — all visual-seed screenshots assembled into one review document, human approves verdicts before TRIAGE.md rows are finalized; (W4) phase close — stamps seed files (D-02), moves files to archive/backlog dirs (D-03/D-04), reconciles REQUIREMENTS.md/ROADMAP.md (D-11).

**Primary recommendation:** Run the substrate pass first, in isolation, as a single serial Gradle command. Pin the SHA. Then dispatch cluster agents with read-only access to the substrate artifacts. Never let cluster agents invoke `clean` or `buildRom` — all evidence originates from the pinned substrate artifacts.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Substrate ROM build | Build / CI tier (Gradle) | — | Must be a single serial invocation; no parallelism (daemon collision risk) |
| JVM test suite execution | Build / CI tier (Gradle test runner) | — | Covers emission-level seeds via existing tests |
| Generated-C inspection | Developer tools (grep/awk on build artifacts) | — | Verifies emission shape without ROM execution |
| Visual-seed screenshot capture | MCP emulator tier (gbkt-emulator MCP server) | — | Only tool that provides runtime screenshots; must be serialized |
| Seed disposition record | `.planning/` docs tier | — | TRIAGE.md is the output artifact; not code |
| Archive / backlog movement | File-system tier | — | Phase-close step; seeds/ becomes live confirmed-open queue |

---

## Seed Corpus — Complete Inventory

### Total entries: 47 (44 seeds + 3 folded todos)

**6 fast-path RE-DEFERREDs (D-12) — no verification work required:**

| ID | Fast-path rationale |
|----|---------------------|
| SEED-001 | REQUIREMENTS.md Future Requirements IDE-02 — v2.0 trigger |
| SEED-018 | REQUIREMENTS.md Future Requirements RPG-01 — archived dungeon/explorer games |
| SEED-019 | REQUIREMENTS.md Future Requirements IDE-01 — IntelliJ plugin test infra lift |
| SEED-024 | REQUIREMENTS.md Future Requirements IDE-01 — same module as SEED-019 |
| SEED-RAW-C-CODEGEN-AST-MIGRATION | REQUIREMENTS.md Future Requirements ARCH-01 — own architecture phase |
| SEED-PHASE-X-CPAREN | REQUIREMENTS.md Future Requirements ARCH-02 — ~50+ fixture re-snapshots |

Move immediately to `.planning/backlog/v0.2.0/` at phase open (before substrate pass). [ASSUMED] that no verification work is needed once the REQUIREMENTS.md Future Requirements entry exists as the rationale record.

---

### Seeds with "RESOLVED/CLOSED" status in their own text

These carry an explicit resolution statement in their seed file. Under D-06, bare commit attribution is insufficient — executable evidence at HEAD is still required. However, the verification burden is lighter (confirm the resolution holds, not diagnose from scratch):

| Seed | Self-reported status | Likely disposition | Evidence needed |
|------|---------------------|-------------------|-----------------|
| SEED-PHASE-12-CONVERTSPRITESTASK-AUDIT | "RESOLVED Phase 12.4 — all 4 stub paths replaced with fail-fast GradleException" | VERIFIED-ALREADY-FIXED | `./gradlew :gbkt-examples:platformer-template:generateC` exits 0 AND generated C references real sprite data (no `/* Stub sprite data */` comment) |
| SEED-PHASE-12-GRASS-TILEMAP-WHITE-PIXELS | "RESOLVED 2026-06-02 by Phase 12.9" | VERIFIED-ALREADY-FIXED | GBC-mode screenshot of platformer-template world1 area shows no white-pixel artifacts in grass tilemap |
| SEED-PHASE-12-RETROACTIVE-BANKS-AUDIT | "Trivially satisfied by D-01" | VERIFIED-ALREADY-FIXED | Assert banks example `./gradlew :gbkt-examples:banks:generateC` exits 0 and generated C references tileset data correctly |
| SEED-PHASE-12-TITLE-ZONE-PATH-A-SCENE-RENDER-DEFECT | "closed (2026-05-23) via Plans 12.2-11/12/13" | VERIFIED-ALREADY-FIXED | GBC-mode screenshot of platformer-template title screen shows no title-zone render defect; OR generated-C inspection confirms zone-path-A scene-enter emission is correct |

[ASSUMED] that "RESOLVED" status in seed files is accurate; verification confirms it still holds at HEAD.

---

### Seeds with ambiguous closure status (requires re-verification)

From `.planning/research/ARCHITECTURE.md` analysis — these were noted as possibly-closed by Phases 12.x–13.8:

| Seed | Why ambiguous | Evidence path |
|------|--------------|---------------|
| SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED | "Active blocker" at seed creation; Phases 12.6/12.7 physics codegen fixed player grounding | GBC screenshot: player standing on ground tile, not floating mid-tile. `emulator_start gbcMode=true` + traverse right + screenshot |
| SEED-PHASE-12-PLAYER-METASPRITE-RENDER | "Deferred — placeholder square"; Phase 12.3/12.4 ConvertSpritesTask fix shipped real sprites | GBC screenshot: player renders as duck sprite, not dark checkerboard square |
| SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE-BG-AND-OBJ | "OPEN" as of Phase 13.4; Phase 13.8 "palette/sprite codegen hardening APPROVED" — may have fixed it | GBC screenshot: platformer-template colors correct vs inverted. Existing reference: `.planning/seeds/evidence/platformer-gbc-inverted-colors-2026-06-04.png` (the "before" state); a new screenshot at HEAD is required |
| SEED-014-banks-bkg-tiles-load-banked-gating | `hasZoneSceneBinder` guard already added at `GBDKPipeline.kt:1164-1168` (partial fix); INV-2 sentinel still RED at Phase 13.2 base | Run `BanksEmissionTest` INV-2 sentinel at HEAD — if GREEN, VERIFIED-ALREADY-FIXED; if RED, CONFIRMED-OPEN |

---

### Visual-symptom seeds — complete list

Under D-08, these require runtime screenshots before verdicts can be finalized. All screenshots use `gbcMode=true` for metasprites and platformer-template examples.

| Seed | ROM / Example | What to capture | Screenshot notes |
|------|--------------|-----------------|-----------------|
| SEED-004 | `metasprites.gb` | Elephant sprite rendering — is it garbled or correct vs reference? | `gbcMode=true`; compare to png2asset reference shape |
| SEED-005 | `metasprites.gb` | Background pattern — diagonal stripe or checkerboard? | `gbcMode=true`; look at background fill |
| SEED-013 | `metasprites.gb` | GBC sub-palette cycling on B press — correct color cycling or all-black? | `gbcMode=true`; press B to cycle palette; capture color state |
| SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED | `platformer-template.gb` | Player standing on ground vs floating | `gbcMode=true`; traverse right; capture player position on ground tile |
| SEED-PHASE-12-PLAYER-METASPRITE-RENDER | `platformer-template.gb` | Player sprite: duck sprite or dark checkerboard square? | `gbcMode=true`; walk right; capture player |
| SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE-BG-AND-OBJ | `platformer-template.gb` | BG + OBJ palette: normal or inverted? | `gbcMode=true`; compare to approved Phase 13.6 baselines. Existing evidence: `.planning/seeds/evidence/platformer-gbc-inverted-colors-2026-06-04.png` |
| SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK | `platformer-template.gb` | Player bottom edge 1-2px sunk into ground tile or flush? | `gbcMode=true`; close-up of player feet on ground |
| SEED-platformer-template-spawn-polish | `platformer-template.gb` | Spawn position: on a platform or mid-air? | `gbcMode=true`; capture immediately after scene start |

**Emulator note:** The MCP shadow JAR is present at `gbkt-mcp-server/build/libs/gbkt-mcp-server-0.1.0-SNAPSHOT-all.jar`. Rebuild with `./gradlew :gbkt-mcp-server:shadowJar` if `gradle clean` wiped it during the substrate pass.

**Existing reference images for platformer visual review:**
- `.planning/seeds/evidence/platformer-gbc-inverted-colors-2026-06-04.png` — Phase 13.4 "before" state for SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE (evidence of the defect at time of reporting). Phase 13.8 baselines in `.planning/phases/13.8-*/evidence/` are the "after Phase 13.8" state that Phase 16 is verifying against.

---

### Code/build/emission-verifiable seeds (non-visual)

These seeds can be verified via JVM tests, generated-C inspection, or `./gradlew generateC` without the MCP emulator:

| Seed | Verification method | Key file / test |
|------|--------------------|--------------------|
| SEED-006 | Generated-C inspection: does `play_frame()` assign `_elephant_subPalette =` before calling `move_metasprite_ex()`? | `build/gbkt/generated/main.c` — search for `_elephant_subPalette =` in `play_frame` scope |
| SEED-007 | Generated-C inspection: does actor-level palette slot default to 0 or sequential index? Test: multiple actors with default palette → check emitted `set_obj_palette` slot args | `gbkt-backend-gbdk` emission test or generated-C grep |
| SEED-008 | Generated-C inspection: do `buildSpriteDataLoadStatements` and `buildMetaspriteTileDataLoadStatements` both start with `nextTile = 0`? | `build/gbkt/generated/main.c` — look for two `set_sprite_data(0,` calls |
| SEED-009 | Generated-C inspection: does `bank1.c` include `<gbdk/metasprites.h>` when a scene frame calls `move_metasprite_*`? | `build/gbkt/generated/bank1.c` — header section |
| SEED-010 | Generated-C inspection: do symbol names contain metasprite ID prefix? | `build/gbkt/generated/main.c` — look for `sprite_metasprite_0[]` duplication |
| SEED-011 | Generated-C inspection: is `hiwater = 0` per frame-preamble or per `moveMetasprite()` call? | `build/gbkt/generated/main.c` or `bank1.c` — hiwater pattern |
| SEED-012 | Check `gbkt-mcp-server` for `emulator_read_memory` tool — does it exist? | `mcp__gbkt-emulator__emulator_read_memory` or source search |
| SEED-014 | **Run `BanksEmissionTest.kt` INV-2 sentinel** at HEAD. Also INV-6. | `./gradlew :gbkt-examples:banks:test --tests "*.BanksEmissionTest"` |
| SEED-015 | Generated-C inspection: do title-scene trampolines delegate to `title_enter()` (correct) or `pause_enter()` (wrong)? | `build/gbkt/generated/main.c` lines around `title_enter_trampoline` |
| SEED-016 | Check `BanksUatTest.kt` — is Anchor 4 SRAM test present and runnable? | Test file existence + `@Test` annotation |
| SEED-017 | Source grep: do two separate tileset generation paths coexist (`ConvertZoneTilesetsTask` vs `buildBuiltinTrackTilesetVarDecl`)? | `mcp__serena__search_for_pattern` for `buildBuiltinTrackTilesetVarDecl` |
| SEED-020 | JVM test: does `GameIRSerializer` round-trip produce non-empty collections for `zones`, `systems`, `flags`, etc.? | Existing round-trip test in `gbkt-ir` — run `./gradlew :gbkt-ir:test` |
| SEED-021 | Source inspection: is `pivot_adjust` still hardcoded in `PlatformerVisitor.kt:615` area? | `mcp__serena__find_symbol pivot_adjust` |
| SEED-022 | Source inspection: does `gameUsesTilemapCollision` appear in both `PlatformerVisitor.kt` and `GBDKPipeline.kt`? | `mcp__serena__search_for_pattern gameUsesTilemapCollision` |
| SEED-023 | Source grep: does `whenever()` in `ScriptBuilder.kt` have `@Deprecated`? | `mcp__serena__find_symbol whenever` |
| SEED-025 | Source grep: does `combatIsInState(String, String)` overload still exist in `RpgExtensions.kt`? | `mcp__serena__find_symbol combatIsInState` |
| SEED-026 | Run `./gradlew :gbkt-gradle-plugin:validatePlugins` — does it pass? | Gradle plugin module build |
| SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION | Source grep: does `zone(id: String)` still exist in DSL builders? | `mcp__serena__search_for_pattern "zone("` |
| SEED-PHASE-12-ONE-WAY-TILE | Source: does `oneWayThreshold` exist in `PlatformerVisitor.kt`? | `mcp__serena__find_symbol oneWayThreshold` |
| SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS | Source: does banking analysis allocate per-zone separate banks? | `mcp__serena__find_symbol allocateZoneBanks` |
| SEED-PHASE-12-SHARED-TILESET | Source: is there tileset deduplication in `ConvertZoneTilesetsTask`? | `mcp__serena__search_for_pattern dedup` in task file |
| SEED-PHASE-13-SPRITE-OUTLINE-LOST-NONZERO-TRNS-INDEX | Source: does `ConvertSpritesTask.buildPng2AssetArgs()` read tRNS index before passing `-keep_palette_order`? | `mcp__serena__find_symbol buildPng2AssetArgs` — look for tRNS handling |
| SEED-002 | Source: does `ActorRef` have `moveTo(Expr, Expr)` overload? | `mcp__serena__find_symbol moveTo` |
| SEED-003 | Generated-C / runtime: does sprite wrap-around still occur in simple-physics? | `build/gbkt/generated/main.c` — check bounds clamping |

---

### Folded todos — triage pre-analysis

**TODO-1: `metasprites-byte-identity-baseline-stale-since-12.8`** (`resolves_phase: 19`)

Root cause: `elephant.c.baseline` golden file was last regenerated in Phase 12.5, but `-keep_palette_order` was added in Phase 12.8. The test `MetaspritesGeneratedSpriteByteIdentityTest` is therefore RED due to a stale golden, not a codegen defect. Triage disposition: CONFIRMED-OPEN (latent test infra debt). Evidence: run `./gradlew :gbkt-examples:metasprites:test --tests "*MetaspritesGeneratedSpriteByteIdentityTest*"` — test will fail if baseline is stale. Routing: Phase 19 (metasprites cluster, D-15 governs baseline promotion). Repro artifact: failing test output showing baseline mismatch offset.

**TODO-2: `13.8-palette-bank-codegen-followups`** (advisory WR-01, WR-02, WR-03)

Three advisory code-review items from Phase 13.8, none blocking current ROM output:
- WR-01: `SceneIR.allocatedZoneBank` single-zone assumption undocumented — CONFIRMED-OPEN, route to Phase 19/20 (whoever touches `SceneVisitor` for multi-zone support)
- WR-02: No slot-collision guard for `MetaspriteIR.initialSubPaletteSlot` — CONFIRMED-OPEN, route to Phase 19 (metasprites cluster)
- WR-03: Lenient RGB555 integer-fallback parsing in `PngUtils` — CONFIRMED-OPEN, route to Phase 20 (sprite transparency cluster)

Evidence for each: source inspection via `mcp__serena__find_symbol allocatedZoneBank` (WR-01), `mcp__serena__find_symbol initialSubPaletteSlot` (WR-02), `mcp__serena__find_symbol RGB555` (WR-03) confirming the gap is still present.

**TODO-3: `triggersystem-ref-registry-validation`** (WR-07 from Phase 13.1)

`ScriptBuilder.triggerSystem(ref: SystemRef)` lowers `ref.systemId` into `TriggerSystem` IR with no registry check. Triage: CONFIRMED-OPEN (verifiable by source inspection: `mcp__serena__find_symbol triggerSystem` — look for registry validation at `build()` callsite). Probe: write a JVM test asserting `triggerSystem(SystemRef("nonexistent"))` throws at `build()` — currently this test would fail. Routing: Phase 21 (DSL/tooling misc cluster, FIX-06 scope).

---

## Standard Stack

No new external dependencies are introduced in Phase 16. All tooling is already present.

### Core verification tools

| Tool | Version | Purpose | Availability |
|------|---------|---------|--------------|
| Gradle | 9.5.1 | Substrate pass: ROM builds + JVM test suite | Available — `./gradlew --version` confirmed |
| Java (OpenJDK) | 21.0.2 | Gradle JVM runtime | Available — `java --version` confirmed |
| GBDK lcc | GBDK-2020 4.5.0 | ROM compilation in substrate pass | Available — `$GBDK_HOME=/Users/michalsvacha/gbdk`, `lcc` at `$GBDK_HOME/bin/lcc` |
| gbkt-emulator MCP server | 0.1.0-SNAPSHOT | Screenshot capture for visual seeds | Available — shadow JAR at `gbkt-mcp-server/build/libs/gbkt-mcp-server-0.1.0-SNAPSHOT-all.jar` |
| mGBA | installed | Manual visual verification (optional) | Available — `/usr/local/bin/mGBA` |
| Serena MCP tools | — | Code exploration (mcp__serena__find_symbol, search_for_pattern, etc.) | Available per user preference |

### Substrate pass command (D-13)

```bash
./gradlew \
  :gbkt-examples:pong:clean :gbkt-examples:pong:buildRom \
  :gbkt-examples:breakout:clean :gbkt-examples:breakout:buildRom \
  :gbkt-examples:simple-physics:clean :gbkt-examples:simple-physics:buildRom \
  :gbkt-examples:metasprites:clean :gbkt-examples:metasprites:buildRom \
  :gbkt-examples:metasprites-stress:clean :gbkt-examples:metasprites-stress:buildRom \
  :gbkt-examples:banks:clean :gbkt-examples:banks:buildRom \
  :gbkt-examples:platformer-template:clean :gbkt-examples:platformer-template:buildRom \
  && ./gradlew test
```

[ASSUMED] that `metasprites-stress` example exists — it appears in the code context (CONTEXT.md §Reusable Assets references "7 example projects"). Confirmed from CLAUDE.md: "7 example projects (`gbkt-examples/{pong,breakout,simple-physics,metasprites,metasprites-stress,banks,platformer-template}`)".

**Critical constraints:**
- NEVER two separate `./gradlew clean ...` invocations in parallel — Kotlin daemon collision corrupts builds (see `feedback_no_parallel_gradle_clean`)
- Pong ROM hash is known non-deterministic (sdcc/lcc toolchain) — flag as PASS* in evidence
- Record SHA of HEAD at substrate pass start (D-14): `git rev-parse HEAD`

### MCP emulator usage (visual seeds)

```
emulator_start: { romPath: "...", gbcMode: true, symFile: "build/gbkt/output/<name>.noi" }
emulator_screenshot: { path: ".planning/phases/16-seed-triage/evidence/<SEED-ID>/screenshot.png" }
```

**Always `gbcMode: true` for:**
- `platformer-template.gb` (GBC-compatible ROM; DMG captures show green-tint false palette regression)
- `metasprites.gb` (GBC mode for sub-palette cycling in SEED-013)

**Traversal for platformer screenshots:** RIGHT+A to jump over obstacles — held-RIGHT-only stalls at a designed tree obstacle (not a collision bug).

---

## Architecture Patterns

### System Architecture Diagram

```
Phase 16 Evidence Flow
─────────────────────

W0: Setup
  REQUIREMENTS.md → 6 fast-path RE-DEFERRED seeds → .planning/backlog/v0.2.0/
  Create TRIAGE.md skeleton (47 rows, TBD dispositions)
  Create .planning/phases/16-seed-triage/evidence/ (already created)

W1: Substrate Pass (single serial Gradle invocation)
  git HEAD SHA ──────────────────────────────────────────────────┐
  ./gradlew [7 buildRom targets] && ./gradlew test              │
      │                                                          │
      ├─► build/gbkt/generated/main.c (per example)             │
      ├─► build/gbkt/generated/bank*.c (per example)            │
      ├─► build/gbkt/output/*.gb (per example)                  │
      ├─► build/reports/tests/ (JVM test results)               │
      └─► SHA pinned in TRIAGE.md ◄──────────────────────────────┘

W2: Cluster Agents (parallel, read-only from substrate artifacts)
  Metasprites cluster ─► evidence/SEED-004/, 005/, 006/, ...
  Banks cluster       ─► evidence/SEED-014/, 015/, 016/
  Platformer cluster  ─► evidence/SEED-PHASE-12-PLAYER-*/...
  DSL/tooling cluster ─► evidence/SEED-002/, 003/, ...
      │
      ├─► MCP emulator (serialized screenshot capture)
      │       emulator_start(gbcMode=true)
      │       emulator_screenshot(path=evidence/<seed>/)
      └─► Serena source inspection (find_symbol, search_for_pattern)

W3: Batch Visual Review Gate (D-08)
  All visual-seed screenshots
  + reference images (Phase 13.6/13.7/13.8 baselines)
  ──► Review document ──► Human approval ──► Verdicts locked

W4: Phase Close
  TRIAGE.md rows finalized
  Seed files stamped (D-02 frontmatter: triage_disposition: + TRIAGE.md pointer)
  VERIFIED-ALREADY-FIXED + INVALID seeds ──► .planning/seeds/archive/
  RE-DEFERRED seeds ──► .planning/backlog/v0.2.0/ (+ REQUIREMENTS.md index entry)
  CONFIRMED-OPEN seeds ──► remain in .planning/seeds/
  REQUIREMENTS.md + ROADMAP.md ──► D-11 reconciliation pass
```

### Recommended Evidence Directory Structure

```
.planning/phases/16-seed-triage/
├── TRIAGE.md                    # Canonical 47-row disposition table
├── evidence/                    # Already created
│   ├── substrate-sha.txt        # git rev-parse HEAD at substrate pass time
│   ├── substrate-test-report.txt# ./gradlew test summary
│   ├── SEED-004/
│   │   └── screenshot.png       # MCP emulator capture
│   ├── SEED-013/
│   │   └── screenshot-b-press.png
│   ├── SEED-014/
│   │   └── inv2-test-output.txt # BanksEmissionTest INV-2 result
│   ├── SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE-BG-AND-OBJ/
│   │   ├── screenshot-head.png  # New capture at HEAD
│   │   └── reference-13.8.png   # Approved Phase 13.8 baseline (or pointer)
│   └── ... (one directory per seed with visual or JVM evidence)
├── visual-review-document.md    # D-08 batch review doc (all visual seeds)
└── 16-TRIAGE.md → TRIAGE.md    # Or just TRIAGE.md directly
```

### TRIAGE.md Recommended Schema (Claude's discretion per D-10)

| ID | Title (short) | Type | Disposition | Evidence | Fix-phase routing | Notes |
|----|--------------|------|-------------|----------|------------------|-------|
| SEED-001 | IDE & Tooling | RE-DEFERRED | fast-path | REQUIREMENTS.md IDE-02 | v2.0 | D-12 |
| SEED-004 | Elephant tile rendering | visual | CONFIRMED-OPEN | evidence/SEED-004/screenshot.png | Phase 19 FIX-01 | |
| ... | | | | | | |

Type column values: `visual`, `emission`, `jvm-test`, `source-only`, `re-deferred`.

### Seed Frontmatter Stamp (D-02)

Seeds with YAML frontmatter (14 of 44 have frontmatter) get:
```yaml
triage_disposition: VERIFIED-ALREADY-FIXED  # or CONFIRMED-OPEN, RE-DEFERRED, INVALID
triage_evidence: ".planning/phases/16-seed-triage/TRIAGE.md#SEED-014"
triage_date: 2026-06-12
```

Seeds without frontmatter (30 of 44 are markdown-only) get a small header block after the title:
```markdown
> **Triage disposition:** CONFIRMED-OPEN — see [TRIAGE.md](.planning/phases/16-seed-triage/TRIAGE.md#SEED-015)
```

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| ROM screenshots | Custom emulator invocation | `gbkt-emulator` MCP server (`emulator_start`, `emulator_screenshot`) | Handles ROM loading, save-state, sym-file; already configured; produces standardized PNG output |
| Symbol lookup in generated C | Regex-based parsing | `extractFunctionBody()` helper already in `BanksEmissionTest.kt` (lines 200+); reuse pattern | Avoids false positives from matching across function boundaries — file-level `contains()` is a documented anti-pattern (CONTEXT §Existing Code Insights) |
| Source symbol search | grep across all files | Serena MCP `mcp__serena__find_symbol`, `mcp__serena__search_for_pattern` | User standing preference; handles cross-module correctly |
| TRIAGE.md template | Custom format | Follow the schema in this RESEARCH.md §Architecture Patterns — matches D-01 requirements | Downstream fix phases (19/20/21) read TRIAGE.md directly; consistent schema reduces planning friction |

---

## Common Pitfalls

### Pitfall 1: Closing Seeds as Fixed Without Executable Evidence (PITFALLS.md Pitfall #4)

**What goes wrong:** A seed marked "RESOLVED" in its own text is closed by reading the text, not by running evidence at HEAD. The resolution may have been reverted, partially applied, or superseded by a later phase's change.

**How to avoid:** D-06 is binding: executable evidence at HEAD required for EVERY disposition — even self-reported-resolved seeds. Minimum evidence: `./gradlew :gbkt-examples:<example>:generateC` exits 0 AND generated-C inspection shows the defect pattern absent (or present for CONFIRMED-OPEN).

**Warning signs:** A TRIAGE.md row citing only a commit hash with no corresponding test output or generated-C excerpt.

### Pitfall 2: Parallel Gradle Builds (PITFALLS.md Pitfall #12 + feedback)

**What goes wrong:** Substrate pass dispatches concurrent `./gradlew clean :a:buildRom` and `./gradlew clean :b:buildRom` in separate subshells. Kotlin daemon corruption corrupts both build artifacts. Evidence is unreliable. All downstream triage is invalidated.

**How to avoid:** D-13 is binding: ONE serial Gradle invocation for the substrate pass. All 7 `clean/buildRom` targets chained in a single `./gradlew` command. If the substrate pass fails, investigate with `./gradlew --stop` then retry serially.

### Pitfall 3: Pixel-Judgment Without Human Gate (D-08)

**What goes wrong:** A cluster agent captures a screenshot, judges the pixels as "looks correct" or "looks inverted," and writes the TRIAGE.md verdict. The Visual Evidence Rule + D-08 require that visual-seed verdicts are NOT finalized without a human review pass.

**How to avoid:** W3 (batch visual review gate) is a blocking checkpoint before TRIAGE.md rows for visual seeds are finalized. Agents draft the proposed verdict; the human approves or overrides. Screenshots assembled into one review document pairing each with the best available reference image.

### Pitfall 4: SEED-014 Gate Confusion (PITFALLS.md Pitfall #9)

**What goes wrong:** SEED-014's `hasZoneSceneBinder` guard addition is visible at `GBDKPipeline.kt:1164-1168`. A researcher concludes "guard was added → fixed" without running the INV-2 sentinel test. The guard may not cover all multi-bank zone game paths — INV-2 was planted precisely because the fix was uncertain.

**How to avoid:** Run `BanksEmissionTest.kt` INV-2 (and INV-6) sentinel at HEAD. If GREEN → VERIFIED-ALREADY-FIXED. If RED → CONFIRMED-OPEN, route to Phase 20 (Banks cluster, FIX-03).

**Note:** The INV-2 sentinel test (`BanksEmissionTest > INV-2 bkg_tiles_load_banked wrapper in main_c has SWITCH_ROM sequence`) asserts the `_bkg_tiles_load_banked` helper body contains a `SWITCH_ROM(` sequence. This test was RED at Phase 13.2 base. Re-running it at HEAD gives definitive status.

### Pitfall 5: Wrong Emulator Mode for Platformer/Metasprites

**What goes wrong:** A cluster agent runs `emulator_start` without `gbcMode=true` for `platformer-template.gb` or `metasprites.gb`. The DMG-mode capture shows a green-tinted screen, which falsely reads as a palette regression vs GBC reference images.

**How to avoid:** All platformer-template and metasprites captures must use `emulator_start` with `gbcMode=true`. This is a documented project rule (CONTEXT.md §Established Patterns, PITFALLS.md Integration Gotchas).

### Pitfall 6: Stale Metasprites Byte-Identity Baselines (D-15)

**What goes wrong:** After the substrate pass regenerates metasprite ROM artifacts, a cluster agent sees that `MetaspritesGeneratedSpriteByteIdentityTest` fails and concludes "the generated C is wrong." In fact, the failure is a stale golden file from pre-12.8 (the todo exists precisely for this). The agent then wastes investigation time on a non-defect.

**How to avoid:** The metasprites-byte-identity-baseline-stale todo explains the root cause: the baseline golden file needs to be regenerated from current correct output. D-15 says the new baseline is promoted to official only AFTER the batch visual review gate approves the screenshots. Agents MUST recognize that this test RED is expected and documented, not a new defect.

### Pitfall 7: MCP Shadow JAR Wiped by Gradle Clean

**What goes wrong:** The substrate pass's `clean` targets wipe `gbkt-mcp-server/build/`. If the shadow JAR was in that directory, the MCP emulator connection fails ("Failed to connect") when visual-seed cluster agents try to capture screenshots.

**How to avoid:** Run `./gradlew :gbkt-mcp-server:shadowJar` immediately AFTER the substrate pass and before dispatching visual-seed cluster agents. Or re-check that the shadow JAR is in `build/libs/` before starting emulator capture. The JAR is currently present at `gbkt-mcp-server/build/libs/gbkt-mcp-server-0.1.0-SNAPSHOT-all.jar`.

---

## Cluster Agent Pre-Analysis

### Cluster A: Metasprites (7 seeds + 1 todo)

Seeds: SEED-004, SEED-005, SEED-006, SEED-007, SEED-008, SEED-009, SEED-010, SEED-011, SEED-013, SEED-PHASE-12-PLAYER-METASPRITE-RENDER, SEED-PHASE-13-SPRITE-OUTLINE-LOST-NONZERO-TRNS-INDEX
Todo: metasprites-byte-identity-baseline-stale-since-12.8

Primary artifact: `gbkt-examples/metasprites/build/gbkt/generated/main.c`
Visual seeds in cluster: SEED-004, SEED-005, SEED-013, SEED-PHASE-12-PLAYER-METASPRITE-RENDER (partially — render path is also emission-verifiable)

Expected dispositions:
- SEED-004: Likely CONFIRMED-OPEN (elephant tiles garbled — investigation needed; D-V1 metasprite visual parity was never confirmed fixed)
- SEED-005: Likely CONFIRMED-OPEN (bgFillCheckerboard pattern is a known literal bug — Phase 10 reported it; no fix has been reported)
- SEED-006: Likely CONFIRMED-OPEN (sub-palette global not assigned — check generated C)
- SEED-007: Likely CONFIRMED-OPEN (same `else 0` bug from Phase 10 — check `GameBuilder.kt:713`)
- SEED-008: Likely CONFIRMED-OPEN (structural latent — no current example triggers it; verify two separate `nextTile=0` in generated C)
- SEED-009: Depends on banking config — verify if `metasprites.h` is in `bank1.c` header
- SEED-010: CONFIRMED-OPEN (symbol collision — no fix reported; metasprites example only uses 1 metasprite so it's latent)
- SEED-011: CONFIRMED-OPEN (hiwater collision — same latent condition as SEED-010)
- SEED-013: Likely CONFIRMED-OPEN (visual D-V3 — required GBC visual evidence; Phase 10.2 was the driver but no subsequent fix is documented)
- SEED-PHASE-12-PLAYER-METASPRITE-RENDER: Possibly VERIFIED-ALREADY-FIXED (Phase 12.4 ConvertSpritesTask fixed stub path; verify with screenshot)
- SEED-PHASE-13-SPRITE-OUTLINE-LOST-NONZERO-TRNS-INDEX: Likely CONFIRMED-OPEN (tRNS non-zero index — OPEN as of Phase 13.3; no fix in 13.3–13.8 timeline)
- TODO (metasprites-byte-identity-baseline): CONFIRMED-OPEN (stale baseline; route Phase 19)

### Cluster B: Banks (3 seeds)

Seeds: SEED-014, SEED-015, SEED-016, SEED-PHASE-12-RETROACTIVE-BANKS-AUDIT

Primary artifact: `gbkt-examples/banks/build/gbkt/generated/main.c`
JVM test: `./gradlew :gbkt-examples:banks:test --tests "*.BanksEmissionTest"`

Expected dispositions:
- SEED-014: Run INV-2 + INV-6 sentinel — unclear (partial fix exists); if GREEN → VERIFIED-ALREADY-FIXED, if RED → CONFIRMED-OPEN → Phase 20 FIX-03
- SEED-015: Check generated `main.c` for `title_enter_trampoline` delegation — if it calls `pause_enter()` → CONFIRMED-OPEN; if correct → VERIFIED-ALREADY-FIXED
- SEED-016: Check `BanksUatTest.kt` for Anchor 4 `@Test` — if test exists and GREEN → VERIFIED-ALREADY-FIXED; if missing → CONFIRMED-OPEN → Phase 20
- SEED-PHASE-12-RETROACTIVE-BANKS-AUDIT: VERIFIED-ALREADY-FIXED (trivially satisfied by D-01 per seed text; confirm `./gradlew :gbkt-examples:banks:generateC` exits 0)

### Cluster C: Platformer (8 seeds)

Seeds: SEED-021, SEED-022, SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED, SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY, SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS, SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE-BG-AND-OBJ, SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK, SEED-platformer-template-spawn-polish

Primary artifact: `gbkt-examples/platformer-template/build/gbkt/generated/main.c`
Visual seeds: SEED-PHASE-12-PLAYER-LEVITATING, SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE, SEED-PHASE-13-PLAYER-SUB-PIXEL, SEED-platformer-template-spawn-polish

Expected dispositions:
- SEED-021: CONFIRMED-OPEN (pivot_adjust still hardcoded per Phase 12.7 — source inspection to confirm)
- SEED-022: CONFIRMED-OPEN (duplicate predicate still present — source inspection)
- SEED-PHASE-12-PLAYER-LEVITATING: Unknown — screenshot needed; Phases 12.6/12.7/13.x may have fixed it
- SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY: Likely CONFIRMED-OPEN (per Phase 12 notes; superseded by SEED-platformer-template-spawn-polish below)
- SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS: Generated-C check: do `PlatformerTemplate.kt` `cEmit()` calls still appear in generated output? → indicates auto-emission gaps still open; route to Phase 21 FIX-05
- SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE-BG-AND-OBJ: Unknown — screenshot needed; Phase 13.8 may have fixed it
- SEED-PHASE-13-PLAYER-SUB-PIXEL: Unknown — screenshot needed; sub-pixel may be improved or still present
- SEED-platformer-template-spawn-polish: Likely CONFIRMED-OPEN (spawn position; supersedes SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY)

### Cluster D: DSL/Tooling Misc (15 seeds + 2 todos)

Seeds: SEED-002, SEED-003, SEED-012, SEED-017, SEED-020, SEED-023, SEED-025, SEED-026, SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION, SEED-PHASE-12-ONE-WAY-TILE, SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS, SEED-PHASE-12-SHARED-TILESET, SEED-PHASE-12-CONVERTSPRITESTASK-AUDIT, SEED-PHASE-12-GRASS-TILEMAP-WHITE-PIXELS, SEED-PHASE-12-TITLE-ZONE-PATH-A-SCENE-RENDER-DEFECT
Todos: 13.8-palette-bank-codegen-followups, triggersystem-ref-registry-validation

Expected dispositions:
- SEED-002: CONFIRMED-OPEN (moveTo Expr overload missing — source check)
- SEED-003: CONFIRMED-OPEN (wrap behavior intentional but not polished — RE-DEFERRED or CONFIRMED-OPEN for FIX-06)
- SEED-012: CONFIRMED-OPEN (MCP read_memory tool missing — check MCP server tool list)
- SEED-017: CONFIRMED-OPEN (two tileset pipeline paths coexist — source verification)
- SEED-020: CONFIRMED-OPEN (serializer stubs still present — JVM test check)
- SEED-023: CONFIRMED-OPEN (whenever not deprecated — source check)
- SEED-025: CONFIRMED-OPEN (combatIsInState String overload still present — source check)
- SEED-026: CONFIRMED-OPEN (validatePlugins still red — run `./gradlew :gbkt-gradle-plugin:validatePlugins`)
- SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION: CONFIRMED-OPEN (magic string `zone(id: String)` — source check) → FIX-06 or RE-DEFERRED
- SEED-PHASE-12-ONE-WAY-TILE: RE-DEFERRED (no example exercises one-way tiles; future platformer port trigger)
- SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS: RE-DEFERRED (overflow guard hasn't tripped; future analysis pass)
- SEED-PHASE-12-SHARED-TILESET: RE-DEFERRED (option (a) chosen in Phase 12; within 2× ROM-size signal)
- SEED-PHASE-12-CONVERTSPRITESTASK-AUDIT: VERIFIED-ALREADY-FIXED (confirm executable evidence at HEAD)
- SEED-PHASE-12-GRASS-TILEMAP-WHITE-PIXELS: VERIFIED-ALREADY-FIXED (confirm with platformer screenshot)
- SEED-PHASE-12-TITLE-ZONE-PATH-A-SCENE-RENDER-DEFECT: VERIFIED-ALREADY-FIXED (confirm title screen renders)
- TODO 13.8-palette-bank-codegen-followups: 3 rows (WR-01 CONFIRMED-OPEN, WR-02 CONFIRMED-OPEN, WR-03 CONFIRMED-OPEN)
- TODO triggersystem-ref-registry-validation: CONFIRMED-OPEN → FIX-06 / Phase 21

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | 6 fast-path RE-DEFERREDs require no verification work once REQUIREMENTS.md entry exists as rationale | Fast-path RE-DEFERREDs | Low — REQUIREMENTS.md explicitly lists these as Future Requirements; worst case is over-deferring a seed that could be cheap to verify |
| A2 | `metasprites-stress` is one of the 7 example ROMs in the substrate pass | Substrate pass command | Low — confirmed in CLAUDE.md module list |
| A3 | Seeds SEED-005, SEED-006, SEED-007, SEED-008, SEED-010, SEED-011 are CONFIRMED-OPEN (no fix has been reported in any later phase) | Cluster A pre-analysis | Medium — a later phase may have incidentally fixed these; substrate pass generated-C inspection will confirm |
| A4 | SEED-013 is CONFIRMED-OPEN (Phase 10.2 drive was the last known action; no visual closure was reported after Phase 10.2) | Cluster A pre-analysis | Medium — Phase 10.2 drove the visual closure; if Phase 10.2 concluded with a green visual sign-off, the seed should be VERIFIED-ALREADY-FIXED. Triage must check Phase 10.2 sign-off record |
| A5 | SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS has 4 `cEmit()` escape hatches still in `PlatformerTemplate.kt` | Cluster C pre-analysis | Medium — Phase 13.5 added BindCurrentLevel and screen() auto-emission; some gaps may have been closed. Source inspection required |
| A6 | `triggersystem-ref-registry-validation` TODO is CONFIRMED-OPEN | Folded todos | Low — source inspection easily confirms presence/absence of registry check |

---

## Open Questions

1. **Was SEED-013 visually closed by Phase 10.2?**
   - What we know: Phase 10.2 drove 4 rounds of palette fix + GBC visual closure. Phase 10.2 closed 2026-05-XX. SEED-013 has `status: active` in its frontmatter (as of seed creation) and `trigger_when: surfaces in Phase 10.2`.
   - What's unclear: Did Phase 10.2's terminal close include a GBC screenshot showing correct sub-palette cycling? The seed frontmatter was not updated post-Phase-10.2.
   - Recommendation: Check Phase 10.2 closeout SUMMARY.md + VERIFICATION.md for a visual closure evidence record. If found, SEED-013 is VERIFIED-ALREADY-FIXED; if not, it is CONFIRMED-OPEN. Either way, a HEAD screenshot is required (D-06 binding).

2. **How many of Phase 13.5's auto-emission additions closed SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS?**
   - What we know: Phase 13.5 added `BindCurrentLevel` (Req #17), `screen()` (Req #18), `auto-*_exit` synthesis (Req #15). Phase 13.5 CONTEXT.md noted "migrated PlatformerTemplate + Banks off cEmit/zone/exit-tricks."
   - What's unclear: Did Phase 13.5 remove all 4 `cEmit()` escape hatches, or only some?
   - Recommendation: During cluster C triage, grep `PlatformerTemplate.kt` for `cEmit(` occurrences. If none remain, VERIFIED-ALREADY-FIXED; if any remain, each remaining call is a CONFIRMED-OPEN gap.

3. **Is the `hasZoneSceneBinder` guard at GBDKPipeline.kt:1164-1168 sufficient for SEED-014, or does INV-2 remain RED?**
   - What we know: The guard was added in a later phase; BanksEmissionTest.kt INV-2 was RED as of Phase 13.2 base commit.
   - What's unclear: Was the guard updated after Phase 13.2? Did Phase 13.5 or 13.8 touch the banking logic?
   - Recommendation: Run `./gradlew :gbkt-examples:banks:test --tests "*.BanksEmissionTest"` as part of the Banks cluster triage. Result is definitive.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java (OpenJDK) | Substrate pass, JVM tests | ✓ | 21.0.2 LTS | — |
| Gradle | Substrate pass, JVM tests | ✓ | 9.5.1 | — |
| GBDK lcc | Substrate ROM builds | ✓ | GBDK-2020 4.5.0 (`$GBDK_HOME=/Users/michalsvacha/gbdk`) | If missing: `generateC` only; ROM builds SKIPPED; visual seeds cannot be fully triaged |
| gbkt-emulator MCP server | Visual-seed screenshots | ✓ | 0.1.0-SNAPSHOT shadow JAR present | Rebuild with `./gradlew :gbkt-mcp-server:shadowJar` if wiped by clean |
| mGBA | Manual visual spot-checks | ✓ | installed at `/usr/local/bin/mGBA` | Not required; MCP server is primary screenshot tool |
| Serena MCP tools | Source code exploration | ✓ (per user config) | — | Fall back to `Read` / grep if unavailable |

**Missing dependencies with no fallback:** None — all required tools are available.

**Note:** The MCP shadow JAR at `gbkt-mcp-server/build/libs/gbkt-mcp-server-0.1.0-SNAPSHOT-all.jar` is present NOW but may be wiped by the substrate pass `clean` targets. Rebuild with `./gradlew :gbkt-mcp-server:shadowJar` immediately after the substrate pass completes (before dispatching visual-seed cluster agents).

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (via Gradle test task, Kotlin test DSL) |
| Config file | `build.gradle.kts` per module |
| Quick run command | `./gradlew :gbkt-examples:banks:test --tests "*.BanksEmissionTest"` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| TRIAGE-01 | Every seed has a terminal disposition backed by evidence | manual / docs | Verify TRIAGE.md row count = 47; all Disposition cells non-empty | TRIAGE.md created in W0 |
| TRIAGE-01 (SEED-014 sub-requirement) | BanksEmissionTest INV-2 sentinel status at HEAD | unit / emission | `./gradlew :gbkt-examples:banks:test --tests "*.BanksEmissionTest"` | ✅ BanksEmissionTest.kt exists |
| TRIAGE-02 | Visual seeds have HEAD screenshots | manual / MCP | Run `emulator_screenshot` for each visual seed; confirm PNG in `evidence/` | TRIAGE.md + evidence/ artifacts |
| TRIAGE-03 | seeds/ empty at milestone close | structural | `ls .planning/seeds/*.md | wc -l` = 0 | Phase-close step |

### Sampling Rate
- **Per plan commit:** Run applicable cluster's quick-check (e.g., `BanksEmissionTest` for banks cluster)
- **Substrate pass:** `./gradlew [all 7 buildRom] && ./gradlew test` (one-time, W1)
- **Phase gate:** TRIAGE.md complete with 47 rows, batch visual review gate human-approved, archive/backlog movements done

### Wave 0 Gaps
- [ ] TRIAGE.md skeleton — create with 47 placeholder rows (Wave 0 plan)
- [ ] `.planning/seeds/archive/` directory — create (Wave 0 or phase-close plan)
- [ ] `.planning/backlog/v0.2.0/` directory — create (Wave 0 or phase-close plan)
- [ ] `evidence/substrate-sha.txt` — created during W1 substrate plan

---

## Security Domain

This phase makes no changes to authentication, session management, access control, cryptography, or input validation. All work is reading artifacts (ROM builds, JVM test outputs, generated C, screenshots) and writing documentation files. ASVS categories are not applicable to a triage/documentation phase.

| ASVS Category | Applies | Rationale |
|---------------|---------|-----------|
| V2 Authentication | No | No auth code touched |
| V3 Session Management | No | No session code touched |
| V4 Access Control | No | No access control code touched |
| V5 Input Validation | No | No new external inputs added |
| V6 Cryptography | No | No cryptographic code touched |

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Seeds closed at planting by marking "status: dormant" | Seeds require terminal evidence-backed disposition at triage | v0.1.1 milestone definition | Every seed must be verified at HEAD, not trusted from planting-time status |
| Triage by reading seed text | Triage requires executable evidence (D-06) | CONTEXT.md D-06 | "Already fixed" closures require green test run or generated-C inspection at HEAD |
| Visual closure via variable assertion | Visual closure requires runtime screenshot (Visual Evidence Rule) | `.planning/verifier-gates.md` codified | MCP emulator screenshots are mandatory for visual-symptom seeds |

**Deprecated/outdated:**
- Trusting "status: dormant" in seed frontmatter as proof of inactivity: seeds planted before Phase 12.6 may have been silently closed by later phases — or may still be open. Re-verify each.
- Using `hasSportRacing` gate for `_bkg_tiles_load_banked` — this was the original SEED-014 bug; the gate was updated to include `hasZoneSceneBinder` but may still be insufficient.

---

## Project Constraints (from CLAUDE.md)

| Directive | Impact on Phase 16 |
|-----------|-------------------|
| No parallel `gradle clean` | Substrate pass must be a single serial `./gradlew` invocation (D-13 — already aligned) |
| Use Serena MCP tools for code exploration | Cluster agents must use `mcp__serena__find_symbol`, `mcp__serena__search_for_pattern` (agent prompts must explicitly instruct this — D-16) |
| Visual Evidence Rule (verifier-gates.md) | All visual-seed triage dispositions require MCP screenshot at HEAD (D-08 — already aligned) |
| pluginTest race: verify via two invocations | If `pluginTest` is run for SEED-026 triage, first failure may be the race — run twice before concluding RED |
| `pluginTest` not `:gbkt-gradle-plugin:test` | SEED-026 Gradle hygiene triage should run `pluginTest` or `validatePlugins` directly, never `:gbkt-gradle-plugin:test` alone |
| Pong PASS* | Pong ROM hash is non-deterministic; substrate pass records PASS* for pong, no investigation |
| `gbcMode=true` for platformer + metasprites | All MCP screenshots of platformer-template.gb and metasprites.gb must use GBC mode |
| RIGHT+A for platformer traversal | Held-RIGHT-only stalls at a designed obstacle — not a bug; always jump when traversing |

---

## Sources

### Primary (HIGH confidence)
- `.planning/phases/16-seed-triage/16-CONTEXT.md` — all locked decisions (D-01 through D-16); [VERIFIED: direct file read]
- `.planning/seeds/*.md` — all 44 seed files; [VERIFIED: direct file read — complete inventory performed]
- `.planning/todos/pending/*.md` — 3 folded todos; [VERIFIED: direct file read]
- `.planning/REQUIREMENTS.md` — TRIAGE-01..03, FIX-01..06, Future Requirements (fast-path rationale); [VERIFIED: direct file read]
- `.planning/research/SUMMARY.md`, `ARCHITECTURE.md`, `PITFALLS.md` — prior milestone research; [VERIFIED: direct file read]
- `.planning/verifier-gates.md` — Visual Evidence Rule; [VERIFIED: direct file read]
- `CLAUDE.md` — project constraints, tool preferences, no-parallel-gradle-clean rule; [VERIFIED: direct file read]
- `gbkt-examples/banks/src/test/kotlin/.../BanksEmissionTest.kt` — INV-2 sentinel at line 200; [VERIFIED: direct file read + grep]

### Secondary (MEDIUM confidence)
- `.planning/STATE.md` — project history and phase completion context; [VERIFIED: direct file read]
- `.planning/ROADMAP.md` — milestone success criteria and phase ordering; [VERIFIED: direct file read]
- Environment probe — Java 21.0.2, Gradle 9.5.1, GBDK at `$GBDK_HOME`, MCP JAR present; [VERIFIED: bash commands]

### Tertiary (LOW confidence)
- Phase cluster pre-analysis: expected dispositions per seed are based on reading seed files + known fix history; actual dispositions may differ once evidence is gathered at HEAD; [ASSUMED]

---

## Metadata

**Confidence breakdown:**
- Seed corpus inventory: HIGH — all 44 files read directly
- Disposition pre-analysis: MEDIUM — based on seed text + phase history; actual verification at HEAD may change some expected dispositions
- Verification machinery: HIGH — all tools confirmed available; commands verified
- Folded todos: HIGH — all 3 todo files read directly

**Research date:** 2026-06-12
**Valid until:** 2026-06-19 (7 days — this is a pre-execution triage phase; substrate pass may surface new information that supersedes pre-analysis)
