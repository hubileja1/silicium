//#if MC >= 1.21.9
package pl.silicium.lod;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Duck interface mixed into Entity: the last full render state and when it was extracted. */
public interface LodHolder {
	EntityRenderState silicium$lodState();

	long silicium$lodTime();

	void silicium$setLod(EntityRenderState state, long time);
}
//#endif
