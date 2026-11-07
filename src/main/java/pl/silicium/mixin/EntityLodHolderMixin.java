//#if MC >= 1.21.9
package pl.silicium.mixin;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import pl.silicium.lod.LodHolder;

/** Stores the reusable render state for animation LOD on each entity. */
@Mixin(Entity.class)
public abstract class EntityLodHolderMixin implements LodHolder {
	@Unique
	private EntityRenderState silicium$lodState;
	@Unique
	private long silicium$lodTime;

	@Override
	public EntityRenderState silicium$lodState() {
		return silicium$lodState;
	}

	@Override
	public long silicium$lodTime() {
		return silicium$lodTime;
	}

	@Override
	public void silicium$setLod(EntityRenderState state, long time) {
		silicium$lodState = state;
		silicium$lodTime = time;
	}
}
//#endif
