---
phase: 11-port-banks-gbdk-example-to-gbkt
plan: 08
type: execute
wave: 2
depends_on: ["11-05"]
files_modified:
  - gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt
autonomous: true
requirements:
  - BANK-INV-3   # JVM-tier invariant 3: gbkt-build.properties carries mbcType=0x1B — CONTEXT D-12(3)
  - BANK-INV-4   # JVM-tier invariant 4: save_game_saves emits ENABLE_RAM + sram write + DISABLE_RAM — CONTEXT D-12(4)
user_setup: []
must_haves:
  truths:
    - "INV-3: `gbkt-build.properties` in pipeline output contains `mbcType=0x1B`"
    - "INV-4: `save_game_saves` in `main.c` contains `ENABLE_RAM;`, `sram[`, `DISABLE_RAM;` in that order"
    - "INV-4 expects RED for `trigger_saves` BEFORE Plan 11-10 fix (used as a sentinel — see Task 2 note)"
  artifacts:
    - path: "gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt"
      provides: "Adds INV-3 and INV-4 @Test methods (INV-3 GREEN now; INV-4 GREEN now for the ENABLE/DISABLE_RAM parts)"
      contains: "fun \\`INV-3"
  key_links:
    - from: "BanksEmissionTest INV-3"
      to: "GenerateCTask.writeBuildMetadata → gbkt-build.properties mbcType key"
      via: "output.files[\"gbkt-build.properties\"] string match"
      pattern: "mbcType=0x1B"
    - from: "BanksEmissionTest INV-4"
      to: "GBDKSystemVisitor.visitSaveSystem → ENABLE_RAM + sram[ + DISABLE_RAM"
      via: "extractFunctionBody(mainC, \"save_game_saves\")"
      pattern: "ENABLE_RAM.*sram\\[.*DISABLE_RAM"
---

<objective>
Complete the 4 JVM-tier emission invariants by adding INV-3 (cartridge propagation) and INV-4 (SRAM write path) to `BanksEmissionTest.kt`.

Purpose: INV-3 verifies the `cartridge = "MBC5_RAM_BATTERY"` DSL choice from Plan 11-05 propagated through `GenerateCTask.writeBuildMetadata()` (RESEARCH §"Cartridge-Byte Emission"). INV-4 verifies SaveDataBuilder's HOME-bank SRAM write path (RESEARCH §"SaveDataBuilder SRAM Path") — the SRAM emission itself is unconditionally generated; the GAP is the missing `trigger_saves` trampoline, which Plan 11-09 names and Plan 11-10 fixes.

Output: 2 GREEN `@Test` methods. After Plan 11-08, BanksEmissionTest has 4 total `@Test` methods (INV-1..4), matching CONTEXT D-12.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-CONTEXT.md
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-RESEARCH.md
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-PATTERNS.md
@gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsEmissionTest.kt
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Add INV-3 (gbkt-build.properties mbcType propagation)</name>
  <files>gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt</files>
  <read_first>
    - 11-RESEARCH.md §"Cartridge-Byte Emission Status" (lines 343–375 — `"MBC5_RAM_BATTERY"` → `mbcType=0x1B`)
    - 11-RESEARCH.md §"JVM-Tier Brace-Walk Pattern Reference" lines 445–446 (INV-3 specification)
    - 11-RESEARCH.md §"ramBanks two-channel wiring" (lines 204–214 — note that this test verifies cartridge propagation; the parallel ramBanks two-channel issue is fixed in build.gradle.kts via Plan 11-01, not here)
    - 11-PATTERNS.md §"BanksEmissionTest.kt" — invariant 3 placeholder
    - 11-CONTEXT.md D-07 (cartridge config locked)
  </read_first>
  <behavior>
    INV-3: For `banks.build()` piped through `GBDKPipelineV2().generate(...)`:
    - `output.files["gbkt-build.properties"]` exists and is non-null
    - The properties file content contains the literal line `mbcType=0x1B` (per RESEARCH §Cartridge-Byte Emission: `cartridge = "MBC5_RAM_BATTERY"` → CARTRIDGE_MBC_MAP lookup → `"0x1B"` → written to properties as `mbcType=0x1B`)
    - Evidence: write entire properties content to `EVIDENCE_DIR/inv3-build-properties.txt`
  </behavior>
  <action>
    Append a third `@Test` method to `BanksEmissionTest.kt`:

    ```kotlin
    @Test
    fun `INV-3 gbkt-build_properties carries mbcType 0x1B`() {
        val pipeline = GBDKPipelineV2()
        val output = pipeline.generate(banks.build())
        val props = output.files["gbkt-build.properties"]
            ?: error("gbkt-build.properties not generated")

        EVIDENCE_DIR.mkdirs()
        File(EVIDENCE_DIR, "inv3-build-properties.txt").writeText(props)

        assertTrue(
            props.contains("mbcType=0x1B"),
            "gbkt-build.properties must carry mbcType=0x1B (cartridge = \"MBC5_RAM_BATTERY\" per Banks.kt config; CARTRIDGE_MBC_MAP at GenerateCTask.kt:673)"
        )
    }
    ```

    Do NOT verify the actual ROM byte here — that is anchor 3's territory (Plan 11-13). INV-3 locks the upstream codegen surface (the properties file) only.

    Do NOT loosen the match to `Regex("mbcType=0x1[bB9]")` — the RESEARCH-cited expectation is exactly `0x1B` for `"MBC5_RAM_BATTERY"`. If the test fails because Banks.kt has `"MBC5"` instead, that's a Plan 11-05 regression; revert and re-run Plan 11-05 acceptance.
  </action>
  <verify>
    <automated>./gradlew :gbkt-examples:banks:test --tests "io.github.gbkt.examples.banks.BanksEmissionTest.INV-3*" --quiet</automated>
  </verify>
  <acceptance_criteria>
    - `./gradlew :gbkt-examples:banks:test --tests "io.github.gbkt.examples.banks.BanksEmissionTest.INV-3*"` exits 0
    - Evidence file `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/tier1-shape/inv3-build-properties.txt` exists AND contains the literal line `mbcType=0x1B`
  </acceptance_criteria>
  <done>INV-3 GREEN; cartridge string → mbcType propagation is locked at the codegen tier.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Add INV-4 (save_game_saves ENABLE_RAM + sram write + DISABLE_RAM)</name>
  <files>gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt</files>
  <read_first>
    - 11-RESEARCH.md §"SaveDataBuilder SRAM Path" (lines 154–189 — the generated shape: ENABLE_RAM; sram[i] = _var; DISABLE_RAM;)
    - 11-RESEARCH.md §"JVM-Tier Brace-Walk Pattern Reference" lines 448–452 (INV-4 specification — INCLUDING a note that `trigger_saves` is the FIFTH assertion fired only AFTER Plan 11-10 fix)
    - 11-RESEARCH.md §"DSL Call Surface Gap (Top-1 Bug Candidate)" — explains why `trigger_saves` does NOT exist before Plan 11-10
    - 11-PATTERNS.md §"BanksEmissionTest.kt" — invariant 4 placeholder
  </read_first>
  <behavior>
    INV-4 (Plan 11-08 GREEN state — `trigger_saves` ASSERTION DEFERRED to Plan 11-10):
    - `output.files["main.c"]` exists
    - `extractFunctionBody(mainC, "save_game_saves")` returns non-empty
    - Body contains `ENABLE_RAM;`, `sram[`, `DISABLE_RAM;` in that order (substring presence check; not strict regex)
    - Evidence: write body to `EVIDENCE_DIR/inv4-save-game-saves.txt`
    - INV-4 in Plan 11-08 does NOT assert `mainC.contains("trigger_saves")` — that assertion is added by Plan 11-10 RED→GREEN cycle (see Plan 11-10 Task 1)
  </behavior>
  <action>
    Append a fourth `@Test` method to `BanksEmissionTest.kt`:

    ```kotlin
    @Test
    fun `INV-4 save_game_saves in main_c emits ENABLE_RAM and DISABLE_RAM`() {
        val pipeline = GBDKPipelineV2()
        val output = pipeline.generate(banks.build())
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()
        val saveBody = extractFunctionBody(mainC, "save_game_saves")
        File(EVIDENCE_DIR, "inv4-save-game-saves.txt").writeText(saveBody)

        assertTrue(saveBody.isNotEmpty(),
            "save_game_saves must be emitted in main.c by GBDKSystemVisitor.visitSaveSystem")
        assertTrue(saveBody.contains("ENABLE_RAM;"),
            "save_game_saves must contain ENABLE_RAM; (SaveDataBuilder SRAM write contract)")
        assertTrue(saveBody.contains("sram["),
            "save_game_saves must write to sram[...] (slot offset arithmetic)")
        assertTrue(saveBody.contains("DISABLE_RAM;"),
            "save_game_saves must contain DISABLE_RAM; (SaveDataBuilder SRAM write contract)")

        // ORDER CHECK — ENABLE_RAM before sram[, sram[ before DISABLE_RAM
        val enableIdx = saveBody.indexOf("ENABLE_RAM;")
        val sramIdx = saveBody.indexOf("sram[")
        val disableIdx = saveBody.indexOf("DISABLE_RAM;")
        assertTrue(enableIdx < sramIdx, "ENABLE_RAM; must precede first sram[ write")
        assertTrue(sramIdx < disableIdx, "sram[ writes must precede DISABLE_RAM;")
    }
    ```

    Plan 11-10 will append ONE additional assertion to this test (RED→GREEN): `assertTrue(mainC.contains("trigger_saves"), "trigger_saves stub must be emitted post-fix")`. Plan 11-08 stops short of that — the named bug is named in Plan 11-09 (first build), not in this Wave 2 plan.

    Why this split: Plan 11-08 is Wave 2 (parallel with 11-06/07, depends on Plan 11-05 only). Wave 3 (Plan 11-09) is the buildRom smoke + bug-naming gate. Wave 4 (Plan 11-10) writes the RED test for trigger_saves THEN fixes the visitor. Encoding the `trigger_saves` assertion in Plan 11-08 would conflate Wave-2 oracle work with Wave-4 bug-fix work and break the wave dependency graph.
  </action>
  <verify>
    <automated>./gradlew :gbkt-examples:banks:test --tests "io.github.gbkt.examples.banks.BanksEmissionTest.INV-4*" --quiet</automated>
  </verify>
  <acceptance_criteria>
    - `./gradlew :gbkt-examples:banks:test --tests "io.github.gbkt.examples.banks.BanksEmissionTest.INV-4*"` exits 0
    - Evidence file `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/tier1-shape/inv4-save-game-saves.txt` exists AND contains the literal strings `ENABLE_RAM;`, `sram[`, and `DISABLE_RAM;`
    - The test file `BanksEmissionTest.kt` now has exactly 4 `@Test` annotations (one per INV-1..4)
    - The test does NOT assert `mainC.contains("trigger_saves")` — that assertion is added by Plan 11-10
    - All 4 invariant tests GREEN in the full suite: `./gradlew :gbkt-examples:banks:test --tests "io.github.gbkt.examples.banks.BanksEmissionTest" --quiet` exits 0
  </acceptance_criteria>
  <done>INV-4 GREEN for the existing SaveDataBuilder SRAM write path; the missing `trigger_saves` trampoline remains to be fixed in Plan 11-10.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Test → pipeline output | Read-only |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-11-13 | Tampering | mbcType propagation | mitigate | INV-3 catches any regression in GenerateCTask.writeBuildMetadata writing mbcType |
| T-11-14 | Repudiation | SRAM write ordering | mitigate | INV-4 enforces ENABLE_RAM → sram[ → DISABLE_RAM ordering, not just presence |
| T-11-SC | Tampering | npm/pip/cargo installs | n/a | No installs |
</threat_model>

<verification>
  - INV-3 + INV-4 GREEN.
  - Total 4 @Test annotations in BanksEmissionTest.kt.
  - 2 additional evidence files (`inv3-build-properties.txt`, `inv4-save-game-saves.txt`).
</verification>

<success_criteria>
  - All 4 JVM-tier emission invariants GREEN.
  - 5 evidence files total in `evidence/tier1-shape/` from Plans 11-07 + 11-08.
</success_criteria>

<output>
Create `.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-08-SUMMARY.md` with: 2 new tests, all 4 invariants GREEN summary, evidence file paths.
</output>
