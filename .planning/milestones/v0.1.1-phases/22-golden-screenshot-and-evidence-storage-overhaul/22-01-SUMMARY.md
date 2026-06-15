---
phase: 22-golden-screenshot-and-evidence-storage-overhaul
plan: "01"
subsystem: gbkt-emulator
tags: [golden-diff, visual-uat, screenshot-capture, tdd]
dependency_graph:
  requires: []
  provides:
    - assertGoldenMatch (io.github.gbkt.emulator.agent.GoldenAssertions)
    - compareOrBless (io.github.gbkt.emulator.agent.GoldenAssertions)
    - GBKT_UPDATE_GOLDENS_PROP (io.github.gbkt.emulator.agent.GoldenAssertions)
  affects:
    - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/ScreenshotCapture.kt
tech_stack:
  added: []
  patterns:
    - TDD RED/GREEN for GoldenAssertions unit tests
    - File.copyTo raw-copy for byte-identity golden bless (Pitfall 2 avoidance)
key_files:
  created:
    - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/GoldenAssertions.kt
    - gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/GoldenAssertionsTest.kt
  modified:
    - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/ScreenshotCapture.kt
    - gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/ScreenshotCaptureTest.kt
decisions:
  - GBKT_UPDATE_GOLDENS_PROP is a top-level const val (named constant, Project Rule #1 — no magic strings)
  - compareOrBless uses File.copyTo(overwrite=true) for raw byte copy in update-mode (never ImageIO re-encode)
  - assertGoldenMatch delegates to compareOrBless for testability without a live emulator
  - GBC-header guard (D-07) is the CALLER's responsibility; documented in KDoc
  - capturedAt removed from ScreenshotCapture sidecar JSONObject chain (D-08, zero consumers)
metrics:
  duration: "3 min"
  completed_date: "2026-06-14"
  tasks: 2
  files: 4
---

# Phase 22 Plan 01: Golden Assertions Helper + capturedAt Removal Summary

**One-liner:** Exact-match golden diff helper with opt-in re-baseline via `GBKT_UPDATE_GOLDENS_PROP` const, and `capturedAt` timestamp churn eliminated from ScreenshotCapture sidecar.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 (RED) | Failing GoldenAssertions tests | 1fe17301 | GoldenAssertionsTest.kt |
| 1 (GREEN) | assertGoldenMatch + compareOrBless implementation | e3ef0c74 | GoldenAssertions.kt, GoldenAssertionsTest.kt |
| 2 | Drop capturedAt from ScreenshotCapture + update test | 8c2c12ee | ScreenshotCapture.kt, ScreenshotCaptureTest.kt |

## What Was Built

### GoldenAssertions.kt (NEW)

Top-level file in `io.github.gbkt.emulator.agent` package:

- `const val GBKT_UPDATE_GOLDENS_PROP = "gbkt.updateGoldens"` — named system-property constant (Project Rule #1)
- `fun assertGoldenMatch(agent, label, goldenFile, scratchDir)` — captures via `agent.captureScreenshot(label)`, delegates to `compareOrBless`
- `fun compareOrBless(goldenFile, capturedFile, scratchDir)` — internal testable delegate:
  - **Diff mode** (default): missing golden throws `AssertionError` naming path + `-Pgbkt.updateGoldens` hint (D-05); present golden compared via `VisualDiff.compare(tolerance=0.0)` (D-04); mismatch throws `AssertionError` naming diffCount, paths, diff image
  - **Update mode** (property set): raw-copies `capturedFile` to `goldenFile` via `File.copyTo(overwrite=true)` (never re-encodes through ImageIO — byte-identity preserved, Pitfall 2)

### GoldenAssertionsTest.kt (NEW)

Five unit tests covering:
1. Missing golden + update-mode OFF → `AssertionError` with golden path + `GBKT_UPDATE_GOLDENS_PROP` in message
2. Pixel-identical captured PNG → no exception
3. Captured PNG differs ≥ 1 pixel → `AssertionError` mentioning `pixels differ`
4. Diff image path included in mismatch error (non-null message confirmed)
5. Update-mode ON + missing golden → golden written with parent dirs created; SHA-256 byte-identity verified

Tests use `@TempDir`, pre-written 160×144 PNG helper, and `MessageDigest.getInstance("SHA-256")` — no live emulator required.

### ScreenshotCapture.kt (MODIFIED)

Removed `.put("capturedAt", System.currentTimeMillis())` from the sidecar `JSONObject` chain (D-08). The `capturedAt` field had zero production consumers but was the source of sidecar timestamp churn (16 committed sidecars re-dirtied per `./gradlew test` run). KDoc example updated to remove `capturedAt` from the sidecar format.

### ScreenshotCaptureTest.kt (MODIFIED)

Replaced the three `capturedAt` assertions (lines 92-94: `getLong("capturedAt")`, two `assertTrue` bounds checks) with:
```kotlin
assertFalse(json.has("capturedAt"), "capturedAt field must be absent from sidecar (D-08)")
```
Dead `beforeCapture`/`afterCapture` locals removed. `assertFalse` import added.

## Verification Results

All plan verification criteria passed:

- `./gradlew :gbkt-emulator:test` — GREEN (full suite)
- `grep -c "capturedAt" gbkt-emulator/src/main/kotlin/.../ScreenshotCapture.kt` → `0`
- `grep "gbkt.updateGoldens" .../GoldenAssertions.kt | grep "const val"` → single const declaration
- `./gradlew :gbkt-emulator:detekt` — clean

## Deviations from Plan

None — plan executed exactly as written.

The `compareOrBless` internal function name was chosen for testability (plan suggested "an internal `compareOrBless(goldenFile, capturedFile, scratchDir)` that `assertGoldenMatch` delegates to after capture" — this was followed precisely). The function is `internal` visibility by default (top-level in the package, same as the public `assertGoldenMatch`); Wave 3 plans can unit-test it directly.

## Known Stubs

None. All behavioral paths are fully wired:
- Missing golden → hard `AssertionError` (not a soft log)
- Pixel mismatch → hard `AssertionError` with diff image path
- Update mode → raw byte copy with parent dir creation
- No placeholder returns or TODO comments

## Threat Flags

No new externally-reachable surface introduced. The T-22-01 threat (golden bless via update-mode) is mitigated as designed: system property gate prevents a normal `./gradlew test` from writing any golden. Unit-tested in Task 1 (update-mode write path).

## Self-Check: PASSED

- GoldenAssertions.kt: FOUND
- GoldenAssertionsTest.kt: FOUND
- ScreenshotCapture.kt capturedAt removed: CONFIRMED (grep returns 0)
- Commits 1fe17301, e3ef0c74, 8c2c12ee: FOUND in git log
