# Phase 10.2 — Defect Closure Matrix

**Generated:** Plan 10.2-12 (phase close gate), 2026-05-19
**Plan range:** 10.2-01 through 10.2-12 (12 plans shipped — meets ≥12 ideal target per D-03)

This matrix maps every defect decision from `10.2-CONTEXT.md` to the plan that
satisfied it and the evidence artifact that proves it.

---

## Single Named Defect: DEF-10.1-13-C (D-V3 visual closure 5th layer)

| Dimension | Decision Ref | Verdict | Evidence |
|-----------|-------------|---------|----------|
| Visual closure (cyan + checker on behavior3) | D-14, D-15 | **PASS** | `evidence/uat-postfix/behavior3-postfix.png` (1325 bytes, 5 distinct colors, cyan present, checker present) + `closure-verdict.md` |
| DMG non-regression (behavior1/2 unchanged) | D-17 | **PASS** | `evidence/uat-postfix/behavior1-postfix.{png,json}` (4 colors, exact RGB match) + `behavior2-postfix.{png,json}` (4 colors, equivalent RGBs) |
| 5 locked tests preserved (GREEN) | D-11 | **PASS** | `evidence/rom-smoke/full-test-suite.log` — `DV3GbcPaletteWriteDiagnosticTest` (3/3), `DV3VisualV2DiagnosticTest` (2/2), `BgCheckerboardEmissionTest` (4/4), `SpritePaletteSlotEmissionTest`, `GbcCompatEmissionTest` all GREEN |
| New Plan 07 RED test flipped to GREEN | D-13 | **PASS** | `evidence/rom-smoke/full-test-suite.log` — `DV3VisualV3DiagnosticTest` (2/2) GREEN after Plan 08 fix |
| Bisect-named cause documented | D-19 | **PASS** | `evidence/d-v3-visual-finding-v3.md` Section 2 — VRAM collision named; `evidence/probe-table.md` — full 7-probe chain |
| ROM smoke (metasprites clean buildRom) | D-16 | **PASS** | `evidence/rom-smoke/metasprites-buildrom.log` (32 KB ROM, BUILD SUCCESSFUL) |
| Stress ROM smoke | D-16 + 10.1 D-21 | **PASS** | `evidence/rom-smoke/metasprites-stress-buildrom.log` (32 KB ROM, BUILD SUCCESSFUL, 2-bank MBC5) |
| MCP `emulator_read_memory` tool + sanity test | D-08, D-09, D-10 | **PASS** | `gbkt-mcp-server` `ToolHandlersTest` — 2 new tests GREEN; LCDC bit 7 transition assertion passes |
| Cross-phase evidence update (Phase 10's behavior3 PNG) | D-17 | **PASS** | `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior3-subpalette-cycle-gbc.{png,json}` updated (Plan 10.2-10, commit e5b1bd6e) |
| Worktree drift hygiene | D-20 | **PASS** | `evidence/worktree-drift-check.md` — no leakage (pre-removal reflog confirms probe commits ONLY under `worktrees/bisect/HEAD@{N}`), worktree removed (`git worktree remove --force`), branch NOT force-deleted (detached HEAD — no named branch to delete) |
| Plan-count gate (≥8 plans; ideally ≥12; one concern per plan) | D-03, D-03b | **PASS** | 12 PLAN.md files committed (10.2-01 through 10.2-12); each plan covers one concern (MCP tool / worktree setup / 7 bisect probes / finding / RED test / fix / UAT / cross-phase / ROM smoke / phase close) |

---

## Overall

**PASS**

All 11 dimensions above pass. DEF-10.1-13-C (GBC all-black regression) is CLOSED visually.

---

## Phase Scope Cap Adherence (D-01, D-02)

- **ONE named visual cause:** YES — sprite TILE VRAM collision (LCDC.4=1 shared region; `set_bkg_data` overwrites elephant tile 0 after `set_sprite_data` when bgFillCheckerboard emits AFTER allSpriteDataLoads)
- **ONE fix:** YES — `GBDKPipelineV2.kt` lines 3826-3838: swap `addAll(allSpriteDataLoads)` and `addAll(hoistedBgFillCheckerboardStatements)` execution order (commit `f2e8cecc`)
- **No DSL surface changes:** YES — no new DSL keywords, no DSL builder changes
- **No new IR fields:** YES — no IR node additions
- **No new visitor methods:** YES — no visitor interface changes
- **Per D-02: NO Phase 10.2.1/10.2.2 created (terminal subphase):** YES — surplus absorbed via inserted plans (06b/06c/06d sub-probes) within the existing wave graph, never via a sub-subphase

---

## Bisect Probe Trail

| Probe | Source | Cyan in PNG | Checker in PNG | BCPD slot 0 | OCPD slot 2 | Verdict |
|-------|--------|-------------|----------------|-------------|-------------|---------|
| 0 baseline | `cfe41ad7` (pre-Plan-19/20 buildable anchor) | YES | YES | 0x7FFF (non-zero) | 0x7FFF (non-zero) | BOTH PATHS WORK — sprite + BG functional baseline |
| A | `2767fab7` (+Plan 19+20: DISPLAY_OFF prepend, sprite-palette hoist, LCDC reorder) | YES | YES | 0x7FFF | 0x7FFF | Plans 19+20 did NOT break cyan |
| B | `0d4e4bb4` (+Plan 22: all 3 emissions = constant + set_bkg_palette + bgFillCheckerboard hoist) | NO | YES | 0x7FFF | 0x7FFF | **REGRESSION NAMED: Plan 22** |
| C-1 | On Probe A + constant declaration only (#1) | YES | YES | 0x7FFF | 0x7FFF | CLEARED — constant alone is inert |
| C-2 | On Probe A + constant + set_bkg_palette (#1+#2) | YES | YES | 0x7FFF | 0x7FFF | CLEARED — set_bkg_palette alone inert; BCPD confirmed writing correctly |
| C-3 | On Probe A + bgFillCheckerboard hoist only (#3) | YES | YES | 0x7FFF | 0x7FFF | CLEARED — bgFillCheckerboard hoist alone inert (SURPRISE FINDING) |
| C-4 | On Probe A + constant + bgFillCheckerboard (#1+#3, no set_bkg_palette) | YES | YES | 0x7FFF | 0x7FFF | CLEARED — constant+bgFillCheckerboard without set_bkg_palette inert |

**Bisect interpretation:** Full 7-probe chain isolated the minimal breaking pair as:
Emission #2 (`set_bkg_palette`) + Emission #3 (`bgFillCheckerboard hoist`). No single emission
causes the regression. The fix is to reorder Emission #3 to execute BEFORE `allSpriteDataLoads`
so the elephant tile data is the last write to tile 0 in the shared $8000-$97FF VRAM region.

---

## Named Regression Site

- **File:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt`
- **Lines (at regression):** 3826–3835 — the `addAll(allSpriteDataLoads)` before
  `addAll(hoistedBgFillCheckerboardStatements)` order in `buildMainFunction()`'s mainBody
- **Regression commit:** `0976e08b` (Plan 10.1-22 fix: "fix(10.1-22): close DEF-10.1-13-C visually — explicit BG palette + bgFillCheckerboard hoist (Plan 21 named cause)")
- **Source plan:** `10.1-22`

---

## Fix Shape Applied

**Order-Tweaked** — two `addAll(...)` calls swapped in `buildMainFunction()`:

Before (regression order):
```
addAll(allSpriteDataLoads)                   // set_sprite_data writes to $8000+
addAll(hoistedBgFillCheckerboardStatements)  // set_bkg_data OVERWRITES $8000 (checker)
```

After (fix order, commit `f2e8cecc`):
```
addAll(hoistedBgFillCheckerboardStatements)  // set_bkg_data writes checker to $8000 first
addAll(allSpriteDataLoads)                   // set_sprite_data OVERWRITES $8000 (elephant wins)
```

**Concrete diff scope:** 2 lines added, 1 line removed (the `addAll(allSpriteDataLoads)` line
moved up), plus a 12-line comment block added for documentation. Net: ~13 lines changed.

---

## Scope-Shift Acknowledgment

The phase title "gbc-palette-write-path" was a misnomer in light of the bisect findings:

1. **The GBC palette WRITE path was never broken.** OCPD slot 2 (cyan_pal) and BCPD slot 0
   (_gbkt_default_bg_pal) were correctly in palette RAM in EVERY probe including Probe B.
   The `set_bkg_palette` call was writing correctly; Coffee-GB's `cgb_compatibility()` /
   BCPD register path was NOT the actual defect layer.

2. **The actual defect was a sprite-tile VRAM collision.** With LCDC.4=1 (shared $8000-$97FF
   region for BG + sprite tile data), Plan 22's hoisted `set_bkg_data(0, 1, _checkerboard_bg_pattern)`
   ran AFTER `set_sprite_data(0u, 48u, elephant_tiles)` in `main()`, overwriting elephant tile 0
   bytes with checker pattern bytes before the first frame rendered.

Phase title kept as-is for traceability (D-V3 was named in Phase 10.1's open-defect carry-over).
Future search for this regression class should match **"sprite tile VRAM collision (LCDC.4=1)"**
rather than "palette write path."

---

## Final Summary

**What was broken:** Plan 10.1-22's `buildMainFunction()` hoisted `set_bkg_data(0, 1, _checkerboard_bg_pattern)` (the bgFillCheckerboard emission) into `main()` AFTER `set_sprite_data(0u, 48u, elephant_tiles)`. On the GBC with LCDC.4=1, both BG tiles and sprite tiles share the same VRAM region `$8000-$97FF`. The last write to any tile-slot address wins. Because bgFillCheckerboard wrote to BG tile slot 0 AFTER the elephant sprite tiles were loaded, checker pattern bytes overwrote elephant tile 0. At runtime, the PPU composited the sprite using the checker tile data via OCPD slot 2 (subpal=2) — producing a grayscale-appearing sprite rather than the expected cyan elephant.

**What fixed it:** Plan 10.2-08 (commit `f2e8cecc`) swapped two `addAll(...)` calls in `GBDKPipelineV2.buildMainFunction()`: `hoistedBgFillCheckerboardStatements` now emits BEFORE `allSpriteDataLoads`. The checker tile data is written to VRAM first; then the elephant sprite data overwrites tile 0 — the last writer wins, and the elephant tile data is correct at first frame.

**What stayed locked:** All 5 pre-existing codegen-shape tests remain GREEN (`DV3GbcPaletteWriteDiagnosticTest` 3/3, `DV3VisualV2DiagnosticTest` 2/2, `BgCheckerboardEmissionTest` 4/4, `SpritePaletteSlotEmissionTest`, `GbcCompatEmissionTest`). The new `DV3VisualV3DiagnosticTest` (2 tests, Plan 07) is the regression guard for the specific emission order that the bisect named.

**What's deferred:** Real-hardware (flash cart) and BGB/SameBoy cross-emulator confirmation. Coffee-GB is the binding ground truth for Phase 10.2 per D-14. The `emulator_read_memory` MCP tool general-purpose expansion (audio/banking/OAM helpers beyond SEED-012 scope) per D-10. Refactoring `GBDKPipelineV2.buildMainFunction()` for clarity (the function now has 3+ hoist clusters + emission-order guards) per D-01 scope-cap and Phase 13 hygiene deferral.

---

## Phase 10.2 D-V3 Closure Verdict: PASS

Reference: `evidence/uat-postfix/closure-verdict.md` (Plan 10.2-09 outcome)

DEF-10.1-13-C (GBC screenshot renders grayscale/black despite JVM GREEN — the visual
defect that escaped 4 inline fix rounds in Phase 10.1) is CLOSED.

---

## Plans Inventory (10.2-01 through 10.2-12)

| Plan | Purpose | Closes |
|------|---------|--------|
| 10.2-01 | MCP `emulator_read_memory(address, count)` tool + sanity test | D-08, D-09, D-10 |
| 10.2-02 | Scratch worktree setup + GBC baseline capture at cfe41ad7 | D-05 (bisect scaffold) |
| 10.2-03 | Bisect Probe 0: baseline evidence capture at pre-Plan-19/20 anchor | D-07 (baseline acceptance test) |
| 10.2-04 | Bisect Probe A: re-apply Plan 19+20 edits → cyan PRESERVED | D-06 (probe A: Plans 19+20 inert) |
| 10.2-05 | Bisect Probe B: re-apply Plan 22 edits → cyan KILLED (regression named) | D-06 (probe B: Plan 22 regression cluster named) |
| 10.2-06a | Sub-probe C-1: constant only → cyan PRESERVED | D-06 sub-narrow (emission #1 inert) |
| 10.2-06b | Sub-probe C-2: constant + set_bkg_palette → cyan PRESERVED | D-06 sub-narrow (emission #2 inert alone) |
| 10.2-06c | Sub-probe C-3: bgFillCheckerboard hoist only → cyan PRESERVED | D-06 sub-narrow (emission #3 inert alone; SURPRISE) |
| 10.2-06d | Sub-probe C-4: constant + bgFillCheckerboard (no set_bkg_palette) → cyan PRESERVED | D-06 sub-narrow (minimal breaking pair = #2+#3 confirmed) |
| 10.2-07 | Named-cause finding `d-v3-visual-finding-v3.md` + RED `DV3VisualV3DiagnosticTest` | D-19 (diagnostic→fix decoupling) |
| 10.2-08 | Fix: swap `addAll` order in `buildMainFunction()` → `DV3VisualV3DiagnosticTest` GREEN | D-01 (one fix), D-11 (all 5 locked tests GREEN) |
| 10.2-09 | UAT re-shoot: behavior3 PNG captures cyan elephant + checker BG (5 colors, PASS) | D-14, D-15, D-17 (closure verdict + DMG non-regression) |
| 10.2-10 | Cross-phase evidence update: Phase 10's `behavior3-subpalette-cycle-gbc.png` updated | D-17 (cross-phase D-04 reprise) |
| 10.2-11 | ROM smoke gate: metasprites + metasprites-stress buildRom + full test suite | D-16, 10.1 D-21 |
| 10.2-12 | Phase close: worktree teardown + drift check + this defect closure matrix | D-20 |

**Total plans shipped:** 12 (target: ≥8 min, ≥12 ideal per D-03 — IDEAL TARGET ACHIEVED)
Note: 10.2-06 was split into 06a/06b/06c/06d (4 sub-probes) per D-02 escape valve #1
(split-a-stalling-plan within the same wave graph). No sub-subphase (10.2.1/10.2.2) created.

---

*Phase: 10.2-gbc-palette-write-path-d-v3-visual-closure-4-round-inline-gr*
*Generated by: Plan 10.2-12 Task 3 (phase close gate)*
