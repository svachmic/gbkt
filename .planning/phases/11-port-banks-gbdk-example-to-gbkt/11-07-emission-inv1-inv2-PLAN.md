---
phase: 11-port-banks-gbdk-example-to-gbkt
plan: 07
type: execute
wave: 2
depends_on: ["11-05"]
files_modified:
  - gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt
autonomous: true
requirements:
  - BANK-INV-1   # JVM-tier invariant 1: HOME→bank trampoline shape (BANKED keyword on play scene fns) — CONTEXT D-12(1)
  - BANK-INV-2   # JVM-tier invariant 2: SWITCH_ROM-from-HOME wrapper emission — CONTEXT D-12(2)
user_setup: []
must_haves:
  truths:
    - "INV-1: `play_enter`, `play_frame`, `play_exit` in `bank1.c` all carry the `BANKED` keyword (per-function brace-walked grep)"
    - "INV-2: `_bkg_tiles_load_banked` in `main.c` contains `SWITCH_ROM(bank);`, `set_bkg_tiles(`, `SWITCH_ROM(1);` in the expected order"
    - "Both tests use `extractFunctionBody()` brace-walk, NOT file-level grep (CLAUDE.md scope-level grep gates corollary)"
  artifacts:
    - path: "gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt"
      provides: "Adds INV-1 and INV-2 @Test methods"
      contains: "fun \\`INV-1"
  key_links:
    - from: "BanksEmissionTest INV-1"
      to: "CFunction.isBanked + CEmitter.kt:192 BANKED emission"
      via: "extractFunctionBody(bank1C, \"play_enter\").contains(\" BANKED\")"
      pattern: "play_enter.*BANKED"
    - from: "BanksEmissionTest INV-2"
      to: "GBDKPipelineV2.buildBkgTilesLoadBankedHelper (Plan 07.4-30 wrapper)"
      via: "extractFunctionBody(mainC, \"_bkg_tiles_load_banked\").contains(\"SWITCH_ROM\")"
      pattern: "_bkg_tiles_load_banked.*SWITCH_ROM"
---

<objective>
Add JVM-tier emission invariants INV-1 and INV-2 to `BanksEmissionTest.kt`. These are 2 of the 4 invariants required by CONTEXT D-12; the other 2 (INV-3, INV-4) are added in Plan 11-08.

Purpose: Tier-1 codegen oracle (per RESEARCH §Validation Architecture Tier-1). Verifies that the BANKED auto-injection + the HOME-bank SWITCH_ROM wrapper from Plan 07.4-30 still fire for the banks port substrate. Per CLAUDE.md "Scope-level grep gates corollary", the tests use per-function brace-walk via `extractFunctionBody()` (already copied verbatim in Plan 11-01), NOT file-level grep.

Output: 2 GREEN `@Test` methods in `BanksEmissionTest.kt`.
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
@CLAUDE.md
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Add INV-1 (BANKED keyword on play_enter/frame/exit)</name>
  <files>gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt</files>
  <read_first>
    - gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsEmissionTest.kt (full file — analog with `extractFunctionBody` + GBDKPipelineV2 invocation; copy the @Test shape verbatim)
    - 11-PATTERNS.md §"BanksEmissionTest.kt" (lines 202–270 — full invariant test structure)
    - 11-RESEARCH.md §"JVM-Tier Brace-Walk Pattern Reference" (lines 379–453 — INV-1..4 assertion specifications)
    - 11-RESEARCH.md §State of the Art ("Manual BANKED tracking" row — CFunction.isBanked + CEmitter.kt:192 is the production mechanism)
    - CLAUDE.md §"Scope-level grep gates (corollary)" — mandates brace-walk, forbids file-level grep
    - The Plan 11-01 stub of BanksEmissionTest.kt (already contains `extractFunctionBody`, `EVIDENCE_DIR`, imports)
  </read_first>
  <behavior>
    INV-1: For `banks.build()` piped through `GBDKPipelineV2().generate(...)`:
    - `output.files["bank1.c"]` exists and is non-null
    - `extractFunctionBody(bank1C, "play_enter")` returns non-empty AND contains the substring ` BANKED` (note leading space — the emission template is `void name(...) BANKED { ... }` so " BANKED" appears in the function signature line before the opening brace)
    - Same for `play_frame` and `play_exit`
    - The extracted function body is also written to `EVIDENCE_DIR/inv1-play-enter.txt` BEFORE the assertions fire (evidence-before-assert pattern from 11-PATTERNS.md §"Evidence-before-assert pattern", lines 268–269)
  </behavior>
  <action>
    Open `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt` and add ONE @Test method after the existing `extractFunctionBody` helper:

    ```kotlin
    @Test
    fun `INV-1 play scene functions carry BANKED keyword in bank1`() {
        val pipeline = GBDKPipelineV2()
        val output = pipeline.generate(banks.build())
        val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")

        EVIDENCE_DIR.mkdirs()
        val enterBody = extractFunctionBody(bank1C, "play_enter")
        val frameBody = extractFunctionBody(bank1C, "play_frame")
        val exitBody = extractFunctionBody(bank1C, "play_exit")
        File(EVIDENCE_DIR, "inv1-play-enter.txt").writeText(enterBody)
        File(EVIDENCE_DIR, "inv1-play-frame.txt").writeText(frameBody)
        File(EVIDENCE_DIR, "inv1-play-exit.txt").writeText(exitBody)

        assertTrue(enterBody.contains(" BANKED"), "play_enter must have BANKED keyword in signature")
        assertTrue(frameBody.contains(" BANKED"), "play_frame must have BANKED keyword in signature")
        assertTrue(exitBody.contains(" BANKED"), "play_exit must have BANKED keyword in signature")
    }
    ```

    Companion-level imports needed (already in Plan 11-01 stub): `import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipelineV2`, `import java.io.File`, `import kotlin.test.Test`, `import kotlin.test.assertTrue`. Add any that are missing.

    Do NOT use file-level grep (e.g., `bank1C.contains("BANKED")`) — that would mask a regression where one scene has BANKED but another doesn't. The per-function brace-walk is mandatory per CLAUDE.md.
  </action>
  <verify>
    <automated>./gradlew :gbkt-examples:banks:test --tests "io.github.gbkt.examples.banks.BanksEmissionTest.INV-1*" --quiet</automated>
  </verify>
  <acceptance_criteria>
    - `./gradlew :gbkt-examples:banks:test --tests "io.github.gbkt.examples.banks.BanksEmissionTest.INV-1*"` exits 0
    - Evidence files exist after test run: `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/tier1-shape/inv1-play-enter.txt`, `inv1-play-frame.txt`, `inv1-play-exit.txt`
    - All three evidence files contain the literal string ` BANKED` (proves the brace-walk extracted the right scope)
    - `grep -c "@Test" gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt` returns ≥ 1
  </acceptance_criteria>
  <done>INV-1 GREEN; BANKED auto-injection contract is locked for the banks port.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Add INV-2 (_bkg_tiles_load_banked SWITCH_ROM wrapper)</name>
  <files>gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt</files>
  <read_first>
    - 11-PATTERNS.md §"BanksEmissionTest.kt" — invariant 2 shape
    - 11-RESEARCH.md §"JVM-Tier Brace-Walk Pattern Reference" lines 440–443 (INV-2 specification: SWITCH_ROM(bank); + set_bkg_tiles( + SWITCH_ROM(1); all present)
    - 11-RESEARCH.md §"Code Insights" entry for `_bkg_tiles_load_banked` (Plan 07.4-30 surface — GBDKPipelineV2.kt:1855+, 1964+)
    - 11-RESEARCH.md §Open Questions Q1 — unconditional emission of `_bkg_tiles_load_banked` for any game with zones; this test will surface the answer (if assert fails, that's the named bug Candidate 2)
  </read_first>
  <behavior>
    INV-2: For `banks.build()` piped through `GBDKPipelineV2().generate(...)`:
    - `output.files["main.c"]` exists and is non-null
    - `extractFunctionBody(mainC, "_bkg_tiles_load_banked")` returns non-empty (proves the helper is emitted unconditionally for games with zones)
    - The extracted body contains three substrings: `SWITCH_ROM(`, `set_bkg_tiles(`, and `SWITCH_ROM(1);` (note: the `SWITCH_ROM(1);` at the end is the post-emission bank-restore; the leading `SWITCH_ROM(<n>);` switches to the zone's bank)
    - Evidence: write body to `EVIDENCE_DIR/inv2-bkg-tiles-wrapper.txt` before asserts
  </behavior>
  <action>
    Append a second `@Test` method to `BanksEmissionTest.kt`:

    ```kotlin
    @Test
    fun `INV-2 bkg_tiles_load_banked wrapper in main_c has SWITCH_ROM sequence`() {
        val pipeline = GBDKPipelineV2()
        val output = pipeline.generate(banks.build())
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()
        val wrapperBody = extractFunctionBody(mainC, "_bkg_tiles_load_banked")
        File(EVIDENCE_DIR, "inv2-bkg-tiles-wrapper.txt").writeText(wrapperBody)

        assertTrue(wrapperBody.isNotEmpty(),
            "_bkg_tiles_load_banked helper must be emitted in main.c for games with zones (Plan 07.4-30 surface)")
        assertTrue(wrapperBody.contains("SWITCH_ROM("),
            "_bkg_tiles_load_banked must contain SWITCH_ROM(N) to enter zone bank")
        assertTrue(wrapperBody.contains("set_bkg_tiles("),
            "_bkg_tiles_load_banked must call set_bkg_tiles after SWITCH_ROM")
        assertTrue(wrapperBody.contains("SWITCH_ROM(1);"),
            "_bkg_tiles_load_banked must restore bank via SWITCH_ROM(1) on exit")
    }
    ```

    If INV-2 FAILS at this point, that's the named codegen bug Candidate 2 surfacing — but per RESEARCH §Top-2 Likely Codegen Bug Candidates, Candidate 1 (`trigger_saves` missing) has HIGH probability and Candidate 2 is MEDIUM. The named-bug-naming logic lives in Plan 11-09 (first buildRom), not here. If INV-2 fails in Wave 2, Plan 11-09 will name the bug-fix scope accordingly (it may then become Plan 11-10's target instead of `trigger_saves`).

    Do NOT use file-level grep (`mainC.contains("SWITCH_ROM")`) — the wrapper might emit SWITCH_ROM inside an unrelated function (e.g. `navigate_to_scene`) and a file-level grep would false-positive.
  </action>
  <verify>
    <automated>./gradlew :gbkt-examples:banks:test --tests "io.github.gbkt.examples.banks.BanksEmissionTest.INV-2*" --quiet</automated>
  </verify>
  <acceptance_criteria>
    - `./gradlew :gbkt-examples:banks:test --tests "io.github.gbkt.examples.banks.BanksEmissionTest.INV-2*"` exits 0 (RESEARCH §Pitfall expected outcome: INV-2 GREEN because helper is unconditional)
    - Evidence file `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/tier1-shape/inv2-bkg-tiles-wrapper.txt` exists AND contains all three literal substrings: `SWITCH_ROM(`, `set_bkg_tiles(`, `SWITCH_ROM(1);`
    - Total `@Test` count in file is now 2
    - If test fails: STOP — do not proceed past Plan 11-08 in Wave 2. Capture failure in `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/inv2-failure.txt` and flag for Plan 11-09 named-bug renaming (Candidate 2 surfaced earlier than first buildRom).
  </acceptance_criteria>
  <done>INV-2 GREEN; the Plan 07.4-30 HOME-bank SWITCH_ROM wrapper is verified for the banks port substrate (no genre, just a plain zone).</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Test → pipeline output | Read-only access; tests cannot mutate generated C |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-11-11 | Tampering | Brace-walk extraction | mitigate | extractFunctionBody copied verbatim from SimplePhysicsEmissionTest:82–102; depth-counting handles strings/comments correctly per prior test usage |
| T-11-12 | Repudiation | File-level grep would mask scope-specific regressions | mitigate | CLAUDE.md scope-level grep gates corollary cited; per-function brace-walk used in both INVs |
| T-11-SC | Tampering | npm/pip/cargo installs | n/a | No installs |
</threat_model>

<verification>
  - INV-1 and INV-2 GREEN.
  - Evidence files exist with expected literal substrings.
  - No file-level grep used (manual scan of test bodies).
</verification>

<success_criteria>
  - 2 GREEN @Test methods in BanksEmissionTest.
  - 4 evidence files written under `evidence/tier1-shape/` (3 from INV-1, 1 from INV-2).
</success_criteria>

<output>
Create `.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-07-SUMMARY.md` with: 2 invariant outcomes, evidence file paths, total test count.
</output>
