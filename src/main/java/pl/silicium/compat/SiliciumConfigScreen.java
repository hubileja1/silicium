//#if !SODIUM_CONFIG_API
package pl.silicium.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import pl.silicium.SiliciumConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Silicium settings screen for Minecraft versions whose Sodium has no settings API (no Sodium tab).
 * Opened from the "Silicium..." button in the Options menu or from Mod Menu.
 * Same pages and options as the Sodium tab, built from plain vanilla widgets.
 */
public class SiliciumConfigScreen extends Screen {
	private static final int ROW = 24, GAP = 8;
	private int columnWidth = 150;
	/** 0xFF alpha - text without alpha is invisible since 1.21.6. */
	private static final int WHITE = 0xFFFFFFFF;

	private final Screen parent;
	private final List<Page> pages = new ArrayList<>();
	private int page;
	private boolean needsReload;

	public SiliciumConfigScreen(Screen parent) {
		super(Component.literal("Silicium"));
		this.parent = parent;
		buildPages();
	}

	// --- option model ---

	private interface Option {
	}

	private record BoolOption(String key, Supplier<Boolean> get, Consumer<Boolean> set, boolean reload) implements Option {
	}

	private record IntOption(String key, int min, int max, int step, IntFunction<Component> format,
							 Supplier<Integer> get, Consumer<Integer> set, boolean reload) implements Option {
	}

	private record Page(String nameKey, List<Option> options) {
	}

	private static SiliciumConfig cfg() {
		return SiliciumConfig.get();
	}

	private static Component t(String key, Object... args) {
		return Component.translatable(key, args);
	}

	private static IntFunction<Component> distance() {
		return v -> v == 0 ? t("silicium.value.unlimited") : t("silicium.value.blocks", v);
	}

	private static IntFunction<Component> count() {
		return v -> v == 0 ? t("silicium.value.unlimited") : Component.literal(Integer.toString(v));
	}

	private static IntFunction<Component> percent() {
		return v -> t("silicium.value.percent", v);
	}

	private static IntFunction<Component> offOrBlocks() {
		return v -> v == 0 ? t("silicium.value.off") : t("silicium.value.blocks", v);
	}

	private void buildPages() {
		pages.add(new Page("silicium.page.general", List.of(
				new BoolOption("enabled", () -> cfg().enabled, v -> cfg().enabled = v, true),
				new BoolOption("static_chests", () -> cfg().staticChests, v -> cfg().staticChests = v, true),
				new BoolOption("occlusion_culling", () -> cfg().occlusionCulling, v -> cfg().occlusionCulling = v, false)
		)));
		pages.add(new Page("silicium.page.distances", List.of(
				new IntOption("entity_distance", 0, 256, 8, distance(), () -> cfg().entityDistance, v -> cfg().entityDistance = v, false),
				new IntOption("item_distance", 0, 256, 8, distance(), () -> cfg().itemDistance, v -> cfg().itemDistance = v, false),
				new IntOption("item_frame_distance", 0, 256, 8, distance(), () -> cfg().itemFrameDistance, v -> cfg().itemFrameDistance = v, false),
				new IntOption("armor_stand_distance", 0, 256, 8, distance(), () -> cfg().armorStandDistance, v -> cfg().armorStandDistance = v, false),
				new IntOption("block_entity_distance", 0, 256, 8, distance(), () -> cfg().blockEntityDistance, v -> cfg().blockEntityDistance = v, false),
				new IntOption("moving_piston_distance", 0, 256, 8, distance(), () -> cfg().movingPistonDistance, v -> cfg().movingPistonDistance = v, false),
				new BoolOption("always_players", () -> cfg().alwaysRenderPlayers, v -> cfg().alwaysRenderPlayers = v, false),
				new BoolOption("always_named", () -> cfg().alwaysRenderNamedAndGlowing, v -> cfg().alwaysRenderNamedAndGlowing = v, false)
		)));
		pages.add(new Page("silicium.page.crowds", List.of(
				new BoolOption("single_item_model", () -> cfg().singleItemModel, v -> cfg().singleItemModel = v, false),
				new IntOption("max_items_per_block", 0, 64, 1, count(), () -> cfg().maxItemsPerBlock, v -> cfg().maxItemsPerBlock = v, false),
				new IntOption("max_mobs_per_block", 0, 64, 1, count(), () -> cfg().maxMobsPerBlock, v -> cfg().maxMobsPerBlock = v, false),
				new BoolOption("hide_spawner_mobs", () -> cfg().hideSpawnerMobs, v -> cfg().hideSpawnerMobs = v, false)
				//#if MC >= 1.21.9
				, new IntOption("animation_lod_distance", 0, 256, 8, offOrBlocks(), () -> cfg().animationLodDistance, v -> cfg().animationLodDistance = v, false)
				, new IntOption("distant_animation_fps", 5, 60, 5, v -> t("silicium.value.fps", v), () -> cfg().distantAnimationFps, v -> cfg().distantAnimationFps = v, false)
				//#endif
		)));
		pages.add(new Page("silicium.page.explosions", List.of(
				new IntOption("max_tnt_per_block", 0, 32, 1, count(), () -> cfg().maxTntPerBlock, v -> cfg().maxTntPerBlock = v, false),
				new IntOption("max_explosions_per_tick", 0, 32, 1, count(), () -> cfg().maxExplosionsPerTick, v -> cfg().maxExplosionsPerTick = v, false),
				new IntOption("max_explosion_sounds_per_tick", 0, 32, 1, count(), () -> cfg().maxExplosionSoundsPerTick, v -> cfg().maxExplosionSoundsPerTick = v, false)
				//#if MC >= 1.21.9
				, new BoolOption("explosion_debris", () -> cfg().explosionDebris, v -> cfg().explosionDebris = v, false)
				//#endif
		)));
		pages.add(new Page("silicium.page.particles", List.of(
				new IntOption("max_particles", 0, 10000, 250, v -> v == 0 ? t("silicium.value.vanilla") : Component.literal(Integer.toString(v)),
						() -> cfg().maxParticles, v -> cfg().maxParticles = v, false),
				new IntOption("particle_distance", 0, 256, 8, distance(), () -> cfg().particleDistance, v -> cfg().particleDistance = v, false)
		)));
		pages.add(new Page("silicium.page.fluids", List.of(
				new IntOption("opaque_water", 0, 2, 1, v -> t("silicium.value.opaque_water." + v), () -> cfg().opaqueWaterMode, v -> cfg().opaqueWaterMode = v, true),
				new IntOption("opaque_water_near_distance", 0, 48, 2, offOrBlocks(), () -> cfg().opaqueWaterNearDistance, v -> cfg().opaqueWaterNearDistance = v, true),
				new IntOption("lava_effects_percent", 0, 100, 10, percent(), () -> cfg().lavaEffectsPercent, v -> cfg().lavaEffectsPercent = v, false),
				new IntOption("water_effects_percent", 0, 100, 10, percent(), () -> cfg().waterEffectsPercent, v -> cfg().waterEffectsPercent = v, false),
				new IntOption("drip_particle_percent", 0, 100, 10, percent(), () -> cfg().dripParticlePercent, v -> cfg().dripParticlePercent = v, false),
				new IntOption("bubble_column_percent", 0, 100, 10, percent(), () -> cfg().bubbleColumnPercent, v -> cfg().bubbleColumnPercent = v, false)
		)));
		pages.add(new Page("silicium.page.terrain", List.of(
				new IntOption("block_effects_density", 0, 100, 10, percent(), () -> cfg().blockEffectsDensity, v -> cfg().blockEffectsDensity = v, false),
				new IntOption("leaf_particle_percent", 0, 100, 10, percent(), () -> cfg().leafParticlePercent, v -> cfg().leafParticlePercent = v, false),
				new IntOption("ambient_particle_percent", 0, 100, 10, percent(), () -> cfg().ambientParticlePercent, v -> cfg().ambientParticlePercent = v, false)
		)));
	}

	// --- screen ---

	public int pageCount() {
		return pages.size();
	}

	public void showPage(int index) {
		page = Math.floorMod(index, pages.size());
		rebuildWidgets();
	}

	@Override
	protected void init() {
		int center = width / 2;
		addRenderableWidget(Button.builder(Component.literal("<"), b -> showPage(page - 1)).bounds(center - columnWidth - GAP / 2, 32, 20, 20).build());
		addRenderableWidget(Button.builder(Component.literal(">"), b -> showPage(page + 1)).bounds(center + columnWidth + GAP / 2 - 20, 32, 20, 20).build());

		columnWidth = Math.max(150, Math.min(200, (width - 16 - GAP) / 2));
		List<Option> options = pages.get(page).options();
		int left = center - columnWidth - GAP / 2;
		for (int i = 0; i < options.size(); i++) {
			int x = left + (i % 2) * (columnWidth + GAP);
			int y = 62 + (i / 2) * ROW;
			addRenderableWidget(widgetFor(options.get(i), x, y));
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(center - 100, height - 28, 200, 20).build());
	}

	private net.minecraft.client.gui.components.AbstractWidget widgetFor(Option option, int x, int y) {
		if (option instanceof BoolOption b) {
			CycleButton<Boolean> button = CycleButton.onOffBuilder(b.get().get())
					.create(x, y, columnWidth, 20, t("silicium.option." + b.key()), (btn, value) -> {
						b.set().accept(value);
						if (b.reload()) needsReload = true;
					});
			button.setTooltip(Tooltip.create(t("silicium.option." + b.key() + ".tooltip")));
			return button;
		}
		IntOption o = (IntOption) option;
		int steps = (o.max() - o.min()) / o.step();
		double initial = steps == 0 ? 0 : (o.get().get() - o.min()) / (double) (o.max() - o.min());
		AbstractSliderButton slider = new AbstractSliderButton(x, y, columnWidth, 20, Component.empty(), Mth.clamp(initial, 0, 1)) {
			{
				updateMessage();
			}

			private int current() {
				return o.min() + (int) Math.round(value * steps) * o.step();
			}

			@Override
			protected void updateMessage() {
				setMessage(t("silicium.option." + o.key()).copy().append(": ").append(o.format().apply(current())));
			}

			@Override
			protected void applyValue() {
				int v = current();
				if (v != o.get().get()) {
					o.set().accept(v);
					if (o.reload()) needsReload = true;
				}
			}
		};
		slider.setTooltip(Tooltip.create(t("silicium.option." + o.key() + ".tooltip")));
		return slider;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		//#if MC < 1.20.2
		renderBackground(graphics);
		//#endif
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, 12, WHITE);
		Component pageName = t(pages.get(page).nameKey()).copy().append("  (" + (page + 1) + "/" + pages.size() + ")");
		graphics.drawCenteredString(font, pageName, width / 2, 38, WHITE);
	}

	@Override
	public void onClose() {
		SiliciumConfig.save();
		if (needsReload && Minecraft.getInstance().levelRenderer != null) {
			Minecraft.getInstance().levelRenderer.allChanged(); // static chests / opaque water: rebuild chunks
		}
		Minecraft.getInstance().setScreen(parent);
	}
}
//#endif
