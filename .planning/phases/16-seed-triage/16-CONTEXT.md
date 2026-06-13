# Phase 16: Seed Triage - Context

**Gathered:** 2026-06-12
**Status:** Ready for planning

<domain>
## Phase Boundary

Every one of the 44 seeds in `.planning/seeds/` (plus 3 folded todos — 47 entries total) gets a terminal, evidence-backed disposition against current master, establishing which bugs Phases 12–13.8 already fixed and which still require v0.1.1 fix work. The phase produces a canonical `TRIAGE.md` disposition table, per-seed evidence artifacts, a published confirmed-open list, and a batch visual review gate. **This phase fixes nothing** — code fixes belong to Phases 19–21 (and 17/18 for their assigned items). Requirements: TRIAGE-01, TRIAGE-02, TRIAGE-03 (TRIAGE-03's "seeds/ empty" is a milestone-close criterion; Phase 16 contributes by archiving closed seeds at phase close).

</domain>

<decisions>
## Implementation Decisions

### Triage record format
- **D-01:** `TRIAGE.md` in the phase dir is the canonical record — one row per entry: disposition, evidence link, fix-commit/screenshot ref, routing (for open seeds). This is the published confirmed-open list (Success Criterion 3).
- **D-02:** Each seed file additionally gets a small frontmatter stamp (`triage_disposition:` + pointer to TRIAGE.md). The stamp is a pointer, not a duplicate record — no two-sources-of-truth drift.
- **D-03:** Seeds closed by Phase 16 (VERIFIED-ALREADY-FIXED, INVALID) are archived out of `.planning/seeds/` at phase close (e.g., `.planning/seeds/archive/`). `seeds/` becomes the live confirmed-open work queue for Phases 19–21; Phase 21's "directory empty" criterion is then satisfied naturally as fixes land.
- **D-04:** RE-DEFERRED seeds move at phase close to `.planning/backlog/v0.2.0/` as full seed files (no content lost — root-cause analysis survives for the future implementer), plus a one-line index entry under REQUIREMENTS.md "Future Requirements". Mirrors the DOCS-02 archive-don't-delete philosophy.
- **D-05:** The 3 folded todos get full TRIAGE.md rows with the same disposition taxonomy and evidence bar as the 44 seeds (47 entries total).

### Evidence standard
- **D-06:** Bare commit attribution is NEVER sufficient for any disposition. VERIFIED-ALREADY-FIXED requires executable evidence at HEAD: a green test run covering the seed's specific failure mode, or generated-C inspection at HEAD showing the defect pattern absent. (Research pitfall #2 is binding.)
- **D-07:** CONFIRMED-OPEN requires a repro at HEAD: a failing probe/emission test, a defect screenshot, or generated-C inspection showing the defect present. Each repro is deliberately the RED half of the receiving fix phase's RED→GREEN cycle — triage pre-builds the fix phases' failing tests.
- **D-08:** Visual-seed verdicts (both "fixed" and "still broken") go through ONE binding batch human review gate: agents capture all visual-seed screenshots at HEAD (correct emulator modes — gbcMode=true for platformer), assemble a single review document (seed → screenshot → proposed verdict → reference image), and the user does one review pass before TRIAGE.md verdicts are finalized. Agent pixel-judgment alone never closes a visual seed.
- **D-09:** Evidence artifacts (screenshots, test outputs, generated-C excerpts) live in `.planning/phases/16-seed-triage/evidence/`, organized per seed ID. The existing `.planning/seeds/evidence/` PNG is referenced or moved in. Links remain valid after seed files archive.

### Open-seed routing
- **D-10:** Phase 16 issues exactly four dispositions: VERIFIED-ALREADY-FIXED, RE-DEFERRED, INVALID (not-a-bug, same evidence bar as verified-fixed, with written rationale), and CONFIRMED-OPEN (with a routing column naming the receiving fix phase 19/20/21 — or 17/18 where an item belongs there). FIXED appears in TRIAGE.md only later, as fix phases update rows. No code fixes in Phase 16, no exceptions for trivial seeds — preserves the triage/fix commit boundary the byte-identity oracle depends on.
- **D-11:** TRIAGE.md findings are authoritative over the pre-assigned FIX-01..06 seed lists. At phase close, REQUIREMENTS.md/ROADMAP.md get a reconciliation pass: already-closed seeds drop out of FIX criteria; newly-relevant items slot into the matching cluster phase. Fix phases plan purely from TRIAGE.md.
- **D-12:** The six seeds already deferred by REQUIREMENTS.md (SEED-001, SEED-018, SEED-019, SEED-024, SEED-RAW-C-CODEGEN-AST-MIGRATION, SEED-PHASE-X-CPAREN) fast-path to RE-DEFERRED citing the REQUIREMENTS.md milestone-definition decision as rationale — no verification work spent on them. They move to the v0.2.0 backlog dir immediately.

### Execution mechanics
- **D-13:** One shared substrate pass at triage start: a single clean, **serial** Gradle invocation builds all 7 example ROMs + runs the full JVM suite (never parallel `gradle clean` — Kotlin daemon collision). The artifacts (ROMs, generated C, test reports) are the shared evidence substrate for all per-seed work.
- **D-14:** The substrate-build commit SHA is pinned and recorded in TRIAGE.md; all evidence is attributed to that SHA. Parallel Phase 17/18 commits do not invalidate evidence (their codegen-touching commits are byte-identity-gated; the rest are docs/static-analysis). Only an actual byte-identity break forces re-capture.
- **D-15:** Stale metasprite/metasprites-stress byte-identity baselines (folded todo): ROM hashes + screenshots are captured during the substrate pass but promoted to official baselines ONLY after the batch visual review gate approves the screenshots. If metasprite seeds are confirmed still open, the regenerated baseline is documented as "known-current-state with defects" — never blessed as "correct rendering".
- **D-16:** After the substrate pass, per-seed work runs as parallel cluster agents (metasprites, banks, platformer, DSL/tooling misc) reading the shared artifacts. Agents NEVER run `clean`/`buildRom` themselves; emulator screenshot capture is serialized through the gbkt-emulator MCP server; agent prompts must explicitly instruct use of Serena MCP tools (user's standing preference — agents don't inherit it).

### Claude's Discretion
- Exact TRIAGE.md column layout and row schema.
- Cluster boundaries for agent assignment (the four named clusters are a guide, not a contract).
- Archive directory naming (`.planning/seeds/archive/` vs milestone dir) — pick one and be consistent.
- Whether SEED-014's mandated `BanksEmissionTest.kt` INV-2 sentinel run happens in the substrate pass or the banks cluster agent — as long as it runs.

### Folded Todos
- **Regenerate metasprite byte-identity baselines** (`metasprites-byte-identity-baseline-stale-since-12.8.md`) — baselines stale since Phase 12.8; folded because metasprite-cluster triage evidence depends on trustworthy baselines. Handled per D-15.
- **13.8 WR follow-ups** (`13.8-palette-bank-codegen-followups.md`) — 3 advisory code-review follow-ups (GBDKPipelineV2/SceneVisitor/PngUtils); triaged as full TRIAGE.md rows per D-05.
- **triggerSystem ref-registry validation** (`triggersystem-ref-registry-validation.md`) — validate `triggerSystem(SystemRef)` against the ref registry at `build()`; triaged as a full TRIAGE.md row per D-05 (likely CONFIRMED-OPEN → routed).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Triage methodology & gates
- `.planning/verifier-gates.md` — Visual Evidence Rule (TRIAGE-02): visual truths require runtime screenshots at HEAD, never variable assertions
- `.planning/research/SUMMARY.md` — triage pitfalls (#2: no closure without reproduction evidence), SEED-014 `hasZoneSceneBinder` gap (INV-2 sentinel must run), work-stream ordering
- `.planning/research/PITFALLS.md` — full pitfall list with prevention strategies
- `.planning/research/ARCHITECTURE.md` — seed cluster root causes at file/line granularity

### Scope & requirements
- `.planning/REQUIREMENTS.md` — TRIAGE-01..03, FIX-01..06 seed assignments (subject to D-11 reconciliation), Future Requirements list (drives D-12 fast-path)
- `.planning/ROADMAP.md` — Phase 16 success criteria; Phases 19–21 success criteria (what the routing column feeds)

### Subject matter
- `.planning/seeds/` — all 44 seed files (the triage corpus); `.planning/seeds/evidence/platformer-gbc-inverted-colors-2026-06-04.png` (existing evidence artifact)
- `.planning/todos/metasprites-byte-identity-baseline-stale-since-12.8.md` — folded todo
- `.planning/todos/13.8-palette-bank-codegen-followups.md` — folded todo
- `.planning/todos/triggersystem-ref-registry-validation.md` — folded todo

### Testing & evidence tooling
- `context/TESTING.md` — test tiers, GbktTestExtension, MCP tool reference for emulator screenshot capture
- `context/UAT_GUIDE.md` — debugging/play-testing ROMs with MCP agent tools

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `gbkt-mcp-server` / gbkt-emulator MCP tools (`emulator_start`, `emulator_screenshot`, etc.) — visual-seed evidence capture; rebuild shadow JAR via `./gradlew :gbkt-mcp-server:shadowJar` if `gradle clean` wiped it
- `BanksEmissionTest.kt` INV-2 sentinel — mandated for SEED-014 triage
- Existing emission tests across `gbkt-backend-gbdk` — many seeds' failure modes may already have covering tests; a green run at the pinned SHA is valid D-06 evidence
- 7 example projects (`gbkt-examples/{pong,breakout,simple-physics,metasprites,metasprites-stress,banks,platformer-template}`) — the substrate ROM set; pong is PASS\* (known toolchain non-determinism, never byte-identical)

### Established Patterns
- Byte-identity oracle: ROM-hash comparison across the 7 examples; pong flagged PASS\* per `project_pong_toolchain_nondeterminism`
- Phase `evidence/` directory convention from prior phases (e.g., Phase 12's `evidence/` artifacts)
- Platformer captures: always `gbcMode=true` + `.noi` symFile (DMG captures false-flag as palette regressions)
- Platformer traversal probes need RIGHT+A jumps — held-RIGHT stalls at a designed obstacle (not a collision bug)
- Seed frontmatter is heterogeneous: 14 seeds have YAML frontmatter (`status: dormant/active`), the rest are markdown-only — the D-02 stamp must handle both shapes

### Integration Points
- `.planning/seeds/` → archive dir + `.planning/backlog/v0.2.0/` (new, created by this phase)
- REQUIREMENTS.md / ROADMAP.md reconciliation pass at phase close (D-11)
- TRIAGE.md is the planning input for Phases 19, 20, 21

</code_context>

<specifics>
## Specific Ideas

- The confirmed-open repro artifacts should be shaped so fix phases can adopt them directly as RED tests (D-07) — name and place probe tests where the receiving phase's cluster would put them.
- The batch visual review document should pair each HEAD screenshot with the best available reference image (GBDK reference ROM capture or prior approved baseline from 13.6/13.7/13.8) so verdicts are comparisons, not pixel-judgment in a vacuum.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope. (No reviewed-but-unfolded todos: all three surfaced matches were folded; the remaining 7 todo matches were keyword noise and were not reviewed as candidates.)

</deferred>

---

*Phase: 16-Seed Triage*
*Context gathered: 2026-06-12*
