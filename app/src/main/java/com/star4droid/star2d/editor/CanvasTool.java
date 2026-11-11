package com.star4droid.star2d.editor;

/**
 * A tool that takes the touches of the canvas (2D) or of the ground of the 3D view while it is on: painting a NavGrid,
 * drawing a path. One finger uses the tool, two fingers still move / zoom the view. Units: px in 2D, meters (x, z) in 3D.
 * GL thread.
 */
public interface CanvasTool {
	boolean active();

	/** True while one finger moves the view instead of using the tool. */
	boolean navigate();

	void down(float x, float y);

	void drag(float x, float y);

	void up(float x, float y);

	/** A second finger went down: the started stroke is dropped. */
	void cancel();

	/** 3D scenes: height of the ground plane the tool works on. */
	float groundHeight();
}
