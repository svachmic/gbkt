# OAM Attribute Byte Trace — Port vs Reference

Date: 2026-05-19
Plan: 10.1-21 (D-V3 iteration v2, Task 2)

## Reference: png2asset output for the elephant sprite

Source: `/Users/michalsvacha/gbdk/examples/cross-platform/metasprites/obj/gb/res/sprite.c:65-98`
Conversion args: `png2asset /sprite.png -sh 48 -spr8x8 -noflip -c /sprite.c`

```c
const metasprite_t sprite_metasprite0[] = {
    METASPR_ITEM(-16, -24, 0, S_PAL(0)),
    METASPR_ITEM(0, 8, 1, S_PAL(0)),
    METASPR_ITEM(0, 8, 2, S_PAL(0)),
    ...                                         // 31 entries, all S_PAL(0)
    METASPR_ITEM(0, 8, 30, S_PAL(0)),
    METASPR_TERM
};
```

Every single `METASPR_ITEM` entry hardcodes the 4th argument (attribute byte) to `S_PAL(0)`.

### What is `S_PAL(n)`?

From `/Users/michalsvacha/gbdk/include/gb/gb.h:105`:

```c
#define S_PAL(n)     n
```

On Game Boy (and NES), `S_PAL(n) = n` — a no-op macro. So **the reference's per-descriptor
attribute byte is literally `0` (zero) for every sub-tile of every frame**.

The reference does NOT regenerate the descriptor table per frame. It does NOT rotate the
attribute byte per sub-palette. **The descriptor table is static AND uses palette slot 0
in every entry.** The dynamic sub-palette selection happens entirely via the `base_prop`
argument to `move_metasprite_*`.

### How does the reference get sub-palette cycling, then?

From `/Users/michalsvacha/gbdk/include/gb/metasprites.h:140-164`, `move_metasprite_ex`:

```c
inline uint8_t move_metasprite_ex(const metasprite_t * metasprite, uint8_t base_tile,
                                   uint8_t base_prop, uint8_t base_sprite,
                                   uint8_t x, uint8_t y) {
    __current_metasprite = metasprite;
    __current_base_tile = base_tile;
    __current_base_prop = base_prop;
    return __move_metasprite(base_sprite, (y << 8) | (uint8_t)x);
}
```

The runtime `__move_metasprite()` (a sm83 asm routine compiled into `sm83.lib`) iterates
the descriptor table and **OVERWRITES** each generated OAM entry's attribute byte with
`base_prop`. The header docs (lines 42-49) confirm:

> When the move_metasprite_*() functions are called they update all properties for the
> affected sprites in the Shadow OAM. This means any existing property flags set for a
> sprite (CGB palette, BG/WIN priority, Tile VRAM Bank) will get overwritten.

The reference's `metasprites.c:235-238` passes `subpal = rot >> 2` as the `base_prop`:

```c
uint8_t subpal = rot >> 2;
hiwater += move_metasprite_ex(sprite_metasprite0, 0, subpal, hiwater, ...);
```

`subpal ∈ {0,1,2,3}`, which directly indexes sprite palette slots 0..3 (same as the bare
`OAMF_CGB_PAL0..3` constants).

## Port: gbkt-generated descriptor table

Source: `gbkt-examples/metasprites/build/gbkt/generated/main.c:38-49` (post-Plan-20 regen)

```c
const metasprite_t sprite_elephant_frame_0[] = {
    {-16, -24, 0}, {0, 8, 1}, {0, 8, 2}, ..., {0, 8, 30}, {metasprite_end}
};
```

Port uses **3-field initializer** `{dy, dx, dtile}` (omits 4th `props` field).

### Source: `MetaspriteVisitor.kt:148-149`

```kotlin
"{${tile.relY}, ${tile.relX}, ${tile.tileId}}"
```

### What does C do with the missing 4th field?

C99 §6.7.8/21: "If there are fewer initializers in a brace-enclosed list than there are
elements or members of an aggregate ... the remainder of the aggregate shall be initialized
implicitly the same as objects that have static storage duration."

Static storage duration zero-initializes. So `{-16, -24, 0}` for `metasprite_t {dy, dx,
dtile, props}` produces `{-16, -24, 0, 0}` — **`props = 0`**.

The port's descriptor table is **byte-for-byte equivalent** to the reference's descriptor
table (both have `props = 0` for every entry).

### Port's `move_metasprite_*` calls

Source: `main.c:317` (default no-flip case):

```c
hiwater += move_metasprite_ex(sprite_elephant_frames[_idx], 0, subpal, hiwater,
                              DEVICE_SPRITE_PX_OFFSET_X + (_posX >> 4),
                              DEVICE_SPRITE_PX_OFFSET_Y + (_posY >> 4));
```

Positional args match the header signature `(metasprite, base_tile, base_prop, base_sprite,
x, y)`:
- `base_tile = 0`
- `base_prop = subpal` ← **DYNAMIC**, computed each frame from `_rot >> 2`
- `base_sprite = hiwater`

`subpal` is declared at `main.c:296`:

```c
uint8_t subpal = _rot >> 2;
```

So the port DOES pass `subpal` as `base_prop`. The OAM attribute byte at runtime will be
`subpal & 0x07` (the CGB palette selector bits).

## Per-frame OAM attribute byte: side-by-side

| Aspect                              | Reference         | Port              | Match? |
|-------------------------------------|-------------------|-------------------|--------|
| Descriptor `props` field            | `S_PAL(0) = 0`    | `0` (zero-fill)   | ✓ YES  |
| `base_prop` passed to move_metasprite_ex | `subpal = rot>>2` | `subpal = rot>>2` | ✓ YES  |
| OAM attr byte at runtime (final)    | `subpal`          | `subpal`          | ✓ YES  |
| Descriptor regen per frame?         | NO                | NO                | ✓ YES  |

## Verdict on Hypotheses 2 and 3

**Hypothesis 2 (OAM attribute hardcoded to S_PAL(0)):** **FALSIFIED**.
The descriptor `props` field IS 0 — but this is identical to the reference. Runtime
`move_metasprite_*` overwrites the OAM attribute byte from `base_prop`. The port passes
`subpal` (the cycling sub-palette index) as `base_prop` exactly as the reference does.

**Hypothesis 3 (descriptor table needs per-frame regeneration):** **FALSIFIED**.
The reference does NOT regenerate the descriptor table per frame. It relies on the
`base_prop` runtime override. The port does the same. There is no descriptor-regen
mechanism in the GBDK API — the entire design is "static descriptor, dynamic base_prop".

## Runtime corroboration

From `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior3-subpalette-cycle-gbc.json` (frame 61, rot=8):

```
_elephant_subPalette = 2
_current_base_prop = 2     // ← This is the GBDK runtime global set by move_metasprite_ex
_cpu = 17                  // ← CGB_TYPE (0x11) — emulator IS in GBC mode
```

`__current_base_prop = 2` confirms that `subpal=2` reached the GBDK runtime AND was stored.
The runtime sm83.lib then writes `2` into the OAM attribute byte of every elephant sub-sprite.
The PPU reads OAM attr bits[2:0] = 2 → selects sprite palette slot 2 (cyan).

**The dynamic OAM-attribute-byte path is working correctly.** The screen is still black
because of a problem OUTSIDE the OAM-attribute pipeline.
