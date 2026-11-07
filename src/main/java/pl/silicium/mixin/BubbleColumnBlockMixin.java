package pl.silicium.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pl.silicium.SiliciumConfig;

/**
 * Bubble columns (soul sand up, magma down) spawn particles from EVERY water block of the column -
 * water elevators and farms produce thousands of them. Only the configured percentage is kept.
 * The column itself (pushing/pulling) works unchanged.
 */
@Mixin(BubbleColumnBlock.class)
public abstract class BubbleColumnBlockMixin {
	@WrapWithCondition(method = "animateTick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
	private boolean silicium$reduceBubbles(Level level, ParticleOptions options, double x, double y, double z,
											  double xd, double yd, double zd,
											  BlockState state, Level lvl, BlockPos pos, RandomSource random) {
		SiliciumConfig cfg = SiliciumConfig.get();
		if (!cfg.enabled || cfg.bubbleColumnPercent >= 100) return true;
		if (cfg.bubbleColumnPercent <= 0) return false;
		return random.nextInt(100) < cfg.bubbleColumnPercent;
	}
}
