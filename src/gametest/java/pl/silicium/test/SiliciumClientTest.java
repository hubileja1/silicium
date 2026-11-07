package pl.silicium.test;

//#if SODIUM_CONFIG_API
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.config.structure.ModOptions;
import net.caffeinemc.mods.sodium.client.config.structure.OptionPage;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
//#endif

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
//#if MC >= 1.21.11
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
//#else
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
//#endif
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.MixinEnvironment;
import pl.silicium.EntityGrouping;
import pl.silicium.SiliciumConfig;
import pl.silicium.chest.StaticChests;
//#if MC >= 1.21.9
import pl.silicium.lod.LodHolder;
import net.minecraft.world.entity.animal.goat.Goat;
//#endif
import pl.silicium.culling.CullHolder;

/**
 * In-game test against a real dedicated server (multiplayer):
 * - every mixin applies (audit),
 * - surplus dropped items and mobs in one block are hidden,
 * - occlusion culling hides a mob behind a wall and shows it again when the wall is removed,
 * - TNT explosions and bubble columns do not crash the client,
 * - the Silicium tab exists in Sodium's settings.
 */
public class SiliciumClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		// Force-loads every mixin target class - throws if any mixin fails to apply
		MixinEnvironment.getCurrentEnvironment().audit();

		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer();
			 var conn = server.connect()) {
			//#if MC >= 26.2
			conn.waitForChunksRender();
			//#elif MC >= 26.1
			conn.getClientLevel().waitForChunksRender();
			//#else
			conn.getClientWorld().waitForChunksRender();
			//#endif
			server.runCommand("gamemode creative @a");
			server.runCommand("time set day");
			server.runCommand("gamerule spawn_mobs false");

			BlockPos p = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).blockPosition());
			int x = p.getX(), y = p.getY(), z = p.getZ();

			// --- crowd limits: 20 item stacks and 20 cows in one block ---
			for (int i = 0; i < 20; i++) {
				server.runCommand(String.format("summon item %d %d %d {Item:{id:\"minecraft:diamond\",count:64},PickupDelay:32767,Motion:[0d,0d,0d]}", x + 3, y, z));
				server.runCommand(String.format("summon cow %d %d %d {NoAI:1b}", x + 3, y, z + 3));
			}
			server.runCommand(String.format("tp @a %d %d %d facing %d %d %d", x, y, z, x + 3, y, z + 1));
			ctx.waitTicks(40);
			int hiddenItems = countHidden(ctx, ItemEntity.class);
			int hiddenCows = countHidden(ctx, Cow.class);
			SiliciumConfig cfg = SiliciumConfig.get();
			// stable lottery per entity: roughly the limit stays visible (not an exact count)
			int visibleItems = 20 - hiddenItems, visibleCows = 20 - hiddenCows;
			System.out.println("[silicium-test] visible items " + visibleItems + "/20 (limit " + cfg.maxItemsPerBlock + "), cows " + visibleCows + "/20 (limit " + cfg.maxMobsPerBlock + ")");
			check(visibleItems >= 1 && visibleItems <= cfg.maxItemsPerBlock * 2, "visible items: " + visibleItems);
			check(visibleCows >= 2 && visibleCows <= cfg.maxMobsPerBlock * 2, "visible cows: " + visibleCows);
			ctx.takeScreenshot("silicium-crowds");

			//#if MC >= 1.21.9
			// --- animation LOD: a near and a far mob ---
			server.runCommand(String.format("summon goat %d %d %d {NoAI:1b,CustomName:'\"near\"'}", x + 6, y, z - 1));
			server.runCommand(String.format("summon goat %d %d %d {NoAI:1b}", x + 40, y, z - 1));
			server.runCommand(String.format("tp @a %d %d %d facing %d %d %d", x, y + 1, z, x + 40, y, z - 1));
			ctx.waitTicks(20);
			boolean[] lod = ctx.computeOnClient(mc -> {
				boolean near = false, far = false;
				for (Entity e : mc.level.entitiesForRendering()) {
					if (e instanceof Goat) {
						boolean cached = ((LodHolder) e).silicium$lodState() != null;
						if (e.distanceToSqr(mc.player) < 20 * 20) near |= cached; else far |= cached;
					}
				}
				return new boolean[]{near, far};
			});
			check(!lod[0], "near mob must be fully animated (no LOD cache)");
			check(lod[1], "far mob should use animation LOD");
			ctx.takeScreenshot("silicium-animation-lod");
			//#endif

			// --- packed farm: 30 walking chickens in a 2x2 pen - visibility must not keep flipping (flicker) ---
			int fx = x - 10, fz = z + 10;
			server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:glass", fx - 1, y, fz - 1, fx + 2, y + 2, fz + 2));
			server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:air", fx, y, fz, fx + 1, y + 2, fz + 1));
			for (int i = 0; i < 30; i++) {
				server.runCommand(String.format("summon chicken %d.5 %d %d.5", fx + (i % 2), y, fz + (i / 2 % 2)));
			}
			ctx.waitTicks(40);
			int flips = 0;
			java.util.Map<Integer, Boolean> last = new java.util.HashMap<>();
			for (int t = 0; t < 60; t++) {
				ctx.waitTick();
				flips += ctx.computeOnClient(mc -> {
					int n = 0;
					for (Entity e : mc.level.entitiesForRendering()) {
						if (!net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals("chicken")) continue;
						boolean h = EntityGrouping.isHidden(e);
						Boolean prev = last.put(e.getId(), h);
						if (prev != null && prev != h) n++;
					}
					return n;
				});
			}
			System.out.println("[silicium-test] chicken pen visibility flips in 60 ticks: " + flips);
			check(flips <= 30, "farm mobs flicker: " + flips + " visibility flips in 60 ticks");
			server.runCommand("kill @e[type=chicken]");

			// --- occlusion culling: pig 10 blocks away, then a stone wall in between ---
			int px = x, pz = z - 10;
			server.runCommand(String.format("summon pig %d %d %d {NoAI:1b}", px, y, pz));
			server.runCommand(String.format("tp @a %d %d %d facing %d %d %d", x, y, z, px, y, pz));
			ctx.waitTicks(20);
			check(pigVisible(ctx), "pig should be visible without a wall");
			ctx.takeScreenshot("silicium-occlusion-open");

			server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:stone", x - 4, y - 1, z - 5, x + 4, y + 6, z - 5));
			ctx.waitTicks(30);
			check(!pigVisible(ctx), "pig behind the wall should be culled");
			ctx.takeScreenshot("silicium-occlusion-wall");

			server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:air", x - 4, y - 1, z - 5, x + 4, y + 6, z - 5));
			ctx.waitTicks(30);
			check(pigVisible(ctx), "pig should be visible again after removing the wall");

			// --- static chests: same look as vanilla, lid still animates ---
			int cz = z - 4;
			server.runCommand(String.format("setblock %d %d %d minecraft:chest[facing=south]", x - 4, y, cz));
			server.runCommand(String.format("setblock %d %d %d minecraft:chest[facing=north]", x - 2, y, cz));
			server.runCommand(String.format("setblock %d %d %d minecraft:chest[facing=east]", x, y, cz));
			server.runCommand(String.format("setblock %d %d %d minecraft:chest[facing=west]", x + 2, y, cz));
			server.runCommand(String.format("setblock %d %d %d minecraft:chest[facing=south,type=right]", x + 4, y, cz));
			server.runCommand(String.format("setblock %d %d %d minecraft:chest[facing=south,type=left]", x + 5, y, cz));
			server.runCommand(String.format("setblock %d %d %d minecraft:ender_chest[facing=south]", x - 4, y, cz - 2));
			server.runCommand(String.format("setblock %d %d %d minecraft:trapped_chest[facing=south]", x - 2, y, cz - 2));
			server.runCommand(String.format("setblock %d %d %d minecraft:copper_chest[facing=south]", x, y, cz - 2));
			server.runCommand(String.format("setblock %d %d %d minecraft:oxidized_copper_chest[facing=south]", x + 2, y, cz - 2));
			server.runCommand(String.format("setblock %d %d %d minecraft:trapped_chest[facing=south,type=right]", x + 4, y, cz - 2));
			server.runCommand(String.format("setblock %d %d %d minecraft:trapped_chest[facing=south,type=left]", x + 5, y, cz - 2));
			server.runCommand(String.format("tp @a %d.5 %d %d.5 facing %d.5 %d %d.5", x, y + 2, z, x, y, cz - 1));
			ctx.waitTicks(30);
			ctx.takeScreenshot("silicium-chests-static");

			// same view rendered the vanilla way, for comparison
			SiliciumConfig.get().staticChests = false;
			rebuildAround(ctx, x, y, z);
			ctx.waitTicks(30);
			ctx.takeScreenshot("silicium-chests-vanilla");
			SiliciumConfig.get().staticChests = true;
			rebuildAround(ctx, x, y, z);
			ctx.waitTicks(30);

			// open the south-facing chest for real (server opens the chest menu for the player, like a right-click)
			BlockPos chestPos = new BlockPos(x - 4, y, cz);
			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().get(0);
				player.openMenu(player.level().getBlockState(chestPos).getMenuProvider(player.level(), chestPos));
			});
			ctx.waitTicks(15);
			check(ctx.computeOnClient(mc -> StaticChests.isAnimating(chestPos)), "opened chest should be drawn by the renderer");
			ctx.setScreen(() -> null);
			ctx.waitTicks(2);
			ctx.takeScreenshot("silicium-chest-open");
			server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).closeContainer());
			ctx.waitTicks(60);
			check(!ctx.computeOnClient(mc -> StaticChests.isAnimating(chestPos)), "closed chest should be back in the chunk mesh");

			// --- bubble column: soul sand + water ---
			server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:glass", x - 4, y - 1, z - 1, x - 2, y + 6, z + 1));
			server.runCommand(String.format("setblock %d %d %d minecraft:soul_sand", x - 3, y - 1, z));
			server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:water", x - 3, y, z, x - 3, y + 5, z));
			server.runCommand(String.format("tp @a %d %d %d facing %d %d %d", x + 1, y + 2, z, x - 3, y + 2, z));
			ctx.waitTicks(20);
			ctx.takeScreenshot("silicium-bubbles");

			// --- water, lava, leaves and pistons (near and 60 blocks away) ---
			server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:lava", x + 4, y - 1, z + 4, x + 8, y - 1, z + 8));
			server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:water", x - 8, y - 1, z + 4, x - 4, y - 1, z + 8));
			server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:oak_leaves[persistent=true]", x - 2, y + 4, z + 6, x + 2, y + 6, z + 10));
			for (int px2 : new int[]{x, x + 60}) {
				server.runCommand(String.format("setblock %d %d %d minecraft:piston[facing=up]", px2, y, z + 12));
				server.runCommand(String.format("setblock %d %d %d minecraft:stone", px2, y + 1, z + 12));
				server.runCommand(String.format("setblock %d %d %d minecraft:redstone_block", px2 + 1, y, z + 12));
			}
			server.runCommand(String.format("tp @a %d %d %d facing %d %d %d", x, y + 3, z, x, y, z + 8));
			ctx.waitTicks(40);
			ctx.takeScreenshot("silicium-fluids-leaves-pistons");

			// --- TNT cannon: 12 TNT in one block, 12 blocks away ---
			for (int i = 0; i < 12; i++) {
				server.runCommand(String.format("summon tnt %d %d %d {fuse:30,NoGravity:1b,Motion:[0d,0d,0d]}", x + 12, y + 1, z));
			}
			ctx.waitTicks(10);
			ctx.takeScreenshot("silicium-tnt");
			ctx.waitTicks(40);
			ctx.takeScreenshot("silicium-after-explosion");

			// --- opaque water: a huge water wall like on big servers ---
			int wz = z - 24;
			server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:water", x - 20, y, wz, x + 20, y + 39, wz));
			server.runCommand(String.format("tp @a %d %d %d facing %d %d %d", x, y + 2, z, x, y + 12, wz));
			SiliciumConfig.get().opaqueWaterMode = 0;
			ctx.runOnClient(mc -> mc.level.setSectionRangeDirty((x >> 4) - 3, (y >> 4) - 1, (wz >> 4) - 2, (x >> 4) + 3, (y >> 4) + 4, (z >> 4) + 1));
			ctx.waitTicks(40);
			ctx.takeScreenshot("silicium-water-translucent");
			SiliciumConfig.get().opaqueWaterMode = 2;
			ctx.runOnClient(mc -> mc.level.setSectionRangeDirty((x >> 4) - 3, (y >> 4) - 1, (wz >> 4) - 2, (x >> 4) + 3, (y >> 4) + 4, (z >> 4) + 1));
			ctx.waitTicks(40);
			ctx.takeScreenshot("silicium-water-opaque");

			// walk up to the wall: the section right in front of us must turn see-through, the rest stays opaque
			server.runCommand(String.format("tp @a %d %d %d facing %d %d %d", x + 2, y + 2, wz + 4, x - 12, y + 6, wz));
			ctx.waitTicks(60);
			ctx.takeScreenshot("silicium-water-near-translucent");

			// waterfalls mode: a waterfall next to the wall - only the falling water is opaque
			server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:stone", x + 24, y, wz + 8, x + 24, y + 18, wz + 8));
			server.runCommand(String.format("setblock %d %d %d minecraft:water", x + 24, y + 19, wz + 9));
			SiliciumConfig.get().opaqueWaterMode = 1;
			ctx.runOnClient(mc -> mc.level.setSectionRangeDirty((x >> 4) - 3, (y >> 4) - 1, (wz >> 4) - 2, (x >> 4) + 3, (y >> 4) + 4, (z >> 4) + 1));
			server.runCommand(String.format("tp @a %d %d %d facing %d %d %d", x + 12, y + 14, wz + 22, x + 22, y + 8, wz + 8));
			ctx.waitTicks(80);
			ctx.takeScreenshot("silicium-water-waterfalls-mode");
			SiliciumConfig.get().opaqueWaterMode = 0;

			// --- every Silicium page in Sodium's settings (only when Sodium is installed) ---
			//#if !SODIUM_CONFIG_API
			// own settings screen (versions whose Sodium has no settings API)
			var screen = new pl.silicium.compat.SiliciumConfigScreen(null);
			ctx.setScreen(() -> screen);
			ctx.waitTicks(5);
			ctx.takeScreenshot("silicium-config-screen-page0");
			ctx.runOnClient(mc -> screen.showPage(1));
			ctx.waitTicks(5);
			ctx.takeScreenshot("silicium-config-screen-page1");
			ctx.setScreen(() -> null);
			//#endif

			//#if SODIUM_CONFIG_API
			if (FabricLoader.getInstance().isModLoaded("sodium")) {
				SodiumPages.screenshotAll(ctx);
			}
			//#endif
		}
	}

	//#if SODIUM_CONFIG_API
	/** Separate class so Sodium classes are only loaded when Sodium is present. */
	private static final class SodiumPages {
		static void screenshotAll(ClientGameTestContext ctx) {
			ModOptions mod = ConfigManager.CONFIG.getModOptions().stream()
					.filter(m -> m.configId().equals("silicium")).findFirst()
					.orElseThrow(() -> new AssertionError("Silicium tab missing in Sodium"));
			for (int i = 0; i < mod.pages().size(); i++) {
				OptionPage page = (OptionPage) mod.pages().get(i);
				ctx.setScreen(() -> VideoSettingsScreen.createScreen(null, page));
				ctx.waitTicks(5);
				ctx.takeScreenshot("silicium-sodium-page" + i);
			}
			ctx.setScreen(() -> null);
		}
	}
	//#endif

	private static int countHidden(ClientGameTestContext ctx, Class<? extends Entity> type) {
		return ctx.computeOnClient(mc -> {
			int n = 0;
			for (Entity e : mc.level.entitiesForRendering()) {
				if (type.isInstance(e) && EntityGrouping.isHidden(e)) n++;
			}
			return n;
		});
	}

	private static boolean pigVisible(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e instanceof Pig) return ((CullHolder) e).silicium$cullTarget().lastKnownVisible();
			}
			throw new AssertionError("pig not found on the client");
		});
	}

	private static void rebuildAround(ClientGameTestContext ctx, int x, int y, int z) {
		ctx.runOnClient(mc -> mc.level.setSectionRangeDirty((x >> 4) - 1, (y >> 4) - 1, (z >> 4) - 1, (x >> 4) + 1, (y >> 4) + 1, (z >> 4) + 1));
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
