# SEED-028 — ConfigBuilder property→function setter removal: migration note + stale guidance

> VERIFIED-ALREADY-FIXED (Phase 21): `grep -rn "config { ramBanks = " --include=*.kt --include=*.md .` (excluding build/ and .planning/) returns nothing — all 4 stale guidance strings corrected; CHANGELOG.md and CONTRIBUTING.md contain the migration note; fixed by Phase 18 plan 18-12.

> **Origin:** Phase 17 code review WR-04/WR-05 ([17-REVIEW.md#WR-05], [17-UAT.md#2]). Recorded as a developer decision 2026-06-13; routed here so the cleanup is scoped into Phase 18 planning.

**Status:** VERIFIED-ALREADY-FIXED — closed by Phase 18 plan 18-12; archived Phase 21.
**Routing:** Fits Phase 18 naturally — that phase already opens `CONTRIBUTING.md` to document the deprecation-train convention, which is exactly the context for recording this breaking change.
**Blast radius:** 4 stale doc/comment strings + 1 changelog/migration note. No code-behavior change.

## Background

Plan 17-11 replaced the in-DSL `config { romBanks = N; ramBanks = N }` mutable
**property setters** with **function setters** (`config { romBanks(N); ramBanks(N) }`)
— a hard removal with no `@Deprecated` shim. The *Gradle-extension* fallback
(`gbkt { ramBanks = N }`, a `Property<Int>`) still exists and works; only the
in-game-DSL property-assignment form broke.

## Decision (recorded 2026-06-13)

**WR-04 — no `@Deprecated` shim. Accept the hard removal.** Rationale:
- v0.1.0 was the MVP first release; this milestone (v0.1.1) is explicitly
  *Hardening* before wider adoption — external consumers of the in-DSL property
  form are effectively zero.
- The migration is a one-token mechanical change: `ramBanks = N` → `ramBanks(N)`.
- A shim would *reintroduce* the mutable property setters that 17-11 deliberately
  removed, only to delete them again in v0.2.0 — contradicting the unification.
- Instead: record a one-line breaking-change note in the v0.1.1 CHANGELOG /
  release notes.

**WR-05 — fix all stale guidance strings (not optional).** They instruct users to
write code that no longer compiles.

## Scope sketch (for Phase 18 planning)

1. `gbkt-gradle-plugin/.../GbktExtension.kt:166` — `@deprecated` KDoc says *"Set
   `ramBanks` in the DSL `config { ramBanks = N }` block instead"* → update to the
   function-setter form `config { ramBanks(N) }`.
2. `gbkt-gradle-plugin/.../tasks/CompileRomTask.kt:319` — comment using the dead syntax.
3. `gbkt-examples/platformer-template/.../PlatformerTemplate.kt:61` — comment
   "add back `romBanks = 8`" → `romBanks(8)`.
4. `gbkt-examples/metasprites/.../MetaspriteEmissionTest.kt:44` — comment using `romBanks = 2`.
5. Add a v0.1.1 migration line (CHANGELOG / release notes): `config { ramBanks = N }`
   → `config { ramBanks(N) }`.

> **Note:** these are the in-DSL `ConfigBuilder` occurrences only. The
> `CartridgeConfig(romBanks = …, ramBanks = …)` *IR data-class constructor*
> named-argument sites are unrelated and must NOT be touched.

## Cross-references

- Sibling carry-in from the same review: [[SEED-027-gbc-screen-bitsperpixel-correctness]].
- Phase 18 already documents the deprecation-train convention per [[SEED-025]].
