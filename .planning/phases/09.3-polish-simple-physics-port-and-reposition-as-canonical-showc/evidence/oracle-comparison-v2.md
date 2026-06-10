# Oracle Comparison v2 — Phase 09.3

## Summary

The post-09.3 `SimplePhysics.kt` mirrors the `phys.c` `#define` block (lines 30-34) verbatim via seven `internal const val` declarations at file top. The 1:1 mapping between reference oracle and gbkt port is **preserved and strengthened** by Plan 01's constants extraction: every physics literal that was previously inlined in the DSL frame body now flows through a named constant whose value matches the reference C `#define` it descended from. The five reference `#define`s become seven Kotlin constants because gbkt splits the `MAX_SPEED_IN_SUBPIXELS` / `ACCELERATION_IN_SUBPIXELS` axis-shared defines into per-axis pairs (`MAX_X_SPEED_IN_SUBPIXELS` + `MAX_Y_SPEED_IN_SUBPIXELS`, `X_ACCELERATION_IN_SUBPIXELS` + `Y_ACCELERATION_IN_SUBPIXELS`); two gbkt-only constants (`CARTRIDGE_ROM_ONLY`, `INITIAL_POS_IN_SUBPIXELS`) cover surface that phys.c handles via the C build system and inline `PIXELS_TO_SUBPIXELS(64)` macro respectively.

## Constants Mirror Table

| phys.c #define name | phys.c value | SimplePhysics.kt constant name | SimplePhysics.kt value | Site count in SimplePhysics.kt |
| --- | --- | --- | --- | --- |
| `MAX_X_SPEED_IN_SUBPIXELS` (phys.c L30) | `64` | `MAX_X_SPEED_IN_SUBPIXELS` | `64` | 2 (`-MAX_X_SPEED_IN_SUBPIXELS` lower clamp + bare upper clamp inside `dpad.left.held` and `dpad.right.held` branches) |
| `X_ACCELERATION_IN_SUBPIXELS` (phys.c L31) | `2` | `X_ACCELERATION_IN_SUBPIXELS` | `2` | 2 (`spdX -=` in `dpad.left.held`, `spdX +=` in `dpad.right.held`) |
| `MAX_Y_SPEED_IN_SUBPIXELS` (phys.c L32; axis-split of L31 `MAX_SPEED_IN_SUBPIXELS`) | `64` | `MAX_Y_SPEED_IN_SUBPIXELS` | `64` | 2 (`-MAX_Y_SPEED_IN_SUBPIXELS` lower clamp + bare upper clamp inside `dpad.up.held` and `dpad.down.held` branches) |
| `Y_ACCELERATION_IN_SUBPIXELS` (phys.c L33; axis-split of L32 `ACCELERATION_IN_SUBPIXELS`) | `2` | `Y_ACCELERATION_IN_SUBPIXELS` | `2` | 2 (`spdY -=` in `dpad.up.held`, `spdY +=` in `dpad.down.held`) |
| `JUMP_ACCELERATION_IN_SUBPIXELS` (phys.c L34) | `32` | `JUMP_ACCELERATION_IN_SUBPIXELS` | `32` | 1 (`spdY set -JUMP_ACCELERATION_IN_SUBPIXELS` in `buttons.a.pressed` branch — **the D-01 oracle correction site**) |
| `PIXELS_TO_SUBPIXELS(64)` (inline expansion at phys.c L59) | `1024` (= `64 << 4`) | `INITIAL_POS_IN_SUBPIXELS` (gbkt-only — explicit constant instead of inline macro expansion) | `1024` | 6 (initial value of `posX`, `posY` var declarations + 2 each in scene `enter { }` block reset; pre/post pairs verify the codegen emits `1024u` at L14/L15/L202/L203 of post-09.3-main.c) |
| (no equivalent; banking is the C build system's responsibility) | — | `CARTRIDGE_ROM_ONLY` (gbkt-only — surfaces the cartridge MBC type as a typed constant pending Phase 13 `Cartridge.ROM_ONLY` typed surface — see D-11 PHASE-13 breadcrumb at SimplePhysics.kt L44) | `"ROM_ONLY"` | 1 (`config { cartridge = CARTRIDGE_ROM_ONLY }`) |

**Total constants:** 7 declared, 5 oracle-derived + 2 gbkt-only. The 5 oracle-derived constants are byte-identical to phys.c's `#define` values.

## Generated-C Site Mapping

| Constant | phys.c source | Generated C site (search pattern) | post-09.3-main.c line |
| --- | --- | --- | --- |
| `INITIAL_POS_IN_SUBPIXELS = 1024` | phys.c L59 `PIXELS_TO_SUBPIXELS(64)` | `INT16 _posX = 1024u;` | L14 |
| `INITIAL_POS_IN_SUBPIXELS = 1024` | phys.c L59 (same) | `INT16 _posY = 1024u;` | L15 |
| `INITIAL_POS_IN_SUBPIXELS = 1024` | phys.c L59 (scene-enter reset) | `_posX = 1024u;` | L202 |
| `INITIAL_POS_IN_SUBPIXELS = 1024` | phys.c L59 (scene-enter reset) | `_posY = 1024u;` | L203 |
| `MAX_Y_SPEED_IN_SUBPIXELS = 64` (lower clamp) | phys.c L69 `SpdY < -MAX_Y_SPEED_IN_SUBPIXELS` | `_spdY = -64;` | L212 |
| `MAX_Y_SPEED_IN_SUBPIXELS = 64` (upper clamp) | phys.c L72 `SpdY > MAX_Y_SPEED_IN_SUBPIXELS` | `_spdY = 64u;` | L218 |
| `MAX_X_SPEED_IN_SUBPIXELS = 64` (lower clamp) | phys.c L76 `SpdX < -MAX_X_SPEED_IN_SUBPIXELS` | `_spdX = -64;` | L224 |
| `MAX_X_SPEED_IN_SUBPIXELS = 64` (upper clamp) | phys.c L79 `SpdX > MAX_X_SPEED_IN_SUBPIXELS` | `_spdX = 64u;` | L230 |
| `JUMP_ACCELERATION_IN_SUBPIXELS = 32` | phys.c L83 `SpdY = -JUMP_ACCELERATION_IN_SUBPIXELS` | `_spdY = -32;` (**D-01 site** — the only behavior delta in the refactor-diff) | L234 |
| `Y_ACCELERATION_IN_SUBPIXELS = 2`, `X_ACCELERATION_IN_SUBPIXELS = 2` | phys.c L68/L71/L75/L78 (`SpdY -= …`, `SpdY += …`, `SpdX -= …`, `SpdX += …`) | embedded in the `+= 2u` / `-= 2u` compound-assign emissions inside the four held-direction branches; verify via `grep '+ 2u\|- 2u' post-09.3-main.c` matches the four held-direction sites | (multiple) |

The verifier can re-locate any of these sites in a future regenerated build by running `grep -n '<pattern>' <build path>/main.c`. Patterns are stable across the codegen because each constant pins a specific literal width into the emitted C.

## Differences from Phase 09's oracle-comparison.md

- **v2 is constants-focused** (the post-09.3 polish surface); **v1 was a full three-signal report** (codegen quality + ROM size + DSL value). v1 remains the **canonical Phase 09 record** at `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/oracle-comparison.md` and is not superseded by v2.
- v2 specifically tracks the constants-extraction work that Phase 09.3 added on top of Phase 09's port. The Phase 09.3 polish does NOT change the v1 three-signal verdicts; it strengthens the codegen-quality signal by giving every literal a name.
- v2 records the **D-01 oracle correction at the JUMP site** (`_spdY = -32`) — the only behavior delta in the Phase 09.3 refactor-diff, paired with the explicit verifier-facing deviation note in `09.3-04-SUMMARY.md` § Accepted Deviation from Roadmap Success Criterion.

---

*D-IDs implemented by this artifact: D-01 (JUMP oracle correction recorded as the only behavior delta in the constants mirror), D-03 (constants block extracted to mirror phys.c L30-34 verbatim — six oracle-aligned constants documented), D-13 (verifier-facing evidence artifact published).*
