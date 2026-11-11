package com.star4droid.star2d.ui.blocks;

import com.star4droid.star2d.blocks.SceneDims;

/**
 * Blocks of the path finding (Navigation), timeline / tween (Timeline) and local multiplayer (Network) systems, for 2D and
 * 3D scenes. They call the static facades {@code com.star4droid.template.nav.Nav}, {@code ...anim.Anim} and
 * {@code ...net.Net}, fully qualified, Java 7 only (the event code compiles on the phone). Units: px in 2D scenes, meters
 * (x, z on the ground) in 3D scenes. Same {@link Blocks3D.Sink} as the 3D blocks (checked by tools/test_blocks3d.py).
 */
public final class BlocksSystems {
	private BlocksSystems() {
	}

	static final String N = "com.star4droid.template.nav.Nav.", A = "com.star4droid.template.anim.Anim.", NET = "com.star4droid.template.net.Net.";

	public static final String NAVIGATION = "Navigation", TIMELINE = "Timeline", NETWORK = "Network";

	public static final String[] PATH_MODES = {"ONCE", "LOOP", "PING_PONG"};
	/** Properties a timeline / tween can animate (2D, then 3D; "var:name" = an element variable). */
	public static final String[] ANIM_PROPS = {"x", "y", "rotation", "Scale X", "Scale Y", "Opacity", "Tint", "width", "height", "Visible", "Text", "image",
			"z", "Rotation X", "Rotation Y", "Rotation Z", "Scale Z", "Color", "var:"};
	public static final String[] EASES = {"linear", "smooth", "easeIn", "easeOut", "sine", "bounce", "elastic", "back", "fast", "step"};

	private static Blocks3D.Sink s;
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

	private static String f(int i) {
		return "(float)(%" + i + "$s)";
	}

	private static String i(int i) {
		return "(int)(%" + i + "$s)";
	}

	private static String str(int i) {
		return "String.valueOf(%" + i + "$s)";
	}

	private static String q(int i) {
		return "\"%" + i + "$s\"";
	}

	private static void st(String title, String fields, String defaults, String code, String en, String ar, String ie, String ia) {
		s.def(cat, col, title, fields, defaults, false, code + "\n%" + (count(fields) + 1) + "$s", dims, en, ar, ie, ia);
	}

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

	public static synchronized void register(Blocks3D.Sink sink) {
		s = sink;
		int BOTH = SceneDims.BOTH;

		// ---------------------------------------------------------------- field names and value lists
		fd("grid", "grid", "الشبكة");
		fd("path", "path", "المسار");
		fd("from", "from", "من");
		fd("to", "to", "إلى");
		fd("target", "target", "الهدف");
		fd("speed", "speed", "السرعة");
		fd("x1", "from x", "من x");
		fd("y1", "from y", "من y");
		fd("x2", "to x", "إلى x");
		fd("y2", "to y", "إلى y");
		fd("cost", "slowness", "البطء");
		fd("obstacle", "obstacle", "العائق");
		fd("timeline", "timeline", "الخط الزمني");
		fd("prop", "property", "الخاصية");
		fd("value", "value", "القيمة");
		fd("seconds", "seconds", "ثوانٍ");
		fd("ease", "easing", "التسارع");
		fd("startDelay", "delay", "التأخير");
		fd("repeat", "repeat", "التكرار");
		fd("yoyo", "back and forth", "ذهاب وإياب");
		fd("room", "room", "الغرفة");
		fd("index", "number", "الرقم");
		fd("player", "player", "اللاعب");
		fd("message", "message", "الرسالة");
		fd("data", "data", "البيانات");
		fd("newName", "new name", "الاسم الجديد");
		s.choices("pathMode", PATH_MODES);
		s.choices("animProp", ANIM_PROPS);
		s.choices("ease", EASES);

		// ---------------------------------------------------------------- Navigation (path finding)
		cat(NAVIGATION, 0xFF00897B, BOTH, "Navigation (path finding)", "الملاحة (إيجاد المسار)");
		st("navGoTo", "body(Body) x y speed grid", "current|400|300|150|\"\"", N + "goTo(%1$s, %5$s, " + f(2) + ", " + f(3) + ", " + f(4) + ");",
				"Walk to point", "امشِ إلى نقطة",
				"Walks along the shortest way around the walls of the NavGrid (grid \"\" = the scene's grid). Then \"When path ends\".",
				"يمشي في أقصر طريق حول جدران شبكة المسارات (الشبكة \"\" = شبكة المشهد). ثم \"عند انتهاء المسار\".");
		st("navGoToElement", "body(Body) target(Body) speed grid", "current|current|150|\"\"", N + "goToElement(%1$s, %4$s, %2$s, " + f(3) + ");",
				"Walk to element", "امشِ إلى عنصر", "Walks to where another element is now (once). Use \"Chase on the grid\" to keep following it.",
				"يمشي إلى مكان عنصر آخر الآن (مرة واحدة). استخدم \"طارد على الشبكة\" ليتبعه باستمرار.");
		st("navFindPath", "grid from(Body) to(Body) path", "\"\"|current|current|way", N + "findPath(%1$s, %2$s, %3$s, %4$s);",
				"Find path between elements", "أوجد مساراً بين عنصرين",
				"Fills a path variable (type PathHolder) with the way from one element to another. Ready now or a few frames later.",
				"يملأ متغير مسار (نوع PathHolder) بالطريق من عنصر إلى آخر. جاهز الآن أو بعد إطارات قليلة.");
		st("navFindPathXY", "grid x1 y1 x2 y2 path", "\"\"|0|0|400|300|way", N + "findPathXY(%1$s, " + f(2) + ", " + f(3) + ", " + f(4) + ", " + f(5) + ", %6$s);",
				"Find path between points", "أوجد مساراً بين نقطتين", "Fills a path variable with the way between two points.", "يملأ متغير مسار بالطريق بين نقطتين.");
		st("navFollow", "body(Body) path speed mode(pathMode)", "current|way|150|ONCE", N + "follow(%1$s, %2$s, " + f(3) + ", " + q(4) + ");",
				"Follow path", "اتبع المسار",
				"Walks along a path (found or drawn), from its nearest point ahead. ONCE, LOOP (patrol) or PING_PONG. Physical elements move by velocity.",
				"يمشي على مسار (موجود أو مرسوم) من أقرب نقطة أمامه. ONCE أو LOOP (دورية) أو PING_PONG. العناصر الفيزيائية تتحرك بالسرعة.");
		st("navChase", "body(Body) target(Body) speed grid", "current|current|150|\"\"", N + "chase(%1$s, %4$s, %2$s, " + f(3) + ");",
				"Chase on the grid", "طارد على الشبكة", "Runs after a target around the walls; finds a new way when it moves or a door closes.",
				"يجري خلف هدف حول الجدران ويجد طريقاً جديداً عندما يتحرك أو يُغلق باب.");
		st("navFlee", "body(Body) target(Body) speed grid", "current|current|150|\"\"", N + "flee(%1$s, %4$s, %2$s, " + f(3) + ");",
				"Flee on the grid", "اهرب على الشبكة", "Runs away from a target to the farthest place it can reach.", "يهرب من هدف إلى أبعد مكان يمكنه الوصول إليه.");
		st("navPause", "body(Body)", "current", N + "pause(%1$s);", "Pause walking", "أوقف المشي مؤقتاً", "Stops on the path (resume goes on).", "يتوقف على المسار (الاستئناف يكمل).");
		st("navResume", "body(Body)", "current", N + "resume(%1$s);", "Resume walking", "استأنف المشي", "Goes on walking its path.", "يكمل المشي على مساره.");
		st("navStop", "body(Body)", "current", N + "stop(%1$s);", "Stop walking", "أوقف المشي", "Stops following / chasing for good.", "يتوقف عن التتبع أو المطاردة نهائياً.");
		st("navSetSpeed", "body(Body) speed", "current|200", N + "setSpeed(%1$s, " + f(2) + ");", "Set walking speed", "اضبط سرعة المشي",
				"Speed of a walking element (px/s in 2D, m/s in 3D).", "سرعة العنصر الماشي (بكسل/ث في 2D، م/ث في 3D).");
		st("navAddObstacle", "grid obstacle(Body)", "\"\"|current", N + "addObstacle(%1$s, %2$s);", "Add obstacle", "أضف عائقاً",
				"The element blocks the cells it covers (followed when it moves, opened when it is destroyed).", "يسد العنصر الخلايا التي يغطيها (يُتابَع عندما يتحرك، وتنفتح عند حذفه).");
		st("navRemoveObstacle", "grid obstacle(Body)", "\"\"|current", N + "removeObstacle(%1$s, %2$s);", "Remove obstacle", "أزل العائق",
				"Its cells open again (a door that opens).", "تنفتح خلاياه من جديد (باب يُفتح).");
		st("navCloseCell", "grid x y", "\"\"|0|0", N + "setCell(%1$s, " + f(2) + ", " + f(3) + ", true);", "Close cell at", "أغلق الخلية عند",
				"The cell under the point becomes a wall.", "تصبح الخلية تحت النقطة جداراً.");
		st("navOpenCell", "grid x y", "\"\"|0|0", N + "setCell(%1$s, " + f(2) + ", " + f(3) + ", false);", "Open cell at", "افتح الخلية عند",
				"The cell under the point can be walked again.", "يمكن المشي على الخلية تحت النقطة من جديد.");
		st("navCellCost", "grid x y cost", "\"\"|0|0|3", N + "setCellCost(%1$s, " + f(2) + ", " + f(3) + ", " + f(4) + ");", "Set cell slowness", "اضبط بطء الخلية",
				"1 = normal ... 9 = very slow (mud, water): paths avoid slow cells.", "1 = عادي ... 9 = بطيء جداً (طين، ماء): المسارات تتجنب الخلايا البطيئة.");
		st("navPlace", "body(Body) x y", "current|400|300", N + "place(%1$s, " + f(2) + ", " + f(3) + ");", "Put element at", "ضع العنصر عند",
				"Puts the center of the element at a point (px in 2D, meters x, z in 3D) and stops it.", "يضع مركز العنصر عند نقطة (بكسل في 2D، أمتار x و z في 3D) ويوقفه.");
		st("navPathAdd", "path x y", "way|0|0", N + "addPoint(%1$s, " + f(2) + ", " + f(3) + ");", "Add point to path", "أضف نقطة للمسار",
				"Adds a point at the end of a path variable.", "يضيف نقطة في نهاية متغير المسار.");
		st("navPathClear", "path", "way", N + "clear(%1$s);", "Clear path", "أفرغ المسار", "Removes every point of a path.", "يزيل كل نقاط المسار.");
		st("navPathReverse", "path", "way", N + "reverse(%1$s);", "Reverse path", "اعكس المسار", "The last point becomes the first.", "تصبح النقطة الأخيرة هي الأولى.");
		cb("ifWalkable", "x y", "0|0", N + "walkable(\"\", " + f(1) + ", " + f(2) + ")", "If walkable at", "إذا كان المشي ممكناً عند",
				"Runs the first part when an agent can stand at the point (scene's grid).", "ينفذ الجزء الأول إذا أمكن الوقوف عند النقطة (شبكة المشهد).");
		rp("walkable at ___ ___", N + "walkable(\"\", (float)(%1$s), (float)(%2$s))", 'b', "nn", "يمكن المشي عند ___ ___",
				"True when an agent can stand at the point (scene's grid).", "صحيح إذا أمكن الوقوف عند النقطة (شبكة المشهد).");
		rp("___ is walking", N + "isFollowing(%1$s)", 'b', "e", "___ يمشي", "True while the element walks a path or chases.", "صحيح أثناء مشي العنصر على مسار أو مطاردته.");
		rp("center x of ___", N + "xOf(%1$s)", 'n', "e", "x مركز ___", "x of the element's center (px in 2D, meters in 3D).", "x لمركز العنصر (بكسل في 2D، أمتار في 3D).");
		rp("center y of ___", N + "yOf(%1$s)", 'n', "e", "y مركز ___", "y of the element's center in 2D (px); its z on the ground in 3D.", "y لمركز العنصر في 2D (بكسل)؛ و z على الأرض في 3D.");
		rp("distance between ___ and ___", N + "distance(%1$s, %2$s)", 'n', "ee", "المسافة بين ___ و ___", "Distance between the centers of two elements.", "المسافة بين مركزي عنصرين.");
		rp("path ___ was found", N + "found(%1$s)", 'b', "p", "تم إيجاد المسار ___", "True when the search of the path is done and found a way.", "صحيح عندما ينتهي البحث ويجد طريقاً.");
		rp("no way in path ___", N + "notFound(%1$s)", 'b', "p", "لا طريق في المسار ___", "True when the search found no way.", "صحيح عندما لا يجد البحث طريقاً.");
		rp("length of path ___", N + "length(%1$s)", 'n', "p", "طول المسار ___", "Total length of the path.", "الطول الكلي للمسار.");
		rp("points in path ___", N + "size(%1$s)", 'n', "p", "عدد نقاط المسار ___", "How many points the path has.", "كم نقطة في المسار.");
		rp("x of point ___ of path ___", N + "pointX(%2$s, (float)(%1$s))", 'n', "np", "x للنقطة ___ من المسار ___", "x of a point (from 0).", "x لنقطة (من 0).");
		rp("y of point ___ of path ___", N + "pointY(%2$s, (float)(%1$s))", 'n', "np", "y للنقطة ___ من المسار ___", "y of a point (z in 3D).", "y لنقطة (z في 3D).");
		rp("nearest point of path ___ to ___", N + "nearest(%1$s, %2$s)", 'n', "pe", "أقرب نقطة من المسار ___ إلى ___", "Index of the point closest to an element.", "رقم النقطة الأقرب إلى عنصر.");
		rp("distance left for ___", N + "remaining(%1$s)", 'n', "e", "المسافة المتبقية لـ ___", "Length of the path still to walk.", "طول المسار المتبقي للمشي.");

		// ---------------------------------------------------------------- Timeline & Tween
		cat(TIMELINE, 0xFF8E24AA, BOTH, "Timeline & Tween", "الخط الزمني والتحريك");
		st("tlPlay", "name(timeline)", "intro", A + "play(activeStage(), " + q(1) + ");", "Play timeline", "شغّل الخط الزمني",
				"Plays a timeline made in the Timeline panel on the elements of its tracks.", "يشغّل خطاً زمنياً صنعته في لوحة الخط الزمني على عناصر مساراته.");
		st("tlPlayOn", "name(timeline) body(Body)", "jump|current", A + "play(activeStage(), " + q(1) + ", %2$s);", "Play timeline on element", "شغّل الخط الزمني على عنصر",
				"Plays a timeline on another element (its tracks marked @self move this element): one animation for many enemies.",
				"يشغّل خطاً زمنياً على عنصر آخر (مسارات @self تحرك هذا العنصر): حركة واحدة لأعداء كثيرين.");
		st("tlStop", "name(timeline)", "intro", A + "stop(activeStage(), " + q(1) + ");", "Stop timeline", "أوقف الخط الزمني", "The elements stay where they are.", "تبقى العناصر في أماكنها.");
		st("tlPause", "name(timeline)", "intro", A + "pause(activeStage(), " + q(1) + ");", "Pause timeline", "أوقف الخط الزمني مؤقتاً", "Freezes it (resume goes on).", "يجمده (الاستئناف يكمل).");
		st("tlResume", "name(timeline)", "intro", A + "resume(activeStage(), " + q(1) + ");", "Resume timeline", "استأنف الخط الزمني", "Goes on playing.", "يكمل التشغيل.");
		st("tlSeek", "name(timeline) seconds", "intro|0", A + "seek(activeStage(), " + q(1) + ", " + f(2) + ");", "Jump to time", "اقفز إلى وقت",
				"Moves the playing timeline to a time (seconds).", "ينقل الخط الزمني العامل إلى وقت (بالثواني).");
		st("tlSpeed", "name(timeline) speed", "intro|1", A + "setSpeed(activeStage(), " + q(1) + ", " + f(2) + ");", "Timeline speed", "سرعة الخط الزمني",
				"1 = normal, 2 = twice as fast, 0.5 = slow motion.", "1 = عادي، 2 = ضعف السرعة، 0.5 = حركة بطيئة.");
		st("tlReverse", "name(timeline)", "intro", A + "reverse(activeStage(), " + q(1) + ");", "Reverse timeline", "اعكس الخط الزمني",
				"Plays backward from where it is (again: forward).", "يعمل للخلف من مكانه (مرة أخرى: للأمام).");
		st("tween", "body(Body) prop(animProp) value seconds ease(ease)", "current|x|400|1|smooth",
				A + "tween(%1$s, " + q(2) + ", %3$s, " + f(4) + ", " + q(5) + ");", "Tween", "حرّك بنعومة",
				"Changes a property smoothly to a value in seconds: x, y, rotation, Scale X, Opacity, Tint \"#FF0000\", Text, var:score...",
				"يغيّر خاصية بنعومة إلى قيمة خلال ثوانٍ: x و y و rotation و Scale X و Opacity و Tint \"#FF0000\" و Text و var:score...");
		st("tweenFull", "body(Body) prop(animProp) value seconds ease(ease) startDelay repeat yoyo(Boolean)", "current|Scale X|1.3|0.4|sine|0|-1|true",
				A + "tween(%1$s, " + q(2) + ", %3$s, " + f(4) + ", " + q(5) + ", " + f(6) + ", " + i(7) + ", %8$s);", "Tween with delay and repeat", "حرّك بتأخير وتكرار",
				"Starts after delay seconds and plays repeat more times (-1 = forever); back and forth goes to the value and back (pulse, float).",
				"يبدأ بعد التأخير ويتكرر بعدد التكرار (-1 = للأبد)؛ ذهاب وإياب يذهب إلى القيمة ويعود (نبض، طفو).");
		st("tweenStop", "body(Body)", "current", A + "stopTweens(%1$s);", "Stop tweens", "أوقف الحركات", "Stops every tween of the element.", "يوقف كل حركات العنصر.");
		rp("timeline ___ is playing", A + "isPlaying(activeStage(), String.valueOf(%1$s))", 'b', "t", "الخط الزمني ___ يعمل", "True while the timeline plays.", "صحيح أثناء تشغيل الخط الزمني.");
		rp("time of timeline ___", A + "time(activeStage(), String.valueOf(%1$s))", 'n', "t", "وقت الخط الزمني ___", "Seconds since it started.", "الثواني منذ بدايته.");
		rp("duration of timeline ___", A + "duration(activeStage(), String.valueOf(%1$s))", 'n', "t", "مدة الخط الزمني ___", "Its length in seconds.", "طوله بالثواني.");
		rp("___ is tweening", A + "isTweening(%1$s)", 'b', "e", "___ يتحرك", "True while a tween of the element runs.", "صحيح أثناء حركة للعنصر.");

		// ---------------------------------------------------------------- Network (local multiplayer)
		cat(NETWORK, 0xFF43A047, BOTH, "Network (Wi-Fi play)", "الشبكة (لعب واي فاي)");
		st("netHost", "room", "\"\"", NET + "host(" + str(1) + ");", "Host a game", "استضف لعبة",
				"This phone opens a room on the Wi-Fi / hotspot and becomes player 1 (room \"\" = the NETWORK element's name).",
				"يفتح هذا الهاتف غرفة على الواي فاي أو نقطة الاتصال ويصبح اللاعب 1 (الغرفة \"\" = اسم عنصر الشبكة).");
		st("netSearch", "", "", NET + "findGames();", "Search for games", "ابحث عن ألعاب", "Looks for rooms on the network: \"When a game is found\" for each one.",
				"يبحث عن غرف على الشبكة: \"عند إيجاد لعبة\" لكل واحدة.");
		st("netJoin", "index", "0", NET + "join(" + i(1) + ");", "Join game number", "انضم إلى اللعبة رقم", "Joins a room found by the search (0 = the first).",
				"ينضم إلى غرفة وجدها البحث (0 = الأولى).");
		st("netJoinRoom", "room", "\"Room\"", NET + "joinRoom(" + str(1) + ");", "Join room", "انضم إلى الغرفة", "Joins a room by its name (or an address like 192.168.1.5).",
				"ينضم إلى غرفة باسمها (أو بعنوان مثل 192.168.1.5).");
		st("netLeave", "", "", NET + "leave();", "Leave the game", "غادر اللعبة", "Leaves the room (the host closes it for everybody).", "يغادر الغرفة (المضيف يغلقها للجميع).");
		st("netStart", "scene(scene)", "game", NET + "startForAll(" + q(1) + ");", "Start game for all", "ابدأ اللعبة للجميع",
				"Host: opens the scene on every phone (\"When the game starts\").", "المضيف: يفتح المشهد على كل الهواتف (\"عند بدء اللعبة\").");
		st("netLoadScene", "scene(scene)", "game", NET + "loadSceneForAll(" + q(1) + ");", "Load scene for all", "حمّل المشهد للجميع", "Host: every phone goes to this scene.",
				"المضيف: ينتقل كل هاتف إلى هذا المشهد.");
		st("netName", "name", "\"Player\"", NET + "setPlayerName(" + str(1) + ");", "Set my player name", "اضبط اسم لاعبي", "The name the other players see.", "الاسم الذي يراه اللاعبون الآخرون.");
		st("netSendAll", "message data", "\"hit\"|\"\"", NET + "sendToAll(" + str(1) + ", %2$s);", "Send to all", "أرسل للجميع",
				"A message to every other phone: \"When a message arrives\" (name, data, from).", "رسالة لكل الهواتف الأخرى: \"عند وصول رسالة\" (name، data، from).");
		st("netSendHost", "message data", "\"input\"|\"\"", NET + "sendToHost(" + str(1) + ", %2$s);", "Send to host", "أرسل للمضيف", "A message to the host only.", "رسالة للمضيف فقط.");
		st("netSendTo", "player message data", "2|\"hello\"|\"\"", NET + "sendTo(" + i(1) + ", " + str(2) + ", %3$s);", "Send to player", "أرسل للاعب",
				"A message to one player (by number).", "رسالة للاعب واحد (برقمه).");
		st("netRunAll", "message data", "\"boom\"|\"\"", NET + "runForAll(" + str(1) + ", %2$s);", "Run event at all", "نفّذ الحدث عند الجميع",
				"\"When a message arrives\" runs on every phone, this one too (effects everybody must see).", "\"عند وصول رسالة\" يعمل على كل الهواتف وهذا منها (تأثيرات يجب أن يراها الجميع).");
		st("netSpawn", "body(Body) var player", "current|copy|1", "com.star4droid.template.Utils.PlayerItem %2$s = " + NET + "spawn(%1$s, " + i(3) + ");",
				"Create networked copy for player", "أنشئ نسخة شبكية للاعب",
				"Host: copies an element on every phone, controlled by a player (1 = host). Use the variable name after it.",
				"المضيف: ينسخ عنصراً على كل الهواتف يتحكم به لاعب (1 = المضيف). استخدم اسم المتغير بعده.");
		st("netDespawn", "body(Body)", "current", NET + "despawn(%1$s);", "Delete networked element", "احذف العنصر الشبكي", "Removes it on every phone.", "يزيله من كل الهواتف.");
		st("netOwner", "body(Body) player", "current|1", NET + "setOwner(%1$s, " + i(2) + ");", "Give element to player", "أعطِ العنصر للاعب",
				"Host: another player controls it from now on.", "المضيف: يتحكم به لاعب آخر من الآن.");
		st("netShowLobby", "", "", NET + "showLobby();", "Show lobby", "اعرض صالة الانتظار", "The ready-made lobby: host, rooms, players, start.", "صالة الانتظار الجاهزة: استضافة، غرف، لاعبون، ابدأ.");
		st("netHideLobby", "", "", NET + "hideLobby();", "Hide lobby", "أخفِ صالة الانتظار", "Closes the lobby window.", "يغلق نافذة صالة الانتظار.");
		cb("ifHost", "", "", NET + "isHost()", "If I am the host", "إذا كنت المضيف", "The host runs the game rules (score, spawning, winner).", "المضيف ينفذ قواعد اللعبة (النقاط، الظهور، الفائز).");
		cb("ifMine", "body(Body)", "current", NET + "isMine(%1$s)", "If element is mine", "إذا كان العنصر لي",
				"True on the phone that controls the element: move it there (joystick), the others just show it.", "صحيح على الهاتف المتحكم بالعنصر: حركه هناك (عصا التحكم)، والبقية تعرضه فقط.");
		rp("I am the host", NET + "isHost()", 'b', "", "أنا المضيف", "True on the phone that opened the room.", "صحيح على الهاتف الذي فتح الغرفة.");
		rp("I am online", NET + "isConnected()", 'b', "", "أنا متصل", "True while hosting or joined.", "صحيح أثناء الاستضافة أو الانضمام.");
		rp("game started", NET + "gameStarted()", 'b', "", "بدأت اللعبة", "True once the host pressed start.", "صحيح بعد أن يضغط المضيف ابدأ.");
		rp("___ is mine", NET + "isMine(%1$s)", 'b', "e", "___ لي", "True on the phone that controls the element.", "صحيح على الهاتف المتحكم بالعنصر.");
		rp("my player number", NET + "myId()", 'n', "", "رقم لاعبي", "1 = host, 2, 3... for the others.", "1 = المضيف، 2، 3... للبقية.");
		rp("player count", NET + "playerCount()", 'n', "", "عدد اللاعبين", "Players in the game, me included.", "اللاعبون في اللعبة وأنا منهم.");
		rp("max players", NET + "maxPlayers()", 'n', "", "أقصى عدد للاعبين", "Seats of the room.", "مقاعد الغرفة.");
		rp("player number at ___", NET + "playerAt((int)(%1$s))", 'n', "n", "رقم اللاعب في الموضع ___", "Number of the n-th player (from 0).", "رقم اللاعب رقم n (من 0).");
		rp("name of player ___", NET + "playerName((int)(%1$s))", 't', "n", "اسم اللاعب ___", "Name of a player by number.", "اسم لاعب برقمه.");
		rp("my name", NET + "myName()", 't', "", "اسمي", "My player name.", "اسم لاعبي.");
		rp("ping", NET + "ping()", 'n', "", "زمن الاستجابة", "Milliseconds to the host and back.", "ميلي ثانية إلى المضيف والعودة.");
		rp("ping of player ___", NET + "pingOf((int)(%1$s))", 'n', "n", "زمن استجابة اللاعب ___", "Host: milliseconds to a player.", "المضيف: ميلي ثانية إلى لاعب.");
		rp("owner of ___", NET + "ownerOf(%1$s)", 'n', "e", "مالك ___", "Number of the player that controls the element.", "رقم اللاعب المتحكم بالعنصر.");
		rp("element of player ___", NET + "playerElement((int)(%1$s))", 'n', "n", "عنصر اللاعب ___", "The copy of the player template of a player.", "نسخة قالب اللاعب الخاصة بلاعب.");
		rp("my element", NET + "myElement()", 'n', "", "عنصري", "The copy of the player template I control.", "نسخة قالب اللاعب التي أتحكم بها.");
		rp("games found", NET + "roomCount()", 'n', "", "الألعاب الموجودة", "How many rooms the search found.", "كم غرفة وجد البحث.");
		rp("name of game ___", NET + "roomName((int)(%1$s))", 't', "n", "اسم اللعبة ___", "Name of a room found (from 0).", "اسم غرفة موجودة (من 0).");
		rp("players in game ___", NET + "roomPlayers((int)(%1$s))", 'n', "n", "اللاعبون في اللعبة ___", "Players already in a room found.", "اللاعبون الموجودون في غرفة.");
		rp("my address", NET + "myAddress()", 't', "", "عنواني", "This phone's address on the Wi-Fi (for joining by address).", "عنوان هذا الهاتف على الواي فاي (للانضمام بالعنوان).");
		rp("network error", NET + "lastError()", 't', "", "خطأ الشبكة", "Why the last join / host failed.", "سبب فشل آخر انضمام أو استضافة.");
		s = null;
	}
}
