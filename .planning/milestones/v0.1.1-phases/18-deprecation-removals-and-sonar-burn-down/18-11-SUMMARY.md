---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: 11
subsystem: gbkt-intellij-plugin
tags: [sonar, s3776, extract-method, non-emitting, cognitive-complexity]
dependency_graph:
  requires: []
  provides: [SONAR-01-N14, SONAR-01-N17]
  affects: [gbkt-intellij-plugin]
tech_stack:
  added: []
  patterns: [extract-method, private-helper-delegation]
key_files:
  created: []
  modified:
    - gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/completion/GbktPropertyChainCompletionProvider.kt
    - gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/toolwindow/CCodePreviewPanel.kt
decisions:
  - "N-14: Promoted local `traverse` function to four focused private methods (traverseChainExpression, traverseDotQualified, addSelectorElement, addCallExprElement); extractChain now delegates with CC=0"
  - "N-17: Extracted onDslFileSaved (guard logic) and triggerRefreshIfInProject (project-path check + async dispatch); setupAutoRefreshListener now only wires the listener"
metrics:
  duration: "6 min"
  completed: "2026-06-13"
  tasks_completed: 2
  files_modified: 2
---

# Phase 18 Plan 11: D-06 NON-EMITTING S3776 Batch — gbkt-intellij-plugin Summary

SONAR-01 NON-EMITTING batch (D-06): two gbkt-intellij-plugin S3776 findings closed via extract-method; JVM tests green; two separate commits per D-06a.

## Tasks Completed

| Task | Finding | Method | CC Before | Action | Commit |
|------|---------|--------|-----------|--------|--------|
| 1 | N-14 | `GbktPropertyChainCompletionProvider.extractChain` | 17 | Extract-method: 4 private helpers | 2b1f4878 |
| 2 | N-17 | `CCodePreviewPanel.setupAutoRefreshListener` | 16 | Extract-method: 2 private helpers | ca747611 |

## Verification

`./gradlew :gbkt-intellij-plugin:test` — 4 tests, BUILD SUCCESSFUL (both tasks).

## Refactoring Detail

### N-14: GbktPropertyChainCompletionProvider.extractChain (cc=17 → below threshold)

The original `extractChain` contained a local `fun traverse(expr)` with nested `when` expressions that drove CC to 17. Promoted to four focused private class-level methods:

- `traverseChainExpression(expr, chain)` — top-level PSI node dispatch (`when` on KtDotQualifiedExpression / KtNameReferenceExpression / KtCallExpression)
- `traverseDotQualified(expr, chain)` — recurse into receiver, then delegate to selector handler
- `addSelectorElement(selector, chain)` — selector-specific `when` (KtNameReferenceExpression with IntelliJ-placeholder guard; KtCallExpression with placeholder guard)
- `addCallExprElement(expr, chain)` — root-level KtCallExpression handling

`extractChain` itself now has CC=0 (creates list, delegates, returns).

### N-17: CCodePreviewPanel.setupAutoRefreshListener (cc=16 → below threshold)

The original method mixed listener registration with multi-level guard logic (auto-refresh check, disposed check, file type check, project path check). Extracted:

- `onDslFileSaved(document)` — handles auto-refresh guard, disposed guard, file extension check; delegates to `triggerRefreshIfInProject`
- `triggerRefreshIfInProject(file)` — handles project-path scoping and schedules async `refreshCode()` on EDT

`setupAutoRefreshListener` now only wires the `FileDocumentManagerListener` and calls `onDslFileSaved`; CC reduced below threshold.

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — no stub patterns introduced.

## Threat Flags

None — internal IDE plugin refactor; no new network, auth, or data surface.

## Self-Check: PASSED

- `GbktPropertyChainCompletionProvider.kt` modified: confirmed
- `CCodePreviewPanel.kt` modified: confirmed
- Commit 2b1f4878 exists: confirmed
- Commit ca747611 exists: confirmed
- `./gradlew :gbkt-intellij-plugin:test` BUILD SUCCESSFUL: confirmed
