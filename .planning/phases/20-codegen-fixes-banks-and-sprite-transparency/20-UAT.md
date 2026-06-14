---
status: complete
phase: 20-codegen-fixes-banks-and-sprite-transparency
source: [20-VERIFICATION.md]
started: "2026-06-14T08:29:06Z"
updated: "2026-06-14T08:55:00Z"
---

## Current Test

[testing complete]

## Tests

### 1. Metasprites sprite-outline tRNS renders clean (SC-3, D-08 visual oracle #1)
expected: |
  Open `.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04/metasprites-sprite-outline.png`.
  Confirm the elephant metasprite renders with NO spurious black outline around the
  sprite edges over the checkerboard background (tRNS transparency correct).
result: pass
note: |
  User visual sign-off 2026-06-14: "Both screenshots visually look good."
  Cross-checked live in the MCP emulator (GBC mode) at HEAD — sprite-outline clean.

### 2. Platformer player transparency — no regression (SC-4, D-08 visual oracle #2, GBC mode)
expected: |
  Open `.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04/platformer-player-transparency.png`.
  Confirm the platformer player character shows correct GBC colours and clean
  transparent edges with NO black border / no transparency regression vs prior baselines.
result: pass
note: |
  User visual sign-off 2026-06-14: player transparency/colours correct, no regression.
  User follow-up question on player↔box overlap resolved: MCP A/B comparison of the
  gbkt port vs the ORIGINAL GBDK platformer_template (both GBC mode) confirmed the
  few-pixel sprite-over-crate overlap is intended sprite-vs-hitbox overhang, faithful
  to the original (24×32 art over a ~10px collision box; same pivot 12,6). Not a bug,
  not too big, and orthogonal to FIX-04. Evidence: emulator OAM coords match within ~2px.

## Summary

total: 2
passed: 2
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

[none — both visual oracles passed human sign-off]
