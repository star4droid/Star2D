# Elements: types, properties, meaning

An element is one object in a scene. You create and change them with the `elements` tool (batch, units = editor pixels):

```json
{"ops":[
 {"op":"add","type":"BOX","name":"player","props":{"x":100,"y":120,"width":64,"height":96,"image":"/hero.png","type":"DYNAMIC","Fixed Rotation":"true","restitution":0,"friction":0.2}},
 {"op":"add","type":"BOX","name":"ground","props":{"x":0,"y":0,"width":1280,"height":60,"type":"STATIC","Tint":"#5D4037"}},
 {"op":"set","name":"player","props":{"x":300}},
 {"op":"rename","name":"ground","new_name":"floor"},
 {"op":"duplicate","name":"floor","new_name":"floor2","props":{"x":1280}},
 {"op":"order","name":"player","z":5},
 {"op":"delete","name":"floor2"}
]}
```

Rules of the tool: property names are matched case-insensitively but spelled EXACTLY as in the tables below (`Fixed Rotation`, `Gravity Scale`, `Collider Width`...). Values may be JSON numbers/booleans or strings. Unknown properties are reported as errors. `TYPE` (BOX, CIRCLE...) and `Points` cannot be set. `type` and `Shape` are applied first. To add a light use `"type":"LIGHT"` with `"Light Type":"point|cone|directional"` in props (2D scenes; 3D scenes: LIGHT3D, see below). Use `get_elements` or `scene_dump` to read back what you set.

## Position, size, rotation (all element types)

- `x`, `y`: the **bottom-left corner** of the element in logic pixels (origin bottom-left, y up). For a CIRCLE it is the corner of its bounding square (`2*radius` wide). The element's center is `x + width/2`, `y + height/2`.
- `width`, `height` (CIRCLE: `radius`): drawn size in pixels. Setting `width`/`height`/`radius` through the tool also sets `Collider Width`/`Collider Height`/`Collider Radius`; if you set a collider size yourself, do it AFTER the size and verify with `get_elements`.
- `rotation`: degrees, positive = clockwise. At runtime the engine reports angles counter-clockwise (the `angle of ___` reporter is counter-clockwise degrees).
- `z`: draw order, higher = in front (all elements, also UI ones, are sorted by `z`). Equal z: undefined order, give distinct values. MAP is always z 0.
- `Visible`: `false` hides it (it also stops receiving touches). It still exists and still collides unless `Active` is false.
- `name`: unique, letters/digits/`_`. It becomes a Java field of type `PlayerItem` in the scene, so use it directly in blocks (`player`, `coin1`). Avoid Java keywords and engine names (`world`, `steps`, `playing`, `project`, `finished`).
- `Script`: which event folder the element uses (default = its own name). Several elements with the same `Script` share the same events. Do not change it unless you want that.
- `parent`: runtime hierarchy only (`getChild("name")` in Java). It does not move the child with the parent.
- `lock`: only locks dragging in the editor. `Version`: leave `1.0` (an empty value breaks the build).

## type (the most important property of physical elements)

| `type` | Meaning |
|---|---|
| `DYNAMIC` | Moves, falls, collides, is pushed by others. Player, enemies, bullets, balls. |
| `STATIC` | Never moves. Ground, walls, platforms. |
| `KINAMATIC` (sic, spelled like this) | Moves only by the velocity you give it, is not pushed. Moving platforms. Any value starting with `K` works. |
| `UI` | No physics. Drawn on a screen-fixed layer in pixels, ignores the camera. HUD, joystick, buttons, fixed background. |

TEXT, PROGRESS, JOYSTICK, PARTICLE: `type: UI` = screen layer (default for TEXT/PROGRESS/JOYSTICK/PARTICLE); any other value puts them in the physics world (they move with the camera, size in world scale). BOX/CIRCLE/CUSTOM with `type: UI` have NO body, so they never collide and body blocks (`goTo`, `x of ___`...) on them crash. Use UI boxes only for decoration and buttons.

## BOX and CIRCLE (and CUSTOM)

- `image`: path relative to `images/`, with a leading slash: `/hero.png` (subfolders: `/tiles/grass.png`). Do NOT write `images/hero.png` or `/images/hero.png`. If the file does not exist, the Star2D logo is drawn instead (a sure sign of a wrong path). Images are drawn stretched to `width x height` with nearest-neighbor filtering (crisp pixels).
- `Pressed Image`, `Touch Start Image`, `Touch End Image` (BOX only, all empty by default, same path format as `image`, `.9.png` works): `Pressed Image` is shown while the finger is down on the element and restored on release / drag-out / cancel; `Touch Start Image` / `Touch End Image` are applied at touch start / end and stay until something changes the image. They do not replace `onClick`/`onTouchStart`/`onTouchEnd` events, which still fire. Ignored while an animation plays; `tileX/tileY` only apply to `image`.
- Reset: `image` (and `Pad Image`, `Button Image`, the touch images) set to `""` or `"reset"` go back to the default picture; or in a `set` op: `"reset":["image","Pressed Image"]` puts any listed property back to its default.
- To get a plain colored rectangle: create a white PNG with `create_image` and color it with `Tint`.
- `Tint`: color multiplied with the image (`#RRGGBB` or `#RRGGBBAA`; the alpha gives transparency). White = unchanged.
- `tileX`, `tileY`: repeat the image N times across the element instead of stretching (backgrounds, ground).
- `Scale X`, `Scale Y`: scale of the drawn picture only (the collider does not change). Negative `Scale X` mirrors the sprite.
- `density`: mass per area (default 1). `friction`: 0 = ice, ~0.5 grip; default 0. `restitution`: bounciness 0..1; **default is 0.5 (bouncy!)**, set 0 for characters and ground you do not want to bounce. See `physics`.
- `isSensor`: true = detects overlaps (fires collision events and `___ touches ___`) but does not push or block. Use for coins, triggers, goals.
- `Fixed Rotation`: true = never rotates from collisions (characters).
- `Gravity Scale`: multiplier of gravity for this element (0 = floats).
- `Bullet`: true = precise collision for very fast small bodies (prevents tunneling).
- `Active`: false = the body is switched off (no movement, no collisions) but is still drawn.
- `Collider Width/Height` (CIRCLE: `Collider Radius`): physical size in px, independent of the drawn size. A smaller collider than the picture makes forgiving hit boxes.
- `ColliderX`, `ColliderY`: shifts the collider relative to the picture. Positive `ColliderX` moves the collider to the LEFT, positive `ColliderY` DOWN (the engine subtracts them).
- `Collision`: text like `(coin)(spike)`. If another element's `Script`/parent name is contained in this text, the two do NOT collide at all (no push, no events). Rarely needed; use `isSensor` for "overlap without push".
- `Shape`: `Box`, `Circle` or `Custom`; set by the element type (leave it).
- CUSTOM: `Points` is a list `x,y-x,y-...` with each value 0..1 inside the element box, y=0 at the TOP; it builds a hollow outline (use STATIC for terrain shapes). The tool cannot set `Points`; use CUSTOM only if the default square is acceptable, otherwise combine rotated BOXes.

## TEXT

`Text`: the string. `Text Color`: `#RRGGBB(AA)`. `Font Scale`: multiplier of the built-in font (ASCII only, small: use 3 to 6 on a 1280x720 screen). `font`: optional `.s2df` font file for other alphabets or a styled look (make it with the `fonts` tool, `read_docs fonts`; with a custom font keep `Font Scale` near 1 and set the size in the font). `width/height` is the text box; text wraps, is vertically centered and left-aligned unless `Text Align` is CENTER or RIGHT (right is the natural side of Arabic). `Auto Size` (default true): the text size follows the box (grows and shrinks with width/height, Arabic and English), the text never leaves the box and `Font Scale` is ignored. Turn it off to use `Font Scale`. A text written `@key` is a translation (see `features`). Change it at runtime with the block `setText` (or `showText`). Default `type` is UI.

## PROGRESS

A bar. `Max`, `Progress` (current value), `Progress Color`, `Background Color`, `width`, `height`. Change at runtime with `setProgress` and `setMax` (integers). Default `type` is UI.

## JOYSTICK

On-screen stick. Default size is only 50: set `width` and `height` to about 300. `Pad Image`, `Button Image`: optional pictures. Read it with the reporters `joystick x ___` / `joystick y ___` (-1..1, y up) or move things with the blocks `moveWithJoystick` / `walkWithJoystick` (see `ui-input`). Default `type` is UI. Place it near a bottom corner.

## PARTICLE

`file`: a libGDX particle `.p` file under `files/` (e.g. `/fire.p`); empty = default smoke. `Loop`: restart when finished. `Duration`: -1 = forever. `Scale X/Y`. Needs a `.p` file you can create with `files` write only if you know the format; usually skip.

## CAMERA

The CAMERA element is not applied by this engine version (its settings have no effect). Control the view with the Camera blocks or Java instead (see `ui-input`).

## MAP

`Map`: a Tiled `.tmx` file inside `images/` (with its tileset image). Drawn only: it has no collision, add invisible STATIC boxes for the solids.

## LIGHT (point, cone, directional)

Needs a dark `Ambient Light` in the scene config to be seen. `Distance` (px), `rays` (quality), `color`, `Soft`/`Softness Length`, `Static Light`, `X Ray`, `attach To` (element name to follow), `Offset X/Y`, `Cone Degree` (cone only), `x`,`y` position.

## 3D elements: OBJ3D, LIGHT3D, CAMERA3D (3D scenes only) and CAMERAVIEW

Only in a scene made with `scenes create {type:'3D'}` (adding one to a 2D scene is refused). Units are **meters**, y UP, `z` = depth (may be negative), `Rotation X/Y/Z` degrees, `Scale X/Y/Z` = size of primitives in m; `width`/`height`/`rotation`/`type`/`Pivot` are not used by them, and their `z` is not a draw order (no z policy, no `order` op).
```json
{"ops":[
 {"op":"add","type":"PLANE","name":"ground","props":{"Scale X":40,"Scale Z":40,"Color":"#6FA35A","Body":"STATIC"}},
 {"op":"add","type":"CAPSULE","name":"player","props":{"x":0,"y":1,"z":0,"Scale X":0.6,"Scale Y":0.9,"Scale Z":0.6,"Body":"DYNAMIC","Lock Rotation X":true,"Lock Rotation Z":true}},
 {"op":"add","type":"OBJ3D","name":"car","props":{"Mesh":"MODEL","Model":"/car.glb","x":4,"Body":"DYNAMIC","Collider":"BOX"}},
 {"op":"add","type":"LIGHT3D","name":"lamp","props":{"Light Kind":"POINT","x":0,"y":3,"z":-2,"Range":8,"Color":"#FFD27F"}},
 {"op":"set","name":"Camera","props":{"Follow":"player","Follow Mode":"THIRD_PERSON","Follow Distance":6}},
 {"op":"add","type":"CAMERA3D","name":"topCam","props":{"y":30,"Rotation X":-90,"Follow":"player","Follow Mode":"TOP_DOWN"}},
 {"op":"add","type":"CAMERAVIEW","name":"minimap","props":{"Camera":"topCam","width":240,"height":240,"x":20,"y":20}}
]}
```
The type may be a shape name (CUBE, SPHERE, CYLINDER, CAPSULE, CONE, PLANE, TORUS, RAMP, STAIRS, PYRAMID, HEMISPHERE, TUBE, WEDGE, QUAD, DISC, EMPTY, MODEL, TERRAIN, WATER) = OBJ3D with that `Mesh`; `LIGHT`/`SPOT`/`SUN` and `CAMERA` in a 3D scene = LIGHT3D / CAMERA3D. CAMERAVIEW is a 2D element (px, both scene kinds). Meaning of every 3D property, scene settings, physics, models, terrain, water, shaders: read_docs `scenes`, section `3D scenes`.

## All properties, defaults and allowed values (generated from the app)

Kinds: `float` number, `boolean` true/false, `string` text, `color` hex, `image` path under images/, `File` path, `spinner` fixed choices, `body` element name(s), `script` script name, `static`/`points` read-only.

{{ELEMENT_TABLES}}
