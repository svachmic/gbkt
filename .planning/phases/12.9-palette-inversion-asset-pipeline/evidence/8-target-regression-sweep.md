---
phase: 12.9
wave: 10
matrix_size: 7
racer: EXCLUDED-LEGACY-PATH
created: 2026-06-01
---

# Phase 12.9 — 7-Target Byte-Identical Regression Sweep (R-07)

> Per CONTEXT D-15: matrix is 7-target (racer dropped — LEGACY-path zone, cannot be touched by NEW-path palette codegen). SPEC text said "8-target"; filename retained for SPEC alignment.

## Build Invocation

Single chained command per `feedback_no_parallel_gradle_clean`:

```bash
./gradlew clean \
  :gbkt-examples:pong:buildRom \
  :gbkt-examples:breakout:buildRom \
  :gbkt-examples:simple-physics:buildRom \
  :gbkt-examples:metasprites:buildRom \
  :gbkt-examples:metasprites-stress:buildRom \
  :gbkt-examples:banks:buildRom \
  :gbkt-examples:platformer-template:buildRom
```

Build verdict: **BUILD SUCCESSFUL in 11s** (exit 0). 114 actionable tasks: 101 executed, 13 up-to-date.
Build log: `evidence/sweep-build.log`

## Pre-12.9 Baseline Sources

| Target | Baseline Source |
|--------|----------------|
| breakout, simple-physics, metasprites, metasprites-stress, banks | Phase 12.8 W5 manifest (`regression-sweep.md`) — these targets were byte-identical through 12.7-R6 → 12.8-W5 and remained unchanged through W3 revert; confirmed byte-identical post-12.9 |
| pong | Phase 12.8 W5 manifest — PASS\* per `project_pong_toolchain_nondeterminism`; hash used as reference only |
| platformer-template | Phase 12.7 R6 manifest (`regression-sweep-round-6.md`) hash `bef124da...` — the W3 revert in Phase 12.8 post-close restored platformer-template to this Phase 12.7-R6 baseline; Phase 12.9 W4+W5+W6 then intentionally changes it to `a452ccf5...` |

## Pre/Post SHA-256 + Verdict Matrix

| # | Target | Pre-12.9 SHA-256 | Post-12.9 SHA-256 | Verdict | Notes |
|---|--------|------------------|-------------------|---------|-------|
| 1 | breakout | `21a42479ed36a2d3a2f62c5d4b2495168ca6a2178fc05010a155a90421796977` | `21a42479ed36a2d3a2f62c5d4b2495168ca6a2178fc05010a155a90421796977` | strict-byte-identical | Unaffected by palette wiring (no NEW-path zones) |
| 2 | simple-physics | `247e16d2df29c9cad2df3f2f5fb68cba00133e95e94b4d47dbcf87c31384f9ad` | `247e16d2df29c9cad2df3f2f5fb68cba00133e95e94b4d47dbcf87c31384f9ad` | strict-byte-identical | Unaffected |
| 3 | metasprites | `c42610991e67b7d33404c9f2aa9725ed449e14c30e0827fa984f363ada3f5f7b` | `c42610991e67b7d33404c9f2aa9725ed449e14c30e0827fa984f363ada3f5f7b` | strict-byte-identical | Unaffected |
| 4 | metasprites-stress | `a5b3657b765a59b8fc8423b6fef286d424cd6870cccc7594f31ca0532cd26764` | `a5b3657b765a59b8fc8423b6fef286d424cd6870cccc7594f31ca0532cd26764` | strict-byte-identical | Unaffected |
| 5 | banks | `c598231420e4fdc1d06f7f386be318723096a22000feb0971584371d2b58fd8f` | `c598231420e4fdc1d06f7f386be318723096a22000feb0971584371d2b58fd8f` | strict-byte-identical | Unaffected |
| 6 | pong | `55b9d3106cab039e607915ef19a7a4ef064bf3e07a0a272f88ac4e2d5b6fe97b` | `4ae15ff85c607d353aa8d28aa26609f1bf9a07ae6765ae84be56b729b4e6ad6d` | PASS\* | Toolchain non-determinism per `project_pong_toolchain_nondeterminism`; generated C byte-identical; NOT a 12.9 regression |
| 7 | platformer-template | `bef124da1346fc5ffd007aca7692c9470f3f1e41f30acdec4487944fd6d8d115` | `a452ccf54be4bfc6cb636db2c6487ee034e9ca44cf2340ab10677dd674c5dfa0` | INTENTIONALLY-CHANGED | Palette wiring (W4+W5) + 32x32 BG clear (W6); hash pinned in Plan 12.9-07 SUMMARY; G3+G4 visual binding gates APPROVED |

**racer: EXCLUDED** — LEGACY-path zone (`_zone_track1_tiles`); cannot be touched by NEW-path palette codegen by construction per D-15. Not in matrix.

**Strict targets (non-pong, non-platformer-template):** 5 / **Pass byte-identical:** 5 / **Fail:** 0
**Pong:** PASS\* (toolchain non-determinism per `project_pong_toolchain_nondeterminism.md`)
**Platformer-template:** INTENTIONALLY-CHANGED — hash `bef124da...` → `a452ccf5...`; matches Plan 12.9-07 pinned value exactly.

## Per-Target Verdict Logic

- **strict-byte-identical**: pre-hash == post-hash, byte-for-byte. PASS.
- **PASS\***: pong only. ROM hash differs every rebuild even at same commit; generated C byte-identical. Pre-existing sdcc/lcc non-determinism documented in memory; NOT a 12.9 regression.
- **INTENTIONALLY-CHANGED**: platformer-template. Palette wiring + BG clear are the phase deliverables; ROM hash CHANGE is expected and bound to G3+G4 user binding gate APPROVED verdicts (W8 + W9 SUMMARYs).

## Cross-checks

**5 strict targets (breakout, simple-physics, metasprites, metasprites-stress, banks):** All 5 have Phase 12.9 post-fix hash equal to Phase 12.7 round-6 baseline (and Phase 12.8 W5 baseline) byte-for-byte. Proves the W4+W5+W6 changes are correctly scoped to the platformer-template's indexed PNG zone tileset path and do NOT leak into other games' codegen paths. **PASS.**

**Pong:** `4ae15ff8...` differs from the reference Phase 12.8 W5 hash (`55b9d310...`). Expected per `project_pong_toolchain_nondeterminism.md` — every rebuild produces a new hash; pre-existing sdcc/lcc artifact, NOT a 12.9 regression. **PASS\*.**

**Platformer-template:** `a452ccf5...` differs from pre-12.9 baseline `bef124da...`. The delta covers:
- `-keep_palette_order` re-activated in `ConvertZoneTilesetsTask.kt` (W4, Plan 12.9-04)
- Per-zone `set_bkg_palette()` upload codegen in `ZoneCodegen.kt` (W5, Plan 12.9-05)
- `fill_bkg_rect(0u, 0u, 32u, 32u, 0u)` BG clear in scene-enter codegen (W6, Plan 12.9-06)
Hash `a452ccf5...` was pinned in Plan 12.9-07 SUMMARY and matches the post-W10 build exactly. **PASS — INTENTIONAL CHANGE.**

## Conclusions

- 5 strict-byte-identical PASS (breakout / simple-physics / metasprites / metasprites-stress / banks): proves W4+W5+W6 changes do NOT regress games without NEW-path zone palettes.
- 1 PASS\* (pong): pre-existing toolchain non-determinism; NOT a 12.9 regression.
- 1 INTENTIONALLY-CHANGED (platformer-template): G3+G4 user-approved visual binding gates close the visual SCs (R-03, R-04, R-05, R-06). Post-fix hash confirmed: `a452ccf54be4bfc6cb636db2c6487ee034e9ca44cf2340ab10677dd674c5dfa0`.
- racer EXCLUDED per D-15: LEGACY-path `_zone_track1_tiles`; outside NEW-path palette codegen blast radius.

**R-07 acceptance criterion met.**

## References

- Pre-12.9 baseline manifest (strict targets + pong): `.planning/phases/12.8-grass-tileset-white-pixels-diagnostic/evidence/regression-sweep.md`
- Pre-12.9 baseline manifest (platformer-template): `.planning/phases/12.7-player-levitating-physics-codegen/evidence/regression-sweep-round-6.md`
- Post-12.9 platformer-template hash pinned by: `.planning/phases/12.9-palette-inversion-asset-pipeline/12.9-07-SUMMARY.md`
- W10 sweep build log: `evidence/sweep-build.log`
- D-15 matrix definition: `.planning/phases/12.9-palette-inversion-asset-pipeline/12.9-CONTEXT.md`
