---
phase: 22-golden-screenshot-and-evidence-storage-overhaul
plan: "05"
subsystem: testing
tags: [goldens, png, platformer-template, visual-uat, byte-identity, sha256]

requires:
  - phase: 22-03
    provides: goldens/ skeleton and gbkt.updateGoldens wiring in platformer-template
  - phase: 21-codegen-fixes-platformer-and-remaining-seeds
    provides: 15 user-signed-off GBC platformer anchor PNGs (binding baseline)
  - phase: 20-codegen-fixes-banks-and-sprite-transparency
    provides: 1 tRNS player-transparency PNG (FIX-04 baseline)

provides:
  - 16 GBC platformer-template golden PNGs in src/test/resources/goldens/platformer-template/
  - Byte-identical (sha256-proven) migration of all user-blessed baselines

affects: [22-07]

tech-stack:
  added: []
  patterns:
    - "raw cp byte-copy for golden migration (never ImageIO re-encode)"
    - "sha256 equality check per pair to prove byte-identity"

key-files:
  created:
    - gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor1-title.png
    - gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor1-gameplay.png
    - gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor2-grounded.png
    - gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor2-mid-jump.png
    - gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor2-landed.png
    - gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor3-initial.png
    - gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor3-scrolled.png
    - gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor4-walk-frame-0.png
    - gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor4-walk-frame-1.png
    - gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor4-walk-frame-2.png
    - gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor4-facing-left.png
    - gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor5-last-gameplay.png
    - gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor5-nextlevel-flip.png
    - gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor5-nextlevel-card.png
    - gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor5-level-2.png
    - gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/platformer-player-transparency.png
  modified: []

key-decisions:
  - "Raw byte copy (cp) only — ImageIO re-encode strictly forbidden to preserve user-blessed GBC baseline bytes"
  - "Per-file sha256 equality assertion as migration correctness proof (T-22-05 mitigation)"
  - "anchor4 walk frames stored as full-frame goldens; OAM compareRegion hflip gating is plan 22-07 concern"

patterns-established:
  - "Golden migration pattern: cp source target && assert sha256(source)==sha256(target)"

requirements-completed: [FIX-07]

duration: 2min
completed: "2026-06-14"
---

# Phase 22 Plan 05: Platformer-Template GBC Goldens Migration Summary

**16 user-blessed GBC platformer-template anchor PNGs migrated byte-identically (sha256-proven) from Phase 21/20 evidence into test resources goldens directory**

## Performance

- **Duration:** 2 min
- **Started:** 2026-06-14T21:40:53Z
- **Completed:** 2026-06-14T21:43:00Z
- **Tasks:** 1
- **Files modified:** 16

## Accomplishments

- Created `gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/` with 16 PNG goldens
- Migrated 15 Phase 21 binding GBC baselines (user sign-off after 21-07 DMG-capture false-positive fix)
- Migrated 1 Phase 20 FIX-04 tRNS player-transparency baseline
- All 16 files sha256-verified byte-identical to their Phase 21/20 sources

## Task Commits

1. **Task 1: Byte-identically copy 16 platformer anchors into goldens + sha256 prove identity** - `21bc6961` (chore)

**Plan metadata:** (pending docs commit)

## Files Created/Modified

- `gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor1-title.png` - Title screen (Phase 21 anchor-1/01)
- `gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor1-gameplay.png` - Gameplay entry (Phase 21 anchor-1/02)
- `gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor2-grounded.png` - Player grounded (Phase 21 anchor-2/01)
- `gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor2-mid-jump.png` - Player mid-jump (Phase 21 anchor-2/02)
- `gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor2-landed.png` - Player landed (Phase 21 anchor-2/03)
- `gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor3-initial.png` - Camera initial (Phase 21 anchor-3/01)
- `gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor3-scrolled.png` - Camera scrolled (Phase 21 anchor-3/02)
- `gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor4-walk-frame-0.png` - Walk animation frame 0 (Phase 21 anchor-4/01)
- `gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor4-walk-frame-1.png` - Walk animation frame 1 (Phase 21 anchor-4/02)
- `gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor4-walk-frame-2.png` - Walk animation frame 2 (Phase 21 anchor-4/03)
- `gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor4-facing-left.png` - Facing left (Phase 21 anchor-4/04)
- `gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor5-last-gameplay.png` - Pre-level-end gameplay (Phase 21 anchor-5/00)
- `gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor5-nextlevel-flip.png` - Next level flip (Phase 21 anchor-5/01)
- `gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor5-nextlevel-card.png` - Next level card (Phase 21 anchor-5/02)
- `gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/anchor5-level-2.png` - Level 2 entry (Phase 21 anchor-5/03)
- `gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/platformer-player-transparency.png` - tRNS transparency (Phase 20 FIX-04)

## SHA256 Hashes (byte-identity proof)

| Golden File | SHA256 |
|-------------|--------|
| anchor1-title.png | 5f6f18126d5241974450e64c63f1eb6aebb503ffccba23a6dcd9a9fa12adab3e |
| anchor1-gameplay.png | d5a4beb3dac37446205275c9bc7426371c39aec2c47744305d6b9fd5be358234 |
| anchor2-grounded.png | d5a4beb3dac37446205275c9bc7426371c39aec2c47744305d6b9fd5be358234 |
| anchor2-mid-jump.png | c2dacdba6afa2b8186047a9a6a55aa1c949cd2390d9933338c369ec0968405ee |
| anchor2-landed.png | d5a4beb3dac37446205275c9bc7426371c39aec2c47744305d6b9fd5be358234 |
| anchor3-initial.png | d5a4beb3dac37446205275c9bc7426371c39aec2c47744305d6b9fd5be358234 |
| anchor3-scrolled.png | a053e5d8c687f0fc509776a95eac3ab3445fe9d1fe6e224d7fdd27237d9bf44e |
| anchor4-walk-frame-0.png | f80cd40452d304f5de8aee97cc37928fc9fdcb7dbd26d0e13fa7f23d8885fd7e |
| anchor4-walk-frame-1.png | 8e077a4784b35b995ef06aeccfe74deb12d784ac71f78b1de71646785ad9a794 |
| anchor4-walk-frame-2.png | 17af3e03a5164328760e229895c0fea6959709ee34615f3325b7d84f88fcd72d |
| anchor4-facing-left.png | 41968d4de10b5ae861c47cf7d6049d5c36dfa56f15dd5be116016ca988b4655c |
| anchor5-last-gameplay.png | e5e3036f46466827d133c725c954b91851b13445160d60401409e27fedbed1e2 |
| anchor5-nextlevel-flip.png | e5e3036f46466827d133c725c954b91851b13445160d60401409e27fedbed1e2 |
| anchor5-nextlevel-card.png | 5cffbf4d30305c2ace9118cebfc84997577a022cff8aae016972659b6af50198 |
| anchor5-level-2.png | cd730b5ba8eb35003379c8a44a0da32bf5a5a62078656d207aadd7a7623e3a53 |
| platformer-player-transparency.png | af3d00db07f64cc0f5c0a187a861def38700b477ec5fb3c88879797894d4eb7e |

Note: Several anchors share the same hash — this is expected where the game's GBC output was visually identical at those checkpoints (e.g., anchor2-grounded, anchor2-landed, anchor3-initial all render the same initial state of level 1).

## Decisions Made

- Raw byte copy (cp) used exclusively — ImageIO or any re-encode is strictly forbidden per project convention and Pitfall 2 in 22-RESEARCH.md
- Per-file sha256 equality check executed and all 16 passed, satisfying T-22-05 mitigation
- anchor4 walk frames (walk-frame-0/1/2, facing-left) migrated as plain full-frame goldens; the OAM compareRegion hflip gate is a plan 22-07 concern, not 22-05

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. All 16 source files were present in their Phase 21/20 evidence directories and all sha256 assertions passed on first attempt.

## Known Stubs

None.

## Threat Flags

None. Pure local filesystem copy between tracked git paths; no network surface, no user input, no auth. T-22-05 (baseline integrity) mitigated via sha256 equality proof.

## Next Phase Readiness

- 16 platformer-template goldens are ready for consumption by plan 22-07 (visual-UAT swap)
- Plan 22-07 will call `assertGoldenMatch` against these files; ordering hazard (critical constraint #3) is now satisfied
- No blockers

---
*Phase: 22-golden-screenshot-and-evidence-storage-overhaul*
*Completed: 2026-06-14*

## Self-Check: PASSED

- [x] 16 PNG files exist under gbkt-examples/platformer-template/src/test/resources/goldens/platformer-template/
- [x] Task commit 21bc6961 exists in git log
- [x] All 16 sha256 hashes verified byte-identical to Phase 21/20 sources
