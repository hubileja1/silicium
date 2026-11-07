package pl.silicium.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
//#if MC >= 1.21.9
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.phys.Vec3;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pl.silicium.EntityGrouping;
import pl.silicium.SiliciumConfig;

/**
 * Explosions arrive from the server as packets. When a TNT cannon fires dozens at once, the client
 * creates dozens of smoke emitters (each spawning many particles), block debris and sounds.
 * Only the visual/sound effects are limited here - player knockback and block destruction
 * work normally (the server handles those).
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	//#if MC >= 1.21.2
	@WrapWithCondition(method = "handleExplosion", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
	private boolean silicium$limitExplosionParticle(ClientLevel level, ParticleOptions options,
													   double x, double y, double z, double xd, double yd, double zd) {
		SiliciumConfig cfg = SiliciumConfig.get();
		if (!cfg.enabled || cfg.maxExplosionsPerTick <= 0) return true;
		return EntityGrouping.explosionsThisTick++ < cfg.maxExplosionsPerTick;
	}

	//#if MC >= 1.21.9
	@WrapWithCondition(method = "handleExplosion", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientLevel;trackExplosionEffects(Lnet/minecraft/world/phys/Vec3;FILnet/minecraft/util/random/WeightedList;)V"))
	private boolean silicium$explosionDebris(ClientLevel level, Vec3 center, float radius, int blockCount,
												WeightedList<ExplosionParticleInfo> particles) {
		SiliciumConfig cfg = SiliciumConfig.get();
		return !cfg.enabled || cfg.explosionDebris;
	}
	//#endif

	@WrapWithCondition(method = "handleExplosion", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientLevel;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"))
	private boolean silicium$limitExplosionSound(ClientLevel level, double x, double y, double z, SoundEvent sound,
													SoundSource source, float volume, float pitch, boolean distanceDelay) {
		SiliciumConfig cfg = SiliciumConfig.get();
		if (!cfg.enabled || cfg.maxExplosionSoundsPerTick <= 0) return true;
		return EntityGrouping.explosionSoundsThisTick++ < cfg.maxExplosionSoundsPerTick;
	}
	//#else
	/** Before 1.21.2 the client re-creates the explosion and calls finalizeExplosion (smoke, debris, sound). */
	@WrapWithCondition(method = "handleExplosion", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Explosion;finalizeExplosion(Z)V"))
	private boolean silicium$limitExplosions(net.minecraft.world.level.Explosion explosion, boolean spawnParticles) {
		SiliciumConfig cfg = SiliciumConfig.get();
		if (!cfg.enabled || cfg.maxExplosionsPerTick <= 0) return true;
		return EntityGrouping.explosionsThisTick++ < cfg.maxExplosionsPerTick;
	}
	//#endif
}
