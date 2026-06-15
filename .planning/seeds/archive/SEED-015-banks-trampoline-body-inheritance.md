# SEED-015: Scene trampoline body delegates to wrong (prior) scene when source scene has no banked body

> **Triage:** VERIFIED-ALREADY-FIXED — [TRIAGE.md#SEED-015](.planning/phases/16-seed-triage/TRIAGE.md#SEED-015) · 2026-06-12

**Surfaced by:** Phase 11 (banks port, Plan 11-05 generateC + Plan 11-14 close)
**Evidence:** `.planning/phases/11-port-banks-gbdk-example-to-gbkt/deferred-items.md` §"D11-05-1" + `gbkt-examples/banks/build/gbkt/generated/main.c:202-209` (post-clean rebuild reproduces it)
**Symptom:** When FFD places one scene's enter/frame in a banked file but another scene's enter/frame in HOME (because the HOME scene is small enough), the trampoline-generation pass mis-emits the HOME scene's trampoline as a delegation to the previous banked scene's body. Concretely from `main.c:202-209` after the final clean buildRom:

```c
// Trampoline: pause_enter (bank 1)
void title_enter_trampoline(void) {
    pause_enter();        // ← WRONG — title is HOME-resident; trampoline should be no-op
}
void title_frame_trampoline(void) {
    pause_frame();        // ← WRONG — same delegation skew
}
```

The smoking gun is the `// Trampoline: pause_enter (bank 1)` comment header attached to the title block — proving the trampoline-naming-skew is in the emission loop, not just the delegate target.

**Hypothesis:** `GBDKPipelineV2.buildSceneNavigationFunction` (or wherever per-scene trampolines are emitted) iterates the scene list, retains the last-emitted banked function reference as a fallback, and re-uses that reference when the current scene has no banked enter/frame. For HOME-resident scenes (small enough to fit in HOME, never banked), the trampoline should either be:
1. Omitted entirely (HOME→HOME navigation needs no SWITCH_ROM wrapper), or
2. Emitted as a literal no-op stub `void <scene>_enter_trampoline(void) { /* HOME-resident: noop */ }`

**Blast radius:** **LOCAL** — single emission site in `GBDKPipelineV2`. Affects any multi-scene game where FFD packs some scenes into HOME and others into bank ≥1 (banks port surfaces this for the first time because it has small enough scenes; dungeon/explorer have all scenes banked so the bug never triggers). Risk to dungeon/explorer/racer regression is low if the fix is "emit no trampoline OR emit a no-op stub for HOME-resident scenes" — the pre-existing banked-scene path is untouched.

**Why not silently runtime-fatal:** Title scene's `frame` only checks `buttons.start.pressed` → `navigate(playScene)`. If the user holds Start past one frame, `title_frame_trampoline` is called → which calls `pause_frame()` (wrong body) → which checks if Start was pressed (true) → calls `navigate("play")` again. The skew is masked by the input-pressed semantics matching across all three scenes. A different trampoline body (e.g. pause's idle wait vs. play's per-frame zone-update) would be a runtime crash. Banks dodges the crash by coincidence, NOT by correctness.

**Routing:** **Phase 11.1** (terminal closer subphase). Bundled with SEED-014 because both are codegen-pipeline bugs surfaced by the same port and both have JVM-tier sentinel coverage. A RED test should be added during Phase 11.1 planning that asserts `title_enter_trampoline` body is either empty or a no-op (NOT a `pause_enter()` / `play_enter()` call).

**Files in play:**
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` (function emitting scene trampolines — exact line range to be located during research)
- `gbkt-examples/banks/build/gbkt/generated/main.c:198-215` — current output reproducing the bug

**Workaround in Phase 11 banks port:** None — Banks runs because trampolines coincidentally delegate to scenes with the same Start-press handler. The bug is documented and routed; Banks ships with the broken trampolines visible in generated source.
