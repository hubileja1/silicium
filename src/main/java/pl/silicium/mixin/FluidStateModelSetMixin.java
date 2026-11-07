//#if MC >= 26.1
package pl.silicium.mixin;

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.silicium.SiliciumConfig;

/**
 * Opaque water: water is moved from the translucent layer to the solid layer.
 * <p>
 * Translucent geometry is expensive - it is drawn after everything else, section by section from back
 * to front, with blending and without early depth rejection. Huge water walls and oceans therefore cost
 * a lot (on big servers easily 1+ ms per frame). In the solid layer water is batched with the terrain.
 * Trade-off: you can't see through water anymore. Works with Sodium (it reads the layer from this model).
 */
@Mixin(FluidStateModelSet.class)
public abstract class FluidStateModelSetMixin {
	/** Original translucent model -> its opaque copy. Read from chunk-meshing threads, so kept immutable. */
	@Unique
	private volatile FluidModel[] silicium$opaqueCache;

	@Inject(method = "get", at = @At("RETURN"), cancellable = true)
	private void silicium$opaqueWater(FluidState state, CallbackInfoReturnable<FluidModel> cir) {
		if (!pl.silicium.water.WaterDistance.opaque(state)) return;

		FluidModel model = cir.getReturnValue();
		if (model == null || model.layer() != ChunkSectionLayer.TRANSLUCENT) return;

		FluidModel[] cache = silicium$opaqueCache;
		if (cache == null || cache[0] != model) {
			// the overlay texture (water next to glass / leaves) is almost fully transparent - in the solid layer
			// it would become a flat dark color, so use the normal flowing texture there instead
			FluidModel opaque = new FluidModel(ChunkSectionLayer.SOLID, model.stillMaterial(), model.flowingMaterial(),
					model.flowingMaterial(), model.tintSource());
			cache = new FluidModel[]{model, opaque};
			silicium$opaqueCache = cache;
		}
		cir.setReturnValue(cache[1]);
	}
}
//#endif
