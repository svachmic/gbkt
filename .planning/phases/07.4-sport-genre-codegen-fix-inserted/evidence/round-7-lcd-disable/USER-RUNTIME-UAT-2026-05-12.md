# Round-7 Manual Runtime UAT — User Feedback

**Captured:** 2026-05-12, after Plan 30 + Plan 31 merged.
**Method:** User compiled and ran the Racer ROM in the live emulator (not MCP, not JVM proxy).

## User's Verdict

> "We have screen again — I just compiled and ran the game. There is a notion
> of a car, it does move, but camera does not follow and there is no track,
> but there are some tiles on the screen — they are a square shape. Overall
> some progress has been made since the last manual testing, but it is still
> not really a working MVP."

## What the screenshot shows

The user attached a runtime screenshot showing:

| Element | State |
|---------|-------|
| LCD enabled | YES (render visible — frame-124 hang FIXED) |
| BG tilemap rendered | YES (non-blank — `set_bkg_tiles` is working) |
| Player car (OAM) | YES (visible inside the interior area, animating) |
| Rival car (OAM)  | YES (visible south of the track border) |
| Camera follows player | NO — camera stays at world origin |
| Track rendered as circuit | NO — what's rendered is a static square arena with corner cutouts + checkered interior; no waypoint-driven road corridor; no visible checkpoints |
| Game responds to input | YES (car translates between captures) |

## Gap re-classification

GAP-RACE-BLANK-AFTER-START → **CLOSED at the runtime-hang layer.** Plan 30's LCD-wrap fix
restored frame survival; the LCDC trace, 30-frame canary, and 60-frame liveness probe
agree at the JVM tier. The user's screenshot confirms the same at the visual tier:
LCD is on, BG and OAM render, and the ROM does not hang.

NEW GAPS surfaced by the same manual UAT — these were structurally HIDDEN by the
frame-124 hang and only become visible now:

| New gap | Symptom | Likely root cause area |
|---------|---------|------------------------|
| GAP-CAMERA-NO-FOLLOW | Camera stays at world origin when player moves | `visitCameraSystem`/`_camera_target` wiring at scene-enter; possibly Plan 21's UINT8-underflow guard interacting with camera bounds for the new HOME-bank tilemap path; or `_camera_target = 0u;` enterOp from Plan 11 not propagating to the run-loop camera-follow code |
| GAP-TRACK-NOT-RENDERED-AS-CIRCUIT | BG tilemap is a static square arena + checkered interior, not the waypoint-driven road corridor the `racing("track1") { ... waypoint(...) }` DSL declares | `TrackSynthesizer` polygon-rasterisation output AND/OR Plan 16/17 tileset-contrast tile values producing an unintuitive arena shape; OR the `_zone_track1_tiles` array values are correct but `set_bkg_tiles` is rendering the wrong tilemap (Plan 30 HOME-bank helper writes the right tilemap data?) |

## Implication for Plan 29

Task 7 strict resume signal NOT received. UAT-racer.md MUST NOT flip to passed.
Task 8 does NOT execute. Phase exits as `gaps_found` per the plan's strict rules.

Plan 29 evidence captured for Tasks 1-6 is still load-bearing for the
GAP-RACE-BLANK-AFTER-START closure at the runtime-hang layer — it is the
evidence trail that proves Plan 30's fix works at the frame-survival and
LCD-register tiers. The NEW gaps need their own diagnostic and fix plans.

## Recommended next step

`/gsd-plan-phase 07.4 --gaps` (or a new phase 07.4.1) to plan:
1. Diagnose GAP-CAMERA-NO-FOLLOW — likely a probe + JVM-tier RED test against the
   camera-follow expectation `(_camera_x, _camera_y) tracks (_car_x, _car_y)` modulo
   bounds clamp. Suspects: `visitCameraSystem` post-Plan 22 helper, `_camera_target`
   wiring, or `_camera_y` UINT8 underflow when `_car_y` is small after held UP.
2. Diagnose GAP-TRACK-NOT-RENDERED-AS-CIRCUIT — visual diff between waypoint geometry
   declared in `Racer.kt` and rendered tilemap. Suspects: `TrackSynthesizer` raster
   output OR the Plan 30 HOME-bank helper using `_zone_track1_tiles` correctly but
   the upstream tile values are wrong.
