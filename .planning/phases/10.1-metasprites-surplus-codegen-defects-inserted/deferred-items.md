# Deferred Items — Phase 10.1

Items discovered during phase execution that are out of scope for the plan in
which they were found. Logged per the GSD SCOPE BOUNDARY rule.

---

## DEF-10.1-09-A — `game.h` missing `<gbdk/metasprites.h>` include for `metasprite_t` forward decls

**Status:** RESOLVED in wave-4 inline fixup. Fix landed exactly as proposed below:
`metaspriteHeaderInclude = if (gameIR.metasprites.isNotEmpty()) listOf("<gbdk/metasprites.h>") else emptyList()`,
appended to the `includes` list alongside `cgbHeaderInclude`. Regression test
`gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/MetaspriteHeaderIncludeTest.kt`
(2 @Test functions — positive + negative case) added; both GREEN at fixup commit.

Why fixed inline rather than absorbed into Plan 11 or 12: one-line edit with a
ready-made regression test, pattern mirrors Plan 06 and Plan 07; absorbing into
Plan 11 would conflate D-V1 scope with WR-02 ripple; terminal-subphase rule
precluded a 10.1-11.5 spawn. Reviewed against
`feedback_route_to_proper_phase_when_blast_radius_is_wide.md` — blast radius is
a single function + 2 new lines, well below the routing threshold for new-phase
spawning.

Re-classification note: the agent's "pre-existing on cbe81d29" verdict is correct
but slightly misleading — the defect was INTRODUCED by Plan 07's WR-02 extern
emission (which never paired the extern with its dependent header). Plan 07's
agent verified its own module's tests (which never compile game.h through SDCC,
per the agent's own analysis below) but did not run a ROM-build smoke test that
would have surfaced it. Filed as a process learning: future plans touching
`buildHeaderFile` extern emission MUST run a ROM-build smoke test on at least
one downstream example (per `feedback_rom_build_smoke_test_for_codegen_phases.md`).

**Discovered during:** Plan 10.1-09 (WR-05 frame-scope hiwater hoist), ROM-build
smoke test (`./gradlew :gbkt-examples:metasprites:clean buildRom`).

**Symptom:**
```
game.h:36: error 1: Syntax error, declaration ignored at 'metasprite_t'
game.h:36: syntax error: token -> '*' ; column 26
```

**Root cause:** `GBDKPipelineV2.buildHeaderFile` (lines 2116-2127) emits
`extern const metasprite_t* const sprite_<id>_frames[];` via
`metaspriteAutoExterns` (line 2109-2112) but the `includes` list at line 2120
does NOT include `<gbdk/metasprites.h>` — the header that defines
`metasprite_t`. SDCC errors when `game.h` is processed in any TU because
`metasprite_t` is an undefined type.

**Pre-existence verified:** Issue present at the base commit `cbe81d29`
(`git show cbe81d29:.../GBDKPipelineV2.kt` confirms identical
`includes = listOf("<gb/gb.h>", "<stdio.h>", "<gbdk/console.h>") + cgbHeaderInclude`
and identical `extern const metasprite_t* const sprite_<id>_frames[]`
rawSection emission). Plan 10.1-09 does not touch `buildHeaderFile` and does
not introduce this defect.

**Why not auto-fixed in Plan 10.1-09:** SCOPE BOUNDARY — Plan 10.1-09's edits
are confined to MetaspriteVisitor + GBDKPipelineV2's `buildSceneFile`. The fix
for this defect lives in `buildHeaderFile` (a different code surface, a
different consumer). Per the executor protocol:
> Only auto-fix issues DIRECTLY caused by the current task's changes.
> Pre-existing warnings, linting errors, or failures in unrelated files are
> out of scope.

**Likely fix (one-line):** Add a conditional `<gbdk/metasprites.h>` include
to the header's includes list when `gameIR.metasprites.isNotEmpty()`:

```kotlin
val metaspriteHeaderInclude =
    if (gameIR.metasprites.isNotEmpty()) listOf("<gbdk/metasprites.h>") else emptyList()
// ...
includes = listOf("<gb/gb.h>", "<stdio.h>", "<gbdk/console.h>") +
    cgbHeaderInclude + metaspriteHeaderInclude,
```

This mirrors the existing patterns at lines 1168 (main.c) and 1424 (bank1.c).

**Recommended phase routing:** Phase 10.1 still has Wave 6+ plans pending; if
the defect blocks Plan 12 (synthetic stress ROM) or Plan 13 (UAT re-shoot),
either insert a 10.1-11.5 micro-plan via `gsd-phase split-a-plan` or absorb
the one-line fix into Plan 12 as a Rule 2 deviation. Alternatively the
plan-12 author could add the include as part of their own buildRom-smoke-test
acceptance criterion. JVM-tier tests do not surface this defect because
codegen-shape tests grep over `bank1.c` / `main.c` and never compile `game.h`
through SDCC.

**JVM regression-guard test ready to write** (when fixed):
```kotlin
// MetaspriteHeaderIncludeTest.kt
@Test fun `game_h includes gbdk metasprites_h when game has metasprites`() {
    val gameIR = /* ... 1 metasprite ... */
    val output = GBDKPipelineV2().generate(gameIR)
    val gameH = output.files["game.h"]!!
    assertTrue(
        gameH.contains("#include <gbdk/metasprites.h>"),
        "game.h must include <gbdk/metasprites.h> when metasprites are present " +
            "(SDCC fails to parse `extern const metasprite_t* const sprite_<id>_frames[];` " +
            "without the type definition). game.h head:\n${gameH.take(800)}",
    )
}
```

---

## DEF-10.1-13-A — D-V1 visual partial: elephant tile pixel content still incoherent

**Status:** OPEN. Routes to new micro-investigation plan (per user decision at 10.1-13 checkpoint).

**Discovered during:** Plan 10.1-13 (UAT re-shoot, orchestrator-driven MCP capture).

**Symptom:** Even after Plan 10.1-11's joint two-edit fix landed (Edit 1 + Edit 2 both GREEN, `Seed004ElephantTileRenderingDiagnosticTest` flipped RED→GREEN, `elephant_tiles[]` shrunk from [896] interleaved to [720] dense), the captured behavior1 screenshot shows the elephant as "still slightly broken (not a coherent elephant picture)". User-verified at the human-action checkpoint.

**What we know:**
- `elephant_tiles[720]` (45 tiles × 16 bytes) — port still emits 3 fewer tiles than reference's `sprite_tiles[768]` (48 tiles)
- D-V1 codegen-shape closed at both layers (asset pipeline + runtime emission) per the Plan 10 diagnostic finding
- D-V1 visual NOT closed — distinct from the named two-layer cause Plan 10 identified

**Possible causes (need diagnostic plan analog to 10.1-10):**
1. png2asset deduplication threshold differs between port and reference invocations
2. PNG content itself differs subtly between `gbkt-examples/metasprites/res/sprites/elephant.png` and the reference's source PNG
3. DSL transcription error: tile() x/y/baseId indices in `Metasprites.kt` may not match the reference's metasprite descriptor table exactly
4. png2asset `-noflip` or similar arg differs

**Recommended next plan:** insert between 10.1-13 and 10.1-14 — single combined diagnostic+fix plan (analog to 10.1-10/11 pair compressed). RED test: byte-equality check between port `elephant_tiles` and reference `sprite_tiles`. GREEN: whichever of the 4 causes above turns out to be the named cause.

---

## DEF-10.1-13-B — D-V2 visual partial: BG checker is rectangles not squares

**Status:** OPEN. Routes to new micro-investigation plan.

**Discovered during:** Plan 10.1-13 (UAT re-shoot).

**Symptom:** Behavior1 screenshot shows the checker BG as "rectangles rather than squares — they are slightly wider than taller". The byte pattern from Plan 10.1-02 is correct (`0xF0,0xF0,0xF0,0xF0,0x0F,0x0F,0x0F,0x0F` × 2 rows, locked by `BgCheckerboardEmissionTest`), but visually each cell is non-square.

**What we know:**
- Game Boy tile dimensions are 8×8 pixels (square by hardware)
- `fill_bkg_rect(0, 0, DEVICE_SCREEN_WIDTH, DEVICE_SCREEN_HEIGHT, 0)` tiles the single tile across the 20×18 BG grid
- One 8×8 tile rendered repeatedly on a 8×8-cell grid CANNOT produce rectangles — hardware doesn't stretch tiles
- Therefore the rectangle appearance must come from either (a) the BG tile data itself having different vertical vs horizontal nibble masks, OR (b) the byte ordering inside the tile (planes 0/1 interleaving)

**Possible causes:**
1. The `0xF0/0x0F` byte pattern emits 4-wide vertical strips on top + 4-wide vertical strips on bottom — that IS rectangles (4-wide horizontal × 4-tall vertical, two stacked). The literal is "4x4 checker" but actually renders as `4-wide row-stripes` stacked. NEEDS RE-INSPECTION.
2. Alternative literal that produces true 4×4 squares: alternating `0xF0,0x0F,0xF0,0x0F,...` per row instead of two-row blocks.

**Recommended next plan:** single combined diagnostic+fix. RED test: assert true 4×4 squares (8×8 tile = 2×2 grid of 4×4 cells, each cell a single color). Fix likely a literal-pattern correction in `bgFillCheckerboard`.

---

## DEF-10.1-13-C — D-V3 visual broken: GBC screenshot completely black

**Status:** ESCALATED to Phase 10.2 + SEED-013 per user decision after Plan 10.1-22's fix landed and the GBC re-shoot was STILL all-black. **Critical user-observed regression evidence:** "we had EVIDENCE that cyan worked — the metasprite was broken and the checkerboard was stripes, just like the other screenshots. But it was cyan once already." Pre-Plan-19 ROM produced a cyan elephant (sprite-palette write path WORKED); post-Plan-22 ROM is solid black. One of Plans 10.1-19/20/22 introduced a regression that killed the previously-working sprite-palette rendering, OR there's a 5th layer none of the 4 diagnostic rounds named. Phase 10.1 ships with D-V3 partially closed (variable-mirror + bootstrap-order + cgb_compat-explicit-BG-palette layers all closed at codegen-shape level, all 8 RED diagnostic tests across Plans 19+21 flipped GREEN; only the visual is still broken). Deep dive deferred to Phase 10.2 (`gbc-palette-write-path-d-v3-visual-closure-...`) with proper /gsd:discuss-phase → research → plan flow.

**Re-routing rationale (`feedback_route_to_proper_phase_when_blast_radius_is_wide.md`):** 4 inline diag+fix rounds did not close D-V3 visually. The blast radius now demonstrably spans Coffee-GB emulator skip-bootstrap quirks, possibly real-hardware vs. emulator divergence, plus the pre→post-fix regression. Inline grinding is no longer the right tool.

### Original entry (Plan 10.1-13 surface)

### Original entry (Plan 10.1-13 surface)

**Status:** OPEN. Routes to new micro-investigation plan.

**Discovered during:** Plan 10.1-13 (UAT re-shoot, behavior3 GBC mode).

**Symptom:** Behavior3 screenshot (captured at `rot=8`, `elephant_subPalette=2`, GBC mode) is "completely black". Mechanism evidence is perfect: `_elephant_subPalette` syncs from `_rot` via Plan 10.1-04's emission (reads 0 at rot=0, 1 at rot=4, 2 at rot=8), but no visible sprite or BG output on GBC.

**What we know:**
- D-V3 / IN-01 (Plan 10.1-04) closed the gbkt-side variable mirror — `_elephant_subPalette` global writes from `play_frame()` per the metasprite frame-switch
- This is NOT the actual GBC hardware palette write — that's via `BCPS_REG`/`BCPD_REG` (background) or `OCPS_REG`/`OCPD_REG` (sprite) writes to the GBC palette RAM
- The metasprite `cgbMode=true` boot reached `scene=play` and ran for 49 frames without producing visible output

**Possible causes:**
1. Metasprite's GBC sub-palette WRITE path doesn't actually call `set_sprite_palette` / `OBP_*` with the synced `_elephant_subPalette` value — the global mirror exists but isn't consumed by any palette-write code
2. The metasprite example's `Metasprites.kt` DSL doesn't declare a GBC color palette for the sprite, so even if the sub-palette index is correct, slot 2 is uninitialized (all zeros = all black)
3. Some GBC mode bootstrap is missing — `cgb_compatibility()` is emitted but `set_default_palette` / GBC color RAM init isn't happening for sprite palettes

**Recommended next plan:** single combined diagnostic+fix. RED test: capture GBC palette RAM contents at rot=0/4/8 and assert non-zero color writes. Diagnostic should establish which of the 3 causes above is the named one before fixing.
