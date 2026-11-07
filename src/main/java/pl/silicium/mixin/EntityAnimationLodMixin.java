//#if MC >= 1.21.9
package pl.silicium.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.silicium.compat.Mc;
import pl.silicium.lod.AnimationLod;
import pl.silicium.lod.LodHolder;

/**
 * Animation LOD: for distant mobs, reuse the last extracted render state (animation, rotation,
 * equipment, name tag...) for a while instead of extracting a new one every frame.
 * The game creates a brand new state object per entity per frame and never keeps it,
 * so handing the previous one back again is safe.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityAnimationLodMixin {
	@Inject(method = "extractEntity", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void silicium$reuseState(E entity, float partialTicks, CallbackInfoReturnable<EntityRenderState> cir) {
		LodHolder holder = (LodHolder) entity;
		EntityRenderState cached = holder.silicium$lodState();
		if (cached == null) return;

		if (!AnimationLod.applies(entity)) {
			holder.silicium$setLod(null, 0);
			return;
		}
		Vec3 cam = Mc.cameraPos();
		long reuse = AnimationLod.reuseNanos(entity.distanceToSqr(cam));
		if (reuse > 0 && System.nanoTime() - holder.silicium$lodTime() < reuse) {
			AnimationLod.updatePosition(cached, entity, partialTicks, cam.x, cam.y, cam.z);
			cir.setReturnValue(cached);
		}
	}

	@Inject(method = "extractEntity", at = @At("RETURN"))
	private <E extends Entity> void silicium$storeState(E entity, float partialTicks, CallbackInfoReturnable<EntityRenderState> cir) {
		LodHolder holder = (LodHolder) entity;
		if (AnimationLod.applies(entity)) {
			Vec3 cam = Mc.cameraPos();
			if (AnimationLod.reuseNanos(entity.distanceToSqr(cam)) > 0) {
				holder.silicium$setLod(cir.getReturnValue(), System.nanoTime());
				return;
			}
		}
		if (holder.silicium$lodState() != null) holder.silicium$setLod(null, 0); // close mobs: no cache
	}
}
//#endif
