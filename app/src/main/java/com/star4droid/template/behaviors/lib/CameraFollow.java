package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.GameplaySupport;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Camera Follow", icon = "camera", category = "Camera", targets = BehaviorInfo.Target.BOTH,
		desc = "The camera follows an element smoothly, with a dead zone, look-ahead in the direction of movement, scene limits and zoom. Works together with Camera Shake. Put it on the player or on any element and choose the target.")
public class CameraFollow extends Behavior {
	@Header("Target")
	@Param(desc = "The element to follow. Empty = the element that has this behavior.")
	public PlayerItem target;
	@Param(desc = "Follow horizontally.")
	public boolean followX = true;
	@Param(desc = "Follow vertically.")
	public boolean followY = true;
	@Param(desc = "Look this far to the right of the target (negative = left).", unit = "px", min = -5000, max = 5000)
	public float offsetX = 0;
	@Param(desc = "Look this far above the target (negative = below).", unit = "px", min = -5000, max = 5000)
	public float offsetY = 0;
	@Param(desc = "Put the camera on the target at the start (off: glide from where the camera is).")
	public boolean snapOnStart = true;

	@Header("Feel")
	@Param(desc = "How fast the camera catches up. Higher = tighter. 0 = glued to the target.", min = 0, max = 50, step = 0.5)
	public float followSpeed = 6;
	@Param(desc = "The target can move this far sideways before the camera moves at all.", unit = "px", min = 0, max = 4000)
	public float deadZoneWidth = 0;
	@Param(desc = "Same for the vertical movement.", unit = "px", min = 0, max = 4000)
	public float deadZoneHeight = 0;
	@Param(desc = "The camera looks this far ahead in the direction the target moves (physical targets).", unit = "px", min = 0, max = 2000)
	public float lookAhead = 0;
	@Param(desc = "Target speed at which the look-ahead is complete.", unit = "px/s", min = 10, max = 100000)
	public float lookAheadSpeed = 300;

	@Header("Zoom")
	@Param(desc = "1 = the scene's normal view, 2 = twice as close, 0.5 = twice as far.", min = 0.2, max = 5, step = 0.05)
	public float zoom = 1;
	@Param(desc = "How fast the zoom changes. 0 = at once.", min = 0, max = 20, step = 0.5)
	public float zoomSpeed = 3;

	@Header("Limits")
	@Param(desc = "Keep the view inside the rectangle below (the size of your level), so it never shows the empty outside.")
	public boolean useBounds = false;
	@Param(unit = "px", min = -100000, max = 100000)
	public float boundsLeft = 0;
	@Param(unit = "px", min = -100000, max = 100000)
	public float boundsBottom = 0;
	@Param(unit = "px", min = -100000, max = 100000)
	public float boundsRight = 1280;
	@Param(unit = "px", min = -100000, max = 100000)
	public float boundsTop = 720;

	private GameplaySupport.CameraRig rig;
	private float curX, curY, leadX, leadY, baseCamZoom = 1, curZoom = 1;
	private boolean first = true, zoomTouched, forceSnap;

	public void onStart() {
		rig = GameplaySupport.rig(stage);
		Camera cam = stage.getCamera();
		baseCamZoom = cam instanceof OrthographicCamera ? ((OrthographicCamera) cam).zoom : 1f;
		first = true;
	}

	@Block(label = "camera follow", desc = "The camera follows this element from now on.", labelAr = "الكاميرا تتبع", descAr = "الكاميرا تتبع هذا العنصر من الآن.", category = "Camera Follow", args = {"target"})
	public void setTarget(PlayerItem t) {
		target = t;
	}

	@Block(label = "camera jump to target", desc = "Moves the camera onto the target at once (no gliding).", labelAr = "اقفز بالكاميرا إلى الهدف", descAr = "ينقل الكاميرا إلى الهدف فوراً دون انسياب.", category = "Camera Follow")
	public void snapToTarget() {
		forceSnap = true;
	}

	@Block(label = "camera zoom", desc = "Zoom: 1 = normal, 2 = twice as close. It changes smoothly.", labelAr = "تقريب الكاميرا", descAr = "التقريب: 1 = عادي، 2 = أقرب بمرتين. يتغير بسلاسة.", category = "Camera Follow", args = {"zoom"})
	public void setZoomLevel(float z) {
		zoom = Math.max(0.2f, z);
	}

	@Block(label = "camera limits", desc = "Keeps the camera inside this rectangle (editor px).", labelAr = "حدود الكاميرا", descAr = "يبقي الكاميرا داخل هذا المستطيل (بكسل المحرر).", category = "Camera Follow", args = {"left", "bottom", "right", "top"})
	public void setBounds(float left, float bottom, float right, float top) {
		boundsLeft = left;
		boundsBottom = bottom;
		boundsRight = right;
		boundsTop = top;
		useBounds = true;
	}

	@Block(label = "camera no limits", desc = "Removes the camera limits.", labelAr = "بلا حدود للكاميرا", descAr = "يزيل حدود الكاميرا.", category = "Camera Follow")
	public void clearBounds() {
		useBounds = false;
	}

	@Block(label = "camera offset", desc = "Looks this far from the target (px).", labelAr = "إزاحة الكاميرا", descAr = "تنظر الكاميرا على هذه المسافة من الهدف (بكسل).", category = "Camera Follow", args = {"x", "y"})
	public void setOffset(float x, float y) {
		offsetX = x;
		offsetY = y;
	}

	@Block(label = "camera follows axes", desc = "Choose which directions the camera follows.", labelAr = "محاور تتبع الكاميرا", descAr = "اختر الاتجاهات التي تتبعها الكاميرا.", category = "Camera Follow", args = {"horizontal", "vertical"})
	public void setAxes(boolean horizontal, boolean vertical) {
		followX = horizontal;
		followY = vertical;
	}

	@Block(label = "camera zoom level", desc = "The zoom that is set (not the one in the middle of a change).", labelAr = "مستوى التقريب", descAr = "التقريب المضبوط حالياً.", category = "Camera Follow")
	public float getZoomLevel() {
		return zoom;
	}

	public void onUpdate(float dt) {
		PlayerItem t = target != null ? target : item;
		if (t == null || !alive(t)) return;
		Camera cam = stage.getCamera();
		if (rig == null) rig = GameplaySupport.rig(stage);
		float tx = cx(t) + offsetX, ty = cy(t) + offsetY;
		float a = followSpeed <= 0 ? 1f : 1f - (float) Math.exp(-followSpeed * dt);

		if (lookAhead > 0 && t.getBody() != null) {
			float gx = clamp(GameplaySupport.velX(stage, t) / lookAheadSpeed, -1f, 1f) * lookAhead;
			float gy = clamp(GameplaySupport.velY(stage, t) / lookAheadSpeed, -1f, 1f) * lookAhead;
			float al = 1f - (float) Math.exp(-3f * dt);
			leadX += (gx - leadX) * al;
			leadY += (gy - leadY) * al;
			tx += leadX;
			ty += leadY;
		}
		if (first || forceSnap) {
			boolean snap = forceSnap || snapOnStart;
			first = false;
			forceSnap = false;
			if (snap) {
				curX = followX ? tx : rig.baseX(cam) / W;
				curY = followY ? ty : rig.baseY(cam) / W;
				a = 1f;
			} else {
				curX = rig.baseX(cam) / W;
				curY = rig.baseY(cam) / W;
			}
		} else {
			// somebody else moved the camera (setCameraXY...): continue from there instead of jumping back
			float bx = rig.baseX(cam) / W, by = rig.baseY(cam) / W;
			if (Math.abs(bx - curX) > 0.5f) curX = bx;
			if (Math.abs(by - curY) > 0.5f) curY = by;
		}

		float goalX = curX, goalY = curY;
		if (followX) {
			float dx = tx - curX, half = deadZoneWidth * 0.5f;
			goalX = Math.abs(dx) > half ? tx - Math.signum(dx) * half : curX;
		}
		if (followY) {
			float dy = ty - curY, half = deadZoneHeight * 0.5f;
			goalY = Math.abs(dy) > half ? ty - Math.signum(dy) * half : curY;
		}
		curX += (goalX - curX) * a;
		curY += (goalY - curY) * a;

		// zoom first: the limits depend on how much of the world is visible
		if (zoom != 1f) zoomTouched = true;
		if (zoomTouched) {
			float az = zoomSpeed <= 0 ? 1f : 1f - (float) Math.exp(-zoomSpeed * dt);
			curZoom += (zoom - curZoom) * az;
			if (Math.abs(zoom - curZoom) < 0.001f) curZoom = zoom;
			float want = baseCamZoom / curZoom;
			if (cam instanceof OrthographicCamera && Math.abs(((OrthographicCamera) cam).zoom - want) > 0.0001f) stage.setZoom(1f / want);
		}
		float px = curX, py = curY;
		if (useBounds) {
			float hw = GameplaySupport.halfViewW(stage), hh = GameplaySupport.halfViewH(stage);
			px = boundsLeft + hw > boundsRight - hw ? (boundsLeft + boundsRight) * 0.5f : clamp(px, boundsLeft + hw, boundsRight - hw);
			py = boundsBottom + hh > boundsTop - hh ? (boundsBottom + boundsTop) * 0.5f : clamp(py, boundsBottom + hh, boundsTop - hh);
			curX = px;
			curY = py;
		}
		rig.write(cam, px * W, py * W, rig.offX, rig.offY);
	}
}
