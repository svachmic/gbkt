# metasprites-stress

**THROWAWAY codegen-verification ROM. NOT a user-facing example.**

Synthetic stress game introduced by Phase 10.1 (D-06 / D-07) to force composition of
the four latent-cluster codegen defects + the two absorbed warnings under the SDCC +
GBDK lcc toolchain. JVM emission tests in `gbkt-backend-gbdk` lock the shape of each
defect's fix in isolation; this ROM is the **binding integration evidence** that the
fixes compose into a ROM that lcc accepts and links cleanly.

## What it forces

| ID    | Defect / warning                                              | Forced by                                            |
|-------|---------------------------------------------------------------|------------------------------------------------------|
| CR-01 | Actor + metasprite VRAM coexistence (unified loader)          | 1 actor sprite (`player.png`) coexists with 2 metasprites |
| CR-02 | Per-bank include scan for `<gbdk/metasprites.h>`              | 2 scenes (`title` + `play`) escape `BankingAnalysisPass`'s single-scene HOME fast-path, producing a real `bank1.c` that imports `move_metasprite_ex` |
| CR-03 | Distinct metasprite symbol namespacing                        | 2 distinct metasprites (`elephant`, `tiger`) — duplicate symbols would clash without per-metasprite prefix |
| WR-05 | Multi-metasprite-per-frame hiwater scope                      | `play` frame calls `moveMetasprite(elephant)` AND `moveMetasprite(tiger)` — hiwater must not reset between calls |
| WR-01 | Distinct `posX`/`posY`/`idx`/`rot` var-ref parameterization   | Per-metasprite scoped vars via D-10 implicit scope-walk binders (`posX(elephantPosX)`, `posX(tigerPosX)`) |
| WR-02 | `game.h` extern declarations for metasprite frame tables      | 2 metasprites force 2 distinct `sprite_<id>_frames` externs in `game.h`, consumed across bank boundaries |

## Why throwaway (D-07)

This ROM is **allowed to be ugly**. There is no gameplay loop, no graphical polish, no
tuning. The only constraint is that the composition of DSL features must be sufficient
to exercise every code path that the JVM tests in Plans 05–09 + 11 lock in isolation.
Per D-19 no UAT screenshot is required — the ROM existing and `:buildRom` exiting 0 IS
the verdict.

## How to build

```bash
./gradlew :gbkt-examples:metasprites-stress:clean :gbkt-examples:metasprites-stress:buildRom
```

Expected: clean ROM at `gbkt-examples/metasprites-stress/build/gbkt/output/metasprites-stress.gb`.

Pre-fix expected failure modes (now closed by Plans 05–09 + 11; documented here for
historical context only):

- `duplicate symbol sprite_metasprite_0` → CR-03 unfixed
- `undefined reference move_metasprite_ex` in `bank1.c` → CR-02 unfixed
- `MBC5 unknown address/value` runtime errors → CR-01 unfixed (VRAM collision)
- Silent visible-only bug (sprites flicker / disappear) → WR-05 unfixed (hiwater)

## Provenance / traceability

- Phase: `10.1-metasprites-surplus-codegen-defects-inserted`
- Plan: 12 (D-06 closure — synthetic stress ROM exists + binds)
- Decisions: D-06 (need a synthetic ROM), D-07 (allowed to be ugly), D-10 (implicit
  scope-walk binders), D-13 / D-13b (scoped binder DSL exception), D-19 (no UAT
  required — codegen binding evidence only), D-21 (ROM-build smoke gate)
- Sprite assets: `elephant.png` copied verbatim from `gbkt-examples/metasprites/`;
  `tiger.png` is an intentional duplicate of `elephant.png` (distinct ID, identical
  pixels — proves CR-03 namespacing without asset-pipeline complexity, per D-07);
  `player.png` is an 8×16 actor sprite copied from `gbkt-examples/explorer/`.
