# Refactor Diff — Phase 09.3

## Summary

The post-09.3 generated C is **shape-preserving** against the pre-09.3 baseline with **one intentional behavior delta** at the JUMP-immediate site (D-01 oracle correction). All other generated C lines — including the seven named-constant call sites that now flow through `internal const val` declarations in `SimplePhysics.kt` — produce byte-identical C immediates. This artifact closes D-13's "verifier-facing audit trail" requirement and pairs with `oracle-comparison-v2.md` (which proves the oracle alignment of the constants block itself).

## Pre/Post Sources

- **Pre-09.3 baseline:** `.planning/phases/09.3-polish-simple-physics-port-and-reposition-as-canonical-showc/evidence/pre-09.3-main.c` (5450 bytes, 255 lines; captured by Plan 01 Task 1 before the JUMP/constants refactor).
- **Post-09.3 snapshot:** `.planning/phases/09.3-polish-simple-physics-port-and-reposition-as-canonical-showc/evidence/post-09.3-main.c` (5449 bytes, 254 lines; captured by Plan 04 Task 1 after `:gbkt-examples:simple-physics:generateC` GREEN). Net 1-byte / 1-line shrink is the literal-width change `-512` (4 chars) → `-32` (3 chars).

## Unified Diff

```diff
--- pre-09.3-main.c	2026-05-18 13:00:08.445791017 +0200
+++ post-09.3-main.c	2026-05-18 13:00:40.544922151 +0200
@@ -231,7 +231,7 @@
         }
     }
     if (button_pressed(J_A)) {
-        _spdY = -512;
+        _spdY = -32;
     }
     _posX += _spdX;
     _posY += _spdY;
```

That is the **entire** diff — a single hunk, one removed line, one added line, three context lines either side. Nothing else changed in the generated C between the pre-refactor and post-refactor builds.

## Behavior Deltas

| Site | Pre-09.3 | Post-09.3 | Decision Ref | Justification |
| --- | --- | --- | --- | --- |
| JUMP impulse (frame body, `_spdY = …` assignment inside the `button_pressed(J_A)` block at L234 of post-09.3-main.c) | `-512` | `-32` | **D-01** | Oracle-fidelity correction: `phys.c` L83 emits `SpdY = -JUMP_ACCELERATION_IN_SUBPIXELS` (= `-32`). The pre-refactor `-512` was a misreading of the integration loop (it conflated impulse with per-frame velocity accumulation). The Roadmap success criterion "Generated C remains identical OR compiles to same-byte-size ROM — this is a refactor, not a behavior change" is **explicitly deviated** by D-01; the user locked this deviation when accepting the phase context. See `09.3-04-SUMMARY.md` § Accepted Deviation from Roadmap Success Criterion. |

**No UNEXPECTED rows.** The diff contains exactly one behavior delta and it is the one D-01 sanctions. The seven new `internal const val` declarations at the top of `SimplePhysics.kt` did not change any other emitted C immediates — the named constants substitute the same literal values the codegen previously inlined directly (e.g., `MAX_Y_SPEED_IN_SUBPIXELS = 64` still produces `_spdY = -64;` at L212, `_spdY = 64u;` at L218, etc.). This is what "shape-preserving refactor" looks like at the codegen tier.

## Verifier Note

This is an **ACCEPTED deviation**, NOT a regression. The JUMP value change from `-512` to `-32` is the central deliverable of Phase 09.3's oracle-correction lane (D-01) and supersedes the Phase 09 Roadmap success criterion's "refactor not behavior change" framing. The verifier should:

1. Confirm the diff matches the single-hunk shape above (exactly one `-` line, exactly one `+` line, identical context).
2. Read `09.3-04-SUMMARY.md` § Accepted Deviation from Roadmap Success Criterion to confirm the deviation is recorded and the D-01 citation is intact.
3. Cross-check the JVM tier: `:gbkt-examples:simple-physics:test --tests "*SimplePhysicsEmissionTest*"` should be GREEN against `_spdY = -$JUMP_ACCELERATION_IN_SUBPIXELS` (re-baselined by Plan 02).
4. Report the deviation as `accepted-deviation`, NOT as `blocker` or `unexpected-change`.

Reference: `09.3-CONTEXT.md` § D-01 (the deviation registration); `oracle-comparison-v2.md` (the constants-mirror evidence that complements this artifact); `09.3-04-SUMMARY.md` § Accepted Deviation from Roadmap Success Criterion (the verifier-facing prose record, enforced by automated grep gate).

---

*D-IDs implemented by this artifact: D-01 (JUMP oracle correction documented), D-13 (verifier-facing evidence artifact published).*
