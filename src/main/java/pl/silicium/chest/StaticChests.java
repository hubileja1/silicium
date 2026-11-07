package pl.silicium.chest;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
//#if MC >= 1.21.4
import net.minecraft.client.renderer.blockentity.ChestRenderer;
//#endif
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import pl.silicium.SiliciumConfig;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static chests: closed chests are part of the chunk mesh (drawn by the GPU together with the terrain,
 * practically free) instead of being drawn one by one by the block entity renderer every frame.
 * <p>
 * Only while a chest's lid is moving (someone opens it) the static model is removed from the chunk mesh
 * and the vanilla renderer draws the animated chest. After the lid closes, the chest goes back to the mesh.
 */
public final class StaticChests {
	/** Chests currently drawn by the block entity renderer (lid open or moving). Read from chunk-meshing threads. */
	private static final Set<Long> ANIMATING = ConcurrentHashMap.newKeySet();
	/** After closing, keep drawing with the renderer until the rebuilt chunk mesh is ready (avoids a blink). */
	private static final Long2LongOpenHashMap GRACE_UNTIL = new Long2LongOpenHashMap();
	private static final long GRACE_MS = 500;

	//#if MC >= 1.21.4
	private static final boolean XMAS = ChestRenderer.xmasTextures(); // christmas textures - leave vanilla rendering
	//#else
	private static final boolean XMAS = isChristmas();

	private static boolean isChristmas() {
		java.util.Calendar c = java.util.Calendar.getInstance();
		return c.get(java.util.Calendar.MONTH) + 1 == 12 && c.get(java.util.Calendar.DATE) >= 24 && c.get(java.util.Calendar.DATE) <= 26;
	}
	//#endif
	private static ClientLevel lastLevel;

	private StaticChests() {
	}

	public static boolean active() {
		//#if MC < 1.20.1
		if (true) return false; // no model loading API in Fabric API for 1.20
		//#endif
		SiliciumConfig cfg = SiliciumConfig.get();
		return cfg.enabled && cfg.staticChests && !XMAS;
	}

	/** Vanilla chests only - modded chests have no static model in our resources. */
	public static boolean isStaticChest(BlockState state) {
		return state.getBlock() instanceof AbstractChestBlock<?>
				&& BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("minecraft");
	}

	/** Chunk-meshing threads: should the static model be left out of the mesh at this position? */
	public static boolean isAnimating(BlockPos pos) {
		return ANIMATING.contains(pos.asLong());
	}

	/**
	 * Render thread, every frame a chest would be drawn: decides whether the block entity renderer draws it.
	 * Moves the chest between the chunk mesh and the renderer when its lid starts / stops moving.
	 */
	public static boolean shouldUseRenderer(BlockPos pos, float openness) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != lastLevel) {
			ANIMATING.clear();
			GRACE_UNTIL.clear();
			lastLevel = mc.level;
		}
		long key = pos.asLong();

		if (openness > 0.0F) {
			if (ANIMATING.add(key)) {
				GRACE_UNTIL.remove(key);
				rebuild(pos);
			}
			return true;
		}
		if (ANIMATING.remove(key)) { // lid just closed - back into the mesh
			rebuild(pos);
			GRACE_UNTIL.put(key, System.currentTimeMillis() + GRACE_MS);
			return true;
		}
		if (GRACE_UNTIL.containsKey(key)) {
			if (System.currentTimeMillis() < GRACE_UNTIL.get(key)) return true;
			GRACE_UNTIL.remove(key);
		}
		return false;
	}

	private static void rebuild(BlockPos pos) {
		pl.silicium.compat.Mc.rebuildSection(SectionPos.blockToSectionCoord(pos.getX()),
				SectionPos.blockToSectionCoord(pos.getY()), SectionPos.blockToSectionCoord(pos.getZ()));
	}
}
