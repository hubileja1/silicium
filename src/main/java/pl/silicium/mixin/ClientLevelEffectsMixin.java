package pl.silicium.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import pl.silicium.SiliciumConfig;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Random block effects around the player (client-side only).
 * <p>
 * Every tick the game picks 667 random positions within 16 and 32 blocks and runs their "animate tick":
 * leaf particles, lava pops and sounds, water sounds, drips from ceilings, torch/fire smoke,
 * biome ambient particles (ash, spores)... In forests, the Nether and big open areas this creates
 * a constant stream of particles. Here each category can be reduced separately, and the number of
 * sampled positions itself can be lowered (less CPU work every tick).
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelEffectsMixin {
	@ModifyConstant(method = "animateTick", constant = @Constant(intValue = 667))
	private int silicium$blockEffectsDensity(int samples) {
		SiliciumConfig cfg = SiliciumConfig.get();
		if (!cfg.enabled || cfg.blockEffectsDensity >= 100) return samples;
		return samples * Math.max(0, cfg.blockEffectsDensity) / 100;
	}

	@WrapWithCondition(method = "doAnimateTick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/Block;animateTick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
	private boolean silicium$leafParticles(Block block, BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!(block instanceof LeavesBlock)) return true;
		SiliciumConfig cfg = SiliciumConfig.get();
		return !cfg.enabled || chance(cfg.leafParticlePercent);
	}

	@WrapWithCondition(method = "doAnimateTick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/material/FluidState;animateTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
	private boolean silicium$fluidEffects(FluidState fluid, Level level, BlockPos pos, RandomSource random) {
		SiliciumConfig cfg = SiliciumConfig.get();
		if (!cfg.enabled) return true;
		return chance(fluid.is(FluidTags.LAVA) ? cfg.lavaEffectsPercent : cfg.waterEffectsPercent);
	}

	@WrapWithCondition(method = "doAnimateTick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientLevel;trySpawnDripParticles(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/particles/ParticleOptions;Z)V"))
	private boolean silicium$dripParticles(ClientLevel level, BlockPos pos, BlockState state, ParticleOptions particle, boolean onlyWhenRaining) {
		SiliciumConfig cfg = SiliciumConfig.get();
		return !cfg.enabled || chance(cfg.dripParticlePercent);
	}

	//#if MC >= 1.21.11
	/** Second addParticle in doAnimateTick = biome ambient particles (the first one is the barrier/light marker). */
	@WrapWithCondition(method = "doAnimateTick", at = @At(value = "INVOKE", ordinal = 1,
			target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
	private boolean silicium$ambientParticles(ClientLevel level, ParticleOptions particle, double x, double y, double z,
											  double xd, double yd, double zd) {
		SiliciumConfig cfg = SiliciumConfig.get();
		return !cfg.enabled || chance(cfg.ambientParticlePercent);
	}
	//#else
	/** Before 1.21.11 biome ambient particles are spawned via Optional.ifPresent(...) in doAnimateTick. */
	@WrapWithCondition(method = "doAnimateTick", at = @At(value = "INVOKE",
			target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V"))
	private boolean silicium$ambientParticles(java.util.Optional<?> particle, java.util.function.Consumer<?> spawner) {
		SiliciumConfig cfg = SiliciumConfig.get();
		return !cfg.enabled || chance(cfg.ambientParticlePercent);
	}
	//#endif

	private static boolean chance(int percent) {
		if (percent >= 100) return true;
		if (percent <= 0) return false;
		return ThreadLocalRandom.current().nextInt(100) < percent;
	}
}
