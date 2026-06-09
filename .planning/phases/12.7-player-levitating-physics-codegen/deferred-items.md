# Phase 12.7 — Deferred Items (Out-of-Scope Discoveries)

Per Plan executor SCOPE BOUNDARY rule: only auto-fix issues DIRECTLY caused by the current task's
changes. Out-of-scope discoveries are logged here and NOT fixed.

## 2026-05-26 — Plan 12.7-12 — anchor4MetaspriteAnimation pixel-diff failure

**Discovered during:** Plan 12.7-12 Task 1 — running `:gbkt-examples:platformer-template:test
--tests "*PlatformerTemplateUatTest*"` to capture anchor-2 + anchor-5 evidence.

**Failure:** `PlatformerTemplateUatTest.anchor4MetaspriteAnimation()` — assertion at
`PlatformerTemplateUatTest.kt:595`:

```
Phase 12.5 D-08 acceptance: facing-right vs facing-left pixel diff is 6.60% (must be > 10%).
Files: anchor-4/01-walk-frame-0.png vs anchor-4/04-facing-left.png
```

**Why deferred:**
1. Scope is Phase 12.5 D-08 / REQ-3b (visible-hflip walk-cycle, anchor-4), not Phase 12.7
   (player-levitating snap-emission, anchor-2 + anchor-5).
2. The test method's own assertion message says "REQ-3a (human-verify) is the primary closure
   signal — proceed to checkpoint for duck-art approval" — i.e., this mechanical-diff gate is
   secondary to the human-verify closure that has presumably already happened upstream.
3. Plan 12.7-12 was not asked to revise anchor-4 capture timing or threshold.
4. The 6 PNGs + 2 variables.txt that Plan 12.7-12 is responsible for (anchor-2/* and
   anchor-5/*) were ALL captured successfully under the Plan 12.7-11 fixed snap emission
   (binary diffs vs prior commit a9a23d83 confirmed; all > 1KB; D-06 trace fields all
   present).

**Routing:** If anchor-4 REQ-3b needs re-closure under tightened pixel-diff threshold, route
through a dedicated revision of Phase 12.5 (or a new sibling phase). Do NOT inline-revise
under Phase 12.7.
