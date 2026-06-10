---
phase: 12-port-platformer-template-gbdk-example-to-gbkt
plan: 27
status: complete
phase-12-close-routed-to: 12.9
phase-12-close-blocking-gates: []
subsystem: phase-close/admin
tags: [phase-close, seeds, d-13b, d-15, d-18, d-19, d-20, human-checkpoint, BLOCKED, ship-clearance-DENIED, visual-evidence-rule, terminal-plan, routed-to-12.9]

requires:
  - phase: 12-port-platformer-template-gbdk-example-to-gbkt (Plan 12-24)
    provides: 3-signal oracle artifact + 4th-signal bank-layout artifact + Phase 12 closure gate contract
  - phase: 12-port-platformer-template-gbdk-example-to-gbkt (Plan 12-26)
    provides: final clean smoke (D-21) + 7-target regression sweep (D-overfitting-1) + ship-clearance verdict
  - phase: 12-port-platformer-template-gbdk-example-to-gbkt (Plan 12-23 OPTION A)
    provides: 2 deferred codegen defects (DEFECT-1 + DEFECT-2 in main()-loop level-switch handling) routed to Phase 12.6 — the inline-fix-vs-escalate budget input to the human checkpoint

provides:
  - SEED-PHASE-12-ONE-WAY-TILE (D-13b)
  - SEED-PHASE-12-SHARED-TILESET (D-15 RESEARCH-surfaced bonus)
  - SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED (NEW — user-surfaced 2026-05-25 during ship-clearance review of anchor-5/01-near-end.png; Visual Evidence Rule blocker)
  - Updated Phase 12 close gating contract (BLOCKED — Phase 12 does NOT ship until 4 visual gates are GREEN; see ## Phase 12 Close Gating section)
  - Terminal-plan checkpoint state for the human inline-fix-vs-escalate budget + Phase 12 ship-clearance signoff (returned to orchestrator for forwarding) — RESULT: user replied BLOCKED, recorded inline
  - Documented scope-deviation rationale (STATE.md / ROADMAP.md writes deferred to orchestrator per parallel_execution invariant)

affects:
  - phase-12-administrative-close — DENIED; Phase 12 stays OPEN
  - phase-12.6-codegen-fix-followup (cited as the active escalation route; scope MAY expand to absorb the new levitating-not-grounded defect and ISSUE A grass white-pixels — orchestrator decides whether 12.6 alone OR sibling 12.7 carries them)
  - phase-13-framework-primitives (cited as the routing target for both shippable seeds — one-way + shared-tileset; the levitating-not-grounded seed is NOT Phase 13 territory, it is a substrate-correctness blocker for 12.6/12.7)

tech-stack:
  added: []
  patterns:
    - "Terminal-plan executor honors orchestrator parallel_execution invariant: STATE.md / ROADMAP.md writes deferred to orchestrator's queued post-wave-17 phase.complete invocation (avoids merge-conflict with the orchestrator's own tracking commit)."
    - "Seed file shape (Phase 12 cohort): YAML-less markdown header + Context + What's Deferred + Codegen implications + Blast-radius table + Routing Recommendation + JVM-tier marker + Revival Conditions + Related artifacts. Mirrors SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS / SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS shape exactly."
    - "Checkpoint task returned via SUMMARY-then-checkpoint pattern: SUMMARY is written + committed BEFORE the structured checkpoint message returns to the orchestrator, so the orchestrator has a single authoritative artifact to forward to the user (containing the deferred-defect list + 5-anchor verdict matrix + the inline-fix-vs-escalate proposal)."

key-files:
  created:
    - .planning/seeds/SEED-PHASE-12-ONE-WAY-TILE.md
    - .planning/seeds/SEED-PHASE-12-SHARED-TILESET.md
    - .planning/seeds/SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED.md   # added in follow-on commit after user BLOCKED signal
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-27-SUMMARY.md
  modified: []

key-decisions:
  - "USER REPLIED BLOCKED at the Plan 12-27 ship-clearance human-checkpoint (2026-05-25). User-direct words: 'the level transition is broken. The character is also still levitating rather than pinned to the ground and for some reason the grass renders with white pixels (glitches). Why would we close 12 and accept it?' Decision: Phase 12 stays OPEN. The original 'approve with anchor-5-visual-retro-GREEN-pending' framing was wrong — it tried to ship a port whose visual substrate is broken in three distinct ways (anchor-5 level transition, levitation, grass), which violates the CLAUDE.md Visual Evidence Rule. Corrected gating contract written into ## Phase 12 Close Gating below."
  - "Honored orchestrator's parallel_execution invariant: did NOT modify STATE.md or ROADMAP.md despite PLAN.md frontmatter listing them. Per the <objective> override in the prompt, the orchestrator owns those writes via its queued `gsd-sdk query phase.complete 12` invocation post-wave-17 merge. Documented as Rule 3 scope-deviation (blocking-issue auto-resolution under orchestrator authority). NOTE: `phase.complete 12` MUST NOT be invoked yet — Phase 12 is BLOCKED."
  - "Task 1 acceptance criteria fully satisfied via direct Write (slash-command path not attempted): per the user memory `feedback_verify_slash_command_syntax.md`, the `/gsd:capture --seed` slash command would have to be invoked from the parent session; the executor agent's tool surface does not expose slash-command invocation, so direct file Write is the correct fallback per the plan's Task 1 action note."
  - "Both shippable seeds (ONE-WAY-TILE + SHARED-TILESET) explicitly route to Phase 13 per D-20. Neither recommends Phase 12.x absorption. The third (NEW) seed PLAYER-LEVITATING-NOT-GROUNDED is NOT a Phase 13 seed — it is a substrate-correctness blocker routed to Phase 12.6 (or sibling 12.7 if 12.6 scope cannot absorb it; orchestrator decides). ISSUE A (grass white-pixels, already seeded as SEED-PHASE-12-GRASS-TILEMAP-WHITE-PIXELS) is ALSO gating Phase 12 close per user 12-27 review — it was previously framed as orthogonal cosmetic; that framing is now corrected."
  - "Per the <objective> override in the prompt, the D-19 conditional Phase 12.1 absorption is SATISFIED by Phase 12.6's existence — Phase 12.1 already shipped 2026-05-22 and Phase 12.6 was just inserted by the orchestrator to absorb the Plan 12-23 OPTION A escalation (DEFECT-1 + DEFECT-2 in main()-loop level-switch handling). The terminal-sub-phase contract per `feedback_many_small_plans_terminal_subphase.md` still holds: 12.6 must be terminal — no 12.6.1. If 12.6 scope cannot absorb all four blockers (DEFECT-1 + DEFECT-2 + levitating + grass), the orchestrator opens sibling 12.7 (also terminal) rather than 12.6.1."
  - "Checkpoint Task 3 followed the SUMMARY-then-checkpoint pattern: SUMMARY committed BEFORE the structured checkpoint message returned to the orchestrator. User replied BLOCKED. This rewrite captures the corrected state inline so the orchestrator forwards the BLOCKED resolution (NOT a new checkpoint) and queues Phase 12.6 planning instead of `phase.complete 12`."
  - "Visual Evidence Rule (CLAUDE.md §Verification Methodology) explicitly invoked by the user. The original ship-clearance verdict matrix mistakenly treated anchor-5 visual-RED as acceptable closure state because variable-tier evidence was GREEN. That logic is unsound under the rule — the rule was codified specifically to catch the class of bug where state-tier passes while visual-tier shows the broken outcome (Phase 07.4 history). Corrected verdict matrix below treats anchor-5 visual-RED as a BLOCKING gate, with levitation + grass + anchor-1-re-verify added as sibling visual gates."

patterns-established:
  - "Visual Evidence Rule gating for phase-close plans: when ANY visual SC is RED at phase-close time, the close MUST NOT proceed even if all variable-tier / JVM-tier / build-smoke gates are GREEN. The 12-27 round-1 SUMMARY attempted to ship with anchor-5 visual-RED routed to Phase 12.6 with the closure gate as a post-hoc reopen contract; the user (correctly) BLOCKED this. The lesson: phase-close gates are AND-conjunctions across all signal tiers, not OR — the lowest tier wins."
  - "Terminal phase-close plans MAY have to deviate from their own PLAN.md frontmatter when the orchestrator has assumed ownership of cross-cutting state files (STATE.md / ROADMAP.md) for the duration of a parallel-execution wave. The executor MUST honor the orchestrator's override and document the deviation in SUMMARY — this is NOT a Rule 1 bug or Rule 4 architectural change, it's a Rule 3 blocking-issue resolution under the orchestrator's explicit authority delegation."
  - "Human-checkpoint follow-on commits: when a checkpoint returns a BLOCKED signal that requires a new seed + SUMMARY rewrite, perform BOTH in a single follow-on commit (not split across multiple) so the orchestrator sees one authoritative artifact-state transition. The follow-on commit message MUST cite the user's BLOCKED reason in the body."

requirements-completed:
  - D-13b   # SEED-PHASE-12-ONE-WAY-TILE created
  - D-15    # SEED-PHASE-12-SHARED-TILESET created (RESEARCH bonus seed)
  - D-20    # Phase 13 routing for both shippable seeds documented in the seed bodies (Routing Recommendation sections)

requirements-blocked:
  - D-18    # Plan-count floor ≥22 confirmation deferred — Phase 12 cannot mark `shipped` until the close-gating contract below is satisfied; the `phase.complete 12` invocation that would record the plan count is itself BLOCKED.
  - D-19    # Conditional Phase 12.1 (12.6) terminal-sub-phase contract — scope MAY have to expand from 2 defects (DEFECT-1+DEFECT-2) to 4 defects (+ levitating + grass) OR the orchestrator opens sibling 12.7. The terminal constraint (no 12.6.1 / 12.7.1) still holds; only the partition of work across 12.6 vs 12.7 is in question.

phase-12-close-blocking-gates:
  - anchor-5-visual-RED                            # DEFECT-1 card-paint + DEFECT-2 level-skip → Phase 12.6 baseline (existing)
  - player-levitating-not-grounded                 # NEW seed; needs 12.6 (or 12.7) codegen fix + anchor-2 + anchor-5 re-shoot GREEN
  - grass-tilemap-white-pixels                     # SEED-PHASE-12-GRASS-TILEMAP-WHITE-PIXELS (existing); user 12-27 review re-classifies from orthogonal cosmetic to BLOCKING; needs 12.6 (or 12.7) fix + anchor-1 re-verification
  - anchor-1-re-verify-after-grass-fix             # since grass white-pixels affect world1Area1 tilemap render that anchor-1 verified, anchor-1 needs re-verification GREEN AFTER the grass fix lands

metrics:
  duration_round_1: ~5 min
  duration_round_2: ~8 min
  completed: 2026-05-25 (admin work; Phase 12 ship-clearance BLOCKED — phase stays OPEN until 4-gate close)
---

# Phase 12 Plan 27: Phase-Close Seeds + Ship-Clearance Checkpoint — BLOCKED (Phase 12 OPEN)

Plan 12-27 is the TERMINAL plan of Phase 12. It captures the two mandatory seeds
(D-13b `SEED-PHASE-12-ONE-WAY-TILE` + RESEARCH-surfaced D-15
`SEED-PHASE-12-SHARED-TILESET`) and ran the structured ship-clearance human-checkpoint.

**RESULT: BLOCKED — Phase 12 does NOT ship.** The user responded BLOCKED at the
ship-clearance checkpoint, correctly invoking the CLAUDE.md Visual Evidence Rule.
User-direct words:

> "the level transition is broken. The character is also still levitating rather than
> pinned to the ground and for some reason the grass renders with white pixels
> (glitches). Why would we 'close' 12 and accept it?"

The follow-on (round 2) of this plan:

1. **Created a new seed `SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED.md`** for the
   user-surfaced levitation defect (the player metasprite is not pinned to the floor
   tilemap row — visible in `evidence/uat-screenshots/anchor-5/01-near-end.png`).
2. **Rewrote this SUMMARY's verdict sections** to reflect BLOCKED state — the original
   "approved with anchor-5-visual-retro-GREEN-pending" framing is replaced with an
   honest "Phase 12 stays OPEN; 4 visual gates must GREEN before close" contract.
3. **Updated the Phase 12 close gating contract** — see `## Phase 12 Close Gating`
   section below. Phase 12 cannot mark `shipped` until Phase 12.6 (or 12.6+12.7 if
   the orchestrator splits the scope) lands codegen fixes + retro-shoots GREEN.

Per the orchestrator's `<objective>` override, STATE.md / ROADMAP.md writes remain
DEFERRED. The orchestrator's queued `gsd-sdk query phase.complete 12` invocation
**MUST NOT** run until the Phase 12 Close Gating contract is satisfied. Instead, the
orchestrator should queue Phase 12.6 (and possibly 12.7) planning + execution.

## Performance

- **Duration (round 1):** ~5 min (seed authoring + SUMMARY + 2 atomic commits + checkpoint return)
- **Duration (round 2 — user BLOCKED follow-on):** ~8 min (read Plan 12-20 spawn-clarity seed for cross-reference; author SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED; rewrite SUMMARY verdict sections; single follow-on commit)
- **Started:** 2026-05-25T07:26:59Z
- **Round-1 checkpoint return:** 2026-05-25T07:32:36Z
- **User BLOCKED reply received:** 2026-05-25 (mid-execution)
- **Round-2 completion:** 2026-05-25 (this follow-on commit)
- **Tasks:** 1 of 2 auto-tasks completed; 1 human-checkpoint received BLOCKED response. (Plan's Task 2 ROADMAP/STATE writes deferred to orchestrator per `<objective>` — see Deviations.)
- **Files modified:** 3 seed files created (ONE-WAY-TILE + SHARED-TILESET round-1; PLAYER-LEVITATING-NOT-GROUNDED round-2) + 1 SUMMARY created+rewritten; 0 source-tree changes; 0 STATE/ROADMAP writes (deferred to orchestrator).

## Accomplishments

### Task 1 — D-13b + D-15 seeds captured (commit `b6efaee5`)

**SEED-PHASE-12-ONE-WAY-TILE.md** (210 lines, content type: deferred-DSL-extension
seed). Captures the `oneWayThreshold(M)` extension to `platformerPhysics` for
traversable-platform games (Mario / Mega Man / Castlevania style "jump up through,
stand on top" platforms). Contains:

- **Motivation:** Phase 12 ships solid-only via `solidThreshold(N)`; ONE_WAY is
  the third tile class many platformers need.
- **Proposed DSL surface:** `platformerPhysics { solidThreshold(17);
  oneWayThreshold(40) }` partitions tile-index space into SOLID / ONE_WAY /
  PASSABLE; per-level override composes with the existing D-12 per-level
  `platformerPhysics { }` extension pattern.
- **Codegen implications:** `is_tile_solid()` HOME helper becomes `tile_classify()`
  returning enum; 5-point AABB probe gains `_player_vy > 0` AND
  `prev_feet_row < tile_top` gate on the ONE_WAY arm; new
  `_current_level_one_way_threshold` global; per-level shadow extension.
- **Blast radius:** ~4 files (PlatformerBuilders + PhysicsConfig + PlatformerVisitor
  + GBDKPipelineV2); medium scope; no IR-shape changes; no bank-allocation impact.
- **JVM-tier invariant:** parallels Phase 12 D-16 invariant #2; 3-arm branch
  awk-grep + 5-point probe ONE_WAY-arm gate awk-grep.
- **Routing recommendation:** Phase 13 IFF a future port surfaces real need;
  proactive Phase 13 budgeting NOT recommended (no concrete port pull exists today).

**SEED-PHASE-12-SHARED-TILESET.md** (175 lines, content type:
pipeline-deduplication seed). Captures the `ConvertZoneTilesetsTask` shared-tileset
deduplication gap surfaced in RESEARCH §D-15. Contains:

- **Context:** `ConvertZoneTilesetsTask` processes tilesets PER ZONE, so
  `world1Area1Zone` + `world1Area2Zone` (both referencing `world1-tileset.png`)
  produce two byte-identical `_zone_<id>_tileset.c` files. The Phase 12 substrate
  exercises this pattern and contributes ~3KB of the 2× ROM-size delta per
  `oracle-comparison.md` Signal 1.
- **Why Phase 12 chose option (a):** RESEARCH-recommended "accept duplication" for
  Phase 12's multi-bug integration scope; ~3KB ROM overhead within 2× signal threshold.
- **What's deferred:** Two approaches presented — (1) explicit `sharedTileset(asset)`
  DSL concept; (2) automatic content-hash deduplication in
  `ConvertZoneTilesetsTask`. Approach 2 recommended as backward-compatible.
- **Codegen / pipeline implications:** `ConvertZoneTilesetsTask` hash-grouping;
  `ZoneIR.sharedTilesetRef` field; `GBDKPipelineV2.allocateZoneBanks`
  shared-payload accounting (most error-prone touchpoint); `game_metadata.json`
  `sharedTilesetGroupId`; `buildSetupCurrentLevelFunction` unchanged.
- **Blast radius:** ~5 files (Gradle plugin + IR + lang DSL + pipeline + metadata);
  medium-high scope.
- **JVM-tier canary:** `MultiTilesetAllocationTest` (created in Plan 12-15) currently
  ASSERTS the duplication exists; the dedup fix flips its polarity — the test's
  existence is the regression signal that someone partially landed dedup without
  updating the canary.
- **Routing recommendation:** Phase 13 — explicitly listed in RESEARCH as Phase 13
  candidate; triggers on ROM-size pressure OR Phase 13 framework-primitives
  aggregation phase.

### Task 2 — DEFERRED to orchestrator (deviation from PLAN.md frontmatter)

Per the executor `<objective>` override:

> IMPORTANT — STATE.md and ROADMAP.md modification policy:
> This plan's frontmatter lists STATE.md and ROADMAP.md in files_modified, BUT the
> orchestrator has been the sole writer of those files throughout this Phase 12
> wave-by-wave execution. To avoid conflicts with the orchestrator's queued
> post-wave-17 tracking commit (`phase.complete` SDK call), DO NOT modify STATE.md
> or ROADMAP.md in this plan. The orchestrator will mark Phase 12 complete via
> `gsd-sdk query phase.complete 12` AFTER this wave's worktree merges. Honor the
> parallel_execution invariant.

This is honored. The full STATE.md / ROADMAP.md updates that PLAN.md Task 2 would
have authored (Phase 12 status → shipped, 28-plan count, Phase 13 framework-shaping
gaps list, STATE.md `Phase 12 SHIPPED` head entry with seeds list + verdict +
Phase 13 routing summary) are delegated to the orchestrator's
`gsd-sdk query phase.complete 12` invocation.

For traceability, the substantive content that those updates would have carried is
already captured in:

- **Phase 12 status / 28-plan count:** Directory listing
  (`.planning/phases/12-.../{12-01..12-27}-PLAN.md` = 28 files including 12-09b for
  B2 split — visible to orchestrator at phase.complete time).
- **Seeds list:** `SEED-PHASE-12-ONE-WAY-TILE.md` + `SEED-PHASE-12-SHARED-TILESET.md`
  (Task 1 commit `b6efaee5`).
- **Phase 13 framework-shaping gaps:** Both seeds' `## Routing Recommendation`
  sections document the Phase 13 routes; additional candidates from
  CONTEXT D-20 (`bgFill` primitive, per-genre config-table generalization,
  vertical-scroll) remain unchanged from the existing Phase 13 backlog.
- **Verifier inputs:** `evidence/oracle-comparison.md` (Signals 1+2 GREEN; Signal 3
  RED-routed-to-12.6), `evidence/bank-layout-signal.md` (4th signal GREEN),
  `evidence/uat-screenshots/anchor-{1..5}/*`, `evidence/final-smoke.md` +
  `evidence/regression-sweep.md` (Plan 12-26 ship-clearance verdict).

### Task 3 — Human checkpoint RAN and received BLOCKED response

Round 1: structured checkpoint message returned to orchestrator with this SUMMARY
committed at `bb308409`. Original framing proposed ship-clearance approval with the
anchor-5-visual-retro-GREEN gate held as a post-hoc reopen contract.

Round 2: user replied **BLOCKED** citing the Visual Evidence Rule. Three concrete
visible defects named: (a) anchor-5 level transition broken, (b) player levitating
not pinned to ground, (c) grass tilemap white-pixel artifacts. The original framing
was wrong — Visual Evidence Rule requires visual SC GREEN before phase ship, not a
deferred-retro contract. Phase 12 stays OPEN.

Round-2 follow-on actions (this commit):

- New seed `SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED.md` captures the user-surfaced
  levitation defect with suspected root causes ordered for the investigator + Visual
  Evidence Rule alignment + Phase 12 close gating contract.
- This SUMMARY's verdict + checkpoint sections rewritten to reflect BLOCKED state.
- `## Phase 12 Close Gating` section (below) lists the 4 visual gates that must GREEN
  before Phase 12 can ship.
- `frontmatter status: partial` set; Phase 12 itself stays open.

## Task Commits

| Task | Name                                                                  | Commit       | Files                                                                                              |
| ---- | --------------------------------------------------------------------- | ------------ | -------------------------------------------------------------------------------------------------- |
| 1    | D-13b + D-15 seeds                                                    | `b6efaee5`   | `.planning/seeds/SEED-PHASE-12-ONE-WAY-TILE.md`, `.planning/seeds/SEED-PHASE-12-SHARED-TILESET.md` |
| 2    | DEFERRED to orchestrator (parallel_execution invariant)                | n/a          | n/a — see Deviations                                                                               |
| 3    | Human-checkpoint round-1 SUMMARY (proposed ship-clearance)             | `bb308409`   | `12-27-SUMMARY.md` (original verdict-section content)                                              |
| 3    | Round-2 follow-on: BLOCKED + new levitating seed + SUMMARY rewrite     | (this commit) | `.planning/seeds/SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED.md`, `12-27-SUMMARY.md`              |

## Files Created/Modified

**Created (round 1, commits `b6efaee5` + `bb308409`):**
- `.planning/seeds/SEED-PHASE-12-ONE-WAY-TILE.md` — D-13b deferred-extension seed (210 lines)
- `.planning/seeds/SEED-PHASE-12-SHARED-TILESET.md` — D-15 RESEARCH-surfaced bonus seed (175 lines)
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-27-SUMMARY.md` — this file (original)

**Created (round 2, this commit):**
- `.planning/seeds/SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED.md` — NEW seed for the user-surfaced levitation defect (Visual Evidence Rule blocker; routes to Phase 12.6 or sibling 12.7)

**Modified (round 2, this commit):**
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-27-SUMMARY.md` — rewrote verdict sections to reflect user BLOCKED response; added `## Phase 12 Close Gating` section; `status: partial` set in frontmatter

**NOT modified (deviation — see below):**
- `.planning/STATE.md` — orchestrator owns; `phase.complete 12` MUST NOT run while BLOCKED
- `.planning/ROADMAP.md` — orchestrator owns; will be updated by orchestrator AFTER Phase 12.6 (+ optional 12.7) close the visual gates

## Deviations from Plan

### 1. [Rule 3 - Blocking-Issue] STATE.md / ROADMAP.md writes deferred to orchestrator

- **Found during:** Plan execution start (read of executor `<objective>` override)
- **Issue:** PLAN.md Task 2 instructs the executor to modify STATE.md + ROADMAP.md.
  The orchestrator's `<objective>` override explicitly forbids those writes during
  this Phase 12 wave-by-wave execution to avoid conflicts with its queued
  `gsd-sdk query phase.complete 12` tracking commit.
- **Fix:** Skipped Task 2's ROADMAP/STATE writes; documented the deviation in
  this SUMMARY's `key-decisions` + `requirements-deferred-to-orchestrator` +
  `## Files Created/Modified` sections. The substantive content those writes would
  have carried (28-plan count, seeds list, Phase 13 routing) is captured in this
  SUMMARY for the orchestrator's `phase.complete 12` invocation to consume.
- **Files modified:** none (the deviation is the absence of an action)
- **Commit:** N/A (no commit for the deferred work)

### 2. [Rule 3 - Blocking-Issue] D-19 conditional Phase 12.1 absorption satisfied by existing Phase 12.6

- **Found during:** Reading the `<objective>` override + `<task_summary>` note
- **Issue:** PLAN.md Task 3 step 3a contemplates inserting "Phase 12.1" for the
  deferred-defect cluster, but Phase 12.1 already exists (shipped 2026-05-22) and
  Phase 12.6 was just inserted by the orchestrator (post-Plan 12-23 OPTION A) to
  absorb DEFECT-1 + DEFECT-2 from the main()-loop level-switch handling. The
  D-19 terminal-sub-phase contract is fully satisfied by Phase 12.6's existence —
  no new sub-phase needs to be created from this plan.
- **Fix:** Did NOT invoke `/gsd-phase --insert 12`. Recorded Phase 12.6 as the
  active escalation route in this SUMMARY's `key-decisions` + in the checkpoint
  state's `what-built` / `how-to-verify` sections (so the human reads the routing
  resolution inline).
- **Files modified:** none (the deviation is the absence of an action)
- **Commit:** N/A

### 3. [Rule 3 - Blocking-Issue] Direct Write fallback for seed creation (no slash-command invocation)

- **Found during:** Task 1 start
- **Issue:** PLAN.md Task 1 contemplates invoking `/gsd:capture --seed` slash
  command. The executor-agent tool surface does not expose slash-command
  invocation; per user memory `feedback_verify_slash_command_syntax.md`, the
  fallback (direct file Write) is the documented Plan A for this scenario.
- **Fix:** Authored both seeds via direct Write following the shape established by
  existing Phase 12 seeds (`SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS`,
  `SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS`). PLAN.md explicitly allows this fallback:
  "If the slash command is unavailable or the flag differs, fall back to direct
  file creation using the format below."
- **Files modified:** Both round-1 seed files (Task 1 commit `b6efaee5`)
- **Commit:** `b6efaee5`

### 4. [Rule 4 - User-Decision] Ship-clearance verdict re-classified BLOCKED; ISSUE A re-routed orthogonal→gating; new levitating seed added; SUMMARY rewritten in round-2 follow-on

- **Found during:** Round-1 checkpoint return → user BLOCKED reply received
- **Issue:** The round-1 SUMMARY proposed "approve with anchor-5-visual-retro-GREEN-pending"
  ship-clearance, which mistakenly treated visual-RED as acceptable closure state if
  variable-tier / JVM-tier / build-smoke gates were GREEN. This violates the CLAUDE.md
  Visual Evidence Rule (codified after Phase 07.4 specifically to prevent shipping
  with broken visual surfaces when codegen-tier passes). The user correctly invoked
  the rule and named THREE concrete visible defects (anchor-5 level transition,
  player levitating, grass white-pixels) — only ONE (anchor-5) was in the round-1
  blocker list; the other two (levitating, grass) were classified as orthogonal
  cosmetic.
- **Decision:** Rule 4 (architectural change) — re-classifying a phase's ship-clearance
  contract requires user authority. User provided it via the BLOCKED reply. Recorded:
  - Grass white-pixels (ISSUE A): re-classified from orthogonal cosmetic → BLOCKING
    Phase 12 close gate (G3 + G4).
  - Player levitating: NEW defect category — created seed
    `SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED.md` with suspected root causes,
    Visual Evidence Rule alignment, Phase 12.6 routing.
  - Phase 12 close gating contract: rewritten from "approve with retro-pending" →
    "4-gate AND-conjunction with all visual gates GREEN required before ship".
  - `frontmatter status: partial` set; `phase.complete 12` MUST NOT run.
- **Files modified:** New seed `SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED.md` +
  this SUMMARY (verdict + frontmatter + checkpoint sections rewritten)
- **Commit:** (this round-2 follow-on commit)

## Auto-fixed Issues

**None.** Plan 12-27 is administrative seed-capture + checkpoint return only — no
source code touched, no buildRom invocations, no test edits. The four blocking visual
defects (G1-G4 in `## Phase 12 Close Gating`) are out of scope for THIS plan —
they belong to Phase 12.6 (and possibly sibling 12.7).

## Authentication Gates

**None.** Local file authoring only.

## Self-Check

- [x] `.planning/seeds/SEED-PHASE-12-ONE-WAY-TILE.md` exists (round 1)
- [x] `.planning/seeds/SEED-PHASE-12-SHARED-TILESET.md` exists (round 1)
- [x] `.planning/seeds/SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED.md` exists (round 2 — new seed for the user-surfaced levitation defect)
- [x] SEED-PHASE-12-ONE-WAY-TILE.md contains `oneWayThreshold`, `Phase 13`, `blast radius`
- [x] SEED-PHASE-12-SHARED-TILESET.md contains `ConvertZoneTilesetsTask`, `Phase 13`, `MultiTilesetAllocationTest`
- [x] SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED.md contains the user-direct quote, suspected root causes ordered for the investigator, Visual Evidence Rule alignment, Phase 12.6 routing, and Phase 12 close gating contract
- [x] Round-1 commit exists: `b6efaee5` (Task 1 seeds)
- [x] Round-1 SUMMARY commit exists: `bb308409`
- [x] STATE.md / ROADMAP.md NOT modified (per `<objective>` override; honored parallel_execution invariant)
- [x] No source-tree changes (only `.planning/seeds/*.md` + `.planning/phases/12-.../12-27-SUMMARY.md`)
- [x] Human checkpoint returned (round 1) → user replied BLOCKED → follow-on (round 2) captured in this rewrite + new seed
- [x] `frontmatter status: partial` set (Phase 12 stays OPEN; this plan's terminal admin work is done EXCEPT the human-approval gate which the user BLOCKED)
- [x] `phase-12-close-blocking-gates` enumerated in frontmatter (4 gates)
- [x] `## Phase 12 Close Gating` section authored below

## Threat Flags

None. Plan is admin/seed-capture only with zero source surface changes. The levitation
defect IS a substrate-correctness blocker but its FIX (in Phase 12.6) is where the
threat surface lives, not this admin plan.

## Self-Check: PASSED (round 2 — BLOCKED resolution recorded)

All acceptance criteria met for Task 1 (round-1 seeds) + Task 3 (round-1 checkpoint returned, round-2 BLOCKED resolution recorded). Task 2 deferred to orchestrator per `<objective>` override AND must not run until Phase 12 Close Gating below is satisfied. The plan's terminal admin artifacts are in place; Phase 12 itself stays OPEN.

---

## Phase 12 Close Gating

**Status: BLOCKED — Phase 12 does NOT ship until ALL four gates below are GREEN.**

Original Plan 12-26 ship-clearance verdict and the round-1 12-27 SUMMARY proposed a
"approve with anchor-5-visual-retro-GREEN-pending" framing. The user BLOCKED that
framing at the round-1 checkpoint, correctly invoking the CLAUDE.md Visual Evidence
Rule (a phase whose visual surface is broken in three distinct ways cannot ship even
if all variable-tier / JVM-tier / build-smoke gates are GREEN).

The corrected Phase 12 close contract — all FOUR visual gates must GREEN before
`phase.complete 12` runs:

| # | Gate | Status now | What unblocks it |
|---|------|-----------|------------------|
| G1 | **Anchor 5 visual-GREEN re-shoot** (level transition: card paints correctly + level advances 1→2 not 1→3) | RED | Phase 12.6 lands DEFECT-1 + DEFECT-2 codegen fixes → re-run anchor-5 UAT → new `01-near-end.png` + `02-nextlevel-card.png` + `03-level-2.png` show correct visual surfaces |
| G2 | **Player visibly pinned to ground** (across all relevant anchors, especially anchor-2 and anchor-5) | CLOSED | Phase 12.7 Round 6 — H3 grounded-guard fix (Plan 12.7-28) + PNGs human-verified (Plan 12.7-31 APPROVED 2026-05-27). R-02 + R-03 CLOSED. |
| G3 | **Grass tilemap renders without white-pixel artifacts** | RED | Phase 12.6 (or sibling 12.7) lands the fix for `SEED-PHASE-12-GRASS-TILEMAP-WHITE-PIXELS` (per user 12-27 review, this is BLOCKING not orthogonal) → re-shoot world1Area1 visual surface |
| G4 | **Anchor 1 re-verification GREEN AFTER grass fix** | not-yet-required | Triggered conditionally on G3 closure — once the grass fix lands, anchor-1's title/world1Area1 render surface must be re-verified to ensure no regression |

**Orchestrator's role (per `<objective>` parallel_execution invariant):**

- DO NOT run `gsd-sdk query phase.complete 12` yet.
- DO NOT update STATE.md to mark Phase 12 shipped.
- DO update STATE.md to reflect "Phase 12 OPEN — pending 4-gate close per 12-27 SUMMARY".
- Queue Phase 12.6 planning. Decide whether Phase 12.6 scope can absorb all four
  blockers (DEFECT-1 + DEFECT-2 + levitating + grass) OR whether to open sibling
  Phase 12.7. Per `feedback_many_small_plans_terminal_subphase.md`, 12.6 (and 12.7
  if opened) MUST be terminal — no 12.6.1 / 12.7.1.
- After 12.6 (+ optional 12.7) ship GREEN with re-shoot evidence, REOPEN this Plan
  12-27 to record the retro-GREEN closure: append a `## Round 3 — Phase 12 Close
  Approval` section with the post-fix re-shoot evidence inline, flip `status: partial`
  → `status: complete`, and only then run `phase.complete 12`.

**Investigator handoff (for Phase 12.6 planning):**

The four blocking gates trace to FOUR distinct codegen surfaces:

| Gate | Codegen surface | Seed / defect ID |
|------|-----------------|------------------|
| G1 (card-paint + level-skip) | `GBDKPipelineV2.buildMainLoopLevelSwitchGuardIfNeeded` + `buildPhysicsUpdateFunction` (trigger ordering) | DEFECT-1 + DEFECT-2 (Plan 12-23 key-decisions) |
| G2 (levitating) | `PlatformerVisitor.build5PointProbe` + `GBDKPipelineV2.buildPhysicsUpdateFunction` (snap-to-tile-top step) | `SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED` (this plan, round 2) |
| G3 (grass white-pixels) | `ConvertZoneTilesetsTask` (suspected png2asset palette/index handling for `world1-tileset.png`) OR upstream-tileset PNG itself | `SEED-PHASE-12-GRASS-TILEMAP-WHITE-PIXELS` (Plan 12-23) |
| G4 (anchor-1 re-verify) | Same as G3 — conditional re-verification after G3 lands | n/a — re-verification, not a separate defect |

Three of the four are codegen defects in the platformer-template substrate; G3 may
be either codegen (ConvertZoneTilesetsTask palette handling) or a tileset-asset
issue (the source PNG itself). Phase 12.6 (or sibling 12.7) investigator decides
the partition.

---

## Round-1 Human Checkpoint State (ARCHIVED — superseded by user BLOCKED reply)

> **This section is preserved for traceability only.** The round-1 framing proposed
> "approve with anchor-5-visual-retro-GREEN-pending" ship-clearance. The user
> correctly invoked the Visual Evidence Rule to BLOCK that framing. The corrected
> resolution lives in `## Phase 12 Close Gating` above.

**Type (round 1):** checkpoint:human-verify
**Plan:** 12-27 (TERMINAL plan of Phase 12)
**Round-1 progress:** Task 1 complete (seeds committed `b6efaee5`); Task 2 deferred to orchestrator per `<objective>` override; Task 3 awaited human ship-clearance signoff (response received BLOCKED).

### Round-1 asks of the user

1. Confirm OPTION A routing for DEFECT-1 + DEFECT-2 → Phase 12.6 (already inserted).
2. **"approved — ship Phase 12 with anchor-5-visual-retro-GREEN-pending"** OR
   **"blocked: <description>"**.

### Round-1 ship-clearance verdict matrix (proposed by Plan 12-26)

| Gate | Round-1 Verdict | Round-2 Re-classification |
|------|-----------------|----------------------------|
| D-21 final clean smoke | GREEN | unchanged GREEN — build-smoke is independent of visual gates |
| D-overfitting-1 7-target sweep | GREEN | unchanged GREEN — regression sweep is independent of visual gates |
| 3-signal Signal 1 (ROM ratio 2.000) | GREEN (boundary) | unchanged GREEN |
| 3-signal Signal 2 (~35% shorter C) | GREEN (informational) | unchanged GREEN |
| 3-signal Signal 3 (5-anchor UAT) | RED (anchor 5 → 12.6 baseline) | **RED — now properly enforced as BLOCKING; anchor-5 visual-RED + levitating + grass all gate close** |
| 4th-signal bank-layout | GREEN | unchanged GREEN |
| 5 D-16 JVM emission invariants | GREEN | unchanged GREEN — note: these locked the GENERATED C SHAPE, NOT the visual outcome (Visual Evidence Rule: codegen GREEN is necessary but never sufficient for visual SCs) |
| Post-rebuild determinism | GREEN | unchanged GREEN |

### Round-1 user reply (RECEIVED 2026-05-25)

**`blocked: Phase 12 must NOT close until visual closures land.`**

User-direct words:

> "the level transition is broken. The character is also still levitating rather than
> pinned to the ground and for some reason the grass renders with white pixels
> (glitches). Why would we 'close' 12 and accept it?"

→ Triggered round-2 follow-on: new seed `SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED`,
ISSUE A re-classification (grass white-pixels from orthogonal cosmetic → BLOCKING),
G4 anchor-1 re-verification added, this SUMMARY rewrite. No new checkpoint returned —
the BLOCKED state is recorded inline for the orchestrator to act on (queue Phase
12.6 planning instead of phase verification).

## Round 3 — Phase 12 Close Approval

**Round 3 date:** 2026-05-26
**Phase 12.6 status:** SHIPPED — Gate G1 closed.
**Phase 12 status:** STAYS PARTIAL — G2 + G3 + G4 still PENDING. Phase 12.8 owns the final `status: complete` flip + `phase.complete 12` invocation.

### G1 — Anchor 5 visual re-shoot — CLOSED 2026-05-26 by Phase 12.6

- DEFECT-1 closed: `02-nextlevel-card.png` shows card art on a blank BG. Verified
  via direct MCP capture at frame 1198 of the anchor-5 input sequence (live
  capture; the UAT test-harness PNG capture remains unreliable per Cycle 2
  finding — routes to NEW Phase 12.10).
  Evidence: `.planning/phases/12.6-main-loop-level-switch-codegen-fix-phase-12-6/evidence/uat-screenshots/anchor-5/02-nextlevel-card.png`
  + live MCP composite `/tmp/anchor5-compare-v2/cmp-nextlevel-card.png`.
- DEFECT-2 closed: `03-level-2.png` shows `world1-area2` grass tilemap (clean,
  no level-1 wraparound leftover) with player sprite visible on-screen at
  spawn (40, 104). Camera reset to 0 confirmed. MCP frame 1233 bgText is
  byte-identical to reference GBDK frame 1154.
  Evidence: `.../anchor-5/03-level-2.png` + composite `/tmp/anchor5-compare-v3/cmp-level-2.png`.
- Regression guard: `01-near-end.png` UAT-harness shot remains in the
  evidence dir but documents a known harness-timing bug — the captured frame
  is post-trigger (scene already in `nextLevelScene`). Routes to Phase 12.10.
- Runtime gate closed across three debug cycles:
  - D-07 (commit `baea851b`): `_camera_x` / `_old_camera_x` reset in `setup_current_level`
  - D-D07-cycle2 (commit `d3672ebd`): `ShowCentered()` equivalent synthesized in
    `LevelCardSceneBuilder.materialize()` (hide_sprites + fill_bkg_rect + centered tilemap place + DISPLAY_ON)
  - D-08 (commit `b06bf4d1`): windowed submap write (`_bkg_set_level_submap_banked(0u, 0u, 21u, 18u)`)
    replaces wrapping full-tilemap write that corrupted BG cells 0..27 at camera_x=0.
- JVM-tier complement: `LevelSwitchEmissionTest` D-07 + D-08 regressions GREEN;
  `LevelCardSceneEmissionTest` 4/4 GREEN.
- UAT-harness anchor5LevelSwitch passes; anchor4MetaspriteAnimation FAILS 6.60%
  pixel diff (was passing by accident — wrap corruption inflated walking-right
  BG variation; clean windowed write reduces visual delta below the 10%
  threshold). Per the assertion's own message this is a SOFT gate
  (`REQ-3a (human-verify) is the primary closure signal`). Threshold retune
  routed to Phase 12.10.
- ROM smoke gate (D-13): clean `:gbkt-examples:platformer-template:buildRom`
  exits 0; 64 KB ROM, 3 banks (HOME + bank1 + bank2 tilemap data).
  Evidence: `.../evidence/rom-smoke-platformer-template.txt`.
- 7-target regression sweep (D-14): codegen byte-identity GREEN across all 7
  sibling targets (`gameUsesTilemapCollision` gate confirmed). ROM byte-identity
  PASS for 6/7; pong shows toolchain-level non-determinism (4 distinct hashes
  from 4 rebuilds, generated C unchanged). Pong drift is NOT a Phase 12.6
  regression — pre-existing sdcc/lcc non-determinism specific to pong.
  Evidence: `.../evidence/regression-sweep-diff.md` (Pass: 7 / Fail: 0 with `*`).

### G2 — Player visibly pinned to ground — CLOSED 2026-05-27 by Phase 12.7 (Round 6 H3 fix)

**Defect closed:** H3 (level-end trigger grounded-blind) per Plan 12.7-26 diagnostic.
**Fix locus:** `PlatformerVisitor.kt:1204-1218` (the `// --- 8. Level-end trigger` CIf) — condition extended from `player_real_x > _current_level_width - 32u` to `player_real_x > _current_level_width - 32u && _grounded != 0`.
**JVM invariant:** `LevelEndTriggerGroundedGuardEmissionTest` (Plan 12.7-27 RED → Plan 12.7-28 GREEN) locks the H3 guard shape via per-function brace-walk + scope-walk.
**PNGs (R-02 + R-03):**
- `anchor-2/01-grounded.png` — zero pixel gap (Plan 12.7-29 re-capture under Plan 12.7-19 pivot_adjust + Plan 12.7-28 H3 guard). User-approved "OK" at Plan 12.7-31.
- `anchor-2/03-landed.png` — zero pixel gap. User-approved "OK" at Plan 12.7-31.
- `anchor-5/00-last-gameplay.png` — player visibly grounded at right-edge level-end trigger fire. User approved Branch A at Plan 12.7-31.
- `anchor-5/00-last-gameplay.json` sidecar: `grounded=1, playerVy=0, frameNumber=1347` at trigger-fire frame (was `grounded=0, playerVy=416` in Plan 12.7-21 BLOCKED).
**R-04 regression sweep:** `regression-sweep-round-6.md` — 5 strict + pong PASS*; platformer-template intentionally changed (Plan 12.7-30). R-04 CLOSED.
**ROM smoke:** `rom-smoke-round-5.txt` — clean `:buildRom` GREEN (Plan 12.7-23); H3 fix verified post-rebuild via grep.
**Binding gate:** Plan 12.7-31 APPROVED 2026-05-27. R-02 + R-03 CLOSED.

See `## Round 6 — G2 Closed by Phase 12.7` below for the full evidence trail and retro-supersession record.

### G3 — Grass tilemap renders without white-pixel artifacts — PENDING (Phase 12.8)
Seed: `.planning/seeds/SEED-PHASE-12-GRASS-TILEMAP-WHITE-PIXELS.md`.
Routing: NEW sibling Phase 12.8 (`grass-tileset-white-pixels-diagnostic`).
Closes via: png2asset palette / index mismatch diagnostic on `ConvertZoneTilesetsTask`.

### G4 — Anchor-1 re-verification after G3 fix — PENDING (Phase 12.8, conditional on G3 close)
Bundled with G3 per CONTEXT D-01 routing.

### Phase 12 status: STAYS PARTIAL

The `status: partial` in this SUMMARY's frontmatter is NOT flipped by Phase 12.6.
Phase 12.8 (the final-gate closer) owns:

- The flip `status: partial → status: complete` in this file's frontmatter
- The `gsd-sdk query phase.complete 12` invocation

Per `feedback_decimal_phase_pitstop_resume_target.md`, Phase 12 remains the
resume target across the 12.6 → 12.7 → 12.8 sequence.

### Sibling-phase inserts (D-01)

Inserted by the previous turn (ahead of Plan 12.6-08 execution):

- Phase 12.7 (`player-levitating-physics-codegen`) — planned in 12.6-CONTEXT.md.
- Phase 12.8 (`grass-tileset-white-pixels-diagnostic`) — planned in 12.6-CONTEXT.md.
- Phase 12.9 (`palette-inversion-asset-pipeline`) — NEW from debug Cycle 2/3:
  card colors + character metasprite + title BG all show inverted palette.
- Phase 12.10 (`uat-test-harness-capture-timing`) — NEW from debug Cycle 2/3 +
  Plan 12.6-08 Task 2 sweep: Coffee-GB pre-enter VRAM capture + anchor4 6.60%
  threshold retune + pong toolchain non-determinism investigation.

---

## Round 6 — G2 Closed by Phase 12.7 (gap-closure terminal cluster)

**Date:** 2026-05-27
**Verdict:** APPROVED on Plan 12.7-31 human-verify
**Defect closed:** H3 (level-end trigger grounded-blind) per Plan 12.7-26 diagnostic
**Fix locus:** `PlatformerVisitor.kt:1204-1218` (the `// --- 8. Level-end trigger` CIf)

**G2 status:** CLOSED 2026-05-27 by Phase 12.7

**Evidence (R-02 + R-03):**
- `anchor-2/01-grounded.png` — zero pixel gap (Plan 12.7-29 re-capture under Plan 12.7-19 pivot_adjust + Plan 12.7-28 H3 guard)
- `anchor-2/03-landed.png` — zero pixel gap
- `anchor-5/00-last-gameplay.png` — player visibly grounded at right-edge level-end trigger fire (Plan 12.7-29 re-capture under Plan 12.7-28 H3 guard)
- `anchor-5/00-last-gameplay.json` sidecar: `grounded=1, playerVy=0, frameNumber=1347` at trigger-fire frame (was `grounded=0, playerVy=416` in Plan 12.7-21 BLOCKED)

**R-04 + R-06 supporting evidence:**
- `regression-sweep-round-6.md` — 5 strict + pong PASS*; platformer-template intentionally changed (Plan 12.7-30)
- `rom-smoke-round-5.txt` — clean `:buildRom` GREEN (Plan 12.7-23); H3 fix verified post-rebuild via grep "Level-end trigger" main.c

**R-05 JVM invariant:**
- `LevelEndTriggerGroundedGuardEmissionTest` GREEN (Plan 12.7-27 RED → Plan 12.7-28 GREEN); locks H3 guard shape via per-function brace-walk + scope-walk
- `PlatformerPhysicsSnapToTileTopEmissionTest` GREEN (Round 5 carry-over); locks pivot_adjust shape

**Round history (full closure trail):**
- Round 1 (Plans 12.7-01..09): planned-but-broken; Plan 12.7-04 shipped C-precedence bug
- Round 2 (Plan 12.7-08 retry): blocked on user UAT
- Round 3 (Plans 12.7-10..16 planned): partially shipped; Plan 12.7-11 intermediate-vars Path A
- Round 4 (Plan 12.7-15 binding gate): BLOCKED 2026-05-26 on H1 + H2 defects
- Round 5 (Plans 12.7-17..25 planned): DIAGNOSED H1+H2 + FIXED via Plans 12.7-19 (pivot_adjust) + 12.7-20 (capture-timing); Plan 12.7-21 SURFACED H3 (BLOCKED — see below)
- Round 6 (Plans 12.7-26..32): DIAGNOSED H3 + FIXED via Plan 12.7-28 (grounded-guard) + VERIFIED at Plan 12.7-31; APPROVED 2026-05-27

**Retro-supersession of Round-5 planned-but-unexecuted plans:**
- Plan 12.7-22 (Round-5 regression sweep): PLANNED, UNEXECUTED — superseded by Plan 12.7-30 (Round-6 sweep). Plan 12.7-22's PLAN.md stays committed as a planning-only historical artifact.
- Plan 12.7-24 (Round-5 binding gate): PLANNED, UNEXECUTED — superseded by Plan 12.7-31 (Round-6 binding gate). The Round-5 escalation clauses in Plan 12.7-24's resume-signal options were authored before Plan 12.7-21 surfaced H3; H3 was successfully addressed in-phase (Round 6) instead.
- Plan 12.7-25 (Round-5 ledger close): PLANNED, UNEXECUTED — superseded by Plan 12.7-32 (this plan). Plan 12.7-25's Round-5 append action is moot; this Round-6 section replaces it.

**Phase 12 close contract progress:**
- G1 closed by Phase 12.6 (2026-05-26)
- G2 closed by Phase 12.7 (this round)
- G3 + G4 — Phase 12.8 owns

Per Plan 12.7-CONTEXT.md D-08 and SPEC R-07: Phase 12's `status: complete` frontmatter
STAYS unflipped until Phase 12.8 ships. Resume target after 12.7 SHIPS is Phase 12
(waiting on 12.8), NOT Phase 13. Per `feedback_decimal_phase_pitstop_resume_target`.

### Watchpoints carried to Phase 12.8

The following items were observed during the Round-6 binding gate review (Plan 12.7-31) and are NOT regressions against Phase 12.7 requirements. Phase 12.8 owns all three:

1. **anchor-5/00-last-gameplay.png: 1-2 px sink concern** — Re-verify once Phase 12.8 resolves G3 grass tileset. User observation: "character is on a platform, maybe a pixel or two sunk, but difficult to tell if thats because of the broken environment rather than the positioning of the character". Visual ambiguity entangled with G3 background tile corruption ("0F" / "F" letter scatter).

2. **anchor-5/02-nextlevel-card.png: "0F"/"F" text corruption** — Phase 12.8 G4 / palette inversion territory. User observation: "Still the same error, repeated letter F, I can also see '0F' in the text, character is lower than before."

3. **anchor-5/03-level-2.png: character sunk under ground level** — Phase 12.8 G3 grass tilemap territory. User observation: "character is sunk under the level of the ground - it is on the bottom of the screen."

## Round 7 — G3+G4 PARTIAL-BLOCKED by Phase 12.8; ROUTED to Phase 12.9

**Date:** 2026-05-27
**Verdict:** PARTIAL-BLOCKED — Phase 12.8 ships as diagnostic-only; G3+G4 closure carries forward to existing Phase 12.9.
**Defect closed (W3 asset-pipeline boundary):** `-keep_palette_order` flag-pin at `ConvertZoneTilesetsTask.kt:288-298` made conditional on PNG IHDR color-type (indexed only) via new `isIndexedPng()` helper.
**Fix locus:** `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ConvertZoneTilesetsTask.kt` (W3 — Plan 12.8-03)
**G3 status:** BLOCKED — color inversion at runtime; routed to Phase 12.9 for set_bkg_palette multi-file wiring
**G4 status:** NOT-ATTEMPTED — anchor-1 re-shoot + G4 binding gate (Plans 12.8-08/09) SKIPPED per conditional-on-G3-APPROVED frontmatter

### Evidence (G3 — anchor-5)

- `.planning/phases/12.8-grass-tileset-white-pixels-diagnostic/evidence/uat-screenshots/anchor-5/00-last-gameplay.png` — D-09 1-2 px sink persists (CARRIED-AS-NEW-SEED)
- `.planning/phases/12.8-grass-tileset-white-pixels-diagnostic/evidence/uat-screenshots/anchor-5/01-nextlevel-flip.png` — RED, new orthogonal regression introduced by W3
- `.planning/phases/12.8-grass-tileset-white-pixels-diagnostic/evidence/uat-screenshots/anchor-5/02-nextlevel-card.png` — NEUTRAL, unchanged (orthogonal — static title-screen.png path is RGB, flag skipped per W3 conditional)
- `.planning/phases/12.8-grass-tileset-white-pixels-diagnostic/evidence/uat-screenshots/anchor-5/03-level-2.png` — BLOCKED, ALL colors inverted, char still sunk
- `.planning/phases/12.8-grass-tileset-white-pixels-diagnostic/evidence/uat-screenshots/anchor-5/anchor5-variables.txt` — sidecar with zone tileset address (0x22BD) + 16-entry palette dump + pre/post-fix index-0 delta (cream RGB8(224,248,207) → near-black RGB8(8,24,32))

### Evidence (G4 — anchor-1)

NOT CAPTURED. Plan 12.8-08 (anchor-1 re-shoot) SKIPPED per conditional-on-G3-APPROVED frontmatter; G3 BLOCKED → G4 evidence deferred to Phase 12.9.

### R-04 / R-05 / R-06 supporting evidence (W3..W5 codegen-tier truth set)

- **R-04 7-target regression sweep:** `.planning/phases/12.8-grass-tileset-white-pixels-diagnostic/evidence/regression-sweep.md` — 6/6 strict targets byte-identical (breakout, simple-physics, metasprites, metasprites-stress, banks, racer); pong PASS* (toolchain non-determinism per memory `project_pong_toolchain_nondeterminism`); platformer-template INTENTIONALLY-CHANGED (proves W3 conditional flag activated on indexed `world1-tileset.png`)
- **R-05 JVM emission invariant:** `gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/tasks/World1TilesetGrassEncodingTest.kt` — locks post-fix `_zone_world1Area1Zone_tileset_tiles[432]` byte pattern; tile-0 / tile-6 first-byte = 0x80 (near-black slot 0 from PLTE)
- **R-06 ROM-build smoke:** `.planning/phases/12.8-grass-tileset-white-pixels-diagnostic/evidence/rom-smoke.txt` — `:gbkt-examples:platformer-template:buildRom` exit 0, ROM 65,536 bytes
- **D-14 pre/post byte-diff:** `.planning/phases/12.8-grass-tileset-white-pixels-diagnostic/12.8-DIAGNOSTIC.md` §"Pre-fix vs post-fix byte-diff (D-14 ROOT-CAUSE-EVIDENCE)" — index-0 cream→near-black; palette grew [4]→[16]; tile bitplane rows re-permuted

### Cross-pollination guard (T-12.8-01 mitigation)

The W3 conditional logic via `isIndexedPng(File)` (Plan 12.8-03 re-scope) ensures the flag is suppressed for RGB tilesets (banks/checker.png, platformer-template/title-screen.png, next-level.png) — confirmed by the 6/6 byte-identical strict-target sweep above. The fix does NOT leak into other game targets.

### Why ROUTE-TO-12.9 (not new 12.11)

Per CONTEXT D-16 + memory `feedback_many_small_plans_terminal_subphase`: subphases must CLOSE their defect cluster — no sub-sub-phase (12.8.1 forbidden). The plan's "12.11 or 12.12" language was placeholder for "NEW sibling outside 12.8".

**Phase 12.9 (`palette-inversion-asset-pipeline`) ALREADY EXISTS** as a sibling, created during Phase 12.6 debug cycle 2/3 for exactly this defect class: "card colors + character metasprite + title BG all show inverted palette family bug" (per STATE.md Resume signal). The W3 grass-tileset color inversion is the same root-cause family — `set_bkg_palette(0u, 1u, _zone_<id>_tileset_palettes)` wiring at zone-load codegen ordering is the multi-file fix.

Routing G3+G4 to existing 12.9 instead of inventing new 12.11 satisfies the spirit of the plan (NEW sibling outside 12.8) without phase-number bloat. Phase 12.9 scope expansion (carried into 12.9-CONTEXT.md when 12.9 begins):
- Per-zone palette wiring: `set_bkg_palette` invocation per zone-load
- Re-shoot anchor-5 PNGs post-fix; bind G3 verdict in 12.9
- Re-shoot anchor-1 PNGs; bind G4 verdict
- Close 01-nextlevel-flip orthogonal regression (W3-introduced)
- Investigate 00-last-gameplay 1-2 px sink as a sub-watchpoint (was CARRIED-AS-NEW-SEED in 12.8 G3 verdict — see `SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK.md`)

### Round history (full closure trail)

- **Round 1 (Plan 12-27 round-1, archived):** Phase 12 ship-clearance DENIED by user; visual-evidence rule blocked. 5 anchor verdict matrix recorded.
- **Round 2 (Plan 12-27 round-2, archived):** BLOCKED resolution recorded; Phase 12.6 inserted as gap-closure phase.
- **Round 3 (Plan 12-27 round-3):** Phase 12 close approval gating contract authored — G1+G2+G3+G4 4-gate visual close required.
- **Round 4 (Phase 12.6 closure):** G1 CLOSED. Sibling phases 12.7..12.10 inserted as routing destinations.
- **Round 5 (Phase 12.7 round 1-5):** Iterative gap-closure on player physics; superseded by Round 6.
- **Round 6 (Phase 12.7 round-6 terminal cluster, Plans 12.7-26..32):** G2 CLOSED. H3 defect resolved. Plan 12.7-31 binding gate APPROVED 2026-05-27.
- **Round 7 (this round — Phase 12.8 terminal):** W3 conditional flag-pin landed at codegen layer; W5 7-target sweep GREEN; G3 binding gate BLOCKED on color inversion (A6-CONFIRMED palette-wiring gap). G3+G4 routed to Phase 12.9. Phase 12.8 is HARD TERMINAL per D-16.

**Phase 12 close contract progress (post-Round 7):**
- G1 CLOSED by Phase 12.6 (2026-05-26)
- G2 CLOSED by Phase 12.7 (2026-05-27)
- G3 ROUTED to Phase 12.9 (palette-wiring fix required)
- G4 ROUTED to Phase 12.9 (anchor-1 re-shoot + G4 binding gate run there)

Per CONTEXT D-08 / D-15 / D-16: Phase 12's `status: complete` frontmatter STAYS unflipped until Phase 12.9 ships G3+G4. Resume target after 12.8 SHIPS is Phase 12.9, NOT Phase 13. Per `feedback_decimal_phase_pitstop_resume_target`.

### Phase 12.8 HARD TERMINAL declaration

Phase 12.8 is HARD TERMINAL per D-16 + `feedback_many_small_plans_terminal_subphase`:
- NO Plan 12.8.11 — Plan 12.8-10 is the final plan in this sub-phase
- NO Phase 12.8.1 — carry-forward work routes to sibling Phase 12.9 (existing) or new seed for Phase 13 (collision-mask)
- The 2 SKIPPED plans (12.8-08 anchor-1 re-shoot + 12.8-09 G4 binding gate) are NOT scheduled for re-execution in 12.8; their work is absorbed by Phase 12.9 scope

## Round 7 addendum — W3 RUNTIME CHANGE REVERTED (2026-05-27 post-close)

After Phase 12.8's terminal close commit landed, user reviewed the final state and pushed back: the W3 fix did not replace one bug with another — it **layered** color inversion on top of the previously-broken state. The original Round-7 narrative ("color inversion" as the new symptom) mischaracterized the user's actual observation. Corrected observations:

- 00-last-gameplay, 01-nextlevel-flip, AND 02-nextlevel-card are **visually the same screen** (gameplay frame, character at same x,y, cross-scene "0F" text artifact in bottom-right). The nextLevel scene transition is not actually flipping the BG layer; sprites persist across the transition.
- 03-level-2 shows the previously-broken state (grass white pixels + character sunk) WITH color inversion layered on top.

**Decision:** Revert the W3 runtime change. ROM returns to pre-Phase-12.8 visual baseline. Phase 12.9 starts from a clean known-state baseline AND inherits a wider scope (palette wiring + scene-transition VRAM-clear + cross-scene "0F" artifact investigation).

**Revert details:**
- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ConvertZoneTilesetsTask.kt:293-318` — reverted to unconditional 8-arg flag list (no `-keep_palette_order`)
- `gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/tasks/World1TilesetGrassEncodingTest.kt` — marked `@Disabled` (would FAIL without the W3 flag active)
- Retained: `isIndexedPng()` helper + companion-object PNG header constants + `IsIndexedPngTest` (5 tests) as infrastructure for Phase 12.9
- Verification: clean `:gbkt-examples:platformer-template:buildRom` produces `_zone_world1Area1Zone_tileset.c` SHA-256 byte-identical to `evidence/pre-fix-baseline/_zone_world1Area1Zone_tileset.c` (`8f9f021d…`)

**Phase 12.9 scope (UPDATED, broader than originally documented):**
1. (Original) Per-zone palette wiring via `set_bkg_palette(0u, 1u, _zone_<id>_tileset_palettes)` at zone-load codegen ordering
2. (Original) Re-activate `-keep_palette_order` via the retained `isIndexedPng()` guard as part of the palette-wiring bundle (not standalone)
3. **(NEW from corrected observation)** nextLevel scene-transition VRAM-clear defect: BG layer not cleared between gameplay and nextLevel; sprites + character x,y persist; card-draw renders on top of stale state. Hypothesis: `nextLevel_enter` missing `hideSprites()` + `fill_bkg_rect()`, OR the card-draw codegen ordering needs revision.
4. **(NEW from corrected observation)** Cross-scene "0F" text artifact in bottom-right quadrant on 00 + 01 + 02. Investigate whether pre-existing (Phase 12.6/12.7 era).
5. Re-shoot anchor-5 + anchor-1; bind G3 + G4 verdicts
6. (Carry-forward) 1-2 px sink → `SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK.md`

**Phase 12.9 may need reframing:** Original slug "palette-inversion-asset-pipeline" is now narrower than the actual scope. Plan 12.9-CONTEXT.md gathering should explicitly cover both palette wiring AND the cross-scene VRAM-clear bug. Renaming the phase (e.g., to "scene-transition-and-palette-wiring") is a planner-discretion call when 12.9 begins.

**Net value of Phase 12.8 (honest assessment):**
- Established that `-keep_palette_order` alone is insufficient (it actively makes things worse without palette wiring) — this saved Phase 12.9 from making the same mistake
- Surfaced the broader scene-transition VRAM-clear defect that wasn't in any prior phase's scope (otherwise would have been discovered mid-12.9 with same revert cost)
- Built infrastructure (`isIndexedPng()` + tests) that Phase 12.9 will consume
- Established 7-target byte-identical baseline (W5 evidence retained as historical record)
- ROM returned to pre-12.8 visual baseline; no net visual regression shipped

---

## Round 8 — G3 + G4 Closed by Phase 12.9 (2026-06-02)

Phase 12.9 (palette-inversion-asset-pipeline) closed both remaining Phase 12 visual binding gates,
G3 (anchor-5) and G4 (anchor-1). Phase 12 SHIPS.

**G3 closure took THREE rounds of the diagnose→fix→reshoot→rebind cluster:**

- **Round 1 (Plan 12.9-08):** initial G3 gate BLOCKED — palette inversion + dead-jump false alarm.
- **Round 2 (Plans 12.9-08a..08d):** fixed RC-1 palette inversion (per-zone `set_bkg_palette` in the
  `setup_current_level` template); RC-2 dead-jump was a wrong-button (B vs A/UP) false alarm. The
  Round-2 re-bind (08d) BLOCKED on FOUR residual defects: 0F-in-flip, player box-frame, walk-through
  blocks, level-2 sunk.
- **Round 3 (Plans 12.9-08e..08h):** diagnosed and fixed all four. **The 08e diagnose corrected two
  earlier (falsified) root-cause hypotheses** via codegen-read + MCP GBC runtime probe:
  - **D1 (0F-in-flip):** `SceneVisitor.kt` emitted the inline `DISPLAY_ON;` BEFORE the user clear in
    `nextLevelScene_enter`. Fix: gate the inline `DISPLAY_ON` (trailing-`DISPLAY_ON` heuristic) so the
    BG clear precedes LCD-on. Module: `gbkt-backend-gbdk`.
  - **D2 (player box-frame) — re-scoped:** NOT a missing `set_sprite_palette` default. Root cause =
    `ConvertSpritesTask` omitted `-keep_palette_order`, so png2asset re-sorted the indexed player PNG
    palette and the orange transparency key landed at index 2 (opaque) instead of the source's index
    0 (hardware-transparent on GBC). Fix = `-keep_palette_order` for indexed sprite PNGs
    (`gbkt-gradle-plugin`) + GBC-gated `set_sprite_palette(<metasprite>_palettes)` upload for correct
    character colors (`gbkt-backend-gbdk`).
  - **D3 (walk-through blocks):** `PlatformerVisitor.buildHorizontalProbe` used halfW=4 vs reference
    HALF_WIDTH=5. Fix: `CLiteral(halfW)` → `CLiteral(halfW + 1)` (`player_real_x ± 5u`). Module:
    `gbkt-genre-platformer`.
  - **D4 (level-2 sunk) — re-scoped:** NOT a spawn_y error (world2 ground geometry is identical to
    world1, row 16 in every column). Root cause = `setup_current_level` reset `_playerVy` per level but
    never reset the grounded flag, so the player carried `grounded=1` across the level switch →
    gravity suppressed → the ground-snap never fired → frozen at raw spawn y=120. Fix: reset the
    grounded symbol to 0 on every level switch (resolved from `tilemap_collision` config `groundedVar`).
    Module: `gbkt-backend-gbdk`. Level-1 worked only because grounded inits to 0 at boot.

  All four fixes shipped in Plan 12.9-08f with RED→GREEN JVM emission tests
  (`LevelCardSceneEmissionTest`, `ConvertSpritesKeepPaletteOrderTest`,
  `MetaspriteSpritePaletteEmissionTest`, `TilemapCollisionEmissionTest`,
  `SetupCurrentLevelGroundedResetEmissionTest`) + clean `:gbkt-examples:platformer-template:buildRom`
  (ROM SHA-256 `434bf90630421e5d7640d86c1993ad052bdcf23fa7b278952eeda430519f020f`). **3 modules**
  (`gbkt-backend-gbdk`, `gbkt-gradle-plugin`, `gbkt-genre-platformer`). User overrode the D-16
  ≥3-module route-to-sibling trigger (2026-06-02) to fix all four in-12.9.

**Routing note:** when 08e's diagnose surfaced that the corrected D2 fix landed in a new module
(`gbkt-gradle-plugin` sprite-transparency) — breaching D-16 — execution paused and escalated the
routing decision to the user rather than shipping the known-insufficient pre-authored D2 fix
(per `feedback_dont_pay_to_confirm_obvious` + `feedback_route_to_proper_phase_when_blast_radius_is_wide`).

**Binding-gate verdicts (user, 2026-06-02):**

- **G3 (R-05): APPROVED** — Plan 12.9-08g anchor-5 re-shoot + 12.9-08h re-bind. All four defects
  visually resolved: no box (real colors), no 0F in the cleared flip frame, level-2 player on the
  ground (y=102, = level 1), blocks stop the player (codegen `+5u`). Evidence:
  `12.9/evidence/uat-screenshots/anchor-5/{00-last-gameplay,01-nextlevel-flip,02-nextlevel-card,03-level-2}.png`.
- **G4 (R-06): APPROVED** — Plan 12.9-09 anchor-1 re-shoot (conditional on G3). Title +
  initial-gameplay render authored palettes, no inversion, no white-pixel grass, no regression.
  Evidence: `12.9/evidence/uat-screenshots/anchor-1/{00-title,01-initial-gameplay}.png`.

**Regression sweep (R-07):** Plan 12.9-10 manifest `12.9/evidence/8-target-regression-sweep.md` —
strict byte-identical PASS for the unaffected examples, pong PASS\* (toolchain non-determinism),
platformer-template INTENTIONALLY-CHANGED. racer EXCLUDED (LEGACY-path, D-15).

**Phase 12 ledger close:**

- G1 CLOSED by Phase 12.6 (main-loop level-switch codegen fix)
- G2 CLOSED by Phase 12.7 (player-levitating physics codegen fix)
- G3 CLOSED by Phase 12.9 (Round 3 — 08e..08h)
- G4 CLOSED by Phase 12.9
- All 4 visual binding gates APPROVED by user
- **Phase 12 SHIPS 2026-06-02**

**HARD TERMINAL** — NO Phase 12.9.1 (D-16 + `feedback_many_small_plans_terminal_subphase`). Residual
defects route to a NEW sibling Phase 12.{N} after checking ROADMAP for an existing match
(`feedback_sibling_phase_already_exists_routing`). Phase 12.10 (UAT capture-timing) remains a queued
sibling, NOT part of Phase 12's close gate.

`phase.complete 12` invocation evidence: `12.9/evidence/phase-complete-12-invocation.txt` (28/28 plans,
roadmap/state/requirements updated). NOTE: the SDK reported `next_phase: 12.1`, which is the
decimal-pitstop top-down-walk gotcha (`feedback_decimal_phase_pitstop_resume_target`) — Phase 12.1
already SHIPPED 2026-05-22. Real next target: Phase 12.10 (sibling, unplanned) or Phase 13.
