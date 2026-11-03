package com.star4droid.star2d.ui.blocks;

import com.star4droid.star2d.blocks.SceneDims;

/**
 * The built-in 3D blocks (and the Inventory blocks, for 2D and 3D). Shown only in the block editor of a 3D scene
 * (configs/&lt;scene&gt;.json "sceneType":"3D"). Every block calls the static facades
 * {@code com.star4droid.template.d3.B3} / {@code B3x} (or other d3 classes), fully qualified, Java 7 only, so the event
 * code compiles on the phone. Units: meters, Y up, degrees.
 *
 * No Android code: tools/test_blocks3d.py feeds a {@link Sink} to check that every block has its texts, translations and
 * compiles with its default values.
 */
public final class Blocks3D {
	private Blocks3D() {
	}

	/** Receives the definitions (BlockCatalog / BlockInfo in the app, a checker in the tests). */
	public interface Sink {
		void category(String name, int color, String en, String ar);

		void def(String cat, int color, String title, String fields, String defaults, boolean isIf, String code, int dims,
				String en, String ar, String infoEn, String infoAr);

		/** {@code slots}: one letter per ___ : e element, n number, t text, b condition (for checkers / the AI). */
		void reporter(String group, String display, String template, char shape, String slots, int dims, String displayAr,
				String infoEn, String infoAr);

		void field(String name, String en, String ar);

		void choices(String type, String[] values);
	}

	static final String B = "com.star4droid.template.d3.B3.", X = "com.star4droid.template.d3.B3x.", GDX = "com.badlogic.gdx.Gdx.";

	/** Field types that offer a list of values (chips in the value pane). */
	public static final String[] FOLLOW = {"THIRD_PERSON", "FIRST_PERSON", "ORBIT", "TOP_DOWN", "SIDE", "LOOK_AT"};
	public static final String[] PRESETS = {"DAY", "SUNSET", "NIGHT", "OVERCAST", "STUDIO", "HORROR", "INDOOR", "CARTOON", "CUSTOM"};
	public static final String[] RENDER = {"SIMPLE", "SOFT", "TOON", "PBR", "UNLIT"};
	public static final String[] POST = {"NONE", "VIGNETTE", "NIGHT_VISION", "GRAYSCALE", "SEPIA", "HORROR", "CHROMATIC", "PIXELATE", "BLOOM_LITE", "UNDERWATER", "DREAM"};
	public static final String[] QUALITY = {"LOW", "MEDIUM", "HIGH", "ULTRA"};
	public static final String[] BODY = {"NONE", "STATIC", "DYNAMIC", "KINEMATIC", "TRIGGER"};
	public static final String[] SHAPES = {"CUBE", "SPHERE", "CYLINDER", "CAPSULE", "CONE", "PLANE", "TORUS", "RAMP", "STAIRS", "PYRAMID", "HEMISPHERE", "TUBE", "WEDGE", "QUAD", "DISC"};
	public static final String[] SHADERS = {"builtin:glow", "builtin:hologram", "builtin:dissolve", "builtin:wave", "builtin:rim", "builtin:toon", "builtin:gradient", "builtin:pulse", "builtin:xray", "builtin:flag", "builtin:lava", "builtin:ghost"};

	/** Category names (English keys; shown translated by BlockInfo). */
	public static final String MOTION = "3D Motion", PHYSICS = "3D Physics", LOOKS = "3D Looks", ANIM = "3D Animation", CAMERA = "3D Camera",
			LIGHTS = "3D Lights Sky", EFFECTS = "3D Effects", QUALITY_CAT = "3D Quality", WORLD = "3D Terrain Water", OBJECTS = "3D Objects",
			SOUND = "3D Sound", INVENTORY = "Inventory";

	private static Sink s;
	private static String cat;
	private static int col, dims;

	private static void cat(String name, int color, int d, String en, String ar) {
		cat = name;
		col = color;
		dims = d;
		s.category(name, color, en, ar);
	}

	private static int count(String fields) {
		return fields.trim().isEmpty() ? 0 : fields.trim().split(" ").length;
	}

	/** (float)(%from$s), ..., (float)(%to$s) */
	private static String fl(int from, int to) {
		StringBuilder sb = new StringBuilder();
		for (int i = from; i <= to; i++) {
			if (i > from) sb.append(", ");
			sb.append("(float)(%").append(i).append("$s)");
		}
		return sb.toString();
	}

	private static String f(int i) {
		return "(float)(%" + i + "$s)";
	}

	private static String str(int i) {
		return "String.valueOf(%" + i + "$s)";
	}

	private static String q(int i) {
		return "\"%" + i + "$s\"";
	}

	/** Action block: {@code code} plus the next blocks. */
	private static void st(String title, String fields, String defaults, String code, String en, String ar, String ie, String ia) {
		s.def(cat, col, title, fields, defaults, false, code + "\n%" + (count(fields) + 1) + "$s", dims, en, ar, ie, ia);
	}

	/** C-block "if (cond) {inside} else {else}". */
	private static void cb(String title, String fields, String defaults, String cond, String en, String ar, String ie, String ia) {
		int n = count(fields);
		s.def(cat, col, title, fields, defaults, true, "if(" + cond + "){\n%" + (n + 1) + "$s\n} else {\n%" + (n + 2) + "$s\n}\n%" + (n + 3) + "$s", dims, en, ar, ie, ia);
	}

	private static void rp(String display, String template, char shape, String slots, String ar, String ie, String ia) {
		s.reporter(cat, display, template, shape, slots, dims, ar, ie, ia);
	}

	private static void fd(String name, String en, String ar) {
		s.field(name, en, ar);
	}

	public static synchronized void register(Sink sink) {
		s = sink;
		int D3 = SceneDims.D3;

		// ---------------------------------------------------------------- field names and value lists
		fd("z", "z", "z");
		fd("dz", "dz", "إزاحة z");
		fd("meters", "meters", "أمتار");
		fd("pitch", "pitch", "الميل");
		fd("yaw", "yaw", "الاتجاه");
		fd("roll", "roll", "اللف");
		fd("parent", "parent", "الأب");
		fd("terrain", "terrain", "التضاريس");
		fd("water", "water", "الماء");
		fd("light", "light", "الضوء");
		fd("camera", "camera", "الكاميرا");
		fd("place", "place", "المكان");
		fd("forward", "forward", "للأمام");
		fd("right", "right", "لليمين");
		fd("up", "up", "للأعلى");
		fd("height", "height", "الارتفاع");
		fd("distance", "distance", "المسافة");
		fd("fov", "view angle", "زاوية الرؤية");
		fd("mode", "mode", "الوضع");
		fd("intensity", "intensity", "الشدة");
		fd("range", "range", "المدى");
		fd("amount", "amount", "المقدار");
		fd("preset", "preset", "الإعداد الجاهز");
		fd("near", "near", "قريب");
		fd("far", "far", "بعيد");
		fd("top", "top color", "لون الأعلى");
		fd("bottom", "bottom color", "لون الأسفل");
		fd("effect", "effect", "التأثير");
		fd("shader", "shader", "الشيدر");
		fd("params", "params", "المعاملات");
		fd("param", "param", "المعامل");
		fd("level", "level", "المستوى");
		fd("scale", "scale", "المقياس");
		fd("size", "size", "الحجم");
		fd("fps", "fps", "الإطارات/ث");
		fd("detail", "detail", "التفاصيل");
		fd("length", "length", "الطول");
		fd("shallow", "shallow color", "لون الضحل");
		fd("deep", "deep color", "لون العميق");
		fd("sound", "sound", "الصوت");
		fd("volume", "volume", "مستوى الصوت");
		fd("id", "item id", "معرّف الغرض");
		fd("count", "count", "العدد");
		fd("mass", "mass", "الكتلة");
		fd("friction", "friction", "الاحتكاك");
		fd("bounce", "bounce", "الارتداد");
		fd("onlyOnGround", "only on ground", "على الأرض فقط");
		fd("model", "model", "المجسم");
		fd("shape", "shape", "الشكل");
		fd("part", "part", "الجزء");
		fd("clip", "clip", "المقطع");
		fd("loop", "loop", "تكرار");
		fd("width", "width", "العرض");
		fd("arabic", "Arabic texts", "نصوص عربية");
		s.choices("followMode", FOLLOW);
		s.choices("lightPreset", PRESETS);
		s.choices("renderMode", RENDER);
		s.choices("postEffect", POST);
		s.choices("quality", QUALITY);
		s.choices("bodyType3D", BODY);
		s.choices("shape3D", SHAPES);
		s.choices("shader", SHADERS);

		// ---------------------------------------------------------------- 3D Motion
		cat(MOTION, 0xFF1E88E5, D3, "3D Motion", "الحركة ثلاثية الأبعاد");
		st("setPosition3D", "body(Body) x y z", "current|0|1|0", B + "setPosition(%1$s, " + fl(2, 4) + ");",
				"Go to x, y, z", "انتقل إلى x و y و z", "Puts the element at a point (meters, y is up).", "يضع العنصر في نقطة (بالأمتار، y للأعلى).");
		st("moveBy3D", "body(Body) dx dy dz", "current|0|0|1", B + "move(%1$s, " + fl(2, 4) + ");",
				"Move by", "تحرك بمقدار", "Moves the element by these meters along the world axes.", "يحرك العنصر بهذه الأمتار على محاور العالم.");
		st("moveForward3D", "body(Body) meters", "current|1", B + "moveForward(%1$s, " + f(2) + ");",
				"Move forward", "تقدم للأمام", "Moves along its own front (negative = back). Put it in OnStep to keep walking.", "يتحرك باتجاه وجهه (السالب = للخلف). ضعه في OnStep ليستمر بالمشي.");
		st("moveRight3D", "body(Body) meters", "current|1", X + "moveRight(%1$s, " + f(2) + ");",
				"Move sideways", "تحرك جانبياً", "Moves to its own right (negative = left).", "يتحرك إلى يمينه (السالب = لليسار).");
		st("moveUp3D", "body(Body) meters", "current|1", X + "moveUp(%1$s, " + f(2) + ");",
				"Move up", "تحرك للأعلى", "Moves along its own up direction (negative = down).", "يتحرك باتجاه أعلاه (السالب = للأسفل).");
		st("setX3D", "body(Body) x", "current|0", X + "setX(%1$s, " + f(2) + ");", "Set x", "اضبط x", "Changes only the x position.", "يغيّر الموضع x فقط.");
		st("setY3D", "body(Body) y", "current|0", X + "setY(%1$s, " + f(2) + ");", "Set y (height)", "اضبط y (الارتفاع)", "Changes only the height.", "يغيّر الارتفاع فقط.");
		st("setZ3D", "body(Body) z", "current|0", X + "setZ(%1$s, " + f(2) + ");", "Set z", "اضبط z", "Changes only the z position.", "يغيّر الموضع z فقط.");
		st("moveTo3D", "body(Body) x y z seconds", "current|0|1|0|1", B + "moveTo(%1$s, " + fl(2, 5) + ");",
				"Glide to in seconds", "انزلق إلى خلال ثوانٍ", "Moves smoothly to a point in the given time.", "ينتقل بنعومة إلى نقطة خلال الوقت المحدد.");
		st("moveToward3D", "body(Body) target(Body) speed", "current|current|2", X + "moveToward(%1$s, %2$s, " + f(3) + ");",
				"Move toward element", "تحرك نحو عنصر", "Walks toward another element (meters per second). Use in OnStep.", "يمشي نحو عنصر آخر (أمتار في الثانية). ضعه في OnStep.");
		st("setRotation3D", "body(Body) x y z", "current|0|0|0", B + "setRotation(%1$s, " + fl(2, 4) + ");",
				"Set rotation", "اضبط الدوران", "Rotation in degrees around x, y and z.", "الدوران بالدرجات حول x و y و z.");
		st("rotate3D", "body(Body) x y z", "current|0|15|0", B + "rotate(%1$s, " + fl(2, 4) + ");",
				"Rotate by", "دوّر بمقدار", "Adds degrees to the rotation (y turns left / right).", "يضيف درجات إلى الدوران (y يدير يميناً ويساراً).");
		st("turn3D", "body(Body) pitch yaw roll", "current|0|15|0", B + "turnLocal(%1$s, " + fl(2, 4) + ");",
				"Turn (own axes)", "استدر (حول محاوره)", "Turns around its own axes, like a plane or a car.", "يستدير حول محاوره الخاصة مثل طائرة أو سيارة.");
		st("rotateTo3D", "body(Body) x y z seconds", "current|0|90|0|1", B + "rotateTo(%1$s, " + fl(2, 5) + ");",
				"Rotate to in seconds", "دوّر إلى خلال ثوانٍ", "Turns smoothly to a rotation in the given time.", "يدور بنعومة إلى زاوية خلال الوقت المحدد.");
		st("lookAt3D", "body(Body) x y z", "current|0|0|0", B + "lookAt(%1$s, " + fl(2, 4) + ");",
				"Look at point", "انظر إلى نقطة", "Turns its front toward a point.", "يوجّه واجهته نحو نقطة.");
		st("lookAtElement3D", "body(Body) target(Body)", "current|current", B + "lookAtItem(%1$s, %2$s);",
				"Look at element", "انظر إلى عنصر", "Turns its front toward another element.", "يوجّه واجهته نحو عنصر آخر.");
		st("face3D", "body(Body) target(Body)", "current|current", B + "face(%1$s, %2$s);",
				"Face element (upright)", "واجه عنصراً (بشكل قائم)", "Turns only left / right to face an element: good for characters.", "يستدير يميناً ويساراً فقط ليواجه عنصراً: مناسب للشخصيات.");
		st("turnToward3D", "body(Body) target(Body) speed", "current|current|180", X + "turnToward(%1$s, %2$s, " + f(3) + ");",
				"Turn toward element", "استدر نحو عنصر", "Turns toward an element at most this many degrees per second. Use in OnStep.", "يستدير نحو عنصر بحد أقصى هذه الدرجات في الثانية. ضعه في OnStep.");
		st("setScale3D", "body(Body) x y z", "current|1|1|1", B + "setScale(%1$s, " + fl(2, 4) + ");",
				"Set scale", "اضبط المقياس", "Size multiplier on each axis (1 = normal).", "مضاعف الحجم على كل محور (1 = الطبيعي).");
		st("scaleTo3D", "body(Body) x y z seconds", "current|2|2|2|0.5", B + "scaleTo(%1$s, " + fl(2, 5) + ");",
				"Scale to in seconds", "غيّر المقياس خلال ثوانٍ", "Grows or shrinks smoothly in the given time.", "يكبر أو يصغر بنعومة خلال الوقت المحدد.");
		st("attach3D", "body(Body) parent(Body)", "current|current", B + "attach(%1$s, %2$s);",
				"Attach to", "اربط بـ", "The element follows the parent (a sword in a hand, a light on a car).", "يتبع العنصر العنصر الأب (سيف في يد، ضوء على سيارة).");
		st("detach3D", "body(Body)", "current", B + "detach(%1$s);", "Detach", "افصل", "Stops following its parent.", "يتوقف عن تتبع العنصر الأب.");
		rp("3D x of ___", B + "x(%1$s)", 'n', "e", "x ثلاثي لـ ___", "x position (meters).", "الموضع x (بالأمتار).");
		rp("3D y of ___", B + "y(%1$s)", 'n', "e", "y ثلاثي لـ ___", "Height (meters).", "الارتفاع (بالأمتار).");
		rp("3D z of ___", B + "z(%1$s)", 'n', "e", "z ثلاثي لـ ___", "z position (meters).", "الموضع z (بالأمتار).");
		rp("3D rotation x of ___", B + "rotX(%1$s)", 'n', "e", "دوران x لـ ___", "Rotation around x (degrees).", "الدوران حول x (درجات).");
		rp("3D rotation y of ___", B + "rotY(%1$s)", 'n', "e", "دوران y لـ ___", "Rotation around y (degrees): the heading.", "الدوران حول y (درجات): الاتجاه.");
		rp("3D rotation z of ___", B + "rotZ(%1$s)", 'n', "e", "دوران z لـ ___", "Rotation around z (degrees).", "الدوران حول z (درجات).");
		rp("3D scale x of ___", B + "scaleX(%1$s)", 'n', "e", "مقياس x لـ ___", "Scale on x.", "المقياس على x.");
		rp("3D scale y of ___", B + "scaleY(%1$s)", 'n', "e", "مقياس y لـ ___", "Scale on y.", "المقياس على y.");
		rp("3D scale z of ___", B + "scaleZ(%1$s)", 'n', "e", "مقياس z لـ ___", "Scale on z.", "المقياس على z.");
		rp("3D distance ___ to ___", B + "distance(%1$s, %2$s)", 'n', "ee", "المسافة ثلاثية من ___ إلى ___", "Distance between two elements (meters).", "المسافة بين عنصرين (بالأمتار).");
		rp("3D distance ___ to point ___ ___ ___", B + "distanceToPoint(%1$s, " + fl(2, 4) + ")", 'n', "ennn", "المسافة من ___ إلى النقطة ___ ___ ___", "Distance from an element to a point.", "المسافة من عنصر إلى نقطة.");
		rp("3D flat distance ___ to ___", X + "distanceXZ(%1$s, %2$s)", 'n', "ee", "المسافة الأفقية من ___ إلى ___", "Distance on the ground (ignores height).", "المسافة على الأرض (بدون الارتفاع).");
		rp("3D angle ___ to ___", B + "angleTo(%1$s, %2$s)", 'n', "ee", "الزاوية من ___ إلى ___", "Degrees to turn to face the other (0 = ahead, + = right).", "الدرجات اللازمة لمواجهة الآخر (0 = أمامه، الموجب = يمين).");
		rp("3D forward x of ___", B + "forwardX(%1$s)", 'n', "e", "اتجاه الأمام x لـ ___", "x of the direction it faces.", "مركبة x لاتجاه الوجه.");
		rp("3D forward y of ___", B + "forwardY(%1$s)", 'n', "e", "اتجاه الأمام y لـ ___", "y of the direction it faces.", "مركبة y لاتجاه الوجه.");
		rp("3D forward z of ___", B + "forwardZ(%1$s)", 'n', "e", "اتجاه الأمام z لـ ___", "z of the direction it faces.", "مركبة z لاتجاه الوجه.");
		rp("___ is gliding (3D)", B + "isMoving(%1$s)", 'b', "e", "___ ينزلق (ثلاثي)", "True while a glide / rotate / scale in seconds runs.", "صحيح أثناء انزلاق أو دوران أو تغيير حجم بالثواني.");

		// ---------------------------------------------------------------- 3D Physics
		cat(PHYSICS, 0xFF00897B, D3, "3D Physics", "الفيزياء ثلاثية الأبعاد");
		st("applyForce3D", "body(Body) x y z", "current|0|10|0", B + "applyForce(%1$s, " + fl(2, 4) + ");",
				"Push (force)", "ادفع (قوة)", "Pushes continuously in world directions. Use in OnStep.", "يدفع باستمرار باتجاهات العالم. ضعه في OnStep.");
		st("applyImpulse3D", "body(Body) x y z", "current|0|5|0", B + "applyImpulse(%1$s, " + fl(2, 4) + ");",
				"Kick (impulse)", "دفعة فورية", "An instant push: jumps, explosions, hits.", "دفعة فورية: قفز، انفجارات، ضربات.");
		st("applyLocalForce3D", "body(Body) forward right up", "current|10|0|0", B + "applyLocalForce(%1$s, " + fl(2, 4) + ");",
				"Push along own axes", "ادفع على محاوره", "Force relative to where it faces (a car engine).", "قوة بالنسبة لاتجاه وجهه (محرك سيارة).");
		st("applyTorque3D", "body(Body) x y z", "current|0|5|0", B + "applyTorque(%1$s, " + fl(2, 4) + ");",
				"Torque", "عزم دوران", "Spins the body around x, y, z.", "يدوّر الجسم حول x و y و z.");
		st("setVelocity3D", "body(Body) x y z", "current|0|0|0", B + "setVelocity(%1$s, " + fl(2, 4) + ");",
				"Set velocity", "اضبط السرعة", "Speed in meters per second on each axis.", "السرعة بالمتر في الثانية على كل محور.");
		st("setVelocityKeepY3D", "body(Body) x z", "current|0|0", B + "setVelocityKeepY(%1$s, " + fl(2, 3) + ");",
				"Set velocity (keep falling)", "اضبط السرعة (مع السقوط)", "Sets the ground speed, keeps the up / down speed (walking with gravity).", "يضبط السرعة الأفقية ويحافظ على سرعة السقوط (المشي مع الجاذبية).");
		st("setAngularVelocity3D", "body(Body) x y z", "current|0|90|0", B + "setAngularVelocity(%1$s, " + fl(2, 4) + ");",
				"Set spin speed", "اضبط سرعة الدوران", "Rotation speed around each axis.", "سرعة الدوران حول كل محور.");
		st("walkForward3D", "body(Body) speed", "current|3", X + "walkForward(%1$s, " + f(2) + ");",
				"Walk forward", "امشِ للأمام", "Moves along its front at a speed (m/s), gravity still works.", "يتحرك باتجاه وجهه بسرعة (م/ث) مع بقاء الجاذبية.");
		st("pushForward3D", "body(Body) strength", "current|5", X + "pushForward(%1$s, " + f(2) + ");",
				"Kick forward", "ادفع للأمام", "Instant push toward its front (a ball, a dash).", "دفعة فورية نحو الأمام (كرة، اندفاع).");
		st("jump3D", "body(Body) speed onlyOnGround(Boolean)", "current|5|true", X + "jump(%1$s, " + f(2) + ", %3$s);",
				"Jump", "اقفز", "Sets the up speed (m/s). With only on ground it jumps only when standing.", "يضبط السرعة للأعلى (م/ث). مع «على الأرض فقط» يقفز فقط عندما يقف على شيء.");
		st("stop3D", "body(Body)", "current", X + "stop(%1$s);", "Stop moving", "أوقف الحركة", "Sets speed and spin to zero.", "يجعل السرعة والدوران صفراً.");
		st("setBodyType3D", "body(Body) type(bodyType3D)", "current|DYNAMIC", B + "setBodyType(%1$s, " + q(2) + ");",
				"Set body type", "اضبط نوع الجسم", "NONE, STATIC (walls), DYNAMIC (falls), KINEMATIC (moved by code) or TRIGGER (detects only).", "NONE أو STATIC (جدران) أو DYNAMIC (يسقط) أو KINEMATIC (يتحرك بالكود) أو TRIGGER (يكتشف فقط).");
		st("setMass3D", "body(Body) mass", "current|1", B + "setMass(%1$s, " + f(2) + ");", "Set mass", "اضبط الكتلة", "Weight in kilograms.", "الوزن بالكيلوغرام.");
		st("setGravityScale3D", "body(Body) scale", "current|1", B + "setGravityScale(%1$s, " + f(2) + ");",
				"Gravity scale", "مقياس الجاذبية", "0 = floats, 1 = normal, 2 = heavy.", "0 = يطفو، 1 = طبيعي، 2 = ثقيل.");
		st("setFriction3D", "body(Body) friction", "current|0.5", B + "setFriction(%1$s, " + f(2) + ");",
				"Set friction", "اضبط الاحتكاك", "0 = ice, 1 = rubber.", "0 = جليد، 1 = مطاط.");
		st("setBounce3D", "body(Body) bounce", "current|0.3", B + "setBounce(%1$s, " + f(2) + ");",
				"Set bounce", "اضبط الارتداد", "0 = no bounce, 1 = bounces back fully.", "0 = بلا ارتداد، 1 = يرتد بالكامل.");
		st("setPhysics3D", "body(Body) on(Boolean)", "current|true", B + "setPhysics(%1$s, %2$s);",
				"Physics on / off", "تشغيل / إيقاف الفيزياء", "Turns the physics body of the element on or off.", "يشغّل أو يوقف الجسم الفيزيائي للعنصر.");
		st("setWorldGravity3D", "x y z", "0|-9.81|0", B + "setGravity(" + fl(1, 3) + ");",
				"World gravity", "جاذبية العالم", "Gravity of the scene in m/s² (Earth: 0, -9.81, 0).", "جاذبية المشهد بوحدة م/ث² (الأرض: 0، -9.81، 0).");
		cb("ifGrounded3D", "body(Body)", "current", B + "isGrounded(%1$s)",
				"If on ground", "إذا كان على الأرض", "Runs the first part when the element stands on something, else the second.", "ينفّذ الجزء الأول إذا كان العنصر واقفاً على شيء، وإلا الثاني.");
		cb("ifTouching3D", "body(Body) target(Body)", "current|current", B + "touching(%1$s, %2$s)",
				"If touching", "إذا كان يلمس", "Runs the first part while the two elements touch.", "ينفّذ الجزء الأول أثناء تلامس العنصرين.");
		cb("ifRayForward3D", "body(Body) distance", "current|10", B + "raycastForward(%1$s, " + f(2) + ")",
				"If something ahead", "إذا كان هناك شيء أمامه", "Casts a ray from its front; the hit values blocks tell what it hit.", "يطلق شعاعاً من أمامه؛ قيم «الإصابة» تخبرك بما أصابه.");
		rp("3D speed x of ___", B + "velX(%1$s)", 'n', "e", "السرعة x لـ ___", "Speed on x (m/s).", "السرعة على x (م/ث).");
		rp("3D speed y of ___", B + "velY(%1$s)", 'n', "e", "السرعة y لـ ___", "Up / down speed (m/s).", "سرعة الصعود والهبوط (م/ث).");
		rp("3D speed z of ___", B + "velZ(%1$s)", 'n', "e", "السرعة z لـ ___", "Speed on z (m/s).", "السرعة على z (م/ث).");
		rp("3D speed of ___", B + "speed(%1$s)", 'n', "e", "سرعة ___", "Total speed (m/s).", "السرعة الكلية (م/ث).");
		rp("___ is on ground (3D)", B + "isGrounded(%1$s)", 'b', "e", "___ على الأرض (ثلاثي)", "True when it stands on something.", "صحيح عندما يقف على شيء.");
		rp("___ touches ___ (3D)", B + "touching(%1$s, %2$s)", 'b', "ee", "___ يلمس ___ (ثلاثي)", "True while the two elements touch.", "صحيح أثناء تلامس العنصرين.");
		rp("3D ray from ___ ___ ___ toward ___ ___ ___ within ___ hits", B + "raycast(" + fl(1, 7) + ")", 'b', "nnnnnnn",
				"شعاع من ___ ___ ___ نحو ___ ___ ___ ضمن ___ يصيب", "Casts a ray from a point in a direction; true when it hits something.", "يطلق شعاعاً من نقطة باتجاه؛ صحيح إذا أصاب شيئاً.");
		rp("3D ray forward from ___ within ___ hits", B + "raycastForward(%1$s, " + f(2) + ")", 'b', "en",
				"شعاع للأمام من ___ ضمن ___ يصيب", "Ray from the element's front.", "شعاع من أمام العنصر.");
		rp("3D ray down from ___ within ___ hits", X + "raycastDown(%1$s, " + f(2) + ")", 'b', "en",
				"شعاع للأسفل من ___ ضمن ___ يصيب", "Ray straight down from the element.", "شعاع للأسفل مباشرة من العنصر.");
		rp("___ can see ___ within ___ (3D)", X + "canSee(%1$s, %2$s, " + f(3) + ")", 'b', "een",
				"___ يرى ___ ضمن ___ (ثلاثي)", "True when nothing blocks the line between them (enemy sight).", "صحيح إذا لم يحجب شيء الخط بينهما (رؤية العدو).");
		rp("3D screen ray at ___ ___ hits", B + "raycastScreen(" + fl(1, 2) + ")", 'b', "nn",
				"شعاع الشاشة عند ___ ___ يصيب", "Ray from a screen point (pixels, y down) through the camera.", "شعاع من نقطة على الشاشة (بكسل، y للأسفل) عبر الكاميرا.");
		rp("3D hit element", B + "rayHit()", 'n', "", "العنصر المصاب", "Element hit by the last ray (null = none).", "العنصر الذي أصابه آخر شعاع (null = لا شيء).");
		rp("3D hit x", B + "rayHitX()", 'n', "", "x الإصابة", "x of the last ray hit point.", "x لنقطة إصابة آخر شعاع.");
		rp("3D hit y", B + "rayHitY()", 'n', "", "y الإصابة", "y of the last ray hit point.", "y لنقطة إصابة آخر شعاع.");
		rp("3D hit z", B + "rayHitZ()", 'n', "", "z الإصابة", "z of the last ray hit point.", "z لنقطة إصابة آخر شعاع.");
		rp("3D hit distance", B + "rayHitDistance()", 'n', "", "مسافة الإصابة", "Distance to the last ray hit (meters).", "المسافة إلى إصابة آخر شعاع (بالأمتار).");
		rp("3D element at touch", B + "elementAtScreen(" + GDX + "input.getX(), " + GDX + "input.getY())", 'n', "",
				"العنصر عند اللمس", "The 3D element under the finger (null = none).", "العنصر ثلاثي الأبعاد تحت الإصبع (null = لا شيء).");
		rp("3D element at screen ___ ___", B + "elementAtScreen(" + fl(1, 2) + ")", 'n', "nn",
				"العنصر عند الشاشة ___ ___", "Element at a screen point (pixels, y down).", "العنصر عند نقطة على الشاشة (بكسل، y للأسفل).");
		rp("3D gravity x", X + "gravityX()", 'n', "", "الجاذبية x", "World gravity on x.", "جاذبية العالم على x.");
		rp("3D gravity y", X + "gravityY()", 'n', "", "الجاذبية y", "World gravity on y (normally -9.81).", "جاذبية العالم على y (عادة -9.81).");
		rp("3D gravity z", X + "gravityZ()", 'n', "", "الجاذبية z", "World gravity on z.", "جاذبية العالم على z.");

		// ---------------------------------------------------------------- 3D Looks
		cat(LOOKS, 0xFF8E24AA, D3, "3D Looks", "المظهر ثلاثي الأبعاد");
		st("setColor3D", "body(Body) color", "current|#FF8800", B + "setColor(%1$s, " + q(2) + ");",
				"Set color", "اضبط اللون", "Color of the element (#RRGGBB). On a light it is the light color.", "لون العنصر (#RRGGBB). على الضوء هو لون الضوء.");
		st("setTexture3D", "body(Body) pic(image)", "current|", B + "setTexture(%1$s, " + q(2) + ");",
				"Set texture", "اضبط الخامة", "Covers the element with a picture of the project.", "يغطي العنصر بصورة من المشروع.");
		st("setOpacity3D", "body(Body) alpha", "current|0.5", B + "setOpacity(%1$s, " + f(2) + ");",
				"Set opacity", "اضبط الشفافية", "0 = invisible, 1 = solid.", "0 = غير مرئي، 1 = صلب.");
		st("setEmissive3D", "body(Body) color intensity", "current|#FFAA00|1", B + "setEmissive(%1$s, " + q(2) + ", " + f(3) + ");",
				"Glow color", "لون التوهج", "Makes it shine by itself (lamps, lava). Intensity 0 = off.", "يجعله يضيء بنفسه (مصابيح، حمم). الشدة 0 = إيقاف.");
		st("setVisible3D", "body(Body) visible(Boolean)", "current|true", B + "setVisible(%1$s, %2$s);",
				"Set visible", "اضبط الظهور", "Shows (true) or hides (false) the element.", "يُظهر (true) أو يخفي (false) العنصر.");
		st("toggleVisible3D", "body(Body)", "current", X + "toggleVisible(%1$s);", "Show / hide", "إظهار / إخفاء", "Hides it if shown, shows it if hidden.", "يخفيه إن كان ظاهراً ويظهره إن كان مخفياً.");
		st("setOutline3D", "body(Body) on(Boolean) color width", "current|true|#FFFFFF|0.03", B + "setOutline(%1$s, %2$s, " + q(3) + ", " + f(4) + ");",
				"Outline", "حدود خارجية", "Draws a colored outline around it (selection, cartoon look).", "يرسم حداً ملوناً حوله (تحديد، مظهر كرتوني).");
		st("setShape3D", "body(Body) shape(shape3D)", "current|CUBE", B + "setShape(%1$s, " + q(2) + ");",
				"Set shape", "اضبط الشكل", "Changes a shape element: CUBE, SPHERE, CYLINDER, CAPSULE...", "يغيّر عنصر الشكل: CUBE أو SPHERE أو CYLINDER أو CAPSULE...");
		st("setModel3D", "body(Body) model(model)", "current|model.glb", B + "setModel(%1$s, " + q(2) + ");",
				"Set model", "اضبط المجسم", "Shows another .glb / .gltf model of the models folder.", "يعرض مجسماً آخر .glb أو .gltf من مجلد models.");
		st("setPartVisible3D", "body(Body) part visible(Boolean)", "current|\"wheel\"|true", B + "setNodeVisible(%1$s, " + str(2) + ", %3$s);",
				"Show model part", "أظهر جزءاً من المجسم", "Shows or hides a named part (node) of the model.", "يُظهر أو يخفي جزءاً مسمى (عقدة) من المجسم.");
		st("rotatePart3D", "body(Body) part x y z", "current|\"wheel\"|10|0|0", B + "rotateNode(%1$s, " + str(2) + ", " + fl(3, 5) + ");",
				"Rotate model part by", "دوّر جزء المجسم بمقدار", "Adds degrees to a part of the model (wheels, doors, turret).", "يضيف درجات إلى جزء من المجسم (عجلات، أبواب، برج).");
		st("setPartRotation3D", "body(Body) part x y z", "current|\"door\"|0|90|0", B + "setNodeRotation(%1$s, " + str(2) + ", " + fl(3, 5) + ");",
				"Set model part rotation", "اضبط دوران جزء المجسم", "Sets the rotation of a part of the model.", "يضبط دوران جزء من المجسم.");
		rp("3D opacity of ___", X + "opacity(%1$s)", 'n', "e", "شفافية ___", "Opacity 0..1.", "الشفافية من 0 إلى 1.");
		rp("___ is shown (3D)", X + "isVisible(%1$s)", 'b', "e", "___ ظاهر (ثلاثي)", "True when the element is visible.", "صحيح عندما يكون العنصر ظاهراً.");
		rp("___ has model part ___", X + "hasNode(%1$s, " + str(2) + ")", 'b', "et", "___ يملك الجزء ___", "True when the model has a part with this name.", "صحيح إذا كان للمجسم جزء بهذا الاسم.");

		// ---------------------------------------------------------------- 3D Animation
		cat(ANIM, 0xFFD81B60, D3, "3D Animation", "تحريك المجسمات");
		st("playAnimation3D", "body(Body) clip loop(Boolean) speed", "current|\"Run\"|true|1", B + "playAnimation(%1$s, " + str(2) + ", %3$s, " + f(4) + ");",
				"Play animation", "شغّل حركة", "Plays a clip of the model (its name in the model).", "يشغّل مقطعاً من المجسم (باسمه في المجسم).");
		st("crossFade3D", "body(Body) clip loop(Boolean) speed seconds", "current|\"Walk\"|true|1|0.3", B + "crossFade(%1$s, " + str(2) + ", %3$s, " + fl(4, 5) + ");",
				"Blend to animation", "انتقل بسلاسة إلى حركة", "Fades smoothly from the current clip to another.", "ينتقل بنعومة من المقطع الحالي إلى آخر.");
		st("playOnce3D", "body(Body) clip speed", "current|\"Jump\"|1", B + "playOnce(%1$s, " + str(2) + ", " + f(3) + ");",
				"Play once", "شغّل مرة واحدة", "Plays a clip one time, then goes back to the previous one.", "يشغّل المقطع مرة واحدة ثم يعود إلى السابق.");
		st("stopAnimation3D", "body(Body)", "current", B + "stopAnimation(%1$s);", "Stop animation", "أوقف الحركة", "Stops the model's animation.", "يوقف حركة المجسم.");
		st("pauseAnimation3D", "body(Body)", "current", B + "pauseAnimation(%1$s);", "Pause animation", "أوقف الحركة مؤقتاً", "Freezes the clip where it is.", "يجمّد المقطع في مكانه.");
		st("resumeAnimation3D", "body(Body)", "current", B + "resumeAnimation(%1$s);", "Resume animation", "تابع الحركة", "Continues a paused clip.", "يتابع مقطعاً موقوفاً مؤقتاً.");
		st("animationSpeed3D", "body(Body) speed", "current|1", B + "setAnimationSpeed(%1$s, " + f(2) + ");",
				"Animation speed", "سرعة الحركة", "1 = normal, 2 = twice as fast, 0.5 = slow motion.", "1 = طبيعي، 2 = ضعف السرعة، 0.5 = حركة بطيئة.");
		rp("___ is animating (3D)", B + "isAnimationPlaying(%1$s)", 'b', "e", "___ يتحرك (ثلاثي)", "True while a clip plays.", "صحيح أثناء تشغيل مقطع.");
		rp("3D clip of ___", B + "animation(%1$s)", 't', "e", "مقطع ___", "Name of the clip playing now.", "اسم المقطع المشغّل الآن.");
		rp("3D clip names of ___", B + "animationNames(%1$s)", 't', "e", "أسماء مقاطع ___", "All clip names of the model, separated by commas.", "أسماء كل مقاطع المجسم مفصولة بفواصل.");
		rp("3D clip count of ___", X + "animationCount(%1$s)", 'n', "e", "عدد مقاطع ___", "How many clips the model has.", "عدد مقاطع المجسم.");

		// ---------------------------------------------------------------- 3D Camera
		cat(CAMERA, 0xFF3949AB, D3, "3D Camera", "الكاميرا ثلاثية الأبعاد");
		st("setMainCamera3D", "camera(Body)", "current", B + "setMainCamera(%1$s);",
				"Use camera", "استخدم الكاميرا", "Makes this camera element the one that shows the scene.", "يجعل عنصر الكاميرا هذا هو الذي يعرض المشهد.");
		st("cameraFollow3D", "target(Body) mode(followMode)", "current|THIRD_PERSON", B + "follow(null, %1$s, " + q(2) + ");",
				"Camera follow", "الكاميرا تتبع", "The main camera follows an element: THIRD_PERSON, FIRST_PERSON, ORBIT, TOP_DOWN, SIDE or LOOK_AT.", "الكاميرا الرئيسية تتبع عنصراً: THIRD_PERSON أو FIRST_PERSON أو ORBIT أو TOP_DOWN أو SIDE أو LOOK_AT.");
		st("cameraStopFollow3D", "", "", B + "stopFollow(null);", "Camera stop following", "الكاميرا تتوقف عن التتبع", "The camera stays where it is.", "تبقى الكاميرا في مكانها.");
		st("cameraDistance3D", "distance height", "6|2.5", B + "setFollowDistance(null, " + fl(1, 2) + ");",
				"Follow distance", "مسافة التتبع", "How far behind and how high the following camera stays (meters).", "كم تبقى الكاميرا خلف الهدف وعلى أي ارتفاع (بالأمتار).");
		st("cameraFov3D", "fov", "60", B + "setFov(null, " + f(1) + ");", "Camera view angle", "زاوية رؤية الكاميرا", "Field of view in degrees (smaller = zoom in).", "مجال الرؤية بالدرجات (أصغر = تقريب).");
		st("cameraZoomTo3D", "fov seconds", "40|0.5", B + "zoomTo(null, " + fl(1, 2) + ");", "Camera zoom to", "قرّب الكاميرا إلى", "Changes the view angle smoothly (sniper zoom).", "يغيّر زاوية الرؤية بنعومة (تقريب القناص).");
		st("cameraShake3D", "strength", "0.3", B + "shake(null, " + f(1) + ");", "Shake camera", "هز الكاميرا", "Shakes the camera (hits, explosions).", "يهز الكاميرا (ضربات، انفجارات).");
		st("cameraTouchLook3D", "on(Boolean)", "true", B + "setTouchLook(null, %1$s);", "Look around by touch", "النظر حولك باللمس", "Dragging on the screen turns the camera.", "السحب على الشاشة يدير الكاميرا.");
		st("cameraMoveTo3D", "x y z seconds", "0|5|10|1", X + "cameraTo(" + fl(1, 4) + ");", "Camera to x, y, z", "الكاميرا إلى x و y و z", "Moves the main camera to a point (0 seconds = at once).", "ينقل الكاميرا الرئيسية إلى نقطة (0 ثانية = فوراً).");
		st("cameraLookAt3D", "x y z", "0|0|0", B + "lookAt(" + B + "cam(), " + fl(1, 3) + ");", "Camera look at point", "الكاميرا تنظر إلى نقطة", "Turns the main camera toward a point.", "يوجّه الكاميرا الرئيسية نحو نقطة.");
		st("cameraLookAtElement3D", "target(Body)", "current", X + "cameraLookAtItem(%1$s);", "Camera look at element", "الكاميرا تنظر إلى عنصر", "Turns the main camera toward an element.", "يوجّه الكاميرا الرئيسية نحو عنصر.");
		rp("3D screen x of ___", B + "screenX(%1$s)", 'n', "e", "x الشاشة لـ ___", "Pixels from the left where the element is drawn (-1 = behind the camera).", "البكسلات من اليسار حيث يُرسم العنصر (-1 = خلف الكاميرا).");
		rp("3D screen y of ___", B + "screenY(%1$s)", 'n', "e", "y الشاشة لـ ___", "Pixels from the bottom where the element is drawn.", "البكسلات من الأسفل حيث يُرسم العنصر.");
		rp("___ is on screen (3D)", B + "isOnScreen(%1$s)", 'b', "e", "___ على الشاشة (ثلاثي)", "True when the camera sees the element.", "صحيح عندما ترى الكاميرا العنصر.");
		rp("3D camera x", B + "x(" + B + "cam())", 'n', "", "x الكاميرا", "x of the main camera.", "x للكاميرا الرئيسية.");
		rp("3D camera y", B + "y(" + B + "cam())", 'n', "", "y الكاميرا", "Height of the main camera.", "ارتفاع الكاميرا الرئيسية.");
		rp("3D camera z", B + "z(" + B + "cam())", 'n', "", "z الكاميرا", "z of the main camera.", "z للكاميرا الرئيسية.");
		rp("3D camera view angle", X + "fov()", 'n', "", "زاوية رؤية الكاميرا", "Field of view of the main camera (degrees).", "مجال رؤية الكاميرا الرئيسية (درجات).");
		rp("3D camera follow mode", X + "followMode()", 't', "", "وضع تتبع الكاميرا", "NONE or the follow mode.", "NONE أو وضع التتبع.");
		rp("3D main camera", B + "cam()", 'n', "", "الكاميرا الرئيسية", "The camera element showing the scene.", "عنصر الكاميرا الذي يعرض المشهد.");

		// ---------------------------------------------------------------- 3D Lights & Sky
		cat(LIGHTS, 0xFFEF8F00, D3, "3D Lights & Sky", "الأضواء والسماء");
		st("lightOn3D", "light(Body) on(Boolean)", "current|true", B + "setLightOn(%1$s, %2$s);", "Light on / off", "تشغيل / إطفاء الضوء", "Turns a light on (true) or off (false).", "يشغّل الضوء (true) أو يطفئه (false).");
		st("toggleLight3D", "light(Body)", "current", B + "toggleLight(%1$s);", "Toggle light", "بدّل الضوء", "Switches a light on / off.", "يبدّل الضوء بين التشغيل والإطفاء.");
		st("lightIntensity3D", "light(Body) intensity", "current|1", B + "setLightIntensity(%1$s, " + f(2) + ");", "Light intensity", "شدة الضوء", "Brightness of the light.", "سطوع الضوء.");
		st("lightRange3D", "light(Body) range", "current|10", B + "setLightRange(%1$s, " + f(2) + ");", "Light range", "مدى الضوء", "How far the light reaches (meters).", "إلى أي مدى يصل الضوء (بالأمتار).");
		st("lightColor3D", "light(Body) color", "current|#FFE0B0", B + "setColor(%1$s, " + q(2) + ");", "Light color", "لون الضوء", "Color of the light (#RRGGBB).", "لون الضوء (#RRGGBB).");
		st("lightCone3D", "light(Body) degrees", "current|35", X + "setConeAngle(%1$s, " + f(2) + ");", "Spot light angle", "زاوية الضوء الموجّه", "Opening of a spot light (degrees).", "فتحة الضوء الموجّه (درجات).");
		st("lightFlicker3D", "light(Body) amount speed", "current|0.3|8", B + "setFlicker(%1$s, " + fl(2, 3) + ");", "Light flicker", "وميض الضوء", "Makes the light flicker (fire, broken lamp). Amount 0 = steady.", "يجعل الضوء يومض (نار، مصباح معطوب). المقدار 0 = ثابت.");
		st("ambient3D", "color intensity", "#737882|1", B + "setAmbient(" + q(1) + ", " + f(2) + ");", "Ambient light", "الإضاءة المحيطة", "Light that reaches everything evenly.", "ضوء يصل إلى كل شيء بالتساوي.");
		st("sun3D", "on(Boolean) color intensity", "true|#FFF5E0|1", B + "setSun(%1$s, " + q(2) + ", " + f(3) + ");", "Sun", "الشمس", "Turns the sun on / off and sets its color and strength.", "يشغّل الشمس أو يطفئها ويضبط لونها وقوتها.");
		st("sunAngle3D", "pitch yaw", "-55|35", B + "setSunAngle(" + fl(1, 2) + ");", "Sun angle", "زاوية الشمس", "Height (pitch) and direction (yaw) of the sun in degrees.", "ارتفاع الشمس واتجاهها بالدرجات.");
		st("lightingPreset3D", "preset(lightPreset)", "DAY", B + "setLightingPreset(" + q(1) + ");", "Lighting preset", "إضاءة جاهزة", "Ready lighting: DAY, SUNSET, NIGHT, OVERCAST, STUDIO, HORROR, INDOOR, CARTOON or CUSTOM.", "إضاءة جاهزة: DAY أو SUNSET أو NIGHT أو OVERCAST أو STUDIO أو HORROR أو INDOOR أو CARTOON أو CUSTOM.");
		st("fog3D", "on(Boolean) color near far", "true|#C7DBF2|40|160", B + "setFog(%1$s, " + q(2) + ", " + fl(3, 4) + ");", "Fog", "الضباب", "Fog starts at near and hides everything after far (meters).", "يبدأ الضباب عند «قريب» ويخفي كل شيء بعد «بعيد» (بالأمتار).");
		st("fogOff3D", "", "", X + "fogOff();", "No fog", "بلا ضباب", "Removes the fog.", "يزيل الضباب.");
		st("sky3D", "top bottom", "#5C9EF2|#C7E0FA", B + "setSky(" + q(1) + ", " + q(2) + ");", "Sky colors", "ألوان السماء", "Sky gradient from the top color to the horizon color.", "تدرج السماء من لون الأعلى إلى لون الأفق.");
		st("skyImage3D", "pic(image)", "sky.png", B + "setSkyImage(" + q(1) + ");", "Sky picture", "صورة السماء", "Uses a picture (panorama) of the project as the sky.", "يستخدم صورة (بانوراما) من المشروع كسماء.");
		st("renderMode3D", "mode(renderMode)", "SIMPLE", B + "setRenderMode(" + q(1) + ");", "Render style", "نمط الرسم", "SIMPLE, SOFT, TOON (cartoon), PBR (realistic) or UNLIT.", "SIMPLE أو SOFT أو TOON (كرتوني) أو PBR (واقعي) أو UNLIT.");
		st("shadows3D", "on(Boolean)", "true", B + "setShadows(%1$s);", "Shadows on / off", "تشغيل / إيقاف الظلال", "Sun shadows.", "ظلال الشمس.");
		st("shadowDistance3D", "distance", "40", X + "setShadowDistance(" + f(1) + ");", "Shadow distance", "مسافة الظلال", "Shadows are drawn up to this far from the camera.", "تُرسم الظلال حتى هذه المسافة من الكاميرا.");
		st("exposure3D", "amount", "1", X + "setExposure(" + f(1) + ");", "Brightness", "السطوع", "Brightness of the whole picture (1 = normal).", "سطوع الصورة كاملة (1 = طبيعي).");
		rp("___ light is on (3D)", X + "isLightOn(%1$s)", 'b', "e", "ضوء ___ يعمل (ثلاثي)", "True when the light is on.", "صحيح عندما يكون الضوء يعمل.");
		rp("3D light intensity of ___", X + "lightIntensity(%1$s)", 'n', "e", "شدة ضوء ___", "Brightness of the light.", "سطوع الضوء.");
		rp("3D lighting preset", X + "lightingPreset()", 't', "", "الإضاءة الجاهزة", "Name of the lighting preset.", "اسم الإضاءة الجاهزة.");
		rp("3D render style", X + "renderMode()", 't', "", "نمط الرسم", "SIMPLE, SOFT, TOON, PBR or UNLIT.", "SIMPLE أو SOFT أو TOON أو PBR أو UNLIT.");

		// ---------------------------------------------------------------- 3D Effects & Shaders
		cat(EFFECTS, 0xFF00ACC1, D3, "3D Effects & Shaders", "التأثيرات والشيدر");
		st("postEffect3D", "effect(postEffect) amount", "VIGNETTE|0.8", B + "setPostEffect(" + q(1) + ", " + f(2) + ");",
				"Screen effect", "تأثير الشاشة", "Effect on the whole 3D picture: VIGNETTE, NIGHT_VISION, GRAYSCALE, SEPIA, HORROR... Amount 0..1.", "تأثير على صورة المشهد كلها: VIGNETTE أو NIGHT_VISION أو GRAYSCALE أو SEPIA أو HORROR... المقدار من 0 إلى 1.");
		st("postAmount3D", "amount", "0.5", X + "setPostAmount(" + f(1) + ");", "Screen effect amount", "مقدار تأثير الشاشة", "Strength of the screen effect (0..1).", "قوة تأثير الشاشة (من 0 إلى 1).");
		st("postEffectOff3D", "", "", X + "postEffectOff();", "No screen effect", "بلا تأثير شاشة", "Removes the screen effect.", "يزيل تأثير الشاشة.");
		st("postCode3D", "code(glsl)", "vec4 post(vec4 c, vec2 uv){ return vec4(1.0 - c.rgb, 1.0); }", B + "setPostCode(" + q(1) + ");",
				"Screen effect code", "كود تأثير الشاشة", "Your own effect: vec4 post(vec4 color, vec2 uv). u_time, u_texture, u_resolution and u_amount are ready.", "تأثيرك الخاص: vec4 post(vec4 color, vec2 uv). المتغيرات u_time و u_texture و u_resolution و u_amount جاهزة.");
		st("setShader3D", "body(Body) shader(shader) params", "current|builtin:glow|speed=1", B + "setShader(%1$s, " + q(2) + ", " + q(3) + ");",
				"Element shader", "شيدر العنصر", "builtin:glow, builtin:hologram... or a file of the shaders folder (shaders/name). Params: speed=2;tint=#ff0000", "builtin:glow أو builtin:hologram... أو ملف من مجلد shaders (shaders/name). المعاملات: speed=2;tint=#ff0000");
		st("shaderCode3D", "body(Body) code(glsl)", "current|vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos){ return vec4(color.rgb * lambert(normal), color.a); }",
				B + "setShaderCode(%1$s, " + q(2) + ");",
				"Element shader code", "كود شيدر العنصر", "Write vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos) (and optional vec3 displace(vec3 pos, vec3 normal)).", "اكتب vec4 effect(vec4 color, vec2 uv, vec3 normal, vec3 worldPos) (واختيارياً vec3 displace(vec3 pos, vec3 normal)).");
		st("shaderFloat3D", "body(Body) param value", "current|speed|2", B + "setShaderFloat(%1$s, " + q(2) + ", " + f(3) + ");",
				"Shader number", "رقم الشيدر", "Sets a number of the element's shader (uniform u_<param>).", "يضبط رقماً في شيدر العنصر (المتغير u_<param>).");
		st("shaderColor3D", "body(Body) param color", "current|tint|#FF0000", B + "setShaderParam(%1$s, " + q(2) + ", " + q(3) + ");",
				"Shader color", "لون الشيدر", "Sets a color of the element's shader (uniform u_<param>).", "يضبط لوناً في شيدر العنصر (المتغير u_<param>).");
		st("clearShader3D", "body(Body)", "current", X + "clearShader(%1$s);", "Remove shader", "أزل الشيدر", "The element is drawn normally again.", "يُرسم العنصر بشكل عادي من جديد.");
		rp("3D screen effect", X + "postEffect()", 't', "", "تأثير الشاشة", "Name of the screen effect.", "اسم تأثير الشاشة.");
		rp("3D shader error of ___", X + "shaderError(%1$s)", 't', "e", "خطأ شيدر ___", "Compile error of the element's shader (\"\" = fine).", "خطأ ترجمة شيدر العنصر (\"\" = سليم).");
		rp("3D time", X + "time()", 'n', "", "الوقت ثلاثي الأبعاد", "Seconds since the 3D scene started (u_time).", "الثواني منذ بدء المشهد (u_time).");

		// ---------------------------------------------------------------- 3D Graphics quality
		cat(QUALITY_CAT, 0xFF546E7A, D3, "3D Graphics quality", "جودة الرسومات");
		st("setQuality3D", "level(quality)", "MEDIUM", B + "setQuality(" + q(1) + ");", "Graphics quality", "جودة الرسومات", "LOW, MEDIUM, HIGH or ULTRA. Saved for the next time the game opens.", "LOW أو MEDIUM أو HIGH أو ULTRA. تُحفظ لفتح اللعبة التالي.");
		st("nextQuality3D", "", "", X + "nextQuality();", "Next quality", "الجودة التالية", "LOW > MEDIUM > HIGH > ULTRA > LOW (a settings button).", "LOW > MEDIUM > HIGH > ULTRA > LOW (زر إعدادات).");
		st("renderScale3D", "scale", "0.8", B + "setRenderScale(" + f(1) + ");", "Render scale", "دقة الرسم", "0.4..1: size of the 3D picture (lower = faster).", "من 0.4 إلى 1: حجم صورة المشهد (أقل = أسرع).");
		st("viewDistance3D", "distance", "150", B + "setViewDistance(" + f(1) + ");", "View distance", "مسافة الرؤية", "Things farther than this (meters) are not drawn.", "لا تُرسم الأشياء الأبعد من هذه المسافة (بالأمتار).");
		st("shadowQuality3D", "size", "1024", B + "setShadowQuality((int)(%1$s));", "Shadow quality", "جودة الظلال", "0 = no shadows, 512, 1024, 2048 or 4096.", "0 = بلا ظلال، 512 أو 1024 أو 2048 أو 4096.");
		st("fpsLimit3D", "fps", "60", B + "setFpsLimit((int)(%1$s));", "Frame limit", "حد الإطارات", "Frames per second limit (0 = no limit, 30 saves battery).", "حد الإطارات في الثانية (0 = بلا حد، 30 يوفر البطارية).");
		st("waterQuality3D", "detail", "1", B + "setWaterQuality((int)(%1$s));", "Water quality", "جودة الماء", "0 = flat, 1 = waves, 2 = waves + foam.", "0 = مسطح، 1 = أمواج، 2 = أمواج مع رغوة.");
		st("postEffectsAllowed3D", "on(Boolean)", "true", X + "setPostEffectsAllowed(%1$s);", "Allow screen effects", "اسمح بتأثيرات الشاشة", "Turns screen effects off on weak phones.", "يوقف تأثيرات الشاشة على الهواتف الضعيفة.");
		st("showGraphicsMenu3D", "arabic(Boolean)", "false", "com.star4droid.template.d3.GraphicsMenu3D.show(activeStage(), %1$s);",
				"Show graphics settings", "اعرض إعدادات الرسومات", "Opens a ready settings panel (quality, render scale, shadows...). arabic = true shows Arabic texts.", "يفتح لوحة إعدادات جاهزة (الجودة، دقة الرسم، الظلال...). arabic = true يعرض النصوص بالعربية.");
		st("hideGraphicsMenu3D", "", "", "com.star4droid.template.d3.GraphicsMenu3D.hide();",
				"Hide graphics settings", "أخفِ إعدادات الرسومات", "Closes the graphics settings panel.", "يغلق لوحة إعدادات الرسومات.");
		rp("3D quality", B + "quality()", 't', "","الجودة", "LOW, MEDIUM, HIGH, ULTRA or CUSTOM.", "LOW أو MEDIUM أو HIGH أو ULTRA أو CUSTOM.");
		rp("3D fps", B + "fps()", 'n', "", "الإطارات في الثانية", "Frames drawn per second now.", "عدد الإطارات المرسومة في الثانية الآن.");
		rp("3D render scale", X + "renderScale()", 'n', "", "دقة الرسم", "Size of the 3D picture (0.3..1).", "حجم صورة المشهد (0.3 إلى 1).");
		rp("3D view distance", X + "viewDistance()", 'n', "", "مسافة الرؤية", "Drawing distance (meters).", "مسافة الرسم (بالأمتار).");
		rp("3D shadow quality", X + "shadowQuality()", 'n', "", "جودة الظلال", "Shadow map size (0 = off).", "حجم خريطة الظلال (0 = إيقاف).");
		rp("3D frame limit", X + "fpsLimit()", 'n', "", "حد الإطارات", "Frames per second limit.", "حد الإطارات في الثانية.");
		rp("3D water quality", X + "waterQuality()", 'n', "", "جودة الماء", "0 flat, 1 waves, 2 waves + foam.", "0 مسطح، 1 أمواج، 2 أمواج مع رغوة.");

		// ---------------------------------------------------------------- 3D Water & Terrain
		cat(WORLD, 0xFF2E7D32, D3, "3D Water & Terrain", "الماء والتضاريس");
		st("waves3D", "water(Body) height speed length", "current|0.2|1|4", B + "setWaves(%1$s, " + fl(2, 4) + ");", "Waves", "الأمواج", "Wave height, speed and length of a water element (meters).", "ارتفاع وسرعة وطول أمواج عنصر الماء (بالأمتار).");
		st("waterColors3D", "water(Body) shallow deep", "current|#3FA9D6|#0B3D6B", B + "setWaterColors(%1$s, " + q(2) + ", " + q(3) + ");", "Water colors", "ألوان الماء", "Colors of shallow and deep water.", "ألوان الماء الضحل والعميق.");
		st("snapToGround3D", "body(Body) terrain(Body) height", "current|current|0", X + "snapToGround(%1$s, %2$s, " + f(3) + ");", "Put on ground", "ضع على الأرض", "Places the element on the terrain (plus a height).", "يضع العنصر على التضاريس (مع ارتفاع إضافي).");
		rp("3D ground height of ___ at ___ ___", B + "groundHeight(%1$s, " + fl(2, 3) + ")", 'n', "enn", "ارتفاع أرض ___ عند ___ ___", "Height of a terrain at x, z.", "ارتفاع التضاريس عند x و z.");
		rp("3D water level of ___ at ___ ___", B + "groundHeight(%1$s, " + fl(2, 3) + ")", 'n', "enn", "مستوى ماء ___ عند ___ ___", "Height of the water surface (with waves) at x, z.", "ارتفاع سطح الماء (مع الأمواج) عند x و z.");

		// ---------------------------------------------------------------- 3D Objects
		cat(OBJECTS, 0xFF6D4C41, D3, "3D Objects", "الكائنات ثلاثية الأبعاد");
		st("spawn3D", "body(Body) var newName x y z", "current|copy|\"copy\"|0|1|0",
				"com.star4droid.template.Utils.PlayerItem %2$s = " + B + "spawn(%1$s, " + str(3) + ", " + fl(4, 6) + ");",
				"Make a copy at", "أنشئ نسخة عند", "Copies an element to a point (bullets, coins, enemies). Use the variable name after it.", "ينسخ عنصراً إلى نقطة (رصاص، عملات، أعداء). استخدم اسم المتغير بعده.");
		st("spawnAt3D", "body(Body) var newName place(Body)", "current|copy|\"copy\"|current",
				"com.star4droid.template.Utils.PlayerItem %2$s = " + X + "spawnAt(%1$s, " + str(3) + ", %4$s);",
				"Make a copy at element", "أنشئ نسخة عند عنصر", "Copies an element to where another element is (a bullet at the gun).", "ينسخ عنصراً إلى مكان عنصر آخر (رصاصة عند السلاح).");
		st("destroy3D", "body(Body)", "current", B + "destroy(%1$s);", "Destroy", "احذف", "Removes the element from the game.", "يزيل العنصر من اللعبة.");
		st("destroyAfter3D", "body(Body) seconds", "current|2", X + "destroyAfter(%1$s, " + f(2) + ");", "Destroy after", "احذف بعد", "Removes the element after some seconds.", "يزيل العنصر بعد عدد من الثواني.");
		rp("3D size x of ___", B + "sizeX(%1$s)", 'n', "e", "العرض x لـ ___", "Width of the element (meters).", "عرض العنصر (بالأمتار).");
		rp("3D size y of ___", B + "sizeY(%1$s)", 'n', "e", "الارتفاع y لـ ___", "Height of the element (meters).", "ارتفاع العنصر (بالأمتار).");
		rp("3D size z of ___", B + "sizeZ(%1$s)", 'n', "e", "العمق z لـ ___", "Depth of the element (meters).", "عمق العنصر (بالأمتار).");
		rp("___ is destroyed (3D)", X + "isDestroyed(%1$s)", 'b', "e", "___ محذوف (ثلاثي)", "True when the element was removed.", "صحيح إذا أزيل العنصر.");
		rp("3D copy of ___ named ___ at ___ ___ ___", B + "spawn(%1$s, " + str(2) + ", " + fl(3, 5) + ")", 'n', "etnnn",
				"نسخة من ___ باسم ___ عند ___ ___ ___", "Makes a copy and gives it (put it in an element field).", "ينشئ نسخة ويعيدها (ضعها في حقل عنصر).");

		// ---------------------------------------------------------------- 3D Sound
		cat(SOUND, 0xFFE53935, D3, "3D Sound", "الصوت المجسم");
		st("playSoundAt3D", "sound(sound) body(Body) distance", "hit.mp3|current|30", B + "playSoundAt(" + q(1) + ", %2$s, " + f(3) + ");",
				"Play sound at element", "شغّل صوتاً عند عنصر", "Louder near the camera, silent beyond the distance (meters), left / right by position.", "أعلى قرب الكاميرا وصامت بعد المسافة (بالأمتار)، يميناً ويساراً حسب الموضع.");
		st("playSound3D", "sound(sound) volume", "hit.mp3|1", B + "playSound(" + q(1) + ", " + f(2) + ");", "Play sound", "شغّل صوتاً", "Plays a sound of the sounds folder (volume 0..1).", "يشغّل صوتاً من مجلد sounds (مستوى الصوت من 0 إلى 1).");

		// ---------------------------------------------------------------- Inventory (2D and 3D)
		cat(INVENTORY, 0xFF7CB342, SceneDims.BOTH, "Inventory", "حقيبة الأغراض");
		st("addItem", "id count", "\"coin\"|1", B + "addItem(" + str(1) + ", (int)(%2$s));", "Add item", "أضف غرضاً", "Puts items in the player's inventory (shared by all scenes).", "يضع أغراضاً في حقيبة اللاعب (مشتركة بين كل المشاهد).");
		st("removeItem", "id count", "\"coin\"|1", B + "removeItem(" + str(1) + ", (int)(%2$s));", "Remove item", "أزل غرضاً", "Takes items out (nothing happens if there are not enough).", "يُخرج أغراضاً (لا يحدث شيء إن لم تكفِ).");
		st("setItemCount", "id count", "\"coin\"|0", X + "setItemCount(" + str(1) + ", (int)(%2$s));", "Set item count", "اضبط عدد الغرض", "Sets how many of an item the player has (0 removes it).", "يضبط عدد الغرض لدى اللاعب (0 يزيله).");
		st("selectItem", "id", "\"sword\"", X + "selectItem(" + str(1) + ");", "Select item", "اختر غرضاً", "Marks the item in the player's hand.", "يحدد الغرض الذي في يد اللاعب.");
		st("clearInventory", "", "", X + "clearInventory();", "Empty inventory", "أفرغ الحقيبة", "Removes every item.", "يزيل كل الأغراض.");
		cb("ifHasItem", "id", "\"key\"", B + "hasItem(" + str(1) + ")", "If has item", "إذا كان يملك غرضاً", "Runs the first part when the player has the item, else the second.", "ينفّذ الجزء الأول إذا كان اللاعب يملك الغرض، وإلا الثاني.");
		rp("has item ___", B + "hasItem(" + str(1) + ")", 'b', "t", "يملك الغرض ___", "True when the player has at least one.", "صحيح إذا كان لدى اللاعب واحد على الأقل.");
		rp("count of item ___", B + "itemCount(" + str(1) + ")", 'n', "t", "عدد الغرض ___", "How many of the item the player has.", "كم يملك اللاعب من هذا الغرض.");
		rp("item at ___", X + "inventoryAt((int)(%1$s))", 't', "n", "الغرض رقم ___", "Id of the n-th item (from 0, in pickup order).", "معرّف الغرض رقم n (من 0 بترتيب الالتقاط).");
		rp("inventory size", X + "inventorySize()", 'n', "", "عدد أنواع الأغراض", "How many different items the player has.", "عدد الأغراض المختلفة لدى اللاعب.");
		rp("selected item", X + "selectedItem()", 't', "", "الغرض المختار", "Id of the item in hand (\"\" = none).", "معرّف الغرض في اليد (\"\" = لا شيء).");
		s = null;
	}
}
