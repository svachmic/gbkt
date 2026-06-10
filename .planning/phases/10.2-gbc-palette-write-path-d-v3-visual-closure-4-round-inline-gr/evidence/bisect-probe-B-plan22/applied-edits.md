# Probe B — Applied Edits Log

## Probe Identity

- **Probe:** B (Plan 21+22 edits)
- **Base anchor:** cfe41ad7 (pre-Plan-19/20 buildable baseline, same as Probe A)
- **Probe A commit:** 2767fab7 (Plans 19+20 selective restore from 7b86049f)
- **Source commit for Probe B restore:** 0976e08b (fix(10.1-22): close DEF-10.1-13-C visually)
- **Probe B commit in scratch/bisect:** 0d4e4bb4

## Source Plan Citations

- `10.1-21-SUMMARY.md` — Plan 21 diagnostic: named 4th-layer root cause (Coffee-GB BG palette
  RAM zero-init + missing `set_bkg_palette()` emission) + committed RED
  `DV3VisualV2DiagnosticTest.kt` (2 tests gate the Plan 22 GREEN fix). DIAGNOSTIC ONLY —
  no GBDKPipelineV2.kt change in Plan 21.
- `10.1-22-SUMMARY.md` — Plan 22 fix: applied Plan 21's named cause. 3 emission additions
  in `GBDKPipelineV2.buildMainFunction()`:
  1. `_gbkt_default_bg_pal[4]` constant declaration at file scope
  2. `set_bkg_palette(0u, 1u, _gbkt_default_bg_pal)` call in main() AFTER sprite palettes,
     BEFORE DISPLAY_ON
  3. Hoist bgFillCheckerboard RawOp from play_enter into main() between set_sprite_data
     and SHOW_BKG (duplication-not-relocation)
  Landing commit: 0976e08b.

## Why Plan 21+22 Bundled into Single Probe

Same logic as Probe A bundling Plans 19+20: Plan 21 is diagnostic-only (no GBDKPipelineV2.kt
change). The actual codegen change is in Plan 22's commit 0976e08b. The restore source is
0976e08b, which brings GBDKPipelineV2.kt to its Plan 22 state.

Plan 21's test file `DV3VisualV2DiagnosticTest.kt` IS restored separately (it was not in the
Probe A state — it originated in commit 2359f529, which is between 7b86049f and 0976e08b).

## Files Restored

From commit `0976e08b` via `git checkout 0976e08b -- <path>`:

| File | Status | Lines changed (vs 7b86049f) |
|------|--------|-----------------------------|
| `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` | Modified | +84 / -4 |
| `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/DV3VisualV2DiagnosticTest.kt` | Created (new file) | +253 / 0 |

**Total:** 2 files changed, 333 insertions(+), 4 deletions(-)

## Summary of Changes Applied (on top of Probe A)

### GBDKPipelineV2.kt — 3 emission additions (Plan 22 fix)

1. **Emission #1:** `const palette_color_t _gbkt_default_bg_pal[4] = {0x7FFF, 0x56B5, 0x294A, 0x0000};`
   declared at file scope in paletteDataRaw block alongside user-declared palettes.
   GBC-conditional (gate: `gbcTarget != GbcTarget.DMG`).

2. **Emission #2:** `set_bkg_palette(0u, 1u, _gbkt_default_bg_pal);` inserted in main() body
   AFTER the 4× sprite-palette block (Plan 20 hoisted) and BEFORE DISPLAY_ON.
   This writes GBC palette RAM (BCPD) directly via BCPS/BCPD register pair — compensates
   for Coffee-GB's `cgb_compatibility()` not reaching BCPD.

3. **Emission #3:** bgFillCheckerboard RawOp (`fill_bkg_rect + set_bkg_data`) hoisted from
   play_enter into main() between set_sprite_data and SHOW_BKG.
   Duplication-not-relocation: RawOp also preserved in play_enter().
   Reason: set_sprite_data (Plan 20 hoist) overwrites BG tile-0 with elephant bytes;
   bgFillCheckerboard must run AFTER set_sprite_data and BEFORE SHOW_BKG to re-write
   $8000 with checker bytes before the PPU composites the first frame.

### DV3VisualV2DiagnosticTest.kt — Plan 21's RED gate (now GREEN after Plan 22)

2 test cases restored from Plan 21 commit (2359f529, within 7b86049f..0976e08b range):
- `main body contains set_bkg_palette before DISPLAY_ON`
- `main body contains hoisted set_bkg_data and fill_bkg_rect before DISPLAY_ON`
These were RED at Plan 21 commit; Plan 22 (GBDKPipelineV2.kt change) flipped them GREEN.
In scratch/bisect (Probe B state), these tests should pass GREEN.

## GBDKPipelineV2.kt Match vs Main Checkout HEAD

`diff` between scratch/bisect GBDKPipelineV2.kt and main checkout HEAD shows minor
post-Plan-22 improvements:
- stderr routing for println warning (cosmetic, no behavioral impact)
- null-safety refactors for `bankSlot!!.bank` access (defensive, no behavioral impact)

These differences are NOT bisect-relevant (Plan 22 emissions are present in both). The
core emissions (`_gbkt_default_bg_pal`, `set_bkg_palette`, bgFillCheckerboard hoist) are
byte-identical between scratch/bisect (0976e08b state) and main HEAD.

**Claim:** scratch/bisect Probe B state contains the same GBC palette bootstrap emissions
as the main checkout HEAD. If Probe B produces solid black, those post-Plan-22 cosmetic
commits did NOT change the visual outcome.

## Deviation from Plan Spec

### cfe41ad7 base anchor (inherited from Probe A / Plan 03)

Same deviation as documented in Probe A's applied-edits.md. Plan 10.2-03 established
cfe41ad7 as the bisect anchor (cbe81d29 fails lcc compilation). This plan inherits that
decision. The selective restore from 0976e08b is content-based and applies the same
diff regardless of the base anchor. Bisect validity is unaffected.

---

## Drift check after Probe B

*Section to be appended after Task 3 completion.*

### `git reflog --all | head -50` (main checkout, after Probe B)

```
8f3b8c85 refs/heads/feat/d_and_d_gaps@{0}: commit: feat(10.2-05): capture Probe B evidence triplet
55a2e967 refs/heads/feat/d_and_d_gaps@{1}: commit: docs(10.2-05): Probe B applied-edits.md
0d4e4bb4 worktrees/bisect/HEAD@{0}: commit: probe-B: apply Plan 21+22 edits onto Probe A
436f2411 refs/heads/feat/d_and_d_gaps@{2}: commit: docs(state): record 10.2-04 complete
...
2767fab7 worktrees/bisect/HEAD@{1}: commit: probe-A: apply Plan 19+20 edits
cfe41ad7 worktrees/bisect/HEAD@{2}: (original worktree anchor)
```

Grep for "probe-B" in `git reflog feat/d_and_d_gaps`: **ZERO MATCHES — NO LEAKAGE**

The probe-B commit (0d4e4bb4) appears ONLY in `worktrees/bisect/HEAD` — NOT in
`feat/d_and_d_gaps`. The probe-A commit (2767fab7) also remains isolated.

### `git status` (main checkout after Probe B commits)

Output: CLEAN (only untracked `behavior3-frame61.png` leftover — deleted before this commit)

### `git log --oneline -10 feat/d_and_d_gaps`

```
8f3b8c85 feat(10.2-05): capture Probe B evidence triplet — Plan 22 edits REGRESSION-NAMED: yes
55a2e967 docs(10.2-05): Probe B applied-edits.md
436f2411 docs(state): record 10.2-04 complete — Probe A REGRESSION-NAMED: no
a4381405 docs(10.2-04): SUMMARY.md — Probe A REGRESSION-NAMED: no
cae8f5c9 docs(10.2-04): complete Probe A drift check
f60d468e feat(10.2-04): capture Probe A evidence triplet
...
```

No `probe-B` or `probe-A` commits in `feat/d_and_d_gaps`. Both probe commits
(0d4e4bb4, 2767fab7) live ONLY on scratch/bisect detached HEAD.

### Verdict: NO LEAKAGE DETECTED

Pitfall 2 (worktree commit leakage) did NOT occur for Probe B. The probe-B commit
is isolated to `scratch/bisect`'s detached HEAD. Main checkout is clean.

