# SEED-024 — BuildLogPanel export: native save-to-file dialog

> **Triage:** RE-DEFERRED — [TRIAGE.md#SEED-024](.planning/phases/16-seed-triage/TRIAGE.md#SEED-024) · 2026-06-12

**Origin:** SonarCloud Info-issue sweep of PR #33 (`feat/d_and_d_gaps`), 2026-06-10
**Status:** Open — not yet bound to a target phase
**Routing:** Fold into the IntelliJ-plugin testing/hardening phase seeded by [[SEED-019]] as a sibling task — same module, and the fix should land with a fixture test rather than untested.
**Blast radius:** `gbkt-intellij-plugin/.../buildtools/BuildLogPanel.kt` only.

## Problem

`BuildLogPanel.exportLog()` is a stub behind a real toolbar action: it joins all
log entries and `println`s them to the IDE process stdout instead of saving
anywhere the user can find. From the user's perspective the Export button
silently does nothing.

## Goal

Export opens a native save dialog (`FileSaverDescriptor` /
`FileChooserFactory.createSaveFileDialog`), defaults to something like
`gbkt-build-<timestamp>.log` in the project dir, writes `allEntries` text, and
surfaces success/failure via a notification balloon. Interim stdout dump is
removed.

## Scope sketch

1. ~20-line change: `FileSaverDescriptor("Export Build Log", "", "log", "txt")`
   → `save(project.guessProjectDir(), defaultName)` → write text, NIO charset
   UTF-8.
2. Error path: IOException → `NotificationGroupManager` warning balloon.
3. Test under the [[SEED-019]] platform-test framework (light fixture; the
   dialog itself can be factored behind a small seam for headless testing).

## Discovery hooks

- `BuildLogPanel.kt` — `Deferred (SEED-024)` marker in `exportLog()` (~line 247).
- [[SEED-019]] — plugin test-framework phase this should ride with.
