package com.star4droid.template.behaviors;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Describes a behavior to the editor (Add dialog, info icon, Events panel).
 * Project behaviors (Java source in the project) are read as TEXT by the editor, so keep the values plain literals:
 * {@code @BehaviorInfo(name="Floating", icon="wave", desc="...", targets=BehaviorInfo.Target.BOTH)}.
 */
@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Target(ElementType.TYPE)
public @interface BehaviorInfo {
	/**
	 * Where the behavior makes sense. UI = screen-space elements (type UI), PHYSICS = world elements with a body, BOTH = any
	 * 2D element, THREE_D = elements of a 3D scene (TYPE OBJ3D, LIGHT3D, CAMERA3D; narrow it with {@link #types()}).
	 * 2D behaviors are never offered for 3D elements and 3D behaviors never for 2D / UI elements.
	 */
	enum Target { UI, PHYSICS, BOTH, THREE_D }

	/** Display name (default: the class name split into words). */
	String name() default "";
	/** Icon id of the icon library (assets/icons/catalog.json), e.g. "run-fast", "heart". */
	String icon() default "puzzle";
	/** One or two short sentences. */
	String desc() default "";
	Target targets() default Target.BOTH;
	/** Group in the Add dialog: Effects, Movement, Combat, Vehicles, Camera, Logic... */
	String category() default "General";
	/**
	 * Events this behavior can raise with {@link Behavior#fire(String, Object...)}. Each entry is {@code "onName"} or
	 * {@code "onName(Type arg, Type arg2)"} with Type one of PlayerItem, int, float, double, boolean, String.
	 * They show up in the Events panel of the element, the user attaches blocks/code to them.
	 */
	String[] events() default {};
	/**
	 * Editor TYPEs the behavior is made for, e.g. {@code types = {"LIGHT3D"}} or {@code {"OBJ3D", "CAMERA3D"}}.
	 * Empty = every element of {@link #targets()}. Plain string literals (the editor reads project behaviors as text).
	 */
	String[] types() default {};
	String version() default "1.0";
	String author() default "";
}
