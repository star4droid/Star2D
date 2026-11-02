package com.star4droid.star2d.ui.behaviors;

import android.content.Context;

import com.star4droid.star2d.behaviors.BehaviorCatalog;
import com.star4droid.star2d.behaviors.BehaviorMeta;
import com.star4droid.star2d.behaviors.VarSpec;
import com.star4droid.star2d.ui.Tr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * The single place that turns the text of a behavior (name, description, group, parameter labels / descriptions, event names...) into
 * the language of the app. Everything that shows such text goes through here.
 *
 * Key scheme (the English text is always the default, so a missing key just shows English):
 * <pre>
 *   bhv_n_&lt;Id&gt;            display name                 bhv_d_&lt;Id&gt;            description
 *   bhv_p_&lt;Id&gt;_&lt;param&gt;    parameter label              bhv_pd_&lt;Id&gt;_&lt;param&gt;   parameter description
 *   bhv_e_&lt;Id&gt;_&lt;event&gt;    event name (falls back to the engine's own event translation, then to the humanized name)
 *   bhv_g_&lt;Group&gt;         group / category / header (spaces and symbols become '_': "Who can pick it up" = bhv_g_Who_can_pick_it_up)
 *   bhv_note_&lt;Id&gt;          note of the @Requires annotation
 * </pre>
 * Project and imported (.s2b) behaviors have no keys: they show their own text (only the generic words, like group names and the
 * engine's events, are translated). Results are cached per behavior and language; computing them touches no Android view.
 */
public final class BehaviorText {
	private BehaviorText() {}

	/** Everything translated for one behavior in one language. */
	private static final class Loc {
		String lang, name, desc, category, note, search;
		final List<VarSpec> vars = new ArrayList<VarSpec>();
		final Map<String, String> events = new HashMap<String, String>();
		final Map<String, String> labels = new HashMap<String, String>();
		final Map<String, String> descs = new HashMap<String, String>();
	}

	private static final Map<BehaviorMeta, Loc> CACHE = Collections.synchronizedMap(new WeakHashMap<BehaviorMeta, Loc>());

	private static Loc loc(BehaviorMeta m) {
		String lang = Tr.lang();
		Loc l = CACHE.get(m);
		if (l != null && lang.equals(l.lang)) return l;
		l = new Loc();
		l.lang = lang;
		l.name = tr(m, "bhv_n_" + m.id, m.title());
		l.desc = tr(m, "bhv_d_" + m.id, m.desc == null ? "" : m.desc);
		l.category = group(m.category);
		l.note = tr(m, "bhv_note_" + m.id, m.reqNote == null ? "" : m.reqNote);
		for (VarSpec v : m.vars) {
			String label = tr(m, "bhv_p_" + m.id + "_" + v.name, v.displayLabel());
			String desc = tr(m, "bhv_pd_" + m.id + "_" + v.name, v.desc == null ? "" : v.desc);
			l.labels.put(v.name, label);
			l.descs.put(v.name, desc);
			VarSpec c = copy(v);
			c.label = label;
			c.desc = desc;
			if (v.header != null && v.header.length() > 0) c.header = group(v.header);
			l.vars.add(c);
		}
		for (BehaviorMeta.EventSpec e : m.events) l.events.put(e.name, eventName(m.builtin ? m.id : null, e.name));
		StringBuilder sb = new StringBuilder();
		sb.append(l.name).append(' ').append(m.title()).append(' ').append(m.id).append(' ').append(l.desc).append(' ')
				.append(m.desc).append(' ').append(l.category).append(' ').append(m.category);
		l.search = sb.toString().toLowerCase(Locale.ROOT);
		CACHE.put(m, l);
		return l;
	}

	/** Translation of a key of a built-in behavior; project behaviors keep their own text. */
	private static String tr(BehaviorMeta m, String key, String def) {
		return m.builtin ? Tr.t(key, def) : def;
	}

	private static VarSpec copy(VarSpec v) {
		VarSpec c = new VarSpec(v.name, v.kind);
		c.list = v.list;
		c.array = v.array;
		c.elemType = v.elemType;
		c.options = v.options;
		c.label = v.label;
		c.desc = v.desc;
		c.unit = v.unit;
		c.header = v.header;
		c.min = v.min;
		c.max = v.max;
		c.step = v.step;
		c.def = v.def;
		return c;
	}

	// ------------------------------------------------------------------------------------------------ names

	public static String name(BehaviorMeta m) {
		return loc(m).name;
	}

	/** Name of a behavior that may be missing (deleted file): its id. */
	public static String name(BehaviorMeta m, String fallbackId) {
		return m == null ? fallbackId : loc(m).name;
	}

	public static String desc(BehaviorMeta m) {
		return loc(m).desc;
	}

	/** Group of the Add dialog. */
	public static String category(BehaviorMeta m) {
		return loc(m).category;
	}

	/** Lower case text the Add dialog's search box looks in (name + description + id + group, in English and in the app language). */
	public static String search(BehaviorMeta m) {
		return loc(m).search;
	}

	/** Translated group / header name ({@code bhv_g_<Name>}). */
	public static String group(String name) {
		if (name == null || name.length() == 0) return "";
		StringBuilder k = new StringBuilder("bhv_g_");
		for (int i = 0; i < name.length(); i++) {
			char ch = name.charAt(i);
			k.append(Character.isLetterOrDigit(ch) ? ch : '_');
		}
		return Tr.t(k.toString(), name);
	}

	/** Group the behavior is listed under ("Project" for the project's own behaviors). */
	public static String listGroup(BehaviorMeta m) {
		return m.builtin ? loc(m).category : Tr.t("bhv_project", "Project");
	}

	// ------------------------------------------------------------------------------------------------ parameters

	public static String param(BehaviorMeta m, VarSpec p) {
		String s = loc(m).labels.get(p.name);
		return s != null ? s : p.displayLabel();
	}

	public static String paramDesc(BehaviorMeta m, VarSpec p) {
		String s = loc(m).descs.get(p.name);
		return s != null ? s : (p.desc == null ? "" : p.desc);
	}

	/** The parameters as the inspector should show them: copies whose label / description / header are already translated. */
	public static List<VarSpec> vars(BehaviorMeta m) {
		return loc(m).vars;
	}

	// ------------------------------------------------------------------------------------------------ events

	public static String event(BehaviorMeta m, BehaviorMeta.EventSpec e) {
		String s = loc(m).events.get(e.name);
		return s != null ? s : eventName(m.builtin ? m.id : null, e.name);
	}

	/** Event of a behavior by id (the block editor only knows file names): the behavior's own spec when it is found. */
	public static String event(String behaviorId, String projectPath, String eventName) {
		BehaviorMeta m = null;
		try {
			m = BehaviorCatalog.find(behaviorId, projectPath);
		} catch (Throwable ignored) {
		}
		if (m != null) {
			BehaviorMeta.EventSpec spec = spec(m, eventName);
			if (spec != null) return event(m, spec);
		}
		return eventName(m != null && m.builtin ? m.id : null, eventName);
	}

	/** {@code bhv_e_<Id>_<event>} (built-ins), then the engine's translation of that event name, then "On death". */
	private static String eventName(String builtInId, String event) {
		String v = builtInId == null ? null : Tr.t("bhv_e_" + builtInId + "_" + event, null);
		if (v != null) return v;
		v = Tr.t(event, null);
		if (v == null) {
			String k = event.replace(" ", "");
			if (k.length() > 0) k = Character.toLowerCase(k.charAt(0)) + k.substring(1);
			v = Tr.t(k, null);
		}
		if (v == null || v.equals(event)) v = VarSpec.humanize(event);
		return v;
	}

	/** Translated event name with its argument names: "When hit (enemy, damage)". */
	public static String eventWithArgs(BehaviorMeta m, BehaviorMeta.EventSpec e) {
		String s = event(m, e);
		if (e.args.isEmpty()) return s;
		StringBuilder sb = new StringBuilder(s).append("  (");
		for (int i = 0; i < e.args.size(); i++) sb.append(i > 0 ? ", " : "").append(e.args.get(i)[1]);
		return sb.append(')').toString();
	}

	public static BehaviorMeta.EventSpec spec(BehaviorMeta m, String eventName) {
		for (BehaviorMeta.EventSpec e : m.events) if (e.name.equals(eventName)) return e;
		return null;
	}

	// ------------------------------------------------------------------------------------------------ requirements

	/** Translated note of the {@code @Requires} annotation, "" when there is none. */
	public static String note(BehaviorMeta m) {
		return loc(m).note;
	}

	/** Translated name of an editor element TYPE (BOX, TEXT...). */
	public static String elementType(String type) {
		String def;
		switch (type) {
			case "BOX": def = "Box"; break;
			case "CIRCLE": def = "Circle"; break;
			case "CUSTOM": def = "Custom shape"; break;
			case "MAP": def = "Map"; break;
			case "TEXT": def = "Text"; break;
			case "PROGRESS": def = "Progress bar"; break;
			case "JOYSTICK": def = "Joystick"; break;
			case "PARTICLE": def = "Particles"; break;
			case "UIHOLDER": def = "UI holder"; break;
			case "GROUP": def = "Group"; break;
			case "CAMERA": def = "Camera"; break;
			case "OBJ3D": def = "3D object"; break;
			case "LIGHT3D": def = "3D light"; break;
			case "CAMERA3D": def = "3D camera"; break;
			case "CAMERAVIEW": def = "Camera view"; break;
			default: def = VarSpec.humanize(type.toLowerCase(Locale.ROOT));
		}
		return Tr.t("ui_type_" + type.toLowerCase(Locale.ROOT), def);
	}

	/** Name of one {@code @Sets} key ("gravityScale" = "Gravity scale"). */
	public static String setKey(String key) {
		return Tr.t("bhv_set_" + key, VarSpec.humanize(key));
	}

	/** Value of a {@code @Sets} entry: yes / no for booleans, the value as written otherwise. */
	public static String setValue(String key, String value) {
		if (value.equalsIgnoreCase("true")) return Tr.t("bhv_yes", "Yes");
		if (value.equalsIgnoreCase("false")) return Tr.t("bhv_no", "No");
		return value;
	}

	/** "gravityScale=0, fixedRotation=true" as "Gravity scale = 0, Fixed rotation = Yes". */
	public static String setsText(BehaviorMeta m) {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> e : m.sets.entrySet())
			sb.append(sb.length() > 0 ? ", " : "").append(setKey(e.getKey())).append(" = ").append(setValue(e.getKey(), e.getValue()));
		return sb.toString();
	}

	/** One line: "UI element · physical element (DYNAMIC) · sets Gravity scale = 0". */
	public static String requirements(BehaviorMeta m) {
		StringBuilder sb = new StringBuilder();
		if (m.reqUi) sb.append(Tr.t("bhv_req_ui", "UI element"));
		if (m.reqBody) sb.append(Tr.t("bhv_req_body", "physical element")).append(m.reqType.length() > 0 ? " (" + m.reqType + ")" : "");
		if (!m.sets.isEmpty()) {
			if (sb.length() > 0) sb.append(" · ");
			sb.append(Tr.t("bhv_req_sets", "sets")).append(' ').append(setsText(m));
		}
		return sb.toString();
	}

	/** Why the behavior does not fit the element of env (2D / 3D, UI / physical, body type), or null when it does. */
	public static String misfit(BehaviorMeta m, BehaviorEnv env) {
		return env == null ? null : misfit(m, env.bodyType, env.elementType);
	}

	/**
	 * Like {@link #misfit(BehaviorMeta, String)} but also knows 3D: 3D behaviors on 2D elements, 2D behaviors on 3D elements,
	 * and the {@code types} a behavior is made for (Flicker for LIGHT3D...).
	 * @param bodyType   the "type" property (DYNAMIC / KINEMATIC / STATIC / UI)
	 * @param editorType the TYPE (BOX, TEXT, OBJ3D, LIGHT3D, CAMERA3D...), "" when unknown
	 */
	public static String misfit(BehaviorMeta m, String bodyType, String editorType) {
		if (editorType != null && editorType.length() > 0) {
			boolean el3d = com.star4droid.star2d.behaviors.ElementTypes.is3D(editorType);
			if (m.is3D() && !el3d) return Tr.t("bhv_mf_made_3d", "Made for 3D elements");
			if (!m.is3D() && el3d) return Tr.t("bhv_mf_made_2d", "Made for 2D elements (this one is 3D)");
			if (!m.types.isEmpty() && !m.types.contains(editorType)) {
				StringBuilder sb = new StringBuilder();
				for (String t : m.types) sb.append(sb.length() > 0 ? ", " : "").append(elementType(t));
				return String.format(Tr.t("bhv_mf_made_types", "Made for: %1$s"), sb.toString());
			}
			if (el3d) return null;
		}
		return misfit(m, bodyType);
	}

	/** Why the behavior does not fit an element (the "type" property: DYNAMIC / KINEMATIC / STATIC / UI), or null when it does. */
	public static String misfit(BehaviorMeta m, String elementType) {
		if (elementType == null || elementType.length() == 0) return null;
		if (m.is3D()) return null; // a 3D behavior: only misfit(m, bodyType, TYPE) can tell
		boolean ui = elementType.equals("UI");
		String note = m.reqNote.length() > 0 ? ". " + note(m) : "";
		if (m.reqUi && !ui) return Tr.t("bhv_mf_need_ui", "Needs a UI element");
		if (m.reqBody && ui) return Tr.t("bhv_mf_need_body", "Needs a physical element (this one is UI)") + note;
		if (m.reqBody && m.reqType.length() > 0 && !m.reqType.equalsIgnoreCase(elementType))
			return String.format(Tr.t("bhv_mf_need_type", "Needs a %1$s body (this one is %2$s)"), m.reqType, elementType) + note;
		if (!m.reqBody && !m.reqUi) {
			if (m.targets.equals(BehaviorMeta.PHYSICS) && ui) return Tr.t("bhv_mf_made_physical", "Made for physical elements");
			if (m.targets.equals(BehaviorMeta.UI) && !ui) return Tr.t("bhv_mf_made_ui", "Made for UI elements");
		}
		return null;
	}

	/** "UI" / "Physical" / "UI + Physical" for the Add dialog's chips and badges. */
	public static String targets(BehaviorMeta m) {
		String t = m.effectiveTargets();
		if (t.equals(BehaviorMeta.THREE_D)) return Tr.t("bhv_f_3d", "3D");
		if (t.equals(BehaviorMeta.UI)) return Tr.t("bhv_f_ui", "UI");
		if (t.equals(BehaviorMeta.PHYSICS)) return Tr.t("bhv_f_physics", "Physical");
		return Tr.t("bhv_f_ui", "UI") + " + " + Tr.t("bhv_f_physics", "Physical");
	}

	// ------------------------------------------------------------------------------------------------ background work

	private static final ExecutorService BG = Executors.newSingleThreadExecutor(new ThreadFactory() {
		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, "behavior-ui");
			t.setDaemon(true);
			t.setPriority(Thread.MIN_PRIORITY + 1);
			return t;
		}
	});

	/** Runs on the shared low priority thread of the behavior UI (catalog reading, icon drawing...). */
	public static void background(Runnable r) {
		try {
			BG.execute(r);
		} catch (RuntimeException ignored) {
		}
	}

	private static volatile boolean warming;

	/**
	 * Reads the catalog, the translations and the icons in the background so that the Add dialog and the Properties section open
	 * instantly. Safe to call any number of times, from any thread.
	 */
	public static void prewarm(final Context c) {
		if (warming || BehaviorCatalog.isWarm() && BehaviorIcons.warm()) return;
		warming = true;
		final Context app = c.getApplicationContext() != null ? c.getApplicationContext() : c;
		background(new Runnable() {
			@Override
			public void run() {
				try {
					List<BehaviorMeta> all = BehaviorCatalog.builtIns();
					for (BehaviorMeta m : all) loc(m);
					BehaviorIcons.prewarm(app, all);
				} catch (Throwable ignored) {
				} finally {
					warming = false;
				}
			}
		});
	}

	/** Runs {@code onUi} on the UI thread once the built-in library is read (at once when it already is; never blocks the UI thread). */
	public static void whenReady(final Context c, final Runnable onUi) {
		if (BehaviorCatalog.isWarm()) {
			com.star4droid.star2d.ui.Ui.ui(onUi);
			return;
		}
		background(new Runnable() {
			@Override
			public void run() {
				BehaviorCatalog.builtIns();
				com.star4droid.star2d.ui.Ui.ui(onUi);
			}
		});
	}
}
