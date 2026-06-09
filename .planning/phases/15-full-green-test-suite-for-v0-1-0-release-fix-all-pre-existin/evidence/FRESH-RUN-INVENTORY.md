# FRESH-RUN Inventory — Phase 15 authoritative red-test work-list

**Run date:** 2026-06-09
**Base commit:** `771850c9` (branch `feat/d_and_d_gaps`)
**Commands run (serially, from a settled tree):**
1. `./gradlew test --continue` → BUILD FAILED, exit 1 (all library/genre/example modules; gradle-plugin is NOT wired into root `test` — it runs only via `pluginTest`)
2. `./gradlew --stop` then `./gradlew pluginTest` → BUILD FAILED in 29s, exit 1 (republishes the 7 modules to mavenLocal, then runs the TestKit `IntegrationTest`)

This inventory — not the stale 2026-06-06 19-test snapshot — is the binding scope for the phase (Req 2).

## Genuinely-failing tests (authoritative work-list)

Auto-skipped emulator-tier tests with missing prerequisites are NOT failures and are excluded. Every row below is a test that actually executed and failed.

| Class | Test | Module | Symptom |
|-------|------|--------|---------|
| `IntegrationTest` | `end-to-end minimal game generates C code successfully` | `gbkt-gradle-plugin` (via `pluginTest`) | `GradleRunner` UnexpectedBuildFailure → `NoSuchMethodError: SceneIR.copy$default(...)` in sandbox sub-build |
| `IntegrationTest` | `end-to-end game with sprites generates C code with tile data` | `gbkt-gradle-plugin` | same `SceneIR.copy$default` linkage skew |
| `IntegrationTest` | `complex game configuration generates valid C code` | `gbkt-gradle-plugin` | same |
| `IntegrationTest` | `generated C code is valid C syntax structure` | `gbkt-gradle-plugin` | same |
| `IntegrationTest` | `asset pipeline processes valid sprites correctly` | `gbkt-gradle-plugin` | same |
| `IntegrationTest` | `asset pipeline handles missing asset directory gracefully` | `gbkt-gradle-plugin` | same |
| `IntegrationTest` | `asset pipeline handles missing sprite file gracefully` | `gbkt-gradle-plugin` | same |
| `IntegrationTest` | `cleanGbkt task removes generated files` | `gbkt-gradle-plugin` | same |
| `IntegrationTest` | `generateC deletes stale files dropped from the emission set` | `gbkt-gradle-plugin` | same |
| `IntegrationTest` | `simple-physics fixture builds ROM end-to-end without staleness errors` | `gbkt-gradle-plugin` | same |
| `IntegrationTest` | `task outputs are cached correctly` | `gbkt-gradle-plugin` | same |
| `IntegrationTest` | `tasks are isolated and can run independently` | `gbkt-gradle-plugin` | same |
| `BanksUatTest` | `anchor 1 cross-bank scene navigation` | `gbkt-examples/banks` | `dominant colour must cover < 95% of pixels` — near-blank play scene trips the non-uniformity gate |
| `BanksUatTest` | `anchor 2 banked zone tilemap visible` | `gbkt-examples/banks` | `anchor2-tilemap: dominant colour must cover < 95% of pixels` |
| `PongStepAgentTest` | `metadata and symbol table agree on variable names` | `gbkt-examples/pong` | `verifyMetadataSymbolAgreement: actor 'paddle1' OAM count mismatch — expected=2, actual=1` |
| `PlatformerTemplate128UatTest` | `anchor4MetaspriteAnimation` | `gbkt-examples/platformer-template` | `Phase 12.5 D-08 acceptance: facing-right vs facing-left pixel diff is 6.80% (must be > 10%)` |
| `PlayerMetaspriteGeometryTest` | `player_metasprite_array_exists` | `gbkt-examples/platformer-template` | `sprite_player_frame_0[] not found in main.c` — player metasprite moved to png2asset `sprites/player.c` |
| `PlayerMetaspriteGeometryTest` | `player_frame_0 has 3 x-columns and 2 y-rows (3col x 2row 24x32 SPR8x16 layout)` | `gbkt-examples/platformer-template` | `sprite_player_frame_0[] not found in main.c — cannot assert geometry` |

**Total genuinely-red: 18 tests across 5 classes** (12 + 2 + 1 + 1 + 2).

## Reconciliation against SPEC's 6 known classes and research F1–F7

| SPEC known class | Research F# | Snapshot count | Fresh-run count | Status |
|------------------|------------|----------------|-----------------|--------|
| `IntegrationTest` | F1 (copy$default × ~12, hermeticity) | 12 | **12** | Confirmed red (count matches; A1 "~12 of 19" observed = exactly 12 this run) |
| `BanksUatTest` | F5/F6 (dominant-colour ≥95% near-blank) | 2 | **2** | Confirmed red |
| `PongStepAgentTest` | F2 (paddle OAM expected=2 actual=1) | 1 | **1** | Confirmed red |
| `PlayerMetaspriteGeometryTest` | F3/F4 (`sprite_player_frame_0[]` renamed/moved) | 2 | **2** | Confirmed red |
| `PlatformerTemplate128UatTest` | F7 (facing diff 6.80% < 10%) | 1 | **1** | Confirmed red |
| `PlatformerTemplateUatTest` | F7 sibling | 1 | **0** | **DRIFT — no longer red** (see below) |

### Drift flags (Req 2 — surface added/no-longer-failing tests)

- **`PlatformerTemplateUatTest` is GREEN in the main checkout.** Fresh JUnit XML
  (`gbkt-examples/platformer-template/build/test-results/test/TEST-…PlatformerTemplateUatTest.xml`,
  timestamp 2026-06-09T11:20:03Z) reports `tests="5" skipped="0" failures="0" errors="0"`.
  The only failing `PlatformerTemplateUatTest` XML found on disk lives under
  `.claude/worktrees/agent-ab26b52501849d960/…` — a **stale leftover agent worktree**
  that the research Runtime State Inventory explicitly says to ignore ("work only on the
  main checkout … do not let greps pick them up"). This is the ROADMAP-under-counted
  sibling from the 2026-06-06 snapshot; it is **no longer red on the main checkout** and
  is therefore NOT in the fix scope. Net effect: the 19-test snapshot is **18** today.
- **No red tests exist outside the SPEC's 6 known classes** — no new/added failures
  surfaced by the fresh run. All other library/genre/example module tests passed.

## Notes

- `gbkt-gradle-plugin:test` is intentionally excluded from the root `test` aggregate
  (CLAUDE.md: gradle-plugin tests run via `pluginTest`, which republishes the 7 dependency
  modules to mavenLocal first). The `IntegrationTest` verdict above is from the authoritative
  `pluginTest` run, not a stale `:gbkt-gradle-plugin:test` artifact.
- IntegrationTest case count this run = **12** (research A1 noted "~12 of 19 run-to-run"; observed exactly 12).
- This is inventory only — no fixes were attempted in plan 15-01.
