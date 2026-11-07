//#if MC < 26.1
package pl.silicium;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Opaque water before 26.1: water sides touching glass or leaves use the "overlay" texture, which is
 * almost fully transparent. In the solid layer that turns into a flat dark color, so while opaque water
 * is on, the overlay is replaced with the normal flowing water texture. Works for vanilla and Sodium,
 * both read the sprites from Fabric's fluid render handler.
 */
final class OpaqueWaterSprites implements FluidRenderHandler {
	private final FluidRenderHandler original;
	private TextureAtlasSprite[] lastOriginal;
	private TextureAtlasSprite[] lastReplaced;

	private OpaqueWaterSprites(FluidRenderHandler original) {
		this.original = original;
	}

	static void register() {
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			FluidRenderHandler water = FluidRenderHandlerRegistry.INSTANCE.get(Fluids.WATER);
			if (water != null && !(water instanceof OpaqueWaterSprites)) {
				FluidRenderHandlerRegistry.INSTANCE.register(Fluids.WATER, Fluids.FLOWING_WATER, new OpaqueWaterSprites(water));
			}
		});
	}

	@Override
	public TextureAtlasSprite[] getFluidSprites(BlockAndTintGetter view, BlockPos pos, FluidState state) {
		TextureAtlasSprite[] sprites = original.getFluidSprites(view, pos, state);
		if (sprites == null || sprites.length < 3 || sprites[2] == null || !pl.silicium.water.WaterDistance.opaque(state)) return sprites;
		TextureAtlasSprite[] last = lastReplaced;
		if (lastOriginal != sprites || last == null) {
			last = new TextureAtlasSprite[]{sprites[0], sprites[1], sprites[1]};
			lastReplaced = last;
			lastOriginal = sprites;
		}
		return last;
	}

	@Override
	public int getFluidColor(BlockAndTintGetter view, BlockPos pos, FluidState state) {
		return original.getFluidColor(view, pos, state);
	}

	@Override
	public void reloadTextures(TextureAtlas atlas) {
		original.reloadTextures(atlas);
	}
}
//#endif
