---
id: SEED-CONFIGBUILDER-SETTER-API
status: dormant
planted: 2026-06-15
planted_during: "v0.1.1 / milestone close cleanup"
trigger_when: "v0.2.0"
scope: low
triage_disposition: RE-DEFERRED
triage_date: 2026-06-15
original_id: configbuilder-cartridge-setter-api-consistency
title: Unify ConfigBuilder setter convention (function vs var per field)
source: phase-13.1-code-review
area: gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SystemBuilders.kt
original_priority: low
---

## Context

Carried advisory item **IN-02** from `13.1-REVIEW.md`.

`ConfigBuilder` has `var cartridge` (and `var gbcTarget`) plus `cartridge(type)` / `target(mode)`
functions that set the same field — two ways to set one value. Meanwhile `romBanks` and `ramBanks`
are only settable via direct property assignment (no setter function), so the API is inconsistent
about which style it offers per field. Minor DSL smell (the MagicNumber/ergonomics exemption makes
this informational, not a defect).

## Fix

Pick one convention per field (function setters for all, or `var` for all) for a uniform config
DSL. Low priority — revisit if/when the config DSL surface is otherwise touched.
