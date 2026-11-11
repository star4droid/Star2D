package com.star4droid.template.anim;

import com.badlogic.gdx.math.Interpolation;

/** Names of the transitions between keyframes (the editor shows their curve). */
public final class Interp {
	private Interp() {
	}

	/** Every name, in the order the editor lists them. */
	public static final String[] NAMES = {"linear", "smooth", "easeIn", "easeOut", "sine", "bounce", "elastic", "back", "fast", "step"};

	/** "step" = no transition: the value jumps when the key is reached. */
	public static boolean isStep(String name) {
		return "step".equalsIgnoreCase(name) || "none".equalsIgnoreCase(name) || "jump".equalsIgnoreCase(name);
	}

	public static Interpolation of(String name) {
		if (name == null) return Interpolation.smooth;
		String n = name.trim().toLowerCase();
		if (n.equals("linear")) return Interpolation.linear;
		if (n.equals("smooth") || n.equals("ease") || n.equals("easeinout")) return Interpolation.smooth;
		if (n.equals("easein") || n.equals("in")) return Interpolation.pow2In;
		if (n.equals("easeout") || n.equals("out")) return Interpolation.pow2Out;
		if (n.equals("sine")) return Interpolation.sine;
		if (n.equals("bounce")) return Interpolation.bounceOut;
		if (n.equals("elastic")) return Interpolation.elasticOut;
		if (n.equals("back") || n.equals("swing")) return Interpolation.swingOut;
		if (n.equals("fast") || n.equals("exp")) return Interpolation.exp5Out;
		if (n.equals("smoother")) return Interpolation.smoother;
		if (n.equals("circle")) return Interpolation.circleOut;
		return Interpolation.smooth;
	}

	/** Progress 0..1 through this transition (step stays at 0 until the end). */
	public static float apply(String name, float a) {
		if (a <= 0) return 0;
		if (a >= 1) return 1;
		if (isStep(name)) return 0;
		return of(name).apply(a);
	}
}
