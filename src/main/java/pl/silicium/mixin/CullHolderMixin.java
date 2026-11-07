package pl.silicium.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import pl.silicium.culling.CullHolder;
import pl.silicium.culling.CullTarget;

/** Adds a lazily created CullTarget field to every Entity and BlockEntity. */
@Mixin({Entity.class, BlockEntity.class})
public abstract class CullHolderMixin implements CullHolder {
	@Unique
	private CullTarget silicium$cullTarget;

	@Override
	public CullTarget silicium$cullTarget() {
		CullTarget t = silicium$cullTarget;
		if (t == null) {
			t = new CullTarget();
			silicium$cullTarget = t;
		}
		return t;
	}
}
