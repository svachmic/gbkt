---
phase: 19
slug: codegen-fixes-metasprite-cluster
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-13
---

# Phase 19 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from `19-RESEARCH.md` § Validation Architecture. This is a confirmation /
> regression-guard phase: nearly all test infrastructure already exists. The only
> Wave 0 gap is one new GBC-mode UAT class for FIX-01 capture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) via `kotlin("test")`, `useJUnitPlatform()` per module |
| **Config file** | each module's `build.gradle.kts` (existing) |
| **Quick run command** | `./gradlew :gbkt-backend-gbdk:test :gbkt-lang:test` |
| **Full suite command** | `./gradlew :gbkt-backend-gbdk:test :gbkt-lang:test :gbkt-examples:metasprites:test :gbkt-examples:metasprites-stress:test` |
| **Estimated runtime** | ~120–240 seconds (cold JVM + ROM-driving UAT) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :module:spotlessApply :module:detekt :module:test` for the touched module (D-09 — spotless/detekt are NOT run by `:module:test` or the pre-commit hook).
- **After every plan wave:** Run the full suite command above.
- **Before `/gsd-verify-work`:** Full suite green + 4 FIX-01 screenshots exist + `19-AUDIT-FIX-02.md` authored + byte-identity sha256 diff shows no Phase-19-attributable change.
- **Max feedback latency:** ~240 seconds.

---

## Per-Task Verification Map

| Req ID | Behavior | Test Type | Automated Command | File Exists | Status |
|--------|----------|-----------|-------------------|-------------|--------|
| FIX-01 | Fresh HEAD GBC screenshots SEED-004 (elephant tiles uncorrupted) / SEED-005 (BG checkerboard) | UAT/emulator | `./gradlew :gbkt-examples:metasprites:test --tests "*.Phase19VisualEvidenceTest"` | ❌ W0 (new class) | ⬜ pending |
| FIX-01 | Fresh HEAD GBC screenshots SEED-006 (assigned sub-palette) / SEED-013 (correct GBC colors) | UAT/emulator | same as above | ❌ W0 | ⬜ pending |
| FIX-02 SEED-007 | Actor palette auto-slots sequential 0,1,2,3 (`actorPaletteAutoSlot++`) | unit | `./gradlew :gbkt-lang:test --tests "*.Seed007GameBuilderPaletteSlotTest"` | ✅ exists | ⬜ pending |
| FIX-02 SEED-008 | Actor + metasprite `set_sprite_data` VRAM non-colliding (monotonic allocator) | unit | `./gradlew :gbkt-backend-gbdk:test --tests "*.Seed008VramCollisionTest"` | ✅ exists | ⬜ pending |
| FIX-02 SEED-009 | `bank1.c` includes `<gbdk/metasprites.h>` when MoveMetasprite used | unit | `./gradlew :gbkt-backend-gbdk:test --tests "*.Seed009BankIncludeTest"` | ✅ exists | ⬜ pending |
| FIX-02 SEED-010 | Two metasprites emit distinct namespaced descriptor arrays | unit | `./gradlew :gbkt-backend-gbdk:test --tests "*.Seed010NamespaceTest"` | ✅ exists | ⬜ pending |
| FIX-02 SEED-011 | `play_frame` has exactly one hiwater init per frame | unit | `./gradlew :gbkt-backend-gbdk:test --tests "*.Seed011HiwaterFrameScopeTest"` | ✅ exists | ⬜ pending |
| Req 3 | metasprites buildRom exits 0 | build | `./gradlew :gbkt-examples:metasprites:buildRom` | ✅ (task) | ⬜ pending |
| Req 3 | metasprites ROM smoke screenshot at HEAD | UAT/emulator | part of `Phase19VisualEvidenceTest` | ❌ W0 | ⬜ pending |
| Req 5 | byte-identity no drift — `main.c` + `bank1.c` sha256 before/after | manual procedure | `sha256sum build/gbkt/generated/main.c build/gbkt/generated/bank1.c` (D-07) | manual | ⬜ pending |
| Req 5 | elephant.c / tiger.c sprite sidecar baselines unchanged | unit | `./gradlew :gbkt-examples:metasprites:test :gbkt-examples:metasprites-stress:test --tests "*ByteIdentity*"` | ✅ exists | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/Phase19VisualEvidenceTest.kt` — new GBC-mode (`gbcMode=true` + `.noi` symFile) UAT class for FIX-01 captures (SEED-004/005/006/013) + the Req-3 ROM smoke screenshot, emitting PNGs into `19-codegen-fixes-metasprite-cluster/evidence/`.

*All other test infrastructure — the 5 SEED emission guards (1 in `gbkt-lang`, 4 in `gbkt-backend-gbdk`), the metasprites/metasprites-stress byte-identity baselines, and framework configs — already exists. No new FIX-02 guards are authored (research confirmed full existing coverage).*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Byte-identity before/after diff | Req 5 | Same-session procedural oracle (D-07) — maintained `main.c` baseline would be brittle to toolchain non-determinism | Clean `buildRom`, `sha256sum` the generated `main.c`+`bank1.c` for metasprites and metasprites-stress at phase start; re-diff at phase end; any change must be explained + screenshot-re-confirmed |
| FIX-01 / ROM-smoke visual parity | FIX-01, Req 3 | Visual Evidence Rule — "renders correctly" is a visual truth; PNG must be human-/screenshot-confirmed, not asserted on variables | Capture via `Phase19VisualEvidenceTest`; confirm each screenshot visibly shows the fixed behavior |

---

## Validation Sign-Off

- [ ] All tasks have automated verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (one: `Phase19VisualEvidenceTest.kt`)
- [ ] No watch-mode flags
- [ ] Feedback latency < 240s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
