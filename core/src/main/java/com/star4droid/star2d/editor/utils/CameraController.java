package com.star4droid.star2d.editor.utils;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Vector3;

public class CameraController {
    private final OrthographicCamera camera;
    private boolean isMoving = false;
    private float moveDuration;
    private float elapsedTime;
    private Vector3 startPosition;
    private Vector3 targetPosition;
    private Interpolation interpolation;

    public CameraController(OrthographicCamera camera) {
        this.camera = camera;
        this.interpolation = Interpolation.linear; // Default interpolation
    }

    public void moveTo(float targetX, float targetY, float duration) {
        this.moveDuration = duration;
        this.elapsedTime = 0;
        this.startPosition = camera.position.cpy();
        this.targetPosition = new Vector3(targetX, targetY, 0);
        this.isMoving = true;
    }

    public void update(float delta) {
        if (!isMoving) return;

        elapsedTime += delta;
        float progress = Math.min(1, elapsedTime / moveDuration);

        // Calculate interpolated position
        float x = interpolation.apply(startPosition.x, targetPosition.x, progress);
        float y = interpolation.apply(startPosition.y, targetPosition.y, progress);
        float zoom = interpolation.apply(camera.zoom, 2f, progress);
		
        camera.position.set(x, y, 0);
		camera.zoom = zoom;
        camera.update();

        if (progress >= 1) {
            isMoving = false;
        }
    }

    // Optional: Change interpolation type
    public void setInterpolation(Interpolation interpolation) {
        this.interpolation = interpolation;
    }

    public boolean isMoving() {
        return isMoving;
    }
}