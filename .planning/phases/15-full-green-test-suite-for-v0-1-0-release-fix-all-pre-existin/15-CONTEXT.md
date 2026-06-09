# Phase 15: Full-green test suite for v0.1.0 release — fix all pre-existing - Context

**Gathered:** 2026-06-09
**Status:** Ready for planning

<domain>
## Phase Boundary

Drive the entire JVM test suite to **zero failures** — both `./gradlew test
--continue` (all library/genre/example modules) and `./gradlew pluginTest`
(gradle-plugin IntegrationTest) — reached **diagnose-first** by fixing real
bugs or correcting *provably*-stale assertions, **NEVER** by weakening a
threshold. This is the hard release gate for tagging v0.1.0. The phase begins
with a fresh full-suite re-run; the authoritative work-list is "every red test
today," not the 2026-06-06 19-test snapshot. Tagging/publishing v0.1.0 and
re-presenting Phase 14's sign-off are downstream of this phase, not part of it.

</domain>

<spec_lock>
## Requirements (locked via SPEC.md)

**7 requirements are locked.** See `15-SPEC.md` for full requirements, boundaries, and acceptance criteria.

Downstream agents MUST read `15-SPEC.md` before planning or implementing. Requirements are not duplicated here.

**In scope (from SPEC.md):**
- Driving `./gradlew test --continue` to zero failures (all library, genre, and example modules)
- Driving `./gradlew pluginTest` to zero failures (gradle-plugin IntegrationTest)
- A phase-start fresh-run inventory of all red tests (authoritative work-list)
- Per-failure diagnosis and a fix via: real product/codegen bug fix, provably-stale assertion correction, or documented removal of an obsolete test
- Fixing the 6 known classes: IntegrationTest, BanksUatTest, PongStepAgentTest, PlatformerTemplate128UatTest, PlatformerTemplateUatTest, PlayerMetaspriteGeometryTest — plus any additional red surfaced by the fresh run

**Out of scope (from SPEC.md):**
- Tagging / publishing v0.1.0 — manual human step after this phase + Phase 14 sign-off re-presentation + `/gsd-complete-milestone`
- Re-presenting Phase 14's release sign-off — downstream of this phase
- The pong ROM-hash / ball.c padding nondeterminism (PASS\*) — produces no test failure, naturally excluded under "fix all red"
- New features, new examples, or new tests beyond what is needed to make existing tests pass or replace a provably-obsolete one
- Weakening any threshold or assertion to coerce a pass — explicitly forbidden (`feedback_quality_over_shortcuts`)
- Re-introducing any example/code retired by Phase 14

</spec_lock>

<decisions>
## Implementation Decisions

### Wide-Blast-Radius Escalation (the central HOW question)
- **D-01: Fix inline, gate-first.** If diagnose-first reveals a *real* bug whose
  fix has wide blast radius (touches shared backend codegen, not just the one
  example), it is fixed **in this phase** — the green suite IS the release gate,
  so routing the fix out would block v0.1.0 indefinitely. This deliberately
  OVERRIDES the usual "route wide blast radius to a new phase" rule
  (`feedback_route_to_proper_phase_when_blast_radius_is_wide`) *for the purpose
  of closing the release gate*; wide fixes instead earn extra verification rigor
  (D-02). No new sibling phase is spun up for a wide fix.
- **D-02: Split regression guard for an intentional-change fix.** A real
  codegen-bug fix *legitimately changes* the emitted C for the affected example,
  so a byte-identity-vs-pre-phase gate would fail there by design. Therefore:
  - **Affected example:** NEW generated-C output verified correct (failing test
    goes green **+** a live MCP screenshot proving the visual fix, per D-03), and
    the byte-identity baseline is **re-pinned** to the corrected output.
  - **Other 6 KEEP examples:** must stay **byte-identical** to their pre-phase
    baseline (proves zero collateral codegen drift).
  - **All 7 KEEP examples:** `:buildRom` EXIT 0 (no regression to the green build
    state — SPEC AC line 101).

### Evidence Tier per Diagnosis (Visual Evidence Rule application)
- **D-03: Live MCP screenshot required for every visual-truth verdict.** The
  visual UAT failures — `BanksUatTest` (dominant-colour < 95% on the by-design
  near-blank scene) and `PlatformerTemplate128UatTest` / `PlatformerTemplateUatTest`
  (facing/non-uniform pixel-diff) — are truths phrased "X is visible on screen."
  Per CLAUDE.md §"Visual Evidence Rule" + standing `feedback_visual_evidence_for_visual_truths`:
  the "real bug vs stale assertion" verdict MUST include a live `mcp__gbkt-emulator__*`
  screenshot captured to the phase `evidence/` directory showing what the player
  actually sees. Static / generated-C evidence alone is **insufficient** for a
  visual verdict (this is the exact failure class the rule was written to catch —
  see CLAUDE.md History note on Phase 07.4 SC-4).
- **D-03b: Non-visual failures use static evidence.** `IntegrationTest`
  (`NoSuchMethodError`) and `PlayerMetaspriteGeometryTest` (greps `sprite_player_frame_0[]`
  in `main.c`) are internal-shape truths upstream of the visual surface; a
  generated-C grep / stack-trace + diagnosis is acceptable evidence there.

### Removal Latitude (Req 7)
- **D-04: Removal is last resort, capability-retired only.** Of the three Req-7
  fix paths (real-bug fix / provably-stale assertion correction / documented
  removal), removal is permitted **only** when the capability the test covered is
  genuinely retired (e.g. it tests a Phase-14-deleted example or feature). Every
  removal must cite the retired capability in the diagnosis ledger. Default
  expectation: the 6 known classes are all fix-or-correct, **zero removals**
  (`PlayerMetaspriteGeometryTest` is a grep-the-renamed-array *correction*
  — `sprite_player_frame_0` → `player_metasprites` — not a removal).

### IntegrationTest Fix Path (Req 3)
- **D-05: Diagnose-first, prefer the durable root-cause fix.** Root-cause WHY
  `pluginTest`'s republish of the 7 dependency modules does NOT clear the
  `SceneIR.copy$default` skew (candidates: a stale `~/.m2` artifact surviving the
  republish, TestKit/Gradle classpath caching, or a fixture compiled against the
  pre-`zoneRefs` `SceneIR` signature). Where both a build-hermeticity fix
  (make `pluginTest` reliably clean/republish so the skew cannot recur) and a
  one-off fixture-data patch would work, **prefer the hermetic/durable fix** —
  but let the recorded diagnosis pick. No path forced ahead of evidence.

### Diagnose-First Ledger (Req 7 — cross-cutting)
- **D-06:** A per-failure diagnosis ledger is the Req-7 acceptance artifact. Each
  entry records: failing class/test, root-cause diagnosis, fix path taken
  (real-bug / stale-assertion / retired-capability removal), and the evidence
  reference (live screenshot path for visual verdicts per D-03, grep/trace for
  static per D-03b). Zero entries may weaken a threshold to mask a genuine
  failure. Ledger + screenshots live in the phase `evidence/` directory
  (mirrors the Phase 14 `evidence/` convention).

### Claude's Discretion
- Exact fresh-run inventory format and the order in which the 6+ classes are
  driven green — executor's call within diagnose-first.
- The specific hermeticity mechanism for D-05 (clean step, version bump, classpath
  isolation) once the root cause is known.
- Whether the `metasprites`/`metasprites-stress` `*GeneratedSpriteByteIdentityTest`
  baselines need a behavior-neutral re-pin if drift is detected at phase start
  (already re-pinned in 13.6-07; verify-then-re-pin is acceptable, not a failure).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase requirements (LOCKED)
- `.planning/phases/15-full-green-test-suite-for-v0-1-0-release-fix-all-pre-existin/15-SPEC.md` — 7 locked requirements, boundaries, acceptance criteria. MUST read before planning.

### Authoritative failing-test inventory (phase start re-run reconfirms this)
- `.planning/phases/14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff/evidence/FINAL-REGRESSION.md` — the 2026-06-06 differential sweep recording 6 failing classes / 19 tests, reproduced byte-identically at pre-phase commit `f92efec7` (genuinely pre-existing; zero Phase-14 regressions). Re-run is authoritative per Req 2.

### Verification methodology (governs the diagnosis evidence tier — D-03)
- `CLAUDE.md` §"Verification Methodology — Visual Evidence Rule" — runtime-visible truths require a screenshot, not a variable assertion; the History note (Phase 07.4 SC-4) is the precedent D-03 enforces.
- `CLAUDE.md` §"Scope-level grep gates (corollary)" — for per-function invariants, brace-walk the function body (awk) and grep WITHIN scope; relevant to `PlayerMetaspriteGeometryTest`'s array-body extraction.
- `context/TESTING.md` — test tiers (unit / emulator / UAT / MCP), `GbktTestExtension`, PLAYBOOK format, MCP tool reference; informs how the live run-check (D-03) is driven.
- `context/UAT_GUIDE.md` — MCP agent tooling for play-testing ROMs.

### Failing test sources (per-failure diagnose targets)
- `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt` — `assertScreenshotIsNonUniform`, dominant-ratio `< 0.95` gate (lines ~142–162); ×2 fail on the by-design near-blank codegen-demo scene.
- `gbkt-examples/pong/src/test/kotlin/io/github/gbkt/examples/pong/PongStepAgentTest.kt` — paddle1 OAM count expected=2 actual=1 (metadata vs runtime).
- `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate128UatTest.kt` — facing L/R pixel-diff (threshold > 10%, actual 6.80%) + `assertScreenshotIsNonUniform`.
- `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateUatTest.kt` — failing screenshot/UAT assertion (under-counted in the ROADMAP summary; present in the line-84 differential sweep).
- `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlayerMetaspriteGeometryTest.kt` — greps `sprite_player_frame_0[]` (lines ~102, 182, 191); array renamed `player_metasprites` (likely provably-stale assertion; ROM byte-identical).
- gradle-plugin `IntegrationTest` (run ONLY via `pluginTest`) — `NoSuchMethodError: SceneIR.copy$default(...)`; `SceneIR` `zoneRefs` was added in `eda282ec`, fixtures last touched in `09.2-02`; skew known since Phase 11.1-04.

### gradle / build invariants
- `CLAUDE.md` §"Build & Run" → "Gradle-plugin tests: use pluginTest, NOT :gbkt-gradle-plugin:test" — pluginTest republishes the 7 dependency modules to mavenLocal first; D-05 hardens this path.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **MCP emulator tooling** (`mcp__gbkt-emulator__*`): emulator_start/observe/press/step/screenshot drive the D-03 live visual-verdict captures. Each example may carry a `PLAYBOOK.md` (`emulator_get_playbook`). Platformer is GBC-target — start with `gbcMode=true` + `.noi` symFile (per `learning_platformer_mcp_needs_gbc_mode`); world1Area1 traversal needs RIGHT+A jumps, not held-RIGHT (per `learning_platformer_traversal_needs_jumps`).
- **`assertScreenshotIsNonUniform`** helper (duplicated in `BanksUatTest` + `PlatformerTemplate128UatTest`): decodes the PNG, asserts ≥2 distinct RGB values AND dominant-colour ratio `< 0.95`. The verdict on these is the D-03 visual-evidence call.
- **Brace-walk array extraction** in `PlayerMetaspriteGeometryTest.extractArrayBody` (CLAUDE.md scope-level grep-gate pattern) — reusable for the renamed-array correction.
- **Committed byte-identity tests**: `metasprites` + `metasprites-stress` `*GeneratedSpriteByteIdentityTest` — the second gate for the D-02 split-guard "other-examples-byte-identical" check.

### Established Patterns
- **Diagnose-first per failure** (`feedback_quality_over_shortcuts`): every fix preceded by a recorded root-cause distinguishing real-bug from stale-assertion.
- **Byte-identity discipline** (13.6/13.7/13.8): generated-C SHA vs a pinned baseline is the standard behavior-neutral regression proof; pong ROM is the documented PASS\* exception.
- **`pluginTest`, not `:gbkt-gradle-plugin:test`**; **no parallel `gradle clean`** against the same root (`feedback_no_parallel_gradle_clean` — chain into one invocation or run serially; recover with `./gradlew --stop`).

### Integration Points
- A wide-blast-radius real-bug fix (D-01) likely lands in `gbkt-backend-gbdk` codegen (e.g. metasprite/OAM emission for the pong case) — the D-02 split guard verifies the affected example's new output AND the other 6's byte-identity.
- `IntegrationTest` runs inside the gradle-plugin TestKit sandbox resolving 7 modules (gbkt-core/backend-api/backend-gbdk + transitive ir/lang/engine/world) from mavenLocal — D-05's hermeticity fix operates on this republish path.

</code_context>

<specifics>
## Specific Ideas

- The green suite is a **hard release gate**, not advisory — Phase 14's sign-off was WITHHELD precisely because shipping v0.1.0 with a red suite is unacceptable. This is why D-01 chooses fix-inline over route-out even for wide blast radius.
- "Provably stale" is the bar for correcting an assertion without fixing a bug — the diagnosis must *prove* the screen/output is actually fine (D-03 live screenshot for visual cases), never assume it.
- No threshold-weakening is a **phase failure condition**, not a guideline — lowering the 0.95 dominant-colour ratio, the >10% facing pixel-diff, or the OAM expected=2 to coerce a pass is forbidden.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

### Reviewed Todos (not folded)
All 11 keyword-matched todos are behavior/codegen-correctness changes (they would alter emitted C) and were already deferred as out-of-scope by Phase 14's cleanup-only, byte-shape-preserving boundary. They remain out of scope for a green-suite phase whose SPEC forbids new behavior fixes. Reviewed and NOT folded:
- `compilerom-silent-mbc5-fallback-warning`, `configbuilder-cartridge-setter-api-consistency`, `easetozero-oscillates-when-by-greater-than-one`, `orelse-may-attach-to-wrap-guard-ifop`, `triggersystem-ref-registry-validation`, `wrapat-decrement-asymmetry-mask-vs-compare`, `wrapat-zero-silent-always-reset` — behavior/API changes. Deferred.
- `13.8-palette-bank-codegen-followups`, `13.6-07-convertsprites-hardening-followups` — codegen-correctness follow-ups; would change emitted C. Deferred.
- `rpgregistry-clear-never-called` — dead-method/behavior item; not a test failure. Deferred.
- `metasprites-byte-identity-baseline-stale-since-12.8` — already discharged by 13.6-07's re-pin; only a behavior-neutral verify-then-re-pin remains (D-Discretion).

</deferred>

---

*Phase: 15-full-green-test-suite-for-v0-1-0-release-fix-all-pre-existin*
*Context gathered: 2026-06-09*
