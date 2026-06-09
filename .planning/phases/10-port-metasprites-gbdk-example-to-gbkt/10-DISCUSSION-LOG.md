# Phase 10: Port metasprites GBDK example to gbkt — Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-18
**Phase:** 10-port-metasprites-gbdk-example-to-gbkt
**Areas discussed:** Port fidelity, Scope-cap interaction, UAT floor, GBC compat + BG, Flip/sub-pal DSL shape, Phase 13 routing

---

## Area selection (multi-select)

| Option | Description | Selected |
|--------|-------------|----------|
| Port fidelity vs gbkt idiomatic shape | (A) idiomatic actor+animationStates, (B) faithful new metasprite DSL, (C) hybrid fixed-grid with honestly-fitting asset | ✓ |
| UAT contract floor — the 3 behaviors | Anim cycle / X-Y flip / sub-pal switch / sub-pixel movement / OAM hiwater | ✓ |
| X/Y flip and sub-palette DSL gap | Add this phase, seed to Phase 13, or it IS the named bug-fix | ✓ |
| GBC compatibility & background pattern | GBC compat in/out + BG checkerboard fill in/out | ✓ |
| Post-port framework gaps Phase 13 routing | User-added 5th area | ✓ |

**User's choice:** All four pre-selected + user added "Post-port framework gaps Phase 13 routing".

---

## Port fidelity strategy

| Option | Description | Selected |
|--------|-------------|----------|
| (A) Idiomatic — fixed-grid actor + animationStates | Zero new DSL surface; may lose variable-length OAM surface that 07.3 sat on | |
| (B) Faithful — add `metasprite { frame { tile(x,y,id) } }` DSL + visitor | New IR/builder/visitor; heaviest scope; fights anti-rail 1; matches ROADMAP's literal language | ✓ |
| (C) Hybrid — fixed-grid composite, asset designed to honestly fit | Pick 16x16 4-frame where all frames legitimately use 4 tiles; capture hiwater path as seed | |

**User's choice:** (B) Faithful — add metasprite DSL + visitor.
**Notes:** Captured as D-04 in CONTEXT.md. Acknowledged anti-overfitting rail 1 exception with explicit "defensible across future ports" justification.

---

## Scope-cap interaction with new DSL substrate

| Option | Description | Selected |
|--------|-------------|----------|
| Metasprite DSL + visitor IS the named codegen 'bug-fix' | Reframe the slot; "named thing" is a new thing, not a fix | |
| Metasprite DSL is the port shape; named bug-fix is whatever surfaces first | Substrate is foundational; exploratory bug-fix on top (same Phase 9 D-04) | ✓ |
| Lift the cap for Phase 10 only; revisit at port-close | Explicit acknowledgment in CONTEXT.md; precedent risk | |

**User's choice:** Metasprite DSL is the port shape; named bug-fix is whatever surfaces first.
**Notes:** Captured as D-04 + D-05 in CONTEXT.md. Phase scope is structurally bigger than Phase 9 (acknowledged). Named bug-fix remains exploratory per Phase 9 D-04 discipline.

---

## UAT contract floor — the 3 behaviors

| Option | Description | Selected |
|--------|-------------|----------|
| Animation cycle + X/Y flip + sub-palette switch | Drops sub-pixel movement (Phase 9 dup); most faithful to what reference DEMONSTRATES; requires GBC | ✓ |
| Animation cycle + variable-length OAM hiwater + sub-pixel movement | OAM hiwater is the 07.3 surface story; movement is a regression guard | |
| Animation cycle + X/Y flip + variable-length OAM hiwater | Drops sub-palette (defers GBC); covers no-current-DSL surfaces | |
| Custom | Freeform mix | |

**User's choice:** Animation cycle + X/Y flip + sub-palette switch.
**Notes:** Captured as D-01 in CONTEXT.md. Locks GBC compatibility (sub-palettes are CGB-only). Variable-length OAM hiwater bumped from anchor to candidate-bug / seed status.

---

## GBC compatibility + background pattern

| Option | Description | Selected |
|--------|-------------|----------|
| GBC-compatible ROM + skip the BG checkerboard fill | Minimal port; behavior 3 works on GBC; no orthogonal BG-tile-data surface | |
| GBC-compatible ROM + include the BG checkerboard fill | Visual parity; BG-fill surplus defects → seeds | ✓ |
| GBC-only ROM (no DMG fallback) | Simpler codegen; diverges from reference | |

**User's choice:** GBC-compatible ROM + include the BG checkerboard fill.
**Notes:** Captured as D-09 + D-10 in CONTEXT.md. BG-fill defects routed to seeds per Phase 9 D-05.

---

## X/Y flip and sprite sub-palette DSL shape

| Option | Description | Selected |
|--------|-------------|----------|
| Per-tile attributes inside `metasprite { frame { tile(x,y,id, flipX=..., subPal=...) } }` | Most faithful to GBDK descriptor; largest DSL surface | |
| Per-frame attribute `metasprite { frame { flipX(...); subPal(...) } }`, applied to all tiles | Simpler; matches reference RUNTIME pattern | |
| Runtime accessors: `actor.flipX set true`; `actor.subPalette set 2` | Mirrors `actor.x` / `actor.visible`; matches UAT mutability | ✓ |

**User's choice:** Runtime accessors on actor/metasprite ref.
**Notes:** Captured as D-07 in CONTEXT.md. Slims the metasprite { } primitive significantly — no per-frame or per-tile flip/subpal defaults this phase. Both deferred-defaults shapes captured in Deferred Ideas for future expansion.

---

## Phase 13 routing for post-port discoveries

| Option | Description | Selected |
|--------|-------------|----------|
| Metasprite + flip + subpal land in Phase 10; Phase 13 stays focused on existing 3 reqs | Phase 10 owns OAM surface end-to-end | |
| Metasprite primitive in Phase 10; flip / subpal / hiwater edited INTO Phase 13 | Cross-port surfaces wait for pattern | |
| Defer the split decision to research/planning | Planner re-discusses with user mid-flight | |
| (User freeform) Keep Phase 10 to what's discussed; new DSL surfaced AFTER port works → Phase 13 | Blend: lock Phase 10 to discussed scope; route post-port surfaces to Phase 13 | ✓ |

**User's choice:** Keep Phase 10 scope to what we are discussing. If we surface new DSL requirements after the port is working (e.g., missing else/unless from simple physics), add it to Phase 13's scope.
**Notes:** Captured as D-13 in CONTEXT.md. The metasprite primitive itself does NOT go to Phase 13 — it's Phase 10's substrate. Phase 13 captures cross-port collector items surfaced during/after this phase.

---

## Plan sizing follow-up

After initial CONTEXT.md write, user raised concern that 7-plan (Phase 9-equivalent) sizing would risk 90% of work being deferred because individual plans would be oversized. Amended CONTEXT.md to add D-14 ("many small plans over few large") with explicit ≥12-plan target, plan-sizing heuristic, and a ~18-plan rough frame in Claude's Discretion. Plan-checker MUST flag <12 plans as a sizing concern.

## Claude's Discretion

- **Plan count / wave structure** — Now D-14 directive: target ≥12, expect ~15–18; concrete rough frame in CONTEXT.md.
- **Scene shape** — Single `play` scene mirroring Phase 9 D-06.
- **PNG asset specifics** — Faithful to reference's 4-frame variable-tile-count sprite; actual layout is a discovery moment during the port.
- **Cartridge type** — Inherits Phase 9.4's `CARTRIDGE_ROM_ONLY` string magic; deferred to Phase 13 via D-13 if port surfaces a need for typed enum.
- **Metasprite tile-load timing** — Reference loads in `main()`; gbkt analog is scene.enter or game-level init; planner decides.

## Deferred Ideas

- Per-tile attribute granularity inside metasprite { frame { } }
- Declarative defaults for flip / subPal inside metasprite { } block
- Tile-duplication-fallback for X/Y flip (NES/SMS/GG cross-platform)
- Variable-length OAM hiwater as a 4th UAT anchor behavior
- Sub-pixel movement as a 4th UAT anchor (Phase 9 covers it)
- DMG-only ROM
- GBC-only ROM
- Pre-inserting Phase 10.1 placeholder (same Phase 9 conditional discipline)
- 4th comparison artifact (.asm diff or bank size)
- Moving metasprite primitive into Phase 13 (rejected per D-13)
- Title screen / game-flow scene
