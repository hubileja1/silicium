//#if MC >= 1.21.9
package pl.silicium.lod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import pl.silicium.SiliciumConfig;

/**
 * Animation LOD for mobs (client-side only - works on any server).
 * <p>
 * Every frame the game "extracts" a fresh render state for each mob: held items and armor models,
 * name tag, shadow, light, equipment... For distant mobs that is refreshed less often and the state is
 * reused in between. Everything that MOVES (position, body/head rotation, walk cycle, idle animation
 * time) is still updated every frame - it is cheap - so distant mobs don't stutter or glitch.
 */
public final class AnimationLod {
	private AnimationLod() {
	}

	/** Which entities take part: mobs only - never players, the camera entity, glowing or riding ones. */
	public static boolean applies(Entity entity) {
		SiliciumConfig cfg = SiliciumConfig.get();
		if (!cfg.enabled || cfg.animationLodDistance <= 0) return false;
		if (!(entity instanceof LivingEntity) || entity instanceof Player) return false;
		if (entity.isCurrentlyGlowing() || entity.isVehicle() || entity.isPassenger()) return false;
		return entity != Minecraft.getInstance().getCameraEntity();
	}

	/** How long a render state may be reused at this distance, in nanoseconds (0 = refresh every frame). */
	public static long reuseNanos(double distSq) {
		SiliciumConfig cfg = SiliciumConfig.get();
		double start = cfg.animationLodDistance;
		if (distSq < start * start) return 0;
		// rate falls off with distance: full "distant FPS" at the start distance, half at 2x, ...
		double fps = Math.max(1.0, cfg.distantAnimationFps * start / Math.sqrt(distSq));
		return (long) (1_000_000_000L / fps);
	}

	/** Updates everything that moves on a reused state (position, rotations, walk cycle, animation time). */
	public static void updatePosition(EntityRenderState state, Entity entity, float partialTicks, double camX, double camY, double camZ) {
		state.x = Mth.lerp(partialTicks, entity.xOld, entity.getX());
		state.y = Mth.lerp(partialTicks, entity.yOld, entity.getY());
		state.z = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
		double dx = state.x - camX, dy = state.y - camY, dz = state.z - camZ;
		state.distanceToCameraSq = dx * dx + dy * dy + dz * dz;
		state.ageInTicks = entity.tickCount + partialTicks;

		if (entity instanceof LivingEntity living && state instanceof LivingEntityRenderState ls && !ls.isUpsideDown) {
			// same math as LivingEntityRenderer.extractRenderState (mobs riding something never get here)
			float bodyRot = Mth.rotLerp(partialTicks, living.yBodyRotO, living.yBodyRot);
			float headRot = Mth.rotLerp(partialTicks, living.yHeadRotO, living.yHeadRot);
			ls.bodyRot = bodyRot;
			ls.yRot = Mth.wrapDegrees(headRot - bodyRot);
			ls.xRot = living.getXRot(partialTicks);
			if (living.isAlive()) {
				ls.walkAnimationPos = living.walkAnimation.position(partialTicks);
				ls.walkAnimationSpeed = living.walkAnimation.speed(partialTicks);
			}
		}
	}
}
//#endif
