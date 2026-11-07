package pl.silicium.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
//#if MC >= 1.21.11
import net.minecraft.world.entity.decoration.painting.Painting;
//#else
import net.minecraft.world.entity.decoration.Painting;
//#endif
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.silicium.EntityGrouping;
import pl.silicium.SiliciumConfig;
import pl.silicium.Silicium;
import pl.silicium.culling.CullHolder;
import pl.silicium.culling.CullTarget;
import pl.silicium.culling.OcclusionCuller;

/**
 * Skips rendering entities that are: in a crowd surplus, too far for their type, or hidden behind walls.
 * Injects at RETURN, so it only ever turns "true" into "false" - vanilla's / Sodium's own checks
 * (frustum, chunk visibility) run first and cheap rejections never reach the occlusion test.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	@Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
	private <E extends Entity> void silicium$cull(E entity, Frustum frustum, double camX, double camY, double camZ,
												  //#if MC >= 26.3
												  float partialTicks,
												  //#endif
												  CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) return;
		SiliciumConfig cfg = SiliciumConfig.get();
		if (!cfg.enabled) return;

		Minecraft mc = Minecraft.getInstance();
		if (entity == mc.getCameraEntity()) return;

		// 1. crowd surplus (too many items / mobs / TNT in one block)
		if (EntityGrouping.isHidden(entity)) {
			cir.setReturnValue(false);
			return;
		}

		boolean player = entity instanceof Player;
		boolean important = (cfg.alwaysRenderPlayers && player)
				|| (cfg.alwaysRenderNamedAndGlowing && (entity.hasCustomName() || entity.isCurrentlyGlowing()));
		if (important) return;

		// 2. per-type distance
		double limitSq;
		if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) limitSq = cfg.itemDistSq;
		else if (entity instanceof ItemFrame || entity instanceof Painting) limitSq = cfg.itemFrameDistSq;
		else if (entity instanceof ArmorStand) limitSq = cfg.armorStandDistSq;
		else limitSq = cfg.entityDistSq;

		boolean big = entity.getBbWidth() > 3.0F || entity.getBbHeight() > 4.0F; // bosses, ghasts
		if (!big && !entity.isVehicle() && !entity.isPassenger() && limitSq != Double.MAX_VALUE) {
			// hysteresis: a visible mob disappears only ~7% past the limit and reappears inside it,
			// so mobs wandering around the border don't blink in and out every few frames
			CullTarget target = ((CullHolder) entity).silicium$cullTarget();
			double distSq = entity.distanceToSqr(camX, camY, camZ);
			target.farHidden = target.farHidden ? distSq > limitSq : distSq > limitSq * 1.15;
			if (target.farHidden) {
				cir.setReturnValue(false);
				return;
			}
		}

		// 3. occlusion (hidden behind solid blocks)
		if (Silicium.occlusionActive()) {
			OcclusionCuller.updateCamera(camX, camY, camZ);
			OcclusionCuller.setCameraBlocked(mc.player != null && mc.player.isSpectator());
			if (!((CullHolder) entity).silicium$cullTarget().isVisible(entity.getBoundingBox(), System.currentTimeMillis())) {
				cir.setReturnValue(false);
			}
		}
	}
}
