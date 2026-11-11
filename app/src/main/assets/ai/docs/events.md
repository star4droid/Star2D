# Events: where logic lives

An **event** is a block sequence (or Java) that runs when something happens. Scene events belong to the scene, element events belong to one element. Write them with the `events` tool (see `blocks` for the format; `blocks-examples` for working sequences).

```json
{"ops":[
 {"op":"set","scope":"scene","event":"OnCreate","blocks":[ ... ]},
 {"op":"set","scope":"scene","event":"OnStep","blocks":[ ... ]},
 {"op":"set","scope":"element","element":"playButton","event":"onClick","blocks":[ ... ]},
 {"op":"get","scope":"scene","event":"OnStep"},
 {"op":"delete","scope":"element","element":"playButton","event":"onClick"},
 {"op":"list"}
]}
```

- `set` REPLACES the whole event. `get` returns the blocks (or the Java). `list` shows which events already have code. Add `"scene":"level2"` to any op (or at the top level) to work on another scene.
- Give either `blocks` (preferred) or `java` (raw statements, no method header, e.g. `player.getBody().setLinearVelocity(0f, 5f);`). `{"block":"codeLine","fields":{"code":"..."}}` puts one Java line inside a block sequence.
- Names are case-insensitive and common aliases are accepted (`onCreate`, `onUpdate` -> `OnStep`, `onCollisionBegin` -> `onCollisionStart`), but the canonical names below are what you see in `list`/`get`.

## Scene events (`scope: "scene"`)

| Event | When it runs | Variables |
|---|---|---|
| `OnCreate` | Once, when the scene starts, after ALL elements and joints are created. Put setup here: `setWorldGravity`, initial values, camera, timers. | none |
| `OnStep` | Every frame, after the physics step (about 60 times per second). Keep it light. Use the reporter `frame time` for seconds per frame. | none |
| `onCollisionStart` | When two bodies begin touching (sensors included). | `body1`, `body2` (PlayerItems, either order) |
| `onCollisionEnd` | When two bodies stop touching. | `body1`, `body2` |
| `onPathEnd` | A walker finished its path (ONCE). See `navigation`. | `current` |
| `onNoPath` | No way to the target for a walker. | `current` |
| `onPathBlocked` | A walker's way got blocked (it re-plans by itself). | `current` |
| `onTimelineEnd` | A ONCE timeline finished. See `timeline`. | `timeline`, `current` |
| `onTimelineMarker` | A playing timeline passed a marker. | `timeline`, `marker`, `current` |
| `onTweenEnd` | A tween finished. | `current`, `property` |
| `onPlayerJoined` / `onPlayerLeft` | A player joined / left the network game. See `network`. | `player`, `name` |
| `onGameFound` | Search found a game. | `room`, `host`, `players`, `maxPlayers` |
| `onConnected` / `onHostLeft` / `onGameStarted` | Joined (or hosting) / host closed the game / host pressed Start. | none |
| `onDisconnected` / `onJoinFailed` | Connection lost / joining failed. | `reason` |
| `onNetMessage` | A network message arrived (`netSendAll`, `netRunAll`...). | `name`, `data`, `from` |
| `OnPause` | The game screen is paused (the app goes to the background). | none |
| `OnResume` | The game screen comes back. | none |

Collision events are the reliable way to react to touching. Because the order of `body1`/`body2` is arbitrary, check BOTH orders (see the pickup pattern in `blocks-examples`), or use the reporter `___ touches ___` inside `OnStep` for simple one-pair checks.

## Element events (`scope: "element"`, plus `element`)

| Event | When it runs | Variables |
|---|---|---|
| `onClick` | The element is tapped (touch down and up on it). Works for UI and world elements; not for `Visible: false`. | `current` (the element) |
| `onTouchStart` | A finger goes down on the element. | `current`, `event` |
| `onTouchEnd` | The finger is released. | `current`, `event` |
| `OnBodyUpdate` | Every frame while the element exists. | `current` |
| `OnBodyCreated` | When the element's body has been created. Reliable for clones created while the game runs; for elements defined in the scene prefer putting initialisation in the scene's `OnCreate`. | `current` |
| `onBodyCollided` | A collision with this element started. **Unreliable, see below.** | `current`, `body2` |
| `onBodyCollideEnd` | A collision ended. **Unreliable.** | `current`, `body2` |

`current` is the element that owns the event: in blocks write `current` in any field that takes an element. In `onClick` of `playButton`, `current` is `playButton`. Elements sharing a `Script` share events.

**Do not use `onBodyCollided`/`onBodyCollideEnd`.** In this engine version, for two elements without Java scripts, the handler of the first body runs twice (second time with the roles swapped) and the second body's own handler never runs. Use the scene's `onCollisionStart`/`onCollisionEnd` and decide with `body1`/`body2`.

## Where the event code runs

Event bodies are pasted inside the scene's Java class, so everything of the scene is directly visible:

- every element by name (`player`, `coin1`): type `PlayerItem`,
- the scene's variables by name (`score`, `lives`),
- all engine methods: `openScene("x")`, `toast("hi")`, `findItem("coin1")`, `getDelta()`, `setValue/getValue`, `random(1,6)`... (full list in `java-api`).

Imports available in the generated class: `Vector2`, `Gdx`, `World`, Box2D joints, `Timer`, `PlayerItem`, `InputEvent`, the engine items. NOT imported (write the full name): `com.badlogic.gdx.graphics.Color`, `com.badlogic.gdx.math.MathUtils`, `com.badlogic.gdx.utils.Align`, `com.badlogic.gdx.Input.Keys`.

## Timers and the `final` rule (Java source level 7)

The blocks `waitSeconds`, `everySeconds`, `_DoAfter`, `_repeatEvery`, `destroyAfter` run their inner blocks later on a timer (an anonymous class). In Java 7 such code may only use **final** local variables. The event variables `current`, `body1`, `body2`, `event` are NOT final, so they cause a compile error when used inside those blocks. Fix: use the helper block `destroyLater` for removing things (see `blocks`); for other timer code, before the timer block add `{"block":"codeLine","fields":{"code":"final PlayerItem me = current;"}}` and use `me`, or refer to the element by its field name (`player`). Scene variables and elements named in the scene are fine.

`everySeconds` / `_repeatEvery` keep running until the scene ends (they are not cancelled by `Pause`). To stop one, guard it with a variable (`ifThen` on `running`). `cancelTimer` only works in the same event that created a `_DoAfter`/`_repeatEvery` timer.

## Typical split of logic

- `OnCreate`: `setWorldGravity`, set variables, start timers, `cameraFollowX/Y`, show texts.
- `OnStep`: continuous input and AI (keys, joystick, followers, camera, score text, win/lose checks).
- `onCollisionStart`: pickups, damage, landing detection, goals.
- Element `onClick`: buttons.
- Use `stopEvent` (block) to leave an event early.

## Debugging events

`build` reports Java compile errors with the file (`Events/<scene>/...`). At runtime use `game_logs` (exceptions with a stack) and the blocks `debug` / `Toast` / `logValue` / `toastValue`. Runtime exceptions are reported by `game_logs` (and as `runtime_error` in `game_input`/`run_test` results): always check them after a test.
