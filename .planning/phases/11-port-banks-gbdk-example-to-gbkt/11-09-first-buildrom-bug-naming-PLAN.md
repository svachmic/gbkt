---
phase: 11-port-banks-gbdk-example-to-gbkt
plan: 09
type: execute
wave: 3
depends_on: ["11-05", "11-06", "11-07", "11-08"]
files_modified:
  - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/first-buildrom.log
  - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/named-bug.md
autonomous: false
requirements:
  - BANK-FIRST-BUILD   # First buildRom smoke run + name the codegen bug per CONTEXT D-13
user_setup: []
must_haves:
  truths:
    - "`./gradlew :gbkt-examples:banks:buildRom` runs to completion (output captured even if non-zero exit)"
    - "First-build outcome is recorded under `evidence/first-buildrom.log`"
    - "If the build fails, the SPECIFIC failure is named in `evidence/named-bug.md` (one named bug only, per D-13 + scope cap)"
    - "If the build succeeds, `evidence/named-bug.md` records the outcome and Plan 11-10 is REPURPOSED to add a deferred Plan-11-08-style assertion only"
  artifacts:
    - path: ".planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/first-buildrom.log"
      provides: "Raw stdout+stderr from first buildRom invocation"
      contains: "BUILD SUCCESSFUL"   # OR "BUILD FAILED" — both are valid outcomes; the LOG must exist
    - path: ".planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/named-bug.md"
      provides: "Named-bug record: which codegen defect blocks which UAT anchor"
      contains: "## Named codegen bug"
  key_links:
    - from: "first-buildrom.log"
      to: "named-bug.md"
      via: "evidence/ folder reference"
      pattern: "named-bug\\.md"
---

<objective>
Run the FIRST clean `./gradlew :gbkt-examples:banks:buildRom` (with the Plan 11-05 DSL but BEFORE any codegen bug-fix) to surface the named-bug-of-this-phase per CONTEXT D-13 (exploratory bug-fix slot — name the bug AFTER the first build, not before).

Purpose: D-13 discipline — Phase 9 and Phase 10 each surfaced ONE concrete codegen defect via the first port build. Phase 11 expects the same. RESEARCH §"Top-2 Likely Codegen Bug Candidates" predicts Candidate 1 (`trigger_saves` missing — lcc linker error) with HIGH probability; Candidate 2 (`_bkg_tiles_load_banked` guarded by genre detection — INV-2 would have already failed in Wave 2 if so) with MEDIUM probability. The first buildRom either confirms Candidate 1, surfaces an unforeseen bug, or surprises us by succeeding.

Output: A `named-bug.md` artifact that fixes the scope of Plan 11-10. Per CONTEXT scope cap "ONE example, ONE named codegen bug-fix", this plan names AT MOST ONE bug for Plan 11-10 to fix. Surplus codegen defects → seeds + Phase 11.1 (handled in Plan 11-14).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-CONTEXT.md
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-RESEARCH.md
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-PATTERNS.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: Run first buildRom and capture log</name>
  <read_first>
    - 11-CONTEXT.md D-13 (name AFTER first build), D-14 (surplus → seeds + Phase 11.1)
    - 11-RESEARCH.md §"Top-2 Likely Codegen Bug Candidates" (lines 268–287 — Candidate 1 = trigger_saves; Candidate 2 = _bkg_tiles_load_banked genre guard)
    - 11-RESEARCH.md §"Common Pitfalls" Pitfall 4 (lcc reports `undefined identifier 'trigger_saves'` — Candidate 1 symptom)
    - feedback_rom_build_smoke_test_for_codegen_phases.md (user memory) — buildRom is mandatory for codegen phases
    - feedback_quality_over_shortcuts.md (user memory) — capture FULL output, do not summarize
  </read_first>
  <files>.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/first-buildrom.log</files>
  <action>
    1. Ensure GBDK toolchain is available. Run `echo "$GBDK_HOME"; which lcc` and capture into the log. If `lcc` is not found AND GBDK_HOME is unset:
       - Halt and emit the standard plan-skip message: "lcc/GBDK not detected — this plan requires GBDK to surface the named bug. Set GBDK_HOME or install GBDK per CLAUDE.md §Prerequisites."
       - Continue past only if the developer has GBDK installed.

    2. Create the evidence directory: `mkdir -p .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/`.

    3. Run clean buildRom WITH output capture (both stdout and stderr), allowing non-zero exit:
       ```
       (./gradlew :gbkt-examples:banks:clean :gbkt-examples:banks:buildRom --console=plain 2>&1; echo "EXIT_CODE=$?") | tee .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/first-buildrom.log
       ```

    4. Read the final 60 lines of the log into context for Task 2's bug-naming decision.

    Do NOT manually edit the log to remove errors. The log is evidence — preserve verbatim.
  </action>
  <verify>
    <automated>test -f .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/first-buildrom.log && test $(wc -l < .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/first-buildrom.log) -ge 5 && grep -qE "BUILD SUCCESSFUL|BUILD FAILED|EXIT_CODE=" .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/first-buildrom.log</automated>
  </verify>
  <acceptance_criteria>
    - File `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/first-buildrom.log` exists
    - Log size ≥ 5 lines (proves the command actually ran)
    - Log contains one of: `BUILD SUCCESSFUL`, `BUILD FAILED`, or `EXIT_CODE=` line (proves the command ran to completion)
    - Log is NOT manually edited (no commit-time diff that strips errors)
  </acceptance_criteria>
  <done>buildRom log captured; outcome (pass / fail with specific error) is known.</done>
</task>

<task type="auto">
  <name>Task 2: Write named-bug.md based on log outcome</name>
  <read_first>
    - The first-buildrom.log file produced by Task 1
    - 11-RESEARCH.md §"Top-2 Likely Codegen Bug Candidates" + §"Common Pitfalls" Pitfall 4 (for matching the log signature to a known candidate)
    - 11-RESEARCH.md §"DSL Call Surface Gap (Top-1 Bug Candidate)" (lines 217–232 — the fix specification for Candidate 1)
    - 11-CONTEXT.md D-13 (one named bug per plan) and D-14 (surplus → seeds)
    - 11-PATTERNS.md §"GBDKSystemVisitor.kt bug-fix" (lines 455–494 — implementation analog if Candidate 1 confirmed)
  </read_first>
  <files>.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/named-bug.md</files>
  <action>
    Write `evidence/named-bug.md` based on the log outcome. Choose ONE of the three branches:

    **Branch A — Log contains `undefined identifier 'trigger_saves'` (or similar `trigger_*` linker error):**
    Named bug = Candidate 1 (RESEARCH §Top-2). File content:

    ```markdown
    # Named codegen bug — Phase 11

    **Surfaced by:** First clean buildRom (Plan 11-09)
    **Log evidence:** `evidence/first-buildrom.log` (search: `undefined identifier 'trigger_saves'`)
    **Bug class:** Candidate 1 — SaveDataBuilder has no `trigger_<id>()` trampoline
    **UAT anchor blocked:** Anchor 4 (SRAM save persistence — cannot reach the save path from DSL scripts)
    **File:** `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/GBDKSystemVisitor.kt`
    **Line:** `visitSaveSystem()` at line 299 returns `listOf(saveGame, loadGame)` at line 485 (line numbers as of research date 2026-05-19; verify before edit)

    ## Fix spec (Plan 11-10 will implement)

    Per 11-PATTERNS.md §"GBDKSystemVisitor.kt bug-fix" + 11-RESEARCH.md §"DSL Call Surface Gap":

    - Add a third returned CFunction `triggerStub` to `visitSaveSystem()`:
      - name = "trigger_$sanitizedId"
      - returnType = CVoid
      - params = `listOf(CParam("slotIndex", CU8))`
      - body = `listOf(CExprStatement(CCall("save_game_$sanitizedId", listOf(CVar("slotIndex")))))`
    - Change `return listOf(saveGame, loadGame)` to `return listOf(saveGame, loadGame, triggerStub)`
    - Add `sectionComment = "SaveSystem trigger stub — called by ScriptOpVisitor.visitTriggerSystem"`

    ## Scope cap

    Per CONTEXT D-13: ONE named bug per phase. Any other defects surfaced by the buildRom log MUST be captured as seeds in Plan 11-14, NOT folded into Plan 11-10.
    ```

    **Branch B — Log contains a different specific codegen failure (e.g., `_bkg_tiles_load_banked` guarded — Candidate 2; OR a literal `bank overflow` — different class; OR a totally unforeseen error):**
    Write `named-bug.md` describing the bug class with the SAME 6 fields as Branch A. Adapt the Fix spec to the surfaced bug. Plan 11-10 will implement that fix instead.

    Mandatory fields regardless of branch:
    1. `**Surfaced by:**` (which Plan)
    2. `**Log evidence:**` (search string the verifier can grep)
    3. `**Bug class:**` (Candidate 1/2/other — clear named class)
    4. `**UAT anchor blocked:**` (which D-08 anchor)
    5. `**File:**` (absolute repo-relative path)
    6. `**Fix spec:**` (concrete steps for Plan 11-10)

    **Branch C — `BUILD SUCCESSFUL` and the ROM is produced:**
    Surprise outcome (lower probability per RESEARCH). Write:

    ```markdown
    # Named codegen bug — Phase 11

    **Surfaced by:** First clean buildRom (Plan 11-09)
    **Log evidence:** `evidence/first-buildrom.log` (contains `BUILD SUCCESSFUL`)
    **Bug class:** NONE — no codegen bug surfaced by the first buildRom

    ## Decision

    Per CONTEXT D-13 (exploratory bug-fix slot), the slot is NOT artificially filled. Plan 11-10 is REPURPOSED to add a single defensive JVM-tier assertion: `mainC.contains("trigger_saves")` in BanksEmissionTest.INV-4 (lock the trigger trampoline contract). This is a confirmation-of-no-bug, not a bug-fix.

    The phase still ships ONE named codegen ARTIFACT — either the fix (Branches A/B) or the lock (Branch C). No artificial bug-naming.
    ```

    Do NOT name more than one bug. Do NOT split the fix across multiple plans. If the log contains multiple distinct errors, pick the FIRST blocking one as the named bug and capture the rest as seeds in Plan 11-14.
  </action>
  <verify>
    <automated>test -f .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/named-bug.md && grep -qE "Bug class:|## Decision" .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/named-bug.md</automated>
  </verify>
  <acceptance_criteria>
    - File `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/named-bug.md` exists
    - File contains either the literal string `**Bug class:**` (Branches A/B) OR `## Decision` (Branch C)
    - File names AT MOST ONE bug (grep `Bug class:` returns ≤ 1)
    - File names a specific file path (e.g., `GBDKSystemVisitor.kt`) under `**File:**` OR explicitly declares "NONE" in Branch C
    - File names a specific UAT anchor under `**UAT anchor blocked:**` OR declares "NONE"
  </acceptance_criteria>
  <done>Bug named (or absence-of-bug confirmed); Plan 11-10's scope is now bound.</done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 3: Human gate — confirm named-bug scope before Plan 11-10</name>
  <what-built>
    Task 1 ran the first `:gbkt-examples:banks:buildRom` and captured the log.
    Task 2 wrote `evidence/named-bug.md` naming the bug-class to fix in Plan 11-10 (or declaring no bug per Branch C).
  </what-built>
  <how-to-verify>
    1. Read `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/first-buildrom.log` — verify the final 30 lines describe the same outcome that `named-bug.md` claims.
    2. Read `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/named-bug.md` — verify:
       - The bug class matches the log evidence (no fabrication).
       - The fix spec is concrete (named file, named function, named change).
       - Only ONE bug is named (per CONTEXT scope cap).
    3. Cross-check: if the log shows multiple compilation errors but `named-bug.md` collapsed them into one, confirm the FIRST one is the blocking one and the rest are surplus (handled in Plan 11-14 seeds).
  </how-to-verify>
  <files>.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/named-bug.md (read-only review)</files>
  <action>Human inspects evidence/first-buildrom.log and evidence/named-bug.md per the steps in <how-to-verify>. If the named bug is correct, approve; if a wide-blast fix is required, route to a new phase per memory feedback_route_to_proper_phase_when_blast_radius_is_wide.md.</action>
  <verify><human-check>Review log + named-bug.md side-by-side; one bug named; fix spec concrete; scope cap honoured.</human-check></verify>
  <done>Human approves the scope OR redirects to a new phase. No further action in Plan 11-09 until response received.</done>
  <resume-signal>
    Type `approved` to proceed to Plan 11-10 with the named scope, OR provide a renaming directive (e.g., "rename bug to Candidate X because Y") to revise `named-bug.md` before continuing.
    Per CONTEXT D-13 + scope cap + memory `feedback_route_to_proper_phase_when_blast_radius_is_wide.md`: if the named bug has blast-radius beyond `GBDKSystemVisitor.visitSaveSystem` (e.g., it requires changes to ScriptOpVisitor AND a new ScriptOp class AND new IR), STOP and reply `route to new phase` — Plan 11-10 will NOT absorb wide-blast fixes inline.
  </resume-signal>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| GBDK toolchain → host filesystem | lcc writes ROM and intermediate files under `build/`; trusted local toolchain |
| Log artifact → planning decisions | If a tampered log misnames the bug, Plan 11-10 fixes the wrong thing |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-11-15 | Tampering | first-buildrom.log | mitigate | `tee` captures raw output; acceptance criteria forbid manual edits; human gate (Task 3) cross-checks log vs named-bug.md |
| T-11-16 | Repudiation | Bug-naming ambiguity | mitigate | named-bug.md template enforces 6 mandatory fields; one-bug cap explicit |
| T-11-17 | Elevation of privilege | Wide-blast bug-fix bypassing phase boundary | mitigate | Human gate explicitly invokes `feedback_route_to_proper_phase_when_blast_radius_is_wide.md` — reviewer can divert to a new phase |
| T-11-SC | Tampering | npm/pip/cargo installs | n/a | Gradle + lcc only — no package-manager installs in this plan |
</threat_model>

<verification>
  - Log exists and contains a build outcome marker.
  - named-bug.md exists with 6 mandatory fields (or declares NONE).
  - Human gate approves the scope before Plan 11-10 runs.
</verification>

<success_criteria>
  - First buildRom log captured verbatim.
  - Exactly ONE codegen bug named (or NONE confirmed in Branch C).
  - Plan 11-10 has a concrete, bounded fix specification.
</success_criteria>

<output>
Create `.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-09-SUMMARY.md` with: buildRom exit code, log path, named-bug class, file/anchor citations, human-gate outcome.
</output>
