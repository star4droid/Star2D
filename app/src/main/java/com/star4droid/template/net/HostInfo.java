package com.star4droid.template.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** A room found on the network (what a host answers to a search). */
public final class HostInfo {
	static final int MAGIC = 0x53324E31; // "S2N1"

	public String game = "", version = "", room = "", host = "", address = "";
	public int players, maxPlayers, tcpPort, udpPort;
	/** True while the host is still in its lobby (players may join any time anyway). */
	public boolean inLobby = true;
	/** When it was last seen (System.currentTimeMillis). */
	public long seen;

	public byte[] toBytes() {
		try {
			ByteArrayOutputStream bo = new ByteArrayOutputStream();
			DataOutputStream o = new DataOutputStream(bo);
			o.writeInt(MAGIC);
			o.writeUTF(game);
			o.writeUTF(version);
			o.writeUTF(room);
			o.writeUTF(host);
			o.writeShort(players);
			o.writeShort(maxPlayers);
			o.writeInt(tcpPort);
			o.writeInt(udpPort);
			o.writeBoolean(inLobby);
			o.flush();
			return bo.toByteArray();
		} catch (IOException e) {
			return new byte[0];
		}
	}

	/** Reads an answer; null when it is not a Star2D room. */
	public static HostInfo parse(byte[] data, int length, String address) {
		if (data == null || length < 8) return null;
		try {
			DataInputStream in = new DataInputStream(new ByteArrayInputStream(data, 0, length));
			if (in.readInt() != MAGIC) return null;
			HostInfo h = new HostInfo();
			h.game = in.readUTF();
			h.version = in.readUTF();
			h.room = in.readUTF();
			h.host = in.readUTF();
			h.players = in.readShort();
			h.maxPlayers = in.readShort();
			h.tcpPort = in.readInt();
			h.udpPort = in.readInt();
			try {
				h.inLobby = in.readBoolean();
			} catch (IOException ignored) {
			}
			h.address = address == null ? "" : address;
			h.seen = System.currentTimeMillis();
			return h;
		} catch (IOException e) {
			return null;
		}
	}

	public boolean full() {
		return maxPlayers > 0 && players >= maxPlayers;
	}

	@Override
	public String toString() {
		return room + " (" + players + "/" + maxPlayers + ") " + address;
	}
}
