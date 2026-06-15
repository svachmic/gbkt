# Phase 20: Codegen Fixes — Banks and Sprite Transparency - Context

**Gathered:** 2026-06-14
**Status:** Ready for planning

<domain>
## Phase Boundary

Produce formal Phase-20 closure evidence for the **banks trio** (SEED-014/015/016, FIX-03)
and the **tRNS sprite-outline** defect (SEED-PHASE-13-SPRITE-OUTLINE, FIX-04) — all four
already dispositioned **VERIFIED-ALREADY-FIXED** by Phase 16 triage. This is a
**confirmation / regression-guard phase, not a new-fix phase** — the direct mirror of
Phase 19's metasprite-cluster closure.

Deliverables:
- A standalone `20-AUDIT-FIX-03.md` mapping the three banks seeds to their existing
  `BanksEmissionTest.kt` sentinel assertions (INV-2/INV-5/INV-6, Anchor 4), gated on a
  fresh GREEN run that re-verifies the `hasZoneSceneBinder` guard sufficiency for SEED-014.
- Two HEAD runtime screenshots (D-08 visual oracle): the sprite-outline rendering clean
  (FIX-04) and a platformer-template player-transparency no-regression twin shot.
- Per-commit byte-identity diffs on affected examples plus one full 7-example byte-identity
  sweep at phase close (Success Criterion 5).

**No production codegen change is expected.** Any C-output drift is a regression signal.

</domain>

<decisions>
## Implementation Decisions

### FIX-03 — Banks Trio Evidence & Re-Verify Gate
- **D-01:** Produce a **standalone `20-AUDIT-FIX-03.md`** in the phase dir (mirrors Phase 19's
  `19-AUDIT-FIX-02.md`). Table columns: seed → guarding test file → assertion name →
  existing-or-newly-authored → reverted-fix scenario the guard would catch. Mapping per
  triage: SEED-014 → `BanksEmissionTest.kt` INV-2 (`_bkg_tiles_load_banked` SWITCH_ROM
  sequence) + INV-6; SEED-015 → INV-5 (`title_enter_trampoline` section comment retains
  `title_enter`); SEED-016 → Anchor 4 @Test. Kept separate from VERIFICATION.md so it is a
  clean, checkable acceptance-criteria deliverable.
- **D-02 (ordering gate, locked):** **Re-verify SEED-014 FIRST** — run `BanksEmissionTest`
  fresh to GREEN to confirm the `hasZoneSceneBinder` guard (`GBDKPipeline.kt`) already
  satisfies INV-2/INV-6 on current master, *before* authoring any guard-gap work. The
  roadmap and REQUIREMENTS.md both flag this as the explicit first step ("the
  `hasZoneSceneBinder` guard may already satisfy it on master").
- **D-03:** Audit existing coverage FIRST; author new guards only where a seed has no
  existing guarding assertion — **no duplicate coverage** (Phase 19 D-05 pattern). Each new
  guard asserts the fixed behavior on current code (GREEN) and carries a comment naming the
  reverted-fix scenario it would catch (RED-by-design). No revert→RED demonstration required.

### FIX-04 — tRNS Sprite-Outline Visual Oracle
- **D-04:** Capture the D-08 visual oracle by **reusing/extending the JVM `*UatTest`
  StepAgent `captureAndRename()` harness** (Phase 19 D-01), emitting PNGs to the phase
  `evidence/` dir. Two screenshots required:
  1. The **sprite-outline** rendering clean at HEAD, captured from the **metasprites
     example** (where the Phase 13.6 tRNS auto-route lives) — confirms Success Criterion 3.
  2. A **platformer-template player-transparency** twin shot confirming no regression —
     Success Criterion 4.
  Chosen for repeatability, commit-traceability, and determinism over interactive MCP
  capture; the MCP `gbkt-emulator` server wraps the same StepAgent API, so JVM-tier and
  MCP-tier results are equivalent.
- **D-05 (constraint, locked):** Captures MUST run in each example's correct target mode —
  `gbcMode=true` with the `.noi` symFile for any GBC-target example (the platformer-template
  is GBC; verify the metasprites example's target before capture)
  ([[learning_platformer_mcp_needs_gbc_mode]]). The ROM MUST be rebuilt **clean immediately
  before capture** ([[feedback_rom_build_smoke_test_for_codegen_phases]]).

### Byte-Identity Oracle (Success Criterion 5)
- **D-06:** Satisfy Criterion 5 with a **two-tier** approach:
  1. **Per-commit:** procedural same-session before/after hash diff on the **affected
     examples only** (banks, metasprites, platformer-template) — gives per-commit
     attribution so any drift is trivially bisectable.
  2. **Phase close:** **one full 7-example byte-identity sweep** to satisfy Criterion 5's
     coverage intent.
  This interprets Criterion 5's "after every commit" as *per-commit attribution on the
  affected set + a single full-coverage proof at close*, rather than rebuilding all 7 ROMs
  after every single commit. Rationale: this is a no-codegen-change phase, so only the
  affected examples can plausibly drift; pong's known toolchain non-determinism
  ([[project_pong_toolchain_nondeterminism]]) makes a literal per-commit full sweep both
  expensive and noisy (pong is PASS\*). **The verifier should accept the final full sweep +
  per-commit affected diffs as satisfying Criterion 5.**

### Commit Discipline (locked, inherited from Phase 19)
- **D-07:** Every Phase 20 commit contains only banks/tRNS confirmation work (evidence,
  audit doc, emission tests, docs) — **zero S3776 / PR-#77 cognitive-complexity refactors
  interleaved**, so the byte-identity oracle can attribute any C-output change unambiguously
  ([[project_18_hardening_pr77]]). Commit messages scope each change to Phase 20 / FIX-03 /
  FIX-04.
- **D-08 (constraint):** Executors must run `:module:spotlessApply :module:detekt` per-commit
  — `:module:test` and the pre-commit hook do NOT run them
  ([[project_executor_gate_misses_spotless_detekt]]).

### Claude's Discretion
- Exact test method/assertion names, evidence PNG filenames, the precise hashing command for
  the byte-identity diffs, and whether any FIX-03 guard gap actually requires a new assertion
  (the audit may find full existing coverage) are left to the planner/executor, provided they
  meet the acceptance criteria above.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase scope (MUST read first)
- `.planning/ROADMAP.md` §"Phase 20: Codegen Fixes — Banks and Sprite Transparency" — Goal,
  D-11 triage note, 5 success criteria
- `.planning/REQUIREMENTS.md` — FIX-03 and FIX-04 definitions + triage dispositions
- `.planning/phases/16-seed-triage/TRIAGE.md` — Disposition of SEED-014/015/016 and
  SEED-PHASE-13-SPRITE-OUTLINE as VERIFIED-ALREADY-FIXED; source-of-truth for each seed's
  fix location

### Phase 19 precedent (the direct mirror — adapt, don't reinvent)
- `.planning/phases/19-codegen-fixes-metasprite-cluster/19-CONTEXT.md` — D-01/D-04/D-05/D-06/
  D-07/D-08/D-09 patterns this phase inherits (UAT harness, audit-first guards, standalone
  audit doc, procedural byte-identity diff, commit discipline)
- `.planning/phases/19-codegen-fixes-metasprite-cluster/19-AUDIT-FIX-02.md` — the audit-doc
  format `20-AUDIT-FIX-03.md` should mirror

### Existing test harnesses to reuse/extend
- `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt` —
  INV-2 (line ~200), INV-5 (line ~408), Anchor 4, and other banks sentinels; the FIX-03
  re-verify-first gate (D-02) and audit mapping (D-01) target these assertions
- `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt` —
  StepAgent UAT pattern for the banks example
- `gbkt-examples/metasprites/.../MetaspriteUatTest.kt` — StepAgent `captureAndRename()`
  screenshot harness (FIX-04 sprite-outline capture, D-04)
- `gbkt-examples/platformer-template/.../**UatTest.kt` — platformer player-transparency twin
  shot harness (FIX-04, D-04); GBC mode required (D-05)
- `gbkt-backend-gbdk/.../codegen/pipeline/GBDKPipeline.kt:~1428` — `hasZoneSceneBinder` guard
  (SEED-014 re-verify target, D-02)

### Methodology gates
- `.planning/verifier-gates.md` — Visual Evidence Rule (FIX-04 closure and ROM render require
  runtime screenshots, not variable assertions)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `BanksEmissionTest.kt`: already contains the INV-2/INV-5/INV-6 sentinels + Anchor 4 that
  the triage confirmed GREEN — FIX-03 is an audit-and-gate over these, not new fix work.
- JVM `*UatTest` StepAgent + `captureAndRename()`: drives the built ROM and emits PNGs to the
  evidence dir — the FIX-04 visual-oracle capture mechanism (D-04).
- Phase 19's `19-AUDIT-FIX-02.md`: a ready template for `20-AUDIT-FIX-03.md`.

### Established Patterns
- Phase 16/19 evidence layout: per-seed directory with `screenshot.png` + `capture-note.txt`
  (visual) / `main-c-excerpt.txt` (structural). Phase 20 evidence should follow the same
  per-seed convention under its own `evidence/` dir.
- Emission guards assert against generated C output via JVM tests; RED-by-design documented
  in a comment, no revert demonstration.
- Procedural same-session before/after byte-identity diff (Phase 19 D-07) — robust to
  toolchain rebuild non-determinism vs. maintained `main.c` baselines.

### Integration Points
- Banks sentinels run via `./gradlew :gbkt-examples:banks:test`; metasprites/platformer
  captures run via their example modules' UAT test source sets.
- Byte-identity diffs read `build/gbkt/generated/` output — require a clean `:buildRom` /
  `generateC` before sampling (staleness caveat).

</code_context>

<specifics>
## Specific Ideas

- The MCP `gbkt-emulator` server wraps the same `StepAgent` API used by the JVM UAT harness,
  so the chosen JVM-tier capture is deterministically equivalent to an MCP-tier capture — no
  need to maintain two capture paths.
- pong's `.gb` hashes non-deterministically every rebuild even from the same commit
  ([[project_pong_toolchain_nondeterminism]]) — flag pong as PASS\* in the final 7-example
  sweep; do not re-investigate.
- If any FIX-03 guard touches plugin fixtures, use `pluginTest` (not `:gbkt-gradle-plugin:test`)
  — it has a known publish/test ordering race; verify via two invocations if used.

</specifics>

<deferred>
## Deferred Ideas

- Platformer `cEmit()` escapes and remaining DSL/tooling seeds → Phase 21 (FIX-05/FIX-06).
- Merging PR #77 (S3776 cognitive-complexity burn-down) → held open until Phases 19/20/21
  complete; not Phase 20 work.

### Reviewed Todos (not folded)
The `cross_reference_todos` step surfaced these; all are **new robustness / API-design work**,
not verification work, so they are out of scope for this no-codegen-change confirmation phase:
- `13.8-palette-bank-codegen-followups.md` (WR-01/02/03) — palette/bank codegen robustness
  (comment-vs-code multi-zone clarification, sub-palette slot-collision guard, RGB555 range
  check). Advisory deferred debt; touching it would risk byte-identity drift. → keep pending,
  suitable for any future palette-pipeline touch / v0.2.0.
- `configbuilder-cartridge-setter-api-consistency.md` — ConfigBuilder setter convention; API
  design, unrelated to FIX-03/FIX-04.
- `easetozero-oscillates-when-by-greater-than-one.md` — tween math defect; unrelated.
- `orelse-may-attach-to-wrap-guard-ifop.md` — DSL wrap-guard binding; unrelated.
- `compilerom-silent-mbc5-fallback-warning.md` — cartridge metadata warning; unrelated.

</deferred>

---

*Phase: 20-codegen-fixes-banks-and-sprite-transparency*
*Context gathered: 2026-06-14*
