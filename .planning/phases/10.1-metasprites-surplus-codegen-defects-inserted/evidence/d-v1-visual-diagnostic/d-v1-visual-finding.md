# D-V1 Visual Diagnostic Finding — Elephant Tile Pixel Content Still Incoherent

**Phase:** 10.1-metasprites-surplus-codegen-defects-inserted
**Plan:** 10.1-15 (diagnostic — name the bug; Plan 10.1-16 fixes)
**Defect:** DEF-10.1-13-A
**Authored:** 2026-05-19

---

## Background

Plan 10.1-11 closed D-V1 at the codegen-shape level via a joint two-layer fix:

- **Edit 1** (asset pipeline) — `ConvertSpritesTask.kt`'s `resolveSpriteMode()` now
  detects metasprite-bound PNGs (via the `sprite_<stem>_frames` descriptor reference
  in main.c) and forces `png2asset -spr8x8` regardless of PNG height.
- **Edit 2** (runtime emission) — `GBDKPipelineV2.buildMainFunction` now conditionally
  emits `SPRITES_8x8;` between `cgb_compatibility();` and `DISPLAY_ON;` when
  `gameIR.metasprites.isNotEmpty()`.

Result: `elephant_tiles[]` shrunk from pre-fix `[896]` interleaved (56 tiles, every
other blank) to post-fix `[720]` dense (45 tiles). Codegen shape contract: GREEN.

The Plan 10.1-13 UAT re-shoot (orchestrator-driven MCP capture) then revealed the
elephant rendering is "still slightly broken (not a coherent elephant picture)". Port
emits 45 tiles; reference emits 48 tiles. **3 tiles' worth of pixel content is still
missing.**

---

## Hypothesis Tested

Four candidate causes from the deferred-items entry (DEF-10.1-13-A):

1. **png2asset deduplication threshold** — port and reference use different
   invocation flags controlling how many duplicate tiles get merged.
2. **PNG content drift** — port's `gbkt-examples/metasprites/res/sprites/elephant.png`
   differs from the reference's source PNG.
3. **DSL transcription error** — `Metasprites.kt`'s `tile(x, y, baseId)` indices don't
   match the reference's `METASPR_ITEM(dy, dx, dtile, attr)` table after the
   documented `(dy, dx, dtile) -> (x=dx, y=dy, baseId=dtile)` arg swap.
4. **png2asset arg drift** — `-noflip`, `-sh N`, `-c` etc. arg differs between port
   (`ConvertSpritesTask`) and reference (Makefile).

---

## Evidence Captured

| File | Summary |
|------|---------|
| `port-vs-reference-tile-bytes.txt` | xxd dumps of both tile arrays + size math. Port `elephant_tiles[720]` (45 tiles, max baseId 44). Reference `sprite_tiles[768]` (48 tiles, max baseId 47). DSL `Metasprites.kt` references baseIds 0..47 (all 48) — so DSL baseIds 45/46/47 dereference past the end of port emission. |
| `png2asset-args-grep.txt` | Conversion-args comment from both generated files + 4-variant isolation experiment. Reference: `-sh 48 -spr8x8 -noflip`. Port: `-spr8x8` only. The 4-variant experiment proves `-noflip` ALONE controls the 720 vs 768 difference; `-sh` is irrelevant. Without `-noflip`, png2asset emits `S_FLIPX` attrs on METASPR_ITEM entries. |
| `png-content-comparison.txt` | md5 ecd8079c82760223213cd14aa3f4b1a1 on both PNGs; cmp clean. Source PNGs are byte-for-byte identical. |
| `dsl-transcription-check.txt` | Per-frame tile counts match exactly (31+33+33+32+32 = 161 in both DSL and reference). Spot-checks of dx/dy/baseId on multiple entries match after the documented arg-swap. DSL is faithful to the reference's `-noflip` id space. |

---

## Hypothesis Verdicts

| # | Hypothesis | Verdict | Evidence |
|---|------------|---------|----------|
| 1 | png2asset dedup threshold | **NEEDS RE-NAMING (subsumed by #4)** | The dedup is controlled by an arg, not a threshold — `-noflip` toggles it. Verdict folds into hypothesis 4. |
| 2 | PNG content drift | **REJECTED** | Both PNGs are byte-for-byte identical (md5 ecd8079c..., cmp clean). |
| 3 | DSL transcription error (narrow) | **REJECTED** | Per-frame counts match exactly; spot-checks of dx/dy/baseId line up with reference METASPR_ITEM after the documented arg-swap. The DSL is faithful to the reference's id space. |
| 3 | DSL ↔ asset-pipeline id-space mismatch (broad) | **ACTIVE — symptom of #4** | DSL speaks the `-noflip` id space (0..47); port's `elephant_tiles[720]` only contains ids 0..44. Mismatch surfaces as garbage pixels for baseIds 45/46/47. Root cause is upstream in #4, not in the DSL. |
| 4 | png2asset arg drift (`-noflip` missing) | **ACTIVE — NAMED ROOT CAUSE** | Port's `ConvertSpritesTask.kt` lines 199-205 build args = `[pngPath, "-o", outPath]` then conditionally adds `-spr8x8`. **Never adds `-noflip`.** Reference Makefile invokes `png2asset ... -sh 48 -spr8x8 -noflip -c sprite.c`. 4-variant experiment isolates `-noflip` as the sole discriminator between 768-byte and 720-byte output. |

---

## Named Root Cause (singular per D-05)

**`ConvertSpritesTask` omits the `-noflip` png2asset argument when converting
metasprite-bound PNG assets.**

Concretely: at `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ConvertSpritesTask.kt`
lines 199-205, the args list for invoking `png2asset` is:

```kotlin
val args = mutableListOf(pngFile.absolutePath, "-o", outputC.absolutePath)
when (spriteMode) {
    SpriteMode.SPR8x8 -> args.add("-spr8x8")
    SpriteMode.SPR8x16 -> { /* default, no flag needed */ }
}
```

Without `-noflip`, png2asset's default behaviour is to detect tiles that are mirrors
of each other and emit ONE tile in `elephant_tiles[]` plus an `S_FLIPX`/`S_FLIPY` attr
on the METASPR_ITEM entries that need the mirrored variant. This shrinks the unique
tile count from 48 to 45 and renumbers the surviving tile ids.

The DSL `Metasprites.kt` was hand-transcribed from the reference's `-noflip` output —
where tile ids are 0..47. After Plan 10.1-11's two-layer fix, the port emits tile ids
0..44 only. **DSL `tile()` baseIds 45, 46, 47 dereference past the end of
`elephant_tiles[720]`** (each tile is 16 bytes, so the OOB reads pull whatever follows
the array in ROM — garbage pixels).

This is a **singular** root cause per D-05 discipline: the asset pipeline disagrees
with the DSL on which png2asset id space is canonical. The fix names a single side of
the contract (the asset pipeline) to match the other (the DSL).

---

## Fix Shape Proposed for Plan 10.1-16

**One literal addition.** Inside the metasprite-bound branch of `convertSprite()` (i.e.,
the same code path that already passes `-spr8x8` for `SpriteMode.SPR8x8`), append
`-noflip` to the args list. The natural placement is immediately after the existing
`args.add("-spr8x8")` line, gated by the same `SpriteMode.SPR8x8` switch arm OR by an
explicit `if (isMetaspriteBound(includePath, mainCContent))` check.

```kotlin
// Existing:
when (spriteMode) {
    SpriteMode.SPR8x8 -> args.add("-spr8x8")
    SpriteMode.SPR8x16 -> { /* default, no flag needed */ }
}

// Plan 10.1-16 adds:
if (ConvertSpritesTask.isMetaspriteBound(includePath, mainCContent)) {
    args.add("-noflip")
}
```

Why gated on `isMetaspriteBound(...)` rather than fused into the `SpriteMode.SPR8x8`
arm: keeps the orthogonal flags separate. `-noflip` is a metasprite-pipeline contract
(the DSL transcribes from `-noflip` output); `-spr8x8` is a sprite-size contract
(metasprite or otherwise). A non-metasprite SPR8x8 sprite (e.g. a 16×16 Pong paddle
sliced to 4 tiles) MUST NOT get `-noflip` — it would suppress legitimate dedup for
actor tilesets that benefit from mirror reuse. Plan 10.1-11's `isMetaspriteBound()`
helper is the right gate.

### Why the fix won't regress

- Non-metasprite sprites (Pong paddle, Breakout brick): `isMetaspriteBound()` returns
  false → no new arg → behaviour unchanged.
- Metasprite sprites (elephant, future): `isMetaspriteBound()` returns true → `-noflip`
  added → `elephant_tiles[]` grows to 768 → DSL baseIds 45/46/47 land inside the array
  → coherent elephant pixels.

### Test plan for Plan 10.1-16

- Flip `DV1VisualDiagnosticTest.convertSpritesTask_currently_omits_noflip_arg_for_metasprite_PNGs`
  from RED to GREEN (this plan introduces the RED test).
- Add an integration test asserting `elephant_tiles[]` array size grows from 720 to
  768 after a clean `:gbkt-examples:metasprites:convertSprites` (per
  `feedback_rom_build_smoke_test_for_codegen_phases.md` — JVM-tier tests cannot see
  staleness in `build/gbkt/generated/`).
- Re-shoot the Phase 10 UAT screenshot (behavior1-animation-advance) and confirm the
  elephant renders coherently (per CLAUDE.md Visual Evidence Rule — variable
  assertions and codegen-shape tests are necessary but never sufficient for visual
  truths).

### Scope caveat

If a future metasprite asset is authored from-scratch in gbkt (not transcribed from
a reference's `-noflip` output), the asset author may legitimately want to consume
png2asset's flip-deduped output to save ROM. That would require a per-metasprite
opt-in DSL flag (e.g. `metasprite { noflip = false; frame { ... } }`) and a matching
extension to `isMetaspriteBound()` that consults the IR rather than just the
descriptor-reference signal. Out of scope for 10.1-16 — the only known metasprite in
gbkt-examples is the elephant, and the elephant DSL definitively transcribes from
`-noflip` output.

---

## RED Diagnostic Test (Task 5)

`DV1VisualDiagnosticTest` (in
`gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/tasks/`) is authored in
this plan and locks the named root cause. The test asserts that the production source
of `ConvertSpritesTask.kt` contains the literal `"-noflip"`. At this plan's close the
test is RED — the literal is absent from the file. Plan 10.1-16's one-line addition
turns it GREEN.

### Why a source-level grep test (and not a behavioural test)

The `args` list is built inline inside the `convertSprite()` method (lines 199-205).
Extracting a testable helper that returns the args list would require a production
refactor outside the scope of this diagnostic-only plan. A source-level grep is a
legitimate RED-lock pattern: the only literal the production source could legitimately
contain `"-noflip"` for is the fix Plan 16 will make. The fragility is acceptable for
a single-purpose diagnostic test.

### Deviation note

The plan's `<files_modified>` frontmatter named the test path as
`gbkt-backend-gbdk/src/test/kotlin/.../DV1VisualDiagnosticTest.kt`. The test is
actually committed at
`gbkt-gradle-plugin/src/test/kotlin/.../DV1VisualDiagnosticTest.kt` because
`gbkt-backend-gbdk` does not depend on `gbkt-gradle-plugin` (verified at
`gbkt-backend-gbdk/build.gradle.kts`) — the `ConvertSpritesTask` type is not
visible from `gbkt-backend-gbdk`'s test classpath. Co-locating with the existing
`ConvertSpritesTaskMetaspriteDefaultTest` is the natural home for the contract under
test. This is a Rule 3 deviation (blocking — fix inline): the plan's path would not
compile. Documented in SUMMARY.md.
