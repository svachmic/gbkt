# Phase 22: Golden Screenshot and Evidence Storage Overhaul - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-14
**Phase:** 22-golden-screenshot-and-evidence-storage-overhaul
**Areas discussed:** Goldens dir + key scheme, Diff helper / reuse strategy, Re-baseline trigger shape, Sidecar disposition

---

## Goldens directory + key scheme

### Location

| Option | Description | Selected |
|--------|-------------|----------|
| Repo-root `goldens/` | Single top-level `goldens/<rom>/<anchor>.png` at repo root (seed proposal) | |
| `gbkt-test/goldens/` | Goldens owned by the test-infra module, co-located with diff/recipe code | |
| Per-module test resources | Each example keeps goldens under its own `src/test/resources/goldens` | ✓ |

**User's choice:** Per-module test resources (`src/test/resources/goldens/<rom>/<anchor>.png`).
**Notes:** Overrides the seed's "one tracked top-level location". Since each example module = one ROM, the `<rom>/` segment is organizational; planner may flatten if redundant.

### Anchor naming

| Option | Description | Selected |
|--------|-------------|----------|
| Descriptive, phase-agnostic | e.g. `metasprites/elephant-cyan-subpalette.png`; decouples from phase/seed IDs | ✓ |
| Keep existing labels | Reuse current capture labels (`SEED-004`, `ROM-smoke`) as anchor names | |

**User's choice:** Descriptive, phase-agnostic.
**Notes:** Migration preserves PNG bytes identically; only the filename/key changes. ~12 anchors total.

---

## Diff helper / reuse strategy

### Diff wiring

| Option | Description | Selected |
|--------|-------------|----------|
| New thin `assertGolden` helper | Helper captures to scratch, calls `VisualDiff.compare(0.0)`, fails on mismatch; bespoke tests swap `captureAndRename` → `assertGolden`. UatRunner untouched | ✓ |
| Rewire tests through UatRunner | Migrate 33 bespoke tests onto UatRunner checkpoint+goldenDir | |
| Both: helper reuses UatRunner core | New helper delegates to shared goldenDir+VisualDiff resolution | |

**User's choice:** New thin `assertGolden` helper.
**Notes:** Low-risk; reuses existing `VisualDiff.compare()` exact-match engine.

### Missing-golden behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Fail with re-baseline hint | Test fails with "GOLDEN MISSING … run re-baseline task to bless"; never auto-writes | ✓ |
| Auto-create on first run | First run writes golden, later runs diff | |

**User's choice:** Fail with re-baseline hint.
**Notes:** Preserves the "no writes on plain `./gradlew test`" guarantee.

---

## Re-baseline trigger shape

### Trigger mechanism

| Option | Description | Selected |
|--------|-------------|----------|
| Gradle `-P` prop → system property | `-Pgbkt.updateGoldens` wires a system property the helper reads; composes with `--tests` | ✓ |
| Dedicated gradle task | `./gradlew updateGoldens` runs visual tests in bless-mode | |
| Env var | `GBKT_UPDATE_GOLDENS=1` | |

**User's choice:** Gradle `-P` prop → system property.
**Notes:** Idiomatic; granular via `--tests`; explicit flag avoids accidental bless.

### Bless guard

| Option | Description | Selected |
|--------|-------------|----------|
| Guarded bless | GBC auto-detect (R5) still runs in update mode; mis-built DMG ROM can't bless inverted golden | ✓ |
| Blind write | Update mode writes whatever the capture produces | |

**User's choice:** Guarded bless.
**Notes:** Prevents the Phase 13.5 / 21-07 wrong-mode failure from being immortalized.

---

## Sidecar disposition

| Option | Description | Selected |
|--------|-------------|----------|
| Keep in scratch, drop `capturedAt` | Sidecar written only to gitignored scratch; remove nondeterministic `capturedAt` field; update `ScreenshotCaptureTest` | ✓ |
| Keep sidecar fully intact | Leave `capturedAt`; churn solved purely by gitignoring scratch + git-rm | |
| Stop writing sidecars entirely | Remove sidecar emission from `capture()` | |

**User's choice:** Keep in scratch, drop `capturedAt`.
**Notes:** `capturedAt` has zero production consumers — only `ScreenshotCaptureTest.kt:92-94` asserts on it. Keeps `variables`/`debugLog` for debugging; deterministic against future accidental commits.

---

## Claude's Discretion

- Module placement of the `assertGolden` helper (`gbkt-test` vs `gbkt-emulator`).
- Whether to flatten `goldens/<rom>/<anchor>.png` to `goldens/<anchor>.png` per module.
- Exact system-property key name and missing-golden failure-message wording.

## Deferred Ideas

None — discussion stayed within phase scope.
