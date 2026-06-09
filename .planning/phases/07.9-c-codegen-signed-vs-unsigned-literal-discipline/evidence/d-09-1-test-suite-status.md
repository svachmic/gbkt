# D-09 #1 Full Test Suite Status — Phase 07.9 Final Verification Gate

## Invocation

```
./gradlew test
```

- **HEAD SHA:** 9cc623fd96951a0fc6f2f22f45c7f81f1d7cf4c0
- **Date:** 2026-05-13
- **Exit code:** 1 (non-zero)
- **Total tests:** 158 tests completed, 2 failed

## Verdict

**D-09 #1 PARTIAL** — Full suite RED on exactly 2 pre-existing Phase 07.4-33 tests that were intentionally RED before Phase 07.9 began. All Phase 07.9 success-criteria tests are GREEN.

## Failing Tests

```
TrackSynthesizerCircuitShapeTest > racer_waypoints_synthesize_to_corridor_not_arena() FAILED
    org.opentest4j.AssertionFailedError at TrackSynthesizerCircuitShapeTest.kt:163

TrackSynthesizerCircuitShapeTest > racer_corridor_interior_is_non_drivable() FAILED
    org.opentest4j.AssertionFailedError at TrackSynthesizerCircuitShapeTest.kt:194
```

## Pre-existing Confirmation

These tests were added in commit **44fba585** (`test(07.4-33): JVM RED locking TrackSynthesizer corridor-shape contract for GAP-TRACK-NOT-RENDERED-AS-CIRCUIT`) which is **BEFORE** Phase 07.9 Plan 01 commit `117bb580` (2026-05-12).

```
git log --oneline -- gbkt-genre-sport/src/test/kotlin/.../TrackSynthesizerCircuitShapeTest.kt
44fba585 test(07.4-33): JVM RED locking TrackSynthesizer corridor-shape contract for GAP-TRACK-NOT-RENDERED-AS-CIRCUIT
```

The test file's header comment states:
> `// This test MUST FAIL against HEAD. Its job is to lock the per-tile contract the Plan 07.4-35 GREEN fix has to satisfy.`

These failures pre-date Phase 07.9 entirely and were already RED at the parent commit (`117bb5809a95`) before any Phase 07.9 work started.

## Phase 07.9 Success-Criteria Tests — All GREEN

| Test Class | Tests | Status |
|-----------|-------|--------|
| SignedComparisonLiteralEmissionTest | 8/8 | GREEN |
| CLiteralAuditScanTest | 3/3 | GREEN |
| Round8CameraMonotonicityProbe | 1/1 | GREEN (Plan 07.4-32 RED gate CLOSED) |
| PlatformerJumpCancelAndFrictionProbe | 2/2 | GREEN |
| CameraBoundsClampPrecedenceTest | 1/1 | GREEN (Plan 07.4-34 non-regression) |

## Exception Request — For Human Checkpoint Approval

Per Plan 06 Task 1 step 3.b: the 2 RED tests are unrelated to Phase 07.9. Their presence does not constitute a Phase 07.9 regression. The STATE.md `Next step` already routes to `/gsd-execute-phase 07.4 --gaps-only` which resumes Plans 35 (TrackSynthesizer GREEN) and 36 (round-8 visual UAT) — the specific plans designed to turn these tests GREEN.

**Exception rationale:** Accepting this non-zero exit code as equivalent to D-09 #1 SATISFIED because:
1. The 2 failures predate Phase 07.9 (commit `44fba585`, Phase 07.4-33 work).
2. The failures are intentionally RED by design (forcing function for Phase 07.4-35 GREEN fix).
3. All 07.9 success-criteria tests (SignedComparisonLiteralEmissionTest 8/8, CLiteralAuditScanTest 3/3, Round8CameraMonotonicityProbe, PlatformerJumpCancelAndFrictionProbe 2/2) are GREEN.
4. The resume plan (`/gsd-execute-phase 07.4 --gaps-only`) accounts for these failures — they are the next work item.

**Human checkpoint (Task 4) must explicitly approve this exception before Plan 06 closes.**
