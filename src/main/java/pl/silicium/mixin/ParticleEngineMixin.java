package pl.silicium.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
//#if MC >= 1.21.9
import net.minecraft.client.particle.ParticleGroup;
//#endif
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.silicium.SiliciumConfig;
import pl.silicium.compat.Mc;

import java.util.Map;
import java.util.Queue;

/**
 * Particles: never create ones that are far away, and enforce a global cap on live particles
 * (fewer particles = less to tick AND less to render).
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
	@Shadow
	@Final
	//#if MC >= 1.21.9
	private Map<ParticleRenderType, ParticleGroup<?>> particles;
	//#else
	private Map<ParticleRenderType, Queue<Particle>> particles;
	//#endif

	@Shadow
	@Final
	private Queue<Particle> particlesToAdd;

	@Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
	private void silicium$distanceCull(ParticleOptions options, double x, double y, double z,
										  double xd, double yd, double zd, CallbackInfoReturnable<Particle> cir) {
		SiliciumConfig cfg = SiliciumConfig.get();
		if (!cfg.enabled || cfg.particleDistSq == Double.MAX_VALUE) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.gameRenderer == null) return;
		Vec3 cam = Mc.cameraPos();
		if (cam.distanceToSqr(x, y, z) > cfg.particleDistSq) {
			cir.setReturnValue(null);
		}
	}

	@Inject(method = "add", at = @At("HEAD"), cancellable = true)
	private void silicium$particleCap(Particle particle, CallbackInfo ci) {
		SiliciumConfig cfg = SiliciumConfig.get();
		if (!cfg.enabled || cfg.maxParticles <= 0) return;

		int total = particlesToAdd.size();
		//#if MC >= 1.21.9
		for (ParticleGroup<?> group : particles.values()) {
		//#else
		for (Queue<Particle> group : particles.values()) {
		//#endif
			total += group.size();
		}
		if (total >= cfg.maxParticles) {
			ci.cancel();
		}
	}
}
