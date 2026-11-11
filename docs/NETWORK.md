# Local multiplayer (Wi-Fi / hotspot)

User / AI guide: `app/src/main/assets/ai/docs/network.md` (`read_docs {topic:'network'}`). This file is the map of the code.

## Model
Host-authoritative, 2..8 phones on one LAN (no server, no internet). The host (player 1) owns the game; every networked element has an owner who runs its physics / behaviors; other phones show a kinematic copy interpolated between the two last states. Positions go over UDP `sendRate` times per second (deltas + a full state every second), reliable changes (spawn, despawn, owner, visibility, image, animation, text, synced variables, messages, scene changes) over TCP. Heartbeat every second; a silent player is lost after 5 s and keeps its seat 30 s.

## Runtime (`com.star4droid.template.net`)
| Class | Role |
|---|---|
| `Transport` | Byte transport interface (connection 0 = host seen from a client). Bluetooth / internet can be added behind it. |
| `KryoTransport` | KryoNet implementation: TCP + UDP + LAN discovery; only `byte[]` is registered in Kryo. |
| `NetCodec` | Binary writer / reader of the messages. |
| `NetSession` | The engine: join handshake (game name + Game Version + free seat), players, objects, snapshots, corrections, heartbeats, events to the GL thread. |
| `NetObject`, `NetPlayer`, `HostInfo`, `NetConfig` | Networked element state, player, discovered room, settings. |
| `NetTrack` | Remembers image / animation names given to elements so they can be synced. |
| `NetLobby` | Ready-made lobby window (translatable with game translation keys `net_lobby`, `net_host`, ...). |
| `NetAndroid` | Multicast lock + device name by reflection (no-op on desktop). |
| `NetworkItem` | NETWORK element at run time; hands its settings to `Net.configure`. |
| `Net` | Static facade for blocks / scripts (`host`, `findGames`, `join`, `startForAll`, `sendToAll`, `spawn`, `isMine`, reporters...). `Net.update` runs from `StageImp` each frame. |

## Editor
- Element: `assets/items/network.json` (+ `types.json`, `map.json` section `Network`, type `scene` for Game Scene), `editor/items/EditorNetworkItem.java`, `ControlLayer.actionAddNetwork()` (one per scene).
- Per element: property `net` (`behaviors/NetSpec.java`, card `ui/behaviors/NetSection.java`) -> generated `Net.register(element, owner, sync)`; element variables marked synced (`ObjectVarsSection`, `ObjectVarsGen` -> `Net.syncVar`).
- Blocks: `ui/blocks/BlocksSystems.java` category Network; scene events in `behaviors/SceneEventsExt.java`.
- Export: the game template already has INTERNET, ACCESS_WIFI_STATE and ACCESS_NETWORK_STATE; `Helpers/NetExport.java` adds CHANGE_WIFI_MULTICAST_STATE (needed by the LAN search on many phones) only when a scene has a NETWORK element (`ExportThread`).

## Not yet (phase 7)
Bluetooth transport, internet relay, host migration, nav-mesh. The `Transport` interface is the extension point.

## Tests
`python3 tools/test_net.py` (two sessions over loopback: join, spawn, sync, messages, correction, leave), `tools/test_blocks_systems.py`, `tools/gen_systems_examples.py --check` (example WifiStars).
