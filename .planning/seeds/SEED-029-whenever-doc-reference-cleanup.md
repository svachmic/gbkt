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
