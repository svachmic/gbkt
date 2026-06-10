---
id: savedatadelegate-single-use-guard
title: Guard SaveDataDelegate against double-provideDelegate / reuse
created: 2026-06-03
source: phase-13.1-code-review
status: resolved
priority: medium
resolves_phase: "13.2"
scope: gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SystemBuilders.kt
---

## Context

Carried advisory item **WR-06** from `13.1-REVIEW.md`.

`SaveDataDelegate` (ReadOnlyProperty) stores `ref` as a nullable populated in `provideDelegate`,
and `getValue` errors "SaveDataDelegate not initialized" if `ref` is null. This is correct for
the normal `by` path, but if a caller reuses one delegate instance across two properties (or any
path where `provideDelegate` is skipped), `getValue` throws at read time rather than registration
time. Worse: the `SaveSystem` is registered as a side effect inside `provideDelegate`, so reusing
one instance across two `val`s double-registers under whichever property name fires last and
silently drops the first. Fragility, not a guaranteed bug.

## Fix

Document that a `SaveDataDelegate` instance is single-use, and guard against double
`provideDelegate` (throw if `ref != null` on a second `provideDelegate` call) so misuse fails
loudly at build time.

**Folded into Phase 13.2** (`resolves_phase: 13.2`) — shares the delegate surface with the
Req #12 unused-warning fix; recorded as a carried-in item + success criterion in the 13.2 ROADMAP
entry, so `/gsd-discuss-phase 13.2` will pick it up. Auto-closes when 13.2 completes.
