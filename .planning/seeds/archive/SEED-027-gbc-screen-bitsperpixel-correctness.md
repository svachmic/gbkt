# SEED-027 — GBC `GAME_BOY_COLOR_SCREEN` bitsPerPixel correctness

> VERIFIED-ALREADY-FIXED (Phase 21): `grep -n "bitsPerPixel" TargetProfiles.kt` shows `bitsPerPixel = 2` at lines 34 and 53 (no `bitsPerPixel = 4` present); fixed by Phase 18 plan 18-05.

> **Origin:** Phase 17 code review WR-01 ([17-REVIEW.md], [17-UAT.md#1]). Recorded as a developer decision 2026-06-13; routed here so the fix is scoped into Phase 18 planning.

**Status:** VERIFIED-ALREADY-FIXED — closed by Phase 18 plan 18-05; archived Phase 21.
**Routing:** Constants/KDoc correction. Trivial standalone change; rides along with the Phase 18 doc/convention work. NOT a deprecation-cycle item.
**Blast radius:** `gbkt-core/.../constraints/TargetProfiles.kt` (one literal + one KDoc block). Zero runtime consumers today (`ScreenSpec.bitsPerPixel` has no readers), so the change is byte-identical by construction.

## Problem

`TargetProfiles.GAME_BOY_COLOR_SCREEN` (`TargetProfiles.kt:50`) declares
`bitsPerPixel = 4`, and the KDoc prose says "4 bits per pixel". But:

- The **shipped** `GameBoyColorProfile.kt:40` uses `GameBoyConstants.BITS_PER_PIXEL`
  (= 2) with an explicit comment: *"Still 2bpp tiles, but with palettes."*
- GBDK always emits 2bpp tile data; GBC color depth comes from per-tile **palette
  attributes**, not deeper tiles. `4bpp` (16 colors/tile) is GBA, not GBC.
- `ScreenSpec.bitsPerPixel` has **zero readers** anywhere in the tree, so the `4`
  is a latent value, not an active bug — today.

The object's KDoc also asserts "All backends MUST derive from this object," but
only `width`/`height` are actually consumed. The claim overstates reality.

## Decision (recorded 2026-06-13)

**Align the preset to `bitsPerPixel = 2`** (matching `GameBoyColorProfile` and the
hardware model the rest of the codebase uses), fix the "4 bits per pixel" prose,
and **narrow the "All backends MUST derive" KDoc** to the dimensions that are
actually consumed (`width`/`height`). Rationale: the `4` is a landmine — the day
[[SEED-TARGETPROFILE-SCREEN-THREADING]] (backlog/v0.2.0) wires `bitsPerPixel` into
codegen, a `4` would silently mis-size GBC tile data. Correcting it now is free.

## Scope sketch (for Phase 18 planning)

1. `TargetProfiles.kt:50` — `bitsPerPixel = 4` → `2`.
2. Same file — fix the GBC KDoc prose ("4 bits per pixel" → "2 bits per pixel,
   color via 8 hardware palettes") and scope the "MUST derive" claim to width/height.
3. No test churn expected (no consumers); confirm with a build.

## Cross-references

- Full threading of the preset into backends is deferred: [[SEED-TARGETPROFILE-SCREEN-THREADING]] (`.planning/backlog/v0.2.0/`).
- Sibling carry-in from the same review: [[SEED-028-configbuilder-removal-migration-guidance]].
