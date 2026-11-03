import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.star4droid.star2d.ElementDefs.Camera3DDef;
import com.star4droid.star2d.ElementDefs.Object3DDef;
import com.star4droid.star2d.behaviors.BehaviorMeta;
import com.star4droid.star2d.behaviors.ElementTypes;
import com.star4droid.star2d.behaviors.ReflectScanner;
import com.star4droid.star2d.behaviors.SourceScanner;
import com.star4droid.star2d.behaviors.VarSpec;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.*;
import com.star4droid.template.behaviors.lib.*;
import com.star4droid.template.d3.Camera3D;
import com.star4droid.template.d3.Object3D;
import com.star4droid.template.d3.World3D;

import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;

/**
 * Run by tools/test_lib_3d.py. Part 1: conventions of every 3D behavior (lib/*3D.java): THREE_D target + valid types, icon in the
 * catalog and unique, @Param everywhere with ranges and units, @Block texts with Arabic, events fired == declared, Java 7 (no lambdas,
 * no android.*), text scanner == reflection (types too). Part 2: 2D / 3D fit rules of the editor (BehaviorMeta). Part 3: a headless
 * smoke run of the 3D behaviors that need no physics or GL (EMPTY meshes, no Bullet).
 */
public class Lib3DTest {
	static int failures = 0, checks = 0;
	static final float DT = 1f / 60f;

	static void check(boolean ok, String what) {
		checks++;
		if (!ok) {
			failures++;
			System.out.println("FAIL " + what);
		}
	}

	static String read(File f) throws Exception {
		return new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
	}

	public static void main(String[] a) throws Exception {
		String root = a[0];
		List<String> ids = metadata(root);
		fitRules();
		simulate();
		if (a.length > 1 && "bullet".equals(a[1])) vehicle();
		else System.out.println("SKIP Car3D Bullet drive test (gdx-bullet-platform natives-desktop jar not found in ~/.gradle)");
		System.out.println(ids.size() + " 3D behaviors, " + checks + " checks, " + failures + " failures");
		System.exit(failures == 0 ? 0 : 1);
	}

	// ================================================================= part 1

	static List<String> metadata(String root) throws Exception {
		Set<String> icons = new HashSet<String>();
		JsonObject cat = new Gson().fromJson(read(new File(root, "app/src/main/assets/icons/catalog.json")), JsonObject.class);
		for (JsonElement e : cat.getAsJsonArray("icons")) icons.add(e.getAsJsonObject().get("i").getAsString());
		Set<String> categories = new HashSet<String>(Arrays.asList("Movement", "AI", "Vehicles", "Motion", "Animation", "Interaction", "Combat", "Gameplay", "Lights", "Audio", "Water", "Camera"));
		File lib = new File(root, "app/src/main/java/com/star4droid/template/behaviors/lib");
		Set<String> usedIcons = new HashSet<String>();
		List<String> ids = new ArrayList<String>();
		File[] files = lib.listFiles();
		Arrays.sort(files);
		for (File f : files) {
			if (!f.getName().endsWith("3D.java")) continue;
			String id = f.getName().substring(0, f.getName().length() - 5);
			ids.add(id);
			String src = read(f);
			Class<?> c = Class.forName("com.star4droid.template.behaviors.lib." + id);
			check(Arrays.asList(BuiltIns.all()).contains(c), id + ": listed in BuiltIns (run tools/gen_behavior_builtins.py)");
			BehaviorMeta m = ReflectScanner.behavior(c);
			check(m != null, id + ": reflection metadata");
			if (m == null) continue;
			check(m.is3D(), id + ": targets THREE_D");
			check(!m.types.isEmpty(), id + ": types given");
			for (String t : m.types) check(ElementTypes.is3D(t), id + ": type " + t + " is a 3D type");
			check(m.name.length() >= 3 && m.name.endsWith("3D"), id + ": name ends with 3D");
			check(m.desc.length() >= 40 && m.desc.length() <= 420, id + ": desc length " + m.desc.length());
			check(icons.contains(m.icon), id + ": icon '" + m.icon + "' is in the catalog");
			check(usedIcons.add(m.icon), id + ": icon '" + m.icon + "' unique in the 3D library");
			check(categories.contains(m.category), id + ": category " + m.category);
			String code = src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
			check(!code.contains("android.") && !code.contains("->") && !code.contains("::"), id + ": no android.* / lambdas");
			check(src.contains("import com.star4droid.template.behaviors.Block;"), id + ": has blocks");
			check(!code.contains("com.badlogic.gdx.physics.bullet"), id + ": no Bullet types (use Object3D / Vehicle3D)");
			Set<String> declared = new HashSet<String>();
			for (BehaviorMeta.EventSpec e : m.events) declared.add(e.name);
			Matcher fm = Pattern.compile("fire\\(\\s*(?:[^\"]*\\?\\s*)?\"(on[A-Z]\\w*)\"(?:\\s*:\\s*\"(on[A-Z]\\w*)\")?").matcher(code);
			Set<String> fired = new HashSet<String>();
			while (fm.find()) {
				fired.add(fm.group(1));
				if (fm.group(2) != null) fired.add(fm.group(2));
			}
			for (String e : fired) check(declared.contains(e), id + ": fires undeclared event " + e);
			for (String e : declared) check(fired.contains(e), id + ": declared event never fired " + e);
			Object inst = c.newInstance();
			int nFields = 0;
			for (Field fl : c.getFields()) {
				int mod = fl.getModifiers();
				if (Modifier.isStatic(mod) || Modifier.isFinal(mod) || fl.isAnnotationPresent(Hidden.class) || fl.getDeclaringClass() == Behavior.class) continue;
				nFields++;
				Param p = fl.getAnnotation(Param.class);
				check(p != null, id + "." + fl.getName() + ": has @Param");
				if (p == null) continue;
				check(p.desc().length() >= 8, id + "." + fl.getName() + ": param desc");
				check(fl.getAnnotation(Header.class) != null || nFields > 1, id + "." + fl.getName() + ": first field starts a @Header group");
				Class<?> t = fl.getType();
				if (t == float.class || t == int.class || t == double.class) {
					check(p.min() > -1e8 && p.max() < 1e8 && p.max() > p.min(), id + "." + fl.getName() + ": explicit min/max");
					double v = ((Number) fl.get(inst)).doubleValue();
					check(v >= p.min() - 1e-6 && v <= p.max() + 1e-6, id + "." + fl.getName() + ": default " + v + " within [" + p.min() + "," + p.max() + "]");
					String n = fl.getName().toLowerCase();
					check(!(t == float.class && (n.contains("speed") || n.contains("distance") || n.contains("range") || n.endsWith("time")) && p.unit().isEmpty()), id + "." + fl.getName() + ": unit");
				}
			}
			check(nFields >= 3, id + ": at least 3 parameters (" + nFields + ")");
			int blocks = 0;
			for (Method me : c.getDeclaredMethods()) {
				Block b = me.getAnnotation(Block.class);
				if (b == null) continue;
				blocks++;
				String w = id + "." + me.getName();
				check(b.label().length() >= 3 && b.desc().length() >= 3 && b.labelAr().length() >= 2 && b.descAr().length() >= 3 && b.category().length() >= 3, w + ": @Block texts");
				check(b.args().length == me.getParameterTypes().length, w + ": @Block args names");
				check(Modifier.isPublic(me.getModifiers()), w + ": public");
			}
			check(blocks >= 3, id + ": at least 3 blocks (" + blocks + ")");
			BehaviorMeta s = SourceScanner.behavior(src, f.getPath());
			check(s != null, id + ": text scanner finds the class");
			if (s != null) {
				check(s.name.equals(m.name) && s.icon.equals(m.icon) && s.desc.equals(m.desc) && s.category.equals(m.category) && s.targets.equals(m.targets), id + ": scanner info equal");
				check(s.types.equals(m.types), id + ": scanner types " + s.types + " == " + m.types);
				check(s.exclusive.equals(m.exclusive) && s.events.size() == m.events.size(), id + ": scanner exclusive / events");
				check(s.vars.size() == m.vars.size(), id + ": scanner var count " + s.vars.size() + " vs " + m.vars.size());
				for (int i = 0; i < Math.min(s.vars.size(), m.vars.size()); i++) {
					VarSpec x = s.vars.get(i), y = m.vars.get(i);
					boolean same = x.name.equals(y.name) && x.kind == y.kind && x.list == y.list && x.elemType.equals(y.elemType.replace('$', '.')) && x.header.equals(y.header)
							&& x.desc.equals(y.desc) && x.unit.equals(y.unit) && x.min == y.min && x.max == y.max;
					check(same, id + "." + x.name + ": scanner spec == reflection " + x + " / " + y);
				}
			}
		}
		check(ids.size() >= 25, "at least 25 3D behaviors (" + ids.size() + ")");
		return ids;
	}

	// ================================================================= part 2

	static void fitRules() {
		BehaviorMeta fl = ReflectScanner.behavior(Flicker3D.class), car = ReflectScanner.behavior(Car3D.class), d2 = ReflectScanner.behavior(Floating.class);
		check(fl.misfit("STATIC", "BOX") != null, "3D behavior flagged on a 2D BOX");
		check(fl.misfit("UI", "TEXT") != null, "3D behavior flagged on a UI TEXT");
		check(fl.misfit("STATIC", "CAMERAVIEW") != null, "3D behavior flagged on a CAMERAVIEW (2D/UI element)");
		check(fl.misfit("STATIC", "LIGHT3D") == null, "Flicker3D fits a LIGHT3D");
		check(fl.misfit("STATIC", "OBJ3D") != null, "Flicker3D flagged on an OBJ3D (types)");
		check(car.misfit("STATIC", "OBJ3D") == null, "Car3D fits an OBJ3D");
		check(d2.misfit("STATIC", "OBJ3D") != null, "2D behavior flagged on an OBJ3D");
		check(d2.misfit("DYNAMIC", "BOX") == null, "2D behavior still fits a BOX");
		check(!fl.fits(true) && !fl.fits(false), "3D behaviors never fit 2D (fits)");
		check(fl.fits3D("LIGHT3D") && !fl.fits3D("OBJ3D") && !d2.fits3D("OBJ3D"), "fits3D");
		check(fl.physicalTypes().length == 0 && fl.uiTypes().length == 0 && fl.threeDTypes().length == 1, "3D type lists");
		check(d2.threeDTypes().length == 0, "2D behavior has no 3D types");
		BehaviorMeta src = SourceScanner.behavior("package p;\n@BehaviorInfo(name=\"Hover\", targets = BehaviorInfo.Target.THREE_D, types = {\"OBJ3D\", \"camera3d\"})\npublic class Hover extends Behavior { public float amp = 3; }", "x");
		check(src != null && src.is3D() && src.types.equals(Arrays.asList("OBJ3D", "CAMERA3D")), "scanner reads THREE_D + types " + (src == null ? null : src.types));
	}

	// ================================================================= part 3

	static final Set<Integer> keys = new HashSet<Integer>();

	static Object zero(Class<?> t) {
		if (t == boolean.class) return false;
		if (t == int.class) return 0;
		if (t == float.class) return 0f;
		if (t == long.class) return 0L;
		if (t == double.class) return 0d;
		return null;
	}

	static void installGdx() {
		Gdx.input = (Input) Proxy.newProxyInstance(Lib3DTest.class.getClassLoader(), new Class[] {Input.class}, new InvocationHandler() {
			public Object invoke(Object p, Method m, Object[] args) {
				if (m.getName().equals("isKeyPressed")) return keys.contains((Integer) args[0]);
				return zero(m.getReturnType());
			}
		});
		Gdx.graphics = (com.badlogic.gdx.Graphics) Proxy.newProxyInstance(Lib3DTest.class.getClassLoader(), new Class[] {com.badlogic.gdx.Graphics.class}, new InvocationHandler() {
			public Object invoke(Object p, Method m, Object[] args) {
				String n = m.getName();
				if (n.equals("getWidth")) return 720;
				if (n.equals("getHeight")) return 1560;
				if (n.equals("getDeltaTime")) return DT;
				return zero(m.getReturnType());
			}
		});
		Gdx.app = (Application) Proxy.newProxyInstance(Lib3DTest.class.getClassLoader(), new Class[] {Application.class}, new InvocationHandler() {
			public Object invoke(Object p, Method m, Object[] args) {
				if (m.getName().equals("error")) System.out.println("  [gdx error] " + Arrays.toString(args));
				return zero(m.getReturnType());
			}
		});
	}

	static Object3D empty(World3D w, String name, float x, float y, float z) {
		Object3DDef d = new Object3DDef();
		d.name = name;
		d.Mesh = "EMPTY";
		d.x = x;
		d.y = y;
		d.z = z;
		return Object3D.forWorld(w, d);
	}

	static Method SYNC;

	/** One game frame without GL: world logic, the transforms the renderer would compute, then the elements' updates (behaviors). */
	static void run(World3D w, int frames) {
		try {
			if (SYNC == null) {
				SYNC = Object3D.class.getDeclaredMethod("sync");
				SYNC.setAccessible(true);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		for (int i = 0; i < frames; i++) {
			w.update(DT, true);
			for (int k = 0; k < w.objects.size; k++) {
				try {
					SYNC.invoke(w.objects.get(k));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
			for (int k = 0; k < w.objects.size; k++) w.objects.get(k).update();
			for (int k = 0; k < w.cameras.size; k++) w.cameras.get(k).update();
		}
	}

	static boolean finite(Vector3Like v) {
		return !Float.isNaN(v.x) && !Float.isInfinite(v.x) && !Float.isNaN(v.y) && !Float.isNaN(v.z);
	}

	static final class Vector3Like {
		float x, y, z;

		Vector3Like(com.badlogic.gdx.math.Vector3 v) {
			x = v.x;
			y = v.y;
			z = v.z;
		}
	}

	static void simulate() {
		com.badlogic.gdx.utils.GdxNativesLoader.load();
		installGdx();
		World3D w = new World3D(null);
		World3D.current = w;
		Camera3DDef cd = new Camera3DDef();
		cd.name = "cam";
		cd.Active = true;
		Camera3D cam = Camera3D.forWorld(w, cd);
		w.setActiveCamera(cam);
		cam.position.set(0, 1.6f, 10);

		// Rotator: 90 deg/s for 1 s
		Object3D r = empty(w, "fan", 0, 0, 0);
		Rotator3D rot = new Rotator3D();
		r.addBehavior(rot);
		// Oscillator: 2 m up in 2 s, no pause
		Object3D lift = empty(w, "lift", 5, 0, 0);
		Oscillator3D osc = new Oscillator3D();
		osc.pause = 0;
		osc.motion = Oscillator3D.Motion.LINEAR;
		lift.addBehavior(osc);
		// path follower on the ground (no physics: the start height is the floor)
		Object3D walker = empty(w, "walker", 0, 0, -5);
		Object3D wp1 = empty(w, "wp1", 4, 0, -5), wp2 = empty(w, "wp2", 4, 0, -9);
		PathFollower3D pf = new PathFollower3D();
		pf.waypoints.add(wp1);
		pf.waypoints.add(wp2);
		pf.loop = PathFollower3D.Loop.ONCE;
		pf.speed = 4;
		walker.addBehavior(pf);
		// trigger watched by the camera
		Object3D zone = empty(w, "zone", 0, 0, 0);
		zone.setScale3(4, 4, 4);
		final int[] entered = {0};
		Trigger3D tr = new Trigger3D();
		tr.on("onEnter", new Behavior.Handler() {
			public void run(PlayerItem current, Object[] args) {
				entered[0]++;
			}
		});
		zone.addBehavior(tr);
		// a door the camera opens from blocks
		Object3D door = empty(w, "door", 0, 0, 3);
		Door3D dr = new Door3D();
		dr.openTime = 0.5f;
		door.addBehavior(dr);

		run(w, 60);
		check(Math.abs(Math.abs(r.getYaw3()) - 90) < 3, "Rotator3D: ~90 deg after 1 s (" + r.getYaw3() + ")");
		check(Math.abs(lift.position.y - 1) < 0.1f && osc.isGoingOut(), "Oscillator3D: half way after 1 s (" + lift.position.y + ")");
		check(entered[0] == 0, "Trigger3D: nobody inside yet");
		dr.open();
		run(w, 60);
		check(dr.isOpen() && Math.abs(Math.abs(door.euler.y) - 95) < 1, "Door3D: hinge swung 95 deg (" + door.euler.y + ")");
		check(finite(new Vector3Like(door.position)), "Door3D: finite position");
		dr.close();
		run(w, 60);
		check(!dr.isOpen() && Math.abs(door.euler.y) < 1 && door.position.dst(0, 0, 3) < 0.01f, "Door3D: closed back exactly");
		run(w, 200);
		check(pf.isFinished() && walker.position.dst(4, 0, -9) < 0.5f, "PathFollower3D: reached the last point on the floor (" + walker.position + ")");
		check(Math.abs(walker.position.y) < 0.01f, "PathFollower3D: stays on the floor height");
		cam.position.set(0, 1.6f, 0);
		run(w, 3);
		check(entered[0] == 1 && tr.isOccupied(), "Trigger3D: camera entered once");
		cam.position.set(0, 1.6f, 20);
		run(w, 3);
		check(!tr.isOccupied(), "Trigger3D: camera left");

		// first person controller on the camera: W walks forward (-Z), keeps eye height on the floor
		FirstPersonController3D fp = new FirstPersonController3D();
		cam.addBehavior(fp);
		run(w, 2);
		float z0 = cam.position.z, y0 = cam.position.y;
		keys.add(Input.Keys.W);
		run(w, 60);
		keys.clear();
		check(z0 - cam.position.z > 2 && z0 - cam.position.z < 3.5f, "FirstPersonController3D: W walks ~3 m forward (" + (z0 - cam.position.z) + ")");
		check(Math.abs(cam.position.y - y0) < 0.1f, "FirstPersonController3D: keeps the eye height (" + cam.position.y + " vs " + y0 + ")");
		// patrol wanders and stays in its radius
		Object3D guard = empty(w, "guard", 20, 0, 20);
		Patrol3D pt = new Patrol3D();
		pt.wanderRadius = 3;
		pt.waitMin = pt.waitMax = 0.2f;
		guard.addBehavior(pt);
		float far = 0;
		for (int i = 0; i < 600; i++) {
			run(w, 1);
			far = Math.max(far, guard.position.dst(20, 0, 20));
		}
		check(far > 0.5f && far < 3.5f, "Patrol3D: wanders within its radius (" + far + ")");

		// health on the camera player + a chaser that spots it, runs to it and hurts it
		Health3D hp = new Health3D();
		hp.invincibleTime = 0;
		cam.addBehavior(hp);
		cam.position.set(30, 1.6f, 0);
		Object3D monster = empty(w, "monster", 30, 0, -8);
		monster.setYaw3(180); // looks toward +Z, at the camera
		Chase3D ch = new Chase3D();
		ch.needLineOfSight = false;
		ch.damage = 30;
		ch.attackInterval = 0.5f;
		final int[] spotted = {0}, attacks = {0};
		ch.on("onSpotted", new Behavior.Handler() {
			public void run(PlayerItem current, Object[] args) {
				spotted[0]++;
			}
		});
		ch.on("onAttack", new Behavior.Handler() {
			public void run(PlayerItem current, Object[] args) {
				attacks[0]++;
			}
		});
		monster.addBehavior(ch);
		run(w, 5);
		check(spotted[0] == 1 && ch.isChasing(), "Chase3D: spots the camera in front of it");
		run(w, 240);
		check(monster.position.dst(30, 0, 0) < 2f, "Chase3D: ran to the player (" + monster.position + ")");
		check(attacks[0] >= 2, "Chase3D: attacks when close (" + attacks[0] + " attacks)");
		// (GameplaySupport.hit needs elements on a scene2d stage, so the damage itself is tested through the block)
		final int[] deaths = {0};
		hp.on("onDeath", new Behavior.Handler() {
			public void run(PlayerItem current, Object[] args) {
				deaths[0]++;
			}
		});
		hp.damage(60);
		check(Math.abs(hp.getHealth() - 40) < 0.01f && Math.abs(hp.getHealthPercent() - 40) < 0.01f, "Health3D: damage (" + hp.getHealth() + ")");
		hp.damage(60);
		run(w, 2);
		check(hp.isDead() && deaths[0] == 1, "Health3D: dies at 0, onDeath once");
		check(!fp.enabled, "Health3D: death pauses the movement controller");
		hp.revive();
		fp.setEnabled(true);
		ch.setEnabled(false);

		// interactable in range and looked at -> onInteract by block / prompt state
		cam.position.set(50, 1.6f, 0);
		cam.yaw = 0;
		cam.pitch = 0;
		Object3D lever = empty(w, "lever", 50, 1.5f, -1.5f);
		Interactable3D it = new Interactable3D();
		final int[] used = {0};
		it.on("onInteract", new Behavior.Handler() {
			public void run(PlayerItem current, Object[] args) {
				used[0]++;
			}
		});
		lever.addBehavior(it);
		run(w, 3);
		check(it.isInRange(), "Interactable3D: in range while close and looked at");
		cam.yaw = 180;
		run(w, 3);
		check(!it.isInRange(), "Interactable3D: not usable when looking away");
		it.interact();
		check(used[0] == 1, "Interactable3D: use block raises onInteract");

		// seat: sitting pauses the first person controller, standing resumes it
		cam.yaw = 0;
		Object3D bench = empty(w, "bench", 50, 0, -3);
		Seat3D seat = new Seat3D();
		seat.moveTime = 0.2f;
		bench.addBehavior(seat);
		run(w, 2);
		seat.sit();
		run(w, 30);
		check(seat.isSitting() && !fp.enabled, "Seat3D: sitting pauses the controller");
		check(Math.abs(cam.position.y - 1.1f) < 0.05f, "Seat3D: camera glided to the seat eye height (" + cam.position.y + ")");
		seat.stand();
		run(w, 30);
		check(!seat.isSitting() && fp.enabled, "Seat3D: standing resumes the controller");
	}

	// ================================================================= part 4: Car 3D on real Bullet physics (desktop natives)

	static Object3D body(World3D w, String name, String type, float mass, float x, float y, float z, float sx, float sy, float sz) {
		Object3DDef d = new Object3DDef();
		d.name = name;
		d.Mesh = "EMPTY";
		d.Body = type;
		d.Collider = "BOX";
		d.Mass = mass;
		d.x = x;
		d.y = y;
		d.z = z;
		d.Scale_X = sx;
		d.Scale_Y = sy;
		d.Scale_Z = sz;
		return Object3D.forWorld(w, d);
	}

	static float drive(World3D w, Car3D car, float seconds) {
		float best = 0;
		for (int i = 0; i < (int) (seconds / DT); i++) {
			run(w, 1);
			best = Math.max(best, car.getSpeedKmh());
		}
		return best;
	}

	static void vehicle() {
		World3D old = World3D.current;
		World3D w = new World3D(null);
		World3D.current = w;
		w.setGravity(0, -9.81f, 0);
		Camera3DDef cd = new Camera3DDef();
		cd.name = "carCam";
		cd.Active = true;
		Camera3D cam = Camera3D.forWorld(w, cd);
		w.setActiveCamera(cam);
		body(w, "ground", "STATIC", 0, 0, -0.5f, -500, 60, 1, 1400); // a huge static box: Physics3D makes it a triangle box (exact rays)
		// a 1200 kg sedan-sized box; its front is local -Z, like every 3D element
		Object3D o = body(w, "car", "DYNAMIC", 1200, 0, 0.6f, 0, 1.8f, 1.1f, 4.2f);
		Car3D car = new Car3D();
		car.useKeyboard = false;
		car.followCamera = false;
		car.wheelMode = Car3D.WheelMode.INVISIBLE;
		o.addBehavior(car);
		run(w, 90); // settle on the springs
		check(w.hasPhysics(), "Car3D: physics world running");
		check(car.isGrounded(), "Car3D: wheels touch the ground (" + o.position + ")");
		float z0 = o.position.z, x0 = o.position.x, yaw0 = o.getYaw3();
		car.setGas(1);
		float t100 = -1, t = 0;
		for (int i = 0; i < (int) (6f / DT); i++) {
			run(w, 1);
			t += DT;
			if (t100 < 0 && car.getSpeedKmh() >= 100) t100 = t;
		}
		float v6 = car.getSpeedKmh();
		System.out.println("  Car3D defaults: " + Math.round(v6) + " km/h after 6 s, 0-100 km/h in " + (t100 < 0 ? "never" : String.format(Locale.US, "%.1f s", t100)));
		check(v6 > 80, "Car3D: default car reaches more than 80 km/h in 6 s (" + v6 + ")");
		check(z0 - o.position.z > 50, "Car3D: gas drives toward the visible front (-Z) (" + (z0 - o.position.z) + " m)");
		check(Math.abs(o.getYaw3() - yaw0) < 1.5f, "Car3D: drives straight without steering (heading " + yaw0 + " -> " + o.getYaw3() + ")");
		float fz = o.forward(new com.badlogic.gdx.math.Vector3()).z;
		check(fz < -0.95f, "Car3D: still faces -Z (" + fz + ")");
		check(car.isGrounded(), "Car3D: all wheels stay on the road at full gas");
		// top speed param
		car.setMaxSpeed(60);
		run(w, 300);
		float v = car.getSpeedKmh();
		check(v > 52 && v < 68, "Car3D: top speed param holds 60 km/h (" + v + ")");
		car.setMaxSpeed(250);
		car.setAcceleration(14);
		float best = 0;
		for (int k = 0; k < 12; k++) {
			best = Math.max(best, drive(w, car, 1));
		}
		check(best > 200, "Car3D: no speed cap: top speed 250 km/h is reachable (" + best + ")");
		// steering: right input turns right (from -Z toward +X)
		car.setMaxSpeed(60);
		car.setAcceleration(5.5f);
		car.setGas(0);
		car.setBrake(true);
		float tb = 0;
		while (car.getSpeedKmh() > 5 && tb < 15) {
			run(w, 1);
			tb += DT;
		}
		car.setBrake(false);
		check(tb < 9, "Car3D: brakes from 250 km/h to a stop (" + tb + " s)");
		car.setGas(1);
		drive(w, car, 3);
		float xs = o.position.x;
		car.setSteering(1);
		drive(w, car, 1.2f);
		check(o.position.x - xs > 1f, "Car3D: steering right turns right (+X) (" + (o.position.x - xs) + ")");
		car.setSteering(0);
		// reverse
		car.setGas(-1);
		drive(w, car, 8);
		check(car.getSpeedKmh() < -5, "Car3D: brake / reverse goes backwards (" + car.getSpeedKmh() + ")");
		w.dispose();
		World3D.current = old;
	}
}
