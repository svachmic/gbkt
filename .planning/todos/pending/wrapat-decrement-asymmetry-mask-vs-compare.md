---
id: wrapat-decrement-asymmetry-mask-vs-compare
title: wrapAt decrement underflow differs between mask and compare-reset paths
created: 2026-06-03
source: phase-13.2-code-review
status: pending
priority: medium
scope: gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/VariableBuilders.kt
---

## Context

Carried advisory WARNING (W2) from `13.2-REVIEW.md`.

The two `emitWrapGuard` paths handle decrement-below-zero differently:

- **Power-of-two N (mask `& (N-1)`):** decrementing a u8 `0` underflows to `255`, then
  `255 & (N-1)` yields `N-1` — a correct backward wrap.
- **Non-power-of-two N (compare-reset `if (v >= N) { v = 0 }`):** decrementing `0` underflows
  to `255`; `255 >= N` is true, so it resets to `0`, NOT `N-1`. Backward wrap is broken.

So `--idx` wraps to `N-1` for power-of-two cycles but collapses to `0` for non-power-of-two
cycles. Current call sites only ever increment `idx`/`rot` (forward), so this is latent — but
the `dec()`/`minusAssign` operators advertise wrap support they don't correctly provide for
non-power-of-two N.

## Fix

Either (a) emit a symmetric compare-reset for decrement on the non-power-of-two path
(`if (v >= N) { v = N - 1 }` is wrong too for the underflow case — needs an explicit
`if (underflowed) v = N - 1`), or (b) document that non-power-of-two `wrapAt` only supports
forward iteration and `require` no decrement op is used against such a var. Pick one and add a
test covering `--` against both a power-of-two and a non-power-of-two `wrapAt`.
