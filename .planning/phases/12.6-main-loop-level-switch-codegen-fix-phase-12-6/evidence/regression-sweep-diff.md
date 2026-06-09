# D-14 7-target Regression Sweep — Pre-fix vs Post-fix SHA-256 Verdict

**Capture date:** 2026-05-26
**Plan:** 12.6-08 Task 2
**Pre-fix baseline:** `.planning/phases/12.6-main-loop-level-switch-codegen-fix-phase-12-6/evidence/pre-fix-rom-sha256.txt` (HEAD `28f2077f`, captured by Plan 12.6-01)
**Post-fix snapshot:** `.planning/phases/12.6-main-loop-level-switch-codegen-fix-phase-12-6/evidence/post-fix-rom-sha256.txt` (HEAD `8e13f6d4`)

## Verdict

| Target              | Pre-fix SHA-256       | Post-fix SHA-256      | Verdict | Notes |
|---------------------|-----------------------|-----------------------|---------|-------|
| pong                | `8cb4cec8…f25c61a8`   | `cf9d01d0…7c38017`    | PASS\*  | toolchain non-deterministic; codegen is byte-identical |
| breakout            | `21a42479…21796977`   | `21a42479…21796977`   | PASS    | |
| simple-physics      | `247e16d2…31384f9ad`  | `247e16d2…31384f9ad`  | PASS    | |
| metasprites         | `c4261099…ada3f5f7b`  | `c4261099…ada3f5f7b`  | PASS    | |
| metasprites-stress  | `a5b3657b…cd26764`    | `a5b3657b…cd26764`    | PASS    | |
| banks               | `c598231420…b58fd8f`  | `c598231420…b58fd8f`  | PASS    | |
| racer               | `48d3a71c…089b6e8`    | `48d3a71c…089b6e8`    | PASS    | |

**Total:** 7 / **Pass:** 7 / **Fail:** 0  
**Codegen drift:** 0 / 7 (the `gameUsesTilemapCollision` gate holds across all sibling targets)

## Pong toolchain investigation (in-line, 2026-05-26)

Initial sweep flagged pong as a byte-identity FAIL (pre-fix `8cb4cec8` vs
post-fix `cf9d01d0`). Per D-14 acceptance, the plan HALTED and we
investigated before resuming Task 3.

**Method:** checkout HEAD `28f2077f` (the pre-fix baseline commit), rebuild
pong from scratch, capture (a) the new ROM SHA-256 and (b) the generated
`main.c` / `bank1.c` / `game.h` byte-content. Compare to (c) the saved
post-fix ROM SHA-256 and (d) the saved post-fix generated C from HEAD
`8e13f6d4`.

**Finding 1 — generated C is byte-identical pre-fix vs post-fix:**

```bash
diff /tmp/pong-prefix-main.c  /tmp/pong-postfix-main.c   # empty
diff /tmp/pong-prefix-bank1.c /tmp/pong-postfix-bank1.c  # empty
diff /tmp/pong-prefix-game.h  /tmp/pong-postfix-game.h   # empty
```

Phase 12.6's codegen changes do **not** affect pong's emitted C. The
`gameUsesTilemapCollision` gate works correctly.

**Finding 2 — pong's ROM is non-deterministic across rebuilds:**

| Build site                                          | SHA-256              |
|-----------------------------------------------------|----------------------|
| Plan 12.6-01 baseline @ `28f2077f`                  | `8cb4cec8…f25c61a8`  |
| 12.6-08 sweep @ `8e13f6d4` (post-fix)               | `cf9d01d0…7c38017`   |
| 12.6-08 investigation rebuild @ `28f2077f`          | `fce9a4de…91658`     |
| 12.6-08 investigation post-restore @ `8e13f6d4`     | `4ae15ff8…ad6d`      |

Four builds, four different SHAs, even for the same commit. The other 6
games hash identically across the same four rebuilds. Pong has
toolchain-level non-determinism (likely a timestamp or randomized
allocation in sdcc/lcc that the other games don't trigger).

**Conclusion — verdict flipped FAIL → PASS\*:**

The byte-identity check fails for pong, but the **codegen byte-identity**
check passes (the actual D-14 intent). The pong drift is not introduced
by Phase 12.6; it would reproduce on any rebuild irrespective of commit.

The `*` annotation on pong's PASS verdict is preserved so future readers
can grep `PASS\*` and find this caveat. The bottom-line count rolls up to
`Pass: 7 / Fail: 0` because no actual 12.6 regression was introduced.

**Follow-up route (out of scope for Phase 12.6):**

Pong's toolchain non-determinism is a pre-existing condition surfaced by
this sweep. It should be tracked as a new investigation under
gbkt-gradle-plugin / GBDK toolchain reproducibility — likely a sibling
finding for Phase 12.10 (UAT test-harness work) or a separate phase
focused on build reproducibility. Routed: not now; documented here so
the next regression sweep doesn't re-litigate the same finding.
