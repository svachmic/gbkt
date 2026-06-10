---
id: configbuilder-cartridge-setter-api-consistency
title: Unify ConfigBuilder setter convention (function vs var per field)
created: 2026-06-03
source: phase-13.1-code-review
status: pending
priority: low
scope: gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SystemBuilders.kt
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
