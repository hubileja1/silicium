package pl.silicium;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
//#if MC >= 1.21.11
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
//#else
import net.minecraft.world.entity.projectile.AbstractArrow;
//#endif

/**
 * Once per tick (20x/s, not every frame) groups crowd-prone entities by block and marks
 * the surplus as hidden. On servers a single block can hold hundreds of dropped items,
 * farm mobs, TNT or falling sand - drawing all of them adds nothing visually but costs a lot.
 * Purely client-side: the server and gameplay are unaffected.
 */
public final class EntityGrouping {
	private static final Long2IntOpenHashMap ITEMS = new Long2IntOpenHashMap();
	private static final Long2IntOpenHashMap MOBS = new Long2IntOpenHashMap();
	private static final Long2IntOpenHashMap TNT = new Long2IntOpenHashMap();
	private static IntOpenHashSet hidden = new IntOpenHashSet();
	private static IntOpenHashSet building = new IntOpenHashSet();

	/** Per-tick explosion counters (see ClientPacketListenerMixin). */
	public static int explosionsThisTick;
	public static int explosionSoundsThisTick;

	private EntityGrouping() {
	}

	public static boolean isHidden(Entity entity) {
		return hidden.contains(entity.getId());
	}

	public static void tick(Minecraft mc) {
		explosionsThisTick = 0;
		explosionSoundsThisTick = 0;

		SiliciumConfig cfg = SiliciumConfig.get();
		ClientLevel level = mc.level;
		building.clear();

		if (level != null && cfg.enabled) {
			ITEMS.clear();
			MOBS.clear();
			TNT.clear();
			// pass 1: count entities per block
			for (Entity e : level.entitiesForRendering()) {
				Long2IntOpenHashMap counts = categoryOf(e);
				if (counts != null) counts.addTo(e.blockPosition().asLong(), 1);
			}
			// pass 2: decide per entity. Every entity has a fixed "lottery number" from its id; in a block with
			// N entities and limit L, those whose number is below L/N stay visible. A mob walking into the next,
			// similarly crowded block keeps its number, so it stays visible (or hidden) - no swapping, no flicker.
			// The crowd level is averaged over the block and its horizontal neighbours, and a +-15% margin keeps
			// entities from flipping when the count changes by one.
			IntOpenHashSet wasHidden = hidden;
			for (Entity e : level.entitiesForRendering()) {
				Long2IntOpenHashMap counts = categoryOf(e);
				if (counts == null) continue;
				int limit = limitOf(counts, cfg);
				if (limit <= 0) continue;
				BlockPos p = e.blockPosition();
				int own = counts.get(p.asLong());
				if (own <= limit && !wasHidden.contains(e.getId())) continue;
				double crowd = crowdAround(counts, p);
				if (crowd <= limit) continue;
				double keep = limit / crowd;          // fraction that stays visible
				double lottery = lottery(e.getId());  // fixed per entity, 0..1
				boolean hide = wasHidden.contains(e.getId()) ? lottery >= keep * 0.85 : lottery >= keep * 1.15;
				if (hide) building.add(e.getId());
			}
		}

		// swap sets - the render thread always reads a complete set from the previous tick
		IntOpenHashSet tmp = hidden;
		hidden = building;
		building = tmp;
	}

	/** Average number of entities per occupied block in the block and its 4 horizontal neighbours. */
	private static double crowdAround(Long2IntOpenHashMap counts, BlockPos p) {
		int x = p.getX(), y = p.getY(), z = p.getZ();
		int sum = 0, occupied = 0;
		for (long key : new long[]{p.asLong(), BlockPos.asLong(x + 1, y, z), BlockPos.asLong(x - 1, y, z),
				BlockPos.asLong(x, y, z + 1), BlockPos.asLong(x, y, z - 1)}) {
			int c = counts.get(key);
			if (c > 0) {
				sum += c;
				occupied++;
			}
		}
		return occupied == 0 ? 0 : (double) sum / occupied;
	}

	/** Stable pseudo-random number in [0, 1) for an entity id. */
	private static double lottery(int id) {
		int h = id * 0x9E3779B9;
		h ^= h >>> 15;
		h *= 0x85EBCA6B;
		h ^= h >>> 13;
		return (h >>> 8) / (double) (1 << 24);
	}

	private static Long2IntOpenHashMap categoryOf(Entity e) {
		if (e instanceof ItemEntity || e instanceof ExperienceOrb || e instanceof AbstractArrow) return ITEMS;
		if (e instanceof PrimedTnt || e instanceof FallingBlockEntity) return TNT;
		if (e instanceof Mob && !e.hasCustomName() && !e.isVehicle() && !e.isPassenger()) return MOBS;
		return null;
	}

	private static int limitOf(Long2IntOpenHashMap category, SiliciumConfig cfg) {
		if (category == ITEMS) return cfg.maxItemsPerBlock;
		if (category == TNT) return cfg.maxTntPerBlock;
		return cfg.maxMobsPerBlock;
	}
}
