/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.domain

/**
 * Experience curve strategy that determines how much EXP is required per level.
 *
 * The backend uses the curve constant when emitting level-up threshold tables. Each strategy
 * produces a different growth function:
 * - [STANDARD]: Linear growth — `base * level`. Simple and predictable.
 * - [SLOW]: Quadratic growth — `base * level^2`. Levels become increasingly hard to reach.
 * - [FAST]: Sub-linear growth — `base * sqrt(level)`. Early levels gain quickly; late levels slow.
 */
enum class ExpCurve {
    /** Linear growth: exp_to_next_level = base * level. */
    STANDARD,

    /** Quadratic growth: exp_to_next_level = base * level^2. */
    SLOW,

    /** Sub-linear growth: exp_to_next_level = base * sqrt(level). */
    FAST,
}
