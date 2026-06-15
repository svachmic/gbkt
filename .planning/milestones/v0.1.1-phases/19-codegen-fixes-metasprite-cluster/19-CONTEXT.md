# Phase 19: Codegen Fixes — Metasprite Cluster - Context

**Gathered:** 2026-06-13
**Status:** Ready for planning

<domain>
## Phase Boundary

Produce formal Phase-19 closure evidence for the nine already-fixed metasprite seeds — **a confirmation / regression-guard phase, not a new-fix phase.** Deliverables: fresh HEAD runtime screenshots for the visual-parity four (SEED-004/005/006/013), an audited-and-gap-filled set of JVM emission guards for the structural five (SEED-007/008/009/010/011), a clean ROM build + smoke screenshot of the metasprites example, and a byte-identity confirmation that no production codegen drifted. No production codegen change is expected.

</domain>

<spec_lock>
## Requirements (locked via SPEC.md)

**5 requirements are locked.** See `19-SPEC.md` for full requirements, boundaries, and acceptance criteria.

Downstream agents MUST read `19-SPEC.md` before planning or implementing. Requirements are not duplicated here.

**In scope (from SPEC.md):**
- Fresh Phase-19-HEAD runtime screenshots for SEED-004/005/006/013 (FIX-01)
- An emission-guard audit document mapping SEED-007..011 to guarding assertions, plus newly authored per-seed guards only where coverage is missing (FIX-02)
- A clean `buildRom` + fresh runtime screenshot of the metasprites example at HEAD (Success Criterion 3)
- Confirmation that all 9 seeds remain in `.planning/seeds/archive/` (moved by Plan 16-10)
- JVM emission tests in `gbkt-backend-gbdk` and/or `gbkt-examples/metasprites*` modules

**Out of scope (from SPEC.md):**
- New fixes to metasprite codegen — all 9 seeds are already VERIFIED-ALREADY-FIXED
- Actual revert→RED→restore demonstrations — assert-GREEN + RED-by-design comment is sufficient
- Banks trio (SEED-014/015/016) and tRNS sprite outline — Phase 20
- Platformer and remaining DSL/tooling seeds — Phase 21
- S3776 cognitive-complexity refactors and merging PR #77 — separate workstream; PR #77 stays open until 19/20/21 complete
- Any production codegen change — would break byte-identity and signal an unexpected regression

</spec_lock>

<decisions>
## Implementation Decisions

### Screenshot Capture Harness (FIX-01 + ROM smoke)
- **D-01:** Capture the four fresh FIX-01 HEAD screenshots by **reusing/extending the existing JVM `MetaspriteUatTest` StepAgent + `captureAndRename()` harness**, emitting PNGs into `.planning/phases/19-codegen-fixes-metasprite-cluster/evidence/`. Chosen for repeatability, commit-traceability, and determinism — it matches the established metasprites UAT pattern and the MCP `gbkt-emulator` server wraps the same `StepAgent` API, so JVM-tier and MCP-tier results are equivalent.
- **D-02:** Where `metasprites-stress` lacks a UAT path for a seed's behavior, author a small new UAT scaffold rather than a one-off manual capture, so the capture stays committed and repeatable.
- **D-03 (constraint, locked):** Captures MUST run in the example's correct target mode — `gbcMode=true` with the `.noi` symFile if the metasprites example targets GBC ([[learning_platformer_mcp_needs_gbc_mode]]) — and the ROM MUST be rebuilt clean immediately before capture ([[feedback_rom_build_smoke_test_for_codegen_phases]]).

### FIX-02 Emission-Guard Placement (SEED-007..011)
- **D-04:** Place each new/identified guard **where its fix is observable** (split by observability):
  - Generic codegen invariants → `gbkt-backend-gbdk` (alongside existing `MetaspriteEmissionTest`, `MetaspriteSubPaletteEmissionTest`, etc.): SEED-007 (`actorPaletteAutoSlot++` counter), SEED-008 (monotonic VRAM allocator), SEED-011 (hiwater=0 once per frame).
  - Stress-example-specific output → `gbkt-examples/metasprites-stress` module tests: SEED-009 (`<gbdk/metasprites.h>` include in `bank1.c`), SEED-010 (namespaced `elephant_metasprites[]`/`tiger_metasprites[]`).
- **D-05:** Audit existing coverage FIRST; author guards only for seeds with no existing guard — **no duplicate coverage**. Each new guard asserts the fixed behavior on current code (GREEN) and carries a comment naming the reverted-fix scenario it would catch (RED-by-design). No revert→RED demonstration required.

### FIX-02 Audit Document
- **D-06:** Produce a **standalone `19-AUDIT-FIX-02.md`** in the phase dir. Table columns: seed → guarding test file → assertion name → existing-or-newly-authored → reverted-fix scenario the guard would catch. Kept separate from VERIFICATION.md so it is a clean, checkable acceptance-criteria deliverable.

### Byte-Identity Oracle (Req 5 — no production codegen drift)
- **D-07:** Satisfy Req 5 via a **procedural before/after diff**: the executor/verifier captures a hash of the generated `main.c` + bank files for `metasprites` (and `metasprites-stress`) at phase start, then re-diffs at phase end; any diff must be explained and screenshot-re-confirmed. Chosen over a new committed `main.c` baseline because this is a confirmation-only phase touching no codegen — a same-session before/after diff is robust to toolchain rebuild non-determinism, whereas maintained `main.c` baselines would be brittle. The existing `MetaspritesGeneratedSpriteByteIdentityTest` (elephant.c baseline) still runs as-is.

### Commit Discipline (locked)
- **D-08:** Every Phase 19 commit contains only metasprite-confirmation work (evidence, audit doc, emission tests, docs) — **zero S3776 / PR-#77 cognitive-complexity refactors interleaved**, so the byte-identity oracle can attribute any C-output change unambiguously. Commit messages scope each change to Phase 19 / FIX-01 / FIX-02.
- **D-09 (constraint):** Executors must run `:module:spotlessApply :module:detekt` per-commit — `:module:test` and the pre-commit hook do NOT run them ([[project_executor_gate_misses_spotless_detekt]]).

### Claude's Discretion
- Exact test method/assertion names, evidence PNG filenames, and the precise hashing command for the byte-identity diff are left to the planner/executor, provided they meet the acceptance criteria above.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase scope (MUST read first)
- `.planning/phases/19-codegen-fixes-metasprite-cluster/19-SPEC.md` — Locked requirements, boundaries, acceptance criteria (5 reqs)
- `.planning/phases/16-seed-triage/TRIAGE.md` — Disposition of all 9 metasprite seeds as VERIFIED-ALREADY-FIXED; the source-of-truth for each seed's fix location

### FIX-01 visual-parity evidence (prior triage shots, for comparison)
- `.planning/phases/16-seed-triage/evidence/SEED-004/` ... `SEED-013/` — Phase 16 triage screenshots (captured before Phase 17/18); Phase 19 re-shoots fresh at HEAD

### Existing test harnesses to reuse/extend
- `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/MetaspriteUatTest.kt` — StepAgent `captureAndRename()` screenshot harness (D-01)
- `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/MetaspritesGeneratedSpriteByteIdentityTest.kt` — existing elephant.c baseline guard (runs alongside D-07)
- `gbkt-backend-gbdk` emission tests: `MetaspriteEmissionTest`, `MetaspriteSubPaletteEmissionTest`, `MetaspriteAssetTileLoadEmissionTest`, `MetaspritePathAEmissionTest`, `MetaspriteSpritePaletteEmissionTest`, `MetaspriteDescriptorEmissionTest`, `MetaspriteBoundPosEmissionTest`, `SubPaletteAccessorEmissionTest` — audit these before authoring new FIX-02 guards (D-04/D-05)

### Methodology gates
- `.planning/verifier-gates.md` — Visual Evidence Rule (FIX-01 closure and ROM render require runtime screenshots, not variable assertions)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `MetaspriteUatTest` StepAgent + `captureAndRename()`: directly drives the built ROM and emits PNGs to the evidence dir — the FIX-01 + ROM-smoke capture mechanism (D-01).
- `MetaspritesGeneratedSpriteByteIdentityTest`: existing committed byte-identity guard pattern (elephant.c) — keeps running; the broader main.c/bank check is procedural (D-07).
- 8+ existing metasprite emission tests in `gbkt-backend-gbdk` — likely already guard several FIX-02 seeds; audit-first avoids duplicate coverage (D-05).

### Established Patterns
- Phase 16 evidence layout: per-seed directory with `screenshot.png` + `capture-note.txt` (visual) / `main-c-excerpt.txt` (structural). Phase 19 evidence should follow the same per-seed convention under its own evidence dir.
- Emission guards assert against generated C output via JVM tests; RED-by-design documented in a comment, no revert demonstration.

### Integration Points
- New FIX-02 guards hook into the existing GBDK codegen emission-test suite (`./gradlew :gbkt-backend-gbdk:test`) and, for stress-specific seeds, the `gbkt-examples/metasprites-stress` module test source set.
- Byte-identity diff reads `build/gbkt/generated/` output — requires a clean `:buildRom`/`generateC` before sampling (staleness caveat).

</code_context>

<specifics>
## Specific Ideas

- The MCP `gbkt-emulator` server wraps the same `StepAgent` API used by the JVM UAT harness, so the chosen JVM-tier capture is deterministically equivalent to an MCP-tier capture — no need to maintain two capture paths.
- `pluginTest` (not `:gbkt-gradle-plugin:test`) is the correct task if any plugin fixtures are touched; it has a known publish/test ordering race — verify via two invocations if used.

</specifics>

<deferred>
## Deferred Ideas

- Banks trio (SEED-014/015/016) and tRNS sprite outline (SEED-PHASE-13-SPRITE-OUTLINE) → Phase 20 (FIX-03/FIX-04).
- Platformer cEmit escapes and remaining DSL/tooling seeds → Phase 21 (FIX-05/FIX-06).
- Merging PR #77 (S3776 cognitive-complexity burn-down) → held open until Phases 19/20/21 complete; not Phase 19 work.

None of the above are Phase 19 scope — discussion stayed within phase boundary.

</deferred>

---

*Phase: 19-codegen-fixes-metasprite-cluster*
*Context gathered: 2026-06-13*
