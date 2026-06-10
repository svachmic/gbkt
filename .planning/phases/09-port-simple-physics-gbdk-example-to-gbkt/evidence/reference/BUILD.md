# Simple Physics Reference Build

## Purpose

This directory holds the canonical GBDK reference ROM source (`phys.c`) used as
the codegen-quality oracle for Phase 9 (port the GBDK `simple_physics` example
to gbkt). The reference is the upstream GBDK-2020 cross-platform example at
`/Users/michalsvacha/gbdk/examples/cross-platform/simple_physics/`.

Binaries (`.gb`, `.map`, `.noi`, `.ihx`, `.sym`) are intentionally **NOT
committed** (per D-10 — see `09-CONTEXT.md`). Rebuild locally using the command
below whenever a binary is needed. The committed `phys.c` is byte-identical to
the upstream source and serves Plan 07's C-diff appendix.

## Prerequisites

- GBDK-2020 installed (https://github.com/gbdk-2020/gbdk-2020/releases)
- `GBDK_HOME` env var set, or GBDK installed at the default path
  `/Users/michalsvacha/gbdk`

## Build Command

```bash
cd /Users/michalsvacha/gbdk/examples/cross-platform/simple_physics
GBDK_HOME=/Users/michalsvacha/gbdk make gb
```

This produces `build/gb/physics.gb` (the ROM) alongside `.map`, `.noi`, and
`.sym` companion files.

## Outputs

Verified baseline from Phase 9 research (`09-RESEARCH.md` §"ROM-Size Baseline"):

| Metric                            | Value                              |
|-----------------------------------|------------------------------------|
| ROM file size                     | 32768 bytes (standard 32 KB header-pad) |
| Actual code size (`l__CODE`)      | 574 bytes (0x23E)                  |
| HOME bank size (`l__HOME`)        | 187 bytes (0xBB)                   |
| Data size (`l__DATA`)             | 26 bytes (0x1A)                    |

The 32768-byte file size is the standard 32 KB minimum a Game Boy cartridge
header pads to; the meaningful codegen-size metric is `l__CODE` (574 bytes).

## Within-2× Target

The gbkt port's actual-code size MUST be **≤ 1148 bytes** (= 574 × 2) per the
Phase 9 ROADMAP success criterion. Filesystem ROM size will likely match
exactly (both ROMs pad to the same 32 KB minimum).

## Verify ROM size

```bash
# File size (ROM as written to disk)
stat -f%z /Users/michalsvacha/gbdk/examples/cross-platform/simple_physics/build/gb/physics.gb

# Actual code size from .noi (linker output map; DEF l__CODE gives the hex byte count)
grep '^DEF l__CODE' /Users/michalsvacha/gbdk/examples/cross-platform/simple_physics/build/gb/physics.noi
# Convert hex → decimal to compare against the 574-byte baseline
```

For the gbkt port, the equivalent verification reads from
`gbkt-examples/simple-physics/build/gbkt/output/simple-physics.gb` and the
corresponding `.noi` under `build/gbkt/output/` after `./gradlew
:gbkt-examples:simple-physics:buildRom`.
