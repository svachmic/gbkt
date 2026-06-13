# SEED — CParenExpr in C AST (CEmitter precedence-aware paren emission)

> **Triage:** RE-DEFERRED — [TRIAGE.md#SEED-PHASE-X-CPAREN-EXPR-IN-C-AST](.planning/phases/16-seed-triage/TRIAGE.md#SEED-PHASE-X-CPAREN-EXPR-IN-C-AST) · 2026-06-12

**Surfaced:** Phase 12.7 (player-levitating-physics-codegen)
**Rounds:** 5 + 6 (gap-closure terminal cluster)
**Symptom:** Plan 12.7-04 emission `_player_y = foot_tile_row << 3u - 24u << 4u;`
  C-parsed as `foot_tile_row << (3u - 24u) << 4u` due to precedence (C11 §6.5.6 `+`/`-`
  higher than §6.5.7 `<<`/`>>`). Player glued to top of screen. Required Plan 12.7-11
  (Path A — intermediate-vars workaround) to fix.
**Path B option (deferred until this seed phase):** Add `CParenExpr` to
  `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CExpr.kt`
  AND rewrite every `CBinaryExpr` printer site in `CEmitter.emitExpr` to insert
  precedence-aware parens. Backend-wide change. Re-enables single-expression
  `CBinaryExpr` nesting without the precedence trap.

**Blast radius:** EVERY visitor that emits `CBinaryExpr` (13 visitors per
  `gbkt-backend-gbdk/codegen/visitor/CLAUDE.md`). EVERY tier1-shape evidence file
  across phases 12, 12.1, 12.3, 12.7 will regenerate. Approximately 50+ test fixtures
  will need re-snapshotting.

**When to run:** When the next codegen change is blocked by precedence; or when a
  proactive audit phase is opened to harden CEmitter against the bug class.

**Sibling-phase suggested entry:**
  /gsd-phase --insert 13 cparen-expr-c-ast-surgery
  (Pass integer parent 13; this is a NEW phase, NOT a child of 12.7.)

**Cross-reference:** Plan 12.7-11 SUMMARY §"Decisions Made" — chose Path A scope-locally
to keep 12.7's blast radius small.
