# DSL/Lang/Tooling Cluster — Proposed TRIAGE Rows
# Draft for Plan 16-06 (Cluster D triage)
# Substrate SHA: 8cef3dbca7d0868f42cf0d627921b8559d7754e8
# Draft date: 2026-06-12
#
# These are PROPOSED rows for TRIAGE.md — do NOT merge until TRIAGE.md is finalized.
# This file covers 8 entries from the DSL/tooling cluster assigned to Plan 16-06.

## Proposed TRIAGE Rows (8 entries)

| ID | Title | Type | Disposition | Evidence | Fix-phase routing | Notes |
|----|-------|------|-------------|----------|------------------|-------|
| SEED-002 | ActorRef.moveTo(Expr,Expr) overload | source-only | VERIFIED-ALREADY-FIXED | evidence/SEED-002/source-inspection.txt | N/A | moveTo(Expr,Expr) at ActorBuilder.kt:335-346; seed concern resolved |
| SEED-003 | simple-physics wrap-around polish | emission | RE-DEFERRED | evidence/SEED-003/evidence.txt | v0.2.0 examples-polish | Reference-faithful behavior; trigger is "playable demos" milestone, not v0.1.1; PLAYBOOK.md misclaim packaged with seed |
| SEED-012 | MCP emulator_read_memory tool | source-only | VERIFIED-ALREADY-FIXED | evidence/SEED-012/source-inspection.txt | N/A | Both emulator_read_memory and emulator_write_memory registered in ToolHandlers.kt |
| SEED-020 | GameIRSerializer 10 stubbed collections | source-only | CONFIRMED-OPEN | evidence/SEED-020/evidence.txt | Phase 21 FIX-06 | 10 emptyList() stubs in deserializeGameIR with SEED-020 markers; substrate tests GREEN but don't exercise stubs |
| SEED-023 | whenever/runIf unification | source-only | CONFIRMED-OPEN | evidence/SEED-023/source-inspection.txt | Phase 18 DEPR-01 | whenever() not @Deprecated; KDoc says "Not deprecated this phase"; SEED-023 ref in KDoc |
| SEED-025 | Remove deprecated combatIsInState(String) overload | source-only | CONFIRMED-OPEN | evidence/SEED-025/source-inspection.txt | Phase 18 DEPR-02 | String overload still present @Deprecated(ReplaceWith); removal needed in v0.2.0; SonarCloud S1133 open |
| SEED-026 | Gradle plugin validatePlugins + pluginTest race | jvm-test | VERIFIED-ALREADY-FIXED | evidence/SEED-026/evidence.txt | N/A | validatePlugins=PASS, pluginTest=174/0 at substrate SHA; race not triggered |
| TODO-triggersystem-validation | triggerSystem(SystemRef) registry validation | source-only | CONFIRMED-OPEN | evidence/TODO-triggersystem-validation/source-inspection.txt | Phase 21 FIX-06 | triggerSystem() emits TriggerSystem(ref.systemId) with no registry check; RED repro: triggerSystem(SystemRef("nonexistent")) should throw at build() |

## Summary

| Disposition | Count | Seeds |
|-------------|-------|-------|
| VERIFIED-ALREADY-FIXED | 3 | SEED-002, SEED-012, SEED-026 |
| RE-DEFERRED | 1 | SEED-003 (v0.2.0 examples-polish) |
| CONFIRMED-OPEN → Phase 18 | 2 | SEED-023 (DEPR-01), SEED-025 (DEPR-02) |
| CONFIRMED-OPEN → Phase 21 | 2 | SEED-020 (FIX-06), TODO-triggersystem-validation (FIX-06) |

## Phase 21 FIX-06 Repros (D-07)

### SEED-020 — GameIRSerializer 10 stubbed collections

RED test shape (to be added in Phase 21):
  @Test fun `round-trip preserves zones`() {
      val game = gbktGame("test") {
          zone("z1") { ... }
          startScene = ref("main")
      }
      val json = GameIRSerializer.toJson(game)
      val restored = GameIRSerializer.fromJson(json)
      assertThat(restored.zones).hasSize(1)  // FAILS at HEAD (returns emptyList())
  }

### TODO-triggersystem-validation — SystemRef registry validation

RED test shape (to be added in Phase 21):
  @Test fun `triggerSystem with nonexistent system ref should fail at build`() {
      assertFailsWith<IllegalArgumentException> {
          gbktGame("test") {
              scene("main") {
                  every.frame {
                      triggerSystem(SystemRef("nonexistent_system"))
                  }
              }
              startScene = ref("main")
          }
      }
      // FAILS at HEAD: no exception thrown; TriggerSystem("nonexistent_system") emitted silently
  }
