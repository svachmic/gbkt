---
id: SEED-006
status: dormant
planted: 2026-05-18
planted_during: v1.0 / Phase 10 closeout (Plan 10-20)
trigger_when: when Phase 10.1 (metasprites surplus codegen defects) is opened
scope: small
---

# SEED-006: _elephant_subPalette global variable never assigned in frame loop (D-V3)

## Why This Matters

`MetaspriteVisitor` generates a `_elephant_subPalette` global variable in the
generated C (`UINT8 _elephant_subPalette = 0u;`). The intent is to allow external
code (and the MCP-based UAT test via sym-file) to read the current active
sub-palette index of the elephant metasprite.

However, the generated `play_frame()` function computes the sub-palette as a LOCAL
variable (`uint8_t subpal = _rot >> 2;`) and passes it directly to `move_metasprite_ex()`.
The global `_elephant_subPalette` is never assigned from `subpal` — it stays at `0`
for the entire runtime regardless of how many A presses have occurred.

## Visual Impact (None at Runtime)

The visual rendering is CORRECT. The OAM call receives the right `subpal` value via
the local variable. GBC sub-palette cycling (behavior 3 in 10-UAT.md) works correctly
at the visual layer.

The defect is ONLY observable via the sym file: `emulator_read_variable("_elephant_subPalette")`
returns `0` even when `_rot == 8` (subpal should be `2`). The Plan 18 UAT script was
updated to assert on `_rot` (the correct proxy) rather than `_elephant_subPalette` to
work around this.

## Root Cause

`MetaspriteVisitor.generateMoveMetaspriteOp()` (or equivalent) emits code like:

```c
uint8_t subpal = _rot >> 2;
_elephant_flipX = (_rot & 0x3u) >> 0u;   // etc.
move_metasprite_ex(sprite_metasprites[_idx], SPR_NUM_START, subpal, _pos_x, _pos_y);
```

But does NOT emit:

```c
_elephant_subPalette = subpal;
```

The global is declared but the assignment is missing from the frame body codegen path.

## Fix Routes

**Option A:** Add `_elephant_subPalette = subpal;` assignment in the generated
`play_frame()` after computing `subpal`. Fix site: `MetaspriteVisitor.kt` in the
method that emits the `move_metasprite_ex()` call.

**Option B:** Remove `_elephant_subPalette` global entirely if it is never read by
other parts of the generated code. The OAM call uses the local `subpal` correctly;
the global adds unnecessary RAM usage with no benefit. If Option B is chosen, update
the UAT test to remove any reference to `_elephant_subPalette` as a valid sym variable.

Option A is preferred because the global is useful for MCP-based debugging (allows
the emulator agent to read the current sub-palette state without tracing execution).

## Evidence

- `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior3-subpalette-cycle-gbc.json`
  (shows `_elephant_subPalette: 0` at `_rot == 8` — should be 2)
- Phase 10 UAT.md §"Defect D-V3"

## Affected Files

- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/MetaspriteVisitor.kt`
  (method emitting `move_metasprite_ex()` call in the frame handler)

## Scope Estimate

**Small** — 1-2 lines of Kotlin in `MetaspriteVisitor.kt` + 1 JVM-tier test asserting
`_elephant_subPalette` is assigned in the generated frame function body.

## Why Not Fixed in Phase 10

Phase 10's scope cap is ONE named codegen bug-fix. D-V3 was surfaced only during the
GBC-mode UAT in Plan 18 — after Phase 10's coding scope was closed. The visual behavior
is correct; only the debug global is stale. Since the UAT works around it cleanly via
`_rot` assertions, the defect does not block Phase 10 shipping. Seeded per D-06 doctrine.

## When to Surface

**Trigger:** when Phase 10.1 (metasprites surplus codegen defects) is opened.

Fix alongside D-V1 and D-V2 (SEED-004, SEED-005) in a single Phase 10.1 pass.

## Related

- `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/10-UAT.md` §D-V3
- SEED-004 (D-V1: corrupted tile rendering) — companion visual defect in same Phase 10.1
- SEED-005 (D-V2: diagonal bg stripes) — companion visual defect in same Phase 10.1
