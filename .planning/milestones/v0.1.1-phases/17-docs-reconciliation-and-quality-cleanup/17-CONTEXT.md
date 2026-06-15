# Phase 17: Docs Reconciliation and Quality Cleanup - Context

**Gathered:** 2026-06-12
**Status:** Ready for planning

<domain>
## Phase Boundary

DSL_REFERENCE.md tells the truth and static-quality debt is burned down: (1) the 13 stale-API caveated sections in `context/DSL_REFERENCE.md` are audited per-method against source — implemented APIs get accurate rewritten docs, aspirational content is archived as tracked v0.2.0 candidates with no spec value lost; (2) the 2 doc-only fixes land (deprecated-API example block, `subpixel {}` no-op clarification); (3) `./gradlew detekt` passes with zero violations across all modules **including** the `gbkt-gradle-plugin` composite build, with no baseline files; (4) magic 160/144 pixel literals in framework code are replaced by named platform constants, with the remaining set enumerated and exempted with rationale. Requirements: DOCS-01, DOCS-02, DOCS-03, QUAL-01, QUAL-02, QUAL-03. Parallel-capable with Phase 18 (DEPR + SONAR) — scope boundaries below are drawn to avoid collision.

</domain>

<decisions>
## Implementation Decisions

### Detekt exclusion-removal scope (QUAL-01)
- **D-01:** Exclusion-removal targets the **globally-disabled rules only**: re-enable `MagicNumber`, `UnusedPrivateMember`, `UnusedPrivateProperty`, `ComplexCondition` and fix what they flag. The principled path-based complexity exclusions (`**/codegen/**`, `**/ir/**`, `**/dsl/**`, etc. on LongMethod/TooManyFunctions/LongParameterList/LargeClass) are **kept** — those files are Phase 18's S3776 extract-method targets and the phases must stay parallel-capable.
- **D-02:** `MagicNumber` is re-enabled in **targeted** form: active globally with documented path excludes for generated-code-emitting internals (`codegen/`, `test/`) and an `ignoreNumbers` list for idiomatic values. User-facing modules (lang, engine, examples, gradle-plugin) get full enforcement. Every new exclude carries a rationale comment matching the existing detekt.yml style.
- **D-03:** The composite build is covered via **apply + root-task bridge**: the detekt plugin is applied inside `gbkt-gradle-plugin/build.gradle.kts` (sharing the root `detekt.yml`), and the root `detekt` task depends on the composite's detekt task via `gradle.includedBuild(...)`. Plain `./gradlew detekt` covers everything — satisfies the success criterion literally; no CI special-casing.
- **D-04:** The dead `baseline = file("detekt-baseline.xml")` wiring in `build.gradle.kts` (both apply sites) is **deleted entirely**. The only paths to green are fixing violations or a visible detekt.yml exclusion with rationale — no resurrectable debt-hiding mechanism.

### Screen-constant design & exemptions (QUAL-02/03)
- **D-05:** Canonical constant home: backend/genre codegen uses the **existing `GameBoyConstants.SCREEN_WIDTH/SCREEN_HEIGHT`** (`gbkt-backend-gbdk/.../profiles/GameBoyConstants.kt`) — the right altitude for GBDK-target hardware facts. A canonical Game Boy `ScreenSpec`/`TargetProfile` preset is added in `gbkt-core` and `GameBoyConstants` derives from it, so the numbers live in exactly one place. (The roadmap's literal `ScreenSpec.WIDTH` wording is satisfied via the core preset.)
- **D-06:** Replacement is **mechanical** — each in-scope literal swaps for the named constant; generated C is byte-identical by construction. Threading `TargetProfile.screen` through codegen visitors is **deferred**: file a v0.2.0 backlog seed for it during this phase (multi-target support aspiration not lost).
- **D-07:** Exemption policy: replace literals in framework code paths (backend-gbdk visitors, genre modules, anything emitting C). Documented-exempt: `gbkt-emulator` (implements the physical 160×144 LCD), `gbkt-intellij-plugin` (preview rendering, no gbkt-core dependency), KDoc/comment mentions, and CLI template strings that ARE generated game source.
- **D-08:** QUAL-03 enumeration evidence: a scripted repo-wide 160/144 sweep is committed as phase evidence; every remaining hit appears in an **exemption table with rationale**. Regression guard = the re-enabled MagicNumber rule in user-facing modules; codegen modules (detekt-excluded) rely on review discipline.

### Unimplemented-API archive format (DOCS-02)
- **D-09:** Pruned spec content goes to **per-subsystem files in `.planning/backlog/v0.2.0/`** (e.g., `FEAT-STATE-MACHINES.md`, `FEAT-TWEENING.md`, ...). The existing FEAT-XX placeholder in REQUIREMENTS.md "Future Requirements" expands into individual indexed entries. Mirrors Phase 16 D-04 convention exactly.
- **D-10:** DSL_REFERENCE.md gets **clean removal, no pointers** — strictly implemented-only documentation; no "planned for v0.2.0" breadcrumbs in the reference doc. Backlog tracking lives solely in `.planning/`.
- **D-11:** Archived content is preserved **verbatim with provenance**: each backlog file carries the removed doc section verbatim (code samples intact) plus a provenance header — source line range, removal commit, and a note on what IS implemented today.
- **D-12:** Partially-implemented sections (camera, save, physics, items, dialogs, menus, ...) get a **full rewrite from source**: implemented methods documented accurately against the actual builder source (e.g., `CameraBuilder` in `gbkt-lang/.../dsl/SystemBuilders.kt`); aspirational parts move to backlog; the stale-API caveat banners disappear because the docs are simply true. This satisfies DOCS-01's per-method audit in the same pass.

### DOCS-01 accuracy evidence bar
- **D-13:** Audit scope: deep per-method audit on the **13 caveated sections** (the committed requirement) **plus one cheap full-document triage sweep** that only flags suspect uncaveated sections — flagged items are fixed if trivial or filed as backlog todos if not. No silent blind spots in the 3,224-line doc.
- **D-14:** Snippet accuracy bar: every rewritten snippet is **lifted or adapted from in-tree code that compiles today** (example games, tests, builder KDoc), with the source file recorded in the audit evidence. No new compile-the-docs infrastructure this phase.
- **D-15:** The audit produces a **committed evidence artifact**: per-section audit tables (documented method → source symbol file:line → verdict accurate/corrected/moved-to-backlog) in `.planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/`. The verifier checks the table against source instead of re-deriving the audit; "no spec value lost" is provable.
- **D-16:** **Grep-driven cross-doc consistency pass**: for each pruned/renamed API, grep the other docs (root CLAUDE.md, module CLAUDE.md files, `context/*.md`, CONTRIBUTING.md) and fix hits in the same plan. CLAUDE.md stays a routing index per the standing rule — no quick-refs re-added.

### Carried-forward constraints (binding, from prior phases)
- **D-17:** Phase 16 D-14 byte-identity gate: any Phase 17 commit touching codegen modules (`gbkt-backend-gbdk`, `gbkt-genre-*`) must leave the 7-example ROM sweep byte-identical (pong PASS\*). D-06's mechanical replacement is designed to satisfy this trivially, and the verifier must run a clean ROM smoke per `feedback_rom_build_smoke_test_for_codegen_phases`.
- **D-18:** Never run parallel `gradle clean` invocations (Kotlin daemon collision) — chain ROM sweep targets in a single Gradle invocation or run serially.

### Claude's Discretion
- Exact `ignoreNumbers` list and path-exclude set for the MagicNumber re-enable — whatever makes "zero violations" true without weakening user-facing enforcement.
- Naming of the core Game Boy preset (e.g., `TargetProfiles.GAME_BOY`, `ScreenSpec.GAME_BOY`) and the derivation mechanism for `GameBoyConstants`.
- Where the QUAL-03 exemption table lives (phase evidence vs a durable doc) — pick one and reference it from the audit evidence.
- Backlog file naming for the pruned subsystems (FEAT-* slugs) and how the 13 sections group into files (some sections may share a subsystem).
- Plan sequencing between docs work and quality work (independent clusters; parallelize as plan waves if useful).

### Folded Todos
- **MBC5 silent-fallback warning** (`.planning/todos/compilerom-silent-mbc5-fallback-warning.md`) — CompileRom should warn (not silently fall back to MBC5) when cartridge metadata is missing. Fits as a small gradle-plugin quality fix; warning-only change, no ROM output change.
- **ConfigBuilder setter consistency** (`.planning/todos/configbuilder-cartridge-setter-api-consistency.md`) — unify the function-vs-var setter convention per field in `ConfigBuilder`. Public DSL surface consistency; DSL_REFERENCE config{} section must document the unified convention (docs + API in one pass). Codegen output must remain identical (D-17 applies if any codegen module is touched).
- **RpgRegistry.clear() never called** (`.planning/todos/rpgregistry-clear-never-called.md`) — call `RpgRegistry.clear()` on `game{}` teardown or remove the dead method. Dead-code quality item aligned with the UnusedPrivateMember re-enable.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Subject matter (docs)
- `context/DSL_REFERENCE.md` — the 3,224-line target doc; 13 stale-API caveat blocks at lines ~372, 922, 1007, 1234, 1316, 1477, 1585, 1658, 1704, 1824, 2011, 2408, 2489
- `CONTRIBUTING.md`, root `CLAUDE.md`, module `CLAUDE.md` files, `context/*.md` — cross-doc consistency pass targets (D-16); CLAUDE.md is a routing index, do not re-add quick-refs

### Quality config & wiring
- `detekt.yml` — root config; the globally-disabled rules (MagicNumber, UnusedPrivateMember/Property, ComplexCondition) and the principled exclusions that stay
- `build.gradle.kts` (root) — detekt apply sites at ~lines 161–205 incl. the dead baseline wiring to delete (D-04)
- `gbkt-gradle-plugin/build.gradle.kts` — composite build needing detekt application (D-03)
- `.github/workflows/kotlin.yml` — code-quality job (~line 231) running `detekt spotlessCheck :gbkt-gradle-plugin:spotlessCheck`; comment documents the composite gap as "tracked debt"

### Constants & codegen
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/profiles/GameBoyConstants.kt` — existing SCREEN_WIDTH/SCREEN_HEIGHT constants (D-05 home)
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/constraints/ScreenSpec.kt` and `TargetProfile.kt` — core preset site (D-05); ScreenSpec is instance-based, no companion exists today
- Known literal sites: `gbkt-backend-gbdk/.../codegen/visitor/ActorVisitor.kt:468,490`, `GBDKSystemVisitor.kt:172-173`, `gbkt-genre-platformer/.../PlatformerVisitor.kt:1986-2002`, `gbkt-genre-sport` (2 files) — full enumeration is plan work (D-08)

### Scope & requirements
- `.planning/REQUIREMENTS.md` — DOCS-01..03, QUAL-01..03; FEAT-XX placeholder to expand (D-09); Future Requirements format precedent
- `.planning/ROADMAP.md` — Phase 17 success criteria; Phase 18 scope (collision boundary for D-01)
- `.planning/codebase/CONCERNS.md` — "Detekt exclusions encode tech-debt acceptance" section documents the principled-exclusion rationale D-01 preserves

### Folded todos
- `.planning/todos/compilerom-silent-mbc5-fallback-warning.md`
- `.planning/todos/configbuilder-cartridge-setter-api-consistency.md`
- `.planning/todos/rpgregistry-clear-never-called.md`

### Verification methodology
- `.planning/verifier-gates.md` — verification gates incl. ROM smoke requirements for codegen-touching phases (D-17)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `GameBoyConstants` object (`gbkt-backend-gbdk/.../profiles/`) — SCREEN_WIDTH=160/SCREEN_HEIGHT=144 already exist; visitors just don't use them
- Existing detekt.yml exclusion style — every exclude carries an inline rationale comment; D-02's new excludes must match
- Phase 16's `.planning/backlog/v0.2.0/` convention + REQUIREMENTS.md "Future Requirements" index — D-09 mirrors it (10 RE-DEFERRED seeds already live there as precedent files)
- `:gbkt-gradle-plugin:spotlessCheck` CI special-casing — precedent for reaching into the composite build; D-03's root-task bridge supersedes the pattern for detekt
- 7 example projects — the byte-identity ROM sweep substrate (pong PASS\*)

### Established Patterns
- Module dependency reality: `gbkt-lang` depends only on `gbkt-ir` (not core); `gbkt-emulator` and `gbkt-intellij-plugin` have no gbkt-core dependency — this shapes D-07's exemptions
- gbkt-lang's three 160/144 matches are KDoc comments only; gbkt-cli's one match is inside a generated-game template string — both exempt categories under D-07
- Audit-table evidence discipline from Phase 16 TRIAGE.md — D-15 reuses the shape
- Detekt runs in the CI code-quality job, deliberately excluded (`-x detekt`) from the build job — keep that separation when adding the composite

### Integration Points
- `.planning/backlog/v0.2.0/` — receives ~13 FEAT-* archive files (D-09) + 1 TargetProfile-threading seed (D-06)
- `REQUIREMENTS.md` Future Requirements — FEAT-XX expands to indexed per-subsystem entries
- Root `detekt` task ← composite detekt task dependency (D-03)
- Phase 18 boundary: path-based complexity exclusions and S3776-target files are Phase 18 territory — Phase 17 must not refactor them

</code_context>

<specifics>
## Specific Ideas

- The user explicitly chose **clean removal with no pointers** in DSL_REFERENCE.md (rejected the recommended breadcrumb-line option) — the reference doc must read as implemented-only truth, with zero forward-looking residue.
- Exemptions framing: gbkt-emulator's 160/144 "describe the display it implements" — the exemption table should use this implements-the-hardware vs consumes-the-platform distinction as its rationale axis.

</specifics>

<deferred>
## Deferred Ideas

- **TargetProfile.screen threading through codegen visitors** (multi-target support) — v0.2.0 backlog seed to be filed during this phase (D-06).

### Reviewed Todos (not folded)
- `13.8-palette-bank-codegen-followups.md` — already a full Phase 16 TRIAGE.md row (D-05 of Phase 16); routed via triage
- `metasprites-byte-identity-baseline-stale-since-12.8.md` — handled by Phase 16 D-15 (baselines promoted after visual gate)
- `triggersystem-ref-registry-validation.md` — already a full Phase 16 TRIAGE.md row; routed via triage
- `easetozero-oscillates-when-by-greater-than-one.md`, `wrapat-decrement-asymmetry-mask-vs-compare.md`, `wrapat-zero-silent-always-reset.md`, `orelse-may-attach-to-wrap-guard-ifop.md` — DSL/codegen behavior bugs; belong to fix phases (19–21) or backlog, not docs/quality
- `13.6-07-convertsprites-hardening-followups.md` — asset-pipeline code changes, byte-identity-sensitive; weakest match (0.2), out of scope

</deferred>

---

*Phase: 17-Docs Reconciliation and Quality Cleanup*
*Context gathered: 2026-06-12*
