# First Blocker Analysis — Plan 10-14 (D-05 Exploratory)

## Build outcome

**FAIL-COMPILE**

`./gradlew :gbkt-examples:metasprites:buildRom` exits with lcc error (Gradle task
`:gbkt-examples:metasprites:compileRom` FAILED, Gradle overall exit code 1 for the
compileRom task, 0 for the Gradle process itself).

```
main.c:267: error 20: Undefined identifier 'sprite_metasprites'
main.c:267: error 22: Array or pointer required for '[]' operation
main.c:272: error 20: Undefined identifier 'sprite_metasprites'
main.c:272: error 22: Array or pointer required for '[]' operation
main.c:277: error 20: Undefined identifier 'sprite_metasprites'
main.c:277: error 22: Array or pointer required for '[]' operation
main.c:282: error 20: Undefined identifier 'sprite_metasprites'
main.c:282: error 22: Array or pointer required for '[]' operation
```

No lcc warnings — the build only has hard errors (4 unique errors × 2 each = 8 error lines).

---

## First blocker name

**`MetaspriteVisitor.generateMetaspriteDescriptor()` is implemented but never called from
`GBDKPipelineV2` — the `sprite_metasprite_N[]` per-frame OAM descriptor arrays and the
`sprite_metasprites[]` pointer table are never emitted into the generated C.**

---

## Minimal repro

The DSL `val elephant by metasprite { frame { tile(...) } × 5 }` lowers correctly through
the IR. The visitor method `MetaspriteVisitor.generateMetaspriteDescriptor(metasprite)` is
fully implemented (Plans 10-05/10-06) and would emit the correct C:

```c
const metasprite_t sprite_metasprite_0[] = { {-24,-16,0}, {8,0,1}, ..., {metasprite_end} };
...
const metasprite_t* const sprite_metasprites[] = {
    sprite_metasprite_0, sprite_metasprite_1, ... sprite_metasprite_4,
};
```

However, a `grep generateMetaspriteDescriptor GBDKPipelineV2.kt` returns zero matches.
`GBDKPipelineV2.buildHomeFile()` (the function that builds `main.c`) includes the
`#include <gbdk/metasprites.h>` guard and emits the `_elephant_flipX/Y/subPalette` runtime
vars, but it never invokes `MetaspriteVisitor.generateMetaspriteDescriptor()` to
emit the actual descriptor arrays.

The `play_frame()` body at `main.c:267` then references `sprite_metasprites[_idx]` (emitted
by `ScriptOpVisitor.visitMoveMetasprite()` via `MetaspriteVisitor.generateMetaspriteFrameSwitch()`)
against an undeclared symbol — hence lcc error 20.

Exact defect location: `GBDKPipelineV2.kt` — the section that assembles the globals block
of `main.c` (near line 893, where `metaspriteRuntimeVars` is appended) does NOT follow with
a call to `MetaspriteVisitor.generateMetaspriteDescriptor(ms)` for each metasprite in
`gameIR.metasprites`.

---

## Mapped to RESEARCH §12 candidate

**Not in the catalog — novel blocker.**

The five plausible first-blocker candidates listed in RESEARCH §12 were:
1. OAM-tail hiwater off-by-one on variable-length frames
2. Sprite palette emission ordering before `cgb_compatibility()`
3. `set_sprite_prop()` not flushed per frame after move
4. Missing `<gbdk/metasprites.h>` include (mitigated by Plan 10-10)
5. Signed-comparison in animation index wrap (mitigated by Plan 13's `u8Var` declaration)

None match. The actual blocker is a pipeline wiring gap: the descriptor visitor method
exists but the pipeline orchestrator never calls it. This is a plan integration error
introduced during phased delivery (Plans 10-05/10-06 created the visitor, but the pipeline
call-site was not added in either plan).

---

## Proposed Plan 15 scope

**Single pipeline wiring call in `GBDKPipelineV2.kt`.**

In `buildHomeFile()` (or the globals-assembly section around line 893), after emitting
`metaspriteRuntimeVars`, add a loop:

```kotlin
val metaspriteDescriptors: List<CStatement> = gameIR.metasprites.flatMap { ms ->
    listOf(MetaspriteVisitor.generateMetaspriteDescriptor(ms))
}
```

Then include `metaspriteDescriptors` in the `CFile` being built (likely as raw globals
before the first function body, i.e., before `play_enter()` in `main.c`).

**Estimated size:** 1–5 lines of Kotlin in `GBDKPipelineV2.kt`. No new types, no new
visitors, no IR changes. Regression: run `MetaspriteVisitorDescriptorTest` (existing) +
new golden-output test that does `grep sprite_metasprites main.c && lcc main.c` (or
equivalent JVM-tier assertion).

**Files likely modified:**
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt`
  (1-5 line call-site addition)

**Secondary concern (runtime, not compile):** `set_sprite_data()` call from
`generateMetaspriteTileData()` also appears to be missing from `play_enter()` in the
generated C (the tile data load for VRAM). This may be the next blocker after Plan 15
fixes the descriptor — but it's a separate, lower-priority gap. Deferred to Plan 15 scope
or Plan 16 depending on whether the initial fix is sufficient for lcc to pass.

---

## Surplus defects deferred to seeds

No surplus lcc errors or warnings — all 8 error lines stem from the single root cause
(4 `sprite_metasprites[_idx]` references × 2 errors each = 8). There are no other
independent compile errors in `first-build-log.txt`.

Deferred observations (not compile errors; noted for Plan 18 / D-06 seeds):

1. `set_sprite_data()` call for VRAM tile data loading is absent from `play_enter()` in the
   generated C. `MetaspriteVisitor.generateMetaspriteTileData()` exists but — like
   `generateMetaspriteDescriptor()` — is not called from the pipeline. Fix: same pipeline
   wiring pattern as Plan 15, in the scene-enter builder.

2. All four `set_sprite_palette()` calls in `play_enter()` use slot 0 (not slots 0-3):
   ```c
   set_sprite_palette(0u, 1u, gray_pal);
   set_sprite_palette(0u, 1u, pink_pal);
   set_sprite_palette(0u, 1u, cyan_pal);
   set_sprite_palette(0u, 1u, green_pal);
   ```
   This is a known PHASE-13 gap documented in `Metasprites.kt` — deferred to Plan 18 (D-13).
   Behavior 3 (sub-palette cycling via `rot >> 2`) will not display correctly until each
   palette is loaded into its own slot.

3. `ConvertSpritesTask: No sprite includes found in main.c` — the elephant sprite PNG is
   not wired into the asset pipeline. `set_sprite_data()` will need a valid C array name
   (e.g., `elephant_tiles`) once tile data loading is added. Deferred to Plan 18 (D-13 gap 1).
