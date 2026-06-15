# Phase 21: Codegen Fixes — Platformer and Remaining Seeds - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-14
**Phase:** 21-codegen-fixes-platformer-and-remaining-seeds
**Areas discussed:** Unlisted-seed disposition, Heavy FIX-06 refactor appetite, Platformer fix-vs-accept, Phase shape & byte-identity

---

## Unlisted-seed disposition (Criterion 5)

| Option | Description | Selected |
|--------|-------------|----------|
| Each gets a disposition | Every seed reaches terminal disposition (fix-in-place or backlog-move) — literal empty dir | ✓ |
| Named scope only + bulk move | Only FIX-05/06 named work; bulk-move the 5 extras without per-seed analysis | |

**User's choice:** Each gets a disposition.

| Option | Description | Selected |
|--------|-------------|----------|
| Fix all three now | Land SEED-027/028/029 in Phase 21 (trivial/byte-identical/cosmetic) | ✓ |
| Fix 027/028, defer 029 | Fix Phase-18 loose ends; defer the ~25-file doc sweep | |
| Backlog all three | Move all three to v0.2.0 | |

**User's choice:** Fix all three now.

| Option | Description | Selected |
|--------|-------------|----------|
| Move both to backlog | SEED-023/025 need deprecation cycle / explicitly v0.2.0 → backlog | ✓ |
| Pull 023 into Phase 21 | Do whenever→runIf deprecation now | |

**User's choice:** Move both to backlog.

**Notes:** Criterion 5 interpreted literally. SEED-027/028 were "bound to Phase 18" but never closed (real loose ends); 029 is a Phase-18 verification residual.

---

## Heavy FIX-06 refactor appetite

| Option | Description | Selected |
|--------|-------------|----------|
| Defer to v0.2.0 backlog | ZONE-MAGIC-STRING wide-blast migration too risky at hardening close; dedicated v0.2.0 phase | ✓ |
| Land it in Phase 21 | Full delegate migration + every zone() site rewrite now | |

**User's choice:** Defer ZONE-MAGIC-STRING to v0.2.0 backlog.

| Option | Description | Selected |
|--------|-------------|----------|
| SEED-020 serializer | Contained to gbkt-ir, no codegen blast — land | ✓ |
| SEED-022 predicate | Small, pairs with SEED-021 visitor — land | ✓ |
| SEED-017 sport-zone | Moderate refactor, no shipping example exercises it — (not selected → defer) | |

**User's choice:** Land SEED-020 + SEED-022; SEED-017 deferred to v0.2.0.

**Notes:** Criterion 3 explicitly permits "re-deferred with evidence", so ZONE-MAGIC-STRING + SEED-017 re-deferral is compliant. Planner must update REQUIREMENTS.md FIX-06 status.

---

## Platformer fix-vs-accept (FIX-05, all LOCKED-visual)

| Option | Description | Selected |
|--------|-------------|----------|
| Land the DSL lift | Move pivot_adjust into tilemapCollision { }, delete fallback constants | ✓ |
| Defer refactor to v0.2.0 | Pivot fix works; defer fragility cleanup | |

**User's choice:** Land the SEED-021 DSL lift (pairs with SEED-022).

| Option | Description | Selected |
|--------|-------------|----------|
| Reposition spawn coords | Move hardcoded spawn to bottom-ground row, example-only | |
| Add per-zone spawnPosition DSL | Each zone owns start coords (framework primitive) | ✓ |

**User's choice:** Per-zone spawn DSL. **Notes:** Research flag captured — `ZoneBuilder.spawn(x,y)` already exists (WorldBuilders.kt:247, Phase 12.6); planner must reconcile, likely just wire the template to call it rather than add a duplicate.

| Option | Description | Selected |
|--------|-------------|----------|
| Investigate, then decide | Run diagnostic ladder; fix if real off-by-one, else accept w/ sign-off | ✓ |
| Accept as-is, close with sign-off | Treat 1-2px as within-tolerance | |
| Full fix required | Commit to a code fix regardless of cause | |

**User's choice:** Investigate, then decide (SEED-PHASE-13). **Notes:** Distinct from the by-design horizontal overhang learning — this is vertical foot alignment.

---

## Phase shape & byte-identity

| Option | Description | Selected |
|--------|-------------|----------|
| Unchanged-set guard + targeted proof | Byte-identity on untouched examples; changed ones via UAT visual + emission tests | ✓ |
| Drop byte-identity, rely on tests+visual | Skip the oracle entirely | |

**User's choice:** Unchanged-set guard + targeted proof.

| Option | Description | Selected |
|--------|-------------|----------|
| Fix first, then re-shoot all anchors | One post-fix capture pass; anchors double as fix evidence + Criterion-1 confirm | ✓ |
| Confirm baseline first, fix, re-shoot | Two capture passes (pre + post) | |

**User's choice:** Fix first, then re-shoot all 3 GBC anchors.

---

## Claude's Discretion

- Exact test method/assertion names, evidence PNG filenames, hashing commands for byte-identity diffs, the precise SEED-PHASE-13 diagnostic order, and whether spawn-polish needs any new DSL at all (likely not).

## Deferred Ideas

- SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION, SEED-017, SEED-023, SEED-025 → `.planning/backlog/v0.2.0/`.
- Merging PR #77 (S3776) — assess after Phase 21 closes; not Phase 21 work.
