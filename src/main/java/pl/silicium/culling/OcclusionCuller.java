package pl.silicium.culling;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
//#if MC >= 1.20.5
import net.minecraft.world.level.chunk.status.ChunkStatus;
//#else
import net.minecraft.world.level.chunk.ChunkStatus;
//#endif
import net.minecraft.world.phys.AABB;
import pl.silicium.Silicium;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Asynchronous occlusion culling.
 * <p>
 * A background thread casts rays (3D DDA voxel traversal) from the camera to the corners and center
 * of every entity / block entity that the game wants to draw. If every ray hits a solid opaque block,
 * the object is hidden behind walls and is skipped by the renderer. This is the biggest win in
 * bases, cities and underground: Sodium only culls whole chunk sections, not single objects behind walls.
 * <p>
 * The render thread never waits for it - it only reads a cached flag. Newly seen objects
 * start as visible, and objects stay visible for a short time after they were last seen.
 */
public final class OcclusionCuller {
	private static final ConcurrentLinkedQueue<CullTarget> NEW_TARGETS = new ConcurrentLinkedQueue<>();
	/** Targets not requested for this long (out of view / unloaded) are dropped. */
	private static final long STALE_MS = 2000;
	/** Objects closer than this are always visible. */
	private static final double ALWAYS_VISIBLE_DIST_SQ = 3 * 3;
	/** Rays longer than this are not traced (distance culling handles far objects). */
	private static final double MAX_TRACE_DIST_SQ = 128 * 128;
	private static final int MAX_STEPS = 256;

	private static volatile double camX, camY, camZ;
	private static volatile boolean cameraBlocked;

	private OcclusionCuller() {
	}

	public static void start() {
		Thread t = new Thread(OcclusionCuller::run, "Silicium Occlusion Culling");
		t.setDaemon(true);
		t.setPriority(Thread.NORM_PRIORITY - 1);
		t.start();
	}

	static void register(CullTarget target) {
		NEW_TARGETS.add(target);
	}

	/** Render thread: current camera position (called once per object check - cheap volatile writes). */
	public static void updateCamera(double x, double y, double z) {
		camX = x;
		camY = y;
		camZ = z;
	}

	/** Render thread: spectators fly through walls - never cull for them. */
	public static void setCameraBlocked(boolean blocked) {
		cameraBlocked = blocked;
	}

	private static void run() {
		ArrayList<CullTarget> targets = new ArrayList<>();
		ClientLevel lastLevel = null;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		while (true) {
			try {
				Minecraft mc = Minecraft.getInstance();
				ClientLevel level = mc == null ? null : mc.level;

				if (level != lastLevel) { // world changed - forget everything
					for (CullTarget t : targets) t.registered = false;
					targets.clear();
					lastLevel = level;
				}
				CullTarget added;
				while ((added = NEW_TARGETS.poll()) != null) targets.add(added);

				if (level == null || !Silicium.occlusionActive()) {
					long far = Long.MAX_VALUE / 2;
					for (CullTarget t : targets) t.visibleUntil = far;
					Thread.sleep(50);
					continue;
				}

				long passStart = System.currentTimeMillis();
				double cx = camX, cy = camY, cz = camZ;
				boolean blocked = cameraBlocked;
				ChunkCache chunks = new ChunkCache(level);

				Iterator<CullTarget> it = targets.iterator();
				while (it.hasNext()) {
					CullTarget t = it.next();
					if (passStart - t.lastRequested > STALE_MS) {
						it.remove();
						t.registered = false;
						continue;
					}
					AABB box = t.box;
					if (box == null || blocked || isVisible(chunks, pos, cx, cy, cz, box)) {
						t.visibleUntil = System.currentTimeMillis() + CullTarget.KEEP_VISIBLE_MS;
					}
				}

				long took = System.currentTimeMillis() - passStart;
				Thread.sleep(Math.max(2, 10 - took)); // ~100 passes/s at most, always yields some CPU
			} catch (InterruptedException e) {
				return;
			} catch (Throwable e) {
				// chunk data can change under us while reading off-thread - just retry next pass
				try {
					Thread.sleep(20);
				} catch (InterruptedException ie) {
					return;
				}
			}
		}
	}

	private static boolean isVisible(ChunkCache chunks, BlockPos.MutableBlockPos pos, double cx, double cy, double cz, AABB b) {
		double nx = Math.max(b.minX, Math.min(cx, b.maxX));
		double ny = Math.max(b.minY, Math.min(cy, b.maxY));
		double nz = Math.max(b.minZ, Math.min(cz, b.maxZ));
		double dSq = (nx - cx) * (nx - cx) + (ny - cy) * (ny - cy) + (nz - cz) * (nz - cz);
		if (dSq < ALWAYS_VISIBLE_DIST_SQ || dSq > MAX_TRACE_DIST_SQ) return true;

		// slightly inset so points don't sit exactly on a neighbouring block's face
		double x0 = b.minX + 0.05, x1 = b.maxX - 0.05;
		double y0 = b.minY + 0.05, y1 = b.maxY - 0.05;
		double z0 = b.minZ + 0.05, z1 = b.maxZ - 0.05;

		// center first (most likely to be visible), then the 8 corners
		if (rayClear(chunks, pos, cx, cy, cz, (b.minX + b.maxX) * 0.5, (b.minY + b.maxY) * 0.5, (b.minZ + b.maxZ) * 0.5)) return true;
		return rayClear(chunks, pos, cx, cy, cz, x0, y1, z0)
				|| rayClear(chunks, pos, cx, cy, cz, x1, y1, z0)
				|| rayClear(chunks, pos, cx, cy, cz, x0, y1, z1)
				|| rayClear(chunks, pos, cx, cy, cz, x1, y1, z1)
				|| rayClear(chunks, pos, cx, cy, cz, x0, y0, z0)
				|| rayClear(chunks, pos, cx, cy, cz, x1, y0, z0)
				|| rayClear(chunks, pos, cx, cy, cz, x0, y0, z1)
				|| rayClear(chunks, pos, cx, cy, cz, x1, y0, z1);
	}

	/** Amanatides-Woo voxel traversal. Start and end voxels are not tested. */
	private static boolean rayClear(ChunkCache chunks, BlockPos.MutableBlockPos pos,
									double sx, double sy, double sz, double ex, double ey, double ez) {
		int x = floor(sx), y = floor(sy), z = floor(sz);
		int endX = floor(ex), endY = floor(ey), endZ = floor(ez);
		double dx = ex - sx, dy = ey - sy, dz = ez - sz;

		int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0;
		int stepY = dy > 0 ? 1 : dy < 0 ? -1 : 0;
		int stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;
		double tDeltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
		double tDeltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
		double tDeltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
		double tMaxX = stepX == 0 ? Double.MAX_VALUE : (stepX > 0 ? (x + 1 - sx) : (sx - x)) * tDeltaX;
		double tMaxY = stepY == 0 ? Double.MAX_VALUE : (stepY > 0 ? (y + 1 - sy) : (sy - y)) * tDeltaY;
		double tMaxZ = stepZ == 0 ? Double.MAX_VALUE : (stepZ > 0 ? (z + 1 - sz) : (sz - z)) * tDeltaZ;

		for (int i = 0; i < MAX_STEPS; i++) {
			if (tMaxX < tMaxY && tMaxX < tMaxZ) {
				x += stepX;
				tMaxX += tDeltaX;
			} else if (tMaxY < tMaxZ) {
				y += stepY;
				tMaxY += tDeltaY;
			} else {
				z += stepZ;
				tMaxZ += tDeltaZ;
			}
			if (x == endX && y == endY && z == endZ) return true;
			if (chunks.isOpaque(pos.set(x, y, z))) return false;
		}
		return true; // too long to trace - assume visible
	}

	private static int floor(double v) {
		int i = (int) v;
		return v < i ? i - 1 : i;
	}

	/** Remembers the last chunk so consecutive lookups along a ray are cheap. */
	private static final class ChunkCache {
		private final ClientLevel level;
		private int lastCx = Integer.MIN_VALUE, lastCz = Integer.MIN_VALUE;
		private LevelChunk last;

		ChunkCache(ClientLevel level) {
			this.level = level;
		}

		boolean isOpaque(BlockPos pos) {
			int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
			if (cx != lastCx || cz != lastCz) {
				lastCx = cx;
				lastCz = cz;
				last = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
			}
			//#if MC >= 1.21.2
			return last != null && last.getBlockState(pos).isSolidRender();
			//#else
			return last != null && last.getBlockState(pos).isSolidRender(last, pos);
			//#endif
		}
	}
}
