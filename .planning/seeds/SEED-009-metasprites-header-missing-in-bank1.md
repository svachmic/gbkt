---
id: SEED-009
status: dormant
planted: 2026-05-18
planted_during: v1.0 / Phase 10 close (code-review-gate, CR-02)
trigger_when: before Phase 11 (banks port) starts — any banking config that pushes scene frame functions into bank1.c+
scope: small
triage_disposition: VERIFIED-ALREADY-FIXED
triage_evidence: ".planning/phases/16-seed-triage/TRIAGE.md#SEED-009"
triage_date: 2026-06-12
---

# SEED-009: `<gbdk/metasprites.h>` missing from bank1.c when scene frame is banked (CR-02)

## Why This Matters

Plan 10-10 added `#include <gbdk/metasprites.h>` to `main.c` (line 1168 in
GBDKPipelineV2) so the metasprite descriptor types and `move_metasprite_*`
inline functions are visible to `main()`'s code.

But scene `frame { }` blocks containing `moveMetasprite()` ops get emitted into
`bank1.c` (or higher banks) under any banking config. The metasprites Phase 10
example fits entirely in HOME so frame code stays in `main.c` — but Phase 11
(banks port) and any user game with multi-scene complexity will push scene frame
functions into `bank1.c`.

When that happens, `bank1.c` references `move_metasprite_ex`, `move_metasprite_flipx`,
etc — but does not include the header. Since these are `inline` definitions in
`<gbdk/metasprites.h>`, the bank file fails to compile with "undefined reference".

## Root Cause

`gbkt-backend-gbdk/.../GBDKPipelineV2.kt` — the include is added unconditionally
to `main.c` headers, but the per-bank file headers (built by the bank-emission
path) do not check whether any scene in that bank uses metasprites.

## Fix Routes

**Route A — include on every bank that uses metasprites:**

In the bank file builder, scan the bank's emitted statements for any
`move_metasprite_*` call (or, more robustly: any `MoveMetasprite` ScriptOp lowered
into that bank). If found, add `#include <gbdk/metasprites.h>` to that bank file's
header.

```kotlin
fun buildBankHeaders(bankIR: BankIR): List<CInclude> {
    val baseHeaders = standardBankHeaders()
    val needsMetasprites = bankIR.scenes.any { scene ->
        scene.frameOps.any { it is MoveMetasprite }
    }
    return if (needsMetasprites) baseHeaders + CInclude("<gbdk/metasprites.h>") else baseHeaders
}
```

**Route B — unconditional include in all bank files:**

Cheaper but wasteful. `<gbdk/metasprites.h>` adds ~16 bytes per bank for the
inline trampolines that link-time GC may not always drop. Acceptable if the
analysis-pass approach is too complex for the current architecture.

## Tests Needed

Build a game with two scenes (one in HOME, one forced to bank 1) where the
banked scene calls `moveMetasprite()`. Assert that bank1.c contains
`#include <gbdk/metasprites.h>`.

## Phase Routing

→ **Phase 10.1** (or as a Phase 11 hard prerequisite — Phase 11's whole point
is banks, so the assertion would naturally fail there if not fixed first).

## Discovery

Code review gate post-Plan 10-20 (CR-02).

## Related

- SEED-008 (CR-01: VRAM tile-slot collision)
- SEED-010 (CR-03: non-namespaced descriptor symbol names)
