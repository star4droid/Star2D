package com.star4droid.template.net;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless checks of the multiplayer core (tools/test_net.py): the codec, the room description, KryoNet on the loopback
 * address, and whole sessions in one JVM (a host and clients): join, names, version / full rejections, messages and their
 * relay, leaving, the host closing. No phone, no emulator.
 */
public class NetTest {
	static int fails = 0, checks = 0;

	static void check(boolean ok, String msg) {
		checks++;
		if (!ok) {
			fails++;
			System.out.println("FAIL: " + msg);
		}
	}

	static final class Rec implements Net.Spy {
		final String who;
		final List<String> events = new ArrayList<String>();

		Rec(String who) {
			this.who = who;
		}

		public synchronized void event(String name, Object... args) {
			StringBuilder sb = new StringBuilder(name);
			for (Object a : args) sb.append('|').append(a);
			events.add(sb.toString());
		}

		synchronized boolean has(String prefix) {
			for (String e : events) if (e.startsWith(prefix)) return true;
			return false;
		}

		synchronized int count(String prefix) {
			int n = 0;
			for (String e : events) if (e.startsWith(prefix)) n++;
			return n;
		}
	}

	static void setField(Object o, String name, Object v) throws Exception {
		java.lang.reflect.Field f = o.getClass().getDeclaredField(name);
		f.setAccessible(true);
		f.set(o, v);
	}

	static float cx(Fake f) {
		return f.getX() + f.getWidth() * 0.5f;
	}

	static float cy(Fake f) {
		return f.getY() + f.getHeight() * 0.5f;
	}

	private static com.badlogic.gdx.scenes.scene2d.Stage dummyStage;

	/** An element without a scene: a 50 x 50 actor (its "stage" is a blank object so it counts as alive). */
	static final class Fake extends com.badlogic.gdx.scenes.scene2d.Actor implements com.star4droid.template.Utils.PlayerItem {
		final com.star4droid.template.Utils.ChildsHolder holder = new com.star4droid.template.Utils.ChildsHolder(this);

		Fake(String name, float cx, float cy) {
			setName(name);
			setSize(50, 50);
			setPosition(cx - 25, cy - 25);
		}

		@Override
		public com.badlogic.gdx.scenes.scene2d.Stage getStage() {
			if (dummyStage == null) dummyStage = new org.objenesis.ObjenesisStd().newInstance(com.badlogic.gdx.scenes.scene2d.Stage.class);
			return dummyStage;
		}

		public void update() {
		}

		public com.badlogic.gdx.physics.box2d.Body getBody() {
			return null;
		}

		public void setScript(com.star4droid.template.Utils.ItemScript s) {
		}

		public <T extends com.star4droid.template.Utils.ItemScript> T getScript() {
			return null;
		}

		public com.star4droid.template.Utils.ChildsHolder getChildsHolder() {
			return holder;
		}

		public com.star4droid.star2d.ElementDefs.ItemDef getProperties() {
			return null;
		}

		public com.star4droid.template.Utils.PlayerItem getClone(String n) {
			return new Fake(n, getX() + 25, getY() + 25);
		}

		public com.star4droid.star2d.ElementDefs.ElementEvent getElementEvents() {
			return null;
		}

		@Override
		public String getName() {
			return super.getName();
		}
	}

	static NetSession session(String name, Rec rec, int tcp, int udp, String version, int max) {
		NetSession s = new NetSession();
		s.cfg.game = "TestGame";
		s.cfg.version = version;
		s.cfg.maxPlayers = max;
		s.cfg.tcpPort = tcp;
		s.cfg.udpPort = udp;
		s.myName = name;
		s.spy = rec;
		return s;
	}

	/** Runs the game loops of the sessions until the condition holds (or the time is up). */
	interface Cond {
		boolean ok();
	}

	static boolean pump(Cond c, long ms, NetSession... ss) throws Exception {
		long end = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < end) {
			for (NetSession s : ss) s.update(null, 0.016f);
			if (c.ok()) return true;
			Thread.sleep(8);
		}
		return c.ok();
	}

	public static void main(String[] a) {
		int code = 1;
		try {
			code = run();
		} catch (Throwable t) {
			t.printStackTrace(System.out);
		} finally {
			System.exit(code); // KryoNet threads would keep the JVM alive
		}
	}

	static int run() throws Exception {
		com.esotericsoftware.minlog.Log.set(com.esotericsoftware.minlog.Log.LEVEL_WARN);
		// ---------------- codec
		NetCodec.Out o = new NetCodec.Out();
		o.u8(7).bool(true).i16(-1234).i32(123456789).i64(-9876543210L).f32(3.5f).var(300).var(5).str("سلام hi");
		NetCodec.In in = new NetCodec.In(o.bytes());
		check(in.u8() == 7 && in.bool() && in.i16() == -1234 && in.i32() == 123456789 && in.i64() == -9876543210L && in.f32() == 3.5f
				&& in.var() == 300 && in.var() == 5 && in.str().equals("سلام hi") && !in.more(), "codec round trip");

		// ---------------- room description
		HostInfo h = new HostInfo();
		h.game = "G";
		h.version = "2";
		h.room = "Room A";
		h.host = "Ali";
		h.players = 2;
		h.maxPlayers = 4;
		h.tcpPort = 1;
		h.udpPort = 2;
		byte[] hb = h.toBytes();
		HostInfo h2 = HostInfo.parse(hb, hb.length, "10.0.0.5");
		check(h2 != null && h2.room.equals("Room A") && h2.players == 2 && h2.maxPlayers == 4 && h2.address.equals("10.0.0.5") && !h2.full(), "room info round trip");
		check(HostInfo.parse(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9}, 9, "x") == null, "foreign answer ignored");
		check(NetObject.varValue(NetObject.varText(5f)).equals(5) && NetObject.varValue(NetObject.varText(2.5f)).equals(2.5f)
				&& NetObject.varValue(NetObject.varText(true)).equals(Boolean.TRUE) && NetObject.varValue(NetObject.varText("ab")).equals("ab"), "variable values");
		check(Net.maskOf("pos,rot") == (NetObject.POS | NetObject.ROT) && Net.maskOf("") == NetObject.DEFAULT_MASK
				&& Net.maskOf("pos rot vel vis image anim text vars") == 255, "field masks");

		int base = 31000 + new java.util.Random().nextInt(6000);
		int tcp = base, udp = base + 1;

		// ---------------- raw transport on the loopback
		final List<byte[]> got = new ArrayList<byte[]>();
		final int[] conns = new int[1];
		KryoTransport server = new KryoTransport();
		server.startServer(tcp + 10, udp + 10, new Transport.Listener() {
			public void connected(int c) {
				conns[0] = c;
			}

			public void disconnected(int c) {
			}

			public void received(int c, byte[] d) {
				synchronized (got) {
					got.add(d);
				}
			}
		}, null);
		final List<byte[]> back = new ArrayList<byte[]>();
		KryoTransport client = new KryoTransport();
		client.connect("127.0.0.1", tcp + 10, udp + 10, 3000, new Transport.Listener() {
			public void connected(int c) {
			}

			public void disconnected(int c) {
			}

			public void received(int c, byte[] d) {
				synchronized (back) {
					back.add(d);
				}
			}
		});
		client.sendReliable(0, new byte[] {1, 2, 3});
		client.sendFast(0, new byte[] {4, 5});
		long end = System.currentTimeMillis() + 3000;
		while (System.currentTimeMillis() < end) {
			synchronized (got) {
				if (got.size() >= 2) break;
			}
			Thread.sleep(10);
		}
		synchronized (got) {
			check(got.size() >= 2, "server received reliable + fast bytes (" + got.size() + ")");
		}
		server.sendReliable(conns[0], new byte[] {9});
		end = System.currentTimeMillis() + 3000;
		while (System.currentTimeMillis() < end) {
			synchronized (back) {
				if (!back.isEmpty()) break;
			}
			Thread.sleep(10);
		}
		synchronized (back) {
			check(back.size() == 1 && back.get(0)[0] == 9, "client received bytes from the server");
		}
		List<HostInfo> found = new KryoTransport().discover(udp + 10, 800);
		System.out.println("discovery on this machine: " + found.size() + " answer(s) (broadcast may be blocked in a sandbox)");
		client.stop();
		server.stop();
		Thread.sleep(300);

		// ---------------- sessions: host + 2 clients
		final Rec hr = new Rec("host"), c1r = new Rec("c1"), c2r = new Rec("c2"), c3r = new Rec("c3"), badr = new Rec("bad");
		final NetSession host = session("Hana", hr, tcp, udp, "1.0", 3);
		check(host.host(null), "host opens the room");
		check(host.role == NetSession.HOST && host.myId == 1 && hr.has("Connected"), "host is player 1");
		final NetSession c1 = session("Omar", c1r, tcp, udp, "1.0", 3);
		c1.join("127.0.0.1", tcp, udp);
		check(pump(new Cond() {
			public boolean ok() {
				return c1.myId == 2 && host.players.size() == 2;
			}
		}, 6000, host, c1), "client 1 joins as player 2");
		check(c1r.has("Connected") && hr.has("PlayerJoined|2|Omar"), "join events");
		check("Hana".equals(c1.players.get(1).name) && "Omar".equals(host.players.get(2).name), "names on both sides");

		final NetSession c2 = session("Lina", c2r, tcp, udp, "1.0", 3);
		c2.join("127.0.0.1", tcp, udp);
		check(pump(new Cond() {
			public boolean ok() {
				return c2.myId == 3 && c1.players.size() == 3;
			}
		}, 6000, host, c1, c2), "client 2 joins as player 3, client 1 hears it");
		check(c1r.has("PlayerJoined|3|Lina"), "client 1 gets PlayerJoined for player 3");

		// ---------------- discovery of the room (broadcast; may be blocked on some machines)
		List<HostInfo> rooms = new KryoTransport().discover(udp, 1000);
		boolean seen = false;
		for (HostInfo r : rooms) if (r.room.equals("Hana") && r.players == 3 && r.maxPlayers == 3 && r.game.equals("TestGame")) seen = true;
		System.out.println("discovery found " + rooms.size() + " room answer(s): " + rooms);
		if (!rooms.isEmpty()) check(seen, "the room answers the search with its name and players: " + rooms);

		// ---------------- room full (max 3)
		final NetSession c3 = session("Late", c3r, tcp, udp, "1.0", 3);
		c3.join("127.0.0.1", tcp, udp);
		check(pump(new Cond() {
			public boolean ok() {
				return c3r.has("JoinFailed");
			}
		}, 6000, host, c1, c2, c3), "a fourth player is refused (room of 3)");
		check(c3.myId == 0 && c3.role == NetSession.NONE && host.players.size() == 3, "refused player is not in the room");

		// ---------------- version mismatch
		final NetSession bad = session("Old", badr, tcp, udp, "0.9", 3);
		bad.join("127.0.0.1", tcp, udp);
		check(pump(new Cond() {
			public boolean ok() {
				return badr.has("JoinFailed");
			}
		}, 6000, host, c1, c2, bad), "another version is refused");
		check(badr.count("JoinFailed|Different game version") == 1, "with a clear reason: " + badr.events);

		// ---------------- messages
		c1.message(0, "hello", "42");
		check(pump(new Cond() {
			public boolean ok() {
				return hr.has("Message|hello|42|2") && c2r.has("Message|hello|42|2");
			}
		}, 4000, host, c1, c2), "client -> everyone (the host relays): " + hr.events + " / " + c2r.events);
		check(!c1r.has("Message|hello"), "the sender does not get its own message");
		c2.message(1, "toHost", "x");
		check(pump(new Cond() {
			public boolean ok() {
				return hr.has("Message|toHost|x|3");
			}
		}, 4000, host, c1, c2), "client -> host");
		c2.message(2, "private", "y");
		check(pump(new Cond() {
			public boolean ok() {
				return c1r.has("Message|private|y|3");
			}
		}, 4000, host, c1, c2), "client -> another client through the host");
		check(!hr.has("Message|private"), "the host does not see a private message for player 2");
		host.eventForAll("boom", "1");
		check(pump(new Cond() {
			public boolean ok() {
				return hr.has("Message|boom|1|1") && c1r.has("Message|boom|1|1") && c2r.has("Message|boom|1|1");
			}
		}, 4000, host, c1, c2), "run for all reaches every phone, the host included");
		c1.eventForAll("ping2", "z");
		check(pump(new Cond() {
			public boolean ok() {
				return c1r.has("Message|ping2|z|2") && hr.has("Message|ping2|z|2") && c2r.has("Message|ping2|z|2");
			}
		}, 4000, host, c1, c2), "client run-for-all reaches everyone once");
		check(c1r.count("Message|ping2") == 1, "the sender runs it once");

		// ---------------- heartbeat measures a ping
		check(pump(new Cond() {
			public boolean ok() {
				return host.players.get(2).ping >= 0 && c1.hostPing >= 0 && host.players.get(2).lastSeen > 0;
			}
		}, 2500, host, c1, c2), "heartbeat");

		// ---------------- state sync with stand-in elements (no scene): snapshots, owner reports, discrete fields, corrections
		setField(host, "sceneReady", true);
		setField(c1, "sceneReady", true);
		final Fake hBox = new Fake("box", 100, 100), cBox = new Fake("box", 0, 0);
		final Fake hP2 = new Fake("p2", 300, 300), cP2 = new Fake("p2", 300, 300);
		int mask = NetObject.POS | NetObject.ROT | NetObject.VIS | NetObject.VARS;
		NetObject hob = new NetObject(1, hBox, 1, mask), cob = new NetObject(1, cBox, 1, mask);
		hob.syncVars.add("score");
		cob.syncVars.add("score");
		host.objects.put(1, hob);
		c1.objects.put(1, cob);
		final NetObject hp = new NetObject(2, hP2, 2, NetObject.POS | NetObject.VIS), cp = new NetObject(2, cP2, 2, NetObject.POS | NetObject.VIS);
		host.objects.put(2, hp);
		c1.objects.put(2, cp);
		hBox.setPosition(400 - 25, 250 - 25);
		check(pump(new Cond() {
			public boolean ok() {
				return Math.abs(cx(cBox) - 400) < 1 && Math.abs(cy(cBox) - 250) < 1;
			}
		}, 3000, host, c1), "the host's element shows on the client (" + cx(cBox) + "," + cy(cBox) + ")");
		hBox.setVisible(false);
		com.star4droid.template.Utils.ObjectVars.set(hBox, "score", 7);
		check(pump(new Cond() {
			public boolean ok() {
				return !cBox.isVisible() && Integer.valueOf(7).equals(com.star4droid.template.Utils.ObjectVars.get(cBox, "score"));
			}
		}, 3000, host, c1), "visibility and a synced variable arrive (" + cBox.isVisible() + ", " + com.star4droid.template.Utils.ObjectVars.get(cBox, "score") + ")");
		// smooth: positions in between while the host moves the element along a line
		final float[] last = {cx(cBox)};
		final boolean[] backwards = {false};
		for (int i = 0; i < 40; i++) {
			hBox.moveBy(5, 0);
			host.update(null, 0.016f);
			c1.update(null, 0.016f);
			if (cx(cBox) < last[0] - 0.01f) backwards[0] = true;
			last[0] = cx(cBox);
			Thread.sleep(16);
		}
		check(!backwards[0], "the copy never jumps back while following");
		// the client moves its own element: the host follows
		cP2.setPosition(500 - 25, 120 - 25);
		check(pump(new Cond() {
			public boolean ok() {
				return Math.abs(cx(hP2) - 500) < 1 && Math.abs(cy(hP2) - 120) < 1;
			}
		}, 3000, host, c1), "the owner's moves reach the host (" + cx(hP2) + "," + cy(hP2) + ")");
		// the host moves the client's element (respawn): the owner is corrected
		hP2.setPosition(50 - 25, 60 - 25);
		check(pump(new Cond() {
			public boolean ok() {
				return Math.abs(cx(cP2) - 50) < 1 && Math.abs(cy(cP2) - 60) < 1;
			}
		}, 3000, host, c1), "a host change corrects the owner (" + cx(cP2) + "," + cy(cP2) + ")");
		check(cp.ackedCorrection >= 1, "correction acknowledged");
		// and the owner goes on from there without being pulled back
		cP2.setPosition(80 - 25, 60 - 25);
		check(pump(new Cond() {
			public boolean ok() {
				return Math.abs(cx(hP2) - 80) < 1;
			}
		}, 3000, host, c1), "after the correction the owner's moves count again (" + cx(hP2) + ")");
		host.objects.clear();
		c1.objects.clear();

		// ---------------- leaving
		c2.stop(true);
		check(pump(new Cond() {
			public boolean ok() {
				return hr.has("PlayerLeft|3") && c1r.has("PlayerLeft|3");
			}
		}, 5000, host, c1, c2), "a player leaves: host and others hear it");
		check(host.players.size() == 2 && c1.players.size() == 2, "seat freed");
		host.stop(false);
		check(pump(new Cond() {
			public boolean ok() {
				return c1r.has("HostLeft");
			}
		}, 5000, host, c1), "the host closes: clients get HostLeft");
		check(c1.role == NetSession.NONE, "client back offline");
		c1.stop(false);
		Thread.sleep(300);

		System.out.println(fails == 0 ? ("NET OK (" + checks + " checks)") : (fails + " of " + checks + " checks FAILED"));
		return fails == 0 ? 0 : 1;
	}
}
