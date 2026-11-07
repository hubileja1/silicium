package pl.silicium.water;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import pl.silicium.SiliciumConfig;
import pl.silicium.compat.Mc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Opaque water only in the distance: chunk sections (16x16x16) closer to the camera than the configured
 * distance keep normal see-through water, sections further away get the fast opaque water.
 * <p>
 * The chunk mesher (vanilla or Sodium, on worker threads) tells us which section it is building through
 * a thread-local, and the water layer decision checks it against a snapshot of "near" sections. Once per
 * tick the snapshot is recomputed from the camera position; sections that entered or left the near zone
 * are rebuilt, so only a handful of sections are rebuilt when you walk across a section border.
 */
public final class WaterDistance {
	private static final long NONE = Long.MIN_VALUE;
	private static final ThreadLocal<long[]> CURRENT = ThreadLocal.withInitial(() -> new long[]{NONE});

	/** Sections where water stays translucent. Replaced as a whole (never mutated) - read from mesher threads. */
	private static volatile LongOpenHashSet near = new LongOpenHashSet();
	private static ClientLevel lastLevel;
	private static int lastDistance = -1;

	private WaterDistance() {
	}

	public static final int MODE_OFF = 0, MODE_WATERFALLS = 1, MODE_EVERYWHERE = 2;

	/**
	 * Should this water be drawn opaque (solid layer)? Called from chunk-mesher threads.
	 * Waterfalls mode: only falling water (waterfalls, water walls) - oceans, rivers and lakes stay pretty.
	 * Everywhere mode: all water.
	 * In both modes water in sections close to the camera stays see-through.
	 */
	public static boolean opaque(FluidState state) {
		SiliciumConfig cfg = SiliciumConfig.get();
		if (!cfg.enabled || cfg.opaqueWaterMode == MODE_OFF || !state.is(FluidTags.WATER)) return false;
		if (cfg.opaqueWaterMode == MODE_WATERFALLS
				&& !(state.hasProperty(FlowingFluid.FALLING) && state.getValue(FlowingFluid.FALLING))) {
			return false;
		}
		return !keepTranslucent();
	}

	// --- mesher side (worker threads) ---

	public static void beginSection(int sx, int sy, int sz) {
		CURRENT.get()[0] = SectionPos.asLong(sx, sy, sz);
	}

	public static void endSection() {
		CURRENT.get()[0] = NONE;
	}

	/** True when the water being meshed right now is close to the camera and must stay see-through. */
	public static boolean keepTranslucent() {
		if (SiliciumConfig.get().opaqueWaterNearDistance <= 0) return false;
		long section = CURRENT.get()[0];
		return section != NONE && near.contains(section);
	}

	// --- render / tick side ---

	public static void tick(Minecraft mc) {
		SiliciumConfig cfg = SiliciumConfig.get();
		ClientLevel level = mc.level;
		boolean active = level != null && cfg.enabled && cfg.opaqueWaterMode != MODE_OFF && cfg.opaqueWaterNearDistance > 0;
		if (level != lastLevel || cfg.opaqueWaterNearDistance != lastDistance) {
			near = new LongOpenHashSet(); // new world / new distance: start fresh (chunks are rebuilt anyway on load)
			lastLevel = level;
			lastDistance = cfg.opaqueWaterNearDistance;
		}
		LongOpenHashSet next = new LongOpenHashSet();
		if (active) {
			Vec3 cam = Mc.cameraPos();
			double r = cfg.opaqueWaterNearDistance;
			int reach = (int) Math.ceil(r / 16.0) + 1;
			int cx = SectionPos.blockToSectionCoord((int) Math.floor(cam.x));
			int cy = SectionPos.blockToSectionCoord((int) Math.floor(cam.y));
			int cz = SectionPos.blockToSectionCoord((int) Math.floor(cam.z));
			for (int x = cx - reach; x <= cx + reach; x++) {
				for (int y = cy - reach; y <= cy + reach; y++) {
					for (int z = cz - reach; z <= cz + reach; z++) {
						if (distSqToSection(cam, x, y, z) < r * r) next.add(SectionPos.asLong(x, y, z));
					}
				}
			}
		}
		LongOpenHashSet previous = near;
		if (next.equals(previous)) return;
		near = next; // publish first, then rebuild - a rebuilt section always sees the new decision
		for (long s : next) if (!previous.contains(s)) rebuild(s);
		for (long s : previous) if (!next.contains(s)) rebuild(s);
	}

	private static double distSqToSection(Vec3 cam, int sx, int sy, int sz) {
		double dx = axis(cam.x, sx), dy = axis(cam.y, sy), dz = axis(cam.z, sz);
		return dx * dx + dy * dy + dz * dz;
	}

	private static double axis(double c, int s) {
		double min = s * 16.0, max = min + 16.0;
		return c < min ? min - c : c > max ? c - max : 0;
	}

	private static void rebuild(long section) {
		Mc.rebuildSection(SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
	}

	// --- Sodium meshing task: find the section through reflection (field/method names differ per Sodium version) ---

	private static volatile Method sectionGetter;
	private static volatile Field sectionField;
	private static volatile Method chunkX, chunkY, chunkZ;
	private static volatile boolean reflectionFailed;

	public static void beginSodiumTask(Object task) {
		if (reflectionFailed) return;
		try {
			Object section = findSection(task);
			if (section == null) return;
			if (chunkX == null) {
				Class<?> c = section.getClass();
				chunkX = c.getMethod("getChunkX");
				chunkY = c.getMethod("getChunkY");
				chunkZ = c.getMethod("getChunkZ");
			}
			beginSection((int) chunkX.invoke(section), (int) chunkY.invoke(section), (int) chunkZ.invoke(section));
		} catch (Throwable t) {
			reflectionFailed = true; // unknown Sodium layout - water just stays opaque everywhere
		}
	}

	private static Object findSection(Object task) throws ReflectiveOperationException {
		if (sectionGetter == null && sectionField == null) {
			for (Class<?> c = task.getClass(); c != null && sectionGetter == null && sectionField == null; c = c.getSuperclass()) {
				for (Method m : c.getDeclaredMethods()) {
					if (m.getName().equals("getRenderSection") && m.getParameterCount() == 0) {
						m.setAccessible(true);
						sectionGetter = m;
						break;
					}
				}
				if (sectionGetter == null) {
					for (String name : new String[]{"render", "section"}) {
						try {
							Field f = c.getDeclaredField(name);
							f.setAccessible(true);
							sectionField = f;
							break;
						} catch (NoSuchFieldException ignored) {
						}
					}
				}
			}
			if (sectionGetter == null && sectionField == null) throw new NoSuchFieldException("render section");
		}
		return sectionGetter != null ? sectionGetter.invoke(task) : sectionField.get(task);
	}
}
