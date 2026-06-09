# Phase 11 — Deferred Items (Out-of-Scope Discoveries)

Items surfaced during Phase 11 plan execution that are NOT directly caused by the
plan's task changes (per executor SCOPE BOUNDARY rule). Recorded here so they
don't get lost; the verifier / orchestrator routes them to the right plan or seed.

## From Plan 11-05 (Banks DSL substrate)

### D11-05-1: `<sceneId>_enter_trampoline` body delegates to the WRONG scene

**Discovered:** During Plan 11-05 generateC.
**Symptom:** `gbkt-examples/banks/build/gbkt/generated/main.c` emits

```c
// Trampoline: pause_enter (bank 1)
void title_enter_trampoline(void) {
    pause_enter();        // ← should be a no-op (title has no banked body), not pause_enter
}

void title_frame_trampoline(void) {
    pause_frame();        // ← same wrong delegation
}
```

The `// Trampoline: pause_enter (bank 1)` comment header on the title block is the
smoking gun — the trampoline-generation pass appears to inherit the last-emitted
banked scene's body when the current scene has no banked function of its own.

**Pre-existing or 11-05-induced?** Pre-existing in `GBDKPipelineV2` /
`buildSceneNavigationFunction` (or wherever trampolines are emitted). Plan 11-05
is the first multi-scene gbkt example where one scene (`title`) has small enough
enter/frame bodies that FFD does NOT place it in a banked file — so the
trampoline-naming-skew was never triggered before.

**Out-of-scope reason:** The plan 11-05 task is "write Banks.kt DSL"; the bug is
in `gbkt-backend-gbdk` codegen, not in DSL authoring.

**Suggested routing:** Plan 11-09 is "first-buildrom-bug-naming" — this IS the
named bug candidate per RESEARCH §Top-2 Likely Codegen Bug Candidates (a new
candidate (e): trampoline body inheritance). Surface as the named bug for Plan
11-10, OR capture as a seed alongside the `trigger_saves` gap if Plan 11-10
picks the SaveSystem fix instead.

**No fix attempted in Plan 11-05** — per SCOPE BOUNDARY + Rule 4 (architectural).
