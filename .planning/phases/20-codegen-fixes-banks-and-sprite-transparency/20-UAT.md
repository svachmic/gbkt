---
status: testing
phase: 20-codegen-fixes-banks-and-sprite-transparency
source: [20-VERIFICATION.md]
started: "2026-06-14T08:29:06Z"
updated: "2026-06-14T08:29:06Z"
---

## Current Test

number: 1
name: Metasprites sprite-outline tRNS renders clean (SC-3, D-08 visual oracle #1)
expected: |
  Open evidence/fix-04/metasprites-sprite-outline.png — the elephant metasprite
  renders on the checkerboard background with NO spurious black outline around the
  sprite edges (tRNS auto-route working; Phase 13.6 fix confirmed at HEAD).
awaiting: user response

## Tests

### 1. Metasprites sprite-outline tRNS renders clean (SC-3, D-08 visual oracle #1)
expected: |
  Open `.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04/metasprites-sprite-outline.png`.
  Confirm the elephant metasprite renders with NO spurious black outline around the
  sprite edges over the checkerboard background (tRNS transparency correct).
result: [pending]

### 2. Platformer player transparency — no regression (SC-4, D-08 visual oracle #2, GBC mode)
expected: |
  Open `.planning/phases/20-codegen-fixes-banks-and-sprite-transparency/evidence/fix-04/platformer-player-transparency.png`.
  Confirm the platformer player character shows correct GBC colours and clean
  transparent edges with NO black border / no transparency regression vs prior baselines.
result: [pending]

## Summary

total: 2
passed: 0
issues: 0
pending: 2
skipped: 0
blocked: 0

## Gaps

Note: Code review WR-01 (20-REVIEW.md) — both oracle tests discard `waitForScene`'s
return value and dropped the precedent `assertEquals(scene)` assertion
(`PlatformerTemplate128UatTest.kt:218-224`), so the mechanical `assertScreenshotIsNonUniform`
gate cannot prove the captured frame is the intended scene. Human sign-off is the
binding gate for SC-3/SC-4. If sign-off surfaces a wrong-scene capture, harden the
oracles by restoring the scene assertion before re-shooting.
