# Milestones

## v0.1.1 Hardening (Shipped: 2026-06-15)

**Phases completed:** 7 phases, 79 plans, 110 tasks

**Key accomplishments:**

- 47-row TRIAGE.md disposition skeleton created with 6 D-12 fast-path RE-DEFERRED rows pre-filled (SEED-001/018/019/024/RAW-C/CPAREN citing REQUIREMENTS.md IDE-02/RPG-01/IDE-01/IDE-01/ARCH-01/ARCH-02) and three phase-close destination directories established.
- Single serial Gradle substrate pass produced all 7 example ROMs + pinned HEAD SHA 8cef3dbc; full JVM suite 3655/3656 GREEN with BanksEmissionTest INV-2, GameIRSerializerTest, and validatePlugins all PASS — SEED-014, SEED-015, SEED-020, and SEED-026 signalled VERIFIED-ALREADY-FIXED
- Banks cluster fully dispositioned: 5 seeds VERIFIED-ALREADY-FIXED via INV-2/INV-6 sentinel + generated-C inspection; 3 WR-followup items CONFIRMED-OPEN with Phase 19/20 routing; 8-row cluster-banks.md drafted
- 3 VERIFIED-ALREADY-FIXED, 2 CONFIRMED-OPEN → Phase 18, 2 CONFIRMED-OPEN → Phase 21, 1 RE-DEFERRED; 8-row cluster-dsl.md draft ready for TRIAGE.md merge
- 10 visual-seed verdicts locked by D-08 human gate: 8 VERIFIED-ALREADY-FIXED + 2 CONFIRMED-OPEN; SEED-004 overridden from proposed CONFIRMED-OPEN to VERIFIED-ALREADY-FIXED by user review
- One-liner
- `.planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/DOCS-AUDIT.md` (438 lines)
- Added `TargetProfiles.GAME_BOY_SCREEN` canonical `ScreenSpec` preset in `gbkt-core` and wired `GameBoyConstants.SCREEN_WIDTH/HEIGHT` to derive from it, establishing 160x144 as a single-source-of-truth constant (D-05).
- Replaced all 8 in-scope 160/144 magic-pixel literals in ActorVisitor/GBDKSystemVisitor/PlatformerVisitor with `GameBoyConstants.SCREEN_WIDTH/HEIGHT`; confirmed byte-identical ROM output across 7 examples; committed 47-entry exemption table (QUAL-03) and v0.2.0 backlog seed for TargetProfile.screen threading (D-06).
- Drove `./gradlew detekt` from 2064 violations to zero via source fixes (dead code removal, named constant extraction, named boolean extraction) and rationale-commented path exclusions in detekt.yml — no baselines (D-04 compliance).
- Closed D-03 composite detekt gap — applied detekt inside gbkt-gradle-plugin, bridged via `tasks.named("detekt")` root bridge, deleted both D-04 baseline lines, and cleared 147 composite violations via rationale-commented `**/gradle/**` path exclusions; `./gradlew detekt` now covers the whole repo with zero violations and no baseline files.
- Cross-checked `assign()` / `varRef()` / `literal()` migration arrows against `ScriptBuilder.kt`. DOCS-AUDIT T-09 verdict `accurate` confirmed — the migration arrows (`score set 0`, direct `score` use, raw Int auto-wrap) are all correct. No arrow changes needed. Recorded in SUMMARY as required.
- Auto-fix [Rule 2 - Missing correction]: Remove stale testGame() in Global Flags section
- Unified ConfigBuilder to function setters, added MBC5 fallback warning in CompileRomTask, and wired RpgRegistry.clear() to game{} teardown via a new GameBuilderContext hook mechanism.
- Grep-driven D-16 cross-doc pass confirmed zero stale API references across 50 CLAUDE.md files, CONTRIBUTING.md, and context/*.md; all 12 FEAT-*.md provenance headers backfilled with real removal-commit hashes (D-11 complete).
- Hard-removed both `whenever` DSL overloads and migrated all 80+ in-tree Kotlin call sites to `runIf` in a single atomic wave; build + pluginTest green with zero residual whenever( sites.
- Migrated all 83 `whenever` references in `context/DSL_REFERENCE.md` to `runIf`, plus fixed confusing before/after section prose.
- Two-tier API deprecation convention added to CONTRIBUTING.md (D-04) and root CHANGELOG.md created in Keep a Changelog format with the complete v0.1.1 breaking-change entry for whenever, combatIsInState(String), and ramBanks setter removal (D-09).
- Corrected `GAME_BOY_COLOR_SCREEN.bitsPerPixel` from `4` to `2`, fixed GBC KDoc prose to "2 bits per pixel, color via 8 hardware palettes (4 colours each)", and narrowed the top-level "All backends MUST derive" claim to `width`/`height` only.
- Three gbkt-mcp-server S3776 findings closed via extract-method: GameMetadata (cc=26→7 put-calls), Observation (cc=18→9 put-calls), handleStart (cc=19→4 lines)
- Extract-method refactor across gbkt-test, gbkt-core test infra, and gbkt-lang DSL builder closes three S3776 NON-EMITTING findings via focused private helpers returning values.
- Extract per-system global-var and home-file section sub-builders from GBDKPipeline.kt to resolve S3776 findings E-03 (cc=71) and E-04 (cc=44).
- Promoted local `walkOps` to top-level private fun (E-13+E-19) and extracted three `buildHeaderFile` sub-builders (E-15) in GBDKPipeline.kt with 6/6 byte-identical non-pong ROM sweeps.
- Captured before Task 1 from a clean 7-example build.
- Decomposed `buildPuzzleObjectFunctions` (cc=92) into 5 value-returning per-puzzle-type private helpers via `PuzzleObjectOutput` data class; 7-example byte-identity sweep green (6/6 identical, pong PASS*).
- Extract-method `visitSoundSystem` (E-10, cc=30)
- 1. [Rule 3 - Blocking] Cross-module smart cast compile errors
- `SceneVisitor.visit` (CC=39) refactored to delegate to four private helpers.
- Disposition: EXTRACT-METHOD
- 1. [Rule 1 - Bug in research artifact] E-21 misattributed to generateAnimationDefines
- RESEARCH.md described this as "class body/init" but the actual high-cc symbol is the `generateAllCollections` extension function at line 436. CC=20 confirmed (4 for-loops × (1 for + 2 ifs) = 20). Correct symbol to fix.
- Extract-method refactor closes the final two EMITTING S3776 findings (TrackSynthesizer.scanlineFill cc17 + RpgVisitor.generateApplyEffectFunction cc16), completing all 29 EMITTING findings across Phase 18.
- SonarCloud public API query
- Gap-closure plan that flattens nesting in the 4 methods SonarCloud PR #77 found still over cc=15 after Phase 18 EMITTING sweep. All 4 fixed via extract-method (no NOSONAR). Byte-identity preserved across all 7 examples. 4 atomic commits.
- IDE `whenever` keyword retired across all gbkt-intellij-plugin surfaces: DSL_FUNCTIONS, CONTROL_FLOW set, completion maps, documentation provider, color-settings sample, and all three project templates now advertise `runIf`.
- Four new Sonar MAJOR smells introduced by Phase 18 extract-method refactors fixed via output-preserving mechanical transforms with 7/7 example byte-identity confirmed.
- GBC-mode UAT class + five Phase-19-HEAD evidence PNGs (SEED-004/005/006/013 + ROM-smoke) captured against a clean-rebuilt metasprites ROM.
- Phase 19 closed with zero codegen drift (byte-identity oracle CLEAN), all 9 seeds archived with no orphans, full FIX-01+FIX-02 suite GREEN, and D-08 commit separation confirmed.
- BanksEmissionTest INV-2/INV-5/INV-6 and BanksUatTest Anchor 4 all GREEN at HEAD on chore/hardening_0_1_0; SEED-014/015/016 confirmed VERIFIED-ALREADY-FIXED; D-02 ordering gate satisfied; zero production code change
- Standalone 20-AUDIT-FIX-03.md authored mapping SEED-014/015/016 to existing BanksEmissionTest (INV-2/INV-5/INV-6) and BanksUatTest (Anchor 4) guards; zero new guards needed; D-02 GREEN result recorded; D-03 no-duplicate-coverage confirmed
- GBC-mode runtime screenshot oracles for FIX-04: metasprites elephant sprite-outline (4 colours, dominant 0.4978) and platformer player-transparency (7 colours, dominant 0.8599) — both assertScreenshotIsNonUniform() gates PASSED, evidence in fix-04/ for human sign-off
- Full 7-example generated-C byte-identity sweep PASS — 14/14 .c files stable; affected examples (banks/metasprites/platformer-template) byte-identical to per-commit baselines; D-06 two-tier proof complete; Success Criterion 5 satisfied; zero production code change
- Lifted pivot_adjust resolution from a metasprite-lookup dance in PlatformerVisitor into a typed `pivotAdjust(Int)` DSL setter on TilemapCollisionBuilder, with config-driven visitor read + fallback diagnostic, closing SEED-021 per Project Rule #1.
- Extracted `gameUsesTilemapCollisionPathC(GameIR)` into `gbkt-backend-api/TilemapCollisionGate.kt` and routed both callers through it, fixing the previously-missing Path C in `PlatformerVisitor` (latent bug: visitor under-detected tilemap-collision games); a 4-fixture lockstep contract test guards against future divergence.
- Closed SEED-020 by replacing all 10 emptyList() deserialization stubs with real supported-subset deserializers and a round-trip test guarding the contract.
- Both D-10/D-11 seeds confirmed already-fixed by Phase 18 via structural grep; annotated VERIFIED-ALREADY-FIXED and moved to seeds/archive/.
- `whenever(` → `runIf(` sweep across 18 docs/KDoc files + SEED-029 archived as FIXED (49 replacements, 6 intentional keeps).
- Re-deferred four D-03 seeds (SEED-017, SEED-023, SEED-025, SEED-ZONE-MAGIC-STRING) to backlog/v0.2.0 via git mv with rationale headers; updated REQUIREMENTS.md FIX-06 to reflect Phase 21 dispositions per D-04.
- Post-fix GBC anchor screenshots (grounded duck on platform, title→gameplay, initial→scrolled) captured against the final fixed ROM with a gbcMode harness fix, plus terminal archival of all four LOCKED-visual platformer seeds on binding user sign-off.
- D-13 two-tier byte-identity oracle CLEAN (5 examples + pong PASS*) + seeds/ empty (Criterion 5) + Phase 21 ROADMAP finalized.
- Exact-match golden diff helper with opt-in re-baseline via `GBKT_UPDATE_GOLDENS_PROP` const, and `capturedAt` timestamp churn eliminated from ScreenshotCapture sidecar.
- `AgentSessionConfig.discoverFiles` now reads ROM header byte `0x143` to set `gbcMode` automatically, eliminating the per-test `.copy(gbcMode = true)` workaround.
- Gitignore rule for evidence scratch dirs and `-Pgbkt.updateGoldens` → JVM systemProperty wiring in 4 example modules with goldens skeleton dirs for Wave 2 migration.
- 6 Phase 19/20 USER-blessed metasprites screenshots migrated byte-identically (sha256-proven) into goldens/metasprites/ with descriptive phase-agnostic names
- 16 user-blessed GBC platformer-template anchor PNGs migrated byte-identically (sha256-proven) from Phase 21/20 evidence into test resources goldens directory
- 3 metasprites visual-UAT test classes migrated from captureAndRename/EVIDENCE_DIR pattern to assertGoldenMatch golden diffs and SCRATCH_DIR smoke captures, with D-07 GBC-header guards added to all GBC-target helpers
- 1. [Rule 1 - Bug] CompileRomTask gbcMode convention always-present prevents metadata fallback
- Task 1 — SimplePhysicsUatTest (`a12ec8ff`)
- Replaced multi-line `.planning/phases/<phase>/evidence/tier1-shape` resolves with `"build/gbkt/test-evidence"` in all 6 pipeline emission test companions:
- Redirect all 9 gbkt-genre-platformer emission test EVIDENCE_DIR companions from .planning/phases/**/evidence paths to the module's gitignored build/gbkt/test-evidence scratch directory (R1 + R3).
- 143 tracked .planning/phases/
- Automated R1/R5/R6 grep-gate acceptance test added; full-suite green + clean-tree EMPTY confirmed; GBC buildRom smoke passed; USER visual sign-off APPROVED on migrated goldens

---

## v0.1.0 MVP — Compiler Pipeline Rebuild (Shipped: 2026-06-09)

**Phases completed:** 66 phases, 652 plans, 887 tasks
**Git timeline:** 2026-01-04 → 2026-06-09
**Release gate:** Full-green JVM test suite — `./gradlew test --continue` (0 failures) + `./gradlew pluginTest` (IntegrationTest 19/0/0/0), reached diagnose-first with zero threshold-weakening and zero production-codegen drift (Phase 15, VERIFICATION passed 7/7).

**Key accomplishments:**

- **Clean compiler pipeline (DSL → IR → analysis → codegen → C):** replaced the string-concatenating prototype with a layered architecture — a Kotlin DSL records games into a non-sealed IR + visitor hierarchy (`gbkt-ir`, zero deps), nine ordered analysis passes annotate it with hardware-resource assignments, and a structured C AST emits bank-split GBDK C.
- **Semantic IR + visitor dispatch:** 24-subtype `ScriptOp` instruction set, 9-subtype `Expr` tree, nullable platform annotations (bank slot / VRAM range / OAM slot), unsealed to open interfaces so genre packages extend the IR without modifying core.
- **Idiomatic Kotlin DSL:** delegate-based variables (`u8Var`/`i8Var`/…), operator overloads (`set`/`+=`/comparisons), type-safe input (`dpad.up.held`), type-safe scene refs (`navigate(scene)`), actor name inference, AABB collision (`ball.collides(paddle)`) — zero magic strings.
- **Structured C AST codegen:** sealed `CFile/CFunction/CStatement/CExpr` hierarchy with bank as a typed immutable field (no mutable `currentBank`), single `CEmitter` pretty-printer, ~150 `CRawCode` escape hatches eliminated to typed nodes.
- **"GC for hardware" analysis passes:** FFD bank bin-packing with scene locality + auto-trampolines, per-scene VRAM tile planning with dedup + overflow errors, OAM/scanline planning, WRAM/HRAM/SRAM layout, and a Rust-cargo-style budget audit as the final gate.
- **Asset pipeline in Gradle:** PNG → deduplicated 2bpp tiles, Tiled/LDtk → tilemaps with collision, sprite-sheet slicing, hUGETracker music, png2asset metasprite path — all incremental build tasks.
- **JVM test runner:** `ScriptOpInterpreter` + `SimulationContextV2` run game logic on the JVM without an emulator (sub-5s), plus a `GbktTestExtension` JUnit5 layer, an embedded Coffee-GB emulator (`gbkt-emulator`), and a 17-tool MCP server for AI-agent UAT.
- **20-module architecture + genre plugins:** layered modules (ir/lang/engine/world/core/backend-api/backend-gbdk/analysis) with ServiceLoader-discovered genre packages (RPG, platformer, puzzle, sport) and a Gradle plugin orchestrating the build.
- **Source maps + IDE DX:** `.gbkt.map` C-line → Kotlin file:line mapping, Rust-style error formatting, bidirectional DSL↔C navigation and budget gutter icons in the IntelliJ plugin.
- **GBDK SDK reference-port validation track (Phases 9–13):** simple_physics, metasprites, banks, and platformer_template re-implemented as idiomatic gbkt DSL and validated against GBDK reference C as a codegen oracle, with binding visual UAT sign-offs (banking, OAM, palette/sprite polarity, tilemap collision).
- **Release hardening:** retired dead examples + dropped V2 suffixes + removed pre-AST dead code (Phase 14), then drove the entire pre-existing-red suite green diagnose-first (Phase 15) as the hard v0.1.0 gate.

**Known deferred items at close:** 56 (see STATE.md `## Deferred Items`) — 35 dormant/active backlog seeds, 9 historical verification gaps, 4 UAT-status flags (0 pending scenarios each), 5 advisory codegen todos, 2 debug sessions (one is the resolved-sessions KB; one targets the retired `racer` example), 1 quick task. All triaged as out-of-v0.1.0-scope; none block the release gate.

### Known Gaps

Shipped with these accepted gaps (deferred to a future milestone):

- **4 Pending requirements** — QUAL-01, QUAL-02 (Phase 5.15 quality), QUAL-03 (Phase 08 detekt/tech-debt cleanup), IDE-04 (Phase 5.4 IDE) — map to phases deliberately not executed in this milestone.
- **Deferred genre-codegen phases** — 07.5 (platformer genre), 07.6 (RPG genre audit), 07.7 (GBC palette init), 07.8 (UAT re-run), 08 (detekt cleanup) remain open in the archived roadmap; the 7 KEEP example games build and pass the full suite.
- **Advisory DSL-primitive correctness smells** (unreached by shipping examples) — `easeToZero` oscillation when `by > 1`, `wrapAt(0)` silent always-reset, `orElse` may attach to wrap-guard `IfOp`.

---
