# Phase 12.10 — Deferred Items

## PlatformerTemplate128UatTest.anchor4MetaspriteAnimation (out of scope, pre-existing)

- **Discovered during:** Plan 12.10-03 execution (the `--tests "*anchor4*"` glob matched
  both `PlatformerTemplateUatTest` and its Phase-12.8 clone `PlatformerTemplate128UatTest`).
- **What:** `PlatformerTemplate128UatTest` is a Phase-12.8 D-11 clone of
  `PlatformerTemplateUatTest`, behaviorally identical except for its EVIDENCE_DIR target
  (Phase 12.8 evidence root). Its `anchor4MetaspriteAnimation()` still uses the OLD
  full-frame `VisualDiff.compare(..., tolerance = 0.10)` gate and the OLD per-capture
  `walkFrameIdx` sampling — i.e. it carries the exact 6.60% full-frame soft-fail this
  phase was created to fix, for the clone.
- **Why deferred:** Plan 12.10-03 scope is explicitly limited to
  `PlatformerTemplateUatTest.kt` ("No edits outside PlatformerTemplateUatTest.kt; zero
  codegen-module edits" — acceptance criterion). The clone's failure is a pre-existing
  baseline condition (the clone was created in Phase 12.8 and predates 12.10), not a
  regression introduced by this plan. Per the executor SCOPE BOUNDARY rule, out-of-scope
  failures in unrelated files are logged here and not fixed.
- **Suggested follow-up:** A small in-12.10 follow-up plan (or a 12.8 cleanup) could either
  (a) retire the 12.8 clone now that Phase 12.8 shipped diagnostic-only, or (b) apply the
  same settled-capture + OAM-region retune to the clone. Neither is required for 12.10-03's
  acceptance.
