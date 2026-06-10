---
phase: 11-port-banks-gbdk-example-to-gbkt
plan: 01
type: execute
wave: 0
depends_on: []
files_modified:
  - gbkt-examples/banks/build.gradle.kts
  - settings.gradle.kts
  - gbkt-examples/banks/src/main/kotlin/io/github/gbkt/examples/banks/Banks.kt
  - gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksIRTest.kt
  - gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt
  - gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt
autonomous: true
requirements:
  - BANK-W0-SCAFFOLD   # implements VALIDATION.md "Wave 0 Requirements"
user_setup: []
must_haves:
  truths:
    - "`./gradlew :gbkt-examples:banks:compileTestKotlin` exits 0"
    - "The 5 Wave-0 source files all exist and compile against stub references"
    - "`settings.gradle.kts` includes `gbkt-examples:banks`"
  artifacts:
    - path: "gbkt-examples/banks/build.gradle.kts"
      provides: "Gradle subproject configuration with `gbkt { ramBanks.set(2) }`"
      contains: "ramBanks.set(2)"
    - path: "gbkt-examples/banks/src/main/kotlin/io/github/gbkt/examples/banks/Banks.kt"
      provides: "Placeholder `val banks = game(\"Banks\") { }` so test files reference a real symbol"
      contains: "val banks = game"
    - path: "gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksIRTest.kt"
      provides: "Empty IR test class; Plan 11-06 fills in test bodies"
      contains: "class BanksIRTest"
    - path: "gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt"
      provides: "Empty emission test class; Plans 11-07/11-08 fill in INV-1..4"
      contains: "class BanksEmissionTest"
    - path: "gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt"
      provides: "Empty UAT test class with `Assumptions.assumeTrue(ROM_FILE.exists())` skip guard; Plans 11-11/11-12 fill in anchor tests"
      contains: "class BanksUatTest"
    - path: "settings.gradle.kts"
      provides: "Includes `gbkt-examples:banks` subproject"
      contains: "include(\"gbkt-examples:banks\")"
  key_links:
    - from: "settings.gradle.kts"
      to: "gbkt-examples/banks/build.gradle.kts"
      via: "Gradle include directive"
      pattern: "include\\(\"gbkt-examples:banks\"\\)"
    - from: "gbkt-examples/banks/build.gradle.kts"
      to: "io.github.gbkt Gradle plugin (GbktExtension.ramBanks)"
      via: "gbkt { ramBanks.set(2) }"
      pattern: "ramBanks\\.set\\(2\\)"
---

<objective>
Wave-0 scaffolding: create the `gbkt-examples/banks/` Gradle subproject skeleton with stub source + test files so subsequent plans have compiling references. NO behavioral DSL yet — Plan 11-05 fills `Banks.kt`. NO test bodies yet — Plans 11-06..08 + 11-11..13 fill them.

Purpose: Make Wave 0 of VALIDATION.md GREEN. Every later plan's `<verify>` block references `:gbkt-examples:banks:test` (or `compileTestKotlin`) — those commands must run, which means the subproject must exist before Wave 1.

Output: Compiling-but-empty `gbkt-examples/banks/` module + `settings.gradle.kts` entry.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-CONTEXT.md
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-RESEARCH.md
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-VALIDATION.md
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-PATTERNS.md
@gbkt-examples/simple-physics/build.gradle.kts
@gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsUatTest.kt
@settings.gradle.kts
@gbkt-examples/CLAUDE.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: Create build.gradle.kts and add settings include</name>
  <read_first>
    - gbkt-examples/simple-physics/build.gradle.kts (verbatim analog per 11-PATTERNS.md §gbkt-examples/banks/build.gradle.kts)
    - settings.gradle.kts lines 56–67 (existing include block; insertion point line 68)
    - 11-PATTERNS.md §gbkt-examples/banks/build.gradle.kts (lines 28–80) — critical-note re: ramBanks.set(2) two-channel wiring
    - 11-RESEARCH.md §Pitfall 1 (ramBanks two-channel problem) — explains WHY ramBanks.set(2) is mandatory
    - 11-CONTEXT.md D-07 (cartridge config locked)
  </read_first>
  <files>
    - gbkt-examples/banks/build.gradle.kts (CREATE)
    - settings.gradle.kts (MODIFY)
  </files>
  <action>
    1. Create `gbkt-examples/banks/build.gradle.kts` by copying `gbkt-examples/simple-physics/build.gradle.kts` verbatim and changing:
       - Top KDoc comment to: `Banks - GBDK banks reference port`, with body line `Demonstrates: multi-bank ROM (MBC5_RAM_BATTERY), BANKED calling convention, cross-bank scene navigation, SRAM persistence via SaveDataBuilder.`
       - `gbkt { ... }` block contents to exactly four lines:
         - `game("io.github.gbkt.examples.banks.BanksKt::banks")`
         - `assets("res")`
         - `outputName.set("banks")`
         - `ramBanks.set(2)` (per RESEARCH §Pitfall 1 — mandatory; lcc does NOT receive `-Wl-ya2` without it)
    2. Modify `settings.gradle.kts`: append `include("gbkt-examples:banks")` on a new line directly after the existing line `include("gbkt-examples:metasprites-stress")` (currently line 67). Preserve all other lines unchanged.
    3. Do NOT add genre package deps — `simple-physics/build.gradle.kts` already has none; Banks.kt uses only `io.github.gbkt.core.dsl.*`.
  </action>
  <verify>
    <automated>./gradlew :gbkt-examples:banks:tasks --quiet 2>&1 | grep -q "buildRom\|generateC"</automated>
  </verify>
  <acceptance_criteria>
    - File `gbkt-examples/banks/build.gradle.kts` exists and contains the literal string `ramBanks.set(2)`
    - File `gbkt-examples/banks/build.gradle.kts` contains `game("io.github.gbkt.examples.banks.BanksKt::banks")`
    - File `settings.gradle.kts` contains a new line `include("gbkt-examples:banks")`
    - `./gradlew :gbkt-examples:banks:tasks --quiet` exits 0 AND its stdout names the `buildRom` task (proves plugin applied)
  </acceptance_criteria>
  <done>Gradle recognizes `:gbkt-examples:banks` as a project with the gbkt plugin applied.</done>
</task>

<task type="auto">
  <name>Task 2: Create placeholder Banks.kt</name>
  <read_first>
    - gbkt-examples/simple-physics/src/main/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysics.kt (header + package + imports pattern)
    - 11-PATTERNS.md §"MPL 2.0 File Header" (license header to copy)
    - 11-PATTERNS.md §"gbkt-examples/banks/src/main/kotlin/io/github/gbkt/examples/banks/Banks.kt" (full DSL skeleton for Plan 11-05; this task only writes the stub)
  </read_first>
  <files>gbkt-examples/banks/src/main/kotlin/io/github/gbkt/examples/banks/Banks.kt</files>
  <action>
    Create a minimal placeholder file so the test files in Tasks 3–5 have a `banks` symbol to reference. Plan 11-05 will overwrite the body of the `game { }` block with the full DSL.

    File contents:
    1. MPL 2.0 header (copy verbatim from 11-PATTERNS.md §"MPL 2.0 File Header" — six lines starting with `/* This Source Code Form is subject to ...`).
    2. Blank line.
    3. `package io.github.gbkt.examples.banks`
    4. Blank line.
    5. `import io.github.gbkt.core.dsl.*`
    6. Blank line.
    7. `val banks = game("Banks") { start = "title"; scene("title") { } }` (one-liner so the file compiles; Plan 11-05 replaces the body).

    Do NOT add any other DSL surface in this task. Do NOT add `config { }` (Plan 11-05 owns that, with the locked `cartridge = "MBC5_RAM_BATTERY"; romBanks = 4; ramBanks = 2`). This file is intentionally a one-line stub.
  </action>
  <verify>
    <automated>./gradlew :gbkt-examples:banks:compileKotlin --quiet</automated>
  </verify>
  <acceptance_criteria>
    - File `gbkt-examples/banks/src/main/kotlin/io/github/gbkt/examples/banks/Banks.kt` exists
    - File contains the exact tokens `package io.github.gbkt.examples.banks`, `val banks = game`, and `scene("title")`
    - File starts with `/* This Source Code Form is subject to the terms of the Mozilla Public`
    - `./gradlew :gbkt-examples:banks:compileKotlin --quiet` exits 0
  </acceptance_criteria>
  <done>`banks` symbol is exported from the package and resolvable by test files.</done>
</task>

<task type="auto">
  <name>Task 3: Create empty test class stubs (IR/Emission/UAT)</name>
  <read_first>
    - gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsIRTest.kt (analog header for BanksIRTest)
    - gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsEmissionTest.kt lines 50–80 (companion object pattern for EVIDENCE_DIR; verbatim header to copy)
    - gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsUatTest.kt lines 1–67 (companion object with ROM_FILE/METADATA_FILE + newAgent() skip-guard pattern)
    - 11-PATTERNS.md §"BanksUatTest.kt" (covers `newAgent()` shape + `Assumptions.assumeTrue` skip guard)
    - 11-VALIDATION.md §"Wave 0 Requirements" — the three test files are listed there
  </read_first>
  <files>
    - gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksIRTest.kt
    - gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt
    - gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt
  </files>
  <action>
    Create three test files. Each file: MPL 2.0 header, `package io.github.gbkt.examples.banks`, imports, empty class body. NO @Test methods (Plans 11-06/07/08/11/12 add them).

    **BanksIRTest.kt** — copy header + package + imports from `SimplePhysicsIRTest.kt`, class body is:
    ```
    class BanksIRTest {
        private val ir = banks.build()
    }
    ```
    Imports: `import kotlin.test.Test`, `import kotlin.test.assertEquals`, `import kotlin.test.assertTrue`, `import io.github.gbkt.core.ir.SaveSystem`, `import io.github.gbkt.core.ir.VarType`.

    **BanksEmissionTest.kt** — copy `extractFunctionBody()` private method verbatim from `SimplePhysicsEmissionTest.kt` lines 82–102 (this helper is shared by all 4 invariant tests in Plans 11-07/08). Companion object: `EVIDENCE_DIR = File(System.getProperty("user.dir")).resolve("../../.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/tier1-shape").normalize()`. Imports: `kotlin.test.*`, `io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipelineV2`, `java.io.File`.

    **BanksUatTest.kt** — copy `newAgent()` private method verbatim from `SimplePhysicsUatTest.kt` lines 55–67 (skip guard pattern). Companion object holds `ROM_FILE = File("build/gbkt/output/banks.gb")`, `METADATA_FILE = File("build/gbkt/generated/game_metadata.json")`, `EVIDENCE_DIR = File("../../.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots").normalize()`. Imports: `kotlin.test.*`, `org.junit.jupiter.api.Assumptions`, `io.github.gbkt.emulator.agent.StepAgent`, `io.github.gbkt.emulator.agent.AgentSessionConfig`, `io.github.gbkt.emulator.metadata.GameMetadata`, `java.io.File`.

    Do NOT add any `@Test` methods in this task. The file body is just companion object + private helper(s).
  </action>
  <verify>
    <automated>./gradlew :gbkt-examples:banks:compileTestKotlin --quiet</automated>
  </verify>
  <acceptance_criteria>
    - All three files exist at the paths in `<files>`
    - `BanksEmissionTest.kt` contains the literal string `private fun extractFunctionBody(` (the brace-walk helper from 11-PATTERNS.md §"Scope-level brace-walk")
    - `BanksUatTest.kt` contains the literal string `Assumptions.assumeTrue(` (skip guard for missing ROM)
    - `./gradlew :gbkt-examples:banks:compileTestKotlin --quiet` exits 0 (Wave 0 success criterion from VALIDATION.md)
  </acceptance_criteria>
  <done>All three test files compile against the placeholder `banks` symbol; no @Test methods yet.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Gradle build → host filesystem | Build tasks read/write under `build/` and `.planning/phases/11-.../evidence/`; no untrusted input |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-11-01 | Information disclosure | EVIDENCE_DIR path resolution | accept | No PII; path scoped under `.planning/phases/11-...` |
| T-11-02 | Tampering | settings.gradle.kts edit | mitigate | Only append a single literal include line; no template expansion or shell variables |
| T-11-SC | Tampering | npm/pip/cargo installs | n/a | No package installs in this plan (Gradle/Kotlin-only; no transitive `[ASSUMED]`/`[SUS]` additions) |
</threat_model>

<verification>
  - `./gradlew :gbkt-examples:banks:compileTestKotlin --quiet` exits 0 (VALIDATION.md Wave-0 sign-off row).
  - `./gradlew projects --quiet | grep -c "gbkt-examples:banks"` ≥ 1 (settings include applied).
  - No other example projects regressed (e.g. `./gradlew :gbkt-examples:simple-physics:compileKotlin --quiet` still exits 0).
</verification>

<success_criteria>
  - All five Wave-0 source files exist.
  - `:gbkt-examples:banks:compileTestKotlin` GREEN.
  - `settings.gradle.kts` lists `gbkt-examples:banks` exactly once.
</success_criteria>

<output>
Create `.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-01-SUMMARY.md` when done. Summary lists: files created (5), files modified (1), Gradle smoke-command output (1 line).
</output>
