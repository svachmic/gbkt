---
phase: 11-port-banks-gbdk-example-to-gbkt
plan: 14
type: execute
wave: 6
depends_on: ["11-06", "11-07", "11-08", "11-10", "11-11", "11-12", "11-13"]
files_modified:
  - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/final-buildrom.log
  - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/handoff.md
  - .planning/seeds/
  - .planning/ROADMAP.md
autonomous: false
requirements:
  - BANK-FINAL-SMOKE
  - BANK-SEED-SURPLUS
  - BANK-PHASE13-EDIT
user_setup: []
must_haves:
  truths:
    - "`./gradlew :gbkt-examples:banks:clean :gbkt-examples:banks:buildRom` exits 0 with no warnings, no SDCC errors, no MBC5 traps"
    - "All 4 UAT anchors marked `passed` in `gbkt-examples/banks/11-UAT.md`"
    - "All 4 JVM-tier invariants GREEN; all 8 IR-shape tests GREEN"
    - "If >=1 surplus codegen defect: a Phase 11.1 placeholder is inserted in ROADMAP.md AND it is explicitly marked TERMINAL (no Phase 11.1.1)"
    - "If 0 surplus defects: no Phase 11.1 placeholder created"
    - "If a framework-shaping DSL gap surfaced: `/gsd-phase --edit 13` performed AND ROADMAP §Phase 13 §Requirements list updated"
    - "`evidence/handoff.md` summarises all 9 verdicts (4 UAT + 4 INV + 1 4th-signal) + named-bug-fix outcome"
  artifacts:
    - path: ".planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/final-buildrom.log"
      provides: "Final clean buildRom log (BLOCKING per CONTEXT D-20)"
      contains: "BUILD SUCCESSFUL"
    - path: ".planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/handoff.md"
      provides: "Verification handoff doc"
      contains: "## Anchor 1"
  key_links:
    - from: "evidence/handoff.md"
      to: "11-UAT.md + evidence/oracle-comparison.md + evidence/anchor3-cartridge-byte.txt + evidence/named-bug.md"
      via: "single-page summary"
      pattern: "Anchor [1-4]|INV-[1-4]|4th-signal"
---

<objective>
Close Phase 11: run the BLOCKING final clean buildRom smoke test per CONTEXT D-20 + memory `feedback_rom_build_smoke_test_for_codegen_phases.md`, sweep surplus codegen defects into seeds, conditionally insert a TERMINAL Phase 11.1 placeholder, edit Phase 13 if framework-shaping gaps surfaced, and produce the verification handoff doc.

Purpose: D-20 makes the verifier-gate mandatory for codegen phases. D-14 + D-19 require conditional terminal-subphase placement (no Phase 11.1.1). D-17 routes framework-shaping DSL gaps to Phase 13 via the `gsd-phase --edit` workflow. The handoff doc condenses all 9 signals into one page for the verification step.

Output: 2 evidence files (`final-buildrom.log`, `handoff.md`), conditional ROADMAP edit, conditional seed files.
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
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/oracle-comparison.md
@.planning/ROADMAP.md
@gbkt-examples/banks/11-UAT.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: BLOCKING — final clean buildRom smoke test</name>
  <read_first>
    - 11-CONTEXT.md D-20 (verifier MUST run clean buildRom)
    - User memory `feedback_rom_build_smoke_test_for_codegen_phases.md` — JVM tests cannot see staleness; clean buildRom mandatory
    - 11-RESEARCH.md §"Validation Architecture" Tier-2 (zero warnings, zero SDCC errors, zero MBC5 traps)
  </read_first>
  <files>.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/final-buildrom.log</files>
  <action>
    1. Ensure GBDK is available (see Plan 11-09 Task 1 gating).
    2. Run clean buildRom with output capture:
       ```
       (./gradlew :gbkt-examples:banks:clean :gbkt-examples:banks:buildRom --console=plain 2>&1; echo "EXIT_CODE=$?") | tee .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/final-buildrom.log
       ```
    3. Grep the log; must NOT contain any of:
       - `warning:` (lcc warnings)
       - `error:` or `SDCC:` (compilation errors)
       - `unknown address` or `unknown value` (MBC5 trap)
       - `BUILD FAILED`
       - `undefined identifier` (would imply Plan 11-10 regression)

    4. If any pattern matches: STOP. Reopen the matching plan (most likely 11-05 or 11-10) per memory `feedback_quality_over_shortcuts.md` — do NOT proceed to handoff with a broken build.

    5. Verify ROM exists: `test -f gbkt-examples/banks/build/gbkt/output/banks.gb`.

    6. Re-run JVM tests after clean: `./gradlew :gbkt-examples:banks:test --quiet`. All tests GREEN.
  </action>
  <verify>
    <automated>test -f .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/final-buildrom.log && grep -q "BUILD SUCCESSFUL" .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/final-buildrom.log && test -f gbkt-examples/banks/build/gbkt/output/banks.gb && ./gradlew :gbkt-examples:banks:test --quiet</automated>
  </verify>
  <acceptance_criteria>
    - File `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/final-buildrom.log` exists
    - Log contains literal `BUILD SUCCESSFUL`
    - Log does NOT contain any of: `BUILD FAILED`, `undefined identifier`, `unknown address`, `unknown value`, `SDCC:`, `warning:`, `error:`
    - File `gbkt-examples/banks/build/gbkt/output/banks.gb` exists
    - `./gradlew :gbkt-examples:banks:test --quiet` exits 0 (full test suite GREEN after clean rebuild)
  </acceptance_criteria>
  <done>Clean buildRom passes; all tests GREEN against the freshly-built ROM.</done>
</task>

<task type="auto">
  <name>Task 2: Sweep surplus codegen defects into seeds</name>
  <read_first>
    - 11-CONTEXT.md D-14 (surplus defects → seeds via `/gsd-capture --seed`)
    - 11-CONTEXT.md D-19 (Phase 11.1, if it surfaces, MUST be terminal — no 11.1.1)
    - 11-CONTEXT.md scope cap (ONE example, ONE named codegen bug-fix — anything else is surplus)
    - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/first-buildrom.log + evidence/final-buildrom.log (re-scan for `warning:` or any non-blocking advisories that did NOT block the named bug)
    - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/named-bug.md (the ONE bug — anything else is surplus)
    - Existing seed naming convention: `ls .planning/seeds/SEED-*.md | tail -5` to find the next available SEED-NNN number
    - User memory `feedback_many_small_plans_terminal_subphase.md` (terminal-subphase rule)
  </read_first>
  <files>
    - .planning/seeds/SEED-NNN-*.md (conditional — only if surplus exists)
  </files>
  <action>
    1. Re-scan both buildrom logs (first + final) for non-named issues:
       - Compile warnings that did NOT block the named bug
       - Spurious lcc output that was not the named bug
       - Tests that were flaky or had assumeTrue skip outcomes
       - Generated C oddities (e.g. duplicated functions, oversized banks below threshold but suspicious)

    2. For EACH surplus item, capture a seed:
       - Choose the next SEED-NNN number based on existing seed inventory
       - File path: `.planning/seeds/SEED-NNN-banks-<short-slug>.md`
       - File contents (minimal seed template):
         ```markdown
         # SEED-NNN: <one-line title>

         **Surfaced by:** Phase 11 (banks port close — Plan 11-14)
         **Evidence:** `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/<log-or-artifact>`
         **Symptom:** <one paragraph describing what was observed>
         **Hypothesis:** <one paragraph of root-cause speculation, if any>
         **Blast radius:** <local|wide|unknown>
         **Routing:** <Phase 11.1 follow-up | Phase 13 framework-shaping | future-port>
         ```

    3. Count surplus seeds:
       - If 0 surplus → skip Task 3 (no Phase 11.1 placeholder needed).
       - If >=1 surplus → proceed to Task 3 with the list of seeds to bundle.

    4. Use `gsd-sdk query capture-seed` (or the documented `/gsd-capture --seed` workflow) if available; otherwise write seed files directly. The seed files MUST exist before Task 3 references them.

    Do NOT bundle the named bug (already fixed in Plan 11-10) into a seed.
    Do NOT bundle anchor failures (those would have blocked Plan 11-11/12/13 and prevented reaching this plan).
  </action>
  <verify>
    <automated>SURPLUS_COUNT=$(ls .planning/seeds/SEED-*-banks-*.md 2>/dev/null | wc -l); echo "Surplus seeds captured: $SURPLUS_COUNT"; true</automated>
  </verify>
  <acceptance_criteria>
    - For each surplus item identified in either buildrom log: a file `.planning/seeds/SEED-NNN-banks-<slug>.md` exists
    - Each seed file contains the literal headings `**Surfaced by:**`, `**Symptom:**`, `**Routing:**`
    - The named bug from Plan 11-09 is NOT duplicated as a seed (it's already fixed in 11-10)
    - If 0 surplus: no seed files created in `.planning/seeds/SEED-*-banks-*.md`; this is a valid outcome
  </acceptance_criteria>
  <done>Surplus defect ledger captured; Task 3's conditional decision can be made deterministically.</done>
</task>

<task type="auto">
  <name>Task 3: CONDITIONAL — insert Phase 11.1 placeholder (only if surplus seeds exist)</name>
  <read_first>
    - .planning/seeds/SEED-*-banks-*.md (output of Task 2 — count >0 triggers this task)
    - 11-CONTEXT.md D-19 (Phase 11.1 MUST be terminal)
    - User memory `feedback_many_small_plans_terminal_subphase.md` — terminal closer cluster rule
    - User memory `feedback_gsd_phase_insert_after_decimal.md` — pass integer 11 to `gsd-phase insert`, NOT 11.0 / 11.x; otherwise creates 11.0.1 instead of 11.1
    - .planning/phases/10.2-port-spritetile-final-defect-closer/ (existing terminal-closer example for shape reference)
    - .planning/ROADMAP.md — examine the Phase 10.1 / 10.2 inserted entries (lines 1246-1326) for the format the placeholder should follow
  </read_first>
  <files>.planning/ROADMAP.md</files>
  <action>
    Run this task ONLY if `ls .planning/seeds/SEED-*-banks-*.md 2>/dev/null | wc -l` returns >0.

    1. Use `gsd-sdk` or direct ROADMAP edit to insert a Phase 11.1 placeholder entry. If the SDK exposes `gsd-sdk query phase.insert 11` (or `gsd-phase insert --after 11`), use that. Per user memory `feedback_gsd_phase_insert_after_decimal.md`, the argument MUST be the integer `11` (NOT `11.0` or `11.x`).

    2. The placeholder entry MUST contain:
       - Heading: `### Phase 11.1: banks-port surplus codegen defects (INSERTED, TERMINAL)`
       - `**Goal:**` — one sentence: "Close the surplus codegen defects bundle surfaced by Phase 11 (banks port) — listed under `.planning/seeds/SEED-*-banks-*.md`. TERMINAL closer per CONTEXT D-19 + user memory `feedback_many_small_plans_terminal_subphase.md`: no Phase 11.1.1 / 11.2."
       - `**Requirements:**` — `TBD (define during /gsd-discuss-phase 11.1)`
       - `**Depends on:**` — `Phase 11`
       - `**Architecture:**` — `Bundle of N surplus codegen defects` (replace N with actual seed count)
       - `**Success Criteria:**` — `Every SEED-*-banks-* either fixed (with RED→GREEN cycle), routed to Phase 13 (framework-shaping), or marked won't-fix with explicit rationale.`
       - `**Hard scope cap:**` — `Terminal closer. No follow-up subphases. If a seed has wide blast radius (>1 file outside gbkt-backend-gbdk or new IR types), route to Phase 13 instead.`
       - `**Plans:**` — `TBD (run /gsd-discuss-phase 11.1 → /gsd-plan-phase 11.1)`
       - List of seed IDs the phase closes:
         ```
         Seeds to close:
         - SEED-NNN-banks-<slug-1>
         - SEED-NNN-banks-<slug-2>
         ...
         ```

    3. Verify the entry was inserted at the correct position (after Phase 11, before Phase 12 — per Phase 10.1/10.2 precedent, may be after Phase 11 even if numeric ordering would suggest between 11 and 11.5). If the SDK placed it incorrectly (e.g., created `11.0.1` instead of `11.1`), fix manually per the user memory rule.

    4. Update ROADMAP.md's table of contents / index if one exists at the top.

    If Task 2 captured 0 surplus seeds, SKIP this task entirely. Write a one-line note in `evidence/handoff.md` (Task 5): "0 surplus seeds — no Phase 11.1 placeholder created."
  </action>
  <verify>
    <automated>SEED_COUNT=$(ls .planning/seeds/SEED-*-banks-*.md 2>/dev/null | wc -l); if [ "$SEED_COUNT" -gt 0 ]; then grep -q "### Phase 11.1:" .planning/ROADMAP.md && grep -q "TERMINAL" .planning/ROADMAP.md; else true; fi</automated>
  </verify>
  <acceptance_criteria>
    - If surplus seed count >0: ROADMAP.md contains literal `### Phase 11.1:` heading AND literal `TERMINAL` in the same Phase 11.1 block
    - If surplus seed count =0: ROADMAP.md is unchanged from prior state (no spurious 11.1 entry)
    - If 11.1 inserted: it lists every SEED-*-banks-* file by name under the "Seeds to close:" list
    - ROADMAP.md does NOT contain `### Phase 11.1.1` or `### Phase 11.2` (terminal rule per D-19)
  </acceptance_criteria>
  <done>Phase 11.1 placeholder conditionally inserted (or not, if zero surplus); D-19 terminal rule enforced.</done>
</task>

<task type="auto">
  <name>Task 4: CONDITIONAL — Phase 13 edits for framework-shaping gaps</name>
  <read_first>
    - 11-CONTEXT.md D-17 (framework-shaping DSL gaps → Phase 13 via `/gsd-phase --edit 13`)
    - 11-CONTEXT.md §Deferred Ideas — items already routed to Phase 13 (typed `Cartridge` enum, SRAM-bank-assignment DSL); do NOT re-route these
    - .planning/ROADMAP.md §"Phase 13" lines 1355-1391 (current requirements list; new items append numbered)
    - The buildrom logs + Plan 11-09 named-bug.md (look for hints of framework-shaping gaps: "the DSL forced me to ..." patterns, "I had to escape via raw() because ..." patterns)
    - User memory `feedback_route_to_proper_phase_when_blast_radius_is_wide.md`
  </read_first>
  <files>.planning/ROADMAP.md</files>
  <action>
    1. Audit Phase 11 deliverables for framework-shaping DSL gaps. Candidates:
       - The two-channel ramBanks wiring (DSL `config { ramBanks = 2 }` + Gradle `gbkt { ramBanks.set(2) }`) — RESEARCH §Pitfall 1. The dual-source is fragile; a single source of truth would be a framework primitive.
       - Magic-string cartridge config (already a Phase 13 item — DO NOT re-route).
       - SaveDataBuilder's lack of `save.write()` / `save.load()` fluent surface (DSL currently uses `triggerSystem("saves")` magic string; the typed surface would mirror property delegates per `feedback_no_magic_strings.md`).

    2. For each candidate that is NEW (not already in Phase 13's requirements list):
       - Append a new numbered item to ROADMAP §"Phase 13" §"Additional requirements (from Phase 11 banks audit — 2026-05-19):" subsection
       - Format mirrors existing Phase 13 items (lines 1367-1377): item number, **bold name** — description, file-line citation when available, surfaced-at: `Banks.kt:<line>` or `gbkt-build.properties:<line>` etc.

    3. If 0 framework-shaping gaps surface: SKIP. Note this in handoff.md: "0 Phase 13 edits needed — port surfaced no new framework-shaping gaps."

    Do NOT re-add typed `Cartridge` enum (already item 1) or SRAM-bank-DSL (CONTEXT D-17 says only if a future port needs it AND SaveDataBuilder doesn't already cover it — Phase 11 confirmed SaveDataBuilder covers SRAM via SaveDataBuilder, so no re-route).
  </action>
  <verify>
    <automated>grep -c "^### Phase 13" .planning/ROADMAP.md | grep -qE "^1$"</automated>
  </verify>
  <acceptance_criteria>
    - ROADMAP.md still has exactly one `### Phase 13` heading (no duplicate phase insertion)
    - If gaps surfaced: ROADMAP §Phase 13 contains a new "(from Phase 11 banks audit — 2026-05-19)" subsection with at least one numbered item
    - If 0 gaps: ROADMAP.md §Phase 13 unchanged
    - Newly added items (if any) cite a specific file path (e.g., `Banks.kt:<line>` or `gbkt-build.properties:<line>`)
  </acceptance_criteria>
  <done>Framework-shaping audit complete; Phase 13's rolling-collector backlog is updated (or confirmed unchanged).</done>
</task>

<task type="auto">
  <name>Task 5: Write evidence/handoff.md (one-page verification summary)</name>
  <read_first>
    - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/final-buildrom.log (Task 1 output)
    - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/named-bug.md (Plan 11-09 output)
    - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/anchor3-cartridge-byte.txt (Plan 11-13 output)
    - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/oracle-comparison.md (Plan 11-13 output)
    - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor1-play-scene.png + anchor2-tilemap.png (Plan 11-11 outputs)
    - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/anchor4-sram-persistence.txt (Plan 11-12 output)
    - gbkt-examples/banks/11-UAT.md (anchor `Result:` fields)
    - 11-CONTEXT.md §"Three-signal + 4th-signal artifact" (D-15)
  </read_first>
  <files>.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/handoff.md</files>
  <action>
    Author the verification handoff doc. Structure:

    ```markdown
    # Phase 11 Verification Handoff

    **Phase:** 11 — Port banks GBDK example to gbkt
    **Closed:** <date>
    **Plan-checker entry point:** this file

    ## Quick Verdict Table

    | Signal | Status | Evidence |
    |--------|--------|----------|
    | Anchor 1 (cross-bank scene nav) | <PASS/FAIL> | evidence/uat-screenshots/anchor1-play-scene.png |
    | Anchor 2 (zone tilemap visible) | <PASS/FAIL> | evidence/uat-screenshots/anchor2-tilemap.png |
    | Anchor 3 (MBC5 byte 0x0147) | <PASS/FAIL> | evidence/anchor3-cartridge-byte.txt |
    | Anchor 4 (SRAM GBST round-trip) | <PASS/FAIL> | evidence/anchor4-sram-persistence.txt |
    | INV-1 (BANKED keyword) | <PASS/FAIL> | evidence/tier1-shape/inv1-*.txt |
    | INV-2 (SWITCH_ROM wrapper) | <PASS/FAIL> | evidence/tier1-shape/inv2-*.txt |
    | INV-3 (mbcType propagation) | <PASS/FAIL> | evidence/tier1-shape/inv3-*.txt |
    | INV-4 (SRAM write path + trigger_saves) | <PASS/FAIL> | evidence/tier1-shape/inv4-*.txt |
    | 4th-signal (.noi bank thresholds) | <PASS/FAIL> | evidence/oracle-comparison.md |
    | BLOCKING smoke test | <PASS/FAIL> | evidence/final-buildrom.log |
    | Named codegen bug | <CLOSED/OPEN> | evidence/named-bug.md |

    ## Detail Sections

    ### Anchor 1
    <copy result line from 11-UAT.md>

    ### Anchor 2
    <copy result line from 11-UAT.md>

    ### Anchor 3
    <inline the python3 output from anchor3-cartridge-byte.txt>

    ### Anchor 4
    <inline pre/post hex from anchor4-sram-persistence.txt>

    ### 4th-signal
    <inline the bank-size table from oracle-comparison.md>

    ### Named codegen bug
    <inline content of named-bug.md>

    ### Surplus seeds (Phase 11.1 cluster, if any)
    <list each SEED-*-banks-*.md filename + symptom one-liner; if 0, write "None — no Phase 11.1 placeholder created.">

    ### Phase 13 audit edits
    <list new requirements appended to Phase 13 (Task 4 output); if 0, write "None — port surfaced no framework-shaping gaps.">

    ## Phase Verdict

    **<GREEN | RED>** — <one-sentence justification>

    ## ROADMAP follow-up state

    - Phase 11.1 placeholder: <created N seeds | not created — 0 surplus>
    - Phase 13 edits: <N new items | unchanged>
    ```

    Fill in every `<...>` placeholder by reading the cited evidence file. Per memory `feedback_quality_over_shortcuts.md`, do NOT fabricate — if any anchor's evidence is missing or FAIL, reflect that honestly. The phase verdict should be RED if any anchor or INV is FAIL or if the BLOCKING smoke test failed.
  </action>
  <verify>
    <automated>test -f .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/handoff.md && grep -c "PASS\|FAIL" .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/handoff.md | awk '{ if ($1 >= 9) exit 0; else exit 1 }'</automated>
  </verify>
  <acceptance_criteria>
    - File `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/handoff.md` exists
    - File contains all 9 signal rows in the verdict table (4 anchors + 4 INVs + 1 4th-signal)
    - File contains literal `BLOCKING smoke test` row + the named-bug row (11 rows total)
    - File contains a `## Phase Verdict` heading with one of `**GREEN**` or `**RED**` immediately following
    - File contains a section for surplus seeds (with content matching Task 2's output)
    - File contains a section for Phase 13 audit edits (with content matching Task 4's output)
  </acceptance_criteria>
  <done>Verification handoff doc complete; the verifier can flip the phase verdict from one file.</done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 6: Human gate — final phase-close approval</name>
  <what-built>
    Tasks 1–5 produced:
    - `evidence/final-buildrom.log` (BLOCKING smoke test)
    - `evidence/handoff.md` (one-page verification summary)
    - Conditional `.planning/seeds/SEED-*-banks-*.md` files (surplus defects)
    - Conditional ROADMAP.md edits (Phase 11.1 placeholder + Phase 13 audit items)
  </what-built>
  <how-to-verify>
    1. Read `evidence/handoff.md` — confirm all 9 verdict rows match the underlying evidence files. Spot-check ANY claimed `PASS` by opening the evidence file.
    2. Open `evidence/uat-screenshots/anchor1-play-scene.png` and `anchor2-tilemap.png` — confirm visually (per CLAUDE.md Visual Evidence Rule + memory `feedback_visual_evidence_for_visual_truths.md`).
    3. Read `evidence/final-buildrom.log` last 30 lines — confirm `BUILD SUCCESSFUL`, no warnings.
    4. Check ROADMAP.md:
       - Phase 11 entry: `**Plans:** 14 plans` (was 0; updated by orchestrator at planning end OR manually here).
       - Phase 11.1 entry (if surplus): present AND marked TERMINAL.
       - Phase 13 audit edits (if any): listed under a "from Phase 11 banks audit" subsection.
    5. Confirm scope cap: ONE example shipped (banks/), ONE named codegen bug-fix shipped (Plan 11-10), surplus → seeds only.
    6. Run `git status` — review the diff one last time before merge.
  </how-to-verify>
  <files>.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/handoff.md, .planning/ROADMAP.md (read-only review)</files>
  <action>Human reads handoff.md, spot-checks underlying evidence files, opens the two anchor screenshots one more time per Visual Evidence Rule, and verifies ROADMAP.md state (Phase 11.1 conditional placeholder; no Phase 11.1.1; Phase 13 edits if any). Approves the close OR names a regression to re-open.</action>
  <verify><human-check>handoff.md verdict is GREEN; all 9 signal rows PASS; ROADMAP.md coherent (no duplicates, no 11.1.1).</human-check></verify>
  <done>Phase 11 closed; ready for /gsd-verify-work 11.</done>
  <resume-signal>
    Type `approved` to mark Phase 11 closed and ready for `/gsd-verify-work 11`. The verifier will read `evidence/handoff.md` as the entry point.
    Type `regressed: <reason>` if any verdict was wrong — name the plan to re-open.
    Type `route to phase: <N>` if a wide-blast surplus surfaced that should NOT live in Phase 11.1 (per memory `feedback_route_to_proper_phase_when_blast_radius_is_wide.md`).
  </resume-signal>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Phase-close artifacts → next phase planner | handoff.md drives the verifier's decision and the next phase's CONTEXT.md inputs |
| ROADMAP edits → entire project | A misplaced 11.1 / Phase 13 entry mis-routes future work |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-11-31 | Tampering | handoff.md fabricated PASS | mitigate | Human gate (Task 6) spot-checks evidence files; acceptance gate counts PASS/FAIL strings |
| T-11-32 | Repudiation | Skipping the BLOCKING smoke test | mitigate | CONTEXT D-20 + memory rule cited; Task 1 verify command runs the smoke test directly |
| T-11-33 | Elevation of privilege | Phase 11.1.1 created by mistake | mitigate | Acceptance gate forbids `### Phase 11.1.1` heading in ROADMAP.md |
| T-11-34 | Tampering | ROADMAP.md `Phase 13` duplicated by sloppy edit | mitigate | Acceptance gate requires exactly one `^### Phase 13` heading |
| T-11-35 | Information disclosure | Seed files leak internal architecture | accept | All architectural details are already public in source tree; seeds add no PII |
| T-11-SC | Tampering | npm/pip/cargo installs | n/a | No installs |
</threat_model>

<verification>
  - Final clean buildRom GREEN; ROM produced.
  - All 4 UAT anchors + 4 INVs + 4th-signal PASS in handoff.md.
  - Surplus seeds captured (or 0 confirmed); Phase 11.1 placeholder conditionally created with TERMINAL marker.
  - Phase 13 edits applied (or 0 confirmed).
  - Human gate approves.
</verification>

<success_criteria>
  - handoff.md verdict is `**GREEN**`.
  - All 11 verdict rows are PASS / CLOSED.
  - ROADMAP.md in a coherent state (no duplicate phases, no Phase 11.1.1).
</success_criteria>

<output>
Create `.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-14-SUMMARY.md` with: final smoke test outcome, handoff verdict, seed count, Phase 13 edit count, ROADMAP state.
</output>
