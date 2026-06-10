---
phase: 11-port-banks-gbdk-example-to-gbkt
plan: 10
type: tdd
wave: 4
depends_on: ["11-09"]
files_modified:
  - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/GBDKSystemVisitor.kt
  - gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt
autonomous: true
requirements:
  - BANK-NAMED-BUGFIX   # The ONE named codegen bug-fix per CONTEXT D-13 + scope cap
user_setup: []
must_haves:
  truths:
    - "Named-bug RED test added to BanksEmissionTest.INV-4: `mainC.contains(\"trigger_saves\")` (fails BEFORE fix)"
    - "Named-bug fix applied to `GBDKSystemVisitor.visitSaveSystem()` per `evidence/named-bug.md` Fix spec"
    - "RED test now GREEN (TDD cycle: failing test → fix → passing test)"
    - "All previously-GREEN INV-1..4 + 8 IR tests remain GREEN (no regression)"
  artifacts:
    - path: "gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/GBDKSystemVisitor.kt"
      provides: "Patched visitSaveSystem returning [saveGame, loadGame, triggerStub]"
      contains: "trigger_"
    - path: "gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt"
      provides: "INV-4 extended with the trigger_saves assertion (RED→GREEN cycle)"
      contains: "trigger_saves"
  key_links:
    - from: "GBDKSystemVisitor.visitSaveSystem (post-fix)"
      to: "ScriptOpVisitor.visitTriggerSystem CCall(\"trigger_<id>\", args)"
      via: "matched function symbol — trigger_<id>(slotIndex) calls save_game_<id>(slotIndex)"
      pattern: "trigger_\\$sanitizedId"
---

<objective>
Implement the ONE named codegen bug-fix from Plan 11-09's `evidence/named-bug.md`. The expected scope per RESEARCH §"DSL Call Surface Gap" and 11-PATTERNS.md §"GBDKSystemVisitor.kt bug-fix" is: add a `trigger_<id>()` stub to `GBDKSystemVisitor.visitSaveSystem()` that delegates to `save_game_<id>(slotIndex)`.

Purpose: Close the gap where `ScriptOpVisitor.visitTriggerSystem()` calls `trigger_<id>()` but `visitSaveSystem()` never emits one. Without this fix, `triggerSystem("saves")` in DSL produces a linker error (lcc: `undefined identifier 'trigger_saves'`) and UAT anchor 4 (SRAM persistence) cannot be reached.

TDD cycle: RED (add the assertion to INV-4 — fails) → GREEN (apply the visitor fix — passes) → REFACTOR (none needed; the fix is a single stub).

Output: Patched `GBDKSystemVisitor.kt` + extended INV-4 assertion. ALL of INV-1..4 still GREEN. The first buildRom from Plan 11-09 was the RED state; this plan closes it.

**Scope guardrail:** If `evidence/named-bug.md` (from Plan 11-09) names a DIFFERENT bug (Branch B in Plan 11-09), this plan's `<action>` is REINTERPRETED to fix THAT bug instead, using its named Fix spec. The TDD cycle structure (RED test in the appropriate test file → fix → GREEN) is identical. The default assumes the most-likely outcome per RESEARCH (Candidate 1).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-CONTEXT.md
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-RESEARCH.md
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-PATTERNS.md
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/named-bug.md
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: RED — extend INV-4 with trigger_saves assertion (will fail)</name>
  <files>gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt</files>
  <read_first>
    - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/named-bug.md (Plan 11-09 output — confirms named bug class)
    - 11-RESEARCH.md §"DSL Call Surface Gap (Top-1 Bug Candidate)" lines 217–232
    - 11-RESEARCH.md §"JVM-Tier Brace-Walk Pattern Reference" line 452 (INV-4 fifth assertion, post-fix)
    - The existing `INV-4 save_game_saves` test method in BanksEmissionTest.kt (from Plan 11-08)
  </read_first>
  <behavior>
    Append ONE additional assertion to the existing INV-4 test method (don't add a 5th test method — keep the test count at 4, but extend INV-4's scope to also cover the trigger stub):

    - `mainC.contains("trigger_saves")` evaluates TRUE

    BEFORE the GBDKSystemVisitor fix in Task 2, this assertion will fail. AFTER Task 2, it passes. This is the standard TDD RED → GREEN signal.
  </behavior>
  <action>
    Open `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt`. Find the existing `@Test fun \`INV-4 save_game_saves in main_c emits ENABLE_RAM and DISABLE_RAM\`()` method (added in Plan 11-08). Append after the existing assertions (after the `assertTrue(sramIdx < disableIdx, ...)` line) but before the closing `}`:

    ```kotlin
        // Post-fix from Plan 11-10: trigger_saves trampoline stub must be emitted in main.c
        // Per RESEARCH §"DSL Call Surface Gap" — ScriptOpVisitor.visitTriggerSystem always
        // emits CCall("trigger_<id>", args); without this stub, lcc reports
        // `undefined identifier 'trigger_saves'`.
        assertTrue(
            mainC.contains("trigger_saves"),
            "trigger_saves stub must be emitted in main.c by visitSaveSystem (fix in Plan 11-10)"
        )
    ```

    Run the test BEFORE applying the visitor fix:
    ```
    ./gradlew :gbkt-examples:banks:test --tests "io.github.gbkt.examples.banks.BanksEmissionTest.INV-4*" --quiet
    ```
    Expected: this RED phase FAILS with `AssertionError: trigger_saves stub must be emitted...`. Capture the failure into `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/inv4-red-failure.txt` (the assertion message + stack trace's relevant lines).

    Do NOT skip the RED phase. The point of TDD is the test's own truth — if the test passes BEFORE Task 2, the bug was already fixed or the test is mis-targeted.

    Commit at end of this task with message `test(11-10): RED — INV-4 asserts trigger_saves stub presence` (per CLAUDE.md memory `feedback_use_git_commit_F_not_heredoc`: use `git commit -F <tempfile>` not heredoc through Bash; tempfile contains the message).
  </action>
  <verify>
    <automated>./gradlew :gbkt-examples:banks:test --tests "io.github.gbkt.examples.banks.BanksEmissionTest.INV-4*" --quiet; test $? -ne 0 && echo "RED-OK" || (echo "RED FAILED — assertion passed before fix, scope is wrong" && false)</automated>
  </verify>
  <acceptance_criteria>
    - Test `INV-4 save_game_saves in main_c emits ENABLE_RAM and DISABLE_RAM` FAILS in Task 1 (RED phase) with the message `trigger_saves stub must be emitted in main.c`
    - Evidence file `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/inv4-red-failure.txt` exists with the assertion failure captured
    - File `BanksEmissionTest.kt` contains the literal string `trigger_saves stub must be emitted`
    - The total `@Test` annotation count in `BanksEmissionTest.kt` is still 4 (the assertion is appended to existing INV-4, NOT a new test method)
    - Git commit with subject `test(11-10): RED — INV-4 asserts trigger_saves stub presence` exists in HEAD~ or HEAD
  </acceptance_criteria>
  <done>RED phase locked: the named-bug assertion is in place and verifiably fails before the visitor fix.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: GREEN — apply trigger_saves stub fix to GBDKSystemVisitor</name>
  <read_first>
    - 11-PATTERNS.md §"GBDKSystemVisitor.kt bug-fix" (lines 455–494 — exact implementation; the `visitGenericSystem` else-branch analog at lines 2616–2631 of the visitor file)
    - 11-RESEARCH.md §"DSL Call Surface Gap (Top-1 Bug Candidate)" Fix options (line 226 — choose option 1: trigger_<id>(UINT8 slot) wrapper that calls save_game_<id>(slot))
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/GBDKSystemVisitor.kt — find `visitSaveSystem` at line 299 and its return statement at line 485 (verify line numbers; the visitor may have shifted)
    - The analog: same file, lines 2617–2631 (visitGenericSystem trigger-stub pattern)
    - feedback_no_magic_strings.md (user memory) — use the existing `sanitizedId` symbol, do NOT hard-code "saves"
  </read_first>
  <files>gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/GBDKSystemVisitor.kt</files>
  <action>
    Patch `visitSaveSystem()` in `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/GBDKSystemVisitor.kt`.

    1. Locate the existing `return listOf(saveGame, loadGame)` (was line 485 at research time). Use `mcp__serena__find_symbol` if available; otherwise grep for the literal `return listOf(saveGame, loadGame)` within the `visitSaveSystem` method scope. Verify the function name and class context before editing.

    2. Immediately BEFORE the `return` statement, construct the trigger stub. The exact construction per 11-PATTERNS.md §"GBDKSystemVisitor.kt bug-fix" lines 461–474:

       ```kotlin
       val triggerStub = CFunction(
           name = "trigger_$sanitizedId",
           returnType = CVoid,
           params = listOf(CParam("slotIndex", CU8)),
           body = listOf(
               CExprStatement(CCall("save_game_$sanitizedId", listOf(CVar("slotIndex"))))
           ),
           sectionComment = "SaveSystem trigger stub — called by ScriptOpVisitor.visitTriggerSystem",
       )
       ```

    3. Change the return statement to:
       ```kotlin
       return listOf(saveGame, loadGame, triggerStub)
       ```

    4. Verify the `sanitizedId` symbol is already in scope inside `visitSaveSystem` (per RESEARCH §SaveDataBuilder SRAM Path, the existing `save_game_$sanitizedId` and `load_game_$sanitizedId` use this — so it's confirmed local).

    5. If imports for `CFunction`, `CParam`, `CU8`, `CVoid`, `CExprStatement`, `CCall`, `CVar` are missing in the visitor file's top section, they almost certainly already exist (the analog stub at line 2620 uses the same types). Confirm by grepping `import.*CFunction` in the file.

    6. Run the test:
       ```
       ./gradlew :gbkt-examples:banks:test --tests "io.github.gbkt.examples.banks.BanksEmissionTest" --quiet
       ```
       All 4 INVs MUST be GREEN now.

    7. Run the FULL backend-gbdk test suite to catch regressions (Phase 9/10 INV tests use the same visitor):
       ```
       ./gradlew :gbkt-backend-gbdk:test --quiet
       ```
       Must exit 0. If any backend-gbdk test breaks, the fix's blast-radius exceeds the named-bug scope — STOP and route to a new phase per memory `feedback_route_to_proper_phase_when_blast_radius_is_wide.md`.

    8. Commit with message `fix(11-10): GREEN — emit trigger_<id> stub in visitSaveSystem`. Use `git commit -F <tempfile>` per the memory rule.

    DO NOT make any OTHER changes to `visitSaveSystem` or any other visitor method. The named-bug fix is bounded to adding ONE CFunction and changing ONE return statement.
  </action>
  <verify>
    <automated>./gradlew :gbkt-examples:banks:test --tests "io.github.gbkt.examples.banks.BanksEmissionTest" --quiet && ./gradlew :gbkt-backend-gbdk:test --quiet</automated>
  </verify>
  <acceptance_criteria>
    - `./gradlew :gbkt-examples:banks:test --tests "io.github.gbkt.examples.banks.BanksEmissionTest" --quiet` exits 0 (all 4 INVs GREEN)
    - `./gradlew :gbkt-backend-gbdk:test --quiet` exits 0 (no regression in the broader visitor suite)
    - `GBDKSystemVisitor.kt` contains literal `trigger_$sanitizedId` AND `save_game_$sanitizedId` inside `visitSaveSystem` (or its scope) — verify via grep
    - `GBDKSystemVisitor.kt` `visitSaveSystem` now returns a 3-element list (grep `return listOf(saveGame, loadGame, triggerStub)`)
    - Re-generated `main.c` for banks (run `./gradlew :gbkt-examples:banks:generateC --quiet`) contains the literal string `trigger_saves` exactly once
    - Re-generated `main.c` contains `save_game_saves` AND `load_game_saves` (existing functions still present — no replacement; addition only)
    - Git commit with subject `fix(11-10): GREEN — emit trigger_<id> stub in visitSaveSystem` exists in HEAD
  </acceptance_criteria>
  <done>GREEN phase complete: visitor patched, all 4 INVs GREEN, no regression in backend-gbdk tests, the named bug is closed.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Codegen visitor → all gbkt downstream | A bug in the visitor affects every game that uses SaveDataBuilder |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-11-18 | Tampering | Visitor logic | mitigate | Fix is a single CFunction addition; backend-gbdk test suite must remain GREEN as a regression gate |
| T-11-19 | Denial of service | Wide-blast unintended change | mitigate | Acceptance criterion blocks merge if `:gbkt-backend-gbdk:test` regresses; memory `feedback_route_to_proper_phase_when_blast_radius_is_wide` activates if scope grows |
| T-11-20 | Repudiation | TDD cycle skipped | mitigate | RED phase commit + GREEN phase commit are separate; evidence file `inv4-red-failure.txt` proves RED before GREEN |
| T-11-21 | Tampering | Magic string `"saves"` hard-coded | mitigate | Memory `feedback_no_magic_strings` enforced — code uses `sanitizedId` variable already in scope |
| T-11-SC | Tampering | npm/pip/cargo installs | n/a | Kotlin source-only fix; no package installs |
</threat_model>

<verification>
  - RED phase: INV-4 fails with `trigger_saves stub must be emitted...` (captured in evidence file).
  - GREEN phase: all 4 INVs GREEN; full backend-gbdk suite GREEN.
  - Generated `main.c` for banks contains `trigger_saves` exactly once.
  - Two separate commits (RED + GREEN) in git history.
</verification>

<success_criteria>
  - Named bug from Plan 11-09 closed.
  - INV-4 expanded to include the trigger-stub contract.
  - No regression in backend-gbdk tests.
  - TDD cycle observable in git history (two commits with `test(11-10):` and `fix(11-10):` prefixes).
</success_criteria>

<output>
Create `.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-10-SUMMARY.md` with: RED failure message, GREEN test counts (4/4 GREEN; full suite N tests GREEN), patched file + line range, 2 commit subjects.
</output>
