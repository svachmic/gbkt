# Phase 15 — Per-Failure Diagnosis Ledger (Req 7 / D-06)

**Status:** scaffold (created by 15-01). Fix plans 02–05 fill their rows via per-class
fragments under `evidence/diagnosis/<class>.md`; plan 06 consolidates those fragments
into the table below and finalizes the ledger.

## D-06 ledger contract (binding)

Every row's **Fix Path** MUST be exactly one of:
- `real-bug-fix` — a genuine product/codegen/test-infra defect was corrected.
- `provably-stale-assertion` — the test's expectation drifted from deliberately-changed,
  demonstrably-correct behavior; the expectation is realigned to the proven current truth.
- `retired-capability-removal` — the test covered a capability Phase 14 retired; the test
  is removed. **Any `retired-capability-removal` row MUST cite the Phase-14-retired
  capability it covered (D-04).**

**Evidence ref** tier (per D-03 / D-03b):
- **Visual-truth verdicts (D-03)** — `BanksUatTest`, `PlatformerTemplate128UatTest`,
  `PlatformerTemplateUatTest`: the Evidence ref MUST be a **LIVE MCP screenshot path under
  `evidence/`** (CLAUDE.md Visual Evidence Rule — "X is visible on screen" requires a
  runtime screenshot, not a variable/state assertion).
- **Static verdicts (D-03b)** — `IntegrationTest`, `PongStepAgentTest` (OAM-count),
  `PlayerMetaspriteGeometryTest` (geometry): the Evidence ref is a grep / stack-trace /
  generated-C reference. OAM *count* is an internal metadata-vs-runtime truth, NOT a
  "visible on screen" verdict (Pitfall 3).

**No-weakening rule (hard):** Zero rows may weaken a threshold or assertion (lower the 0.95
dominant-colour gate, the >10% facing-diff threshold, or an OAM-count expectation) to mask
a genuine failure. Green is reached only by a real-bug-fix or a *provably*-stale-assertion
correction. (`feedback_quality_over_shortcuts`; SPEC Constraints.)

## Research-flagged PRIORS (not foreclosing diagnose-first)

These are the research F1–F7 expected verdicts. Each fix plan MUST still diagnose-first and
record its own evidence; a prior is overturned if the evidence contradicts it.
- **F1 / IntegrationTest** → expected `real-bug-fix` (test-infra build-hermeticity: defeat the
  changing-module SNAPSHOT cache in the TestKit sandbox; D-05). Static evidence.
- **F2 / PongStepAgentTest** → expected `provably-stale-assertion` (8x16 paddle = one 16px OAM
  slot → metadata `oamCount=1`; test's `{2,2,1}`/total=5 drifted to `{1,1,1}`/total=3). Static.
- **F3 / F4 / PlayerMetaspriteGeometryTest** → expected `provably-stale-assertion` (player
  metasprite moved to png2asset-native `sprites/player.c` `player_metasprite0[]`; repoint the
  source/symbol/parser — larger than the D-04 one-token rename, see Pitfall 1). Static.
- **F5 / F6 / BanksUatTest** → UNDECIDED pending live screenshot (D-03). Near-blank-by-design
  vs real bank-load/render bug.
- **F7 / PlatformerTemplate128UatTest** (+ green sibling PlatformerTemplateUatTest) → UNDECIDED
  pending live screenshot (D-03); arithmetic smell — a 24×32 sprite flip can change at most
  ~3.3% of a 160×144 frame, so a >10% global gate may be unreachable (Pitfall 4: re-architect
  the measure if the flip is visually real; do NOT nudge the percent).

## Diagnosis Ledger

One row per genuinely-red test from `FRESH-RUN-INVENTORY.md` (18 rows). Diagnosis / Fix Path /
Evidence ref are filled by the owning fix plan (via its `evidence/diagnosis/<class>.md` fragment),
then consolidated here by plan 06.

| Class/Test | Root-cause diagnosis | Fix Path | Evidence ref |
|------------|----------------------|----------|--------------|
| `IntegrationTest` · end-to-end minimal game generates C code successfully | Stale `gbkt-analysis` (not in the republish set) linked against fresh 14-field `gbkt-ir` → `NoSuchMethodError: SceneIR.copy$default` in the TestKit sandbox | `real-bug-fix` | static (D-03b) — `evidence/diagnosis/integrationtest.md`; stack trace + `~/.m2` timestamp table |
| `IntegrationTest` · end-to-end game with sprites generates C code with tile data | same (`gbkt-analysis` republish-set omission + changing-module cache) | `real-bug-fix` | static — `evidence/diagnosis/integrationtest.md` |
| `IntegrationTest` · complex game configuration generates valid C code | same | `real-bug-fix` | static — `evidence/diagnosis/integrationtest.md` |
| `IntegrationTest` · generated C code is valid C syntax structure | same | `real-bug-fix` | static — `evidence/diagnosis/integrationtest.md` |
| `IntegrationTest` · asset pipeline processes valid sprites correctly | same | `real-bug-fix` | static — `evidence/diagnosis/integrationtest.md` |
| `IntegrationTest` · asset pipeline handles missing asset directory gracefully | same | `real-bug-fix` | static — `evidence/diagnosis/integrationtest.md` |
| `IntegrationTest` · asset pipeline handles missing sprite file gracefully | same | `real-bug-fix` | static — `evidence/diagnosis/integrationtest.md` |
| `IntegrationTest` · cleanGbkt task removes generated files | same | `real-bug-fix` | static — `evidence/diagnosis/integrationtest.md` |
| `IntegrationTest` · generateC deletes stale files dropped from the emission set | same | `real-bug-fix` | static — `evidence/diagnosis/integrationtest.md` |
| `IntegrationTest` · simple-physics fixture builds ROM end-to-end without staleness errors | same | `real-bug-fix` | static — `evidence/diagnosis/integrationtest.md` |
| `IntegrationTest` · task outputs are cached correctly | same | `real-bug-fix` | static — `evidence/diagnosis/integrationtest.md` |
| `IntegrationTest` · tasks are isolated and can run independently | same | `real-bug-fix` | static — `evidence/diagnosis/integrationtest.md` |
| `BanksUatTest` · anchor 1 cross-bank scene navigation | banked checker renders as a 16×16 swatch by design (tileset-only `playZone` → 2×2 tilemap); full-frame `<95% dominant` gate is unsatisfiable for a ≤1.1%-of-frame swatch (wrong premise) | `provably-stale-assertion` | live D-03 — `evidence/banks-anchor1-play-scene.png` (full 0.9944 / swatch 0.50) |
| `BanksUatTest` · anchor 2 banked zone tilemap visible | same (banked checker swatch renders correctly; SWITCH_ROM bank-2 load works) | `provably-stale-assertion` | live D-03 — `evidence/banks-anchor2-tilemap.png` (swatch dominant 0.50) |
| `PongStepAgentTest` · metadata and symbol table agree on variable names | 4×16 paddle = 1 hardware OAM (16px-slot rule, GBDKPipeline.kt:227-229); metadata + runtime read = 1; test `expected=2` is pre-16px-slot stale | `provably-stale-assertion` | static (D-03b) — `evidence/diagnosis/pong.md`; pipeline comment + metadata.json + runtime `actual=1` |
| `PlatformerTemplate128UatTest` · anchor4MetaspriteAnimation | hflip is visually real (settled-camera sprite-region diff 45%, OAM xFlip=true, facingRot=3) but the `>10%` GLOBAL-frame gate is arithmetically unreachable for a 3.3%-of-frame sprite (Pitfall 4) | `provably-stale-assertion` | live D-03 — `evidence/platformer-facing-{right,left}.png` (full 2.20% / sprite-region 45.36%) |
| `PlayerMetaspriteGeometryTest` · player_metasprite_array_exists | player metasprite moved to png2asset `sprites/player.c` `player_metasprite0[]` (METASPR_ITEM); left `main.c` entirely; test grepped `sprite_player_frame_0[]` in main.c | `provably-stale-assertion` | static (D-03b) — `evidence/diagnosis/platformer.md`; `sprites/player.c:143` grep |
| `PlayerMetaspriteGeometryTest` · player_frame_0 has 3 x-columns and 2 y-rows (3col x 2row 24x32 SPR8x16 layout) | same (geometry byte-identical/correct: cumulative x `{-12,-4,4}`, y `{-6,10}`) | `provably-stale-assertion` | static (D-03b) — `evidence/diagnosis/platformer.md` |

### Ledger finalization (plan 06)

- **All 18 rows resolved.** Fix-path tally: `real-bug-fix` ×12 (IntegrationTest hermeticity, one
  root cause), `provably-stale-assertion` ×6 (banks ×2, pong ×1, platformer geometry ×2, platformer
  facing ×1). **`retired-capability-removal` ×0** — no test was removed (D-04: F2/F3/F4 were
  corrected-not-deleted; the geometry capability is live, repointed to the png2asset asset).
- **Zero threshold-weakening rows.** banks `0.95` dominant-colour ratio UNCHANGED (re-scoped to the
  painted swatch region); platformer `>10%` facing gate REPLACED with a sprite-region measure (not
  lowered); pong OAM corrected to the proven runtime value (not deleted).
- **D-04 deviation noted** (PlayerMetaspriteGeometryTest): the one-token grep rename was under-scoped
  (the array left main.c); the 4-part repoint to png2asset `player_metasprite0` was applied instead.
- **D-02 (plan 06 Task 1):** zero production (`src/main`) source changed in phase 15 (all fixes are
  test-side / build-wiring), so all 7 KEEP examples are byte-identical to the pre-phase baseline; no
  re-pin. See FINAL-GREEN.md.

> Note: `PlatformerTemplateUatTest` is NOT a row here — it is GREEN on the main checkout in
> the fresh run (its only failing XML was a stale agent worktree). See FRESH-RUN-INVENTORY.md
> drift flags.
