# Phase 17: Docs Reconciliation and Quality Cleanup - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-12
**Phase:** 17-docs-reconciliation-and-quality-cleanup
**Areas discussed:** Detekt exclusion-removal scope, Screen-constant design & exemptions, Unimplemented-API archive format, DOCS-01 accuracy evidence bar (+ todo folding)

---

## Todo Folding (cross_reference_todos)

11 matched todos presented in three groups (already-triaged, DSL behavior bugs, tooling/API).

| Option | Description | Selected |
|--------|-------------|----------|
| 13.8 palette/bank WR follow-ups | Already a Phase 16 TRIAGE.md row | |
| Metasprite byte-identity baselines | Handled by Phase 16 D-15 | |
| triggerSystem ref-registry validation | Already a Phase 16 TRIAGE.md row | |
| easeToZero(by>1) oscillates | Codegen behavior bug, fix-phase territory | |
| wrapAt decrement asymmetry | Codegen behavior bug | |
| u8Var(wrapAt=0) silent always-reset | Validation behavior bug | |
| orElse attaches to wrap-guard IfOp | Codegen behavior bug | |
| MBC5 silent-fallback warning | Gradle-plugin warning when cartridge metadata missing | ✓ |
| ConfigBuilder setter consistency | Unify function-vs-var convention per field | ✓ |
| RpgRegistry.clear() never called | Wire teardown or remove dead method | ✓ |
| 13.6-07 ConvertSprites hardening | Asset-pipeline, byte-identity-sensitive, weakest match | |

**User's choice:** "Let me pick" → folded the 3 tooling items; left triaged items and DSL behavior bugs unfolded.

---

## Detekt exclusion-removal scope

### Q1: Which exclusions are in scope for removal?

| Option | Description | Selected |
|--------|-------------|----------|
| Globally-disabled rules only | Re-enable MagicNumber/UnusedPrivateMember/Property/ComplexCondition; keep principled path-based complexity exclusions (Phase 18 collision avoidance) | ✓ |
| Path-based exclusions too | Also remove codegen/ir/dsl complexity excludes and fix the flood | |
| Audit-then-decide per exclusion | Enumeration pass with keep/remove verdict per exclusion | |

### Q2: How should MagicNumber be re-enabled?

| Option | Description | Selected |
|--------|-------------|----------|
| Targeted re-enable | Active globally with documented codegen/test path excludes + ignoreNumbers; full enforcement in user-facing modules | ✓ |
| Full re-enable, fix everything | No path excludes; every magic number becomes a named constant | |
| Leave MagicNumber off | Rely on manual QUAL-02/03 enumeration only | |

### Q3: How should detekt reach the gbkt-gradle-plugin composite?

| Option | Description | Selected |
|--------|-------------|----------|
| Apply + root-task bridge | Apply plugin in composite + root detekt task dependsOn composite task via gradle.includedBuild | ✓ |
| Apply + explicit CI task | Separate :gbkt-gradle-plugin:detekt CI step mirroring spotlessCheck | |
| You decide | Researcher picks the cleanest mechanism | |

### Q4: Baseline wiring in build.gradle.kts?

| Option | Description | Selected |
|--------|-------------|----------|
| Delete the wiring | Remove both baseline lines entirely | ✓ |
| Keep wiring, gitignore the file | Local-only baselines allowed | |

---

## Screen-constant design & exemptions

### Q1: Canonical home for the screen constants?

| Option | Description | Selected |
|--------|-------------|----------|
| GameBoyConstants, core-derived | Codegen uses existing GameBoyConstants; new core ScreenSpec/TargetProfile preset is the single source it derives from | ✓ |
| ScreenSpec companion only | ScreenSpec.WIDTH/HEIGHT companion constants in gbkt-core | |
| Keep both independent | Replace literals with whichever is in reach; values stay duplicated | |

### Q2: Thread TargetProfile through codegen or mechanical replacement?

| Option | Description | Selected |
|--------|-------------|----------|
| Mechanical replacement | Swap literals for named constants; byte-identical by construction | |
| Thread TargetProfile through codegen | Visitors read dimensions from the game's profile | |
| Mechanical now + deferred seed | Mechanical replacement + v0.2.0 backlog seed for threading | ✓ |

### Q3: Exemption policy for the 69 raw matches?

| Option | Description | Selected |
|--------|-------------|----------|
| Exempt non-framework surfaces | Replace framework code paths; exempt emulator/intellij-plugin/comments/templates with documented rationale | ✓ |
| Replace everywhere reachable | Add gbkt-core deps to emulator/intellij-plugin | |
| Replace code, exempt comments/templates | Emulator/intellij get local named constants instead | |

### Q4: QUAL-03 enumeration evidence and regression guard?

| Option | Description | Selected |
|--------|-------------|----------|
| Grep audit + exemption table | Scripted sweep as phase evidence + exemption table; MagicNumber guards user-facing modules | ✓ |
| Audit + source-scan test | Additionally a JVM test scanning visitor sources for raw 160/144 CLiterals | |
| Audit only | One-time enumeration, no standing guard | |

---

## Unimplemented-API archive format

### Q1: Destination and granularity for pruned spec content?

| Option | Description | Selected |
|--------|-------------|----------|
| Per-subsystem backlog files | One FEAT-*.md per subsystem in .planning/backlog/v0.2.0/ + expanded REQUIREMENTS.md index | ✓ |
| Single consolidated archive doc | One DSL-FUTURE-APIS.md holding all 13 sections | |
| You decide | Pick based on post-audit content volume | |

### Q2: What remains in DSL_REFERENCE.md?

| Option | Description | Selected |
|--------|-------------|----------|
| Implemented-only + pointer line | Rewritten sections + one-line backlog breadcrumb (recommended) | |
| Clean removal, no pointers | Strictly implemented-only; tracking lives solely in .planning/ | ✓ |
| Keep stub sections | Headers remain with "planned for v0.2.0" notes | |

**Notes:** User went against the recommendation here — the reference doc must carry zero forward-looking residue.

### Q3: Archival fidelity?

| Option | Description | Selected |
|--------|-------------|----------|
| Verbatim + provenance | Removed sections copied verbatim with provenance header (source lines, removal commit, current baseline) | ✓ |
| Distilled candidate summary | Summarized capability + key API shapes | |

### Q4: Partially-implemented sections — rewrite or excise?

| Option | Description | Selected |
|--------|-------------|----------|
| Full rewrite from source | All 13 sections rewritten against actual builder source; caveat banners disappear | ✓ |
| Surgical excision only | Remove aspirational snippets, leave surrounding prose | |

---

## DOCS-01 accuracy evidence bar

### Q1: Audit scope?

| Option | Description | Selected |
|--------|-------------|----------|
| 13 sections + triage sweep | Deep audit on the 13 + cheap full-doc sweep that flags suspect uncaveated sections | ✓ |
| 13 sections only | Strictly the committed DOCS-01 scope | |
| Full per-method audit | Every section audited per-method | |

### Q2: Snippet accuracy bar?

| Option | Description | Selected |
|--------|-------------|----------|
| Sourced from real code | Snippets lifted/adapted from in-tree compiling code with source recorded | ✓ |
| Compiled-snippet harness | Extract-and-compile test for all kotlin blocks | |
| Manual review only | Careful authoring + verifier spot-checks | |

### Q3: Committed audit artifact?

| Option | Description | Selected |
|--------|-------------|----------|
| Audit table in phase evidence | Per-section tables: method → source symbol → verdict, in evidence/ | ✓ |
| Commit messages only | No separate artifact | |

### Q4: Cross-doc consistency pass?

| Option | Description | Selected |
|--------|-------------|----------|
| Grep-driven consistency pass | Grep other docs for each pruned/renamed API; fix hits in the same plan | ✓ |
| DSL_REFERENCE.md only | Other docs out of scope | |

---

## Claude's Discretion

- Exact ignoreNumbers list and path-exclude set for MagicNumber re-enable
- Naming of the core Game Boy preset and the GameBoyConstants derivation mechanism
- Where the QUAL-03 exemption table lives
- Backlog file naming/grouping for the 13 pruned subsystems
- Plan sequencing between docs and quality clusters

## Deferred Ideas

- TargetProfile.screen threading through codegen visitors (multi-target support) — v0.2.0 backlog seed filed during Phase 17
- Reviewed-but-not-folded todos: 3 Phase-16-triaged items, 4 DSL behavior bugs, ConvertSprites hardening (see CONTEXT.md deferred section)
