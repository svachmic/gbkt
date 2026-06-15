---
id: SEED-EASETOZERO-OSCILLATION
status: dormant
planted: 2026-06-15
planted_during: "v0.1.1 / milestone close cleanup"
trigger_when: "v0.2.0"
scope: medium
triage_disposition: RE-DEFERRED
triage_date: 2026-06-15
original_id: easetozero-oscillates-when-by-greater-than-one
title: easeToZero(by > 1) oscillates around zero instead of settling
source: phase-13.2-code-review
area: gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ExprBuilder.kt
original_priority: medium
---

## Context

Carried advisory WARNING (W4) from `13.2-REVIEW.md`.

`AssignableVar.easeToZero(by = 1)` (D-13) emits two IfOp nodes:
`if (v < 0) { v += by }` and `if (v > 0) { v -= by }`. With the default `by = 1` the value
converges to exactly 0. With `by > 1` and a value not divisible by `by`, it overshoots and
oscillates: e.g. `v = 1, by = 3` → `v > 0` so `v -= 3` → `v = -2` → next frame `v < 0` so
`v += 3` → `v = 1` → repeats forever, never settling at 0.

All current call sites use the default `by = 1` (SimplePhysics, Metasprites decay ladders), so
this is latent — but the `by` parameter advertises a decay rate that produces a stuck
oscillation for any non-unit value against a non-aligned magnitude. Mirrors the [[wrapat-zero-silent-always-reset]]
class of "parameter promises more than the emission honors".

## Fix

Add a clamp-to-zero guard so the decay never crosses zero:
`if (v < 0) { v += by; if (v > 0) v = 0 }` and symmetrically for the positive branch
(or emit `if (v < -by) v += by else v = 0` style). Add a test for `easeToZero(by = 3)` from
`v = 1` and `v = 2` asserting it reaches and holds 0 within one step.
