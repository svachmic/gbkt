# SEED-025 — Remove deprecated `combatIsInState(String, String)` overload in v0.2.0

**Origin:** SonarCloud Info-issue sweep of PR #33 (`feat/d_and_d_gaps`), 2026-06-10 — the single S1133 finding ("remove this deprecated code someday")
**Status:** Open — scheduled intent: v0.2.0 (next minor)
**Routing:** One-plan removal inside any v0.2.0 DSL/API phase; no discuss-phase needed on its own.
**Blast radius:** `gbkt-genre-rpg/.../dsl/RpgExtensions.kt` (~line 421) + any remaining call sites in examples/tests/docs.

## Problem

The magic-strings sweep (9fb7240c, Project Rule #1) replaced
`combatIsInState(stateId: String, battleId: String)` with the typed
`combatIsInState(CombatStateId, BattleRef)` and left the String overload as a
`@Deprecated(ReplaceWith(...))` escape hatch. The tagged v0.1.0 shipped the
String overload **un-deprecated**, so deleting it immediately would break v0.1.0
adopters with no deprecation release in between. Decision (2026-06-10): keep it
for one release cycle.

Until removal, SonarCloud carries one open S1133 Info issue on PR #33 — this is
intentional; optionally mark it "Accepted" in the SonarCloud UI with a pointer
to this seed.

## Goal

In v0.2.0: delete the String overload, migrate any straggler call sites to the
typed form, drop the now-unused imports, and confirm the S1133 issue closes on
the next analysis.

## Scope sketch

1. `grep -rn "combatIsInState(\"" gbkt-examples/ gbkt-genre-rpg/src/test` — migrate stragglers.
2. Delete the deprecated function + its KDoc from `RpgExtensions.kt`.
3. Mention the removal in the v0.2.0 release notes (breaking for String-API users).
4. Check at the same time whether other `@Deprecated` v0.1.x escape hatches are
   due for the same train, so removals batch per release ([[SEED-023]] may add
   `whenever` to this train).

## Discovery hooks

- `RpgExtensions.kt` ~line 413-422 — the deprecated overload with `ReplaceWith`.
- `CombatStateId` (gbkt-ir), `BattleRef` (gbkt-genre-rpg) — the typed replacements.
