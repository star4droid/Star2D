# Timeline (keyframe animation) and tweens

Animate elements over time: a door that slides, a coin that bounces, a boss entrance, a menu that flies in. Works for 2D and 3D elements.

## 1. Timelines
A timeline is a file `timelines/<name>.s2tl` in the project, made in the editor's Timeline panel (rail button "Timeline"): select an element, press ● Record, move the playhead and change the element (move, turn, resize, color...): keys are added by themselves. ◆ buttons in the Properties panel add a key for one property.

File format (JSON):
```json
{"name": "door_open", "duration": 1.5, "loop": "ONCE", "relative": false, "playOnStart": false, "target": "door", "scene": "scene1",
 "tracks": [{"element": "door", "property": "y", "keys": [{"t": 0, "v": "200", "i": "smooth"}, {"t": 1.5, "v": "320", "i": "bounce"}]}],
 "markers": [{"t": 1.2, "name": "thud"}]}
```
- `loop`: `ONCE`, `LOOP` or `PING_PONG`. `playOnStart`: starts with the scene (`scene` = only in that scene, "" = any).
- `relative`: number values are added to where the element was when the timeline started (one timeline can move any element).
- Track `element`: an element name or `@self` (= the element it is played on, see `tlPlayOn`; `target` is the element used in the editor preview).
- Track `property`: 2D `x`, `y`, `rotation`, `Scale X`, `Scale Y`, `width`, `height`, `Tint` (#RRGGBBAA), `Opacity`, `Visible`, `Text`, `image` (/coin.png); 3D `x`, `y`, `z`, `Rotation X/Y/Z`, `Scale X/Y/Z`, `Color`, `Opacity`, `Visible`; any element: `var:<variable>`.
- Key: `t` seconds, `v` value as text, `i` = how the value moves INTO this key: `linear`, `smooth`, `easeIn`, `easeOut`, `sine`, `bounce`, `elastic`, `back`, `fast`, `step` (jump, no transition).
- Markers raise the scene event `onTimelineMarker(timeline, marker, current)` (play a sound, hurt the player...).

## 2. Timeline blocks (category "Timeline & Tween")
`tlPlay` (name), `tlPlayOn` (name + element: @self tracks move that element: one animation for many enemies), `tlStop`, `tlPause`, `tlResume`, `tlSeek` (seconds), `tlSpeed` (1 normal, 2 fast, 0.5 slow motion), `tlReverse`. Values: `timeline ___ is playing`, `time of timeline ___`, `duration of timeline ___`.

## 3. Tweens (no file needed)
- `tween` element, property, value, seconds, easing: e.g. `tween coin "y" 300 0.5 bounce`.
- `tweenFull` adds delay, repeat (-1 = forever) and back and forth (pulse: `tweenFull star "Scale X" 1.3 0.4 sine 0 -1 true`).
- `tweenStop` element; value `___ is tweening`.
- Property names are the same as the timeline track properties (`var:score` counts a number up smoothly).

## 4. Events
`onTimelineEnd(timeline, current)` (ONCE timeline finished), `onTimelineMarker(timeline, marker, current)`, `onTweenEnd(current, property)`.

## Pitfalls
- A property animated by a playing timeline is overwritten every frame: stop the timeline before moving the element yourself.
- Physical bodies whose position / rotation is animated become KINEMATIC while it plays (they follow the keys exactly and still push what they touch) and get their body type back at the end: do not set their velocity meanwhile.
- Timeline names: letters, digits, `_`. Renaming a timeline does not rename it inside blocks.
