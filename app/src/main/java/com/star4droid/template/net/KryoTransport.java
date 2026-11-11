package com.star4droid.template.net;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.ClientDiscoveryHandler;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.kryonet.ServerDiscoveryHandler;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link Transport} over KryoNet: TCP for what must arrive (joins, spawns, messages), UDP for the frequent positions, and
 * KryoNet's host discovery (the host answers a broadcast with a {@link HostInfo}). Every message is one byte array, the
 * only class registered in Kryo: nothing is serialized by reflection.
 */
public final class KryoTransport implements Transport {
	private static final int WRITE_BUFFER = 1 << 17, OBJECT_BUFFER = 1 << 15;
	private Server server;
	private Client client;
	private boolean clientUdp;

	private static void register(Kryo k) {
		k.register(byte[].class);
	}

	private static com.esotericsoftware.kryonet.Listener wrap(final Transport.Listener l) {
		return new com.esotericsoftware.kryonet.Listener() {
			@Override
			public void connected(Connection c) {
				l.connected(c.getID());
			}

			@Override
			public void disconnected(Connection c) {
				l.disconnected(c.getID());
			}

			@Override
			public void received(Connection c, Object o) {
				if (o instanceof byte[]) l.received(c.getID(), (byte[]) o);
			}
		};
	}

	@Override
	public void startServer(int tcpPort, int udpPort, Transport.Listener listener, final DiscoveryReply reply) throws IOException {
		stop();
		Server s = new Server(WRITE_BUFFER, OBJECT_BUFFER);
		register(s.getKryo());
		s.addListener(wrap(listener));
		s.setDiscoveryHandler(discoveryHandler(reply));
		s.bind(tcpPort, udpPort);
		runSafely(s, "star2d-net-server");
		server = s;
	}

	/**
	 * KryoNet's discovery answer gets the server's UDP channel through a package-private class, so the handler is a
	 * dynamic proxy that reaches the channel by reflection and sends the room description.
	 */
	private static ServerDiscoveryHandler discoveryHandler(final DiscoveryReply reply) {
		return (ServerDiscoveryHandler) Proxy.newProxyInstance(ServerDiscoveryHandler.class.getClassLoader(), new Class<?>[] {ServerDiscoveryHandler.class},
				new InvocationHandler() {
					@Override
					public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
						String n = m.getName();
						if (n.equals("onDiscoverHost")) {
							try {
								Object udp = args[0];
								InetSocketAddress from = (InetSocketAddress) args[1];
								java.lang.reflect.Field f = udp.getClass().getDeclaredField("datagramChannel");
								f.setAccessible(true);
								DatagramChannel ch = (DatagramChannel) f.get(udp);
								byte[] info = reply == null ? new byte[0] : reply.info();
								ch.send(ByteBuffer.wrap(info), from);
							} catch (Throwable t) {
								// the default empty answer still lets the client find the address
								return Boolean.FALSE;
							}
							return Boolean.TRUE;
						}
						if (n.equals("hashCode")) return System.identityHashCode(proxy);
						if (n.equals("equals")) return proxy == args[0];
						if (n.equals("toString")) return "Star2DDiscovery";
						return null;
					}
				});
	}

	@Override
	public void connect(String host, int tcpPort, int udpPort, int timeoutMs, Transport.Listener listener) throws IOException {
		stop();
		Client c = new Client(WRITE_BUFFER, OBJECT_BUFFER);
		register(c.getKryo());
		c.addListener(wrap(listener));
		runSafely(c, "star2d-net-client");
		try {
			c.connect(timeoutMs, host, tcpPort, udpPort);
			clientUdp = true;
		} catch (IOException udpFailed) {
			// some hotspots drop UDP: the reliable port alone still plays (a little less smooth)
			c.connect(timeoutMs, host, tcpPort);
			clientUdp = false;
		}
		client = c;
	}

	private volatile boolean stopped;

	/**
	 * Runs the KryoNet update loop on our own thread: KryoNet rethrows some errors (garbage arriving on the port) out of its
	 * loop, which would be an uncaught exception (an Android app dies of it). Here they are logged and the loop goes on.
	 */
	private void runSafely(final Runnable endpoint, String name) {
		stopped = false;
		Thread t = new Thread(new Runnable() {
			public void run() {
				int errors = 0;
				while (!stopped && errors < 50) {
					try {
						endpoint.run();
						return; // stop() was called
					} catch (Throwable e) {
						errors++;
						try {
							com.badlogic.gdx.Gdx.app.error("Net", "network loop: " + e);
						} catch (Throwable ignored) {
						}
					}
				}
			}
		}, name);
		t.setDaemon(true);
		t.start();
	}

	@Override
	public List<HostInfo> discover(int udpPort, int timeoutMs) {
		final List<HostInfo> found = new ArrayList<HostInfo>();
		Client c = new Client();
		try {
			c.setDiscoveryHandler(new ClientDiscoveryHandler() {
				@Override
				public DatagramPacket onRequestNewDatagramPacket() {
					return new DatagramPacket(new byte[1024], 1024);
				}

				@Override
				public void onDiscoveredHost(DatagramPacket p, Kryo kryo) {
					String addr = p.getAddress() == null ? "" : p.getAddress().getHostAddress();
					HostInfo h = HostInfo.parse(p.getData(), p.getLength(), addr);
					if (h == null) return;
					synchronized (found) {
						for (HostInfo o : found) if (o.address.equals(h.address) && o.tcpPort == h.tcpPort) return;
						found.add(h);
					}
				}

				@Override
				public void onFinally() {
				}
			});
			c.discoverHosts(udpPort, timeoutMs);
		} catch (Throwable ignored) {
		} finally {
			try {
				c.close();
			} catch (Throwable ignored) {
			}
		}
		return found;
	}

	@Override
	public boolean sendReliable(int connection, byte[] data) {
		try {
			if (server != null) server.sendToTCP(connection, data);
			else if (client != null) client.sendTCP(data);
			else return false;
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	@Override
	public boolean sendFast(int connection, byte[] data) {
		try {
			if (server != null) {
				Connection c = find(connection);
				if (c == null) return false;
				if (c.getRemoteAddressUDP() != null) c.sendUDP(data);
				else c.sendTCP(data);
			} else if (client != null) {
				if (clientUdp) client.sendUDP(data);
				else client.sendTCP(data);
			} else return false;
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	private Connection find(int id) {
		if (server == null) return null;
		for (Connection c : server.getConnections()) if (c.getID() == id) return c;
		return null;
	}

	@Override
	public void close(int connection) {
		try {
			if (server != null) {
				Connection c = find(connection);
				if (c != null) c.close();
			} else if (client != null) client.close();
		} catch (Throwable ignored) {
		}
	}

	@Override
	public void stop() {
		final Server s = server;
		final Client c = client;
		server = null;
		client = null;
		stopped = true;
		if (s == null && c == null) return;
		// stop() only asks the network thread to end: wait for it before closing its selector (closing it under the running
		// thread throws ClosedSelectorException there, an uncaught exception that would kill an Android app). Never on the
		// network thread itself, never blocking the game.
		Thread t = new Thread(new Runnable() {
			public void run() {
				try {
					if (s != null) {
						Thread ut = s.getUpdateThread();
						s.stop();
						if (ut != null && ut != Thread.currentThread()) ut.join(2000);
						s.dispose();
					}
				} catch (Throwable ignored) {
				}
				try {
					if (c != null) {
						Thread ut = c.getUpdateThread();
						c.stop();
						if (ut != null && ut != Thread.currentThread()) ut.join(2000);
						c.dispose();
					}
				} catch (Throwable ignored) {
				}
			}
		}, "star2d-net-stop");
		t.setDaemon(true);
		t.start();
	}

	@Override
	public boolean isServer() {
		return server != null;
	}
}
