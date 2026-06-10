# SEED-023 — Unify `whenever` → `runIf` for reactive script sites

**Origin:** SonarCloud Info-issue sweep of PR #33 (`feat/d_and_d_gaps`), 2026-06-10. The `ScriptBuilder.whenever()` KDoc claimed this was "tracked as a pending todo" — no such todo existed; this seed is now the tracker.
**Status:** Open — not yet bound to a target phase
**Routing:** DSL-surface tier-3 phase (deprecation/unification sweep). API-surface change → needs a deprecation cycle, not an inline patch.
**Blast radius:** `gbkt-lang/.../ScriptBuilder.kt`, every example/test using `whenever`, `context/DSL_REFERENCE.md`, IntelliJ-plugin completion/inspections if they special-case the name.

## Problem

`ScriptBuilder.whenever(condition) { }` and `runIf(condition) { }` emit the
identical IR (`IfOp(condition, body, emptyList())`). The KDoc already steers
users: single-frame imperative conditionals should read as `runIf` / `unless` /
`orElse`; `whenever` exists for sites that *read* as reactive triggers. Two
names for one semantic costs DSL surface, docs, and completion noise — and the
"reactive" reading over-promises (it is not an event subscription; it re-tests
every frame the script runs).

## Goal

One conditional construct on the script surface. Tier-3 roadmap intent: unify
`whenever` → `runIf` for reactive sites — either deprecate `whenever` with a
`ReplaceWith(runIf)` cycle, or give `whenever` real reactive semantics that
justify its existence (edge-triggered, fire-on-transition). Decide which in the
discuss-phase; the former is the cheap, honest option.

## Scope sketch (for the discuss-phase)

1. Census of `whenever` usage across examples, tests, and docs.
2. If deprecating: `@Deprecated(ReplaceWith("runIf(condition) { … }"))`, migrate
   call sites, one release-cycle grace per the project's deprecation practice
   ([[SEED-025]] establishes the v0.2.0 removal train).
3. If keeping with real semantics: edge-trigger needs per-site previous-value
   state — a RAM cost on the Game Boy; spec it before committing.
4. Update `context/DSL_REFERENCE.md` and the `whenever` KDoc either way.

## Discovery hooks

- `ScriptBuilder.kt` — `whenever()` KDoc (~line 200-215) now cites SEED-023.
- `runIf` / `unless` / `orElse` family in the same file — target idiom.
