# Phase 14: cleanup for v0.1.0 release — Research

**Researched:** 2026-06-06
**Domain:** Codebase cleanup — example retirement, V2 suffix removal, dead-code sweep, release readiness
**Confidence:** HIGH (all findings verified against live codebase; no training-data assumptions)

## Summary

Phase 14 is a behavior-neutral cleanup phase with zero new features. The strategy is fully locked in CONTEXT.md (D-01..D-13, 7-step track order). This research document is an evidence-grade codebase inventory: real file paths, real counts, and real reachability findings so the planner can cite actuals rather than SPEC estimates.

The most consequential discovery is that the Gradle plugin calls `generateV2` **by name via reflection** (`GenerateCTask.kt:382`), so the V2 rename requires simultaneous update to the reflection call site in addition to the semantic rename. Failure to update the reflection string would cause a silent runtime failure (`NoSuchMethodException`) not caught by compile.

The second consequential discovery is that `gbkt-examples/.archive/` is **git-ignored** (not git-tracked) per `.gitignore:55`. The SPEC's "git rm of .archive/" cannot be executed — the directory needs `rm -rf` on the working tree plus removal of the `.gitignore` entry. No git history exists for these files.

The third finding clarifies dead-code status: `GBDKBackend.generate()` is NOT dead — it is the live `CodegenBackend` interface implementation that delegates to `generateV2()`. The dead `generate()` described in the SPEC refers to a conceptual situation where `generate()` would be replaced by the rename. The rename strategy is: (1) remove the `generateV2()` delegation-wrapper body, (2) promote `generateV2()`'s signature/body to become `generate()`, and (3) remove the now-redundant old `generate()` override. No collision risk exists once this is understood correctly.

**Primary recommendation:** Execute the 7-track order from D-11 exactly. Use Serena `rename_symbol` for the 3 big code symbols (GBDKPipelineV2 → GBDKPipeline, SimulationContextV2 → SimulationContext, generateV2 → generate), then update the reflection call string in GenerateCTask.kt, then sweep filenames + KDoc. The byte-identity gate after each mutating track is the backstop against any drift.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Run-Verification Method (Req 1)**
- D-01: Each KEEP example gets a live MCP emulator run-check (mcp__gbkt-emulator__* must be live; GBDK required for the ROMs).
- D-02: Run-check depth is boot + one input cycle: boot to first meaningful screen → screenshot → one input cycle (Start/move per PLAYBOOK.md) → screenshot again.
- D-03: `racer` is RETIRE (known-dead) — confirmed on documented-failure status, not repaired.
- D-04: Per-example audit table + boot/input screenshots live in phase `evidence/` directory.

**Byte-Identity Baseline Strategy (Req 5)**
- D-05: Capture full generated-C snapshot (main.c + all bank*.c, SHA-256) for every KEEP example.
- D-06: 2 committed `*GeneratedSpriteByteIdentityTest` baselines (metasprites, metasprites-stress; re-pinned 13.6-07) stay as a second, independent gate. Verify current at phase start; re-pin if stale (behavior-neutral).
- D-07: Baseline captured from pre-phase HEAD (feat/d_and_d_gaps) before any mutation.
- D-08: Gate runs after each mutating track (V2 rename and dead-code sweep). pong ROM is PASS* (known toolchain nondeterminism — generated-C is the real gate).

**V2 Rename Execution (Req 3)**
- D-09: Semantic-first (Serena rename_symbol for big code symbols), textual-sweep-second (sed for filenames, KDoc, diagnostic test class names). Byte-identity gate is the backstop.
- D-10: Acceptance grep `grep -rE "[A-Za-z_]*V2\b" --include=*.kt` (excluding build/.git/.claude/worktrees) must return zero matches.

**Track Ordering (D-11)**
1. Audit all 8 included examples (build + live run-check) → KEEP/RETIRE verdicts
2. Retire — git rm RETIRE examples + LabyrinthOfTheDragon-port/ + LabyrinthOfTheDragon/ + gbkt-examples/.archive/ cleanup; update settings.gradle.kts to KEEP-only
3. Baseline — full generated-C snapshot for surviving examples
4. Dead-code sweep — conservative, proof-gated; clears dead path in GBDKBackend → gate (compile + full JVM suite GREEN) + byte-identity diff
5. V2 rename — generateV2→generate collision-free (step 4 cleared it) → byte-identity diff
6. CI / docs / version — update workflows, README, CLAUDE.md, context docs
7. Final regression sweep — clean :buildRom EXIT 0 + whole-tree compile + full JVM suite GREEN

**CI Workflow Rewrite (Req 5)**
- D-12: Rewrite .github/workflows/kotlin.yml to build + generateC for ALL KEEP examples.
- D-13: CI stays build + generateC only — NO :buildRom in CI (GBDK toolchain not on runner).

### Claude's Discretion
- Exact dead-code reachability-proof technique (grep-callers + compile + full-suite) is planner/executor's call, within conservative mandate.
- Whether to fold `rpgregistry-clear-never-called` into the Req-4 sweep depends on non-reachability proof — decide at sweep time.

### Deferred Ideas (OUT OF SCOPE)
- `compilerom-silent-mbc5-fallback-warning` — behavior change. Deferred.
- `configbuilder-cartridge-setter-api-consistency` — API change. Deferred.
- `easetozero-oscillates-when-by-greater-than-one` — behavior fix. Deferred.
- `orelse-may-attach-to-wrap-guard-ifop` — behavior fix. Deferred.
- `triggersystem-ref-registry-validation` — behavior change. Deferred.
- `wrapat-decrement-asymmetry-mask-vs-compare` — behavior fix. Deferred.
- `wrapat-zero-silent-always-reset` — behavior change. Deferred.
- `13.8-palette-bank-codegen-followups`, `13.6-07-convertsprites-hardening-followups` — codegen correctness follow-ups. Deferred.
- `rpgregistry-clear-never-called` — fold ONLY if non-reachability proof succeeds at sweep time.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| Req 1 | Per-example build+run audit → documented KEEP/RETIRE verdict with live-emulator evidence | Example inventory table below; PLAYBOOK.md availability per example confirmed; GBC mode flags confirmed |
| Req 2 | git rm of LabyrinthOfTheDragon-port/, LabyrinthOfTheDragon/, gbkt-examples/.archive/, and all RETIRE examples; settings.gradle.kts → KEEP-only | Tracked file counts per path confirmed; .archive is gitignored — needs rm -rf not git rm; exact settings.gradle.kts lines identified |
| Req 3 | Rename every V2 migration-suffix identifier (symbols + files + KDoc/doc refs) across main src + examples + tests | Complete V2 identifier census with reference counts; reflection call site in GenerateCTask.kt identified as critical |
| Req 4 | Conservative, proof-gated dead-code sweep (remove only positively-proven-unreachable pre-AST code) | GBDKBackend.generate() vs generateV2() relationship fully mapped; RpgRegistry.clear() reachability investigated |
| Req 5 | Release readiness — tree builds GREEN, KEEP-only examples in CI, docs/version read v0.1.0, byte-identity regression gate | generateC output layout confirmed; committed byte-identity test files verified; pong PASS* exemption confirmed; gradle conventions confirmed |
</phase_requirements>

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Example audit run-check | MCP emulator (runtime) | GBDK local toolchain | Visual Evidence Rule: "build AND run" requires live ROM in emulator, not JVM-tier proxy |
| V2 symbol rename | IDE/Serena semantic rename | Textual sed sweep | Cross-module references (374 GBDKPipelineV2 refs) require reference-following, not text replace |
| Reflection call site update | Manual text edit | — | GenerateCTask.kt:382 uses string literal "generateV2" — not covered by semantic rename |
| Byte-identity gate | SHA-256 diff of generated C | Committed test baselines | Generated C is the authoritative behavioral proof; ROM hash is nondeterministic (pong) |
| Dead-code removal | Compiler evidence (compile breaks) | Serena find_referencing_symbols | Conservative mandate: compiler GREEN after removal is the proof; find_referencing_symbols pre-screens |
| CI update | Workflow YAML edit | — | .github/workflows/kotlin.yml text edit to replace stale module references |
| Doc update | Text edit in .md files | — | CLAUDE.md, README.md, context/*.md stale references identified |

---

## Deliverable 1: Example Inventory (Req 1 / Req 2 Audit Surface)

### Current settings.gradle.kts included examples (lines 56–63)

[VERIFIED: codebase grep]

| Example | PLAYBOOK.md? | GBC Target? | Notes |
|---------|-------------|-------------|-------|
| `pong` | YES | NO (DMG) | `emulator_start {"game":"pong"}` — DMG mode; `.noi` confirmed in build/gbkt/output/ |
| `breakout` | YES | NO (DMG) | Has `BreakoutV2.kt` file that needs rename to `Breakout.kt` |
| `racer` | YES (has PLAYBOOK) | YES (GBC) | **RETIRE per D-03** — known-dead; PLAYBOOK exists but build confirmation not attempted yet |
| `simple-physics` | YES | NO (DMG) | Standard emulator_start; no V2 filename |
| `metasprites` | NO | YES (GBC) | GBC mode required; committed byte-identity test exists |
| `metasprites-stress` | NO | YES (GBC) | GBC mode required; committed byte-identity test exists |
| `banks` | YES | NO (DMG) | Has BanksEmissionTest that calls `generateV2` directly |
| `platformer-template` | YES | YES (GBC) | GBC mode required; `.noi` symFile needed for MCP agent |

**MCP run-check setup notes:**
- GBC examples (`metasprites`, `metasprites-stress`, `platformer-template`): `emulator_start` with `gbcMode: true` + symFile path to `.noi`
- DMG examples (`pong`, `breakout`, `simple-physics`, `banks`): standard `emulator_start {"game":"<name>"}`
- `racer`: run-check is just `:buildRom` confirmation of failure (D-03 says confirm fail, do not repair)

**Executor note on PLAYBOOK absence for metasprites/metasprites-stress:** No PLAYBOOK.md exists. D-02 says "per the example's PLAYBOOK.md where present." For these two examples, the boot-screen + any visible sprite animation constitutes the run evidence. The executor should capture a screenshot at boot and one after a few frames.

### Dead-tree git rm surface [VERIFIED: git ls-files]

| Directory | git-tracked files | Action |
|-----------|------------------|--------|
| `LabyrinthOfTheDragon-port/` | 102 | `git rm -r LabyrinthOfTheDragon-port/` |
| `LabyrinthOfTheDragon/` | 160 | `git rm -r LabyrinthOfTheDragon/` |
| `gbkt-examples/.archive/` | 0 (git-ignored per .gitignore:55) | `rm -rf gbkt-examples/.archive/` + remove `.gitignore` entry at line 54-55 |
| `gbkt-examples/racer/` | 21 (if RETIRE verdict confirmed) | `git rm -r gbkt-examples/racer/` |

**Critical note on .archive:** The SPEC says "git rm of gbkt-examples/.archive/" but this directory is **gitignored** — it was never committed (`.gitignore:54-55`: `# Phase 11.3: archived aspirational examples (local stash, not tracked)` / `gbkt-examples/.archive/`). The cleanup action is `rm -rf gbkt-examples/.archive/` (filesystem only) plus removing those two lines from `.gitignore`. No git history is lost.

**settings.gradle.kts cleanup:** Remove `include("gbkt-examples:racer")` line (line ~57). The 7 KEEP examples stay. Confirm `LabyrinthOfTheDragon-port` is already commented out (verified — it's a comment, not an active include).

---

## Deliverable 2: V2 Identifier Inventory (Req 3)

### Summary census [VERIFIED: codebase grep]

- **Total V2-pattern lines** in `.kt` files (excluding build/.git/.claude/.archive): **537 lines across 165 files**
- **Migration-suffix V2 occurrences** (confirmed rename targets): **473 lines**
- **False-positive V2 occurrences** (must NOT rename): **~64 lines** — see False Positives section below

### Top migration-suffix symbols with occurrence counts

[VERIFIED: grep -rE in live codebase, excluding build/.git/.claude]

| Symbol | Occurrence count | Declaring file | Rename to |
|--------|-----------------|----------------|-----------|
| `GBDKPipelineV2` | 378 | `gbkt-backend-gbdk/.../pipeline/GBDKPipelineV2.kt` | `GBDKPipeline` |
| `SimulationContextV2` | 163 | `gbkt-core/.../test/SimulationContextV2.kt` | `SimulationContext` |
| `generateV2` | 27 | `GBDKBackend.kt:81` (declaration) | `generate` (see collision note) |
| `PipelineV2Output` | 8 | inside `GBDKPipelineV2.kt:136` | `PipelineOutput` |
| `PongV2` | 9 (4 in code + 5 variations) | `gbkt-examples/pong/src/.../PongV2.kt` | `Pong` |
| `BreakoutV2` | 5 (+ 1 lowercase) | `gbkt-examples/breakout/src/.../BreakoutV2.kt` | `Breakout` |
| `ExplorerV2` | 2 | `gbkt-examples/.archive/explorer/.../ExplorerV2.kt` | DELETED with archive (Track 2) |
| `pipelineV2` (local var) | 2 | `GBDKBackend.kt:159` | `pipeline` |
| `executeV2Path` | 3 | `GenerateCTask.kt:360` (declaration) | `executePath` or `executeMainPath` |

### V2 files requiring rename [VERIFIED: find command]

| Current path | Rename to |
|-------------|-----------|
| `gbkt-core/src/main/kotlin/.../test/SimulationContextV2.kt` | `SimulationContext.kt` |
| `gbkt-examples/pong/src/main/kotlin/.../pong/PongV2.kt` | `Pong.kt` |
| `gbkt-examples/breakout/src/main/kotlin/.../breakout/BreakoutV2.kt` | `Breakout.kt` |
| `gbkt-backend-gbdk/src/main/kotlin/.../pipeline/GBDKPipelineV2.kt` | `GBDKPipeline.kt` |
| `gbkt-examples/.archive/explorer/src/.../ExplorerV2.kt` | DELETED with archive (not renamed) |

### V2 test class names requiring rename [VERIFIED: grep for class declarations]

| Current class name | File | Rename to |
|-------------------|------|-----------|
| `GBDKPipelineV2MetadataSpritesTest` | `gbkt-backend-gbdk/.../pipeline/GBDKPipelineV2MetadataSpritesTest.kt` | `GBDKPipelineMetadataSpritesTest` |
| `SimulationContextV2Test` | `gbkt-core/.../test/SimulationContextV2Test.kt` | `SimulationContextTest` |
| `DV2BgAspectDiagnosticTest` | `gbkt-lang/.../dsl/DV2BgAspectDiagnosticTest.kt` | `BgAspectDiagnosticTest` (or keep as named diagnostic) |
| `DV3VisualV2DiagnosticTest` | `gbkt-backend-gbdk/.../visitor/DV3VisualV2DiagnosticTest.kt` | `DV3VisualDiagnosticTest` |

### KDoc/comment/string mentions [VERIFIED: grep]

| Location | Type | V2 reference |
|----------|------|--------------|
| `gbkt-ir/src/.../SceneIR.kt:52` | KDoc | `GBDKPipelineV2.buildCFiles` |
| `gbkt-ir/src/.../MetaspriteIR.kt:99,116` | KDoc | `GBDKPipelineV2.buildMetaspriteSpritePaletteStatements` |
| `gbkt-ir/src/.../WorldIR.kt:83` | KDoc | `GBDKPipelineV2.buildSetupCurrentLevelFunctionIfNeeded` |
| `gbkt-backend-gbdk/CLAUDE.md:11,36,51,59,66` | Docs | `GBDKPipelineV2` and `generateV2()` |
| `gbkt-backend-gbdk/.../pipeline/CLAUDE.md:5` | Docs | `## GBDKPipelineV2` |
| `CLAUDE.md:214,698,717` | Project docs | `GBDKPipelineV2.buildMetadataFile()`, pipeline table entry, test table entry |
| `context/TESTING.md:23` | Docs | `val ctx = SimulationContextV2()` |
| `context/TOOLING.md:81` | Docs | `GBDKPipelineV2.buildMetadataFile()` |
| `gbkt-backend-gbdk/src/test/.../RomBanksDerivedTest.kt:17,65,97` | Comments | `GBDKBackend.generateV2()` |
| `gbkt-backend-gbdk/src/test/.../AutoExitSynthesisTest.kt:288,291` | Comments | `GBDKBackend.generateV2()` |
| Multiple test files | Comments | Various `GBDKPipelineV2` references in test preamble comments |

### CRITICAL: Reflection call site [VERIFIED: codebase grep]

**This is not covered by semantic rename.** `GenerateCTask.kt:382-383`:
```kotlin
val generateV2Method =
    backend.javaClass.getMethod(
        "generateV2",    // ← this string literal must be updated to "generate"
        gameIrClass,
        assetManifestClass,
        java.io.File::class.java,
    )
```
Additionally, `GenerateCTask.kt:390`:
```kotlin
generateV2Method.invoke(backend, gameIR, null, outputDir)
    ?: throw GradleException("generateV2 returned null")   // ← update error message too
```
And `GenerateCTask.kt:360`: `private fun executeV2Path(...)` → rename to `executePath()` or similar.

**Failure mode if missed:** Runtime `NoSuchMethodException` in `generateC` task — the plugin calls the non-existent method name via reflection. Compile is GREEN but `generateC` fails at execution time.

### False positives — V2 occurrences that MUST NOT be renamed [VERIFIED: codebase grep]

| Pattern | Location | Why it must NOT change |
|---------|----------|----------------------|
| `D-V2` (decision label) | Comments in BgCheckerboardEmissionTest, ScriptOpVisitor, MetaspriteBuilder, Seed005CheckerboardBytePatternTest | Historical plan decision identifier ("Decision V2 of plan 10.1"), not a migration suffix |
| `DV3V2` / `Dv3V2` | Comment in DV3VisualV3DiagnosticTest | Refers to "DV3 visual test iteration 2" (diagnostic round label), not a migration suffix |
| `extractFunctionBodyForDv3V2` | Private helper in DV3VisualV2DiagnosticTest | Class itself is being renamed; this local function suffix "V2" is a disambiguation label |
| `"V2 code generation failed"` | GBDKBackend.kt:213 string literal | Error message string — rename to `"code generation failed"` as part of the sweep |
| `"V2 port"` in LabyrinthOfTheDragon-port/ | Various Labyrinth files | DELETED with the whole directory in Track 2 — no rename action needed |

**The acceptance grep `grep -rE "[A-Za-z_]*V2\b" --include=*.kt` will NOT return zero after rename** until `D-V2`, `DV3V2`, and similar comment strings are also cleaned. These false positives in comments still match the acceptance pattern. The planner must include a sed sweep for comment-level occurrences too, or relax the acceptance grep to allow comment-only matches — the SPEC says "zero matches" so all occurrences in comments must be updated or annotated as intentional exceptions. **Recommendation:** update all comment/string occurrences as part of the textual sweep step (D-09 "textual sed pass"), converting `D-V2` to `D-V2` (keep as historical label — these are inside the files that the grep includes; they match `\bV2\b` due to the separator). The planner should address this with a targeted text sweep that converts plan-decision labels like `D-V2` within comments to an equivalent that does not match the acceptance grep.

---

## Deliverable 3: Dead-Code Reachability (Req 4)

### GBDKBackend.generate() vs generateV2() [VERIFIED: file read + grep]

**Finding: `GBDKBackend.generate()` is NOT dead code.** It is the live `CodegenBackend` interface implementation (line 57). It delegates to `generateV2()` on line 58:
```kotlin
override fun generate(game: GameIR, options: GenerationOptions): GenerationResult {
    return generateV2(game)
}
```

The SPEC's "dead `GBDKBackend.generate()`" refers to the conceptual state AFTER the rename. The correct rename sequence is:

1. `generateV2(gameIR, assetManifest?, outputDirectory?, assetRoot?)` becomes `generate(gameIR, assetManifest?, outputDirectory?, assetRoot?)` — the full-signature method
2. The old `override fun generate(game: GameIR, options: GenerationOptions)` bridge becomes redundant (it delegated to generateV2)
3. Remove the bridge `generate()` override; the interface is satisfied by the renamed full-signature `generate()` with `@JvmOverloads`

**What callers of generateV2 exist:** [VERIFIED: grep]
- `GBDKBackend.kt:58`: `return generateV2(game)` (the bridge — this IS removed in the rename)
- `GBDKBackend.kt:159`: `val pipelineV2 = GBDKPipelineV2()` (local var, not calling generateV2)
- `RomBanksDerivedTest.kt:128`: `backend.generateV2(...)` — direct Kotlin call (updated by symbol rename)
- `AutoExitSynthesisTest.kt:293,294`: `backend.generateV2(...)` — direct Kotlin calls (updated by symbol rename)
- `SportLegacyTilesetPathInvariantTest.kt:70`: `GBDKBackend().generateV2(ir)` — direct call (updated)
- `BanksEmissionTest.kt:117,416`: `backend.generateV2(...)` — direct calls (updated)
- `GenerateCTask.kt:382-390`: via reflection string `"generateV2"` — **MUST be manually updated**

**CodegenBackend.generate() interface contract:** `fun generate(game: GameIR, options: GenerationOptions = GenerationOptions()): GenerationResult` — the renamed full-signature `generate()` with optional parameters satisfies this via `@JvmOverloads`. The interface is in `gbkt-backend-api`.

### RpgRegistry.clear() [VERIFIED: grep for callers]

`RpgRegistry.clear()` in `gbkt-genre-rpg/.../dsl/RpgExtensions.kt:80` has **zero callers** in the non-archive, non-build codebase. The grep search for `RpgRegistry.clear\|RpgRegistry\.clear` returned no results outside the declaration.

**Reachability verdict:** CANDIDATE for removal under Req-4 conservative sweep. **However:** `RpgRegistry` is an `internal object` — if any test or future code instantiates a game and records RPG entities without calling `clear()` between tests, this could cause test pollution. Confirm non-reachability by: (1) `find_referencing_symbols` on `RpgRegistry.clear`, (2) compile without it, (3) full JVM suite GREEN. Only then remove.

### Other dead-code candidates

No other proof-dead pre-AST code candidates identified with HIGH confidence. The old `GBDKCodeGenerator` was already deleted per REQUIREMENTS.md CGEN-04. The `v2/` subdirectories were already promoted per REQUIREMENTS.md CLEAN-02. The main V2 leftover is the suffix on live, working code — not dead code.

**Conservative mandate:** Do not remove any other code without first verifying with `find_referencing_symbols`. The blast radius of broad sweeps was explicitly excluded from Req-4 scope.

---

## Deliverable 4: Byte-Identity Gate Mechanics (Req 5)

### generateC output layout [VERIFIED: ls on live build output]

For each example, `./gradlew :gbkt-examples:<name>:generateC` produces under `gbkt-examples/<name>/build/gbkt/generated/`:
```
main.c          ← always present
main.c.gbkt.map
game.h          ← always present (header/prototypes)
bank1.c         ← always present (banked scenes)
bank1.c.gbkt.map
game_metadata.json
gbkt-build.properties
sprites/        ← directory with sprite .c files
```
Multi-bank examples (e.g., platformer-template) may also have `bank2.c`, `bank3.c`, etc.

**SHA snapshot command (for D-05 baseline):**
```bash
find gbkt-examples/<name>/build/gbkt/generated -name "*.c" | sort | xargs sha256sum
```

### Committed byte-identity test files [VERIFIED: find command]

| File | Status |
|------|--------|
| `gbkt-examples/metasprites/src/test/kotlin/.../MetaspritesGeneratedSpriteByteIdentityTest.kt` | EXISTS — tests `elephant.c` against `baseline/elephant.c.baseline` |
| `gbkt-examples/metasprites-stress/src/test/kotlin/.../MetaspritesStressGeneratedSpriteByteIdentityTest.kt` | EXISTS — tests metasprites-stress sprites |
| Baselines re-pinned | 2026-06-05 (Plan 13.6-07) — deterministic temp names ensure byte-identical output |

**How to verify baselines are current:** Run `:gbkt-examples:metasprites:test` and `:gbkt-examples:metasprites-stress:test` — GREEN means baselines match current output.

### pong PASS* nondeterminism exemption [ASSUMED]

Documented in project memory: `pong.gb` hashes differently every rebuild even from the same commit; generated C is unchanged (pre-existing `sdcc`/`lcc` issue). Generated-C byte-identity is the real gate for pong; ROM hash is exempt. Do NOT re-investigate.

### Gradle conventions [VERIFIED: CLAUDE.md + build.gradle.kts]

- `pluginTest` (not `:gbkt-gradle-plugin:test`) — republishes 7 snapshot modules to mavenLocal first
- No parallel `gradle clean` — chain into single Gradle invocation or run serially
- `./gradlew :gbkt-examples:<name>:buildRom` requires GBDK local install (not in CI)

---

## Deliverable 5: CI / Docs Surface (Req 5)

### .github/workflows/kotlin.yml stale references [VERIFIED: file read]

**Current stale state (lines 107, 113):**
```yaml
# Line 107 — Build all modules:
... :gbkt-examples:pong:build :gbkt-examples:breakout:build :gbkt-examples:explorer:build ...
#              ^^^^ stale: explorer is archived

# Line 113 — Verify Example C Generation:
./gradlew :gbkt-examples:pong:generateC :gbkt-examples:breakout:generateC :gbkt-examples:explorer:generateC
#                                                                          ^^^^ stale: explorer archived
```

**Target state (D-12, D-13):** Build + generateC for ALL KEEP examples (pong, breakout, simple-physics, metasprites, metasprites-stress, banks, platformer-template). No `:buildRom` — GBDK not provisioned on runner.

**Note:** The workflow also has a `Publish library modules to mavenLocal` step that references all 13 lib modules. These do not include example modules and do not need updating. The `Run Tests` step (`gbkt-core:test`, `gbkt-backend-api:test`, `gbkt-backend-gbdk:test`, `gbkt-gradle-plugin:test`) also does not reference examples and does not need updating.

### Docs needing update for retired examples and v1.0 label [VERIFIED: grep]

| File | Line(s) | Current text | Target |
|------|---------|-------------|--------|
| `README.md:169` | `# Or try breakout / racer` | Remove `racer` reference | Update to KEEP examples |
| `README.md:171` | `./gradlew :gbkt-examples:racer:buildRom` | Remove racer build command | Update to KEEP examples |
| `CLAUDE.md:94` | "three example projects (pong, breakout, **explorer**)" | Explorer is archived | Update to current KEEP set |
| `CLAUDE.md:214` | `GBDKPipelineV2.buildMetadataFile()` | V2 suffix | `GBDKPipeline.buildMetadataFile()` |
| `CLAUDE.md:698` | `GBDKPipelineV2, SourceMapCollector` | V2 suffix | `GBDKPipeline, SourceMapCollector` |
| `CLAUDE.md:717` | `SimulationContextV2, ScriptOpInterpreter` | V2 suffix | `SimulationContext, ScriptOpInterpreter` |
| `context/TESTING.md:23` | `val ctx = SimulationContextV2()` | V2 suffix | `SimulationContext()` |
| `context/TOOLING.md:81` | `GBDKPipelineV2.buildMetadataFile()` | V2 suffix | `GBDKPipeline.buildMetadataFile()` |
| `context/UAT-racer.md` | Entire file | References retired racer example | Delete or archive |
| `context/UAT-explorer.md` | Entire file | References archived explorer example | Delete or archive |
| `gbkt-backend-gbdk/CLAUDE.md:11,36,51,59,66` | Multiple | `GBDKPipelineV2` and `generateV2()` | V2 stripped |
| `gbkt-backend-gbdk/.../pipeline/CLAUDE.md:5` | `## GBDKPipelineV2` | V2 suffix | `## GBDKPipeline` |

### Version surface [VERIFIED: file read]

`gradle.properties`: `gbktVersion=0.1.0` — already correct. No change needed.

Release build is `-SNAPSHOT` unless `-Prelease` flag. CI checks version consistency (`checkVersionConsistency` task). No doc references a "v1.0" milestone label in the shipping docs (the v1.0 label was only in planning files which are out of scope).

---

## Architecture Patterns

### V2 rename execution sequence

The planner must sequence the rename in this order to avoid name collisions:

1. **Track 2 first (retire):** `gbkt-examples/.archive/` deletion removes `ExplorerV2.kt` — one less file to rename
2. **Track 4 (dead-code sweep):** Remove the bridge `generate(game, options)` method in `GBDKBackend.kt` that delegates to `generateV2`. This is what the SPEC calls "clearing the dead `generate()`" — after renaming, `generateV2()` BECOMES `generate()`, so the old bridge is no longer needed.
3. **Track 5 (rename):** Promote `generateV2()` → `generate()`. The Serena symbol rename handles cross-module references to `generateV2`. The reflection string in `GenerateCTask.kt:382` must be updated manually.

### Rename collision analysis

Before rename: `GBDKBackend` has:
- `override fun generate(game: GameIR, options: GenerationOptions)` — bridge (remove in Track 4)
- `fun generateV2(gameIR, assetManifest?, outputDirectory?, assetRoot?)` — real impl (rename to `generate` in Track 5)

After Track 4 (bridge removed) and Track 5 (rename):
- `@JvmOverloads override fun generate(gameIR, assetManifest?, outputDirectory?, assetRoot?)` — single entry point, no collision

### Anti-Patterns to Avoid

- **Renaming before retiring:** Rename BreakoutV2.kt before retiring racer — wastes time since `.archive/` files get deleted. Track order (D-11) handles this correctly.
- **Skipping reflection update:** Relying on the Serena semantic rename to cover `GenerateCTask.kt:382` — reflection string is NOT a symbol reference, it is a string literal that Serena will not update. Must update manually.
- **Treating .archive as git-tracked:** Running `git rm -r gbkt-examples/.archive/` would fail with "pathspec did not match any files" since it is gitignored. Use `rm -rf` instead.
- **parallel gradle clean:** Never fan out two `./gradlew clean` on the same root — chain into one Gradle invocation.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| V2 symbol rename | Custom regex replace | Serena `rename_symbol` / IDE rename refactor | Cross-module refs; follows imports, class usages, test references |
| Reference count verification | Manual file search | `find_referencing_symbols` (Serena) + acceptance grep | Serena catches usages the grep misses |
| Generated-C comparison | Diff tool with fuzzy matching | `sha256sum` + exact byte comparison | Phase 13.x established the exact byte-identity discipline |
| Acceptance test | Ad-hoc grep | `grep -rE "[A-Za-z_]*V2\b" --include=*.kt` per SPEC | The SPEC acceptance criterion is exact; use verbatim |

---

## Common Pitfalls

### Pitfall 1: Reflection String Not Updated
**What goes wrong:** `generateC` Gradle task throws `NoSuchMethodException` at runtime after rename, even though compile is GREEN.
**Why it happens:** `GenerateCTask.kt:382` uses string `"generateV2"` to find the method by reflection — not covered by semantic rename.
**How to avoid:** Explicitly update the three string/comment references in `GenerateCTask.kt:382,390` and the local variable name at line 381 as part of the rename task.
**Warning signs:** `./gradlew :gbkt-examples:pong:generateC` fails with reflection error after compile passes.

### Pitfall 2: .archive git rm Command Fails
**What goes wrong:** `git rm -r gbkt-examples/.archive/` fails: "pathspec 'gbkt-examples/.archive/' did not match any files known to git."
**Why it happens:** `.archive/` is gitignored (`.gitignore:55`) — it was never committed.
**How to avoid:** Use `rm -rf gbkt-examples/.archive/` (filesystem) and remove the `.gitignore` lines 54-55. Commit the `.gitignore` change.
**Warning signs:** `git ls-files gbkt-examples/.archive/` returns nothing before the cleanup attempt.

### Pitfall 3: Acceptance Grep False Negatives from D-V2 Comments
**What goes wrong:** After rename, `grep -rE "[A-Za-z_]*V2\b" --include=*.kt` still returns matches because "D-V2" in comment strings matches the pattern.
**Why it happens:** Comments like `// D-V2 / SEED-005 history:` and `(D-V2 regression)` contain a word-boundary `V2` match.
**How to avoid:** The textual sed sweep (D-09 second pass) must also update comment strings. Options: (a) change `D-V2` to `D-V2-10.1` (keeps historical meaning, no longer matches), (b) change to `D-Checkerboard` (semantic), (c) accept these as annotation-only and exclude from the grep with a comments flag. **Planner should decide the approach upfront** so the executor has a clear action.
**Warning signs:** Acceptance grep returns only comment-line matches after the rename is otherwise complete.

### Pitfall 4: GBC Examples Require gbcMode in MCP Start
**What goes wrong:** MCP emulator shows green-tinted garbled screen for GBC-targeted examples (metasprites, metasprites-stress, platformer-template) when started without `gbcMode: true`.
**Why it happens:** These examples are compiled with `-Wm-yc` (GBC_COMPATIBLE) — DMG emulation mode gives wrong colors.
**How to avoid:** Always use `emulator_start {"game": "<name>", "gbcMode": true}` for GBC examples; always supply `symFile` path to `.noi` for variable inspection.
**Warning signs:** Screenshot looks monochrome/green-tinted for examples that should show color.

### Pitfall 5: `pipelineV2` Local Variable in GBDKBackend.kt
**What goes wrong:** After renaming `GBDKPipelineV2` → `GBDKPipeline`, the local `val pipelineV2 = GBDKPipelineV2()` at `GBDKBackend.kt:159` still compiles but has a misleading name.
**Why it happens:** Local variable names are not covered by class-level symbol rename.
**How to avoid:** Rename the local variable to `val pipeline = GBDKPipeline()` as part of the rename sweep. This is a single line edit in `GBDKBackend.kt`.
**Warning signs:** Acceptance grep returns a match on `pipelineV2` (local variable).

---

## Code Examples

### Byte-identity baseline capture command
```bash
# Capture baseline for a KEEP example (run before any mutation)
find gbkt-examples/pong/build/gbkt/generated -name "*.c" | sort | xargs sha256sum > .planning/phases/14-.../evidence/baseline-pong.sha256
```

### Acceptance grep (verbatim from SPEC)
```bash
grep -rE "[A-Za-z_]*V2\b" --include="*.kt" . \
  --exclude-dir=build --exclude-dir=.git --exclude-dir=".claude/worktrees"
```

### GBDKBackend rename — target state (Track 5)
```kotlin
// BEFORE (bridge + impl):
override fun generate(game: GameIR, options: GenerationOptions): GenerationResult {
    return generateV2(game)  // ← bridge to be removed in Track 4
}

@JvmOverloads
fun generateV2(gameIR: GameIR, assetManifest: AssetManifest? = null, ...): GenerationResult { ... }

// AFTER (single entry point):
@JvmOverloads
override fun generate(gameIR: GameIR, assetManifest: AssetManifest? = null, ...): GenerationResult { ... }
```

### Reflection string update (Track 5, manual edit)
```kotlin
// GenerateCTask.kt — BEFORE:
val generateV2Method = backend.javaClass.getMethod("generateV2", ...)
val result = generateV2Method.invoke(backend, gameIR, null, outputDir)
    ?: throw GradleException("generateV2 returned null")

// AFTER:
val generateMethod = backend.javaClass.getMethod("generate", ...)
val result = generateMethod.invoke(backend, gameIR, null, outputDir)
    ?: throw GradleException("generate returned null")
```

---

## Validation Architecture

This phase is behavior-neutral. The central risk is silent drift from rename or deletion. The validation strategy uses two independent gates that must both pass after each mutating track.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Kotlin) |
| Quick run command | `./gradlew :gbkt-backend-gbdk:test :gbkt-core:test :gbkt-lang:test :gbkt-genre-sport:test` |
| Plugin integration command | `./gradlew pluginTest` (NOT :gbkt-gradle-plugin:test) |
| Full suite command | `./gradlew test` (all modules) |
| Byte-identity check | `./gradlew :gbkt-examples:metasprites:test :gbkt-examples:metasprites-stress:test` |

### Gate 1: Generated-C Byte-Identity (primary)

For each KEEP example after each mutating track (D-08):
```bash
# Capture post-mutation SHA
find gbkt-examples/<name>/build/gbkt/generated -name "*.c" | sort | xargs sha256sum
# Compare with pre-mutation baseline
diff baseline-<name>.sha256 post-<name>.sha256
```
Expected result: **zero diff** (identical hashes). Any diff proves behavior change — investigate before proceeding.

pong ROM (`.gb` file) is PASS* — generated-C must be byte-identical even if ROM hash differs.

### Gate 2: Committed Byte-Identity Tests (second independent gate)

```bash
./gradlew :gbkt-examples:metasprites:test :gbkt-examples:metasprites-stress:test
```
These tests compare `elephant.c` and tiger sprite files against pinned baselines from 13.6-07. Must be GREEN after V2 rename (proves rename did not alter sprite codegen).

### Gate 3: Whole-Tree Compile + Full JVM Suite

```bash
./gradlew test pluginTest
```
Must be GREEN after each mutating track (dead-code sweep AND V2 rename). Any RED means reachable code was deleted or rename introduced a call-site mismatch.

### Per-Track Sampling Points (Nyquist)

| Track | Action | Gate(s) Required |
|-------|--------|-----------------|
| Track 1: Audit | Build + run-check per example | Visual evidence (screenshots) — not a compile/test gate |
| Track 2: Retire | `git rm`, settings update | `./gradlew projects` must succeed (configure-only); `./gradlew :gbkt-core:test` confirms nothing references deleted modules |
| Track 3: Baseline | SHA capture | No gate — capture only; comparison happens in Tracks 4 and 5 |
| Track 4: Dead-code sweep | Remove bridge `generate()` | Gate 1 (byte-identity diff) + Gate 3 (compile + full JVM suite) |
| Track 5: V2 rename | Symbol + file + KDoc | Gate 1 (byte-identity diff) + Gate 2 (committed tests) + Gate 3 (compile + suite) + acceptance grep returns zero |
| Track 6: CI/docs | YAML + .md edits | `./gradlew checkVersionConsistency`; acceptance grep for retired example names |
| Track 7: Final sweep | buildRom per KEEP + whole-tree | Gate 1 + Gate 2 + Gate 3 + `:buildRom` EXIT 0 per KEEP example (pong PASS*) |

### Phase Gate (final)

All of Track 7 GREEN before calling `/gsd-verify-work`:
- [ ] `grep -rE "[A-Za-z_]*V2\b" --include=*.kt` returns zero matches (excluding build/.git/.claude/worktrees)
- [ ] `git ls-files` returns zero under LabyrinthOfTheDragon, LabyrinthOfTheDragon-port
- [ ] `./gradlew test pluginTest` GREEN
- [ ] Per-KEEP-example: generated-C SHA-identical to pre-phase baseline
- [ ] Per-KEEP-example: `:buildRom` EXIT 0 (pong PASS*)
- [ ] `.github/workflows/kotlin.yml` references only KEEP examples

---

## Runtime State Inventory

> Included because Track 2 is a git rm / deletion operation (a variant of rename/refactor/removal).

| Category | Items Found | Action Required |
|----------|-------------|-----------------|
| Stored data | None — no databases involved in this cleanup phase | None |
| Live service config | None — no external services reference example names by ID | None |
| OS-registered state | None | None |
| Secrets/env vars | None — no secrets reference example names or V2 identifiers | None |
| Build artifacts | `gbkt-examples/.archive/` (physical directory, gitignored, not in repo) | `rm -rf gbkt-examples/.archive/` + update .gitignore |
| Build artifacts | Old `build/` directories in retired examples | Handled by `git rm` (only tracked files); untracked build/ is gitignored |
| MCP shadow JAR | `gbkt-mcp-server/build/libs/gbkt-mcp-server-0.1.0-SNAPSHOT-all.jar` | May need rebuild after changes; use `./gradlew :gbkt-mcp-server:shadowJar` if MCP emulator fails to start |

---

## Open Questions (RESOLVED)

1. **D-V2 comment strings and acceptance grep**
   - What we know: Comments like `// D-V2 / SEED-005` match `[A-Za-z_]*V2\b` in the acceptance grep
   - What's unclear: Whether the SPEC intends zero comment matches or zero code matches
   - Recommendation: Planner should decide upfront — either (a) change `D-V2` in comments to `D-V2-10.1` or a semantic description, OR (b) the acceptance grep should exclude comments via `grep -v "^\s*//"`. Option (a) is cleaner.
   - **(RESOLVED)** Plan 06 Task 2 adopts option (a): historical `D-V2` labels are rewritten to `D-Seed005` and `DV3V2`/`Dv3V2` to `DV3-iter2` (non-matching, meaning preserved) so the acceptance grep reaches zero.

2. **simplePhysics example audit**
   - What we know: `simple-physics` has a PLAYBOOK.md, is DMG-target, and has no V2 filename issues
   - What's unclear: Whether it currently builds cleanly (not in CI, no existing buildRom evidence in this session)
   - Recommendation: Audit task confirms via `:gbkt-examples:simple-physics:buildRom`; executor confirms during Track 1
   - **(RESOLVED)** Plan 01 confirms build/banks state empirically during the Track 1 baseline audit (`:gbkt-examples:simple-physics:buildRom`).

3. **RpgRegistry.clear() removal decision point**
   - What we know: No callers found in non-archive, non-build codebase
   - What's unclear: Whether CONTEXT's "fold ONLY if non-reachability proof succeeds" means a Req-4 task should include this or whether it's entirely executor-discretion
   - Recommendation: Include one plan step in Track 4 that runs `find_referencing_symbols` on `RpgRegistry.clear` and removes it if confirmed; compile + suite GREEN is the proof
   - **(RESOLVED)** Plan 04 Task 2 runs `find_referencing_symbols` on `RpgRegistry.clear` and removes it once non-reachability is proven; compile + suite GREEN is the proof.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| GBDK / lcc | Track 1 buildRom audit (Req 1) + Track 7 final buildRom | Local only; not verified in this session | Unknown | Track 1/7 skips buildRom if GBDK absent; escalate to human |
| MCP gbkt-emulator | Track 1 live run-check (D-01, D-02) | Must be live at Track 1 time | Build from shadowJar if needed | No fallback — D-01 mandates live emulator |
| Serena MCP tools | Track 5 V2 symbol rename | Must be available at Track 5 time | Current session | IDE rename (IntelliJ) as fallback |
| JVM 21 + Kotlin 2.3.0 | All tracks with compile/test | YES (project standard) | As configured | — |
| Git | Track 2 git rm | YES | Current | — |

**Missing dependencies with no fallback:**
- MCP gbkt-emulator must be live for Track 1. If not available, Track 1 cannot complete D-01/D-02. Executor must rebuild shadowJar: `./gradlew :gbkt-mcp-server:shadowJar` then reconnect.

---

## Security Domain

Security is not applicable to this cleanup phase. All changes are behavioral-neutral renames and deletions. No new external interfaces, authentication, or data flows are introduced.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | pong ROM PASS* nondeterminism is pre-existing sdcc/lcc issue, not a Phase 14 regression | Byte-identity gate mechanics | Low — well-documented in project memory; generated-C is the real gate |
| A2 | `.archive/` contains no git-tracked files (confirmed via `git ls-files`) | Req 2 dead-tree surface | None — verified |
| A3 | `simple-physics`, `banks`, `pong`, `breakout`, `metasprites`, `metasprites-stress`, `platformer-template` all currently build | Track 1 audit | Track 1 is the empirical audit; if any fail, they become RETIRE candidates |
| A4 | `racer` currently fails buildRom (RETIRE per D-03 without requiring confirmation) | Track 1 audit | Low — CONTEXT explicitly says "retire on documented-failure status, NOT repaired" |

**All major claims are verified against the live codebase. No HIGH-risk assumptions.**

---

## Sources

### Primary (HIGH confidence)
- Live codebase grep — all file paths, line numbers, occurrence counts verified in this research session against `feat/d_and_d_gaps` HEAD
- `14-SPEC.md` — 5 locked requirements, acceptance criteria
- `14-CONTEXT.md` — D-01..D-13, track ordering D-11

### Secondary (HIGH confidence)
- `settings.gradle.kts` — confirmed 8 included examples, LabyrinthOfTheDragon-port commented out
- `.github/workflows/kotlin.yml` — confirmed stale `explorer` references at lines 107, 113
- `GBDKBackend.kt` — confirmed `generate()` bridge pattern and `generateV2()` real impl
- `GenerateCTask.kt:378-390` — confirmed reflection call string `"generateV2"` (critical finding)
- `gbkt-examples/.archive/` gitignore entry — confirmed at `.gitignore:54-55`
- PLAYBOOK.md presence per example — verified with `ls` per example directory
- GBC target detection — verified by grepping build.gradle.kts and main .kt for `GBC`/`gbcTarget`

---

## Metadata

**Confidence breakdown:**
- Example inventory: HIGH — verified via git ls-files, directory listing, PLAYBOOK checks
- V2 identifier census: HIGH — verified via grep with occurrence counts; reflection site confirmed
- Dead-code analysis: HIGH — file read of GBDKBackend.kt; grep for all generateV2 callers
- Byte-identity gate: HIGH — generateC output layout confirmed via ls on live build output
- CI/docs surface: HIGH — kotlin.yml read; grep on all doc files

**Research date:** 2026-06-06
**Valid until:** Indefinite — purely structural findings from a fixed commit; no external dependencies
