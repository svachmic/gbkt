# Baseline Evidence Verdict — cfe41ad7 (pre-Plan-19/20)

## Summary Table

| Signal | Value |
|--------|-------|
| CYAN in PNG | YES |
| CHECKER in PNG | YES |
| Distinct colors | 5 |
| BCPD any slot non-zero | true |
| BCPD slot 0 first-color | 0x7FFF |
| OCPD slot 2 first-color (cyan_pal) | 0x7FFF |
| LCDC | 0xC3 |
| OVERALL | BOTH PATHS WORK — cyan sprite + checker BG (confirmed working state) |

## Context

- **Baseline commit:** cfe41ad7 (fix(10.1-18): swap bgFillCheckerboard literal to 4-row-period)
- **Original plan anchor:** cbe81d29 — but that commit doesn't build. See deviation note below.
- **Actual anchor:** cfe41ad7 is the pre-Plan-19/20 buildable baseline
- **GBC mode:** true (ROM built with GBC compatibility flag)

## Deviation Note

Plan 10.2-03 specified cbe81d29 as the baseline commit but that commit is a docs tracking commit
(between Plans 10.1-08 and 10.1-09). At that state, Plan 10.1-07 (WR-02) had already added the
'extern const metasprite_t*' declaration to game.h, but the matching '#include <gbdk/metasprites.h>'
wasn't added to game.h until commit 20fdd8e8 (which comes after cbe81d29). The ROM at cbe81d29
cannot compile because lcc errors at game.h:36 with 'Syntax error: metasprite_t'.

cfe41ad7 is used instead: it includes the metasprites.h header fix AND the Plan 18 4-row-period
BG checker literal, but does NOT include Plans 19/20/22's bootstrap-order or palette-hoisting
changes — it is the correct "cyan baseline before the regression" anchor.

## Sprite Tile-Order Note (mirrorDedup ordering quirk)

User UAT at this baseline flagged the elephant sprite tile-order as visually wrong
(same defect class seen during Phase 10.1's B&W debugging). Root cause: mirrorDedup
(Plan 10.1-16, commit 607a3e64) is the metasprite tile-layout fix, but it integrated
on the branch AFTER Plan 10.1-20's palette-bootstrap fix (7b86049f) via worktree-merge
ordering. So at cfe41ad7, mirrorDedup is NOT yet applied → metasprite tile-flip
arithmetic emits wrong-ordered tiles. There is no naturally-existing commit that has
both mirrorDedup AND lacks Plans 10.1-19/20/22's palette changes.

**Decision (user, 2026-05-19):** keep cfe41ad7 as anchor. Tile-order will be
consistently wrong across all probes (baseline + A + B + C-1/2/3), so it does NOT
confound the COLOR signal. D-V3 is about the GBC palette write path, not tile
geometry. Subsequent probes evaluate cyan-present + checker-present + BCPD/OCPD
slot signals as the decisive bisect evidence.

## Bisect Implication

At cfe41ad7 (pre-Plan-19/20): BOTH sprite-palette (cyan) AND BG-palette (checker) work.
After Plans 10.1-19/20/22 (current HEAD): ALL BLACK — both paths broken.

This means Plans 10.1-19/20/22 together broke BOTH paths. The bisect probes in Plans
10.2-04/05/06 will re-apply each plan's changes in isolation to identify which plan
(or combination) introduced the black-screen regression.

Expected probe grid:
| Probe | Cyan? | Checker? | Verdict |
|-------|-------|----------|---------|
| Baseline cfe41ad7 | YES | YES | GOOD — both work |
| After Plan 19 (Probe A) | ? | ? | Names if Plan 19 kills either |
| After Plan 20 (Probe B) | ? | ? | Names if Plan 20 kills either |
| After Plan 22 (Probe C) | ? | ? | Names regression site |
