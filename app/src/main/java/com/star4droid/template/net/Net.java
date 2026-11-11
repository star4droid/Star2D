package com.star4droid.template.net;

import com.badlogic.gdx.Gdx;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.PlayerItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Multiplayer on the local network (Wi-Fi or a phone's hotspot) for blocks and scripts (category "Network").
 *
 * <pre>
 * Net.host();                         // this phone opens a room (NETWORK element: max players, ports, player template)
 * Net.findGames();                    // "When a game is found" for every room, then Net.roomName(0) ...
 * Net.join(0);                        // joins the first room found
 * Net.startForAll("game");            // the host opens the scene "game" on every phone ("When the game starts")
 * Net.sendToAll("hit", "5");          // "When a message arrives" (name, data, from) on the other phones
 * Net.isMine(player)                  // true on the phone that controls this element
 * </pre>
 * The host owns the game: physics of networked elements runs on the phone that owns each one, the others show smooth
 * copies. Events: "When a player joins / leaves", "When a game is found", "When connected / disconnected", "When the host
 * leaves", "When a message arrives", "When the game starts", "When joining fails" (scene events).
 */
public final class Net {
	private Net() {
	}

	static final NetSession S = new NetSession();

	/** Tests: receives the events of a session. */
	interface Spy {
		void event(String name, Object... args);
	}

	/** A networked element as the scene declared it (Network section of its properties). */
	static final class Reg {
		PlayerItem item;
		String owner;
		int mask;
		final ArrayList<String> vars = new ArrayList<String>();
	}

	private static final WeakHashMap<StageImp, ArrayList<Reg>> REGS = new WeakHashMap<StageImp, ArrayList<Reg>>();
	private static StageImp last;
	private static boolean nameSet;

	// ================================================================== engine (generated code / StageImp)

	/** The NETWORK element of a scene gives its settings (ports are only used when a room is opened / joined). */
	public static void configure(StageImp stage, int maxPlayers, int sendRate, String room, int tcpPort, int udpPort, String template,
								 float spawnRadius, String version, boolean lobby, String gameScene) {
		NetConfig c = S.cfg;
		c.maxPlayers = Math.max(1, Math.min(32, maxPlayers));
		c.sendRate = Math.max(1, Math.min(60, sendRate));
		c.room = room == null ? "" : room;
		if (!S.active()) {
			c.tcpPort = tcpPort <= 0 ? 54555 : tcpPort;
			c.udpPort = udpPort <= 0 ? 54777 : udpPort;
		}
		c.template = template == null ? "" : template.trim();
		c.spawnRadius = Math.max(0, spawnRadius);
		c.version = version == null || version.isEmpty() ? "1" : version;
		c.lobby = lobby;
		c.gameScene = gameScene == null ? "" : gameScene;
		String game = "";
		try {
			if (stage != null && stage.project != null) game = new java.io.File(stage.project.getPath()).getName();
		} catch (Throwable ignored) {
		}
		c.game = game;
		if (!nameSet && S.myName.isEmpty()) S.myName = NetAndroid.deviceName();
		if (lobby && stage != null && !S.active()) NetLobby.showLater(stage);
	}

	/** Declares a networked element (generated code): owner "host" or a seat "2", fields like "pos,rot,vel,vis". */
	public static void register(PlayerItem item, String owner, String fields) {
		register(item, owner, maskOf(fields));
	}

	public static void register(PlayerItem item, String owner, int mask) {
		if (item == null) return;
		StageImp st = StageImp.of(item);
		if (st == null) st = last;
		if (st == null) return;
		synchronized (REGS) {
			ArrayList<Reg> l = REGS.get(st);
			if (l == null) {
				l = new ArrayList<Reg>();
				REGS.put(st, l);
			}
			for (Reg r : l) if (r.item == item) return;
			Reg r = new Reg();
			r.item = item;
			r.owner = owner;
			r.mask = mask == 0 ? NetObject.DEFAULT_MASK : mask;
			l.add(r);
		}
	}

	/** An element variable synced like the other fields (generated code for variables marked "synced"). */
	public static void syncVar(PlayerItem item, String variable) {
		if (item == null || variable == null) return;
		StageImp st = StageImp.of(item);
		if (st == null) st = last;
		Reg r = regOf(st, item);
		if (r == null) {
			register(item, "host", NetObject.DEFAULT_MASK | NetObject.VARS);
			r = regOf(st, item);
		}
		if (r == null) return;
		r.mask |= NetObject.VARS;
		if (!r.vars.contains(variable)) r.vars.add(variable);
		// already in the game: add it to the running element too
		NetObject o = S.objectOf(item);
		if (o != null) {
			o.mask |= NetObject.VARS;
			if (!o.syncVars.contains(variable)) o.syncVars.add(variable);
		}
	}

	static int maskOf(String fields) {
		if (fields == null || fields.trim().isEmpty()) return NetObject.DEFAULT_MASK;
		int m = 0;
		for (String f : fields.toLowerCase().split("[,; ]+")) {
			if (f.startsWith("pos")) m |= NetObject.POS;
			else if (f.startsWith("rot")) m |= NetObject.ROT;
			else if (f.startsWith("vel") || f.startsWith("speed")) m |= NetObject.VEL;
			else if (f.startsWith("vis")) m |= NetObject.VIS;
			else if (f.startsWith("im")) m |= NetObject.IMG;
			else if (f.startsWith("anim")) m |= NetObject.ANIM;
			else if (f.startsWith("text")) m |= NetObject.TEXT;
			else if (f.startsWith("var")) m |= NetObject.VARS;
		}
		return m == 0 ? NetObject.DEFAULT_MASK : m;
	}

	static List<Reg> registrations(StageImp st) {
		synchronized (REGS) {
			ArrayList<Reg> l = st == null ? null : REGS.get(st);
			return l == null ? Collections.<Reg>emptyList() : new ArrayList<Reg>(l);
		}
	}

	static Reg regOf(StageImp st, PlayerItem item) {
		synchronized (REGS) {
			ArrayList<Reg> l = st == null ? null : REGS.get(st);
			if (l != null) for (Reg r : l) if (r.item == item) return r;
		}
		return null;
	}

	static StageImp lastStage() {
		return last;
	}

	/** Engine: once per frame from the running scene. */
	public static void update(StageImp stage, float dt) {
		if (stage != null) last = stage;
		if (!S.active() && S.inbound.isEmpty() && !S.searching) {
			if (stage != null && stage != S.bound) S.bound = stage;
			NetLobby.tick(stage);
			return;
		}
		try {
			S.update(stage, dt);
		} catch (RuntimeException e) {
			Gdx.app.error("Net", "update: " + e);
		}
		NetLobby.tick(stage);
	}

	/** Engine: the game closed (or the editor left play mode): the room / connection closes. */
	public static void shutdown() {
		try {
			if (S.active()) S.stop(false);
		} catch (Throwable ignored) {
		}
		NetLobby.close();
		synchronized (REGS) {
			REGS.clear();
		}
		S.objects.clear();
		S.bound = null;
		last = null;
	}

	// ================================================================== session

	/** Opens a room on this phone (it becomes the host, player 1). */
	public static boolean host() {
		return S.host(last);
	}

	/** Opens a room with this name. */
	public static boolean host(String room) {
		if (room != null && !room.trim().isEmpty()) S.cfg.room = room.trim();
		return S.host(last);
	}

	/** Looks for rooms on the network (about 1.5 s); each one raises "When a game is found". */
	public static void findGames() {
		S.find();
	}

	/** True while a search runs. */
	public static boolean isSearching() {
		return S.searching;
	}

	/** Joins a room found by the last search (0 = the first). */
	public static void join(int roomIndex) {
		if (roomIndex < 0 || roomIndex >= S.rooms.size()) {
			fireJoinFailed("no room " + roomIndex);
			return;
		}
		HostInfo h = S.rooms.get(roomIndex);
		S.join(h.address, h.tcpPort, h.udpPort);
	}

	/** Joins a room by its name (as found by the last search) or by an address ("192.168.43.1"). */
	public static void joinRoom(String nameOrAddress) {
		if (nameOrAddress == null) return;
		String n = nameOrAddress.trim();
		for (HostInfo h : S.rooms) {
			if (h.room.equals(n) || h.address.equals(n)) {
				S.join(h.address, h.tcpPort, h.udpPort);
				return;
			}
		}
		if (n.matches("[0-9.]+") || n.contains(":")) S.join(n, S.cfg.tcpPort, S.cfg.udpPort);
		else fireJoinFailed("no room called " + n);
	}

	/** Leaves the room (the host closes it for everyone). */
	public static void leave() {
		S.stop(true);
	}

	/** Host: opens a scene on every phone and raises "When the game starts" there ("" = stay, just raise it). */
	public static void startForAll(String scene) {
		S.loadForAll(scene, true);
	}

	/** Host: opens a scene on every phone. */
	public static void loadSceneForAll(String scene) {
		S.loadForAll(scene, false);
	}

	/** The name the others see (before hosting / joining). */
	public static void setPlayerName(String name) {
		S.myName = name == null ? "" : name.trim();
		nameSet = true;
	}

	public static String myName() {
		if (S.myName.isEmpty()) S.myName = NetAndroid.deviceName();
		return S.myName;
	}

	// ================================================================== messages

	/** A message to every other phone ("When a message arrives" there). */
	public static void sendToAll(String name, Object data) {
		S.message(0, s(name), s(data));
	}

	public static void sendToHost(String name, Object data) {
		S.message(NetSession.HOST_ID, s(name), s(data));
	}

	public static void sendTo(int player, String name, Object data) {
		S.message(player, s(name), s(data));
	}

	/** Runs "When a message arrives" on every phone, this one included. */
	public static void runForAll(String name, Object data) {
		S.eventForAll(s(name), s(data));
	}

	private static String s(Object o) {
		if (o == null) return "";
		if (o instanceof Float || o instanceof Double) {
			double d = ((Number) o).doubleValue();
			if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d);
		}
		return String.valueOf(o);
	}

	// ================================================================== values

	public static boolean isHost() {
		return S.role == NetSession.HOST;
	}

	public static boolean isClient() {
		return S.role == NetSession.CLIENT;
	}

	/** True while in a room (hosting or joined). */
	public static boolean isOnline() {
		return S.active();
	}

	public static boolean isConnected() {
		return S.active() && S.connected;
	}

	/** My player number (host = 1, 0 = not in a room). */
	public static int myId() {
		return S.myId;
	}

	public static int playerCount() {
		int n = 0;
		for (NetPlayer p : S.players.values()) if (!p.lost) n++;
		return n;
	}

	public static int maxPlayers() {
		return S.cfg.maxPlayers;
	}

	/** Player number of the n-th player (0 = the first, the host), 0 when there is none. */
	public static int playerAt(int index) {
		int i = 0;
		for (NetPlayer p : S.players.values()) {
			if (i == index) return p.id;
			i++;
		}
		return 0;
	}

	public static String playerName(int player) {
		NetPlayer p = S.players.get(player);
		return p == null ? "" : p.name;
	}

	public static boolean hasPlayer(int player) {
		NetPlayer p = S.players.get(player);
		return p != null && !p.lost;
	}

	/** Round trip time to the host (ms); on the host: of a player (0 for itself). */
	public static int ping() {
		return S.role == NetSession.CLIENT ? S.hostPing : 0;
	}

	public static int pingOf(int player) {
		NetPlayer p = S.players.get(player);
		return S.role == NetSession.HOST ? (p == null ? 0 : p.ping) : S.hostPing;
	}

	/** Player who controls a networked element (0 when it is not networked). */
	public static int ownerOf(PlayerItem item) {
		NetObject o = S.objectOf(item);
		return o == null ? 0 : o.owner;
	}

	/** True when this phone controls the element (also true for elements that are not networked, and offline). */
	public static boolean isMine(PlayerItem item) {
		NetObject o = S.objectOf(item);
		return o == null || S.mine(o);
	}

	public static boolean isNetworked(PlayerItem item) {
		return S.objectOf(item) != null;
	}

	public static int roomCount() {
		return S.rooms.size();
	}

	public static String roomName(int i) {
		return i >= 0 && i < S.rooms.size() ? S.rooms.get(i).room : "";
	}

	public static int roomPlayers(int i) {
		return i >= 0 && i < S.rooms.size() ? S.rooms.get(i).players : 0;
	}

	public static int roomMax(int i) {
		return i >= 0 && i < S.rooms.size() ? S.rooms.get(i).maxPlayers : 0;
	}

	public static String roomAddress(int i) {
		return i >= 0 && i < S.rooms.size() ? S.rooms.get(i).address : "";
	}

	/** This phone's address on the network (what others type to join by address). */
	public static String myAddress() {
		return NetAndroid.localAddress();
	}

	/** Why the last join failed / the connection ended. */
	public static String lastError() {
		return S.lastError;
	}

	public static boolean gameStarted() {
		return S.cfg.gameStarted;
	}

	// ================================================================== elements

	/** Host: a networked copy of an element (template) for a player, who controls it. Returns the copy. */
	public static PlayerItem spawn(PlayerItem template, int player) {
		if (template == null) return null;
		if (S.role != NetSession.HOST) {
			if (S.active()) Gdx.app.error("Net", "only the host creates networked elements");
			return null;
		}
		NetObject o = S.spawn(template, template.getName() + "_" + player + "_" + System.nanoTime() % 100000, player);
		return o == null ? null : o.item;
	}

	/** Host (or the owner, through the host): removes a networked element on every phone. */
	public static void despawn(PlayerItem item) {
		NetObject o = S.objectOf(item);
		if (o == null) {
			if (item != null) item.destroy();
			return;
		}
		if (S.role == NetSession.HOST) S.despawn(o);
		else if (S.role == NetSession.CLIENT && o.owner == S.myId) item.destroy(); // the next frame asks the host
	}

	/** Host: gives the control of an element to a player (1 = back to the host). */
	public static void setOwner(PlayerItem item, int player) {
		S.setOwner(S.objectOf(item), player);
	}

	/** The copy of the player template made for a player (null when there is none). */
	public static PlayerItem playerElement(int player) {
		NetObject o = S.playerObject(player);
		return o == null ? null : o.item;
	}

	/** My own copy of the player template. */
	public static PlayerItem myElement() {
		return playerElement(S.myId);
	}

	// ================================================================== lobby

	/** The ready-made lobby window (host, rooms, players, start). */
	public static void showLobby() {
		NetLobby.show(last);
	}

	public static void hideLobby() {
		NetLobby.close();
	}

	public static String lobbyScene() {
		return S.cfg.gameScene;
	}

	// ================================================================== events -> scene

	private static StageImp target() {
		StageImp st = last;
		return st == null ? null : st.activeStage();
	}

	static void firePlayerJoined(int id, String name) {
		NetLobby.refresh();
		StageImp st = target();
		try {
			if (st != null) st.onPlayerJoined(id, name);
		} catch (RuntimeException e) {
			Gdx.app.error("Net", "onPlayerJoined: " + e);
		}
	}

	static void firePlayerLeft(int id, String name) {
		NetLobby.refresh();
		StageImp st = target();
		try {
			if (st != null) st.onPlayerLeft(id, name);
		} catch (RuntimeException e) {
			Gdx.app.error("Net", "onPlayerLeft: " + e);
		}
	}

	static void fireGameFound(HostInfo h) {
		StageImp st = target();
		try {
			if (st != null) st.onGameFound(h.room, h.address, h.players, h.maxPlayers);
		} catch (RuntimeException e) {
			Gdx.app.error("Net", "onGameFound: " + e);
		}
	}

	static void fireSearchDone() {
		NetLobby.refresh();
	}

	static void fireConnected() {
		NetLobby.refresh();
		StageImp st = target();
		try {
			if (st != null) st.onConnected();
		} catch (RuntimeException e) {
			Gdx.app.error("Net", "onConnected: " + e);
		}
	}

	static void fireDisconnected(String reason) {
		NetLobby.refresh();
		StageImp st = target();
		try {
			if (st != null) st.onDisconnected(reason);
		} catch (RuntimeException e) {
			Gdx.app.error("Net", "onDisconnected: " + e);
		}
	}

	static void fireHostLeft() {
		NetLobby.refresh();
		StageImp st = target();
		try {
			if (st != null) {
				st.onHostLeft();
				st.onDisconnected("host left");
			}
		} catch (RuntimeException e) {
			Gdx.app.error("Net", "onHostLeft: " + e);
		}
	}

	static void fireJoinFailed(String reason) {
		NetLobby.failed(reason);
		StageImp st = target();
		try {
			if (st != null) st.onJoinFailed(reason);
		} catch (RuntimeException e) {
			Gdx.app.error("Net", "onJoinFailed: " + e);
		}
	}

	static void fireGameStarted() {
		NetLobby.close();
		StageImp st = target();
		try {
			if (st != null) st.onGameStarted();
		} catch (RuntimeException e) {
			Gdx.app.error("Net", "onGameStarted: " + e);
		}
	}

	static void fireMessage(String name, String data, int from) {
		StageImp st = target();
		try {
			if (st != null) st.onNetMessage(name, data, from);
		} catch (RuntimeException e) {
			Gdx.app.error("Net", "onNetMessage: " + e);
		}
	}
}
