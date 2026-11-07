package pl.silicium;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Config stored in config/silicium.json (edited from the Silicium tab in Sodium's video settings).
 * For all distances and limits: 0 = no limit.
 */
public final class SiliciumConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("silicium.json");

	public boolean enabled = true;

	// --- Chests ---
	/** Closed chests are baked into the chunk mesh instead of being drawn by the block entity renderer every frame. */
	public boolean staticChests = true;

	// --- Occlusion culling ---
	/** Skip rendering entities and block entities hidden behind solid blocks (computed on a background thread). */
	public boolean occlusionCulling = true;

	// --- Render distances (blocks) ---
	public int entityDistance = 64;
	public int itemDistance = 32;
	public int itemFrameDistance = 24;
	public int armorStandDistance = 64;
	public int blockEntityDistance = 48;
	public int particleDistance = 32;
	public boolean alwaysRenderPlayers = true;
	public boolean alwaysRenderNamedAndGlowing = true;

	// --- Crowds (per block) ---
	/** Max dropped items / XP orbs / arrows rendered in one block. */
	public int maxItemsPerBlock = 4;
	/** Max mobs rendered in one block (mob farms, stacked animals). */
	public int maxMobsPerBlock = 6;
	/** Max primed TNT / falling blocks rendered in one block (TNT and sand cannons). */
	public int maxTntPerBlock = 2;
	// --- Mob animation LOD ---
	/** Beyond this distance mob animation is refreshed less often (0 = off). */
	public int animationLodDistance = 32;
	/** Animation refresh rate at the LOD start distance; it falls off further away (half at 2x the distance). */
	public int distantAnimationFps = 20;

	/** Render only one item model per stack instead of up to 5. */
	public boolean singleItemModel = true;

	// --- Explosions ---
	public int maxExplosionsPerTick = 4;
	public int maxExplosionSoundsPerTick = 3;
	public boolean explosionDebris = false;

	// --- Particles & misc ---
	/** Hard cap on live particles (vanilla allows ~16k per group). 0 = vanilla. */
	public int maxParticles = 1000;
	/** Percentage of bubble column particles kept (soul sand / magma). */
	public int bubbleColumnPercent = 0;
	/** Hide the spinning mob inside monster spawners. */
	public boolean hideSpawnerMobs = true;

	// --- Water rendering ---
	/** Render water in the solid layer instead of the translucent one (much faster, but not see-through). */
	/** 0 = off, 1 = waterfalls only (falling water - water walls, waterfalls), 2 = everywhere (except near the camera). */
	public int opaqueWaterMode = 1;
	/** Water closer than this (blocks) stays see-through even with opaque water on. 0 = opaque everywhere. */
	public int opaqueWaterNearDistance = 10;

	// --- Water & lava (percent of effects kept) ---
	/** Lava pops, lava particles and lava ambient sounds. */
	public int lavaEffectsPercent = 20;
	/** Underwater particles and flowing water sounds. */
	public int waterEffectsPercent = 20;
	/** Water / lava drips from ceilings. */
	public int dripParticlePercent = 10;

	// --- Pistons ---
	/** Blocks moved by pistons further than this are not drawn during the move (flying machines, farms). */
	public int movingPistonDistance = 32;

	// --- Terrain / open world ---
	/** Percentage of random block positions sampled for effects every tick (vanilla samples 667 x 2). */
	public int blockEffectsDensity = 40;
	/** Falling leaf particles in forests (also cherry petals, pale oak, rain drips from leaves). */
	public int leafParticlePercent = 20;
	/** Biome ambient particles: ash, spores, white ash, crimson/warped particles. */
	public int ambientParticlePercent = 30;

	// Squared distances (compared against distanceSq, no square roots)
	public transient double entityDistSq, itemDistSq, itemFrameDistSq, armorStandDistSq, blockEntityDistSq, particleDistSq, movingPistonDistSq;

	private static SiliciumConfig instance = new SiliciumConfig();

	static {
		instance.recompute();
	}

	public static SiliciumConfig get() {
		return instance;
	}

	public static void load() {
		SiliciumConfig cfg = new SiliciumConfig();
		if (Files.exists(FILE)) {
			try (Reader r = Files.newBufferedReader(FILE)) {
				SiliciumConfig read = GSON.fromJson(r, SiliciumConfig.class);
				if (read != null) cfg = read;
			} catch (Exception e) {
				Silicium.LOGGER.warn("Could not read {}, using defaults", FILE, e);
			}
		}
		cfg.recompute();
		instance = cfg;
		save();
	}

	public static void save() {
		instance.recompute();
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer w = Files.newBufferedWriter(FILE)) {
				GSON.toJson(instance, w);
			}
		} catch (Exception e) {
			Silicium.LOGGER.warn("Could not save {}", FILE, e);
		}
	}

	public void recompute() {
		entityDistSq = sq(entityDistance);
		itemDistSq = sq(itemDistance);
		itemFrameDistSq = sq(itemFrameDistance);
		armorStandDistSq = sq(armorStandDistance);
		blockEntityDistSq = sq(blockEntityDistance);
		particleDistSq = sq(particleDistance);
		movingPistonDistSq = sq(movingPistonDistance);
	}

	private static double sq(int d) {
		return d <= 0 ? Double.MAX_VALUE : (double) d * d;
	}
}
