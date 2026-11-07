package pl.silicium.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.silicium.water.WaterDistance;

/**
 * Sodium's chunk mesher: remember which section is being built on this worker thread.
 * {@code @Pseudo} - Sodium is optional; without it this mixin is simply skipped.
 */
@Pseudo
@Mixin(targets = {
		"net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask",
		"me.jellysquid.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask"
}, remap = false)
public abstract class SodiumMeshingWaterMixin {
	@Inject(method = "execute", at = @At("HEAD"), require = 0, remap = false)
	private void silicium$beginSection(CallbackInfoReturnable<?> cir) {
		WaterDistance.beginSodiumTask(this);
	}

	@Inject(method = "execute", at = @At("RETURN"), require = 0, remap = false)
	private void silicium$endSection(CallbackInfoReturnable<?> cir) {
		WaterDistance.endSection();
	}
}
