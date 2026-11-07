package pl.silicium.mixin;

//#if MC >= 1.21.4
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
//#else
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.silicium.SiliciumConfig;

/**
 * Vanilla draws up to 5 copies of the model for one dropped item stack.
 * With this option only 1 copy is drawn - up to 5x less geometry per dropped item.
 */
//#if MC >= 1.21.4
@Mixin(ItemClusterRenderState.class)
//#else
@Mixin(ItemEntityRenderer.class)
//#endif
public abstract class ItemClusterRenderStateMixin {
	//#if MC >= 1.20.5
	@Inject(method = "getRenderedAmount", at = @At("HEAD"), cancellable = true)
	private static void silicium$singleModel(int count, CallbackInfoReturnable<Integer> cir) {
		SiliciumConfig cfg = SiliciumConfig.get();
		if (cfg.enabled && cfg.singleItemModel) {
			cir.setReturnValue(1);
		}
	}
	//#else
	@Inject(method = "getRenderAmount", at = @At("HEAD"), cancellable = true)
	private void silicium$singleModel(net.minecraft.world.item.ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		SiliciumConfig cfg = SiliciumConfig.get();
		if (cfg.enabled && cfg.singleItemModel) {
			cir.setReturnValue(1);
		}
	}
	//#endif
}
