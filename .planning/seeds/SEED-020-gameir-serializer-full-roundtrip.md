# SEED-020 — GameIRSerializer full round-trip (deserialize the 10 stubbed IR collections)

> **Triage:** CONFIRMED-OPEN — [TRIAGE.md#SEED-020](.planning/phases/16-seed-triage/TRIAGE.md#SEED-020) · 2026-06-12

**Origin:** SonarCloud Info-issue sweep of PR #33 (`feat/d_and_d_gaps`), 2026-06-10 — 11 of the 21 S1135 findings clustered here
**Status:** Open — not yet bound to a target phase
**Routing:** Single contained phase or a fat plan inside a serializer/tooling phase. No codegen blast radius — the serializer is consumed by external tooling (IDE plugin, MCP describe), not by the compile pipeline.
**Blast radius:** `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIRSerializer.kt` only, plus new round-trip tests in `gbkt-ir`.

## Problem

`GameIRSerializer.deserialize()` silently drops 10 IR collections — each comes back
as `emptyList()` regardless of what was serialized:

- `systems` (SystemIR)
- `zones` (ZoneIR)
- `flags` (GlobalFlagsIR)
- `itemCategories` (ItemCategoryDef)
- `items` (ItemDef)
- `containers` (ContainerIR)
- `dropTables` (DropTableIR)
- `puzzleObjects` (PuzzleObjectIR)
- `collisionGroups` (CollisionGroupIR)
- `collisionRules` (CollisionRuleIR)

Additionally `serializeSystemIR` is lossy on the *serialize* side: it emits only
`{id, type}` ("full SystemIR round-trip not needed for external tool use cases"),
so even a future deserializer cannot reconstruct systems from today's JSON.

The asymmetry is undocumented at the API surface — a caller doing
`deserialize(serialize(gameIR))` gets a structurally valid but silently
incomplete `GameIR`. For zone-based or RPG games that is most of the game.

## Goal

Either of these is an acceptable terminal state (decide in discuss-phase):

1. **Full round-trip:** implement the 10 deserializers + full `SystemIR`
   serialization, proven by a property-shaped round-trip test over a maximal
   `GameIR` fixture (every collection non-empty).
2. **Explicit one-way contract:** keep the simplification but make it a
   documented, enforced contract — KDoc on `serialize`/`deserialize` stating
   exactly which collections survive, a `require`/result-flag for callers that
   need fidelity, and round-trip tests locking the supported subset.

## Scope sketch (for the discuss-phase)

1. Inventory which consumers call `deserialize` today (IntelliJ plugin? MCP
   server? none?) — if nobody deserializes, option 2 is cheap and honest.
2. If option 1: the serialize side already covers ZoneIR etc.; mirror each
   `serializeX` with a `deserializeX`, reusing the existing `deserializeList`
   helper pattern at `GameIRSerializer.kt:170-186`.
3. `SystemIR` is the hard one — it is an open interface with genre-specific
   implementations (`GenericSystem`, `CombatEngineSystem`, …); round-trip needs
   a type registry or sealed serialization strategy. This is why it was
   simplified in the first place; do not underestimate it.
4. Round-trip test fixture: maximal `GameIR` with all collections populated.

## Discovery hooks

- `GameIRSerializer.kt` — `// Deferred: … — SEED-020` markers on each stubbed
  collection (lines ~178-187) and on `serializeSystemIR` (~line 1247).
- `gbkt-ir/CLAUDE.md` — module doc for IR types.
