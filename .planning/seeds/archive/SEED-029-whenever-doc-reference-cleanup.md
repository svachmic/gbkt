# SEED-029 — `whenever` → `runIf` cosmetic doc/KDoc reference cleanup

> **Origin:** Phase 18 verification (18-VERIFICATION.md, DEPR-01 gap). Plan 18-01's migration grep scoped only the DSL/examples/tests, so `whenever` references survived in docs, KDoc, and README. The functional sites (gbkt-cli templates, gbkt-intellij-plugin templates + DSL registration) were fixed in Phase 18 (plans 18-29/18-30); this seed covers the remaining COSMETIC residual only.

**Status:** Open — backlog (cosmetic, non-breaking)
**Routing:** Pure docs/KDoc sweep — fits a docs-cleanup quick task or the next docs phase. No code-behavior change, no compile impact.
**Blast radius (~25 files, all cosmetic):**
- `README.md` — 12 occurrences (user-facing DSL examples)
- KDoc examples/comments across gbkt-lang, gbkt-core, gbkt-ir, gbkt-genre-rpg/platformer/sport (~22 files): e.g. `References.kt`, `CollectionBuilders.kt`, `CombatEngineBuilder.kt`, `WorldBuilders.kt`, `GameBuilder.kt`, `ScriptBuilderContext.kt`, `ActorBuilder.kt`, `UIBuilders.kt`, `SceneBuilder.kt`, `CombatStates.kt`, `ScriptOp.kt`, `Expr.kt`
- Example `CLAUDE.md` docs: `gbkt-examples/{pong,breakout,simple-physics}/CLAUDE.md`
- A few test comments referencing the historical `whenever` ladder (these may legitimately keep the historical name — judgment per file)

## What is NOT in scope here (already done in Phase 18)
- `gbkt-cli/.../templates/*.kt` — fixed (plan 18-29)
- `gbkt-intellij-plugin/src` templates + `DSL_FUNCTIONS` + highlighter + completion + doc-provider — fixed (plan 18-30)
- `context/ARCHITECTURE.md`, `RpgExtensions.kt` KDoc — fixed (plan 18-29)

## Suggested approach
`grep -rln 'whenever' --include=*.kt --include=*.md` (excluding `.planning/`, `.serena/`, `sessions/`), then per-file replace DSL `whenever(`→`runIf(` while preserving the English word "whenever" in prose/comments. Verify `./gradlew build` stays green (KDoc-only changes won't affect output). No ROM sweep needed.

---

> FIXED (Phase 21, plan 21-05): doc/KDoc `whenever(` → `runIf(` sweep complete. Functional sites already migrated in Phase 18 (plans 18-29/18-30). 49 occurrences replaced across 14 files; 6 occurrences intentionally kept with rationale (see inventory below).
>
> **Grep inventory (post-fix) — REPLACED occurrences (49 total across 14 files):**
> - `README.md` — 9 occurrences (lines 24-29, 86, 141-144, 182-183)
> - `gbkt-core/src/main/kotlin/io/github/gbkt/core/References.kt` — 3 occurrences (lines 24, 66, 85)
> - `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/Expr.kt` — 1 occurrence (line 116)
> - `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/ScriptOp.kt` — 1 occurrence (line 661)
> - `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ActorBuilder.kt` — 2 occurrences (lines 56, 352)
> - `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/CollectionBuilders.kt` — 2 occurrences (lines 86, 197)
> - `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/CombatEngineBuilder.kt` — 2 occurrences (lines 49, 50)
> - `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/GameBuilder.kt` — 1 occurrence (line 296)
> - `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SceneBuilder.kt` — 1 occurrence (line 177)
> - `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/UIBuilders.kt` — 1 occurrence (line 143)
> - `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/WorldBuilders.kt` — 1 occurrence (line 517)
> - `gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/domain/CombatStates.kt` — 2 occurrences (lines 26, 29)
> - `gbkt-genre-sport/src/main/kotlin/io/github/gbkt/genre/sport/codegen/SportVisitor.kt` — 1 occurrence (line 151)
> - `gbkt-genre-platformer/src/test/kotlin/.../PlatformerInputEmissionTest.kt` — 2 occurrences (lines 47, 261)
> - `gbkt-genre-platformer/src/test/kotlin/.../LevelCardSceneBuilderTest.kt` — 2 occurrences (lines 28, 88)
> - `gbkt-examples/breakout/CLAUDE.md` — 4 occurrences
> - `gbkt-examples/pong/CLAUDE.md` — 7 occurrences
> - `gbkt-examples/simple-physics/CLAUDE.md` — 2 occurrences
>
> **Kept occurrences (6 total, with rationale):**
> - `CHANGELOG.md:13,15` — Migration guide text: shows the deprecated name `whenever(` as the thing users need to migrate FROM. Correct to keep.
> - `CONTRIBUTING.md:461` — Migration table "Old DSL" column showing `whenever(` as deprecated. Correct to keep.
> - `gbkt-backend-gbdk/CLAUDE.md:32` — Historical explanation of the bucket-b bug path; describes the syntax that was active when the Phase 07.9 bug occurred. Accurate historical documentation.
> - `gbkt-backend-gbdk/src/main/kotlin/.../ExprVisitor.kt:105` — Same historical context for the Phase 07.9 fix. Accurate historical documentation.
> - `gbkt-lang/CLAUDE.md:49` — Pipeline diagram showing `ScriptBuilder.whenever()` which still exists as a `@Deprecated` method in the codebase. Accurate reference to the still-extant API.
