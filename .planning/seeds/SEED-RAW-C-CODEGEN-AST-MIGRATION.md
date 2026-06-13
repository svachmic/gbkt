# SEED — Migrate raw-C escape-hatch generators to the C AST

> **Triage:** RE-DEFERRED — [TRIAGE.md#SEED-RAW-C-CODEGEN-AST-MIGRATION](.planning/phases/16-seed-triage/TRIAGE.md#SEED-RAW-C-CODEGEN-AST-MIGRATION) · 2026-06-12

**Origin:** SonarCloud HIGH-issue review of PR #33 (`feat/d_and_d_gaps`), 2026-06-10
**Status:** Open — not yet bound to a target phase
**Routing:** Needs its own phase (discuss-phase + research). Changing the `CollectionCodegen`
contract and rebuilding two large raw-string builders on the C AST is a codegen-architecture
effort with ROM-output blast radius, not an inline patch.
**Blast radius:** `gbkt-backend-api` (interface contract), `gbkt-backend-gbdk` (two generators,
possibly new AST affordances + CEmitter emission rules), `gbkt-genre-sport` (one generator).
Generated-C formatting will change; behavior must be proven equivalent via ROM smokes and
emulator tests. Note: racing/sport codegen has **no example ROM** to smoke-test today — the
phase should add one (or an emulator test) before migrating `SportVisitor`.

## Problem

The v0.1.0 compiler rewrite (PR #33) replaced string-concat codegen with a typed C AST
(`CFile`/`CFunction`/`CStatement`/`CExpr` + `CEmitter`), but three generators still build raw C
text because the AST lacks affordances they need. SonarCloud flags their structural literals
(S1192); extracting `CLOSING_BRACE`-style constants would be smoke-and-mirrors, so the findings
were **Accepted** in SonarCloud with reference to this seed:

| Sonar issue key | Site | Flagged literal | Why it's structural |
|---|---|---|---|
| `AZ6wAfo4USgq0rpN4-y5` | `GBDKCollectionCodegen.kt:139` | `"    UINT8 i;"` ×8 | every hash-table fn re-emits the probe-loop preamble |
| `AZ6wAfo4USgq0rpN4-y6` | `GBDKCollectionCodegen.kt:147` | `"        }"` ×6 | closing braces in `buildString` emission |
| `AZ6wAfo4USgq0rpN4-y7` | `GBDKCollectionCodegen.kt:164` | `"    return 0;"` ×3 | shared miss-path of lookup/get/contains |
| `AZ6wAfnhUSgq0rpN4-yf` | `MetaspriteVisitor.kt:350` | `"            break;\n"` ×4 | one `break` per flip-variant switch case |
| `AZ6wAfs7USgq0rpN4-0J` | `SportVisitor.kt:745` | `"        }"` ×5 | closing braces in the 110-line AI heading block |

## Goal

1. **`GBDKCollectionCodegen`** — change the `CollectionCodegen` contract (gbkt-backend-api)
   from `String` to C AST nodes, then rebuild the hash-table/list functions on
   `CFunction`/`CFor`/`CIf`. The linear-probe loop shared by insert/lookup/get/contains/remove
   becomes one composable builder instead of six re-emissions.
2. **`MetaspriteVisitor.buildMoveMetaspriteCallExpr`** — rebuild the flip-variant dispatch on
   `CSwitch`/`CSwitchCase` (the four cases are one template over
   `move_metasprite_{flipy,flipxy,flipx,ex}`).
3. **`SportVisitor.buildAiHeadingPickWithFallback`** — rebuild the AI decision tree on
   `CIf`/`CFor`/`CSwitch` expression trees.
4. Close the AST gaps that forced these escape hatches: no include node (includes are
   `List<String>` on `CFile`, now centralized in `GBDKIncludes`), no macro-statement node
   (GBDK macros like `DISABLE_RAM;` ride on `CRawCode`, now vended by `GBDKMacros`).
   Related: [[SEED-PHASE-X-CPAREN-EXPR-IN-C-AST]] (`.planning/seeds/SEED-PHASE-X-CPAREN-EXPR-IN-C-AST.md`)
   tracks a missing parenthesized-expression node — same root cause, fold into the same phase.
5. Re-open (or let re-analysis close) the five accepted Sonar findings once the raw builders
   are gone.

## Constraints

- Generated-C **formatting** may change (CEmitter conventions), but emitted ROMs must be
  behaviorally identical — verify per the ROM-build smoke rule plus emulator tests; add a
  racing example or emulator coverage for `SportVisitor` first.
- Golden/text-asserting codegen tests will need updating in the same phase — diff intent,
  not whitespace.
