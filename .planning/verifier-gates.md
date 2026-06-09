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
| `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` | Owns the file-set decision (fold or split) |
| `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/**` | Any visitor change can shift emission shape |
| `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BankingAnalysisPass.kt` | Owns the bank-assignment fast-path |

**Smoke command:**

```bash
./gradlew :gbkt-examples:simple-physics:clean :gbkt-examples:simple-physics:buildRom
```

**Pass criteria:** Exit code 0 AND `gbkt-examples/simple-physics/build/gbkt/output/simple-physics.gb` exists with size > 0.

**Skip behavior:** When `GBDK_HOME` is unset or `lcc` is not on `PATH`, mark the gate SKIPPED with the structured note: `ROM-build smoke SKIPPED — GBDK not available on verifier machine; human MUST run :gbkt-examples:simple-physics:buildRom locally before sign-off.`
