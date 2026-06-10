/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis

/**
 * A structured diagnostic message produced by an analysis pass.
 *
 * @property id Unique diagnostic code (e.g., "ANLZ-01", "ANLZ-03").
 * @property severity Whether this diagnostic is an error, warning, or informational note.
 * @property message Human-readable description of what was found.
 * @property location Optional source location for context (e.g., "scene 'gameplay'").
 * @property suggestion Optional actionable fix suggestion for the developer.
 */
data class Diagnostic(
    val id: String,
    val severity: Severity,
    val message: String,
    val location: String? = null,
    val suggestion: String? = null,
) {
    /** Typed alternative to the primary constructor for diagnostics emitted by built-in passes. */
    constructor(
        code: DiagnosticCode,
        severity: Severity,
        message: String,
        location: String? = null,
        suggestion: String? = null,
    ) : this(code.id, severity, message, location, suggestion)
}

/**
 * Stable diagnostic codes emitted by the built-in analysis passes.
 *
 * Custom passes (injected via `beforePasses`/`afterPasses`) may still emit free-form string ids
 * through the primary [Diagnostic] constructor. ANLZ-06 is deliberately absent: it is reserved and
 * never emitted (tests assert its absence).
 *
 * @property id The stable string id surfaced to users in build output and reports.
 */
enum class DiagnosticCode(val id: String) {
    /**
     * SemanticValidationPass and DeadCodeEliminationPass: reference resolution and IR integrity.
     */
    SEMANTIC_INTEGRITY("ANLZ-01"),

    /** BankingAnalysisPass: ROM bank capacity. Also ConstraintCheckPass OAM-entry pre-check. */
    BANK_CAPACITY("ANLZ-02"),

    /**
     * VRAMLayoutPass: per-scene VRAM tile budget. Also ConstraintCheckPass WRAM pre-check and
     * BankingAnalysisPass cartridge maxRomBanks.
     */
    VRAM_CAPACITY("ANLZ-03"),

    /** OAMAllocationPass: OAM sprite slot capacity. */
    OAM_CAPACITY("ANLZ-04"),

    /**
     * RAMPlanningPass: WRAM/SRAM capacity. Also SemanticValidationPass raw() escape-hatch warning.
     */
    RAM_CAPACITY("ANLZ-05"),

    /** SemanticValidationPass: GBC palette count limits. */
    GBC_PALETTE_LIMIT("ANLZ-07"),

    /** SemanticValidationPass: music fade requested without an audioMixer configured. */
    AUDIO_FADE_UNSUPPORTED("ANLZ-08"),

    /** BankingAnalysisPass: banked tilemap data exceeding its assigned bank. */
    TILEMAP_BANK_OVERFLOW("ANLZ-12"),

    /** RacingValidationPass: track geometry constraints. */
    RACING_TRACK_GEOMETRY("ANLZ-RACING-01"),

    /** RacingValidationPass: missing player vehicle. */
    RACING_PLAYER_MISSING("ANLZ-RACING-02"),

    /** RacingValidationPass: vehicle references an unresolved actor. */
    RACING_VEHICLE_ACTOR_UNRESOLVED("ANLZ-RACING-03"),

    /** RacingValidationPass: manual movement conflicting with racing physics. */
    RACING_MANUAL_MOVEMENT("ANLZ-RACING-04"),

    /** RacingValidationPass: camera follow target mismatching the player vehicle. */
    RACING_CAMERA_FOLLOW_MISMATCH("ANLZ-RACING-05"),

    /** BitwiseOptimizationPass: power-of-2 multiply/divide rewritten to shift. */
    BITWISE_REWRITE("OPT-01"),
}

/** Severity level of a [Diagnostic]. */
enum class Severity {
    ERROR,
    WARNING,
    INFO,
}
