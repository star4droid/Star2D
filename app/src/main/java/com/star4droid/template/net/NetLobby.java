package com.star4droid.template.net;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;
import com.kotcrab.vis.ui.widget.VisWindow;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.GameStrings;

/**
 * Ready-made lobby window (NETWORK element "Lobby", or the block "show lobby"): your name, Host / Search, the rooms found
 * (tap one to join), the players of the room, Start (host: opens the NETWORK element's Game Scene for everyone) and Leave.
 * Texts can be translated with the game translations (keys net_lobby, net_host, net_search, net_start, net_leave ...).
 */
final class NetLobby {
	private NetLobby() {
	}

	private static VisWindow win;
	private static StageImp owner;
	private static VisLabel status;
	private static VisTable rooms, players;
	private static VisTextButton hostBtn, searchBtn, startBtn, leaveBtn;
	private static VisTextField name;
	private static StageImp pendingStage;
	private static boolean dirty;
	private static float searchTimer;
	private static String error = "";

	private static String t(String key, String def) {
		try {
			if (GameStrings.has(key)) return GameStrings.tr(key);
		} catch (Throwable ignored) {
		}
		return def;
	}

	/** Shows it once the scene runs (from the NETWORK element while the scene is created). */
	static void showLater(StageImp st) {
		pendingStage = st;
	}

	static void show(StageImp st) {
		if (st == null) return;
		st = st.activeStage();
		if (!VisUI.isLoaded()) VisUI.load(VisUI.SkinScale.X2);
		close();
		owner = st;
		Stage ui = st.getUiStage();
		final VisWindow w = new VisWindow(t("net_lobby", "Multiplayer"));
		win = w;
		w.setModal(true);
		w.setMovable(false);
		w.defaults().pad(6);
		name = new VisTextField(Net.myName());
		name.setMessageText(t("net_name", "Your name"));
		w.add(new VisLabel(t("net_name", "Your name"))).left();
		w.add(name).growX().colspan(2).row();
		hostBtn = new VisTextButton(t("net_host", "Host a game"));
		searchBtn = new VisTextButton(t("net_search", "Search"));
		w.add(hostBtn).growX();
		w.add(searchBtn).growX().colspan(2).row();
		w.add(new VisLabel(t("net_rooms", "Rooms on this network"))).left().colspan(3).row();
		rooms = new VisTable();
		rooms.top();
		VisScrollPane rs = new VisScrollPane(rooms);
		rs.setFadeScrollBars(false);
		w.add(rs).grow().colspan(3).height(ui.getHeight() * 0.22f).row();
		w.add(new VisLabel(t("net_players", "Players"))).left().colspan(3).row();
		players = new VisTable();
		players.top();
		VisScrollPane ps = new VisScrollPane(players);
		w.add(ps).grow().colspan(3).height(ui.getHeight() * 0.18f).row();
		status = new VisLabel("");
		status.setWrap(true);
		status.setAlignment(Align.center);
		w.add(status).growX().colspan(3).row();
		startBtn = new VisTextButton(t("net_start", "Start"));
		leaveBtn = new VisTextButton(t("net_leave", "Leave"));
		w.add(startBtn).growX().colspan(2);
		w.add(leaveBtn).growX().row();
		hostBtn.addListener(new ChangeListener() {
			public void changed(ChangeEvent e, Actor a) {
				applyName();
				error = "";
				Net.host();
				dirty = true;
			}
		});
		searchBtn.addListener(new ChangeListener() {
			public void changed(ChangeEvent e, Actor a) {
				applyName();
				error = "";
				Net.findGames();
				dirty = true;
			}
		});
		startBtn.addListener(new ChangeListener() {
			public void changed(ChangeEvent e, Actor a) {
				if (Net.isHost()) Net.startForAll(Net.S.cfg.gameScene);
			}
		});
		leaveBtn.addListener(new ChangeListener() {
			public void changed(ChangeEvent e, Actor a) {
				if (Net.isOnline()) Net.leave();
				dirty = true;
			}
		});
		float W = Math.min(ui.getWidth() * 0.92f, Math.max(560, ui.getWidth() * 0.6f));
		w.setSize(W, Math.min(ui.getHeight() * 0.94f, ui.getHeight() * 0.9f));
		w.setPosition((ui.getWidth() - w.getWidth()) / 2f, (ui.getHeight() - w.getHeight()) / 2f);
		ui.addActor(w);
		searchTimer = 0;
		if (!Net.isOnline()) Net.findGames();
		dirty = true;
		rebuild();
	}

	private static void applyName() {
		if (name != null && !name.getText().trim().isEmpty()) Net.setPlayerName(name.getText().trim());
	}

	static void close() {
		pendingStage = null;
		if (win != null) win.remove();
		win = null;
		owner = null;
	}

	static void refresh() {
		dirty = true;
	}

	static void failed(String reason) {
		error = reason == null ? "" : reason;
		dirty = true;
	}

	/** Every frame (Net.update). */
	static void tick(StageImp st) {
		if (pendingStage != null && st != null && st.onCreateCalled()) {
			StageImp p = pendingStage;
			pendingStage = null;
			show(p);
		}
		if (win == null) return;
		if (st != null && owner != null && st != owner && st.activeStage() != owner) {
			close(); // another scene runs now
			return;
		}
		// keep looking for rooms while not in one
		if (!Net.isOnline()) {
			searchTimer += com.badlogic.gdx.Gdx.graphics.getDeltaTime();
			if (searchTimer > 3f && !Net.isSearching()) {
				searchTimer = 0;
				Net.findGames();
			}
		}
		if (dirty) rebuild();
	}

	private static void rebuild() {
		if (win == null) return;
		dirty = false;
		boolean online = Net.isOnline(), host = Net.isHost();
		hostBtn.setDisabled(online);
		searchBtn.setDisabled(online);
		name.setDisabled(online);
		startBtn.setVisible(host);
		leaveBtn.setDisabled(!online);
		rooms.clearChildren();
		if (!online) {
			if (Net.roomCount() == 0) rooms.add(new VisLabel(Net.isSearching() ? t("net_searching", "Searching...") : t("net_none", "No rooms yet"))).pad(4);
			for (int i = 0; i < Net.roomCount(); i++) {
				final int idx = i;
				VisTextButton b = new VisTextButton(Net.roomName(i) + "   " + Net.roomPlayers(i) + "/" + Net.roomMax(i));
				b.addListener(new ChangeListener() {
					public void changed(ChangeEvent e, Actor a) {
						applyName();
						error = "";
						Net.join(idx);
						status.setText(t("net_joining", "Joining..."));
					}
				});
				rooms.add(b).growX().pad(2).row();
			}
		} else rooms.add(new VisLabel(host ? t("net_hosting", "You host this room") + "  " + Net.myAddress() : t("net_joined", "In the room"))).pad(4);
		players.clearChildren();
		for (int i = 0; i < Net.playerCount() + 4 && Net.playerAt(i) != 0; i++) {
			int id = Net.playerAt(i);
			if (!Net.hasPlayer(id)) continue;
			String n = id + ". " + Net.playerName(id) + (id == Net.myId() ? "  (" + t("net_you", "you") + ")" : "") + (id == 1 ? "  ★" : "");
			players.add(new VisLabel(n)).left().pad(2).row();
		}
		String st;
		if (!error.isEmpty()) st = error;
		else if (host) st = Net.playerCount() + " / " + Net.maxPlayers() + " · " + t("net_wait_start", "press Start when everyone is here");
		else if (online) st = t("net_wait_host", "Waiting for the host to start");
		else st = t("net_pick", "Host a game, or tap a room to join");
		status.setText(st);
	}
}
