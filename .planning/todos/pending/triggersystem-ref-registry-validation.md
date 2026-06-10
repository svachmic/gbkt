---
id: triggersystem-ref-registry-validation
title: Validate triggerSystem(SystemRef) against the ref registry at build()
created: 2026-06-03
source: phase-13.1-code-review
status: pending
priority: high
scope: gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ScriptBuilder.kt, GameBuilder.kt
---

## Context

Carried advisory item **WR-07** from `.planning/phases/13.1-framework-primitives-config-cartridge-inserted/13.1-REVIEW.md`.

`ScriptBuilder.triggerSystem(ref: SystemRef)` lowers `ref.systemId` straight into a
`TriggerSystem` IR node with **no check** that the id corresponds to a registered system.
The codebase already has a `RefRegistry` mechanism that `GameBuilder.build()` resolves and
fails on for unresolved `SoundRef`/`SceneRef` (GameBuilder ~694-714). For the new `SystemRef`
path this enforcement is missing, so a `triggerSystem(BattleRef("typo"))` — or a `saveData`
ref whose registration was skipped — produces a `TriggerSystem("typo")` that compiles to a
call to a non-existent C function, surfacing only as an opaque `lcc` "undefined identifier"
error at link time. This is the exact failure class the typed-ref work exists to eliminate.

## Fix

Register `SystemRef` usages (or at minimum validate `ref.systemId` against
`refRegistry.registeredIds(RefKind.SYSTEM)` at `build()`), consistent with the scene/sound
ref validation already in place. Add a JVM test that an unregistered SystemRef fails at
`build()` with an actionable message rather than at link time.

## Notes

Thematically fits the typed-ref / DX track — consider folding into a future 13.x phase
that touches the ref-resolution surface. Owned by no phase yet.
