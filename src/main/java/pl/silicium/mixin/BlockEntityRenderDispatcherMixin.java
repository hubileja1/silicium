package pl.silicium.mixin;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import pl.silicium.culling.BlockEntityCulling;
//#if MC >= 1.21.9
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//#else
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#endif

/**
 * Skips block entities (chests, signs, banners, skulls, shulkers, spawners...) that are too far,
 * hidden behind walls, or drawn as part of the chunk mesh (static chests).
 * Sodium also goes through this method, so it works with Sodium installed.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
	//#if MC >= 1.21.9
	@Shadow
	private Vec3 cameraPos;

	@Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
	private <E extends BlockEntity, S extends BlockEntityRenderState> void silicium$cull(
			E blockEntity, float partialTicks, ModelFeatureRenderer.CrumblingOverlay crumbling,
			//#if MC >= 26.2
			boolean offScreen,
			//#endif
			CallbackInfoReturnable<S> cir) {
		//#if MC < 26.2
		var renderer = ((BlockEntityRenderDispatcher) (Object) this).getRenderer(blockEntity);
		boolean offScreen = renderer != null && renderer.shouldRenderOffScreen();
		//#endif
		if (cameraPos != null && BlockEntityCulling.shouldSkip(blockEntity, cameraPos.x, cameraPos.y, cameraPos.z, partialTicks, offScreen)) {
			cir.setReturnValue(null);
		}
	}
	//#else
	@Shadow
	public Camera camera;

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private <E extends BlockEntity> void silicium$cull(E blockEntity, float partialTicks, PoseStack poseStack,
														MultiBufferSource buffers, CallbackInfo ci) {
		if (camera == null) return;
		var renderer = ((BlockEntityRenderDispatcher) (Object) this).getRenderer(blockEntity);
		//#if MC >= 1.21.6
		boolean offScreen = renderer != null && renderer.shouldRenderOffScreen();
		//#else
		boolean offScreen = renderer != null && renderer.shouldRenderOffScreen(blockEntity);
		//#endif
		Vec3 cam = camera.getPosition();
		if (BlockEntityCulling.shouldSkip(blockEntity, cam.x, cam.y, cam.z, partialTicks, offScreen)) {
			ci.cancel();
		}
	}
	//#endif
}
