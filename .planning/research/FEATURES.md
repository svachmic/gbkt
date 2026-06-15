# Feature Research

**Domain:** Pre-1.0 open-source compiler/DSL framework — internal hardening patch release
**Researched:** 2026-06-12
**Confidence:** HIGH

---

## Context

This FEATURES.md covers the v0.1.1 Hardening milestone, not a user-facing feature release. "Features" here are the hardening deliverables themselves. The template categories (table stakes / differentiators / anti-features) are applied to those deliverables: what a hardening release *must* do to be credible, what makes it exemplary, and what to exclude so it stays focused.

The five target deliverables, as stated in PROJECT.md, are:

1. Seed triage and closure (all 44 seeds reach terminal disposition)
2. Deprecation removals (SEED-023 whenever/runIf, SEED-025 combat String overload)
3. DSL_REFERENCE.md reconciliation (13 stale-API sections pruned or rewritten)
4. QUAL-01..03 (detekt violations, platform-aware screen constants, magic-pixel elimination)
5. Sonar S3776 burn-down (46 cognitive-complexity HIGH findings)

---

## Feature Landscape

### Table Stakes (A Hardening Release Must Provide These)

| Deliverable | Why Expected | Complexity | Notes |
|---|---|---|---|
| Terminal disposition for every seed | "Hardening" means no loose ends. A seed left in limbo is a commitment the project cannot honor. | MEDIUM | 44 seeds; many (SEED-004..011, SEED-PHASE-12-*) may be already fixed by Phases 12–13.8 — verify against current master, not the planted-time status. Stale status hints must be explicitly re-checked. |
| Disposition taxonomy enforced (fixed / verified-already-fixed / re-deferred-with-rationale) | A binary done/not-done collapses distinct outcomes. Fixed means code changed. Verified-already-fixed means a later phase solved it with evidence. Re-deferred means v0.2.0+ with a documented reason. Without the taxonomy, seeds are disposed ambiguously and reappear. | LOW | The taxonomy is already implied by the seed format (Status field, routing notes). Formalizing it costs one paragraph in the phase spec. |
| Evidence artifact per closed seed | Seed closure without evidence is assertion-based. For a moving codebase (Phases 12–13.8 shipped between when most seeds were planted and now), "looks fixed" is not verifiable. | MEDIUM | Evidence minimum: unit test output, buildRom exit code, or screenshot for visual defects. The project already has the Visual Evidence Rule in `.planning/verifier-gates.md` — apply the same standard here. |
| @Deprecated annotation on `whenever` if unifying to `runIf` | One release cycle deprecation warning before removal is a social contract for library users. Skipping it for a pre-1.0 project is tempting; skipping it for APIs already documented in DSL_REFERENCE.md is not acceptable. | LOW | SEED-023. The cheap option: `@Deprecated("Use runIf { } for single-frame conditionals", ReplaceWith("runIf(condition, block)"))`. If real reactive semantics for `whenever` are chosen instead, that is a v0.2.0 feature, not v0.1.1 scope. |
| Deprecated `combatIsInState(String, String)` action | SEED-025 states "scheduled intent: v0.2.0". PROJECT.md says it "lands in v0.1.1". The resolution: if v0.1.0 shipped the overload WITHOUT a @Deprecated annotation (confirmed by SEED-025 text: "shipped the String overload un-deprecated"), then v0.1.1 must ADD @Deprecated (one cycle grace) and v0.2.0 removes it. Either action is legitimate; the table-stakes requirement is that the action is deliberate and documented in the release notes. | LOW | Blast radius: `RpgExtensions.kt` ~line 421 + any call sites in examples/tests. SEED-025 provides the grep recipe. |
| DSL_REFERENCE.md matches the implemented API on every section | Docs that contradict the code are more harmful than no docs — they generate false expectations and distrust. The 13 "Stale-API caveat" blockquotes are better than silence but not sufficient; the sections themselves need rewriting or removal. | MEDIUM | 13 sections carry Stale-API caveats: State Machine DSL, Dialog System (property-style API/isActive/isComplete), Menu System (style{}/gridMenu()/menu.tick()), Save System (field-level API), Entity Pools, Tweening/Easing, Camera (shake-builder/smoothing/snapTo), Camera Transitions (wipe/iris/flash), Physics (global world/tag/gravityZone), Pathfinding (partial), Testing Framework (testGame/testScene), Battle Menu/Combat Formulas/Custom Battle States, Item & Inventory System (partial). Each needs one of: (a) rewrite to match current API, (b) stub with "not implemented; tracked as v0.2.0 candidate", or (c) move to PLANNED_APIS.md. |
| Each pruned DSL section explicitly tracked as v0.2.0 feature candidate | Pruning without tracking is information loss. The 13 stale sections represent real design intent. These become v0.2.0 scoping raw material. | LOW | Mechanism: a seed file per removed subsystem, or a PLANNED_APIS.md aggregator. Whichever is chosen must be consistent across all 13 sections; ad-hoc deletion leaves no trail. |
| detekt violations resolved (QUAL-01) | A static analysis tool that reports violations on every build trains developers to ignore it. The deferred Phase 08 detekt scope must close so the tool is meaningful. | MEDIUM | Scope is from the Phase 08 deferral — the specific violation classes need to be enumerated at phase-spec time; "detekt violations" without a list is too vague to gate against. |
| Platform-aware screen constants replacing magic numbers (QUAL-02) | Magic pixel values like 160 and 144 hardcoded across the codebase are a correctness and readability gap. They conflict with Project Rule #1 (no magic strings/numbers — the same principle applies to numeric constants). | LOW | Source: deferred Phase 08 scope. Fix: `ScreenSpec.WIDTH`, `ScreenSpec.HEIGHT` constants from `gbkt-core/constraints`. Search-and-replace is mechanical once the constants are confirmed to exist and be published. |
| Sonar S3776 burn-down (cognitive complexity) | 46 HIGH-severity findings block the Sonar quality gate from being meaningful. A quality gate perpetually red on HIGHs cannot catch new HIGHs. | HIGH | S3776 findings are in deeply nested visitors (PlatformerVisitor, MetaspriteVisitor, SportVisitor, RPG codegen). Correct fix is extract-method refactoring, not complexity threshold increase. Each extraction changes method signatures; snapshot-based codegen tests will need updating. This is the costliest deliverable in the milestone. |

### Differentiators (What Makes This Hardening Release Exemplary)

| Deliverable | Value Proposition | Complexity | Notes |
|---|---|---|---|
| Re-verification against current master rather than trusting planted-time status | Seeds planted during Phases 9–13 may have been silently fixed by later phases (especially Phases 12–13.8 which touched metasprites, palette, sprite codegen). A naive "mark all dormant seeds as re-deferred" misses a real fixed count. Re-verification surfaces the actual debt reduction from v0.1.0 work. | MEDIUM | Requires targeted test runs or buildRom smokes per seed, not a full suite re-run for each. The seed file usually contains a repro recipe or discovery hook — use it. |
| Batch deprecation train convention established | Rather than ad-hoc per-API deprecation, SEED-023 and SEED-025 together establish the gbkt deprecation cycle: (1) annotate in patch, (2) remove in next minor, (3) both moves documented in CHANGELOG. This pattern becomes the template for future API evolution. | LOW | Document the convention in CONTRIBUTING.md (one paragraph). This pays forward: every future @Deprecated annotation inherits the pattern without reinventing the policy. |
| v0.2.0 feature candidate list with scope estimates | If each pruned DSL section produces a seed with a rough complexity estimate (Small/Medium/Large), the v0.2.0 roadmap starts from known material. Dialog system, entity pools, and pathfinding are each independently scoped rather than lumped as "finish the docs." | LOW | Each seed should carry the same fields as SEED-023/025: routing, blast radius, scope estimate. Consistent format means gsd-new-milestone can ingest them directly. |
| SEED-026 validatePlugins green (optional bandwidth item) | SEED-026 (gbkt-gradle-plugin build hygiene — validatePlugins red, pluginTest ordering race) is currently dormant and not in the v0.1.1 target list. Closing it makes the project publishable to the Gradle Plugin Portal without a blocking validation failure. | MEDIUM | Include only if seed triage and QUAL work complete ahead of schedule. Do not let it delay the seed triage gate. |

### Anti-Features (Commonly Requested, Often Counterproductive)

| Feature | Why Requested | Why Problematic | Alternative |
|---|---|---|---|
| Implement any of the 13 stale DSL sections in v0.1.1 | "We are already touching those docs, might as well implement dialog property-style API / entity pools / pathfinding." | The 13 sections are stale precisely because they require significant IR + codegen + test work. Absorbing even one into v0.1.1 turns a focused hardening release into an implicit feature release, breaks the patch-release boundary, and likely pushes the milestone to the right. | Track each as a v0.2.0 seed. The docs become accurate stubs ("not implemented; see v0.2.0 roadmap") instead of aspirational fiction. |
| Raising Sonar complexity thresholds instead of extracting methods | "46 findings is too many to fix; just increase the threshold." | Raising the threshold silences the tool without reducing actual complexity. It also sets a precedent: the next batch of complex visitors gets the same treatment, and the tool becomes noise. | Extract-method refactoring per visitor. Smaller methods are independently testable and reduce diff noise on future changes. Accept that codegen snapshot tests need re-baselining — this is expected for a structural refactor. |
| Blanket "re-defer all uncertain seeds to v0.2.0" | Time-efficient: no re-verification work needed. | Hides real fixes (seeds that Phases 12–13.8 actually resolved) and real problems (seeds that are still broken). A seed fixed by Phase 13.5 and marked "re-deferred" is debt misclassification — it stays in the backlog forever. | Per-seed re-verification using the repro recipe in each seed file. Seeds with no repro recipe get a quick buildRom smoke; seeds with visual symptoms get a screenshot. |
| Adding new detekt rules beyond the Phase 08 deferral scope | "While we have detekt configured, let's add X, Y, Z rules." | Scope creep on static analysis delays the milestone and may invalidate a large number of passing files. Static analysis rule changes require triage, suppression decisions, and CI stabilization. | Fix the known QUAL-01 set. New rule additions are their own phase with their own scope definition. |
| Magic-pixel elimination beyond QUAL-02's screen-constant scope | "While we are at it, remove all numeric literals from codegen." | Not all numeric literals in codegen are magic numbers. Bank slot numbers, sprite sizes, and tile strides are intentional. Broad numeric-literal elimination creates false positives (replacing intentional constants with mis-named identifiers) and false negatives (suppressed valid constants). | Replace 160 and 144 (screen dimensions) with ScreenSpec.WIDTH/HEIGHT. Hardware constants like tile size (8, 16) can stay documented inline rather than extracted to named constants. |
| Changelog-only deprecation (no @Deprecated annotation) | "Pre-1.0 projects do not need formal deprecation cycles." | gbkt already has users (the 7 example projects, any external adopters). A changelog entry without a @Deprecated annotation produces no IDE warning, no quickfix, and no compile-time signal. Users only discover the removal when they upgrade to v0.2.0. | Annotate with @Deprecated(ReplaceWith(...)) in v0.1.1. The annotation costs one line and provides IDE completion guidance. |

---

## Feature Dependencies

```
[Seed Triage]
    |--must precede--> [Deprecation Removals]
        (seeds SEED-023/025 cannot be finalized until the full
         seed landscape is known — other seeds may join the train)

[Seed Triage]
    |--informs--> [DSL_REFERENCE Reconciliation]
        (seeds for metasprites, camera, physics overlap with doc
         sections; a seed marked "fixed" changes whether its DSL
         section stays or is pruned)

[DSL_REFERENCE Reconciliation]
    |--produces--> [v0.2.0 Feature Candidate Seeds]
        (each pruned section becomes a new seed)

[QUAL-01 detekt fix] -- independent of -- [Seed Triage]
[QUAL-01 detekt fix] -- independent of -- [Sonar S3776]
        (can run in parallel; different tools, different file sets)

[Sonar S3776 burn-down]
    |--may touch same files as--> [QUAL-01 detekt]
        (coordinate to avoid conflicting refactors in the same
         visitor; sequence the two passes or scope to non-overlapping
         modules)

[QUAL-02 magic-pixel elimination]
    |--requires--> [ScreenSpec constants confirmed in gbkt-core/constraints]
        (verify constants are already published before searching
         call sites; if not, add the constants first)
```

### Dependency Notes

- **Seed triage precedes deprecation**: SEED-023 and SEED-025 are part of the seed backlog. Their disposition must be confirmed before "deprecation removals" is considered complete. Other seeds (e.g., SEED-022 tilemap predicate, SEED-021 platformer pivot) may also join the v0.1.1 execution list if their scope is small and triage confirms they are not already fixed.
- **Seed triage informs DSL docs**: Seeds SEED-004 through SEED-013 cover metasprites, GBC palette, and banks — all of which have corresponding DSL_REFERENCE.md sections. If triage confirms those seeds are fixed, the corresponding doc sections can be updated with confidence. If triage reveals they are still broken, the sections need stronger stale caveats.
- **Sonar and detekt may share file scope**: PlatformerVisitor and MetaspriteVisitor are likely targets for both Sonar S3776 (cognitive complexity) and QUAL-01 (detekt). Running extract-method refactoring for S3776 on those files before the detekt pass avoids redundant editing of the same files.

---

## MVP Definition

### Must Reach Terminal Disposition in v0.1.1

- [ ] All 44 seeds disposed: fixed, verified-already-fixed, or re-deferred with rationale and v0.2.0 routing
- [ ] Seeds directory empty at close (disposed seeds archived or deleted; no open item remains)
- [ ] SEED-023: `whenever` receives @Deprecated(ReplaceWith("runIf(...)")) annotation; all in-tree usages in examples/tests migrated; DSL_REFERENCE.md updated
- [ ] SEED-025: `combatIsInState(String, String)` receives @Deprecated annotation (v0.1.1 marks it deprecated; v0.2.0 removes it); CHANGELOG entry present
- [ ] All 13 stale DSL_REFERENCE.md sections rewritten to one of: (a) match current API, (b) stub with "not implemented / tracked as v0.2.0 candidate", or (c) moved to PLANNED_APIS.md
- [ ] Each removed/stubbed DSL section has a corresponding v0.2.0 seed or PLANNED_APIS entry
- [ ] QUAL-01: detekt violation set from the Phase 08 deferral is clean (enumerate specific rule set at phase-spec time)
- [ ] QUAL-02: 160/144 magic pixel literals replaced with ScreenSpec constants across codebase
- [ ] QUAL-03: additional Phase 08 magic-pixel elimination scope resolved (enumerate at phase-spec time)
- [ ] Sonar S3776: 46 to 0 HIGH cognitive-complexity findings via extract-method refactoring (not threshold raise)

### Add After Validation (triggers from triage; these become v0.2.0 seeds)

- [ ] Implement Dialog System property-style API (textSpeed, speaker, isActive, isComplete) — triggered when dialog seed is created from DSL_REFERENCE pruning
- [ ] Implement Entity Pool DSL — triggered when entity-pool seed is created from DSL_REFERENCE pruning
- [ ] Implement Tweening/Easing (tween(), Easing enum) — triggered when tween seed is created
- [ ] Implement Pathfinding DSL (complete PathfindingBuilder beyond current stub) — triggered from pruning
- [ ] SEED-022 tilemap predicate consolidation — if triage confirms it is not already resolved
- [ ] SEED-021 platformer pivot auto-derive — if triage confirms it is not already resolved
- [ ] SEED-026 gradle-plugin validatePlugins green — if bandwidth permits within v0.1.1 after P1 complete

### Future Consideration (v0.2.0+)

- [ ] SEED-023 `whenever` removal (after one release cycle — v0.2.0)
- [ ] SEED-025 `combatIsInState(String)` removal (v0.2.0)
- [ ] SEED-RAW-C-CODEGEN-AST-MIGRATION — architecture-tier change (own phase, requires research)
- [ ] SEED-PHASE-X-CPAREN — C AST parenthesized-expression; ~50+ fixture re-snapshots; own phase
- [ ] SEED-001 IDE and tooling (v2.0 trigger)
- [ ] SEED-019 IntelliJ plugin test framework coverage
- [ ] SEED-024 buildlog export save dialog
- [ ] Genre-codegen phases 07.5–07.8
- [ ] Camera shake-builder, followX/followY, snapTo (full camera API)
- [ ] Physics global world, tag(), gravityZone() (full physics API)

---

## Feature Prioritization Matrix

| Deliverable | User Value | Implementation Cost | Priority |
|---|---|---|---|
| Seed triage and closure (all 44) | HIGH — technical debt cleared, no silent commitments | MEDIUM — re-verification per seed, not just re-labeling | P1 |
| DSL_REFERENCE.md reconciliation (13 sections) | HIGH — docs that lie are worse than no docs | MEDIUM — 13 sections, most need rewrite not deletion | P1 |
| SEED-023 whenever @Deprecated annotation | MEDIUM — API surface clarity, IDE guidance | LOW — one annotation + in-tree migration | P1 |
| SEED-025 combatIsInState deprecation action | MEDIUM — Sonar S1133 open finding closure | LOW — one annotation | P1 |
| QUAL-02 magic-pixel screen constants | LOW–MEDIUM — readability and correctness | LOW — mechanical search-replace | P1 |
| Sonar S3776 burn-down (46 HIGHs) | HIGH — quality gate becomes meaningful | HIGH — extract-method per complex visitor, snapshot re-baselining | P1 |
| QUAL-01 detekt violations | MEDIUM — tool becomes useful signal | MEDIUM — depends on Phase 08 scope enumeration | P1 |
| QUAL-03 remaining magic-pixel scope | LOW–MEDIUM | LOW | P2 |
| v0.2.0 seed creation from pruned DSL sections | MEDIUM — roadmap raw material | LOW — write seed files during reconciliation pass | P1 (bundled with reconciliation) |
| SEED-026 validatePlugins / pluginTest race (optional) | MEDIUM — Plugin Portal readiness | MEDIUM | P2 |

---

## Process Patterns (Research Findings Adapted to This Domain)

These answer the four research questions. They directly inform requirements wording.

### 1. Backlog Triage and Closure Process

Rigorous pattern for a moving codebase (seeds planted during Phases 9–13 but not re-verified since):

**Per-seed triage step, not a sweep.** Do not bulk-disposition. Each seed has a repro recipe or a discovery hook (specific file + line number). Use the repro recipe to test against current master before assigning a disposition.

**Disposition taxonomy (exactly three categories):**
- `FIXED` — A subsequent phase or commit addressed the root cause. Provide commit reference or test output as evidence. Delete or archive the seed file.
- `VERIFIED-ALREADY-FIXED` — No explicit fix commit, but current-master behavior matches the expected state. Provide a buildRom smoke, unit test, or screenshot. Delete or archive the seed file.
- `RE-DEFERRED` — Still reproducible OR deliberately out of v0.1.1 scope. Provide either a repro artifact (for still-broken cases) or an explicit rationale and v0.2.0 routing (for scope decisions). Update the seed's Status and Routing fields; leave the file in the backlog for the next milestone.

**Evidence standards for closure:**
- Behavioral defect: unit test green + buildRom exit 0 for the relevant example.
- Visual defect: screenshot from the emulator (per the Visual Evidence Rule in `.planning/verifier-gates.md`).
- Ergonomic gap (wrong DSL API): a test asserting the correct IR lowering.
- Seeds with no explicit repro recipe: a targeted grep or buildRom smoke is sufficient; do not gold-plate verification.

**Stale-status heuristic:** Seeds planted before Phase 12.6 (2026-05-25) should be treated as unverified regardless of their status field. Phases 12–13.8 significantly changed metasprite, palette, and sprite codegen. Seeds from that window (SEED-004 through SEED-013) almost certainly need re-checking.

### 2. Docs Match Reality Reconciliation

Pattern for removing or rewriting aspirational API sections:

**Preserve the design intent in a tracking artifact.** Every section that is pruned should produce either a new seed (consistent with the existing seed format) or an entry in PLANNED_APIS.md. The content goes somewhere, not into the void.

**Rewrite stubs, not deletes (preferred).** A section that reads "Not implemented in v0.1.x. Tracked as v0.2.0 candidate: [seed link]" is more useful than a deleted section. Users searching for camera shake do not want a 404; they want "not yet, here is what exists." This makes the Stale-API caveat pattern systematic and explicit rather than ad-hoc.

**The implemented API section stays, the aspirational code blocks go.** For Camera, the implemented API is `follow(actor)` and `bounds(mapWidth, mapHeight)`. The section header stays; smooth-follow/deadzone/snapTo snippets are replaced with the real API signature. The removed snippets move to a seed.

**Changelog entry.** The v0.1.1 changelog should have a section: "Documentation: corrected DSL_REFERENCE.md to reflect implemented APIs; aspirational sections for [list] are tracked as v0.2.0 candidates." This signals to users that the change was deliberate, not an oversight.

### 3. Kotlin Deprecation Removal Conventions (Pre-1.0)

Pattern applicable to gbkt even before 1.0:

**Annotate first, remove second — always.** Even pre-1.0. The @Deprecated annotation costs one line; it provides IDE warnings, ReplaceWith quickfix, and a signal in the binary that the API is going away. Removing without prior annotation is a breaking change with no warning to users.

**@Deprecated signature pattern.** `@Deprecated(message = "Use runIf { } for single-frame conditionals", replaceWith = ReplaceWith("runIf(condition) { block() }"), level = DeprecationLevel.WARNING)`. Use WARNING for the first release cycle, optionally ERROR for the next, then remove.

**ReplaceWith must be accurate.** If the replacement requires import changes, include `imports = ["io.github.gbkt..."]` in the ReplaceWith. A ReplaceWith that produces non-compiling code after the quickfix is worse than no ReplaceWith.

**Migrate all in-tree usages.** Every usage in examples, tests, and docs must be migrated in the same commit as the @Deprecated annotation. Leaving in-tree usages on the deprecated API produces build warnings immediately and sets a bad precedent.

**Release notes minimum:** "v0.1.1: `whenever(condition) { }` is deprecated; use `runIf(condition) { }` instead. Will be removed in v0.2.0." and "v0.1.1: `combatIsInState(String, String)` is deprecated; use `combatIsInState(CombatStateId, BattleRef)` instead. Will be removed in v0.2.0."

### 4. Patch vs Minor Release Scope (0.x Semver Convention)

In 0.x, there is no formal stable API guarantee, but conventions matter for adopter trust:

- **Patch (v0.1.1):** Backlog drain, documentation fixes, code-quality cleanup, static-analysis debt, deprecation *marking* (adding @Deprecated), backward-compatible bugfixes. No new public API symbols, no new DSL constructs, no behavior changes to existing working functionality.
- **Minor (v0.2.0):** New public DSL constructs, @Deprecated *removal* (the second half of the deprecation cycle), new genre features, implemented APIs from the 13 stale sections, new analysis passes. May include breaking changes if flagged in the changelog.
- **The rule for this milestone:** If a change adds a new exported symbol or changes observable behavior of existing working code, it belongs in v0.2.0, not v0.1.1. The only new symbol permitted in v0.1.1 is a @Deprecated annotation on an existing symbol (which is additive, not a new public API).

---

## Competitor Feature Analysis

Not applicable to an internal hardening milestone. The relevant comparison is between this project's current state (44 open seeds, 13 stale doc sections, 46 Sonar HIGHs) and the target state (zero of each). The "competitor" is the previous milestone's technical debt.

---

## Sources

- `.planning/seeds/` — all 44 seed files, direct inspection (HIGH)
- `.planning/PROJECT.md` — milestone requirements and constraints, authoritative (HIGH)
- `context/DSL_REFERENCE.md` — Stale-API caveat sections, direct inspection (HIGH)
- `.planning/STATE.md` — deferred items and current position, direct inspection (HIGH)
- SEED-023, SEED-025, SEED-026 — specific deprecation and build-hygiene seeds, direct inspection (HIGH)
- Kotlin @Deprecated / ReplaceWith API conventions — inferred from Kotlin stdlib and kotlinx community patterns (HIGH for annotation mechanics; MEDIUM for pre-1.0 convention)
- SemVer 2.0.0 spec §4 with community-standard 0.x interpretation (MEDIUM — spec permits arbitrary 0.x changes; practice is stricter)

---

*Feature research for: gbkt v0.1.1 Hardening milestone — backlog drain, deprecation, doc reconciliation, code quality*
*Researched: 2026-06-12*
