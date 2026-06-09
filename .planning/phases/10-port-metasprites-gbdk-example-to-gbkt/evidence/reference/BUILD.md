# Metasprites Reference Build

## Purpose

This directory holds the canonical GBDK reference ROM (`metasprites.gb`) and
reference C source (`metasprites.c`) used as the codegen-quality oracle for
Phase 10 (port the GBDK `metasprites` cross-platform example to gbkt).

The reference is the upstream GBDK-2020 cross-platform example at
`/Users/michalsvacha/gbdk/examples/cross-platform/metasprites/`.

Binaries (`.gb`, `.map`, `.noi`, `.ihx`, `.sym`, `.rel`, `.lst`, `.asm`) are
intentionally **NOT committed** (per D-11 — see `10-CONTEXT.md`). Rebuild
locally using the commands below whenever a binary is needed for Plan 16
ROM-size comparison. The committed `metasprites.c` is byte-identical to the
upstream source and serves the C-diff appendix in Plan 16.

## Prerequisites

- GBDK-2020 installed (https://github.com/gbdk-2020/gbdk-2020/releases)
- GBDK installed at `/Users/michalsvacha/gbdk` (or adjust `GBDK_HOME` below)

## Build Command

```bash
cd /Users/michalsvacha/gbdk/examples/cross-platform/metasprites
GBDK_HOME=/Users/michalsvacha/gbdk make gb
```

This produces `build/gb/metasprites.gb` (the ROM) alongside `.map`, `.noi`,
and other companion files.

## Copy Artifacts to Evidence Directory

After building, copy the artifacts into this directory for Plan 16 comparison:

```bash
# Absolute path to this evidence directory:
EVIDENCE=/Users/michalsvacha/GitHub/personal/gbkt/.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/reference

cp /Users/michalsvacha/gbdk/examples/cross-platform/metasprites/build/gb/metasprites.gb  "$EVIDENCE/"
cp /Users/michalsvacha/gbdk/examples/cross-platform/metasprites/build/gb/metasprites.map "$EVIDENCE/"
cp /Users/michalsvacha/gbdk/examples/cross-platform/metasprites/build/gb/metasprites.noi "$EVIDENCE/"
cp /Users/michalsvacha/gbdk/examples/cross-platform/metasprites/src/metasprites.c        "$EVIDENCE/"
```

## Outputs

Expected files after the build and copy:

| File             | Description                                      | Committed? |
|------------------|--------------------------------------------------|------------|
| `metasprites.gb` | Reference ROM binary                             | NO — gitignored |
| `metasprites.map` | Linker map file                                 | NO — gitignored |
| `metasprites.noi` | Linker symbol file (bank/section sizes)         | NO — gitignored |
| `metasprites.c`  | Reference C source (upstream, byte-identical)    | YES |
| `BUILD.md`       | This file                                        | YES |

## Verification (ROM Size Measurement)

Run these commands after copying artifacts to get the comparison numbers for
Plan 16:

```bash
# File size (ROM as written to disk — expect 32768 bytes for ROM_ONLY)
wc -c /Users/michalsvacha/gbdk/examples/cross-platform/metasprites/build/gb/metasprites.gb

# Actual code size from .noi (hex → convert to decimal for comparison)
grep '^DEF l__CODE' /Users/michalsvacha/gbdk/examples/cross-platform/metasprites/build/gb/metasprites.noi

# HOME bank size
grep '^DEF l__HOME' /Users/michalsvacha/gbdk/examples/cross-platform/metasprites/build/gb/metasprites.noi
```

For the gbkt port, the equivalent verification reads from:
- ROM: `gbkt-examples/metasprites/build/gbkt/output/metasprites.gb`
- NOI: `gbkt-examples/metasprites/build/gbkt/output/metasprites.noi`

After running `./gradlew :gbkt-examples:metasprites:buildRom`.

## Gitignore Note

All build artifacts (`.gb`, `.map`, `.noi`, `.ihx`, `.rel`, `.sym`, `.lst`,
`.asm`) under this directory are gitignored. Only `BUILD.md` and
`metasprites.c` (committed as text evidence) are tracked. See the
`.gitignore` entries for this phase's `evidence/reference/` path.

## Compiler Flags Used

The Makefile builds `metasprites.gb` with:

```
-Wl-yt0x1B -autobank -Wl-j -Wm-yoA -Wm-ya4 -Wb-ext=.rel -Wb-v
```

Key flags:
- `-autobank`: Automatic bank allocation
- `-Wl-j`: Join banks
- `-Wm-yoA`: Output addressing mode A
- `-Wm-ya4`: 4 banks allocated
- `NOT -Wm-yc` for the `gb` target (plain DMG build, not GBC compatible)

The gbkt port uses `config { target(GbcTarget.GBC_COMPATIBLE) }` which adds
`-Wm-yc` — GBC-compatible is the intended final target per D-09.
