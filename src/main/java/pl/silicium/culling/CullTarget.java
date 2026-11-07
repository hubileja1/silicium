package pl.silicium.culling;

import net.minecraft.world.phys.AABB;

/**
 * Visibility state shared between the render thread (writes box / lastRequested, reads visibility)
 * and the culling thread (reads box, writes visibility).
 */
public final class CullTarget {
	/** How long an object stays visible after it was last seen - hides result latency, prevents popping. */
	static final long KEEP_VISIBLE_MS = 300;

	volatile AABB box;
	volatile long lastRequested;
	volatile long visibleUntil;
	volatile boolean registered;

	/** Render thread only: entity currently hidden by the distance limit (for hysteresis). */
	public boolean farHidden;

	/** Last known visibility without registering a request (for debugging / tests). */
	public boolean lastKnownVisible() {
		return !registered || System.currentTimeMillis() < visibleUntil;
	}

	/** Called by the render thread every frame the object would be rendered. */
	public boolean isVisible(AABB currentBox, long now) {
		box = currentBox;
		lastRequested = now;
		if (!registered) {
			registered = true;
			visibleUntil = now + KEEP_VISIBLE_MS; // unknown yet -> visible
			OcclusionCuller.register(this);
		}
		return now < visibleUntil;
	}
}
