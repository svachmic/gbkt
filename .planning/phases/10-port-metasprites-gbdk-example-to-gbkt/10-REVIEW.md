---
phase: 10-port-metasprites-gbdk-example-to-gbkt
reviewed: 2026-05-18T00:00:00Z
depth: standard
files_reviewed: 14
files_reviewed_list:
  - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/MetaspriteIR.kt
  - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIR.kt
  - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIRSerializer.kt
  - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/ScriptOp.kt
  - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/ScriptOpVisitorI.kt
  - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/MetaspriteBuilder.kt
  - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/PaletteBuilder.kt
  - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SceneBuilder.kt
  - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/GameBuilder.kt
  - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/MetaspriteVisitor.kt
  - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ScriptOpVisitor.kt
  - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt
  - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/CoffeeGbEmulator.kt
  - gbkt-examples/metasprites/src/main/kotlin/io/github/gbkt/examples/metasprites/Metasprites.kt
findings:
  critical: 3
  warning: 5
  info: 3
  total: 11
status: issues_found
---

# Phase 10: Code Review Report

**Reviewed:** 2026-05-18
**Depth:** standard
**Files Reviewed:** 14
**Status:** issues_found

## Summary

Phase 10 introduces MetaspriteIR, the `metasprite { }` DSL, MetaspriteVisitor codegen, flip/subPalette
accessor lowering, GBC compat injection in GBDKPipelineV2, and the metasprites example game. The IR
design, DSL delegate pattern, and frame-switch codegen are structurally sound for the single-metasprite
case demonstrated by the example.

Three critical blockers were found, none of which manifest in the current single-metasprite example
but all of which trigger on the first game that uses metasprites with actors present or with a
second metasprite declared, or with banking enabled. All three are codegen-layer bugs that would
produce C compile or link errors.

Five warnings cover missing validations, an uncaught exception path, and missing include
propagation. Three info items cover dead API, formatting, and a minor type-convention mismatch.

**Already-seeded defects not re-reported:** D-V1 (tile data corruption), D-V2 (diagonal stripe
pattern), D-V3 (`_elephant_subPalette` never written), D-extra (`GameBuilder.kt:713` palette slot 0).

---

## Narrative Findings (AI reviewer)

## Critical Issues

### CR-01: VRAM collision — actor sprite tiles and metasprite tiles both start at slot 0

**File:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt:3635-3684`

**Issue:** `buildSpriteDataLoadStatements` and `buildMetaspriteTileDataLoadStatements` each
initialize `nextTile = 0` independently. Both emit `set_sprite_data(startTile, count, array)`
calls into `main()`. When a game contains both actor sprites and metasprites, the two sets of
calls target the same VRAM tile slots starting at 0, and whichever `set_sprite_data` runs last
overwrites the earlier one. The KDoc comment at line 3663-3665 describes them as "independent" and
"NOT shared", which is accurate but obscures that they therefore collide. The current
`metasprites` example has no actors, so the bug is latent.

**Fix:** Advance `buildMetaspriteTileDataLoadStatements`'s `nextTile` past the tiles consumed by
`buildSpriteDataLoadStatements`. Compute the actor VRAM high-water mark separately and pass it as
`startTile` offset to the metasprite loader:

```kotlin
private fun buildMetaspriteTileDataLoadStatements(
    gameIR: GameIR,
    actorTileOffset: Int = 0,   // <- new parameter
): List<CStatement> {
    if (gameIR.metasprites.isEmpty()) return emptyList()
    val statements = mutableListOf<CStatement>()
    var nextTile = actorTileOffset  // <- starts after actor tiles
    for (ms in gameIR.metasprites) {
        ...
    }
    return statements
}
```

And in `buildMainFunction`:
```kotlin
val actorTileCount = buildActorTileCount(gameIR)  // sum of all actor tiles
val metaspriteTileDataLoads = buildMetaspriteTileDataLoadStatements(gameIR, actorTileOffset = actorTileCount)
```

---

### CR-02: `<gbdk/metasprites.h>` not included in `bank1.c` — inline functions missing at banked call sites

**File:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt:1396-1403`

**Issue:** `<gbdk/metasprites.h>` is conditionally added to `main.c` (line 1168) but never to
`bank1.c`. The scene `frame` functions that contain `moveMetasprite()` ops are emitted into
`bank1.c` under any banking configuration. The `move_metasprite_ex`, `move_metasprite_flipx`,
`move_metasprite_flipy`, `move_metasprite_flipxy`, and `metasprite_t` typedef are all defined as
`inline` functions and types in `<gbdk/metasprites.h>` — they must be visible at every call site.
`game.h` does not include this header (confirmed: `game.h` includes only `<gb/gb.h>`,
`<stdio.h>`, `<gbdk/console.h>`, and optionally `<gb/cgb.h>`). The current `metasprites`
example escapes this because its small size causes scene code to land in `main.c` (single-file
output), but any game that triggers banking would fail to compile with `undefined identifier`
errors on all `move_metasprite_*` sites.

**Fix:** Add a conditional include in `buildSceneFile()` mirroring the main-file pattern:

```kotlin
val metaspriteInclude =
    if (gameIR.metasprites.isNotEmpty()) listOf("<gbdk/metasprites.h>") else emptyList()

return CFile(
    name = "bank1.c",
    bank = fileBank,
    includes = listOf("<stdio.h>", "<gbdk/console.h>", "\"game.h\"") +
               cgbInclude + metaspriteInclude,
    ...
)
```

---

### CR-03: `sprite_metasprites[]` and `sprite_metasprite_N[]` are not namespaced per metasprite ID — duplicate symbol at compile time for two or more metasprites

**File:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/MetaspriteVisitor.kt:113-129`

**Issue:** `generateMetaspriteDescriptor` emits the same global C symbol names for every
metasprite it is called on:

```c
const metasprite_t sprite_metasprite_0[] = { ... };
const metasprite_t* const sprite_metasprites[] = { ... };
```

When `GBDKPipelineV2.buildHomeFile` iterates `gameIR.metasprites` and calls
`generateMetaspriteDescriptor` for each one, a game with two metasprites (e.g. `elephant` and
`hero`) produces two definitions of `sprite_metasprites[]` and two definitions of
`sprite_metasprite_0[]` in the same compilation unit. SDCC will error with "symbol already
defined". Additionally `generateMetaspriteFrameSwitch` always emits `sprite_metasprites[_idx]`
regardless of which metasprite is being rendered, so even the single-metasprite game will use
the wrong frame table once a second metasprite exists.

**Fix:** Namespace all emitted symbol names with the metasprite ID:

```kotlin
fun generateMetaspriteDescriptor(metasprite: MetaspriteIR): CRawCode {
    val id = metasprite.id
    val buf = StringBuilder()
    for ((index, frame) in metasprite.frames.withIndex()) {
        buf.append("const metasprite_t ${id}_metasprite_$index[] = {\n    ")
        ...
    }
    buf.append("const metasprite_t* const ${id}_metasprites[] = {\n")
    for (index in metasprite.frames.indices) {
        buf.append("    ${id}_metasprite_$index,\n")
    }
    buf.append("};\n")
    return CRawCode(buf.toString())
}
```

And `generateMetaspriteFrameSwitch` must receive the ID to emit `${id}_metasprites[_idx]`:

```kotlin
fun generateMetaspriteFrameSwitch(metasprite: MetaspriteIR): CRawCode {
    val tableRef = "${metasprite.id}_metasprites"
    // use tableRef instead of literal "sprite_metasprites"
    ...
}
```

---

## Warnings

### WR-01: `generateMetaspriteFrameSwitch` hardcodes global variable names `_rot`, `_idx`, `_posX`, `_posY` — any game with different variable names gets compile errors

**File:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/MetaspriteVisitor.kt:193-223`

**Issue:** The frame-switch block unconditionally emits `_rot`, `_idx`, `_posX`, `_posY` as
literal C identifiers. These are documented as a "variable name contract" that the port must
satisfy. However the contract is enforced only by KDoc comment — there is no IR-level field on
`MoveMetasprite` that carries the actual variable references, and no DSL validation that the user
has declared variables with exactly these names. A game that names its rotation variable `facing`
instead of `rot` will compile successfully at DSL level but fail at C compile time with
"undefined identifier `_rot`". The `@Suppress("UnusedParameter")` on `metasprite` acknowledges
the ID is not used, which is the symptom of this structural issue.

**Fix (short-term):** Document the contract in a DSL-level error. Add a check in
`MetaspriteVisitor` (or in an analysis pass) that the four required variables are declared in
the `GameIR`:

```kotlin
// In an analysis pass or in GBDKPipelineV2.buildHomeFile:
val requiredMetaspriteVars = setOf("idx", "rot", "posX", "posY")
val declared = gameIR.variables.map { it.name }.toSet()
val missing = requiredMetaspriteVars - declared
if (missing.isNotEmpty() && gameIR.metasprites.isNotEmpty()) {
    error("MoveMetasprite requires variables: $missing (declare via u8Var/i16Var)")
}
```

**Fix (long-term):** Add `posXVar`, `posYVar`, `idxVar`, `rotVar` fields to `MoveMetasprite` IR
node and emit them symbolically.

---

### WR-02: `sprite_metasprites[]` defined in `main.c` raw section has no extern declaration in `game.h` — latent linker failure when banking is active

**File:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt:1014-1017`

**Issue:** `metaspriteDescriptorRaw` is emitted into `homeFile.rawSections`. The
`homeGlobalAutoExterns` logic (line 2005-2010) only iterates `homeFile.variables` (typed
`CVarDecl` nodes), not raw sections. So `sprite_metasprites[]` has no `extern` declaration in
`game.h`. When `bank1.c` references `sprite_metasprites[_idx]` — even via `game.h` — the
external linkage is absent. SDCC in pedantic mode will diagnose this; at minimum it is an
implicit-declaration hazard. (Currently masked because scene code falls into `main.c` for the
small example.)

**Fix:** After emitting the descriptor raw section, also emit an extern block into
`game.h`'s `rawSections`:

```kotlin
val metaspriteExterns =
    gameIR.metasprites.joinToString("\n") { ms ->
        val id = ms.id  // after CR-03 fix, uses namespaced names
        "extern const metasprite_t* const ${id}_metasprites[];"
    }.takeIf { gameIR.metasprites.isNotEmpty() }
```

---

### WR-03: `MetaspriteFrameBuilder.tile()` does not validate that `relX`/`relY` fit in `int8_t` range

**File:** `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/MetaspriteBuilder.kt:39-41`

**Issue:** The `GBDK metasprite_t` struct uses `int8_t dy, dx`, which are 8-bit signed fields
with range -128..127. The DSL `tile(x: Int, y: Int, baseId: Int)` accepts any `Int`. A user who
transcribes tile coordinates incorrectly (e.g. `tile(160, 0, 5)` for an off-screen sprite) will
get a silent integer truncation to `int8_t` in the generated C. The existing validation only
checks `tileId >= 0`. No DSL-time error is raised for out-of-range offsets.

**Fix:**
```kotlin
fun tile(x: Int, y: Int, baseId: Int) {
    require(x in -128..127) {
        "metasprite tile relX must be in -128..127 (int8_t range), got $x"
    }
    require(y in -128..127) {
        "metasprite tile relY must be in -128..127 (int8_t range), got $y"
    }
    tiles.add(MetaspriteTile(relX = x, relY = y, tileId = baseId))
}
```

---

### WR-04: `GameIRSerializer.fromJson()` does not catch `JSONException` — unchecked exception crashes callers

**File:** `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIRSerializer.kt:69-78`

**Issue:** `JSONObject(json)` throws `org.json.JSONException` (an unchecked `RuntimeException`)
on malformed JSON. The `fromJson` function does not catch this exception and has no `@Throws`
annotation or documentation of the failure mode. External tools (the primary consumers of this
API per the class KDoc) will receive an unformatted stack trace rather than a diagnostic message
when given a truncated or malformed IR file.

**Fix:**
```kotlin
fun fromJson(json: String): GameIR {
    val root = try {
        JSONObject(json)
    } catch (e: org.json.JSONException) {
        throw IllegalArgumentException("GameIR JSON is malformed: ${e.message}", e)
    }
    ...
}
```

---

### WR-05: `hiwater` is re-initialized to 0 on every `moveMetasprite()` call — OAM slots always start at 0, ghost sprites not hidden when two metasprites are rendered per frame

**File:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/MetaspriteVisitor.kt:192`

**Issue:** `generateMetaspriteFrameSwitch` emits `uint8_t hiwater = 0u;` at the top of every
invocation. If a game calls `moveMetasprite(elephant)` and `moveMetasprite(hero)` in the same
scene frame, each call resets `hiwater` to 0 and starts placing sprites at OAM slot 0. The
second metasprite overwrites the first one's OAM entries. Furthermore, `hide_sprites_range` is
called with the count from only the second metasprite's frame, leaving any OAM slots from the
first metasprite's wider frames un-hidden. This is a structural limitation that is currently
undocumented.

**Fix (short-term):** Document the single-metasprite-per-frame constraint on `moveMetasprite()`
with a DSL-level error if called more than once per scene frame. Alternatively, manage `hiwater`
as a persistent scene-level variable rather than a local.

**Fix (long-term):** Thread `hiwater` state across multiple `moveMetasprite()` calls within a
frame by emitting a single `uint8_t hiwater = 0u;` declaration at frame-function scope (not
inside the block) and accumulating across calls.

---

## Info

### IN-01: `MetaspriteRef.flipX`, `flipY`, `subPalette` accessors are dead API — they emit globals that `generateMetaspriteFrameSwitch` never reads

**File:** `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/MetaspriteBuilder.kt:120-139`

**Issue:** `MetaspriteRef.flipX` returns `ActorPropertyRef(id, "flipX")`. An assignment
`elephant.flipX set 1` emits `Assign("elephant.flipX", ...)`, which `visitAssign` lowers to
`_elephant_flipX = 1`. The pipeline declares `_elephant_flipX`, `_elephant_flipY`,
`_elephant_subPalette` as UINT8 globals in `main.c`. However `generateMetaspriteFrameSwitch`
never reads these globals — it hardcodes `_rot >> 2` for sub-palette and `_rot & 0x3u` for flip.
The three globals are always 0 and have no effect on rendering. The KDoc says "Wired to
`OAMF_X_FLIP` in the metasprite visitor (Plan 08+09)" — those plans have not shipped, making
this API surface a forward reference with no backing implementation. Users who write
`elephant.flipX set 1` expecting a visual flip will see no change.

**Suggestion:** Add a compile-time or DSL-time warning that these accessors are not yet wired
to codegen, or remove them from `MetaspriteRef` until Plan 08+09 ships.

---

### IN-02: `bgFillCheckerboard()` indentation is broken in generated C output

**File:** `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/MetaspriteBuilder.kt:268-274`

**Issue:** The raw C string in `bgFillCheckerboard()` does not include leading whitespace for
`fill_bkg_rect(...)` and `set_bkg_data(...)`. In the generated `play_enter()`, these lines
appear at column 0 while surrounding DSL-lowered statements are indented 4 spaces. SDCC accepts
the misaligned code, but it creates an inconsistent style in the generated C that makes diffs
harder to read and tools like linters may flag column-0 function calls.

**Suggestion:** Prefix the raw lines with 4 spaces:
```kotlin
"fill_bkg_rect(0, 0, DEVICE_SCREEN_WIDTH, DEVICE_SCREEN_HEIGHT, 0);\n" +
```
becomes:
```kotlin
"    fill_bkg_rect(0, 0, DEVICE_SCREEN_WIDTH, DEVICE_SCREEN_HEIGHT, 0);\n" +
```

---

### IN-03: `INT16` global initializers use unsigned suffix (`1280u`, `1152u`) — type convention mismatch

**File:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt:799-804`

**Issue:** Global variable declarations for all types use `CLiteral(initialValue)`, which emits
the `u` suffix (e.g., `INT16 _posX = 1280u;`). Per the project's literal-emission convention
(CLAUDE.md "Literal Emission Convention"), `CIntLiteral` should be used for signed-type
initializers. The values fit (1280 < 32767) so SDCC produces no warning, but the generated C
is inconsistent with the convention established in Phase 07.9 and will cause reviewer confusion
when auditing signed-context literals.

**Suggestion:** In `buildHomeFile`, use `CIntLiteral(varDef.initialValue)` for I8/I16 variable
declarations:
```kotlin
val globalVars = gameIR.variables.map { varDef ->
    val init = when (varDef.type) {
        VarType.I8, VarType.I16 -> CIntLiteral(varDef.initialValue)
        else -> CLiteral(varDef.initialValue)
    }
    CVarDecl(name = "_${varDef.name}", type = varTypeToC(varDef.type), initializer = init)
}
```

---

_Reviewed: 2026-05-18_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
