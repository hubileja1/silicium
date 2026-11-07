//#if MC < 26.1
package pl.silicium.mixin;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
//#if MC >= 1.21.6
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
//#else
import net.minecraft.client.renderer.RenderType;
//#endif
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.silicium.SiliciumConfig;

/**
 * Opaque water for versions before 26.1: the chunk layer of a fluid comes from
 * ItemBlockRenderTypes.getRenderLayer(FluidState) (used by vanilla and Sodium).
 * See FluidStateModelSetMixin for why this is so much faster.
 */
@Mixin(ItemBlockRenderTypes.class)
public abstract class ItemBlockRenderTypesMixin {
	//#if MC >= 1.21.6
	@Inject(method = "getRenderLayer(Lnet/minecraft/world/level/material/FluidState;)Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;",
			at = @At("RETURN"), cancellable = true)
	private static void silicium$opaqueWater(FluidState state, CallbackInfoReturnable<ChunkSectionLayer> cir) {
		SiliciumConfig cfg = SiliciumConfig.get();
		if (pl.silicium.water.WaterDistance.opaque(state) && cir.getReturnValue() == ChunkSectionLayer.TRANSLUCENT) {
			cir.setReturnValue(ChunkSectionLayer.SOLID);
		}
	}
	//#else
	@Inject(method = "getRenderLayer(Lnet/minecraft/world/level/material/FluidState;)Lnet/minecraft/client/renderer/RenderType;",
			at = @At("RETURN"), cancellable = true)
	private static void silicium$opaqueWater(FluidState state, CallbackInfoReturnable<RenderType> cir) {
		SiliciumConfig cfg = SiliciumConfig.get();
		if (pl.silicium.water.WaterDistance.opaque(state) && cir.getReturnValue() == RenderType.translucent()) {
			cir.setReturnValue(RenderType.solid());
		}
	}
	//#endif
}
//#endif
