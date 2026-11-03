# Movement, AI and vehicle behaviors

Built-in behaviors in `app/src/main/java/com/star4droid/template/behaviors/lib/` (see `docs/BEHAVIORS.md` for how behaviors work).
All of them follow the same conventions:

* Units are editor **pixels**, `y` up, origin bottom-left. Speeds are px/s (no upper limit; very fast physical bodies may need `Bullet` true / CCD against tunneling), angles are degrees,
  counter-clockwise positive, `0` = right, `90` = up. World = px x 0.25.
* Every public field is a parameter shown in the inspector (`@Param` with description, range, unit). Every behavior has blocks
  (`@Block`, English + Arabic) that appear only when the scene uses the behavior, and events (`@BehaviorInfo(events=...)`).
* Physics settings (`@Sets`) and runtime changes (gravity off, fixed rotation, sensor...) are applied **when the game starts** and are
  undone when the behavior is disabled; the saved element is never rewritten.
* No per-frame allocations, no `android.*`, Java 7 syntax.
* A physical element is moved with **velocities** (so walls stop it); a UI element (no body) is moved with `setCenter`.

## The library

| Id | Name | Icon | Category | For | Events | Short description |
|---|---|---|---|---|---|---|
| `TopDownPlayer` | Top-down player | `walk` | Movement | UI + physical | `onStartMoving`, `onStopMoving` | Joystick, 4 buttons or keyboard; ANY/EIGHT/FOUR directions; acceleration + friction; turn toward the movement |
| `SpaceShip` | Space ship | `spaceship` | Vehicles | UI + physical | `onBoostStart`, `onBoostEnd` | Turn + thrust in the facing direction, drag, speed limit, boost; joystick (ROTATE or POINT), buttons, keys, tilt |
| `Car` | Car | `car` | Vehicles | physical | `onDriftStart`, `onDriftEnd` | TOP_DOWN bicycle-model car (steering angle, wheelbase, grip, handbrake drift) or SIDE_SCROLLER (drives wheel elements) |
| `Follow` | Follow | `crosshairs-gps` | Movement | UI + physical | `onArrive`, `onLeave` | Stays near a target with offset, smoothing, speed limit, stop distance |
| `Patrol` | Patrol | `swap-horizontal` | AI | UI + physical | `onTurn`, `onReachWaypoint(int index)` | Back and forth by distance, or through waypoint elements; waits, flips, turns at walls/ledges |
| `Chase` | Chase | `run` | AI | UI + physical | `onSeeTarget`, `onLoseTarget`, `onReachTarget`, `onResumeChase` | Runs at a target in sight range, gives up beyond a range, optional line of sight, optional return home |
| `MovingPlatform` | Moving platform | `elevator-passenger` | Movement | physical | `onReachA`, `onReachB` | Kinematic platform between two ends (range or point elements), pause, easing, carries riders |
| `JumpPad` | Jump pad | `rocket-launch` | Movement | physical | `onLaunch(PlayerItem who)` | Launches what touches it in a direction; target list / name filter; optional sensor |
| `Bounce` | Bounce | `tennis-ball` | Movement | UI + physical | `onBounce(PlayerItem other)`, `onBounceEdge(String edge)` | Constant-speed ball (Pong/Breakout), min angle, speed gain, reflects off screen edges |
| `WrapScreen` | Wrap around screen | `repeat` | Movement | UI + physical | `onWrap` | Leaves one side, appears on the other (view / scene / custom rectangle) |
| `LimitToScreen` | Limit to screen | `selection` | Movement | UI + physical | `onHitEdge(String edge)` | Keeps the element inside the view / scene / rectangle with a margin, optional bounce |
| `LookAtTarget` | Look at target | `eye-target` | Movement | UI + physical | `onAligned` | Turns toward an element or the finger with a turn speed (turrets, eyes) |
| `Orbit` | Orbit | `orbit` | Movement | UI + physical | `onLap` | Circles around an element or a point; oval, spiral, face the path |
| `TapToMove` | Tap to move | `cursor-pointer` | Movement | UI + physical | `onArrive`, `onTapPoint(float x, float y)` | TAP (go to the tapped point), HOLD (follow the finger), DRAG_ITEM (grab and drag) |
| `TiltMove` | Tilt move | `cellphone` | Movement | UI + physical | - | Moves by tilting the phone (accelerometer): VELOCITY or ROLL; arrow keys simulate on a computer |

## Exclusivity (what happens with two controllers on one element)

`TopDownPlayer`, `SpaceShip`, `Car`, `TapToMove` and `TiltMove` (and `PlatformerPlayer`, `JoystickMovement` from the base library) are
`@Exclusive("movement")`: the editor asks before adding a second one, and at runtime `item.setBehaviorEnabled("Car", true)` switches the
others of the group off. Two of them running together would overwrite each other's velocity every frame.

`Follow`, `Patrol`, `Chase`, `MovingPlatform`, `Orbit`, `Bounce`, `JumpPad`, `WrapScreen`, `LimitToScreen` and `LookAtTarget` have no group:

* `Patrol` + `Chase` on the same element is the intended guard: `Patrol` pauses while `Chase.isChasing()` (option `yieldToChase`), `Chase` does
  nothing while it is idle.
* `WrapScreen` / `LimitToScreen` / `LookAtTarget` only correct the position or rotation, so they combine with any controller. Do not put both
  `WrapScreen` and `LimitToScreen` on one element for the same axis.
* `Follow`, `Orbit`, `MovingPlatform`, `Bounce` drive the velocity or position themselves: do not combine them with a controller.

## Physics requirements and runtime changes

| Behavior | Needs | Changed at game start (restored on disable) |
|---|---|---|
| `TopDownPlayer` | gravity 0 in the scene (`setWorldGravity(0, 0)`) | `@Sets` gravityScale=0, fixedRotation=true |
| `SpaceShip` | gravity 0 | `@Sets` gravityScale=0, fixedRotation=true |
| `Car` | physical DYNAMIC element; TOP_DOWN: gravity 0; SIDE_SCROLLER: gravity + wheels joined with joints | TOP_DOWN: gravityScale=0 and fixedRotation=true (restored) |
| `Follow` | - | gravityScale=0 only when `followY` is on |
| `Patrol` | physical: DYNAMIC with gravity for ground enemies | gravityScale=0 when `flying`, in WAYPOINTS mode or on a VERTICAL route |
| `Chase` | - | gravityScale=0 when `horizontalOnly` is off; fixedRotation when `faceTarget` |
| `MovingPlatform` | a physical element | `@Sets` type=KINEMATIC, gravityScale=0, fixedRotation=true |
| `JumpPad` | a physical element (STATIC is fine) | the fixtures become sensors when `sensor` is on |
| `Bounce` | - | `@Sets` gravityScale=0, linearDamping=0; fixture restitution = `bounciness`, friction = 0 |
| `Orbit` | - | `@Sets` gravityScale=0, fixedRotation=true |
| `TapToMove` | - | gravityScale=0 unless `horizontalOnly` (or DRAG_ITEM) |
| `TiltMove` | - | gravityScale=0 when `vertical` is on |
| `LookAtTarget` | - | `@Sets` fixedRotation=true |
| `WrapScreen`, `LimitToScreen` | - | nothing |

## Notes per behavior

**TopDownPlayer.** Inputs add up: joystick + 4 buttons + keyboard. `acceleration` / `friction` 0 mean instant. `FOUR` picks the dominant
axis, `EIGHT` snaps to 45 degrees. With `rotateToDirection` the element turns toward the input (`faceOffset` = the angle the image faces when
not rotated, `turnSpeed` 0 = instant). Blocks: set move speed / acceleration / friction / joystick, stop moving, is moving, current speed.

**SpaceShip.** `noseAngle` is the direction the picture's nose points at rotation 0 (default 90 = up; use 0 for ships drawn facing right).
Steering `ROTATE`: joystick left/right turns and up thrusts; `POINT`: the ship turns toward the stick and thrusts while pushed. `drag` 0
keeps drifting forever. Boost multiplies thrust and speed limit for `boostTime` seconds, then `boostCooldown` (keys: shift, button or the
`boost now` block). `useTilt` turns the ship by tilting the phone.

**Car, TOP_DOWN.** Bicycle model: the front wheels turn by the steering angle (limited by `maxSteerAngle`, reduced by `steerReduction` at
speed); the yaw rate is `speed / wheelBase x tan(steer)`. `frontAxle` / `rearAxle` are the distances from the element's center to the axles
(0 and 0 = 35% of the longer side each), the car swings around the rear axle. `grip` is how fast sideways sliding is removed; the handbrake
(button or space) uses `handbrakeGrip` and slows the car a little, so the rear slides (drift). Gas and brake are on the joystick Y, the
buttons, or W/S; the brake becomes reverse when the car has stopped. The wheels are not separate elements here: read `steering angle` (block)
to turn wheel pictures yourself. `drive input` (block) lets an AI drive the car: gas -1..1, steering -1 (right)..1 (left).

**Car, SIDE_SCROLLER.** Build the car in the editor: a chassis element (this one) plus wheel elements (physical circles with friction)
joined to it with revolute / wheel joints. Add the wheels to the `wheels` list. Right / gas spins the wheels forward (speed from `maxSpeed`
and the wheel radius, ramped by `wheelAcceleration`), left drives backward at `reverseSpeed`, the brake button stops them and the handbrake
locks them. In the air (no wheel touching for 0.2 s) left/right rotates the chassis (`airSpin`). Limits: no suspension tuning, no motor
torque limit (the wheel's friction on the ground is the limit), joints are made by you. With an empty `wheels` list the chassis itself is pushed
(a sled).

**Follow.** `stiffness` is the 1/s catch-up factor (speed = distance x stiffness, capped by `maxSpeed`), 0 = glued. Physical followers use
velocity, UI followers `setCenter`. `mirrorOffset` flips the x offset when the target's image is mirrored.

**Patrol.** DISTANCE mode walks `rangeBack` / `rangeForward` around the position the element had at the start (axis HORIZONTAL or
VERTICAL). It turns when it passes the end, when a static wall is 3 px ahead (`turnAtWalls`) or when there is no ground within
`ledgeDepth` below its front (`turnAtLedges`, ray cast). WAYPOINTS mode walks to the center of each element of `waypoints` (they may be
invisible and may move); ping-pong or `loopWaypoints`. `flipOnTurn` mirrors the image (`artFacesLeft` if it is drawn facing left).

**Chase.** Starts when the target is closer than `sightRange`, gives up beyond `giveUpRange`. `needLineOfSight` ray casts to the target: only
fixed bodies (static / kinematic) block the view. `horizontalOnly` (default) keeps gravity for ground enemies; off = flies straight at the
target. `stopDistance` raises `onReachTarget` (a melee range; `onResumeChase` when the target moves away again: play the attack animation in one and the run animation in the other). Contact damage (Damage On Touch) uses the collider boxes, not the picture size. `returnHome` walks back to the start position.

**MovingPlatform.** `distance` moves along the axis from the start; setting `pointB` (and optionally `pointA`) uses the position of
those elements instead. It is driven with velocities so things on top are pushed; `carryRiders` also moves bodies standing on it sideways
(a platformer sets its own x velocity every frame and would otherwise slide off). The carry is a heuristic: a rider that already travels
with the platform (friction) is not moved twice. Block `platform go` stops or starts it (levers).

**JumpPad.** Sets (or `additive` adds) the velocity of the body that touches it: `power` px/s at `angle`. `onlyFromFront` ignores bodies
that touch from behind. `targets` list and `nameFilter` (substring of the element name, for example `Player`) choose who is launched.
`cooldown` avoids double launches. There is no tag system, so the name filter is the "tag".

**Bounce.** Keeps the speed constant (`keepSpeed`), never lets the path get closer than `minAngle` to horizontal / vertical (stops endless
ping-pong), optionally speeds up by `speedGain` at each bounce up to `maxSpeed`. Physical balls bounce off walls and other bodies through
Box2D restitution (`bounciness`); `bounceOffView` also bounces off the camera view. A UI element bounces inside the screen.

**WrapScreen / LimitToScreen.** `area`: VIEW = what the camera shows now (the screen for UI elements), SCENE = the first screen (0,0 to the
scene size), CUSTOM = `customLeft/Bottom/Right/Top`. `LimitToScreen.keepFullyInside` keeps the whole element inside; physical elements lose
(or reflect, `bounciness`) the speed that pushes them out.

**LookAtTarget.** Source TARGET (an element) or FINGER (the touched point, `keepLastTouch` keeps facing it after lifting the finger).
`faceOffset` = angle the image faces at rotation 0. The element must not be driven by a rotating physics body (fixed rotation is set).

**Orbit.** Around `center` (the starting distance is kept when `radius` is 0) or, without a center element, around a point chosen so that
the start position lies on the circle. `ovalRatio`, `spiral` (px/s) and `faceMovement` shape the path. Physical elements follow the path
with a velocity (`distance / dt`): a large radius with a high speed gives a very fast body, so prefer kinematic bodies or UI for fast orbits (or `Bullet` true).

**TapToMove.** `ignoreTouchesOn` lists buttons / joysticks whose touches must not move the element. TAP shows an optional `marker`
element at the tapped point and hides it on arrival. `horizontalOnly` keeps gravity and only runs left / right. DRAG_ITEM needs the
element itself to receive touches (it uses its touch-start event) and keeps the grab offset.

**TiltMove.** Reads the accelerometer in screen axes (x > 0 when the right edge is lower, y > 0 when the top edge is lower; a ball rolls to
the lower side). `orientation` AUTO follows `Gdx.input.getRotation()`; if the directions are wrong on a device, set it by hand
(`NORMAL`, `ROTATED_90`, `UPSIDE_DOWN`, `ROTATED_270`) or use `invertX` / `invertY`. VELOCITY: the tilt sets the speed; ROLL: the tilt is an
acceleration, the element keeps rolling (`damping` slows it). The arrow keys simulate the tilt when there is no accelerometer.
`TiltMove.read(orientation, out)` is shared with `SpaceShip` and `Car` (tilt steering).

## Tests

* `python3 tools/test_lib_movement.py` (needs `bash gradlew :template:compileDebugJavaWithJavac` once): annotation completeness for every
  class, text scanner == reflection, plus a headless Box2D simulation with a fake stage (motion direction, y up, no NaN, limits, walls,
  ledges, a joint-built car, tap / drag / tilt with scripted input).
* On the phone: see the "Phone checks" list in the K3 report / `docs/behaviors/movement.md` notes above: accelerometer directions,
  joystick feel, the side-scroller car with real joints, rider carrying on a moving platform, touch conversion with a moving camera.
