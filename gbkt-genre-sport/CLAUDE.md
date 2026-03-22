# gbkt-genre-sport -- Sport Genre Plugin

Provides sport-game DSL constructs and GBDK code generation for three sub-genres: racing (tracks, vehicles, AI, laps), ball sports (field, ball physics, goals, scoring), and tournaments (brackets, standings, match progression).

## Dependencies
- **Depends on:** `gbkt-core`, `gbkt-backend-api`, `gbkt-backend-gbdk`, `gbkt-engine`
- **Used by:** `gbkt-backend-gbdk` (via ServiceLoader)

## Structure
- `domain/` -- Type definitions across four files: `SportTypes.kt`, `RacingTypes.kt`, `BallSportTypes.kt`, `TournamentTypes.kt`
- `dsl/` -- Builder DSL (`SportBuilders.kt`) and extension functions (`SportExtensions.kt`)
- `codegen/` -- GBDK visitor (`SportVisitor.kt`) for C generation

## Key Types -- Racing
| Type | Role |
|------|------|
| `RacingConfig` | Top-level racing config: mode, laps, track, vehicles, AI, pickups |
| `RacingMode` | Enum: racing variant (time-trial, head-to-head, etc.) |
| `TrackDef` | Track layout with waypoints and lap count |
| `WaypointDef` | Track waypoint with tile position and checkpoint flag |
| `VehicleDef` | Named vehicle with stats |
| `VehicleStats` | Speed, acceleration, handling |
| `RacingAIConfig` | AI speed percent, difficulty, rubber-banding |

## Key Types -- Ball Sports
| Type | Role |
|------|------|
| `BallSportConfig` | Field, ball physics, scoring rules, match structure, pickups |
| `FieldDef` | Playing field dimensions and optional goal config |
| `GoalConfig` | Goal width and height |
| `BallPhysicsConfig` | Ball speed, friction, bounce |
| `ScoringRules` | Points per goal, win condition, target score, time limit |
| `MatchStructure` | Halves, rounds to win, half duration |
| `WinCondition` | Enum: `SCORE_LIMIT`, `TIME_LIMIT`, etc. |

## Key Types -- Tournament
| Type | Role |
|------|------|
| `TournamentConfig` | Bracket type, participants, rounds per match, standings |
| `BracketType` | Enum: `SINGLE_ELIMINATION`, `ROUND_ROBIN`, etc. |
| `TournamentMatch` | Single match entry |
| `StandingEntry` | Participant standing with wins/losses |

## Key Types -- Shared
| Type | Role |
|------|------|
| `SportPickupDef` | Power-up pickup with type |
| `SportPickupType` | Enum: pickup variants (boost, shield, etc.) |

## DSL Extensions
`racing`, `ballSport`, `tournament` -- each takes a builder lambda to configure the respective sub-genre and registers the config on the active game.

## Codegen
`SportVisitor` dispatches to `visitRacing`, `visitBallSport`, and `visitTournament`, plus a shared `buildPickupResult` helper for power-up generation.

## Testing
```bash
./gradlew :gbkt-genre-sport:test
```
