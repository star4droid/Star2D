package com.star4droid.template.net;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The multiplayer engine. One phone hosts and owns the game state; the others send what they control and show what the host
 * sends, so phones of different speeds never disagree.
 *
 * <ul>
 * <li>Every networked element has an owner (the host unless it was made for a player). Only the owner moves it: the owner
 * runs its behaviors and physics; on the other phones it is a kinematic copy placed between the two last states received
 * (smooth), its behaviors switched off.</li>
 * <li>The host sends the positions {@code sendRate} times a second over UDP (only what changed, everything once a second),
 * visibility / image / animation / text / variables over TCP when they change. A client sends the state of what it owns the
 * same way; it moves its own player at once and is put back where the host says if the host changed it (correction).</li>
 * <li>Joining: game name and version must match the host's, the room must have a free seat. A heartbeat goes both ways
 * every second; a silent player is lost after 5 s and keeps its seat 30 s to come back (same id, same elements).</li>
 * </ul>
 * Everything runs on the game thread ({@link #update}); the network thread only queues what arrives.
 */
final class NetSession implements Transport.Listener {
	static final int NONE = 0, HOST = 1, CLIENT = 2;
	static final int HOST_ID = 1;
	// message types
	static final int HELLO = 1, WELCOME = 2, REJECT = 3, PLAYER_JOIN = 4, PLAYER_LEFT = 5, PING = 6, PONG = 7, SNAP = 8, OWNER_STATE = 9,
			CORRECT = 10, SPAWN = 11, DESPAWN = 12, OWNER = 13, MSG = 14, EVENT = 15, LOAD_SCENE = 16, BYE = 17, REQ_DESPAWN = 18, STATE_R = 19,
			START = 20, WORLD = 255;
	static final int REJECT_VERSION = 1, REJECT_FULL = 2, REJECT_GAME = 3;
	static final long LOST_MS = 5000, SEAT_MS = 30000, PING_MS = 1000;
	static final int MAX_DATAGRAM = 1100;

	final NetConfig cfg = new NetConfig();
	int role = NONE;
	Transport transport;
	int myId;
	String myName = "";
	long token;
	final LinkedHashMap<Integer, NetPlayer> players = new LinkedHashMap<Integer, NetPlayer>();
	private final HashMap<Integer, Integer> connToPlayer = new HashMap<Integer, Integer>();
	int epoch;
	final LinkedHashMap<Integer, NetObject> objects = new LinkedHashMap<Integer, NetObject>();
	StageImp bound;
	private boolean sceneReady;
	private final ArrayList<byte[]> pending = new ArrayList<byte[]>();
	final ConcurrentLinkedQueue<Object[]> inbound = new ConcurrentLinkedQueue<Object[]>();
	private final long startMs = System.currentTimeMillis();
	private int nextSpawnId = 10000;
	private float snapTimer, refreshTimer;
	private long lastPing;
	// client side
	boolean connected;
	String hostAddress = "";
	private long hostLastSeen;
	private boolean hostLost, reconnecting, closing;
	private long hostLostSince, lastReconnectTry;
	private double clockOffset;
	private boolean offsetSet;
	private boolean fireStarted;
	int hostPing;
	final ArrayList<HostInfo> rooms = new ArrayList<HostInfo>();
	boolean searching;
	private boolean joining;
	private int joinGen;
	/** Last reason a join failed / the connection closed. */
	String lastError = "";
	/** Tests: sees the events of this session. */
	Net.Spy spy;

	private void spy(String name, Object... args) {
		if (spy != null) spy.event(name, args);
	}

	NetSession() {
		token = new java.util.Random().nextLong();
	}

	long now() {
		return System.currentTimeMillis() - startMs;
	}

	boolean active() {
		return role != NONE;
	}

	// ================================================================== network thread -> queue

	@Override
	public void connected(int connection) {
		inbound.add(new Object[] {"c", connection});
	}

	@Override
	public void disconnected(int connection) {
		inbound.add(new Object[] {"d", connection});
	}

	@Override
	public void received(int connection, byte[] data) {
		inbound.add(new Object[] {"m", connection, data});
	}

	// ================================================================== starting / stopping

	boolean host(StageImp st) {
		if (role != NONE) stop(false);
		try {
			transport = new KryoTransport();
			transport.startServer(cfg.tcpPort, cfg.udpPort, this, new Transport.DiscoveryReply() {
				public byte[] info() {
					return roomInfo().toBytes();
				}
			});
		} catch (Throwable e) {
			lastError = "Cannot open the room: " + e.getMessage();
			Gdx.app.error("Net", lastError);
			transport = null;
			{ spy("JoinFailed", lastError); Net.fireJoinFailed(lastError); }
			return false;
		}
		role = HOST;
		myId = HOST_ID;
		players.clear();
		connToPlayer.clear();
		NetPlayer me = new NetPlayer(HOST_ID, myName);
		me.lastSeen = now();
		players.put(HOST_ID, me);
		epoch++;
		connected = true;
		closing = false;
		bound = null; // bind the scene again as the host (roles, the host's own player)
		{ spy("Connected"); Net.fireConnected(); }
		return true;
	}

	HostInfo roomInfo() {
		HostInfo h = new HostInfo();
		h.game = cfg.game;
		h.version = cfg.version;
		h.room = cfg.room.isEmpty() ? (myName.isEmpty() ? "Room" : myName) : cfg.room;
		h.host = myName;
		int n = 0;
		synchronized (players) {
			for (NetPlayer p : players.values()) if (!p.lost) n++;
		}
		h.players = n;
		h.maxPlayers = cfg.maxPlayers;
		h.tcpPort = cfg.tcpPort;
		h.udpPort = cfg.udpPort;
		h.inLobby = !cfg.gameStarted;
		return h;
	}

	/** Searches rooms in the background; results arrive as "f" items on the queue (game thread raises the events). */
	void find() {
		if (searching) return;
		searching = true;
		final int udp = cfg.udpPort;
		Thread t = new Thread(new Runnable() {
			public void run() {
				Object lock = NetAndroid.multicastLock();
				List<HostInfo> found;
				try {
					found = new KryoTransport().discover(udp, 1500);
				} finally {
					NetAndroid.release(lock);
				}
				inbound.add(new Object[] {"f", found});
			}
		}, "star2d-net-find");
		t.setDaemon(true);
		t.start();
	}

	/** Connects to a room in the background ("j" ok / "x" failed on the queue). */
	void join(final String address, final int tcp, final int udp) {
		if (role == HOST) stop(false);
		if (role == CLIENT && connected && address.equals(hostAddress)) return;
		if (joining) return;
		joining = true;
		stoppedSinceJoin = false;
		final Transport tr = new KryoTransport();
		hostAddress = address;
		cfg.tcpPort = tcp;
		cfg.udpPort = udp;
		final int gen = ++joinGen;
		Thread t = new Thread(new Runnable() {
			public void run() {
				try {
					tr.connect(address, tcp, udp, 4000, NetSession.this);
					inbound.add(new Object[] {"j", tr, gen});
				} catch (Throwable e) {
					inbound.add(new Object[] {"x", tr, String.valueOf(e.getMessage()), gen});
				}
			}
		}, "star2d-net-join");
		t.setDaemon(true);
		t.start();
	}

	private boolean stoppedSinceJoin;

	/** Leaves the session (graceful: the others are told). */
	void stop(boolean fireEvents) {
		stoppedSinceJoin = true;
		joinGen++;
		joining = false;
		if (transport != null) {
			NetCodec.Out o = new NetCodec.Out().u8(BYE).str("left");
			byte[] b = o.bytes();
			if (role == HOST) {
				for (NetPlayer p : players.values()) if (p.connection >= 0) transport.sendReliable(p.connection, b);
			} else if (role == CLIENT) transport.sendReliable(0, b);
			final Transport tr = transport;
			transport = null;
			// let the goodbye leave before closing
			Thread t = new Thread(new Runnable() {
				public void run() {
					try {
						Thread.sleep(150);
					} catch (InterruptedException ignored) {
					}
					tr.stop();
				}
			}, "star2d-net-close");
			t.setDaemon(true);
			t.start();
		}
		int was = role;
		role = NONE;
		connected = false;
		reconnecting = false;
		hostLost = false;
		players.clear();
		connToPlayer.clear();
		// what we owned stays here; copies of the others are given back their physics / behaviors
		for (NetObject o : objects.values()) restoreLocal(o);
		pending.clear();
		myId = 0;
		cfg.gameStarted = false;
		if (fireEvents && was == CLIENT) { spy("Disconnected", "left"); Net.fireDisconnected("left"); }
	}

	// ================================================================== game thread

	void update(StageImp st, float dt) {
		Object[] in;
		int guard = 0;
		while ((in = inbound.poll()) != null && guard++ < 2000) {
			try {
				handle(in);
			} catch (RuntimeException e) {
				Gdx.app.error("Net", "message: " + e);
			}
		}
		if (st != null && st != bound) bind(st);
		if (role == NONE) return;
		long now = now();
		if (now - lastPing >= PING_MS) {
			lastPing = now;
			heartbeat(now);
		}
		if (role == HOST) hostFrame(dt, now);
		else if (role == CLIENT) clientFrame(dt, now);
	}

	// ------------------------------------------------------------------ scenes

	/** The scene is running: its networked elements get their ids (same order on every phone) and their roles. */
	private void bind(StageImp st) {
		bound = st;
		sceneReady = true;
		objects.clear();
		// the player template is only a model: hidden and without physics (its copies are the players)
		if (!cfg.template.isEmpty()) hideTemplate(find(cfg.template));
		List<Net.Reg> regs = Net.registrations(st);
		int id = 1;
		for (Net.Reg r : regs) {
			if (!cfg.template.isEmpty() && cfg.template.equals(r.item.getName())) continue; // the player template is a model, not in the game
			NetObject o = new NetObject(id++, r.item, ownerOf(r.owner), r.mask);
			o.syncVars.addAll(r.vars);
			objects.put(o.id, o);
			applyRole(o);
		}
		if (role == HOST) {
			for (NetPlayer p : new ArrayList<NetPlayer>(players.values())) if (!p.lost) spawnPlayer(p.id);
			if (fireStarted) {
				fireStarted = false;
				{ spy("GameStarted"); Net.fireGameStarted(); }
			}
		} else if (role == CLIENT) {
			ArrayList<byte[]> waiting = new ArrayList<byte[]>(pending);
			pending.clear();
			for (byte[] b : waiting) {
				if (b.length > 0 && (b[0] & 0xFF) == WORLD) applyWorld(b);
				else handleMessage(-1, b);
			}
			if (fireStarted) {
				fireStarted = false;
				{ spy("GameStarted"); Net.fireGameStarted(); }
			}
		}
	}

	/** Owner of a scene element from its setting: "host" or a seat number ("2" = the first player who joins). */
	private int ownerOf(String spec) {
		if (spec == null) return HOST_ID;
		String s = spec.trim().toLowerCase();
		if (s.isEmpty() || s.equals("host") || s.equals("player")) return HOST_ID;
		try {
			int seat = Integer.parseInt(s.replace("seat", "").replace("player", "").trim());
			if (seat > 1 && players.containsKey(seat) && !players.get(seat).lost) return seat;
		} catch (NumberFormatException ignored) {
		}
		return HOST_ID;
	}

	private int seatOf(NetObject o) {
		for (Net.Reg r : Net.registrations(bound)) {
			if (r.item != o.item || r.owner == null) continue;
			try {
				return Integer.parseInt(r.owner.trim().toLowerCase().replace("seat", "").replace("player", "").trim());
			} catch (NumberFormatException e) {
				return -1;
			}
		}
		return -1;
	}

	private void openScene(String scene) {
		StageImp st = bound != null ? bound : Net.lastStage();
		if (st == null || scene == null || scene.isEmpty()) return;
		sceneReady = false;
		st.openScene(scene);
	}

	/** The host opens a scene for everyone ("start" also raises "When the game starts" there). */
	void loadForAll(String scene, boolean start) {
		if (role != HOST) return;
		epoch++;
		if (start) cfg.gameStarted = true;
		NetCodec.Out o = new NetCodec.Out().u8(start ? START : LOAD_SCENE).var(epoch).str(scene == null ? "" : scene);
		sendAll(o.bytes(), true, -1);
		if (scene == null || scene.isEmpty()) {
			if (start) { spy("GameStarted"); Net.fireGameStarted(); }
			return;
		}
		fireStarted = start;
		objects.clear();
		openScene(scene);
	}

	// ------------------------------------------------------------------ roles

	boolean mine(NetObject o) {
		return role == NONE || o.owner == myId;
	}

	/** Owner: the element is played here (behaviors, physics). Anyone else: a kinematic copy with its behaviors off. */
	void applyRole(NetObject o) {
		if (!o.alive()) return;
		boolean mine = mine(o);
		if (o.is3D()) {
			com.star4droid.template.d3.Object3D obj = (com.star4droid.template.d3.Object3D) o.item;
			if (!mine && obj.isDynamic()) {
				o.savedType = BodyDef.BodyType.DynamicBody;
				obj.setBodyType("KINEMATIC");
			} else if (mine && o.savedType != null) {
				obj.setBodyType("DYNAMIC");
				o.savedType = null;
			}
		} else {
			Body b = o.body();
			if (b != null) {
				if (!mine && b.getType() == BodyDef.BodyType.DynamicBody) {
					o.savedType = b.getType();
					b.setType(BodyDef.BodyType.KinematicBody);
				} else if (mine && o.savedType != null) {
					b.setType(o.savedType);
					o.savedType = null;
				}
			}
		}
		setBehaviors(o, mine);
	}

	private void restoreLocal(NetObject o) {
		if (!o.alive()) return;
		if (o.savedType != null) {
			if (o.is3D()) ((com.star4droid.template.d3.Object3D) o.item).setBodyType("DYNAMIC");
			else {
				Body b = o.body();
				if (b != null) b.setType(o.savedType);
			}
			o.savedType = null;
		}
		setBehaviors(o, true);
	}

	private final java.util.WeakHashMap<Behavior, Boolean> disabledByNet = new java.util.WeakHashMap<Behavior, Boolean>();

	private void setBehaviors(NetObject o, boolean on) {
		ItemScript s;
		try {
			s = o.item.getScript();
		} catch (RuntimeException e) {
			return;
		}
		if (s == null || s.getBehaviors() == null) return;
		for (Behavior b : new ArrayList<Behavior>(s.getBehaviors())) {
			if (!on && b.isEnabled()) {
				disabledByNet.put(b, Boolean.TRUE);
				b.setEnabled(false);
			} else if (on && disabledByNet.remove(b) != null) b.setEnabled(true);
		}
		o.behaviorsOff = !on;
	}

	// ------------------------------------------------------------------ spawning

	/** Host: the copy of the player template for a player (made once per scene). */
	NetObject spawnPlayer(int player) {
		if (role != HOST || bound == null || cfg.template.isEmpty()) return null;
		NetObject have = playerObject(player);
		if (have != null) return have;
		PlayerItem tpl = find(cfg.template);
		if (tpl == null) return null;
		NetObject o = spawn(tpl, cfg.template + "_" + player, player);
		if (o != null && cfg.spawnRadius > 0) {
			// spread the players around the template so they do not start inside each other
			float a = (float) (player * 2.399963);
			float dx = (float) Math.cos(a) * cfg.spawnRadius, dy = (float) Math.sin(a) * cfg.spawnRadius;
			NetObject.State s = new NetObject.State();
			o.capture(s, bound);
			s.x += dx;
			if (o.is3D()) s.z += dy;
			else s.y += dy;
			o.applyContinuous(s, NetObject.POS, bound, false);
			sendState(o, true);
		}
		return o;
	}

	NetObject playerObject(int player) {
		for (NetObject o : objects.values()) if (o.spawned && o.owner == player && o.template.equals(cfg.template)) return o;
		return null;
	}

	PlayerItem find(String name) {
		if (bound == null || name == null) return null;
		PlayerItem it = bound.findItem(name);
		if (it == null && bound.hasWorld3D()) it = bound.world3d().find(name);
		return it;
	}

	/** Host: a networked copy of an element for a player (the owner moves it). */
	NetObject spawn(PlayerItem tpl, String name, int owner) {
		if (role != HOST || tpl == null || bound == null) return null;
		PlayerItem copy;
		try {
			copy = tpl.getClone(name);
		} catch (RuntimeException e) {
			Gdx.app.error("Net", "cannot copy " + tpl.getName() + ": " + e);
			return null;
		}
		if (copy == null) return null;
		show(copy);
		Net.Reg r = Net.regOf(bound, tpl);
		NetObject o = new NetObject(nextSpawnId++, copy, owner, r == null ? NetObject.DEFAULT_MASK : r.mask);
		o.spawned = true;
		o.template = tpl.getName();
		if (r != null) o.syncVars.addAll(r.vars);
		objects.put(o.id, o);
		applyRole(o);
		NetCodec.Out out = new NetCodec.Out().u8(SPAWN).var(epoch).var(o.id).str(o.template).str(name).var(owner).var(o.mask).var(o.syncVars.size());
		for (String v : o.syncVars) out.str(v);
		NetObject.State s = new NetObject.State();
		o.capture(s, bound);
		o.write(out, s, o.mask);
		sendAll(out.bytes(), true, -1);
		o.sent.set(s);
		o.sentOnce = true;
		return o;
	}

	private static void hideTemplate(PlayerItem t) {
		if (t == null) return;
		try {
			if (t instanceof com.star4droid.template.d3.Object3D) {
				com.star4droid.template.d3.Object3D o = (com.star4droid.template.d3.Object3D) t;
				o.setVisible3(false);
				o.setPhysicsEnabled(false);
				return;
			}
			if (t.getActor() != null) t.getActor().setVisible(false);
			com.badlogic.gdx.physics.box2d.Body b = t.getBody();
			if (b != null && !b.getWorld().isLocked()) b.setActive(false);
		} catch (RuntimeException ignored) {
		}
	}

	private static void show(PlayerItem it) {
		if (it instanceof com.star4droid.template.d3.Object3D) ((com.star4droid.template.d3.Object3D) it).setVisible3(true);
		else if (it.getActor() != null) it.getActor().setVisible(true);
	}

	/** Host: removes a networked element everywhere. */
	void despawn(NetObject o) {
		if (o == null) return;
		objects.remove(o.id);
		if (role == HOST) sendAll(new NetCodec.Out().u8(DESPAWN).var(epoch).var(o.id).bytes(), true, -1);
		try {
			if (o.alive()) o.item.destroy();
		} catch (RuntimeException ignored) {
		}
	}

	void setOwner(NetObject o, int owner) {
		if (o == null || role != HOST) return;
		o.owner = owner;
		o.hasOwnerState = false;
		applyRole(o);
		sendAll(new NetCodec.Out().u8(OWNER).var(epoch).var(o.id).var(owner).bytes(), true, -1);
	}

	NetObject objectOf(PlayerItem it) {
		if (it == null) return null;
		for (NetObject o : objects.values()) if (o.item == it) return o;
		return null;
	}

	// ------------------------------------------------------------------ sending

	void sendAll(byte[] b, boolean reliable, int exceptPlayer) {
		if (transport == null) return;
		if (role == HOST) {
			for (NetPlayer p : players.values()) {
				if (p.id == HOST_ID || p.id == exceptPlayer || p.connection < 0 || p.lost) continue;
				if (reliable) transport.sendReliable(p.connection, b);
				else transport.sendFast(p.connection, b);
			}
		} else if (role == CLIENT && connected) {
			if (reliable) transport.sendReliable(0, b);
			else transport.sendFast(0, b);
		}
	}

	private void sendTo(int player, byte[] b, boolean reliable) {
		if (transport == null) return;
		if (role == CLIENT) {
			if (reliable) transport.sendReliable(0, b);
			else transport.sendFast(0, b);
			return;
		}
		NetPlayer p = players.get(player);
		if (p == null || p.connection < 0) return;
		if (reliable) transport.sendReliable(p.connection, b);
		else transport.sendFast(p.connection, b);
	}

	/** Discrete fields of an element (visibility, image, animation, text, variables) to everyone, when they changed. */
	private void sendState(NetObject o, boolean force) {
		NetObject.State s = new NetObject.State();
		o.capture(s, bound);
		int changed = force ? (o.mask & NetObject.DISCRETE) : o.diff(s, o.sent, o.mask & NetObject.DISCRETE);
		if (changed == 0) return;
		NetCodec.Out out = new NetCodec.Out().u8(STATE_R).var(epoch);
		o.write(out, s, changed);
		sendAll(out.bytes(), true, -1);
		copyDiscrete(o.sent, s);
	}

	private static void copyDiscrete(NetObject.State to, NetObject.State from) {
		to.vis = from.vis;
		to.img = from.img;
		to.anim = from.anim;
		to.text = from.text;
		to.vars.clear();
		to.vars.putAll(from.vars);
	}

	// ------------------------------------------------------------------ host frame

	private void hostFrame(float dt, long now) {
		// elements destroyed by the game: gone everywhere
		for (Iterator<Map.Entry<Integer, NetObject>> it = objects.entrySet().iterator(); it.hasNext(); ) {
			NetObject o = it.next().getValue();
			if (!o.alive()) {
				it.remove();
				sendAll(new NetCodec.Out().u8(DESPAWN).var(epoch).var(o.id).bytes(), true, -1);
			}
		}
		snapTimer += dt;
		refreshTimer += dt;
		float every = 1f / Math.max(1, cfg.sendRate);
		if (snapTimer < every) return;
		snapTimer = 0;
		boolean refresh = refreshTimer >= 1f;
		if (refresh) refreshTimer = 0;
		NetCodec.Out out = null;
		int count = 0;
		int stamp = (int) now;
		NetObject.State s = new NetObject.State();
		for (NetObject o : objects.values()) {
			o.capture(s, bound);
			int cont = o.mask & NetObject.CONTINUOUS;
			int fields = refresh || !o.sentOnce ? cont : o.diff(s, o.sent, cont);
			if (fields != 0) {
				if (out == null) out = snapHeader(stamp);
				o.write(out, s, fields);
				count++;
				o.sent.x = s.x;
				o.sent.y = s.y;
				o.sent.z = s.z;
				o.sent.rx = s.rx;
				o.sent.ry = s.ry;
				o.sent.rz = s.rz;
				o.sent.vx = s.vx;
				o.sent.vy = s.vy;
				o.sent.vz = s.vz;
				if (out.size() > MAX_DATAGRAM) {
					sendAll(out.bytes(), false, -1);
					out = null;
				}
			}
			// discrete changes go the reliable way
			int disc = o.mask & NetObject.DISCRETE;
			if (disc != 0) {
				int ch = o.sentOnce ? o.diff(s, o.sent, disc) : disc;
				if (ch != 0) {
					NetCodec.Out r = new NetCodec.Out().u8(STATE_R).var(epoch);
					o.write(r, s, ch);
					sendAll(r.bytes(), true, -1);
					copyDiscrete(o.sent, s);
				}
			}
			o.sentOnce = true;
			// the host changed an element a player owns (respawn, teleport...): tell its owner
			if (o.owner != HOST_ID && o.hasOwnerState) checkCorrection(o, s, now);
		}
		if (out != null && count > 0) sendAll(out.bytes(), false, -1);
	}

	private NetCodec.Out snapHeader(int stamp) {
		return new NetCodec.Out(MAX_DATAGRAM + 128).u8(SNAP).var(epoch).i32(stamp);
	}

	private void checkCorrection(NetObject o, NetObject.State s, long now) {
		NetObject.State own = o.ownerState;
		float dtS = (now - (long) o.ownerTime) / 1000f;
		float px = own.x + own.vx * dtS, py = own.y + own.vy * dtS, pz = own.z + own.vz * dtS;
		float far = o.is3D() ? 0.75f : 24f;
		boolean moved = Math.abs(s.x - px) > far || Math.abs(s.y - py) > far || Math.abs(s.z - pz) > far;
		boolean disc = (o.mask & NetObject.DISCRETE) != 0 && o.diff(s, own, o.mask & NetObject.DISCRETE) != 0;
		if (!moved && !disc) return;
		o.correctionSeq++;
		NetCodec.Out out = new NetCodec.Out().u8(CORRECT).var(epoch).var(o.correctionSeq);
		o.write(out, s, o.mask);
		sendTo(o.owner, out.bytes(), true);
		own.set(s);
		o.ownerTime = now;
	}

	// ------------------------------------------------------------------ client frame

	private void clientFrame(float dt, long now) {
		if (!connected) {
			if (reconnecting && !joining && now - lastReconnectTry > 2000) {
				lastReconnectTry = now;
				join(hostAddress, cfg.tcpPort, cfg.udpPort);
			}
			if (reconnecting && now - hostLostSince > SEAT_MS) {
				reconnecting = false;
				giveUp("lost");
			}
			return;
		}
		// own elements: report their state
		snapTimer += dt;
		float every = 1f / Math.max(1, cfg.sendRate);
		boolean report = snapTimer >= every;
		if (report) snapTimer = 0;
		NetObject.State s = new NetObject.State();
		double renderT = hostNowEstimate(now) - interpDelay();
		for (NetObject o : new ArrayList<NetObject>(objects.values())) {
			if (!o.alive()) {
				objects.remove(o.id);
				if (o.owner == myId) sendTo(HOST_ID, new NetCodec.Out().u8(REQ_DESPAWN).var(epoch).var(o.id).bytes(), true);
				continue;
			}
			if (o.owner == myId) {
				if (!report) continue;
				o.capture(s, bound);
				NetCodec.Out out = new NetCodec.Out().u8(OWNER_STATE).var(epoch).var(o.ackedCorrection);
				o.write(out, s, o.mask & NetObject.CONTINUOUS);
				sendTo(HOST_ID, out.bytes(), false);
				int disc = o.mask & NetObject.DISCRETE;
				int ch = disc == 0 ? 0 : (o.sentOnce ? o.diff(s, o.sent, disc) : disc);
				if (ch != 0) {
					NetCodec.Out r = new NetCodec.Out().u8(STATE_R).var(epoch);
					o.write(r, s, ch);
					sendTo(HOST_ID, r.bytes(), true);
					copyDiscrete(o.sent, s);
				}
				o.sentOnce = true;
			} else if (o.bufCount > 0 && o.sample(renderT, s)) {
				o.applyContinuous(s, o.mask & NetObject.CONTINUOUS, bound, true);
			}
		}
	}

	double hostNowEstimate(long now) {
		return offsetSet ? now - clockOffset : now;
	}

	double interpDelay() {
		return Math.max(80, 2.2 * 1000.0 / Math.max(1, cfg.sendRate));
	}

	private void giveUp(String reason) {
		lastError = reason;
		Transport tr = transport;
		transport = null;
		if (tr != null) tr.stop();
		role = NONE;
		connected = false;
		for (NetObject o : objects.values()) restoreLocal(o);
		players.clear();
		myId = 0;
		{ spy("HostLeft"); Net.fireHostLeft(); }
	}

	// ------------------------------------------------------------------ heartbeat

	private void heartbeat(long now) {
		byte[] ping = new NetCodec.Out().u8(PING).i64(now).bytes();
		if (role == HOST) {
			for (NetPlayer p : new ArrayList<NetPlayer>(players.values())) {
				if (p.id == HOST_ID) continue;
				if (p.connection >= 0 && !p.lost) transport.sendReliable(p.connection, ping);
				if (!p.lost && now - p.lastSeen > LOST_MS) markLost(p, now);
				if (p.lost && now - p.lostSince > SEAT_MS) removePlayer(p, "lost");
			}
		} else if (role == CLIENT && connected) {
			transport.sendReliable(0, ping);
			if (!hostLost && now - hostLastSeen > LOST_MS) {
				hostLost = true;
				hostLostSince = now;
			}
			if (hostLost && now - hostLostSince > SEAT_MS) giveUp("host silent");
		}
	}

	private void markLost(NetPlayer p, long now) {
		if (p.lost) return;
		p.lost = true;
		p.lostSince = now;
		if (p.connection >= 0) connToPlayer.remove(p.connection);
		p.connection = -1;
	}

	private void removePlayer(NetPlayer p, String reason) {
		players.remove(p.id);
		if (p.connection >= 0) {
			connToPlayer.remove(p.connection);
			transport.close(p.connection);
		}
		// their copies go away, seats come back to the host
		for (NetObject o : new ArrayList<NetObject>(objects.values())) {
			if (o.owner != p.id) continue;
			if (o.spawned) despawn(o);
			else setOwner(o, HOST_ID);
		}
		sendAll(new NetCodec.Out().u8(PLAYER_LEFT).var(p.id).str(p.name).str(reason).bytes(), true, p.id);
		{ spy("PlayerLeft", p.id, p.name); Net.firePlayerLeft(p.id, p.name); }
	}

	// ================================================================== receiving

	private void handle(Object[] in) {
		String k = (String) in[0];
		if (k.equals("f")) {
			searching = false;
			@SuppressWarnings("unchecked")
			List<HostInfo> found = (List<HostInfo>) in[1];
			rooms.clear();
			for (HostInfo h : found) if (h.game.equals(cfg.game) || cfg.game.isEmpty()) rooms.add(h);
			for (HostInfo h : rooms) { spy("GameFound", h); Net.fireGameFound(h); }
			{ spy("SearchDone"); Net.fireSearchDone(); }
			return;
		}
		if (k.equals("j")) {
			Transport tr = (Transport) in[1];
			joining = false;
			if ((Integer) in[2] != joinGen || (role == NONE && !reconnecting && stoppedSinceJoin)) {
				tr.stop(); // the player gave up meanwhile
				return;
			}
			if (transport != null && transport != tr) transport.stop();
			transport = tr;
			role = CLIENT;
			connected = true;
			hostLost = false;
			hostLastSeen = now();
			NetCodec.Out o = new NetCodec.Out().u8(HELLO).str(cfg.game).str(cfg.version).str(myName).i64(token);
			transport.sendReliable(0, o.bytes());
			return;
		}
		if (k.equals("x")) {
			joining = false;
			if ((Integer) in[3] != joinGen) return;
			String why = String.valueOf(in[2]);
			if (reconnecting) {
				// refused = the host app is gone (not just paused): no point waiting for the seat
				if (why.toLowerCase().contains("refused")) {
					reconnecting = false;
					giveUp("host left");
				}
				return; // keep trying until the seat expires
			}
			lastError = why;
			{ spy("JoinFailed", "cannot connect: " + lastError); Net.fireJoinFailed("cannot connect: " + lastError); }
			return;
		}
		int conn = (Integer) in[1];
		if (k.equals("c")) return; // the HELLO tells who it is
		if (k.equals("d")) {
			onDisconnect(conn);
			return;
		}
		handleMessage(conn, (byte[]) in[2]);
	}

	private void onDisconnect(int conn) {
		if (role == HOST) {
			Integer pid = connToPlayer.remove(conn);
			if (pid == null) return;
			NetPlayer p = players.get(pid);
			if (p != null) markLost(p, now());
		} else if (role == CLIENT && connected && !closing) {
			connected = false;
			if (myId == 0) return; // never joined: the REJECT / failure already said why
			// the host may come back (it was paused): try again while our seat is kept
			reconnecting = true;
			hostLostSince = now();
			lastReconnectTry = now();
		}
	}

	private void handleMessage(int conn, byte[] data) {
		NetCodec.In in = new NetCodec.In(data);
		int type = in.u8();
		long now = now();
		NetPlayer from = null;
		if (role == HOST && conn >= 0) {
			Integer pid = connToPlayer.get(conn);
			from = pid == null ? null : players.get(pid);
			if (from != null) {
				from.lastSeen = now;
				if (from.lost) {
					from.lost = false; // came back without reconnecting
				}
			}
		} else if (role == CLIENT) {
			hostLastSeen = now;
			hostLost = false;
		}
		switch (type) {
			case HELLO:
				if (role == HOST) onHello(conn, in, now);
				break;
			case WELCOME:
				if (role == CLIENT) onWelcome(in, now);
				break;
			case REJECT: {
				int code = in.u8();
				String text = in.str();
				lastError = text;
				closing = true;
				Transport tr = transport;
				transport = null;
				role = NONE;
				connected = false;
				reconnecting = false;
				if (tr != null) tr.stop();
				{ spy("JoinFailed", text); Net.fireJoinFailed(text); }
				break;
			}
			case PLAYER_JOIN: {
				int id = in.var();
				String name = in.str();
				NetPlayer p = new NetPlayer(id, name);
				players.put(id, p);
				{ spy("PlayerJoined", id, name); Net.firePlayerJoined(id, name); }
				break;
			}
			case PLAYER_LEFT: {
				int id = in.var();
				String name = in.str();
				players.remove(id);
				{ spy("PlayerLeft", id, name); Net.firePlayerLeft(id, name); }
				break;
			}
			case PING: {
				long t = in.i64();
				byte[] pong = new NetCodec.Out().u8(PONG).i64(t).bytes();
				if (role == HOST && from != null && from.connection >= 0) transport.sendReliable(from.connection, pong);
				else if (role == CLIENT && transport != null) transport.sendReliable(0, pong);
				break;
			}
			case PONG: {
				long t = in.i64();
				int rtt = (int) Math.max(0, now - t);
				if (role == HOST && from != null) from.ping = rtt;
				else hostPing = rtt;
				break;
			}
			case SNAP:
				if (role == CLIENT) onSnap(in, now);
				break;
			case OWNER_STATE:
				if (role == HOST && from != null) onOwnerState(from, in, now);
				break;
			case CORRECT:
				if (role == CLIENT) onCorrect(in);
				break;
			case STATE_R:
				onStateR(from, in, data);
				break;
			case SPAWN:
			case DESPAWN:
			case OWNER:
				if (role == CLIENT) onObjectMessage(type, in, data);
				break;
			case MSG:
				onMsg(from, in);
				break;
			case EVENT:
				onEvent(from, in);
				break;
			case LOAD_SCENE:
			case START: {
				if (role != CLIENT) break;
				epoch = in.var();
				String scene = in.str();
				objects.clear();
				pending.clear();
				if (type == START) cfg.gameStarted = true;
				if (scene.isEmpty()) {
					if (type == START) { spy("GameStarted"); Net.fireGameStarted(); }
				} else {
					fireStarted = type == START;
					openScene(scene);
				}
				break;
			}
			case BYE:
				if (role == HOST && from != null) removePlayer(from, "left");
				else if (role == CLIENT) {
					closing = true;
					giveUp("host left");
				}
				break;
			case REQ_DESPAWN: {
				if (role != HOST || from == null) break;
				if (in.var() != epoch) break;
				NetObject o = objects.get(in.var());
				if (o != null && o.owner == from.id) despawn(o);
				break;
			}
		}
	}

	private void onHello(int conn, NetCodec.In in, long now) {
		String game = in.str(), version = in.str(), name = in.str();
		long tok = in.i64();
		if (!cfg.game.isEmpty() && !game.equals(cfg.game)) {
			reject(conn, REJECT_GAME, "This room plays another game (" + cfg.game + ").");
			return;
		}
		if (!version.equals(cfg.version)) {
			reject(conn, REJECT_VERSION, "Different game version: the room has " + cfg.version + ", you have " + version + ".");
			return;
		}
		// a player coming back within 30 s gets the same seat and elements
		NetPlayer p = null;
		for (NetPlayer q : players.values()) if (q.id != HOST_ID && q.token == tok) p = q;
		boolean resume = p != null;
		if (p == null) {
			int active = 0;
			for (NetPlayer q : players.values()) if (!q.lost) active++;
			if (active >= Math.max(1, cfg.maxPlayers)) {
				reject(conn, REJECT_FULL, "The room is full (" + cfg.maxPlayers + " players).");
				return;
			}
			int id = 2;
			while (players.containsKey(id)) id++;
			p = new NetPlayer(id, name.isEmpty() ? "Player " + id : name);
			p.token = tok;
			players.put(id, p);
		}
		if (p.connection >= 0 && p.connection != conn) connToPlayer.remove(p.connection);
		p.connection = conn;
		p.lost = false;
		p.lastSeen = now;
		connToPlayer.put(conn, p.id);
		// welcome: who is here, the scene, every networked element with its state
		NetCodec.Out o = new NetCodec.Out(512).u8(WELCOME).var(p.id).var(cfg.maxPlayers).var(cfg.sendRate).var(epoch).i32((int) now)
				.str(bound == null ? "" : sceneName(bound)).bool(cfg.gameStarted).bool(resume);
		o.var(players.size());
		for (NetPlayer q : players.values()) o.var(q.id).str(q.name);
		if (!resume) seatOwners(p.id);
		if (!resume) spawnPlayer(p.id);
		writeWorld(o);
		transport.sendReliable(conn, o.bytes());
		if (!resume) {
			sendAll(new NetCodec.Out().u8(PLAYER_JOIN).var(p.id).str(p.name).bytes(), true, p.id);
			{ spy("PlayerJoined", p.id, p.name); Net.firePlayerJoined(p.id, p.name); }
		}
	}

	/** Scene elements set to belong to this seat ("2", "3"...) go to the player who sits there. */
	private void seatOwners(int player) {
		for (NetObject o : objects.values()) if (!o.spawned && seatOf(o) == player && o.owner != player) setOwner(o, player);
	}

	static String sceneName(StageImp st) {
		return st.sceneName != null && !st.sceneName.isEmpty() ? st.sceneName : st.getClass().getSimpleName();
	}

	private void reject(int conn, int code, String text) {
		transport.sendReliable(conn, new NetCodec.Out().u8(REJECT).u8(code).str(text).bytes());
		final int c = conn;
		final Transport tr = transport;
		Thread t = new Thread(new Runnable() {
			public void run() {
				try {
					Thread.sleep(300);
				} catch (InterruptedException ignored) {
				}
				tr.close(c);
			}
		}, "star2d-net-reject");
		t.setDaemon(true);
		t.start();
	}

	/** Every networked element: spawned copies (to make them) and the current state of all. */
	private void writeWorld(NetCodec.Out o) {
		ArrayList<NetObject> list = new ArrayList<NetObject>(objects.values());
		o.var(list.size());
		NetObject.State s = new NetObject.State();
		for (NetObject n : list) {
			o.bool(n.spawned);
			if (n.spawned) o.str(n.template).str(n.item.getName());
			o.var(n.owner).var(n.mask).var(n.syncVars.size());
			for (String v : n.syncVars) o.str(v);
			n.capture(s, bound);
			n.write(o, s, n.mask);
		}
	}

	private void onWelcome(NetCodec.In in, long now) {
		myId = in.var();
		cfg.maxPlayers = in.var();
		cfg.sendRate = in.var();
		int ep = in.var();
		int hostTime = in.i32();
		String scene = in.str();
		cfg.gameStarted = in.bool();
		boolean resume = in.bool();
		clockOffset = now - hostTime;
		offsetSet = true;
		int n = in.var();
		players.clear();
		for (int i = 0; i < n; i++) {
			int id = in.var();
			players.put(id, new NetPlayer(id, in.str()));
		}
		boolean wasReconnecting = reconnecting;
		reconnecting = false;
		// the rest (the world) is applied once the host's scene runs here
		byte[] world = java.util.Arrays.copyOfRange(in.b, in.p, in.b.length);
		NetCodec.Out w = new NetCodec.Out(world.length + 8).u8(WORLD).var(ep);
		for (byte b : world) w.u8(b);
		boolean sameScene = bound != null && sceneName(bound).equalsIgnoreCase(scene) && ep == epoch;
		epoch = ep;
		if (sameScene || scene.isEmpty()) applyWorld(w.bytes());
		else {
			objects.clear();
			pending.clear();
			pending.add(w.bytes());
			openScene(scene);
		}
		if (!wasReconnecting || !resume) { spy("Connected"); Net.fireConnected(); }
	}

	/** World of a WELCOME: spawned copies made here, every state applied. */
	private void applyWorld(byte[] data) {
		NetCodec.In in = new NetCodec.In(data);
		in.u8();
		if (in.var() != epoch) return;
		int n = in.var();
		NetObject.State s = new NetObject.State();
		for (int i = 0; i < n; i++) {
			boolean spawned = in.bool();
			String template = spawned ? in.str() : "", name = spawned ? in.str() : "";
			int owner = in.var(), mask = in.var(), nv = in.var();
			ArrayList<String> vars = new ArrayList<String>();
			for (int k = 0; k < nv; k++) vars.add(in.str());
			int id = in.var(), fields = in.var();
			NetObject o = spawned ? objects.get(id) : objects.get(id);
			if (spawned && o == null) o = makeCopy(id, template, name, owner, mask, vars);
			NetObject.read(in, s, fields, o != null ? o.is3D() : guess3D());
			if (o == null) continue;
			o.owner = owner;
			applyRole(o);
			o.applyContinuous(s, fields & NetObject.CONTINUOUS, bound, false);
			o.applyDiscrete(s, fields & NetObject.DISCRETE, bound);
			o.sent.set(s);
		}
	}

	private boolean guess3D() {
		return bound != null && bound.is3D();
	}

	/** A networked copy made from a template element of this scene (client side). */
	private NetObject makeCopy(int id, String template, String name, int owner, int mask, List<String> vars) {
		PlayerItem existing = find(name);
		PlayerItem copy = existing;
		if (copy == null) {
			PlayerItem tpl = find(template);
			if (tpl == null) return null;
			try {
				copy = tpl.getClone(name);
			} catch (RuntimeException e) {
				return null;
			}
		}
		if (copy == null) return null;
		show(copy);
		NetObject o = new NetObject(id < 0 ? nextSpawnId++ : id, copy, owner, mask);
		o.spawned = true;
		o.template = template;
		o.syncVars.addAll(vars);
		objects.put(o.id, o);
		applyRole(o);
		return o;
	}

	private void onSnap(NetCodec.In in, long now) {
		if (in.var() != epoch || !sceneReady) return;
		int stamp = in.i32();
		// the host clock: keep the smallest delay seen (the least jitter)
		double off = now - stamp;
		if (!offsetSet || off < clockOffset) {
			clockOffset = off;
			offsetSet = true;
		} else clockOffset += (off - clockOffset) * 0.002; // follow slow drifts
		NetObject.State s = new NetObject.State();
		while (in.more()) {
			int id = in.var(), fields = in.var();
			NetObject o = objects.get(id);
			boolean d3 = o != null ? o.is3D() : guess3D();
			NetObject.read(in, s, fields, d3);
			if (o == null || o.owner == myId) continue;
			// fields not in this snapshot keep their last value
			if (o.bufCount > 0) {
				NetObject.State last = o.buf[o.bufCount - 1];
				if ((fields & NetObject.POS) == 0) {
					s.x = last.x;
					s.y = last.y;
					s.z = last.z;
				}
				if ((fields & NetObject.ROT) == 0) {
					s.rx = last.rx;
					s.ry = last.ry;
					s.rz = last.rz;
				}
				if ((fields & NetObject.VEL) == 0) {
					s.vx = last.vx;
					s.vy = last.vy;
					s.vz = last.vz;
				}
			} else {
				NetObject.State cur = new NetObject.State();
				o.capture(cur, bound);
				if ((fields & NetObject.POS) == 0) {
					s.x = cur.x;
					s.y = cur.y;
					s.z = cur.z;
				}
				if ((fields & NetObject.ROT) == 0) {
					s.rx = cur.rx;
					s.ry = cur.ry;
					s.rz = cur.rz;
				}
			}
			o.push(s, stamp);
		}
	}

	private void onOwnerState(NetPlayer from, NetCodec.In in, long now) {
		if (in.var() != epoch) return;
		int ack = in.var();
		int id = in.var(), fields = in.var();
		NetObject o = objects.get(id);
		NetObject.State s = new NetObject.State();
		NetObject.read(in, s, fields, o != null ? o.is3D() : guess3D());
		if (o == null || o.owner != from.id) return;
		if (ack < o.correctionSeq) return; // sent before our correction arrived there
		// keep the discrete fields the host knows
		NetObject.State keep = o.ownerState;
		s.vis = keep.vis;
		s.img = keep.img;
		s.anim = keep.anim;
		s.text = keep.text;
		s.vars.clear();
		s.vars.putAll(keep.vars);
		if (!o.hasOwnerState) {
			NetObject.State cur = new NetObject.State();
			o.capture(cur, bound);
			copyDiscrete(s, cur);
		}
		o.ownerState.set(s);
		o.ownerTime = now;
		o.hasOwnerState = true;
		o.applyContinuous(s, fields, bound, true);
	}

	private void onCorrect(NetCodec.In in) {
		if (in.var() != epoch) return;
		int seq = in.var();
		int id = in.var(), fields = in.var();
		NetObject o = objects.get(id);
		NetObject.State s = new NetObject.State();
		NetObject.read(in, s, fields, o != null ? o.is3D() : guess3D());
		if (o == null) return;
		o.ackedCorrection = Math.max(o.ackedCorrection, seq);
		o.applyContinuous(s, fields & NetObject.CONTINUOUS, bound, false);
		o.applied.set(o.sent);
		o.applyDiscrete(s, fields & NetObject.DISCRETE, bound);
		copyDiscrete(o.sent, s);
	}

	private void onStateR(NetPlayer from, NetCodec.In in, byte[] raw) {
		int ep = in.var();
		if (ep != epoch) return;
		if (role == CLIENT && !sceneReady) {
			pending.add(raw);
			return;
		}
		int id = in.var(), fields = in.var();
		NetObject o = objects.get(id);
		NetObject.State s = new NetObject.State();
		NetObject.read(in, s, fields, o != null ? o.is3D() : guess3D());
		if (o == null) return;
		if (role == HOST) {
			// a player changed something of its own element: the host takes it (and forwards it with its next changes)
			if (from == null || o.owner != from.id) return;
			o.applyDiscrete(s, fields, bound);
			copyDiscrete(o.ownerState, s);
			if (!o.hasOwnerState) o.ownerState.set(s);
		} else {
			if (o.owner == myId) return;
			o.applyDiscrete(s, fields, bound);
		}
	}

	private void onObjectMessage(int type, NetCodec.In in, byte[] raw) {
		int ep = in.var();
		if (ep != epoch) return;
		if (!sceneReady) {
			pending.add(raw);
			return;
		}
		if (type == SPAWN) {
			int id = in.var();
			String template = in.str(), name = in.str();
			int owner = in.var(), mask = in.var(), nv = in.var();
			ArrayList<String> vars = new ArrayList<String>();
			for (int k = 0; k < nv; k++) vars.add(in.str());
			NetObject o = objects.get(id);
			if (o == null) o = makeCopy(id, template, name, owner, mask, vars);
			if (o == null) return;
			int oid = in.var(), fields = in.var();
			NetObject.State s = new NetObject.State();
			NetObject.read(in, s, fields, o.is3D());
			o.owner = owner;
			applyRole(o);
			o.applyContinuous(s, fields & NetObject.CONTINUOUS, bound, false);
			o.applyDiscrete(s, fields & NetObject.DISCRETE, bound);
			o.sent.set(s);
		} else if (type == DESPAWN) {
			NetObject o = objects.remove(in.var());
			if (o != null && o.alive()) {
				try {
					o.item.destroy();
				} catch (RuntimeException ignored) {
				}
			}
		} else if (type == OWNER) {
			NetObject o = objects.get(in.var());
			if (o == null) return;
			o.owner = in.var();
			o.bufCount = 0;
			applyRole(o);
		}
	}

	// ------------------------------------------------------------------ messages

	/** to: 0 everyone (but me), 1 the host, n a player. */
	void message(int to, String name, String data) {
		if (role == NONE) return;
		NetCodec.Out o = new NetCodec.Out().u8(MSG).var(myId).var(to).str(name).str(data);
		byte[] b = o.bytes();
		if (role == HOST) {
			if (to == 0) sendAll(b, true, -1);
			else if (to == HOST_ID) { spy("Message", name, data, myId); Net.fireMessage(name, data, myId); }
			else sendTo(to, b, true);
		} else {
			if (to == myId) { spy("Message", name, data, myId); Net.fireMessage(name, data, myId); }
			else transport.sendReliable(0, b);
		}
	}

	/** Runs the message event on every phone, this one included. */
	void eventForAll(String name, String data) {
		if (role == NONE) {
			{ spy("Message", name, data, 0); Net.fireMessage(name, data, 0); }
			return;
		}
		{ spy("Message", name, data, myId); Net.fireMessage(name, data, myId); }
		byte[] b = new NetCodec.Out().u8(EVENT).var(myId).str(name).str(data).bytes();
		if (role == HOST) sendAll(b, true, -1);
		else transport.sendReliable(0, b);
	}

	private void onMsg(NetPlayer fromPlayer, NetCodec.In in) {
		int from = in.var(), to = in.var();
		String name = in.str(), data = in.str();
		if (role == HOST) {
			if (fromPlayer == null) return;
			from = fromPlayer.id; // never trust the id written by a client
			byte[] relay = new NetCodec.Out().u8(MSG).var(from).var(to).str(name).str(data).bytes();
			if (to == 0) {
				{ spy("Message", name, data, from); Net.fireMessage(name, data, from); }
				sendAll(relay, true, from);
			} else if (to == HOST_ID) { spy("Message", name, data, from); Net.fireMessage(name, data, from); }
			else sendTo(to, relay, true);
		} else { spy("Message", name, data, from); Net.fireMessage(name, data, from); }
	}

	private void onEvent(NetPlayer fromPlayer, NetCodec.In in) {
		int from = in.var();
		String name = in.str(), data = in.str();
		if (role == HOST) {
			if (fromPlayer == null) return;
			from = fromPlayer.id;
			{ spy("Message", name, data, from); Net.fireMessage(name, data, from); }
			sendAll(new NetCodec.Out().u8(EVENT).var(from).str(name).str(data).bytes(), true, from);
		} else { spy("Message", name, data, from); Net.fireMessage(name, data, from); }
	}
}
