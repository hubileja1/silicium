//#if MC >= 1.20.1
package pl.silicium.chest;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;
//#if MC >= 26.1
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
//#elif MC >= 1.21.5
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.client.renderer.block.model.BlockStateModel;
//#elif MC >= 1.21.4
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.DelegateBakedModel;
import net.minecraft.world.level.BlockAndTintGetter;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
//#else
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.BlockAndTintGetter;

import java.util.Set;
import java.util.function.Supplier;
//#endif

/**
 * Wraps the static chest block model: emits nothing while the chest is animating
 * (the block entity renderer draws it then) or when the feature is turned off.
 * Works with Sodium (it supports the Fabric Renderer API) and vanilla's renderer.
 */
//#if MC >= 1.21.5
public final class StaticChestModel extends WrapperBlockStateModel {
	private StaticChestModel(BlockStateModel wrapped) {
		super(wrapped);
	}

	public static void register() {
		ModelLoadingPlugin.register(ctx -> ctx.modifyBlockModelAfterBake().register((model, context) ->
				StaticChests.isStaticChest(context.state()) ? new StaticChestModel(model) : model));
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
						  RandomSource random, Predicate<Direction> cullTest) {
		if (!StaticChests.active() || StaticChests.isAnimating(pos)) return;
		super.emitQuads(emitter, level, pos, state, random, cullTest);
	}
}
//#elif MC >= 1.21.4
public final class StaticChestModel extends DelegateBakedModel {
	/** Vanilla chest blocks we ship static models for (copper chests only exist since 1.21.9). */
	private static final Set<String> CHESTS = Set.of("chest", "trapped_chest", "ender_chest");

	private StaticChestModel(BakedModel wrapped) {
		super(wrapped);
	}

	public static void register() {
		ModelLoadingPlugin.register(ctx -> ctx.modifyBlockModelAfterBake().register((model, context) -> {
			var id = context.id().id();
			return id.getNamespace().equals("minecraft") && CHESTS.contains(id.getPath()) ? new StaticChestModel(model) : model;
		}));
	}

	@Override
	public boolean isVanillaAdapter() {
		return false;
	}

	@Override
	public void emitBlockQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockState state, BlockPos pos,
							   Supplier<RandomSource> random, Predicate<Direction> cullTest) {
		if (!StaticChests.active() || StaticChests.isAnimating(pos)) return;
		parent.emitBlockQuads(emitter, level, state, pos, random, cullTest);
	}

	@Override
	public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
		return parent.getQuads(state, side, random);
	}

	@Override
	public boolean useAmbientOcclusion() {
		return parent.useAmbientOcclusion();
	}

	@Override
	public boolean isGui3d() {
		return parent.isGui3d();
	}

	@Override
	public boolean usesBlockLight() {
		return parent.usesBlockLight();
	}
}
//#else
public final class StaticChestModel extends ForwardingBakedModel {
	/** Vanilla chest blocks we ship static models for. */
	private static final Set<String> CHESTS = Set.of("chest", "trapped_chest", "ender_chest");

	private StaticChestModel(BakedModel wrapped) {
		this.wrapped = wrapped;
	}

	public static void register() {
		ModelLoadingPlugin.register(ctx -> ctx.modifyModelAfterBake().register((model, context) -> {
			//#if MC >= 1.21
			var id = context.topLevelId();
			if (model == null || id == null) return model;
			return id.id().getNamespace().equals("minecraft") && CHESTS.contains(id.id().getPath()) && !id.variant().equals("inventory")
					? new StaticChestModel(model) : model;
			//#else
			if (model == null || !(context.id() instanceof net.minecraft.client.resources.model.ModelResourceLocation id)) return model;
			return id.getNamespace().equals("minecraft") && CHESTS.contains(id.getPath()) && !id.getVariant().equals("inventory")
					? new StaticChestModel(model) : model;
			//#endif
		}));
	}

	@Override
	public boolean isVanillaAdapter() {
		return false;
	}

	@Override
	public void emitBlockQuads(BlockAndTintGetter level, BlockState state, BlockPos pos, Supplier<RandomSource> random, RenderContext context) {
		if (!StaticChests.active() || StaticChests.isAnimating(pos)) return;
		super.emitBlockQuads(level, state, pos, random, context);
	}
}
//#endif
//#else
package pl.silicium.chest;

/** Fabric API for 1.20 has no model loading API - static chests are not available there. */
public final class StaticChestModel {
	public static void register() {
	}
}
//#endif
