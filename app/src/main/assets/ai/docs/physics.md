# Physics (Box2D): bodies, units, collisions, typical numbers

This page is about 2D scenes (Box2D, pixels). 3D scenes use Bullet in meters with y up (`Body`, `Collider`, `Mass`, gravity = scene setting `Gravity Y`): read `scenes`, section `3D scenes` > `Physics`.

## Signs and formulas (verified in the engine code; the `physics_info` tool does the arithmetic for you)

- **Y is UP.** `setVelocityY` and `jump` with a POSITIVE number move the element UP, negative moves it DOWN. `setVelocityX`: positive = right, negative = left. `setWorldGravity` strength: a positive number pulls DOWN (never pass a negative one).
- The velocity blocks take px/s and multiply by `WORLD_SCALE` (0.25) for Box2D; `setWorldGravity` also sets 1 physics step per frame. So with that block: **gravity g = strength px/s2, jump height = v^2 / (2g), time to the top = v / g, flat air time = 2v / g, horizontal reach of a jump = move_speed * air time.** Real numbers are within a few percent.
- Example, gravity 800, jump power 450: height 126 px, air time 1.1 s, with walk speed 250 the reach is 280 px. Wanted a 200 px jump? power = sqrt(2 * 800 * 200) = 566 works too, or LOWER the gravity to 500 to keep the jump slower (450^2 / (2*500) = 202 px).
- Without `setWorldGravity` the engine runs 6 steps per frame: everything is 6x too fast and these numbers are wrong. Check with `physics_info`: it warns when the scene has no `setWorldGravity`.
- Design platforms with margin: vertical step <= 70% of the jump height, gap <= 70% of the reach. Ask `physics_info` with `jumps:[{"gap":220,"rise":90}]` to see if each jump works BEFORE building the level.
- The frame decides the layout: a PORTRAIT 720 x 1280 scene is narrow (about 3 s to walk across at 250 px/s) and tall (many platforms stacked vertically); a landscape 1280 x 720 scene is the opposite. Read `status.frame` and place things inside it; `elements` warns when something is outside.

## The one unit rule

- **Everything you type in `elements`, and every "px" block field, is in editor pixels** of the scene's logic screen.
- The engine simulates in **world units = pixels * 0.25** (`WORLD_SCALE = 0.25`). You only meet world units in: the raw Box2D blocks (`setLinearVelocity`, `setTransform`, `setX`, `setY`, `applyForce*`, `applyLinearImpulse*`, `setCameraXY`/`setCameraX`/`setCameraY`/`setCameraCenter`/`setCameraOffset`), the reporters `___ .getX()`, `___ .getY()`, `width of ___`, `height of ___`, and the numbers returned by `game_state` for world elements (x, y, width, height are world units there; multiply by 4 for pixels; `vx`, `vy` are raw Box2D velocities).
- Pixel-based blocks (use these): `moveBy`, `goTo`, `goToBody`, `setVelocityX`, `setVelocityY`, `jump`, `moveToward`, `moveWithJoystick`, `walkWithJoystick`, `rotateBy`, `spawnCopy`, and the reporters `x of ___`, `y of ___` (the element's CENTER), `speed x of ___`, `speed y of ___`, `distance ___ to ___`, `touch x`, `touch y`, `camera x`, `camera y`.
- y is up. An element at `y: 0` sits on the bottom edge of the screen.

## Step 1 of every physical scene: set gravity with `setWorldGravity`

By default the engine runs the physics 6 times per frame (6x real-time speed), so speeds in px/s would be about 6x too fast and the default gravity is about 1400 px/s2. The block **`setWorldGravity`** sets "steps per frame = 1" and gravity to `strength` px/s2. After it, all px/s numbers in blocks are true pixels per real second. Put it FIRST in `OnCreate` of every scene that has dynamic bodies:

```json
{"block":"setWorldGravity","fields":{"strength":800}}
```

Platformer/side view: strength 700 to 1000 (see the speed limit below). Top-down or space game: strength 0 (nothing falls). Floaty moon: 500. Do not use the `setGravity` block unless you need a sideways gravity (it takes raw world units like `0f`, `-9.8f`, and keeps the 6x steps).

## Typical numbers (logic screen 1280 x 720, player about 64 x 96 px)

| What | Value |
|---|---|
| Gravity | 800 px/s2 |
| Walk / run speed | 250 to 350 px/s (`setVelocityX`) |
| Jump | `jump` power 400 to 450 px/s (height = power^2 / (2*gravity): 450 gives 126 px with gravity 800) |
| Bullet | 400 to 450 px/s |
| Enemy walker | 80 to 150 px/s |
| Top-down player | 300 px/s |
| Falling/air time of a jump | 2 * power / gravity seconds |

**No speed limit**: Box2D moves a body at most 2 world units (8 px) per physics step, so the engine splits a frame's step into smaller ones automatically when a body is faster: any speed or jump power works. Very fast small bodies can still pass through thin walls (tunneling): set `Bullet` true on them (CCD) and make walls at least 20 px thick. Always confirm real speeds with `game_state` (`vx`, `vy` are world units per second: px/s = value * 4).

## Body types (`type` property)

- `DYNAMIC`: simulated. Player, enemies, bullets, balls, crates.
- `STATIC`: fixed forever. Ground, walls, platforms. Cheap; use for everything that never moves.
- `KINAMATIC`: moves only because YOU set its velocity (`setVelocityX/Y`, `setLinearVelocity`), not pushed by others, not affected by gravity. Moving platforms, elevators, rotating bars (`setAngularVelocity`).
- `UI`: no body at all.

## Material properties

- `density` (default 1): heavier bodies push lighter ones. Make crates 0.5 to 2, a pushed ball 1.
- `friction` (default 0 = ice): 0.2 to 0.6 for ground and crates. Characters often keep 0 so they do not stick to walls; friction is mixed between the two touching bodies.
- `restitution` (default **0.5**, bouncy): 0 for player, enemies, ground, walls; 0.8 to 1 for balls and breakout balls.
- `Fixed Rotation: true` for characters so they stay upright.
- `Gravity Scale`: 0 for things that must float (pickups that move, bullets flying straight). The block `setGravityScale` takes raw Java (`0` or `0.5f`).
- Damping: block `setLinearDamping` (value like 2 to 8) slows bodies down like drag; good for top-down friction.

## Moving things (prefer velocities, avoid forces)

- Run: `setVelocityX` (keeps vertical speed). Stop: `setVelocityX` with 0. Jump: `jump` (sets vertical speed). Top-down: `setVelocityX` + `setVelocityY`, or `moveWithJoystick`.
- Chase: `moveToward(body, target, speed)` sets the velocity toward another element.
- Teleport: `goTo(body, x, y)` (pixel CENTER position) or `moveBy`. Teleporting keeps the velocity: follow with `stopMoving` if needed.
- `rotateBy(body, degrees)` rotates instantly.
- Forces and impulses (`applyForce*`, `applyLinearImpulse*`) are in raw Box2D units and depend on mass: avoid them. If you must, start with small numbers and check with `game_state`.
- Do not move a DYNAMIC body every frame with `goTo` while it should collide (it will tunnel). Use velocities.

## Collisions

Two bodies collide (push each other) unless one of them is a sensor or the `Collision` filter excludes the pair.

- **Sensor (`isSensor: true`)**: overlap is detected, nothing is pushed or blocked. Ideal for coins, goals, damage zones, pickups, checkpoints. Sensors still fire collision events and count for the reporter `___ touches ___`. A sensor DYNAMIC body still falls with gravity: use `STATIC` sensors, or `Gravity Scale: 0`.
- **`Collision` property**: a text of names, e.g. `(enemy)(coin)`. Pairs where either body's `Collision` text contains the other's name do not collide AND fire no events. Use it for "player passes through enemy bullets" style filtering, not for pickups.
- **Events**: use the SCENE event `onCollisionStart` (variables `body1`, `body2`, both `PlayerItem`) and `onCollisionEnd`. Test who collided with the reporter `___ is named ___` (args: an element variable, the plain name) or with a raw Java condition such as `body2.getName().startsWith("coin")` in an `ifThen`. The element events `onBodyCollided`/`onBodyCollideEnd` exist, but the engine currently runs the first body's handler twice and never the second body's own handler, so they are unreliable: prefer the scene events (see `events`).
- `___ touches ___` (reporter) / `ifCollision` (block): true while the two named elements are in contact.
- Never create, destroy or deactivate bodies inside a collision event (the physics world is locked and a new body may be null). To remove something that was touched use `hide` plus the helper `destroyLater(item, 0.05)` (defined in `blocks`; it waits a moment on a timer and is safe to call twice). `hide` alone is safe, but a hidden sensor still triggers. The built-in `destroy`/`destroyAfter` blocks are NOT safe to run twice on the same element (a second destroy can crash the game).

## Grounded check for jumping

Option A (simple): jump only when the vertical speed is about zero: `ifThen` with `{"r":"abs ___","args":[{"r":"speed y of ___","args":["player"]}]} < 20`. Works while standing; allows a jump at the top of an arc.
Option B (accurate): count ground contacts with a variable `ground` (int): in `onCollisionStart` add 1 when the pair is player and an element whose name starts with `ground`; subtract 1 in `onCollisionEnd`. Jump only if `ground > 0`. See `blocks-examples`.

## Clones and bullets

`spawnCopy(body, var, newName, x, y)` clones an element at a pixel position (center) while the game runs. Clone from the original template element (keep a hidden or off-screen template, or the original itself), give each clone a unique name (join a counter), and clean up with `destroyLater`. The clone keeps the template's properties and events. A clone created inside a collision event must be avoided.

## Quick recipes of physics properties

- Player: `type DYNAMIC, Fixed Rotation true, restitution 0, friction 0`.
- Ground/wall: `type STATIC, restitution 0, friction 0.4`.
- Coin: `type STATIC, isSensor true`.
- Enemy walker: `type DYNAMIC, Fixed Rotation true, restitution 0`, velocity set each time it turns.
- Bullet: `type DYNAMIC, Gravity Scale 0, Bullet true, isSensor true` (or normal) and `destroyLater` 2 seconds.
- Ball (breakout/pong): `CIRCLE, DYNAMIC, restitution 1, friction 0, Gravity Scale 0`, paddle `KINAMATIC` or `STATIC` moved by `goTo` / velocity, walls `STATIC`.

## Platformer checklist (any orientation: use the numbers of `status.frame`, W wide x H tall)

1. `scenes create` with `orientation` (or read `status.frame` of an existing scene). `physics_info` once to confirm jump height.
2. Ground: BOX `STATIC`, x 0, y 0, width W, height 60, restitution 0, friction 0.4, z 10. Its top edge is y 60. Platforms: width 150 to 250, height 30, y at most 70% of the jump height above the one before, z 10.
3. Player: BOX `DYNAMIC`, Fixed Rotation true, restitution 0, friction 0, about 50 x 70, `x` about 15% of W, `y` = the top of the ground (60) so it starts standing, z 20. Do not start it inside a platform or the ground (it gets pushed out sideways).
4. Background: BOX at x 0, y 0, width W, height H, z 0 (it must be the lowest). The `elements` tool sets these z values for you when you do not pass z; UI boxes 100, TEXT 110, JOYSTICK 120.
5. OnCreate: `setWorldGravity` 800 FIRST. Move: `walkWithJoystick` (body, joystick, speed 250 to 300) in `OnStep`, or on touch buttons `setVelocityX` with +250 (right) / -250 (left) and 0 on release. Jump: `jump` power 400 to 450 (positive = up) only when standing (see "Grounded check").
6. Camera: for a level wider/taller than the frame add a CAMERA element and follow the player in `OnStep` (`cameraFollow`); for a one-screen level nothing is needed.
7. Verify: `layout_check`, `run_game`, `game_input` (tap the jump button), `game_state` (vy must be POSITIVE right after the jump, vx about 0 when idle), `screenshot`.

## When it goes wrong (symptoms the user actually reported)

- **The whole scene is landscape but the game is vertical** (or the reverse): the frame in `status.frame` is the truth. Fix with `scenes configure {"or":"portrait"}` (and `{"swap_size":true}` if the stored sizes look reversed). Then re-check every x/y against the real frame; `elements` and `layout_check` report what is outside.
- **The background covers the elements**: higher z is drawn on top and equal z is drawn in creation order, so a background created last covers everything. Background z 0, everything else 10 or more. `layout_check` reports `background_covers` / `background_ties`.
- **The player slides sideways at the start and disappears**: usual causes, check them in this order with `game_state` (vx, vy) and `scene_dump`: (1) the player spawns overlapping another body or outside the frame (the physics pushes it out sideways); (2) no ground under it, or the ground is not as wide as the frame, so it falls out of view; (3) a velocity block with the wrong sign or a wrong body/joystick name keeps setting vx every frame; (4) no `setWorldGravity` (6x speed); (5) Fixed Rotation is off and friction/restitution are the defaults (restitution 0.5 bounces, friction 0 never stops), so the box tips and rolls. Set Fixed Rotation true, restitution 0 on player and ground.
- **The player jumps DOWN or barely**: you used a negative power, or the power is too small for the gravity. The jump must be a POSITIVE number; use `physics_info` {"jump_height":N} for the number.
- **Touch buttons do nothing**: put the event on the BOX, not on the TEXT over it (see `ui-input`).
