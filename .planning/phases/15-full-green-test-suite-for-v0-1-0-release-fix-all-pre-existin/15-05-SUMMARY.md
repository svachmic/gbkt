---
phase: 15-full-green-test-suite-for-v0-1-0-release-fix-all-pre-existin
plan: 05
subsystem: platformer-template-test
tags: [metasprite-geometry, png2asset, hflip, facing, d-03, provably-stale-assertion]
requires: [15-01, 15-04]
provides: [platformer-template-test-green]
affects: [15-06]
tech-stack:
  added: []
  patterns: [on-disk-asset-read, sprite-region-diff, oam-xflip-assertion]
key-files:
  created:
    - .planning/phases/15-full-green-test-suite-for-v0-1-0-release-fix-all-pre-existin/evidence/diagnosis/platformer.md
    - .planning/phases/15-full-green-test-suite-for-v0-1-0-release-fix-all-pre-existin/evidence/platformer-facing-right.png
    - .planning/phases/15-full-green-test-suite-for-v0-1-0-release-fix-all-pre-existin/evidence/platformer-facing-left.png
  modified:
    - gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlayerMetaspriteGeometryTest.kt
    - gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate128UatTest.kt
    - gbkt-examples/platformer-template/build.gradle.kts
key-decisions:
  - "PlayerMetaspriteGeometryTest repointed to on-disk png2asset sprites/player.c / player_metasprite0 / METASPR_ITEM (D-04 one-token rename was under-scoped — the array left main.c). Runs GREEN executed (tests=2 skipped=0)."
  - "anchor4 hflip >10% GLOBAL gate was arithmetically unreachable (live: settled-camera flip = 2.2% full-frame, 45% sprite-region). Re-architected to a sprite-region diff (>=20%) + OAM xFlip bit + facingRot==3. No threshold lowered."
  - "PlatformerTemplateUatTest was already green (not modified). No gbkt-backend-gbdk codegen edited."
requirements-completed: [REQ-6]
duration: 34 min
completed: 2026-06-09
---

# Phase 15 Plan 05: platformer-template suite green (3 classes) Summary

Drove all three platformer-template test classes green: repointed the metasprite geometry test to
the png2asset-native asset, and re-architected the anchor4 visible-hflip measure to a sprite-region
diff backed by the OAM xFlip bit — with live D-03 screenshot evidence and no threshold weakening.

- **Duration:** 34 min · **Tasks:** 3 · **Files:** 3 created (incl. 2 screenshots), 3 modified

## What was done

**Task 1 — PlayerMetaspriteGeometryTest repoint (F3/F4, provably-stale).** The player metasprite is
Path A png2asset-native: `player_metasprite0[]` `METASPR_ITEM(...)` lives in on-disk
`build/gbkt/generated/sprites/player.c`, not `main.c` (Pitfall 1; D-04 one-token rename
under-scoped). Repointed SOURCE (on-disk read + `Assumptions.assumeTrue` graceful skip), SYMBOL
(`player_metasprite0`), PARSER (`METASPR_ITEM` regex), and wired `tasks.test { dependsOn("convertSprites") }`.
Geometry cluster assertions preserved. Runs GREEN executed (`tests=2 skipped=0 failures=0`).

**Task 2 — Live D-03 facing diagnosis (F7).** Drove the ROM live (MCP, GBC) at a settled camera:
full-frame facing diff **2.20%** but sprite-region diff **45.36%** (bbox = the player sprite, zero
background diff); OAM `xFlip=true` facing-left, `facingRot=3`, duck visibly mirrored. The `>10%`
GLOBAL gate is arithmetically unreachable for a 3.3%-of-frame sprite (Pitfall 4). Verdict =
provably-stale-assertion. Screenshots in evidence/.

**Task 3 — anchor4 re-architecture (F7 fix).** Replaced the global `VisualDiff(>10%)` gate with a
settled-camera **sprite-region diff (≥20%, live ~45%)** + direct **OAM xFlip** assertion
(facing-right not flipped / facing-left flipped), triple-locked with `facingRot==3`. Removed the
now-unused `VisualDiff` import. `./gradlew :gbkt-examples:platformer-template:test` → 0 failures
across all 3 classes (geometry 2/0, 128UatTest 6/0, UatTest 5/0).

## Deviations from Plan

**[D-04 under-scoping — recorded per plan]** D-04's "one-token grep rename" for the geometry test is
provably insufficient (the array left main.c); the 4-part repoint was applied as the plan's
`d04_deviation_note` anticipated. `PlatformerTemplateUatTest.kt` was listed in files_modified but
needed NO change (already green) — not modified.

**Total deviations:** the D-04 under-scoping (anticipated by the plan). **Impact:** none adverse;
codegen-touch = NONE (test + build-wiring only; D-02 input for plan 06).

## Issues Encountered

None unresolved.

## Next

Wave 3 complete. Ready for Wave 4 (15-06: close release gate, consolidate ledger, FINAL-GREEN).

## Self-Check: PASSED

- [x] `./gradlew :gbkt-examples:platformer-template:test` 0 failures across all 3 classes
- [x] PlayerMetaspriteGeometryTest repointed (player_metasprite0, sprites/player.c, METASPR_ITEM); no sprite_player_frame_0 / main.c refs; runs executed not skipped
- [x] Live GBC-mode D-03 screenshots in evidence/ (facing right + left)
- [x] >10% global facing threshold NOT lowered — measure re-architected (sprite-region + OAM xFlip); no assertion deleted
- [x] evidence/diagnosis/platformer.md filled for all 3 classes + D-04 deviation; codegen-touch NONE
- [x] `git log --grep="15-05"` returns 3 commits
