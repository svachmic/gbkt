# DEFECT-1 + DEFECT-2 RED Baseline — Phase 12.6 Wave 1

**Captured:** 2026-05-25
**Plan:** 12.6-01 (Wave 1 baseline capture, Task 3)
**HEAD commit:** 28f2077ffeedcaaf876e112073d9fb037984b21b

**Purpose:** Lock the RED visual-evidence ground truth that Wave 4 (Plan 12.6-07) re-shoot MUST overwrite to GREEN. Per CLAUDE.md § "Verification Methodology — Visual Evidence Rule" (codified after Phase 07.4 round-2) and `feedback_visual_evidence_for_visual_truths.md`: variable-only assertions are **necessary but NEVER sufficient** for visual SCs — the runtime PNG IS the load-bearing closure evidence.

This file is intentionally a **redirection document** — it points at the existing Plan 12-23 round-2 PNGs (already committed) rather than re-shooting them, because the master tree at HEAD `28f2077f` still produces the RED baseline. Re-shooting now would just re-encode the same defective frames into a new path with no information gain.

---

## DEFECT-1 — Same-frame VRAM stomp

**Symptom:** The NextLevel card art is overwritten by the new level's tilemap in the same frame, so the card never visually renders. The player sees a brief flash of the level 2 tilemap where the card should be.

**Codegen citation:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt:2546-2567` — `buildMainLoopLevelSwitchGuardIfNeeded` emits BOTH `navigate_to_scene(SCENE_NEXTLEVEL)` AND `setup_current_level()` inside a single main-loop iteration with no vblank between them. The new-level tilemap write inside `setup_current_level()` runs BEFORE the LCD has rendered the card-scene's enter() output.

**Fix scope:** D-04 (Plan 12.6-02) trims the guard body to emit ONLY `navigate_to_scene(SCENE_NEXTLEVEL)`; D-03 (Plan 12.6-06) introduces a new `levelCardScene` DSL helper that owns the show-card → wait-for-Start → setup_current_level → navigate-to-gameplay lifecycle ACROSS multiple frames (mirroring the reference at `platformer_template/src/main.c:44-82` — see `reference-toolchain-notes.md` § "NextLevel card lifecycle").

**RED evidence (load-bearing per Visual Evidence Rule):**

- **PNG:** `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-5/02-nextlevel-card.png`
  - **What it shows:** world1-area2 tilemap (the new-level grass tilemap that should NOT be visible yet) instead of the NextLevel card's text/image art.
  - **What GREEN looks like:** card art (text reading something like "Level 2" or whichever card art the platformer-template DSL emits), NOT a tilemap row.
- **Sidecar metadata:** `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-5/02-nextlevel-card.json`
- **Variable trace:** `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-5/anchor5-variables.txt` (contains inline `codegen_defect_1` annotation)

---

## DEFECT-2 — `_playerX` preserved across level switch

**Symptom:** After the level 1 → level 2 transition, the player appears on world2-area1 (rocky tileset, which is level 3) instead of world1-area2 (grass tileset, which is level 2). The level-end trigger re-fires on the first gameplay frame of level 2 because `_playerX` retained the right-edge value from level 1, advancing `_next_level` past level 2 to level 3.

**Codegen citation:**

- `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt:1110-1126` — level-end trigger emission (UNTOUCHED by this phase; the trigger is correct, the bug is upstream). Per `PATTERNS.md` (also frozen-by-RESEARCH), this is a regression-guard zone: changing it would expand blast radius.
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt:2434-2464` — `setup_current_level()` per-zone case body. This is the FIX site: D-06 (Plan 12.6-05) extends each case body to write `_playerX = ((INT16)_level_spawn_x[N]) << 4; _playerY = ((INT16)_level_spawn_y[N]) << 4; _playerVx = 0; _playerVy = 0;` AFTER the existing `_bkg_tiles_load_banked(...)` call.

**Fix scope:** D-06 (Plan 12.6-05 codegen) + D-07 (Plan 12.6-03/04 DSL surface for `spawn(x, y)`) + D-08 (Plan 12.6-07 platformer-template `spawn(40u, 120u)` on each zone).

**RED evidence (load-bearing per Visual Evidence Rule):**

- **PNG:** `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-5/03-level-2.png`
  - **What it shows:** world2-area1 rocky tileset (level 3) — the level-end trigger fired again same-frame, advancing past level 2.
  - **What GREEN looks like:** world1-area2 grass tilemap (level 2). The player should land at spawn(40, 120) — left edge, on the visible ground row.
- **Sidecar metadata:** `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-5/03-level-2.json`
- **Variable trace:** `.../anchor5-variables.txt` (contains inline `codegen_defect_2` annotation)

---

## Regression guard — `01-near-end.png`

**Role:** This anchor-5 PNG captures the player at the right edge of level 1, BEFORE the level-switch trigger fires. It is **CORRECT for this frame** at the RED baseline — the player on level 1 with `_current_level == 0` (or whatever value level 1 has) is the expected pre-trigger state.

**Wave 4 regression-guard contract:** Per CONTEXT D-10 gate (c), Plan 12.6-07's re-shoot of this anchor MUST remain **byte-identical** (or visually-indistinguishable; the JSON sidecar's perceptual-diff baseline tolerates emulator non-determinism but the tilemap row pattern should be unchanged). The frame's visual content does not depend on either DEFECT-1 or DEFECT-2 fixes — it captures pre-trigger state. If this PNG changes materially after the fix lands, that signals a regression elsewhere in the codegen (e.g., spawn-table write happens too early and overwrites `_playerX` mid-level-1).

**RED-baseline-that-stays-baseline:**

- **PNG:** `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-5/01-near-end.png`
- **Sidecar:** `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-5/01-near-end.json`

---

## Wave 4 re-shoot target dir

Plan 12.6-07 (D-08 platformer-template migration + D-10 visual closure protocol) re-runs the `anchor5LevelSwitch` UAT after the fix and writes 3 fresh PNGs to:

```
.planning/phases/12.6-main-loop-level-switch-codegen-fix-phase-12-6/evidence/uat-screenshots/anchor-5/
├── 01-near-end.png        (must remain visually equivalent to Plan 12-23 baseline above)
├── 02-nextlevel-card.png  (must FLIP RED→GREEN — card art visible, not tilemap)
├── 03-level-2.png         (must FLIP RED→GREEN — world1-area2 grass, not world2-area1 rocky)
├── 01-near-end.json       (sidecar)
├── 02-nextlevel-card.json (sidecar)
├── 03-level-2.json        (sidecar)
└── anchor5-variables.txt  (post-fix variable trace; assertions tightened per D-09 from `>= 1` to `== 1`)
```

This directory does NOT exist yet — it is reserved here by reference so Plan 12.6-07's executor has an unambiguous write target. The directory will be created by the UAT re-shoot itself (Plan 12.6-07 Task body), not pre-created by this Wave 1 plan.

---

## Visual Evidence Rule — necessary-but-not-sufficient discipline

**Restated for this phase:** Per CLAUDE.md § "Verification Methodology — Visual Evidence Rule":

> Variable assertions like `assertVariable("_current_level", 1)` prove that the codegen wrote a value at one point in scene-enter — they do NOT prove the value is visually reflected by the time the player sees the screen. A subsequent op (e.g., a user-authored `clear()` lowering to `cls()`) can wipe the visual outcome while leaving the variable intact.

**For Phase 12.6 specifically:** Plan 12.6-08's verification suite includes BOTH:

1. JVM-tier assertion tightening (D-09): `assertVariable("_current_level", == 1)` (was `>= 1`). **Necessary** — locks the codegen contract at the data level.
2. Visual re-shoot of the 3 anchor-5 PNGs (D-10) confirming card art renders + level 2 grass renders. **Sufficient** — proves the visual surface is in the desired state.

JVM-tier alone CANNOT close this phase. The Visual Evidence Rule is the explicit guard against the bug-class that Phase 07.4 round-2 demonstrated: codegen could write `_current_level == 1` and the runtime ROM could STILL render the wrong tilemap (because a downstream op stomps VRAM). DEFECT-1 is literally an instance of this bug-class — the variable state was correct, the visual outcome was not.

`feedback_visual_evidence_for_visual_truths.md` (the memory rule codifying the lesson from Phase 07.4 round-2) is the project-level enforcement of this discipline.

---

## Cross-references

| Field | Source |
|-------|--------|
| Phase 12-23 round-2 SUMMARY (defect catalog + escalation decision) | `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-23-SUMMARY.md` |
| Phase 12.6 CONTEXT (D-10 visual closure protocol + D-11 Visual Evidence Rule compliance) | `.planning/phases/12.6-main-loop-level-switch-codegen-fix-phase-12-6/12.6-CONTEXT.md` |
| Sibling Wave 1 file — reference toolchain notes (D-02 + D-08 source-of-truth) | `.planning/phases/12.6-main-loop-level-switch-codegen-fix-phase-12-6/evidence/reference-toolchain-notes.md` |
| Sibling Wave 1 file — pre-fix SHA-256 manifest (D-14 byte-identity baseline) | `.planning/phases/12.6-main-loop-level-switch-codegen-fix-phase-12-6/evidence/pre-fix-rom-sha256.txt` |
