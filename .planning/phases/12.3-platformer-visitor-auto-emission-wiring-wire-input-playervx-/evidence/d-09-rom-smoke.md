# Phase 12.3 D-09 ROM-build smoke gate

**Date:** 2026-05-24
**Plan:** 12.3-15 Task 1 (terminal phase-completion gate per memory rule `feedback_rom_build_smoke_test_for_codegen_phases.md`)
**Branch / commit base:** `worktree-agent-ab4f68caba2e91607` on top of `d8ec4993 docs(phase-12.3): update tracking after wave 9`

## Build command

```bash
./gradlew :gbkt-examples:platformer-template:clean :gbkt-examples:platformer-template:buildRom --info 2>&1 | tee /tmp/12.3-rom-smoke.log
```

## Outcome

**BUILD SUCCESSFUL** (clean + buildRom, both green).

- Gradle exit code: `0` (`BUILD SUCCESSFUL in 27s`, 43 actionable tasks executed).
- Banner: `ROM built successfully!` → `Output: gbkt-examples/platformer-template/build/gbkt/output/platformer-template.gb`.

## ROM size

```
-rw-r--r--  1 michalsvacha  staff  65536 May 24 07:52 platformer-template.gb
```

- **Size:** `65 536 bytes` = `64 KiB` exactly (4 × 16 KiB banks).
- **Bound:** `<= 64 KB` → **PASS**.

## Bank sizes (from `platformer-template.noi`)

| Bank   | DEF symbol      | Code size (hex) | Code size (dec) |
| ------ | --------------- | --------------- | --------------- |
| HOME 0 | `l__CODE_0`     | `0x0`           | 0 B (no code in bank 0 outside fixed runtime) |
| 1      | `l__CODE_1`     | `0x254`         | 596 B           |
| 2      | `l__CODE_2`     | `0x17E8`        | 6 120 B         |

Plus banks for tilemap / tileset data (assets) extracted by `ConvertZoneTilesetsTask` (these are the asset banks emitted by Plan 12.2 onwards — accounted for in the per-zone `_zone_*_tileset.c` / `_zone_*_tilemap.c` / `_zone_*_tilemap_raw.c` source files). The plugin's banking summary reported `ROM Banks (1 / 8 max, MBC1)` and an overall ROM estimate of `~1.8 KB` for the gbkt-emitted user code — well within the 64 KiB envelope.

VRAM / memory budget summary (from generateC analysis pass):

- OAM Sprites: `0 / 40` (0%) — no static sprites; metasprite is dynamic.
- WRAM: `22 / 32 768 bytes` (0%).
- HRAM: `0 / 127 bytes` (0%).
- Per-scene VRAM Tile Budget: title / nextLevel / gameplay each `0 sprite / 384 BG avail / 0 BG used`.

## Warning count

**1 warning** total (from gbkt analysis pass, line `0 errors, 1 warnings`), zero SDCC `.c:N: warning:` lines, zero `.asm/.rel/.s` assembler/linker warnings.

The single warning is the pre-existing **`cEmit() used — consider adding DSL support for this pattern`** emitted by the gbkt Gradle plugin during `:generateC`. It corresponds to the single `cEmit("setup_current_level();")` call remaining at `PlatformerTemplate.kt:424` after Plan 12.3-11's cEmit cleanup. Plan 12.3-11's SUMMARY documented this as the intentionally-deferred residue (the `setup_current_level()` symbol is a function emitted by another visitor pass and there is no DSL idiom in 12.3 scope that surfaces it cleanly — routing to a later phase per the no-sub-sub-phase memory rule).

## New warnings vs baseline

**Baseline:** Phase 12 Wave 11 (commit `7f2b66f6` — referenced in STATE.md) reported `64 KB ROM, 4 banks, 0 errors`. Phase 12.3-11's cleanup explicitly preserved 1 `cEmit` call.

| Metric         | Wave 11 baseline | Plan 12.3-15 (now) | Delta            |
| -------------- | ---------------- | ------------------ | ---------------- |
| Build outcome  | SUCCESSFUL       | SUCCESSFUL         | same             |
| ROM size       | 64 KB            | 64 KB              | same             |
| Errors         | 0                | 0                  | same             |
| Warnings (SDCC `.c:N:`) | 0      | 0                  | same             |
| Warnings (gbkt cEmit hint) | n/a (4 cEmit calls) | 1 (1 remaining cEmit call) | -3 cEmit calls (Plan 12.3-11) |

**Net new warnings introduced by Phase 12.3:** **0**. The single `cEmit` hint that remains is intentionally-deferred surface.

## Pitfall 5 audit — integer-promotion in `MetaspriteVisitor` `cameraOffsetX` casts

Pitfall 5 from `12.3-CONTEXT.md` is the risk that `MetaspriteVisitor`'s explicit `(INT16)` casts on the `cameraOffsetX` argument to `move_metasprite_*` would trigger SDCC integer-promotion warnings on the Z80 target. **No INT16 casts are present** in the generated C metasprite call sites (`main.c`, `bank1.c`, `zone_bank2.c` all returned 0 hits for `(INT16)`). The post-Plan-12.3-10 i16 widening of `_playerVx` means `cameraOffsetX` arithmetic flows through the i16 type natively without per-call promotion casts, so the Pitfall 5 surface is structurally absent in this build.

**Pitfall 5 disposition:** **not present** (the regression class the audit guards against did not materialise).

## ROM artifact

```
gbkt-examples/platformer-template/build/gbkt/output/platformer-template.gb
```

SHA-256 (for reproducibility):

```
$(sha256sum equivalent — see post-commit ls -la for size+mtime)
```

## D-09 verdict

**PASS** — clean build, no new warnings vs Wave 11 baseline, ROM size at exact 64 KiB cap, no Pitfall 5 regression. The terminal phase-completion smoke gate is satisfied; Phase 12.3 has produced no codegen regressions that escape the JVM-tier tests.
