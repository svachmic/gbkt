# Phase 22: Golden Screenshot and Evidence Storage Overhaul - Context

**Gathered:** 2026-06-14
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase changes how visual evidence is **stored, diffed, and re-baselined** — it does NOT change what any ROM renders. Goldens become immutable, committed, exact-match PNG baselines that UAT tests diff against; the per-phase `EVIDENCE_DIR` pattern is removed from all 33 `src/test` classes; `.planning/phases/**/evidence/` becomes gitignored scratch; GBC-vs-DMG capture mode is auto-derived from the ROM CGB-flag byte. Success = a clean `./gradlew test` leaves zero new untracked files and zero modified committed evidence.

</domain>

<spec_lock>
## Requirements (locked via SPEC.md)

**7 requirements are locked.** See `22-SPEC.md` for full requirements, boundaries, and acceptance criteria.

Downstream agents MUST read `22-SPEC.md` before planning or implementing. Requirements are not duplicated here.

**In scope (from SPEC.md):**
- Removing the `EVIDENCE_DIR` companion from all 33 `src/test` classes
- A central, tracked, ROM+anchor-keyed goldens location for visual PNG anchors
- A golden-diff helper (exact PNG match) wired into the UAT capture flow
- Redirecting emission-test `.txt` dumps to gitignored `build/` scratch (no text golden)
- An explicit re-baseline action (gradle flag/task)
- `AgentSessionConfig.discoverFiles` reading ROM byte `0x143` to set `gbcMode`; removing the Phase 21 `.copy(gbcMode = true)`; GBC-header assertion in GBC-target tests
- Dropping `capturedAt` churn (drop the field or stop committing sidecars)
- Migrating ONLY the Phase 19/20/21 blessed visual anchors into the central goldens location
- `git rm` of all other tracked `.planning/phases/**/evidence` files
- `.gitignore` rules for `.planning/phases/**/evidence/` and `build/` screenshot scratch
- TESTING.md update (layout + re-baseline command)

**Out of scope (from SPEC.md):**
- Golden-diffing or committing emission `.txt` C-code dumps — the in-test C assertion is already the gate
- Migrating archived v0.1.0-phase evidence or non-19/20/21 PNGs as goldens
- Perceptual-tolerance PNG diffing — exact match is required
- Fixing pong-class ROM-hash non-determinism — affects `.gb` binaries, not PNGs
- New codegen/visual fixes — this phase changes evidence STORAGE only
- Changing MCP/emulator runtime behavior beyond `discoverFiles` GBC detection

</spec_lock>

<decisions>
## Implementation Decisions

### Goldens directory + anchor key scheme
- **D-01:** Goldens live **per-module under `src/test/resources/goldens/<rom>/<anchor>.png`** — NOT a single repo-root location (user overrode the seed's "one top-level dir" idea). Each example module owns its own goldens, co-located with its tests. Because each example module compiles a single ROM, the `<rom>/` segment is organizational; the planner MAY flatten it to `goldens/<anchor>.png` if it proves redundant, but keep the structure consistent across modules by default.
- **D-02:** Anchor names are **descriptive and phase-agnostic** (e.g. `metasprites/elephant-cyan-subpalette.png`, `platformer/world1-boot.png`) — decoupled from phase/seed IDs (`SEED-004`, `ROM-smoke`). The migration preserves the PNG **bytes** byte-identically (binding baselines, no re-render) while renaming the file/key. There are ~12 anchors total (Phase 19/20/21), so the renaming is a bounded, deliberate step.

### Diff helper / reuse strategy
- **D-03:** Add a **new thin `assertGolden`-style helper** (e.g. `assertGoldenMatch(rom, anchor, capturedFrame)`) rather than rewiring the 33 bespoke `StepAgent`-capture tests onto `UatRunner`. The helper: captures to gitignored scratch, calls the **existing** `VisualDiff.compare(...)` at **tolerance `0.0` (exact pixel match)**, and fails the test on any mismatch. The bespoke tests swap their `captureAndRename(...)` calls for `assertGolden(...)`. `UatRunner`'s own goldenDir flow is left untouched (low-risk; no forced rewrite of bespoke capture tests).
- **D-04:** Reuse the existing `VisualDiff.compare()` (`gbkt-emulator/.../agent/VisualDiff.kt`) as the diff engine — it already supports exact match at tolerance 0.0 and emits a red diff image on mismatch. Do not introduce a second diff implementation. (Module placement of the new helper — `gbkt-test` vs `gbkt-emulator` — is left to the planner; the example tests already depend on both.)
- **D-05:** **Missing golden = test failure** with a re-baseline hint (e.g. `GOLDEN MISSING <path> — run ./gradlew test -Pgbkt.updateGoldens to bless it`). A normal run never auto-creates a golden, so the "no writes on plain `./gradlew test`" guarantee holds. (Mirrors `UatRunner`'s existing "GOLDEN MISSING … promote with cp" message, but as a hard failure pointing at the re-baseline flag.)

### Re-baseline trigger shape
- **D-06:** Re-baseline is triggered by a **Gradle project property `-Pgbkt.updateGoldens`** propagated into the test JVM as a **system property** that the `assertGolden` helper reads. When set, the helper **writes** the golden (and passes) instead of diffing. Idiomatic Gradle; composes with `--tests` filters to re-bless a subset. Chosen over a dedicated gradle task (less granular) and an env var (easier to leave set accidentally → weakens the "never bless by accident" guarantee). A plain `./gradlew test` (flag absent) must never write a golden.
- **D-07:** **Guarded bless** — even in update mode, the GBC-header auto-detect (SPEC R5) still runs and the GBC-target tests still assert the ROM is GBC, so a mis-built DMG ROM cannot silently bless an inverted-palette golden. This directly prevents the Phase 13.5 / 21-07 wrong-mode-capture failure class from being immortalized as a baseline.

### Sidecar disposition
- **D-08:** **Keep the `.json` sidecar but only in gitignored scratch, and drop the nondeterministic `capturedAt` field** from `ScreenshotCapture.capture()` (`ScreenshotCapture.kt:106`). The sidecar's `variables`/`debugLog` stay useful for debugging a failed diff; removing `capturedAt` makes it fully deterministic (belt-and-suspenders against any future accidental commit). `capturedAt` has **zero production consumers** — only `ScreenshotCaptureTest.kt:92-94` asserts on it, so update that test in lock-step. The 22 currently-committed sidecar `.json` files are `git rm`'d as part of the evidence migration (R6).

### Claude's Discretion
- Module placement of the new `assertGolden` helper (`gbkt-test` test-infra vs `gbkt-emulator` agent package) — planner decides; both are already on the example tests' classpath.
- Whether to flatten `goldens/<rom>/<anchor>.png` to `goldens/<anchor>.png` per module if the `<rom>/` segment is redundant (D-01).
- Exact name of the system-property key derived from `-Pgbkt.updateGoldens` and the precise wording of the missing-golden failure message.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Locked requirements
- `.planning/phases/22-golden-screenshot-and-evidence-storage-overhaul/22-SPEC.md` — 7 locked requirements, boundaries, acceptance criteria. MUST read before planning.
- `.planning/phases/22-golden-screenshot-and-evidence-storage-overhaul/22-SEED-SOURCE.md` — USER-locked decisions (2026-06-14), problem statement, non-binding implementation sketch.

### Code the phase touches (grounding for the decisions above)
- `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/ScreenshotCapture.kt` — `capture()` writes PNG + `.json` sidecar; `capturedAt` churn source at line 106.
- `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/VisualDiff.kt` — existing exact/tolerance PNG diff engine to reuse (D-04).
- `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/AgentSessionConfig.kt` — `discoverFiles()` (line 63) wires symFile but never sets `gbcMode`; target of SPEC R5 (read ROM `0x143`).
- `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/UatRunner.kt` — existing `goldenDir` + `goldenTolerance` + per-checkpoint comparison (lines ~44, 184-202); left untouched (D-03) but is prior art for the diff/missing-golden flow.
- `gbkt-emulator/src/test/kotlin/io/github/gbkt/emulator/agent/ScreenshotCaptureTest.kt` — asserts on `capturedAt` (lines 92-94); update in lock-step with D-08.
- `gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/Phase19VisualEvidenceTest.kt` — representative bespoke visual UAT test (raw `capture()` + `captureAndRename` + `.copy(gbcMode=true)`); pattern to migrate.

### Project conventions / related decisions
- `context/TESTING.md` — current per-phase evidence convention; target of SPEC R7 update.
- `.planning/verifier-gates.md` — Visual Evidence Rule (visual truths require runtime screenshots).
- Memory `[[project_golden_screenshot_storage_decision]]` — USER-locked storage decision and grounded root cause.
- Memory `[[learning_platformer_mcp_needs_gbc_mode]]` — GBC-mode capture requirement (motivates SPEC R5 + D-07).
- Memory `[[feedback_rom_build_smoke_test_for_codegen_phases]]` — clean `:gbkt-examples:<game>:buildRom` smoke required for codegen-touching changes (this phase is storage-only, but `discoverFiles` GBC detect affects capture).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `VisualDiff.compare(expected, actual, tolerance, diffOutputDir)`: already does exact match at tolerance 0.0 and writes a red diff image on mismatch — the diff engine for `assertGolden` (D-03/D-04). No new diff code needed.
- `UatRunner` golden flow (`goldenDir`, `goldenTolerance`, `DiffResult`, "GOLDEN MISSING … promote" message): prior art for the missing-golden + diff behavior to mirror in the helper.
- `AgentSessionConfig.discoverFiles(romFile)`: single convention-based discovery entry point — the natural place to add ROM `0x143` CGB-flag reading (SPEC R5).

### Established Patterns
- All 33 `src/test` evidence classes share an `EVIDENCE_DIR` companion resolving to `.planning/phases/<phase>/evidence` — uniform pattern, so the removal/redirect is mechanical and repeatable across classes.
- Visual UAT tests use raw `ScreenshotCapture.capture()` + a `captureAndRename(...)` helper into `EVIDENCE_DIR`, NOT `UatRunner` — confirms D-03's "swap the call site" approach over a `UatRunner` rewrite.
- Emission tests (e.g. `BanksEmissionTest`) dump `.txt` purely as artifacts; the in-test C assertion is the real gate — confirms R3's "redirect scratch only, no text golden".

### Integration Points
- `assertGolden` helper ↔ per-module `src/test/resources/goldens/` (D-01) ↔ gitignored scratch capture dir ↔ `VisualDiff` ↔ `-Pgbkt.updateGoldens` system property.
- `discoverFiles` ↔ ROM byte `0x143` ↔ `gbcMode` ↔ removal of Phase 21 `.copy(gbcMode = true)` (commit `71dd3a57`).
- `.gitignore` ↔ `.planning/phases/**/evidence/` (scratch) + `build/**/screenshots/` (scratch) ↔ `git rm` of 148 tracked evidence files (38 PNG / 110 text / 22 sidecar json).

</code_context>

<specifics>
## Specific Ideas

- Migration preserves the **exact bytes** of the Phase 19 (metasprite cyan-elephant), Phase 20 (banks/tRNS), and Phase 21 (platformer GBC) blessed anchors — they are binding USER-signed-off baselines; no re-render.
- The GBC CGB-flag read uses ROM offset `0x143` with values `0x80` (GBC-enhanced) and `0xC0` (GBC-only) → `gbcMode = true`.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope. (pong-class ROM-hash non-determinism remains tracked separately in `[[project_pong_toolchain_nondeterminism]]`; it affects `.gb` binaries not PNGs and is explicitly out of scope per SPEC.)

</deferred>

---

*Phase: 22-golden-screenshot-and-evidence-storage-overhaul*
*Context gathered: 2026-06-14*
