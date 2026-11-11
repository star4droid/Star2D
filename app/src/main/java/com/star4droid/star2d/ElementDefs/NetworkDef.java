package com.star4droid.star2d.ElementDefs;

import com.star4droid.template.Items.StageImp;
import com.star4droid.template.net.NetworkItem;

/**
 * The multiplayer settings of a scene (one NETWORK element per scene; invisible in the game). The session itself lives
 * across scenes: the lobby scene and the game scene each have their own NETWORK element.
 */
public class NetworkDef extends ItemDef {
	public ElementEvent elementEvents;
	public static final String TYPE = "NETWORK";
	public String name = "", parentName = "", Script = "", type = "UI";
	public float x = 0, y = 0, z = 100, width = 96, height = 96, rotation = 0;
	public float Max_Players = 4, Send_Rate = 20;
	/** Room name shown to the others ("" = the host's name). */
	public String Room_Name = "";
	public float TCP_Port = 54555, UDP_Port = 54777;
	/** Element copied for every player who joins (usually hidden); the copy belongs to that player. */
	public String Player_Template = "";
	/** The copies are placed around the template at this distance (0 = all at the template). */
	public float Spawn_Radius = 0;
	/** Players must have the same game version to join. */
	public String Game_Version = "1";
	/** Shows the ready-made lobby window when the scene starts; its Start button opens Game Scene for everyone. */
	public boolean Lobby = false;
	public String Game_Scene = "";
	public boolean Visible = true;

	public NetworkDef() {
	}

	public NetworkItem build(StageImp stage) {
		if (name.equals("")) throw new RuntimeException("NetworkDef error : set name to the item..!!");
		if (parentName != null && parentName.equals("")) parentName = name;
		return NetworkItem.create(stage, this, elementEvents);
	}

	public NetworkDef getClone(String newName) {
		NetworkDef c = new NetworkDef();
		for (java.lang.reflect.Field f : NetworkDef.class.getFields()) {
			if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || java.lang.reflect.Modifier.isFinal(f.getModifiers())) continue;
			try {
				f.set(c, f.get(this));
			} catch (Exception ignored) {
			}
		}
		c.name = newName;
		return c;
	}
}
