# Phase 12.7 Regression Sweep — Post-Plan-12.7-11 SHA-256 Manifest

**Date:** 2026-05-26
**Plan:** 12.7-13 (R-04 closure — W7 re-record after Plan 12.7-11 snap-emission fix)
**HEAD at capture:** `439644f7b732a4cb3d48322daf7780ccab982f2f` (worktree base for Plan 12.7-13; post-Plan-12.7-11 snap-emission fix is merged)
**Baseline #1:** Phase 12.6 post-fix manifest
(`.planning/phases/12.6-main-loop-level-switch-codegen-fix-phase-12-6/evidence/post-fix-rom-sha256.txt`)
**Baseline #2:** Plan 12.7-07 broken-snap manifest
(`.planning/phases/12.7-player-levitating-physics-codegen/evidence/post-fix-rom-sha256.txt`, captured under Plan 12.7-07 before the Plan 12.7-11 fix)
**Current:** Plan 12.7-11 fixed-snap (this manifest, captured 2026-05-26 in Plan 12.7-13)

This file supersedes Plan 12.7-07's `regression-sweep.md`. Plan 12.7-07's
`post-fix-rom-sha256.txt` stays committed as the audit-trail predecessor.

## Pre-flight

`grep -c "foot_pixel_anchor" gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt` = **3** — Plan 12.7-11's precedence-immune snap emission (3 `CVarDecl`s + 1 `CExprStatement` introducing `foot_tile_row` → `foot_pixel_top` → `foot_pixel_anchor`) is present in tree.

## Sweep Command

Single chained invocation per `feedback_no_parallel_gradle_clean` (ONE `./gradlew clean` at the start, no parallel root cleans):

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

Build verdict: **BUILD SUCCESSFUL in 12s** (exit 0). 124 actionable tasks: 123 executed, 1 up-to-date. Full log captured at `/tmp/12.7-13-sweep.log` (transient).

Hashing command:

```bash
for game in pong breakout simple-physics metasprites metasprites-stress banks racer platformer-template; do
  shasum -a 256 gbkt-examples/$game/build/gbkt/output/$game.gb
done
```

## Results

| Target              | 12.6 baseline                                                      | 12.7-07 broken-snap                                                | 12.7-11 fixed-snap                                                 | Status                             |
|---------------------|--------------------------------------------------------------------|--------------------------------------------------------------------|--------------------------------------------------------------------|------------------------------------|
| pong                | `4ae15ff85c607d353aa8d28aa26609f1bf9a07ae6765ae84be56b729b4e6ad6d` | `36c7a52e59452114f989a377d0da90e2ce4e85a67a7eef1d32d1a8ede8818d9c` | `a42c514c6f38674cdd98235f9cea9f4f0a31bdb40a98dbec2b12156730b196e9` | PASS* (non-deterministic)          |
| breakout            | `21a42479ed36a2d3a2f62c5d4b2495168ca6a2178fc05010a155a90421796977` | `21a42479ed36a2d3a2f62c5d4b2495168ca6a2178fc05010a155a90421796977` | `21a42479ed36a2d3a2f62c5d4b2495168ca6a2178fc05010a155a90421796977` | PASS (byte-identical to 12.6)      |
| simple-physics      | `247e16d2df29c9cad2df3f2f5fb68cba00133e95e94b4d47dbcf87c31384f9ad` | `247e16d2df29c9cad2df3f2f5fb68cba00133e95e94b4d47dbcf87c31384f9ad` | `247e16d2df29c9cad2df3f2f5fb68cba00133e95e94b4d47dbcf87c31384f9ad` | PASS (byte-identical to 12.6)      |
| metasprites         | `c42610991e67b7d33404c9f2aa9725ed449e14c30e0827fa984f363ada3f5f7b` | `c42610991e67b7d33404c9f2aa9725ed449e14c30e0827fa984f363ada3f5f7b` | `c42610991e67b7d33404c9f2aa9725ed449e14c30e0827fa984f363ada3f5f7b` | PASS (byte-identical to 12.6)      |
| metasprites-stress  | `a5b3657b765a59b8fc8423b6fef286d424cd6870cccc7594f31ca0532cd26764` | `a5b3657b765a59b8fc8423b6fef286d424cd6870cccc7594f31ca0532cd26764` | `a5b3657b765a59b8fc8423b6fef286d424cd6870cccc7594f31ca0532cd26764` | PASS (byte-identical to 12.6)      |
| banks               | `c598231420e4fdc1d06f7f386be318723096a22000feb0971584371d2b58fd8f` | `c598231420e4fdc1d06f7f386be318723096a22000feb0971584371d2b58fd8f` | `c598231420e4fdc1d06f7f386be318723096a22000feb0971584371d2b58fd8f` | PASS (byte-identical to 12.6)      |
| racer               | `48d3a71c7bcc2842dc6a086b8f2eb8271e671a37eb96d8232352e459d089b6e8` | `48d3a71c7bcc2842dc6a086b8f2eb8271e671a37eb96d8232352e459d089b6e8` | `48d3a71c7bcc2842dc6a086b8f2eb8271e671a37eb96d8232352e459d089b6e8` | PASS (byte-identical to 12.6)      |
| platformer-template | `318775aa086dc345f5e18fbc43869b5d8c6163e66434996bcb8aee7bba02c7c7` | `a7fc51f277a992402f1b9a87324bda7f39920a32845cac692cec917246243317` | `b9f4665ed758f13203c10e983d987478738af419e362da7fc1d414f9151cfa62` | INTENTIONAL CHANGE (Plan 12.7-11)  |

**Strict targets (non-pong, non-platformer-template):** 6 / **Pass byte-identical to 12.6:** 6 / **Fail:** 0
**Pong:** PASS\* (toolchain non-determinism per `project_pong_toolchain_nondeterminism.md`; three distinct hashes across the three build sites is the expected pattern, not a regression)
**Platformer-template:** INTENTIONAL CHANGE — fixed-snap hash matches Plan 12.7-14 ROM smoke evidence exactly.

## Annotations

- **pong PASS\***: pong.gb hashes differently every rebuild from the same commit per
  `project_pong_toolchain_nondeterminism.md`. Phase 12.6's investigation captured four
  pong hashes across two commits, all distinct, while the other six targets remained
  bit-identical across the same four rebuilds. The toolchain non-determinism is a
  pre-existing sdcc/lcc artifact (likely a timestamp or randomized allocation triggered
  only by pong's emission shape) and is NOT a Phase 12.7 regression. The three pong
  hashes in this table (`4ae15ff8…`, `36c7a52e…`, `a42c514c…`) are three independent
  builds across three sessions — the drift is the expected pattern. Do NOT investigate.

- **platformer-template INTENTIONAL CHANGE**: Plan 12.7-11 rewrote
  `PlatformerVisitor.buildVerticalFootProbe`'s snap emission to use intermediate
  `CVarDecl` locals (`foot_tile_row` → `foot_pixel_top` → `foot_pixel_anchor` → assigned
  back into `_player_y`). This is the C-operator-precedence fix for the snap bug that
  escaped Plan 12.7-04's substring-only emission test (precedence collapsed
  `(tile_row * 16) << 4` into a wrong-order arithmetic chain at the bare-expression
  emission shape). The hash change `a7fc51f2…` → `b9f4665e…` reflects the new emission
  shape — three additional `CVarDecl` statements plus a precedence-safe assignment. The
  player-visual outcome (zero pixel gap, no levitation, no submersion) is verified by
  Plan 12.7-12's PNG re-capture and Plan 12.7-15 (human-verify). Plan 12.7-14's ROM
  smoke separately confirmed the same `b9f4665e…` hash and BUILD SUCCESSFUL through
  GBDK lcc — the post-12.7-11 codegen is link-clean.

- **6 strict targets PASS**: `breakout`, `simple-physics`, `metasprites`,
  `metasprites-stress`, `banks`, `racer` all hash-match their Phase 12.6 baselines
  byte-for-byte. This proves the Plan 12.7-11 PlatformerVisitor change is scoped to
  platformer codegen and does NOT leak into unrelated genres (pong/breakout
  collision-free actors, simple-physics, metasprites, banks, racer/sport).

## Cross-checks

- **Plan 12.7-14 cross-check (platformer-template):** This sweep's
  `platformer-template.gb` SHA-256 `b9f4665ed758f13203c10e983d987478738af419e362da7fc1d414f9151cfa62`
  matches Plan 12.7-14's authoritative post-Plan-12.7-11 hash exactly (verbatim
  match in `evidence/rom-smoke.txt`). Session-state divergence: **NONE**.
- **12.6 baseline vs 12.7-11 fixed-snap (strict 6 targets):** All six strict
  targets' fixed-snap column hashes equal their 12.6 baseline column hashes
  byte-for-byte. No drift into unrelated systems.
- **12.7-07 broken-snap vs 12.7-11 fixed-snap (platformer-template only):**
  `a7fc51f2…` → `b9f4665e…` — confirmed differs (intended). The intermediate
  Plan 12.7-07 column was captured BEFORE the snap-emission fix landed.
- **12.6 baseline vs 12.7-11 fixed-snap (platformer-template only):**
  `318775aa…` → `b9f4665e…` — confirmed differs (intended; the entire Phase 12.7
  reason-for-being is to change platformer-template's player-physics codegen).

## Audit Trail

Plan 12.7-07's sweep recorded the 7 non-platformer-template targets PASS (6 strict +
pong PASS\*) and platformer-template at the broken-snap hash `a7fc51f2…`. The seven
non-platformer-template hashes in the 12.7-07 column equal the 12.6-baseline column
exactly (pong PASS\* aside, since pong's expected pattern is per-build drift). This
manifest's third column is the post-Plan-12.7-11 capture.

Plan 12.7-14 (ROM smoke gate) and Plan 12.7-12 (UAT re-capture) shipped earlier in
Wave 7; both produced identical platformer-template ROMs to this manifest's hash
(`b9f4665e…`). Plan 12.7-15 (human-verify) is the binding visual gate. This manifest
is the audit-trail companion proving the Plan 12.7-11 codegen change is scoped and
reproducible.

Plan 12.7-07's evidence file `evidence/post-fix-rom-sha256.txt` stays committed in
the git history as the audit-trail predecessor; this file (`evidence/regression-sweep.md`)
overwrites Plan 12.7-07's earlier version (the prior content is recoverable via
`git show` against the Plan 12.7-07 commit).

## Conclusion — R-04 GREEN

R-04 ("8-target regression sweep — 6/6 strict targets byte-identical to 12.6 baseline
under the post-fix codegen") is **CLOSED** post-Plan-12.7-11:

- Six strict non-pong non-platformer-template hashes match the Phase 12.6 baseline
  byte-for-byte across an independent clean rebuild.
- Pong's `PASS*` status is preserved per the project memory caveat; it is not a 12.7
  regression and is not investigated further by this plan.
- Platformer-template's hash drift `a7fc51f2…` → `b9f4665e…` is the INTENTIONAL
  outcome of Plan 12.7-11's precedence-immune snap emission and matches Plan 12.7-14's
  independently-captured ROM smoke hash exactly. The W7 codegen change is the only
  source of drift across the entire example matrix.

No silent FAIL was written; no escalation was issued — the strict-targets contract
held on first sweep.

## Forward references

- Plan 12.7-15 — human-verify visual gate (binding closure for Phase 12.7)
- Plan 12.7-16 — phase ledger; cites this manifest + Plan 12.7-14 rom-smoke.txt
  + Plan 12.7-12 UAT PNGs as the R-04 / R-06 / Plan-12.7-08 evidence chain
