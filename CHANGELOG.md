# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

## [0.1.1] - 2026-06-13

### Removed

- `whenever(condition, block)` / `whenever(collision, block)` — use `runIf(condition, block)` instead.
  Both overloads lowered to the same `IfOp` IR node; this is a pure rename with no semantic change.
  Migrate by replacing every `whenever(` call with `runIf(`. (SEED-023)

- `combatIsInState(stateId: String, battleId: String)` — use the typed overload
  `combatIsInState(stateId: CombatStateId, battleId: BattleRef)` instead.
  The string overload was a convenience shim that bypassed type-safety; the typed form has been
  available since v0.1.0. (SEED-025)

### Changed

- `config { ramBanks = N }` property-setter syntax is no longer supported — use the function-call
  form `config { ramBanks(N) }` instead. This aligns with the DSL convention that configuration
  methods are function calls, not property assignments. (SEED-028)

## [0.1.0] - 2026-06-09

Initial MVP release. See the project README for the full feature set.
