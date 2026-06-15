# Phase 18: Deprecation Removals and Sonar Burn-down - Context

**Gathered:** 2026-06-13
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase removes two redundant/deprecated DSL APIs with full in-tree migration,
documents the gbkt deprecation-train convention, lands two Phase-17 code-review
carry-ins (SEED-027, SEED-028), and burns down the 46 SonarCloud S3776
cognitive-complexity HIGH findings to 0 under strict byte-identity discipline.

**In scope:**
- DEPR-01 — unify `whenever`/`runIf` (remove `whenever`, keep `runIf`), migrate all in-tree sites
- DEPR-02 — remove the `combatIsInState(String, String)` overload, migrate the 1 in-tree site
- DEPR-03 — document the deprecation convention in CONTRIBUTING.md
- SONAR-01 — S3776 HIGH 46 → 0 via extract-method refactoring
- SONAR-02 — per-commit byte-identity ROM sweep for emitting refactors
- SEED-027 — GBC `bitsPerPixel` 4→2 + KDoc correction
- SEED-028 — ConfigBuilder stale-guidance fix + v0.1.1 CHANGELOG migration note
- New root `CHANGELOG.md` (canonical home for breaking-change notes)

**Out of scope (belongs to other phases):**
- Codegen/asset correctness bugs (metasprite, banks, tRNS, platformer) — Phases 19–21
- Threading `TargetProfile.bitsPerPixel`/`screen` into codegen — v0.2.0 backlog
- Broader ConfigBuilder setter-convention redesign beyond SEED-028's stale strings — own pass / v0.2.0
- Giving `whenever` real edge-trigger semantics (rejected — see D-01)

</domain>

<decisions>
## Implementation Decisions

### `whenever`/`runIf` unification (DEPR-01)
- **D-01:** **`runIf` survives; `whenever` is removed.** Both emit identical `IfOp` —
  the distinction was KDoc-only. Direction follows SEED-023's "`whenever`
  over-promises reactive" argument and coheres with the `runIf`/`unless`/`orElse`
  control-flow family. The "keep both with real edge-trigger semantics" option was
  **rejected** (per-site previous-value RAM cost on the Game Boy; does not reduce
  DSL surface, which the roadmap requires).
- **D-01a:** **Both `whenever` overloads migrate to `runIf`:**
  - `ScriptBuilder.whenever(condition: Expr, block)` (`ScriptBuilder.kt:209`) → fold into `runIf`.
  - `ScriptBuilder.whenever(collision: PoolPoolCollisionExpr, block: (PoolIterator, PoolIterator) -> Unit)` (`ActorPoolBuilder.kt:396`) → reborn as a `runIf` overload (`runIf` does not have this overload today).
- **D-01b:** Migration is **mechanical and exhaustive** — ~63 example call sites,
  ~250 KDoc/doc references, internal framework callers (e.g. `VariableBuilders.kt:193`
  `sb.whenever(...)`), `context/DSL_REFERENCE.md`. Census: `whenever` = 327 total
  occurrences / 63 example call sites vs `runIf` = 31 / 8.
- **D-01c:** `unless`/`orElse` are **unchanged** (they lower to `ifOp` independently of
  the `runIf`/`whenever` naming). Only re-anchor their KDoc cross-references so they
  point at `runIf` (no more `[whenever]` mentions).

### Removal timing (DEPR-01 + DEPR-02)
- **D-02:** **Hard-remove this phase (v0.1.1)** — delete `whenever` and
  `combatIsInState(String,String)` outright and migrate every in-tree call site in
  the **same change**. No `@Deprecated`-grace release for either. Rationale: roadmap
  success-criterion 1 says "removed … in the same change"; consistent with SEED-028's
  just-accepted hard-removal precedent; v0.1.1 is the Hardening milestone with ~zero
  external adoption. The "deprecate-now-delete-in-v0.2.0" and "split by current
  deprecation state" options were both **rejected**.

### `combatIsInState` removal (DEPR-02)
- **D-03:** Remove `combatIsInState(stateId: String, battleId: String)`
  (`RpgExtensions.kt:440`) and its `@Deprecated`/KDoc block. Keep the typed
  `combatIsInState(CombatStateId, BattleRef)`. Migrate the **single** in-tree call
  site — `CombatStatesTest.kt:122` (drop the `@Suppress("DEPRECATION")` and the
  string-vs-typed equivalence assertion, or re-express it against the typed form).
  Drop now-unused imports.

### Deprecation-train convention (DEPR-03)
- **D-04:** CONTRIBUTING.md documents a **two-tier rule**:
  1. **Default (post-1.0 / once shipped to consumers):** `@Deprecated(level = WARNING, ReplaceWith(...))` in version N → removal in N+1, with a mandatory CHANGELOG breaking-change note.
  2. **Pre-1.0 / explicitly-labeled Hardening milestones:** hard removal with no shim is permitted while adoption is ~zero, with a **mandatory CHANGELOG breaking-change note** as the minimum bar.
  Cite SEED-023 (`whenever`), SEED-025 (`combatIsInState`), and SEED-028
  (ConfigBuilder) as the worked examples of the carve-out in action this milestone.

### S3776 burn-down (SONAR-01 / SONAR-02)
- **D-05:** **NOSONAR is last-resort / irreducible-only.** Default is ALWAYS
  extract-method. A suppression is earned only where extraction would harm
  readability or risk byte-identity (typically flat visitor/dispatch switches that
  are inherently one jump table). Each NOSONAR carries an inline rationale comment
  **and** a tracked v0.2.0 seed so it is revisited. Target spend **0–2** of the ≤5
  milestone budget, not all 5.
- **D-06:** **Commit attribution — one finding per commit for emitting code; batch
  non-emitting.** Each S3776 in C-emitting code (`codegen/visitor/**`,
  `GBDKPipeline.kt`) gets its own commit + its own 7-example byte-identity ROM sweep
  (cleanest oracle attribution if a sweep ever drifts). Non-emitting refactors (lang
  DSL, analysis, genre logic that produces no C) are batched per-file with
  JVM-test-only evidence. One consolidated full 7-example sweep at phase end as a
  backstop.
- **D-06a:** (Requirements-locked, restated for planners) S3776 commits are **never**
  combined with seed-fix commits, so the byte-identity oracle can unambiguously
  attribute any C-output change. No detekt/Sonar threshold changes.

### Phase-17 code-review carry-ins (locked — developer-decided 2026-06-13)
- **D-07 (SEED-027):** `TargetProfiles.GAME_BOY_COLOR_SCREEN.bitsPerPixel` `4` → `2`
  (`TargetProfiles.kt:50`); fix the "4 bits per pixel" KDoc prose to "2 bits per
  pixel, color via 8 hardware palettes"; **narrow** the "All backends MUST derive"
  KDoc claim to `width`/`height` (the only consumed fields). Byte-identical by
  construction — `ScreenSpec.bitsPerPixel` has zero readers today.
- **D-08 (SEED-028):** The ConfigBuilder property→function setter removal already
  shipped in Plan 17-11 (hard removal, no shim — **do not** re-add a shim). This
  phase fixes the **4 stale guidance strings** that still show the dead
  `config { ramBanks = N }` form:
  - `gbkt-gradle-plugin/.../GbktExtension.kt:166`
  - `gbkt-gradle-plugin/.../tasks/CompileRomTask.kt:319`
  - `gbkt-examples/platformer-template/.../PlatformerTemplate.kt:61`
  - `gbkt-examples/metasprites/.../MetaspriteEmissionTest.kt:44`
  …and adds the v0.1.1 CHANGELOG migration line
  (`config { ramBanks = N }` → `config { ramBanks(N) }`). **Do NOT** touch the
  `CartridgeConfig(romBanks = …, ramBanks = …)` IR data-class constructor
  named-argument sites — those are unrelated.

### CHANGELOG home
- **D-09:** Create a **new root `CHANGELOG.md`** (Keep a Changelog format) as the
  canonical, adopter-facing home for breaking-change/migration notes. The DEPR-03
  convention points here for its "mandatory CHANGELOG note." The v0.1.1 entry records:
  `whenever` removal, `combatIsInState(String)` removal, and the ConfigBuilder setter
  migration. (`.planning/MILESTONES.md` rejected — not adopter-facing.)

### Claude's Discretion
- Exact extract-method decomposition of each S3776 method (names, boundaries) — left to research/planning, constrained only by D-05/D-06 and byte-identity.
- Whether `CombatStatesTest.kt`'s string-vs-typed equivalence test is deleted or
  re-expressed against the typed form (D-03) — either is acceptable once the String
  overload is gone.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Seeds (locked decisions / scope sources)
- `.planning/seeds/SEED-023-whenever-runif-unification.md` — `whenever`→`runIf` rationale + census/scope sketch (DEPR-01)
- `.planning/seeds/SEED-025-remove-deprecated-combat-string-overload.md` — `combatIsInState(String)` removal + blast radius (DEPR-02)
- `.planning/seeds/SEED-027-gbc-screen-bitsperpixel-correctness.md` — `bitsPerPixel` 4→2 decision (D-07)
- `.planning/seeds/SEED-028-configbuilder-removal-migration-guidance.md` — ConfigBuilder stale-string list + "do-not-touch" boundary (D-08)

### Requirements / roadmap
- `.planning/REQUIREMENTS.md` §DEPR-01..03, §SONAR-01..02 — phase requirements
- `.planning/ROADMAP.md` §"Phase 18" — goal, success criteria, carry-in notes

### Source touch-points
- `gbkt-lang/.../dsl/ScriptBuilder.kt:209` — `whenever(Expr)` to fold into `runIf` (lines 226/234/242 = `runIf`/`unless`/`orElse`)
- `gbkt-lang/.../dsl/ActorPoolBuilder.kt:396` — pool-collision `whenever` overload
- `gbkt-lang/.../dsl/InputBuilders.kt`, `VariableBuilders.kt:193` — internal `whenever` callers / KDoc
- `gbkt-genre-rpg/.../dsl/RpgExtensions.kt:419,440` — typed (keep) + String (remove) `combatIsInState`
- `gbkt-genre-rpg/.../dsl/CombatStatesTest.kt:122` — sole String-overload call site
- `gbkt-core/.../constraints/TargetProfiles.kt:50` — GBC `bitsPerPixel` (D-07)
- `context/DSL_REFERENCE.md` — `whenever`→`runIf` doc migration
- `CONTRIBUTING.md` — add deprecation-convention section (DEPR-03); currently has none

### Methodology
- `.planning/verifier-gates.md` — Visual Evidence Rule; byte-identity oracle discipline
- Prior precedent: `.planning/phases/17-.../17-CONTEXT.md` (D-01/D-02 detekt-exclusion split keeps S3776 targets for this phase; SEED-028 origin)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `runIf`/`unless`/`orElse` family (`ScriptBuilder.kt:226-242`) — the survivor idiom; `whenever` migration targets these.
- 7-example byte-identity ROM sweep harness (`./gradlew :gbkt-examples:<game>:buildRom`) — the locked SONAR-02 oracle.

### Established Patterns
- `whenever` and `runIf` already lower to identical `IfOp` — migration is pure rename + overload relocation, zero IR/codegen change → naturally byte-identical for the DSL half.
- Detekt path-based complexity excludes on `codegen/`/`ir/`/`dsl/` were deliberately KEPT by Phase 17 D-01 precisely so they remain Phase 18's S3776 extract-method targets.
- SEED-027 change is byte-identical by construction (zero readers of `bitsPerPixel`).

### Integration Points
- `gbkt-genre-platformer`/`gbkt-genre-rpg` example projects + all example DSL files consume `whenever` — migration spans examples and tests, not just `gbkt-lang`.
- The byte-identity gate only binds commits that touch `codegen/visitor/**` or `GBDKPipeline.kt`; DSL-rename and doc/string commits need JVM-test evidence only (D-06).

</code_context>

<specifics>
## Specific Ideas

- Census numbers to plan against: `whenever` 327 total / 63 example call sites; `runIf` 31 / 8; `combatIsInState(String)` exactly 1 in-tree call site; S3776 HIGH = 46.
- CONTRIBUTING.md convention should read as honest about this milestone's three hard removals (D-04 two-tier), not aspirational.

</specifics>

<deferred>
## Deferred Ideas

### Reviewed Todos (not folded)
- **`configbuilder-cartridge-setter-api-consistency.md`** — broader "unify ConfigBuilder
  setter convention (function vs var per field)" API redesign. SEED-028 already
  resolved the *stale-string* slice (D-08); the wider convention-unification is a
  separate API-design pass (v0.2.0 candidate), out of this phase's deprecation-removal
  scope.
- **`orelse-may-attach-to-wrap-guard-ifop.md`** — `orElse` can silently attach to an
  auto-emitted wrap-guard `IfOp`. Genuinely adjacent (we touch the `runIf`/`orElse`
  family), but it is a **codegen-correctness bug**, not an API removal, and this phase
  must stay byte-identical. Belongs to a FIX phase (19–21) / its own correctness pass.
  Flagged for planner awareness so the `whenever`→`runIf` migration does not perturb
  `orElse` attachment semantics.

### Out-of-scope (other phases)
- Threading `TargetProfile.bitsPerPixel`/`screen` into codegen — v0.2.0 backlog (`SEED-TARGETPROFILE-SCREEN-THREADING`).
- All remaining 0.6-match codegen/asset bug todos (metasprite baselines, wrapAt, easeToZero, triggerSystem validation, MBC5 fallback warning, ConvertSprites hardening) — Phases 19–21.

</deferred>

---

*Phase: 18-deprecation-removals-and-sonar-burn-down*
*Context gathered: 2026-06-13*
