package com.star4droid.template.net;

import java.io.IOException;
import java.util.List;

/**
 * How bytes travel between the phones. The game logic ({@link NetSession}) only knows this interface, so another way
 * (Bluetooth, the internet) can be added later without changing the blocks. Connection 0 = the host, seen from a client.
 * Callbacks come on the network thread; {@link NetSession} hands them to the game (GL) thread.
 */
public interface Transport {
	interface Listener {
		void connected(int connection);

		void disconnected(int connection);

		void received(int connection, byte[] data);
	}

	/** Bytes the host answers a "who is there?" search with (room name, players ...). */
	interface DiscoveryReply {
		byte[] info();
	}

	/** Host: opens a reliable port (TCP) and a fast one (UDP) that also answers searches. */
	void startServer(int tcpPort, int udpPort, Listener listener, DiscoveryReply reply) throws IOException;

	/** Client: connects to a host (blocks up to timeoutMs). Falls back to the reliable port alone when UDP is blocked. */
	void connect(String host, int tcpPort, int udpPort, int timeoutMs, Listener listener) throws IOException;

	/** Rooms answering on the local network (blocks about timeoutMs). */
	List<HostInfo> discover(int udpPort, int timeoutMs);

	boolean sendReliable(int connection, byte[] data);

	/** Fast and unreliable (positions); goes the reliable way when the fast one is not available. */
	boolean sendFast(int connection, byte[] data);

	void close(int connection);

	/** Closes everything (server or client). */
	void stop();

	boolean isServer();
}
