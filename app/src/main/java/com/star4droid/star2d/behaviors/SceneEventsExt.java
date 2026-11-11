package com.star4droid.star2d.behaviors;

/**
 * Scene events of the path finding, animation and network systems (besides OnCreate, OnStep, OnPause, OnResume and the
 * collisions). Each one is a file Events/&lt;scene&gt;/public/&lt;name&gt;.java like the other scene events; the code generator
 * turns it into an override of the matching StageImp method, whose parameters the blocks use as variables. Pure Java.
 */
public final class SceneEventsExt {
	private SceneEventsExt() {
	}

	public static final String NAV = "nav", ANIM = "anim", NET = "net";

	public static final class Ev {
		public final String name, group, signature, en, ar;
		public final String[] params;

		Ev(String name, String group, String signature, String params, String en, String ar) {
			this.name = name;
			this.group = group;
			this.signature = signature;
			this.params = params.isEmpty() ? new String[0] : params.split(",");
			this.en = en;
			this.ar = ar;
		}
	}

	public static final Ev[] ALL = {
			new Ev("onPathEnd", NAV, "public void onPathEnd(PlayerItem current)", "current",
					"An element reached the end of its path (ONCE mode). current = the element.", "وصل عنصر إلى نهاية مساره (وضع مرة واحدة). current = العنصر."),
			new Ev("onNoPath", NAV, "public void onNoPath(PlayerItem current)", "current",
					"No way to the target was found for an element (it is walled in).", "لم يوجد طريق إلى الهدف لعنصر (محاط بالجدران)."),
			new Ev("onPathBlocked", NAV, "public void onPathBlocked(PlayerItem current)", "current",
					"The way of a walking element got blocked; it looks for a new one by itself.", "انسد طريق عنصر يمشي؛ يبحث عن طريق جديد وحده."),
			new Ev("onTimelineEnd", ANIM, "public void onTimelineEnd(String timeline, PlayerItem current)", "timeline,current",
					"A timeline finished playing (ONCE). timeline = its name, current = the element it played on.", "انتهى تشغيل خط زمني (مرة واحدة). timeline = اسمه، current = العنصر."),
			new Ev("onTimelineMarker", ANIM, "public void onTimelineMarker(String timeline, String marker, PlayerItem current)", "timeline,marker,current",
					"A playing timeline passed one of its markers (e.g. \"hit\" to play a sound).", "مرّ خط زمني يعمل بإحدى علاماته (مثل \"hit\" لتشغيل صوت)."),
			new Ev("onTweenEnd", ANIM, "public void onTweenEnd(PlayerItem current, String property)", "current,property",
					"A tween of an element finished. property = what it animated (x, Opacity...).", "انتهت حركة tween لعنصر. property = الخاصية المتحركة."),
			new Ev("onPlayerJoined", NET, "public void onPlayerJoined(int player, String name)", "player,name",
					"A player joined the game (player = number, name).", "انضم لاعب إلى اللعبة (player = رقمه، name = اسمه)."),
			new Ev("onPlayerLeft", NET, "public void onPlayerLeft(int player, String name)", "player,name",
					"A player left (or was lost for 5 seconds).", "غادر لاعب (أو انقطع 5 ثوانٍ)."),
			new Ev("onGameFound", NET, "public void onGameFound(String room, String host, int players, int maxPlayers)", "room,host,players,maxPlayers",
					"Search: a game was found on the network.", "البحث: وُجدت لعبة على الشبكة."),
			new Ev("onConnected", NET, "public void onConnected()", "",
					"This phone joined a game (or opened one as host).", "انضم هذا الهاتف إلى لعبة (أو فتح واحدة كمضيف)."),
			new Ev("onDisconnected", NET, "public void onDisconnected(String reason)", "reason",
					"The connection to the game was lost.", "انقطع الاتصال باللعبة."),
			new Ev("onHostLeft", NET, "public void onHostLeft()", "",
					"The host closed the game.", "أغلق المضيف اللعبة."),
			new Ev("onNetMessage", NET, "public void onNetMessage(String name, String data, int from)", "name,data,from",
					"A message arrived from another player (name, data, from = player number).", "وصلت رسالة من لاعب آخر (name، data، from = رقم اللاعب)."),
			new Ev("onGameStarted", NET, "public void onGameStarted()", "",
					"The host started the game for everybody.", "بدأ المضيف اللعبة للجميع."),
			new Ev("onJoinFailed", NET, "public void onJoinFailed(String reason)", "reason",
					"Joining failed (full room, other version, no answer...).", "فشل الانضمام (غرفة ممتلئة، إصدار مختلف، لا رد...)."),
	};

	public static Ev find(String name) {
		if (name == null) return null;
		for (Ev e : ALL) if (e.name.equals(name)) return e;
		return null;
	}

	public interface Reader {
		/** Code of a scene event ("" when it has none). */
		String code(String event);
	}

	/** Overrides of the events that have code (part of the generated scene class body). */
	public static String overrides(Reader reader) {
		StringBuilder b = new StringBuilder();
		for (Ev e : ALL) {
			String code = reader.code(e.name);
			if (code == null || code.trim().isEmpty()) continue;
			b.append("\n\t@Override\n\t").append(e.signature).append(" {\n").append(code).append("\n\t}\n");
		}
		return b.toString();
	}
}
