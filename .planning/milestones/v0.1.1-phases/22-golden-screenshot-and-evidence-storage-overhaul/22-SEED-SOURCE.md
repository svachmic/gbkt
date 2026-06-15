# SEED — Golden Screenshot Storage Overhaul (UAT visual-evidence infra)

> Captured: 2026-06-14 (post Phase 21 / milestone v0.1.1 close). Target milestone: **v0.2.0**.
> Owner decision: USER. Status: **DECIDED — ready for spec/plan**.

## Problem

UAT visual evidence has two recurring failure modes that make "old phases resurface" in `git status`
and produce false regression flags:

1. **Sidecar timestamp churn (noise).** Each captured screenshot writes a `.json` sidecar containing
   `"capturedAt": <wall-clock-millis>`. The PNG is deterministic; the sidecar is not. Any `gradle test`
   run that re-executes a UAT class re-dirties its *committed* sidecars with a meaningless timestamp
   delta. A single `./gradlew test` on 2026-06-14 dirtied 16 sidecars across Phase 20 AND Phase 21
   (both supposedly closed). Reverted as junk, but it keeps recurring.

2. **Mode/baseline drift (dangerous).** Golden screenshots captured under the wrong emulator mode
   (DMG vs GBC) read as a regression even for a correct ROM. Hit 3×: Phase 13.5 (false D-11), and
   twice in Phase 21-07 (inverted/"negative" colors). Root cause: `AgentSessionConfig.discoverFiles()`
   wires the `.noi` symFile but never sets `gbcMode` (defaults false → DMG); the GBC-vs-DMG decision
   lives implicitly per-test instead of being derived from the ROM. See
   [[learning_platformer_mcp_needs_gbc_mode]].

**Root cause of "old phases resurface":** evidence is stored INSIDE each phase dir
(`.planning/phases/NN-.../evidence/`) AND committed, while UAT tests point back at those committed
dirs and OVERWRITE them every run. The artifacts play two conflicting roles at once — immutable
golden baseline AND live test scratch output.

## Decisions (locked by USER, 2026-06-14)

1. **Goldens are immutable; tests compare.** Goldens are committed once and read-only. UAT tests
   capture to `build/` (gitignored) and DIFF against the golden, failing on mismatch. Re-baselining
   is an explicit, reviewed action (e.g. a `--update-goldens` / dedicated gradle flag), never a side
   effect of a normal test run.

2. **Central `goldens/` dir; phase dir = scratch.** Goldens live in one tracked top-level location
   (e.g. `test/goldens/<rom>/<anchor>.png`) keyed by ROM + anchor, NOT by phase. Per-phase
   `evidence/` becomes gitignored scratch so nothing tracked lives under a closed phase → closed
   phases can never resurface.

3. **Auto-detect GBC from the ROM CGB header.** `discoverFiles` reads the ROM's `0x143` CGB-flag
   byte and sets `gbcMode` automatically, so every GBC example captures correctly with zero per-test
   config. (Stretch: ALSO assert the ROM is GBC in GBC-target tests so a mis-built DMG ROM fails
   loudly instead of rendering inverted.)

## Implementation sketch (for the planner — not binding)

- `AgentSessionConfig.discoverFiles`: read CGB flag (ROM byte 0x143 ∈ {0x80, 0xC0}) → set `gbcMode`.
  Remove the now-redundant `.copy(gbcMode = true)` added in Phase 21 (commit 71dd3a57) once this lands.
- A golden-diff helper (exact PNG match, or perceptual tolerance — note pong-class toolchain
  non-determinism affects ROMs, not generated PNGs, but confirm). Wire into UAT capture flow.
- `.gitignore`: ignore `.planning/phases/**/evidence/` (scratch) and `build/**/screenshots/`.
- Migration: move the visual goldens that are genuinely "blessed" (e.g. the Phase 21 GBC anchors,
  Phase 19 metasprite cyan-elephant, Phase 20 tRNS) into `test/goldens/`; drop the committed
  per-phase `evidence/` copies. Decide what historical evidence to keep vs. drop.
- Drop `capturedAt` from sidecars (or stop committing sidecars entirely) to kill timestamp churn.
- A re-baseline command + docs in TESTING.md.

## Routing

Wide blast radius (test-infra across gbkt-emulator + gbkt-test + every UAT test + .gitignore + a
file migration). Do NOT hack inline. Route: `/gsd-spec-phase` → `/gsd-discuss-phase` →
`/gsd-plan-phase` WITH research, as a v0.2.0 phase. Per
[[feedback_route_to_proper_phase_when_blast_radius_is_wide]].

Related: [[learning_platformer_mcp_needs_gbc_mode]], [[feedback_visual_evidence_for_visual_truths]],
[[learning_platformer_sprite_hitbox_overhang]].
