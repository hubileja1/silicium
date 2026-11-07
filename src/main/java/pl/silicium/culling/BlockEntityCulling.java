package pl.silicium.culling;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.phys.AABB;
import pl.silicium.Silicium;
import pl.silicium.SiliciumConfig;
import pl.silicium.chest.StaticChests;

/**
 * Version-independent decision whether a block entity (chest, sign, banner, skull, shulker, spawner,
 * moving piston block...) should be skipped this frame. The per-version mixins only hook it into
 * the game's block entity renderer.
 */
public final class BlockEntityCulling {
	private BlockEntityCulling() {
	}

	/**
	 * @param offScreen true for renderers that draw outside their block (beacon beams) - never skipped
	 * @return true if the block entity should not be rendered
	 */
	public static boolean shouldSkip(BlockEntity blockEntity, double camX, double camY, double camZ,
									 float partialTicks, boolean offScreen) {
		SiliciumConfig cfg = SiliciumConfig.get();
		if (!cfg.enabled || offScreen) return false;

		if (cfg.hideSpawnerMobs && blockEntity instanceof SpawnerBlockEntity) return true;

		BlockPos p = blockEntity.getBlockPos();

		// Closed chests are part of the chunk mesh - the renderer only draws them while the lid moves
		if (blockEntity instanceof LidBlockEntity lid && StaticChests.active()
				&& StaticChests.isStaticChest(blockEntity.getBlockState())
				&& !StaticChests.shouldUseRenderer(p, lid.getOpenNess(partialTicks))) {
			return true;
		}

		double dx = p.getX() + 0.5 - camX;
		double dy = p.getY() + 0.5 - camY;
		double dz = p.getZ() + 0.5 - camZ;
		// blocks moved by pistons have their own (usually longer) distance - flying machines, piston farms
		double limitSq = blockEntity instanceof PistonMovingBlockEntity ? cfg.movingPistonDistSq : cfg.blockEntityDistSq;
		if (limitSq != Double.MAX_VALUE && dx * dx + dy * dy + dz * dz > limitSq) return true;

		if (Silicium.occlusionActive()) {
			OcclusionCuller.updateCamera(camX, camY, camZ);
			// box grown upwards for tall renderers (banners, signs on posts)
			AABB box = new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 2, p.getZ() + 1);
			return !((CullHolder) blockEntity).silicium$cullTarget().isVisible(box, System.currentTimeMillis());
		}
		return false;
	}
}
