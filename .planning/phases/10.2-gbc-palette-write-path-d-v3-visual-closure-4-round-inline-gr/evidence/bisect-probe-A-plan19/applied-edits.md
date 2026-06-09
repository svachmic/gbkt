# Probe A — Applied Edits Log

## Probe Identity

- **Probe:** A (Plan 19+20 edits bundled)
- **Base anchor:** cfe41ad7 (pre-Plan-19/20 buildable baseline)
- **Source commit:** 7b86049f (fix(10.1-20): close DEF-10.1-13-C)
- **Probe commit in scratch/bisect:** 2767fab7

## Source Plan Citations

- `10.1-19-SUMMARY.md` — Plan 19 diagnostic: named root cause (bootstrap-order mismatch) + committed RED DV3GbcPaletteWriteDiagnosticTest.kt (3 tests). Plan 19 was DIAGNOSTIC only — no GBDKPipelineV2.kt change landed in Plan 19.
- `10.1-20-SUMMARY.md` — Plan 20 fix: applied Plan 19's named cause to GBDKPipelineV2.buildMainFunction(). The actual codegen change (DISPLAY_OFF prepend + sprite-palette hoist + LCDC reorder) is in commit 7b86049f.

## Why Bundled as "Plan 19+20 Union"

Plan 19 is a diagnostic-only plan — it committed the RED test class (`DV3GbcPaletteWriteDiagnosticTest.kt`) but made NO changes to GBDKPipelineV2.kt. Plan 20 is the fix plan that actually applied the bootstrap-order changes. Phase 10.2 D-06 states probe order = chronological (Plan 19 → Plan 20 → Plan 22). Because Plan 19's codegen-relevant content is zero (no GBDKPipelineV2.kt change), this bisect probe bundles Plans 19+20 into a single probe using 7b86049f (Plan 20's landing commit) as the restore source.

This bundling is explicitly noted in CONTEXT.md D-06: "plan 19's bootstrap-order changes" = the union. Plans 04/05/06 apply 19+20 / 22 / sub-splits.

## Files Restored

From commit `7b86049f` via `git checkout 7b86049f -- <path>`:

| File | Status | Lines changed |
|------|--------|---------------|
| `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` | Modified | +90 / -22 |
| `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/BgCheckerboardEmissionTest.kt` | Modified | +52 / -63 |
| `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/SpriteRenderingTest.kt` | Modified | +23 / -1 |

**Note:** `DV3GbcPaletteWriteDiagnosticTest.kt` was NOT restored because it was ALREADY PRESENT in cfe41ad7. Plan 19's test commit (46947fa6) predates the bisect anchor (cfe41ad7) in the git history — `git merge-base --is-ancestor cfe41ad7 46947fa6` returned false, confirming 46947fa6 is before cfe41ad7.

## Net Diff Line Count

From `git diff cfe41ad7 7b86049f --stat`:

```
3 files changed, 102 insertions(+), 63 deletions(-)
```

## Summary of Changes Applied

### GBDKPipelineV2.kt — bootstrap-order refactor

1. **GAP-1 fix:** Prepend `DISPLAY_OFF;` at main() entry (before cgb_compatibility())
2. **GAP-2 fix:** Hoist start-scene `set_sprite_palette(0..3u, ...)` calls into main() BEFORE DISPLAY_ON (duplication-not-relocation: also kept in play_enter())
3. **GAP-3 fix:** Reorder LCDC — emit `SHOW_BKG; SHOW_SPRITES; SPRITES_8x8;` BEFORE `DISPLAY_ON`; DISPLAY_ON is now LAST bootstrap macro

This brings main() in line with GBDK's reference `metasprites.c:160-194` 9-step boot sequence.

### BgCheckerboardEmissionTest.kt — updated for new emission structure

Assertion updates to match the post-Plan-20 emission order in generated C.

### SpriteRenderingTest.kt — assertion inversion

`test show sprites in main`: assertion `showSpritesIdx > displayOnIdx` (encoding GAP-3 defect) → `showSpritesIdx < displayOnIdx` (encoding the correct reference-aligned order). This was a Rule 1 deviation in Plan 20 — the test was locking the bug.

## Restore Mechanism

`git checkout <commit> -- <path>` selective restore (content-based, not cherry-pick). This copies the file state at the source commit into the scratch/bisect working tree, independent of the base commit's ancestry. The approach works correctly whether the base is cfe41ad7 or cbe81d29.

## Deviation from Plan Spec

### cfe41ad7 used instead of plan-specified cbe81d29 as the base anchor

The 10.2-04-PLAN.md Task 1 says to "Verify HEAD is still at `cbe81d29`" — this verification would FAIL because the actual base anchor is `cfe41ad7`.

**Reason:** Plan 10.2-03 deviated from the spec (committed as b8cc3398 and documented in the baseline verdict.md). `cbe81d29` fails lcc compilation because `game.h` at that commit references `metasprite_t` without `#include <gbdk/metasprites.h>` (added in 20fdd8e8, which comes after cbe81d29). cfe41ad7 is the earliest pre-Plan-19/20 buildable commit.

**Impact on bisect validity:** The selective restore from 7b86049f is content-based and applies the same diff regardless of the base commit. The probe produces the same signal (Plan 19+20 edits applied → does cyan persist?). The deviation does NOT affect the bisect outcome.

**Documentation:** This deviation was pre-approved in Plan 10.2-03's human-verify checkpoint (user verdict: "Keep cfe41ad7; document + proceed"). This plan inherits that decision and documents it here per the Plan 04 spec requirement.

### Probe naming clarification

- Probe A = Plans 19+20 bundled (this plan, 10.2-04)
- Probe B = Plan 22 on top (next plan, 10.2-05)
- Probe C-1/2/3 = Plan 22 sub-splits if needed (10.2-06a/06b/06c)

---

## Drift Check after Probe A

*Appended post-probe (Task 3 of this plan)*

### `git reflog --all | head -50` (main checkout)

```
f60d468e refs/heads/feat/d_and_d_gaps@{0}: commit: feat(10.2-04): capture Probe A evidence triplet
6b292517 refs/heads/feat/d_and_d_gaps@{1}: commit: docs(10.2-04): Probe A applied-edits.md
2767fab7 worktrees/bisect/HEAD@{0}: commit: probe-A: apply Plan 19+20 edits onto cfe41ad7 baseline (Phase 10.2 bisect)
f19089c1 refs/heads/feat/d_and_d_gaps@{2}: commit: docs(state): record 10.2-03 complete
...
cfe41ad7 worktrees/bisect/HEAD@{1}: (original worktree anchor)
```

Grep for "probe-A" in reflog: `2767fab7 worktrees/bisect/HEAD@{0}` only.
The probe-A commit (2767fab7) appears ONLY in `worktrees/bisect/HEAD` — NOT in `feat/d_and_d_gaps`. No leak.

### `git status` (main checkout)

Output: CLEAN (no output from `git status --short` — zero untracked or modified files leaked from worktree).

### `git log --oneline -10 feat/d_and_d_gaps`

```
f60d468e feat(10.2-04): capture Probe A evidence triplet — Plan 19+20 edits REGRESSION-NAMED: no
6b292517 docs(10.2-04): Probe A applied-edits.md — Plan 19+20 selective restore from 7b86049f onto cfe41ad7
f19089c1 docs(state): record 10.2-03 complete — Wave 3 baseline triplet captured
17b7b481 docs(10.2-03): SUMMARY.md — baseline triplet captured + checkpoint resolved
b8cc3398 docs(10.2-03): annotate verdict.md with mirrorDedup ordering quirk
...
```

No `probe-A` commit in `feat/d_and_d_gaps`. The `2767fab7` probe-A commit lives ONLY on scratch/bisect detached HEAD.

### Verdict: NO LEAKAGE DETECTED

Pitfall 2 (worktree commit leakage) did NOT occur. The probe-A commit is isolated to `scratch/bisect`'s detached HEAD. Main checkout is clean. Phase can proceed to Plan 05 (Probe B).
