# D-V2 Visual Diagnostic Finding

**Defect ID:** DEF-10.1-13-B
**Discovered:** Plan 10.1-13 (user-verified UAT re-shoot at the human-action checkpoint)
**Diagnosed:** Plan 10.1-17 (this file)
**Status:** ROOT CAUSE NAMED — ready for Plan 10.1-18 fix

## TL;DR

The byte literal Plan 10.1-02 shipped in `bgFillCheckerboard()` renders per tile as **4-pixel-wide × 2-pixel-tall** alternating cells (a "thin horizontal-bar" pattern), NOT the intended 4×4-pixel square checker cells. When uniformly tiled across the 20×18-tile BG grid by `fill_bkg_rect`, this produces the visible **wider-than-tall rectangles** the user reported at the Plan 10.1-13 checkpoint.

The literal is wrong; the alternation period needs to be **4 rows** (consecutive `0xF0` rows then consecutive `0x0F` rows), not the current **2 rows** (4-byte groups alternating mid-literal).

## Named root cause

**The bytes for plane 0 and plane 1 are correctly identical (both planes lit ⇒ color 3), but the row-to-row alternation period is wrong.**

A Game Boy tile is 8 pixels tall × 8 pixels wide, encoded as 8 rows × 2 bytes per row (plane 0 byte followed by plane 1 byte). For a true 4×4-pixel-square checker, the 4 consecutive rows in the top half of the tile must share the same horizontal byte (e.g., all `0xF0` for "left 4 pixels lit"), and the 4 consecutive rows in the bottom half must share the inverted byte (e.g., all `0x0F` for "right 4 pixels lit").

The Plan 10.1-02 literal achieves the per-plane symmetry (plane 0 == plane 1 ⇒ color 3 vs color 0) but lays out the rows as:

| Tile row | (plane0, plane1) | Lit nibble |
|----------|------------------|-----------|
| 0 | (`0xF0`,`0xF0`) | left 4 |
| 1 | (`0xF0`,`0xF0`) | left 4 |
| 2 | (`0x0F`,`0x0F`) | right 4 |
| 3 | (`0x0F`,`0x0F`) | right 4 |
| 4 | (`0xF0`,`0xF0`) | left 4 |
| 5 | (`0xF0`,`0xF0`) | left 4 |
| 6 | (`0x0F`,`0x0F`) | right 4 |
| 7 | (`0x0F`,`0x0F`) | right 4 |

Alternation period in tile rows = **2**, so per-tile cells are 4w × 2h. Uniform tiling preserves the aspect, hence the user-visible "rectangles wider than taller".

The intended layout for true 4×4-square cells is:

| Tile row | (plane0, plane1) | Lit nibble |
|----------|------------------|-----------|
| 0 | (`0xF0`,`0xF0`) | left 4 |
| 1 | (`0xF0`,`0xF0`) | left 4 |
| 2 | (`0xF0`,`0xF0`) | left 4 |
| 3 | (`0xF0`,`0xF0`) | left 4 |
| 4 | (`0x0F`,`0x0F`) | right 4 |
| 5 | (`0x0F`,`0x0F`) | right 4 |
| 6 | (`0x0F`,`0x0F`) | right 4 |
| 7 | (`0x0F`,`0x0F`) | right 4 |

Alternation period in tile rows = **4**, per-tile cells are 4w × 4h ⇒ true square checker.

## Why Plan 10.1-02 shipped the wrong literal

The plan author and the orchestrator both held the same incorrect mental model: that the textual byte grouping `0xF0,0xF0,0xF0,0xF0,0x0F,0x0F,0x0F,0x0F` represented **one pixel row** of a checker (left half lit, right half lit). In reality it represents **two pixel rows** (because each tile row consumes 2 bytes). Repeating the 8-byte group twice produces a 16-byte tile that alternates every 2 tile rows, not every 4.

The KDoc inside `MetaspriteBuilder.kt` also restated the wrong model:

> "whose top 4 rows are `11110000` (plane 0 high nibble lit) and whose bottom 4 rows are `00001111` (plane 0 low nibble lit)"

— but the literal it documents emits 2-row, not 4-row, half-tile groupings. The docstring intent matches the desired layout; only the byte literal under it is mis-grouped.

The `Seed005CheckerboardBytePatternTest` then locked the wrong shape because it asserted the exact substring `"0xF0,0xF0,0xF0,0xF0,0x0F,0x0F,0x0F,0x0F"` appears **exactly twice** in the emitted code. That string is correct as a tile half-description but not as a per-row repeating unit, and the test passing prevented the per-row period error from being caught.

This is exactly the failure mode `feedback_visual_evidence_for_visual_truths.md` warned about: a JVM-tier codegen-shape test passed GREEN against an authored literal whose visual outcome was never verified — only the user's UAT re-shoot caught the mismatch.

## Plan 10.1-18 fix shape (proposed)

**Smallest possible change.** Three coordinated edits, all in `MetaspriteBuilder.kt` + the locking test:

1. **`MetaspriteBuilder.kt:bgFillCheckerboard()` — re-emit the literal with the corrected layout.**

   Replace:
   ```
   "    0xF0,0xF0,0xF0,0xF0,0x0F,0x0F,0x0F,0x0F,\n" +
   "    0xF0,0xF0,0xF0,0xF0,0x0F,0x0F,0x0F,0x0F};\n" +
   ```
   With:
   ```
   "    0xF0,0xF0,0xF0,0xF0,0xF0,0xF0,0xF0,0xF0,\n" +
   "    0x0F,0x0F,0x0F,0x0F,0x0F,0x0F,0x0F,0x0F};\n" +
   ```
   Pixel decode: top 4 tile rows = (`0xF0`,`0xF0`) ⇒ left 4 columns color 3; bottom 4 tile rows = (`0x0F`,`0x0F`) ⇒ right 4 columns color 3 ⇒ true 4×4 square checker.

2. **`MetaspriteBuilder.kt:bgFillCheckerboard()` KDoc — tighten the comment to match.** The current KDoc text "top 4 rows are `11110000` (plane 0 high nibble lit) and bottom 4 rows are `00001111`" already describes the CORRECTED layout — it just doesn't match the buggy literal directly above it. After the literal swap, the KDoc and the literal will agree. No KDoc edit strictly required, but a one-line note at the top of the literal explaining the per-row 2-byte stride and the 4-row alternation period would help any future reader avoid the same trap. (Avoid quoting the WRONG literal in the KDoc — that would re-trip the file-level grep gate equivalent of D-V2's diagonal-stripe rule.)

3. **`Seed005CheckerboardBytePatternTest.kt` — replace the locked substring with one that:**
   - Asserts the **corrected** 16-byte literal shape (top 8 bytes are `0xF0` × 8; bottom 8 bytes are `0x0F` × 8).
   - Adds a NEGATIVE guard that the OLD wrong substring `"0xF0,0xF0,0xF0,0xF0,0x0F,0x0F,0x0F,0x0F"` appears ZERO times in the emitted code (regression guard against re-introducing the 2-row period).
   - Keeps the existing diagonal-stripe negative guard (`"0x80,0x80,0x40,0x40"` zero occurrences) so D-V2 v1 cannot re-surface.

4. **Optional (recommended): rename the helper to make the assertion explicit.** Currently `bgFillCheckerboard()`. After the fix it would be more precise to say `bgFill4x4Checkerboard()` — but that's a renaming churn against existing call sites (`gbkt-examples/metasprites/src/.../Metasprites.kt`). **Recommendation: skip the rename in Plan 18.** Stick with the current name + a tightened KDoc; the helper name is honest once the literal is correct.

## RED test asserted in Plan 10.1-17

This plan creates `DV2BgAspectDiagnosticTest` that asserts the CORRECTED literal shape. It MUST be RED on the current `HEAD` (Plan 02's wrong literal) and MUST flip GREEN once Plan 10.1-18 applies the literal swap above. See `gbkt-lang/src/test/kotlin/io/github/gbkt/core/dsl/DV2BgAspectDiagnosticTest.kt`.

The diagnostic test is **separate from** the existing `Seed005CheckerboardBytePatternTest` on purpose: Plan 10.1-18 will replace Seed005's asserted shape, but the new diagnostic test should remain as a permanent regression lock for the per-row alternation period (semantic invariant: "BG checker cells are square, not rectangles"). The two together form belt-and-suspenders coverage: Seed005 locks the exact emitted substring; DV2Aspect locks the structural invariant that justifies why the substring is what it is.

## Out-of-scope (NOT for Plan 18)

- **MCP-driven runtime VRAM read** at `0x8000..0x800F` for redundant hardware-side confirmation. The DSL → generated-C → `set_bkg_data` chain is verbatim memcpy (no transformation), so the JVM-tier codegen lock + screenshot re-shoot is sufficient. If the orchestrator wants extra rigor it can add a `/gbkt-play-game metasprites` step that dumps the VRAM bytes post-enter and pipes them into an assertion — but that's a polish task, not a fix task.

- **Visual re-shoot of the 3 Phase 10 behavior screenshots.** Per the chain established in Plan 10.1-13, the re-shoot belongs to whichever plan declares Phase 10.1 closed (currently Plan 10.1-14). Plan 18's contract is the codegen literal swap + the test flip; the visual re-shoot is the verification at Plan 14, not at Plan 18.

- **Aspect-correction at the renderer level** (e.g., emulating non-square LCD pixels). The GB hardware has square pixels. The "rectangles" are honestly encoded in the bytes; there's no aspect distortion to compensate for. The fix is the bytes themselves.

## Closure criteria for DEF-10.1-13-B

- [ ] Plan 10.1-18 applies the literal swap above
- [ ] `DV2BgAspectDiagnosticTest` (this plan, Task 3) flips RED → GREEN
- [ ] `Seed005CheckerboardBytePatternTest` is updated to match the new shape (per Plan 10.1-18) and stays GREEN
- [ ] Plan 10.1-14 re-shoots `behavior1-animation-advance.png` after a clean rebuild; user confirms checker cells are visibly square (1:1 aspect) in the re-shot screenshot
- [ ] No new defects surfaced by the re-shoot
