package com.star4droid.star2d.editor.ui.scripting;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class NodeEditor extends Stage {

    /* ------------- constants ------------- */
    public static final float GRID_SQUARE_SIZE = 50f;

    /* ------------- fields ------------- */
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final OrthographicCamera cam;
	Vector2 camStart = new Vector2();
	float zoomSpeed = 0;

    public NodeEditor() {
        super(new ScreenViewport());
        cam = (OrthographicCamera) getCamera();
        shapeRenderer.setAutoShapeType(true);
    }

    public void addNode(VisualNode node) {
        addActor(node);
    }

    /* called from UiStage every frame */
    public void moveCamera(float dx, float dy) {
        cam.position.add(-dx * cam.zoom, dy * cam.zoom, 0);
        for (Actor a : getRoot().getChildren())
            if (a instanceof VisualNode) ((VisualNode) a).popupHide();
    }

    @Override
    public void draw() {
		if (zoomSpeed != 0) {
			cam.zoom += zoomSpeed * Gdx.graphics.getDeltaTime();
			cam.zoom = Math.max(0.1f, Math.min(3f, cam.zoom));
		}
        cam.update();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setProjectionMatrix(cam.combined);
        drawGrid();
        /* ---------- grid + connection lines ---------- */
        shapeRenderer.setColor(Color.WHITE);
        for (Actor a : getRoot().getChildren())
            if (a instanceof VisualNode)
                ((VisualNode) a).drawConnectionLines(shapeRenderer);
        shapeRenderer.end();
        super.draw();               // draw actors (nodes)

    }

    private void drawGrid() {
        float zoom = cam.zoom;
        float worldWidth  = getViewport().getWorldWidth()  * zoom;
        float worldHeight = getViewport().getWorldHeight() * zoom;

        float left   = cam.position.x - worldWidth  / 2;
        float bottom = cam.position.y - worldHeight / 2;

        /* start / end indices */
        int startX = (int) (left   / GRID_SQUARE_SIZE);
        int endX   = (int) ((left   + worldWidth)  / GRID_SQUARE_SIZE) + 1;
        int startY = (int) (bottom / GRID_SQUARE_SIZE);
        int endY   = (int) ((bottom + worldHeight) / GRID_SQUARE_SIZE) + 1;

        shapeRenderer.setColor(0.3f, 0.3f, 0.3f, 1f);

        /* vertical lines */
        for (int x = startX; x <= endX; x++) {
            float wx = x * GRID_SQUARE_SIZE;
            shapeRenderer.line(wx, bottom, wx, bottom + worldHeight);
        }
        /* horizontal lines */
        for (int y = startY; y <= endY; y++) {
            float wy = y * GRID_SQUARE_SIZE;
            shapeRenderer.line(left, wy, left + worldWidth, wy);
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        shapeRenderer.dispose();
    }
}
