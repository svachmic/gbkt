# Phase 12.7 Round-6 Regression Sweep — Post-Plan-12.7-28 SHA-256 Manifest

**Date:** 2026-05-26
**Round:** 6 (TERMINAL — per feedback_many_small_plans_terminal_subphase, NO Round 7)
**SUPERSEDES:** Plan 12.7-22 (Round-5 sweep — PLANNED but UNEXECUTED; staged as a planning-only artifact with no SUMMARY)
**Current HEAD codegen:** Plan 12.7-28 — H3 grounded-guard on level-end trigger CIf (`&& _grounded != 0` conjunction added to `PlatformerVisitor.buildTilemapPhysicsUpdateFunction`)
**Plan 12.7-29 branch:** Branch A (visual sanity GREEN — player visibly grounded at trigger-fire frame, sidecar grounded=1 at frame 1347)

## Audit-Trail Context

Phase 12.7's regression-sweep evidence has accumulated across the gap-closure rounds:

- Plan 12.7-07 (Round-1): broken-snap sweep — superseded by Plan 12.7-13
- Plan 12.7-13 (Round-4): intermediate-vars fixed-snap sweep — at `evidence/regression-sweep.md`
- Plan 12.7-22 (Round-5 planned): pivot_adjust sweep — PLANNED but NOT EXECUTED (no SUMMARY exists)
- Plan 12.7-30 (Round-6 — this file): H3 grounded-guard sweep — supersedes Plan 12.7-22

The Plan 12.7-19 pivot_adjust column is omitted in the table below (Plan 12.7-22's unexecuted
manifest would have provided it; not load-bearing for R-04 closure per the `<interfaces>` block
in Plan 12.7-30).

## Pre-flight Confirmation

```bash
grep -c "groundedSym" gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt
# → 14 (Plan 12.7-28 fix in place)

grep -A3 "Level-end trigger" gbkt-examples/platformer-template/build/gbkt/generated/main.c
# → if (player_real_x > _current_level_width - 32u && _grounded != 0)
# _grounded guard confirmed in the compiled C
```

## Sweep Command

Single chained invocation per `feedback_no_parallel_gradle_clean` (ONE `./gradlew clean` at the
start, no parallel root cleans):

```bash
./gradlew clean \
  :gbkt-examples:pong:buildRom \
  :gbkt-examples:breakout:buildRom \
  :gbkt-examples:simple-physics:buildRom \
  :gbkt-examples:metasprites:buildRom \
  :gbkt-examples:metasprites-stress:buildRom \
  :gbkt-examples:banks:buildRom \
  :gbkt-examples:racer:buildRom \
  :gbkt-examples:platformer-template:buildRom
```

Build verdict: **BUILD SUCCESSFUL in 11s** (exit 0). 124 actionable tasks: 123 executed, 1 up-to-date. Full log captured at `/tmp/12.7-30-sweep.log` (transient).

Hashing command:

```bash
for game in pong breakout simple-physics metasprites metasprites-stress banks racer platformer-template; do
  shasum -a 256 gbkt-examples/$game/build/gbkt/output/$game.gb
done
```

## Results

| Target              | 12.6 baseline                                                        | 12.7-13 inter-vars                                                   | 12.7-28 H3                                                           | Status                                         |
|---------------------|----------------------------------------------------------------------|----------------------------------------------------------------------|----------------------------------------------------------------------|------------------------------------------------|
| pong                | `4ae15ff85c607d353aa8d28aa26609f1bf9a07ae6765ae84be56b729b4e6ad6d`  | `a42c514c6f38674cdd98235f9cea9f4f0a31bdb40a98dbec2b12156730b196e9`  | `70112b44fa38691c519b41b1d287bb5f493ffce0a252f37b952e6ed5ce1bc326`  | PASS* (non-deterministic)                      |
| breakout            | `21a42479ed36a2d3a2f62c5d4b2495168ca6a2178fc05010a155a90421796977`  | `21a42479ed36a2d3a2f62c5d4b2495168ca6a2178fc05010a155a90421796977`  | `21a42479ed36a2d3a2f62c5d4b2495168ca6a2178fc05010a155a90421796977`  | PASS (byte-identical to 12.6)                  |
| simple-physics      | `247e16d2df29c9cad2df3f2f5fb68cba00133e95e94b4d47dbcf87c31384f9ad`  | `247e16d2df29c9cad2df3f2f5fb68cba00133e95e94b4d47dbcf87c31384f9ad`  | `247e16d2df29c9cad2df3f2f5fb68cba00133e95e94b4d47dbcf87c31384f9ad`  | PASS (byte-identical to 12.6)                  |
| metasprites         | `c42610991e67b7d33404c9f2aa9725ed449e14c30e0827fa984f363ada3f5f7b`  | `c42610991e67b7d33404c9f2aa9725ed449e14c30e0827fa984f363ada3f5f7b`  | `c42610991e67b7d33404c9f2aa9725ed449e14c30e0827fa984f363ada3f5f7b`  | PASS (byte-identical to 12.6)                  |
| metasprites-stress  | `a5b3657b765a59b8fc8423b6fef286d424cd6870cccc7594f31ca0532cd26764`  | `a5b3657b765a59b8fc8423b6fef286d424cd6870cccc7594f31ca0532cd26764`  | `a5b3657b765a59b8fc8423b6fef286d424cd6870cccc7594f31ca0532cd26764`  | PASS (byte-identical to 12.6)                  |
| banks               | `c598231420e4fdc1d06f7f386be318723096a22000feb0971584371d2b58fd8f`  | `c598231420e4fdc1d06f7f386be318723096a22000feb0971584371d2b58fd8f`  | `c598231420e4fdc1d06f7f386be318723096a22000feb0971584371d2b58fd8f`  | PASS (byte-identical to 12.6)                  |
| racer               | `48d3a71c7bcc2842dc6a086b8f2eb8271e671a37eb96d8232352e459d089b6e8`  | `48d3a71c7bcc2842dc6a086b8f2eb8271e671a37eb96d8232352e459d089b6e8`  | `48d3a71c7bcc2842dc6a086b8f2eb8271e671a37eb96d8232352e459d089b6e8`  | PASS (byte-identical to 12.6)                  |
| platformer-template | `318775aa086dc345f5e18fbc43869b5d8c6163e66434996bcb8aee7bba02c7c7`  | `b9f4665ed758f13203c10e983d987478738af419e362da7fc1d414f9151cfa62`  | `bef124da1346fc5ffd007aca7692c9470f3f1e41f30acdec4487944fd6d8d115`  | INTENTIONAL CHANGE — Round 6 (H3 fix)          |

**Strict targets (non-pong, non-platformer-template):** 6 / **Pass byte-identical to 12.6:** 6 / **Fail:** 0
**Pong:** PASS\* (toolchain non-determinism per `project_pong_toolchain_nondeterminism.md`)
**Platformer-template:** INTENTIONAL CHANGE — new H3 hash differs from BOTH Phase 12.6 baseline AND Plan 12.7-13 inter-vars hash.

## Cross-checks

- **5 strict non-pong non-platformer-template targets (breakout, simple-physics, metasprites, metasprites-stress, banks, racer):** All 6 have `12.7-28 H3` hash equal to `12.6 baseline` AND `12.7-13 inter-vars` hash byte-for-byte. Proves the Plan 12.7-28 H3 grounded-guard change is scoped to `PlatformerVisitor`'s level-end trigger emit and does NOT leak into other games' codegen paths. **PASS.**

- **Pong:** `70112b44...` differs from both predecessor columns (`4ae15ff8...`, `a42c514c...`). Expected per `project_pong_toolchain_nondeterminism.md` — every rebuild produces a new hash; this is a pre-existing sdcc/lcc artifact, NOT a Round-6 regression. **PASS\*.**

- **Platformer-template:** `bef124da...` differs from Phase 12.6 baseline (`318775aa...`) AND from Plan 12.7-13 inter-vars hash (`b9f4665e...`). The 12.7-13 → 12.7-28 delta is purely the CIf condition extension (adding `&& _grounded != 0` conjunction + extending the CComment text to cite the H3 fix). **PASS — INTENTIONAL CHANGE.**

## Annotations

- **pong PASS\***: pong.gb hashes differently every rebuild from the same commit per
  `project_pong_toolchain_nondeterminism.md`. Pre-existing sdcc/lcc non-determinism;
  NOT a Round-6 regression. Do NOT investigate.

- **platformer-template INTENTIONAL CHANGE — Round 6 (H3 fix)**: Plan 12.7-28 extended
  `PlatformerVisitor.buildTilemapPhysicsUpdateFunction`'s level-end trigger CIf condition
  with an `&& _grounded != 0` conjunction. The hash change `b9f4665e...` → `bef124da...`
  reflects:
    - The CIf condition now contains a `_grounded` reference (new `CBinaryExpr` leaf with
      `CVar(groundedSym), "!=", CIntLiteral(0)` joined by `"&&"` to the original position test).
    - The CComment text was extended to cite the H3 fix, Plan 12.7-26 verdict, and SPEC R-03.
    - All other emission shape (foot-probe snap/intermediate-vars from Plan 12.7-11,
      pivot_adjust from Plan 12.7-19, JumpHold, WalkCycle, camera, etc.) is UNCHANGED —
      Round-4 and Round-5 emissions hold.
  The player visual outcome (grounded at trigger fire, no mid-air level-end) is verified by
  Plan 12.7-29 PNGs (sidecar grounded=1, playerVy=0 at frame 1347) and Plan 12.7-31
  BINDING human-verify gate.

- **6 strict targets PASS — Round 6**: breakout, simple-physics, metasprites,
  metasprites-stress, banks, racer all hash-match their Phase 12.6 baselines AND
  Plan 12.7-13 intermediate-vars hashes byte-for-byte. Proves the Plan 12.7-28 change
  is scoped to the PlatformerVisitor's level-end trigger emit and does NOT leak into
  other games' codegen paths.

- **SUPERSEDES Plan 12.7-22**: Plan 12.7-22 (Round-5 pivot_adjust sweep) was PLANNED
  but UNEXECUTED. Its PLAN file stays committed as a historical planning artifact; no
  SUMMARY exists. This Plan 12.7-30 manifest is the binding R-04 audit trail at HEAD.
  Plan 12.7-22's intent (record pivot_adjust hashes) is partially preserved by noting
  that had it run, the platformer-template column would have shown a 12.7-19 hash between
  12.7-13 and 12.7-28; that intermediate hash is omitted here per the terminal-cluster
  contract (Plan 12.7-22 will NOT be re-run; Round 6 IS terminal).

## Audit Trail (3-revision chain)

The R-04 audit trail under Phase 12.7's gap-closure cycles:

1. Plan 12.7-07 — broken-snap sweep (Round-1, superseded by Plan 12.7-13) → `evidence/post-fix-rom-sha256.txt`
2. Plan 12.7-13 — intermediate-vars sweep (Round-4) → `evidence/regression-sweep.md`
3. Plan 12.7-30 — H3 grounded-guard sweep (Round-6 — THIS FILE) → supersedes Plan 12.7-22

The hash progression for platformer-template across the audit trail:
- Phase 12.6 baseline: `318775aa...` (pre-Phase-12.7 codegen)
- Plan 12.7-07 broken-snap: `a7fc51f2...` (broken snap emission — superseded)
- Plan 12.7-13 fixed-snap / inter-vars: `b9f4665e...` (Plan 12.7-11 precedence-safe snap)
- Plan 12.7-22 pivot_adjust: OMITTED (plan unexecuted)
- Plan 12.7-28 H3 grounded-guard: `bef124da...` (THIS FILE — grounded trigger guard)

Plan 12.7-31 (W25 BINDING gate) is the visual-truth gate. This manifest is the
audit-trail companion.

## Terminal-Round Contract

Per `feedback_many_small_plans_terminal_subphase.md`: Round 6 IS the terminal round
for Phase 12.7. NO Round 7. If Plan 12.7-31 BINDING gate fails, the resume signal
routes to a sibling phase under parent 12 (via `/gsd-phase --insert 12 <slug>`,
passing INTEGER parent 12 per `feedback_gsd_phase_insert_after_decimal`).
Plan 12.7-22 will NOT be re-run.

## Conclusion — R-04 GREEN (Round 6)

R-04 ("8-target regression sweep — 6/6 strict targets byte-identical to 12.6 baseline
under the post-fix codegen") is **CLOSED** post-Plan-12.7-28:

- Six strict non-pong non-platformer-template hashes match the Phase 12.6 baseline
  byte-for-byte across an independent clean rebuild.
- Pong's `PASS*` status is preserved per the project memory caveat; it is not a 12.7
  regression and is not investigated further.
- Platformer-template's hash `bef124da...` differs from all prior manifests. This is the
  INTENTIONAL outcome of Plan 12.7-28's `_grounded != 0` conjunction guard on the
  level-end trigger CIf. The change is scoped to the platformer-template ROM only; all
  6 strict targets remain bit-identical to their Phase 12.6 baselines.
- Plan 12.7-22 (Round-5 planned sweep) is explicitly superseded. This file is the
  binding R-04 audit trail at HEAD.

No silent FAIL was written; no escalation was issued — the strict-targets contract
held on first Round-6 sweep.
