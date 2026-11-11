# Timeline and tweens

User / AI guide: `app/src/main/assets/ai/docs/timeline.md` (`read_docs {topic:'timeline'}`). This file is the map of the code.

## File: `<project>/timelines/<name>.s2tl`
```json
{"name": "door_open", "duration": 1.5, "loop": "ONCE", "relative": false, "playOnStart": false, "target": "door", "scene": "",
 "tracks": [{"element": "door", "property": "y", "keys": [{"t": 0, "v": "200", "i": "smooth"}, {"t": 1.5, "v": "320", "i": "bounce"}]}],
 "markers": [{"t": 1.2, "name": "thud"}]}
```
`element` may be `@self` (the element the timeline is played on). Key `i` = transition INTO the key (`Interp.NAMES`: linear, smooth, easeIn, easeOut, sine, bounce, elastic, back, fast, step).

## Runtime (`com.star4droid.template.anim`)
| Class | Role |
|---|---|
| `Timeline` | Data + JSON (no GL), `Track.valueAt(t)`; also used by the editor preview so editor and game show the same values. |
| `Interp` | Transition curves. |
| `Props` | Reads / writes animatable properties with editor names and units (2D bottom-left px, editor degrees; 3D meters / degrees; `var:` element variables). |
| `TimelinePlayer` | One playing timeline on one target; animated bodies become kinematic while it plays. Raises `onTimelineEnd`, `onTimelineMarker`. |
| `Anim` | Facade: `play`, `stop`, `pause`, `resume`, `seek`, `setSpeed`, `reverse`, `tween`, `stopTweens`, reporters; `Anim.update` runs from `StageImp` each frame; `playOnStart` timelines start with their scene. |

## Editor
- `editor/TimelineSession.java`: open timeline, playhead, preview (applies values to the editor actors, restores the base on close), record mode (changes after touch release become keys at the playhead), copy / paste keys. It is a `LibgdxEditor.SaveFilter`: the scene file always gets the base values, never the preview.
- UI: `ui/timeline/TimelinePanel.java` (bottom panel: transport, tracks, keys, markers, settings), `TimelineView.java` (tracks canvas), `CurveView.java` (transition previews). Properties panel ◆ buttons add a key for one property.
- Blocks: `ui/blocks/BlocksSystems.java` category Timeline & Tween; events `onTimelineEnd`, `onTimelineMarker`, `onTweenEnd` (`behaviors/SceneEventsExt.java`).

## Tests
`python3 tools/test_anim.py`, `tools/test_blocks_systems.py`.
