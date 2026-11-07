//#if MC < 1.21.2
package pl.silicium.mixin;

import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.silicium.chest.StaticChests;

/**
 * Before 1.21.2 chests use the ENTITYBLOCK_ANIMATED render shape, which the chunk mesher skips.
 * With static chests the shape is MODEL, so our static chest model becomes part of the chunk mesh.
 */
@Mixin({ChestBlock.class, EnderChestBlock.class})
public abstract class ChestRenderShapeMixin {
	@Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
	private void silicium$staticShape(BlockState state, CallbackInfoReturnable<RenderShape> cir) {
		if (StaticChests.active() && StaticChests.isStaticChest(state)) {
			cir.setReturnValue(RenderShape.MODEL);
		}
	}
}
//#endif
