//#if SODIUM_CONFIG_API
package pl.silicium.compat;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.option.ControlValueFormatter;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.structure.BooleanOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.IntegerOptionBuilder;
import net.minecraft.network.chat.Component;
//#if MC >= 1.21.11
import net.minecraft.resources.Identifier;
//#else
import net.minecraft.resources.ResourceLocation;
//#endif
import pl.silicium.SiliciumConfig;
import pl.silicium.Silicium;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * "Silicium" tab in Sodium's video settings.
 * Loaded by Sodium through the "sodium:config_api_user" entrypoint - without Sodium this class is never loaded.
 */
public final class SodiumOptionsPage implements ConfigEntryPoint {
	private static final SiliciumConfig DEFAULTS = new SiliciumConfig();

	private static final ControlValueFormatter DISTANCE = v -> v == 0
			? Component.translatable("silicium.value.unlimited")
			: Component.translatable("silicium.value.blocks", v);
	private static final ControlValueFormatter COUNT = v -> v == 0
			? Component.translatable("silicium.value.unlimited")
			: Component.literal(Integer.toString(v));

	@Override
	public void registerConfigLate(ConfigBuilder b) {
		b.registerModOptions("silicium")
				.setNonTintedIcon(id("textures/gui/icon.png"))
				.addPage(b.createOptionPage()
						.setName(Component.translatable("silicium.page.general"))
						.addOptionGroup(b.createOptionGroup()
								.setName(Component.translatable("silicium.group.general"))
								.addOption(bool(b, "enabled", OptionImpact.HIGH, DEFAULTS.enabled,
										v -> cfg().enabled = v, () -> cfg().enabled)))
						.addOptionGroup(b.createOptionGroup()
								.setName(Component.translatable("silicium.group.chests"))
								.addOption(bool(b, "static_chests", OptionImpact.HIGH, DEFAULTS.staticChests,
										v -> cfg().staticChests = v, () -> cfg().staticChests)
										.setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)))
						.addOptionGroup(b.createOptionGroup()
								.setName(Component.translatable("silicium.group.culling"))
								.addOption(bool(b, "occlusion_culling", OptionImpact.HIGH, DEFAULTS.occlusionCulling,
										v -> cfg().occlusionCulling = v, () -> cfg().occlusionCulling)
										.setEnabled(!Silicium.entityCullingModPresent))))
				.addPage(b.createOptionPage()
						.setName(Component.translatable("silicium.page.distances"))
						.addOptionGroup(b.createOptionGroup()
								.setName(Component.translatable("silicium.group.distances"))
								.addOption(distance(b, "entity_distance", DEFAULTS.entityDistance,
										v -> cfg().entityDistance = v, () -> cfg().entityDistance))
								.addOption(distance(b, "item_distance", DEFAULTS.itemDistance,
										v -> cfg().itemDistance = v, () -> cfg().itemDistance))
								.addOption(distance(b, "item_frame_distance", DEFAULTS.itemFrameDistance,
										v -> cfg().itemFrameDistance = v, () -> cfg().itemFrameDistance))
								.addOption(distance(b, "armor_stand_distance", DEFAULTS.armorStandDistance,
										v -> cfg().armorStandDistance = v, () -> cfg().armorStandDistance))
								.addOption(distance(b, "block_entity_distance", DEFAULTS.blockEntityDistance,
										v -> cfg().blockEntityDistance = v, () -> cfg().blockEntityDistance))
								.addOption(distance(b, "moving_piston_distance", DEFAULTS.movingPistonDistance,
										v -> cfg().movingPistonDistance = v, () -> cfg().movingPistonDistance)))
						.addOptionGroup(b.createOptionGroup()
								.setName(Component.translatable("silicium.group.exceptions"))
								.addOption(bool(b, "always_players", OptionImpact.LOW, DEFAULTS.alwaysRenderPlayers,
										v -> cfg().alwaysRenderPlayers = v, () -> cfg().alwaysRenderPlayers))
								.addOption(bool(b, "always_named", OptionImpact.LOW, DEFAULTS.alwaysRenderNamedAndGlowing,
										v -> cfg().alwaysRenderNamedAndGlowing = v, () -> cfg().alwaysRenderNamedAndGlowing))))
				.addPage(b.createOptionPage()
						.setName(Component.translatable("silicium.page.crowds"))
						.addOptionGroup(b.createOptionGroup()
								.setName(Component.translatable("silicium.group.items"))
								.addOption(bool(b, "single_item_model", OptionImpact.MEDIUM, DEFAULTS.singleItemModel,
										v -> cfg().singleItemModel = v, () -> cfg().singleItemModel))
								.addOption(count(b, "max_items_per_block", 64, 1, DEFAULTS.maxItemsPerBlock,
										v -> cfg().maxItemsPerBlock = v, () -> cfg().maxItemsPerBlock)))
						.addOptionGroup(b.createOptionGroup()
								.setName(Component.translatable("silicium.group.mobs"))
								.addOption(count(b, "max_mobs_per_block", 64, 1, DEFAULTS.maxMobsPerBlock,
										v -> cfg().maxMobsPerBlock = v, () -> cfg().maxMobsPerBlock))
								.addOption(bool(b, "hide_spawner_mobs", OptionImpact.MEDIUM, DEFAULTS.hideSpawnerMobs,
										v -> cfg().hideSpawnerMobs = v, () -> cfg().hideSpawnerMobs)))
						.addOptionGroup(b.createOptionGroup()
								.setName(Component.translatable("silicium.group.animation_lod"))
								.addOption(distance(b, "animation_lod_distance", DEFAULTS.animationLodDistance,
										v -> cfg().animationLodDistance = v, () -> cfg().animationLodDistance)
										.setValueFormatter(v -> v == 0
												? Component.translatable("silicium.value.off")
												: Component.translatable("silicium.value.blocks", v)))
								.addOption(b.createIntegerOption(id("distant_animation_fps"))
										.setName(Component.translatable("silicium.option.distant_animation_fps"))
										.setTooltip(Component.translatable("silicium.option.distant_animation_fps.tooltip"))
										.setImpact(OptionImpact.MEDIUM)
										.setRange(5, 60, 5)
										.setValueFormatter(v -> Component.translatable("silicium.value.fps", v))
										.setDefaultValue(DEFAULTS.distantAnimationFps)
										.setBinding(v -> cfg().distantAnimationFps = v, () -> cfg().distantAnimationFps)
										.setStorageHandler(SiliciumConfig::save))))
				.addPage(b.createOptionPage()
						.setName(Component.translatable("silicium.page.explosions"))
						.addOptionGroup(b.createOptionGroup()
								.setName(Component.translatable("silicium.group.tnt"))
								.addOption(count(b, "max_tnt_per_block", 32, 1, DEFAULTS.maxTntPerBlock,
										v -> cfg().maxTntPerBlock = v, () -> cfg().maxTntPerBlock))
								.addOption(count(b, "max_explosions_per_tick", 32, 1, DEFAULTS.maxExplosionsPerTick,
										v -> cfg().maxExplosionsPerTick = v, () -> cfg().maxExplosionsPerTick))
								.addOption(count(b, "max_explosion_sounds_per_tick", 32, 1, DEFAULTS.maxExplosionSoundsPerTick,
										v -> cfg().maxExplosionSoundsPerTick = v, () -> cfg().maxExplosionSoundsPerTick))
								.addOption(bool(b, "explosion_debris", OptionImpact.MEDIUM, DEFAULTS.explosionDebris,
										v -> cfg().explosionDebris = v, () -> cfg().explosionDebris))))
				.addPage(b.createOptionPage()
						.setName(Component.translatable("silicium.page.particles"))
						.addOptionGroup(b.createOptionGroup()
								.setName(Component.translatable("silicium.group.particles"))
								.addOption(b.createIntegerOption(id("max_particles"))
										.setName(Component.translatable("silicium.option.max_particles"))
										.setTooltip(Component.translatable("silicium.option.max_particles.tooltip"))
										.setImpact(OptionImpact.HIGH)
										.setRange(0, 10000, 250)
										.setValueFormatter(v -> v == 0 ? Component.translatable("silicium.value.vanilla") : Component.literal(Integer.toString(v)))
										.setDefaultValue(DEFAULTS.maxParticles)
										.setBinding(v -> cfg().maxParticles = v, () -> cfg().maxParticles)
										.setStorageHandler(SiliciumConfig::save))
								.addOption(distance(b, "particle_distance", DEFAULTS.particleDistance,
										v -> cfg().particleDistance = v, () -> cfg().particleDistance))))
				.addPage(b.createOptionPage()
						.setName(Component.translatable("silicium.page.fluids"))
						.addOptionGroup(b.createOptionGroup()
								.setName(Component.translatable("silicium.group.water_rendering"))
								.addOption(b.createIntegerOption(id("opaque_water_mode"))
										.setName(Component.translatable("silicium.option.opaque_water"))
										.setTooltip(Component.translatable("silicium.option.opaque_water.tooltip"))
										.setImpact(OptionImpact.HIGH)
										.setRange(0, 2, 1)
										.setValueFormatter(v -> Component.translatable("silicium.value.opaque_water." + v))
										.setDefaultValue(DEFAULTS.opaqueWaterMode)
										.setBinding(v -> cfg().opaqueWaterMode = v, () -> cfg().opaqueWaterMode)
										.setStorageHandler(SiliciumConfig::save)
										.setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD))
								.addOption(b.createIntegerOption(id("opaque_water_near_distance"))
										.setName(Component.translatable("silicium.option.opaque_water_near_distance"))
										.setTooltip(Component.translatable("silicium.option.opaque_water_near_distance.tooltip"))
										.setImpact(OptionImpact.MEDIUM)
										.setRange(0, 48, 2)
										.setValueFormatter(v -> v == 0
												? Component.translatable("silicium.value.off")
												: Component.translatable("silicium.value.blocks", v))
										.setDefaultValue(DEFAULTS.opaqueWaterNearDistance)
										.setBinding(v -> cfg().opaqueWaterNearDistance = v, () -> cfg().opaqueWaterNearDistance)
										.setStorageHandler(SiliciumConfig::save)
										.setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)))
						.addOptionGroup(b.createOptionGroup()
								.setName(Component.translatable("silicium.group.fluids"))
								.addOption(percent(b, "lava_effects_percent", DEFAULTS.lavaEffectsPercent,
										v -> cfg().lavaEffectsPercent = v, () -> cfg().lavaEffectsPercent))
								.addOption(percent(b, "water_effects_percent", DEFAULTS.waterEffectsPercent,
										v -> cfg().waterEffectsPercent = v, () -> cfg().waterEffectsPercent))
								.addOption(percent(b, "drip_particle_percent", DEFAULTS.dripParticlePercent,
										v -> cfg().dripParticlePercent = v, () -> cfg().dripParticlePercent))
								.addOption(percent(b, "bubble_column_percent", DEFAULTS.bubbleColumnPercent,
										v -> cfg().bubbleColumnPercent = v, () -> cfg().bubbleColumnPercent))))
				.addPage(b.createOptionPage()
						.setName(Component.translatable("silicium.page.terrain"))
						.addOptionGroup(b.createOptionGroup()
								.setName(Component.translatable("silicium.group.terrain"))
								.addOption(percent(b, "block_effects_density", DEFAULTS.blockEffectsDensity,
										v -> cfg().blockEffectsDensity = v, () -> cfg().blockEffectsDensity))
								.addOption(percent(b, "leaf_particle_percent", DEFAULTS.leafParticlePercent,
										v -> cfg().leafParticlePercent = v, () -> cfg().leafParticlePercent))
								.addOption(percent(b, "ambient_particle_percent", DEFAULTS.ambientParticlePercent,
										v -> cfg().ambientParticlePercent = v, () -> cfg().ambientParticlePercent))));
	}

	private static IntegerOptionBuilder percent(ConfigBuilder b, String key, int def,
												Consumer<Integer> set, Supplier<Integer> get) {
		return b.createIntegerOption(id(key))
				.setName(Component.translatable("silicium.option." + key))
				.setTooltip(Component.translatable("silicium.option." + key + ".tooltip"))
				.setImpact(OptionImpact.MEDIUM)
				.setRange(0, 100, 10)
				.setValueFormatter(v -> Component.translatable("silicium.value.percent", v))
				.setDefaultValue(def)
				.setBinding(set, get)
				.setStorageHandler(SiliciumConfig::save);
	}

	// --- helpers ---

	private static SiliciumConfig cfg() {
		return SiliciumConfig.get();
	}

	//#if MC >= 1.21.11
	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("silicium", path);
	}
	//#else
	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("silicium", path);
	}
	//#endif

	private static BooleanOptionBuilder bool(ConfigBuilder b, String key, OptionImpact impact, boolean def,
											 Consumer<Boolean> set, Supplier<Boolean> get) {
		return b.createBooleanOption(id(key))
				.setName(Component.translatable("silicium.option." + key))
				.setTooltip(Component.translatable("silicium.option." + key + ".tooltip"))
				.setImpact(impact)
				.setDefaultValue(def)
				.setBinding(set, get)
				.setStorageHandler(SiliciumConfig::save);
	}

	private static IntegerOptionBuilder distance(ConfigBuilder b, String key, int def,
												 Consumer<Integer> set, Supplier<Integer> get) {
		return b.createIntegerOption(id(key))
				.setName(Component.translatable("silicium.option." + key))
				.setTooltip(Component.translatable("silicium.option." + key + ".tooltip"))
				.setImpact(OptionImpact.HIGH)
				.setRange(0, 256, 8)
				.setValueFormatter(DISTANCE)
				.setDefaultValue(def)
				.setBinding(set, get)
				.setStorageHandler(SiliciumConfig::save);
	}

	private static IntegerOptionBuilder count(ConfigBuilder b, String key, int max, int step, int def,
											  Consumer<Integer> set, Supplier<Integer> get) {
		return b.createIntegerOption(id(key))
				.setName(Component.translatable("silicium.option." + key))
				.setTooltip(Component.translatable("silicium.option." + key + ".tooltip"))
				.setImpact(OptionImpact.MEDIUM)
				.setRange(0, max, step)
				.setValueFormatter(COUNT)
				.setDefaultValue(def)
				.setBinding(set, get)
				.setStorageHandler(SiliciumConfig::save);
	}
}
//#endif
