package com.star4droid.template.net;

/** Settings of the session (from the NETWORK element of the scene). */
final class NetConfig {
	int maxPlayers = 4;
	/** Snapshots per second. */
	int sendRate = 20;
	String room = "";
	int tcpPort = 54555, udpPort = 54777;
	/** Element copied for every player who joins ("" = none). */
	String template = "";
	/** Copies are placed around the template at this distance (px / m), 0 = all at the template. */
	float spawnRadius = 0;
	/** Game name and version: a client must have the same to join. */
	String game = "", version = "1";
	/** Built-in lobby window at the start of the scene, and the scene its Start button opens for everyone. */
	boolean lobby = false;
	String gameScene = "";
	/** The host pressed Start (late joiners go straight to the game). */
	boolean gameStarted;
}
