package com.star4droid.star2d.behaviors;

/**
 * Network settings of an element (property {@code net}, Network card of the Properties panel):
 * {@code {"on":true,"owner":"host","sync":"pos,rot,vel,vis"}}. The code generator turns them into
 * {@code Net.register(element, owner, sync)}. Pure Java (editor + tests).
 */
public final class NetSpec {
	public static final String PROPERTY = "net";
	/** What can be synced, in the order of the card. */
	public static final String[] FIELDS = {"pos", "rot", "vel", "vis", "img", "anim", "text", "vars"};
	public static final String DEFAULT_SYNC = "pos,rot,vel,vis";

	public boolean on;
	/** "host" or a seat "2".."8" (the n-th player who joins). */
	public String owner = "host";
	public String sync = DEFAULT_SYNC;

	public static NetSpec parse(String json) {
		NetSpec s = new NetSpec();
		if (json == null || json.trim().isEmpty()) return s;
		try {
			com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
			s.on = o.has("on") && o.get("on").getAsBoolean();
			if (o.has("owner")) s.owner = clean(o.get("owner").getAsString(), "host");
			if (o.has("sync")) s.sync = o.get("sync").getAsString();
		} catch (Exception ignored) {
		}
		return s;
	}

	private static String clean(String v, String def) {
		v = v == null ? "" : v.trim().toLowerCase();
		return v.isEmpty() ? def : v;
	}

	public String toJson() {
		if (!on) return "";
		com.google.gson.JsonObject o = new com.google.gson.JsonObject();
		o.addProperty("on", true);
		o.addProperty("owner", owner);
		o.addProperty("sync", sync);
		return o.toString();
	}

	public boolean has(String field) {
		for (String f : sync.split("[,; ]+")) if (f.trim().equals(field)) return true;
		return false;
	}

	public void set(String field, boolean v) {
		StringBuilder b = new StringBuilder();
		for (String f : FIELDS) {
			boolean keep = f.equals(field) ? v : has(f);
			if (keep) b.append(b.length() == 0 ? "" : ",").append(f);
		}
		sync = b.toString();
	}

	/** Statement of the generated scene ("" when the element is not networked). */
	public static String register(String element, String json) {
		NetSpec s = parse(json);
		if (!s.on) return "";
		return "com.star4droid.template.net.Net.register(" + element + ", " + ValueEmit.javaLiteral(s.owner) + ", "
				+ ValueEmit.javaLiteral(s.sync.isEmpty() ? DEFAULT_SYNC : s.sync) + ");\n";
	}
}
