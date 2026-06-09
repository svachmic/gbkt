# Phase 12: Port platformer_template GBDK example to gbkt — Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-19
**Phase:** 12-port-platformer-template-gbdk-example-to-gbkt
**Areas discussed:** Substrate, Existing platformer disposition, Tile-collision mechanism, Horizontal-scroll camera codegen gap, Plan budget, Metasprite animation depth, Variable-height jump primitive, Multi-tileset asset pipeline

---

## Substrate

### Q1: How many levels should Phase 12 port from the platformer_template reference?

| Option | Description | Selected |
|--------|-------------|----------|
| 1 level only | Single-screen-or-wider level, no level-switching surface. Smallest integration check. ~13-14 plans. | |
| 2 levels | Cross-level switching + 2 banked zones. Validates multi-zone allocateZoneBanks. ~17 plans. | |
| 3 levels (faithful) | Full reference parity: 2 tilesets, 3 maps, world-switch transition card. ~22+ plans. | ✓ |

**User's choice:** 3 levels (faithful)
**Notes:** Maximum reference parity over the 1/2-level minimum-integration options. User accepts feature-demo shape and the larger plan budget.

### Q2: How do we reconcile 3-level faithful scope with the 'ONE named codegen bug-fix' hard cap inherited from Phase 9/10/11?

| Option | Description | Selected |
|--------|-------------|----------|
| First bug = named; rest = seeds + Phase 12.1 | Keep Phase 9/10/11 discipline; additional gaps → seeds. | |
| Lift the cap; explicit multi-bug integration phase | Acknowledge Phase 12 IS integration; plan handles N named codegen bug-fixes inline. ~25+ plans. | ✓ |
| Pre-budget exactly 2-3 named slots | Compromise; cap blast radius. | |

**User's choice:** Lift the cap; explicit multi-bug integration phase
**Notes:** Phase becomes the explicit multi-bug integration check. Bounded by `feedback_route_to_proper_phase_when_blast_radius_is_wide.md` — orthogonal/wide defects still escalate.

### Q3: Given 3-level faithful + lifted bug cap, how many UAT anchors should Phase 12 lock?

| Option | Description | Selected |
|--------|-------------|----------|
| 3 anchors (Phase 9/10 default) | Title→gameplay / tile-collision / level-switch. | |
| 5 anchors (faithful integration) | One anchor per integration surface: scene-transition / tile-collision / scroll / animation / level-switch. | ✓ |
| 4 anchors | Fold one of the 5 surfaces. | |
| You decide | Defaults to 4. | |

**User's choice:** 5 anchors (faithful integration)
**Notes:** Second one-time expansion beyond Phase 11's 4-anchor one-time expansion. Phase 11 D-09 had explicitly stated "Phase 12 is NOT pre-licensed to ≥4 anchors"; user knowingly accepted the 5-anchor justification (5 distinct integration surfaces).

### Q4: How should the title screen + level-transition card be rendered?

| Option | Description | Selected |
|--------|-------------|----------|
| Window-text idiomatic (gbkt convention) | `print(...)` on window layer. Skip banked-tile-screen path. | |
| Banked tile-data screens (faithful to reference) | `ShowCentered()`-equivalent: banked tileset+tilemap for title + NextLevel cards. | ✓ |
| Window-text title + banked NextLevel card | Compromise. | |

**User's choice:** Banked tile-data screens (faithful to reference)
**Notes:** Adds 2 menu screens as banked data units (title + NextLevel), bringing total banked zones to ~5-7.

---

## Existing platformer disposition

### Q5: What happens to `gbkt-examples/platformer/` when the reference port lands?

| Option | Description | Selected |
|--------|-------------|----------|
| Retire — replace contents with reference port | Reuse `gbkt-examples/platformer/` dir; overwrite contents. ROADMAP-suggested. | |
| Add new `gbkt-examples/platformer-template/` | New subdirectory; existing stays as genre-DSL showcase. | ✓ |
| Move existing platformer to `gbkt-examples/platformer-genre-demo/` + add new | Rename + relocate. | |
| You decide | Defaults to retire. | |

**User's choice:** Add new `gbkt-examples/platformer-template/`
**Notes:** Initial decision to keep both, with new subdir for reference port. (Later revised in Q12 to retire existing.)

### Q6: Should the reference port's existence trigger any restructuring of the existing `gbkt-examples/platformer/`?

| Option | Description | Selected |
|--------|-------------|----------|
| No change — existing platformer stays as-is | Lowest churn. Names self-distinguish. | ✓ |
| Add 'see also' cross-references | README cross-links. | |
| Rename docs to clarify scope | Update title to 'Genre-DSL Platformer Showcase'. | |

**User's choice:** No change — existing platformer stays as-is
**Notes:** Superseded by Q12 below (retirement during Phase 12 close).

### Q12 (revisit, after Q11 surfaced existing-example brokenness): Given the existing `gbkt-examples/platformer/` doesn't really work, what's its fate in Phase 12's close?

| Option | Description | Selected |
|--------|-------------|----------|
| Retire it from `gbkt-examples/settings.gradle.kts` during Phase 12 close | Remove subproject entry + delete (or archive) dir. `platform()`/`goalZone()` DSL primitives REMAIN in genre-platformer. | ✓ |
| Replace existing platformer's contents with reference port (no new directory) | Reverse Q5. | |
| Keep both — fix existing platformer in Phase 12.1 or later | Risks 'fix later' never happens. | |
| Keep both — plan a separate fix-up plan inside Phase 12 | Bigger phase scope. | |

**User's choice:** Retire it from `gbkt-examples/settings.gradle.kts` during Phase 12 close
**Notes:** Revises Q5+Q6. Final state: new `gbkt-examples/platformer-template/` is the working playable example; existing `gbkt-examples/platformer/` is retired at phase close; DSL primitives stay.

---

## Tile-collision mechanism

### Q7: How should the reference port express tile-based collision against banked tilemap data?

| Option | Description | Selected |
|--------|-------------|----------|
| Add minimal tilemap-collision primitive to genre-platformer this phase | New DSL `tilemapCollision`/`solidThreshold`. Phase 12 substrate is FIRST consumer. | ✓ |
| Use raw `whenever` + tile-lookup at game-script level | Smaller DSL surface; verbose game code. | |
| Accept as named codegen surface; design later | Discovery-driven. | |
| Extend existing `platform(SOLID)` to accept a tile-region | Reuses existing genre primitive; may twist semantics. | |

**User's choice:** Add minimal tilemap-collision primitive to genre-platformer this phase
**Notes:** New DSL surface justified by integration need + scalable-abstraction criterion.

### Q8: What is the DSL shape for the new tilemap-collision primitive?

| Option | Description | Selected |
|--------|-------------|----------|
| Per-zone solid-threshold (closest to reference) | `zone { tileset(...); solidThreshold(17) }`. | |
| Per-tile solidity annotations on tileset | `tileset(asset(...), solid = listOf(0..16))`. | |
| On the platformer-physics block | `platformerPhysics { tilemapCollision { ... } }`. | |
| You decide | Defaults to per-zone. | |

**User's choice (free-text):** "Could we expand platformer-physics so that it works per level as well? That would be the most flexible and DSL appropriate solution I believe."
**Notes:** User redirected to a deeper restructuring — per-level `platformerPhysics` overrides.

### Q9: How should per-level platformer-physics be scoped in the DSL?

| Option | Description | Selected |
|--------|-------------|----------|
| Optional per-level overrides; game-level defaults inherited | Backwards-compatible; recommended. | ✓ |
| Required per-level; no game-level fallback | Most explicit; biggest authoring burden. | |
| Game-level only; per-level passed via sub-block | `platformerPhysics { level(z) { ... } }`. | |
| You decide | Defaults to option 1. | |

**User's choice:** Optional per-level overrides; game-level defaults inherited
**Notes:** Re-entrant `platformerPhysics { }` block inside `zone { }` shadows specific fields; missing fields inherit from game-level defaults.

### Q10a: Bounding-box probe shape — Reference does 5-point collision sampling. How should this lower into gbkt?

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-generate 5-point check from actor hitbox (recommended) | Framework derives probe points from `hitbox(0, 0, 8, 24)`. | ✓ |
| Expose probe-point list to user | Verbose DSL. | |
| Single-point collision only | Naive; produces 'stuck in ground' artifact. | |

**User's choice:** Auto-generate 5-point check from actor hitbox
**Notes:** Hidden from user DSL; framework matches reference's player.c lines 158-200 pattern.

### Q10b: Where does the `IsTileSolid()` helper live in the generated C?

| Option | Description | Selected |
|--------|-------------|----------|
| HOME bank NONBANKED with SWITCH_ROM wrapper (Plan 07.4-30 pattern) | Inherits Phase 07.4-30 + Phase 11 D-12 invariant 2 pattern. | ✓ |
| Per-zone BANKED helper | Faster; not faithful to reference. | |
| You decide | Defaults to option 1. | |

**User's choice:** HOME bank NONBANKED with SWITCH_ROM wrapper
**Notes:** Reuses the existing wrapper convention.

### Q10c: ONE_WAY tile-type — only solid, or solid + one-way?

| Option | Description | Selected |
|--------|-------------|----------|
| Solid-only (matches reference) | Binary; tile < threshold = solid. | |
| Add ONE_WAY tile-type via secondary threshold | `oneWayThreshold(M)` where M > N. | |
| You decide | Defaults to solid-only. | |

**User's choice (free-text):** "Solid only, but add SEED for one_way to be added in the future."
**Notes:** SEED-PHASE-12-ONE-WAY-TILE created at phase close.

### Q10d: Relationship between new `tilemapCollision` and existing `platform()` rectangles?

| Option | Description | Selected |
|--------|-------------|----------|
| Both surfaces coexist (recommended) | No deprecation; choose by level geometry. | |
| Tilemap-collision replaces platform() when active | Cleaner; breaks existing example combo. | |
| Deprecate `platform()` rectangles | Breaking change. | |

**User's choice (free-text):** "Current example doesnt really work. I need an example that works, but I want the DSL to scale. I don't want dead code but I also want modern DSL with the right abstractions."
**Notes:** Both surfaces coexist as scalable abstractions; user surfaced the brokenness of existing example (leading to Q12 revision retiring it).

---

## Horizontal-scroll camera codegen gap

### Q11: How should Phase 12 handle the missing column-by-column `set_bkg_submap` scroll codegen path?

| Option | Description | Selected |
|--------|-------------|----------|
| Add minimum scroll-tilemap update codegen this phase | Extend `PlatformerVisitor.buildCameraUpdateFunction()`. | ✓ |
| Substrate single-screen levels (20 tiles wide) | Sidesteps gap; conflicts with 3-level faithful. | |
| Hardware-scroll only; visual artifact accepted | UAT visual-evidence rule would fail. | |
| Defer scroll codegen to Phase 13 | Conflicts with substrate. | |

**User's choice:** Add minimum scroll-tilemap update codegen this phase
**Notes:** Second named codegen surface (after tile-collision); fits lifted bug cap.

### Q13a: Scroll direction support?

| Option | Description | Selected |
|--------|-------------|----------|
| Horizontal only (matches reference) | Smallest new surface. | ✓ |
| Horizontal + vertical | Bigger codegen surface; not exercised. | |
| You decide | Defaults to horizontal-only. | |

**User's choice:** Horizontal only (matches reference)

### Q13b: Where does the scroll-update logic codegen live?

| Option | Description | Selected |
|--------|-------------|----------|
| Inside existing camera-update function (recommended) | Extend `buildCameraUpdateFunction()`. Mirrors reference. | ✓ |
| New dedicated `_camera_redraw_columns()` function | Cleaner separation; one more wiring step. | |
| You decide | Defaults to option 1. | |

**User's choice:** Inside existing camera-update function

### Q13c: How is the player's 'past half-screen' camera-tracking trigger expressed in DSL?

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-derived from `platformerCamera { smoothFollow() }` (existing DSL) | No new DSL. | ✓ |
| Add explicit `cameraStartX(N)` / threshold DSL | Verbose. | |
| You decide | Defaults to option 1. | |

**User's choice:** Auto-derived from `platformerCamera { smoothFollow() }`

---

## Plan budget

### Q14: Plan budget — lock an explicit floor/ceiling in CONTEXT.md, or let planner derive?

| Option | Description | Selected |
|--------|-------------|----------|
| Lock explicit 25-30 plan range; plan-checker enforces | Tightest discipline. | |
| Lock floor only (≥22); planner picks ceiling | Floor binding; planner picks ceiling from research. | ✓ |
| Let planner derive from research — no explicit budget | Most flex. | |

**User's choice:** Lock floor only (≥22); planner picks ceiling

---

## Metasprite animation depth

### Q15: Metasprite player animation depth?

| Option | Description | Selected |
|--------|-------------|----------|
| Hflip-based 6 frames + frame-multiplier (recommended) | Exercises Phase 10 hflip codegen path. Half asset bytes. | ✓ |
| 12 frames faithful (mirrors reference) | Largest asset; doesn't exercise hflip path. | |
| Reduced 4 frames (idle/walk/jump/turn) | Loses animation as one of the 5 anchors. | |

**User's choice:** Hflip-based 6 frames + frame-multiplier

---

## Variable-height jump primitive

### Q16: Variable-height jump — is `platformerPhysics.jumpHold(maxFrames)` already lowered, or does Phase 12 add it?

| Option | Description | Selected |
|--------|-------------|----------|
| Treat as new primitive Phase 12 adds (likely missing) | Scout: `platformerPhysics` has jumpBuffer but no jumpHold. Third named codegen surface. | ✓ |
| Verify in planning; add if missing, skip if present | Less commitment. | |
| Accept existing lowering; player can't variable-jump in Phase 12 port | Reduced faithfulness. | |

**User's choice:** Treat as new primitive Phase 12 adds

---

## Multi-tileset asset pipeline

### Q17: Multi-tileset asset pipeline confirmation strategy?

| Option | Description | Selected |
|--------|-------------|----------|
| Plan-time verification — confirm during research | Most likely 'just works'. | |
| Pre-budget as candidate named surface #4 | Pre-allocate plan; collapses if research finds it works. | ✓ |
| You decide | Defaults to option 1. | |

**User's choice:** Pre-budget as candidate named surface #4

---

## Claude's Discretion

- **Exact level / scene names + minimum-viable substrate** (D-claude-1) — planner picks Kotlin-idiomatic names (e.g. `world1Area1Zone`).
- **Exact timing for `gbkt-examples/platformer/` retirement** (D-claude-2) — recommended last plan before phase close.
- **Cartridge config — `"ROM_ONLY"` or `"MBC1"`** (D-claude-3) — FFD verdict + ROM size determines.
- **GBC vs DMG target** (D-claude-4) — recommended `gbcTarget = GBC_COMPATIBLE`.
- **Joypad edge-detection parity** (D-claude-5) — verify `buttons.a.pressed` emission matches reference's intent.
- **Level-end trigger DSL** (D-claude-6) — `goalZone()` rectangle vs zone-width-derived expression; planner picks.
- **Asset-pipeline tilesets — reference's `res/graphics/` or custom-authored** (D-claude-7) — recommended verbatim reuse with attribution.

## Deferred Ideas

- **ONE_WAY tile-type encoding** — Phase 12 ships solid-only; `SEED-PHASE-12-ONE-WAY-TILE` at phase close.
- **Vertical scroll codegen** — horizontal-only this phase; Phase 13 IF future port surfaces it.
- **Typed `Cartridge` enum** — Phase 13 requirement #1.
- **Fixed-point sub-pixel wrapper (`i16FixedVar`)** — Phase 13 requirement #3.
- **Per-genre per-level config-table primitive** — Phase 13 IF cross-genre pattern emerges.
- **`platform()` rectangle deprecation** — explicitly rejected; both surfaces coexist.
- **Fixing existing `gbkt-examples/platformer/` IN PLACE** — rejected in favor of retirement (D-03).
- **5+ UAT anchors** — capped at 5; Phase 12.1 inherits ≤5.
- **Pre-inserting Phase 12.1 placeholder** — conditional on ≥1 surplus seed at port-close.
- **Manual-banking DSL** — REQUIREMENTS.md hard constraint.
