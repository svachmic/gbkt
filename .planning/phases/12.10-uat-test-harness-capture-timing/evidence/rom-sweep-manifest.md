---
phase: 12.10-uat-test-harness-capture-timing
plan: 04
wave: 3
matrix_size: 7
created: 2026-06-02
codegen_guard_base: 27dab3f5
verdict: NO-DRIFT (test-harness-only phase; all 7 targets byte-identical to base; pong PASS*)
---

# Phase 12.10 — Suite-GREEN Gate (R-04) + 7-Target Byte-Identical ROM Sweep (R-05)

> This phase changed ONLY JVM test-harness code (StepAgent, VisualDiff, the anchor4 test,
> SettleCaptureTest/VisualDiffTest). There is no INTENTIONALLY-CHANGED target (unlike 12.9).
> ALL 7 targets are expected byte-identical; pong is PASS* per documented sdcc/lcc
> non-determinism (`project_pong_toolchain_nondeterminism` — not re-investigated).

---

## Part 1 — Suite-GREEN Gate (R-04)

### Command

```bash
./gradlew :gbkt-emulator:test :gbkt-examples:platformer-template:test
# emulator suite force-reverified: ./gradlew :gbkt-emulator:test --rerun-tasks
```

The platformer-template ROM was built first (Part 2 sweep) so anchor4 RUNS (does not auto-skip).

### `:gbkt-emulator:test` — GREEN (0 failures, 0 errors across 27 classes)

| Class | tests | skipped | failures | errors |
|-------|-------|---------|----------|--------|
| AgentDebugSessionTest | 20 | 0 | 0 | 0 |
| AgentErrorBoundaryTest | 4 | 0 | 0 | 0 |
| AgentSessionConfigTest | 6 | 0 | 0 | 0 |
| GameMetadataTest | 30 | 0 | 0 | 0 |
| InputScriptPlayerTest | 8 | 0 | 0 | 0 |
| InputScriptTest | 8 | 0 | 0 | 0 |
| OamSpriteReaderTest | 7 | 0 | 0 | 0 |
| RealEmulatorAgentTest | 10 | 0 | 0 | 0 |
| SavestateManagerTest | 10 | 1 | 0 | 0 |
| SceneMapTest | 6 | 0 | 0 | 0 |
| ScreenshotCaptureTest | 9 | 0 | 0 | 0 |
| **SettleCaptureTest** | **3** | **0** | **0** | **0** |
| StepAgentTest | 35 | 0 | 0 | 0 |
| UatRunnerTest | 31 | 0 | 0 | 0 |
| VariableInspectorTest | 23 | 0 | 0 | 0 |
| **VisualDiffTest** | **14** | **0** | **0** | **0** |
| VramTextVerifierTest | 23 | 0 | 0 | 0 |
| CoffeeGbEmulatorTest | 32 | 0 | 0 | 0 |
| DebugLogWriterTest | 10 | 0 | 0 | 0 |
| EmuPrintfInterceptorTest | 17 | 0 | 0 | 0 |
| SourceMapResolverTest | 15 | 0 | 0 | 0 |
| EmulatorSessionTest | 12 | 0 | 0 | 0 |
| EmulatorIntegrationTest | 7 | 1 | 0 | 0 |
| EmulatorToolbarTest | 17 | 0 | 0 | 0 |
| InputHandlerTest | 16 | 0 | 0 | 0 |
| LogCatPanelTest | 20 | 0 | 0 | 0 |
| MemoryInspectorPanelTest | 23 | 0 | 0 | 0 |

- **BUILD SUCCESSFUL** (`--rerun-tasks`, all 17 actionable tasks executed — not cached).
- **SettleCaptureTest GREEN** (3/3) and **VisualDiffTest GREEN** (14/14) — the two phase-introduced test classes.
- The 2 skips (SavestateManagerTest +1, EmulatorIntegrationTest +1) are pre-existing environment-conditional skips, NOT this phase.

### `:gbkt-examples:platformer-template:test`

| Class | tests | skipped | failures | errors | In-scope? |
|-------|-------|---------|----------|--------|-----------|
| **PlatformerTemplateUatTest** | 5 | 1 | **0** | 0 | YES (in-scope) |
| PlayerMetaspriteGeometryTest | 2 | 0 | 0 | 0 | YES |
| PlatformerTemplate128UatTest | 5 | 0 | 2 | 0 | NO (out-of-scope clone) |

**In-scope `PlatformerTemplateUatTest` per-testcase status:**

| Testcase | Status |
|----------|--------|
| anchor1Title_to_Gameplay() | PASSED |
| anchor2TilemapCollision() | PASSED |
| anchor3HorizontalScroll() | PASSED |
| **anchor4MetaspriteAnimation()** | **PASSED (ran, NOT skipped)** |
| anchor5LevelSwitch() | SKIPPED (`@Disabled`, commit bfc63090, routed to Phase 12.11) |

- anchor4 **actually RAN and PASSED** (non-skipped) — R-04 requirement met. The single skip is the
  documented `@Disabled` anchor5LevelSwitch (pre-existing level-2 codegen/runtime defect, byte-identical
  ROM, proven NOT a capture-timing issue, USER-APPROVED routing to NEW sibling Phase 12.11).

**Documented exceptions (NOT this phase's scope — confirmed unchanged):**

1. **Inherited-14 IntegrationTest baseline** — pre-existing `SceneIR.<init>` signature mismatch
   (`project_integration_test_baseline_red`). Lives in a different module/suite; not exercised by the
   two suites above; not in scope; not fixed.
2. **`PlatformerTemplate128UatTest` (Phase-12.8 clone)** — 2 failures
   (`anchor4MetaspriteAnimation` pre-existing 6.60% full-frame soft-fail — the clone was never retuned by
   12.10-03; `anchor5LevelSwitch` the same level-2 defect routed to 12.11). Logged in `deferred-items.md`;
   out of scope per the 12.10-03 plan's edit boundary (only `PlatformerTemplateUatTest.kt` was retuned).
   These two clone failures are the documented baseline, confirmed unchanged.

**R-04 verdict: GREEN** — emulator suite fully green; in-scope platformer-template suite green with a
non-skipped passing anchor4; SettleCaptureTest + VisualDiffTest green; the only failures are the
out-of-scope 12.8 clone (documented) and the only skip is the documented @Disabled anchor5.

---

## Part 2 — 7-Target Byte-Identical ROM Sweep (R-05)

### Single chained build invocation

Per `feedback_no_parallel_gradle_clean` (one chained command; never two parallel `gradlew clean`;
recovery is `./gradlew --stop`):

```bash
./gradlew clean \
  :gbkt-examples:pong:buildRom \
  :gbkt-examples:breakout:buildRom \
  :gbkt-examples:simple-physics:buildRom \
  :gbkt-examples:metasprites:buildRom \
  :gbkt-examples:metasprites-stress:buildRom \
  :gbkt-examples:banks:buildRom \
  :gbkt-examples:platformer-template:buildRom
```

Build verdict: **BUILD SUCCESSFUL in 8s** (exit 0). 114 actionable tasks: 100 executed, 14 up-to-date.
GBDK toolchain: `GBDK_HOME=/Users/michalsvacha/gbdk` (`lcc` present).

### Reference-hash basis (corrected from a clean base-commit rebuild)

The Phase 12.9-10 manifest hashes are the correct reference for breakout, simple-physics, and banks.
For **metasprites, metasprites-stress, and platformer-template** the 12.9-10 hashes are STALE relative to
this phase's base commit `27dab3f5` (intervening pre-12.10 phase work changed them BEFORE 12.10 began,
outside this phase's blast radius). To produce an authoritative, in-scope no-drift reference, those three
targets were rebuilt from a clean detached worktree at the base commit `27dab3f5` and hashed:

```bash
git worktree add --detach /tmp/gbkt-base-27dab3f5 27dab3f5
(cd /tmp/gbkt-base-27dab3f5 && ./gradlew clean \
   :gbkt-examples:metasprites:buildRom \
   :gbkt-examples:metasprites-stress:buildRom \
   :gbkt-examples:platformer-template:buildRom)
# hash each, then: git worktree remove --force /tmp/gbkt-base-27dab3f5
```

Base-commit (`27dab3f5`) hashes — the authoritative no-drift reference for the three otherwise-stale targets:

| Target | Base 27dab3f5 SHA-256 |
|--------|------------------------|
| metasprites | `ddfb9c0b3738bbe4c5fc776332159cab5b0590ce740588c4a305cfae334882df` |
| metasprites-stress | `e13ff7f52f887550475a2764a5bc100d006c9921731f06edc14dd14727f1821e` |
| platformer-template | `c7c9afc328e0934b8320cc1d17713e539f1bb4fa4ebf28864bf7ef8cd2849730` |

### Per-Target SHA-256 + Verdict Matrix

| # | Target | Reference SHA-256 | HEAD (post-12.10) SHA-256 | Verdict | Reference source |
|---|--------|-------------------|----------------------------|---------|------------------|
| 1 | breakout | `21a42479ed36a2d3a2f62c5d4b2495168ca6a2178fc05010a155a90421796977` | `21a42479ed36a2d3a2f62c5d4b2495168ca6a2178fc05010a155a90421796977` | strict-byte-identical | 12.9-10 manifest |
| 2 | simple-physics | `247e16d2df29c9cad2df3f2f5fb68cba00133e95e94b4d47dbcf87c31384f9ad` | `247e16d2df29c9cad2df3f2f5fb68cba00133e95e94b4d47dbcf87c31384f9ad` | strict-byte-identical | 12.9-10 manifest |
| 3 | banks | `c598231420e4fdc1d06f7f386be318723096a22000feb0971584371d2b58fd8f` | `c598231420e4fdc1d06f7f386be318723096a22000feb0971584371d2b58fd8f` | strict-byte-identical | 12.9-10 manifest |
| 4 | metasprites | `ddfb9c0b3738bbe4c5fc776332159cab5b0590ce740588c4a305cfae334882df` | `ddfb9c0b3738bbe4c5fc776332159cab5b0590ce740588c4a305cfae334882df` | strict-byte-identical | base 27dab3f5 rebuild |
| 5 | metasprites-stress | `e13ff7f52f887550475a2764a5bc100d006c9921731f06edc14dd14727f1821e` | `e13ff7f52f887550475a2764a5bc100d006c9921731f06edc14dd14727f1821e` | strict-byte-identical | base 27dab3f5 rebuild |
| 6 | platformer-template | `c7c9afc328e0934b8320cc1d17713e539f1bb4fa4ebf28864bf7ef8cd2849730` | `c7c9afc328e0934b8320cc1d17713e539f1bb4fa4ebf28864bf7ef8cd2849730` | strict-byte-identical | base 27dab3f5 rebuild |
| 7 | pong | `4ae15ff85c607d353aa8d28aa26609f1bf9a07ae6765ae84be56b729b4e6ad6d` | `4ae15ff85c607d353aa8d28aa26609f1bf9a07ae6765ae84be56b729b4e6ad6d` | PASS* | 12.9-10 manifest (non-deterministic) |

**Strict targets (all non-pong): 6 / Byte-identical: 6 / Fail: 0**
**Pong: PASS\*** (`project_pong_toolchain_nondeterminism` — hash differs per rebuild; generated C unchanged; not re-investigated)

### Determinism cross-check (rules out toolchain non-determinism for the 3 corrected targets)

metasprites, metasprites-stress, and platformer-template were each built TWICE at HEAD (initial sweep +
a second clean rebuild) and produced byte-identical hashes both times, AND those hashes equal the
base-commit (27dab3f5) build. They are deterministic and byte-identical base→HEAD — confirming the
hash difference vs the 12.9-10 manifest is pre-12.10 history, not a 12.10 regression and not toolchain noise.

---

## Part 3 — Codegen-Guard (zero codegen/IR/DSL/genre edits across the phase)

```bash
git diff --name-only 27dab3f5..HEAD
```

Output:

```
.planning/ROADMAP.md
.planning/STATE.md
.planning/phases/12.10-uat-test-harness-capture-timing/12.10-01-SUMMARY.md
.planning/phases/12.10-uat-test-harness-capture-timing/12.10-02-SUMMARY.md
.planning/phases/12.10-uat-test-harness-capture-timing/12.10-03-SUMMARY.md
.planning/phases/12.10-uat-test-harness-capture-timing/deferred-items.md
.planning/phases/12.11-platformer-level-2-gameplay-zone-near-blank-render-in-uat-ha/.gitkeep
.planning/phases/12.11-platformer-level-2-gameplay-zone-near-blank-render-in-uat-ha/SEED.md
.planning/phases/12.7-player-levitating-physics-codegen/evidence/uat-screenshots/anchor-4/*  (evidence PNG/JSON/txt)
gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/StepAgent.kt
gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/VisualDiff.kt
gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/SettleCaptureTest.kt
gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/VisualDiffTest.kt
gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateUatTest.kt
```

**Codegen-guard verdict: CLEAN.** Across the whole phase, ZERO files changed under:
- `gbkt-backend-gbdk/` (codegen / C AST / pipeline / postprocess) — none
- `gbkt-ir/` (IR types) — none
- `gbkt-lang/` (DSL builders) — none
- `gbkt-engine/`, `gbkt-core/` (ROM-feeding library code) — none
- any `gbkt-genre-*` module — none
- any generated-C path or any ROM-feeding `gbkt-examples/` source — none

The only `gbkt-examples/` change is `PlatformerTemplateUatTest.kt` under `src/test/` — a JUnit test file
that is NOT compiled into any ROM. Therefore the ROM-feeding source is provably identical between base
`27dab3f5` and HEAD, independently corroborating the byte-identical sweep above.

---

## Conclusions

- **R-04 GREEN:** `:gbkt-emulator:test` fully green (SettleCaptureTest 3/3, VisualDiffTest 14/14);
  in-scope `PlatformerTemplateUatTest` green with a **non-skipped passing anchor4**. The only skip is the
  documented `@Disabled` anchor5 (12.11); the only failures are the out-of-scope 12.8 clone (deferred-items).
- **R-05 met:** 6 strict targets byte-identical to reference; pong PASS\*; all corrected via an
  authoritative base-commit (27dab3f5) rebuild. No INTENTIONALLY-CHANGED target — this is a test-only phase.
- **No codegen drift:** codegen-guard diff shows zero codegen/IR/DSL/genre/ROM-feeding edits; base→HEAD
  ROM source is identical; byte-identical sweep confirms it at the binary level.
- **Phase 12.10 is TERMINALLY CLOSED** — no 12.10.1 follow-up. The level-2 defect is the only remaining
  open item and is owned by NEW sibling Phase 12.11 (anchor5LevelSwitch @Disabled, USER-APPROVED).

## References

- Reference manifest (breakout / simple-physics / banks / pong): `.planning/phases/12.9-palette-inversion-asset-pipeline/evidence/8-target-regression-sweep.md`
- Base-commit reference (metasprites / metasprites-stress / platformer-template): clean rebuild at `27dab3f5` (detached worktree)
- Pong non-determinism: `project_pong_toolchain_nondeterminism.md`
- Inherited IntegrationTest baseline: `project_integration_test_baseline_red.md`
- anchor5 routing: commit bfc63090 + `.planning/phases/12.11-platformer-level-2-gameplay-zone-near-blank-render-in-uat-ha/SEED.md`
