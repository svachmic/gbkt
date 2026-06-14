# Phase 21 — D-13 Byte-Identity Oracle

**Date:** 2026-06-14  
**Oracle type:** Two-tier (byte-identity guard for untouched set; targeted emission/UAT proof for changed set)  
**Baseline commit (BEFORE):** `df9ead5c` (docs(phase-20): mark phase complete — end of Phase 20)  
**Head commit (AFTER):** `e072c9da` (docs(21-07): complete platformer GBC anchor re-shoot + seed archival)

---

## Tier 1: Untouched-Set Byte-Identity Guard

These 5 examples plus pong were NOT modified by Phase 21 changes. The D-13 oracle requires byte-identical generated C (not ROM) before and after Phase 21.

### Method

```bash
# BEFORE baseline: temporary git worktree at df9ead5c
git worktree add /tmp/gbkt-pre-phase21 df9ead5c
cd /tmp/gbkt-pre-phase21 && ./gradlew \
  :gbkt-examples:breakout:generateC \
  :gbkt-examples:simple-physics:generateC \
  :gbkt-examples:metasprites:generateC \
  :gbkt-examples:metasprites-stress:generateC \
  :gbkt-examples:banks:generateC \
  :gbkt-examples:pong:generateC

# AFTER baseline: main working tree at HEAD
./gradlew \
  :gbkt-examples:breakout:generateC \
  :gbkt-examples:simple-physics:generateC \
  :gbkt-examples:metasprites:generateC \
  :gbkt-examples:metasprites-stress:generateC \
  :gbkt-examples:banks:generateC \
  :gbkt-examples:pong:generateC

# Hash comparison: find .../generated -name "*.c" -o -name "*.h" | sort | xargs shasum -a 256
```

### Results: BYTE-IDENTICAL for all 5 untouched examples

| Example | File | BEFORE SHA-256 | AFTER SHA-256 | Result |
|---------|------|----------------|---------------|--------|
| breakout | bank1.c | `8d9a95dd...` | `8d9a95dd...` | IDENTICAL |
| breakout | game.h  | `eaff8e77...` | `eaff8e77...` | IDENTICAL |
| breakout | main.c  | `30d7c7d2...` | `30d7c7d2...` | IDENTICAL |
| simple-physics | game.h | `98f067b6...` | `98f067b6...` | IDENTICAL |
| simple-physics | main.c | `366382be...` | `366382be...` | IDENTICAL |
| metasprites | game.h | `7a399242...` | `7a399242...` | IDENTICAL |
| metasprites | main.c | `51023232...` | `51023232...` | IDENTICAL |
| metasprites-stress | bank1.c | `2a3f299c...` | `2a3f299c...` | IDENTICAL |
| metasprites-stress | game.h  | `626a7d6d...` | `626a7d6d...` | IDENTICAL |
| metasprites-stress | main.c  | `8d293995...` | `8d293995...` | IDENTICAL |
| banks | bank1.c    | `89c46ad9...` | `89c46ad9...` | IDENTICAL |
| banks | game.h     | `831a075e...` | `831a075e...` | IDENTICAL |
| banks | main.c     | `df3307c4...` | `df3307c4...` | IDENTICAL |
| banks | zone_bank2.c | `54d7ff9e...` | `54d7ff9e...` | IDENTICAL |

### pong PASS* (generated C compared; ROM hash excluded)

pong.gb ROM hashes differently every rebuild due to GBDK/lcc/sdcc non-determinism (known toolchain issue, confirmed in project memory `project_pong_toolchain_nondeterminism.md`). The generated C IS deterministic.

| Example | File | BEFORE SHA-256 | AFTER SHA-256 | Result |
|---------|------|----------------|---------------|--------|
| pong | bank1.c | `3eaea514...` | `3eaea514...` | IDENTICAL |
| pong | game.h  | `1a194610...` | `1a194610...` | IDENTICAL |
| pong | main.c  | `b5e81de7...` | `b5e81de7...` | IDENTICAL |

**pong verdict: PASS*** (generated C byte-identical; ROM hash comparison intentionally excluded)

### Full SHA-256 hashes (AFTER HEAD)

```
8d9a95ddcef3fa41d971ff07202fee23765aed1f524c072eb1bfc137675abef4  breakout/bank1.c
eaff8e779013456bea821df8609bb734d9de8a54c9c7b6de6abfd4d31cf9fb69  breakout/game.h
30d7c7d2ec6eb2b829f626735dc9ce7657c8312087b7074d802fcd1ea1ec5897  breakout/main.c
98f067b615ff242acc7f346e0c43ed1efbfeb2a3368c20fd898b1480ae871e68  simple-physics/game.h
366382bef85e32fb7070a31e256924b0df731a3e6557b9f352718cb3d69768c5  simple-physics/main.c
7a39924262566985e3662717768f944fe9183d95370c302263f584452d3d95e3  metasprites/game.h
510232b01bd412fc14a62748483f7bc7296db133e0caf278fd9bf2829e463836  metasprites/main.c
2a3f299ced1ea70ee20b0f82e22e9e6781a565cb66055075fd7b36fd516f06a2  metasprites-stress/bank1.c
626a7d6d3bffc9ad3a596e3728dc041beedd3aaecb497a23f4ed0d54676f7955  metasprites-stress/game.h
8d29399518eb0efde1ef578a9264f40d05ecb45f861250c0ff91914ce2a0b769  metasprites-stress/main.c
89c46ad9fc6a810d1dc6a4364d09dc3e96323189508537be5edb485abc42d6d0  banks/bank1.c
831a075e90563a368a1824cd3e6fcc6a7d211b81f091fb9557f8432b969f39b3  banks/game.h
df3307c483c543f87b8ad16bac08df334aafeae9ed61758b1162e7575ecd050c  banks/main.c
54d7ff9e66dc331e184218e9353d21fa4437a8fb10dee32aa70a58453103905d  banks/zone_bank2.c
3eaea514ca9c0f411d510f48a2010fecc310a26ba9bbe9d9324a59fe6ae5f059  pong/bank1.c
1a194610d8311e041a5a9063d2915a6dd6019332af683f09c2ae7de2b67c89b7  pong/game.h
b5e81de7c67ecacb99a276cfe50ce0313f2a11c2a83dde0adf09bed9479eada1  pong/main.c
```

### Analytical proof: why the 5 untouched examples are byte-identical

Phase 21 code changes are gated behind `gameUsesTilemapCollision()`, which returns `false` for all 5 untouched examples. The changed source files and their impact:

| Changed File | Impact on Untouched Examples |
|-------------|------------------------------|
| `GBDKPipeline.kt` | Refactored `gameUsesTilemapCollision()` to delegate Path C to `TilemapCollisionGate.kt`. Logic-equivalent — same result, different structure. Untouched examples all return `false` → no code path change. |
| `PlatformerVisitor.kt` | Reads `pivotAdjust` from DSL config instead of metasprite lookup. Only invoked for platformer games. Untouched examples do not use `tilemapCollision {}`. |
| `PlatformerExtensions.kt` | Adds `pivotAdjust(Int)` setter to `TilemapCollisionBuilder`. DSL change only; untouched examples do not use `tilemapCollision {}`. |
| `gbkt-backend-api/TilemapCollisionGate.kt` | New shared utility for Path C detection. Called only by `GBDKPipeline.gameUsesTilemapCollision()` and `PlatformerVisitor.gameUsesTilemapCollision()`. Returns `false` for untouched examples. |
| `gbkt-ir/GameIRSerializer.kt` | Serializer stubs. Not called during `generateC` (external tooling only). |
| `gbkt-lang/*.kt`, `gbkt-ir/Expr.kt`, `gbkt-ir/ScriptOp.kt`, `gbkt-core/References.kt`, `gbkt-genre-rpg/CombatStates.kt`, `gbkt-genre-sport/SportVisitor.kt`, `README.md` | Doc/KDoc-only changes (`whenever` → `runIf` in comments). Zero impact on compiled output. |

---

## Tier 2: Changed-Set Attribution (platformer-template)

`gbkt-examples/platformer-template` is the **changed example** in Phase 21. It is NOT subject to byte-identity comparison (it is intentionally changed).

**Proof path for platformer-template** (as mandated by D-13):

| Evidence Type | Source | Result |
|--------------|--------|--------|
| JVM emission test (D-05 pivotAdjust) | `PlatformerSnapArithmeticEmissionTest` — 21-01 | GREEN |
| JVM emission test (D-07 snap arithmetic) | `PlatformerSnapArithmeticEmissionTest.verifySnapArithmetic` — 21-01 | GREEN |
| JVM lockstep test (D-09 predicate) | `TilemapCollisionPredicateLockstepTest` — 21-02 | GREEN |
| GBC anchor re-shoot (D-14) | `PlatformerTemplateUatTest` all 5 anchors — 21-07 | PASSED + user visual sign-off |
| Seed archival (D-06/D-07/platformer seeds) | 4 platformer seeds archived — 21-07 | COMPLETE |

The changed-set is proven correct by the emission-test + UAT-anchor combination from plans 21-01, 21-02, and 21-07. Byte-identity is intentionally not required for the changed set (the pivotAdjust config path produces the same numeric result as the pre-Phase-21 fallback for existing tests, but the `pivotAdjust(2)` call in `PlatformerTemplate.kt` makes the value explicit in the generated IR).

---

## Verdict

| Set | Criterion | Result |
|-----|-----------|--------|
| breakout | Byte-identical generated C | PASS |
| simple-physics | Byte-identical generated C | PASS |
| metasprites | Byte-identical generated C | PASS |
| metasprites-stress | Byte-identical generated C | PASS |
| banks | Byte-identical generated C | PASS |
| pong | Generated C byte-identical; ROM hash excluded | PASS* |
| platformer-template | Emission tests + UAT anchors (changed set) | PASS (not byte-identity) |

**D-13 oracle: CLEAN** — No collateral drift in the untouched set. pong PASS*. Changed set proven by targeted evidence.
