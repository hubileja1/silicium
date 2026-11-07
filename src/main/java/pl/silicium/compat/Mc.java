package pl.silicium.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Small helpers for game APIs that differ between Minecraft versions. */
public final class Mc {
	private Mc() {
	}

	/** Current camera position (render thread). */
	public static Vec3 cameraPos() {
		Minecraft mc = Minecraft.getInstance();
		//#if MC >= 26.2
		return mc.gameRenderer.mainCamera().position();
		//#elif MC >= 1.21.11
		return mc.gameRenderer.getMainCamera().position();
		//#else
		return mc.gameRenderer.getMainCamera().getPosition();
		//#endif
	}

	/** Schedules a chunk section rebuild (vanilla or Sodium). Render/main thread only. */
	public static void rebuildSection(int sx, int sy, int sz) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		//#if MC >= 1.21.4
		mc.level.setSectionRangeDirty(sx, sy, sz, sx, sy, sz);
		//#else
		mc.levelRenderer.setSectionDirty(sx, sy, sz);
		//#endif
	}
}
