package com.star4droid.template.net;

/** A player of the session (the host is player 1). */
public final class NetPlayer {
	public final int id;
	public String name;
	/** Transport connection of this player on the host (-1 for the host itself / unknown). */
	int connection = -1;
	long token;
	/** Host: last time something came from this player; lost = silent / disconnected, kept until seatUntil. */
	long lastSeen;
	boolean lost;
	long lostSince;
	int ping;
	/** Host: last ping time sent. */
	long pingSent;

	NetPlayer(int id, String name) {
		this.id = id;
		this.name = name == null ? "" : name;
	}

	public boolean isLost() {
		return lost;
	}

	public int ping() {
		return ping;
	}
}
