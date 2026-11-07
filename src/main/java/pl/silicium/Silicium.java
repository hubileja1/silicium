package pl.silicium;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.silicium.chest.StaticChestModel;
import pl.silicium.culling.OcclusionCuller;

public final class Silicium implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("Silicium");

	/** True when the EntityCulling mod is installed - it already does occlusion culling, so ours stays off. */
	public static boolean entityCullingModPresent;

	@Override
	public void onInitializeClient() {
		SiliciumConfig.load();
		entityCullingModPresent = FabricLoader.getInstance().isModLoaded("entityculling");
		ClientTickEvents.END_CLIENT_TICK.register(EntityGrouping::tick);
		ClientTickEvents.END_CLIENT_TICK.register(pl.silicium.water.WaterDistance::tick);
		OcclusionCuller.start();
		StaticChestModel.register();
		SelfTest.registerIfEnabled();
		//#if MC < 26.1
		OpaqueWaterSprites.register();
		//#endif
		LOGGER.info("Silicium loaded{}", entityCullingModPresent ? " (EntityCulling detected - built-in occlusion culling disabled)" : "");
	}

	public static boolean occlusionActive() {
		SiliciumConfig cfg = SiliciumConfig.get();
		return cfg.enabled && cfg.occlusionCulling && !entityCullingModPresent;
	}
}
