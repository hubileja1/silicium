//#if MC >= 1.21.1
package pl.silicium.mixin;

import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.silicium.water.WaterDistance;

/** Vanilla chunk mesher (without Sodium): remember which section is being built on this thread. */
@Mixin(SectionCompiler.class)
public abstract class SectionCompilerWaterMixin {
	@ModifyVariable(method = "compile", at = @At("HEAD"), argsOnly = true)
	private SectionPos silicium$beginSection(SectionPos pos) {
		WaterDistance.beginSection(pos.x(), pos.y(), pos.z());
		return pos;
	}

	@Inject(method = "compile", at = @At("RETURN"))
	private void silicium$endSection(CallbackInfoReturnable<?> cir) {
		WaterDistance.endSection();
	}
}
//#endif
