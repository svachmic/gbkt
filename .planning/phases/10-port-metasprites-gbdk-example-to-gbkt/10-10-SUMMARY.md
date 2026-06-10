---
phase: 10-port-metasprites-gbdk-example-to-gbkt
plan: 10
subsystem: dsl-palette, codegen-pipeline
tags: [gbc-compat, spritePalette, cgb_compatibility, metasprites-h, tdd]
dependency_graph:
  requires: []
  provides: [spritePalette-factory, cgb_compatibility-emission, metasprites-h-include]
  affects: [gbkt-lang/PaletteBuilder, gbkt-backend-gbdk/GBDKPipelineV2, gbkt-ir/GameIR]
tech_stack:
  added: [SpritePaletteDelegate, MetaspriteIR stub, MetaspriteFrame, MetaspriteTile]
  patterns: [PaletteDelegate mirror for SPRITE type, CRawCode conditional emission, buildList conditional include]
key_files:
  created:
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/PaletteBuilder.kt (SpritePaletteDelegate + spritePalette factory appended)
    - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/MetaspriteIR.kt (stub for compilation prerequisite)
    - gbkt-lang/src/test/kotlin/io/github/gbkt/core/dsl/SpritePaletteDelegateTest.kt
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GbcCompatEmissionTest.kt
  modified:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt
    - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIR.kt (metasprites field added)
decisions:
  - "Separate SpritePaletteDelegate class over parameterizing PaletteDelegate — avoids exposing PaletteType in DSL author imports"
  - "cgb_compatibility() emitted as CRawCode (first item in buildList), before NR52_REG sound init — matches GBDK reference ordering"
  - "MetaspriteIR added as stub in this wave-1 plan since GBDKPipelineV2 compilation requires gameIR.metasprites field (plan 10-02 wave-2 will expand the stub)"
  - "brace-walk extractor in Kotlin test mimics awk scope-gate from CLAUDE.md — scopes assertions to main() body only"
metrics:
  duration: "265 seconds"
  completed: "2026-05-18T15:30:16Z"
  tasks_completed: 2
  files_changed: 6
---

# Phase 10 Plan 10: GBC Compat Gap — spritePalette + cgb_compatibility() + metasprites.h — Summary

**One-liner:** Added `spritePalette {}` DSL factory (PaletteType.SPRITE), injected `cgb_compatibility()` as first `main()` statement for GBC targets (Pitfall 2 mitigation), and conditionally included `<gbdk/metasprites.h>` when metasprites present (Pitfall 4 mitigation).

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Add spritePalette { } DSL factory (TDD RED) | 095084b6 | SpritePaletteDelegateTest.kt |
| 1 | Add spritePalette { } DSL factory (TDD GREEN) | 909cb186 | PaletteBuilder.kt |
| 2 | GbcCompatEmissionTest + MetaspriteIR stub (TDD RED) | 09bec986 | GbcCompatEmissionTest.kt, MetaspriteIR.kt, GameIR.kt |
| 2 | cgb_compatibility() + metasprites.h injection (TDD GREEN) | 29d63c5d | GBDKPipelineV2.kt |

## What Was Built

### Task 1: `spritePalette { }` DSL Factory

Added `SpritePaletteDelegate` class and `spritePalette()` top-level factory to `PaletteBuilder.kt`. Mirrors `PaletteDelegate`/`palette()` exactly, but passes `PaletteType.SPRITE` to `PaletteBuilder.build()`. The name is inferred from the Kotlin property name at the `by` keyword.

```kotlin
val gray by spritePalette {
    color0(GbcColor.WHITE)
    color1(gbc(20, 20, 20))
    color2(gbc(10, 10, 10))
    color3(GbcColor.BLACK)
}
```

The `visitSetPalette()` in `ScriptOpVisitor.kt` already has the `PaletteType.SPRITE -> "set_sprite_palette"` branch — no codegen change needed.

### Task 2: Pipeline Injections

**`cgb_compatibility()` injection** (`buildMainFunction`):
- Prepended as first `CRawCode` in `mainBody` when `gameIR.config.gbcTarget != GbcTarget.DMG`
- Runs before sound init (NR52_REG/NR50_REG/NR51_REG) and DISPLAY_ON — correct GBDK ordering
- Pitfall 2 mitigation: palette loads via `set_sprite_palette()` run after `cgb_compatibility()`

**`<gbdk/metasprites.h>` include** (`buildHomeFile`):
- Conditionally added to `allIncludes` when `gameIR.metasprites.isNotEmpty()`
- Pitfall 4 mitigation: prevents undefined reference linker errors for `move_metasprite_*()` calls

## Deviations from Plan

### Auto-added Missing Dependency (Rule 2)

**[Rule 2 - Missing Critical Functionality] Added MetaspriteIR stub + GameIR.metasprites field**

- **Found during:** Task 2 (writing `GbcCompatEmissionTest.kt`)
- **Issue:** Plan 10-10 is in wave 1 with `depends_on: []`, but references `gameIR.metasprites` which requires the `MetaspriteIR` type and `GameIR.metasprites` field added by plan 10-02 (wave 2). Without the field, `GBDKPipelineV2.kt` and its test would not compile.
- **Fix:** Created `MetaspriteIR.kt` with minimal `MetaspriteIR`, `MetaspriteFrame`, `MetaspriteTile` data classes. Added `metasprites: List<MetaspriteIR> = emptyList()` field to `GameIR`. Both use `emptyList()` defaults for backward compatibility.
- **Plan 10-02 impact:** Plan 10-02 (wave 2) will expand `MetaspriteIR.kt` with the full implementation. The field addition to `GameIR.kt` is identical to what plan 10-02 would have added — no merge conflict expected since the field is in a different position in the file, with `emptyList()` default.
- **Files modified:** `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/MetaspriteIR.kt` (created), `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIR.kt` (metasprites field added)
- **Commit:** 09bec986

## TDD Gate Compliance

Both tasks followed RED → GREEN sequence:

| Gate | Task 1 Commit | Task 2 Commit |
|------|--------------|--------------|
| RED (test) | 095084b6 | 09bec986 |
| GREEN (impl) | 909cb186 | 29d63c5d |
| REFACTOR | Not needed | Not needed |

## Known Stubs

None — `SpritePaletteDelegate` and the pipeline changes are complete implementations. The `MetaspriteIR` added as a deviation is intentionally minimal (stub) to support compilation only; plan 10-02 will add the full implementation. This stub does not prevent plan 10-10's goal from being achieved.

## Self-Check: PASSED

Files created/modified:
- `/Users/michalsvacha/GitHub/personal/gbkt/.claude/worktrees/agent-a7ff1447c428a2213/gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/PaletteBuilder.kt` — spritePalette factory appended
- `/Users/michalsvacha/GitHub/personal/gbkt/.claude/worktrees/agent-a7ff1447c428a2213/gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/MetaspriteIR.kt` — created
- `/Users/michalsvacha/GitHub/personal/gbkt/.claude/worktrees/agent-a7ff1447c428a2213/gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIR.kt` — metasprites field added
- `/Users/michalsvacha/GitHub/personal/gbkt/.claude/worktrees/agent-a7ff1447c428a2213/gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` — cgb_compatibility + metasprites.h include
- `/Users/michalsvacha/GitHub/personal/gbkt/.claude/worktrees/agent-a7ff1447c428a2213/gbkt-lang/src/test/kotlin/io/github/gbkt/core/dsl/SpritePaletteDelegateTest.kt` — created
- `/Users/michalsvacha/GitHub/personal/gbkt/.claude/worktrees/agent-a7ff1447c428a2213/gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GbcCompatEmissionTest.kt` — created

Commits verified:
- 095084b6: test(10-10) RED SpritePaletteDelegateTest
- 909cb186: feat(10-10) GREEN spritePalette factory
- 09bec986: test(10-10) RED GbcCompatEmissionTest + MetaspriteIR stub
- 29d63c5d: feat(10-10) GREEN cgb_compatibility + metasprites.h

Tests pass: `./gradlew :gbkt-lang:test :gbkt-backend-gbdk:test` → BUILD SUCCESSFUL
