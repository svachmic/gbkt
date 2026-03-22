# Racer

## Overview
A top-down racing game built with the sport genre package. Drive a car around a square circuit track, complete 3 laps to win. The track has 4 waypoints and an AI opponent (Rival) with rubber-banding enabled. The game targets GBC_COMPATIBLE for color support on Game Boy Color hardware.

## How to Play
Steer the car with the d-pad: up to accelerate forward (move car up), down to brake/reverse (move car down), left/right to turn. Lap progress is detected when the car crosses the start/finish zone at x < 50, y in [95, 115). Increment 3 laps to finish the race and see results. Race time accumulates each frame. The AI opponent (Rival) follows the track waypoints with rubber-banding at 85% speed and difficulty 3.

## Controls
| Scene | Button | Effect |
|-------|--------|--------|
| title | START | Start the race |
| race | UP | Accelerate (move car up, 3px/frame) |
| race | DOWN | Brake/reverse (move car down, 2px/frame) |
| race | LEFT | Steer left (3px/frame) |
| race | RIGHT | Steer right (3px/frame) |
| results | START | Return to title (resets lap=0, raceTime=0, position=1) |

## Scene Flow
- title -> race (press START)
- race -> results (lap count reaches 3)
- results -> title (press START)

## Win / Lose Conditions
- **Win**: `lap` reaches 3 — all laps completed → results scene showing position and race time
- **Lose**: No lose condition — the race completes after 3 laps regardless of AI position

## Known Quirks
- Lap detection uses a coordinate zone at the start/finish line: car x < 50 AND car y in [95, 115) AND lap < 3
- Lap zone can be crossed multiple times per pass — the `lap isBelow 3` guard prevents over-counting (but not double-counting on a single slow pass)
- Race time increments every frame (not seconds); displayed as a raw frame count on the results screen
- `position` variable tracks race position (starts at 1st); not dynamically updated by the AI system in this example
- Track is a 32x32 tile zone at 8px = 256x256 pixel world; camera follows car with smoothing=0.3 and bounds(256, 256)
- Car uses smooth movement controller: speed=3, acceleration=1, friction=1
- AI vehicle (Rival) has stats: speed=180, acceleration=150, handling=200; rubber-banding strength=40
- Engine sound (`engineSfx`) plays on up/down movement; turn sound (`turnSfx`) plays on left/right

## Variables Reference
| Variable | Type | Semantic | Description |
|----------|------|----------|-------------|
| lap | UINT8 | counter | Current lap count (0-3); race finishes at 3 |
| raceTime | UINT8 | timer | Frames elapsed since race start; displayed on results screen |
| position | UINT8 | rank | Race position (starts at 1, not dynamically updated in this example) |
