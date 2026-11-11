# Network play: NETWORK element, lobby, synced elements, Network blocks

Local multiplayer for 2 to 8 phones on the same Wi-Fi or on one phone's hotspot (no internet, no server). One phone is the host (player 1): it runs the game rules and corrects the others. Players find games by themselves (search on the local network).

## 1. The NETWORK element (one per scene)
Add with `elements {op:'add', type:'NETWORK'}` in every scene that is online (lobby scene and game scene). Editor-only tile, never drawn in the game.

| Property | Meaning |
|---|---|
| Room Name | Name others see in the search ("" = device name). |
| Max Players | Seats, host included (2..8). |
| Send Rate | Updates per second (20 smooth on Wi-Fi). |
| Player Template | An element copied once for every player (each copy moved by its own player). "" = make players with blocks (`netSpawn`). Turn Network OFF on the template itself. |
| Spawn Radius | Copies appear around the template within this distance. |
| Game Version | Only equal versions can join. |
| Lobby | Show the ready-made lobby window when the scene starts (host, rooms list, players, Start). |
| Game Scene | Scene the lobby opens on every phone when the host presses Start. |
| TCP Port, UDP Port | Keep 54555 / 54777. |

## 2. Elements that must be the same on every phone
Properties panel > "Network" card of the element: "Same on every phone", "Moved by" (Host or Player 2..8) and what is kept the same: position, rotation, speed, visible, image, animation, text, synced variables. Stored in the element property `net`, e.g. `{"on":true,"owner":"host","sync":"pos,rot,vel,vis"}`. Element variables can be marked "Synced over the network" (the host's value is sent to everyone).
Rule: the owner moves it (joystick, physics run there), the other phones only show it smoothly. Move your own player only inside `ifMine` (or use `my element`).

## 3. Network blocks (category "Network")
- Rooms: `netHost` (room name), `netSearch` (then `onGameFound` for each), `netJoin` (index of a found game), `netJoinRoom` (name or address), `netLeave`, `netStart` (host: opens the game scene everywhere, `onGameStarted`), `netLoadScene`, `netName`, `netShowLobby`, `netHideLobby`.
- Messages: `netSendAll`, `netSendHost`, `netSendTo` (player number), `netRunAll` (also runs here) -> scene event `onNetMessage(name, data, from)`.
- Elements: `netSpawn` (host: networked copy controlled by a player), `netDespawn`, `netOwner`.
- Conditions: `ifHost` (game rules: score, spawning, winner), `ifMine`.
- Values: `I am the host`, `I am online`, `game started`, `___ is mine`, `my player number`, `player count`, `max players`, `player number at ___`, `name of player ___`, `my name`, `ping`, `ping of player ___`, `owner of ___`, `element of player ___`, `my element`, `games found`, `name of game ___`, `players in game ___`, `my address`, `network error`.

## 4. Events (scene)
`onPlayerJoined(player, name)`, `onPlayerLeft(player, name)` (also after 5 s without answer), `onGameFound(room, host, players, maxPlayers)`, `onConnected`, `onDisconnected(reason)`, `onHostLeft`, `onNetMessage(name, data, from)`, `onGameStarted`, `onJoinFailed(reason)`.

## 5. Recipe: two scenes
1. `scene1` (lobby): NETWORK with Lobby true and Game Scene `arena` (or your own menu: buttons with `netHost`, `netSearch`, a list from `games found` / `name of game`, `netJoin`, `netStart` shown only `ifHost`).
2. `arena`: NETWORK with the same Room settings and Player Template `ball` (a DYNAMIC element, Network OFF on it), a JOYSTICK. Scene `OnStep`: velocity of `my element` from the joystick.
3. Shared things (stars, score text): Network ON, Moved by Host. Spawning, scoring and the winner run inside `ifHost`; tell everyone with `netRunAll "win" <name>`.
4. Opening `arena` directly (not from the lobby) is offline: check `I am online` and show a hint.
See the example project "Wi-Fi Stars".

## Pitfalls
- Test needs two phones (or phone + hotspot). The phone's firewall / "client isolation" of some public Wi-Fi blocks the search: use a hotspot.
- Never move an element you do not own; never change the score on clients (only the host).
- Exported games get the Wi-Fi permissions automatically when a scene has a NETWORK element.
- Bluetooth, internet play and host migration are not available yet.
