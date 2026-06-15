---
id: SEED-WRAPAT-ZERO-ALWAYS-RESET
status: dormant
planted: 2026-06-15
planted_during: "v0.1.1 / milestone close cleanup"
trigger_when: "v0.2.0"
scope: low
triage_disposition: RE-DEFERRED
triage_date: 2026-06-15
original_id: wrapat-zero-silent-always-reset
title: u8Var(wrapAt = 0) silently always-resets instead of erroring
source: phase-13.2-code-review
area: gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/VariableBuilders.kt
original_priority: low
---

## Context

Carried advisory WARNING (W1) from `13.2-REVIEW.md`.

`emitWrapGuard` treats `wrapAt = 0` as a non-power-of-two compare-reset, emitting
`if (v >= 0) { v = 0 }` — which, since `v` is an unsigned u8, is always true, so the
variable is pinned to 0 every frame. `wrapAt = 0` is meaningless (a zero-length cycle) and
should be rejected at declaration rather than silently producing a stuck-at-zero var.

No current call site uses `wrapAt = 0` (Metasprites uses `NUM_FRAMES` and `16`), so this is
latent. See [[savedatadelegate-single-use-guard]] for the precedent of build-time delegate
guards.

## Fix

In the `u8Var` factory / `U8VarDelegate`, `require(wrapAt == null || wrapAt >= 1) { ... }`
(arguably `>= 2`, since `wrapAt = 1` also pins to 0 but is at least an honest "always 0").
Decide the lower bound and fail loudly at build time with a message naming the variable.
