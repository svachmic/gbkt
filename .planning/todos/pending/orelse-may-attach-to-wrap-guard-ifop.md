---
id: orelse-may-attach-to-wrap-guard-ifop
title: orElse can silently attach to an auto-emitted wrap-guard IfOp
created: 2026-06-03
source: phase-13.2-code-review
status: pending
priority: medium
scope: gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ScriptBuilder.kt
---

## Context

Carried advisory WARNING (W3) from `13.2-REVIEW.md`.

`orElse { }` (D-06) delegates to `elseOp`, which attaches an else branch to the **most recent
IfOp** in the active `ScriptBuilder`. A `wrapAt` mutation (`idx++`, `idx += n`) auto-emits a
wrap-guard `IfOp` via `emitWrapGuard` on the non-power-of-two path
(`if (v >= N) { v = 0 }`). If an author writes `runIf(cond) { idx += 1 }.orElse { ... }`, the
`orElse` may bind to the wrap-guard `IfOp` instead of the `runIf` `IfOp`, silently misplacing
the else branch.

Latent today: no call site chains `orElse` onto a block that mutates a wrapped var. But the
two features compose unsafely.

## Fix

Make `runIf`/`unless` return a handle to *their* `IfOp` so `.orElse` binds to that specific op
rather than "the last IfOp emitted". Alternatively, have `emitWrapGuard` mark its `IfOp` as
non-else-attachable so `elseOp` skips it. Add a test: `runIf(c){ idx += 1 }.orElse{ x set 1 }`
with `idx` declared `wrapAt = <non-power-of-two>` and assert the else attaches to the runIf op.
