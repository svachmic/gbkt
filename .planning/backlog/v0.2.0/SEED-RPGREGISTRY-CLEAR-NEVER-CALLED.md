---
id: SEED-RPGREGISTRY-CLEAR-NEVER-CALLED
status: dormant
planted: 2026-06-15
planted_during: "v0.1.1 / milestone close cleanup"
trigger_when: "v0.2.0"
scope: low
triage_disposition: RE-DEFERRED
triage_date: 2026-06-15
original_id: rpgregistry-clear-never-called
title: Call RpgRegistry.clear() on game{} teardown (or remove dead method)
source: phase-13.1-code-review
area: gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/RpgExtensions.kt
original_priority: low
---

## Context

Carried advisory item **IN-01** from `13.1-REVIEW.md`.

`RpgRegistry.clear()` is defined and its KDoc says "call after the game-building lambda
completes," but a repo-wide search finds **no call site**. In a long-lived JVM (Gradle daemon,
test runner) that builds multiple games on the same thread, character/monster defs from a prior
`game {}` persist into the next build's registry. Keyed by id so collisions overwrite rather than
accumulate unboundedly, but stale entries from game A can be read while building game B.

## Fix

Invoke `RpgRegistry.clear()` from the `game {}` lambda teardown (alongside the
`GameBuilderContext` restore), or remove the dead `clear()` if the registry is genuinely
build-scoped some other way.
