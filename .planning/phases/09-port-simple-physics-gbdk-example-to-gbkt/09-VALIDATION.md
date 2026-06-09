---
phase: 9
slug: port-simple-physics-gbdk-example-to-gbkt
status: draft
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-13
updated: 2026-05-13
---

# Phase 9 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Anchored to the three-signal contract (codegen quality, DSL value, UAT verdict) from CONTEXT.md and the Validation Architecture section of RESEARCH.md.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Kotlin test — `testImplementation(kotlin("test"))`) |
| **Config file** | none — JUnit 5 auto-discovery |
| **Quick run command** | `./gradlew :gbkt-examples:simple-physics:test` |
| **Full suite command** | `./gradlew :gbkt-examples:simple-physics:test :gbkt-backend-gbdk:test` |
| **Estimated runtime** | ~30 seconds (Quick) / ~3 min (Full) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :gbkt-examples:simple-physics:test`
- **After every plan wave:** Run `./gradlew :gbkt-examples:simple-physics:test :gbkt-backend-gbdk:test`
- **Before `/gsd-verify-work`:** Full suite green + 3 MCP behavior screenshots captured
- **Max feedback latency:** ~30 seconds (Quick run)

---

## Per-Task Verification Map

REQUIREMENTS.md does not assign REQ-IDs to Phase 9. D-IDs from CONTEXT.md serve as requirement anchors for this phase (see RESEARCH.md "Phase Requirements → Test Map"). Planner has mapped plans to D-IDs explicitly (see plan frontmatter `requirements` fields). Canonical D-IDs from CONTEXT.md are D-01..D-11 plus D-overfitting-1/2/3 (anti-overfitting rails enforced via acceptance criteria).

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 09-01-* | 01 | 1 | D-01, D-02, D-03 (UAT first) | — | N/A | docs/MCP harness | `cat .planning/phases/09-.../09-UAT.md` | ✅ co-located | ⬜ pending |
| 09-02-* | 02 | 1 | D-07, D-09, D-10 (gbkt-examples convention + PNG asset + evidence location) | — | N/A | build scaffold | `./gradlew :gbkt-examples:simple-physics:tasks` | ✅ co-located | ⬜ pending |
| 09-03-* | 03 | 2 | D-06, D-08, D-11 (single play scene + i16Var/shr4 + 3 emission invariants) | — | N/A | JVM emission + IR + game | `./gradlew :gbkt-examples:simple-physics:test --tests "*.SimplePhysicsEmissionTest"` | ✅ co-located W0 in Task 2 | ⬜ pending |
| 09-04-* | 04 | 3 | D-04, D-05 (named codegen bug fix + surplus seeds) | — | N/A | JVM RED→GREEN | `./gradlew :gbkt-backend-gbdk:test :gbkt-examples:simple-physics:test` | ✅ written by Plan 03 | ⬜ pending |
| 09-05-* | 05 | 4 | D-09, D-10 (ROM size + evidence location) | — | N/A | Build log + size measurement | `./gradlew :gbkt-examples:simple-physics:buildRom && stat -f%z build/.../simple-physics.gb` | ✅ scaffolded by Plan 02 | ⬜ pending |
| 09-06-* | 06 | 5 | D-01, D-02 (3 UAT behaviors + screenshots) | — | N/A | Runtime MCP screenshots | `./gradlew :gbkt-examples:simple-physics:runEmulator` + `emulator_screenshot` calls in PLAYBOOK | ✅ contract by Plan 01 | ⬜ pending |
| 09-07-* | 07 | 6 | D-05, D-09, D-10 (C-diff + seeds + ROADMAP close) | — | N/A | docs | `cat .planning/phases/09-.../evidence/oracle-comparison.md` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Visual evidence rule (CLAUDE.md):** All D-01.* SCs are runtime-visible — they MUST include `emulator_screenshot(path)` evidence in `.planning/phases/09-.../evidence/uat-screenshots/`. Variable assertions alone are insufficient (see Pitfall 6 in RESEARCH.md, Phase 07.4 history). Plan 06 explicitly verifies per-behavior visual confirmation (one-line sprite-position note in `uat-verdict.md`).

---

## Wave 0 Requirements

**Wave 0 strategy for Phase 9:** Co-located test creation. The three JVM test scaffolds (SimplePhysicsIRTest, SimplePhysicsEmissionTest, SimplePhysicsGameTest) are written together with their RED implementation inside Plan 03 Task 2 — NOT pre-stubbed in a separate empty-Wave-0 plan. For a 99-line reference port targeting a single named codegen bug-fix, separate empty stubs would be ceremony, not signal. The PLAYBOOK + UAT contract IS pre-stubbed in Plan 01 (Wave 1), satisfying the "UAT-first before any DSL" structural rail (D-03).

- [x] `gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsIRTest.kt` — written + RED-able in Plan 03 Task 1 (co-located)
- [x] `gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsEmissionTest.kt` — written + RED in Plan 03 Task 2 (co-located; awk brace-walk pattern with evidence-write-before-assert ordering enforced via line-number gate)
- [x] `gbkt-examples/simple-physics/src/test/kotlin/io/github/gbkt/examples/simple_physics/SimplePhysicsGameTest.kt` — written + GREEN in Plan 03 Task 1 (co-located)
- [x] `gbkt-examples/simple-physics/PLAYBOOK.md` — Plan 01 Task 2 deliverable (Wave 1; precedes Plan 03 Wave 2)
- [x] `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/09-UAT.md` — Plan 01 Task 1 deliverable (Wave 1; precedes Plan 03 Wave 2)
- [x] `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/reference/phys.c` — Plan 02 Task 2 deliverable (Wave 1; copy of GBDK reference for diff)
- [x] `.planning/phases/09-port-simple-physics-gbdk-example-to-gbkt/evidence/reference/BUILD.md` — Plan 02 Task 2 deliverable (Wave 1; reproducible reference build instructions)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Visual parity with reference at climax frames | D-02 (visual evidence) | Screenshots compared by human eye against GBDK reference frames | Run reference + gbkt port side-by-side via mGBA; capture `evidence/uat-screenshots/{behavior}-gbkt.png` and `{behavior}-reference.png`; visual diff |
| DSL-value verdict (shorter/clearer assessment) | D-09 (three-signal artifact — C-diff component) | Subjective "is this idiomatic" judgment — informational appendix only | Side-by-side diff `phys.c` vs `build/gbkt/generated/main.c` in `evidence/oracle-comparison.md`; annotate gbkt-shorter / equal / longer regions |
| Surplus-defect seed harvest | D-05 | Discovery is opportunistic during port; each `/gsd-capture --seed` produces a seed file | At port-close: count `.planning/seeds/*` added during Phase 9; if ≥1, insert Phase 9.1 placeholder in ROADMAP in same commit |
| Per-behavior visual confirmation in uat-verdict.md | D-02 (CLAUDE.md Visual Evidence Rule) | Agent must view the PNG and write a one-line sprite-position note per behavior; mere `test -s` file-size check is insufficient | Plan 06 acceptance criteria require three `Visual confirmation:` lines (one per behavior) in `evidence/uat-verdict.md`; automated grep gate enforces presence |

---

## Validation Sign-Off

- [x] All Plan 3+ tasks have `<automated>` verify or Wave 0 dependencies — Plan 03 Task 2 co-locates Wave 0 test creation with RED execution; line-number evidence-before-assert gate added
- [x] Sampling continuity: no 3 consecutive tasks without automated verify — every task in every plan has an `<automated>` block
- [x] Wave 0 covers all MISSING references (test scaffolds + PLAYBOOK + UAT) — see "Wave 0 strategy" rationale above; co-location chosen over separate empty-stub plan
- [x] No watch-mode flags in any test command — all gradle invocations use `--quiet`, no `-t`/`--continuous`
- [x] Feedback latency < 30 seconds (Quick run) — `./gradlew :gbkt-examples:simple-physics:test` quick run estimated ~30s
- [x] Each D-01.* behavior has a screenshot evidence target listed — Plan 01 09-UAT.md test blocks declare `evidence/uat-screenshots/{behavior}.png` per behavior
- [x] Named codegen bug fix (D-04/D-05) has a RED test before GREEN fix — Plan 03 Task 2's SimplePhysicsEmissionTest D-11.1 is RED at HEAD; Plan 04 flips GREEN
- [x] `nyquist_compliant: true` set in frontmatter

### Sign-Off Rationale (Wave 0 Co-Location)

**Why nyquist_compliant: true with no separate Wave 0 plan?**

Wave 0 test scaffolds are co-located with their RED implementation inside Plan 03 Task 2 (`SimplePhysicsEmissionTest.kt`, `SimplePhysicsIRTest.kt`, `SimplePhysicsGameTest.kt`). Plan 03 Task 2 acceptance criteria mandate evidence-write-before-assert ordering (structurally enforced via a line-number gate that walks each `@Test` function body and confirms `writeText` line numbers precede the first `assertTrue`/`assertFalse` call) and awk brace-walk extraction (`extractFunctionBody(bank1C, "play_frame")` — scope-level grep gate per CLAUDE.md). Co-location is the right granularity for a 99-line reference port — a separate empty-stub Wave 0 plan would be ceremony, not signal, for a phase whose entire scope is one DSL port + one named codegen fix.

The Nyquist Check 8e gate (RED test before GREEN fix) is satisfied: Plan 03 Task 2 writes SimplePhysicsEmissionTest with D-11.1 / D-11.3 RED at HEAD; Plan 04 is the GREEN flip; the RED→GREEN transition is the binding evidence per `feedback_quality_over_shortcuts` in MEMORY.md.

The UAT contract (Plan 01) and module scaffold + reference artifacts (Plan 02) ARE pre-stubbed before any DSL code is written — that is the D-03 (UAT-first) structural rail, enforced by Plan 03's `depends_on: [01, 02]`.

**Approval:** approved 2026-05-13 (planner — pending checker re-verification)
