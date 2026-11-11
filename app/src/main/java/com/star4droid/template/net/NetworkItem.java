package com.star4droid.template.net;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.star4droid.star2d.ElementDefs.ElementEvent;
import com.star4droid.star2d.ElementDefs.NetworkDef;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.ChildsHolder;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.PlayerItem;

/** The NETWORK element at run time: hands its settings to {@link Net}; draws nothing. */
public class NetworkItem extends Actor implements PlayerItem {
	StageImp stage;
	NetworkDef def;
	ElementEvent elementEvent;
	private final ChildsHolder childsHolder = new ChildsHolder(this);
	private ItemScript itemScript;

	public NetworkItem(StageImp s) {
		stage = s;
	}

	public static NetworkItem create(StageImp stage, NetworkDef def, ElementEvent ev) {
		NetworkItem it = new NetworkItem(stage);
		it.def = def;
		it.elementEvent = ev;
		it.setup();
		return it;
	}

	private void setup() {
		setName(def.name);
		setTouchable(Touchable.disabled);
		setBounds(def.x, def.y, 1, 1);
		Net.configure(stage, Math.round(def.Max_Players), Math.round(def.Send_Rate), def.Room_Name, Math.round(def.TCP_Port), Math.round(def.UDP_Port),
				def.Player_Template, def.Spawn_Radius, def.Game_Version, def.Lobby, def.Game_Scene);
		if (stage != null) {
			stage.addActor(this);
			if (stage.onCreateCalled()) {
				if (elementEvent != null) elementEvent.onBodyCreated(this);
				if (getScript() != null) getScript().bodyCreated();
			}
		}
	}

	@Override
	public void draw(Batch batch, float parentAlpha) {
	}

	@Override
	public void update() {
	}

	@Override
	public Body getBody() {
		return null;
	}

	@Override
	public ChildsHolder getChildsHolder() {
		return childsHolder;
	}

	@Override
	public void setScript(ItemScript script) {
		itemScript = script;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends ItemScript> T getScript() {
		return (T) itemScript;
	}

	@Override
	public com.star4droid.star2d.ElementDefs.ItemDef getProperties() {
		return def;
	}

	@Override
	public ElementEvent getElementEvents() {
		return elementEvent;
	}

	@Override
	public PlayerItem getClone(String newName) {
		return create(stage, def.getClone(newName), elementEvent);
	}

	@Override
	public float getBodyX() {
		return getX();
	}

	@Override
	public float getBodyY() {
		return getY();
	}
}
