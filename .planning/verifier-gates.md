# Verifier Gates

Project-level gates the gsd-verifier runs automatically when a phase's diff touches matching
paths. Each gate has a name, a trigger path list, and a smoke command. Gates are read by the
`build_smoke_gates` step in `$HOME/.claude/get-shit-done/workflows/verify-phase.md`.

## Gate: rom-build

**Purpose:** Catch codegen-pipeline regressions that JVM-tier tests cannot see — staleness in
`build/gbkt/generated/`, link errors against dropped bank files, MBC byte drift, etc. See Phase
09.1 closeout incident for the precedent failure mode: a clean
`./gradlew :gbkt-examples:simple-physics:buildRom` produced duplicate `_play_enter` /
`_play_frame` link errors against a stale `bank1.c` left over from a prior MBC5 build even
though all JVM-tier tests had passed (15/15 truths verified). Phase 09.2 converts this
user-feedback memory rule into a verifier-enforced gate.

**Trigger paths (any match fires the gate):**

| Path glob | Reason |
|-----------|--------|
| `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/GenerateCTask.kt` | Owns the file-emission write loop |
| `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/CompileRomTask.kt` | Owns the lcc invocation + MBC upgrade logic |
| `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipeline.kt` | Owns the file-set decision (fold or split) |
| `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/**` | Any visitor change can shift emission shape |
| `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BankingAnalysisPass.kt` | Owns the bank-assignment fast-path |

**Smoke command:**

```bash
./gradlew :gbkt-examples:simple-physics:clean :gbkt-examples:simple-physics:buildRom
```

**Pass criteria:** Exit code 0 AND `gbkt-examples/simple-physics/build/gbkt/output/simple-physics.gb` exists with size > 0.

**Skip behavior:** When `GBDK_HOME` is unset or `lcc` is not on `PATH`, mark the gate SKIPPED with the structured note: `ROM-build smoke SKIPPED — GBDK not available on verifier machine; human MUST run :gbkt-examples:simple-physics:buildRom locally before sign-off.`

## Verification Methodology — Visual Evidence Rule

For verification truths shaped **"X is visible on screen"** (e.g., "track tilemap is
visible", "HUD shows lap count", "menu cursor is highlighted"), evidence MUST include a
runtime screenshot, NOT just a variable-state assertion.

Variable assertions like `assertVariable("_current_tileset_id", 1)` prove that the
codegen wrote a value at one point in scene-enter — they do NOT prove the value is
visually reflected by the time the player sees the screen. A subsequent op (e.g., a
user-authored `clear()` lowering to `cls()`) can wipe the visual outcome while leaving
the variable intact.

### When this rule applies

- GSD verifier runs against truths/SCs whose phrasing is visual ("is visible", "renders
  on screen", "is shown to the player").
- MCP play-throughs that verify SCs at runtime.
- UAT verdicts that flip a phase to `passed`.

### When variable evidence is sufficient

- Internal state truths ("AI active", "lap counter incremented", "save written") where
  the visual surface is downstream of (and inferred from) the state.
- JVM-tier codegen tests that lock the GENERATED C SHAPE (which is upstream of runtime
  visual outcomes) — a generated-C grep is acceptable evidence here because it locks the
  contract one level below the visual.

### How to satisfy the rule

The MCP `gbkt-emulator` provides `emulator_screenshot(path)`. Capture a PNG to the
phase's `evidence/` directory at every visual SC checkpoint. The screenshot becomes the
evidence artifact in `*-VERIFICATION.md`.

### Scope-level grep gates (corollary)

A file-level `grep -c cls() bank1.c` cannot distinguish race_enter from title_enter —
if title_enter has back-compat `cls()`, the count masks a regression in race_enter.
For per-function invariants, extract the function body via brace-walk (awk) and grep
WITHIN scope. Plan 07.4-23 Task 1 step 3 demonstrates the awk pattern.

### History

This rule was codified after Phase 07.4 round-2 verified SC-4 (track visible) via
`_current_tileset_id=1` variable evidence; the runtime ROM never actually rendered the
track. The bug took 5 plans (15-18) to surface and was caught only by user UAT in round
4. Plan 07.4-19 added JVM-tier RED tests; Plan 07.4-20 fixed the codegen; this section
fixes the methodology so the class of bug is structurally guarded against in future
phases.

See: `.planning/phases/07.4-sport-genre-codegen-fix-inserted/07.4-UAT.md`,
`.planning/phases/07.4-sport-genre-codegen-fix-inserted/07.4-19-PLAN.md`.
