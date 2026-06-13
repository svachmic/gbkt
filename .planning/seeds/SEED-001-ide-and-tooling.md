---
id: SEED-001
status: dormant
planted: 2026-05-13
planted_during: v1.0 / Phase 07.9 closeout
trigger_when: when v2.0 milestone is created (after v1.0 ships and audits clean)
scope: large
triage_disposition: RE-DEFERRED
triage_evidence: ".planning/phases/16-seed-triage/TRIAGE.md#SEED-001"
triage_date: 2026-06-12
---

# SEED-001: IDE & Tooling

## Why This Matters

The framework's value is "JVM-Compose-for-Game-Boy" — but without rich IDE support, the developer experience reduces to Kotlin syntax + Gradle. Live DSL feedback, in-editor tilemap preview, and a localization editor would close the inner loop and make the framework approachable to game-development users (not just Kotlin engineers). This was originally planned as Phase 09 of v1.0 but does not match v1.0's framework-correctness focus.

## When to Surface

**Trigger:** when the v2.0 milestone is created (after v1.0 ships and audits clean).

This seed should auto-surface during `/gsd-new-milestone` for v2.0. The work belongs in a developer-experience milestone, not in the framework-correctness/codegen-fix milestone (v1.0).

## Scope Estimate

**Large** — IntelliJ plugin enhancements + live DSL feedback + localization editor + tilemap preview is at least one full milestone of work. Likely splits into 4–6 phases:
- IntelliJ plugin: completion, navigation, error highlighting for DSL constructs
- Live DSL feedback: incremental compilation + emulator hot-reload
- Localization editor: GUI on top of the .po pipeline
- Tilemap preview: in-editor visual editor for asset/tilemap files
- Embedded emulator panel (already partially scaffolded in `gbkt-emulator` per Phase 06.12)

## Breadcrumbs

- `gbkt-intellij-plugin/` — existing IntelliJ plugin module (highlighting, completion, visual editors, C preview)
- `gbkt-emulator/` — embedded Coffee-GB emulator with debug-log capture (Phase 06.12 SHIPPED)
- `context/TOOLING.md` — current asset pipeline + IntelliJ plugin docs
- ROADMAP.md historical reference: previously listed as `Phase 09: IDE & Tooling - IntelliJ plugin enhancements, live DSL feedback, localization editor, tilemap preview`
- Removed via `/gsd-phase --remove 09` on 2026-05-13 because v1.0 scope is framework correctness + example-game shipping, not DX

## Notes

Captured 2026-05-13 during Phase 07.9 closeout. The user explicitly chose to defer this work to v2.0 because it does not fit v1.0's narrative of "ship the framework + ship the example games."

When v2.0 starts, the next-step routing in v1.0's final STATE will already point at this seed via `/gsd-new-milestone`'s seed scan.
